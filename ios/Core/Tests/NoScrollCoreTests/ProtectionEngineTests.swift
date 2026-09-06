import XCTest
@testable import NoScrollCore

final class ProtectionEngineTests: XCTestCase {
    let id = UUID()
    func testThreeDistinctStableFramesRequired() {
        var e = ProtectionEngine()
        XCTAssertEqual(e.observe(.blocked(id), at: 0, mode: .protect, isShielded: false), .none)
        XCTAssertEqual(e.observe(.blocked(id), at: 0.5, mode: .protect, isShielded: false), .none)
        XCTAssertEqual(e.observe(.blocked(id), at: 1, mode: .protect, isShielded: false), .shield)
    }
    func testBurstOfFramesDoesNotSatisfyMinimumDuration() {
        var e = ProtectionEngine()
        for t in [0.0, 0.01, 0.02, 0.03] {
            XCTAssertEqual(e.observe(.blocked(id), at: t, mode: .protect, isShielded: false), .none)
        }
    }
    func testUnknownOrAllowedResetsBlockedEvidence() {
        for interruption in [ScreenMatch.unknown, .allowed(UUID())] {
            var e = ProtectionEngine()
            _ = e.observe(.blocked(id), at: 0, mode: .protect, isShielded: false)
            _ = e.observe(.blocked(id), at: 0.5, mode: .protect, isShielded: false)
            _ = e.observe(interruption, at: 0.75, mode: .protect, isShielded: false)
            XCTAssertEqual(e.observe(.blocked(id), at: 1, mode: .protect, isShielded: false), .none)
        }
    }
    func testDifferentProfileResetsEvidence() {
        var e = ProtectionEngine()
        _ = e.observe(.blocked(id), at: 0, mode: .protect, isShielded: false)
        _ = e.observe(.blocked(id), at: 0.5, mode: .protect, isShielded: false)
        XCTAssertEqual(e.observe(.blocked(UUID()), at: 1, mode: .protect, isShielded: false), .none)
    }
    func testLargeGapDuplicateAndBackwardsFramesCannotTrigger() {
        for times in [[0.0,0.5,4.0], [0,0,1], [1,1.5,0.5]] {
            var e = ProtectionEngine()
            for t in times { XCTAssertEqual(e.observe(.blocked(id), at: t, mode: .protect, isShielded: false), .none) }
        }
    }
    func testObserveAndOffNeverShield() {
        for mode in [ProtectionMode.observe, .off] {
            var e = ProtectionEngine()
            for t in [0.0,0.5,1.0,1.5] { XCTAssertEqual(e.observe(.blocked(id), at: t, mode: mode, isShielded: false), .none) }
        }
    }
    func testLatchedShieldNeverClearedByUnknownOrAllowedFrame() {
        var e = ProtectionEngine()
        for (index, match) in [ScreenMatch.unknown, .allowed(id), .blocked(id)].enumerated() {
            XCTAssertEqual(e.observe(match, at: Double(index), mode: .protect, isShielded: true), .none)
        }
    }
    func testRecoveryDoesNotImmediatelyBlockUnderlyingReels() {
        var e = ProtectionEngine()
        for t in [0.0,0.5,1.0,1.5] { XCTAssertEqual(e.observe(.blocked(id), at: t, mode: .recover, isShielded: false), .none) }
        XCTAssertEqual(e.observe(.allowed(id), at: 2, mode: .recover, isShielded: false), .none)
        XCTAssertEqual(e.observe(.allowed(id), at: 2.5, mode: .recover, isShielded: false), .none)
        XCTAssertEqual(e.observe(.allowed(id), at: 3, mode: .recover, isShielded: false), .rearm)
    }
    func testModeChangeAndResetDiscardPriorEvidence() {
        var e = ProtectionEngine()
        _ = e.observe(.blocked(id), at: 0, mode: .protect, isShielded: false)
        _ = e.observe(.blocked(id), at: 0.5, mode: .protect, isShielded: false)
        _ = e.observe(.blocked(id), at: 0.7, mode: .observe, isShielded: false)
        XCTAssertEqual(e.observe(.blocked(id), at: 1, mode: .protect, isShielded: false), .none)
        e.reset()
        XCTAssertEqual(e.observe(.blocked(id), at: 1.5, mode: .protect, isShielded: false), .none)
    }
    func testNonfiniteClockCannotAccumulateEvidence() {
        var e = ProtectionEngine()
        for t in [Double.nan, .infinity, -1] {
            XCTAssertEqual(e.observe(.blocked(id), at: t, mode: .protect, isShielded: false), .none)
        }
    }
}
