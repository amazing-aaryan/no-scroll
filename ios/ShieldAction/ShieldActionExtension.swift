import Foundation
import ManagedSettings
import UserNotifications

final class ShieldActionExtension: ShieldActionDelegate {
    override func handle(action: ShieldAction, for application: ApplicationToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        do {
            let coordinator = ProtectionCoordinator(store: try AppEnvironment.stateStore())
            switch action {
            case .primaryButtonPressed:
                try coordinator.requestReader()
                let content = UNMutableNotificationContent()
                content.title = "Continue reading in NoScroll"
                content.body = "Tap to open your library. You can also open NoScroll yourself."
                content.userInfo = ["route": "reader"]
                let request = UNNotificationRequest(identifier: "noscroll.reader", content: content,
                    trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false))
                // Optional: denied notifications or Focus mode must never prevent manual recovery.
                UNUserNotificationCenter.current().add(request) { _ in }
                completionHandler(.close)
            case .secondaryButtonPressed:
                try coordinator.recover(application: application)
                completionHandler(.none)
            @unknown default: completionHandler(.close)
            }
        } catch {
            // Keep the safe manual escape: open NoScroll and use Disable & clear.
            completionHandler(.close)
        }
    }
}
