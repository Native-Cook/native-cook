import CoreText
import SwiftUI
import UIKit

// Sprinkle's settings screen, ported from the React Native reference. A candy ring rolls
// through an iridescent glass switch, cross-fading between a donut and a lime gummy.
// Loop (6 s): on/rest 0–1.2; roll left 1.2–2; landing 2–2.6; off/rest until 4.2;
// roll right 4.2–5; landing 5–5.6; on/rest to 6, identical to 0.
// Tap toggles; drag (6 pt threshold) follows the finger, then snaps over 0.8 s.
// After that snap and a 2 s hold, autoplay resumes at the selected side's rest.
struct CandyKnobToggle: View {
    static let loop: Double = 6

    var brand = "Sprinkle"
    var title = "Treat\nmode"
    var autoPlay = false

    private static let trackWidth = 300.0
    private static let trackHeight = 128.0
    private static let knobSize = 112.0
    private static let travel = 172.0
    private static let snap = 0.8
    private static let hold = 2.0

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var origin = Date()
    @State private var visible = false
    @State private var pausedAt: Date?
    @State private var manual = false
    @State private var touching = false
    @State private var tracking = false
    @State private var touchBegan = Date()
    @State private var grab = 1.0
    @State private var fingerPosition = 1.0
    @State private var from = 1.0
    @State private var target = 1.0
    @State private var released = Date()
    @State private var interaction = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @GestureState private var gestureActive = false

    private struct Knob {
        var position: Double
        var moving: Double = 0
        var squash: Double = 0
    }

    private static let fontRegistered: Void = {
        if let url = Bundle.main.url(forResource: "CandyKnobToggle-BricolageHeadline", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static func headline(_ size: CGFloat) -> Font {
        _ = fontRegistered
        return .custom("BricolageHeadline-Regular", fixedSize: size)
    }

    // Resource decoding is shared by every frame of every loop.
    private static let donut = image("knob-donut")
    private static let gummy = image("knob-gummy")

    private static func image(_ name: String) -> Image? {
        guard let url = Bundle.main.url(forResource: "CandyKnobToggle-\(name)", withExtension: "png"),
              let source = UIImage(contentsOfFile: url.path) else { return nil }
        return Image(uiImage: source.preparingForDisplay() ?? source)
    }

    private static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    private static func cubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    private static func landing(_ x: Double) -> Double {
        guard x > 0, x <= 0.6 else { return 0 }
        return 0.1 * exp(-8 * x) * sin(18 * x)
    }

    private static func loopKnob(_ time: Double) -> Knob {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        let left = cubic(segment(t, 1.2, 2))
        let right = cubic(segment(t, 4.2, 5))
        let moving = t >= 1.2 && t < 2 ? segment(t, 1.2, 2) : t >= 4.2 && t < 5 ? segment(t, 4.2, 5) : 0
        return Knob(position: 1 - left + right, moving: moving, squash: landing(t - 2) + landing(t - 5))
    }

    private static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: opacity)
    }

    private static func mix(_ a: UInt32, _ b: UInt32, _ position: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let first = Double((a >> shift) & 255)
            let last = Double((b >> shift) & 255)
            return (first + (last - first) * position).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }

