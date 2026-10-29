import SwiftUI
import UIKit

/// An invented Italian deli's category screen on cream, after the pin's layout: two rows of six
/// small food silhouettes as tabs and one large silhouette in the middle with the category's name
/// and item count. When the tab changes, the big silhouette crumbles into 1,400 flour grains that
/// drain from the bottom, swirl, and rain into the next shape bottom-first while the tab and its
/// indicator move along the strip.
///
/// Loop (9.6 s): Balsamico rests (0.9 s) → sifts into Flour & Pizza (1.5 s) → rests → Pasta &
/// Risotto (bottom row) → Sweets & Coffee → back to Balsamico. Tap a tab to sift to it; drag across
/// the big shape to stir the grains; the loop resumes 2 s after the last touch. Reduced motion:
/// Balsamico, still; a tab tap cross-fades.
struct DeliGrainTabs: View {
    static let loop = DeliGrainTabsMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = DeliGrainTabsTouch()
    @State private var scratch = DeliGrainTabsScratch()
    @State private var awake = false
    @State private var stirring = false
    @State private var selected = 0

    private typealias M = DeliGrainTabsMotion
    private typealias Art = DeliGrainTabsArt

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            let safeBottom = outer.safeAreaInsets.bottom
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let layout = Art.Layout(height: Double(geometry.size.height / k), top: Double(safeTop / k),
                                        bottom: Double(safeBottom / k))
                ZStack(alignment: .topLeading) {
                    Canvas { context, size in Art.drawPaper(context, size) }
                        .allowsHitTesting(false)
                    TimelineView(.animation(paused: scenePhase != .active || !((autoPlay && !reducedMotion) || awake))) { timeline in
                        let clock = max(0, timeline.date.timeIntervalSince(epoch))
                        Canvas { context, _ in
                            context.scaleBy(x: k, y: k)
                            draw(context, layout, clock: clock)
                        }
                    }
                    .allowsHitTesting(false)
                    controls(layout)
                        .frame(width: 393, height: layout.height, alignment: .topLeading)
                        .scaleEffect(k, anchor: .topLeading)
                }
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(Art.color(Art.cream).ignoresSafeArea())
        .preferredColorScheme(.light)
        .task(id: awake) {
            guard awake else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching, clock > touch.restsAfter + 0.1, !(autoPlay && !reducedMotion) {
                    awake = false
                    return
                }
                if autoPlay, !reducedMotion, !touch.touching, clock > touch.restsAfter + M.resumeAfter + 0.1 {
                    awake = false
                    return
                }
            }
        }
    }

    // MARK: Drawing

    private func draw(_ context: GraphicsContext, _ l: Art.Layout, clock: Double) {
        let still = reducedMotion
        let f = touch.frame(clock, autoPlay: autoPlay && !still)
        drawTabs(context, l, f, still: still)
        if still {
            let q = M.easeOutCubic(M.seg(f.p, 0, 0.3))
            if q < 1 { drawRest(context, l, shape: f.fromTab, alpha: 1 - q) }
            drawRest(context, l, shape: f.to, alpha: q)
        } else {
            drawHero(context, l, f)
        }
        drawLabels(context, l, f, still: still)
    }

    private static func heroTransform(_ l: Art.Layout) -> CGAffineTransform {
        CGAffineTransform(a: Art.heroScale, b: 0, c: 0, d: Art.heroScale,
                          tx: 196.5 - 50 * Art.heroScale, ty: l.baseline - 96 * Art.heroScale)
    }

    private func drawTabs(_ context: GraphicsContext, _ l: Art.Layout, _ f: M.Frame, still: Bool) {
        let change = f.fromTab != f.to
        let swap = still ? M.seg(f.p, 0, 0.3) : M.easeOutCubic(M.seg(f.p, 0, 0.25))
        for i in 0..<Art.categories.count {
            var amount = 0.0
            if i == f.to { amount = change ? swap : 1 }
            if change, i == f.fromTab { amount = 1 - swap }
            let pop = (!still && change && i == f.to) ? 1 + 0.14 * sin(Double.pi * M.seg(f.p, 0, 0.35)) : 1
            let c = l.tab(i)
            let s = Art.iconScale * pop
            let t = CGAffineTransform(a: s, b: 0, c: 0, d: s, tx: c.x - 50 * s, ty: c.y - 50 * s)
            context.fill(Art.paths[i].applying(t), with: .color(Art.mix(Art.idle, Art.ink, amount)))
        }
        // The indicator: slides within a row, hands over between rows.
        let a = l.tab(f.fromTab)
        let b = l.tab(f.to)
        func dot(_ x: Double, _ y: Double, _ scale: Double) {
            guard scale > 0.01 else { return }
            let r = 2.0 * scale
            context.fill(Path(ellipseIn: CGRect(x: x - r, y: y + 25 - r, width: 2 * r, height: 2 * r)),
                         with: .color(Art.color(Art.ink)))
        }
        if !change {
            dot(b.x, b.y, 1)
        } else if still {
            dot(a.x, a.y, 1 - swap)
            dot(b.x, b.y, swap)
        } else if a.y == b.y {
            let e = M.easeInOutCubic(M.seg(f.p, 0, 0.45))
            dot(a.x + (b.x - a.x) * e, b.y, 1)
        } else {
            dot(a.x, a.y, 1 - M.easeInCubic(M.seg(f.p, 0, 0.22)))
            dot(b.x, b.y, M.easeOutCubic(M.seg(f.p, 0.22, 0.45)))
        }
    }

    /// A silhouette at rest (reduced motion): its fill and its grains.
    private func drawRest(_ context: GraphicsContext, _ l: Art.Layout, shape: Int, alpha: Double) {
        guard alpha > 0.001 else { return }
        var c = context
        c.opacity = alpha
        c.fill(Art.paths[shape].applying(Self.heroTransform(l)), with: .color(Art.color(Art.ink)))
        let g = Art.grains(shape)
        var dots = Path()
        for i in 0..<Art.grainCount {
            let p = l.hero(g.x[i], g.y[i])
            dots.addEllipse(in: CGRect(x: p.x - 1.9, y: p.y - 1.9, width: 3.8, height: 3.8))
        }
        c.fill(dots, with: .color(Art.color(Art.ink)))
    }

    private func drawHero(_ context: GraphicsContext, _ l: Art.Layout, _ f: M.Frame) {
        let ink = Art.color(Art.ink)
        let transform = Self.heroTransform(l)
        let calm = 1 - M.stirEnergy(f.stirs, clock: f.clock)
        // The solid fill: drains bottom-up from the old shape, fills bottom-up into the new one.
        if calm > 0.01 {
            var c = context
            c.opacity = calm
            if f.resting {
                c.fill(Art.paths[f.to].applying(transform), with: .color(ink))
            } else {
                if f.from >= 0 {
                    let g = Art.grains(f.from)
                    let r = 1 - (f.p - 0.05) / 0.36 - 0.03
                    if r > 0 {
                        let y = l.hero(0, g.top + r * (g.bottom - g.top)).y
                        var clip = c
                        clip.clip(to: Path(CGRect(x: 0, y: -2000, width: 393, height: y + 2000)))
                        clip.fill(Art.paths[f.from].applying(transform), with: .color(ink))
                    }
                }
                let g = Art.grains(f.to)
                let r = 1 - (f.p - 1.04) / 0.40 + 0.08
                if r < 1 {
                    let y = l.hero(0, g.top + max(r, -1) * (g.bottom - g.top)).y
                    var clip = c
                    clip.clip(to: Path(CGRect(x: 0, y: y, width: 393, height: 4000)))
                    clip.fill(Art.paths[f.to].applying(transform), with: .color(ink))
                }
            }
        }
        // The grains.
        M.positions(f, l, xs: &scratch.xs, ys: &scratch.ys, fly: &scratch.fly)
        var dots = Path()
        for i in 0..<Art.grainCount {
            let r = 1.9 * (1 - 0.3 * scratch.fly[i])
            dots.addEllipse(in: CGRect(x: scratch.xs[i] - r, y: scratch.ys[i] - r, width: 2 * r, height: 2 * r))
        }
        context.fill(dots, with: .color(ink))
    }

    private func drawLabels(_ context: GraphicsContext, _ l: Art.Layout, _ f: M.Frame, still: Bool) {
        func label(_ i: Int, alpha: Double, dy: Double) {
            guard alpha > 0.001 else { return }
            var c = context
            c.opacity = alpha
            let cat = Art.categories[i]
            c.draw(Text(cat.name).font(.system(size: 17, weight: .semibold)).tracking(-0.2)
                .foregroundStyle(Art.color(Art.ink)), at: CGPoint(x: 196.5, y: l.nameY + dy), anchor: .center)
            c.draw(Text("\(cat.count) products").font(.system(size: 13))
                .foregroundStyle(Art.color(Art.muted)), at: CGPoint(x: 196.5, y: l.countY + dy), anchor: .center)
        }
        if f.fromTab == f.to {
            label(f.to, alpha: 1, dy: 0)
        } else if still {
            let q = M.seg(f.p, 0, 0.3)
            label(f.fromTab, alpha: 1 - q, dy: 0)
            label(f.to, alpha: q, dy: 0)
        } else {
            let out = M.easeInCubic(M.seg(f.p, 0, 0.3))
            let into = M.easeOutCubic(M.seg(f.p, 1.05, 1.4))
            label(f.fromTab, alpha: 1 - out, dy: 6 * out)
            label(f.to, alpha: into, dy: 6 * (1 - into))
        }
    }

    // MARK: Touch

    private func controls(_ l: Art.Layout) -> some View {
        ZStack(alignment: .topLeading) {
            Color.clear
                .frame(width: 393, height: max(0, l.bottomRow - l.topRow - 80))
                .contentShape(Rectangle())
                .gesture(stir())
                .offset(y: l.topRow + 40)
                .accessibilityHidden(true)
            ForEach(0..<Art.categories.count, id: \.self) { i in
                let c = l.tab(i)
                Color.clear
                    .frame(width: 54, height: 60)
                    .contentShape(Rectangle())
                    .onTapGesture { select(i, l) }
                    .accessibilityElement()
                    .accessibilityLabel("\(Art.categories[i].name), \(Art.categories[i].count) products")
                    .accessibilityAddTraits(i == selected ? [.isButton, .isSelected] : .isButton)
                    .offset(x: c.x - 27, y: c.y - 26)
            }
        }
        .coordinateSpace(name: "DeliGrainTabs")
    }

    private func select(_ i: Int, _ l: Art.Layout) {
        let clock = Date().timeIntervalSince(epoch)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        selected = i
        touch.tap(i, clock: clock, autoPlay: autoPlay && !reducedMotion, still: reducedMotion, layout: l)
        if !awake { awake = true }
    }

    private func stir() -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("DeliGrainTabs"))
            .onChanged { value in
                if reducedMotion { return }
                let clock = Date().timeIntervalSince(epoch)
                let x = Double(value.location.x)
                let y = Double(value.location.y)
                if !stirring {
                    stirring = true
                    touch.begin(x, y, clock: clock, autoPlay: autoPlay)
                    if !awake { awake = true }
                }
                touch.move(x, y, clock: clock)
            }
            .onEnded { _ in
                guard stirring else { return }
                stirring = false
                touch.end(clock: Date().timeIntervalSince(epoch))
            }
    }
}

/// Reused arrays for the grains' positions, so a frame allocates nothing.
final class DeliGrainTabsScratch {
    var xs: [Double] = []
    var ys: [Double] = []
    var fly: [Double] = []
}
