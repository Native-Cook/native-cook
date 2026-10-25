import SwiftUI
import UIKit

/// A café loyalty screen: an espresso coupon whose caramel stub tears off along its perforation
/// hole by hole, swings from the finger and flies into a leather wallet while a REDEEMED stamp
/// slams onto the coupon; then a fresh coupon slides in.
struct PerforatedCouponTear: View {
    /// Tears the stub, files it in the wallet, stamps the coupon and brings a fresh one, every 9 s.
    var autoPlay = false

    static let loop = PerforatedCouponTearMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = PerforatedCouponTearModel()
    @State private var faces: PerforatedCouponTearFaces?
    @State private var awake = false
    @GestureState private var dragging = false

    typealias M = PerforatedCouponTearMotion
    private typealias P = PerforatedCouponTearPalette

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                let layout = PerforatedCouponTearLayout(size: size, top: insets.top, bottom: insets.bottom)
                stage(layout)
                    .frame(width: size.width, height: size.height)
                    .offset(x: -insets.leading, y: -insets.top)
                    .task(id: layout.geom.px) {
                        faces = PerforatedCouponTearFaces(px: layout.geom.px, cw: layout.geom.cw)
                    }
            }
        }
        .background(P.paper[1].ignoresSafeArea())
    }

    private func stage(_ l: PerforatedCouponTearLayout) -> some View {
        let looping = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            PerforatedCouponTearBack(layout: l, faces: faces).equatable()

            TimelineView(.animation(paused: !(looping || awake))) { timeline in
                let pose = model.step(to: timeline.date, geom: l.geom, looping: looping, ready: faces != nil)
                let faces = faces
                ZStack(alignment: .topLeading) {
                    // A filed stub lies behind the pocket's front.
                    Canvas { ctx, _ in
                        guard let faces, pose.filed else { return }
                        var c = ctx
                        c.clip(to: Path(CGRect(x: 0, y: 0, width: l.width, height: l.pocketBottom - 2)))
                        c.translateBy(x: l.cardX, y: l.cardY)
                        PerforatedCouponTearDraw.stubTransform(&c, pose: pose, geom: l.geom)
                        PerforatedCouponTearDraw.stubFace(c, path: faces.tornStub, fibres: faces.tornFibres, faces: faces, filed: true)
                    }
                    PerforatedCouponTearFront(layout: l).equatable()
                    Canvas { ctx, _ in
                        guard let faces else { return }
                        PerforatedCouponTearDraw.coupons(ctx, layout: l, pose: pose, faces: faces)
                    }
                }
                .frame(width: l.width, height: l.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            overlays(l)
                .allowsHitTesting(false)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 2)
                .updating($dragging) { _, state, _ in state = true }
                .onChanged { value in
                    if !model.dragActive {
                        if !looping { awake = true }
                        model.begin(at: value.startLocation, layout: l)
                    }
                    model.update(value, layout: l)
                }
                .onEnded { value in
                    model.end(value, layout: l)
                    sleepWhenResting()
                }
        )
        .onChange(of: dragging) { _, down in
            // A drag the system cancels never reaches onEnded.
            if !down && model.dragActive {
                DispatchQueue.main.async {
                    model.cancel()
                    sleepWhenResting()
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Free coffee coupon. Drag the stub down to tear it off and file it in your wallet.")
    }

    /// Outside autoPlay the timeline pauses again once the coupon is back at rest.
    private func sleepWhenResting() {
        Task { @MainActor in
            while awake {
                try? await Task.sleep(for: .milliseconds(400))
                if model.resting { awake = false }
            }
        }
    }

    private func overlays(_ l: PerforatedCouponTearLayout) -> some View {
        ZStack(alignment: .topLeading) {
            HStack {
                HStack(spacing: 8) {
                    Text("BC")
                        .font(.system(size: 12, weight: .heavy))
                        .tracking(0.5)
                        .foregroundStyle(P.cream)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(P.roast).shadow(color: Color.rgba(46, 26, 14, 0.3), radius: 2, y: 1))
                    Text("Bean Counter")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(P.roast)
                }
                Spacer(minLength: 8)
                Text("Larch St.")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.rgba(58, 36, 24, 0.75))
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .background(Capsule().fill(Color.rgba(58, 36, 24, 0.08)))
            }
            .frame(width: l.width - 40, height: 40)
            .offset(x: 20, y: l.top + 12)

            VStack(alignment: .leading, spacing: 4) {
                Text("Your free coffee")
                    .font(.system(size: 32, weight: .heavy))
                    .tracking(-0.6)
                    .foregroundStyle(P.roast)
                Text("10 of 10 stamps · tear the stub at the counter")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.rgba(58, 36, 24, 0.62))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(width: l.width - 40, alignment: .leading)
            .offset(x: 20, y: l.top + 66)

            VStack(alignment: .leading, spacing: 2) {
                Text("Wallet")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(P.roast)
                Text("\(Text("2").font(.system(size: 13, weight: .bold).monospacedDigit()).foregroundStyle(Color.rgba(58, 36, 24, 0.8))) rewards redeemed this month")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.rgba(58, 36, 24, 0.6))
            }
            .frame(width: l.width - 40, alignment: .leading)
            .offset(x: 20, y: l.cardY + M.cardH + 30)

            Text("Show code at counter")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(P.cream)
                .frame(width: l.width - 40, height: 52)
                .background(
                    Capsule().fill(P.roast)
                        .shadow(color: Color.rgba(46, 26, 14, 0.22), radius: 24, y: 10)
                        .shadow(color: Color.rgba(46, 26, 14, 0.35), radius: 2, y: 1)
                )
                .offset(x: 20, y: l.height - l.bottom - 24 - 52)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
    }
}

