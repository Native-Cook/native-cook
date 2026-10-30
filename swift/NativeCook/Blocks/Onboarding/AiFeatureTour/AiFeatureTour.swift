import SwiftUI

struct AiFeatureTour: View {
    static let loop = 9.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = AiFeatureTourClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "featureTourTime") {
            return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
                let frame = clock.sample(timeline.date, auto: autoPlay && !reduced, pinned: pinned)
                Canvas(opaque: false, colorMode: .nonLinear) { context, size in
                    AiFeatureTourArt.draw(AiFeatureTourCanvas(context), size.width, size.height, frame)
                }
                .overlay {
                    AiFeatureTourTouchView(scale: proxy.size.width / 402 * AiFeatureTourTheme.S,
                                          enabled: pinned == nil, onBegin: clock.begin,
                                          onFinalize: { clock.end($0, reduced: reduced) })
                }
                .accessibilityElement(children: .ignore)
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Halden Intelligence can: create check ins, understand your trends, create workout templates, log food, create a training plan. Continue.")
                .accessibilityHint("Continues.")
                .accessibilityAction { clock.accessibilityContinue() }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .ignoresSafeArea()
        .background(Color(hex: 0xF8F8FA))
        .preferredColorScheme(.light)
        .onChange(of: scenePhase) { _, _ in clock.resetDate() }
        .onChange(of: autoPlay) { _, _ in clock.reset() }
        .onChange(of: reduced) { _, _ in clock.reset() }
        .onDisappear { clock.stop() }
    }
}
