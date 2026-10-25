import SwiftUI
import UIKit

/// An order-delivered modal with a fortune cookie on a porcelain plate. Hold to squeeze it (or tap)
/// and it shakes, cracks along a jagged line and snaps into two halves that swing apart, spilling
/// crumbs; a rolled slip rises out of the gap, drops into place and unrolls to show the fortune,
/// and the lucky numbers pop in. Then everything folds back.
struct FortuneCookieCrack: View {
    /// Squeezes and cracks the cookie, unrolls the fortune, then folds it all back, every 9 s.
    var autoPlay = false

    static let loop = FortuneCookieCrackMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = FortuneCookieCrackModel()
    @State private var art: FortuneCookieCrackArt?
    @State private var awake = false
    @GestureState private var touching = false

    typealias M = FortuneCookieCrackMotion
    private typealias C = FortuneCookieCrackColors

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(FortuneCookieCrackLayout(size: size, top: insets.top, bottom: insets.bottom))
                    .frame(width: size.width, height: size.height)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(C.root.ignoresSafeArea())
        .task {
            if art == nil { art = await FortuneCookieCrackArt.make() }
        }
    }

    private func stage(_ l: FortuneCookieCrackLayout) -> some View {
        let looping = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            FortuneCookieCrackBackdrop(layout: l, grain: art?.grain).equatable()

            TimelineView(.animation(paused: !(looping || awake))) { timeline in
                let clock = model.step(to: timeline.date, looping: looping, ready: art != nil)
                let pose = M.cookieAt(clock)
                let art = art
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in
                        guard let art else { return }
                        FortuneCookieCrackDraw.scene(ctx, layout: l, pose: pose, art: art)
                    }
                    .frame(width: l.width, height: l.height)

                    head(l, stage: pose.stage)
                    lucky(l, t: clock)
                }
                .frame(width: l.width, height: l.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            Color.clear
                .frame(width: l.width, height: l.height)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .updating($touching) { _, state, _ in state = true }
                        .onChanged { v in
                            if !model.touchActive {
                                if model.down(at: v.location, layout: l) && !looping { awake = true }
                            }
                        }
                        .onEnded { _ in
                            model.up()
                            sleepWhenResting()
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Fortune cookie. Tap or press and hold to crack it open.")
                .accessibilityAddTraits(.isButton)

            actions(l, looping: looping)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .onChange(of: touching) { _, down in
            // A touch the system cancels never reaches onEnded.
            if !down && model.touchActive {
                DispatchQueue.main.async {
                    model.cancel()
                    sleepWhenResting()
                }
            }
        }
    }

    /// Outside autoPlay the timeline pauses again once the cookie is back at rest.
    private func sleepWhenResting() {
        Task { @MainActor in
            while awake {
                try? await Task.sleep(for: .milliseconds(400))
                if model.resting { awake = false }
            }
        }
    }

    private func head(_ l: FortuneCookieCrackLayout, stage s: Double) -> some View {
        ZStack(alignment: .topLeading) {
            HStack {
                Text("Golden Wok")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color.rgba(255, 236, 214, 0.55))
                Spacer()
                Text("Order #GW-3318")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(Color.rgba(255, 236, 214, 0.4))
            }
            .frame(width: l.width - 48, height: 32)
            .offset(x: 24, y: l.top + 14)

            VStack(spacing: 8) {
                HStack(spacing: 6) {
                    ZStack {
                        Circle().fill(Color(hex: 0x3E7A4E))
                        Path { p in
                            p.move(to: CGPoint(x: 1.2, y: 4.2))
                            p.addLine(to: CGPoint(x: 3.2, y: 6))
                            p.addLine(to: CGPoint(x: 6.8, y: 2))
                        }
                        .stroke(Color(hex: 0xF2FBF3), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                        .frame(width: 8, height: 8)
                    }
                    .frame(width: 14, height: 14)
                    Text("ORDER DELIVERED · 7:42 PM")
                        .font(.system(size: 11, weight: .bold).monospacedDigit())
                        .tracking(0.8)
                        .foregroundStyle(Color(hex: 0x356A43))
                }
                .padding(.horizontal, 10)
                .frame(height: 24)
                .background(Capsule().fill(Color.rgba(62, 122, 78, 0.12)))

                Text("Your fortune is ready")
                    .font(.system(size: 26, weight: .heavy))
                    .tracking(-0.4)
                    .foregroundStyle(C.ink)
                    .multilineTextAlignment(.center)

                ZStack {
                    Text("Tap or squeeze the cookie to crack it")
                        .opacity(1 - s)
                        .offset(y: -s * 18)
                    Text("Baked into every Golden Wok order")
                        .opacity(s)
                        .offset(y: (1 - s) * 18)
                }
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(C.mute)
                .lineLimit(1)
                .frame(width: l.width - 80, height: 20)
                .clipped()
            }
            .frame(width: l.width - 80)
            .offset(x: 40, y: l.mTop + 26)
        }
        .allowsHitTesting(false)
    }

    private func lucky(_ l: FortuneCookieCrackLayout, t: Double) -> some View {
        VStack(spacing: 8) {
            Text("LUCKY NUMBERS")
                .font(.system(size: 11, weight: .bold))
                .tracking(1.6)
                .foregroundStyle(C.mute)
            HStack(spacing: 8) {
                ForEach(Array([7, 18, 23, 31, 42, 56].enumerated()), id: \.offset) { i, n in
                    let v = M.chipAt(t, i)
                    Text("\(n)")
                        .font(.system(size: 14, weight: .bold).monospacedDigit())
                        .foregroundStyle(Color(hex: 0xFFF1E2))
                        .frame(width: 34, height: 34)
                        .background(
                            Circle().fill(C.lacquer)
                                .overlay(Circle().strokeBorder(Color.rgba(255, 220, 200, 0.45), lineWidth: 1).mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .center)))
                                .shadow(color: Color.rgba(80, 20, 10, 0.18), radius: 6, y: 6)
                                .shadow(color: Color.rgba(80, 20, 10, 0.35), radius: 1, y: 1)
                        )
                        .scaleEffect(0.9 + 0.1 * v)
                        .offset(y: (1 - v) * 10)
                        .opacity(min(1, max(0, v * 1.4)))
                }
            }
        }
        .frame(width: l.width - 80)
        .offset(x: 40, y: l.slipY + FortuneCookieCrackLayout.slipH / 2 + 20)
        .allowsHitTesting(false)
    }

    private func actions(_ l: FortuneCookieCrackLayout, looping: Bool) -> some View {
        HStack(spacing: 12) {
            Button {} label: {
                Text("Save")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(C.ink)
                    .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
                    .background(
                        Capsule().fill(Color.rgba(59, 31, 23, 0.07))
                            .overlay(Capsule().strokeBorder(Color.rgba(59, 31, 23, 0.14), lineWidth: 1))
                    )
            }
            Button {
                if model.close(), !looping { awake = true; sleepWhenResting() }
            } label: {
                Text("Close")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color(hex: 0xFFF4E6))
                    .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
                    .background(
                        Capsule().fill(C.lacquer)
                            .shadow(color: Color.rgba(120, 30, 20, 0.25), radius: 22, y: 10)
                            .shadow(color: Color.rgba(80, 20, 10, 0.4), radius: 2, y: 1)
                    )
            }
        }
        .buttonStyle(FortuneCookieCrackPress())
        .frame(width: l.width - 2 * (16 + 20), height: 52)
        .offset(x: 36, y: l.height - l.bottom - 40 - 52)
    }
}

