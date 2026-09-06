import SwiftUI
import UserNotifications

@main
struct NoScrollApp: App {
    @UIApplicationDelegateAdaptor(NoScrollAppDelegate.self) var delegate
    @StateObject private var model = AppModel()
    @StateObject private var library = LibraryModel()
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            TabView(selection: $model.selectedTab) {
                ProtectionScreen(model: model).tabItem { Label("Protection", systemImage: "eye") }.tag(0)
                LibraryScreen(library: library, readerRequest: model.readerRequest)
                    .tabItem { Label("Read", systemImage: "book") }.tag(1)
            }
            .onChange(of: scenePhase) { _, phase in if phase == .active { model.refresh() } }
            .onReceive(NotificationCenter.default.publisher(for: .noScrollReader)) { _ in model.openReader() }
        }
    }
}

extension Notification.Name { static let noScrollReader = Notification.Name("NoScroll.reader") }

final class NoScrollAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if response.notification.request.content.userInfo["route"] as? String == "reader" {
            DispatchQueue.main.async { NotificationCenter.default.post(name: .noScrollReader, object: nil) }
        }
        completionHandler()
    }
}