enum PerforatedCouponTearPalette {
    static let paper = [Color(hex: 0xF6EFE3), Color(hex: 0xEFE4D2), Color(hex: 0xE6D5BE)]
    static let body = [Color(hex: 0x4E3122), Color(hex: 0x3E271B), Color(hex: 0x2F1D14)]
    static let stub = [Color(hex: 0xD9A066), Color(hex: 0xC98B4E), Color(hex: 0xB37638)]
    static let cream = Color(hex: 0xF4E8D4)
    static let roast = Color(hex: 0x3A2418)
    static let leather = [Color(hex: 0x2F5A49), Color(hex: 0x24473A), Color(hex: 0x1A352B)]
    static let ink = Color(.sRGB, red: 0.93, green: 0.38, blue: 0.28, opacity: 0.95)
}

/// Layout (pt). The coupon is the hero; the wallet below it fills the rest of the screen.
struct PerforatedCouponTearLayout: Equatable {
    let width: Double
    let height: Double
    let top: Double
    let bottom: Double
    let cardX = 16.0
    let cardY: Double
    let pocketTop: Double
    let pocketBottom: Double
    let frontTop: Double
    let pocketCx: Double
    let geom: PerforatedCouponTearGeom

    init(size: CGSize, top: Double, bottom: Double) {
        let M = PerforatedCouponTearMotion.self
        width = size.width
        height = size.height
        self.top = top
        self.bottom = bottom
        cardY = top + 186
        let cw = max(200, size.width - 32)
        let px = cw - M.stubW
        pocketTop = cardY + M.cardH + 78
        pocketBottom = max(pocketTop + 96, size.height - bottom - 96)
        frontTop = pocketTop + 30
        pocketCx = size.width / 2
        geom = PerforatedCouponTearGeom(
            cw: cw, px: px, gx: px + 64, gy: 30,
            pocketX: pocketCx + 43 - 16, pocketY: frontTop - 22 - cardY,
            travel: size.width + 24, front: frontTop - cardY
        )
    }
}

/// Fonts of the coupon's copy (system faces for the RN version's Helvetica Neue and Menlo).
enum PerforatedCouponTearFont {
    static let free = UIFont.systemFont(ofSize: 60, weight: .heavy)
    static let small = UIFont.systemFont(ofSize: 14, weight: .medium)
    static let label = UIFont.systemFont(ofSize: 10, weight: .bold)
    static let mono = UIFont.monospacedSystemFont(ofSize: 12, weight: .regular)
    static let stamp = UIFont.systemFont(ofSize: 21, weight: .heavy)
    static let tiny = UIFont.systemFont(ofSize: 9, weight: .bold)

