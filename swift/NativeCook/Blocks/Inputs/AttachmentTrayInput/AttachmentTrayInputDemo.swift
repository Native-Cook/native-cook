import SwiftUI

struct AttachmentTrayInputDemo: View {
    var body: some View {
        ComponentStage(background: .white, height: 400) { AttachmentTrayInput(autoPlay: true) }
    }
}

#Preview {
    AttachmentTrayInputDemo()
}
