import SwiftUI
import UIKit

/// A weather app's frost advisory seen through a frozen window.
///
/// A night weather screen for "Hollow Creek" (−4°, hourly strip) dimmed under an alert card. A
/// frosted pane covers everything, alert included: the app behind it is a blurred snapshot pulled
/// toward a cold white, with thin ice feathers growing in from the edges and sparkles on top (baked
/// once, FrostWipeAlertArt.swift), so only the alert's shape and its blue button show through. One
/// per-frame Metal shader over the real views (FrostWipeAlert.metal).
///
/// autoPlay (loop 9 s, FrostWipeAlertMotion.swift): 0–1.4 rest (fully frosted), 1.4–4.0 a hand
/// wipes a zig-zag down the alert, 4.0–5.4 it stays clear, 4.2–6.0 six droplets gather at the
/// wipe's lower edge and run down leaving wet streaks, 5.4–8.4 the frost grows back from the edges
/// of the wipe, 8.4–9 rest.
///
/// Touch: drag to wipe (a point every 18 pt; the wipe stays clear 1.2 s, then refreezes over 6 s).
/// The buttons work only where the glass is clear: Remind me (light haptic) turns into "Reminder at
/// 17:30", Dismiss lowers the alert. 2 s after the last touch the pane frosts over (0.9 s), the
/// alert comes back and the loop restarts. Reduced motion: no loop, the alert shown wiped clear.
struct FrostWipeAlert: View {
    /// Plays the 9 s wipe-and-refreeze loop until touched.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = FrostWipeAlertEngine()
    @State private var reminded = false
    @State private var dismissed = false
    @State private var shown = 1.0
    @State private var blurred: Image?
    /// The baked ice (2 px/pt) and haze (1 px/pt), made once off the main thread.
    @State private var iceImage: Image?
    @State private var hazeImage: Image?
    /// The pane appears once its shaders are compiled and the ice is baked; until then the first
    /// frames show the app itself, so the screen is never black.
    @State private var paneReady = false
    @State private var cardSize = CGSize.zero
    @State private var buttonFrames = [CGRect.zero, CGRect.zero]
    @State private var dragging = false
    /// Keeps the timeline running after a touch while there is no loop to play.
    @State private var awake = false

