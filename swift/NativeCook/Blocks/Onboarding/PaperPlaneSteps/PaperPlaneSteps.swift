import SwiftUI
import UIKit

/*
 Paper Plane Steps — onboarding for "Postcard", an app for slow letters to faraway friends. Each
 step is a sheet of writing paper; Next folds it into a paper plane in four visible folds and
 throws it, and the next sheet slides up. The loop is in PaperPlaneStepsMotion.swift (loop = 10 s),
 the folds in PaperPlaneStepsGeometry.swift.

 The paper is drawn in one Canvas as textured triangles: each face of the folded paper is split
 into a fan, and each triangle draws the paper atlas clipped to itself under the affine map from
 its atlas triangle to its screen triangle (as Skia's Vertices does in the React Native version).

 Touch: drag up on the sheet to fold it (60 pt of drag per fold, the flaps following the finger).
 Let go past the second fold, or flick up, and it finishes folding (3–8 folds a second with the
 flick) and flies (0.6–1.2 s, faster for a harder flick); short of it, it unfolds again. Next
 plays the same at 0.32 s a fold. A selection tick per finished fold and a light impact on the
 throw. 2 s after the last touch the first sheet slides back up (0.8 s) and the loop restarts.
 Reduced motion: no loop; Next cross-fades to the next sheet (the plane is not thrown).
*/

struct PaperPlaneSteps: View {
    /// Plays the 10 s demo: fold and throw sheet 1, sheet 2 arrives, fold and throw it, back to 1.
    var autoPlay = false

    static let loop = PaperPlaneStepsMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = PaperPlaneStepsEngine()
    /// True while a touch or Next still moves things outside autoPlay.
    @State private var live = false
    @State private var step = 0
    /// The paper atlas, built off the main thread on first appearance.
    @State private var loaded: UIImage?

    typealias M = PaperPlaneStepsMotion
    typealias S = PaperPlaneStepsStyle

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let l = PaperPlaneStepsLayout(size: size, insets: insets)
        let scale = min(2, displayScale)
        let atlas = loaded ?? PaperPlaneStepsArt.cached(sw: l.sw, sh: l.sh, scale: scale)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        engine.prepare(sw: l.sw, sh: l.sh)
        engine.onRest = { live = false }
        engine.onStep = { step = $0 }

