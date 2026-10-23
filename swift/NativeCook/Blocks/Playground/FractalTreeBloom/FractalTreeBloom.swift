import SwiftUI
import UIKit

/// A fractal tree on a small mound. It grows from a seed, level by level, sways in a wind that
/// reaches its twigs last, and blossoms; each tap turns the season (summer leaves, autumn colour and
/// falling leaves, winter snow), dragging across bends the wind, and holding draws it back into its
/// seed to grow again.
struct FractalTreeBloom: View {
    /// Grows the tree, blooms it and turns it through the seasons, on a loop.
    var autoPlay = false
    /// The seed the tree grows from: the same seed grows the same tree.
    var seed = 27

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = FractalTreeBloomModel()
    @State private var seasonName = 0
    // True while a finger is down; SwiftUI resets it even when the drag is cancelled without onEnded.
    @GestureState private var touching = false

    private static let seasons = ["Spring", "Summer", "Autumn", "Winter"]
    // Reanimated's default timing curve (ease in and out, quadratic) for the season's name.
    private static let fade = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
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
        .onAppear {
            model.onSeason = { name in
                withAnimation(nil) { seasonName = name }
            }
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let seed = seed
        let bottom = insets.bottom
        return ZStack(alignment: .top) {
            Color(hex: 0xf6e6df)

            TimelineView(.animation) { timeline in
                let date = timeline.date
                Canvas { context, canvasSize in
                    model.render(context, size: canvasSize, date: date, bottomInset: bottom, auto: auto, reduced: reduced, seed: seed)
                }
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .updating($touching) { _, state, _ in state = true }
                    .onChanged { model.touchChanged($0) }
                    .onEnded { model.touchEnded($0) }
            )
            .onChange(of: touching) { _, now in
                if !now { model.touchMaybeCancelled() }
            }
            .accessibilityElement()
            .accessibilityLabel("A fractal tree. Tap for the next season, drag across to bend the wind, hold to grow it again.")

            ZStack(alignment: .topLeading) {
                Text("SEED NO. \(seed)")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.6)
                    .foregroundStyle(Color.rgba(52, 38, 34, 0.62))
                Text(Self.seasons[min(Self.seasons.count - 1, max(0, seasonName))])
                    .font(.system(size: 30, weight: .semibold))
                    .kerning(-0.3)
                    .foregroundStyle(Color(hex: 0x33241f))
                    .padding(.top, 18)
                    .id(seasonName)
                    .transition(.asymmetric(
                        insertion: .opacity.animation(.timingCurve(Self.fade, duration: 0.38).delay(0.16)),
                        removal: .opacity.animation(.timingCurve(Self.fade, duration: 0.15))
                    ))
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .frame(height: 64, alignment: .topLeading)
            .padding(.horizontal, 24)
            .padding(.top, insets.top + 22)
            .allowsHitTesting(false)

            Text("Drag to bend the wind · tap for the next season")
                .font(.system(size: 13, weight: .medium))
                .kerning(0.2)
                .foregroundStyle(Color.rgba(40, 30, 26, 0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .frame(width: size.width, height: size.height - insets.bottom - 18, alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
    }
}

/// The engine, stepped once a frame like the RN frame callback, its drawing and the touch tracking.
@MainActor
final class FractalTreeBloomModel {
    private typealias T = FractalTreeBloomTuning

    private var engine: FractalTreeBloomEngine?
    private var last: Date?
    private var wasAuto: Bool?
    private var sprites: FractalTreeBloomSprites?
    var onSeason: ((Int) -> Void)?

    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    // The touch: Gesture Handler's race of a pan (past 10 pt across), a long press (0.5 s, 10 pt) and
    // a tap (0.3 s), from one drag gesture.
    private var touchStart: CGPoint?
    private var touchTime = Date.distantPast
    private var panning = false
    private var pressed = false
    private var pressTimer: DispatchWorkItem?

    func render(
        _ context: GraphicsContext, size: CGSize, date: Date, bottomInset: Double,
        auto: Bool, reduced: Bool, seed: Int
    ) {
        let engine: FractalTreeBloomEngine
        if let e = self.engine, e.reduced == reduced, e.seed == seed {
            engine = e
        } else {
            engine = FractalTreeBloomEngine(auto: auto, reduced: reduced, seed: seed)
            self.engine = engine
            wasAuto = auto
        }
        if wasAuto != auto {
            wasAuto = auto
            engine.setAuto(auto)
        }
        // RN's first frame reports no previous frame: 16 ms.
        let raw = last.map { date.timeIntervalSince($0) } ?? 0.016
        last = date
        engine.frame(max(0, raw), width: size.width, height: size.height, bottomInset: bottomInset)
        let ev = engine.events
        if ev != 0 {
            engine.events = 0
            if ev & T.eventTurned != 0 { selection.selectionChanged() }
            if ev & T.eventPlanted != 0 { light.impactOccurred() }
            if ev & T.eventSeason != 0 {
                let name = engine.shownSeason
                let callback = onSeason
                DispatchQueue.main.async { callback?(name) }
            }
        }
        guard engine.tree != nil else { return }
        let sprites = self.sprites ?? FractalTreeBloomSprites()
        self.sprites = sprites
        FractalTreeBloomDrawing(engine: engine, sprites: sprites, size: size).draw(context)
    }

    func touchChanged(_ value: DragGesture.Value) {
        guard let engine else { return }
        // A new touch while the last one never ended (a cancelled drag): let the last one go first.
        if let start = touchStart, start != value.startLocation { cancelTouch() }
        if touchStart == nil {
            touchStart = value.startLocation
            touchTime = Date()
            panning = false
            pressed = false
            let item = DispatchWorkItem { [weak self] in
                guard let self, self.touchStart != nil, !self.panning, !self.pressed else { return }
                self.pressed = true
                engine.longPress()
            }
            pressTimer = item
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: item)
        }
        guard let start = touchStart else { return }
        let tx = value.location.x - start.x
        let ty = value.location.y - start.y
        // A long press gives up once the finger wanders off.
        if hypot(tx, ty) > 10 { pressTimer?.cancel() }
        if panning {
            engine.windMove(translationX: tx)
        } else if !pressed && abs(tx) > 10 {
            panning = true
            pressTimer?.cancel()
            engine.windStart()
            engine.windMove(translationX: tx)
        }
    }

    /// The drag's state reset: if no `onEnded` came for this touch (the system cancelled it), let go
    /// of the wind so it is not held forever and the loop can resume.
    func touchMaybeCancelled() {
        guard let start = touchStart else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, self.touchStart == start else { return }
            self.cancelTouch()
        }
    }

    private func cancelTouch() {
        pressTimer?.cancel()
        pressTimer = nil
        if panning { engine?.windEnd(velocityX: 0) }
        touchStart = nil
        panning = false
        pressed = false
    }

    func touchEnded(_ value: DragGesture.Value) {
        pressTimer?.cancel()
        pressTimer = nil
        defer {
            touchStart = nil
            panning = false
            pressed = false
        }
        guard let engine, touchStart != nil else { return }
        if panning {
            engine.windEnd(velocityX: value.velocity.width)
        } else if !pressed && Date().timeIntervalSince(touchTime) <= 0.3 {
            engine.tap()
        }
    }
}

/// The sprite sheet, drawn once: two blossoms, a leaf, a petal and a snowflake, 64 pt cells. The
/// leaf and the petal are tinted per sprite: each comes as an alpha mask (filled with the tint) and a
/// shade (black where the grey sprite is darker than white), which together multiply the sprite by
/// the tint as the RN atlas does.
final class FractalTreeBloomSprites {
    static let cell = 64.0

    let blossomA: Image
    let blossomB: Image
    let leafMask: Image
    let leafShade: Image
    let petalMask: Image
    let petalShade: Image
    let snow: Image

    init() {
        let scale = UIScreen.main.scale
        let c = Self.cell / 2
        blossomA = Self.render(scale) { Self.blossom($0, outer: 0xfbe6ee, inner: 0xffffff, heart: 0xc2486c) }
        blossomB = Self.render(scale) { Self.blossom($0, outer: 0xf3a9c0, inner: 0xfcd9e4, heart: 0x9c2f55) }
        // The leaf: an almond with a midrib, in greys so it can be tinted.
        let leaf = Self.renderImage(scale) { g in
            let path = CGMutablePath()
            path.move(to: CGPoint(x: c, y: c - 28))
            path.addCurve(to: CGPoint(x: c, y: c + 28), control1: CGPoint(x: c + 17, y: c - 14), control2: CGPoint(x: c + 15, y: c + 14))
            path.addCurve(to: CGPoint(x: c, y: c - 28), control1: CGPoint(x: c - 15, y: c + 14), control2: CGPoint(x: c - 17, y: c - 14))
            path.closeSubpath()
            g.saveGState()
            g.addPath(path)
            g.clip()
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [Self.cg(0xffffff), Self.cg(0xb8b8b8)] as CFArray, locations: [0, 1])!
            g.drawLinearGradient(gradient, start: CGPoint(x: c - 16, y: c), end: CGPoint(x: c + 16, y: c), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            g.restoreGState()
            g.setStrokeColor(UIColor(red: 80 / 255, green: 80 / 255, blue: 80 / 255, alpha: 0.55).cgColor)
            g.setLineWidth(1.6)
            g.move(to: CGPoint(x: c, y: c - 24))
            g.addLine(to: CGPoint(x: c, y: c + 27))
            g.strokePath()
        }
        leafMask = Image(uiImage: leaf).renderingMode(.template)
        leafShade = Image(uiImage: Self.shade(leaf))
        // A single petal, white, to be tinted.
        let petal = Self.renderImage(scale) { g in
            g.saveGState()
            g.addEllipse(in: CGRect(x: c - 13, y: c - 18, width: 26, height: 36))
            g.clip()
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [Self.cg(0xffffff), Self.cg(0xe9e9e9)] as CFArray, locations: [0, 1])!
            g.drawRadialGradient(gradient, startCenter: CGPoint(x: c, y: c + 8), startRadius: 0, endCenter: CGPoint(x: c, y: c + 8), endRadius: 22, options: [.drawsAfterEndLocation])
            g.restoreGState()
        }
        petalMask = Image(uiImage: petal).renderingMode(.template)
        petalShade = Image(uiImage: Self.shade(petal))
        // A snowflake: a soft round flake.
        snow = Self.render(scale) { g in
            let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: [UIColor.white.cgColor, UIColor.white.withAlphaComponent(0).cgColor] as CFArray,
                locations: [0.35, 1]
            )!
            g.drawRadialGradient(gradient, startCenter: CGPoint(x: c, y: c), startRadius: 0, endCenter: CGPoint(x: c, y: c), endRadius: 20, options: [.drawsBeforeStartLocation])
        }
    }

