import SwiftUI
import UIKit

/*
 Sand PIN Code — a wellness app's lock screen on a zen sand tray. Each digit typed on the
 river-stone keypad is carved into its slot by a bamboo stylus (path trim, 0.26 s): the groove
 is a floor stroke, a shadowed upper wall, a lit lower wall and a faint berm. A wrong code
 shakes the tray, then a rake sweeps across and the digits crumble into grains that slide and
 sink, leaving fresh rake lines; the right code sends rings out through the sand and the tray
 brightens. A horizontal swipe over the tray rakes it by hand.

 One Metal pass draws the sand; one Canvas draws the grooves, grains, rake and stylus; the keypad
 and copy are views. Per frame only uniforms, trims, transforms and two grain paths change.
*/
struct SandPinCode: View {
    /// Types a wrong code, rakes it away, types the right one and unlocks, every `loop` seconds.
    var autoPlay = false

    static let loop = 10.0

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            SandPinCodeStage(autoPlay: autoPlay, size: size, insetTop: insets.top, insetBottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(SandPinCodeLayout.background.ignoresSafeArea())
    }
}

/// Where everything sits, in canvas points (screen x + pad).
struct SandPinCodeLayout: Equatable {
    /// The canvas reaches this far past each side of the screen so a shake never shows its edge.
    static let pad = 24.0
    static let keyW = 84.0
    static let keyH = 60.0
    static let keyGapX = 22.0
    static let keyGapY = 14.0
    static let background = Color(red: 0xCD / 255, green: 0xB3 / 255, blue: 0x8C / 255)

    let w: Double, h: Double, cw: Double, cx: Double
    let keypadTop: Double, slotY: Double, x0: Double
    let rakeFrom: Double, rakeTo: Double
    let stone: (Double, Double, Double)
    let insetTop: Double

    init(w: Double, h: Double, insetTop: Double, insetBottom: Double) {
        typealias M = SandPinCodeMotion
        self.w = w
        self.h = h
        self.insetTop = insetTop
        cw = w + Self.pad * 2
        cx = cw / 2
        let keypadH = Self.keyH * 4 + Self.keyGapY * 3
        keypadTop = h - insetBottom - 20 - keypadH
        let headerBottom = insetTop + 128
        slotY = ((headerBottom + keypadTop) / 2 + 4).rounded()
        x0 = cx - (Double(M.slots - 1) * M.slotGap) / 2
        rakeFrom = x0 - M.rakeOver
        rakeTo = x0 + Double(M.slots - 1) * M.slotGap + M.rakeOver
        stone = (cw - Self.pad - 54, insetTop + 64, 24)
    }

    static func == (a: Self, b: Self) -> Bool {
        a.w == b.w && a.h == b.h && a.keypadTop == b.keypadTop && a.insetTop == b.insetTop
    }
}

private struct SandPinCodeStage: View {
    let autoPlay: Bool
    let size: CGSize
    let insetTop: Double
    let insetBottom: Double

    private typealias M = SandPinCodeMotion
    private typealias L = SandPinCodeLayout

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @State private var model = SandPinCodeModel()
    @State private var rake: Image?
    @State private var awake = false

    private static let keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del"]

