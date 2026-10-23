import SwiftUI

/// Records the eight-second autoplay loop after the local preview cache is ready.
struct LumaRunStudioDemo: View {
    var body: some View { LumaRunStudio(autoPlay: true) }
}

#Preview { LumaRunStudioDemo() }