    private static func cg(_ hex: UInt32) -> CGColor {
        UIColor(
            red: CGFloat((hex >> 16) & 255) / 255,
            green: CGFloat((hex >> 8) & 255) / 255,
            blue: CGFloat(hex & 255) / 255,
            alpha: 1
        ).cgColor
    }

    private static func renderImage(_ scale: CGFloat, _ draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: cell, height: cell), format: format).image { draw($0.cgContext) }
    }

    private static func render(_ scale: CGFloat, _ draw: (CGContext) -> Void) -> Image {
        Image(uiImage: renderImage(scale, draw))
    }

    /// Blossoms: five rounded petals around a deep centre with stamens.
    private static func blossom(_ g: CGContext, outer: UInt32, inner: UInt32, heart: UInt32) {
        let c = cell / 2
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [cg(inner), cg(outer)] as CFArray, locations: [0.15, 1])!
        for k in 0..<5 {
            let a = Double(k) / 5 * Double.pi * 2 - Double.pi / 2
            let px = c + cos(a) * 13
            let py = c + sin(a) * 13
            g.saveGState()
            g.translateBy(x: px, y: py)
            g.rotate(by: a + Double.pi / 2)
            g.addEllipse(in: CGRect(x: -10, y: -14, width: 20, height: 27))
            g.clip()
            // The gradient is centred on the blossom, in the sheet's space.
            g.rotate(by: -(a + Double.pi / 2))
            g.translateBy(x: -px, y: -py)
            g.drawRadialGradient(gradient, startCenter: CGPoint(x: c, y: c), startRadius: 0, endCenter: CGPoint(x: c, y: c), endRadius: 30, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            g.restoreGState()
        }
        g.setFillColor(cg(heart))
        g.fillEllipse(in: CGRect(x: c - 5.5, y: c - 5.5, width: 11, height: 11))
        g.setFillColor(cg(0xf6d36b))
        for k in 0..<7 {
            let a = Double(k) / 7 * Double.pi * 2
            g.fillEllipse(in: CGRect(x: c + cos(a) * 7.5 - 1.6, y: c + sin(a) * 7.5 - 1.6, width: 3.2, height: 3.2))
        }
    }

    /// Black where the sprite is darker than white, so tint × grey = tint, then this shade on top.
    private static func shade(_ image: UIImage) -> UIImage {
        guard let cgImage = image.cgImage else { return image }
        let w = cgImage.width
        let h = cgImage.height
        var px = [UInt8](repeating: 0, count: w * h * 4)
        let space = CGColorSpaceCreateDeviceRGB()
        let info = CGImageAlphaInfo.premultipliedLast.rawValue
        guard let ctx = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: space, bitmapInfo: info) else { return image }
        ctx.draw(cgImage, in: CGRect(x: 0, y: 0, width: w, height: h))
        for i in 0..<(w * h) {
            let a = Double(px[i * 4 + 3]) / 255
            if a <= 0 { continue }
            // Unpremultiplied grey of the sprite, then the darkening as black with that much alpha.
            let grey = Double(px[i * 4]) / 255 / a
            let dark = max(0, min(1, 1 - grey)) * a
            px[i * 4] = 0
            px[i * 4 + 1] = 0
            px[i * 4 + 2] = 0
            px[i * 4 + 3] = UInt8((dark * 255).rounded())
        }
        guard let out = ctx.makeImage() else { return image }
        return UIImage(cgImage: out, scale: image.scale, orientation: .up)
    }
}

