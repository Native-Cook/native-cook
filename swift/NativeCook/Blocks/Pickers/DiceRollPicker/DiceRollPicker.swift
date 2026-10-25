import SwiftUI
import UIKit

/*
 Dice Roll Picker — a board-game night screen ("Game Table — Who goes first?"). Two ivory dice,
 drawn in real 3D (projected rounded cubes, lit faces, concave pips, shadows that grow and soften
 with height) sit on green felt in a walnut tray. Flick on the table to throw: the dice tumble,
 hop, bounce off the far rim and settle on their faces; their sum picks a player, whose avatar pops
 and wears a "Starts" tag while the line rolls to "11 · Priya starts". Push a die with a finger to
 roll it over by hand; let go and it settles flat on the nearest face.

 One Canvas: a baked room and tray (with the players' names), then per frame the shadows, the dice
 (far one first), the avatars from baked sprites and the result line. 3D math and the closed-form
 throw: DiceRollPickerMath.swift; timeline: DiceRollPickerMotion.swift. Touch throws use the same
 closed-form throw with seeded faces.

 2 s after the last touch (once the dice are still) they slide home to 3 and 4 (1.6 s) and the
 loop restarts. Reduced motion: no loop; a throw places the dice at once and the result fades in.
 */
struct DiceRollPicker: View {
    /// Picks the dice up, throws them (5 and 6, Priya starts) and slides them home, on an 8 s loop.
    var autoPlay = false

    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = DiceRollPickerEngine()
    @State private var art: DiceRollPickerImages?
    @State private var awake = true

    private typealias P = DiceRollPickerPaint

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let g = DiceRollPickerGeo(w: size.width, h: size.height, top: insets.top, bottom: insets.bottom)
            stage(g)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
                .task(id: g) { await bake(g) }
        }
        .background(P.color(0x1A1512).ignoresSafeArea())
    }

    private func bake(_ g: DiceRollPickerGeo) async {
        guard g.w > 0, g.h > 0, art?.geo != g else { return }
        let scale = min(displayScale, 3)
        art = await Task.detached(priority: .userInitiated) {
            DiceRollPickerImages(
                geo: g,
                room: DiceRollPickerArt.room(g, scale: scale),
                sprites: (0..<DiceRollPickerArt.players.count).map { DiceRollPickerArt.avatar($0, r: g.avatarR, scale: scale) }
            )
        }.value
    }

    @ViewBuilder
    private func stage(_ g: DiceRollPickerGeo) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        ZStack(alignment: .topLeading) {
            P.color(0x1A1512)
            if let art, art.geo == g {
                TimelineView(.animation(paused: !auto && !awake)) { timeline in
                    Canvas { ctx, _ in
                        let f = engine.step(timeline.date, auto: auto, reduced: reduced)
                        if engine.settled && !auto {
                            DispatchQueue.main.async { if engine.settled { awake = false } }
                        }
                        ctx.draw(Image(uiImage: art.room), in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
                        DiceRollPickerFrame.shadow(ctx, g, f.a)
                        DiceRollPickerFrame.shadow(ctx, g, f.b)
                        // The die farther away (larger y) first.
                        if f.a.y > f.b.y {
                            DiceRollPickerFrame.die(ctx, g, f.a)
                            DiceRollPickerFrame.die(ctx, g, f.b)
                        } else {
                            DiceRollPickerFrame.die(ctx, g, f.b)
                            DiceRollPickerFrame.die(ctx, g, f.a)
                        }
                        DiceRollPickerFrame.avatars(ctx, g, sprites: art.sprites, pop: f.pop, chosen: f.chosen)
                        DiceRollPickerFrame.result(ctx, g, "\(f.sum) · \(DiceRollPickerArt.players[f.chosen].name) starts", k: f.k)
                    }
                }
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            engine.drag(v.location, g: g, reduced: reduced)
                        }
                        .onEnded { v in
                            awake = true
                            engine.release(velocity: v.velocity, reduced: reduced)
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Two dice on a felt table. Flick to throw them, or push a die to turn it by hand.")
                .accessibilityAddTraits(.allowsDirectInteraction)

                header(g)
                cta(g, reduced: reduced)
            }
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    private func header(_ g: DiceRollPickerGeo) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("GAME TABLE · THURSDAY")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.8)
                .foregroundStyle(P.color(0xF3EADB, 0.5))
            Text("Who goes first?")
                .font(.system(size: 30, weight: .bold))
                .kerning(-0.4)
                .foregroundStyle(P.color(0xF7EEDF))
            Text("Harbor Lights · round 2 of 3")
                .font(.system(size: 15))
                .foregroundStyle(P.color(0xF3EADB, 0.6))
        }
        .frame(width: g.w - 48, alignment: .leading)
        .offset(x: 24, y: g.top + 16)
        .allowsHitTesting(false)
    }

    private func cta(_ g: DiceRollPickerGeo, reduced: Bool) -> some View {
        let ink = P.color(0x3A2610)
        return Button {
            engine.roll(reduced: reduced)
            awake = true
        } label: {
            HStack(spacing: 10) {
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .strokeBorder(ink, lineWidth: 1.5)
                    ForEach(0..<3, id: \.self) { i in
                        Circle().fill(ink).frame(width: 3, height: 3)
                            .offset(x: 4 + 4.5 * CGFloat(i), y: 4 + 4.5 * CGFloat(i))
                    }
                }
                .frame(width: 20, height: 20, alignment: .topLeading)
                Text("Roll the dice")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(ink)
            }
            .frame(width: g.w - 32, height: 56)
            .background(Capsule().fill(P.color(0xF2C46B)))
            .shadow(color: P.color(0x000000, 0.35), radius: 1, y: 1)
            .shadow(color: P.color(0xF2C46B, 0.22), radius: 14, y: 10)
            .contentShape(Capsule())
        }
        .buttonStyle(DiceRollPickerPress())
        .offset(x: 16, y: g.ctaTop)
    }
}

