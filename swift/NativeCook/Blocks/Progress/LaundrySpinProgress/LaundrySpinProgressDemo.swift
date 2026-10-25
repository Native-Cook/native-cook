import SwiftUI

/// The washer fills the whole screen; a whole cycle runs by itself.
struct LaundrySpinProgressDemo: View {
    var body: some View {
        LaundrySpinProgress(autoPlay: true)
            .background(LaundrySpinProgressStyle.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    LaundrySpinProgressDemo()
}
