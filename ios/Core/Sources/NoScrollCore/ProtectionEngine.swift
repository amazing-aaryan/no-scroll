import Foundation

public enum ProtectionMode: String, Codable, Sendable { case off, observe, protect, recover }
public enum Intervention: Equatable, Sendable { case none, shield, rearm }

/// Pure temporal policy. It never performs platform effects or releases a latched shield.
public struct ProtectionEngine: Sendable {
    private var candidate: ScreenMatch = .unknown
    private var count = 0
    private var startedAt: Double = 0
    private var lastAt: Double?
    private var previousMode: ProtectionMode?
    public init() {}
    public mutating func reset() {
        candidate = .unknown; count = 0; startedAt = 0; lastAt = nil; previousMode = nil
    }
    public mutating func observe(_ match: ScreenMatch, at time: Double,
                                 mode: ProtectionMode, isShielded: Bool) -> Intervention {
        guard time.isFinite, time >= 0, !isShielded, mode == .protect || mode == .recover else {
            reset(); return .none
        }
        if previousMode != mode { reset(); previousMode = mode }
        if let lastAt, time <= lastAt { reset(); return .none }
        let gap = lastAt.map { time - $0 } ?? 0
        lastAt = time
        let eligible: Bool
        switch (mode, match) {
        case (.protect, .blocked), (.recover, .allowed): eligible = true
        default: eligible = false
        }
        guard eligible else { candidate = .unknown; count = 0; return .none }
        if match != candidate || gap > 1.5 {
            candidate = match; count = 1; startedAt = time
        } else { count += 1 }
        guard count >= 3, time - startedAt >= 1 else { return .none }
        candidate = .unknown; count = 0
        return mode == .protect ? .shield : .rearm
    }
}

public enum ReadingProgress {
    public static func clampedPage(_ page: Int, pageCount: Int) -> Int {
        min(max(page, 0), max(pageCount - 1, 0))
    }
}
