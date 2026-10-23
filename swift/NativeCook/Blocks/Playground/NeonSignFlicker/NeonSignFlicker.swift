import SwiftUI
import UIKit

/// A hand-bent neon sign, "open late" in rose and ice-blue script beside an amber cocktail glass,
/// hanging on two chains in front of a dark brick wall. The tubes buzz on letter by letter and warm
/// up, one letter never quite works, and the wall takes the colour of whatever is lit. Tap a word
/// to switch it; drag the sign to swing it on its chains.
struct NeonSignFlicker: View {
    /// Powers the sign on, browns it out, swings it and powers it down on a 12 s loop. Off by default.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    // The sign's life lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = NeonSignFlickerEngine()
    @State private var touch = NeonSignFlickerTouch()
    @State private var bakes: NeonSignFlickerBakes?

    private typealias M = NeonSignFlickerMetrics
    private var scene: NeonSignFlickerScene { NeonSignFlickerScene.shared }

    var body: some View {
        // The block draws under the system bars and keeps its sign clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let layout = NeonSignFlickerLayout(size: size, top: insets.top, panel: scene.panel)
        // Read here, outside the timeline's closure, so the stage is rebuilt once the bakes arrive.
        let bakes = self.bakes
        return ZStack(alignment: .bottom) {
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, pieces: scene.pieces, faulty: scene.faulty, looping: auto, reduced: reduced)
                Canvas { context, _ in
                    NeonSignFlickerPainter.draw(&context, frame: frame, layout: layout, scene: scene, bakes: bakes)
                }
            }
            .allowsHitTesting(false)

            Text("Tap a word to switch it · Drag to swing")
                .font(.custom("AvenirNext-Medium", fixedSize: 13))
                .kerning(0.3)
                .foregroundStyle(Color(hex: 0x7d6e66))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
                .padding(.bottom, insets.bottom + 22)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: 0x0c0908))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            // One recogniser stands in for the race of a pan (after 4 pt of travel) and a tap (a
            // touch lifted within 350 ms that never travelled that far).
            DragGesture(minimumDistance: 0)
                .onChanged { moved($0, layout: layout, reduced: reduced) }
                .onEnded { ended($0, layout: layout) }
        )
        .onChange(of: auto) { engine.restartScript() }
        .task(id: NeonSignFlickerBakeKey(width: size.width, height: size.height, scale: displayScale)) {
            self.bakes = NeonSignFlickerBakes.make(scene: scene, size: size, wallScale: min(3, displayScale))
        }
        .accessibilityElement()
        .accessibilityLabel("Neon sign: open, late, cocktail glass. Tap a word to switch it, drag to swing the sign.")
    }

    private func moved(_ value: DragGesture.Value, layout: NeonSignFlickerLayout, reduced: Bool) {
        // A new touch, or one that started elsewhere than the last (that gesture was cancelled
        // without an end, e.g. by a system gesture): start over.
        if !touch.active || value.startLocation != touch.start {
            touch.active = true
            touch.panning = false
            touch.began = value.time
            touch.start = value.startLocation
            // A drag that was cut off (the gesture cancelled) lets go before a new one starts.
            engine.release(spin: 0, maxSpin: M.maxSpin, hold: M.userHold)
        }
        if !touch.panning && hypot(value.translation.width, value.translation.height) >= 4 {
            touch.panning = true
            let p = value.location
            if !reduced && hypot(p.x - layout.pivot.x, p.y - layout.pivot.y) >= 40 {
                engine.grab(angle: layout.angle(of: p), hold: M.userHold)
            }
        }
        if touch.panning {
            engine.drag(angle: layout.angle(of: value.location), maxSwing: M.maxSwing, rubber: M.rubber)
        }
    }

    private func ended(_ value: DragGesture.Value, layout: NeonSignFlickerLayout) {
        defer { touch.active = false }
        if touch.panning {
            let rx = value.location.x - layout.pivot.x
            let ry = value.location.y - layout.pivot.y
            let spin = (rx * value.velocity.height - ry * value.velocity.width) / max(rx * rx + ry * ry, 900)
            engine.release(spin: spin, maxSpin: M.maxSpin, hold: M.userHold)
            return
        }
        guard value.time.timeIntervalSince(touch.began) <= 0.35, let st = engine.sim else { return }
        // The finger in the sign's frame.
        let dx = value.location.x - layout.pivot.x
        let dy = value.location.y - layout.pivot.y
        let c = cos(st.theta)
        let s = sin(st.theta)
        let lx = layout.pivotLocal.x + c * dx + s * dy
        let ly = layout.pivotLocal.y - s * dx + c * dy
        var hit = -1
        var best = Double.infinity
        for piece in scene.pieces {
            let b = piece.box
            let ox = max(b.minX - lx, 0, lx - b.maxX)
            let oy = max(b.minY - ly, 0, ly - b.maxY)
            let off = hypot(ox, oy)
            if off < 14 && off < best {
                best = off
                hit = piece.word
            }
        }
        if hit < 0 { return }
        engine.touched(hold: M.userHold)
        st.setWord(hit, st.on[hit] == 1 ? 0 : 1)
        touch.haptic.impactOccurred()
    }
}