private struct FortuneCookieCrackPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

enum FortuneCookieCrackColors {
    static let root = Color(hex: 0x1C0E0B)
    static let lacquer = Color(hex: 0xA8322A)
    static let ink = Color(hex: 0x3B1F17)
    static let mute = Color.rgba(59, 31, 23, 0.6)
    static let modal = [Color(hex: 0xFCF6EA), Color(hex: 0xF7EDDA), Color(hex: 0xF0E2C8)]
}

/// Layout (pt): the modal fills the screen under the app's dimmed header.
struct FortuneCookieCrackLayout: Equatable {
    static let slipH = 84.0
    static let cookieScale = 1.12

    let width: Double
    let height: Double
    let top: Double
    let bottom: Double
    let mx = 16.0
    let mw: Double
    let mTop: Double
    let mH: Double
    let cx: Double
    let cy: Double
    let plateRx: Double
    let slipY: Double
    let slipW: Double

    init(size: CGSize, top: Double, bottom: Double) {
        width = size.width
        height = size.height
        self.top = top
        self.bottom = bottom
        mw = max(240, size.width - 32)
        mTop = top + 64
        mH = size.height - bottom - 20 - mTop
        cx = size.width / 2
        cy = mTop + mH * 0.4
        plateRx = min(140, mw / 2 - 24)
        slipY = mTop + mH * 0.655
        slipW = mw - 40
    }
}

