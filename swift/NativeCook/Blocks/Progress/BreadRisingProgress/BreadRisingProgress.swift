import SwiftUI
import UIKit

/// A bread-proofing timer whose progress is the loaf itself: dough rises in a glass bowl with
/// bubbles popping on its dome, pops into a scored boule on a baking stone, bakes golden-brown
/// inside a glowing oven with steam curling off it, and earns a ready check. The timer's odometer
/// wheels, a rolling status line, the Proof · Shape · Bake control and a progress bar follow it.
/// Drag up on the screen to scrub through the stages; let go and it settles on the nearest one.
struct BreadRisingProgress: View {
    /// Proofs, shapes and bakes the loaf, then starts over with raw dough, every 9 s.
    var autoPlay = false

    static let loop = BreadRisingProgressMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = BreadRisingProgressModel()
    @State private var awake = false
    @GestureState private var dragging = false

    typealias M = BreadRisingProgressMotion
    private typealias C = BreadRisingProgressColors
    private typealias Layer = BreadRisingProgressLayer

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(BreadRisingProgressLayout(size: size, top: insets.top, bottom: insets.bottom))
                    .frame(width: size.width, height: size.height)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(C.root.ignoresSafeArea())
    }

    private func stage(_ l: BreadRisingProgressLayout) -> some View {
        let looping = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            Layer(kind: .backdrop, layout: l).equatable()

            TimelineView(.animation(paused: !(looping || awake))) { timeline in
                let t = model.step(to: timeline.date, looping: looping)
                let b = M.breadAt(t)
                let prevTimer = M.breadAt(max(0, t - 1.0 / 60)).timer
                ZStack(alignment: .topLeading) {
                    // Static layers fade and slide as whole pictures; only the dough is drawn each frame.
                    Layer(kind: .oven, layout: l).equatable().opacity(b.oven)
                    Layer(kind: .cloth, layout: l).equatable().opacity(b.bowlA)
                    Layer(kind: .stone, layout: l).equatable().opacity(b.stone)
                    Layer(kind: .bowlBack, layout: l).equatable()
                        .offset(y: b.bowlY * l.heroScale).opacity(b.bowlA)
                    Canvas { ctx, _ in BreadRisingProgressDraw.dough(ctx, layout: l, bread: b) }
                    Layer(kind: .bowlFront, layout: l).equatable()
                        .offset(y: b.bowlY * l.heroScale).opacity(b.bowlA)
                    if b.steam > 0.001 {
                        Canvas { ctx, _ in BreadRisingProgressDraw.steam(ctx, layout: l, bread: b) }
                            .opacity(b.steam)
                    }
                    BreadRisingProgressBadge()
                        .scaleEffect(max(0.001, b.ready) * l.heroScale)
                        .opacity(min(1, b.ready * 2))
                        .position(x: l.width / 2 + 104 * l.heroScale, y: l.heroY + (M.bouleCY - 100) * l.heroScale)

                    labels(l, bread: b, prevTimer: prevTimer)
                }
                .frame(width: l.width, height: l.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            header(l)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 4)
                .updating($dragging) { _, state, _ in state = true }
                .onChanged { v in
                    if !model.dragActive {
                        if !looping { awake = true }
                        model.begin()
                    }
                    model.update(v)
                }
                .onEnded { v in
                    model.end(v)
                    sleepWhenResting()
                }
        )
        .onChange(of: dragging) { _, down in
            // A drag the system cancels never reaches onEnded.
            if !down && model.dragActive {
                DispatchQueue.main.async {
                    model.end(nil)
                    sleepWhenResting()
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Proofing dough. Drag up to proof, shape and bake the loaf.")
    }

    /// Outside autoPlay the timeline pauses again once the dough is back at rest.
    private func sleepWhenResting() {
        Task { @MainActor in
            while awake {
                try? await Task.sleep(for: .milliseconds(400))
                if model.resting { awake = false }
            }
        }
    }

    private func header(_ l: BreadRisingProgressLayout) -> some View {
        ZStack(alignment: .topLeading) {
            HStack {
                HStack(spacing: 8) {
                    Circle()
                        .fill(C.accent)
                        .overlay(
                            Circle().fill(RadialGradient(colors: [.clear, Color.rgba(80, 30, 5, 0.35)],
                                                         center: UnitPoint(x: 0.5, y: 0.25), startRadius: 4, endRadius: 11))
                        )
                        .frame(width: 18, height: 18)
                        .shadow(color: Color.rgba(80, 40, 10, 0.3), radius: 1, y: 1)
                    Text("Crumb")
                        .font(.system(size: 17, weight: .heavy))
                        .tracking(-0.2)
                        .foregroundStyle(C.ink)
                }
                Spacer()
                Text("Loaf 2 of 3")
                    .font(.system(size: 13, weight: .semibold).monospacedDigit())
                    .foregroundStyle(C.mute)
            }
            .frame(width: l.width - 40, height: 32)
            .offset(x: 20, y: l.top + 12)

            VStack(alignment: .leading, spacing: 2) {
                Text("Country Sourdough")
                    .font(.system(size: 30, weight: .heavy))
                    .tracking(-0.6)
                    .foregroundStyle(C.ink)
                Text("78% hydration · 900 g · Dutch oven")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(C.mute)
            }
            .frame(width: l.width - 40, alignment: .leading)
            .offset(x: 20, y: l.top + 56)
        }
        .allowsHitTesting(false)
    }

    private static let statuses = ["Proofing at 26 °C", "Shaping the boule", "Baking at 240 °C", "Ready to cool", "Proofing at 26 °C"]
    private static let stageNames = ["Proof", "Shape", "Bake"]
    private static let digitH = 62.0

    private func labels(_ l: BreadRisingProgressLayout, bread b: M.Bread, prevTimer: Double) -> some View {
        let at = b.stage <= 2 ? b.stage : 2 * (3 - b.stage)
        let segW = (l.trackW - 8) / 3
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 0) {
                    wheel(b.timer, prevTimer, unit: 3600, base: 10)
                    colon
                    wheel(b.timer, prevTimer, unit: 600, base: 6)
                    wheel(b.timer, prevTimer, unit: 60, base: 10)
                    colon
                    wheel(b.timer, prevTimer, unit: 10, base: 6)
                    wheel(b.timer, prevTimer, unit: 1, base: 10)
                }
                .frame(height: Self.digitH)
                // A status line that rolls: the old one leaves upward and fades, the next comes in from below.
                ZStack(alignment: .leading) {
                    ForEach(0..<Self.statuses.count, id: \.self) { i in
                        let d = Double(i) - b.status
                        Text(Self.statuses[i])
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(C.accent)
                            .frame(height: 22)
                            .opacity(max(0, 1 - abs(d) * 1.6))
                            .offset(y: d * 20)
                    }
                }
                .frame(width: l.trackW, height: 22, alignment: .leading)
                .clipped()
            }
            .offset(x: 20, y: l.top + 124)

            VStack(alignment: .leading, spacing: 16) {
                // Proof · Shape · Bake, with a thumb that slides with the stage (and back to Proof on reset).
                ZStack(alignment: .topLeading) {
                    Capsule()
                        .fill(Color.rgba(255, 250, 240, 0.55))
                        .overlay(
                            Capsule().stroke(Color.rgba(90, 60, 30, 0.18), lineWidth: 2)
                                .blur(radius: 1)
                                .offset(y: 1)
                                .clipShape(Capsule())
                        )
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(C.ink)
                        .shadow(color: Color.rgba(40, 20, 5, 0.18), radius: 14, y: 6)
                        .shadow(color: Color.rgba(40, 20, 5, 0.35), radius: 2, y: 1)
                        .frame(width: segW, height: 36)
                        .offset(x: 4 + at * segW, y: 4)
                    HStack(spacing: 0) {
                        ForEach(0..<3, id: \.self) { i in
                            ZStack {
                                Text(Self.stageNames[i]).foregroundStyle(C.mute)
                                Text(Self.stageNames[i]).foregroundStyle(Color(hex: 0xFBF3E6))
                                    .opacity(max(0, 1 - abs(at - Double(i))))
                            }
                            .font(.system(size: 15, weight: .bold))
                            .frame(width: segW, height: 36)
                        }
                    }
                    .offset(x: 4, y: 4)
                }
                .frame(width: l.trackW, height: 44)

                Capsule()
                    .fill(Color.rgba(90, 60, 30, 0.14))
                    .overlay(alignment: .leading) {
                        Capsule().fill(C.accent)
                            .offset(x: -(1 - b.progress) * l.trackW)
                    }
                    .clipShape(Capsule())
                    .frame(width: l.trackW, height: 6)

                HStack {
                    Text("Started 6:10 PM")
                    Spacer()
                    Text("Out of the oven 8:55 PM")
                }
                .font(.system(size: 12, weight: .semibold).monospacedDigit())
                .foregroundStyle(C.mute)
                .frame(width: l.trackW)
                .padding(.top, -6)
            }
            .offset(x: 20, y: l.controlsTop)
        }
    }

    private var colon: some View {
        Text(":")
            .font(.system(size: 46, weight: .semibold))
            .foregroundStyle(Color.rgba(58, 42, 28, 0.45))
            .padding(.horizontal, 1)
            .offset(y: -3)
    }

    /// One odometer wheel of the timer; a wheel spinning fast dims a little, like a blur.
    private func wheel(_ timer: Double, _ prev: Double, unit: Double, base: Int) -> some View {
        let fb = Double(base)
        let pos = M.column(timer, unit, fb)
        var speed = abs(pos - M.column(prev, unit, fb))
        if speed > fb / 2 { speed = fb - speed }
        let blur = min(1, max(0, (speed - 0.12) / 0.5))
        return VStack(spacing: 0) {
            ForEach(0...base, id: \.self) { d in
                Text("\(d % base)")
                    .font(.system(size: 54, weight: .bold).monospacedDigit())
                    .tracking(-1)
                    .foregroundStyle(C.ink)
                    .frame(width: 34, height: Self.digitH)
            }
        }
        .offset(y: -pos * Self.digitH)
        .opacity(1 - 0.55 * blur)
        .frame(width: 34, height: Self.digitH, alignment: .top)
        .clipped()
    }
}

