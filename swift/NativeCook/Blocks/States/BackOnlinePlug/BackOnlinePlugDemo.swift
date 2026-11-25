import SwiftUI

struct BackOnlinePlugDemo: View {
    var body: some View {
        BackOnlinePlug(autoPlay: true)
            .ignoresSafeArea()
            .preferredColorScheme(.dark)
    }
}

#Preview {
    BackOnlinePlugDemo()
}
