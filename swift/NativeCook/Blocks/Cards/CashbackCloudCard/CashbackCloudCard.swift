import SwiftUI

struct CashbackCloudCard: View {
    static let loop = 9.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = CashbackCloudCardClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "cashbackCloudCardTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let top = safe.safeAreaInsets.top
                ZStack(alignment: .topLeading) {
                CashbackCloudCardPage(top: top)
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active || clock.resting || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                    let frame = clock.sample(timeline.date, auto: autoPlay && !reduced, pinned: pinned)
                    CashbackCloudCardArt(frame: frame, top: top, reduced: reduced)
                        .overlay(alignment: .topLeading) {
                            CashbackCloudCardTouchView(
                                down: { if pinned == nil { clock.touch($0) } })
                                .frame(width: full.size.width - 72, height: 230)
                                .allowsHitTesting(pinned == nil)
                                .offset(x: 36, y: top + 140)
                        }
                }
                }
            }.ignoresSafeArea()
        }
        .background(CashbackCloudCardResources.color(0xEEF3FF))
        .preferredColorScheme(.light)
        .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    }
}