/// The finger's state between gesture callbacks.
private final class NeonSignFlickerTouch {
    var active = false
    var panning = false
    var began = Date.distantPast
    var start = CGPoint.zero
    /// Made once and reused, so rapid taps do not spin up a generator each.
    let haptic = UIImpactFeedbackGenerator(style: .light)
}

enum NeonSignFlickerMetrics {
    // The colours of the gas, per tone (rose, ice cyan, amber, lime): the bright core of a lit
    // tube, its white-hot centre, the colour of its halo, and the coated glass when it is dark.
    static let core: [UInt32] = [0xff6fae, 0x45dcff, 0xffc061, 0xd4ff70]
    static let hot: [UInt32] = [0xffe6f1, 0xc9f6ff, 0xfff3da, 0xf7ffe2]
    // Cyan reads far brighter than rose at the same strength: each tone's halo is scaled to match.
    static let haloGain = [1, 0.5, 0.85, 0.9]
    static let halo: [UInt32] = [0xff2d7c, 0x14c4ff, 0xff8a1c, 0x8cff26]
    static let unlit: [UInt32] = [0x4a3440, 0x2f4650, 0x4d4234, 0x3f4a32]
    // The light each word throws on the wall (rose, cyan, amber), linear 0…1.
    static let spill: [[Double]] = [[1.0, 0.2, 0.48], [0.1, 0.62, 1.0], [1.0, 0.5, 0.14]]

    // The tube: glass diameter, lit core, white-hot centre (pt).
    static let tube = 6.0
    static let coreWidth = 4.4
    static let hotWidth = 1.7
    // The blacked-out glass at a tube's electrode end (pt).
    static let capLength = 3.6
    // Halo layers baked around each tube: stroke width and blur (pt), and strength.
    static let haloLayers: [(width: Double, blur: Double, alpha: Double)] = [(26, 15, 0.3), (12, 5.5, 0.55), (6, 2, 0.85)]
    static let haloPad = 34.0
    static let haloScale = 2.0
    // The light on the wall is baked at this many pixels per pt, this far around the sign (pt).
    static let spillScale = 0.2
    static let spillMargin = 300.0

    // The clear acrylic panel the tubes are mounted on, around the lettering (pt).
    static let panelPadX = 30.0
    static let panelPadY = 30.0
    static let panelRadius = 14.0
    // The two chains meet at a hook `hang` pt above the panel; they hold it `chainInset` from its corners.
    static let hang = 96.0
    static let chainInset = 20.0
    static let link = 6.4
    // The panel's centre, as a share of the screen's height.
    static let centerAt = 0.45
    // The swing a finger can give before it resists (rad), and how hard it resists past that.
    static let maxSwing = 0.5
    static let rubber = 0.35
    static let maxSpin = 2.0
    // After a touch the loop waits this long (s), then starts over.
    static let userHold = 4.0
    // The tube that is allowed to stutter: the "t" of "late".
    static let faultyWord = 1
    static let faultyOrder = 2

    static func rgb(_ hex: UInt32) -> (Double, Double, Double) {
        (Double((hex >> 16) & 0xff) / 255, Double((hex >> 8) & 0xff) / 255, Double(hex & 0xff) / 255)
    }

