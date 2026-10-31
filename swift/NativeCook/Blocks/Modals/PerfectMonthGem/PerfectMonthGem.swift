import SwiftUI

struct PerfectMonthGem: View {
    static let loop = PerfectMonthGemMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = PerfectMonthGemClock()
    private let resources = PerfectMonthGemResources.shared
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "perfectMonthGemTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    private func configure() { clock.configure(autoPlay, reduced, pinned != nil, scenePhase == .active) }
    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top
            let bottom = proxy.safeAreaInsets.bottom
            let w = proxy.size.width
            let h = proxy.size.height + top + bottom
            let cy = top + 196
            TimelineView(.animation(paused: pinned != nil || scenePhase != .active || !clock.running)) { timeline in
                let frame = clock.sample(timeline.date, pinned, autoPlay, reduced)
                PerfectMonthGemScene(frame: frame, size: CGSize(width: w, height: h), cy: cy, bottom: bottom, pinned: pinned != nil, clock: clock)
            }
            .frame(width: w, height: h, alignment: .topLeading)
            .offset(y: -top)
        }
        .background(PerfectMonthGemArt.color(0xf2f2f4).ignoresSafeArea())
        .preferredColorScheme(.light)
        .persistentSystemOverlays(.hidden)
        .onAppear { configure() }
        .onDisappear { clock.configure(autoPlay, reduced, pinned != nil, false) }
        .onChange(of: scenePhase) { _, _ in configure() }
        .onChange(of: reduced) { _, _ in configure() }
        .onChange(of: autoPlay) { _, _ in configure() }
    }
}

struct PerfectMonthGemScene: View {
    typealias M = PerfectMonthGemMotion
    typealias A = PerfectMonthGemArt
    var frame: M.Frame
    var size: CGSize
    var cy: Double
    var bottom: Double
    var pinned: Bool
    var clock: PerfectMonthGemClock
    private func label(_ text: String, _ font: String, _ size: Double, _ color: Int) -> Text {
        Text(text).font(.custom(font, fixedSize: size)).foregroundColor(A.color(color))
    }
    var body: some View {
        let w = size.width
        let cal = 1 - M.smooth(frame.p / 0.35)
        let badge = M.smooth((frame.p - 0.78) / 0.22)
        let rest = M.smooth((frame.p - 0.85) / 0.15)
        ZStack(alignment: .topLeading) {
            A.color(0xf2f2f4)
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in A().draw(context, size, cy, frame) }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            label("October · 30 of 31 days on budget", "Inter-Medium", 13, 0x5b5866)
                .frame(width: w, alignment: .top)
                .opacity(cal).offset(y: cy + 84 + (1 - cal) * -6)
                .allowsHitTesting(false)
            label("Perfect Month", "Inter-Medium", 13, 0x2b2735)
                .frame(width: w, alignment: .top)
                .opacity(badge).offset(y: cy + 84 + (1 - badge) * 8)
                .allowsHitTesting(false)
            VStack(spacing: 12) {
                VStack(spacing: 0) {
                    ForEach(["New", "Badge unlocked!"], id: \.self) { line in
                        label(line, "InterDisplay-Bold", 27, 0x141218)
                            .tracking(-0.4).fixedSize()
                            .frame(width: w - 56, height: 32)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("New\nBadge unlocked!")
                .accessibilityAddTraits(.isHeader)
                VStack(spacing: 0) {
                    ForEach(PerfectMonthGemResources.shared.lines("You stayed within your budget. Keep up the great work!", "Inter24pt-Regular", 15, w - 56), id: \.self) { line in
                        label(line, "Inter24pt-Regular", 15, 0x6d6a76)
                            .fixedSize().frame(width: w - 56, height: 21)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("You stayed within your budget. Keep up the great work!")
            }
            .frame(width: w - 56, alignment: .top)
            .opacity(rest).offset(x: 28, y: cy + 150 + (1 - rest) * 10)
            .allowsHitTesting(false)
            rating.frame(width: w - 36, height: 70).offset(x: 18, y: size.height * 0.645)
            VStack(spacing: 6) {
                Button { clock.buzz(1) } label: {
                    label("View Badge", "Inter-SemiBold", 16, 0xffffff)
                        .offset(y: -1.0 / 3)
                        .frame(maxWidth: .infinity).frame(height: 54)
                        .background(A.color(0x1b1a1f), in: Capsule())
                }.accessibilityLabel("View badge")
                Button { clock.buzz(0) } label: {
                    label("Go to Home", "Inter-SemiBold", 15, 0x1b1a1f)
                        .offset(x: -1.0 / 3, y: -1.0 / 3)
                        .frame(maxWidth: .infinity).frame(height: 48)
                }.accessibilityLabel("Go to home")
            }
            .buttonStyle(PerfectMonthGemButtonStyle())
            .frame(width: w - 36).offset(x: 18, y: size.height - bottom - 18 - 108)
            Group {
                if !pinned {
                    PerfectMonthGemTouch { state, translation, velocity in clock.touch(state, translation, velocity) }
                } else { Color.clear }
            }
            .frame(width: 240, height: 220).contentShape(Rectangle())
            .accessibilityElement(children: .ignore)
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel(frame.p > 0.5 ? "Perfect Month badge, made of your October. Double-tap to see the days." : "October: 30 of 31 days on budget. Double-tap to forge the badge.")
            .accessibilityAction { clock.accessibilityToggle() }
            .offset(x: w / 2 - 120, y: cy - 110)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(!pinned)
        .dynamicTypeSize(.medium)
    }
    private var rating: some View {
        let lines = PerfectMonthGemResources.shared.lines("Are you loving your experience with us so far?", "Inter-SemiBold", 14, size.width - 106)
        return HStack(spacing: 8) {
            ZStack {
                Circle().fill(A.color(0xf6f6f8))
                Text("✦").font(.system(size: 28)).foregroundStyle(A.color(0x141218)).offset(x: 9 - 1.0 / 3, y: -1)
            }.frame(width: 70, height: 70).padding(.leading, -22)
            VStack(alignment: .leading, spacing: 4) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(lines, id: \.self) { line in
                        label(line, "Inter-SemiBold", 14, 0x141218)
                            .fixedSize().frame(height: 19)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Are you loving your experience with us so far?")
                label("Give us a rating", "Inter-Medium", 12.5, 0x4a4652)
                    // RN draws the Inter underline one point thick above SwiftUI's default.
                    .overlay(alignment: .bottomLeading) {
                        Rectangle().fill(A.color(0x4a4652)).frame(height: 1).offset(y: -1.4)
                    }
                    .offset(y: 1.0 / 3)
                    .accessibilityAddTraits(.isLink)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(.trailing, 14)
        }
        .background(A.color(0xe7e7ea), in: RoundedRectangle(cornerRadius: 16))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
struct PerfectMonthGemButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1) }
}
