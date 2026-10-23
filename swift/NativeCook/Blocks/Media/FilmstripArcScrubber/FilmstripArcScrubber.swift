import SwiftUI
import UIKit

/*
 Filmstrip Arc Scrubber — a video player whose scrubber lifts into a curved 3D filmstrip of frames.

 The "video" is a one-minute night drive drawn as a function of its time (FilmstripArcScrubberModel
 .swift): the light goes from golden hour through dusk to night, the sun sets by 0:30 and the moon
 rises after 0:34, stars come out, far mountains and near hills drift past at two speeds, lane
 dashes rush at the camera, lamp posts light up along the verge and a car's tail lights ride
 ahead. Scrubbing really rewinds and forwards it, and the strip's thumbnails are the same scene at
 their own times.

 Look: near-black #0A0A0C; the video fills the top 60 % (12 pt margins, 28 pt corners) with a
 title chip, an HD chip and a mono timecode; then the title (24 pt bold) and a line about the
 episode, a 4 pt progress bar with a 14 pt thumb and times, and transport buttons (back 10,
 play/pause in a 64 pt white circle, forward 10).

 The strip: frames every 2.5 s of video, each a 64 × 82 film cell (sprocket holes top and bottom,
 a 56 × 60 picture), wrapped round a cylinder of radius 230 seen in perspective (focal 520), so the
 frames turn away (up to about 75°) and shrink towards the sides, darkening as they turn; a dock
 magnification (1 + 0.38 · e^(−d²/1.2)) swells the frames near the centre, where a fixed white
 magnifier frame with the time above it marks the chosen one. It lifts out of the bar 98 pt above
 it, each frame flying out of the thumb with a stagger (0.07 of the lift per frame from the
 centre), and slides 40 pt per frame under the finger. While it is up the title fades out and the
 video scrubs with a short horizontal motion blur. On release the chosen frame grows from the
 magnifier into the video (0.6 s, easeInOut), the strip folds back (0.5 s) and playback resumes.

 autoPlay timeline (loop = 10 s):
   0.0 – 2.0   playing from 0:18
   2.0         the thumb is pressed: playback holds, the strip lifts (0.5 s, ease out back)
   2.7 – 4.5   dragged forward to 0:45 (night);  4.6 – 6.6 back to 0:15 (easeInOut)
   7.0         released: the 0:15 frame flies up into the video (0.6 s), playback resumes
   7.2 – 7.7   the strip folds back into the bar
   10.0        0:18 again, playing (the same frame as 0)

 Touch: press the bar and drag sideways to scrub (a tick haptic per frame), release to jump to the
 nearest frame; tap the video or the play button to pause or play; tap back or forward to skip
 10 s. 2 s after the last touch the video scrubs back to 0:18 (0.6 s) and the loop starts over.
 Reduced motion: no loop (paused at 0:18); the strip is flat and fades in, with no motion blur and
 no flight.
 */

struct FilmstripArcScrubber: View {
    var autoPlay = false

    static let loop = FilmstripArcScrubberModel.loop
    typealias M = FilmstripArcScrubberModel
    typealias S = FilmstripArcScrubberShown

    struct Tween {
        var from: Double, to: Double, at: Double, dur: Double, back: Bool
        init(_ v: Double) { from = v; to = v; at = 0; dur = 1; back = false }
        init(from: Double, to: Double, at: Double, dur: Double, back: Bool) {
            self.from = from; self.to = to; self.at = at; self.dur = dur; self.back = back
        }
        func value(_ now: Double) -> Double {
            let u = (now - at) / dur
            return from + (to - from) * (back ? M.easeOutBack(u) : M.easeInOut(u))
        }
    }

    struct Manual {
        var time0 = M.restTime
        var at = 0.0
        var playing = false
        var lift = Tween(0)
        var flyAt: Double? = nil
        var flyTime = 0.0
        var speed = 0.0
        var speedAt = 0.0
        func time(_ now: Double) -> Double { playing ? M.wrap(time0 + now - at) : time0 }
    }

