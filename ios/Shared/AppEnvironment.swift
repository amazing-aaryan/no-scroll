import Foundation
import NoScrollCore

/// Every target receives these values through build settings, not duplicated literal group IDs.
enum AppEnvironment {
    static func stateStore() throws -> LockedStateStore {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "NoScrollAppGroup") as? String,
              !group.isEmpty, !group.contains("$("),
              let root = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else {
            throw NativeError.appGroupUnavailable
        }
        return try LockedStateStore(directory: root.appendingPathComponent("VisualProtection", isDirectory: true))
    }
}

enum NativeError: Error {
    case appGroupUnavailable, invalidMapping, visionUnavailable, invalidSelection
    case consentRequired, authorizationRequired, mappingsRequired, captureEnded, invalidArchive
    case imageTooLarge, unreadableImage, stopBeforeMapping
}
