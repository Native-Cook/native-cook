import SwiftUI

/// The block fills the screen, so it is shown without a stage.
struct TaskToastConfettiDemo: View {
    var body: some View {
        TaskToastConfetti(autoPlay: true)
    }
}

#Preview {
    TaskToastConfettiDemo()
}
