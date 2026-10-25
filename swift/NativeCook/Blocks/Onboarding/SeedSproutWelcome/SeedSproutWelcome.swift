import SwiftUI
import UIKit

/// A plant-care app's welcome: a seed in a terracotta pot on a sunny sill. The watering can tips
/// and showers it, the seed splits and a stem grows, unfurling leaves, a bud opens into a coral
/// flower, and the onboarding pages roll with it; at the end the petals fall and it starts over.
/// Drag the can over the pot and it tips and pours by itself: the water grows the plant.
struct SeedSproutWelcome: View {
    /// Waters the seed, grows it into a sprout and a flower, and lets it fall back, every 10 s.
    var autoPlay = false

    static let loop = SeedSproutWelcomeMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = SeedSproutWelcomeModel()
    @State private var can: Image?
    @State private var awake = false
    @GestureState private var dragging = false

    typealias M = SeedSproutWelcomeMotion
    private typealias C = SeedSproutWelcomeColors

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(SeedSproutWelcomeLayout(size: size, top: insets.top, bottom: insets.bottom))
                    .frame(width: size.width, height: size.height)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(C.root.ignoresSafeArea())
        .task {
            if can == nil { can = SeedSproutWelcomeDraw.renderCan() }
        }
    }

    private func stage(_ l: SeedSproutWelcomeLayout) -> some View {
        let looping = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            SeedSproutWelcomeLayer(kind: .back, layout: l).equatable()

            TimelineView(.animation(paused: !(looping || awake))) { timeline in
                let scene = model.step(to: timeline.date, looping: looping, ready: can != nil)
                let user = model.user
                let can = can
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in SeedSproutWelcomeDraw.soil(l.hero(ctx), wet: scene.wet) }
                    SeedSproutWelcomeLayer(kind: .marker, layout: l).equatable()
                    Canvas { ctx, _ in SeedSproutWelcomeDraw.plant(l.hero(ctx), scene: scene, can: can, user: user) }
                    copy(l, page: scene.page)
                }
                .frame(width: l.width, height: l.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            header(l)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($dragging) { _, state, _ in state = true }
                .onChanged { v in
                    if !model.dragActive {
                        if model.begin(at: v.startLocation, layout: l) && !looping { awake = true }
                    }
                    model.update(v, layout: l)
                }
                .onEnded { v in
                    model.end(v, layout: l)
                    sleepWhenResting()
                }
        )
        .onChange(of: dragging) { _, down in
            // A drag the system cancels never reaches onEnded.
            if !down && model.dragActive {
                DispatchQueue.main.async {
                    model.end(nil, layout: l)
                    sleepWhenResting()
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("A seed in a pot. Drag the watering can over it to make it grow.")
    }

    /// Outside autoPlay the timeline pauses again once the plant is back to a seed.
    private func sleepWhenResting() {
        Task { @MainActor in
            while awake {
                try? await Task.sleep(for: .milliseconds(400))
                if model.resting { awake = false }
            }
        }
    }

    private func header(_ l: SeedSproutWelcomeLayout) -> some View {
        HStack {
            HStack(spacing: 8) {
                UnevenRoundedRectangle(topLeadingRadius: 16, bottomLeadingRadius: 0, bottomTrailingRadius: 16, topTrailingRadius: 0)
                    .fill(C.green)
                    .frame(width: 16, height: 16)
                    .rotationEffect(.degrees(-45))
                Text("Greenroom")
                    .font(.system(size: 17, weight: .heavy))
                    .tracking(-0.2)
                    .foregroundStyle(C.ink)
            }
            Spacer()
            Text("Skip")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(C.mute)
        }
        .frame(width: l.width - 48, height: 32)
        .offset(x: 24, y: l.top + 12)
        .allowsHitTesting(false)
    }

    private static let pages = [
        ("Plant something small", "Greenroom keeps every seed, pot and sill in one calm place."),
        ("Water on its own rhythm", "Gentle nudges tuned to each plant's thirst and your light."),
        ("Watch it bloom", "Log new leaves and buds, and see your garden come alive."),
    ]

    private func copy(_ l: SeedSproutWelcomeLayout, page: Double) -> some View {
        let r = M.clamp01(page - 1)
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                ForEach(0..<3, id: \.self) { i in
                    let d = Double(i) - page
                    VStack(alignment: .leading, spacing: 6) {
                        Text(Self.pages[i].0)
                            .font(.system(size: 28, weight: .heavy))
                            .tracking(-0.5)
                            .foregroundStyle(C.ink)
                        Text(Self.pages[i].1)
                            .font(.system(size: 16, weight: .medium))
                            .lineSpacing(3)
                            .foregroundStyle(C.mute)
                    }
                    .frame(width: l.width - 48, alignment: .leading)
                    .opacity(max(0, 1 - abs(d) * 1.6))
                    .offset(y: d * 28)
                }
            }
            .frame(width: l.width - 48, height: 96, alignment: .topLeading)
            .offset(x: 24, y: l.shelfY + 36)

            VStack(alignment: .leading, spacing: 20) {
                ZStack(alignment: .leading) {
                    HStack(spacing: 8) {
                        ForEach(0..<3, id: \.self) { _ in
                            Circle().fill(Color.rgba(35, 51, 31, 0.18)).frame(width: 8, height: 8)
                        }
                    }
                    Circle().fill(C.green).frame(width: 8, height: 8).offset(x: page * 16)
                }
                .frame(height: 8)
                ZStack {
                    ForEach(0..<2, id: \.self) { k in
                        let d = Double(k) - r
                        Text(k == 0 ? "Continue" : "Get started")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(Color(hex: 0xF1F5EA))
                            .opacity(max(0, 1 - abs(d) * 1.8))
                            .offset(y: d * 20)
                    }
                }
                .frame(width: l.width - 48, height: 52)
                .background(Capsule().fill(C.ink))
                .clipShape(Capsule())
                .background(
                    Capsule().fill(C.ink)
                        .shadow(color: Color.rgba(20, 30, 15, 0.2), radius: 22, y: 10)
                        .shadow(color: Color.rgba(20, 30, 15, 0.35), radius: 2, y: 1)
                )
            }
            .offset(x: 24, y: l.height - l.bottom - 16 - 80)
        }
    }
}

