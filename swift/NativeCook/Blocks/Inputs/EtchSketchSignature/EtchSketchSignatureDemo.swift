import SwiftUI

/// The signature pad alone, signing, shaking clear and signing again by itself.
struct EtchSketchSignatureDemo: View {
    var body: some View {
        EtchSketchSignature(autoPlay: true)
            .background(EtchSketchSignatureColors.root.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    EtchSketchSignatureDemo()
}