        return ZStack(alignment: .topLeading) {
            Rectangle()
                .colorEffect(ShaderLibrary.paperPlaneStepsBackdrop(.float2(max(1, size.width), max(1, size.height))))
                .frame(width: size.width, height: size.height)

            TimelineView(.animation(paused: !(auto || live))) { timeline in
                let pose = engine.step(to: timeline.date, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    if let atlas { paper(pose, l, atlas, reduced) }
                    dots(pose.dot, l)
                }
            }
            .task(id: PaperPlaneStepsArt.key(sw: l.sw, sh: l.sh, scale: scale)) {
                guard atlas == nil else { return }
                let built = await PaperPlaneStepsArt.load(sw: l.sw, sh: l.sh, scale: scale)
                // The loop starts with the first frame that shows the paper.
                engine.restart()
                loaded = built
            }

            header(l)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: l.sw, height: l.sh)
                .offset(x: l.cx - l.sw / 2, y: l.sheetY)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if engine.dragChanged(v.translation, reduced: reduced, auto: auto) { live = true }
                        }
                        .onEnded { v in engine.dragEnded(velocity: v.velocity) }
                )
                .accessibilityElement()
                .accessibilityLabel("Onboarding letter. Swipe up to fold it into a paper plane and send it.")

            Button {
                live = true
                engine.next(reduced: reduced, auto: auto)
            } label: {
                Text(step == M.steps - 1 ? "Start writing" : "Next")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color(hex: 0x2A140B))
                    .frame(width: size.width - 48, height: 56)
                    .contentShape(Capsule())
            }
            .buttonStyle(PaperPlaneStepsPress())
            .offset(x: 24, y: size.height - insets.bottom - 16 - 56)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(S.screen)
        .clipped()
    }

    // MARK: Per frame

    private func paper(_ pose: M.Pose, _ l: PaperPlaneStepsLayout, _ atlas: UIImage, _ reduced: Bool) -> some View {
        let plan = engine.plan ?? PaperPlaneStepsPlan.make(sw: l.sw, sh: l.sh)
        let flat = PaperPlaneStepsPlan.flat(sw: l.sw, sh: l.sh)
        // The paper (or the plane) this frame.
        var xf = M.Xf(ox: l.cx, oy: l.cy, cx: l.sw / 2, cy: l.sh / 2)
        if pose.fly >= 0 {
            let p = M.flight(pose.fly, sx: l.cx, sy: l.cy, w: l.size.width)
            xf = M.Xf(ox: p.x, oy: p.y, cx: l.sw / 2, cy: l.sh / 2, sx: 0.62 * cos(p.bank), sy: 1, rot: p.heading, scale: p.scale, bank: p.bank)
        } else {
            xf.sx = 1 - 0.38 * pose.tuck
            xf.scale = 1 - 0.14 * pose.tuck
        }
        let mesh = pose.fly >= 1 ? M.Out() : M.paperMesh(plan, fold: pose.fold, step: pose.sheet, xf: xf)
        var incoming = M.Out()
        if pose.incP >= 0 {
            let e = reduced ? 1 : M.easeOutBack(pose.incP)
            let ixf = M.Xf(ox: l.cx, oy: l.cy + (1 - e) * (l.size.height - l.sheetY + 40), cx: l.sw / 2, cy: l.sh / 2, rot: (1 - e) * -0.07)
            incoming = M.paperMesh(flat, fold: 4, step: pose.inc, xf: ixf)
        }
        let opacityIn = reduced ? M.clamp(pose.incP * 2, 0, 1) : 1
        let opacityPaper = reduced && pose.incP >= 0 ? 1 - M.clamp(pose.incP * 2, 0, 1) : 1
        let alt = pose.fly >= 0 ? sin(Double.pi * min(1, pose.fly * 1.4)) * 50 : 0
        let shadowOpacity = pose.fly >= 0 ? 0.5 * (1 - M.seg(pose.fly, 0.2, 0.6)) : 0.5
        let trailOpacity = pose.fly < 0 ? 0 : 0.6 * (1 - M.seg(pose.fly, 0.7, 1)) * M.seg(pose.fly, 0.02, 0.12)

        return Canvas { ctx, _ in
            let image = ctx.resolve(Image(uiImage: atlas))
            let atlasRect = CGRect(x: 0, y: 0, width: l.sw * 4, height: l.sh)

            if pose.incP >= 0 {
                ctx.drawLayer { c in
                    c.opacity = opacityIn
                    c.addFilter(.blur(radius: 14))
                    c.fill(Self.outlines(incoming.shadow, dx: 4, dy: 10), with: .color(Color.rgba(6, 10, 24, 0.5)))
                }
                ctx.drawLayer { c in
                    c.opacity = opacityIn
                    Self.draw(incoming.statics, image, atlasRect, in: &c)
                }
            }

            if trailOpacity > 0, pose.fly > 0.02 {
                var trail = Path()
                for i in 0...28 {
                    let p = M.flight(pose.fly * Double(i) / 28, sx: l.cx, sy: l.cy, w: l.size.width)
                    if i == 0 { trail.move(to: CGPoint(x: p.x, y: p.y)) } else { trail.addLine(to: CGPoint(x: p.x, y: p.y)) }
                }
                var c = ctx
                c.opacity = trailOpacity
                c.stroke(trail, with: .color(Color.rgba(244, 239, 230, 1)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, dash: [5, 8]))
            }

            if !mesh.all.isEmpty {
                ctx.drawLayer { c in
                    c.opacity = shadowOpacity
                    c.addFilter(.blur(radius: 14))
                    c.fill(Self.outlines(mesh.shadow, dx: 4 + alt * 0.4, dy: 10 + alt), with: .color(Color.rgba(6, 10, 24, 1)))
                }
                ctx.drawLayer { c in
                    c.opacity = opacityPaper
                    Self.draw(mesh.statics, image, atlasRect, in: &c)
                    Self.draw(mesh.movers, image, atlasRect, in: &c)
                    if !mesh.moving.isEmpty {
                        let t = M.tint(mesh.flap)
                        c.fill(Self.outlines(mesh.moving, dx: 0, dy: 0), with: .color(Color.rgba(t.r, t.g, t.b, t.a)))
                    }
                    // The folded edges, faint, so the stacked layers read.
                    let edges = Self.outlines(mesh.all, dx: 0, dy: 0)
                    c.stroke(edges, with: .color(Color.rgba(90, 70, 48, 0.28)), style: StrokeStyle(lineWidth: 0.8, lineJoin: .round))
                    // The flying plane's wings, lit and shaded by its bank.
                    if xf.bank > 0.01 {
                        let from = M.screenOf(xf, 0, l.sh * 0.5, 0)
                        var to = M.screenOf(xf, l.sw, l.sh * 0.5, 0)
                        if hypot(to.x - from.x, to.y - from.y) < 0.5 { to = CGPoint(x: from.x + 0.5, y: from.y) }
                        let b = xf.bank
                        c.fill(edges, with: .linearGradient(
                            Gradient(stops: [
                                .init(color: Color.rgba(255, 250, 236, 0.22 * b), location: 0),
                                .init(color: Color.rgba(255, 250, 236, 0), location: 0.5),
                                .init(color: Color.rgba(22, 20, 38, 0.34 * b), location: 1),
                            ]),
                            startPoint: from, endPoint: to
                        ))
                    }
                }
            }
        }
        .frame(width: l.size.width, height: l.size.height)
        .allowsHitTesting(false)
    }

    /// Closed outlines as one path, moved by (dx, dy).
    private static func outlines(_ polys: [[CGPoint]], dx: Double, dy: Double) -> Path {
        var p = Path()
        for poly in polys where poly.count >= 3 {
            p.addLines(poly.map { CGPoint(x: $0.x + dx, y: $0.y + dy) })
            p.closeSubpath()
        }
        return p
    }

    /// Each triangle draws the atlas clipped to itself under the affine map from its atlas
    /// triangle to its screen one. The clip is pushed out by a third of a point so neighbouring
    /// triangles of a face overlap instead of leaving an antialiased seam between them.
    private static func draw(_ tris: [M.Tri], _ image: GraphicsContext.ResolvedImage, _ rect: CGRect, in ctx: inout GraphicsContext) {
        for tri in tris {
            let (p0, p1, p2) = tri.p
            let (t0, t1, t2) = tri.t
            let det = (t1.x - t0.x) * (t2.y - t0.y) - (t2.x - t0.x) * (t1.y - t0.y)
            if abs(det) < 1e-6 { continue }
            let i00 = (t2.y - t0.y) / det, i01 = -(t2.x - t0.x) / det
            let i10 = -(t1.y - t0.y) / det, i11 = (t1.x - t0.x) / det
            let ax = p1.x - p0.x, bx = p2.x - p0.x
            let ay = p1.y - p0.y, by = p2.y - p0.y
            let a = ax * i00 + bx * i10
            let cc = ax * i01 + bx * i11
            let b = ay * i00 + by * i10
            let d = ay * i01 + by * i11
            let m = CGAffineTransform(a: a, b: b, c: cc, d: d, tx: p0.x - a * t0.x - cc * t0.y, ty: p0.y - b * t0.x - d * t0.y)
            let gx = (p0.x + p1.x + p2.x) / 3
            let gy = (p0.y + p1.y + p2.y) / 3
            func out(_ p: CGPoint) -> CGPoint {
                let dx = p.x - gx
                let dy = p.y - gy
                let l = max(hypot(dx, dy), 0.001)
                return CGPoint(x: p.x + dx / l * 0.35, y: p.y + dy / l * 0.35)
            }
            var clip = Path()
            clip.addLines([out(p0), out(p1), out(p2)])
            clip.closeSubpath()
            var c = ctx
            c.clip(to: clip)
            c.concatenate(m)
            c.draw(image, in: rect)
        }
    }

    private func dots(_ dot: Double, _ l: PaperPlaneStepsLayout) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(Color.rgba(244, 239, 230, 0.25))
                    .frame(width: 8, height: 8)
                    .offset(x: Double(i) * 16)
            }
            Capsule()
                .fill(S.accent)
                .frame(width: 22, height: 8)
                .offset(x: -7 + dot * 16)
        }
        .frame(width: 46, height: 8, alignment: .topLeading)
        .offset(x: l.cx - 23, y: l.dotsY)
        .allowsHitTesting(false)
    }

    // MARK: Static

    private func header(_ l: PaperPlaneStepsLayout) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(S.accent)
                        .frame(width: 16, height: 12)
                        .rotationEffect(.degrees(-8))
                    Text("Postcard")
                        .font(.system(size: 15, weight: .bold))
                        .kerning(0.2)
                        .foregroundStyle(S.ink)
                }
                Spacer()
                Text("Skip")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(S.inkSoft)
            }
            .frame(height: 24)
            Text("Send notes to faraway friends")
                .font(.system(size: 26, weight: .bold))
                .kerning(-0.4)
                .lineSpacing(1)
                .foregroundStyle(S.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(width: l.size.width - 48, alignment: .leading)
        .offset(x: 24, y: l.top)
        .allowsHitTesting(false)
    }
}

