import SwiftUI
import UIKit

/*
 Jigsaw Steps Welcome — onboarding for a family-photo app ("Piecemeal"). A beach photo is cut
 into twelve jigsaw pieces scattered under the board; each onboarding step snaps four of them
 home (corners, outer middles, middle row), the cut lines fade, and the photo comes alive with a
 slow push-in. The text and dots above the button roll to each step. Timeline in
 JigsawStepsWelcomeMotion.swift (loop = 10 s); outlines and layout in JigsawStepsWelcomeGeometry.swift.

 Per frame: one Canvas drawing each piece from its baked sprites (two ambient shadow softnesses
 cross-faded by lift, a contact shadow, the art) under a transform, plus the brown edge fill and
 the groove / snap-light strokes as paths; the whole photo and its gleam; the text roll.

 Touch: pick up any piece (it lifts, its shadow grows, its tilt straightens on a spring) and drag
 it; let go within 35 % of a cell of its home and it snaps in (spring ω 13 rad/s, damping ratio
 0.7, a light haptic, a light running round its edges); anywhere else it stays where it was
 dropped. The text follows how many pieces are home; with all twelve home the cut lines fade.
 2 s after the last touch the pieces spring back to the pile and the loop restarts.
 Reduced motion: the finished photo and the last step, still; pieces can still be dragged.
 */
struct JigsawStepsWelcome: View {
    /// Plays the three onboarding steps and scatters the pieces again, on a loop.
    var autoPlay = false

    static let loop = JigsawStepsWelcomeMotion.loop
    typealias M = JigsawStepsWelcomeMotion
    typealias C = JigsawStepsWelcomePalette
    typealias Lay = JigsawStepsWelcomeLayout
    typealias Art = JigsawStepsWelcomeArt

    static let texts: [(title: String, body: String)] = [
        ("Your family, in pieces", "Photos scattered across phones, drawers and old albums."),
        ("Gather every photo", "Bring in your camera roll, scanned prints and shared links."),
        ("Invite the whole family", "Everyone adds the moments only they were there for."),
        ("See the whole picture", "Piecemeal puts it all together, year by year."),
    ]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var drive = JigsawStepsWelcomeDrive()
    @State private var sprites: [Art.Sprites] = []
    @State private var paths: [Path] = []
    @State private var bakedFor: Lay? = nil
    @State private var active = false
    @State private var settle: Task<Void, Never>? = nil
    @State private var pressed = false

