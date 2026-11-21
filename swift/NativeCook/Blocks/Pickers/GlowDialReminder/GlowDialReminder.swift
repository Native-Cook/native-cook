import CoreText
import SwiftUI
import UIKit

/// Nocta's sleep-goal picker.  The glow is a Metal field rather than a static gradient: the ruler stirs it, then the
/// same pigment drains down the vein into the dial and blooms back into the numeral.  Automatic frames are pure
/// functions of the ten-second clock; touch adopts the visible value and returns to its rest pose after two seconds.
struct GlowDialReminder: View {
    var autoPlay = true

    static let loop = 10.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var mode: GlowDialReminderMode = .automatic
    @State private var loopStart = Date()
    @State private var value = 8.0
    @State private var enabled = true
    @State private var transfer = 0.0
    @State private var tracking = false
    @State private var isTouching = false
    @State private var dialAngle: Double?
    @State private var dialRawValue = 8.0
    @GestureState private var gestureActive = false
    @State private var startValue = 8.0
    @State private var returnToken = UUID()
    @State private var hapticPulse = 0

    private var qaTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-glow-time"), args.indices.contains(i + 1) { return Double(args[i + 1]) }
        #endif
        return nil
    }
    private var qaNeutral: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("-glow-neutral")
        #else
        return false
        #endif
    }

    private enum GlowDialReminderMode { case automatic, manual }

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
        GlowDialReminderFonts.register()
    }

    var body: some View {
        GeometryReader { safe in
            scene(safeBottom: safe.safeAreaInsets.bottom).ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .sensoryFeedback(.selection, trigger: hapticPulse)
        .onAppear { restartLoop() }
        .onChange(of: gestureActive) { _, active in
            if !active, isTouching {
                // A recognizer cancellation still settles the dial and releases autoplay ownership.
                isTouching = false
                dialAngle = nil
                setValue(value, animated: !reduceMotion)
                scheduleReturn()
            }
        }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced {
                mode = .manual
                value = 8
                enabled = true
                transfer = 0
            } else if autoPlay { restartLoop() }
        }
    }

    private func scene(safeBottom: CGFloat) -> some View {
        GeometryReader { geometry in
            let size = geometry.size
            let available = size.height - max(12, safeBottom)
            let scale = max(0.01, min(size.width / GlowDialReminderMetrics.width, available / 821, (available - 44) / 773))
            let contentWidth = 402 * scale
            let ctaHeight = max(44, 48 * scale)
            let ctaY = available - 5 * scale - ctaHeight / 2
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: paused)) { timeline in
                let frame = displayedFrame(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    Color.gdrPage

                    header(scale: scale)
                    card(frame: frame, size: CGSize(width: contentWidth, height: size.height), scale: scale)
                    labels(frame: frame, scale: scale)
                    GlowDialReminderDots(value: Int(frame.value.rounded()), off: frame.off, shear: frame.shear)
                        .frame(width: contentWidth, height: size.height)
                    GlowDialReminderRuler(value: frame.value, off: frame.off)
                        .frame(width: contentWidth, height: size.height)
                    GlowDialReminderKnob(frame: frame, scale: scale)
                        .frame(width: contentWidth, height: size.height)

                    dialTouch(scale: scale, now: timeline.date)
                    rulerTouch(scale: scale, now: timeline.date)
                    toggle(frame: frame, scale: scale, now: timeline.date)
                    cta(scale: scale, height: ctaHeight, centerY: ctaY, shownTracking: frame.tracking, now: timeline.date)
                }
                .frame(width: contentWidth, height: size.height)
                .clipped()
                .position(x: size.width / 2, y: size.height / 2)
            }
        }
        .background(Color.gdrPage)
    }

    private var paused: Bool {
        (mode == .automatic && qaTime != nil) || ((reduceMotion || !autoPlay) && mode == .manual && !isTouching)
    }

    private func displayedFrame(at date: Date) -> GlowDialReminderFrame {
        if mode == .automatic, let qaTime { return .frame(at: qaTime) }
        if reduceMotion || !autoPlay || qaNeutral || mode == .manual {
            return .manual(value: value, enabled: enabled, transfer: transfer, tracking: tracking, time: date.timeIntervalSinceReferenceDate)
        }
        return .frame(at: date.timeIntervalSince(loopStart))
    }

    private func header(scale: CGFloat) -> some View {
        ZStack {
            Circle().fill(Color(red: 0.106, green: 0.106, blue: 0.106))
                .frame(width: 48 * scale, height: 48 * scale)
            Image(systemName: "arrow.left")
                .font(.system(size: 18 * scale, weight: .medium))
                .foregroundStyle(Color.gdrTitle)
        }
        .position(x: 56 * scale, y: 99 * scale)
        .accessibilityLabel("Back")
        .accessibilityAddTraits(.isButton)
    }

    private func card(frame: GlowDialReminderFrame, size: CGSize, scale: CGFloat) -> some View {
        let rect = CGRect(x: 4 * scale, y: 143 * scale, width: 394 * scale, height: max(0, size.height - 143 * scale + 46 * scale))
        return ZStack {
            RoundedRectangle(cornerRadius: 46 * scale, style: .continuous)
                .fill(Color.gdrCard)
                .colorEffect(
                    ShaderLibrary.glowDialReminderField(
                        .float2(rect.size), .float2(197 * scale, 257 * scale),
                        .float((0.62 + 0.095 * (frame.value - 4)) * frame.pool), .float(frame.shear),
                        .float(frame.ring), .float(frame.off), .float(frame.plug), .float(frame.ember)
                    )
                )
            Image(uiImage: GlowDialReminderAssets.grain)
                .resizable(resizingMode: .tile)
                .opacity(0.055)
                .blendMode(.overlay)
        }
        .frame(width: rect.width, height: rect.height)
        .clipShape(RoundedRectangle(cornerRadius: 46 * scale, style: .continuous))
        .position(x: rect.midX, y: rect.midY)
    }

    @ViewBuilder
    private func labels(frame: GlowDialReminderFrame, scale: CGFloat) -> some View {
        text("Complete setup", face: .regular, size: 24, color: .gdrTitle, scale: scale)
            .position(x: 185 * scale, y: 99 * scale)
        text("Reminder", face: .regular, size: 24, color: .gdrInkCold, scale: scale)
            .position(x: 112 * scale, y: 216 * scale)
        text(frame.value == 1 ? "hour" : "hours", face: .medium, size: 15,
             color: frame.off > 0.5 ? .gdrInkCold : .gdrInkSoft, scale: scale)
            .position(x: 201 * scale, y: 485 * scale)
        text("tue", face: .medium, size: 15, color: .gdrInkCold, scale: scale)
            .position(x: 65 * scale, y: 664 * scale)
        text("25", face: .doto, size: 22, color: .gdrInkCold, scale: scale)
            .position(x: 65 * scale, y: 700 * scale)

        GlowDialReminderMiniClock(frame: frame, scale: scale)
        Rectangle().fill(Color.gdrMarker).frame(width: 2.5 * scale, height: 34 * scale)
            .clipShape(Capsule()).position(x: 201 * scale, y: 522 * scale)
        GlowDialReminderPageDots(scale: scale).position(x: 201 * scale, y: 606 * scale)
    }

    private func text(_ content: String, face: GlowDialReminderFontFace, size: CGFloat, color: Color, scale: CGFloat) -> some View {
        Text(content)
            .font(GlowDialReminderFonts.font(face, size: size * scale))
            .foregroundStyle(color)
            .fixedSize()
    }

    private func dialTouch(scale: CGFloat, now: Date) -> some View {
        Color.clear
            .contentShape(Circle())
            .frame(width: 132 * scale, height: 132 * scale)
            .position(x: 306 * scale, y: 680 * scale)
            .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
                .updating($gestureActive) { _, active, _ in active = true }
                .onChanged { gesture in
                    if !isTouching { beginManual(at: now); dialRawValue = value; dialAngle = nil }
                    isTouching = true
                    let p = CGPoint(x: gesture.location.x - 66 * scale, y: gesture.location.y - 66 * scale)
                    let angle = atan2(p.y, p.x)
                    let previous = dialAngle ?? angle
                    let delta = atan2(sin(angle - previous), cos(angle - previous))
                    dialAngle = angle
                    dialRawValue = min(max(dialRawValue + delta * 180 / .pi / 30, 4), 12)
                    setValue(dialRawValue, animated: false)
                }
                .onEnded { _ in
                    setValue(dialRawValue, animated: !reduceMotion)
                    dialAngle = nil
                    isTouching = false
                    scheduleReturn()
                })
            .accessibilityElement()
            .accessibilityLabel("Sleep goal")
            .accessibilityValue("\(String(format: "%.2g", value)) hours")
            .accessibilityHint("Rotate to change the sleep duration")
            .accessibilityAdjustableAction { direction in
                beginManual(at: now)
                setValue(value + (direction == .increment ? 0.25 : -0.25), animated: !reduceMotion)
                scheduleReturn()
            }
    }

    private func rulerTouch(scale: CGFloat, now: Date) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .frame(width: 362 * scale, height: 70 * scale)
            .position(x: 201 * scale, y: 520 * scale)
            .gesture(DragGesture(minimumDistance: 0)
                .updating($gestureActive) { _, active, _ in active = true }
                .onChanged { gesture in
                    if !isTouching { beginManual(at: now); startValue = value }
                    isTouching = true
                    setValue(startValue - gesture.translation.width / scale / 72, animated: false)
                }
                .onEnded { _ in
                    startValue = value
                    isTouching = false
                    scheduleReturn()
                })
            .accessibilityLabel("Duration ruler")
            .accessibilityValue("\(String(format: "%.2g", value)) hours")
    }

    private func toggle(frame: GlowDialReminderFrame, scale: CGFloat, now: Date) -> some View {
        let shownEnabled = mode == .manual ? enabled : frame.off < 0.5
        return Button {
            beginManual(at: now)
            enabled.toggle()
            withAnimation(reduceMotion ? .easeInOut(duration: 0.2) : .easeInOut(duration: 1.2)) {
                transfer = enabled ? 0 : 1
            }
            hapticPulse += 1
            scheduleReturn()
        } label: {
            ZStack {
                Capsule().fill(shownEnabled ? Color.gdrTrackOn : Color.gdrTrackOff)
                Text(shownEnabled ? "On" : "Off")
                    .font(GlowDialReminderFonts.font(.medium, size: 13 * scale))
                    .foregroundStyle(shownEnabled ? Color.gdrToggleLabel : Color.gdrCtaGlyph)
                    .offset(x: shownEnabled ? -17 * scale : 17 * scale)
                Circle().fill(Color.gdrThumb)
                    .shadow(color: Color.gdrCapShadow.opacity(0.3), radius: 3 * scale, y: 1.5 * scale)
                    .frame(width: 30 * scale, height: 30 * scale)
                    .offset(x: shownEnabled ? 26 * scale : -26 * scale)
            }
            .frame(width: 90 * scale, height: 42 * scale)
        }
        .buttonStyle(.plain)
        .position(x: 307 * scale, y: 216 * scale)
        .accessibilityLabel("Reminder")
        .accessibilityValue(shownEnabled ? "On" : "Off")
    }

    private func cta(scale: CGFloat, height: CGFloat, centerY: CGFloat, shownTracking: Bool, now: Date) -> some View {
        Button {
            beginManual(at: now)
            tracking.toggle()
            hapticPulse += 1
            scheduleReturn()
        } label: {
            ZStack(alignment: .leading) {
                Text(shownTracking ? "Tracking" : "Start tracking")
                    .font(GlowDialReminderFonts.font(.medium, size: 14 * scale))
                    .padding(.leading, 32 * scale)
                GlowDialReminderTrackingMark(progress: shownTracking ? 1 : 0)
                    .stroke(Color.gdrCtaGlyph, style: StrokeStyle(lineWidth: 2 * scale, lineCap: .round, lineJoin: .round))
                    .frame(width: 16 * scale, height: 16 * scale)
                    .frame(width: 34 * scale, height: 34 * scale)
                    .background(Color.gdrCtaDisc, in: Circle())
                    .position(x: 178 * scale, y: height / 2)
            }
            .foregroundStyle(Color.gdrInk)
            .frame(width: 200 * scale, height: height)
            .background(shownTracking ? Color.gdrCtaOn : Color.gdrCta, in: Capsule())
        }
        .buttonStyle(.plain)
        .position(x: 208 * scale, y: centerY)
        .accessibilityLabel(shownTracking ? "Tracking" : "Start tracking")
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.24), value: shownTracking)
    }

    private func beginManual(at date: Date) {
        returnToken = UUID()
        guard mode == .automatic else { return }
        let frame = displayedFrame(at: date)
        value = frame.value
        enabled = frame.off < 0.5
        tracking = frame.tracking
        transfer = frame.off
        startValue = value
        mode = .manual
        returnToken = UUID()
    }

    private func setValue(_ raw: Double, animated: Bool) {
        let snapped = (min(max(raw, 4), 12) * 4).rounded() / 4
        if animated { withAnimation(.spring(response: 0.36, dampingFraction: 0.76)) { value = snapped } }
        else { value = snapped }
    }

    private func scheduleReturn() {
        guard autoPlay, !reduceMotion, !qaNeutral, qaTime == nil else { return }
        let token = UUID()
        returnToken = token
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            guard token == returnToken, !isTouching else { return }
            withAnimation(.spring(response: 0.6, dampingFraction: 0.85)) {
                value = 8; enabled = true; transfer = 0; tracking = false
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.65) {
                guard token == returnToken, !isTouching else { return }
                restartLoop()
            }
        }
    }

    private func restartLoop() {
        loopStart = Date()
        value = 8; enabled = true; transfer = 0; tracking = false
        mode = autoPlay && !reduceMotion && !qaNeutral ? .automatic : .manual
    }
}