    static func color(_ c: (Double, Double, Double), _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: c.0, green: c.1, blue: c.2, opacity: alpha)
    }
}

/// The sign's geometry: the tubes as paths, their electrode ends, the mounts and the panel.
struct NeonSignFlickerScene {
    let pieces: [NeonSignFlickerPiece]
    let paths: [[Path]]
    let caps: Path
    let mounts: [CGPoint]
    let panel: CGRect
    let faulty: Int

    static let shared = NeonSignFlickerScene()

    private init() {
        typealias M = NeonSignFlickerMetrics
        let sign = NeonSignFlickerTubes.buildSign()
        pieces = sign.pieces
        paths = sign.pieces.map { $0.tubes.map(Self.path) }
        panel = CGRect(x: -sign.width / 2 - M.panelPadX, y: -sign.height / 2 - M.panelPadY, width: sign.width + 2 * M.panelPadX, height: sign.height + 2 * M.panelPadY)
        // Where a tube passes through the panel the glass is painted black (the electrode ends),
        // and each piece sits on one clear standoff.
        var caps = Path()
        var mounts: [CGPoint] = []
        for piece in sign.pieces {
            let t = piece.tubes[0]
            let n = t.count
            var ends: [(Double, Double, Double, Double)] = []
            if piece.ends.0 { ends.append((t[0], t[1], t[2] - t[0], t[3] - t[1])) }
            if piece.ends.1 { ends.append((t[n - 2], t[n - 1], t[n - 4] - t[n - 2], t[n - 3] - t[n - 1])) }
            for (x, y, dx, dy) in ends {
                let h = hypot(dx, dy)
                let len = h == 0 ? 1 : h
                caps.move(to: CGPoint(x: x, y: y))
                caps.addLine(to: CGPoint(x: x + dx / len * M.capLength, y: y + dy / len * M.capLength))
            }
            let mid = (t.count / 16) * 8
            mounts.append(CGPoint(x: t[mid], y: t[mid + 1]))
        }
        self.caps = caps
        self.mounts = mounts
        faulty = sign.pieces.firstIndex { $0.word == M.faultyWord && $0.order == M.faultyOrder } ?? -1
    }

    private static func path(_ seg: [Double]) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: seg[0], y: seg[1]))
        var i = 0
        while i < seg.count {
            p.addCurve(
                to: CGPoint(x: seg[i + 6], y: seg[i + 7]),
                control1: CGPoint(x: seg[i + 2], y: seg[i + 3]),
                control2: CGPoint(x: seg[i + 4], y: seg[i + 5])
            )
            i += 8
        }
        return p
    }
}

/// Where the sign hangs on this screen.
struct NeonSignFlickerLayout {
    let size: CGSize
    /// The hook on the wall, and where it sits in the sign's own frame.
    let pivot: CGPoint
    let pivotLocal: CGPoint
    let anchors: [CGPoint]

    init(size: CGSize, top: Double, panel: CGRect) {
        typealias M = NeonSignFlickerMetrics
        self.size = size
        pivot = CGPoint(x: size.width / 2, y: max(top + 34, size.height * M.centerAt - panel.height / 2 - M.hang))
        pivotLocal = CGPoint(x: 0, y: panel.minY - M.hang)
        anchors = [
            CGPoint(x: panel.minX + M.chainInset, y: panel.minY + 11),
            CGPoint(x: panel.maxX - M.chainInset, y: panel.minY + 11),
        ]
    }

    /// The angle of `p` around the hook, measured from straight down, clockwise.
    func angle(of p: CGPoint) -> Double {
        atan2(-(p.x - pivot.x), p.y - pivot.y)
    }
}

struct NeonSignFlickerBakeKey: Equatable {
    let width: Double
    let height: Double
    let scale: Double
}

/// Every image the sign is drawn with, rendered once when the size is known.
struct NeonSignFlickerBakes {
    struct Placed {
        let image: Image
        let rect: CGRect
    }

