import SwiftUI

/// The original app's root: `PurchaseScreen` in the light scheme, as its `App` showed it.
struct FabricPaywallSheetDemo: View {
    var body: some View {
        PurchaseScreen()
            // The gallery this block is shown in asks for the dark scheme at its root, and an outer
            // preference wins over this one, so the block also sets the light scheme on its own views.
            .environment(\.colorScheme, .light)
            .preferredColorScheme(.light)
    }
}

#Preview {
    FabricPaywallSheetDemo()
}