    private func knob(at date: Date) -> Knob {
        guard !reduced else { return Knob(position: 1) }
        if touching { return Knob(position: fingerPosition) }
        if manual {
            let since = max(0, date.timeIntervalSince(released))
            if since <= Self.snap + Self.hold {
                let p = Self.clamp(since / Self.snap)
                return Knob(position: from + (target - from) * Self.cubic(p),
                            moving: abs(target - from) > 0.05 && p < 1 ? p : 0,
                            squash: Self.landing(since - Self.snap) * min(1, abs(target - from) * 2))
            }
            guard autoPlay else { return Knob(position: 1) }
            let phase = target > 0.5 ? 0.0 : 2.4
            return Self.loopKnob(since - Self.snap - Self.hold + phase)
        }
        return autoPlay ? Self.loopKnob(date.timeIntervalSince(origin)) : Knob(position: 1)
    }

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: !visible || scene != .active || reduced || touching || (!autoPlay && !manual))) { timeline in
                let state = knob(at: pausedAt ?? timeline.date)
                let ink = Self.mix(0x24420E, 0x5A1233, state.position)
                let x = (size.width - Self.trackWidth) / 2
                let y = size.height * 0.5 - Self.trackHeight / 2
                ZStack(alignment: .topLeading) {
                    Self.mix(0xD7EFB4, 0xF7C9DD, state.position)
                    heading(safe: safe, width: size.width, ink: ink)
                    Canvas { context, _ in
                        drawSwitch(context, state: state, x: x, y: y)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                    Color.clear
                        .frame(width: Self.trackWidth + 20, height: Self.trackHeight + 40)
                        .contentShape(Rectangle())
                        .gesture(drag, including: reduced ? .none : .all)
                        .offset(x: x - 10, y: y - 20)
                        .accessibilityElement()
                        .accessibilityLabel("Treat mode")
                        .accessibilityValue(state.position > 0.5 ? "On" : "Off")
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction {
                            guard !reduced else { return }
                            let current = knob(at: Date()).position
                            release(at: Date(), from: current, to: current > 0.5 ? 0 : 1)
                        }
                    states(position: state.position, ink: ink)
                        .frame(width: size.width - 48, height: 30, alignment: .top)
                        .offset(x: 24, y: y + Self.trackHeight + 34)
                    footer(safe: safe, width: size.width, ink: ink)
                        .frame(width: size.width, height: size.height, alignment: .bottom)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { visible = true; origin = .now; haptic.prepare() }
        .onDisappear { visible = false }
        .onChange(of: reduced) { _, _ in reset() }
        .onChange(of: autoPlay) { _, _ in reset() }
        .onChange(of: scene) { _, phase in
            if phase != .active {
                if pausedAt == nil { pausedAt = .now }
                interaction += 1
            } else if let pause = pausedAt {
                let gap = Date().timeIntervalSince(pause)
                origin = origin.addingTimeInterval(gap)
                released = released.addingTimeInterval(gap)
                pausedAt = nil
                interaction += 1
            }
        }
        .onChange(of: gestureActive) { _, active in
            guard !active else { return }
            Task { @MainActor in
                await Task.yield()
                guard tracking, !gestureActive else { return }
                if touching {
                    release(at: Date(), from: fingerPosition, to: fingerPosition > 0.5 ? 1 : 0)
                }
                tracking = false
            }
        }
        .task(id: interaction) { await resumeLoop() }
    }

    private func heading(safe: EdgeInsets, width: CGFloat, ink: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text(brand).font(Self.headline(22))
                Spacer(minLength: 0)
                Text("Settings").font(.system(size: 14, weight: .semibold)).opacity(0.7)
            }
            .frame(height: 34)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(title.components(separatedBy: "\n").enumerated()), id: \.offset) { _, line in
                    Text(line)
                        .font(Self.headline(64))
                        .tracking(-2)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(height: 64, alignment: .leading)
                }
            }
            // RN's explicit 64 pt line box puts this face's baseline 6.4 pt above
            // SwiftUI's centred natural line box (the font's em line height is 76.8).
            .offset(y: -6.4)
            .padding(.top, 18)
            Text("Let the app show you the sweet stuff first.")
                .font(.system(size: 17))
                .lineSpacing(23 - UIFont.systemFont(ofSize: 17).lineHeight)
                .frame(width: min(280, width - 48), height: 46, alignment: .leading)
                .opacity(0.75)
                .padding(.top, 12)
        }
        .foregroundStyle(ink)
        .frame(width: width - 48, alignment: .leading)
        .offset(x: 24, y: safe.top + 8)
        .allowsHitTesting(false)
    }

    private func states(position: Double, ink: Color) -> some View {
        let on = Self.clamp((position - 0.5) * 3)
        let off = Self.clamp((0.5 - position) * 3)
        return ZStack(alignment: .top) {
            Text("On · sprinkles everywhere")
                .opacity(on)
                .offset(y: 8 * (1 - on))
                .accessibilityHidden(on < 0.5)
            Text("Off · just the gummies")
                .opacity(off)
                .offset(y: 8 * (1 - off))
                .accessibilityHidden(off < 0.5)
        }
        .font(Self.headline(22))
        .foregroundStyle(ink)
        .allowsHitTesting(false)
    }

    private func footer(safe: EdgeInsets, width: CGFloat, ink: Color) -> some View {
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Daily treat reminder").font(.system(size: 17, weight: .bold))
                Text("Every day at 15:30").font(.system(size: 15)).opacity(0.7)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(Self.color(0xFFFFFF, opacity: 0.45), in: RoundedRectangle(cornerRadius: 24))
            Text("You can change this any time.")
                .font(.system(size: 13))
                .opacity(0.6)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, 20)
        .padding(.bottom, safe.bottom + 18)
        .frame(width: width)
        .allowsHitTesting(false)
    }

    private func drawSwitch(_ context: GraphicsContext, state: Knob, x: Double, y: Double) {
        let track = Path(roundedRect: CGRect(x: x, y: y, width: Self.trackWidth, height: Self.trackHeight), cornerRadius: 64)
        solidFill(context, path: Path(roundedRect: CGRect(x: x + 6, y: y + 22, width: 288, height: 128), cornerRadius: 64),
                  radius: 18, shading: .color(Self.color(0x28143C, opacity: 0.18)))
        context.fill(track, with: .color(.white.opacity(0.22)))

        let drift = (0.5 - state.position) * 70
        var inside = context
        inside.clip(to: track)
        for (center, width, alpha) in [(x + 108 + drift, 64.0, 0.55), (x + 186 + drift * 0.6, 34.0, 0.35)] {
            var smear = inside
            smear.opacity = alpha
            smear.translateBy(x: center, y: y + 64)
            smear.rotate(by: .degrees(18))
            solidFill(smear, path: Path(CGRect(x: -width / 2, y: -94, width: width, height: 188)), radius: 14,
                      shading: .linearGradient(Gradient(colors: [Self.color(0xFF8AD8), Self.color(0xFFD48A), Self.color(0x8AF2FF), Self.color(0xB48AFF)]),
                                               startPoint: CGPoint(x: -width / 2, y: 0), endPoint: CGPoint(x: width / 2, y: 0)))
        }

        let centerX = x + 8 + Self.travel * state.position + Self.knobSize / 2
        let centerY = y + 64
        let stretch = 1 + 0.14 * sin(.pi * state.moving) + state.squash
        let scaleY = 1 / sqrt(stretch) - state.squash * 0.5
        let shadowRect = CGRect(x: centerX - Self.knobSize * stretch / 2 + 4,
                                y: centerY + Self.knobSize * 0.28,
                                width: Self.knobSize * stretch - 8, height: 22)
        solidFill(inside, path: Path(ellipseIn: shadowRect), radius: 8,
                  shading: .color(Self.color(0x3C1432, opacity: 0.28)))

        let gummyWeight = 1 - Self.cubic(Self.segment(state.position, 0.33, 0.67))
        for (image, weight) in [(Self.donut, 1 - gummyWeight), (Self.gummy, gummyWeight)] {
            guard let image, weight > 0.001 else { continue }
            var candy = inside
            candy.opacity = weight
            candy.translateBy(x: centerX, y: centerY)
            candy.scaleBy(x: stretch, y: scaleY)
            candy.rotate(by: .radians(state.position * Self.travel / (Self.knobSize / 2)))
            candy.draw(image, in: CGRect(x: -56, y: -56, width: 112, height: 112))
        }

        var sheen = context
        sheen.blendMode = .screen
        sheen.fill(Path(roundedRect: CGRect(x: x + 14, y: y + 6, width: 272, height: 53.76), cornerRadius: 38.4),
                   with: .linearGradient(Gradient(colors: [.white.opacity(0.55), .white.opacity(0)]),
                                         startPoint: CGPoint(x: 0, y: y), endPoint: CGPoint(x: 0, y: y + 64)))
        context.stroke(track,
                       with: .linearGradient(Gradient(colors: [.white.opacity(0.95), Self.color(0xB4E6FF, opacity: 0.7), Self.color(0xFFB4E6, opacity: 0.8), .white.opacity(0.95)]),
                                             startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x + 300, y: y + 128)),
                       lineWidth: 3)
        var lip = Path()
        lip.move(to: CGPoint(x: x + 44, y: y + 119))
        lip.addLine(to: CGPoint(x: x + 256, y: y + 119))
        solidFill(context, path: lip.strokedPath(StrokeStyle(lineWidth: 2.5)), radius: 1.5,
                  shading: .linearGradient(Gradient(colors: [Self.color(0xFF8CDC, opacity: 0), Self.color(0xFF8AD8), Self.color(0x8AF2FF), Self.color(0x8AF2FF, opacity: 0)]),
                                           startPoint: CGPoint(x: x + 40 - drift, y: 0), endPoint: CGPoint(x: x + 260 - drift, y: 0)))
    }

    /// Skia BlurStyle.Solid blurs the alpha mask, keeps its interior, then shades it.
    /// Keeping RGB out of the blur preserves the narrow rainbow bands in the reference.
    private func solidFill(_ context: GraphicsContext, path: Path, radius: Double, shading: GraphicsContext.Shading) {
        var layer = context
        layer.clipToLayer { mask in
            mask.opacity = 1
            mask.blendMode = .normal
            mask.drawLayer { blurred in
                blurred.addFilter(.blur(radius: radius))
                blurred.fill(path, with: .color(.white))
            }
            mask.fill(path, with: .color(.white))
        }
        layer.fill(Path(path.boundingRect.insetBy(dx: -4 * radius, dy: -4 * radius)), with: shading)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduced else { return }
                if !tracking {
                    tracking = true
                    touchBegan = .now
                    grab = knob(at: Date()).position
                }
                if !touching, hypot(value.translation.width, value.translation.height) >= 6 {
                    touching = true
                    manual = true
                    interaction += 1
                }
                if touching {
                    fingerPosition = Self.clamp(grab + value.translation.width / Self.travel)
                }
            }
            .onEnded { value in
                guard !reduced else { return }
                let now = Date()
                if touching {
                    release(at: now, from: fingerPosition, to: fingerPosition > 0.5 ? 1 : 0)
                } else if now.timeIntervalSince(touchBegan) <= 0.5,
                          CGRect(x: 0, y: 0, width: Self.trackWidth + 20, height: Self.trackHeight + 40).contains(value.location) {
                    let current = knob(at: now).position
                    release(at: now, from: current, to: current > 0.5 ? 0 : 1)
                }
                tracking = false
            }
    }

    private func release(at date: Date, from position: Double, to destination: Double) {
        touching = false
        manual = true
        from = position
        target = destination
        released = date
        interaction += 1
        haptic.impactOccurred()
        haptic.prepare()
    }

    private func resumeLoop() async {
        guard manual, !touching, !reduced, scene == .active else { return }
        let remaining = max(0, Self.snap + Self.hold - Date().timeIntervalSince(released))
        try? await Task.sleep(for: .seconds(remaining))
        guard !Task.isCancelled, manual, !touching, !reduced, scene == .active else { return }
        origin = released.addingTimeInterval(Self.snap + Self.hold - (target > 0.5 ? 0 : 2.4))
        manual = false
    }

    private func reset() {
        manual = false
        touching = false
        tracking = false
        from = 1
        target = 1
        fingerPosition = 1
        origin = .now
        pausedAt = nil
        interaction += 1
    }
}

#Preview {
    CandyKnobToggle(autoPlay: true)
}