private struct PaperPlaneStepsPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                Capsule()
                    .fill(PaperPlaneStepsStyle.accent)
                    .overlay(
                        Capsule().strokeBorder(
                            LinearGradient(colors: [Color.rgba(255, 226, 206, 0.5), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)),
                            lineWidth: 1
                        )
                    )
                    .shadow(color: Color.rgba(232, 131, 90, 0.28), radius: 14, y: 12)
                    .shadow(color: Color.rgba(40, 16, 6, 0.4), radius: 1, y: 1)
            )
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

enum PaperPlaneStepsStyle {
    static let ink = Color(hex: 0xF4EFE6)
    static let inkSoft = Color.rgba(236, 230, 220, 0.66)
    static let accent = Color(hex: 0xE8835A)
    static let screen = Color(hex: 0x1B2236)
}

struct PaperPlaneStepsLayout {
    let size: CGSize
    let top: Double
    let sheetY: Double
    let sw: Double
    let sh: Double
    let cx: Double
    let cy: Double
    let dotsY: Double

    init(size: CGSize, insets: EdgeInsets) {
        self.size = size
        top = insets.top + 16
        sheetY = top + 116
        // The sheet is as large as the screen allows between the title and the dots above Next.
        let ctaTop = size.height - insets.bottom - 16 - 56
        sw = min(size.width - 48, 340, (ctaTop - 44 - sheetY) / 1.32).rounded()
        sh = (sw * 1.32).rounded()
        cx = size.width / 2
        cy = sheetY + sh / 2
        dotsY = sheetY + sh + 22
    }
}

