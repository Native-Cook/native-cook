import SwiftUI

/// The welcome screen alone, watering, growing and letting its flower fall by itself.
struct SeedSproutWelcomeDemo: View {
    var body: some View {
        SeedSproutWelcome(autoPlay: true)
            .background(SeedSproutWelcomeColors.root.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SeedSproutWelcomeDemo()
}
