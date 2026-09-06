import Foundation
import XCTest
@testable import NoScrollCore

final class StateTests: XCTestCase {
    func readyState(now: Date = Date(timeIntervalSince1970: 100)) -> ControlState {
        var s = ControlState()
        s.consentVersion = ControlState.currentConsentVersion
        s.mode = .protect
        s.selectionData = Data([1]); s.profileRevision = UUID()
        s.capture = CaptureHealth(phase: .running, sessionID: UUID(), lastFrameAt: now)
        return s
    }
    func testFreshMatchingSessionCanCommit() {
        let s = readyState()
        XCTAssertTrue(s.canCommit(.shield, revision: s.revision, sessionID: s.capture.sessionID!, now: Date(timeIntervalSince1970: 101)))
    }
    func testLateSessionAndRevisionResultsRejected() {
        let s = readyState()
        XCTAssertFalse(s.canCommit(.shield, revision: UUID(), sessionID: s.capture.sessionID!, now: Date(timeIntervalSince1970: 101)))
        XCTAssertFalse(s.canCommit(.shield, revision: s.revision, sessionID: UUID(), now: Date(timeIntervalSince1970: 101)))
    }
    func testStoppedStaleFutureAndUnconsentedCaptureRejected() {
        for now in [90.0, 104.0] {
            let s = readyState()
            XCTAssertFalse(s.canCommit(.shield, revision: s.revision, sessionID: s.capture.sessionID!, now: Date(timeIntervalSince1970: now)))
        }
        var s = readyState(); let session = s.capture.sessionID!
        s.capture.phase = .paused
        XCTAssertFalse(s.canCommit(.shield, revision: s.revision, sessionID: session, now: Date(timeIntervalSince1970: 101)))
        s = readyState(); s.consentVersion = 0
        XCTAssertFalse(s.canCommit(.shield, revision: s.revision, sessionID: s.capture.sessionID!, now: Date(timeIntervalSince1970: 101)))
    }
    func testObserveOffAndAlreadyShieldedCannotCommitBlock() {
        for mode in [ProtectionMode.observe,.off,.recover] {
            var s = readyState(); s.mode = mode
            XCTAssertFalse(s.canCommit(.shield, revision: s.revision, sessionID: s.capture.sessionID!, now: Date(timeIntervalSince1970: 101)))
        }
        var s = readyState(); s.isShielded = true
        XCTAssertFalse(s.canCommit(.shield, revision: s.revision, sessionID: s.capture.sessionID!, now: Date(timeIntervalSince1970: 101)))
    }
    func testRecoveryCanOnlyRearmFromRecovery() {
        var s = readyState(); let session = s.capture.sessionID!
        XCTAssertFalse(s.canCommit(.rearm, revision: s.revision, sessionID: session, now: Date(timeIntervalSince1970: 101)))
        s.mode = .recover
        XCTAssertTrue(s.canCommit(.rearm, revision: s.revision, sessionID: session, now: Date(timeIntervalSince1970: 101)))
    }
    func testMissingSelectionAndProfilesDisallowBlock() {
        var s = readyState(); s.selectionData = nil
        XCTAssertFalse(s.canCommit(.shield, revision: s.revision, sessionID: s.capture.sessionID!, now: Date(timeIntervalSince1970: 101)))
        s = readyState(); s.profileRevision = nil
        XCTAssertFalse(s.canCommit(.shield, revision: s.revision, sessionID: s.capture.sessionID!, now: Date(timeIntervalSince1970: 101)))
    }
    func testDefaultStateIsUnconsentedAndUnprotected() {
        let s = ControlState()
        XCTAssertEqual(s.mode, .off); XCTAssertFalse(s.isShielded)
        XCTAssertFalse(s.capture.isLive(at: Date())); XCTAssertEqual(s.consentVersion, 0)
    }
    func testFileStorePersistsAndSurfacesCorruption() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = try LockedStateStore(directory: directory)
        try store.withState { $0.readerRequested = true }
        XCTAssertTrue(try LockedStateStore(directory: directory).read().readerRequested)
        try Data("not json".utf8).write(to: directory.appendingPathComponent("control.json"))
        XCTAssertThrowsError(try store.read())
    }
    func testFailedMutationDoesNotPersist() throws {
        enum Expected: Error { case failure }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = try LockedStateStore(directory: directory)
        XCTAssertThrowsError(try store.withState { s in s.readerRequested = true; throw Expected.failure })
        XCTAssertFalse(try store.read().readerRequested)
    }
    func testIndependentStoresSerializeConcurrentMutations() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        _ = try LockedStateStore(directory: directory)
        let errors = SynchronizedErrors()
        DispatchQueue.concurrentPerform(iterations: 50) { _ in
            do {
                let store = try LockedStateStore(directory: directory)
                try store.withState { $0.interruptionCount += 1 }
            } catch { errors.append(error) }
        }
        XCTAssertEqual(errors.count, 0)
        XCTAssertEqual(try LockedStateStore(directory: directory).read().interruptionCount, 50)
    }
    func testUnknownSchemaAndOversizedStateRejected() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = try LockedStateStore(directory: directory)
        var s = ControlState(); s.schemaVersion = 99
        try JSONEncoder().encode(s).write(to: directory.appendingPathComponent("control.json"))
        XCTAssertThrowsError(try store.read())
        try Data(repeating: 32, count: 65_537).write(to: directory.appendingPathComponent("control.json"))
        XCTAssertThrowsError(try store.read())
    }
    func testPersistenceFailureRunsEffectRollback() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = try LockedStateStore(directory: directory)
        var rolledBack = false
        XCTAssertThrowsError(try store.withState(onFailure: { rolledBack = true }) { $0.schemaVersion = 99 })
        XCTAssertTrue(rolledBack)
        XCTAssertEqual(try store.read().schemaVersion, 1)
    }
    func testPageRestorationClampsToValidDocumentBounds() {
        XCTAssertEqual(ReadingProgress.clampedPage(8, pageCount: 4), 3)
        XCTAssertEqual(ReadingProgress.clampedPage(-4, pageCount: 4), 0)
        XCTAssertEqual(ReadingProgress.clampedPage(2, pageCount: 4), 2)
        XCTAssertEqual(ReadingProgress.clampedPage(3, pageCount: 0), 0)
    }
}

private final class SynchronizedErrors: @unchecked Sendable {
    private let lock = NSLock()
    private var errors: [Error] = []
    func append(_ error: Error) { lock.lock(); defer { lock.unlock() }; errors.append(error) }
    var count: Int { lock.lock(); defer { lock.unlock() }; return errors.count }
}
