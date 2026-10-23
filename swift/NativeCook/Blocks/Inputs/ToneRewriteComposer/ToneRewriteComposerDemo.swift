import SwiftUI

struct ToneRewriteComposerDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xd3daeb), height: 420) { ToneRewriteComposer(autoPlay: true) }
    }
}

#Preview {
    ToneRewriteComposerDemo()
}
