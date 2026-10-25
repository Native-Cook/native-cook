import SwiftUI

/// The supper slot machine fills the screen, spinning for dinner by itself.
struct SlotReelListDemo: View {
    var body: some View {
        SlotReelList(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    SlotReelListDemo()
}
