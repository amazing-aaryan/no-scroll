import Foundation
import FamilyControls
import ManagedSettings
import NoScrollCore

/// Platform effects and their durable state changes share one cross-process lock.
final class ProtectionCoordinator {
    let store: LockedStateStore
    let profiles: ProfileRepository
    private let shield = ManagedSettingsStore(named: .init("noscroll.visual"))
    init(store: LockedStateStore) { self.store = store; self.profiles = ProfileRepository(store: store) }

    static func selectedApplications(_ data: Data?) throws -> Set<ApplicationToken> {
        guard let data else { throw NativeError.invalidSelection }
        let selection = try JSONDecoder().decode(FamilyActivitySelection.self, from: data)
        guard selection.applicationTokens.count == 1, selection.categoryTokens.isEmpty,
              selection.webDomainTokens.isEmpty else { throw NativeError.invalidSelection }
        return selection.applicationTokens
    }

    func saveSelection(_ selection: FamilyActivitySelection) throws {
        let data = try JSONEncoder().encode(selection)
        _ = try Self.selectedApplications(data)
        try disable()
        try store.withState { state in state.selectionData = data; state.revision = UUID() }
    }

    func consentToObservation() throws {
        try store.withState(onFailure: { self.shield.clearAllSettings() }) { state in
            state.consentVersion = ControlState.currentConsentVersion
            state.mode = .observe; state.revision = UUID(); state.isShielded = false
            self.shield.clearAllSettings()
        }
    }

    func setMode(_ mode: ProtectionMode) throws {
        guard mode == .observe || mode == .protect else { throw NativeError.invalidSelection }
        var changedShield = false
        try store.withState(onFailure: { if changedShield { self.shield.clearAllSettings() } }) { state in
            guard state.consentVersion == ControlState.currentConsentVersion else { throw NativeError.consentRequired }
            if mode == .protect {
                guard AuthorizationCenter.shared.authorizationStatus == .approved else { throw NativeError.authorizationRequired }
                _ = try Self.selectedApplications(state.selectionData)
                let archive = try self.profiles.load()
                guard archive.readyForIntervention, archive.revision == state.profileRevision else { throw NativeError.mappingsRequired }
            }
            state.mode = mode; state.revision = UUID(); state.isShielded = false
            changedShield = true
            self.shield.clearAllSettings()
        }
    }

    /// Explicit user stop. An in-flight frame carries the old session/revision and cannot reapply a shield.
    func disable() throws {
        do {
            try store.withState(onFailure: { self.shield.clearAllSettings() }) { state in
                state.mode = .off; state.consentVersion = 0; state.revision = UUID()
                state.capture = CaptureHealth(); state.isShielded = false
                self.shield.clearAllSettings()
            }
        } catch {
            // Emergency release still works if the container/lock is unavailable.
            // The UI also instructs the user to stop broadcasting; persistence was not confirmed.
            shield.clearAllSettings()
            throw error
        }
    }

    func startCapture() throws -> (UUID, ProfileArchive) {
        try store.withState { state in
            guard state.consentVersion == ControlState.currentConsentVersion, state.mode != .off else { throw NativeError.consentRequired }
            let archive = try self.profiles.load()
            guard !archive.profiles.isEmpty, archive.revision == state.profileRevision else { throw NativeError.mappingsRequired }
            if state.mode == .protect || state.mode == .recover {
                guard AuthorizationCenter.shared.authorizationStatus == .approved else { throw NativeError.authorizationRequired }
                _ = try Self.selectedApplications(state.selectionData)
                guard archive.readyForIntervention else { throw NativeError.mappingsRequired }
            }
            let session = UUID()
            state.capture = CaptureHealth(phase: .running, sessionID: session)
            return (session, archive)
        }
    }

    /// Called once per sampled frame, never per audio buffer. Revalidates consent before image processing.
    func heartbeat(session: UUID, profileRevision: UUID, now: Date) throws -> ControlState {
        try store.withState { state in
            guard state.capture.sessionID == session, state.profileRevision == profileRevision,
                  state.consentVersion == ControlState.currentConsentVersion, state.mode != .off else { throw NativeError.captureEnded }
            if state.mode == .protect || state.mode == .recover {
                guard AuthorizationCenter.shared.authorizationStatus == .approved else { throw NativeError.authorizationRequired }
            }
            state.capture.phase = .running; state.capture.lastFrameAt = now; state.capture.issue = nil
            return state
        }
    }

    func report(_ match: ScreenMatch, action: Intervention, revision: UUID, session: UUID,
                analysisStartedAt: Double, now: Date) throws {
        // Do not act on slow/stale Vision results. Capture is deliberately not queued.
        let elapsed = ProcessInfo.processInfo.systemUptime - analysisStartedAt
        guard elapsed >= 0, elapsed <= 1.5 else { return }
        var changedShield = false
        try store.withState(onFailure: { if changedShield { self.shield.clearAllSettings() } }) { state in
            guard state.capture.sessionID == session, state.revision == revision,
                  state.consentVersion == ControlState.currentConsentVersion, state.mode != .off else { return }
            if match != .unknown { state.capture.lastMatch = match.summary }
            guard state.canCommit(action, revision: revision, sessionID: session, now: now) else { return }
            guard AuthorizationCenter.shared.authorizationStatus == .approved else { throw NativeError.authorizationRequired }
            let tokens = try Self.selectedApplications(state.selectionData)
            switch action {
            case .shield:
                changedShield = true
                self.shield.shield.applications = tokens
                state.isShielded = true; state.interruptionCount += 1
            case .rearm:
                state.mode = .protect; state.revision = UUID()
            case .none: break
            }
        }
    }

    /// A pause is intentionally not a timed unlock. It can remain unprotected indefinitely.
    func recover(application: ApplicationToken? = nil) throws {
        var changedShield = false
        try store.withState(onFailure: { if changedShield { self.shield.clearAllSettings() } }) { state in
            if let application {
                guard try Self.selectedApplications(state.selectionData).contains(application) else { throw NativeError.invalidSelection }
            }
            state.mode = state.capture.isLive(at: Date()) ? .recover : .observe
            state.isShielded = false; state.revision = UUID()
            changedShield = true
            self.shield.clearAllSettings()
        }
    }

    func endCapture(session: UUID, phase: CapturePhase, issue: String? = nil) throws {
        try store.withState { state in
            guard state.capture.sessionID == session else { return }
            state.capture.phase = phase; state.capture.issue = issue
            if phase != .paused { state.capture.sessionID = nil }
            // Keep an existing shield latched. NoScroll's explicit controls always offer release.
        }
    }

    func requestReader() throws { try store.withState { $0.readerRequested = true } }
}
