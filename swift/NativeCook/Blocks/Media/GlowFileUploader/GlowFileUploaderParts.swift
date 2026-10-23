import SwiftUI

private typealias M = GlowFileUploaderMotion

/// The colours of the block, and a blend between two of them.
enum GlowFileUploaderPalette {
    static let background = Color(hex: 0x0E1013)
    static let zone = Color(hex: 0x1B1E22)
    static let badge = Color(hex: 0x0F1114)
    static let badgeBorder = Color(hex: 0xD9DADC)
    static let ink = Color(hex: 0xF4F4F5)
    static let text = Color(hex: 0xE8E9EA)
    static let hint = Color(hex: 0x7C7F85)
    static let link = Color(hex: 0x9A9DA2)
    static let pill = Color(hex: 0x23262A, opacity: 0.85)
    static let pillBorder = Color(hex: 0x474A4F)
    static let row = Color(hex: 0x181B1F)
    static let rowFill = Color(hex: 0x22262B)
    static let rowFailFill = Color(hex: 0x3A1A1C, opacity: 0.7)
    static let rowGlyph = Color(hex: 0x7E8187)
    static let doneDisc = Color(hex: 0x1E3A23)
    static let green = Color(hex: 0x46C35A)
    static let failPill = Color(hex: 0x3A1A1C)
    static let red = Color(hex: 0xE5484D)
    static let failText = Color(hex: 0xFF5F57)
    static let retry = Color(hex: 0x8B8E93)
    static let cancel = Color(hex: 0x191C20)
    static let cancelBorder = Color(hex: 0x3A3D42)
    static let proceedOff: UInt32 = 0x202327
    static let proceedOn: UInt32 = 0x2A2E33
    static let proceedLabelOff: UInt32 = 0x5E6166
    static let proceedLabelOn: UInt32 = 0xF4F4F5
    static let titleTop = Color(hex: 0x4C4F55)
    static let titleBottom = Color(hex: 0xD3D4D6)

    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ v: UInt32, _ shift: UInt32) -> Double { Double((v >> shift) & 0xff) / 255 }
        func lerp(_ shift: UInt32) -> Double { channel(a, shift) + (channel(b, shift) - channel(a, shift)) * t }
        return Color(.sRGB, red: lerp(16), green: lerp(8), blue: lerp(0), opacity: 1)
    }
}

private typealias P = GlowFileUploaderPalette

// MARK: Glyphs (drawn on a 24-point grid and scaled to their frame)

private func glowFileUploaderArc(_ path: inout Path, _ c: CGPoint, _ r: Double, from a0: Double, to a1: Double) {
    let steps = 24
    for i in 0...steps {
        let a = (a0 + (a1 - a0) * Double(i) / Double(steps)) * .pi / 180
        path.addLine(to: CGPoint(x: c.x + r * cos(a), y: c.y + r * sin(a)))
    }
}

/// A paperclip: an inner loop at the bottom, a loop at the top and the outer loop at the bottom,
/// laid along the "/" diagonal like the design.
struct GlowFileUploaderClip: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 9.5, y: 8))
        glowFileUploaderArc(&p, CGPoint(x: 12, y: 14), 2.5, from: 180, to: 0)
        glowFileUploaderArc(&p, CGPoint(x: 11, y: 7), 3.5, from: 0, to: -180)
        glowFileUploaderArc(&p, CGPoint(x: 12, y: 16.5), 4.5, from: 180, to: 0)
        p.addLine(to: CGPoint(x: 16.5, y: 8.5))
        return p.applying(glowFileUploaderGrid(rect, turn: 45))
    }
}

/// Two arrows chasing each other round a circle (retry).
struct GlowFileUploaderArrows: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let c = CGPoint(x: 12, y: 12)
        let r = 7.5
        for (a0, a1) in [(200.0, 330.0), (20.0, 150.0)] {
            let start = a0 * .pi / 180
            p.move(to: CGPoint(x: c.x + r * cos(start), y: c.y + r * sin(start)))
            glowFileUploaderArc(&p, c, r, from: a0, to: a1)
        }
        return p.applying(glowFileUploaderGrid(rect, turn: 0))
    }
}