enum BreadRisingProgressColors {
    static let root = Color(hex: 0xF0E5D3)
    static let ink = Color(hex: 0x3A2A1C)
    static let mute = Color.rgba(58, 42, 28, 0.58)
    static let accent = Color(hex: 0xB4642A)
    static let raw: [(Double, Double, Double)] = [(0xF8, 0xE6, 0xC8), (0xED, 0xCF, 0xA2), (0xD6, 0xAE, 0x7C)]
    static let baked: [(Double, Double, Double)] = [(0xD9, 0x9A, 0x52), (0xB0, 0x6A, 0x2E), (0x7A, 0x43, 0x1B)]

    static func dough(_ i: Int, _ t: Double) -> Color {
        let a = raw[i]
        let b = baked[i]
        return Color(.sRGB,
                     red: (a.0 + (b.0 - a.0) * t).rounded() / 255,
                     green: (a.1 + (b.1 - a.1) * t).rounded() / 255,
                     blue: (a.2 + (b.2 - a.2) * t).rounded() / 255)
    }
}

/// Layout (pt). The hero sits between the timer and the stage control, on the counter.
struct BreadRisingProgressLayout: Equatable {
    let width: Double
    let height: Double
    let top: Double
    let bottom: Double
    let controlsTop: Double
    let heroScale: Double
    let heroY: Double
    let counterY: Double
    let trackW: Double

