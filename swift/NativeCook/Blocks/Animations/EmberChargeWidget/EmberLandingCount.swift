import SwiftUI

/// A fresh native numeric transition for one endpoint landing; never reused for a running count.
struct EmberLandingCount: View {
  let from: Int, to: Int

  @State private var value: Int

  init(from: Int, to: Int) {
    self.from = from
    self.to = to
    _value = State(initialValue: from)
  }

  var body: some View {
    Text(EmberL10n.number(value))
      .contentTransition(.numericText(value: Double(value)))
      .task {
        // Wait for two actual display frames, not a nominal 33 ms timer.
        await EmberLandingFrames.wait()
        guard !Task.isCancelled else { return }
        withAnimation(
          .spring(
            duration: EmberMotionConstants.count.duration, bounce: EmberMotionConstants.count.bounce
          )
        ) { value = to }
      }
  }
}
