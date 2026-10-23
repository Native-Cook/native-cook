import SwiftUI

/// The tangram alone on the whole screen, solving itself.
struct TangramSnapDemo: View {
    var body: some View {
        TangramSnap(autoPlay: true)
            .background(TangramSnapPainter.board[2].ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    TangramSnapDemo()
}
