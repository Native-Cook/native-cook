import MetalKit
import SwiftUI

struct RainWindowForecastSurface: UIViewRepresentable {
    var sample: RainWindowForecastClock.Sample
    var top: CGFloat
    var bottom: CGFloat
    func makeUIView(context: Context) -> RainWindowForecastRenderView {
        RainWindowForecastRenderView()
    }
    func updateUIView(_ view: RainWindowForecastRenderView, context: Context) {
        view.update(sample, top, bottom)
    }
}
