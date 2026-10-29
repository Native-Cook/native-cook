import SwiftUI
import UIKit

/// The launch screen of an invented burger kitchen (Copper Bun): a deep red poster with a giant
/// condensed "MELTY" above and below a double cheeseburger. The cheese is drawn in code as molten
/// goo: a drape along each slice, drips that stretch under gravity, neck down and let go of a drop
/// that falls into a puddle at the base, glossy with a warm gradient, a specular streak, a darker
/// rim and a soft shadow on the patty. The giant word stacks up behind once, steam rises.
///
/// Loop (8 s): rest → the melt builds (1.6–4.3 s), two drops fall (4.15, 4.6 s) → the drips set
/// back to rest (5–7 s). Hold to melt more (drops keep falling), release and it slowly sets; tap to
/// drop the burger in again. Reduced motion: the rest poster, still.
struct CheeseDripBurger: View {
    static let loop = CheeseDripBurgerMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = CheeseDripBurgerTouch()
    @State private var awake = false
    @State private var pressStart: Date?

    private typealias M = CheeseDripBurgerMotion
    private typealias Art = CheeseDripBurgerArt

    static let word = "MELTY"

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            let safeBottom = outer.safeAreaInsets.bottom
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let layout = Art.Layout(width: 393, height: Double(geometry.size.height / k),
                                        top: Double(safeTop / k), bottom: Double(safeBottom / k))
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active || !(autoPlay || awake))) { timeline in
                    let clock = max(0, timeline.date.timeIntervalSince(epoch))
                    screen(clock: clock, k: k, layout: layout)
                }
            }
            .ignoresSafeArea()
        }
        .background(Art.color(Art.red).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: awake) {
            guard awake, !autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                if !touch.touching, Date().timeIntervalSince(epoch) > touch.restsAfter {
                    awake = false
                    return
                }
            }
        }
    }

    // MARK: Screen

    @ViewBuilder
    private func screen(clock: Double, k: CGFloat, layout l: Art.Layout) -> some View {
        let still = reducedMotion
        let _ = still ? () : schedule(clock)
        let t: Double? = still ? 0 : touch.loopTime(clock, autoPlay: autoPlay)
        let ambient = still ? 0 : M.mod(clock, M.loop)
        let melts = still ? M.layers.map { $0.drips.map { _ in 0.0 } } : touch.melts(clock, t: t)
        let events = still ? [] : touch.activeEvents(clock, t: t)
        let tau = still ? -1 : clock - touch.dropInClock
        let lit = (0..<M.rows).map { still ? 0 : touch.rowLit(clock, t: t, $0) }
        let slide = (0..<M.rows).map { i in t.map { M.rowSlide($0, i) } ?? 0 }
        let frame = M.frame(melts: melts, events: events, ambient: ambient, jiggle: M.jiggle(tau))
        let drop = M.dropIn(tau)

        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                Art.drawBackground(context, l)
                Art.drawRows(context, l, lit: lit, slide: slide, word: Self.word)
                Art.drawFloorShadow(context, l, lift: -drop.y)
                drawBurger(context, l, frame: frame, drop: drop, ambient: ambient)
            }
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel("A double cheeseburger with molten cheese dripping down every layer")

            ZStack(alignment: .topLeading) {
                Color.clear
                    .frame(width: 393, height: l.height)
                    .contentShape(Rectangle())
                    .gesture(press())
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel("Melt the cheese")
                    .accessibilityHint("Hold to melt more, tap to drop the burger in again")
                    .accessibilityAction { dropIn() }
                texts(l)
            }
            .frame(width: 393, height: l.height, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)
        }
    }

    private func drawBurger(_ ctx: GraphicsContext, _ l: Art.Layout, frame: M.Frame, drop: (y: Double, sx: Double, sy: Double), ambient: Double) {
        var b = ctx
        b.translateBy(x: 196.5, y: l.baseY + drop.y)
        b.scaleBy(x: drop.sx, y: drop.sy)
        b.translateBy(x: -196.5, y: -l.baseY)
        b.translateBy(x: l.imageOrigin.x, y: l.imageOrigin.y)
        b.scaleBy(x: Art.burgerScale, y: Art.burgerScale)
        b.draw(b.resolve(Image(uiImage: Art.burger)), in: CGRect(x: 0, y: 0, width: M.imageSize, height: M.imageSize))
        Art.drawCheese(b, frame)
        Art.drawSteam(b, ambient: ambient, strength: 1)
    }

    // MARK: Copy

    private func texts(_ l: Art.Layout) -> some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: 8) {
                Canvas { context, _ in Art.drawMark(context, tint: Art.color(Art.sand)) }
                    .frame(width: 22, height: 15)
                Text("COPPER BUN")
                    .font(Art.anton(15))
                    .tracking(2.4)
                    .foregroundStyle(Art.color(Art.sand))
            }
            .fixedSize()
            .position(x: 196.5, y: l.brandY)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Copper Bun")

            word.position(x: 196.5, y: l.topWordY)
                .accessibilityAddTraits(.isHeader)
            word.position(x: 196.5, y: l.bottomWordY)
                .accessibilityHidden(true)

            Text("BURGER")
                .font(Art.anton(22))
                .tracking(9)
                .foregroundStyle(Art.color(Art.sand))
                .fixedSize()
                .position(x: 196.5 + 4.5, y: l.labelY)

            Text("Double Melt  ·  2 patties, 2 slices  ·  $8.90")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Art.color(Art.sand, 0.85))
                .fixedSize()
                .position(x: 196.5, y: l.productY)
        }
        .allowsHitTesting(false)
        .frame(width: 393, height: l.height, alignment: .topLeading)
    }

    private var word: some View {
        Text(Self.word)
            .font(Art.anton(Art.wordSize))
            .foregroundStyle(Art.color(Art.cream))
            .fixedSize()
    }

    // MARK: Touch

    /// Lets the next drop go while the finger holds; a light tap when it lets go.
    private func schedule(_ clock: Double) {
        guard touch.update(clock) else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + M.pinchLead) {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    private func dropIn() {
        if reducedMotion { return }
        let clock = Date().timeIntervalSince(epoch)
        touch.dropIn(clock: clock)
        if !autoPlay { awake = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + M.fallTime) {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    private func press() -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if reducedMotion { return }
                if pressStart == nil {
                    pressStart = Date()
                    touch.begin(clock: Date().timeIntervalSince(epoch), autoPlay: autoPlay)
                    if !awake { awake = true }
                }
            }
            .onEnded { value in
                guard let start = pressStart else { return }
                pressStart = nil
                let clock = Date().timeIntervalSince(epoch)
                touch.end(clock: clock)
                let moved = hypot(value.translation.width, value.translation.height)
                if Date().timeIntervalSince(start) < 0.25, moved < 12 { dropIn() }
            }
    }
}
