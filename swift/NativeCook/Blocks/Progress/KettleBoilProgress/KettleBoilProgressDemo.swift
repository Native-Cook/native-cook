import SwiftUI

/// The kettle alone on the whole screen, heating, clicking off and cooling by itself.
struct KettleBoilProgressDemo: View {
    var body: some View {
        KettleBoilProgress(autoPlay: true)
            .background(KettleBoilProgressPalette.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    KettleBoilProgressDemo()
}
