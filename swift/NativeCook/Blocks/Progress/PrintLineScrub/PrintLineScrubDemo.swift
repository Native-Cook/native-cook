import SwiftUI

struct PrintLineScrubDemo: View {
    static let loop = 7.6

    var body: some View {
        PrintLineScrub(autoPlay: true)
    }
}

#Preview {
    PrintLineScrubDemo()
}
