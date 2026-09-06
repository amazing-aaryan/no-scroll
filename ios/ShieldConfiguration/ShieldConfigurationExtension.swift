import ManagedSettings
import ManagedSettingsUI
import UIKit

final class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemMaterialDark,
            backgroundColor: .black,
            icon: UIImage(systemName: "book.closed.fill"),
            title: .init(text: "A page instead of a feed", color: .white),
            subtitle: .init(text: "Open NoScroll to read, or tap the reading notification. Pause to navigate gives full access until a mapped allowed screen is recognized; it may remain paused.", color: .white),
            primaryButtonLabel: .init(text: "Read instead", color: .black),
            primaryButtonBackgroundColor: .white,
            secondaryButtonLabel: .init(text: "Pause to navigate", color: .white)
        )
    }
}