/// The arrowheads at the ends of the arrows' arcs, filled.
struct GlowFileUploaderArrowHeads: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let c = CGPoint(x: 12, y: 12)
        let r = 7.5
        for end in [330.0, 150.0] {
            let a = end * .pi / 180
            let tip = CGPoint(x: c.x + r * cos(a), y: c.y + r * sin(a))
            // The arc runs clockwise, so the head points along (-sin, cos), with a little overshoot.
            let dir = CGPoint(x: -sin(a), y: cos(a))
            let out = CGPoint(x: cos(a), y: sin(a))
            let front = CGPoint(x: tip.x + dir.x * 2.6, y: tip.y + dir.y * 2.6)
            let back = CGPoint(x: tip.x - dir.x * 0.6, y: tip.y - dir.y * 0.6)
            p.move(to: front)
            p.addLine(to: CGPoint(x: back.x + out.x * 3, y: back.y + out.y * 3))
            p.addLine(to: CGPoint(x: back.x - out.x * 3, y: back.y - out.y * 3))
            p.closeSubpath()
        }
        return p.applying(glowFileUploaderGrid(rect, turn: 0))
    }
}

struct GlowFileUploaderCheck: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 6, y: 12.5))
        p.addLine(to: CGPoint(x: 10, y: 16.5))
        p.addLine(to: CGPoint(x: 18, y: 7.5))
        return p.applying(glowFileUploaderGrid(rect, turn: 0))
    }
}

/// The "!" of the failed pill: a bar and a dot.
struct GlowFileUploaderBang: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.addRoundedRect(in: CGRect(x: 10.6, y: 5.5, width: 2.8, height: 8.5), cornerSize: CGSize(width: 1.4, height: 1.4))
        p.addEllipse(in: CGRect(x: 10.4, y: 15.6, width: 3.2, height: 3.2))
        return p.applying(glowFileUploaderGrid(rect, turn: 0))
    }
}

/// The 24-point grid to `rect`, turned about its centre by `turn` degrees (clockwise).
private func glowFileUploaderGrid(_ rect: CGRect, turn: Double) -> CGAffineTransform {
    let s = min(rect.width, rect.height) / 24
    return CGAffineTransform(translationX: -12, y: -12)
        .concatenating(CGAffineTransform(rotationAngle: turn * .pi / 180))
        .concatenating(CGAffineTransform(scaleX: s, y: s))
        .concatenating(CGAffineTransform(translationX: rect.midX, y: rect.midY))
}

// MARK: The drop zone's backdrop

/// The drop zone's fill, drifting hairlines, inner glow and the halo behind the clip badge: the
/// ambient parts, on a clock of their own so they keep moving while the rest of the screen is still.
struct GlowFileUploaderBackdrop: View {
    let origin: Double
    /// The flare after a file is added, 0 to 1.
    let flare: Double
    let still: Bool
    let radius: Double
    let badgeCenter: Double
    let badgeSize: Double

    var body: some View {
        TimelineView(.animation(paused: still)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate - origin
            let glow = M.breathe(t, still: still)
            let drift = M.drift(t, still: still)
            let halo = M.halo(t, still: still)
            Canvas { ctx, size in
                draw(&ctx, size: size, glow: glow, drift: drift, halo: halo)
            }
        }
        .accessibilityHidden(true)
    }

    private func draw(_ ctx: inout GraphicsContext, size: CGSize, glow: Double, drift: Double, halo: (scale: Double, opacity: Double)) {
        let rect = CGRect(origin: .zero, size: size)
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous).path(in: rect)
        ctx.clip(to: shape)
        ctx.fill(shape, with: .color(P.zone))

        // Hairlines running "/" at 45°, `GlowFileUploaderZoneMetrics.lineGap` apart, drifting right.
        let step = GlowFileUploaderZoneMetrics.lineGap * 2.squareRoot()
        var lines = Path()
        var x = -size.height + drift * step - step
        while x < size.width + step {
            lines.move(to: CGPoint(x: x, y: size.height))
            lines.addLine(to: CGPoint(x: x + size.height, y: 0))
            x += step
        }
        ctx.stroke(lines, with: .color(.white.opacity(GlowFileUploaderZoneMetrics.lineOpacity)), lineWidth: GlowFileUploaderZoneMetrics.lineWidth)