    /// Width of `text` with extra tracking between glyphs (none after the last).
    static func width(_ text: String, _ font: UIFont, _ tracking: Double) -> Double {
        let w = (text as NSString).size(withAttributes: [.font: font, .kern: tracking]).width
        return w - tracking
    }

    /// Draws `text` with its baseline at `y`; `align` 0 starts at x, 0.5 centres on it, 1 ends at it.
    static func draw(_ ctx: GraphicsContext, _ text: String, _ font: UIFont, x: Double, y: Double,
                     tracking: Double, color: Color, align: Double = 0) {
        let t = Text(text).font(Font(font)).tracking(tracking).foregroundStyle(color)
        // SwiftUI's tracking also follows the last glyph; shift so the ink is placed like RN's.
        let shift = tracking * align
        ctx.draw(t, at: CGPoint(x: x + shift, y: y + font.lineHeight - font.ascender), anchor: UnitPoint(x: align, y: 1))
    }
}

/// Static art of the coupon's two halves, rendered once per layout.
struct PerforatedCouponTearFaces {
    let px: Double
    let cw: Double
    let body: Image
    let stub: Image
    let holes: Path
    let tornStub: Path
    let tornFibres: Path
    let freshBody: Path
    let freshStub: Path

    @MainActor
    init(px: Double, cw: Double) {
        let A = PerforatedCouponTearArt.self
        let M = PerforatedCouponTearMotion.self
        self.px = px
        self.cw = cw
        holes = A.holes(px: px)
        let shape = A.stub(px: px, cw: cw, front: M.cardH)
        tornStub = shape.subtracting(holes)
        tornFibres = A.fibres(px: px, front: M.cardH, dir: -1)
        freshBody = A.body(px: px, front: 0)
        freshStub = A.stub(px: px, cw: cw, front: 0)
        body = Self.render(CGSize(width: px, height: M.cardH)) { ctx in Self.drawBodyFace(ctx, px: px) }
        stub = Self.render(CGSize(width: cw - px, height: M.cardH)) { ctx in
            var c = ctx
            c.translateBy(x: -px, y: 0)
            Self.drawStubFace(c, px: px)
        }
    }

    @MainActor
    private static func render(_ size: CGSize, _ draw: @escaping (GraphicsContext) -> Void) -> Image {
        let r = ImageRenderer(content: Canvas { ctx, _ in draw(ctx) }.frame(width: size.width, height: size.height))
        r.scale = 3
        if let cg = r.cgImage { return Image(decorative: cg, scale: 3) }
        return Image(systemName: "circle")
    }

