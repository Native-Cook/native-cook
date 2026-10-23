import CoreText
import SwiftUI

/// The original app's root: `SignInScreen` in the light scheme, as its `App` showed it.
struct ParticleSignInRingDemo: View {
    init() {
        ParticleSignInRingFonts.register()
    }

    var body: some View {
        SignInScreen()
            // The gallery this block is shown in asks for the dark scheme at its root, and an outer
            // preference wins over this one, so the block also sets the light scheme on its own views.
            .environment(\.colorScheme, .light)
            .preferredColorScheme(.light)
    }
}

/// The original app listed `FontAwesome.ttf` under `UIAppFonts` in Info.plist. The block registers
/// it itself, once, so it needs nothing from the app that hosts it.
enum ParticleSignInRingFonts {
    private static let registered: Void = {
        guard let url = Bundle.main.url(forResource: "FontAwesome", withExtension: "ttf") else {
            assertionFailure("FontAwesome.ttf is missing from the app bundle")
            return
        }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    static func register() {
        _ = registered
    }
}

#Preview {
    ParticleSignInRingDemo()
}