        // The inner glow: the outline drawn as two white lines, a thin one blurred a little and a wide
        // one blurred a lot, clipped to the shape, so the light is strongest at the rim and gone about
        // 26 points in; a crisp rim on it; a little weaker along the top. The flare lays a second one
        // over it.
        let z = GlowFileUploaderZoneMetrics.self
        for (index, amount) in [glow, M.flareGain * flare].enumerated() where amount > 0.001 {
            ctx.drawLayer { layer in
                layer.opacity = amount
                for (width, blur) in [(z.glowWidth, z.glowBlur), (z.coreWidth, z.coreBlur)] {
                    layer.drawLayer { blurred in
                        blurred.addFilter(.blur(radius: blur))
                        blurred.stroke(shape, with: .color(.white), lineWidth: width)
                    }
                }
                if index == 0 {
                    let rim = RoundedRectangle(cornerRadius: radius - 0.5, style: .continuous).path(in: rect.insetBy(dx: 0.5, dy: 0.5))
                    layer.stroke(rim, with: .color(.white.opacity(z.rimOpacity)), lineWidth: 1)
                }
                layer.blendMode = .destinationIn
                layer.fill(
                    Path(rect),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: .white.opacity(z.glowTop), location: 0),
                            .init(color: .white, location: z.glowFull),
                        ]),
                        startPoint: .zero,
                        endPoint: CGPoint(x: 0, y: size.height)
                    )
                )
            }
        }

        // The halo behind the badge.
        let d = GlowFileUploaderZoneMetrics.haloSize * halo.scale
        ctx.drawLayer { layer in
            layer.opacity = halo.opacity
            layer.addFilter(.blur(radius: GlowFileUploaderZoneMetrics.haloBlur))
            layer.fill(Path(ellipseIn: CGRect(x: size.width / 2 - d / 2, y: badgeCenter - d / 2, width: d, height: d)), with: .color(.white))
        }
    }
}

enum GlowFileUploaderZoneMetrics {
    static let lineGap = 10.0
    static let lineWidth = 1.0
    static let lineOpacity = 0.028
    /// The wide line and its blur, and the thin bright one at the rim.
    static let glowWidth = 13.0
    static let glowBlur = 11.0
    static let coreWidth = 3.3
    static let coreBlur = 3.5
    /// The glow's strength at the top, against 1 from `glowFull` of the height down.
    static let glowTop = 0.78
    static let glowFull = 0.45
    static let rimOpacity = 0.25
    static let haloSize = 46.0
    static let haloBlur = 8.0
}

// MARK: A row of the list

