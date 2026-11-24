import SwiftUI

struct LouverThemeSwitchDemo: View {
    @State private var prefs = LouverThemeSwitchPrefs()

    var body: some View {
        LouverThemeSwitch(autoPlay: true, contentKey: prefs) { screen in
            LouverThemeSwitchAlmanac(screen: screen, prefs: $prefs)
        }
    }
}

#Preview {
    LouverThemeSwitchDemo()
}