private struct DiceRollPickerPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

struct DiceRollPickerImages {
    let geo: DiceRollPickerGeo
    let room: UIImage
    let sprites: [UIImage]
}

/// What one frame shows.
struct DiceRollPickerShot {
    var a: DiceRollPickerPose
    var b: DiceRollPickerPose
    var k: Double
    var pop: Double
    var chosen: Int
    var sum: Int
}

/// The clock, the dice's motions and the finger, stepped once a frame.
final class DiceRollPickerEngine {
    private typealias D = DiceRollPickerMath
    private typealias A = DiceRollPickerMotion

    private static let idle = 2.0
    private static let slide = 1.6
    private static let flick = 650.0

    /// A die's motion: still at a pose, a throw, or a slide from one pose to another.
    private enum Die {
        case still(DiceRollPickerPose)
        case thrown(t0: Double, th: DiceRollPickerThrow)
        case slide(t0: Double, dur: Double, m0: DiceRollPickerMat, m1: DiceRollPickerMat, p0: (Double, Double), p1: (Double, Double))

        /// Where the die is now, and whether its motion is over.
        func eval(_ now: Double) -> (DiceRollPickerPose, Bool) {
            switch self {
            case let .still(p):
                return (p, true)
            case let .thrown(t0, th):
                let s = now - t0
                return (DiceRollPickerMath.throwPose(th, DiceRollPickerMath.clamp(s, 0, DiceRollPickerMath.throwEnd)), s >= DiceRollPickerMath.throwEnd)
            case let .slide(t0, dur, m0, m1, p0, p1):
                let u = (now - t0) / dur
                return (DiceRollPickerMath.slidePose(m0, m1, p0, p1, u), u >= 1)
            }
        }
    }

