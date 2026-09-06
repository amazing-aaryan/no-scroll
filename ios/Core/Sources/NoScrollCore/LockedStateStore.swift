import Foundation
#if canImport(Darwin)
import Darwin
#else
import Glibc
#endif

public enum StateStoreError: Error { case lockUnavailable, invalidState, oversizedState }

/// Different processes must use the same lock file. Do not nest calls on this store.
public final class LockedStateStore {
    public let directory: URL
    private let stateURL: URL
    private let lockURL: URL
    public init(directory: URL) throws {
        self.directory = directory
        self.stateURL = directory.appendingPathComponent("control.json")
        self.lockURL = directory.appendingPathComponent("control.lock")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
                                               attributes: [.posixPermissions: 0o700])
    }
    public func read() throws -> ControlState { try locked { try load() } }
    @discardableResult
    public func withState<T>(onFailure: (() -> Void)? = nil, _ body: (inout ControlState) throws -> T) throws -> T {
        try locked {
            do {
                var state = try load()
                let result = try body(&state)
                guard state.isValid else { throw StateStoreError.invalidState }
                let data = try JSONEncoder().encode(state)
                guard data.count <= 65_536 else { throw StateStoreError.oversizedState }
                try data.write(to: stateURL, options: .atomic)
                return result
            } catch {
                // Roll back external effects while the same cross-process lock is held.
                onFailure?()
                throw error
            }
        }
    }
    private func load() throws -> ControlState {
        guard FileManager.default.fileExists(atPath: stateURL.path) else { return ControlState() }
        let size = try stateURL.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size <= 65_536 else { throw StateStoreError.oversizedState }
        let state = try JSONDecoder().decode(ControlState.self, from: Data(contentsOf: stateURL))
        guard state.isValid else { throw StateStoreError.invalidState }
        return state
    }
    private func locked<T>(_ body: () throws -> T) throws -> T {
        let fd = lockURL.path.withCString { open($0, O_CREAT | O_RDWR, mode_t(0o600)) }
        guard fd >= 0 else { throw StateStoreError.lockUnavailable }
        defer { _ = close(fd) }
        while flock(fd, LOCK_EX) != 0 {
            guard errno == EINTR else { throw StateStoreError.lockUnavailable }
        }
        defer { _ = flock(fd, LOCK_UN) }
        return try body()
    }
}