    /// Stands in for the haze until it is baked: an even, medium frost.
    private static let flatHaze = Image(uiImage: UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).image { ctx in
        UIColor(white: 0.5, alpha: 1).setFill()
        ctx.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
    })

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let W = size.width
        let H = size.height
        let cardTop = FrostWipeAlertScreen.cardTop(height: H, insets: insets)
        let card = CGRect(x: 24, y: cardTop, width: cardSize.width, height: cardSize.height)
        let haze = hazeImage ?? Self.flatHaze
        let paused = !auto && !awake

        return ZStack(alignment: .topLeading) {
            FrostWipeAlertScreen(
                size: size,
                insets: insets,
                reminded: reminded,
                shown: shown,
                onCardSize: { cardSize = $0 },
                onButtonFrame: { i, frame in buttonFrames[i] = frame }
            )
            .allowsHitTesting(false)

            if paneReady {
                TimelineView(.animation(paused: paused)) { timeline in
                    let f = engine.step(to: timeline.date, card: card, autoPlay: autoPlay, auto: auto, reduced: reduced)
                    let _ = sleepWhenIdle()
                    ZStack {
                        // The frost pass reads the blurred app as its layer.
                        Group {
                            if let blurred {
                                blurred.resizable().interpolation(.high)
                            } else {
                                Color(hex: 0x1a2a3a)
                            }
                        }
                        .frame(width: W, height: H)
                        .layerEffect(
                            ShaderLibrary.frostWipeAlert(
                                .image(haze),
                                .float2(max(W, 1), max(H, 1)),
                                .float(blurred == nil ? 0 : 1),
                                .float4(card.minX, card.minY, card.width, card.height),
                                .floatArray(f.wipe),
                                .floatArray(f.drops)
                            ),
                            maxSampleOffset: .zero
                        )
                        // The ice pass reads the ice image, drawn at the screen's scale, as its layer.
                        if let iceImage {
                            iceImage
                                .resizable()
                                .interpolation(.high)
                                .frame(width: W, height: H)
                                .layerEffect(
                                    ShaderLibrary.frostWipeAlertIce(
                                        .image(haze),
                                        .float2(max(W, 1), max(H, 1)),
                                        .floatArray(f.wipe),
                                        .floatArray(f.drops)
                                    ),
                                    maxSampleOffset: .zero
                                )
                                .blendMode(.plusLighter)
                        }
                    }
                    .frame(width: W, height: H)
                }
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            awake = true
                            if !dragging {
                                dragging = true
                                engine.dragBegan(value.startLocation)
                            }
                            engine.dragMoved(value.location)
                        }
                        .onEnded { value in
                            dragging = false
                            engine.dragEnded()
                            tapped(value, card: card, auto: auto)
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("A frosted window over a frost advisory. Drag to wipe the glass; the buttons work where it is clear.")
            }
        }
        .frame(width: W, height: H, alignment: .topLeading)
        .background(Color(hex: 0x0a1424))
        .clipped()
        .onAppear {
            engine.onBack = {
                reminded = false
                dismissed = false
            }
        }
        .onChange(of: dismissed) { _, gone in
            withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: gone ? 0.26 : 0.38)) {
                shown = gone ? 0 : 1
            }
        }
        // The ice and the haze, baked off the main thread while SwiftUI compiles the two pane
        // shaders (iOS 18+; compiling them at their first draw held the first frame for seconds).
        .task(id: "ice-\(Int(W))x\(Int(H))") {
            async let made = FrostWipeAlertArt.bake(size)
            if #available(iOS 18.0, *) {
                let none: [Float] = [0, 0, 0, 0]
                let frost = ShaderLibrary.frostWipeAlert(
                    .image(Self.flatHaze), .float2(1, 1), .float(0), .float4(0, 0, 0, 0),
                    .floatArray(none), .floatArray(none)
                )
                let ice = ShaderLibrary.frostWipeAlertIce(
                    .image(Self.flatHaze), .float2(1, 1), .floatArray(none), .floatArray(none)
                )
                try? await frost.compile(as: .layerEffect)
                try? await ice.compile(as: .layerEffect)
            }
            if let made = await made {
                hazeImage = Image(decorative: made.haze, scale: FrostWipeAlertArt.softScale)
                iceImage = Image(decorative: made.ice, scale: FrostWipeAlertArt.iceScale)
            }
            paneReady = true
        }
        // A fresh blurred snapshot of the app under the glass whenever what it shows changes.
        .task(id: "\(Int(W))x\(Int(H))-\(reminded)-\(dismissed)") {
            // The first snapshot waits a moment, so the first frame of the app is drawn before it.
            try? await Task.sleep(for: .milliseconds(blurred == nil ? 60 : 0))
            if blurred != nil {
                try? await Task.sleep(for: .milliseconds(420))
                if Task.isCancelled { return }
            }
            let screen = FrostWipeAlertScreen(
                size: size,
                insets: insets,
                reminded: reminded,
                shown: dismissed ? 0 : 1,
                onCardSize: { _ in },
                onButtonFrame: { _, _ in }
            )
            if let image = await FrostWipeAlertArt.blurred(screen, size: size) { blurred = image }
        }
    }

    /// Lets the timeline pause once the pane is still and nothing loops.
    private func sleepWhenIdle() -> Bool {
        if awake && !dragging && engine.isIdle {
            Task { @MainActor in if !dragging && engine.isIdle { awake = false } }
        }
        return true
    }

    /// A short tap that barely moved presses a button, if the glass over it is clear.
    private func tapped(_ value: DragGesture.Value, card: CGRect, auto: Bool) {
        guard hypot(value.translation.width, value.translation.height) <= 10, shown >= 0.5 else { return }
        let p = value.location
        for i in 0..<2 {
            let frame = buttonFrames[i].offsetBy(dx: card.minX, dy: card.minY)
            if frame.contains(p) && engine.clearAt(p.x, p.y) > 0.5 {
                FrostWipeAlertMetrics.light.impactOccurred()
                if i == 0 {
                    reminded.toggle()
                } else {
                    dismissed = true
                    // Without the loop to bring it back, the alert returns on its own after a moment.
                    if !auto {
                        Task { @MainActor in
                            try? await Task.sleep(for: .seconds(3))
                            dismissed = false
                        }
                    }
                }
            }
        }
    }
}

/// The weather app under the glass: the forecast, a dim, and the alert card.
struct FrostWipeAlertScreen: View {
    let size: CGSize
    let insets: EdgeInsets
    let reminded: Bool
    /// The alert's entrance (1) and exit (0).
    let shown: Double
    let onCardSize: (CGSize) -> Void
    let onButtonFrame: (Int, CGRect) -> Void

    private typealias M = FrostWipeAlertMetrics

    static func cardTop(height: Double, insets: EdgeInsets) -> Double {
        max(insets.top + 150, height * 0.5 - 150)
    }

