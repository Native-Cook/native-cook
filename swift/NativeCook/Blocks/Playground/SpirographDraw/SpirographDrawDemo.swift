import SwiftUI

/// The spirograph alone on its desk, drawing, layering and tearing the sheet off by itself.
struct SpirographDrawDemo: View {
    var body: some View {
        SpirographDraw(autoPlay: true)
            .background(Color(hex: SpirographDrawMetrics.desk).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SpirographDrawDemo()
}