    var body: some View {
        GeometryReader { proxy in
            let ins = proxy.safeAreaInsets
            let L = Lay(W: proxy.size.width + ins.leading + ins.trailing, H: proxy.size.height + ins.top + ins.bottom,
                        top: ins.top, bottom: ins.bottom)
            let looping = autoPlay && !reduceMotion && !sprites.isEmpty
            ZStack(alignment: .topLeading) {
                JigsawStepsWelcomePaper(L: L).equatable()
                cta(L)
                TimelineView(.animation(paused: !(looping || active))) { timeline in
                    let f = drive.frame(at: timeline.date, L: L, auto: looping, reduced: reduceMotion)
                    moving(L, f)
                }
                Color.clear
                    .frame(width: L.W, height: L.H)
                    .contentShape(Rectangle())
                    .gesture(pan(L))
                    .accessibilityElement()
                    .accessibilityLabel("A beach photo cut into jigsaw pieces. Drag a piece to its place on the board.")
                topBar(L)
                ctaHit(L)
            }
            .frame(width: L.W, height: L.H, alignment: .topLeading)
            .ignoresSafeArea()
            .task(id: L) { await bake(L) }
        }
        .background(C.bg.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onDisappear { settle?.cancel() }
    }

    /// Bakes the pieces off the main thread; the loop starts at t = 0 on the first frame that has them.
    private func bake(_ L: Lay) async {
        guard L.W > 0, bakedFor != L else { return }
        let scale = min(3, displayScale)
        let baked = await Task.detached(priority: .userInitiated) { Art.bake(L, scale: scale) }.value
        paths = Art.paths(L)
        sprites = baked
        bakedFor = L
        drive.begin(at: .now, paths: paths)
    }

    // MARK: Drawing

    private func moving(_ L: Lay, _ f: JigsawStepsWelcomeDrive.Frame) -> some View {
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                guard sprites.count == Lay.count, paths.count == Lay.count, f.pieces.count == Lay.count else { return }
                for i in 0..<Lay.count where !f.slots.contains(i) {
                    drawPiece(ctx, i, f.pieces[i], L)
                }
                // The whole photo over the finished board, with a slow push-in and a warm gleam.
                if f.whole.0 > 0.001 {
                    let B = L.board
                    ctx.drawLayer { l in
                        l.opacity = f.whole.0
                        l.clip(to: Path(roundedRect: B, cornerRadius: 4))
                        var k = l
                        let o = CGPoint(x: B.minX + B.width * 0.62, y: B.minY + B.height * 0.45)
                        k.translateBy(x: o.x, y: o.y)
                        k.scaleBy(x: f.whole.1, y: f.whole.1)
                        k.translateBy(x: -o.x, y: -o.y)
                        k.draw(Image(uiImage: Art.photo), in: B)
                        let g = sin(.pi * f.whole.2)
                        if g > 0.001 {
                            var s = l
                            s.opacity = g
                            s.blendMode = .screen
                            s.translateBy(x: (f.whole.2 * 1.6 - 0.3) * B.width, y: 0)
                            s.fill(Path(CGRect(x: B.minX - B.width * 0.3, y: B.minY, width: B.width * 0.6, height: B.height)), with: .linearGradient(
                                Gradient(colors: [C.rgba(255, 220, 170, 0), C.rgba(255, 220, 170, 0.28), C.rgba(255, 220, 170, 0)]),
                                startPoint: CGPoint(x: B.minX - B.width * 0.3, y: B.minY),
                                endPoint: CGPoint(x: B.minX + B.width * 0.3, y: B.minY + B.height * 0.4)))
                        }
                    }
                }
                // Pieces in flight, above the pile and the board.
                for i in f.slots where i >= 0 {
                    drawPiece(ctx, i, f.pieces[i], L)
                }
            }
            .frame(width: L.W, height: L.H)

            textBlock(L, roll: f.roll)
            ctaLabel(L, roll: f.roll)
        }
        .frame(width: L.W, height: L.H, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    /// A piece: soft ambient and tight contact shadows that grow and soften with lift, the
    /// cardboard's edge (1.6 pt of brown under it while off the board), the photo with its bevel,
    /// the groove of the cut while it sits in the board, and a warm light running round it as it snaps in.
    private func drawPiece(_ ctx: GraphicsContext, _ i: Int, _ p: M.Piece, _ L: Lay) {
        let s = sprites[i]
        let h = L.home[i]
        var c = ctx
        c.translateBy(x: p.x, y: p.y)
        c.rotate(by: .radians(p.rot))
        c.scaleBy(x: p.scale, y: p.scale)
        c.translateBy(x: -h.x, y: -h.y)
        let off = 1 - p.onBoard
        if off > 0.001 {
            let lift = min(1, max(0, p.lift))
            var a = c
            a.translateBy(x: 0, y: 3 + 14 * p.lift)
            let amb = (0.16 + 0.16 * p.lift) * off
            if 1 - lift > 0.001 {
                a.opacity = amb * (1 - lift)
                a.draw(Image(decorative: s.soft, scale: displayScaleClamped), in: s.rect)
            }
            if lift > 0.001 {
                a.opacity = amb * lift
                a.draw(Image(decorative: s.softer, scale: displayScaleClamped), in: s.rect)
            }
            let con = 0.32 * (1 - min(1, p.lift * 1.4)) * off
            if con > 0.001 {
                var k = c
                k.translateBy(x: 0, y: 1.2)
                k.opacity = con
                k.draw(Image(decorative: s.contact, scale: displayScaleClamped), in: s.rect)
            }
            var e = c
            e.translateBy(x: 0, y: 1.6)
            e.opacity = off
            e.fill(paths[i], with: .color(C.rgb(0x8C6F55)))
        }
        c.draw(Image(decorative: s.art, scale: displayScaleClamped), in: s.rect)
        if p.groove > 0.001 || p.flash > 0.001 {
            var k = c
            k.clip(to: paths[i])
            if p.groove > 0.001 {
                k.stroke(paths[i], with: .color(C.rgba(64, 40, 22, p.groove * 0.55)), lineWidth: 1.8)
            }
            if p.flash > 0.001 {
                k.stroke(paths[i], with: .color(C.rgba(255, 226, 170, p.flash)), lineWidth: 4)
            }
        }
    }

    private var displayScaleClamped: CGFloat { min(3, displayScale) }

    private func topBar(_ L: Lay) -> some View {
        HStack {
            HStack(spacing: 8) {
                JigsawStepsWelcomeMark()
                    .fill(C.accent)
                    .frame(width: 22, height: 22)
                Text("Piecemeal")
                    .font(.system(size: 18, weight: .bold)).tracking(-0.3)
                    .foregroundStyle(C.ink)
            }
            Spacer()
            Button {} label: {
                Text("Skip")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(C.mute)
                    .padding(10)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.trailing, -10)
        }
        .frame(width: L.W - 40, height: Lay.topBar)
        .offset(x: 20, y: L.top + 8)
    }

    private func textBlock(_ L: Lay, roll: Double) -> some View {
        let row = Lay.textH
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                ForEach(0..<3, id: \.self) { k in
                    let on = M.smoothstep(Double(k) + 0.5, Double(k) + 1, roll)
                    Capsule().fill(C.accent)
                        .frame(width: 8 + 16 * on, height: 8)
                        .opacity(0.3 + 0.7 * on)
                }
            }
            .frame(height: 8)
            .padding(.bottom, 14)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(0..<Self.texts.count, id: \.self) { k in
                    VStack(alignment: .leading, spacing: 0) {
                        Text(Self.texts[k].title)
                            .font(.system(size: 26, weight: .bold)).tracking(-0.5)
                            .foregroundStyle(C.ink)
                        Text(Self.texts[k].body)
                            .font(.system(size: 15, weight: .medium))
                            .lineSpacing(3)
                            .foregroundStyle(C.mute)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 6)
                    }
                    .frame(width: L.W - 48, height: row, alignment: .topLeading)
                }
            }
            .offset(y: -roll * row)
            .frame(width: L.W - 48, height: row - 22, alignment: .topLeading)
            .clipped()
        }
        .frame(width: L.W - 48, height: row, alignment: .topLeading)
        .offset(x: 24, y: L.textY)
    }

    private func cta(_ L: Lay) -> some View {
        Capsule()
            .fill(C.accent)
            .overlay(Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.3), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
            .shadow(color: C.accent.opacity(0.28), radius: 11, y: 10)
            .shadow(color: C.rgba(120, 50, 20, 0.3), radius: 1, y: 1)
            .frame(width: L.W - 40, height: Lay.ctaH)
            .scaleEffect(pressed ? 0.97 : 1)
            .offset(x: 20, y: L.ctaY)
            .allowsHitTesting(false)
    }

    /// The button's touch area, above the pieces' drag layer (its label is drawn in the timeline).
    private func ctaHit(_ L: Lay) -> some View {
        Capsule()
            .fill(Color.clear)
            .frame(width: L.W - 40, height: Lay.ctaH)
            .contentShape(Capsule())
            .onLongPressGesture(minimumDuration: 10, pressing: { pressed = $0 }, perform: {})
            .accessibilityElement()
            .accessibilityLabel("Continue")
            .accessibilityAddTraits(.isButton)
            .offset(x: 20, y: L.ctaY)
    }

    private func ctaLabel(_ L: Lay, roll: Double) -> some View {
        VStack(spacing: 0) {
            Text("Continue").frame(height: 22)
            Text("Start your album").frame(height: 22)
        }
        .font(.system(size: 17, weight: .bold))
        .foregroundStyle(C.rgb(0xFFF8F0))
        .offset(y: -M.smoothstep(2.4, 2.9, roll) * 22)
        .frame(width: L.W - 40, height: 22, alignment: .top)
        .clipped()
        .scaleEffect(pressed ? 0.97 : 1)
        .offset(x: 20, y: L.ctaY + (Lay.ctaH - 22) / 2)
    }

    // MARK: Touch

    private func pan(_ L: Lay) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if drive.held < 0 {
                    guard !drive.ignoring else { return }
                    guard drive.grab(at: now, point: value.startLocation, L: L, auto: autoPlay && !reduceMotion && !sprites.isEmpty, reduced: reduceMotion) else { return }
                    settle?.cancel()
                    active = true
                }
                drive.move(to: value.location, at: now)
            }
            .onEnded { value in
                let had = drive.held >= 0
                drive.release(at: .now, point: value.location, velocity: value.velocity, L: L)
                guard had else { return }
                settle?.cancel()
                settle = Task { @MainActor in
                    try? await Task.sleep(for: .seconds(2 + 1.2 + 0.6))
                    if !Task.isCancelled && drive.held < 0 { active = false }
                }
            }
    }
}

