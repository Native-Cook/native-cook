import SwiftUI
import UIKit

/// A welcome screen under a live aurora: green and violet curtains at three depths ripple over a
/// night sky above a mountain horizon, behind a headline and a frosted glass card with tonight's
/// forecast. Dragging tilts the card in 3D, a sheen sliding across its glass, and the aurora bends
/// towards the finger; let go, the card springs flat.
struct AuroraGlassWelcome: View {
    /// Tilts the forecast card left and right by itself while the aurora flows.
    var autoPlay = false
    var onStart: (() -> Void)? = nil
    var onSignIn: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = AuroraGlassWelcomeEngine()
    @State private var horizon: UIImage?
    @State private var appeared = Date()

    private typealias M = AuroraGlassWelcomeMetrics

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            screen(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .onAppear { appeared = Date() }
        .task { horizon = await AuroraGlassWelcomePhotos.load(M.horizon) }
    }

    private func screen(size: CGSize, insets: EdgeInsets) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, autoPlay: autoPlay, reducedMotion: reduceMotion)
            let since = timeline.date.timeIntervalSince(appeared)
            ZStack(alignment: .topLeading) {
                sky(size: size, frame: frame, since: since)
                bar(clock: frame.clock, since: since)
                    .padding(.horizontal, 24)
                    .frame(width: size.width)
                    .offset(y: insets.top + 10)
                headline(since: since)
                    .padding(.horizontal, 24)
                    .frame(width: size.width, alignment: .leading)
                    .offset(y: insets.top + 74)
                    .allowsHitTesting(false)
                VStack(spacing: 16) {
                    card(frame: frame, since: since)
                    ctas(since: since)
                }
                .padding(.horizontal, 18)
                .padding(.bottom, insets.bottom + 10)
                .frame(width: size.width, height: size.height, alignment: .bottom)
            }
        }
        .frame(width: size.width, height: size.height)
        .background(M.night)
        .clipped()
        .contentShape(Rectangle())
        // Dragging anywhere tilts the card, also over its buttons.
        .simultaneousGesture(
            DragGesture(minimumDistance: M.panSlop)
                .onChanged { engine.touchChanged($0, size: size) }
                .onEnded { engine.touchEnded($0) }
        )
    }

    /// How far an element `at` s after appearing has come in, eased out over `time`.
    private func entered(_ since: Double, _ at: Double, _ time: Double = M.itemIn) -> Double {
        M.easeOut.value(at: min(max((since - at) / time, 0), 1))
    }

    // MARK: The sky

    private func sky(size: CGSize, frame: AuroraGlassWelcomeEngine.Frame, since: Double) -> some View {
        let horizonW = size.width * M.horizonWidth
        let horizonH = horizonW / M.horizonAspect
        let horizonTop = size.height * M.horizonTop
        let groundTop = horizonTop + horizonH - 2
        let reveal = entered(since, 0, reduceMotion ? 0.6 : M.skyIn)
        return ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(ShaderLibrary.auroraGlassWelcomeSky(
                    .float2(size),
                    .float(frame.clock / M.loop * tau),
                    .float2(frame.pointer),
                    .float(frame.pull),
                    .float(reveal)
                ))
            if let horizon {
                // Keyed into a silhouette, fading in over its top 16%, rising into place as the sky lights up.
                Image(uiImage: horizon)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: horizonW, height: horizonH)
                    .colorEffect(ShaderLibrary.auroraGlassWelcomeKey())
                    .mask(
                        LinearGradient(
                            stops: [.init(color: .clear, location: 0), .init(color: .white, location: 0.16)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .opacity(min(1, reveal * 1.6))
                    .offset(x: (size.width - horizonW) / 2, y: horizonTop + (reduceMotion ? 0 : (1 - reveal) * M.horizonRise))
            }
            // The photo's foot melts into the night ground.
            LinearGradient(
                stops: [
                    .init(color: Color.rgba(4, 8, 21, 0), location: 0),
                    .init(color: M.night, location: M.groundFade / (size.height - groundTop + M.groundFade)),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: size.width, height: max(1, size.height - groundTop + M.groundFade))
            .offset(y: groundTop - M.groundFade)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    // MARK: Top

    private func bar(clock: Double, since: Double) -> some View {
        let pulse = pow(sin(clock / M.loop * .pi * M.pulses), 2)
        return HStack {
            Text("SOLVIND")
                .font(.system(size: 13, weight: .heavy))
                .kerning(3.6)
                .foregroundStyle(M.ink)
            Spacer()
            HStack(spacing: 8) {
                Circle()
                    .fill(M.green)
                    .frame(width: 6, height: 6)
                    .opacity(0.5 + 0.5 * pulse)
                Text("Clear · −4°")
                    .font(.system(size: 12, weight: .semibold).monospacedDigit())
                    .foregroundStyle(Color.rgba(243, 246, 255, 0.88))
            }
            .padding(.horizontal, 12)
            .frame(height: 30)
            .background(Capsule().fill(Color.white.opacity(0.08)))
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.22), lineWidth: 1 / UIScreen.main.scale))
        }
        .frame(height: 32)
        .opacity(entered(since, M.barAt))
    }

    private func headline(since: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            line("Chase the", at: M.lineAt[0], since: since)
            line("northern light.", at: M.lineAt[1], since: since)
            let sub = entered(since, M.subAt)
            Text("Live forecasts, wake-up alerts and quiet spots far from the city glow.")
                .font(.system(size: 16, weight: .medium))
                .lineSpacing(22 - UIFont.systemFont(ofSize: 16, weight: .medium).lineHeight)
                .foregroundStyle(M.soft)
                .frame(maxWidth: 310, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 12)
                .opacity(sub)
                .offset(y: reduceMotion ? 0 : (1 - sub) * 12)
        }
    }

    /// One line of the headline, climbing out of a slot at its own baseline.
    private func line(_ text: String, at: Double, since: Double) -> some View {
        let shown = entered(since, at, M.lineIn)
        return Text(text)
            .font(.system(size: 44, weight: .bold))
            .kerning(-1.4)
            .foregroundStyle(M.ink)
            .lineLimit(1)
            .frame(height: 50, alignment: .center)
            .opacity(reduceMotion ? shown : min(1, shown * 2))
            .offset(y: reduceMotion ? 0 : (1 - shown) * 50)
            .frame(height: 50, alignment: .top)
            .clipped()
    }

    // MARK: The card

    private func card(frame: AuroraGlassWelcomeEngine.Frame, since: Double) -> some View {
        let e = entered(since, M.cardAt, M.cardIn)
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        let tilt = hypot(frame.yaw, frame.pitch)
        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("TONIGHT · VÍK")
                        .font(.system(size: 11, weight: .semibold))
                        .kerning(1.5)
                        .foregroundStyle(M.faint)
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text("Kp 6.3")
                            .font(.system(size: 34, weight: .bold).monospacedDigit())
                            .kerning(-0.8)
                            .foregroundStyle(M.ink)
                        Text("High")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(M.green)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("21:40 – 01:10")
                        .font(.system(size: 15, weight: .semibold).monospacedDigit())
                        .foregroundStyle(M.ink)
                    Text("Best window")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(M.faint)
                }
                .padding(.bottom, 4)
            }

            VStack(spacing: 6) {
                AuroraGlassWelcomeChart(since: since, clock: frame.clock, reduced: reduceMotion)
                    .frame(height: M.chartHeight)
                HStack {
                    ForEach(M.hours, id: \.self) { hour in
                        Text(hour)
                        if hour != M.hours.last { Spacer() }
                    }
                }
                .font(.system(size: 11, weight: .medium).monospacedDigit())
                .foregroundStyle(M.faint)
            }

            HStack(spacing: 0) {
                ForEach(Array(M.stats.enumerated()), id: \.offset) { i, stat in
                    let shown = entered(since, M.statsAt + Double(i) * M.statStagger)
                    HStack(spacing: 0) {
                        if i > 0 {
                            Rectangle().fill(Color.white.opacity(0.16)).frame(width: 1 / UIScreen.main.scale)
                            Spacer().frame(width: 14)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(stat.value)
                                .font(.system(size: 17, weight: .semibold).monospacedDigit())
                                .foregroundStyle(M.ink)
                            Text(stat.label)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(M.faint)
                        }
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity)
                    .opacity(shown)
                    .offset(y: reduceMotion ? 0 : (1 - shown) * 8)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 14)
            .overlay(alignment: .top) {
                Rectangle().fill(Color.white.opacity(0.16)).frame(height: 1 / UIScreen.main.scale)
            }
        }
        .padding(20)
        .background {
            ZStack {
                // Frosted glass: the sky blurred through a dark material, a cold tint, and a soft
                // inner light from the top.
                Rectangle().fill(.ultraThinMaterial).environment(\.colorScheme, .dark)
                Color.rgba(10, 18, 44, 0.3)
                LinearGradient(
                    stops: [
                        .init(color: .white.opacity(0.13), location: 0),
                        .init(color: .white.opacity(0.03), location: 0.28),
                        .init(color: .white.opacity(0), location: 0.6),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                // The sheen slides across the glass opposite the tilt, brighter the more it is tilted.
                GeometryReader { geo in
                    LinearGradient(
                        stops: [
                            .init(color: .white.opacity(0), location: 0.32),
                            .init(color: .white.opacity(0.16), location: 0.5),
                            .init(color: .white.opacity(0), location: 0.68),
                        ],
                        startPoint: UnitPoint(x: 0, y: 0.15),
                        endPoint: UnitPoint(x: 1, y: 0.85)
                    )
                    .frame(width: geo.size.width + 440, height: geo.size.height + 160)
                    .offset(x: -220 - frame.yaw / M.maxTilt * 220, y: -80 + frame.pitch / M.maxTilt * 80)
                    .opacity(min(1, 0.2 + tilt / M.maxTilt))
                }
            }
            .allowsHitTesting(false)
        }
        .clipShape(shape)
        // The edge: a hairline brightest along the top, where the light catches it.
        .overlay(
            shape.inset(by: 0.5).stroke(
                LinearGradient(
                    stops: [
                        .init(color: .white.opacity(0.55), location: 0),
                        .init(color: .white.opacity(0.14), location: 0.35),
                        .init(color: .white.opacity(0.06), location: 0.75),
                        .init(color: .white.opacity(0.2), location: 1),
                    ],
                    startPoint: .topLeading,
                    endPoint: UnitPoint(x: 0.35, y: 1)
                ),
                lineWidth: 1
            )
        )
        .background {
            // A deep shadow, drawn only outside the card so the glass does not frost it.
            shape.fill(Color.rgba(0, 2, 10, 0.55))
                .blur(radius: 28)
                .offset(y: 28)
                .mask {
                    ZStack {
                        Rectangle().padding(-120)
                        shape.blendMode(.destinationOut)
                    }
                    .compositingGroup()
                }
                .allowsHitTesting(false)
        }
        .opacity(e)
        .modifier(AuroraGlassWelcomeTiltEffect(
            pitch: frame.pitch,
            yaw: frame.yaw,
            lift: reduceMotion ? 0 : (1 - e) * M.cardRise,
            scale: 1 + 0.015 * frame.pressed - (reduceMotion ? 0 : (1 - e) * 0.04)
        ))
    }

    private func ctas(since: Double) -> some View {
        let shown = entered(since, M.ctaAt)
        return VStack(spacing: 14) {
            AuroraGlassWelcomeStart(action: onStart)
            Button {
                onSignIn?()
            } label: {
                Text("I already have an account")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.rgba(243, 246, 255, 0.72))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 2)
                    .contentShape(Rectangle().inset(by: -10))
            }
            .buttonStyle(.plain)
        }
        .opacity(shown)
        .offset(y: reduceMotion ? 0 : (1 - shown) * 12)
    }
}