    var body: some View {
        let W = size.width
        let H = size.height
        ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x0a1424), location: 0),
                    .init(color: Color(hex: 0x16304b), location: 0.55),
                    .init(color: Color(hex: 0x2c4b68), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: W, height: H)

            weather
                .padding(.horizontal, 24)
                .frame(width: W)
                .offset(y: insets.top + 28)

            hourly
                .padding(.horizontal, 20)
                .frame(width: W)
                .offset(y: insets.top + 236)

            daily
                .padding(.horizontal, 20)
                .frame(width: W, height: H - insets.bottom - 24, alignment: .bottom)

            Color.rgba(4, 9, 18, 0.5)
                .frame(width: W, height: H)

            card
                .padding(.horizontal, 24)
                .frame(width: W, alignment: .leading)
                .opacity(shown)
                .scaleEffect(0.96 + 0.04 * shown)
                .offset(y: Self.cardTop(height: H, insets: insets) + (1 - shown) * 36)
        }
        .frame(width: W, height: H, alignment: .topLeading)
    }

    private var weather: some View {
        VStack(spacing: 0) {
            Text("Hollow Creek")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(M.ink)
            Text("−4°")
                .font(.system(size: 92, weight: .thin))
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0xf2f7fb))
                .padding(.top, -2)
            Text("Clear · frost likely after 22:00")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(M.ink.opacity(0.75))
            Text("H −1°   L −6°")
                .font(.system(size: 14, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(M.ink.opacity(0.55))
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
    }

    private var hourly: some View {
        HStack(spacing: 0) {
            ForEach(0..<M.hours.count, id: \.self) { i in
                VStack(spacing: 8) {
                    Text(M.hours[i].0)
                        .font(.system(size: 12, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(M.ink.opacity(0.6))
                    FrostWipeAlertMoon()
                        .frame(width: 18, height: 18)
                    Text(M.hours[i].1)
                        .font(.system(size: 15, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(M.ink)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 96)
        .background(M.glass(RoundedRectangle(cornerRadius: 22, style: .continuous)))
    }

    private var daily: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("NEXT 3 DAYS")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.4)
                .foregroundStyle(M.ink.opacity(0.55))
            ForEach(0..<M.days.count, id: \.self) { i in
                let day = M.days[i]
                HStack(spacing: 12) {
                    Text(day.name)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(M.ink)
                        .frame(width: 40, alignment: .leading)
                    Text(day.low)
                        .font(.system(size: 15, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(M.ink.opacity(0.6))
                        .frame(width: 36, alignment: .trailing)
                    Capsule()
                        .fill(Color.rgba(214, 232, 250, 0.12))
                        .frame(height: 4)
                        .overlay(alignment: .leading) {
                            GeometryReader { g in
                                let left = g.size.width * day.share * 0.4
                                let right = g.size.width * (1 - day.share) * 0.3
                                Capsule()
                                    .fill(
                                        LinearGradient(
                                            stops: [
                                                .init(color: Color(hex: 0x7fb4e8), location: 0),
                                                .init(color: Color(hex: 0xb9d6f0), location: 0.6),
                                                .init(color: Color(hex: 0xf2d39a), location: 1),
                                            ],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .frame(width: max(0, g.size.width - left - right), height: 4)
                                    .offset(x: left)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    Text(day.high)
                        .font(.system(size: 15, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(M.ink)
                        .frame(width: 32, alignment: .leading)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(M.glass(RoundedRectangle(cornerRadius: 22, style: .continuous)))
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                FrostWipeAlertSnowflake()
                    .frame(width: 24, height: 24)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Color(hex: 0xd8e6f2)))
                Spacer()
                Text("ADVISORY")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.2)
                    .foregroundStyle(Color(hex: 0x2a5d8f))
                    .padding(.horizontal, 10)
                    .frame(height: 24)
                    .background(Capsule().fill(Color(hex: 0xdbe7f2)))
            }
            .padding(.bottom, 16)
            Text("Frost advisory")
                .font(.system(size: 24, weight: .bold))
                .kerning(-0.3)
                .foregroundStyle(Color(hex: 0x0f1c2b))
            Text("−4 °C tonight in Hollow Creek. Cover your plants by 18:00.")
                .font(.system(size: 16))
                .lineSpacing(22 - 19.1)
                .foregroundStyle(Color(hex: 0x3a4a5b))
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
            Text("Issued 15:20 · until Sun 09:00")
                .font(.system(size: 13, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0x6a7989))
                .padding(.top, 12)
            HStack(spacing: 12) {
                HStack(spacing: 6) {
                    if reminded {
                        Canvas { c, _ in
                            var p = Path()
                            p.move(to: CGPoint(x: 2.5, y: 7.5))
                            p.addLine(to: CGPoint(x: 5.6, y: 10.4))
                            p.addLine(to: CGPoint(x: 11.5, y: 3.8))
                            c.stroke(p, with: .color(Color(hex: 0xf4f8fc)), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                        }
                        .frame(width: 14, height: 14)
                    }
                    Text(reminded ? "Reminder at 17:30" : "Remind me")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color(hex: 0xf4f8fc))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(
                    Capsule()
                        .fill(Color(hex: 0x2f6fb3))
                        .overlay(
                            Capsule().strokeBorder(
                                LinearGradient(colors: [Color.white.opacity(0.25), Color.white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)),
                                lineWidth: 1
                            )
                        )
                        .shadow(color: Color.rgba(10, 30, 60, 0.3), radius: 1, y: 1)
                )
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(M.cardSpace)) } action: { onButtonFrame(0, $0) }
                Text("Dismiss")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x21384f))
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Capsule().fill(Color(hex: 0xdde6ee)))
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(M.cardSpace)) } action: { onButtonFrame(1, $0) }
            }
            .padding(.top, 20)
        }
        .padding(M.cardPad)
        .frame(maxWidth: .infinity, alignment: .leading)
        .coordinateSpace(name: M.cardSpace)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Color(hex: 0xeef3f7))
                .overlay(
                    RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(
                        LinearGradient(colors: [Color.white.opacity(0.9), Color.white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)),
                        lineWidth: 1
                    )
                )
                .shadow(color: Color.rgba(2, 8, 18, 0.35), radius: 2, y: 2)
                .shadow(color: Color.rgba(2, 8, 18, 0.45), radius: 24, y: 24)
        )
        .onGeometryChange(for: CGSize.self) { $0.size } action: { onCardSize($0) }
    }
}

/// The crescent moon of the hourly strip (an 18 × 18 pt drawing).
struct FrostWipeAlertMoon: View {
    var body: some View {
        Canvas { c, _ in
            // M12.5 3.5 a6 6 0 1 0 2 9.5 a5 5 0 0 1 -2 -9.5 Z: a disc of radius 6 with one of
            // radius 5 taken out through the same two points.
            let outer = Path(ellipseIn: CGRect(x: 10.05 - 6, y: 8.98 - 6, width: 12, height: 12))
            let inner = Path(ellipseIn: CGRect(x: 14.69 - 5, y: 8.0 - 5, width: 10, height: 10))
            c.fill(outer.subtracting(inner), with: .color(Color(hex: 0xc9d8ea)))
        }
    }
}

/// The alert's snowflake (a 24 × 24 pt drawing): three crossed arms with barbs and a dot.
struct FrostWipeAlertSnowflake: View {
    var body: some View {
        Canvas { c, _ in
            var arm = Path()
            arm.move(to: CGPoint(x: 12, y: 2.5)); arm.addLine(to: CGPoint(x: 12, y: 21.5))
            arm.move(to: CGPoint(x: 12, y: 6)); arm.addLine(to: CGPoint(x: 9.5, y: 3.8))
            arm.move(to: CGPoint(x: 12, y: 6)); arm.addLine(to: CGPoint(x: 14.5, y: 3.8))
            arm.move(to: CGPoint(x: 12, y: 18)); arm.addLine(to: CGPoint(x: 9.5, y: 20.2))
            arm.move(to: CGPoint(x: 12, y: 18)); arm.addLine(to: CGPoint(x: 14.5, y: 20.2))
            let ink = Color(hex: 0x2a5d8f)
            for a in [0.0, 60, 120] {
                let turn = CGAffineTransform(translationX: 12, y: 12).rotated(by: a * .pi / 180).translatedBy(x: -12, y: -12)
                c.stroke(arm.applying(turn), with: .color(ink), style: StrokeStyle(lineWidth: 1.7, lineCap: .round))
            }
            c.fill(Path(ellipseIn: CGRect(x: 10.4, y: 10.4, width: 3.2, height: 3.2)), with: .color(ink))
        }
    }
}

@MainActor
enum FrostWipeAlertMetrics {
    static let ink = Color(hex: 0xe9f1f8)
    static let cardPad = 24.0
    nonisolated static let cardSpace = "FrostWipeAlertCard"
    static let light = UIImpactFeedbackGenerator(style: .light)
    static let hours: [(String, String)] = [
        ("18:00", "−1°"), ("21:00", "−3°"), ("00:00", "−4°"), ("03:00", "−4°"), ("06:00", "−2°"),
    ]
    static let days: [(name: String, low: String, high: String, share: Double)] = [
        ("Sat", "−6°", "2°", 0.1),
        ("Sun", "−4°", "4°", 0.35),
        ("Mon", "0°", "7°", 0.8),
    ]

    /// The forecast panels' frosted-blue glass.
    static func glass<S: InsettableShape>(_ shape: S) -> some View {
        shape
            .fill(Color.rgba(214, 232, 250, 0.08))
            .overlay(shape.strokeBorder(Color.rgba(214, 232, 250, 0.16), lineWidth: 0.5))
    }
}
