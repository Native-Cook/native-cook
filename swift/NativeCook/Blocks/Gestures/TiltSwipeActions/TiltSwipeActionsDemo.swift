import SwiftUI

struct TiltSwipeActionsDemo: View {
    var body: some View {
        TiltSwipeActions(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    TiltSwipeActionsDemo()
}