enum SeedSproutWelcomeColors {
    static let root = Color(hex: 0xE0E8D8)
    static let ink = Color(hex: 0x23331F)
    static let mute = Color.rgba(35, 51, 31, 0.62)
    static let green = Color(hex: 0x3E6B35)

    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func ch(_ v: UInt32, _ s: UInt32) -> Double { Double((v >> s) & 0xff) }
        return Color(.sRGB,
                     red: (ch(a, 16) * (1 - t) + ch(b, 16) * t).rounded() / 255,
                     green: (ch(a, 8) * (1 - t) + ch(b, 8) * t).rounded() / 255,
                     blue: (ch(a, 0) * (1 - t) + ch(b, 0) * t).rounded() / 255)
    }
}

/// Layout (pt): the pot stands on a sill; the copy, dots and button sit under it.
struct SeedSproutWelcomeLayout: Equatable {
    let width: Double
    let height: Double
    let top: Double
    let bottom: Double
    let shelfY: Double
    let scale: Double
    let ox: Double
    let oy: Double

    init(size: CGSize, top: Double, bottom: Double) {
        width = size.width
        height = size.height
        self.top = top
        self.bottom = bottom
        shelfY = size.height - bottom - 236
        scale = max(0.6, min(1.1, (shelfY - top - 64) / 400, (size.width - 32) / 330))
        ox = size.width / 2 - 22 * scale
        oy = shelfY - 112 * scale
    }

    /// A copy of `ctx` in pot coordinates.
    func hero(_ ctx: GraphicsContext) -> GraphicsContext {
        var c = ctx
        c.translateBy(x: ox, y: oy)
        c.scaleBy(x: scale, y: scale)
        return c
    }
}

/// A static layer, drawn once: the wall, sill and pot behind the soil, or the plant marker over it.
struct SeedSproutWelcomeLayer: View, Equatable {
    enum Kind { case back, marker }
    let kind: Kind
    let layout: SeedSproutWelcomeLayout

    var body: some View {
        Canvas { ctx, size in
            switch kind {
            case .back: SeedSproutWelcomeDraw.back(ctx, size: size, layout: layout)
            case .marker: SeedSproutWelcomeDraw.marker(layout.hero(ctx))
            }
        }
        .frame(width: layout.width, height: layout.height)
        .allowsHitTesting(false)
    }
}

