import SwiftUI

/// The fortune modal alone, cracking the cookie and folding it back by itself.
struct FortuneCookieCrackDemo: View {
    var body: some View {
        FortuneCookieCrack(autoPlay: true)
            .background(FortuneCookieCrackColors.root.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FortuneCookieCrackDemo()
}