private struct GlowDialReminderTrackingMark: Shape {
    var progress: CGFloat
    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let unit = rect.width / 16
        let k = min(1, max(0, progress))
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.midX + unit * x, y: rect.midY + unit * y)
        }
        var mark = Path()
        mark.move(to: point(-6, 0))
        mark.addLine(to: point(6 - 7.5 * k, 4.5 * k))
        mark.addLine(to: point(6 + k, -5 * k))
        if k < 1 {
            mark.move(to: point(0, -6 * (1 - k)))
            mark.addLine(to: point(0, 6 * (1 - k)))
        }
        return mark
    }
}

private struct GlowDialReminderFrame {
    let time: Double
    let value: Double
    let shear: Double
    let pool: Double
    let plug: Double
    let ember: Double
    let off: Double
    let ring: Double
    let tracking: Bool

    static func manual(value: Double, enabled: Bool, transfer: Double, tracking: Bool, time: Double) -> Self {
        Self(time: time, value: value, shear: 0, pool: max(0, 1 - transfer), plug: transfer, ember: transfer,
             off: enabled ? 0 : 1, ring: 0, tracking: tracking)
    }

    static func frame(at raw: Double) -> Self {
        let t = wrap(raw)
        let value: Double
        if t < 0.25 { value = 8 }
        else if t < 1.65 { value = hermite(t, 0.25, 1.65, 8, 11) }
        else if t < 2.4 { value = 11 + 0.10 * exp(-(t - 1.65) * 6) * sin((t - 1.65) * 15) }
        else if t < 2.6 { value = 11 }
        else if t < 3.9 { value = 11 - 5 * smooth(segment(t, 2.6, 3.9)) }
        else if t < 6.8 { value = 6 }
        else if t < 8.55 { value = 6 + 2.75 * smooth(segment(t, 6.8, 8.55)) }
        else if t < 9.5 { value = 8.75 - 0.75 * smooth(segment(t, 8.55, 9.5)) }
        else { value = 8 }
        let drain = segment(t, 4.5, 5.7)
        let bloom = segment(t, 6.05, 7.25)
        let off = t < 5 ? 0 : t < 6.4 ? smooth(segment(t, 5, 5.4)) : 1 - smooth(segment(t, 6.4, 6.8))
        let pool = t < 4.5 ? 1 : t < 5.7 ? sqrt(max(0, 1 - drain)) : t < 6.4 ? 0 : min(1.06, 1.06 * smooth(bloom))
        let plug = t < 4.5 ? 0 : t < 6.05 ? drain : max(0, 1 - bloom)
        let ember = t < 4.7 ? 0 : t < 6.05 ? smooth(segment(t, 4.7, 5.65)) : max(0, 1 - smooth(bloom))
        let moving = (t > 0.1 && t < 2.4) || (t > 2.6 && t < 3.9) || (t > 6.8 && t < 9.5)
        let shear = moving ? 1.1 * sin((t - 0.2) * 3.7) * (t < 4 ? 1 : 0.65) : 0
        let ring: Double = (t > 1.95 && t < 2.6) || (t > 9.35 && t < 9.95) ? 1 : 0
        return Self(time: t, value: value, shear: shear, pool: pool, plug: plug, ember: ember, off: off, ring: ring, tracking: false)
    }