/// Draws one frame: the backdrop, the branches, the foliage, what falls and the seed.
struct FractalTreeBloomDrawing {
    private typealias S = FractalTreeBloomSimTuning

    let engine: FractalTreeBloomEngine
    let sprites: FractalTreeBloomSprites
    let size: CGSize

    // Colours by season (spring, summer, autumn, winter): sky top and bottom, far hills, ground, bark.
    static let skyTop: [[Double]] = [[0.96, 0.84, 0.82], [0.72, 0.86, 0.9], [0.93, 0.66, 0.47], [0.47, 0.55, 0.66]]
    static let skyLow: [[Double]] = [[0.99, 0.94, 0.89], [0.97, 0.95, 0.85], [0.99, 0.88, 0.74], [0.8, 0.85, 0.9]]
    static let hills: [[Double]] = [[0.82, 0.86, 0.74], [0.6, 0.74, 0.6], [0.82, 0.6, 0.42], [0.78, 0.82, 0.88]]
    static let groundColors: [[Double]] = [[0.56, 0.7, 0.45], [0.38, 0.56, 0.32], [0.62, 0.45, 0.27], [0.93, 0.95, 0.97]]
    static let bark: [[Double]] = [[0.3, 0.22, 0.2], [0.27, 0.21, 0.17], [0.28, 0.19, 0.15], [0.22, 0.2, 0.21]]
    // Leaves: summer greens, then autumn gold, orange and crimson.
    static let greenA = [0.36, 0.6, 0.3]
    static let greenB = [0.2, 0.46, 0.27]
    static let gold = [0.93, 0.68, 0.18]
    static let orange = [0.89, 0.42, 0.13]
    static let crimson = [0.72, 0.16, 0.14]