    /// 0 loop / rest, 1 a finger is in charge, 2 sliding home.
    private var mode = 0
    private var loopT = -1.0
    private var last: Double?
    private var wasAuto: Bool?
    private var lastTouch = 0.0
    private var touchStarted = false
    private var ignored = false
    private var dragging = -1
    private var dragPrev = (0.0, 0.0)
    private var dice: [Die] = [.still(A.restPose(0)), .still(A.restPose(1))]
    /// The result line's roll, the pop, the chosen player, the sum, and when the dice came to rest.
    private var resK = 0.0
    private var resPop = 0.0
    private var resChosen = 0
    private var resSum = 7
    private var settledSince = -1.0
    private var returnAt = -1.0
    private var shot = DiceRollPickerShot(a: A.restPose(0), b: A.restPose(1), k: 0, pop: 0, chosen: 3, sum: 7)
    private var landed = false
    private(set) var settled = false
    private let snap = UISelectionFeedbackGenerator()
    private let land = UIImpactFeedbackGenerator(style: .light)

    private var now: Double { Date().timeIntervalSinceReferenceDate }

    func step(_ date: Date, auto: Bool, reduced: Bool) -> DiceRollPickerShot {
        let t1 = date.timeIntervalSinceReferenceDate
        let raw = last.map { t1 - $0 } ?? 0
        last = t1
        let dt = min(max(raw, 0), 0.05)
        if wasAuto != auto {
            wasAuto = auto
            mode = 0
            loopT = auto ? 0 : -1
        }
        settled = false
        if mode == 0 && loopT >= 0 {
            // A frame that stalls pauses the loop instead of skipping ahead.
            if raw > 0 && raw <= 0.25 { loopT = (loopT + raw).truncatingRemainder(dividingBy: A.loop) }
            let t = loopT
            let r = A.autoResult(t)
            shot = DiceRollPickerShot(a: A.autoPose(0, t), b: A.autoPose(1, t), k: r.0, pop: r.1, chosen: 5, sum: A.autoSum)
            return shot
        }
        let (a, doneA) = dice[0].eval(t1)
        let (b, doneB) = dice[1].eval(t1)
        let atRest = mode != 2 && dragging < 0 && doneA && doneB
        if atRest {
            if settledSince < 0 {
                settledSince = t1
                resSum = D.topFace(a.m) + D.topFace(b.m)
                resChosen = DiceRollPickerArt.playerFor(resSum)
            }
            resPop = reduced ? 1 : A.springIn(t1 - settledSince)
        } else {
            settledSince = -1
            resPop *= exp(-dt * 8)
        }
        resK += ((atRest ? 1 : 0) - resK) * (1 - exp(-dt * 10))
        if mode == 1 && auto && atRest && t1 - lastTouch > Self.idle {
            // Slide home from wherever the dice lie.
            mode = 2
            returnAt = t1
            let cur = [a, b]
            dice = (0..<2).map { i in
                let rest = A.restPose(i)
                return .slide(t0: t1, dur: Self.slide, m0: cur[i].m, m1: rest.m, p0: (cur[i].x, cur[i].y), p1: (rest.x, rest.y))
            }
        } else if mode == 2 && t1 > returnAt + Self.slide + 0.2 {
            mode = 0
            dice = [.still(A.restPose(0)), .still(A.restPose(1))]
            loopT = 0
        }
        // A light tap as the result lands (finger throws only).
        let isLanded = mode == 1 && resK > 0.5
        if isLanded && !landed { land.impactOccurred() }
        landed = isLanded
        settled = !auto && atRest && !touchStarted && t1 - settledSince > 1.4 && abs(resK - 1) < 0.002
        shot = DiceRollPickerShot(a: a, b: b, k: resK, pop: resPop, chosen: resChosen, sum: resSum)
        return shot
    }

    /// Hands control to the finger: freezes whatever plays into still dice.
    private func takeOver() {
        if mode != 1 { dice = [.still(shot.a), .still(shot.b)] }
        mode = 1
        loopT = -1
        lastTouch = now
    }

