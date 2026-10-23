import SwiftUI

/// The logbook on the whole screen, unfolding and folding sites by itself.
struct OrigamiFoldListDemo: View {
    var body: some View {
        OrigamiFoldList(autoPlay: true)
            .background(Color(hex: 0x071519).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    OrigamiFoldListDemo()
}
