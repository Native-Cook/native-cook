import SwiftUI

/// The original app's root: `TimeTargetContentView` in the dark scheme, as its `App` showed it.
///
/// The original app's deployment target was iOS 18 (the picker is built on `ScrollPosition` and
/// `onScrollGeometryChange`). The app this block is shown in also runs on iOS 17, where the block
/// can only say so.
struct TimeTargetDialDemo: View {
    var body: some View {
        if #available(iOS 18.0, *) {
            TimeTargetContentView()
                .preferredColorScheme(.dark)
        } else {
            Text("Time Target Dial needs iOS 18 or later.")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black.ignoresSafeArea())
                .preferredColorScheme(.dark)
        }
    }
}

#Preview {
    TimeTargetDialDemo()
}