/// Tonight's activity: a smooth curve that draws on from the left, its area filling in under it,
/// and a marker that pops onto the peak and pulses five times a loop.
private struct AuroraGlassWelcomeChart: View {
    let since: Double
    let clock: Double
    let reduced: Bool

    private typealias M = AuroraGlassWelcomeMetrics

    var body: some View {
        Canvas { context, size in
            let w = max(1, size.width)
            let h = M.chartHeight
            let top = 10.0
            let points = M.activity.enumerated().map { i, v in
                CGPoint(x: Double(i) / Double(M.activity.count - 1) * w, y: top + (1 - v / 7) * (h - top))
            }
            var line = Path()
            line.move(to: points[0])
            for i in 0..<(points.count - 1) {
                let p0 = points[max(0, i - 1)]
                let p1 = points[i]
                let p2 = points[i + 1]
                let p3 = points[min(points.count - 1, i + 2)]
                line.addCurve(
                    to: p2,
                    control1: CGPoint(x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6),
                    control2: CGPoint(x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6)
                )
            }
            var area = line
            area.addLine(to: CGPoint(x: w, y: h))
            area.addLine(to: CGPoint(x: 0, y: h))
            area.closeSubpath()
            let now = points[M.nowIndex]

            let curveT = min(max((since - M.curveAt) / M.curveIn, 0), 1)
            let drawn = reduced ? 1 : M.easeInOut.value(at: curveT)
            let markerT = max(0, since - (reduced ? M.curveAt : M.curveAt + M.curveIn * 0.55))
            let marker = reduced
                ? min(1, markerT / 0.4)
                : (since < M.curveAt + M.curveIn * 0.55 ? 0 : Spring(duration: 0.5, bounce: 0.5).value(target: 1, time: markerT))

            // Faint guides.
            for f in [0.33, 0.66] {
                context.fill(Path(CGRect(x: 0, y: top + f * (h - top), width: w, height: 1 / UIScreen.main.scale)), with: .color(.white.opacity(0.1)))
            }
            var guide = context
            guide.opacity = min(1, max(0, marker))
            guide.fill(Path(CGRect(x: now.x - 0.5, y: 0, width: 1, height: h)), with: .color(Color.rgba(243, 246, 255, 0.22)))

            // The area is uncovered left to right with the line.
            var fill = context
            fill.clip(to: Path(CGRect(x: 0, y: 0, width: w * drawn, height: h)))
            fill.fill(area, with: .linearGradient(
                Gradient(colors: [Color(hex: 0x6dffbf, opacity: 0.32), Color(hex: 0x6dffbf, opacity: 0)]),
                startPoint: CGPoint(x: 0, y: top),
                endPoint: CGPoint(x: 0, y: h)
            ))
            let lineOpacity = reduced ? min(1, max(0, marker)) : (drawn > 0.001 ? 1 : 0)
            if lineOpacity > 0 {
                var stroke = context
                stroke.opacity = lineOpacity
                stroke.stroke(
                    line.trimmedPath(from: 0, to: max(0.001, drawn)),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: Color(hex: 0x4fd3c4), location: 0),
                            .init(color: Color(hex: 0x6dffbf), location: 0.4),
                            .init(color: Color(hex: 0x9cf5d2), location: 0.7),
                            .init(color: Color(hex: 0x6f8cff), location: 1),
                        ]),
                        startPoint: .zero,
                        endPoint: CGPoint(x: w, y: 0)
                    ),
                    style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round)
                )
            }

            // The marker and its pulse.
            let pulse = (clock / M.loop * M.pulses).truncatingRemainder(dividingBy: 1)
            if !reduced {
                var ring = context
                ring.opacity = 0.55 * (1 - pulse) * min(1, max(0, marker))
                let r = 4.5 + 13 * pulse
                ring.stroke(Path(ellipseIn: CGRect(x: now.x - r, y: now.y - r, width: 2 * r, height: 2 * r)), with: .color(M.green), lineWidth: 1.4)
            }
            let dot = 4.5 * max(0, marker)
            if dot > 0 {
                let rect = CGRect(x: now.x - dot, y: now.y - dot, width: 2 * dot, height: 2 * dot)
                context.fill(Path(ellipseIn: rect), with: .color(.white))
                context.stroke(Path(ellipseIn: rect), with: .color(M.green), lineWidth: 2)
            }
        }
    }
}