    /// Linear blend of two colour triples.
    static func mix3(_ a: [Double], _ b: [Double], _ t: Double) -> [Double] {
        [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]
    }

    /// A colour from a four-season table at season s (0…4, wrapping), eased between seasons.
    static func seasonal(_ table: [[Double]], _ s: Double) -> [Double] {
        // Int(_:) below traps on NaN or infinity.
        guard s.isFinite else { return table[0] }
        let w = s - 4 * (s / 4).rounded(.down)
        let i = Int(w.rounded(.down)) % 4
        let f = w - w.rounded(.down)
        let e = f * f * (3 - 2 * f)
        return mix3(table[i], table[(i + 1) % 4], e)
    }

    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(1, max(0, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    /// RN's `css`: each channel rounded to a byte.
    static func color(_ c: [Double], _ a: Double = 1) -> Color {
        Color.rgba((c[0] * 255).rounded(), (c[1] * 255).rounded(), (c[2] * 255).rounded(), a)
    }

    /// The autumn colour of a site or a falling leaf.
    static func autumn(_ hue: Double) -> [Double] {
        if hue < 0.34 { return mix3(gold, orange, hue / 0.34 * 0.5) }
        if hue < 0.7 { return mix3(orange, gold, (hue - 0.34) * 0.6) }
        return mix3(crimson, orange, (hue - 0.7) * 1.2)
    }

    func draw(_ context: GraphicsContext) {
        let sea = engine.season
        drawBackdrop(context, sea)
        drawBranches(context, sea)
        drawFoliage(context, sea)
        drawParticles(context)
        drawSeed(context)
    }

    /// Sky, sun, far hills, the mound and the tree's shade on it.
    private func drawBackdrop(_ context: GraphicsContext, _ sea: Double) {
        let width = size.width
        let height = size.height
        let ground = engine.ground
        let full = Path(CGRect(origin: .zero, size: size))
        let c1 = Self.seasonal(Self.skyTop, sea)
        let c2 = Self.seasonal(Self.skyLow, sea)
        context.fill(full, with: .linearGradient(
            Gradient(colors: [Self.color(c1), Self.color(c2)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: ground.base)
        ))
        // A low, soft sun behind the crown.
        context.fill(full, with: .radialGradient(
            Gradient(colors: [Self.color(c2, 0.85), Self.color(c2, 0)]),
            center: CGPoint(x: width * 0.72, y: height * 0.3), startRadius: 0, endRadius: width * 0.55
        ))
        // Two far hills, paler with distance.
        let hill = Self.seasonal(Self.hills, sea)
        for layer in 0..<2 {
            let top = ground.base - (layer == 0 ? 120 : 70)
            var path = Path()
            path.move(to: CGPoint(x: 0, y: height))
            for k in 0...24 {
                let x = Double(k) / 24 * width
                let y = top - (layer == 0 ? 38 : 26) * sin(Double(k) / 24 * Double.pi * (layer == 0 ? 1.3 : 2.1) + (layer == 0 ? 0.6 : 2.4))
                path.addLine(to: CGPoint(x: x, y: y))
            }
            path.addLine(to: CGPoint(x: width, y: height))
            path.closeSubpath()
            context.fill(path, with: .color(Self.color(hill, layer == 0 ? 0.42 : 0.7)))
        }
        // The mound, with a lit crest.
        let g1 = Self.seasonal(Self.groundColors, sea)
        var mound = Path()
        mound.move(to: CGPoint(x: 0, y: height))
        for k in 0...40 {
            let x = Double(k) / 40 * width
            mound.addLine(to: CGPoint(x: x, y: ground.y(x)))
        }
        mound.addLine(to: CGPoint(x: width, y: height))
        mound.closeSubpath()
        context.fill(mound, with: .linearGradient(
            Gradient(colors: [Self.color(g1), Self.color([g1[0] * 0.72, g1[1] * 0.72, g1[2] * 0.74])]),
            startPoint: CGPoint(x: 0, y: ground.base - ground.bump), endPoint: CGPoint(x: 0, y: height)
        ))
        // The crown's shade on the ground, as wide as the crown and as dark as its foliage.
        let g = min(1, engine.growth / Double(S.maxDepth + 1))
        if g > 0.05 {
            let r = width * 0.36 * g
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 10))
                layer.fill(
                    Path(ellipseIn: CGRect(x: engine.rootX - r + 14, y: engine.rootY - 9, width: r * 2, height: 22)),
                    with: .color(Color.rgba(30, 24, 20, 0.22 * g))
                )
            }
        }
    }

