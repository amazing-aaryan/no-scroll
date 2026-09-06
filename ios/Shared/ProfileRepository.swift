import Foundation
import NoScrollCore

final class ProfileRepository {
    private let store: LockedStateStore
    private var url: URL { store.directory.appendingPathComponent("profiles.json") }
    init(store: LockedStateStore) { self.store = store }

    /// Call inside the control-store lock when coordinating a session or profile write.
    func load() throws -> ProfileArchive {
        guard FileManager.default.fileExists(atPath: url.path) else { return ProfileArchive() }
        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size <= 2_097_152 else { throw NativeError.invalidArchive }
        let archive = try JSONDecoder().decode(ProfileArchive.self, from: Data(contentsOf: url))
        guard archive.isValid else { throw NativeError.invalidArchive }
        return archive
    }

    /// The caller must explicitly disable/stop before editing screen mappings.
    func replace(_ profiles: [ScreenProfile]) throws {
        let archive = ProfileArchive(profiles: profiles)
        guard archive.isValid else { throw NativeError.invalidMapping }
        try store.withState { state in
            guard state.mode == .off, state.capture.sessionID == nil else { throw NativeError.stopBeforeMapping }
            let data = try JSONEncoder().encode(archive)
            guard data.count <= 2_097_152 else { throw NativeError.invalidArchive }
            try data.write(to: url, options: [.atomic, .completeFileProtection])
            state.profileRevision = archive.revision
            state.revision = UUID()
        }
    }
}
