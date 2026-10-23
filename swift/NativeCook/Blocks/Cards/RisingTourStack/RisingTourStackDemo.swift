import SwiftUI

struct RisingTourStackDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xebebeb), height: 680) { RisingTourStack(autoPlay: true) }
    }
}

#Preview {
    RisingTourStackDemo()
}
