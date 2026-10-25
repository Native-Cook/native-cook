import SwiftUI

/// The library's shelf on the whole screen, pulling a section out and putting it back by itself.
struct BookshelfSpineNavDemo: View {
    var body: some View {
        BookshelfSpineNav(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    BookshelfSpineNavDemo()
}
