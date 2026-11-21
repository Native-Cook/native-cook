import SwiftUI

struct WaveFieldPadDemo: View {
    @Environment(\.galleryBlockDismiss) private var close

    var body: some View {
        WaveFieldPad(autoPlay: true, onClose: close)
    }
}

#Preview {
    WaveFieldPadDemo()
}