/// The cookie's pieces rendered once (body texture, folded lip, lit rim, broken faces), the paper
/// grain, and the fortune's font.
struct FortuneCookieCrackArt {
    let shapes: FortuneCookieCrackShapes
    let whole: Image
    let left: Image
    let right: Image
    let grain: Image

    @MainActor
    static func make() async -> FortuneCookieCrackArt {
        let made = await Task.detached(priority: .userInitiated) {
            (FortuneCookieCrackTexture.cookie(scale: 3, shades: [1, 0.84]), FortuneCookieCrackTexture.grain())
        }.value
        let shapes = FortuneCookieCrackShapes()
        let body = Image(decorative: made.0[0], scale: 3)
        let lip = Image(decorative: made.0[1], scale: 3)
        func piece(_ b: Path, _ lp: Path, _ inside: Path, broken: Bool) -> Image {
            let bounds = FortuneCookieCrackShapes.bounds
            let view = Canvas { ctx, _ in
                var c = ctx
                c.translateBy(x: -bounds.minX, y: -bounds.minY)
                c.fill(inside, with: .linearGradient(
                    Gradient(colors: [Color(hex: 0x3E2008), Color(hex: 0x6A3A12), Color(hex: 0x8A5520)]),
                    startPoint: CGPoint(x: 0, y: 14), endPoint: CGPoint(x: 0, y: 54)))
                var inBody = c
                inBody.clip(to: b)
                inBody.draw(body, in: bounds)
                var sh = inBody
                sh.addFilter(.blur(radius: 3.5))
                sh.translateBy(x: 0, y: -2.5)
                sh.fill(lp, with: .color(Color.rgba(96, 48, 10, 0.45)))
                var inLip = inBody
                inLip.clip(to: lp)
                inLip.draw(lip, in: bounds)
                var rim = inBody
                rim.addFilter(.blur(radius: 0.8))
                rim.stroke(shapes.rim, with: .color(Color.rgba(255, 226, 160, 0.75)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
                if broken {
                    // The broken face shows the paler, airy inside of the wafer.
                    inBody.stroke(shapes.crack, with: .color(Color.rgba(252, 226, 168, 0.7)), style: StrokeStyle(lineWidth: 6, lineJoin: .round))
                    inBody.stroke(shapes.crack, with: .color(Color.rgba(120, 64, 18, 0.45)), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
                }
            }
            .frame(width: bounds.width, height: bounds.height)
            let r = ImageRenderer(content: view)
            r.scale = 3
            guard let cg = r.cgImage else { return body }
            return Image(decorative: cg, scale: 3)
        }
        return FortuneCookieCrackArt(
            shapes: shapes,
            whole: piece(shapes.whole, shapes.wholeLip, shapes.inside, broken: false),
            left: piece(shapes.left, shapes.leftLip, shapes.leftInside, broken: true),
            right: piece(shapes.right, shapes.rightLip, shapes.rightInside, broken: true),
            grain: Image(decorative: made.1, scale: 1.4)
        )
    }
}

/// Everything static behind the cookie: the dimmed order screen (warm dark, soft lantern lights,
/// a vignette), the modal's paper card and the porcelain plate.
struct FortuneCookieCrackBackdrop: View, Equatable {
    let layout: FortuneCookieCrackLayout
    let grain: Image?

    static func == (a: Self, b: Self) -> Bool { a.layout == b.layout && (a.grain == nil) == (b.grain == nil) }

    var body: some View {
        Canvas { ctx, size in
            let l = layout
            let W = size.width
            let H = size.height
            let full = Path(CGRect(origin: .zero, size: size))
            ctx.fill(full, with: .linearGradient(
                Gradient(colors: [Color(hex: 0x3A1A13), Color(hex: 0x24110D), Color(hex: 0x160B09)]),
                startPoint: .zero, endPoint: CGPoint(x: W * 0.3, y: H)))
            var lights = ctx
            lights.addFilter(.blur(radius: 22))
            for i in 0..<7 {
                let fi = Double(i)
                let x = W * FortuneCookieCrackMotion.hash(fi * 3.3)
                let y = 20 + H * 0.9 * FortuneCookieCrackMotion.hash(fi * 7.1)
                let r = 26 + 40 * FortuneCookieCrackMotion.hash(fi * 1.9)
                lights.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)),
                            with: .color(i % 2 == 0 ? Color.rgba(232, 150, 70, 0.16) : Color.rgba(196, 60, 44, 0.16)))
            }
            ctx.fill(full, with: .radialGradient(
                Gradient(colors: [Color.rgba(0, 0, 0, 0), Color.rgba(10, 4, 3, 0.55)]),
                center: CGPoint(x: W / 2, y: H * 0.45), startRadius: 0, endRadius: max(W, H) * 0.75))

            let card = CGRect(x: l.mx, y: l.mTop, width: l.mw, height: l.mH)
            let modal = Path(roundedRect: card, cornerRadius: 30)
            FortuneCookieCrackDraw.shadow(ctx, modal, color: Color.rgba(0, 0, 0, 0.42), blur: 44, dy: 24)
            FortuneCookieCrackDraw.shadow(ctx, modal, color: Color.rgba(0, 0, 0, 0.3), blur: 4, dy: 2)
            ctx.fill(modal, with: .linearGradient(
                Gradient(colors: FortuneCookieCrackColors.modal),
                startPoint: CGPoint(x: 0, y: l.mTop), endPoint: CGPoint(x: W * 0.2, y: l.mTop + l.mH)))
            if let grain { FortuneCookieCrackDraw.grain(ctx, modal, grain, amount: 0.45) }
            ctx.stroke(Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 29.5),
                       with: .color(Color.rgba(255, 255, 255, 0.7)), lineWidth: 1)

            // Porcelain plate with a red rim line.
            let cx = l.cx
            let cy = l.cy + 62
            let rx = l.plateRx
            let plate = Path(ellipseIn: CGRect(x: cx - rx, y: cy - 32, width: rx * 2, height: 64))
            FortuneCookieCrackDraw.shadow(ctx, plate, color: Color.rgba(70, 40, 20, 0.22), blur: 18, dy: 10)
            FortuneCookieCrackDraw.shadow(ctx, plate, color: Color.rgba(70, 40, 20, 0.3), blur: 2, dy: 1.5)
            ctx.fill(plate, with: .radialGradient(
                Gradient(colors: [Color(hex: 0xFFFDF8), Color(hex: 0xF2ECE2), Color(hex: 0xDCD3C5)]),
                center: CGPoint(x: cx, y: cy - 6), startRadius: 0, endRadius: rx))
            ctx.stroke(Path(ellipseIn: CGRect(x: cx - rx + 10, y: cy - 26, width: rx * 2 - 20, height: 52)),
                       with: .color(Color.rgba(168, 50, 42, 0.55)), lineWidth: 1.6)
            ctx.fill(Path(ellipseIn: CGRect(x: cx - rx * 0.66, y: cy - 18, width: rx * 1.32, height: 34)),
                     with: .color(Color.rgba(120, 90, 60, 0.07)))
        }
        .frame(width: layout.width, height: layout.height)
        .allowsHitTesting(false)
    }
}

