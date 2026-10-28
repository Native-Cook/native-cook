import SwiftUI

/// The guests step on the whole screen, adding and removing its little people by itself.
struct GuestCountFiguresDemo: View {
    var body: some View {
        GuestCountFigures(autoPlay: true)
    }
}

#Preview {
    GuestCountFiguresDemo()
}
