import SwiftUI

struct KeycapMoodInputDemo: View {
    var body: some View {
        KeycapMoodInput(autoPlay: true)
            .background(Color.black.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    KeycapMoodInputDemo()
}