    var body: some View {
        let lay = L(w: size.width, h: size.height, insetTop: insetTop, insetBottom: insetBottom)
        let auto = autoPlay && !reduced
        model.configure(lay: lay, auto: auto, reduced: reduced, onSleep: { awake = false })
        return TimelineView(.animation(paused: !(auto || awake))) { timeline in
            let s = model.frame(timeline.date)
            let t = model.tNow
            ZStack(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.sandPinCodeTray(
                            .float2(lay.cw, lay.h),
                            .float4(lay.x0, lay.slotY, M.slotGap, M.slotHalfH),
                            .float3(lay.stone.0, lay.stone.1, lay.stone.2),
                            .float3(lay.cx, lay.slotY, s.ripple),
                            .float(s.bright)
                        ))
                        .frame(width: lay.cw, height: lay.h)
                        .offset(x: -L.pad)

                    tray(lay: lay, sand: s, t: t)
                        .frame(width: lay.cw, height: lay.h)
                        .offset(x: -L.pad)
                        .allowsHitTesting(false)

                    Color.clear
                        .frame(width: lay.w, height: max(0, lay.keypadTop - 8))
                        .contentShape(Rectangle())
                        .gesture(rakeDrag(lay))
                        .accessibilityElement()
                        .accessibilityLabel("Sand tray with six passcode slots. Swipe across it to rake it smooth.")

                    keypad(sand: s)
                        .offset(x: (lay.w - (L.keyW * 3 + L.keyGapX * 2)) / 2, y: lay.keypadTop)
                }
                .frame(width: lay.w, height: lay.h, alignment: .topLeading)
                .offset(x: s.shake)

                header(unlock: s.unlock)
                    .frame(width: max(0, lay.w - 108), alignment: .leading)
                    .offset(x: 24, y: lay.insetTop + 20)
                    .allowsHitTesting(false)

                Text("Swipe across the tray to rake it smooth")
                    .font(.system(size: 12, weight: .medium))
                    .kerning(0.2)
                    .foregroundStyle(SandPinCodeStage.ink.opacity(0.55))
                    .multilineTextAlignment(.center)
                    .frame(width: max(0, lay.w - 48))
                    .offset(x: 24, y: lay.slotY + M.slotHalfH + 22)
                    .allowsHitTesting(false)
            }
            .frame(width: lay.w, height: lay.h, alignment: .topLeading)
        }
        .clipped()
        .onAppear { rake = Self.renderRake(y: lay.slotY, scale: displayScale) }
        .onChange(of: lay) { _, n in rake = Self.renderRake(y: n.slotY, scale: displayScale) }
    }

    static let ink = Color(red: 61 / 255, green: 45 / 255, blue: 24 / 255)

    private func wake() {
        if !awake { awake = true }
    }

    // MARK: Canvas

    private func tray(lay: SandPinCodeLayout, sand s: SandPinCodeSand, t: Double) -> some View {
        let rake = rake
        let reduced = reduced
        return Canvas { c, _ in
            // Fresh rake lines behind the rake, across the slot row.
            let linesOpacity = s.linesOn * s.fade
            let end = min(s.rakeX, lay.rakeTo)
            if linesOpacity > 0.001 && end > lay.rakeFrom {
                var lines = Path()
                for k in 0..<7 {
                    let y = lay.slotY - 42 + Double(k) * 14
                    lines.move(to: CGPoint(x: lay.rakeFrom, y: y))
                    lines.addLine(to: CGPoint(x: end, y: y))
                }
                c.drawLayer { l in
                    l.opacity = linesOpacity
                    l.stroke(lines.offsetBy(dx: 0.9, dy: 1.1), with: .color(.init(red: 1, green: 246 / 255, blue: 226 / 255).opacity(0.32)), style: StrokeStyle(lineWidth: 4, lineCap: .round))
                    l.stroke(lines, with: .color(.init(red: 132 / 255, green: 104 / 255, blue: 68 / 255).opacity(0.55)), style: StrokeStyle(lineWidth: 3.4, lineCap: .round))
                    l.stroke(lines.offsetBy(dx: -0.6, dy: -0.8), with: .color(.init(red: 92 / 255, green: 70 / 255, blue: 42 / 255).opacity(0.55)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                }
            }

            // Carved digits: berm, lit lower wall, floor, shadowed upper wall, and the unlock glow.
            var groove = Path()
            for i in 0..<M.slots {
                let d = s.digits[i]
                guard d >= 0, s.carveAt[i] >= 0, t >= s.carveAt[i] else { continue }
                if s.passAt[i] >= 0 && t >= s.passAt[i] { continue }
                let e = M.easeInOutCubic(M.clamp01((t - s.carveAt[i]) / M.carve))
                guard e > 0 else { continue }
                let trimmed = e >= 1 ? M.paths[d] : M.paths[d].trimmedPath(from: 0, to: e)
                groove.addPath(trimmed, transform: CGAffineTransform(translationX: lay.x0 + Double(i) * M.slotGap, y: lay.slotY))
            }
            if !groove.isEmpty && s.fade > 0.001 {
                func style(_ w: Double) -> StrokeStyle { StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round) }
                c.drawLayer { g in
                    g.opacity = s.fade
                    g.drawLayer { b in
                        b.addFilter(.blur(radius: 2.5))
                        b.stroke(groove, with: .color(.init(red: 1, green: 244 / 255, blue: 222 / 255).opacity(0.18)), style: style(10))
                    }
                    g.stroke(groove.offsetBy(dx: 1.1, dy: 1.3), with: .color(.init(red: 1, green: 246 / 255, blue: 228 / 255).opacity(0.7)), style: style(6.2))
                    g.stroke(groove, with: .color(.init(red: 0xA6 / 255, green: 0x8B / 255, blue: 0x66 / 255)), style: style(5))
                    g.drawLayer { u in
                        u.addFilter(.blur(radius: 0.8))
                        u.stroke(groove.offsetBy(dx: -0.9, dy: -1), with: .color(.init(red: 98 / 255, green: 74 / 255, blue: 44 / 255).opacity(0.75)), style: style(2.6))
                    }
                    if s.bright > 0.001 {
                        g.drawLayer { w in
                            w.opacity = s.bright * 0.85
                            w.addFilter(.blur(radius: 3))
                            w.stroke(groove, with: .color(.init(red: 1, green: 206 / 255, blue: 120 / 255)), style: style(4))
                        }
                    }
                }
            }

            // Grains: 50 per crumbling digit, pushed ahead by the rake, sliding down, sinking.
            var light = Path()
            var dark = Path()
            for i in 0..<M.slots {
                let d = s.grainDigits[i]
                let at = s.grainAt[i]
                guard d >= 0, at >= 0 else { continue }
                let tau = t - at
                guard tau >= 0, tau <= M.grainLife else { continue }
                let pts = M.samplePoints[d]
                let sx = lay.x0 + Double(i) * M.slotGap
                for k in 0..<M.samples {
                    let h1 = M.hash(Double(i) * 131 + Double(k) * 7.3)
                    let h2 = M.hash(Double(i) * 71 + Double(k) * 3.1 + 9)
                    let life = M.grainLife * (0.55 + 0.45 * h2)
                    if tau > life { continue }
                    let push = (14 + 46 * h1) * (1 - exp(-tau / 0.16))
                    let fall = (6 + 26 * h2) * M.easeOutCubic(min(1, tau / 0.55))
                    let x = sx + pts[k].x + push + (h2 - 0.5) * 6 * tau
                    let y = lay.slotY + pts[k].y + fall
                    let r = (0.9 + 1.1 * h1) * (1 - pow(tau / life, 3))
                    guard r > 0 else { continue }
                    let rect = CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)
                    if k % 3 == 0 { dark.addEllipse(in: rect) } else { light.addEllipse(in: rect) }
                }
            }
            c.fill(light, with: .color(.init(red: 0xE9 / 255, green: 0xD9 / 255, blue: 0xBB / 255)))
            c.fill(dark, with: .color(.init(red: 0xA8 / 255, green: 0x8C / 255, blue: 0x66 / 255)))

            // The stylus rides the stroke of whichever digit is being carved.
            if !reduced, let st = Self.stylus(s, t: t, lay: lay), st.2 > 0.001 {
                c.drawLayer { g in
                    g.opacity = st.2
                    g.translateBy(x: st.0, y: st.1)
                    var stick = Path()
                    stick.move(to: .zero)
                    stick.addLine(to: CGPoint(x: 34, y: -66))
                    let round = StrokeStyle(lineWidth: 5, lineCap: .round)
                    g.drawLayer { sh in
                        sh.addFilter(.blur(radius: 3))
                        sh.stroke(stick.offsetBy(dx: 9, dy: 6), with: .color(.init(red: 70 / 255, green: 48 / 255, blue: 20 / 255).opacity(0.22)), style: round)
                    }
                    g.stroke(stick, with: .color(.init(red: 0xB3 / 255, green: 0x8A / 255, blue: 0x52 / 255)), style: round)
                    g.stroke(stick.offsetBy(dx: -1.2, dy: -0.4), with: .color(.init(red: 1, green: 236 / 255, blue: 196 / 255).opacity(0.55)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                    g.translateBy(x: 16, y: -26)
                    g.rotate(by: .radians(-1.1))
                    g.translateBy(x: -16, y: 26)
                    g.fill(Path(roundedRect: CGRect(x: 13, y: -28, width: 7, height: 3), cornerRadius: 1), with: .color(.init(red: 0x7A / 255, green: 0x5A / 255, blue: 0x2C / 255)))
                }
            }

            // The rake.
            if s.rakeOn > 0.001, let rake {
                c.drawLayer { r in
                    r.opacity = s.rakeOn
                    r.translateBy(x: s.rakeX, y: 0)
                    r.draw(rake, in: Self.rakeRect(y: lay.slotY))
                }
            }
        }
    }

    private static func stylus(_ s: SandPinCodeSand, t: Double, lay: SandPinCodeLayout) -> (Double, Double, Double)? {
        for i in 0..<M.slots {
            let d = s.digits[i]
            guard d >= 0, s.carveAt[i] >= 0 else { continue }
            let tau = t - s.carveAt[i]
            if tau < -0.12 || tau > M.carve + 0.22 { continue }
            let pr = M.easeInOutCubic(M.clamp01(tau / M.carve))
            let k = min(M.samples - 1, Int((pr * Double(M.samples - 1)).rounded()))
            let p = M.samplePoints[d][k]
            let o = tau < 0 ? M.clamp01((tau + 0.12) / 0.12) : 1 - M.clamp01((tau - M.carve) / 0.22)
            return (lay.x0 + Double(i) * M.slotGap + p.x, lay.slotY + p.y, o)
        }
        return nil
    }

    private static func rakeRect(y: Double) -> CGRect {
        CGRect(x: -30, y: y - 80, width: 200, height: 530)
    }

    /// The rake, at x = 0: a bamboo crossbar with seven tines and its handle running off the
    /// bottom right, rendered once.
    @MainActor
    private static func renderRake(y: Double, scale: Double) -> Image {
        let rect = rakeRect(y: y)
        let view = Canvas { c, _ in
            c.translateBy(x: -rect.minX, y: -rect.minY)
            var handle = Path()
            handle.move(to: CGPoint(x: -2, y: y + 10))
            handle.addLine(to: CGPoint(x: 140, y: y + 420))
            let bar = CGRect(x: -8, y: y - 54, width: 16, height: 108)
            c.drawLayer { sh in
                sh.addFilter(.blur(radius: 6))
                sh.stroke(handle.offsetBy(dx: 12, dy: 10), with: .color(.init(red: 60 / 255, green: 40 / 255, blue: 15 / 255).opacity(0.25)), style: StrokeStyle(lineWidth: 10, lineCap: .round))
            }
            c.drawLayer { sh in
                sh.addFilter(.blur(radius: 6))
                sh.fill(Path(roundedRect: bar.offsetBy(dx: 8, dy: 8), cornerRadius: 6), with: .color(.init(red: 60 / 255, green: 40 / 255, blue: 15 / 255).opacity(0.28)))
            }
            c.stroke(handle, with: .color(.init(red: 0xA8 / 255, green: 0x7E / 255, blue: 0x45 / 255)), style: StrokeStyle(lineWidth: 9, lineCap: .round))
            c.stroke(handle.offsetBy(dx: -2, dy: 0), with: .color(.init(red: 1, green: 232 / 255, blue: 190 / 255).opacity(0.5)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
            var tines = Path()
            for k in 0..<7 { tines.addRoundedRect(in: CGRect(x: 6, y: y - 45 + Double(k) * 14, width: 9, height: 4), cornerSize: CGSize(width: 2, height: 2)) }
            c.fill(tines, with: .color(.init(red: 0x6F / 255, green: 0x51 / 255, blue: 0x29 / 255)))
            func hex(_ v: UInt32) -> Color { Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255) }
            c.fill(Path(roundedRect: bar, cornerRadius: 6), with: .linearGradient(
                Gradient(stops: [.init(color: hex(0x7E5A2A), location: 0), .init(color: hex(0xC99B5E), location: 0.35), .init(color: hex(0xE2BB7D), location: 0.55), .init(color: hex(0x8C6531), location: 1)]),
                startPoint: CGPoint(x: -8, y: 0), endPoint: CGPoint(x: 8, y: 0)))
            c.fill(Path(roundedRect: CGRect(x: -5, y: y - 50, width: 3, height: 100), cornerRadius: 1.5), with: .color(.init(red: 1, green: 236 / 255, blue: 200 / 255).opacity(0.45)))
        }
        .frame(width: rect.width, height: rect.height)
        let r = ImageRenderer(content: view)
        r.scale = scale
        r.isOpaque = false
        return Image(uiImage: r.uiImage ?? UIImage())
    }

    // MARK: Views

    private func header(unlock u: Double) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                ZStack {
                    Circle().fill(Color(red: 1, green: 248 / 255, blue: 232 / 255).opacity(0.45))
                    Circle().strokeBorder(LinearGradient(colors: [.white.opacity(0.6), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.2)), lineWidth: 1)
                    Image(systemName: "lock.fill").font(.system(size: 12, weight: .semibold)).opacity(1 - u)
                    Image(systemName: "lock.open.fill").font(.system(size: 12, weight: .semibold)).opacity(u).scaleEffect(0.9 + 0.1 * u)
                }
                .frame(width: 26, height: 26)
                .shadow(color: Color(red: 80 / 255, green: 55 / 255, blue: 20 / 255).opacity(0.25), radius: 1, y: 1)
                .foregroundStyle(Color(red: 0x5B / 255, green: 0x45 / 255, blue: 0x28 / 255))
                Text("DUNE")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(3)
                    .foregroundStyle(Color(red: 0x5B / 255, green: 0x45 / 255, blue: 0x28 / 255))
            }
            ZStack(alignment: .topLeading) {
                copy("Enter your passcode", "Draw six digits in the sand.")
                    .opacity(1 - u)
                    .offset(y: -8 * u)
                copy("Welcome back, Ilsa", "Your evening session is ready.")
                    .opacity(u)
                    .offset(y: 8 * (1 - u))
            }
        }
    }

    private func copy(_ title: String, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 27, weight: .bold))
                .kerning(-0.4)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .foregroundStyle(Color(red: 0x3D / 255, green: 0x2D / 255, blue: 0x18 / 255))
            Text(sub)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Self.ink.opacity(0.68))
        }
    }

    private func keypad(sand s: SandPinCodeSand) -> some View {
        VStack(spacing: L.keyGapY) {
            ForEach(0..<4, id: \.self) { row in
                HStack(spacing: L.keyGapX) {
                    ForEach(0..<3, id: \.self) { col in
                        let i = row * 3 + col
                        SandPinCodePebble(index: i, label: Self.keys[i], press: s.key == i ? s.press : 0) {
                            wake()
                            model.pressKey(i)
                        }
                    }
                }
            }
        }
    }

    // MARK: Raking by hand

    private func rakeDrag(_ lay: SandPinCodeLayout) -> some Gesture {
        DragGesture(minimumDistance: 14)
            .onChanged { v in
                model.rakeChanged(v, lay: lay) { wake() }
            }
            .onEnded { _ in
                model.rakeEnded()
            }
    }
}