    private static func wrap(_ t: Double) -> Double { let r = t.truncatingRemainder(dividingBy: GlowDialReminder.loop); return r < 0 ? r + GlowDialReminder.loop : r }
    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    private static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
    private static func hermite(_ t: Double, _ a: Double, _ b: Double, _ x: Double, _ y: Double) -> Double { x + (y - x) * smooth(segment(t, a, b)) }
}

private struct GlowDialReminderDots: View {
    let value: Int
    let off: Double
    let shear: Double

    var body: some View {
        Canvas { context, size in
            let s = size.width / 402
            let digits = Array(String(min(max(value, 4), 12)))
            let pitch = 12.04 * s, dot = 9.24 * s
            let glyphWidth = 4 * pitch + dot
            let start = size.width / 2 - (digits.count == 1 ? glyphWidth : glyphWidth * 1.72) / 2
            for (index, digit) in digits.enumerated() {
                let base = start + CGFloat(index) * glyphWidth * 1.72
                for cell in GlowDialReminderDigits.points(for: digit) {
                    let x = base + CGFloat(cell.0) * pitch + dot / 2
                    let y = 359.3 * s + CGFloat(cell.1) * pitch + dot / 2
                    let dx = x - size.width / 2, dy = y - 400 * s
                    let angle = 0.09 * shear
                    let p = CGPoint(x: size.width / 2 + dx * cos(angle) - dy * sin(angle), y: 400 * s + dx * sin(angle) + dy * cos(angle))
                    let rect = CGRect(x: p.x - dot / 2, y: p.y - dot / 2, width: dot, height: dot)
                    if off > 0.45 {
                        context.stroke(Path(ellipseIn: rect), with: .color(.gdrInkCold.opacity(0.78)), lineWidth: max(1, 1.35 * s))
                    } else {
                        context.fill(Path(ellipseIn: rect), with: .color(.gdrDot.opacity(0.94)))
                    }
                }
            }
        }
        .allowsHitTesting(false)
    }
}

