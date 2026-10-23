import SwiftUI

/// The original app's root: the view its `App` put in the `WindowGroup`.
struct HeartLikeBurstDemo: View {
    var body: some View {
        ZStack {
            Color.black
            XHeartAnimation(initialLiked: false) { liked in
                print("Liked: \(liked)")
            }
            .frame(width: 58, height: 58)
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }
}

#Preview {
    HeartLikeBurstDemo()
}