/// A river stone key: irregular corners, a warm grey gradient, two shadows on the sand, an
/// engraved label. It squashes from the press value it is given.
private struct SandPinCodePebble: View {
    let index: Int
    let label: String
    let press: Double
    let onPress: () -> Void

    @State private var down = false

    private typealias L = SandPinCodeLayout

    var body: some View {
        if label.isEmpty {
            Text("Forgot?")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(SandPinCodeStage.ink.opacity(0.6))
                .frame(width: L.keyW, height: L.keyH)
        } else {
            pebble
                .frame(width: L.keyW, height: L.keyH)
                .contentShape(Rectangle().inset(by: -6))
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in
                            if !down {
                                down = true
                                onPress()
                            }
                        }
                        .onEnded { _ in down = false }
                )
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label == "del" ? "Delete" : label)
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { onPress() }
        }
    }

    private var pebble: some View {
        let h = SandPinCodeMotion.hash
        let i = Double(index)
        let r = { (k: Double) in 22 + h(i * 9 + k) * 14 }
        let shape = UnevenRoundedRectangle(topLeadingRadius: r(1), bottomLeadingRadius: r(3), bottomTrailingRadius: r(4), topTrailingRadius: r(2), style: .continuous)
        let tone = h(i * 3 + 1)
        let hexes: [UInt32] = tone > 0.66 ? [0xB7AEA1, 0x8F877C, 0x655F57] : (tone > 0.33 ? [0xA9A59C, 0x807C74, 0x57544F] : [0xBCA995, 0x94826E, 0x6A5B4B])
        let colors = hexes.map { Color(red: Double(($0 >> 16) & 0xFF) / 255, green: Double(($0 >> 8) & 0xFF) / 255, blue: Double($0 & 0xFF) / 255) }
        let shadow = Color(red: 70 / 255, green: 48 / 255, blue: 20 / 255)
        return ZStack {
            shape.fill(LinearGradient(
                stops: [.init(color: colors[0], location: 0), .init(color: colors[1], location: 0.55), .init(color: colors[2], location: 1)],
                startPoint: UnitPoint(x: 0.2, y: 0), endPoint: UnitPoint(x: 0.8, y: 1)))
            // Inset edge: a lit top rim and a shaded underside.
            shape.strokeBorder(LinearGradient(
                stops: [
                    .init(color: .white.opacity(0.35), location: 0),
                    .init(color: .white.opacity(0), location: 0.22),
                    .init(color: .clear, location: 0.7),
                    .init(color: Color(red: 30 / 255, green: 24 / 255, blue: 18 / 255).opacity(0.35), location: 1),
                ],
                startPoint: .top, endPoint: .bottom), lineWidth: 2)
            if label == "del" {
                Image(systemName: "delete.left")
                    .font(.system(size: 20))
                    .foregroundStyle(Color(red: 40 / 255, green: 35 / 255, blue: 30 / 255).opacity(0.78))
            } else {
                Text(label)
                    .font(.system(size: 26, weight: .medium).monospacedDigit())
                    .foregroundStyle(Color(red: 38 / 255, green: 33 / 255, blue: 28 / 255).opacity(0.8))
                    .shadow(color: .white.opacity(0.35), radius: 0, y: 1)
            }
        }
        .frame(width: L.keyW - 6, height: L.keyH - 4)
        .compositingGroup()
        .shadow(color: shadow.opacity(0.4), radius: 1.5, y: 2)
        .shadow(color: shadow.opacity(0.28), radius: 10, y: 12)
        .rotationEffect(.degrees((h(i * 5 + 2) - 0.5) * 6))
        .scaleEffect(x: 1 + 0.05 * press, y: 1 - 0.09 * press)
        .offset(y: 2 * press)
    }
}