private struct GlowDialReminderRuler: View {
    let value: Double
    let off: Double

    var body: some View {
        Canvas { context, size in
            let s = size.width / 402
            for i in -120...120 {
                let x = (201 + CGFloat(i) * 6 - CGFloat(value - 8) * 72) * s
                guard x >= 77 * s && x <= 328 * s else { continue }
                let major = i % 4 == 0
                let height = (major ? 26 : 16) * s
                let tint = off > 0.5 ? Color.gdrInkCold.opacity(0.52) : Color.gdrTick.opacity(0.62)
                context.stroke(Path { p in p.move(to: CGPoint(x: x, y: 535 * s)); p.addLine(to: CGPoint(x: x, y: 535 * s - height)) }, with: .color(tint), lineWidth: 2 * s)
            }
        }
        .allowsHitTesting(false)
    }
}

private struct GlowDialReminderKnob: View {
    let frame: GlowDialReminderFrame
    let scale: CGFloat

    var body: some View {
        Canvas { context, _ in
            let c = CGPoint(x: 306 * scale, y: 680 * scale)
            let angle = (frame.value - 8) * 30 * .pi / 180
            for i in 0..<36 {
                let a = CGFloat(i) * 2 * .pi / 36 + CGFloat(angle)
                let long = i % 3 == 0
                let r0 = 50 * scale, r1 = (long ? 63 : 58) * scale
                let p0 = CGPoint(x: c.x + sin(a) * r0, y: c.y - cos(a) * r0)
                let p1 = CGPoint(x: c.x + sin(a) * r1, y: c.y - cos(a) * r1)
                let lit = frame.ember > 0.1 && i < Int(frame.ember * 36)
                context.stroke(Path { p in p.move(to: p0); p.addLine(to: p1) }, with: .color(lit ? .gdrTickHold : .gdrKnobTick), lineWidth: 2 * scale)
            }
            let halo = CGRect(x: c.x - (35 + 15 * frame.ember) * scale, y: c.y - (35 + 15 * frame.ember) * scale,
                              width: 2 * (35 + 15 * frame.ember) * scale, height: 2 * (35 + 15 * frame.ember) * scale)
            context.fill(Path(ellipseIn: halo), with: .color(Color.gdrHalo.opacity(0.25 + 0.45 * frame.ember)))
            let cap = CGRect(x: c.x - 32 * scale, y: c.y - 32 * scale, width: 64 * scale, height: 64 * scale)
            context.fill(Path(ellipseIn: cap), with: .linearGradient(Gradient(colors: frame.ember > 0.15 ? [.gdrEmber0, .gdrEmber1] : [.gdrCapLight, .gdrCapShade]), startPoint: CGPoint(x: cap.minX + 8 * scale, y: cap.minY + 5 * scale), endPoint: CGPoint(x: cap.maxX, y: cap.maxY)))
            context.stroke(Path(ellipseIn: cap.insetBy(dx: scale, dy: scale)), with: .color(.gdrCapLight.opacity(0.9)), lineWidth: scale)
            let slit0 = CGPoint(x: c.x + sin(angle) * 17 * scale, y: c.y - cos(angle) * 17 * scale)
            let slit1 = CGPoint(x: c.x + sin(angle) * 28 * scale, y: c.y - cos(angle) * 28 * scale)
            context.stroke(Path { p in p.move(to: slit0); p.addLine(to: slit1) }, with: .color(frame.ember > 0.15 ? .gdrSlitHold : .gdrSlitOn), style: StrokeStyle(lineWidth: 3 * scale, lineCap: .round))
        }
        .allowsHitTesting(false)
    }
}

