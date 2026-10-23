import SwiftUI

struct GrowthOdometerTimelineDemo: View {
    var body: some View {
        GrowthOdometerTimeline(autoPlay: true)
    }
}

/// A mechanical month wheel, odometer and halo, with an optional twelve-second demonstration.
struct GrowthOdometerTimeline: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GrowthOdometerTimelineSurface(autoPlay: autoPlay, reduceMotion: reduceMotion)
            .ignoresSafeArea()
            .statusBarHidden()
            .background(.black)
    }
}

private struct GrowthOdometerTimelineSurface: UIViewRepresentable {
    let autoPlay: Bool
    let reduceMotion: Bool

    func makeUIView(context: Context) -> GrowthOdometerTimelineView {
        GrowthOdometerTimelineView(autoPlay: autoPlay, reduced: reduceMotion)
    }

    func updateUIView(_ view: GrowthOdometerTimelineView, context: Context) {
        view.configure(autoPlay: autoPlay, reduced: reduceMotion)
    }

    static func dismantleUIView(_ view: GrowthOdometerTimelineView, coordinator: ()) {
        view.stop()
    }
}

#Preview {
    GrowthOdometerTimelineDemo()
}
