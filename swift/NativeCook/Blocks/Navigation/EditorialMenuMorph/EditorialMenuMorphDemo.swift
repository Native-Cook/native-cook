import SwiftUI

/// The museum site, opening its menu and running a finger down the words by itself.
struct EditorialMenuMorphDemo: View {
    var body: some View {
        EditorialMenuMorph(autoPlay: true)
            .background(Color(hex: 0xefe9df).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    EditorialMenuMorphDemo()
}