enum SeedSproutWelcomeDraw {
    typealias M = SeedSproutWelcomeMotion
    typealias A = SeedSproutWelcomeArt

    static func blurred(_ ctx: GraphicsContext, _ radius: Double) -> GraphicsContext {
        var c = ctx
        c.addFilter(.blur(radius: radius))
        return c
    }

    static func grain(_ ctx: GraphicsContext, _ path: Path, amount: Double) {
        var c = ctx
        c.clip(to: path)
        c.blendMode = .softLight
        c.opacity = amount
        c.fill(path, with: .tiledImage(A.grain, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    /// The wall in window light, the sill and the terracotta pot.
    static func back(_ ctx: GraphicsContext, size: CGSize, layout l: SeedSproutWelcomeLayout) {
        let W = size.width
        let H = size.height
        let full = Path(CGRect(origin: .zero, size: size))
        ctx.fill(full, with: .linearGradient(
            Gradient(colors: [Color(hex: 0xEEF2E8), Color(hex: 0xE0E8D8), Color(hex: 0xCFDCC6)]),
            startPoint: .zero, endPoint: CGPoint(x: W * 0.4, y: H)))
        blurred(ctx, 26).fill(A.windowLight(W, H), with: .color(Color.rgba(255, 253, 240, 0.42)))
        grain(ctx, full, amount: 0.42)
        let shelf = Path(CGRect(x: 0, y: l.shelfY, width: W, height: 20))
        var sh = blurred(ctx, 18)
        sh.translateBy(x: 0, y: 10)
        sh.fill(shelf, with: .color(Color.rgba(60, 50, 30, 0.22)))
        ctx.fill(shelf, with: .linearGradient(
            Gradient(colors: [Color(hex: 0xD9B48A), Color(hex: 0xC79C6E), Color(hex: 0xA97E52)]),
            startPoint: CGPoint(x: 0, y: l.shelfY), endPoint: CGPoint(x: 0, y: l.shelfY + 20)))
        ctx.fill(Path(CGRect(x: 0, y: l.shelfY, width: W, height: 1.5)), with: .color(Color.rgba(255, 244, 224, 0.8)))

        let c = l.hero(ctx)
        blurred(c, 8).fill(Path(ellipseIn: CGRect(x: -86, y: 102, width: 172, height: 20)), with: .color(Color.rgba(50, 40, 20, 0.3)))
        c.fill(A.potBody, with: .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x9C4527), location: 0),
                .init(color: Color(hex: 0xD07A4C), location: 0.32),
                .init(color: Color(hex: 0xB45A33), location: 0.62),
                .init(color: Color(hex: 0x8E3D21), location: 1),
            ]),
            startPoint: CGPoint(x: -70, y: 0), endPoint: CGPoint(x: 70, y: 0)))
        grain(c, A.potBody, amount: 0.32)
        blurred(c, 4).fill(Path(CGRect(x: -70, y: 18, width: 140, height: 10)), with: .color(Color.rgba(70, 25, 10, 0.25)))
        var rimShadow = blurred(c, 3)
        rimShadow.translateBy(x: 0, y: 2)
        rimShadow.fill(A.potRim, with: .color(Color.rgba(80, 30, 10, 0.35)))
        c.fill(A.potRim, with: .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0xA84E2B), location: 0),
                .init(color: Color(hex: 0xDC8A5A), location: 0.3),
                .init(color: Color(hex: 0xBE6439), location: 0.62),
                .init(color: Color(hex: 0x953F22), location: 1),
            ]),
            startPoint: CGPoint(x: -80, y: 0), endPoint: CGPoint(x: 80, y: 0)))
        c.fill(Path(CGRect(x: -76, y: -7, width: 152, height: 1.5)), with: .color(Color.rgba(255, 214, 180, 0.6)))
    }

    /// The soil, darker while it is wet, with its crumbs and a wet sheen.
    static func soil(_ c: GraphicsContext, wet: Double) {
        let oval = Path(ellipseIn: A.soil)
        c.fill(oval, with: .linearGradient(
            Gradient(colors: [
                SeedSproutWelcomeColors.mix(0x6A4630, 0x432817, wet),
                SeedSproutWelcomeColors.mix(0x4E321F, 0x2F1C0E, wet),
                SeedSproutWelcomeColors.mix(0x3A2415, 0x22140A, wet),
            ]),
            startPoint: CGPoint(x: 0, y: -9), endPoint: CGPoint(x: 0, y: 9)))
        var inSoil = c
        inSoil.clip(to: Path(A.soil))
        inSoil.fill(A.soilCrumbs, with: .color(Color(hex: 0x7B5638)))
        if wet > 0 {
            var sheen = blurred(inSoil, 4)
            sheen.opacity = wet * 0.55
            sheen.fill(Path(ellipseIn: CGRect(x: -50, y: -7, width: 100, height: 12)), with: .color(Color.rgba(255, 255, 255, 0.12)))
        }
    }

    /// A wooden plant marker, so even the bare pot reads as a promise.
    static func marker(_ ctx: GraphicsContext) {
        var c = ctx
        c.translateBy(x: -44, y: -4)
        c.rotate(by: .radians(-0.12))
        c.fill(Path(CGRect(x: -2.5, y: -30, width: 5, height: 36)), with: .color(Color(hex: 0xC9A574)))
        let card = Path(roundedRect: CGRect(x: -17, y: -58, width: 34, height: 30), cornerRadius: 4)
        var sh = blurred(c, 3)
        sh.translateBy(x: 0, y: 2)
        sh.fill(card, with: .color(Color.rgba(70, 45, 20, 0.3)))
        c.fill(card, with: .linearGradient(
            Gradient(colors: [Color(hex: 0xEBD3A8), Color(hex: 0xD9BA86), Color(hex: 0xC49F68)]),
            startPoint: CGPoint(x: -16, y: -58), endPoint: CGPoint(x: 16, y: -30)))
        let base = UIFont(name: "Georgia-BoldItalic", size: 10) ?? UIFont.italicSystemFont(ofSize: 10)
        let t = Text("Cosmos").font(Font(base)).foregroundStyle(Color(hex: 0x5B4325))
        c.draw(t, at: CGPoint(x: 0, y: -40 + base.lineHeight - base.ascender), anchor: .bottom)
    }

    /// The watering can in its own frame (body centre at 0, 0), rendered once: handle, spout, brass
    /// rose, enamel body with its shadows and highlights.
    @MainActor
    static func renderCan() -> Image? {
        let b = A.canBounds
        let view = Canvas { ctx, _ in
            var c = ctx
            c.translateBy(x: -b.minX, y: -b.minY)
            c.stroke(A.canHandle, with: .color(Color(hex: 0x2F5E57)), style: StrokeStyle(lineWidth: 7, lineCap: .round))
            c.fill(A.canSpout, with: .linearGradient(
                Gradient(colors: [Color(hex: 0x5E9D93), Color(hex: 0x3E7870), Color(hex: 0x2B5A54)]),
                startPoint: CGPoint(x: -80, y: -20), endPoint: CGPoint(x: -60, y: 20)))
            c.fill(A.canRose, with: .linearGradient(
                Gradient(colors: [Color(hex: 0xD9B26A), Color(hex: 0xB88B40), Color(hex: 0x8E6626)]),
                startPoint: CGPoint(x: -100, y: -30), endPoint: CGPoint(x: -80, y: -10)))
            for (blur, dy, color) in [(18.0, 10.0, Color.rgba(30, 50, 40, 0.28)), (2.5, 1.5, Color.rgba(20, 40, 30, 0.35))] {
                var s = blurred(c, blur)
                s.translateBy(x: 0, y: dy)
                s.fill(A.canBody, with: .color(color))
            }
            c.fill(A.canBody, with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x3A746C), location: 0),
                    .init(color: Color(hex: 0x67A79C), location: 0.3),
                    .init(color: Color(hex: 0x4A8A80), location: 0.6),
                    .init(color: Color(hex: 0x2E5F59), location: 1),
                ]),
                startPoint: CGPoint(x: -50, y: 0), endPoint: CGPoint(x: 50, y: 0)))
            c.fill(Path(CGRect(x: -46, y: -30, width: 92, height: 7)), with: .color(Color.rgba(255, 255, 255, 0.18)))
            blurred(c, 2).fill(Path(CGRect(x: -26, y: -20, width: 6, height: 48)), with: .color(Color.rgba(255, 255, 255, 0.22)))
        }
        .frame(width: b.width, height: b.height)
        let r = ImageRenderer(content: view)
        r.scale = 3
        return r.cgImage.map { Image(decorative: $0, scale: 3) }
    }

    /// Seed, stem, leaves, bud, flower, the shower and the can: everything that moves.
    static func plant(_ c: GraphicsContext, scene sc: M.Scene, can: Image?, user: Bool) {
        let sway = 7 * sin(2 * .pi * 2 * sc.t / M.loop) * sc.g
        let plant = M.plantOf(sc.g)

        // The seed splits as the stem pushes out.
        let open = M.clamp01(plant.stem * 8)
        let seedA = 1 - M.clamp01((plant.stem - 0.1) * 6)
        if seedA > 0 {
            var s = c
            s.opacity = seedA
            s.scaleBy(x: 1.6, y: 1.6)
            var l = s
            l.translateBy(x: -2 - 5 * open, y: -3)
            l.rotate(by: .radians(-0.25 - 0.7 * open))
            l.fill(A.seedTop, with: .color(Color(hex: 0x7A5530)))
            var r = s
            r.translateBy(x: -2 + 5 * open, y: -3)
            r.rotate(by: .radians(-0.25 + 0.5 * open))
            r.fill(A.seedBottom, with: .color(Color(hex: 0x5E3E20)))
        }

        if plant.stem > 0.004 {
            let stem = A.stem(upTo: max(0.001, plant.stem), sway: sway)
            c.stroke(stem, with: .color(Color(hex: 0x4E7F3A)), style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
            c.stroke(stem, with: .color(Color.rgba(190, 230, 150, 0.55)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
        }

        // Leaves grow once the stem has passed them and unfurl with a springy overshoot.
        let leaves = (0..<M.leaves.count).filter { M.leafOpen(plant.stem, $0).len > 0 }
        if !leaves.isEmpty {
            c.drawLayer { layer in
                layer.addFilter(.shadow(color: Color.rgba(30, 60, 20, 0.3), radius: 3, x: 0, y: 2))
                for k in leaves {
                    let L = M.leaves[k]
                    let o = M.leafOpen(plant.stem, k)
                    let p = M.stemPoint(L.u, sway * L.u)
                    let droop = 0.06 * sin(2 * .pi * 2 * sc.t / M.loop + Double(k))
                    let angle = p.angle + L.side * (0.25 + 0.95 * o.wide) + droop
                    let len = L.len / 50 * max(0.001, o.len)
                    var f = layer
                    f.translateBy(x: p.x, y: p.y)
                    f.rotate(by: .radians(angle))
                    f.scaleBy(x: len, y: max(0.001, len * (0.3 + 0.7 * o.wide)))
                    f.fill(A.leaf, with: .linearGradient(
                        Gradient(colors: [Color(hex: 0x86BE68), Color(hex: 0x55923F), Color(hex: 0x336F2B)]),
                        startPoint: CGPoint(x: 0, y: -12), endPoint: CGPoint(x: 50, y: 12)))
                    f.stroke(A.veins, with: .color(Color.rgba(220, 245, 190, 0.45)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
                }
            }
        }

        // The flower at the stem's tip: a bud that opens into seven petals, which fall at the end.
        let headP = M.stemPoint(1, sway)
        var head = c
        head.translateBy(x: headP.x, y: headP.y)
        let grow = M.easeOut(M.clamp01((plant.stem - 0.82) / 0.18))
        let budA = 1 - M.clamp01((plant.bloom - 0.25) / 0.3)
        if grow > 0 && budA > 0 {
            var bud = head
            bud.opacity = budA
            let k = grow * (1 - 0.2 * plant.bloom)
            bud.scaleBy(x: k, y: k)
            bud.fill(A.bud, with: .linearGradient(
                Gradient(colors: [Color(hex: 0xF08A72), Color(hex: 0xB9785A), Color(hex: 0x5C8A44)]),
                startPoint: CGPoint(x: 0, y: -26), endPoint: CGPoint(x: 0, y: 2)))
        }
        if plant.bloom > 0 {
            // Falling petals keep falling from where the flower was, not from the shrinking stem.
            var fallFrom = c
            if sc.fall > 0 {
                let p = M.stemPoint(1, 0)
                fallFrom.translateBy(x: p.x, y: p.y)
            } else {
                fallFrom = head
            }
            let b = M.easeOut(plant.bloom)
            fallFrom.drawLayer { layer in
                layer.addFilter(.shadow(color: Color.rgba(120, 30, 20, 0.3), radius: 2.5, x: 0, y: 1.5))
                for i in 0..<M.petals {
                    let fi = Double(i)
                    let closed = -Double.pi / 2 + (fi - 3) * 0.13
                    let opened = -Double.pi / 2 + fi * 2 * .pi / Double(M.petals)
                    let angle = closed + (opened - closed) * b + .pi / 2
                    let scale = max(0.001, (0.45 + 0.55 * b) * M.clamp01(plant.bloom * 5)) * 1.25
                    let tau = sc.fall * 1.4 - fi * 0.12
                    var p = layer
                    if tau > 0 {
                        p.opacity = 1 - M.clamp01(tau / 1.05)
                        p.translateBy(x: 16 * sin(tau * 5 + fi) + tau * (fi - 3) * 14, y: 0.5 * 240 * tau * tau)
                        p.rotate(by: .radians(angle + tau * (M.hash(fi) - 0.5) * 6))
                    } else {
                        p.rotate(by: .radians(angle))
                    }
                    p.scaleBy(x: scale, y: scale)
                    p.fill(A.petal, with: .linearGradient(
                        Gradient(colors: [Color(hex: 0xD9573F), Color(hex: 0xEE7A62), Color(hex: 0xF8B39E)]),
                        startPoint: .zero, endPoint: CGPoint(x: 0, y: -36)))
                }
            }
            let discA = 1 - M.clamp01(sc.fall * 3)
            if discA > 0 {
                var disc = head
                disc.opacity = discA
                let k = max(0.001, b)
                disc.scaleBy(x: k, y: k)
                disc.fill(Path(ellipseIn: CGRect(x: -12, y: -12, width: 24, height: 24)), with: .radialGradient(
                    Gradient(colors: [Color(hex: 0xFFE08A), Color(hex: 0xF2B33D), Color(hex: 0xC9801C)]),
                    center: CGPoint(x: -2, y: -3), startRadius: 0, endRadius: 14))
                disc.fill(A.stamens, with: .color(Color(hex: 0x8A4F12)))
            }
        }

        // The shower from the rose. In the loop a drop shows only if the can was pouring when it left.
        if sc.pour > 0 || !user {
            let tip = M.spout(sc.cx, sc.cy, sc.tilt)
            var drops = Path()
            for i in 0..<M.drops {
                guard let d = M.dropAt(i, sc.t, tip.x, tip.y, sc.tilt) else { continue }
                let on = user ? sc.pour : M.sceneAt(max(0, sc.t - d.life)).pour
                if on > 0.05 {
                    let r = d.r * min(1, on + 0.3)
                    drops.addEllipse(in: CGRect(x: d.x - r, y: d.y - r, width: 2 * r, height: 2 * r))
                }
            }
            if !drops.isEmpty {
                c.drawLayer { layer in
                    layer.addFilter(.shadow(color: Color.rgba(40, 90, 120, 0.35), radius: 0.5, x: 0, y: 0.5))
                    layer.fill(drops, with: .color(Color.rgba(150, 205, 236, 0.95)))
                }
            }
        }

        if let can {
            var k = c
            k.translateBy(x: sc.cx, y: sc.cy)
            k.rotate(by: .radians(sc.tilt))
            k.draw(can, in: A.canBounds)
        }
    }
}

/// The loop's clock, and the finger's watering.
@MainActor
final class SeedSproutWelcomeModel {
    typealias M = SeedSproutWelcomeMotion

    /// Seconds after the last touch before the loop starts again.
    private static let idleDelay = 2.0
    /// Growth per second of full pouring under the finger.
    private static let growRate = 0.32
    private static let back = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))

    private(set) var dragActive = false
    private(set) var user = false
    private var looping: Bool?
    private var clock = 0.0
    private var wt = 0.0
    private var grabbing = false
    private var idle = 0.0
    private var g = 0.0
    private var x = M.park.x
    private var y = M.park.y
    private var vx = 0.0
    private var vy = 0.0
    private var homing = false
    private var tilt = 0.0
    private var tiltV = 0.0
    private var wet = 0.0
    private var offX = 0.0
    private var offY = 0.0
    private var returnFrom: Double?
    private var returnT = 0.0
    private var lastPage = -1
    private var lastDate: Date?
    private var scene = M.Scene()
    private let select = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    var resting: Bool { !user && !dragActive }

    func step(to date: Date, looping: Bool, ready: Bool) -> M.Scene {
        if self.looping != looping {
            self.looping = looping
            clock = 0
        }
        if date == lastDate { return scene }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0
        lastDate = date
        // A slow frame (or the first ones, before the can is drawn) pauses the clock instead of skipping ahead.
        let dt = ready ? min(max(raw, 0), 1.0 / 30) : 0
        wt += dt

        if user {
            if homing {
                // The can flies back to its shelf spot (about 500 ms, damping ratio 0.8), with the finger's speed.
                let k = 160.0
                let c = 2 * 0.8 * k.squareRoot()
                vx += (-k * (x - M.park.x) - c * vx) * dt
                vy += (-k * (y - M.park.y) - c * vy) * dt
                x += vx * dt
                y += vy * dt
            }
            // Over the pot the can tips and pours; the water grows the plant.
            let tip = M.spout(x, y, 0)
            let over = grabbing && abs(tip.x + 10) < 80 && tip.y < -40 && tip.y > -330
            let target = over ? M.tilt : 0
            let a = -140 * (tilt - target) - 16 * tiltV
            tiltV += a * dt
            tilt += tiltV * dt
            let pour = M.clamp01((tilt / M.tilt - 0.6) / 0.4)
            if let from = returnFrom {
                returnT += dt
                let p = min(1, returnT / 0.9)
                g = from * (1 - Self.back.value(at: p))
                if p >= 1 {
                    returnFrom = nil
                    clock = 0
                    user = false
                }
            } else {
                g = min(1, g + pour * Self.growRate * dt)
            }
            wet = max(pour, wet - dt * 0.25)
            if !grabbing && returnFrom == nil && user {
                idle += dt
                if idle > Self.idleDelay {
                    returnFrom = g
                    returnT = 0
                }
            }
            if user {
                var s = M.Scene()
                s.g = g
                s.cx = x
                s.cy = y
                s.tilt = tilt
                s.pour = pour
                s.wet = wet
                s.page = M.pageOf(g)
                s.t = wt
                scene = s
                // A tick when the finger's watering turns the page.
                let page = Int(s.page.rounded())
                if lastPage >= 0 && page > lastPage { select.selectionChanged() }
                lastPage = page
                return scene
            }
        }
        lastPage = -1
        if looping { clock = (clock + dt).truncatingRemainder(dividingBy: M.loop) }
        scene = M.sceneAt(clock)
        return scene
    }

    /// A finger lands; it takes the can if it is near it. Returns whether it did.
    func begin(at p: CGPoint, layout l: SeedSproutWelcomeLayout) -> Bool {
        dragActive = true
        let sc = scene
        let lx = (p.x - l.ox) / l.scale
        let ly = (p.y - l.oy) / l.scale
        guard returnFrom == nil, hypot(lx - sc.cx + 20, ly - sc.cy) <= 90 else { return false }
        if !user {
            g = sc.g
            wet = sc.wet
            tilt = sc.tilt
            tiltV = 0
            wt = sc.t
        }
        x = sc.cx
        y = sc.cy
        vx = 0
        vy = 0
        homing = false
        offX = lx - sc.cx
        offY = ly - sc.cy
        user = true
        grabbing = true
        idle = 0
        lastPage = Int(M.pageOf(g).rounded())
        light.impactOccurred()
        return true
    }

    func update(_ v: DragGesture.Value, layout l: SeedSproutWelcomeLayout) {
        guard grabbing else { return }
        idle = 0
        x = (v.location.x - l.ox) / l.scale - offX
        y = min(-90, (v.location.y - l.oy) / l.scale - offY)
    }

    func end(_ v: DragGesture.Value?, layout l: SeedSproutWelcomeLayout) {
        dragActive = false
        guard grabbing else { return }
        grabbing = false
        idle = 0
        vx = (v?.velocity.width ?? 0) / l.scale
        vy = (v?.velocity.height ?? 0) / l.scale
        homing = true
    }
}
