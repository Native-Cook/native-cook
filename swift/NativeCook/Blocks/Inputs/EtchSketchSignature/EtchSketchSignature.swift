import SwiftUI
import UIKit

/// A proof-of-delivery screen whose signature pad is a red etch-a-sketch. The two knobs turn and
/// the stylus scrapes a stair-stepped "J" with a wavy flourish through the aluminium powder; then
/// the toy shakes, the powder resettles from the top and wipes the line, and it signs again.
/// Draw on the screen with a finger, turn a knob by dragging on it, or shake it clear with two fingers.
struct EtchSketchSignature: View {
    /// Turns the knobs to sign a stair-stepped "J" with a flourish, then shakes it clear, every 10 s.
    var autoPlay = false

    static let loop = EtchSketchSignatureMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = EtchSketchSignatureModel()
    @State private var awake = false

    typealias M = EtchSketchSignatureMotion
    private typealias C = EtchSketchSignatureColors

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(EtchSketchSignatureLayout(size: size, top: insets.top, bottom: insets.bottom))
                    .frame(width: size.width, height: size.height)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(C.root.ignoresSafeArea())
    }

    private func stage(_ l: EtchSketchSignatureLayout) -> some View {
        let looping = autoPlay && !reduceMotion
        let wake = { if !looping { awake = true; sleepWhenResting() } }
        return ZStack(alignment: .topLeading) {
            LinearGradient(colors: [Color(hex: 0xF4F3F0), Color(hex: 0xECEAE5), Color(hex: 0xE2DFD8)],
                           startPoint: .zero, endPoint: UnitPoint(x: 0.3, y: 1))
                .frame(width: l.width, height: l.height)

            TimelineView(.animation(paused: !(looping || awake))) { timeline in
                let e = model.step(to: timeline.date, looping: looping, layout: l)
                toy(l, etch: e)
                    .overlay(alignment: .topLeading) { actions(l, signed: e.signed, wake: wake) }
            }

            // On the screen: one finger draws (the stylus follows it), two fingers shake the toy clear.
            EtchSketchSignatureTouchPad(
                origin: l.screen.origin,
                onStart: { p, n in wake(); model.padStart(p, fingers: n, layout: l) },
                onMove: { p, n, moved in model.padMove(p, fingers: n, moved: moved, layout: l) },
                onEnd: { model.padEnd() }
            )
            .frame(width: l.screen.width, height: l.screen.height)
            .offset(x: l.screen.minX, y: l.screen.minY)
            .accessibilityElement()
            .accessibilityLabel("Signature pad. Draw with your finger, or turn the knobs.")

            knobPad(l, x: l.knobLX, horizontal: true, wake: wake)
                .accessibilityLabel("Left knob: draws across")
            knobPad(l, x: l.knobRX, horizontal: false, wake: wake)
                .accessibilityLabel("Right knob: draws up and down")

            texts(l)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
    }

    /// Outside autoPlay the timeline pauses again once the toy is back at rest.
    private func sleepWhenResting() {
        Task { @MainActor in
            while awake {
                try? await Task.sleep(for: .milliseconds(400))
                if model.resting { awake = false }
            }
        }
    }

    /// Knobs: drag on one to turn it. Left draws across, right draws up and down.
    private func knobPad(_ l: EtchSketchSignatureLayout, x: Double, horizontal: Bool, wake: @escaping () -> Void) -> some View {
        Color.clear
            .frame(width: 88, height: 88)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        if !model.knobActive {
                            wake()
                            model.knobStart(layout: l)
                        }
                        model.knobMove(v.translation, horizontal: horizontal, layout: l)
                    }
                    .onEnded { _ in model.knobEnd() }
            )
            .offset(x: x - 44, y: l.knobY - 44)
            .accessibilityElement()
    }

    /// The toy: its red frame (drawn once), the powder screen, the line, the stylus and the knobs,
    /// all wobbling together when it is shaken.
    private func toy(_ l: EtchSketchSignatureLayout, etch e: M.Etch) -> some View {
        let s = l.screen
        return ZStack(alignment: .topLeading) {
            EtchSketchSignatureFrame(layout: l).equatable()

            Path(roundedRect: s, cornerRadius: 14)
                .fill(Color.white)
                .frame(width: l.width, height: l.height)
                .colorEffect(ShaderLibrary.etchSketchSignatureScreen(
                    .float4(s.minX, s.minY, s.width, s.height),
                    .float(e.wobble), .float(e.sweep), .float(e.band)))

            let line = model.line(layout: l, etch: e)
            Canvas { ctx, _ in
                let style = StrokeStyle(lineWidth: 1.9, lineCap: .round, lineJoin: .round)
                var lip = ctx
                lip.translateBy(x: 0, y: 1.3)
                // A faint light lip just below a dark graphite core, so it reads as a groove.
                lip.stroke(line, with: .color(Color(.sRGB, red: 0.97, green: 0.98, blue: 1, opacity: 0.6)),
                           style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                ctx.stroke(line, with: .color(Color(.sRGB, red: 0.16, green: 0.17, blue: 0.19, opacity: 1)), style: style)
            }
            .frame(width: l.width, height: l.height)
            .colorEffect(ShaderLibrary.etchSketchSignatureLine(.float(e.sweep)))

            Canvas { ctx, _ in
                ctx.fill(Path(ellipseIn: CGRect(x: e.sx - 2.4, y: e.sy - 2.4, width: 4.8, height: 4.8)), with: .color(Color(hex: 0x3A3D42)))
                ctx.fill(Path(ellipseIn: CGRect(x: e.sx - 1, y: e.sy - 1, width: 2, height: 2)), with: .color(Color.rgba(255, 255, 255, 0.5)))
                // The knobs' grip and marker dot turn with the stylus's travel.
                for (kx, turn) in [(l.knobLX, (e.sx - l.sig.pts[0].x) / M.ptPerTurn), (l.knobRX, (e.sy - l.sig.pts[0].y) / M.ptPerTurn)] {
                    var k = ctx
                    k.translateBy(x: kx, y: l.knobY)
                    k.rotate(by: .radians(turn * .pi * 2))
                    k.stroke(EtchSketchSignatureFrame.ridges, with: .color(Color.rgba(120, 110, 95, 0.45)),
                             style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                    let r = EtchSketchSignatureLayout.knobR
                    k.fill(Path(ellipseIn: CGRect(x: -2.6, y: -r + 13 - 2.6, width: 5.2, height: 5.2)), with: .color(Color(hex: 0xC9302A)))
                }
            }
            .frame(width: l.width, height: l.height)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .rotationEffect(.radians(e.wobble), anchor: UnitPoint(x: l.toyCenter.x / l.width, y: l.toyCenter.y / l.height))
        .offset(x: e.wobble * 140)
        .allowsHitTesting(false)
    }

    private func texts(_ l: EtchSketchSignatureLayout) -> some View {
        ZStack(alignment: .topLeading) {
            HStack {
                Text("PROOF OF DELIVERY")
                    .font(.system(size: 12, weight: .bold))
                    .tracking(1.4)
                Spacer()
                Text("Step 2 of 2")
                    .font(.system(size: 13, weight: .semibold).monospacedDigit())
            }
            .foregroundStyle(C.mute)
            .frame(width: l.width - 40, height: 24)
            .offset(x: 20, y: l.top + 12)

            VStack(alignment: .leading, spacing: 4) {
                Text("Sign for parcel #A-2291")
                    .font(.system(size: 26, weight: .heavy))
                    .tracking(-0.5)
                    .foregroundStyle(C.ink)
                Text("Dana Whitfield · 14 Larch Lane, Apt 3")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(C.mute)
            }
            .frame(width: l.width - 40, alignment: .leading)
            .offset(x: 20, y: l.top + 42)

            Text("Turn the knobs or draw · two fingers shake it clear")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(C.mute)
                .multilineTextAlignment(.center)
                .frame(width: l.width - 40)
                .offset(x: 20, y: l.frame.maxY + 14)
        }
        .allowsHitTesting(false)
    }

    private func actions(_ l: EtchSketchSignatureLayout, signed: Double, wake: @escaping () -> Void) -> some View {
        HStack(spacing: 12) {
            Button {
                wake()
                model.clearButton(layout: l)
            } label: {
                Text("Clear")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(C.ink)
                    .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
                    .background(
                        Capsule().fill(Color.rgba(31, 35, 40, 0.06))
                            .overlay(Capsule().strokeBorder(Color.rgba(31, 35, 40, 0.12), lineWidth: 1))
                    )
            }
            .frame(width: (l.width - 32 - 12) / 3)
            Button {} label: {
                Text("Confirm delivery")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color(hex: 0xF7F5F2))
                    .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
                    .background(
                        Capsule().fill(C.ink)
                            .shadow(color: Color.rgba(10, 12, 16, 0.2), radius: 22, y: 10)
                            .shadow(color: Color.rgba(10, 12, 16, 0.35), radius: 2, y: 1)
                    )
            }
            .opacity(0.45 + 0.55 * M.clamp01(signed))
        }
        .buttonStyle(EtchSketchSignaturePress())
        .frame(width: l.width - 32, height: 52)
        .offset(x: 16, y: l.buttonsTop)
    }
}

private struct EtchSketchSignaturePress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

enum EtchSketchSignatureColors {
    static let root = Color(hex: 0xECEAE5)
    static let ink = Color(hex: 0x1F2328)
    static let mute = Color.rgba(31, 35, 40, 0.58)
    static let red = [Color(hex: 0xE5493D), Color(hex: 0xCB3129), Color(hex: 0xA2221C)]
}

/// Layout (pt): the toy fills the space between the parcel details and the buttons.
struct EtchSketchSignatureLayout: Equatable {
    static let knobR = 32.0

    let width: Double
    let height: Double
    let top: Double
    let bottom: Double
    let frame: CGRect
    let screen: CGRect
    let buttonsTop: Double
    let knobY: Double
    let knobLX: Double
    let knobRX: Double
    let sig: EtchSketchSignatureMotion.Signature

    var toyCenter: CGPoint { CGPoint(x: frame.midX, y: frame.midY) }

    init(size: CGSize, top: Double, bottom: Double) {
        width = size.width
        height = size.height
        self.top = top
        self.bottom = bottom
        let fx = 16.0
        let fy = top + 124
        let fw = max(200, size.width - 32)
        buttonsTop = size.height - bottom - 16 - 52
        let fh = max(260, buttonsTop - 44 - fy)
        frame = CGRect(x: fx, y: fy, width: fw, height: fh)
        screen = CGRect(x: fx + 26, y: fy + 30, width: fw - 52, height: fh - 30 - 92)
        knobY = fy + fh - 46
        knobLX = fx + 58
        knobRX = fx + fw - 58
        sig = EtchSketchSignatureMotion.signature(screen)
    }
}

/// The toy's red plastic frame, bezel, label and knob bodies: drawn once.
struct EtchSketchSignatureFrame: View, Equatable {
    let layout: EtchSketchSignatureLayout

    static let ridges = EtchSketchSignatureMotion.knobRidges(EtchSketchSignatureLayout.knobR)

    static func == (a: Self, b: Self) -> Bool { a.layout == b.layout }

    private static let grain: Image = {
        let n = 150 // 1.5 px per pt, 100 pt square
        var bytes = [UInt8](repeating: 0, count: n * n)
        for j in 0..<n {
            for i in 0..<n {
                let s = sin(Double(i) * 12.9898 + Double(j) * 78.233) * 43758.5453
                bytes[j * n + i] = UInt8(max(0, min(255, (s - floor(s)) * 255)))
            }
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        let cg = CGImage(
            width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: n,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )!
        return Image(decorative: cg, scale: 1.5)
    }()

    var body: some View {
        Canvas { ctx, _ in
            let l = layout
            let f = l.frame
            let s = l.screen
            // Red plastic frame: two shadows, a lit top edge, a darker lower lip, a little grain.
            let body = Path(roundedRect: f, cornerRadius: 34)
            for (blur, dy, a) in [(34.0, 18.0, 0.28), (3.0, 2.0, 0.4)] {
                var sh = ctx
                sh.addFilter(.blur(radius: blur))
                sh.translateBy(x: 0, y: dy)
                sh.fill(body, with: .color(Color.rgba(90, 20, 15, a)))
            }
            ctx.fill(body, with: .linearGradient(Gradient(colors: EtchSketchSignatureColors.red),
                                                 startPoint: CGPoint(x: 0, y: f.minY), endPoint: CGPoint(x: 0, y: f.maxY)))
            var grain = ctx
            grain.clip(to: body)
            grain.blendMode = .softLight
            grain.opacity = 0.3
            grain.fill(body, with: .tiledImage(Self.grain, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
            ctx.stroke(Path(roundedRect: f.insetBy(dx: 1, dy: 1), cornerRadius: 33), with: .color(Color.rgba(255, 190, 180, 0.45)), lineWidth: 1.5)
            ctx.fill(Path(roundedRect: CGRect(x: f.minX + 6, y: f.maxY - 30, width: f.width - 12, height: 24), cornerRadius: 14),
                     with: .color(Color.rgba(80, 10, 8, 0.12)))
            // The bezel around the powder screen.
            let bezel = s.insetBy(dx: -7, dy: -7)
            ctx.fill(Path(roundedRect: bezel, cornerRadius: 20), with: .linearGradient(
                Gradient(colors: [Color(hex: 0x8E1A16), Color(hex: 0xB32820), Color(hex: 0xD5463A)]),
                startPoint: CGPoint(x: 0, y: bezel.minY), endPoint: CGPoint(x: 0, y: bezel.maxY)))

            // The label between the knobs.
            let font = UIFont.systemFont(ofSize: 11, weight: .bold)
            let label = Text("SIGN HERE").font(Font(font)).tracking(3).foregroundStyle(Color(hex: 0xF3CF7A))
            var lab = ctx
            lab.addFilter(.shadow(color: Color.rgba(90, 10, 6, 0.6), radius: 0.5, x: 0, y: 1))
            lab.draw(label, at: CGPoint(x: l.width / 2 + 1.5, y: l.knobY + 4 + font.lineHeight - font.ascender), anchor: .bottom)

            // Knob bodies: a soft socket shadow, then cream plastic lit from the top left.
            let r = EtchSketchSignatureLayout.knobR
            for kx in [l.knobLX, l.knobRX] {
                ctx.fill(Path(ellipseIn: CGRect(x: kx - r - 1, y: l.knobY + 3 - r - 1, width: 2 * r + 2, height: 2 * r + 2)),
                         with: .color(Color.rgba(70, 8, 6, 0.35)))
                let disc = Path(ellipseIn: CGRect(x: kx - r, y: l.knobY - r, width: 2 * r, height: 2 * r))
                var sh = ctx
                sh.addFilter(.blur(radius: 6))
                sh.translateBy(x: 0, y: 4)
                sh.fill(disc, with: .color(Color.rgba(70, 8, 6, 0.4)))
                ctx.fill(disc, with: .radialGradient(
                    Gradient(colors: [Color(hex: 0xFFFFFC), Color(hex: 0xEEEAE2), Color(hex: 0xCFC8BC)]),
                    center: CGPoint(x: kx - 9, y: l.knobY - 11), startRadius: 0, endRadius: r * 1.5))
                ctx.stroke(Path(ellipseIn: CGRect(x: kx - r + 9, y: l.knobY - r + 9, width: 2 * r - 18, height: 2 * r - 18)),
                           with: .color(Color.rgba(255, 255, 255, 0.8)), lineWidth: 1)
            }
        }
        .frame(width: layout.width, height: layout.height)
    }
}

/// Every finger on the powder screen from the moment it lands, with how many there are, so one
/// finger draws and two shake the toy clear.
private struct EtchSketchSignatureTouchPad: UIViewRepresentable {
    let origin: CGPoint
    let onStart: (CGPoint, Int) -> Void
    let onMove: (CGPoint, Int, Double) -> Void
    let onEnd: () -> Void

    func makeUIView(context: Context) -> Pad {
        let pad = Pad()
        update(pad)
        return pad
    }

    func updateUIView(_ pad: Pad, context: Context) {
        update(pad)
    }

    private func update(_ pad: Pad) {
        pad.origin = origin
        pad.onStart = onStart
        pad.onMove = onMove
        pad.onEnd = onEnd
    }

    final class Pad: UIView {
        var origin = CGPoint.zero
        var onStart: (CGPoint, Int) -> Void = { _, _ in }
        var onMove: (CGPoint, Int, Double) -> Void = { _, _, _ in }
        var onEnd: () -> Void = {}
        private var touches: Set<UITouch> = []
        private var start = CGPoint.zero

        override init(frame: CGRect) {
            super.init(frame: frame)
            isMultipleTouchEnabled = true
            backgroundColor = .clear
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        /// The fingers' centre, in the block's coordinates (this view sits at the screen's corner).
        private func centre() -> CGPoint {
            var x = 0.0
            var y = 0.0
            for t in touches {
                let p = t.location(in: self)
                x += p.x
                y += p.y
            }
            let n = Double(max(1, touches.count))
            return CGPoint(x: x / n + origin.x, y: y / n + origin.y)
        }

        override func touchesBegan(_ new: Set<UITouch>, with event: UIEvent?) {
            let first = touches.isEmpty
            touches.formUnion(new)
            let c = centre()
            if first {
                start = c
                onStart(c, touches.count)
            } else {
                start = c
                onMove(c, touches.count, 0)
            }
        }

        override func touchesMoved(_ moved: Set<UITouch>, with event: UIEvent?) {
            let c = centre()
            onMove(c, touches.count, hypot(c.x - start.x, c.y - start.y))
        }

        override func touchesEnded(_ ended: Set<UITouch>, with event: UIEvent?) {
            lift(ended)
        }

        override func touchesCancelled(_ cancelled: Set<UITouch>, with event: UIEvent?) {
            lift(cancelled)
        }

        private func lift(_ gone: Set<UITouch>) {
            touches.subtract(gone)
            if touches.isEmpty { onEnd() } else { start = centre() }
        }
    }
}

/// The loop's clock, and the finger's line.
@MainActor
final class EtchSketchSignatureModel {
    typealias M = EtchSketchSignatureMotion

    /// Seconds after the last touch before the loop starts again.
    private static let idleDelay = 2.0
    /// Stylus travel per point of finger travel on a knob.
    private static let knobGain = 0.9
    /// A user clear: wobble and sweep, seconds.
    private static let clearLen = 1.4
    private static let back = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))

    private(set) var knobActive = false
    private var looping: Bool?
    private var clock = 0.0
    private var user = false
    private var active = false
    private var idle = 0.0
    private var clearing = false
    private var clearT = 0.0
    private var clearDur = 1.4
    private var pts: [CGPoint] = []
    private var path = Path()
    private var sx = 0.0
    private var sy = 0.0
    private var tx = 0.0
    private var ty = 0.0
    private var following = false
    private var returnFrom: CGPoint?
    private var returnT = 0.0
    private var prevKnob = CGSize.zero
    private var lastDate: Date?
    private var etch = M.Etch()
    private let light = UIImpactFeedbackGenerator(style: .light)

    var resting: Bool { !user && !active }

    func step(to date: Date, looping: Bool, layout l: EtchSketchSignatureLayout) -> M.Etch {
        if self.looping != looping {
            self.looping = looping
            clock = 0
        }
        if date == lastDate { return etch }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0
        lastDate = date
        // A slow frame pauses the clock instead of skipping ahead.
        let dt = min(max(raw, 0), 1.0 / 30)
        let s = l.screen

        if user {
            if following {
                // On the screen the stylus chases the finger, as fast as two quick hands could turn the knobs.
                let k = min(1, dt * 16)
                scrape(sx + (tx - sx) * k, sy + (ty - sy) * k, l)
            }
            if clearing {
                clearT += dt
                if clearT >= clearDur {
                    pts = []
                    path = Path()
                    clearing = false
                    clearT = 0
                }
            }
            if let from = returnFrom {
                returnT += dt
                let p = min(1, returnT / 0.9)
                let e = Self.back.value(at: p)
                let rest = l.sig.pts[0]
                sx = from.x + (rest.x - from.x) * e
                sy = from.y + (rest.y - from.y) * e
                if p >= 1 {
                    // Back in the loop where the screen is clear and the pen about to set off.
                    returnFrom = nil
                    clock = M.sweepEnd
                    user = false
                }
            } else if !active {
                idle += dt
                if idle > Self.idleDelay {
                    if !pts.isEmpty { clearAll(0.9) }
                    returnFrom = CGPoint(x: sx, y: sy)
                    returnT = 0
                }
            }
            if user {
                let c = clearing ? min(1, clearT / clearDur) : 0
                let sw = M.easeInOut(M.seg(c, 0.12, 0.85))
                var e = M.Etch()
                e.sx = sx
                e.sy = sy
                e.sweep = clearing ? s.minY - 30 + (s.height + 60) * sw : -1e4
                e.band = clearing ? sin(.pi * sw) : 0
                e.wobble = clearing ? M.shake(c * Self.clearLen, Self.clearLen) : 0
                e.signed = pts.count > 4 && !clearing ? 1 : 0
                etch = e
                return e
            }
        }
        if looping { clock = (clock + dt).truncatingRemainder(dividingBy: M.loop) }
        etch = M.etchAt(clock, l.sig, top: s.minY, h: s.height)
        return etch
    }

    /// The line to draw now: the loop's signature, or the finger's own.
    func line(layout l: EtchSketchSignatureLayout, etch e: M.Etch) -> Path {
        user ? path : M.linePath(l.sig, e.drawn)
    }

    /// Moves the stylus, scraping a line behind it.
    private func scrape(_ x: Double, _ y: Double, _ l: EtchSketchSignatureLayout) {
        let s = l.screen
        let nx = min(s.maxX - 6, max(s.minX + 6, x))
        let ny = min(s.maxY - 6, max(s.minY + 6, y))
        if hypot(nx - sx, ny - sy) < 0.8 { return }
        let from = CGPoint(x: sx, y: sy)
        sx = nx
        sy = ny
        if clearing { return }
        if pts.isEmpty {
            pts.append(from)
            path.move(to: from)
        }
        if pts.count < 3000 {
            pts.append(CGPoint(x: nx, y: ny))
            path.addLine(to: CGPoint(x: nx, y: ny))
        }
    }

    /// The finger takes over: what the loop has drawn becomes the finger's own line.
    private func takeOver(_ l: EtchSketchSignatureLayout) {
        if user { return }
        let e = M.etchAt(clock, l.sig, top: l.screen.minY, h: l.screen.height)
        pts = []
        path = Path()
        if e.sweep < -1000 && e.drawn > 0 {
            for k in 0..<l.sig.run.count {
                if l.sig.run[k] > e.drawn { break }
                pts.append(l.sig.pts[k])
            }
            pts.append(CGPoint(x: e.sx, y: e.sy))
            path.addLines(pts)
        }
        sx = e.sx
        sy = e.sy
        clearing = false
        clearT = 0
        returnFrom = nil
        user = true
    }

    /// Shake: the toy wobbles and the powder resettles from the top, wiping the line.
    private func clearAll(_ duration: Double) {
        if clearing { return }
        clearing = true
        clearT = 0
        clearDur = duration
    }

    func padStart(_ p: CGPoint, fingers: Int, layout l: EtchSketchSignatureLayout) {
        takeOver(l)
        active = true
        idle = 0
        tx = p.x
        ty = p.y
        following = fingers < 2
    }

    func padMove(_ p: CGPoint, fingers: Int, moved: Double, layout l: EtchSketchSignatureLayout) {
        idle = 0
        if fingers > 1 {
            following = false
            if moved > 24 && !clearing {
                clearAll(Self.clearLen)
                light.impactOccurred()
            }
            return
        }
        tx = p.x
        ty = p.y
    }

    func padEnd() {
        following = false
        active = false
    }

    func knobStart(layout l: EtchSketchSignatureLayout) {
        knobActive = true
        prevKnob = .zero
        takeOver(l)
        active = true
        idle = 0
        following = false
        light.impactOccurred()
    }

    func knobMove(_ translation: CGSize, horizontal: Bool, layout l: EtchSketchSignatureLayout) {
        idle = 0
        let dx = translation.width - prevKnob.width
        let dy = translation.height - prevKnob.height
        prevKnob = translation
        if horizontal {
            scrape(sx - dy * Self.knobGain + dx * 0.3, sy, l)
        } else {
            scrape(sx, sy + dy * Self.knobGain - dx * 0.3, l)
        }
    }

    func knobEnd() {
        knobActive = false
        active = false
    }

    func clearButton(layout l: EtchSketchSignatureLayout) {
        takeOver(l)
        idle = 0
        clearAll(Self.clearLen)
        light.impactOccurred()
    }
}
