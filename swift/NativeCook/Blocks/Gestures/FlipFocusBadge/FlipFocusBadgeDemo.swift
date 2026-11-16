import SwiftUI

struct FlipFocusBadgeDemo: View {
    static let loop = 8.1

    var body: some View {
        FlipFocusBadge(autoPlay: true)
    }
}

#Preview {
    FlipFocusBadgeDemo()
}
