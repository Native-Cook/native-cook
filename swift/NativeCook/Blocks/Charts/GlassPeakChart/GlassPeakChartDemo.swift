import SwiftUI

struct GlassPeakChartDemo: View {
  var body: some View {
    GlassPeakChart(autoPlay: true)
      .ignoresSafeArea()
      .statusBarHidden()
      .persistentSystemOverlays(.hidden)
  }
}

struct GlassPeakChart: UIViewRepresentable {
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduceMotion

  func makeUIView(context: Context) -> GlassPeakChartView {
    GlassPeakChartView(autoPlay: autoPlay, reduced: reduceMotion)
  }

  func updateUIView(_ view: GlassPeakChartView, context: Context) {
    view.setReducedMotion(reduceMotion)
  }

  static func dismantleUIView(_ view: GlassPeakChartView, coordinator: ()) {
    view.stop()
  }
}