/// The app's mark: a square puzzle piece with a knob on each side.
struct JigsawStepsWelcomeMark: Shape {
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 22
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * k, y: rect.minY + y * k) }
        var path = Path()
        path.move(to: p(3, 3))
        path.addLine(to: p(9.2, 3))
        path.addCurve(to: p(12.8, 3), control1: p(8.3, 0.4), control2: p(13.7, 0.4))
        path.addLine(to: p(19, 3))
        path.addLine(to: p(19, 9.2))
        path.addCurve(to: p(19, 12.8), control1: p(21.6, 8.3), control2: p(21.6, 13.7))
        path.addLine(to: p(19, 19))
        path.addLine(to: p(12.8, 19))
        path.addCurve(to: p(9.2, 19), control1: p(13.7, 21.6), control2: p(8.3, 21.6))
        path.addLine(to: p(3, 19))
        path.addLine(to: p(3, 12.8))
        path.addCurve(to: p(3, 9.2), control1: p(5.6, 13.7), control2: p(5.6, 8.3))
        path.closeSubpath()
        return path
    }
}

/// The pieces' state outside the autoPlay timeline. The finger's pieces move on springs that are
/// stepped from the timeline's date (a small physics model, as in React Native); the loop itself
/// is a pure function of t.
final class JigsawStepsWelcomeDrive {
    typealias M = JigsawStepsWelcomeMotion
    typealias Lay = JigsawStepsWelcomeLayout