    /// The branches: tapered quads lit on one side (a gradient across each), with snow along their tops in winter.
    private func drawBranches(_ context: GraphicsContext, _ sea: Double) {
        guard let tree = engine.tree, engine.growth > 0.001 else { return }
        let bark = Self.seasonal(Self.bark, sea)
        let w = sea - 4 * (sea / 4).rounded(.down)
        let snow = Self.smooth(2.75, 3.2, w) * (1 - Self.smooth(3.6, 4, w))
        for i in 0..<tree.count {
            let bx = tree.bx[i]
            let by = tree.by[i]
            var dx = tree.tx[i] - bx
            var dy = tree.ty[i] - by
            let len = (dx * dx + dy * dy).squareRoot()
            if len < 0.2 { continue }
            dx /= len
            dy /= len
            // Normal to the left of the branch.
            let nx = dy
            let ny = -dx
            let h0 = tree.w0[i] / 2
            let h1 = tree.w1[i] / 2
            // Reach back into the parent a little so joints close up.
            let back = tree.depth[i] == 0 ? 0 : h0 * 0.9
            var quad = Path()
            quad.move(to: CGPoint(x: bx - dx * back + nx * h0, y: by - dy * back + ny * h0))
            quad.addLine(to: CGPoint(x: tree.tx[i] + nx * h1, y: tree.ty[i] + ny * h1))
            quad.addLine(to: CGPoint(x: tree.tx[i] - nx * h1, y: tree.ty[i] - ny * h1))
            quad.addLine(to: CGPoint(x: bx - dx * back - nx * h0, y: by - dy * back - ny * h0))
            quad.closeSubpath()
            // Light from the upper left: the side facing it is lighter. Snow lies on the side facing up.
            var colors: [Color] = []
            for side in 0..<2 {
                let sx = side == 0 ? nx : -nx
                let sy = side == 0 ? ny : -ny
                let lit = 0.78 + 0.42 * max(0, -0.62 * sx - 0.78 * sy)
                let up = max(0, -sy) * snow * (tree.depth[i] < S.maxDepth ? 1 : 0.5)
                colors.append(Color(
                    .sRGB,
                    red: min(1, bark[0] * lit * (1 - up) + 0.97 * up),
                    green: min(1, bark[1] * lit * (1 - up) + 0.98 * up),
                    blue: min(1, bark[2] * lit * (1 - up) + 1 * up)
                ))
            }
            // Across the branch at its middle width: the mesh's colours run edge to edge.
            let mx = (bx + tree.tx[i]) / 2
            let my = (by + tree.ty[i]) / 2
            let hm = (h0 + h1) / 2
            context.fill(quad, with: .linearGradient(
                Gradient(colors: colors),
                startPoint: CGPoint(x: mx + nx * hm, y: my + ny * hm),
                endPoint: CGPoint(x: mx - nx * hm, y: my - ny * hm)
            ))
        }
    }