struct GlowFileUploaderRow: View {
    let name: String
    let pose: GlowFileUploaderMotion.RowPose
    let width: Double
    let retryScale: Double
    let onRetryPress: (Bool) -> Void
    let onRetry: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: GlowFileUploaderRowMetrics.radius, style: .continuous)
        ZStack(alignment: .leading) {
            shape.fill(P.row)
            fill
            HStack(spacing: 0) {
                GlowFileUploaderClip()
                    .stroke(P.rowGlyph, style: StrokeStyle(lineWidth: GlowFileUploaderRowMetrics.glyphStroke, lineCap: .round, lineJoin: .round))
                    .frame(width: GlowFileUploaderRowMetrics.glyph, height: GlowFileUploaderRowMetrics.glyph)
                    .padding(.leading, GlowFileUploaderRowMetrics.glyphCenter - GlowFileUploaderRowMetrics.glyph / 2)
                Text(name)
                    .font(.system(size: GlowFileUploaderRowMetrics.nameSize))
                    .foregroundStyle(P.text)
                    .lineLimit(1)
                    .padding(.leading, GlowFileUploaderRowMetrics.nameLeft - GlowFileUploaderRowMetrics.glyphCenter - GlowFileUploaderRowMetrics.glyph / 2)
                if pose.pillOpacity > 0 {
                    failedPill.padding(.leading, GlowFileUploaderRowMetrics.pillGap)
                }
                Spacer(minLength: GlowFileUploaderRowMetrics.pillGap)
                status.padding(.trailing, GlowFileUploaderRowMetrics.statusRight)
            }
        }
        .frame(width: width, height: GlowFileUploaderRowMetrics.height)
        .clipShape(shape)
    }

    private var fill: some View {
        let w = width * pose.fill
        return ZStack(alignment: .leading) {
            Rectangle().fill(P.rowFill).opacity(1 - pose.fillRed)
            Rectangle().fill(P.rowFailFill).opacity(pose.fillRed)
        }
        .frame(width: w)
        .overlay(alignment: .trailing) {
            LinearGradient(colors: [.white.opacity(0), .white.opacity(GlowFileUploaderRowMetrics.edgeOpacity)], startPoint: .leading, endPoint: .trailing)
                .frame(width: GlowFileUploaderRowMetrics.edgeWidth)
                .opacity(pose.edge)
        }
        .opacity(pose.fillOpacity)
        .frame(width: width, alignment: .leading)
    }

    private var failedPill: some View {
        HStack(spacing: 5) {
            ZStack {
                Circle().fill(P.red)
                GlowFileUploaderBang().fill(.white)
            }
            .frame(width: GlowFileUploaderRowMetrics.alert, height: GlowFileUploaderRowMetrics.alert)
            .scaleEffect(max(0.001, pose.alert))
            Text("Upload failed")
                .font(.system(size: GlowFileUploaderRowMetrics.pillText, weight: .medium))
                .foregroundStyle(P.failText)
                .lineLimit(1)
                .fixedSize()
        }
        .padding(.leading, 6)
        .padding(.trailing, 10)
        .frame(height: GlowFileUploaderRowMetrics.pillHeight)
        .background(Capsule().fill(P.failPill))
        // Opens from the left: a capsule mask growing to the pill's width, so the open end stays round.
        .mask(alignment: .leading) {
            GeometryReader { g in
                Capsule().frame(width: max(g.size.height, g.size.width * pose.pill) * min(1, pose.pill * 4))
            }
        }
        .opacity(pose.pillOpacity)
        .accessibilityElement(children: .combine)
    }

    private var status: some View {
        ZStack(alignment: .trailing) {
            if pose.ringOpacity > 0 {
                ZStack {
                    Circle().stroke(.white.opacity(0.1), lineWidth: GlowFileUploaderRowMetrics.ringStroke)
                    Circle()
                        .trim(from: 0, to: pose.ring)
                        .stroke(P.text, style: StrokeStyle(lineWidth: GlowFileUploaderRowMetrics.ringStroke, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                .padding(GlowFileUploaderRowMetrics.ringStroke / 2)
                .frame(width: GlowFileUploaderRowMetrics.status, height: GlowFileUploaderRowMetrics.status)
                .scaleEffect(max(0.001, pose.ringScale))
                .opacity(pose.ringOpacity)
            }
            if pose.discOpacity > 0 {
                ZStack {
                    Circle().fill(P.doneDisc)
                    GlowFileUploaderCheck()
                        .trim(from: 0, to: pose.check)
                        .stroke(P.green, style: StrokeStyle(lineWidth: GlowFileUploaderRowMetrics.checkStroke, lineCap: .round, lineJoin: .round))
                        .padding(1)
                }
                .frame(width: GlowFileUploaderRowMetrics.status, height: GlowFileUploaderRowMetrics.status)
                .scaleEffect(pose.disc)
                .opacity(pose.discOpacity)
            }
            if pose.retry > 0 {
                Button(action: onRetry) {
                    HStack(spacing: 6) {
                        ZStack {
                            GlowFileUploaderArrows()
                                .stroke(P.retry, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                            GlowFileUploaderArrowHeads().fill(P.retry)
                        }
                        .frame(width: GlowFileUploaderRowMetrics.retryGlyph, height: GlowFileUploaderRowMetrics.retryGlyph)
                        .rotationEffect(.degrees(pose.spin))
                        Text("Retry")
                            .font(.system(size: GlowFileUploaderRowMetrics.retryText))
                            .foregroundStyle(P.retry)
                    }
                    .frame(height: GlowFileUploaderRowMetrics.height)
                    .contentShape(Rectangle())
                }
                .buttonStyle(GlowFileUploaderPressStyle(onPress: onRetryPress))
                .disabled(!pose.canRetry)
                .scaleEffect(retryScale)
                .offset(x: pose.retryX)
                .opacity(pose.retry)
                .accessibilityLabel("Retry \(name)")
            }
        }
    }
}

enum GlowFileUploaderRowMetrics {
    static let height = 48.0
    static let gap = 10.0
    static let radius = 18.0
    static let glyph = 14.0
    static let glyphStroke = 1.2
    static let glyphCenter = 20.0
    static let nameLeft = 40.0
    static let nameSize = 11.0
    static let pillGap = 8.0
    static let pillHeight = 21.0
    static let pillText = 9.5
    static let alert = 10.5
    static let retryText = 11.0
    static let retryGlyph = 13.0
    static let status = 18.0
    static let checkStroke = 1.4
    static let statusRight = 13.0
    static let ringStroke = 2.0
    static let edgeWidth = 24.0
    static let edgeOpacity = 0.1
}

/// Shows the label as it is and reports when the finger goes down and comes up.
struct GlowFileUploaderPressStyle: ButtonStyle {
    var onPress: (Bool) -> Void = { _ in }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of: configuration.isPressed) { _, down in onPress(down) }
    }
}