    struct Frame {
        var pieces: [M.Piece]
        /// Pieces drawn above everything (in flight), −1 for an empty slot.
        var slots: [Int]
        var roll: Double
        var whole: (Double, Double, Double)
    }

    /// Per piece: position, velocity, rotation, lift, snap light, target, on board.
    struct Body {
        var x = 0.0, y = 0.0, rot = 0.0
        var vx = 0.0, vy = 0.0, vrot = 0.0
        var lift = 0.0, flash = 0.0
        var tx = 0.0, ty = 0.0, trot = 0.0
        var onBoard = 0.0
    }

    enum Mode { case auto, manual, returning(since: Date) }

    static let k = 170.0
    static let damp = 2 * 0.7 * 170.0.squareRoot()

    private(set) var held = -1
    var ignoring = false
    private var mode: Mode = .auto
    private var autoStart = Date.now
    private var lastFrame: Date? = nil
    private var lastStep: Date? = nil
    private var lastTouch = Date.distantPast
    private var bodies: [Body] = []
    private var top = -1
    private var grabDX = 0.0, grabDY = 0.0
    private var fingerX = 0.0, fingerY = 0.0
    private var roll = 0.0
    private var wholeA = 0.0
    private var last: Frame? = nil
    private var paths: [Path] = []
    private let light = UIImpactFeedbackGenerator(style: .light)

