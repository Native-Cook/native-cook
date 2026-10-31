import SwiftUI

struct SeasonTierMedals: View {
    static let loop = SeasonTierMedalsMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock = SeasonTierMedalsClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "seasonTierMedalsTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { geometry in
                let size = geometry.size
                let top = safe.safeAreaInsets.top
                let my = top + 248
                let rc = min(84, size.width * 0.21)
                let art = clock.resources(size, top)
                TimelineView(.animation(paused: pinned != nil || phase != .active || ((!autoPlay || reduced) && !clock.awake))) { timeline in
                    let frame = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
                    ZStack(alignment: .topLeading) {
                        Canvas(colorMode: .nonLinear) { context, _ in SeasonTierMedalsArt(art: art).background(context, frame) }
                        ForEach(frame.order, id: \.self) { index in
                            let m = frame.medal(index, size.width, my)
                            if m.visible { medal(m) }
                        }
                        Canvas(colorMode: .nonLinear) { context, _ in SeasonTierMedalsArt(art: art).foreground(context, frame) }
                            .allowsHitTesting(false).accessibilityHidden(true)
                        SeasonTierMedalsClaim(image: art.claim) { clock.buzz(1) }
                            .frame(width: size.width - 64, height: 48)
                            .offset(x: 32, y: art.claimY)
                            .allowsHitTesting(pinned == nil)
                        accessibility(size.width, top, frame.current)
                        if pinned == nil {
                            SeasonTierMedalsTouchView { state, point, translation, velocity in
                                clock.touch(state, point, translation, velocity, size.width, my, reduced)
                            }
                            .frame(width: size.width, height: rc * 2.6)
                            .offset(y: my - rc * 1.3)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("Season level")
                            .accessibilityValue("\(SeasonTierMedalsTheme.names[frame.current].lowercased()), level \(frame.current + 1) of 3")
                            .accessibilityAdjustableAction { direction in
                                clock.step(direction == .increment ? 1 : -1, reduced)
                            }
                        }
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .clipped()
                }
            }.ignoresSafeArea()
        }
        .background(SeasonTierMedalsTheme.color(0x121214))
        .preferredColorScheme(.dark)
        .onAppear { clock.active(phase == .active, reduced, pinned != nil) }
        .onDisappear { clock.active(false, reduced, pinned != nil) }
        .onChange(of: phase) { _, value in clock.active(value == .active, reduced, pinned != nil) }
        .onChange(of: reduced) { _, value in clock.active(phase == .active, value, pinned != nil) }
    }
    private func medal(_ m: SeasonTierMedalsMotion.Medal) -> some View {
        let metal = SeasonTierMedalsTheme.metals[m.index]
        let deep = SeasonTierMedalsTheme.deeps[m.index]
        return Rectangle().fill(.white)
            .frame(width: m.radius * 2.5, height: m.radius * 2.5)
            .colorEffect(ShaderLibrary.seasonTierMedalsMedal(
                .float2(Float(m.radius * 1.25), Float(m.radius * 1.25)), .float(Float(m.radius)),
                .float(Float(m.yaw)), .float(0.1), .float(0), .float(Float(m.index + 1)),
                .float3(metal.x, metal.y, metal.z), .float3(deep.x, deep.y, deep.z),
                .float(m.index == 1 ? 1 : 0), .float(Float(m.sweep)), .float(Float(m.glow))))
            .offset(x: m.x - m.radius * 1.25, y: m.y - m.radius * 1.25)
            .allowsHitTesting(false).accessibilityHidden(true)
    }
    private func accessibility(_ w: Double, _ top: Double, _ current: Int) -> some View {
        let h = SeasonTierMedalsTheme.self
        let k = top + 62
        return ZStack(alignment: .topLeading) {
            accessibilityLabel("Back", 0, top - 6, 60, 60).accessibilityAddTraits(.isButton)
            accessibilityLabel("Season perks", 60, top + 2, w - 120, 44)
            accessibilityLabel("How levels work", w - 60, top - 6, 60, 60).accessibilityAddTraits(.isButton)
            accessibilityLabel(h.kickers[current], 0, k, w, 20)
            accessibilityLabel(h.names[current], 0, k + 22, w, 56)
            accessibilityLabel(h.titles[current], 48, k + 314, w - 96, 26)
            accessibilityLabel(h.nums[current], w - 176, k + 354, 100, 24)
            accessibilityLabel(h.feet[current], 48, k + 390, w - 96, 20)
            accessibilityLabel("Claim your rewards", 0, k + 470, w, 32)
            accessibilityLabel("7% back on clothing and shoes", 90, k + 532, w - 122, 22)
            accessibilityLabel("Paid as bonus points with a Season card", 90, k + 556, w - 122, 38)
            accessibilityLabel("×2", 32, k + 680, 44, 44)
            accessibilityLabel("Double points at cafés", 90, k + 680, w - 122, 22)
            accessibilityLabel("Every weekend until the season ends", 90, k + 704, w - 122, 38)
        }.allowsHitTesting(false)
    }
    private func accessibilityLabel(_ value: String, _ x: Double, _ y: Double, _ w: Double, _ h: Double) -> some View {
        Color.clear.frame(width: w, height: h).offset(x: x, y: y)
            .accessibilityElement().accessibilityLabel(value)
    }
}