enum FortuneCookieCrackDraw {
    typealias M = FortuneCookieCrackMotion
    private static let fortune = ["You will find joy in", "an unplanned detour."]

    static func shadow(_ ctx: GraphicsContext, _ path: Path, color: Color, blur: Double, dy: Double) {
        var c = ctx
        c.addFilter(.blur(radius: blur))
        c.translateBy(x: 0, y: dy)
        c.fill(path, with: .color(color))
    }

    static func grain(_ ctx: GraphicsContext, _ path: Path, _ image: Image, amount: Double) {
        var c = ctx
        c.clip(to: path)
        c.blendMode = .softLight
        c.opacity = amount
        c.fill(path, with: .tiledImage(image, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    /// The fortune's italic serif, shrunk if a line would not fit the slip.
    private static func fortuneFont(slipW: Double) -> UIFont {
        let base = UIFont(name: "Georgia-Italic", size: 18) ?? UIFont.italicSystemFont(ofSize: 18)
        let widest = fortune.map { ($0 as NSString).size(withAttributes: [.font: base]).width }.max() ?? 0
        if widest > slipW - 56 {
            return base.withSize(floor(18 * (slipW - 56) / widest))
        }
        return base
    }

    static func scene(_ ctx: GraphicsContext, layout l: FortuneCookieCrackLayout, pose p: FortuneCookieCrackMotion.Pose, art: FortuneCookieCrackArt) {
        let cx = l.cx
        let cy = l.cy
        let s = art.shapes

        // Soft shadows of the two halves on the plate, sliding apart as they open.
        var sh = ctx
        sh.addFilter(.blur(radius: 7))
        for d in [-1.0, 1.0] {
            let x = cx + d * (60 + 34 * p.open)
            sh.fill(Path(ellipseIn: CGRect(x: x - 62, y: cy + 58 - 9, width: 124, height: 18)), with: .color(Color.rgba(80, 40, 12, 0.28)))
        }

        // Crumbs that fly out of the crack and land on the plate.
        if p.crumbT >= 0 && p.crumbA > 0 {
            var crumbs = Path()
            for i in 0..<M.crumbs {
                let c = M.crumbAt(i, p.crumbT, 58)
                if c.on { crumbs.addEllipse(in: CGRect(x: cx + c.x - c.r, y: cy + c.y - c.r, width: 2 * c.r, height: 2 * c.r)) }
            }
            var cr = ctx
            cr.opacity = p.crumbA
            cr.drawLayer { layer in
                layer.addFilter(.shadow(color: Color.rgba(80, 40, 10, 0.45), radius: 0.8, x: 0, y: 0.8))
                layer.fill(crumbs, with: .color(Color(hex: 0xB9782F)))
            }
        }

        // The cookie: whole until the snap (no seam at rest), then two halves that swing apart.
        var c = ctx
        c.translateBy(x: cx, y: cy + p.bob)
        c.rotate(by: .radians(p.tilt + p.shake))
        c.scaleBy(x: p.sx * FortuneCookieCrackLayout.cookieScale, y: p.sy * FortuneCookieCrackLayout.cookieScale)
        let bounds = FortuneCookieCrackShapes.bounds
        if p.open <= 0.001 {
            c.draw(art.whole, in: bounds)
        } else {
            for (img, pivot, d) in [(art.left, s.pivotL, -1.0), (art.right, s.pivotR, 1.0)] {
                var h = c
                h.translateBy(x: d * 24 * p.open, y: 4 * p.open)
                h.translateBy(x: pivot.x, y: pivot.y)
                h.rotate(by: .radians(d * 0.34 * p.open))
                h.translateBy(x: -pivot.x, y: -pivot.y)
                h.draw(img, in: bounds)
            }
        }
        let crackOpacity = max(0, 1 - p.open * 2.5)
        if crackOpacity > 0 {
            var k = c
            k.opacity = crackOpacity
            var crease = k
            crease.addFilter(.blur(radius: 1.4))
            crease.stroke(s.crease, with: .color(Color.rgba(110, 58, 16, 0.4)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
            if p.crack > 0 {
                k.stroke(s.crack.trimmedPath(from: 0, to: p.crack), with: .color(Color.rgba(70, 32, 8, 0.85)),
                         style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
                k.stroke(s.branches.trimmedPath(from: 0, to: p.crack), with: .color(Color.rgba(70, 32, 8, 0.7)),
                         style: StrokeStyle(lineWidth: 1.1, lineJoin: .round))
            }
        }

        // The slip: a little roll that comes out of the gap, drops into place and unrolls both ways.
        let slipOpacity = min(1, p.emerge * 3)
        guard slipOpacity > 0 else { return }
        let slipH = FortuneCookieCrackLayout.slipH
        let half = 7 + (l.slipW / 2 - 7) * p.unroll
        var slip = ctx
        slip.opacity = slipOpacity
        slip.translateBy(x: cx, y: cy - 46 + (l.slipY - (cy - 46)) * p.drop)
        let sc = 0.58 + 0.42 * p.drop
        slip.scaleBy(x: sc, y: sc)
        let paper = Path(CGRect(x: -half, y: -slipH / 2, width: half * 2, height: slipH))
        let tint = Color.rgba(70, 40, 20, 1)
        let shadowed = slip
        shadowed.drawLayer { layer in
            layer.addFilter(.shadow(color: tint.opacity(0.22), radius: 12, x: 0, y: 6))
            layer.addFilter(.shadow(color: tint.opacity(0.25), radius: 1.5, x: 0, y: 1))
            layer.fill(paper, with: .linearGradient(
                Gradient(colors: [Color(hex: 0xFFFCF4), Color(hex: 0xF8F1E2), Color(hex: 0xEEE3CC)]),
                startPoint: CGPoint(x: 0, y: -slipH / 2), endPoint: CGPoint(x: 0, y: slipH / 2)))
        }
        grain(slip, paper, art.grain, amount: 0.7)
        for x in [-half - 7, half - 7] {
            let roll = Path(roundedRect: CGRect(x: x, y: -slipH / 2 - 3, width: 14, height: slipH + 6), cornerRadius: 7)
            let r = slip
            r.drawLayer { layer in
                layer.addFilter(.shadow(color: tint.opacity(0.3), radius: 3, x: 0, y: 2))
                // A rolled cylinder: lit just left of its middle, darker at both edges.
                layer.fill(roll, with: .linearGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0xD9CCB1), location: 0),
                        .init(color: Color(hex: 0xFFFBF1), location: 0.35),
                        .init(color: Color(hex: 0xE6D9BF), location: 0.7),
                        .init(color: Color(hex: 0xBFAF91), location: 1),
                    ]),
                    startPoint: CGPoint(x: x + 7 - 8, y: 0), endPoint: CGPoint(x: x + 7 + 8, y: 0)))
            }
        }

        // The fortune, revealed by the unrolling paper (not scaled with the slip: it shows once it has landed).
        guard p.drop > 0.98 else { return }
        var text = ctx
        text.clip(to: Path(CGRect(x: cx - half + 6, y: l.slipY - slipH, width: max(0, half * 2 - 12), height: slipH * 2)))
        let font = fortuneFont(slipW: l.slipW)
        let fs = font.pointSize
        for (k, line) in fortune.enumerated() {
            let y = l.slipY - 4 + (Double(k) - 0.5) * fs * 1.35 + fs * 0.36
            let t = Text(line).font(Font(font)).foregroundStyle(FortuneCookieCrackColors.lacquer)
            text.draw(t, at: CGPoint(x: cx, y: y + font.lineHeight - font.ascender), anchor: .bottom)
        }
    }
}

/// One clock. A finger moves it: holding plays the squeeze, a tap when open plays the close.
@MainActor
final class FortuneCookieCrackModel {
    typealias M = FortuneCookieCrackMotion