    func begin(at date: Date, paths: [Path]) {
        self.paths = paths
        autoStart = date
        lastFrame = nil
        mode = .auto
        light.prepare()
    }

    private func loopTime(_ date: Date, auto: Bool) -> Double {
        guard auto else { return 0 }
        // A stalled frame pauses the loop clock instead of skipping ahead.
        if let lf = lastFrame, date.timeIntervalSince(lf) > 0.25 {
            autoStart = autoStart.addingTimeInterval(date.timeIntervalSince(lf) - 1 / 60)
        }
        lastFrame = date
        return max(0, date.timeIntervalSince(autoStart)).truncatingRemainder(dividingBy: M.loop)
    }

    func frame(at date: Date, L: Lay, auto: Bool, reduced: Bool) -> Frame {
        let f = compute(date, L, auto, reduced)
        last = f
        return f
    }

    private func compute(_ date: Date, _ L: Lay, _ auto: Bool, _ reduced: Bool) -> Frame {
        if case .auto = mode {
            let t = reduced ? 7.2 : loopTime(date, auto: auto)
            var pieces: [M.Piece] = []
            for i in 0..<Lay.count {
                pieces.append(M.piece(i, t, home: (Double(L.home[i].x), Double(L.home[i].y)),
                                      scatter: (Double(L.scatter[i].x), Double(L.scatter[i].y), L.scatterRot[i])))
            }
            let slots = (0..<4).map { reduced ? -1 : M.slotPiece($0, t) }
            lastStep = nil
            return Frame(pieces: pieces, slots: slots, roll: M.textRoll(t), whole: M.whole(t))
        }
        // The finger's springs, stepped by the time since the last frame.
        let dt = min(0.034, max(0, date.timeIntervalSince(lastStep ?? date)))
        lastStep = date
        if case .manual = mode, held < 0, date.timeIntervalSince(lastTouch) > 2 {
            mode = .returning(since: date)
            top = -1
        }
        var returning: Date? = nil
        if case let .returning(since) = mode {
            if date.timeIntervalSince(since) > 1.2 {
                mode = .auto
                autoStart = date
                lastFrame = nil
                return compute(date, L, auto, reduced)
            }
            returning = since
        }
        var pieces: [M.Piece] = []
        var home = 0
        for i in 0..<Lay.count {
            var b = bodies[i]
            if returning != nil {
                b.tx = Double(L.scatter[i].x)
                b.ty = Double(L.scatter[i].y)
                b.trot = L.scatterRot[i]
                b.onBoard = 0
            }
            let isHeld = held == i
            if isHeld {
                b.x = fingerX - grabDX
                b.y = fingerY - grabDY
                b.vx = 0
                b.vy = 0
            } else {
                b.vx += (Self.k * (b.tx - b.x) - Self.damp * b.vx) * dt
                b.vy += (Self.k * (b.ty - b.y) - Self.damp * b.vy) * dt
                b.x += b.vx * dt
                b.y += b.vy * dt
            }
            let rotTarget = isHeld ? 0 : b.trot
            b.vrot += (Self.k * 0.6 * (rotTarget - b.rot) - Self.damp * 0.8 * b.vrot) * dt
            b.rot += b.vrot * dt
            let liftTarget: Double = isHeld || (returning.map { date.timeIntervalSince($0) < 0.6 } ?? false) ? 1 : 0
            b.lift += (liftTarget - b.lift) * min(1, dt * (returning != nil ? 4 : 12))
            b.flash = max(0, b.flash - dt / 0.35)
            if b.onBoard > 0 { home += 1 }
            bodies[i] = b
            pieces.append(M.Piece(x: b.x, y: b.y, rot: b.rot, scale: 1 + 0.07 * b.lift, lift: b.lift,
                                  flash: b.flash, groove: b.onBoard, onBoard: b.onBoard))
        }
        // With every piece home the cut lines fade (the whole photo fades in over them).
        if home == Lay.count { for i in 0..<Lay.count { pieces[i].groove = 0 } }
        let slot = top >= 0 && (held == top || bodies[top].lift > 0.02) ? top : -1
        let target: Double = home == Lay.count ? 3 : Double(home / 4)
        roll += (target - roll) * min(1, dt * 8)
        wholeA += ((home == Lay.count ? 1 : 0) - wholeA) * min(1, dt * 3)
        return Frame(pieces: pieces, slots: [slot, -1, -1, -1], roll: roll, whole: (wholeA, 1, 0))
    }