    init(size: CGSize, top: Double, bottom: Double) {
        width = size.width
        height = size.height
        self.top = top
        self.bottom = bottom
        let heroTop = top + 236
        controlsTop = size.height - bottom - 150
        let avail = controlsTop - heroTop - 12
        heroScale = min(1.08, max(0.6, avail / 330), (size.width - 24) / 350)
        heroY = heroTop + 168 * heroScale
        counterY = heroY + (BreadRisingProgressMotion.bowlRY + 8) * heroScale
        trackW = max(0, size.width - 40)
    }

    /// A copy of `ctx` in the hero's coordinates.
    func hero(_ ctx: GraphicsContext) -> GraphicsContext {
        var c = ctx
        c.translateBy(x: width / 2, y: heroY)
        c.scaleBy(x: heroScale, y: heroScale)
        return c
    }
}

/// The ready check: a green disc with a cream tick, 40 pt across.
private struct BreadRisingProgressBadge: View {
    var body: some View {
        ZStack {
            Circle()
                .fill(LinearGradient(colors: [Color(hex: 0x6FAE6E), Color(hex: 0x4E8F52), Color(hex: 0x3D7442)], startPoint: .top, endPoint: .bottom))
                .shadow(color: Color.rgba(30, 60, 30, 0.35), radius: 8, y: 4)
            BreadRisingProgressArt.check
                .offsetBy(dx: 20, dy: 20)
                .stroke(Color(hex: 0xF4FBF2), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
        }
        .frame(width: 40, height: 40)
    }
}

/// One static layer of the screen, drawn once (SwiftUI keeps the picture while only its opacity
/// and offset change).
struct BreadRisingProgressLayer: View, Equatable {
    enum Kind { case backdrop, oven, cloth, stone, bowlBack, bowlFront }
    let kind: Kind
    let layout: BreadRisingProgressLayout

