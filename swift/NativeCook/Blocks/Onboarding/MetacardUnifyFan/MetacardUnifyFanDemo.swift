import SwiftUI

struct MetacardUnifyFanDemo: View {
    var body: some View {
        MetacardUnifyFan(autoPlay: true)
            .ignoresSafeArea()
            .statusBarHidden()
            .persistentSystemOverlays(.hidden)
    }
}

#Preview {
    MetacardUnifyFanDemo()
}
