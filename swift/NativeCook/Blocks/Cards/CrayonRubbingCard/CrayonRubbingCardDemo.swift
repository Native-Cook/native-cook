import SwiftUI

/// The rubbing station on the whole screen, the crayon bringing up the heron by itself.
struct CrayonRubbingCardDemo: View {
    var body: some View {
        CrayonRubbingCard(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    CrayonRubbingCardDemo()
}