/// The loop and the touch controller, stepped once a frame like the React Native frame callback.
@MainActor
final class PaperPlaneStepsEngine {
    typealias M = PaperPlaneStepsMotion

    private(set) var plan: PaperPlaneStepsPlan?
    private var pose = M.Pose()
    /// 0 loop, 1 dragging, 2 controller.
    private var mode = 0
    /// 0 idle, 1 folding, 2 flying, 4 unfolding, 5 returning.
    private var run = 0
    private var rate = 3.0
    private var flyDur = 1.0
    private var idle = 0.0
    private var startFold = 0.0
    private var grab = false
    private var touching = false
    private var dragging = false
    private var loopStart: Date?
    private var loopT = 0.0
    private var last: Date?
    private var wasAuto: Bool?
    private let tick = UISelectionFeedbackGenerator()
    private let throwFeel = UIImpactFeedbackGenerator(style: .light)
    var onRest: () -> Void = {}
    var onStep: (Int) -> Void = { _ in }

    func prepare(sw: Double, sh: Double) {
        if let plan, plan.sw == sw, plan.sh == sh { return }
        plan = PaperPlaneStepsPlan.make(sw: sw, sh: sh)
    }

    /// Starts the loop again from t = 0 at the next frame.
    func restart() {
        loopStart = nil
        last = nil
    }

    private func later(_ f: @escaping () -> Void) {
        DispatchQueue.main.async(execute: f)
    }

