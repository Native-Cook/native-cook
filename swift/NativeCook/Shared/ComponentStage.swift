import SwiftUI

/// One component alone on its backdrop, with nothing else on screen — made for screen recording.
struct ComponentStage<Content: View>: View {
    /// Backdrop colour the component was designed against.
    let background: Color
    /// Height in points of the box the component is laid out in; taller for a large glow.
    let height: CGFloat
    let content: Content

    init(background: Color, height: CGFloat = 220, @ViewBuilder content: () -> Content) {
        self.background = background
        self.height = height
        self.content = content()
    }

    var body: some View {
        ZStack {
            background.ignoresSafeArea()
            // The box the component was designed in, so it lays out the same wherever it is shown.
            content
                .frame(maxWidth: .infinity)
                .frame(height: height)
        }
        .statusBarHidden()
        // The components were built in an app that always ran in the dark scheme.
        .environment(\.colorScheme, .dark)
        .preferredColorScheme(.dark)
    }
}
