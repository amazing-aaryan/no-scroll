import ReplayKit
import CoreMedia
import CoreVideo
import ImageIO
import NoScrollCore

/// ReplayKit serializes these callbacks. Never enqueue or retain incoming sample buffers.
final class SampleHandler: RPBroadcastSampleHandler {
    private var coordinator: ProtectionCoordinator?
    private var matcher: VisionMatcher?
    private var archive: ProfileArchive?
    private var session: UUID?
    private var policyRevision: UUID?
    private var engine = ProtectionEngine()
    private var lastSampleAt = -Double.infinity
    private var finishing = false

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        do {
            let coordinator = ProtectionCoordinator(store: try AppEnvironment.stateStore())
            self.coordinator = coordinator
            let (id, archive) = try coordinator.startCapture()
            session = id; self.archive = archive
            matcher = try VisionMatcher(profiles: archive.profiles)
            finishing = false; lastSampleAt = -.infinity; engine.reset()
        } catch { terminate(issue: "setup_failed") }
    }

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        // No audio processing, recording, networking, or screenshot persistence.
        guard sampleBufferType == .video, !finishing else { return }
        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastSampleAt >= 0.5 else { return }
        lastSampleAt = now
        autoreleasepool {
            do {
                guard let coordinator, let session, let archive, let matcher else { throw NativeError.captureEnded }
                let state = try coordinator.heartbeat(session: session, profileRevision: archive.revision, now: Date())
                if policyRevision != state.revision { engine.reset(); policyRevision = state.revision }
                guard !state.isShielded else { engine.reset(); return }
                guard CMSampleBufferIsValid(sampleBuffer),
                      let pixels = CMSampleBufferGetImageBuffer(sampleBuffer) else { throw NativeError.visionUnavailable }
                let attachment = CMGetAttachment(sampleBuffer, key: RPVideoSampleOrientationKey as CFString,
                                                 attachmentModeOut: nil) as? NSNumber
                let orientation = attachment.flatMap { CGImagePropertyOrientation(rawValue: $0.uint32Value) } ?? .up
                let swapsAxes: Bool
                switch orientation {
                case .left, .leftMirrored, .right, .rightMirrored: swapsAxes = true
                default: swapsAxes = false
                }
                let width = Double(CVPixelBufferGetWidth(pixels)), height = Double(CVPixelBufferGetHeight(pixels))
                let aspect = swapsAxes ? height / width : width / height
                let match: ScreenMatch
                if (0.35...0.8).contains(aspect) {
                    let distances = try matcher.distances(pixelBuffer: pixels, orientation: orientation, aspectRatio: aspect)
                    match = LandmarkClassifier.classify(profiles: archive.profiles, distances: distances, aspectRatio: aspect)
                } else { match = .unknown }
                let action = engine.observe(match, at: now, mode: state.mode, isShielded: state.isShielded)
                try coordinator.report(match, action: action, revision: state.revision, session: session,
                                       analysisStartedAt: now, now: Date())
            } catch NativeError.captureEnded { terminate(issue: "session_ended") }
              catch NativeError.authorizationRequired { terminate(issue: "authorization_revoked") }
              catch { terminate(issue: "analysis_failed") }
        }
    }

    override func broadcastPaused() {
        engine.reset()
        if let session { try? coordinator?.endCapture(session: session, phase: .paused) }
    }
    override func broadcastResumed() {
        engine.reset(); lastSampleAt = -.infinity
        // First video callback rechecks consent and the session before resuming analysis.
    }
    override func broadcastFinished() {
        if let session { try? coordinator?.endCapture(session: session, phase: .stopped) }
        releaseSession()
    }
    private func terminate(issue: String) {
        guard !finishing else { return }
        finishing = true
        if let session { try? coordinator?.endCapture(session: session, phase: .failed, issue: issue) }
        releaseSession()
        finishBroadcastWithError(NSError(domain: "NoScrollCapture", code: 1,
            userInfo: [NSLocalizedDescriptionKey: "NoScroll capture ended. Open NoScroll to check setup or restart. Existing restrictions can be cleared there."]))
    }
    private func releaseSession() {
        session = nil; archive = nil; matcher = nil; policyRevision = nil; engine.reset()
    }
}