/// The user state and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class SandPinCodeModel {
    private typealias M = SandPinCodeMotion
    private static let idle = 2.0
    private static let returnLen = 0.75

    private var lay = SandPinCodeLayout(w: 393, h: 852, insetTop: 59, insetBottom: 34)
    private var auto = false
    private var reduced = false
    private var onSleep: () -> Void = {}

    private var last: Date?
    private var now = 0.0
    private var clock = 0.0
    /// 0 = autoPlay (or rest), 1 = the user drives, 2 = easing back to rest.
    private var mode = 0
    private var lastTouch = 0.0
    private var user = SandPinCodeUser()
    private var raking = 0 // 0 undecided, 1 raking, −1 not a rake
    private var sand = SandPinCodeSand()

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let notify = UINotificationFeedbackGenerator()

    /// The time the drawing reads: the loop clock in autoPlay, the user clock otherwise.
    var tNow: Double { mode == 0 ? clock : now }

    func configure(lay: SandPinCodeLayout, auto: Bool, reduced: Bool, onSleep: @escaping () -> Void) {
        if auto != self.auto { clock = 0 }
        self.lay = lay
        self.auto = auto
        self.reduced = reduced
        self.onSleep = onSleep
    }

    func frame(_ date: Date) -> SandPinCodeSand {
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.064)
        if dt > 0 { step(dt) }
        sand = currentSand()
        return sand
    }

    private func step(_ dt: Double) {
        let n = now + dt
        now = n
        if mode == 0 {
            if auto { clock = (clock + dt).truncatingRemainder(dividingBy: SandPinCode.loop) }
            return
        }
        var u = user
        if u.checkAt >= 0 && n >= u.checkAt {
            u.checkAt = -1
            let ok = u.entered.enumerated().allSatisfy { $0.element == M.code[$0.offset] }
            if ok {
                u.unlockAt = n
                notify.notificationOccurred(.success)
            } else {
                u.shakeAt = n
                u.rakeStart = n + 0.42
                notify.notificationOccurred(.error)
            }
        }
        if u.rakeStart >= 0 && n >= u.rakeStart {
            let r = M.seg(n, u.rakeStart, u.rakeStart + M.rakeLen)
            u.sand.rakeX = lay.rakeFrom + (lay.rakeTo - lay.rakeFrom) * M.easeInOutCubic(r)
            u.sand.rakeFrom = lay.rakeFrom
            u.sand.rakeOn = r < 1 ? M.clamp01((n - u.rakeStart) / 0.12) : 1 - M.seg(n, u.rakeStart + M.rakeLen, u.rakeStart + M.rakeLen + 0.25)
            if n > u.rakeStart + M.rakeLen + 0.25 {
                u.rakeStart = -1
                u.sand.rakeOn = 0
            }
        } else if !u.fingerRake && u.sand.rakeOn > 0 {
            u.sand.rakeOn = max(0, u.sand.rakeOn - dt * 4)
        }
        markPasses(&u, n)
        let busy = u.rakeStart >= 0 || u.fingerRake || u.checkAt >= 0 || (u.unlockAt >= 0 && n - u.unlockAt < 1.4)
        if busy { lastTouch = n }
        if mode == 1 && n - lastTouch > Self.idle {
            mode = 2
            u.returnAt = n
        }
        if mode == 2 && n - u.returnAt > Self.returnLen {
            user = SandPinCodeUser()
            clock = 0
            mode = 0
            if !auto {
                let sleep = onSleep
                DispatchQueue.main.async { sleep() }
            }
            return
        }
        user = u
    }

    private func currentSand() -> SandPinCodeSand {
        if mode == 0 { return auto ? M.autoSand(clock, x0: lay.x0) : SandPinCodeSand() }
        let u = user
        let n = now
        var s = u.sand
        s.press = s.key >= 0 ? M.press(n - u.keyAt) : 0
        s.shake = reduced ? 0 : M.shake(n - u.shakeAt)
        let back = u.returnAt >= 0 ? 1 - M.easeInOutCubic(M.seg(n, u.returnAt, u.returnAt + Self.returnLen - 0.05)) : 1
        if u.unlockAt >= 0 {
            s.ripple = reduced ? -1 : n - u.unlockAt
            s.bright = M.easeOutCubic(M.seg(n, u.unlockAt, u.unlockAt + 0.4)) * back
            s.unlock = M.easeOutCubic(M.seg(n, u.unlockAt + 0.1, u.unlockAt + 0.5)) * back
        }
        s.fade = back
        return s
    }

    /// Leaves autoPlay for the user's own tray (it starts empty: the loop's rest).
    private func takeOver() {
        if mode != 1 {
            user = SandPinCodeUser()
            mode = 1
        }
        lastTouch = now
    }

    private func markPasses(_ u: inout SandPinCodeUser, _ n: Double) {
        for i in 0..<M.slots where u.sand.digits[i] >= 0 && u.sand.rakeOn > 0 && u.sand.rakeX >= lay.x0 + Double(i) * M.slotGap {
            u.sand.grainDigits[i] = u.sand.digits[i]
            u.sand.grainAt[i] = n
            u.sand.digits[i] = -1
            u.sand.carveAt[i] = -1
            u.sand.linesOn = 1
        }
        if u.sand.digits.allSatisfy({ $0 < 0 }) { u.entered = [] }
    }

    func pressKey(_ index: Int) {
        takeOver()
        var u = user
        let n = now
        if u.unlockAt >= 0 || u.rakeStart >= 0 || u.checkAt >= 0 || u.fingerRake { return }
        u.keyAt = n
        u.sand.key = index
        if index == 11 {
            let k = u.entered.count - 1
            if k >= 0 {
                u.entered.removeLast()
                u.sand.digits[k] = -1
                u.sand.carveAt[k] = -1
            }
        } else if index != 9 && u.entered.count < M.slots {
            let d = index == 10 ? 0 : index + 1
            let k = u.entered.count
            u.entered.append(d)
            u.sand.digits[k] = d
            u.sand.carveAt[k] = n
            u.sand.passAt[k] = -1
            if u.entered.count == M.slots { u.checkAt = n + M.carve + 0.12 }
        }
        user = u
        light.impactOccurred()
    }

    /// A horizontal swipe over the tray (activates past 14 pt sideways, fails past 24 pt up or down).
    func rakeChanged(_ v: DragGesture.Value, lay: SandPinCodeLayout, wake: () -> Void) {
        if raking == 0 {
            if abs(v.translation.height) > 24 {
                raking = -1
            } else if abs(v.translation.width) >= 14 {
                raking = 1
                wake()
                takeOver()
                user.fingerRake = true
                user.sand.rakeFrom = v.location.x + SandPinCodeLayout.pad
                user.sand.rakeX = v.location.x + SandPinCodeLayout.pad
                user.sand.rakeOn = 1
            }
            return
        }
        guard raking == 1 else { return }
        user.sand.rakeX = v.location.x + SandPinCodeLayout.pad
        user.sand.rakeFrom = min(user.sand.rakeFrom, v.location.x + SandPinCodeLayout.pad)
        lastTouch = now
    }

    func rakeEnded() {
        defer { raking = 0 }
        guard raking == 1 else { return }
        if user.fingerRake {
            // Letting go rakes the rest of the tray too: every digit left crumbles.
            user.fingerRake = false
            for i in 0..<M.slots where user.sand.digits[i] >= 0 {
                user.sand.grainDigits[i] = user.sand.digits[i]
                user.sand.grainAt[i] = now
                user.sand.digits[i] = -1
                user.sand.carveAt[i] = -1
            }
            user.entered = []
        }
        lastTouch = now
    }
}