    /// The body's copy: bean pattern, REWARD / EXP, FREE, the line, the dashed rule and the code.
    private static func drawBodyFace(_ ctx: GraphicsContext, px: Double) {
        let F = PerforatedCouponTearFont.self
        let A = PerforatedCouponTearArt.self
        ctx.stroke(A.beans(w: px, h: PerforatedCouponTearMotion.cardH), with: .color(Color.rgba(244, 232, 212, 0.06)), lineWidth: 1)
        let soft = Color.rgba(244, 232, 212, 0.6)
        F.draw(ctx, "REWARD", F.label, x: 22, y: 34, tracking: 1.8, color: soft)
        F.draw(ctx, "EXP 11/30", F.label, x: px - 20, y: 34, tracking: 1.4, color: soft, align: 1)
        F.draw(ctx, "FREE", F.free, x: 19, y: 102, tracking: -1, color: PerforatedCouponTearPalette.cream)
        F.draw(ctx, "Any drink, any size, any day", F.small, x: 22, y: 126, tracking: 0, color: Color.rgba(244, 232, 212, 0.78))
        var rule = Path()
        rule.move(to: CGPoint(x: 22, y: 150))
        rule.addLine(to: CGPoint(x: px - 20, y: 150))
        ctx.stroke(rule, with: .color(Color.rgba(244, 232, 212, 0.22)), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
        F.draw(ctx, "CODE", F.tiny, x: 22, y: 176, tracking: 1.6, color: Color.rgba(244, 232, 212, 0.55))
        F.draw(ctx, "BC-4471-0925", F.mono, x: 22 + F.width("CODE", F.tiny, 1.6) + 8, y: 177, tracking: 0.6, color: PerforatedCouponTearPalette.cream)
    }

    /// The stub's copy: a barcode, the sideways "tear to redeem" and its number.
    private static func drawStubFace(_ ctx: GraphicsContext, px: Double) {
        let F = PerforatedCouponTearFont.self
        ctx.fill(PerforatedCouponTearArt.barcode(x: px + 46, y: 34, w: 48, h: 116), with: .color(Color.rgba(58, 36, 24, 0.82)))
        var c = ctx
        c.translateBy(x: px + 26, y: PerforatedCouponTearMotion.cardH / 2)
        c.rotate(by: .radians(-.pi / 2))
        F.draw(c, "TEAR TO REDEEM", F.label, x: 0, y: 0, tracking: 2.2, color: Color.rgba(58, 36, 24, 0.78), align: 0.5)
        F.draw(ctx, "No. 0925", F.mono, x: px + 46, y: 176, tracking: 0.4, color: Color.rgba(58, 36, 24, 0.7))
    }
}

/// Everything that never moves behind the pocket's front: paper, grain, stamps, the wallet's back
/// panel and the two stubs filed before.
struct PerforatedCouponTearBack: View, Equatable {
    let layout: PerforatedCouponTearLayout
    let faces: PerforatedCouponTearFaces?

    static func == (a: Self, b: Self) -> Bool {
        a.layout == b.layout && (a.faces == nil) == (b.faces == nil) && a.faces?.px == b.faces?.px
    }

    var body: some View {
        Canvas { ctx, size in
            let l = layout
            let P = PerforatedCouponTearPalette.self
            let D = PerforatedCouponTearDraw.self
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
                Gradient(colors: P.paper), startPoint: .zero, endPoint: CGPoint(x: size.width * 0.4, y: size.height)))
            D.grain(ctx, Path(CGRect(origin: .zero, size: size)), amount: 0.5)
            D.stampRow(ctx, x: l.cardX, width: l.geom.cw, y: l.top + 150)

            var c = ctx
            c.translateBy(x: l.cardX, y: l.cardY)
            // The wallet's back panel.
            let left = 24 - l.cardX
            let top = l.pocketTop - l.cardY
            let bottom = l.pocketBottom - l.cardY
            let back = Path(roundedRect: CGRect(x: left, y: top, width: l.width - 48, height: bottom - top), cornerRadius: 18)
            D.shadow(c, back, color: Color.rgba(40, 26, 14, 0.22), blur: 28, dx: 0, dy: 14)
            D.shadow(c, back, color: Color.rgba(40, 26, 14, 0.3), blur: 3, dx: 0, dy: 2)
            c.fill(back, with: .linearGradient(
                Gradient(colors: [Color(hex: 0x1F3F33), Color(hex: 0x173126), Color(hex: 0x10241C)]),
                startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: bottom)))

            guard let faces else { return }
            var clip = c
            clip.clip(to: Path(CGRect(x: -l.cardX, y: -l.cardY, width: l.width, height: l.pocketBottom - 2)))
            for (dx, tilt) in [(-96.0, -0.1), (-30.0, 0.05)] {
                var s = clip
                s.translateBy(x: l.pocketCx + dx + 43 - l.cardX, y: l.frontTop - 22 - l.cardY + 6)
                s.rotate(by: .radians(tilt))
                s.scaleBy(x: PerforatedCouponTearMotion.pocketScale, y: PerforatedCouponTearMotion.pocketScale)
                s.translateBy(x: -l.geom.gx, y: -l.geom.gy)
                D.stubFace(s, path: faces.tornStub, fibres: faces.tornFibres, faces: faces, filed: true)
            }
        }
        .frame(width: layout.width, height: layout.height)
    }
}