    enum Zone { case scrub, play, back, forward, video, none }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var autoStart: Double? = nil
    @State private var returning: (at: Double, from: Double)? = nil
    @State private var manual = Manual()
    @State private var zone: Zone? = nil
    @State private var scrub = (start: 0.0, last: 0.0, lastAt: 0.0, frame: 0)
    @State private var busy = false
    @State private var resume: Task<Void, Never>? = nil
    @State private var tick = UISelectionFeedbackGenerator()
    @State private var thump = UIImpactFeedbackGenerator(style: .medium)

    private static func now() -> Double { Date.timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = M.Layout(w: size.width, h: size.height, top: insets.top, bottom: insets.bottom)
            TimelineView(.animation(paused: reduceMotion && !manual.playing && zone == nil && !busy)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let s = shown(now)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in
                        FilmstripArcScrubberDraw.screen(&ctx, layout: layout, s: s, reduce: reduceMotion)
                    }
                    .frame(width: size.width, height: size.height)
                    .contentShape(Rectangle())
                    .gesture(drag(layout: layout))
                    .accessibilityElement()
                    .accessibilityLabel("Night Drive, episode 3, \(M.clock(s.time)) of 1:00, \(s.playing ? "playing" : "paused")")
                    text(layout: layout, s: s)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 0.04, green: 0.04, blue: 0.047).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            tick.prepare()
            let now = Self.now()
            manual = Manual(time0: M.restTime, at: now, playing: false)
            if autoPlay && !reduceMotion { autoStart = now }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: State

    private func shown(_ now: Double) -> S {
        if let autoStart, now >= autoStart, !reduceMotion { return M.shown(at: now - autoStart) }
        if let r = returning {
            let u = (now - r.at) / 0.6
            return S(time: r.from + (M.restTime - r.from) * M.easeInOut(u), lift: 0, fly: 0, flyTime: 0, speed: 0, playing: false)
        }
        let m = manual
        var fly = 0.0
        if let f = m.flyAt, !reduceMotion {
            let u = (now - f) / 0.6
            if u >= 0 && u < 1 { fly = M.easeInOut(u) }
        }
        let speed = reduceMotion ? 0 : m.speed * exp(-(now - m.speedAt) * 6)
        return S(time: m.time(now), lift: m.lift.value(now), fly: fly, flyTime: m.flyTime, speed: speed, playing: m.playing)
    }

