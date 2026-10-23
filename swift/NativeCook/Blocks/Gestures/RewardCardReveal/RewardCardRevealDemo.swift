import SwiftUI

/// Root router mirroring the expo-router stack (`index` + `card/[id]`) with a 240ms fade.
///
/// This is the original app's `RootView`, under the block's name.
struct RewardCardRevealDemo: View {
    @State private var selectedCardId: String?

    private let transition = Animation.easeInOut(duration: 0.24)

    var body: some View {
        GeometryReader { geo in
            // Read the window's insets before extending the screens under the system bars.
            let insets = geo.safeAreaInsets
            let screenHeight = geo.size.height + insets.top + insets.bottom

            ZStack {
                CardGalleryScreen(safeAreaInsets: insets) { id in
                    withAnimation(transition) {
                        selectedCardId = id
                    }
                }
                .allowsHitTesting(selectedCardId == nil)
                .accessibilityHidden(selectedCardId != nil)

                if let id = selectedCardId {
                    CardDetailScreen(cardId: id, safeAreaInsets: insets) {
                        withAnimation(transition) {
                            selectedCardId = nil
                        }
                    }
                    .id(id)
                    .transition(.opacity)
                    .zIndex(1)
                }
            }
            .frame(width: geo.size.width, height: screenHeight)
            .offset(y: -insets.top)
        }
        .background(AppColors.background.ignoresSafeArea())
        // The gallery this block is shown in asks for the dark scheme at its root, and an outer
        // preference wins over this one, so the block also sets the light scheme on its own views.
        .environment(\.colorScheme, .light)
        .preferredColorScheme(.light)
    }
}

#Preview {
    RewardCardRevealDemo()
}