    let glows: [Placed]
    let spill: Image
    let spillRect: CGRect
    let wall: Image
    let wallSize: CGSize
    /// The panel's soft shadow (sign frame) and the hook plate's (screen).
    let shadow: Placed
    let plateShadow: Image
    let plateShadowPad: Double

    private typealias M = NeonSignFlickerMetrics

    @MainActor
    static func make(scene: NeonSignFlickerScene, size: CGSize, wallScale: Double) -> NeonSignFlickerBakes? {
        guard size.width > 0, size.height > 0 else { return nil }
        var glows: [Placed] = []
        for (i, piece) in scene.pieces.enumerated() {
            guard let g = bakeGlow(piece, scene.paths[i]) else { return nil }
            glows.append(g)
        }
        let box = scene.panel.insetBy(dx: M.panelPadX, dy: M.panelPadY)
        guard let spill = bakeSpill(scene, box: box),
              let wall = render(size.width, size.height, scale: wallScale, opaque: true, { bakeWall(&$0, size) }),
              let shadow = bakeSoftRect(scene.panel.size, radius: M.panelRadius, alpha: 0.32, blur: 12),
              let plate = bakeSoftRect(CGSize(width: 36, height: 12), radius: 3, alpha: 0.5, blur: 3)
        else { return nil }
        return NeonSignFlickerBakes(
            glows: glows,
            spill: Image(decorative: spill.image, scale: M.spillScale),
            spillRect: spill.rect,
            wall: Image(decorative: wall, scale: wallScale),
            wallSize: size,
            shadow: Placed(image: Image(decorative: shadow, scale: 1), rect: scene.panel.insetBy(dx: -36, dy: -36)),
            plateShadow: Image(decorative: plate, scale: 2),
            plateShadowPad: 9
        )
    }

    @MainActor
    private static func render(_ width: Double, _ height: Double, scale: Double, opaque: Bool = false, _ draw: @escaping (inout GraphicsContext) -> Void) -> CGImage? {
        let renderer = ImageRenderer(content: Canvas { context, _ in draw(&context) }.frame(width: width, height: height))
        renderer.scale = scale
        renderer.isOpaque = opaque
        return renderer.cgImage
    }

    private static func roundStroke(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// One piece's halo at full brightness, in its own small image.
    @MainActor
    private static func bakeGlow(_ piece: NeonSignFlickerPiece, _ paths: [Path]) -> Placed? {
        let b = piece.box
        let w = b.width + 2 * M.haloPad
        let h = b.height + 2 * M.haloPad
        let image = render(w, h, scale: M.haloScale) { context in
            context.translateBy(x: M.haloPad - b.minX, y: M.haloPad - b.minY)
            context.blendMode = .plusLighter
            for (k, layer) in M.haloLayers.enumerated() {
                let hex = k == M.haloLayers.count - 1 ? M.core[piece.tone] : M.halo[piece.tone]
                let color = M.color(M.rgb(hex), layer.alpha * M.haloGain[piece.tone])
                for path in paths {
                    context.drawLayer { l in
                        l.addFilter(.blur(radius: layer.blur))
                        l.stroke(path, with: .color(color), style: roundStroke(layer.width))
                    }
                }
            }
        }
        guard let image else { return nil }
        return Placed(image: Image(decorative: image, scale: M.haloScale), rect: CGRect(x: b.minX - M.haloPad, y: b.minY - M.haloPad, width: w, height: h))
    }

    /// The light the sign throws on the wall, one word per channel, in the sign's frame.
    @MainActor
    private static func bakeSpill(_ scene: NeonSignFlickerScene, box: CGRect) -> (image: CGImage, rect: CGRect)? {
        let x0 = box.minX - M.spillMargin
        let y0 = box.minY - M.spillMargin
        let w = box.width + 2 * M.spillMargin
        let h = box.height + 2 * M.spillMargin
        let channel: [Color] = [Color(.sRGB, red: 1, green: 0, blue: 0), Color(.sRGB, red: 0, green: 1, blue: 0), Color(.sRGB, red: 0, green: 0, blue: 1)]
        let image = render(w, h, scale: M.spillScale, opaque: true) { context in
            context.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .color(.black))
            context.translateBy(x: -x0, y: -y0)
            context.blendMode = .plusLighter
            for word in 0..<3 {
                var bounds = CGRect.null
                for (i, piece) in scene.pieces.enumerated() where piece.word == word {
                    for path in scene.paths[i] {
                        for (width, alpha, blur) in [(34.0, 0.5, 52.0), (18.0, 0.55, 20.0)] {
                            context.drawLayer { l in
                                l.addFilter(.blur(radius: blur))
                                l.stroke(path, with: .color(channel[word].opacity(alpha)), style: roundStroke(width))
                            }
                        }
                    }
                    bounds = bounds.union(piece.box)
                }
                // A broad wash that reaches across the wall.
                let c = CGPoint(x: bounds.midX, y: bounds.midY)
                var wash = context
                wash.opacity = 0.3
                wash.fill(
                    Path(ellipseIn: CGRect(x: c.x - 260, y: c.y - 260, width: 520, height: 520)),
                    with: .radialGradient(Gradient(colors: [channel[word], .black]), center: c, startRadius: 0, endRadius: 260)
                )
            }
        }
        guard let image else { return nil }
        // The image covers whole pixels, a little more than the area asked for.
        let pw = Double(image.width) / M.spillScale
        let ph = Double(image.height) / M.spillScale
        return (image, CGRect(x: x0, y: y0, width: pw, height: ph))
    }

