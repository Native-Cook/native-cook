import SwiftUI

/// The original app's root: `ExpandingBottomTabsContentView` in the dark scheme, as its `App`
/// showed it.
struct ExpandingBottomTabsDemo: View {
    var body: some View {
        ExpandingBottomTabsContentView()
            .preferredColorScheme(.dark)
    }
}

#Preview {
    ExpandingBottomTabsDemo()
}
