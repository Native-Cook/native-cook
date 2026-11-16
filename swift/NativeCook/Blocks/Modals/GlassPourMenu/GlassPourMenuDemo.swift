import SwiftUI

struct GlassPourMenuDemo: View {
    static let loop = 7.2
    var body: some View {
        GlassPourMenu(autoPlay: true)
    }
}

#Preview {
    GlassPourMenuDemo()
}