    /// Is the point inside piece i where it is drawn now?
    private func hits(_ i: Int, _ x: Double, _ y: Double, _ f: Frame, _ L: Lay) -> Bool {
        let p = f.pieces[i]
        let dx = x - p.x, dy = y - p.y
        let c = cos(-p.rot), s = sin(-p.rot)
        let lx = (dx * c - dy * s) / p.scale + Double(L.home[i].x)
        let ly = (dx * s + dy * c) / p.scale + Double(L.home[i].y)
        return paths[i].contains(CGPoint(x: lx, y: ly))
    }

    /// A finger lands; true when it is on a piece.
    func grab(at date: Date, point: CGPoint, L: Lay, auto: Bool, reduced: Bool) -> Bool {
        guard paths.count == Lay.count else { return false }
        let f = last ?? frame(at: date, L: L, auto: auto, reduced: reduced)
        let x = Double(point.x), y = Double(point.y)
        var hit = -1
        for j in f.slots where hit < 0 && j >= 0 && hits(j, x, y, f, L) { hit = j }
        if hit < 0 {
            for i in stride(from: Lay.count - 1, through: 0, by: -1) where hits(i, x, y, f, L) {
                hit = i
                break
            }
        }
        guard hit >= 0 else { ignoring = true; return false }
        switch mode {
        case .manual: break
        default:
            // Take over from wherever the pieces are drawn now.
            bodies = f.pieces.map { p in
                Body(x: p.x, y: p.y, rot: p.rot, lift: p.lift, tx: p.x, ty: p.y, trot: p.rot, onBoard: p.onBoard)
            }
            roll = f.roll
            wholeA = f.whole.0
            lastStep = date
            mode = .manual
        }
        held = hit
        top = hit
        grabDX = x - f.pieces[hit].x
        grabDY = y - f.pieces[hit].y
        fingerX = x
        fingerY = y
        lastTouch = date
        bodies[hit].onBoard = 0
        return true
    }

    func move(to point: CGPoint, at date: Date) {
        guard held >= 0 else { return }
        fingerX = Double(point.x)
        fingerY = Double(point.y)
        lastTouch = date
    }

    func release(at date: Date, point: CGPoint, velocity: CGSize, L: Lay) {
        ignoring = false
        let i = held
        guard i >= 0 else { return }
        held = -1
        lastTouch = date
        let x = Double(point.x) - grabDX, y = Double(point.y) - grabDY
        var b = bodies[i]
        b.vx = Double(velocity.width) * 0.25
        b.vy = Double(velocity.height) * 0.25
        if hypot(x - Double(L.home[i].x), y - Double(L.home[i].y)) < Double(L.cellW) * 0.35 {
            b.tx = Double(L.home[i].x)
            b.ty = Double(L.home[i].y)
            b.trot = 0
            b.onBoard = 1
            b.flash = 1
            light.impactOccurred()
        } else {
            b.tx = x
            b.ty = y
            b.trot = b.rot
        }
        bodies[i] = b
    }
}