private struct GlowDialReminderMiniClock: View {
    let frame: GlowDialReminderFrame
    let scale: CGFloat

    var body: some View {
        let minutes = (360 - Int((frame.value * 4).rounded()) * 15 + 1440) % 1440
        let hour = (minutes / 60) % 12 == 0 ? 12 : (minutes / 60) % 12
        return ZStack {
            Circle().fill(Color.gdrHourPie).frame(width: 30 * scale, height: 30 * scale)
            Circle().fill(frame.off > 0.5 ? Color.gdrPieCold : Color.gdrMinutePie).frame(width: 30 * scale, height: 30 * scale).offset(y: 38 * scale)
            VStack(alignment: .leading, spacing: 4 * scale) {
                Text("\(hour)").font(GlowDialReminderFonts.font(.medium, size: 16 * scale))
                Text(String(format: "%02d", minutes % 60)).font(GlowDialReminderFonts.font(.medium, size: 16 * scale))
            }.foregroundStyle(Color.gdrInkCold).offset(x: 45 * scale, y: 19 * scale)
        }
        .position(x: 135 * scale, y: 684 * scale)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Bedtime \(hour):\(String(format: "%02d", minutes % 60))")
    }
}

private struct GlowDialReminderPageDots: View {
    let scale: CGFloat
    var body: some View {
        HStack(spacing: 4 * scale) {
            Circle().fill(.white).frame(width: 5 * scale, height: 5 * scale)
            ForEach(0..<3, id: \.self) { _ in Circle().fill(Color.gdrPageDotOff).frame(width: 3 * scale, height: 3 * scale) }
        }
    }
}