    /// A rounded rectangle's soft black shadow, padded by three blur radii.
    @MainActor
    private static func bakeSoftRect(_ size: CGSize, radius: Double, alpha: Double, blur: Double) -> CGImage? {
        let pad = 3 * blur
        return render(size.width + 2 * pad, size.height + 2 * pad, scale: 2) { context in
            context.addFilter(.blur(radius: blur))
            context.fill(
                Path(roundedRect: CGRect(x: pad, y: pad, width: size.width, height: size.height), cornerRadius: radius),
                with: .color(.black.opacity(alpha))
            )
        }
    }

    /// The brick wall's own colour, without light: running bond, each brick a little different.
    private static func bakeWall(_ c: inout GraphicsContext, _ size: CGSize) {
        let width = size.width
        let height = size.height
        var rand = NeonSignFlickerRandom(seed: 7121)
        func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
            Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
        }
        func rgb(_ v: [Double]) -> Color {
            Color(.sRGB, red: v[0].rounded() / 255, green: v[1].rounded() / 255, blue: v[2].rounded() / 255)
        }
        func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: x0, y: y0))
            p.addLine(to: CGPoint(x: x1, y: y1))
            return p
        }
        func blurred(_ path: Path, _ color: Color, _ blur: Double) {
            c.drawLayer { l in
                l.addFilter(.blur(radius: blur))
                l.fill(path, with: .color(color))
            }
        }
        c.fill(Path(CGRect(x: 0, y: 0, width: width, height: height)), with: .color(Color(hex: 0x7a7166)))
        // Grit in the mortar.
        for _ in 0..<1600 {
            let light = rand.next() > 0.5
            let x = rand.next() * width
            let y = rand.next() * height
            let r = 0.3 + rand.next() * 0.6
            c.fill(circle(x, y, r), with: .color(Color(hex: light ? 0xa49a8c : 0x4a443d, opacity: 0.5)))
        }
        let row = 25.0
        let mortar = 3.4
        let brick = 62.0
        let tones: [[Double]] = [[150, 70, 50], [134, 60, 44], [162, 84, 60], [120, 56, 44], [100, 48, 40], [172, 98, 72], [142, 76, 58]]
        var r = 0
        while Double(r) * row < height + row {
            let y = Double(r) * row + mortar / 2
            let h = row - mortar
            let shift = Double(r % 2) * (brick / 2) + (rand.next() - 0.5) * 6
            var x = -brick + shift
            while x < width + brick {
                let w = brick - mortar + (rand.next() - 0.5) * 2
                let tone = tones[Int((rand.next() * Double(tones.count)).rounded(.down))]
                let k = 0.85 + rand.next() * 0.25
                let base = tone.map { $0 * k }
                let rect = CGRect(x: x + mortar / 2, y: y, width: w, height: h)
                c.fill(
                    Path(roundedRect: rect, cornerRadius: 2.4),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: rgb(base.map { $0 * 1.1 }), location: 0),
                            .init(color: rgb(base), location: 0.35),
                            .init(color: rgb(base.map { $0 * 0.78 }), location: 1),
                        ]),
                        startPoint: CGPoint(x: 0, y: y),
                        endPoint: CGPoint(x: 0, y: y + h)
                    )
                )
                // Pits and flecks, and now and then a darker burn or a chipped corner.
                for _ in 0..<16 {
                    let dark = rand.next() > 0.35
                    let fx = x + mortar / 2 + rand.next() * w
                    let fy = y + rand.next() * h
                    let fr = 0.35 + rand.next() * 1.1
                    let color = dark ? rgb(base.map { $0 * 0.6 }) : rgb(base.map { min(255, $0 * 1.35) })
                    c.fill(circle(fx, fy, fr), with: .color(color.opacity(0.45)))
                }
                if rand.next() > 0.72 {
                    let bx = x + mortar / 2 + rand.next() * w
                    let by = y + rand.next() * h
                    let br = 4 + rand.next() * 7
                    blurred(circle(bx, by, br), Color(hex: 0x2a1410, opacity: 0.22), 4)
                }
                if rand.next() > 0.85 {
                    let cx = rand.next() > 0.5 ? x + mortar / 2 : x + mortar / 2 + w
                    let cy = y + (rand.next() > 0.5 ? 0 : h)
                    let cr = 2.5 + rand.next() * 2
                    c.fill(circle(cx, cy, cr), with: .color(Color(hex: 0x6d655b)))
                }
                // The lower edge sits in shadow, the upper one catches the light from above.
                c.stroke(line(x + mortar / 2 + 2, y + h - 0.4, x + mortar / 2 + w - 2, y + h - 0.4), with: .color(.black.opacity(0.32)), style: roundStroke(1))
                c.stroke(line(x + mortar / 2 + 2, y + 0.5, x + mortar / 2 + w - 2, y + 0.5), with: .color(Color(hex: 0xffd9c4, opacity: 0.12)), style: roundStroke(0.8))
                x += brick
            }
            r += 1
        }
        // Soot and damp, a few broad stains.
        for _ in 0..<9 {
            let sx = rand.next() * width
            let sy = rand.next() * height
            let sr = 40 + rand.next() * 90
            blurred(circle(sx, sy, sr), Color(hex: 0x120a08, opacity: 0.16), 36)
        }
    }
}

