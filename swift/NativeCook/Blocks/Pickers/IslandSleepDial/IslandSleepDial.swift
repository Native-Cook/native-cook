import SwiftUI

struct IslandSleepDial: View {
    static let loop = IslandSleepDialMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = IslandSleepDialClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "islandSleepDialTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { proxy in
                let art = IslandSleepDialArt.cached(proxy.size)
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (reduced && clock.canPauseReduced))) { timeline in
                    let f = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
                    ZStack(alignment: .topLeading) {
                        Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
                            art.draw(context, f, reduced: reduced, top: safe.safeAreaInsets.top, bottom: safe.safeAreaInsets.bottom)
                        }
                        .accessibilityHidden(true)
                        VStack(spacing: 0) {
                            Color.clear.frame(height: 17).accessibilityLabel("Tonight")
                            Color.clear.frame(height: 53).accessibilityLabel("Sleep length")
                                .accessibilityValue(IslandSleepDialMotion.duration(f.h))
                            Color.clear.frame(height: 17)
                                .accessibilityLabel("Bedtime 11:00 PM  ·  Wake \(IslandSleepDialMotion.wake(f.h))")
                        }
                        .frame(width: art.w - 48, height: 87)
                        .position(x: art.cx, y: safe.safeAreaInsets.top + 12 + 43.5)
                        .allowsHitTesting(false)
                        IslandSleepDialTouchView(top: art.cy - art.r - 40, enabled: pinned == nil,
                            move: { clock.pan($0, center: CGPoint(x: art.cx, y: art.cy)) },
                            finalize: { clock.endPan() })
                            .frame(width: art.w, height: art.r * 2 + 80)
                            .position(x: art.cx, y: art.cy)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("Sleep length dial. Drag the moon around the island")
                            .accessibilityValue(IslandSleepDialMotion.duration(f.h))
                            .accessibilityAdjustableAction { direction in
                                if pinned == nil { clock.adjust(direction == .increment ? 0.25 : -0.25) }
                            }
                        Color.clear.frame(width: art.w, height: 34)
                            .position(x: art.cx, y: art.h - safe.safeAreaInsets.bottom - 18 - 56 - 12 - 17)
                            .accessibilityLabel("Rain on a tin roof")
                            .allowsHitTesting(false)
                        if f.cloud > 0 {
                            Color.clear.frame(width: art.w, height: 34)
                                .position(x: art.cx, y: art.cy - 3 + (1 - f.cloud) * 10)
                                .accessibilityLabel("Sleep well")
                                .allowsHitTesting(false)
                        }
                        Button { if pinned == nil { clock.setAlarm(resuming: reduced && clock.canPauseReduced) } } label: {
                            Canvas { context, _ in art.drawButton(context, art.w - 40, asleep: f.cloud > 0.5) }
                                .frame(width: art.w - 40, height: 56)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(IslandSleepDialButtonStyle())
                        .position(x: art.cx, y: art.h - safe.safeAreaInsets.bottom - 18 - 28)
                        .accessibilityLabel(f.cloud > 0.5 ? "Alarm set" : "Set alarm")
                    }
                    .coordinateSpace(name: "IslandSleepDialScreen")
                    .frame(width: art.w, height: art.h, alignment: .topLeading)
                }
            }.ignoresSafeArea()
        }
        .background(IslandSleepDialArt.color(0x141A3C))
        .preferredColorScheme(.dark)
        .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    }
}

struct IslandSleepDialButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.96 : 1)
    }
}
