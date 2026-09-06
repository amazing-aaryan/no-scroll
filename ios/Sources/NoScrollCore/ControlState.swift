import Foundation

public enum CapturePhase: String, Codable, Sendable { case stopped, running, paused, failed }

public struct CaptureHealth: Codable, Equatable, Sendable {
    public var phase: CapturePhase
    public var sessionID: UUID?
    public var lastFrameAt: Date?
    public var lastMatch: String
    /// Fixed diagnostic codes only: never localized system errors, pixels or extracted text.
    public var issue: String?
    public init(phase: CapturePhase = .stopped, sessionID: UUID? = nil,
                lastFrameAt: Date? = nil, lastMatch: String = "unknown", issue: String? = nil) {
        self.phase = phase; self.sessionID = sessionID; self.lastFrameAt = lastFrameAt
        self.lastMatch = lastMatch; self.issue = issue
    }
    public func isLive(at now: Date) -> Bool {
        guard phase == .running, sessionID != nil, let lastFrameAt else { return false }
        let age = now.timeIntervalSince(lastFrameAt)
        return age.isFinite && age >= 0 && age <= 3
    }
}

public struct ControlState: Codable, Equatable, Sendable {
    public static let currentConsentVersion = 1
    public var schemaVersion = 1
    public var revision = UUID()
    public var consentVersion = 0
    public var mode = ProtectionMode.off
    /// Encoded FamilyActivitySelection. The native boundary validates exactly one application token.
    public var selectionData: Data?
    public var profileRevision: UUID?
    public var capture = CaptureHealth()
    public var isShielded = false
    public var readerRequested = false
    public var interruptionCount = 0
    public init() {}
    public var isValid: Bool {
        schemaVersion == 1 && (0...Self.currentConsentVersion).contains(consentVersion) &&
        (selectionData?.count ?? 0) <= 32_768 && interruptionCount >= 0 &&
        ["unknown", "allowed", "blocked"].contains(capture.lastMatch) && (capture.issue?.count ?? 0) <= 64
    }
    public func canCommit(_ action: Intervention, revision expectedRevision: UUID,
                          sessionID: UUID, now: Date) -> Bool {
        guard isValid, revision == expectedRevision, capture.sessionID == sessionID,
              consentVersion == Self.currentConsentVersion, capture.isLive(at: now),
              selectionData?.isEmpty == false, profileRevision != nil, !isShielded else { return false }
        switch action {
        case .none: return false
        case .shield: return mode == .protect
        case .rearm: return mode == .recover
        }
    }
}