    func step(to date: Date, auto: Bool, reduced: Bool) -> M.Pose {
        if wasAuto != auto {
            wasAuto = auto
            loopStart = date
            mode = 0
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        if loopStart == nil { loopStart = date }
        // A stalled frame pauses the loop instead of skipping part of it.
        if raw > 0.25, let start = loopStart { loopStart = start.addingTimeInterval(raw - 1.0 / 60) }
        let dt = min(max(raw, 0), 1.0 / 20)

        if mode == 0 {
            loopT = auto ? max(0, date.timeIntervalSince(loopStart ?? date)).truncatingRemainder(dividingBy: M.loop) : 0
            pose = M.script(loopT)
            return pose
        }
        if run == 1 {
            let before = pose.fold
            let next = min(4, before + dt * rate)
            pose.fold = next
            if floor(next) > floor(before) { tick.selectionChanged() }
            if next >= 4 {
                run = 2
                pose.fly = -1
            }
        } else if run == 2 {
            pose.tuck = min(1, pose.tuck + dt / 0.2)
            if pose.tuck > 0.6 || pose.fly >= 0 {
                if pose.fly < 0 { throwFeel.impactOccurred() }
                pose.fly = min(1, max(0, pose.fly) + dt / flyDur)
            }
            if pose.fly > 0.85 && pose.incP < 0 {
                pose.inc = (pose.sheet + 1) % M.steps
                pose.incP = 0
            }
        } else if run == 4 {
            pose.fold = max(0, pose.fold - dt * 5)
            if pose.fold <= 0 { run = 0 }
        }
        if pose.incP >= 0 {
            pose.incP = min(1, pose.incP + dt / 0.8)
            if pose.incP >= 1 {
                let back = run == 5
                pose.sheet = pose.inc
                pose.fold = 0
                pose.tuck = 0
                pose.fly = -1
                pose.incP = -1
                run = 0
                idle = 0
                let s = pose.inc
                later { self.onStep(s) }
                if back {
                    toLoop(date, auto: auto)
                    return pose
                }
            }
        }
        let target = Double(pose.incP > 0.1 ? pose.inc : pose.sheet)
        pose.dot += (target - pose.dot) * min(1, dt * 10)
        if mode == 2 && run == 0 {
            idle += dt
            if idle > M.idleBeforeReturn {
                if pose.sheet == 0 && pose.fold == 0 {
                    toLoop(date, auto: auto)
                } else {
                    run = 5
                    pose.inc = 0
                    pose.incP = 0
                }
            }
        }
        return pose
    }

    private func toLoop(_ date: Date, auto: Bool) {
        mode = 0
        loopStart = date
        loopT = 0
        if !auto { later { self.onRest() } }
    }

    /// Hands the loop's frame to the controller, mid-fold or mid-flight as it may be.
    private func takeOver(auto: Bool) {
        idle = 0
        if mode != 0 { return }
        let t = loopT
        let s = auto ? M.script(t) : M.script(0)
        let second = t >= 5.2 && t < 9.4
        rate = second ? 1 / 0.3 : 1 / 0.45
        flyDur = second ? 1.0 : 1.2
        if s.incP >= 0 || s.fly >= 0 {
            run = 2
        } else if s.fold > 0 {
            run = 1
        } else {
            run = 0
        }
        mode = 2
    }

    func next(reduced: Bool, auto: Bool) {
        if mode == 0 {
            let s = auto ? M.script(loopT) : M.script(0)
            if s.fold > 0 || s.fly >= 0 || s.incP >= 0 { return }
            mode = 2
        }
        idle = 0
        if run != 0 { return }
        if reduced {
            pose.inc = (pose.sheet + 1) % M.steps
            pose.incP = 0
            return
        }
        rate = 1 / 0.32
        flyDur = 1.0
        run = 1
    }

    /// Returns true once the drag has taken hold (the timeline must run).
    func dragChanged(_ translation: CGSize, reduced: Bool, auto: Bool) -> Bool {
        if !touching {
            touching = true
            dragging = false
        }
        if !dragging {
            guard abs(translation.height) > 10 else { return false }
            dragging = true
            takeOver(auto: auto)
            grab = run == 0 && !reduced
            if grab {
                mode = 1
                startFold = pose.fold
                tick.prepare()
            }
        }
        if grab {
            let before = pose.fold
            let f = M.clamp(startFold - translation.height / 60, 0, 3.999)
            pose.fold = f
            if floor(f) > floor(before) { tick.selectionChanged() }
        }
        return true
    }

    func dragEnded(velocity: CGSize) {
        defer {
            touching = false
            dragging = false
        }
        guard dragging, grab else { return }
        let vy = velocity.height
        if pose.fold >= 2 || vy < -900 {
            rate = M.clamp(-vy / 300, 3, 8)
            flyDur = M.clamp(900 / max(500, -vy), 0.6, 1.2)
            run = 1
        } else {
            run = 4
        }
        grab = false
        mode = 2
        idle = 0
    }
}
