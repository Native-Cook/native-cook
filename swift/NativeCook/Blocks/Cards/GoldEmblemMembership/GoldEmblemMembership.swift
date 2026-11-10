import SwiftUI

struct GoldEmblemMembership: View {
    static let loop = GoldEmblemMembershipMotion.LOOP
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var scale
    @StateObject private var clock = GoldEmblemMembershipClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "goldEmblemMembershipTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let l = GoldEmblemMembershipTheme.Layout(full.size, safe.safeAreaInsets.top, safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active || !clock.running)) { timeline in
                    // The debug pose freezes the shader clock too. RN currently leaves its GPU clock running.
                    let frame = clock.sample(timeline.date, pinned)
                    GoldEmblemMembershipScene(layout: l, frame: frame, time: pinned ?? clock.now,
                                              scale: scale, reduced: reduced, pinned: pinned != nil, clock: clock)
                }
                .frame(width: full.size.width, height: full.size.height, alignment: .topLeading)
            }.ignoresSafeArea()
        }
        .background(Color.white)
        .preferredColorScheme(.dark)
        .onAppear { clock.reset(autoPlay, reduced) }
        .onChange(of: autoPlay) { _, _ in clock.reset(autoPlay, reduced) }
        .onChange(of: reduced) { _, _ in clock.reset(autoPlay, reduced) }
        .onChange(of: scenePhase) { _, _ in clock.active() }
    }
}