    /// Draws a sprite with the RSXform (scos, ssin, tx, ty) of the RN atlas.
    private func sprite(_ context: GraphicsContext, _ image: GraphicsContext.ResolvedImage, _ cs: Double, _ sn: Double, _ tx: Double, _ ty: Double, opacity: Double) {
        var c = context
        c.opacity = opacity
        c.concatenate(CGAffineTransform(a: cs, b: sn, c: -sn, d: cs, tx: tx, ty: ty))
        c.draw(image, in: CGRect(x: 0, y: 0, width: FractalTreeBloomSprites.cell, height: FractalTreeBloomSprites.cell))
    }

    /// A tinted sprite: its mask filled with the tint, then its shade.
    private func tinted(
        _ context: GraphicsContext, _ mask: GraphicsContext.ResolvedImage, _ shade: GraphicsContext.ResolvedImage,
        _ tint: [Double], _ cs: Double, _ sn: Double, _ tx: Double, _ ty: Double, opacity: Double
    ) {
        var c = context
        c.opacity = opacity
        c.concatenate(CGAffineTransform(a: cs, b: sn, c: -sn, d: cs, tx: tx, ty: ty))
        var m = mask
        m.shading = .color(Color(.sRGB, red: tint[0], green: tint[1], blue: tint[2]))
        let rect = CGRect(x: 0, y: 0, width: FractalTreeBloomSprites.cell, height: FractalTreeBloomSprites.cell)
        c.draw(m, in: rect)
        c.draw(shade, in: rect)
    }

