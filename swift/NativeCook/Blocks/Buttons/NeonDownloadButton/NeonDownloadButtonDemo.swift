import SwiftUI

struct NeonDownloadButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x313536), height: 260) { NeonDownloadButton(autoPlay: true) }
    }
}

#Preview {
    NeonDownloadButtonDemo()
}
