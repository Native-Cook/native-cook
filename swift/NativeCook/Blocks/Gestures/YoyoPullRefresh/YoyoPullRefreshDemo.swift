import SwiftUI

struct YoyoPullRefreshDemo: View {
    var body: some View {
        YoyoPullRefresh(autoPlay: true)
            .ignoresSafeArea()
            .preferredColorScheme(.dark)
            .statusBarHidden(false)
    }
}

#Preview {
    YoyoPullRefreshDemo()
}