    /// Blossoms and leaves at their sites, each reading the season a little late.
    private func drawFoliage(_ context: GraphicsContext, _ sea: Double) {
        guard let tree = engine.tree, engine.growth >= Double(S.maxDepth - 1) else { return }
        let blossomA = context.resolve(sprites.blossomA)
        let blossomB = context.resolve(sprites.blossomB)
        let leafMask = context.resolve(sprites.leafMask)
        let leafShade = context.resolve(sprites.leafShade)
        let bl = engine.bloom
        // Drawing back into the seed, the foliage goes first.
        let keep = min(1, (engine.growth - Double(S.maxDepth - 1)) / 1.5)
        for i in 0..<tree.sites {
            let b = tree.siteBranch[i]
            if tree.shown[b] < 0.6 { continue }
            let lag = tree.siteLag[i]
            let local = FractalTreeBloomTree.localSeason(sea, lag)
            let x = tree.bx[b] + (tree.tx[b] - tree.bx[b]) * tree.siteAt[i]
            let y = tree.by[b] + (tree.ty[b] - tree.by[b]) * tree.siteAt[i]
            let sway = tree.abs[b]
            // Blossoms open after the tree is grown, each a little after the last, and give way to leaves.
            let open = FractalTreeBloomEase.outBack((bl - lag * 1.6) / 0.45) * keep
            let blossom = open * (1 - Self.smooth(0.45, 0.95, local) + Self.smooth(3.4, 3.95, local))
            if blossom > 0.02 {
                let k = tree.siteSize[i] * 0.33 * blossom
                let a = tree.siteSpin[i] + sway * 0.5
                let cs = cos(a) * k
                let sn = sin(a) * k
                sprite(context, tree.siteKind[i] == 0 ? blossomA : blossomB, cs, sn, x - (cs * 32 - sn * 32), y - (sn * 32 + cs * 32), opacity: min(1, blossom * 1.5))
            }
            // Leaves: in with summer, coloured in autumn, gone at their moment.
            let fall = tree.siteFall[i]
            let wither = engine.reduced || !tree.leafFalls(i) ? 0.22 : 0.03
            let on = Self.smooth(0.4, 0.95, local) * (1 - Self.smooth(fall - wither, fall, local)) * open
            if on > 0.02 && local < 3.5 {
                let hue = tree.siteHue[i]
                let green: Double = hue < 0.5 ? 0 : 1
                let leaf = Self.mix3(Self.mix3(Self.greenA, Self.greenB, green * 0.8 + hue * 0.2), Self.autumn(hue), Self.smooth(1.4, 1.95, local))
                let k = tree.siteSize[i] * 0.28 * on
                let side: Double = tree.siteSide[i] < 0 ? -1 : 1
                let a = sway + side * 0.95 + tree.siteSpin[i] * 0.15
                let cs = cos(a) * k
                let sn = sin(a) * k
                // The leaf hangs from its stalk end (the sprite's bottom centre) at the site.
                tinted(context, leafMask, leafShade, leaf, cs, sn, x - (cs * 32 - sn * 60), y - (sn * 32 + cs * 60), opacity: min(1, on * 1.4))
            }
        }
    }

