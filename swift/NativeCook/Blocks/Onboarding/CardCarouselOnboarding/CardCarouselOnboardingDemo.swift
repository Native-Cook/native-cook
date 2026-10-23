import CoreText
import SwiftUI

/// The original app's root: `OnboardingScreen`, in the light scheme its Info.plist asked for
/// (`UIUserInterfaceStyle` = Light).
struct CardCarouselOnboardingDemo: View {
    init() {
        CardCarouselOnboardingFonts.register()
    }

    var body: some View {
        OnboardingScreen()
            // The gallery this block is shown in asks for the dark scheme at its root, and an outer
            // preference wins over this one, so the block also sets the light scheme on its own views.
            .environment(\.colorScheme, .light)
            .preferredColorScheme(.light)
    }
}

/// The original app listed its fonts under `UIAppFonts` in Info.plist. The block registers
/// its one itself, once, so it needs nothing from the app that hosts it.
enum CardCarouselOnboardingFonts {
    private static let registered: Void = {
        for file in ["CaveatBrush-Regular"] {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                assertionFailure("\(file).ttf is missing from the app bundle")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func register() {
        _ = registered
    }
}

#Preview {
    CardCarouselOnboardingDemo()
}
