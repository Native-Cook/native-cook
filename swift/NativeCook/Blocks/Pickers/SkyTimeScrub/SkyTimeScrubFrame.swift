import SwiftUI
import CoreText

struct SkyTimeScrubFrame: View {
    let pose: SkyTimeScrubMotion.Pose
    let layout: SkyTimeScrubLayout
    let day: SkyTimeScrubMotion.Day
    let now: Double
    let turbidity: Double
    let reduce: Bool
    let displayScale: Double
    @Binding var scheduleOpen: Bool
    @Binding var hintDismissed: Bool
    let travel: (Double) -> Void
    let gridCue: () -> Void
    let touchTargets: AnyView
    @State private var reveal = 0.0
    typealias M = SkyTimeScrubMotion
    typealias R = SkyTimeScrubResources
    private var hintLive: Bool { !hintDismissed && !scheduleOpen }
    private var sun: (elevation: Double, x: Double) { M.solar(pose.minutes, day) }
    private var tint: [Double] { R.skyTint(sun.elevation) }
    var body: some View {
        let l = layout
        ZStack(alignment: .topLeading) {
            R.color(0x020203)
            shader(layer: 0)
            shader(layer: 1)
            touchTargets
            chip
            RoundedRectangle(cornerRadius: 29.5 * l.s).fill(R.color(0x121222))
                .frame(width: 354 * l.s, height: l.scheduleH)
                .opacity(reveal).offset(x: 24 * l.s, y: l.scheduleY).allowsHitTesting(false)
            schedule
            hint
            controls
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .onChange(of: scheduleOpen) {
            withAnimation(reduce ? nil : Animation(SkyTimeScrubTiming(duration: 0.12))) { reveal = scheduleOpen ? 1 : 0 }
        }
    }
    private func uniforms(layer: Int) -> [Float] {
        let l = layout, e = sun.elevation
        // Same ordering as sts-shaders.ts, including the iOS outputP3 flag.
        let u: [Double] = [l.s / (layer == 0 ? 3 : 1), l.horizon / l.s, l.arcCentre / l.s,
                           l.arcAmplitude / l.s, l.rulerY / l.s, (M.wrap(pose.minutes) - now) / 2.5,
                           sun.x, M.sunHeight(e, day, l.projection), e, pose.ev, M.evTarget(e),
                           pose.held, pose.press, M.clamp(turbidity, 2, 8), Double(layer), 1,
                           107.0 / 255, 131.0 / 255, 216.0 / 255, 2.0 / 255, 2.0 / 255, 3.0 / 255]
        return u.map(Float.init)
    }
    private func shader(layer: Int) -> some View {
        let raster = layer == 0 ? 3.0 : 1.0
        return Rectangle().fill(.white)
            .frame(width: layout.width / raster, height: layout.height / raster, alignment: .topLeading)
            .colorEffect(ShaderLibrary.skyTimeScrub(.floatArray(uniforms(layer: layer))))
            .drawingGroup(opaque: layer == 0, colorMode: .nonLinear)
            .scaleEffect(raster, anchor: .topLeading)
            .allowsHitTesting(false).accessibilityHidden(true)
    }
    private func text(_ value: String, weight: String, size: Double) -> Text {
        let font = R.font(weight, size * layout.s * layout.textScale)
        return Text(value).font(Font(font as CTFont))
    }
    private var chip: some View {
        let l = layout, label = M.label(pose.minutes, day, now)
        let chipMax = min(l.width - 32 * l.s, 220 * l.s * max(1, l.textScale))
        let measured = R.width(label, size: 14 * l.s * l.textScale) + 32 * l.s
        let width = M.clamp(measured, 76 * l.s, chipMax)
        let longest = R.width("Solar noon · 00:00", size: 14 * l.s * l.textScale)
        let oneLine = !l.largeText || (longest > 0 && longest + 32 * l.s <= chipMax)
        let height = max(28 * l.s, (20 * l.textScale * (oneLine ? 1 : 2) + 8) * l.s)
        let base = M.diff(M.wrap(pose.minutes), now) > 1 ? 1 : M.clamp(pose.held * 4)
        let opacity = base * (hintLive && !reduce ? 1 - M.smooth((pose.hint - 0.12) / 0.26) : 1)
        let visible = pose.held > 0.001 || M.diff(M.wrap(pose.minutes), now) > 1
        let c = tint, bg = [M.mix(34, c[3], 0.25), M.mix(60, c[4], 0.25), M.mix(99, c[5], 0.25)]
        return Button { travel(now) } label: {
            VStack(spacing: 0) {
                text(label, weight: "Medium", size: 14).monospacedDigit()
                    .foregroundStyle(R.color(0xF3F5F8))
                    .multilineTextAlignment(.center)
                    .frame(width: max(0, width - 24 * l.s), height: height - 8 * l.s)
                    .padding(.horizontal, 12 * l.s).padding(.vertical, 4 * l.s)
                    .background(R.rgb(bg), in: RoundedRectangle(cornerRadius: 14 * l.s))
                Spacer(minLength: 0)
            }
            .frame(width: chipMax, height: max(48 * l.s, height), alignment: .top)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain).opacity(opacity).allowsHitTesting(visible)
        .accessibilityLabel("Return to now").accessibilityHidden(!visible)
        .offset(x: (l.width - chipMax) / 2, y: l.chipY - (height - 28 * l.s))
    }
    private var schedule: some View {
        let l = layout
        return ScrollView {
            VStack(spacing: 0) {
                row(0, "Solar noon", day.noon, 0x75D9C7)
                row(1, "Sunset", day.sunset, 0xFFA34E)
                row(2, "Night starts", day.night, 0xEF7897)
            }
        }
        .scrollIndicators(.hidden).scrollDisabled(!scheduleOpen)
        .frame(width: l.width - 80 * l.s, height: l.scheduleH)
        .allowsHitTesting(scheduleOpen).accessibilityHidden(!scheduleOpen)
        .offset(x: 40 * l.s, y: l.scheduleY)
    }
    private func row(_ index: Int, _ title: String, _ minutes: Double, _ hex: UInt32) -> some View {
        let l = layout, age = pose.ticks[index]
        let env = age >= 0 && age < 0.3 ? exp(-age / 0.09) : 0
        let opacity = 0.55 + 0.45 * max(M.smooth((pose.wake - Double(index) * 0.04) / 0.22), reveal)
        let rgb = [Double(hex >> 16 & 255), Double(hex >> 8 & 255), Double(hex & 255)]
        // interpolateColor's default gamma=2.2, and rgbString rounds the dim endpoints first.
        let color = rgb.map { channel -> Double in
            let dim = floor(channel * 0.78 + 0.5)
            return pow(M.mix(pow(dim / 255, 2.2), pow(channel / 255, 2.2), env), 1 / 2.2) * 255
        }
        let value = text(M.label(minutes, day, now, named: false), weight: "SemiBold", size: 26)
            .monospacedDigit().foregroundStyle(R.rgb(color))
            .scaleEffect(1 + 0.06 * env, anchor: .trailing)
        return Group {
            if l.largeText {
                VStack(alignment: .leading, spacing: 8 * l.s) {
                    text(title, weight: "Regular", size: 13).foregroundStyle(R.color(0xF3F5F8))
                    value
                }.frame(maxWidth: .infinity, minHeight: l.rowH, alignment: .leading)
            } else {
                HStack(spacing: 8 * l.s) {
                    text(title, weight: "Regular", size: 13).foregroundStyle(R.color(0xF3F5F8))
                    Spacer(minLength: 0)
                    value
                }.frame(minHeight: l.rowH)
            }
        }.opacity(opacity)
    }
    private var hint: some View {
        let l = layout, h = reduce ? 1 : pose.hint
        let open = M.smooth((h - 0.35) / 0.45)
        let w = M.mix(76 * l.s, l.hintW, open), tall = M.mix(28 * l.s, l.hintH, open)
        let pillOpacity = M.smooth((h - 0.26) / 0.12)
        let grow = M.smooth((h - 0.1) / 0.3) * max(0, l.tipY - (l.hintY + l.hintH))
        let dotScale = 0.4 + 0.6 * M.smooth((h - 0.06) / 0.14)
        return ZStack(alignment: .topLeading) {
            Rectangle().fill(R.color(0xEEFA3C)).frame(width: 2 * l.s, height: grow)
                .offset(x: l.width / 2 - l.s, y: l.tipY - grow)
                .allowsHitTesting(false)
            Circle().fill(R.color(0xEEFA3C)).frame(width: 10 * l.s, height: 10 * l.s)
                .scaleEffect(dotScale).opacity(M.smooth((h - 0.06) / 0.1))
                .offset(x: l.width / 2 - 5 * l.s, y: l.tipY - 5 * l.s)
                .allowsHitTesting(false)
            ZStack {
                R.color(0xEEFA3C)
                HStack(spacing: 0) {
                    text("Long press and slide to time travel", weight: "Medium", size: 13)
                        .foregroundStyle(R.color(0x15180A))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    SkyTimeScrubIcon(kind: "close", tint: tint).frame(width: 16 * l.s, height: 16 * l.s)
                        .frame(width: 24 * l.s)
                }
                .padding(.leading, 10 * l.s).padding(.trailing, 4 * l.s)
                .frame(width: l.hintW, height: tall)
                .opacity(reduce ? 1 : M.smooth((pose.hint - 0.72) / 0.28))
            }
            .frame(width: w, height: tall)
            .clipShape(.rect(cornerRadius: M.mix(14 * l.s, 8 * l.s, open)))
            .opacity(pillOpacity).offset(x: (l.width - w) / 2, y: l.hintY + l.hintH - tall)
            .allowsHitTesting(false)
            Button { hintDismissed = true } label: { Color.clear.contentShape(Rectangle()).frame(width: 48, height: 48) }
                .buttonStyle(.plain).accessibilityLabel("Dismiss time travel hint")
                .offset(x: l.width - l.hintX - 16 * l.s - 24, y: l.hintY + l.hintH / 2 - 24)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .opacity(hintLive ? (reduce ? pose.hint : 1) : 0)
        .animation(reduce ? nil : Animation(SkyTimeScrubTiming(duration: 0.16)), value: hintLive)
        .allowsHitTesting(hintLive).accessibilityHidden(!hintLive)
    }
    private var controls: some View {
        let l = layout
        return ZStack(alignment: .topLeading) {
            control("sun", "Preview sunset", wide: false) { travel(day.sunset) }.offset(x: 31 * l.s)
            control("location", "Velora. Return to now", wide: true) { travel(now) }.offset(x: 114 * l.s)
            control("grid", scheduleOpen ? "Hide solar times" : "Show solar times", wide: false) {
                scheduleOpen.toggle(); gridCue()
            }.offset(x: 309 * l.s)
        }.offset(y: l.controlsY)
    }
    private func control(_ icon: String, _ label: String, wide: Bool, action: @escaping () -> Void) -> some View {
        let l = layout, radius = 31 * l.s, c = tint
        let rimAlpha = 0.35 * (0.2126 * c[3] + 0.7152 * c[4] + 0.0722 * c[5]) / 255
        return Button(action: action) {
            Group {
                if wide && l.largeText {
                    VStack(spacing: 8 * l.s) { controlIcon(icon); text("Velora", weight: "Regular", size: 13).foregroundStyle(R.color(0xF3F5F8)) }
                } else {
                    HStack(spacing: 8 * l.s) {
                        controlIcon(icon)
                        if wide { text("Velora", weight: "Regular", size: 13).foregroundStyle(R.color(0xF3F5F8)) }
                    }
                }
            }
            .padding(8 * l.s)
            .frame(width: (wide ? 175 : 62) * l.s, height: max(48, wide ? l.controlsH : 62 * l.s))
            .background(R.color(0x151619), in: RoundedRectangle(cornerRadius: radius))
            .overlay { RoundedRectangle(cornerRadius: radius).strokeBorder(R.color(0x292B30), lineWidth: 1 / displayScale) }
            .overlay {
                // RN borderTopWidth follows the rounded top quarter-arcs, rather than a rectangular cap.
                SkyTimeScrubTopRim(radius: radius).fill(R.rgb(c, 3, alpha: rimAlpha), style: FillStyle(eoFill: true))
                    .allowsHitTesting(false)
            }
        }
        .buttonStyle(SkyTimeScrubButtonStyle(reduce: reduce)).accessibilityLabel(label)
    }
    private func controlIcon(_ icon: String) -> some View {
        SkyTimeScrubIcon(kind: icon, tint: tint).frame(width: 20 * layout.s, height: 20 * layout.s)
    }
}

struct SkyTimeScrubButtonStyle: ButtonStyle {
    let reduce: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .modifier(SkyTimeScrubControlPress(scale: configuration.isPressed ? 0.97 : 1, reduce: reduce))
            .animation(reduce ? nil : .interpolatingSpring(mass: 1, stiffness: 322.3, damping: 30.5, initialVelocity: 0), value: configuration.isPressed)
    }
}

/// Opacity follows the spring's current value, including its release crossing, like the RN worklet.
struct SkyTimeScrubControlPress: AnimatableModifier {
    var scale: Double
    let reduce: Bool
    var animatableData: Double { get { scale } set { scale = newValue } }
    func body(content: Content) -> some View { content.scaleEffect(reduce ? 1 : scale).opacity(scale < 1 ? 0.7 : 1) }
}

/// Reanimated withTiming's default Easing.inOut(Easing.quad), without a Bézier approximation.
struct SkyTimeScrubTiming: CustomAnimation {
    var duration: Double
    func animate<V: VectorArithmetic>(value: V, time: TimeInterval, context: inout AnimationContext<V>) -> V? {
        guard time < duration else { return nil }
        let t = max(0, time / duration), k = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        var result = value; result.scale(by: k)
        return result
    }
}

struct SkyTimeScrubTopRim: Shape {
    let radius: Double
    func path(in rect: CGRect) -> Path {
        let r = min(radius, rect.height / 2)
        var p = Path()
        p.move(to: CGPoint(x: 0, y: r))
        p.addArc(center: CGPoint(x: r, y: r), radius: r, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        p.addLine(to: CGPoint(x: rect.width - r, y: 0))
        p.addArc(center: CGPoint(x: rect.width - r, y: r), radius: r, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
        p.closeSubpath()
        // RN's top-only border: the inner top radius loses 1 pt vertically and 0 horizontally.
        // The gap therefore tapers from 1 pt at the top to zero at the sides of the capsule.
        var inner = Path()
        inner.move(to: CGPoint(x: 0, y: r))
        inner.addArc(center: CGPoint(x: r, y: r), radius: r, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        inner.addLine(to: CGPoint(x: rect.width - r, y: 0))
        inner.addArc(center: CGPoint(x: rect.width - r, y: r), radius: r, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
        inner.closeSubpath()
        p.addPath(inner, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: (r - 1) / r, tx: 0, ty: 1))
        return p
    }
}

struct SkyTimeScrubIcon: View {
    let kind: String
    let tint: [Double]
    var body: some View {
        Canvas { c, size in
            var c = c
            c.scaleBy(x: size.width / (kind == "close" ? 16 : 24), y: size.height / (kind == "close" ? 16 : 24))
            let muted = SkyTimeScrubResources.color(0xAEB3C1)
            if kind == "location" {
                let points: [CGPoint] = [.init(x: 3, y: 10.3), .init(x: 21, y: 3), .init(x: 13.6, y: 21), .init(x: 11.1, y: 12.9)]
                var p = Path(); p.addLines(points); p.closeSubpath()
                let rgb = [SkyTimeScrubMotion.mix(68, tint[3], 0.2), SkyTimeScrubMotion.mix(194, tint[4], 0.2), SkyTimeScrubMotion.mix(245, tint[5], 0.2)]
                c.fill(p, with: .color(SkyTimeScrubResources.rgb(rgb)))
            } else if kind == "grid" {
                for rect in [CGRect(x: 3, y: 4, width: 10, height: 7), CGRect(x: 3, y: 14, width: 10, height: 6), CGRect(x: 16, y: 4, width: 5, height: 16)] {
                    c.fill(Path(roundedRect: rect, cornerRadius: 1), with: .color(muted))
                }
            } else if kind == "close" {
                c.fill(Path(ellipseIn: CGRect(x: 1, y: 1, width: 14, height: 14)), with: .color(SkyTimeScrubResources.color(0x15180A)))
                var p = Path(); p.move(to: .init(x: 5.5, y: 5.5)); p.addLine(to: .init(x: 10.5, y: 10.5))
                p.move(to: .init(x: 10.5, y: 5.5)); p.addLine(to: .init(x: 5.5, y: 10.5))
                c.stroke(p, with: .color(SkyTimeScrubResources.color(0xEEFA3C)), lineWidth: 1.4)
            } else {
                c.fill(Path(ellipseIn: CGRect(x: 8, y: 8, width: 8, height: 8)), with: .color(muted))
                let lines: [[Double]] = [[12, 2, 12, 4], [12, 20, 12, 22], [2, 12, 4, 12], [20, 12, 22, 12],
                                         [5, 5, 6.5, 6.5], [17.5, 17.5, 19, 19], [5, 19, 6.5, 17.5], [17.5, 6.5, 19, 5]]
                var p = Path()
                for line in lines { p.move(to: .init(x: line[0], y: line[1])); p.addLine(to: .init(x: line[2], y: line[3])) }
                c.stroke(p, with: .color(muted), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
            }
        }.accessibilityHidden(true)
    }
}
