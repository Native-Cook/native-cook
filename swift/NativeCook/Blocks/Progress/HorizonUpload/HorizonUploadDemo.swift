import SwiftUI

/// Full-screen SwiftUI entry point. UIKit/Metal own presentation and touch clocks;
/// accessibility preferences and Dynamic Type come from the enclosing environment.
struct HorizonUploadDemo: View {
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.accessibilityReduceTransparency) private var opaque
  @ScaledMetric(relativeTo: .body) private var fontScale: Double = 1
  var body: some View {
    HorizonScreen(reduced: reduced, opaque: opaque, fontScale: min(1.3, fontScale))
      .ignoresSafeArea().preferredColorScheme(.dark).persistentSystemOverlays(.hidden)
  }
}

#Preview { HorizonUploadDemo() }