    /// Seconds after the last touch before the loop starts again.
    private static let idleDelay = 2.0
    /// A touch shorter than this is a tap: it cracks the cookie at once.
    private static let tap = 0.22

    private(set) var touchActive = false
    private var looping: Bool?
    private var clock = 0.0
    private var running = false
    private var user = false
    private var pressing = false
    private var pressFor = 0.0
    private var target = -1.0
    private var idle = 0.0
    private var relaxFrom: Double?
    private var relaxT = 0.0
    private var level = -1
    private var lastDate: Date?
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let select = UISelectionFeedbackGenerator()

    var resting: Bool { !user && !running && !touchActive }

    func step(to date: Date, looping: Bool, ready: Bool) -> Double {
        if self.looping != looping {
            self.looping = looping
            clock = 0
            running = looping
        }
        if date == lastDate { return clock }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0
        lastDate = date
        // A slow frame (or the first ones, before the art exists) pauses the clock instead of skipping ahead.
        let dt = ready ? min(max(raw, 0), 1.0 / 30) : 0

        if user {
            if pressing {
                pressFor += dt
                idle = 0
                let c = max(clock, M.squeezeAt) + dt
                if c >= M.crackAt {
                    pressing = false
                    target = M.openAt
                }
                clock = min(c, M.crackAt)
            } else if target >= 0 {
                var c = clock + dt
                if c >= target {
                    c = target >= M.loop ? 0 : target
                    target = -1
                }
                clock = c
            } else {
                if let from = relaxFrom {
                    // Let go before it snapped: the squeeze relaxes (280 ms, ease-in-out).
                    relaxT += dt
                    let x = min(1, relaxT / 0.28)
                    let e = x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
                    clock = from + (M.squeezeAt - from) * e
                    if x >= 1 { relaxFrom = nil }
                }
                idle += dt
                if idle > Self.idleDelay {
                    // Back to the loop: a cookie left open folds itself back up; a closed one starts over.
                    user = false
                    relaxFrom = nil
                    let c = clock
                    let isOpen = c >= M.openAt && c < M.closeAt
                    clock = isOpen ? M.closeAt : c < M.crackAt ? 0 : c
                    running = looping || isOpen
                }
            }
            // Haptics only for what a finger caused: the snap, and the slip lying open.
            let now = clock >= M.openAt - 0.01 ? 2 : clock >= M.crackAt ? 1 : 0
            if user && level >= 0 && now > level {
                if now == 1 { light.impactOccurred() } else { select.selectionChanged() }
            }
            level = user ? now : -1
        } else if running {
            level = -1
            var t = clock + dt
            if t >= M.loop {
                t -= M.loop
                if !looping {
                    t = 0
                    running = false
                }
            }
            clock = t
        }
        return clock
    }

