import SwiftUI

/// The catalogue drawer on the whole screen, thumbing to a card and back by itself.
struct CardCatalogListDemo: View {
    var body: some View {
        CardCatalogList(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    CardCatalogListDemo()
}