    private func takeOver(_ now: Double) {
        resume?.cancel()
        let s = shown(now)
        var m = Manual(time0: s.time, at: now, playing: s.playing)
        m.lift = Tween(s.lift)
        // A flight under way carries on (roughly where it was).
        if s.fly > 0 { m.flyAt = now - s.fly * 0.6; m.flyTime = s.flyTime }
        manual = m
        autoStart = nil
        returning = nil
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Self.now()
            returning = (now, shown(now).time)
            autoStart = now + 0.6
        }
    }

    private func markBusy() {
        guard reduceMotion else { return }
        busy = true
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(0.7))
            busy = false
        }
    }

    // MARK: Touch

    private func zone(at p: CGPoint, layout l: M.Layout) -> Zone {
        if p.y > l.barY - 36 && p.y < l.barY + 30 && p.x > l.barX - 20 && p.x < l.barX + l.barW + 20 { return .scrub }
        let ty = l.transportY
        if hypot(p.x - l.w / 2, p.y - ty) < 38 { return .play }
        if hypot(p.x - (l.w / 2 - 96), p.y - ty) < 30 { return .back }
        if hypot(p.x - (l.w / 2 + 96), p.y - ty) < 30 { return .forward }
        if l.video.contains(p) { return .video }
        return .none
    }

    private func drag(layout: M.Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Self.now()
                if zone == nil {
                    takeOver(now)
                    let z = zone(at: value.startLocation, layout: layout)
                    zone = z
                    if z == .scrub {
                        let t = manual.time(now)
                        manual.time0 = t
                        manual.playing = false
                        manual.at = now
                        manual.lift = Tween(from: manual.lift.value(now), to: 1, at: now, dur: reduceMotion ? 0.2 : 0.45, back: !reduceMotion)
                        scrub = (t, t, now, Int((t / M.frameStep).rounded()))
                        thump.impactOccurred(intensity: 0.6)
                    }
                }
                guard zone == .scrub else { return }
                let t = min(M.duration - 0.01, max(0, scrub.start + value.translation.width / 40 * M.frameStep))
                manual.time0 = t
                let dt = now - scrub.lastAt
                if dt > 0.001 {
                    manual.speed = 0.6 * manual.speed * exp(-(now - manual.speedAt) * 6) + 0.4 * (t - scrub.last) / dt
                    manual.speedAt = now
                }
                scrub.last = t
                scrub.lastAt = now
                let f = Int((t / M.frameStep).rounded())
                if f != scrub.frame {
                    scrub.frame = f
                    tick.selectionChanged()
                }
            }
            .onEnded { _ in
                let now = Self.now()
                let z = zone
                zone = nil
                switch z {
                case .scrub:
                    let snap = min(M.duration - M.frameStep, (manual.time0 / M.frameStep).rounded() * M.frameStep)
                    manual.time0 = snap
                    manual.at = now
                    manual.playing = true
                    manual.flyAt = now
                    manual.flyTime = snap
                    manual.lift = Tween(from: manual.lift.value(now), to: 0, at: now + (reduceMotion ? 0 : 0.2), dur: reduceMotion ? 0.2 : 0.5, back: false)
                    thump.impactOccurred(intensity: 0.8)
                case .play, .video:
                    let t = manual.time(now)
                    manual.time0 = t
                    manual.at = now
                    manual.playing.toggle()
                    thump.impactOccurred(intensity: 0.5)
                case .back, .forward:
                    let t = manual.time(now)
                    let d = z == .back ? -10.0 : 10.0
                    manual.time0 = M.wrap(t + d)
                    manual.at = now
                    manual.speed = d * 3
                    manual.speedAt = now
                    thump.impactOccurred(intensity: 0.5)
                default: break
                }
                markBusy()
                scheduleResume()
            }
    }

    // MARK: Text

    @ViewBuilder
    private func text(layout l: M.Layout, s: S) -> some View {
        let v = l.video
        let lift = M.clamp01(s.lift)
        Group {
            Text("Night Drive · Ep. 3")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .frame(height: 28)
                .background(Capsule().fill(.black.opacity(0.35)))
                .fixedSize()
                .offset(x: v.minX + 16, y: v.minY + 16)
            Text("HD")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 8)
                .frame(height: 22)
                .background(RoundedRectangle(cornerRadius: 6).strokeBorder(.white.opacity(0.7), lineWidth: 1.2))
                .fixedSize()
                .frame(width: 60, alignment: .trailing)
                .offset(x: v.maxX - 16 - 60, y: v.minY + 19)
            Text(M.timecode(s.time))
                .font(.system(size: 12, weight: .medium, design: .monospaced))
                .foregroundStyle(.white.opacity(0.9))
                .padding(.horizontal, 10)
                .frame(height: 24)
                .background(Capsule().fill(.black.opacity(0.35)))
                .fixedSize()
                .offset(x: v.minX + 16, y: v.maxY - 16 - 24)
            VStack(alignment: .leading, spacing: 6) {
                Text("Night Drive")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(.white)
                Text("Episode 3 · The coast road after midnight")
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(.white.opacity(0.55))
            }
            .lineLimit(1)
            .frame(width: l.w - 48, alignment: .leading)
            .opacity(1 - lift)
            .position(x: l.w / 2, y: l.titleY + 28)
            HStack {
                Text(M.clock(s.time))
                Spacer()
                Text("-" + M.clock(M.duration - s.time))
            }
            .font(.system(size: 12, weight: .medium, design: .monospaced))
            .foregroundStyle(.white.opacity(0.55))
            .frame(width: l.barW)
            .position(x: l.w / 2, y: l.barY + 20)
            // The time above the magnifier.
            Text(M.clock(s.time))
                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                .foregroundStyle(.black)
                .padding(.horizontal, 8)
                .frame(height: 20)
                .background(Capsule().fill(.white))
                .fixedSize()
                .opacity(reduceMotion ? lift : M.smoothstep(0.4, 1, lift))
                .position(x: l.w / 2, y: l.stripY - M.cellH * 0.69 - 18)
        }
        .allowsHitTesting(false)
    }
}