private enum GlowDialReminderDigits {
    static let map: [Character: [(Int, Int)]] = [
        "0": [(0,0),(1,0),(2,0),(3,0),(4,1),(4,2),(4,4),(4,5),(0,4),(0,5),(0,6),(1,6),(2,6),(3,6)],
        "1": [(2,0),(2,1),(2,2),(2,3),(2,4),(2,5),(2,6),(1,1),(1,6),(3,6)],
        "2": [(0,0),(1,0),(2,0),(3,0),(4,1),(4,2),(0,3),(1,3),(2,3),(3,3),(0,4),(0,5),(0,6),(1,6),(2,6),(3,6)],
        "3": [(0,0),(1,0),(2,0),(3,0),(4,1),(4,2),(0,3),(1,3),(2,3),(3,3),(4,4),(4,5),(0,6),(1,6),(2,6),(3,6)],
        "4": [(0,1),(0,2),(4,1),(4,2),(0,3),(1,3),(2,3),(3,3),(4,3),(4,4),(4,5),(4,6)],
        "5": [(0,0),(1,0),(2,0),(3,0),(0,1),(0,2),(0,3),(1,3),(2,3),(3,3),(4,4),(4,5),(0,6),(1,6),(2,6),(3,6)],
        "6": [(0,0),(1,0),(2,0),(3,0),(0,1),(0,2),(0,3),(1,3),(2,3),(3,3),(0,4),(0,5),(4,4),(4,5),(0,6),(1,6),(2,6),(3,6)],
        "7": [(0,0),(1,0),(2,0),(3,0),(4,1),(4,2),(4,3),(4,4),(4,5),(4,6)],
        "8": [(0,0),(1,0),(2,0),(3,0),(0,1),(0,2),(4,1),(4,2),(0,3),(1,3),(2,3),(3,3),(0,4),(0,5),(4,4),(4,5),(0,6),(1,6),(2,6),(3,6)],
        "9": [(0,0),(1,0),(2,0),(3,0),(0,1),(0,2),(4,1),(4,2),(0,3),(1,3),(2,3),(3,3),(4,4),(4,5),(0,6),(1,6),(2,6),(3,6)],
    ]
    static func points(for digit: Character) -> [(Int, Int)] { map[digit] ?? [] }
}

