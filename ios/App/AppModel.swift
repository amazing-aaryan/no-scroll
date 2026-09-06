import Foundation
import Combine
import FamilyControls
import UserNotifications
import NoScrollCore

@MainActor
final class AppModel: ObservableObject {
    @Published var state = ControlState()
    @Published var profiles: [ScreenProfile] = []
    @Published var selection = FamilyActivitySelection()
    @Published var message: String?
    @Published var selectedTab = 0
    @Published var readerRequest = UUID()
    @Published private var settingsVerified = false
    private(set) var coordinator: ProtectionCoordinator?

    init() {
        do { coordinator = ProtectionCoordinator(store: try AppEnvironment.stateStore()); refresh() }
        catch { message = "App Group access is unavailable. Configure signing and the shared App Group for all targets. The local reader still works." }
    }
    var captureStatus: String {
        guard settingsVerified else { return "Protection status is unavailable. Do not rely on this session." }
        if (state.mode == .protect || state.mode == .recover || state.isShielded),
           AuthorizationCenter.shared.authorizationStatus != .approved {
            return "Screen Time access is not authorized. Blocking is not verified."
        }
        if state.isShielded { return "Selected app restricted; open the reader or pause to navigate." }
        guard state.capture.isLive(at: Date()) else { return "Capture is not live. Visual protection is not active." }
        switch state.mode {
        case .off: return "Disabled. Stop the iOS broadcast if its recording indicator remains visible."
        case .observe: return "Observing only. Nothing is being blocked."
        case .protect: return "Experimental visual protection is running. Unmatched screens are allowed."
        case .recover: return "Navigation pause: full access until a mapped allowed screen is recognized."
        }
    }
    func refresh() {
        guard let coordinator else { return }
        do {
            state = try coordinator.store.read()
            profiles = try coordinator.profiles.load().profiles
            if let data = state.selectionData { selection = try JSONDecoder().decode(FamilyActivitySelection.self, from: data) }
            settingsVerified = true
            if state.readerRequested {
                try coordinator.store.withState { $0.readerRequested = false }
                openReader()
            }
        } catch { settingsVerified = false; message = "Could not read protection settings. Stop the system broadcast and check the App Group setup; protection is not verified." }
    }
    func openReader() { selectedTab = 1; readerRequest = UUID() }
    func perform(_ work: (ProtectionCoordinator) throws -> Void) {
        guard let coordinator else { message = "Signing and App Group setup are required for this feature."; return }
        do { try work(coordinator); message = nil; refresh() }
        catch NativeError.invalidSelection { message = "Select exactly one app—Instagram—with no categories or websites." }
        catch NativeError.consentRequired { message = "Read and accept the screen-capture disclosure first." }
        catch NativeError.authorizationRequired { message = "Allow Screen Time access before enabling blocking." }
        catch NativeError.mappingsRequired { message = "Save at least one blocked and one allowed screen map before enabling blocking." }
        catch { message = "The operation did not complete. Stop the system broadcast and check setup before relying on protection." }
    }
    func addProfile(_ profile: ScreenProfile) {
        perform { coordinator in
            try coordinator.disable()
            let existing = try coordinator.profiles.load().profiles
            try coordinator.profiles.replace(existing + [profile])
        }
    }
    func deleteProfile(id: UUID) {
        perform { coordinator in
            try coordinator.disable()
            try coordinator.profiles.replace(coordinator.profiles.load().profiles.filter { $0.id != id })
        }
    }
    func requestAuthorization() async {
        do { try await AuthorizationCenter.shared.requestAuthorization(for: .individual); message = nil }
        catch { message = "Screen Time authorization was not granted. Observation and reading remain available." }
        refresh()
    }
    func requestNotifications() async {
        do {
            let granted = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
            message = granted ? "Reading notifications enabled. Focus mode can still hide them." : "Notifications are off. Open NoScroll manually from a shield."
        } catch { message = "Notifications are unavailable. Open NoScroll manually to read." }
    }
}