/// The wallet's stitched front pocket with its curved mouth, over whatever is filed in it.
struct PerforatedCouponTearFront: View, Equatable {
    let layout: PerforatedCouponTearLayout

    var body: some View {
        Canvas { ctx, _ in
            let l = layout
            let D = PerforatedCouponTearDraw.self
            var c = ctx
            c.translateBy(x: l.cardX, y: l.cardY)
            let left = 24 - l.cardX
            let width = l.width - 48
            let r = left + width
            let front = l.frontTop - l.cardY
            let bottom = l.pocketBottom - l.cardY
            var p = Path()
            p.move(to: CGPoint(x: left, y: front + 8))
            p.addQuadCurve(to: CGPoint(x: r, y: front + 8), control: CGPoint(x: left + width / 2, y: front + 26))
            p.addLine(to: CGPoint(x: r, y: bottom - 16))
            p.addQuadCurve(to: CGPoint(x: r - 16, y: bottom), control: CGPoint(x: r, y: bottom))
            p.addLine(to: CGPoint(x: left + 16, y: bottom))
            p.addQuadCurve(to: CGPoint(x: left, y: bottom - 16), control: CGPoint(x: left, y: bottom))
            p.closeSubpath()
            D.shadow(c, p, color: Color.rgba(10, 20, 16, 0.45), blur: 6, dx: 0, dy: -3)
            c.fill(p, with: .linearGradient(
                Gradient(colors: PerforatedCouponTearPalette.leather),
                startPoint: CGPoint(x: 0, y: front), endPoint: CGPoint(x: left + width * 0.3, y: bottom)))
            D.grain(c, p, amount: 0.32)
            c.stroke(p, with: .color(Color.rgba(214, 190, 140, 0.55)), style: StrokeStyle(lineWidth: 1.2, dash: [5, 4]))
            PerforatedCouponTearFont.draw(c, "REWARDS WALLET", PerforatedCouponTearFont.tiny, x: left + width / 2, y: bottom - 22,
                                          tracking: 2.4, color: Color.rgba(214, 190, 140, 0.75), align: 0.5)
        }
        .frame(width: layout.width, height: layout.height)
        .allowsHitTesting(false)
    }
}

/// Drawing shared by the static layers and the moving coupon.
enum PerforatedCouponTearDraw {
    typealias M = PerforatedCouponTearMotion
    private typealias P = PerforatedCouponTearPalette

    /// A soft shadow of `path`: the shape filled, offset and blurred.
    static func shadow(_ ctx: GraphicsContext, _ path: Path, color: Color, blur: Double, dx: Double, dy: Double) {
        var c = ctx
        c.addFilter(.blur(radius: blur))
        c.translateBy(x: dx, y: dy)
        c.fill(path, with: .color(color))
    }

