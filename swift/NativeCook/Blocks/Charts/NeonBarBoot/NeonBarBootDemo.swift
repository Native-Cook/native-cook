import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct NeonBarBootDemo: View {
    var body: some View { NeonBarBoot(autoPlay: true) }
}

#Preview { NeonBarBootDemo() }