/// The card's 3D tilt under a 900 pt perspective, about its middle: scale, yaw, pitch, then lift,
/// in the order the React Native transform list applies them.
private struct AuroraGlassWelcomeTiltEffect: GeometryEffect {
    var pitch: Double
    var yaw: Double
    var lift: Double
    var scale: Double

    var animatableData: EmptyAnimatableData {
        get { EmptyAnimatableData() }
        set {}
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        var t = CATransform3DMakeTranslation(-size.width / 2, -size.height / 2, 0)
        t = CATransform3DConcat(t, CATransform3DMakeScale(scale, scale, 1))
        t = CATransform3DConcat(t, CATransform3DMakeRotation(yaw * .pi / 180, 0, 1, 0))
        t = CATransform3DConcat(t, CATransform3DMakeRotation(pitch * .pi / 180, 1, 0, 0))
        t = CATransform3DConcat(t, CATransform3DMakeTranslation(0, lift, 0))
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / AuroraGlassWelcomeMetrics.perspective
        t = CATransform3DConcat(t, perspective)
        t = CATransform3DConcat(t, CATransform3DMakeTranslation(size.width / 2, size.height / 2, 0))
        return ProjectionTransform(t)
    }
}

/// The white pill, dipping to 0.97 under the finger.
private struct AuroraGlassWelcomeStart: View {
    let action: (() -> Void)?

    var body: some View {
        Button {
            UISelectionFeedbackGenerator().selectionChanged()
            action?()
        } label: {
            Text("Get started")
                .font(.system(size: 17, weight: .semibold))
                .kerning(-0.2)
                .foregroundStyle(Color(hex: 0x08102a))
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(Capsule().fill(.white))
                .contentShape(Capsule())
        }
        .buttonStyle(AuroraGlassWelcomePress())
    }
}

private struct AuroraGlassWelcomePress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? 0.11 : 0.16), value: configuration.isPressed)
    }
}