    var body: some View {
        Canvas { ctx, size in
            switch kind {
            case .backdrop: BreadRisingProgressDraw.backdrop(ctx, size: size, layout: layout)
            case .oven: BreadRisingProgressDraw.oven(layout.hero(ctx))
            case .cloth: BreadRisingProgressDraw.cloth(layout.hero(ctx))
            case .stone: BreadRisingProgressDraw.stone(layout.hero(ctx))
            case .bowlBack: BreadRisingProgressDraw.bowlBack(layout.hero(ctx))
            case .bowlFront: BreadRisingProgressDraw.bowlFront(layout.hero(ctx))
            }
        }
        .frame(width: layout.width, height: layout.height)
        .allowsHitTesting(false)
    }
}

enum BreadRisingProgressDraw {
    typealias M = BreadRisingProgressMotion
    typealias A = BreadRisingProgressArt

    static func shadow(_ ctx: GraphicsContext, _ path: Path, color: Color, blur: Double, dy: Double) {
        var c = ctx
        c.addFilter(.blur(radius: blur))
        c.translateBy(x: 0, y: dy)
        c.fill(path, with: .color(color))
    }

    static func grain(_ ctx: GraphicsContext, _ path: Path, amount: Double) {
        var c = ctx
        c.clip(to: path)
        c.blendMode = .softLight
        c.opacity = amount
        c.fill(path, with: .tiledImage(A.grain, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    static func backdrop(_ ctx: GraphicsContext, size: CGSize, layout l: BreadRisingProgressLayout) {
        let full = Path(CGRect(origin: .zero, size: size))
        ctx.fill(full, with: .linearGradient(
            Gradient(colors: [Color(hex: 0xF7F0E5), Color(hex: 0xF0E5D3), Color(hex: 0xE7D7BE)]),
            startPoint: .zero, endPoint: CGPoint(x: size.width * 0.35, y: size.height)))
        let counter = CGRect(x: 0, y: l.counterY, width: size.width, height: size.height - l.counterY)
        ctx.fill(Path(counter), with: .linearGradient(
            Gradient(colors: [Color(hex: 0xDCC8A8), Color(hex: 0xD2BC98), Color(hex: 0xC4AB84)]),
            startPoint: CGPoint(x: 0, y: l.counterY), endPoint: CGPoint(x: 0, y: size.height)))
        ctx.fill(Path(CGRect(x: 0, y: l.counterY, width: size.width, height: 1.5)), with: .color(Color.rgba(255, 250, 240, 0.7)))
        grain(ctx, full, amount: 0.45)
    }

    /// The oven: a dark window with glowing elements, behind the loaf while it bakes.
    static func oven(_ c: GraphicsContext) {
        let box = Path(roundedRect: CGRect(x: -172, y: -170, width: 344, height: M.baseY + 196), cornerRadius: 30)
        shadow(c, box, color: Color.rgba(60, 30, 10, 0.35), blur: 30, dy: 14)
        c.fill(box, with: .linearGradient(
            Gradient(colors: [Color(hex: 0x2B1810), Color(hex: 0x3A2015), Color(hex: 0x24140D)]),
            startPoint: CGPoint(x: 0, y: -170), endPoint: CGPoint(x: 0, y: M.baseY + 26)))
        c.fill(Path(ellipseIn: CGRect(x: -170, y: -180, width: 340, height: 340)), with: .radialGradient(
            Gradient(colors: [Color.rgba(255, 150, 70, 0.42), Color.rgba(255, 110, 40, 0.14), Color.rgba(255, 90, 30, 0)]),
            center: CGPoint(x: 0, y: -10), startRadius: 0, endRadius: 170))
        for y in [-150.0, -139] {
            let bar = Path(roundedRect: CGRect(x: -136, y: y, width: 272, height: 4), cornerRadius: 2)
            var glow = c
            glow.addFilter(.blur(radius: 7))
            glow.fill(bar, with: .color(Color(hex: 0xFF7A2E)))
            c.fill(bar, with: .color(Color(hex: 0xFF7A2E)))
            c.fill(Path(roundedRect: CGRect(x: -136, y: y + 1, width: 272, height: 2), cornerRadius: 1), with: .color(Color(hex: 0xFFD3A0)))
        }
        c.stroke(Path(roundedRect: CGRect(x: -171, y: -169, width: 342, height: M.baseY + 194), cornerRadius: 29),
                 with: .color(Color.rgba(255, 220, 180, 0.16)), lineWidth: 2)
    }

    /// A linen cloth under the bowl, its hem hanging over the counter's edge, and the bowl's shadows.
    static func cloth(_ c: GraphicsContext) {
        let ry = M.bowlRY
        shadow(c, A.cloth, color: Color.rgba(80, 55, 25, 0.25), blur: 10, dy: 6)
        c.fill(A.cloth, with: .linearGradient(
            Gradient(colors: [Color(hex: 0xF4EFE6), Color(hex: 0xE8E0D2), Color(hex: 0xD6CBB8)]),
            startPoint: CGPoint(x: 0, y: ry - 14), endPoint: CGPoint(x: 0, y: ry + 62)))
        var inCloth = c
        inCloth.clip(to: A.cloth)
        inCloth.fill(Path(CGRect(x: -200, y: ry + 38, width: 400, height: 4)), with: .color(Color.rgba(84, 118, 148, 0.6)))
        inCloth.fill(Path(CGRect(x: -200, y: ry + 46, width: 400, height: 2)), with: .color(Color.rgba(84, 118, 148, 0.5)))
        var folds = inCloth
        folds.addFilter(.blur(radius: 6))
        folds.stroke(A.folds, with: .color(Color.rgba(120, 95, 60, 0.14)), lineWidth: 10)
        grain(inCloth, A.cloth, amount: 0.28)
        var s = c
        s.addFilter(.blur(radius: 12))
        s.fill(Path(ellipseIn: CGRect(x: -112, y: ry - 6, width: 224, height: 22)), with: .color(Color.rgba(80, 50, 20, 0.26)))
        var s2 = c
        s2.addFilter(.blur(radius: 4))
        s2.fill(Path(ellipseIn: CGRect(x: -58, y: ry - 5, width: 116, height: 11)), with: .color(Color.rgba(60, 38, 16, 0.42)))
    }

    /// The baking stone the boule sits on.
    static func stone(_ c: GraphicsContext) {
        let y = M.baseY
        let slab = Path(ellipseIn: CGRect(x: -150, y: y - 6, width: 300, height: 34))
        shadow(c, slab, color: Color.rgba(40, 24, 10, 0.35), blur: 16, dy: 8)
        c.fill(slab, with: .linearGradient(
            Gradient(colors: [Color(hex: 0x57504B), Color(hex: 0x3B3532), Color(hex: 0x2A2522)]),
            startPoint: CGPoint(x: 0, y: y - 6), endPoint: CGPoint(x: 0, y: y + 28)))
        var s = c
        s.addFilter(.blur(radius: 6))
        s.fill(Path(ellipseIn: CGRect(x: -120, y: y + 2, width: 240, height: 16)), with: .color(Color.rgba(20, 10, 4, 0.45)))
    }

    /// The far wall of the glass, a touch tinted and darker, so the bowl has a body.
    static func bowlBack(_ c: GraphicsContext) {
        c.fill(A.front, with: .linearGradient(
            Gradient(colors: [Color.rgba(96, 140, 132, 0.22), Color.rgba(120, 160, 152, 0.14), Color.rgba(70, 110, 104, 0.26)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: M.bowlRY)))
        var soft = c
        soft.addFilter(.blur(radius: 5))
        soft.stroke(A.back, with: .color(Color.rgba(50, 85, 80, 0.18)), lineWidth: 9)
        c.stroke(A.back, with: .color(Color.rgba(255, 255, 255, 0.7)), lineWidth: 2.4)
        c.stroke(A.back, with: .color(Color.rgba(80, 125, 118, 0.5)), lineWidth: 1)
    }

    /// The glass in front of the dough: tinted edges, reflections, the lit rim and the foot.
    static func bowlFront(_ c: GraphicsContext) {
        let rx = M.bowlRX
        c.fill(A.front, with: .linearGradient(
            Gradient(stops: [
                .init(color: Color.rgba(120, 170, 160, 0.5), location: 0),
                .init(color: Color.rgba(210, 236, 230, 0.16), location: 0.16),
                .init(color: Color.rgba(255, 255, 255, 0.04), location: 0.5),
                .init(color: Color.rgba(200, 230, 224, 0.14), location: 0.84),
                .init(color: Color.rgba(110, 160, 150, 0.46), location: 1),
            ]),
            startPoint: CGPoint(x: -rx, y: 0), endPoint: CGPoint(x: rx, y: 0)))
        var streaks = c
        streaks.addFilter(.blur(radius: 2))
        streaks.stroke(A.streaks, with: .color(Color.rgba(255, 255, 255, 0.55)), style: StrokeStyle(lineWidth: 5, lineCap: .round))
        c.stroke(A.front, with: .color(Color.rgba(70, 115, 108, 0.55)), lineWidth: 2.2)
        c.stroke(A.front, with: .color(Color.rgba(255, 255, 255, 0.75)), lineWidth: 1)
        c.stroke(A.rim, with: .color(Color.rgba(150, 195, 186, 0.45)), lineWidth: 6)
        c.stroke(A.rim, with: .color(Color.rgba(255, 255, 255, 0.9)), lineWidth: 3)
        var low = c
        low.translateBy(x: 0, y: 3)
        low.stroke(A.rim, with: .color(Color.rgba(60, 105, 98, 0.55)), lineWidth: 1)
        c.stroke(A.foot, with: .color(Color.rgba(255, 255, 255, 0.6)), lineWidth: 2)
    }

    /// The dough (raw → risen → boule → baked), its bubbles and its score: the only art drawn each frame.
    static func dough(_ ctx: GraphicsContext, layout l: BreadRisingProgressLayout, bread b: M.Bread) {
        let c = l.hero(ctx)
        let path = A.dough(M.doughPoints(rise: b.rise, shape: b.shape))
        let top = M.doughTop(rise: b.rise, shape: b.shape)
        c.fill(path, with: .linearGradient(
            Gradient(colors: (0..<3).map { BreadRisingProgressColors.dough($0, b.bake) }),
            startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: M.bouleCY + 24)))
        var inside = c
        inside.clip(to: path)
        let sheen = CGPoint(x: -38, y: top + 30)
        inside.fill(Path(ellipseIn: CGRect(x: sheen.x - 96, y: sheen.y - 96, width: 192, height: 192)), with: .radialGradient(
            Gradient(colors: [Color.rgba(255, 250, 238, 0.75), Color.rgba(255, 246, 228, 0.25), Color.rgba(255, 246, 228, 0)]),
            center: sheen, startRadius: 0, endRadius: 96))
        if b.bake < 1 { inside.fill(A.flour, with: .color(Color(hex: 0xFFFDF8).opacity(0.55 * (1 - b.bake)))) }
        if b.bake > 0 { inside.fill(A.blisters, with: .color(Color(hex: 0x4A250C).opacity(0.5 * b.bake))) }
        var rim = inside
        rim.addFilter(.blur(radius: 8))
        rim.stroke(path, with: .color(Color.rgba(130, 74, 28, 0.2)), lineWidth: 14)

        if b.t > M.proofStart && b.t < M.proofEnd {
            var fill = Path()
            var ring = Path()
            for i in 0..<M.bubbles {
                let q = M.bubbleAt(i, b.t, b.rise)
                if q.r > 0.2 { fill.addEllipse(in: CGRect(x: q.x - q.r, y: q.y - q.r, width: 2 * q.r, height: 2 * q.r)) }
                if q.ringA > 0.02 { ring.addEllipse(in: CGRect(x: q.x - q.ring, y: q.y - q.ring, width: 2 * q.ring, height: 2 * q.ring)) }
            }
            if !fill.isEmpty {
                c.drawLayer { layer in
                    layer.addFilter(.shadow(color: Color.rgba(150, 110, 60, 0.6), radius: 0.8, x: 0, y: 0.6))
                    layer.fill(fill, with: .color(Color.rgba(255, 250, 238, 0.85)))
                }
            }
            c.stroke(ring, with: .color(Color.rgba(170, 130, 80, 0.55)), lineWidth: 1)
        }

        let scoreA = min(1, max(0, (b.shape - 0.9) * 10)) * b.stone
        if scoreA > 0 {
            var s = c
            s.opacity = scoreA
            s.fill(A.score(top: top, open: b.bake), with: .linearGradient(
                Gradient(colors: [Color(hex: 0xF6E2BC), Color(hex: 0xE9C890), Color(hex: 0xD8AE70)]),
                startPoint: CGPoint(x: 0, y: M.bouleCY - 90), endPoint: CGPoint(x: 0, y: M.bouleCY - 60)))
            s.drawLayer { layer in
                layer.addFilter(.shadow(color: Color.rgba(70, 30, 8, 0.45), radius: 2, x: 0, y: 2))
                layer.stroke(A.ear(top: top, open: b.bake), with: .color(Color.rgba(255, 230, 180, 0.75)),
                             style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
        }
    }

    /// Three wisps of steam off the crust.
    static func steam(_ ctx: GraphicsContext, layout l: BreadRisingProgressLayout, bread b: M.Bread) {
        let c = l.hero(ctx)
        let path = A.steam(t: b.t, top: M.doughTop(rise: b.rise, shape: b.shape))
        var wide = c
        wide.addFilter(.blur(radius: 9))
        wide.stroke(path, with: .color(Color.rgba(255, 250, 244, 0.22)), style: StrokeStyle(lineWidth: 12, lineCap: .round, lineJoin: .round))
        var thin = c
        thin.addFilter(.blur(radius: 3))
        thin.stroke(path, with: .color(Color.rgba(255, 250, 244, 0.18)), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
    }
}

/// The timeline. Auto-play runs it; a finger scrubs it.
@MainActor
final class BreadRisingProgressModel {
    typealias M = BreadRisingProgressMotion

    /// Seconds after the last touch before the loop starts again.
    private static let idleDelay = 2.0
    /// Finger travel (pt) that scrubs from raw dough to the baked loaf.
    private static let scrub = 320.0
    private static let back = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))

    private(set) var dragActive = false
    private var looping: Bool?
    private var tl = 0.0
    private var user = false
    private var grabbing = false
    private var idle = 0.0
    private var startTl = 0.0
    private var lastStop = 0
    private var springTo: Double?
    private var springV = 0.0
    private var returnFrom: Double?
    private var returnT = 0.0
    private var lastDate: Date?
    private let select = UISelectionFeedbackGenerator()

    var resting: Bool { !user && !dragActive }

    func step(to date: Date, looping: Bool) -> Double {
        if self.looping != looping {
            self.looping = looping
            tl = 0
        }
        if date == lastDate { return tl }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0
        lastDate = date
        // A slow frame pauses the clock instead of skipping ahead.
        let dt = min(max(raw, 0), 1.0 / 30)
        if user {
            if let target = springTo {
                // Settles on a stage (about 500 ms, damping ratio 0.85), carrying the finger's speed.
                let k = 160.0
                let c = 2 * 0.85 * k.squareRoot()
                springV += (-k * (tl - target) - c * springV) * dt
                tl += springV * dt
                if abs(tl - target) < 0.0005 && abs(springV) < 0.01 {
                    tl = target
                    springTo = nil
                }
            }
            if let from = returnFrom {
                // Back to raw dough (the scrub runs backwards), then the loop restarts from t = 0.
                returnT += dt
                let x = min(1, returnT / 0.9)
                tl = from + (M.proofStart - from) * Self.back.value(at: x)
                if x >= 1 {
                    returnFrom = nil
                    tl = 0
                    user = false
                }
            } else if !grabbing {
                idle += dt
                if idle > Self.idleDelay {
                    springTo = nil
                    returnFrom = tl
                    returnT = 0
                }
            }
            return tl
        }
        if looping { tl = (tl + dt).truncatingRemainder(dividingBy: M.loop) }
        return tl
    }

    private func stopIndex(_ t: Double) -> Int {
        var k = 0
        for (i, s) in M.stops.enumerated() where t >= s - 0.01 { k = i }
        return k
    }

    func begin() {
        dragActive = true
        if returnFrom != nil { return }
        user = true
        grabbing = true
        idle = 0
        springTo = nil
        var t = tl
        if t > M.bakeEnd + 0.6 || t < M.proofStart { t = M.proofStart }
        if t > M.bakeEnd { t = M.bakeEnd }
        tl = t
        startTl = t
        lastStop = stopIndex(t)
    }

    func update(_ v: DragGesture.Value) {
        guard grabbing else { return }
        idle = 0
        let span = M.bakeEnd - M.proofStart
        var t = startTl - (v.translation.height / Self.scrub) * span
        // Rubber band past raw dough and past the baked loaf.
        if t < M.proofStart { t = M.proofStart - (M.proofStart - t) / (1 + (M.proofStart - t) * 4) }
        if t > M.bakeEnd { t = M.bakeEnd + (t - M.bakeEnd) / (1 + (t - M.bakeEnd) * 4) }
        tl = min(t, M.bakeEnd + 0.25)
        let k = stopIndex(t)
        if k != lastStop {
            lastStop = k
            select.selectionChanged()
        }
    }

    func end(_ v: DragGesture.Value?) {
        dragActive = false
        guard grabbing else { return }
        grabbing = false
        idle = 0
        let span = M.bakeEnd - M.proofStart
        let vel = v.map { (-$0.velocity.height / Self.scrub) * span } ?? 0
        let aim = tl + vel * 0.18
        var best = M.stops[0]
        for s in M.stops.dropFirst() where abs(s - aim) < abs(best - aim) { best = s }
        springTo = best
        springV = vel
    }
}