    /// Falling petals, leaves and snow.
    private func drawParticles(_ context: GraphicsContext) {
        let ps = engine.particles
        if ps.count == 0 { return }
        let leafMask = context.resolve(sprites.leafMask)
        let leafShade = context.resolve(sprites.leafShade)
        let petalMask = context.resolve(sprites.petalMask)
        let petalShade = context.resolve(sprites.petalShade)
        let snow = context.resolve(sprites.snow)
        // Drawing back into the seed, whatever is still falling fades away with the tree.
        let away = engine.growing < 0 ? min(1, engine.growth / Double(S.maxDepth + 1)) : 1
        // Each kind belongs to its season: petals fade out with the summer, leaves by spring, snow by spring.
        let sea = engine.season - 4 * (engine.season / 4).rounded(.down)
        let petals = 1 - Self.smooth(1.5, 1.95, sea)
        let leaves = Self.smooth(1.2, 1.6, sea) * (1 - Self.smooth(3.5, 3.95, sea))
        let flakes = Self.smooth(2.6, 2.9, sea) * (1 - Self.smooth(3.7, 3.98, sea))
        for i in 0..<ps.count {
            let kind = ps.kind[i]
            let isSnow = kind == S.snow
            let isLeaf = kind == S.leaf
            let restFade = away * (isSnow ? flakes : isLeaf ? leaves : petals)
                * (ps.rest[i] < 0 ? 1 : max(0, 1 - ps.rest[i] / (isSnow ? 1.2 : 2)))
            if restFade <= 0 { continue }
            let k = ps.size[i] * (isSnow ? 0.17 : isLeaf ? 0.26 : 0.22)
            // A tumbling flake turns in depth too: its width breathes with the turn.
            let tumble = isSnow ? 1 : 0.55 + 0.45 * abs(cos(ps.rot[i] * 1.7))
            let a = ps.rot[i]
            let cs = cos(a) * k
            let sn = sin(a) * k
            let tx = ps.x[i] - (cs * tumble * 32 - sn * 32)
            let ty = ps.y[i] - (sn * tumble * 32 + cs * 32)
            if isSnow {
                sprite(context, snow, cs * tumble, sn * tumble, tx, ty, opacity: restFade)
            } else if isLeaf {
                tinted(context, leafMask, leafShade, Self.autumn(ps.hue[i]), cs * tumble, sn * tumble, tx, ty, opacity: restFade)
            } else {
                tinted(context, petalMask, petalShade, [0.98, 0.8 + 0.12 * ps.hue[i], 0.86], cs * tumble, sn * tumble, tx, ty, opacity: restFade)
            }
        }
    }

    /// The seed on the mound, until the sprout has taken over.
    private func drawSeed(_ context: GraphicsContext) {
        let g = engine.growth
        if g > 0.6 { return }
        let alpha = 1 - g / 0.6
        var c = context
        c.translateBy(x: engine.rootX, y: engine.rootY - 5)
        c.rotate(by: .degrees(-18))
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: 2.5))
            layer.fill(Path(ellipseIn: CGRect(x: -9, y: 1, width: 20, height: 8)), with: .color(Color.rgba(40, 28, 20, 0.3 * alpha)))
        }
        var body = c
        body.opacity = alpha
        body.fill(
            Path(ellipseIn: CGRect(x: -10, y: -6, width: 20, height: 12)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xb7835a), location: 0),
                    .init(color: Color(hex: 0x6b4329), location: 0.6),
                    .init(color: Color(hex: 0x3d2414), location: 1),
                ]),
                center: CGPoint(x: -3, y: -3), startRadius: 0, endRadius: 12
            )
        )
        c.fill(Path(ellipseIn: CGRect(x: -6, y: -4.5, width: 7, height: 3)), with: .color(Color.rgba(255, 240, 220, 0.55 * alpha)))
    }
}