// MARK: Drawing

enum FilmstripArcScrubberDraw {
    typealias M = FilmstripArcScrubberModel
    typealias S = FilmstripArcScrubberShown

    static func screen(_ ctx: inout GraphicsContext, layout l: M.Layout, s: S, reduce: Bool) {
        ctx.fill(Path(CGRect(x: 0, y: 0, width: l.w, height: l.h)), with: .color(Color(red: 0.04, green: 0.04, blue: 0.047)))
        // The video, smeared sideways while it is scrubbed.
        let v = l.video
        var vc = ctx
        vc.clip(to: Path(roundedRect: v, cornerRadius: 28, style: .continuous))
        M.drawScene(&vc, v, time: s.time, lite: false)
        let blur = reduce ? 0 : M.clamp01((abs(s.speed) - 1.5) / 10)
        if blur > 0.02 {
            let dir = s.speed > 0 ? 1.0 : -1.0
            for k in 1...3 {
                var bc = vc
                bc.opacity = 0.3 * blur
                bc.translateBy(x: -dir * Double(k) * 7 * blur, y: 0)
                M.drawScene(&bc, v, time: s.time - dir * Double(k) * 0.08, lite: true)
            }
        }
        // Progress bar.
        let p = s.time / M.duration
        let lift = M.clamp01(s.lift)
        ctx.fill(Path(roundedRect: CGRect(x: l.barX, y: l.barY - 2, width: l.barW, height: 4), cornerRadius: 2), with: .color(.white.opacity(0.18)))
        ctx.fill(Path(roundedRect: CGRect(x: l.barX, y: l.barY - 2, width: l.barW * p, height: 4), cornerRadius: 2), with: .color(.white.opacity(0.9)))
        let tx = l.barX + l.barW * p
        let tr = 7 * (1 + 0.4 * lift)
        if lift > 0.01 {
            ctx.fill(Path(ellipseIn: CGRect(x: tx - 22, y: l.barY - 22, width: 44, height: 44)),
                     with: .radialGradient(Gradient(colors: [.white.opacity(0.25 * lift), .clear]), center: CGPoint(x: tx, y: l.barY), startRadius: 0, endRadius: 22))
        }
        ctx.fill(Path(ellipseIn: CGRect(x: tx - tr, y: l.barY - tr, width: 2 * tr, height: 2 * tr)), with: .color(.white))
        transport(&ctx, l, playing: s.playing)
        strip(&ctx, l, s: s, reduce: reduce)
        // The chosen frame flying up into the video.
        if s.fly > 0.001 && s.fly < 0.999 {
            let f = s.fly
            let from = magnifiedPicture(l)
            let r = CGRect(x: from.minX + (v.minX - from.minX) * f, y: from.minY + (v.minY - from.minY) * f,
                           width: from.width + (v.width - from.width) * f, height: from.height + (v.height - from.height) * f)
            var fc = ctx
            fc.opacity = 1 - M.smoothstep(0.75, 1, f)
            let path = Path(roundedRect: r, cornerRadius: 3 + 25 * f, style: .continuous)
            fc.clip(to: path)
            M.drawScene(&fc, r, time: s.flyTime, lite: f < 0.5)
        }
    }

