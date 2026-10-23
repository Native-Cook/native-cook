import SwiftUI

struct JellyAttachCapsuleDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xeeeafe), height: 240) { JellyAttachCapsule(autoPlay: true) }
    }
}

#Preview {
    JellyAttachCapsuleDemo()
}