private enum GlowDialReminderFonts {
    static func font(_ face: GlowDialReminderFontFace, size: CGFloat) -> Font {
        .custom(face.name, size: size)
    }
    static func register() { _ = registered }
    private static let registered: Bool = {
        ["GlowDialReminder-Urbanist-Regular", "GlowDialReminder-Urbanist-Medium", "GlowDialReminder-Doto-Bold"].forEach {
            if let url = Bundle.main.url(forResource: $0, withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
        return true
    }()
}

private enum GlowDialReminderFontFace {
    case regular, medium, doto
    var name: String { switch self { case .regular: "Urbanist-Regular"; case .medium: "Urbanist-Medium"; case .doto: "Doto-Bold" } }
}

private enum GlowDialReminderAssets {
    static let grain: UIImage = {
        guard let url = Bundle.main.url(forResource: "GlowDialReminder-gd-grain", withExtension: "png"), let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image
    }()
}

private enum GlowDialReminderMetrics { static let width: CGFloat = 402 }

private extension Color {
    static let gdrPage = Color(hex: 0x0A090A), gdrCard = Color(hex: 0xC8A1C0), gdrTitle = Color(hex: 0xF2F0F2)
    static let gdrInk = Color(hex: 0xFBF3FA), gdrInkSoft = Color(hex: 0xF6E3F0), gdrInkCold = Color(hex: 0x56314F)
    static let gdrTrackOn = Color(hex: 0x865F7D), gdrTrackOff = Color(hex: 0xA58FA1), gdrToggleLabel = Color(hex: 0xF3E6F0), gdrThumb = Color(hex: 0xFCF8FB)
    static let gdrMarker = Color(hex: 0xF4E84A), gdrDot = Color(hex: 0xE9C6DF), gdrTick = Color(hex: 0xF3C3DA), gdrKnobTick = Color(hex: 0xF7E9F3), gdrTickHold = Color(hex: 0xF2A6CC)
    static let gdrHourPie = Color(hex: 0xFCF8FB), gdrMinutePie = Color(hex: 0xE0338A), gdrPieCold = Color(hex: 0xB48AAB), gdrPageDotOff = Color(hex: 0xF1DCEB)
    static let gdrHalo = Color(hex: 0x5A1D45), gdrCapLight = Color(hex: 0xFBFAFB), gdrCapShade = Color(hex: 0xE4DDE3), gdrCapShadow = Color(hex: 0x4A1238)
    static let gdrEmber0 = Color(hex: 0xE0338A), gdrEmber1 = Color(hex: 0x8A1E4F), gdrSlitOn = Color(hex: 0xC12F6E), gdrSlitHold = Color(hex: 0xFF6FB5)
    static let gdrCta = Color(hex: 0x80617B), gdrCtaOn = Color(hex: 0xB6236A), gdrCtaDisc = Color(hex: 0xFCF8FB), gdrCtaGlyph = Color(hex: 0x3A2436)
    init(hex: UInt32) { self.init(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255) }
}