    /// Paper grain mixed in with soft light, inside `path`.
    static func grain(_ ctx: GraphicsContext, _ path: Path, amount: Double) {
        var c = ctx
        c.clip(to: path)
        c.blendMode = .softLight
        c.opacity = amount
        c.fill(path, with: .tiledImage(PerforatedCouponTearArt.grain, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    /// Ten filled stamps across the top of the card, each a coffee-brown disc with a cream cup.
    static func stampRow(_ ctx: GraphicsContext, x: Double, width: Double, y: Double) {
        let d = min(28, (width - 9 * 7) / 10)
        let gap = (width - 10 * d) / 9
        for i in 0..<10 {
            let sx = x + d / 2 + Double(i) * (d + gap)
            let r = d / 2
            let disc = Path(ellipseIn: CGRect(x: sx - r, y: y - r, width: d, height: d))
            shadow(ctx, disc, color: Color.rgba(60, 34, 20, 0.3), blur: 1.5, dx: 0, dy: 1)
            ctx.fill(disc, with: .linearGradient(
                Gradient(colors: [Color(hex: 0x6B4430), Color(hex: 0x5A3826), Color(hex: 0x47291B)]),
                startPoint: CGPoint(x: sx, y: y - r), endPoint: CGPoint(x: sx, y: y + r)))
            var c = ctx
            c.translateBy(x: sx, y: y)
            let k = r / 14 + 0.05
            c.scaleBy(x: k, y: k)
            c.fill(PerforatedCouponTearArt.cup, with: .color(P.cream))
            c.stroke(PerforatedCouponTearArt.cupHandle, with: .color(P.cream), lineWidth: 1.3)
        }
    }

    /// Moves the context into the stub's frame: its grab point at (sx, sy), turned and scaled.
    static func stubTransform(_ c: inout GraphicsContext, pose: PerforatedCouponTearPose, geom: PerforatedCouponTearGeom) {
        c.translateBy(x: pose.sx, y: pose.sy)
        c.rotate(by: .radians(pose.phi))
        c.scaleBy(x: pose.scale, y: pose.scale)
        c.translateBy(x: -geom.gx, y: -geom.gy)
    }

    /// The stub's face: caramel paper, its torn fibres, a barcode and the "tear" label.
    static func stubFace(_ ctx: GraphicsContext, path: Path, fibres: Path, faces: PerforatedCouponTearFaces, filed: Bool) {
        if filed { shadow(ctx, path, color: Color.rgba(8, 18, 14, 0.5), blur: 6, dx: 0, dy: 3) }
        ctx.fill(path, with: .linearGradient(
            Gradient(colors: P.stub), startPoint: CGPoint(x: faces.px, y: 0), endPoint: CGPoint(x: faces.cw, y: M.cardH)))
        ctx.stroke(path, with: .color(Color.rgba(255, 240, 215, 0.35)), lineWidth: 1)
        ctx.stroke(fibres, with: .color(Color.rgba(255, 246, 230, 0.85)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
        ctx.draw(faces.stub, in: CGRect(x: faces.px, y: 0, width: faces.cw - faces.px, height: M.cardH))
        if !filed { ctx.fill(faces.holes, with: .color(P.paper[1])) }
    }

    /// The coupon's body: espresso card, faint bean pattern, grain, its copy and the perforation holes.
    static func body(_ ctx: GraphicsContext, path: Path, fibres: Path, faces: PerforatedCouponTearFaces) {
        let px = faces.px
        shadow(ctx, path, color: Color.rgba(46, 26, 14, 0.26), blur: 26, dx: 0, dy: 16)
        shadow(ctx, path, color: Color.rgba(46, 26, 14, 0.35), blur: 3, dx: 0, dy: 1.5)
        ctx.fill(path, with: .linearGradient(
            Gradient(colors: P.body), startPoint: .zero, endPoint: CGPoint(x: px, y: M.cardH)))
        var c = ctx
        c.clip(to: path)
        grain(c, path, amount: 0.55)
        c.stroke(Path(roundedRect: CGRect(x: 0.5, y: 0.5, width: px + M.corner, height: M.cardH - 1), cornerRadius: M.corner),
                 with: .color(Color.rgba(255, 236, 205, 0.14)), lineWidth: 1)
        c.draw(faces.body, in: CGRect(x: 0, y: 0, width: px, height: M.cardH))
        ctx.stroke(fibres, with: .color(Color.rgba(255, 240, 218, 0.8)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
        ctx.fill(faces.holes, with: .color(P.paper[1]))
    }

    /// The live coupon (and the fresh one sliding in behind it), in screen coordinates.
    static func coupons(_ ctx: GraphicsContext, layout l: PerforatedCouponTearLayout, pose: PerforatedCouponTearPose, faces: PerforatedCouponTearFaces) {
        let A = PerforatedCouponTearArt.self
        let g = l.geom
        var card = ctx
        card.translateBy(x: l.cardX, y: l.cardY)

        // The next coupon, sliding in while the redeemed one is still on its way out.
        if pose.freshOn {
            var f = card
            f.translateBy(x: pose.fresh, y: 0)
            stubFace(f, path: faces.freshStub, fibres: Path(), faces: faces, filed: false)
            body(f, path: faces.freshBody, fibres: Path(), faces: faces)
        }

        var live = card
        live.translateBy(x: pose.slide, y: 0)
        // The live stub sits under the body, so its still-joined part tucks beneath it as it turns.
        if !pose.filed {
            let stub = A.stub(px: g.px, cw: g.cw, front: pose.front)
            var s = live
            stubTransform(&s, pose: pose, geom: g)
            var sh = s
            sh.opacity = 0.18 + 0.16 * pose.lift
            sh.addFilter(.blur(radius: 3 + 9 * pose.lift))
            sh.translateBy(x: 1 + 5 * pose.lift, y: 2 + 12 * pose.lift)
            sh.fill(stub, with: .color(Color(hex: 0x2A160C)))
            stubFace(s, path: stub, fibres: A.fibres(px: g.px, front: pose.front, dir: -1), faces: faces, filed: false)
        }
        body(live, path: A.body(px: g.px, front: pose.front), fibres: A.fibres(px: g.px, front: pose.front, dir: 1), faces: faces)

        if pose.stamp > 0 {
            var st = live
            st.opacity = min(1, max(0.15, (1.36 - pose.stamp) / 0.2))
            st.translateBy(x: g.px * 0.56, y: 104)
            st.rotate(by: .radians(-0.21))
            st.scaleBy(x: pose.stamp, y: pose.stamp)
            st.drawLayer { layer in
                let ink = GraphicsContext.Shading.color(P.ink)
                layer.stroke(Path(roundedRect: CGRect(x: -84, y: -24, width: 168, height: 48), cornerRadius: 8), with: ink, lineWidth: 3)
                layer.stroke(Path(roundedRect: CGRect(x: -78, y: -18, width: 156, height: 36), cornerRadius: 5), with: ink, lineWidth: 1.2)
                PerforatedCouponTearFont.draw(layer, "REDEEMED", PerforatedCouponTearFont.stamp, x: 0, y: 7.5, tracking: 1.2, color: P.ink, align: 0.5)
                // The ink skips where the paper's tooth did not take it.
                layer.blendMode = .destinationIn
                layer.fill(Path(CGRect(x: -90, y: -30, width: 180, height: 60)),
                           with: .tiledImage(A.inkMask, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
            }
        }
    }
}

/// The clock and the finger, stepped once a frame like the RN frame callback.
@MainActor
final class PerforatedCouponTearModel {
    typealias M = PerforatedCouponTearMotion

    /// Seconds after the last touch before the loop starts again.
    private static let idleDelay = 2.0
    /// Finger travel (pt, down and away) that rips the whole perforation.
    private static let tearTravel = 210.0

    private(set) var dragActive = false
    private var looping: Bool?
    private var clock = 0.0
    private var running = false
    private var user = false
    private var idle = 0.0
    private var userRun = false
    private var fly = PerforatedCouponTearFly()
    private var tear = 0.0
    private var tearV = 0.0
    private var springing = false
    private var free = false
    private var x = 0.0
    private var y = 0.0
    private var phi = 0.0
    private var phiV = 0.0
    private var vx = 0.0
    private var grabbing = false
    private var startTear = 0.0
    private var offX = 0.0
    private var offY = 0.0
    private var lastHole = 0
    private var lastDate: Date?
    private var pose = PerforatedCouponTearPose()
    private let select = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    var resting: Bool { !user && !running && !grabbing }

    func step(to date: Date, geom: PerforatedCouponTearGeom, looping: Bool, ready: Bool) -> PerforatedCouponTearPose {
        if self.looping != looping {
            self.looping = looping
            clock = 0
            running = looping
        }
        if date == lastDate { return pose }
        // A slow frame (or the first ones, before the art exists) pauses the clock instead of skipping ahead.
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0
        lastDate = date
        let dt = ready ? min(max(raw, 0), 1.0 / 30) : 0

        if user {
            if free {
                // The free stub hangs from the finger like a pendulum; a sideways flick swings it.
                let target = -0.09 - max(-0.5, min(0.5, vx * 0.0006))
                let a = -70 * (phi - target) - 6 * phiV
                phiV += a * dt
                phi += phiV * dt
                vx *= 0.9
            }
            if springing {
                let k = 170.0
                let c = 2 * 0.78 * k.squareRoot()
                tearV += (-k * tear - c * tearV) * dt
                tear += tearV * dt
                if abs(tear) < 0.001 && abs(tearV) < 0.01 {
                    tear = 0
                    tearV = 0
                    springing = false
                }
            }
            if !grabbing {
                idle += dt
                if idle > Self.idleDelay && abs(tear) < 0.002 {
                    user = false
                    springing = false
                    clock = 0
                    running = looping
                }
            }
        } else if running {
            var t = clock + dt
            if t >= M.loop {
                t -= M.loop
                fly = PerforatedCouponTearFly()
                userRun = false
                if !looping {
                    t = 0
                    running = false
                }
            }
            if userRun && clock < M.stampAt && t >= M.stampAt { light.impactOccurred() }
            clock = t
        }
        pose = user
            ? M.userPose(geom, tear: tear, free: free, x: x, y: y, phi: phi)
            : M.scriptPose(geom, clock, fly)
        return pose
    }

    private func canGrab(_ p: CGPoint, _ l: PerforatedCouponTearLayout) -> Bool {
        let g = l.geom
        if pose.slide != 0 || pose.filed || pose.scale < 1 { return false }
        let lx = p.x - l.cardX
        let ly = p.y - l.cardY
        if pose.tear >= 1 { return hypot(lx - pose.sx, ly - pose.sy) < 120 }
        return lx > g.px - 8 && lx < g.cw + 12 && ly > -12 && ly < M.cardH + 12
    }

    func begin(at p: CGPoint, layout l: PerforatedCouponTearLayout) {
        dragActive = true
        guard canGrab(p, l) else { return }
        grabbing = true
        idle = 0
        user = true
        running = false
        springing = false
        if pose.tear >= 1 {
            free = true
            x = pose.sx
            y = pose.sy
            phi = pose.phi
            phiV = 0
            offX = p.x - l.cardX - pose.sx
            offY = p.y - l.cardY - pose.sy
        } else {
            free = false
            tear = pose.tear
            startTear = pose.tear
            lastHole = Int(floor(pose.tear * Double(M.holeCount)))
        }
    }

    func update(_ v: DragGesture.Value, layout l: PerforatedCouponTearLayout) {
        guard grabbing else { return }
        idle = 0
        if free {
            x = v.location.x - l.cardX - offX
            y = v.location.y - l.cardY - offY
            vx = v.velocity.width
            return
        }
        let raw = startTear + (v.translation.height * 0.85 + v.translation.width * 0.55) / Self.tearTravel
        // Pushing the stub back in only gives a little (rubber band).
        let t = raw < 0 ? -(-raw / (1 + -raw * 8)) : raw
        if t >= 1 {
            let a = M.attachedPose(l.geom, 1, 0)
            free = true
            tear = 1
            x = a.sx
            y = a.sy
            phi = a.phi
            phiV = 0
            offX = v.location.x - l.cardX - a.sx
            offY = v.location.y - l.cardY - a.sy
            light.impactOccurred()
            return
        }
        tear = t
        let hole = Int(floor(max(0, t) * Double(M.holeCount)))
        if abs(hole - lastHole) >= 2 {
            lastHole = hole
            select.selectionChanged()
        }
    }

    func end(_ v: DragGesture.Value?, layout l: PerforatedCouponTearLayout?) {
        dragActive = false
        guard grabbing else { return }
        grabbing = false
        if free {
            // Let go of a free stub: it flies into the wallet and the script finishes the story.
            fly = PerforatedCouponTearFly(x: x, y: y, phi: phi, on: true)
            free = false
            tear = 0
            user = false
            userRun = true
            clock = M.flyStart
            running = true
            return
        }
        let vel = v.map { ($0.velocity.height * 0.85 + $0.velocity.width * 0.55) / Self.tearTravel } ?? 0
        tearV = vel
        springing = true
    }

    func cancel() {
        end(nil, layout: nil)
    }
}
