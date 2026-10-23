import SwiftUI

struct RecordButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x050505), height: 280) { RecordButton(autoPlay: true) }
    }
}

#Preview {
    RecordButtonDemo()
}
