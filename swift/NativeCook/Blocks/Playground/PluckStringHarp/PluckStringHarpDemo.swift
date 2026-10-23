import SwiftUI

/// The harp alone in its dark room, playing by itself until a finger joins in.
struct PluckStringHarpDemo: View {
    var body: some View {
        PluckStringHarp(autoPlay: true)
            .background(Color(hex: 0x050403).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PluckStringHarpDemo()
}