    private func throwAll(_ bias: Double, reduced: Bool) {
        takeOver()
        let t1 = now
        var next: [Die] = []
        for i in 0..<2 {
            let (cur, _) = dice[i].eval(t1)
            // A throw's outcome from the moment it was thrown (seeded, so it needs no random source).
            let seed = floor(t1 * 997) + Double(i * 131)
            let face = 1 + Int(floor(D.hash(seed) * 6))
            let yaw = (D.hash(seed + 1) - 0.5) * 1.4
            let fx = (i == 0 ? -40.0 : 42.0) + bias * 30 + (D.hash(seed + 2) - 0.5) * 24
            let fy = 18 + D.hash(seed + 3) * 40
            let end = D.faceUp(face, yaw)
            if reduced {
                next.append(.still(DiceRollPickerPose(m: end, x: fx, y: fy, hop: 0)))
                continue
            }
            let th = D.makeThrow(cur.m, end, (cur.x, cur.y), (fx, fy), h0: cur.hop, k: 2 + floor(D.hash(seed + 4) * 2), rimY: A.rimY)
            next.append(.thrown(t0: t1 + Double(i) * 0.05, th: th))
        }
        dice = next
    }

    func roll(reduced: Bool) {
        throwAll(0, reduced: reduced)
    }

    func drag(_ p: CGPoint, g: DiceRollPickerGeo, reduced: Bool) {
        if !touchStarted {
            touchStarted = true
            if p.y > g.resultBase - 30 {
                ignored = true
                return
            }
            ignored = false
            takeOver()
            // A die under the finger? (projected centre, generous radius)
            let poses = [shot.a, shot.b]
            var hit = -1
            var best = D.edge * 0.85
            for i in 0..<2 {
                let q = poses[i]
                let s = D.project(Double(g.cx), Double(g.cy), DiceRollPickerV3(q.x, q.y, q.hop + D.support(q.m)))
                let d = hypot(Double(p.x) - s.x, Double(p.y) - s.y)
                if d < best {
                    best = d
                    hit = i
                }
            }
            if hit >= 0 && poses[hit].hop < 4 {
                dragging = hit
                dice[hit] = .still(poses[hit])
                dragPrev = D.unproject(Double(g.cx), Double(g.cy), Double(p.x), Double(p.y), D.half)
                snap.prepare()
            }
            return
        }
        if ignored { return }
        lastTouch = now
        let i = dragging
        guard i >= 0, case let .still(pose) = dice[i] else { return }
        let w = D.unproject(Double(g.cx), Double(g.cy), Double(p.x), Double(p.y), D.half)
        let x = D.clamp(pose.x + (w.0 - dragPrev.0), D.tableX0 + D.half + 2, D.tableX1 - D.half - 2)
        let y = D.clamp(pose.y + (w.1 - dragPrev.1), D.tableY0 + D.half + 2, D.tableY1 - D.half - 2)
        let dx = x - pose.x
        let dy = y - pose.y
        let dist = hypot(dx, dy)
        var m = pose.m
        // Pushed along the felt it tips over: a quarter turn per edge length, about z × direction.
        if dist > 0.01 { m = D.axisAngle(DiceRollPickerV3(-dy / dist, dx / dist, 0), dist / D.edge * (.pi / 2)) * m }
        dice[i] = .still(DiceRollPickerPose(m: m, x: x, y: y, hop: 0))
        dragPrev = w
    }

    func release(velocity: CGSize, reduced: Bool) {
        defer { touchStarted = false }
        if ignored { return }
        lastTouch = now
        let i = dragging
        if i >= 0 {
            dragging = -1
            let (p, _) = dice[i].eval(now)
            let flat = D.snapFlat(p.m)
            dice[i] = reduced
                ? .still(DiceRollPickerPose(m: flat, x: p.x, y: p.y, hop: 0))
                : .slide(t0: now, dur: 0.28, m0: p.m, m1: flat, p0: (p.x, p.y), p1: (p.x, p.y))
            snap.selectionChanged()
        } else if hypot(velocity.width, velocity.height) > Self.flick {
            throwAll(D.clamp(Double(velocity.width) / 2500, -0.6, 0.6), reduced: reduced)
        }
    }
}