    /// Where the magnified centre frame's picture sits.
    static func magnifiedPicture(_ l: M.Layout) -> CGRect {
        let mag = 1.38
        let cy = l.stripY + (M.picY + M.picH / 2 - M.cellH / 2) * mag
        return CGRect(x: l.w / 2 - M.picW * mag / 2, y: cy - M.picH * mag / 2, width: M.picW * mag, height: M.picH * mag)
    }

    static func transport(_ ctx: inout GraphicsContext, _ l: M.Layout, playing: Bool) {
        let cy = l.transportY, cx = l.w / 2
        ctx.fill(Path(ellipseIn: CGRect(x: cx - 32, y: cy - 32, width: 64, height: 64)), with: .color(.white))
        if playing {
            ctx.fill(Path(roundedRect: CGRect(x: cx - 9, y: cy - 11, width: 6, height: 22), cornerRadius: 2), with: .color(.black))
            ctx.fill(Path(roundedRect: CGRect(x: cx + 3, y: cy - 11, width: 6, height: 22), cornerRadius: 2), with: .color(.black))
        } else {
            var tri = Path()
            tri.addLines([CGPoint(x: cx - 7, y: cy - 12), CGPoint(x: cx + 13, y: cy), CGPoint(x: cx - 7, y: cy + 12)])
            tri.closeSubpath()
            ctx.fill(tri, with: .color(.black))
        }
        for (dir, x) in [(-1.0, cx - 96), (1.0, cx + 96)] {
            // A circular arrow round "10", open at the top; its head turns back (or on) over the top.
            var arc = Path()
            for k in 0...40 {
                let a = (-60 + 310 * Double(k) / 40) * Double.pi / 180
                let q = CGPoint(x: x + dir * -14 * cos(a), y: cy + 14 * sin(a))
                if k == 0 { arc.move(to: q) } else { arc.addLine(to: q) }
            }
            ctx.stroke(arc, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
            // The head sits at the start (−60°), pointing along the arc away from it.
            let a0 = -60 * Double.pi / 180
            let tip = CGPoint(x: x + dir * -14 * cos(a0), y: cy + 14 * sin(a0))
            let tx = -dir * sin(a0), ty = -cos(a0)
            var head = Path()
            head.addLines([CGPoint(x: tip.x + tx * 6, y: tip.y + ty * 6),
                           CGPoint(x: tip.x - ty * 4.5 * dir, y: tip.y + tx * 4.5 * dir),
                           CGPoint(x: tip.x + ty * 4.5 * dir, y: tip.y - tx * 4.5 * dir)])
            head.closeSubpath()
            ctx.fill(head, with: .color(.white.opacity(0.9)))
            ctx.draw(Text("10").font(.system(size: 10, weight: .bold)).foregroundColor(.white.opacity(0.9)), at: CGPoint(x: x, y: cy + 0.5), anchor: .center)
        }
    }

    /// The filmstrip on its cylinder.
    static func strip(_ ctx: inout GraphicsContext, _ l: M.Layout, s: S, reduce: Bool) {
        let lift = s.lift
        guard lift > 0.001 else { return }
        let c = s.time / M.frameStep
        let cx = l.w / 2
        let cy = l.stripY + (1 - min(1, lift)) * 60
        let thumb = CGPoint(x: l.barX + l.barW * s.time / M.duration, y: l.barY)
        let last = Int(M.duration / M.frameStep) - 1
        var cells: [(d: Double, k: Int)] = []
        for k in max(0, Int(floor(c)) - 7)...min(last, Int(floor(c)) + 8) { cells.append((Double(k) - c, k)) }
        cells.sort { abs($0.d) > abs($1.d) }
        for (d, k) in cells {
            let theta = d * M.cellW / M.radius
            if !reduce && abs(theta) > 1.32 { continue }
            if reduce && abs(d) > 3.4 { continue }
            let u = reduce ? M.clamp01(lift) : M.easeInOut(lift * 1.5 - abs(d) * 0.07)
            if u <= 0.001 { continue }
            let mag = reduce ? 1 : 1 + 0.38 * exp(-d * d / 1.2)
            let hw = M.cellW / 2 * mag, hh = M.cellH / 2 * mag
            // A point of the cell's plane (lx, ly from its centre) on screen.
            func corner(_ lx: Double, _ ly: Double) -> CGPoint {
                if reduce { return CGPoint(x: cx + d * (M.cellW + 4) + lx, y: cy + ly) }
                let x = M.radius * sin(theta) + cos(theta) * lx
                let z = M.radius * (cos(theta) - 1) - sin(theta) * lx
                let kz = M.focal / (M.focal - z)
                return CGPoint(x: cx + x * kz, y: cy + ly * kz)
            }
            var tl = corner(-hw, -hh), tr = corner(hw, -hh), bl = corner(-hw, hh)
            // Unfolding out of the thumb.
            let small = 0.12
            func lerp(_ a: CGPoint, _ b: CGPoint) -> CGPoint { CGPoint(x: a.x + (b.x - a.x) * u, y: a.y + (b.y - a.y) * u) }
            tl = lerp(CGPoint(x: thumb.x - M.cellW / 2 * small, y: thumb.y - M.cellH / 2 * small), tl)
            tr = lerp(CGPoint(x: thumb.x + M.cellW / 2 * small, y: thumb.y - M.cellH / 2 * small), tr)
            bl = lerp(CGPoint(x: thumb.x - M.cellW / 2 * small, y: thumb.y + M.cellH / 2 * small), bl)
            let t = CGAffineTransform(a: (tr.x - tl.x) / M.cellW, b: (tr.y - tl.y) / M.cellW,
                                      c: (bl.x - tl.x) / M.cellH, d: (bl.y - tl.y) / M.cellH, tx: tl.x, ty: tl.y)
            var cc = ctx
            cc.concatenate(t)
            cc.opacity = reduce ? u : M.smoothstep(0, 0.6, u)
            cell(&cc, time: Double(k) * M.frameStep)
            let dark = reduce ? 0 : 0.7 * M.smoothstep(0.15, 1.3, abs(theta))
            if dark > 0.01 {
                cc.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: M.cellW, height: M.cellH), cornerRadius: 3), with: .color(.black.opacity(dark)))
            }
        }
        // The magnifier over the chosen frame.
        let a = reduce ? M.clamp01(lift) : M.smoothstep(0.4, 1, lift)
        let mag = reduce ? 1 : 1.38
        let box = CGRect(x: cx - M.cellW * mag / 2 - 3, y: cy - M.cellH * mag / 2 - 3, width: M.cellW * mag + 6, height: M.cellH * mag + 6)
        ctx.stroke(Path(roundedRect: box, cornerRadius: 8, style: .continuous), with: .color(.white.opacity(0.95 * a)), lineWidth: 2)
    }

    /// One film cell, 64 × 82: black film, four sprocket holes along each edge, the picture.
    static func cell(_ ctx: inout GraphicsContext, time: Double) {
        ctx.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: M.cellW, height: M.cellH), cornerRadius: 3), with: .color(Color(red: 0.06, green: 0.06, blue: 0.07)))
        for j in 0..<4 {
            let x = 6 + Double(j) * 14.5
            for y in [3, M.cellH - 7.5] {
                ctx.fill(Path(roundedRect: CGRect(x: x, y: y, width: 8.5, height: 4.5), cornerRadius: 1.3), with: .color(.white.opacity(0.32)))
            }
        }
        let pic = CGRect(x: M.picX, y: M.picY, width: M.picW, height: M.picH)
        var pc = ctx
        pc.clip(to: Path(roundedRect: pic, cornerRadius: 2))
        M.drawScene(&pc, pic, time: time, lite: true)
    }
}