    private func onCookie(_ p: CGPoint, _ l: FortuneCookieCrackLayout) -> Bool {
        abs(p.x - l.cx) < 140 && p.y > l.cy - 100 && p.y < l.cy + 90
    }

    /// A finger lands. Returns whether the cookie took it.
    func down(at p: CGPoint, layout l: FortuneCookieCrackLayout) -> Bool {
        touchActive = true
        let c = clock
        let closed = c < M.squeezeAt + 0.05 || c >= 8.4
        let open = c >= M.openAt - 0.01 && c < M.closeAt
        guard onCookie(p, l), closed || open else { return false }
        user = true
        running = false
        idle = 0
        relaxFrom = nil
        level = -1
        if closed {
            clock = M.squeezeAt
            pressing = true
            pressFor = 0
            target = -1
        } else {
            // Open: a touch folds the fortune back into the cookie.
            target = M.loop
            clock = max(c, M.closeAt)
        }
        return true
    }

    func up() {
        touchActive = false
        if pressing {
            pressing = false
            if pressFor < Self.tap {
                clock = max(clock, M.tapFrom)
                target = M.openAt
            } else {
                relaxFrom = clock
                relaxT = 0
            }
        }
        idle = 0
    }

    func cancel() {
        touchActive = false
        if pressing {
            pressing = false
            relaxFrom = clock
            relaxT = 0
        }
    }

    /// The Close button folds an open fortune back up. Returns whether it did.
    func close() -> Bool {
        let c = clock
        guard c >= M.openAt - 0.01 && c < M.closeAt else { return false }
        user = true
        running = false
        idle = 0
        clock = M.closeAt
        target = M.loop
        return true
    }
}
