import SwiftUI

/// The journal on the whole screen, opening its deck and moving between pages by itself.
struct SwitcherDeckNavDemo: View {
    var body: some View {
        SwitcherDeckNav(autoPlay: true)
            .background(Color(hex: 0x0d0c0b).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SwitcherDeckNavDemo()
}