/// Seeded random in [0, 1): mulberry32, with JavaScript's 32-bit wrapping maths.
struct NeonSignFlickerRandom {
    private var a: UInt32

    init(seed: UInt32) { a = seed }

    mutating func next() -> Double {
        a = a &+ 0x6d2b79f5
        var t = a
        t = (t ^ (t >> 15)) &* (t | 1)
        t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
        return Double(t ^ (t >> 14)) / 4_294_967_296
    }
}

/// Draws one frame of the sign.
enum NeonSignFlickerPainter {
    private typealias M = NeonSignFlickerMetrics

    private static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    private static func stroke(_ width: Double, cap: CGLineCap = .round) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: cap, lineJoin: .round)
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    /// An arc of the ellipse inscribed in `rect`, from `start` through `sweep` degrees (Skia's `drawArc`).
    private static func arc(_ rect: CGRect, _ start: Double, _ sweep: Double) -> Path {
        var p = Path()
        p.addRelativeArc(
            center: .zero, radius: 1, startAngle: .degrees(start), delta: .degrees(sweep),
            transform: CGAffineTransform(translationX: rect.midX, y: rect.midY).scaledBy(x: rect.width / 2, y: rect.height / 2)
        )
        return p
    }

    /// Moves the context into the sign's frame: everything that hangs turns `theta` about the hook.
    private static func enterSign(_ c: inout GraphicsContext, _ layout: NeonSignFlickerLayout, _ theta: Double) {
        c.translateBy(x: layout.pivot.x, y: layout.pivot.y)
        c.rotate(by: .radians(theta))
        c.translateBy(x: -layout.pivotLocal.x, y: -layout.pivotLocal.y)
    }

    static func draw(_ context: inout GraphicsContext, frame: NeonSignFlickerFrame, layout: NeonSignFlickerLayout, scene: NeonSignFlickerScene, bakes: NeonSignFlickerBakes?) {
        let size = layout.size
        guard let bakes, size.width > 0, size.height > 0 else {
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(hex: 0x0c0908)))
            return
        }
        let levels = frame.words
        let theta = frame.theta
        let panel = scene.panel

        // The wall, lit by the street and by the sign.
        let wall = ShaderLibrary.neonSignFlickerWall(
            .image(bakes.wall),
            .image(bakes.spill),
            .float2(bakes.wallSize),
            .float4(bakes.spillRect.minX, bakes.spillRect.minY, bakes.spillRect.width, bakes.spillRect.height),
            .float2(layout.pivot),
            .float2(layout.pivotLocal),
            .float2(cos(theta), sin(theta)),
            .float3(levels[0], levels[1], levels[2]),
            .float3(M.spill[0][0], M.spill[0][1], M.spill[0][2]),
            .float3(M.spill[1][0], M.spill[1][1], M.spill[1][2]),
            .float3(M.spill[2][0], M.spill[2][1], M.spill[2][2]),
            .float2(size)
        )
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .shader(wall))

        // The light the sign itself gives off, as a colour: tints its chains and the panel's edge.
        var lr = 0.0
        var lg = 0.0
        var lb = 0.0
        for w in 0..<3 {
            lr += M.spill[w][0] * levels[w]
            lg += M.spill[w][1] * levels[w]
            lb += M.spill[w][2] * levels[w]
        }
        let glowAmount = min(1, (levels[0] + levels[1] + levels[2]) / 1.6)
        let peak = max(lr, lg, lb, 0.001)
        let tint = (lr / peak, lg / peak, lb / peak)

        // Its soft shadow from the street light above.
        var shadow = context
        shadow.translateBy(x: 0, y: 14)
        enterSign(&shadow, layout, theta)
        shadow.draw(bakes.shadow.image, in: bakes.shadow.rect)

        var c = context
        enterSign(&c, layout, theta)

        // Chains: links alternately seen face-on and edge-on.
        let steel = Color(.sRGB, red: mix(0.42, tint.0, 0.55 * glowAmount), green: mix(0.43, tint.1, 0.55 * glowAmount), blue: mix(0.46, tint.2, 0.55 * glowAmount))
        let shine = Color.white.opacity(0.35)
        let pl = layout.pivotLocal
        for a in layout.anchors {
            let dx = a.x - pl.x
            let dy = a.y - pl.y
            let len = hypot(dx, dy)
            let ux = dx / len
            let uy = dy / len
            let count = Int((len / M.link).rounded(.down))
            for k in 0..<count {
                let cx = pl.x + ux * (Double(k) + 0.5) * M.link
                let cy = pl.y + uy * (Double(k) + 0.5) * M.link
                if k % 2 == 0 {
                    var l = c
                    l.translateBy(x: cx, y: cy)
                    l.rotate(by: .radians(atan2(uy, ux)))
                    l.stroke(Path(ellipseIn: CGRect(x: -4.6, y: -2.6, width: 9.2, height: 5.2)), with: .color(steel), lineWidth: 1.5)
                    l.stroke(arc(CGRect(x: -4.0, y: -2.1, width: 8, height: 4.2), 200, 90), with: .color(shine), style: stroke(0.6))
                } else {
                    c.stroke(line(cx - ux * 3.6, cy - uy * 3.6, cx + ux * 3.6, cy + uy * 3.6), with: .color(steel), style: stroke(2.1))
                }
            }
        }

        // The clear acrylic panel: barely there, but its edges carry the neon's light.
        let panelPath = Path(roundedRect: panel, cornerRadius: M.panelRadius)
        c.fill(panelPath, with: .color(.white.opacity(0.035)))
        c.stroke(Path(roundedRect: panel.insetBy(dx: 3, dy: 3), cornerRadius: M.panelRadius - 3), with: .color(.white.opacity(0.08)), style: stroke(0.7))
        let edge = Color(.sRGB, red: mix(0.8, tint.0, glowAmount), green: mix(0.82, tint.1, glowAmount), blue: mix(0.86, tint.2, glowAmount), opacity: 0.16 + 0.5 * glowAmount)
        c.stroke(panelPath, with: .color(edge), lineWidth: 1.4)
        // The holes the chains go through, with steel grommets.
        for a in layout.anchors {
            c.fill(circle(a.x, a.y, 4.2), with: .color(Color(hex: 0x0a0a0c, opacity: 0.9)))
            c.stroke(circle(a.x, a.y, 4.2), with: .color(Color(hex: 0x9a9ca2, opacity: 0.9)), style: stroke(1.3))
            c.stroke(line(a.x, a.y - 4.2, a.x, a.y - 1), with: .color(Color(hex: 0xb9bbc0)), style: stroke(2))
        }

        // Standoffs under the tubes.
        for m in scene.mounts {
            c.fill(circle(m.x, m.y, 4.4), with: .color(Color(hex: 0xc9d2da, opacity: 0.16)))
            c.stroke(circle(m.x, m.y, 4.4), with: .color(.white.opacity(0.25)), style: stroke(0.6))
        }

        // The tubes: coated glass, the lit gas inside it, its white-hot centre and a glint on the glass.
        for (i, piece) in scene.pieces.enumerated() {
            let l = frame.level[i]
            let unlit = M.rgb(M.unlit[piece.tone])
            let lit = M.rgb(M.core[piece.tone])
            let core = Color(.sRGB, red: mix(unlit.0, lit.0, l), green: mix(unlit.1, lit.1, l), blue: mix(unlit.2, lit.2, l))
            let hot = M.color(M.rgb(M.hot[piece.tone]), min(1, l * l * 1.1))
            for path in scene.paths[i] {
                c.stroke(path, with: .color(M.color(unlit)), style: stroke(M.tube))
                c.stroke(path, with: .color(core), style: stroke(M.coreWidth))
                if l > 0.02 { c.stroke(path, with: .color(hot), style: stroke(M.hotWidth)) }
            }
            var glint = c
            glint.translateBy(x: -0.9, y: -1.2)
            for path in scene.paths[i] {
                glint.stroke(path, with: .color(.white.opacity(0.34 * (1 - 0.75 * l))), style: stroke(0.9))
            }
        }
        c.stroke(scene.caps, with: .color(Color(hex: 0x141216)), style: stroke(M.tube + 0.4))

        // The halos, added on top as light.
        var halo = c
        halo.blendMode = .plusLighter
        for i in scene.pieces.indices {
            let l = frame.level[i]
            if l < 0.01 { continue }
            halo.opacity = min(1, pow(l, 1.4))
            halo.draw(bakes.glows[i].image, in: bakes.glows[i].rect)
        }

        // The hook: a small iron plate screwed to the wall, and the ring the chains hang from.
        let px = layout.pivot.x
        let py = layout.pivot.y
        let pad = bakes.plateShadowPad
        context.draw(bakes.plateShadow, in: CGRect(x: px - 18 - pad, y: py - 10 - pad, width: 36 + 2 * pad, height: 12 + 2 * pad))
        context.fill(
            Path(roundedRect: CGRect(x: px - 18, y: py - 13, width: 36, height: 12), cornerRadius: 3),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x4a4b50), location: 0),
                    .init(color: Color(hex: 0x26272b), location: 0.5),
                    .init(color: Color(hex: 0x151518), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: py - 13),
                endPoint: CGPoint(x: 0, y: py - 1)
            )
        )
        for sx in [-11.0, 11.0] {
            context.fill(circle(px + sx, py - 7, 2.2), with: .color(Color(hex: 0x6b6d72)))
            context.stroke(line(px + sx - 1.5, py - 7, px + sx + 1.5, py - 7), with: .color(Color(hex: 0x1a1a1d)), style: stroke(0.8))
        }
        context.stroke(circle(px, py - 1, 4.6), with: .color(Color(hex: 0x8c8f95)), style: stroke(2))
        context.stroke(arc(CGRect(x: px - 4.6, y: py - 5.6, width: 9.2, height: 9.2), 200, 80), with: .color(.white.opacity(0.5)), style: stroke(0.7))
    }
}
