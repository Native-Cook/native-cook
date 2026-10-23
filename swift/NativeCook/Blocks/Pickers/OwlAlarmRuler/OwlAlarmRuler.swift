import SwiftUI

struct OwlAlarmRuler: View {
    static let loop: Double = 10
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var stopped = false

    var body: some View {
        GeometryReader { safe in
            TimelineView(.animation(paused: stopped || scenePhase != .active)) { timeline in
                OwlAlarmRulerSurface(
                    date: timeline.date,
                    top: safe.safeAreaInsets.top,
                    bottom: safe.safeAreaInsets.bottom,
                    autoPlay: autoPlay,
                    still: reducedMotion,
                    active: scenePhase == .active,
                    onActivity: { moving in stopped = !moving }
                )
                .frame(
                    width: safe.size.width,
                    height: safe.size.height + safe.safeAreaInsets.top + safe.safeAreaInsets.bottom,
                    alignment: .topLeading
                )
                .offset(y: -safe.safeAreaInsets.top)
            }
        }
        .background(Color(red: 237 / 255, green: 237 / 255, blue: 237 / 255).ignoresSafeArea())
        .preferredColorScheme(.light)
    }
}
