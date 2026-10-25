import SwiftUI
import UIKit

/*
 Viewmaster Reel — a travel-memories screen ("Reels — Iceland, 2025"): a red stereo viewer's
 window shows the current slide; under it the cardboard reel with its seven film chips, and the
 viewer's lever at its right. Pull the lever down or turn the reel with a finger: the reel turns
 one slot with a mechanical clunk, and the window blinks (its shutter closes between slots) to the
 next slide. Timeline in ViewmasterReelMotion.swift (loop = 9 s).

 Per frame: the reel image's rotation, the lever's rotation, one photo in the window (with the
 wobble transform), the two shutter lids and the caption rows' offsets. The room, bezel, lens,
 gate and lever art are static views; the reel is baked once off the main thread.

 Touch: a finger on the reel turns it about its centre (the angle under the finger); on release
 it springs to the nearest slot the flick points at (0.5 s, damping ratio 0.55, carrying the
 angular velocity: a small overshoot, the clunk). The lever follows a downward drag (rubber-banded
 past its stop); past 82 % it advances the reel one slot (spring 0.42 s, damping ratio 0.5) and
 springs back on release (damping ratio 0.45). A selection tick per slot, a light impact per lever
 pull. 2 s after the last touch the reel eases back to slide 1 (0.9 s) and the loop restarts.
 Reduced motion: slide 1, still; the reel and lever still answer the finger without the wobble.
 */
struct ViewmasterReel: View {
    /// Pulls the lever three times and spins the reel back to the first slide, on a loop.
    var autoPlay = false

    static let loop = ViewmasterReelMotion.loop
    typealias M = ViewmasterReelMotion
    typealias C = ViewmasterReelPalette
    typealias Art = ViewmasterReelArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var drive = ViewmasterReelDrive()
    @State private var reel: UIImage? = nil
    @State private var reelRadius: CGFloat = 0
    @State private var active = false
    @State private var settle: Task<Void, Never>? = nil

    var body: some View {
        GeometryReader { proxy in
            let ins = proxy.safeAreaInsets
            let L = ViewmasterReelLayout(W: proxy.size.width + ins.leading + ins.trailing,
                                         H: proxy.size.height + ins.top + ins.bottom, top: ins.top)
            ZStack(alignment: .topLeading) {
                ViewmasterReelRoom(L: L).equatable()
                TimelineView(.animation(paused: !((autoPlay && !reduceMotion && reel != nil) || active))) { timeline in
                    let v = drive.view(at: timeline.date, auto: autoPlay && !reduceMotion && reel != nil, reduced: reduceMotion)
                    moving(L, v)
                }
                Color.clear
                    .frame(width: L.W, height: L.H)
                    .contentShape(Rectangle())
                    .gesture(pan(L))
                    .accessibilityElement()
                    .accessibilityLabel("A stereo viewer reel of Iceland. Turn the reel or pull the lever down for the next view.")
                header(L)
            }
            .frame(width: L.W, height: L.H, alignment: .topLeading)
            .ignoresSafeArea()
            .task(id: L.r) { await bake(L.r) }
        }
        .background(C.bg.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onDisappear { settle?.cancel() }
    }

    /// Bakes the reel off the main thread; the loop starts at t = 0 on the first frame that has it.
    private func bake(_ r: CGFloat) async {
        guard r > 0, reelRadius != r else { return }
        let scale = min(3, displayScale)
        let image = await Task.detached(priority: .userInitiated) { Art.bakeReel(r: r, scale: scale) }.value
        reel = image
        reelRadius = r
        drive.begin(at: .now)
    }

    // MARK: Drawing

    private func moving(_ L: ViewmasterReelLayout, _ v: ViewmasterReelDrive.View) -> some View {
        let shut = M.shutter(v.pos)
        let idx = Int(M.wrap(v.pos.rounded(), Double(M.slots)))
        let leverDeg = ViewmasterReelLayout.leverRest + (ViewmasterReelLayout.leverPulled - ViewmasterReelLayout.leverRest) * v.lever
        let w = 2 * Double.pi * v.wobble / M.loop
        let wx = 3.5 * sin(2 * w), wy = 2.2 * sin(3 * w + 0.7) + v.shake, ws = 1.06 + 0.012 * sin(w)
        let Wn = L.win
        let winShape = RoundedRectangle(cornerRadius: 18, style: .circular)

        return ZStack(alignment: .topLeading) {
            // The window: the slide (wobbling a little, as if held up to the eye), the lens, the shutter.
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in
                    ctx.fill(Path(Wn), with: .color(C.rgb(0x0A0A0B)))
                    let sl = Art.slides[idx]
                    let img = Art.photos[sl.photo]
                    let px = Art.pixels(img)
                    let sx = Wn.width / sl.crop.width, sy = Wn.height / sl.crop.height
                    var c = ctx
                    c.translateBy(x: Wn.midX + wx, y: Wn.midY + wy)
                    c.scaleBy(x: ws, y: ws)
                    c.translateBy(x: -Wn.midX, y: -Wn.midY)
                    c.draw(Image(uiImage: img), in: CGRect(x: Wn.minX - sl.crop.minX * sx, y: Wn.minY - sl.crop.minY * sy,
                                                           width: px.width * sx, height: px.height * sy))
                }
                .frame(width: L.W, height: L.H)
                ViewmasterReelLens(L: L).equatable()
                Canvas { ctx, _ in
                    let hh = (Wn.height / 2 + 2) * shut
                    guard hh > 0.01 else { return }
                    ctx.fill(Path(CGRect(x: Wn.minX, y: Wn.minY - 2, width: Wn.width, height: hh)), with: .color(C.rgb(0x0B0909)))
                    ctx.fill(Path(CGRect(x: Wn.minX, y: Wn.maxY + 2 - hh, width: Wn.width, height: hh)), with: .color(C.rgb(0x0B0909)))
                }
                .frame(width: L.W, height: L.H)
                ViewmasterReelRecess(L: L).equatable()
            }
            .frame(width: L.W, height: L.H, alignment: .topLeading)
            .mask(winShape.frame(width: Wn.width, height: Wn.height).position(x: Wn.midX, y: Wn.midY))
            .offset(y: v.shake * 0.5)

            if let reel {
                Image(uiImage: reel)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: L.r * 2, height: L.r * 2)
                    .rotationEffect(.degrees(-v.pos * M.stepDeg))
                    .offset(x: L.cx - L.r, y: L.cy - L.r)
            }
            ViewmasterReelGate(L: L).equatable()
            ViewmasterReelLever(L: L).equatable()
                .rotationEffect(.degrees(leverDeg), anchor: UnitPoint(x: L.px / L.W, y: L.py / L.H))
            ViewmasterReelPivot(L: L).equatable()

            captions(L, pos: v.pos)
        }
        .frame(width: L.W, height: L.H, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    /// One caption per row of a clipped box: it rolls up out of the box as the next one rolls in
    /// from below, fading over half a row, so two captions never sit on top of each other.
    private func captions(_ L: ViewmasterReelLayout, pos: Double) -> some View {
        let pm = M.wrap(pos, Double(M.slots))
        let base = floor(pm)
        let roll = base + M.smoothstep(0.3, 0.7, pm - base)
        let row = ViewmasterReelLayout.captionH
        let rows = Art.slides + [Art.slides[0]]
        return ZStack(alignment: .topLeading) {
            ForEach(0..<rows.count, id: \.self) { k in
                let d = Double(k) - roll
                if abs(d) < 0.6 {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(String(format: "VIEW %02d / 07", (k % M.slots) + 1))
                            .font(.system(size: 11, weight: .bold).monospacedDigit()).tracking(1.6)
                            .foregroundStyle(C.accent)
                            .frame(height: 14)
                        Text(rows[k].title)
                            .font(.system(size: 20, weight: .semibold)).tracking(-0.3)
                            .foregroundStyle(C.ink)
                            .lineLimit(1)
                            .frame(height: 25)
                        Text(rows[k].place)
                            .font(.system(size: 13, weight: .medium).monospacedDigit())
                            .foregroundStyle(C.mute)
                            .lineLimit(1)
                            .frame(height: 17)
                    }
                    .frame(width: L.W - 120, height: row, alignment: .leading)
                    .opacity(max(0, 1 - abs(d) * 1.8))
                    .offset(y: d * row)
                }
            }
        }
        .frame(width: L.W - 120, height: row, alignment: .topLeading)
        .clipped()
        .offset(x: 24, y: L.captionY)
    }

    private func header(_ L: ViewmasterReelLayout) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("REELS")
                .font(.system(size: 12, weight: .bold)).tracking(2.4)
                .foregroundStyle(C.accent)
            Text("Iceland, 2025")
                .font(.system(size: 30, weight: .bold)).tracking(-0.6)
                .foregroundStyle(C.ink)
                .padding(.top, 4)
            Text("7 views · Ring Road, June · 1,332 km")
                .font(.system(size: 14, weight: .medium).monospacedDigit())
                .foregroundStyle(C.mute)
                .padding(.top, 2)
        }
        .frame(width: L.W - 48, alignment: .leading)
        .offset(x: 24, y: L.top + 12)
        .allowsHitTesting(false)
        .accessibilityElement(children: .combine)
    }

    // MARK: Touch

    private func pan(_ L: ViewmasterReelLayout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if drive.grab == .none {
                    guard !drive.ignoring else { return }
                    guard drive.begin(touch: value.startLocation, at: now, L: L, auto: autoPlay && !reduceMotion && reel != nil) else { return }
                    settle?.cancel()
                    active = true
                }
                drive.move(to: value.location, translation: value.translation, at: now, L: L)
            }
            .onEnded { value in
                let had = drive.grab != .none
                drive.end(at: .now, location: value.location, velocity: value.velocity, L: L)
                guard had else { return }
                settle?.cancel()
                settle = Task { @MainActor in
                    try? await Task.sleep(for: .seconds(2 + 0.9 + 0.8))
                    if !Task.isCancelled && drive.grab == .none { active = false }
                }
            }
    }
}

/// The reel's state outside the autoPlay timeline: what the finger and its springs are doing.
/// Read from the timeline's date; haptics fire from here at slot changes (never per frame).
final class ViewmasterReelDrive {
    struct View { var pos: Double; var lever: Double; var shake: Double; var wobble: Double }
    enum Grab { case none, reel, lever }
    enum Mode { case auto, manual, returning(until: Date) }
    typealias M = ViewmasterReelMotion
    typealias Lay = ViewmasterReelLayout

    var grab: Grab = .none
    var ignoring = false
    private var mode: Mode = .auto
    private var autoStart = Date.now
    private var lastFrame: Date? = nil
    private var lastTouch = Date.distantPast
    private var lastAngle = 0.0
    private var leverStart = 0.0
    private var fired = false
    private var lastSlot: Double? = nil
    private var reduced = false
    private let pos = ViewmasterReelChannel()
    private let lever = ViewmasterReelChannel()
    private let tick = UISelectionFeedbackGenerator()
    private let pull = UIImpactFeedbackGenerator(style: .light)

    func begin(at date: Date) {
        autoStart = date
        lastFrame = nil
        mode = .auto
        tick.prepare(); pull.prepare()
    }

    private func loopTime(_ date: Date, auto: Bool) -> Double {
        guard auto else { return 0 }
        // A stalled frame pauses the loop clock instead of skipping ahead.
        if let last = lastFrame, date.timeIntervalSince(last) > 0.25 {
            autoStart = autoStart.addingTimeInterval(date.timeIntervalSince(last) - 1 / 60)
        }
        lastFrame = date
        return max(0, date.timeIntervalSince(autoStart)).truncatingRemainder(dividingBy: M.loop)
    }

    func view(at date: Date, auto: Bool, reduced: Bool) -> View {
        self.reduced = reduced
        switch mode {
        case .manual where grab == .none && date.timeIntervalSince(lastTouch) > 2:
            let start = lastTouch.addingTimeInterval(2)
            lever.tween(to: 0, duration: 0.3, curve: M.easeInOutQuad, at: start)
            let p = pos.value(at: start)
            pos.tween(to: (p / Double(M.slots)).rounded() * Double(M.slots), duration: 0.9, curve: M.returnEase, at: start)
            mode = .returning(until: start.addingTimeInterval(0.9))
            return view(at: date, auto: auto, reduced: reduced)
        case let .returning(until) where date >= until:
            mode = .auto
            autoStart = until
            lastFrame = nil
            return view(at: date, auto: auto, reduced: reduced)
        case .auto:
            let t = loopTime(date, auto: auto)
            let p = M.pose(t)
            return View(pos: p.pos, lever: p.lever, shake: reduced ? 0 : p.shake, wobble: reduced ? 0 : t)
        default:
            let p = pos.value(at: date)
            if case .manual = mode {
                // A tick per slot that passes the gate.
                let slot = p.rounded()
                if let last = lastSlot, last != slot { tick.selectionChanged() }
                lastSlot = slot
            }
            let wob = reduced ? 0 : date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: M.loop)
            return View(pos: p, lever: lever.value(at: date), shake: 0, wobble: wob)
        }
    }

    private func knob(_ L: Lay, lever lv: Double) -> CGPoint {
        let a = (Lay.leverRest + (Lay.leverPulled - Lay.leverRest) * lv) * .pi / 180
        return CGPoint(x: L.px + cos(a) * L.len, y: L.py + sin(a) * L.len)
    }

    /// A finger lands; true when it is on the lever or the reel.
    func begin(touch p: CGPoint, at date: Date, L: Lay, auto: Bool) -> Bool {
        let v = view(at: date, auto: auto, reduced: reduced)
        let k = knob(L, lever: v.lever)
        let onLever = hypot(p.x - k.x, p.y - k.y) < 48
        let onDisc = hypot(p.x - L.cx, p.y - L.cy) < L.r + 12
        guard onLever || onDisc else { ignoring = true; return false }
        lastTouch = date
        switch mode {
        case .manual: break
        default:
            pos.set(v.pos)
            lever.set(v.lever)
            mode = .manual
            lastSlot = v.pos.rounded()
        }
        if onLever {
            grab = .lever
            lever.set(lever.value(at: date))
            leverStart = lever.value(at: date)
            fired = false
        } else {
            grab = .reel
            pos.set(pos.value(at: date))
            lastAngle = atan2(p.y - L.cy, p.x - L.cx)
        }
        return true
    }

    func move(to p: CGPoint, translation: CGSize, at date: Date, L: Lay) {
        guard grab != .none else { return }
        lastTouch = date
        if grab == .reel {
            let ang = atan2(p.y - L.cy, p.x - L.cx)
            var d = ang - lastAngle
            if d > .pi { d -= 2 * .pi }
            if d < -.pi { d += 2 * .pi }
            lastAngle = ang
            pos.set(pos.value(at: date) - d / M.stepRad)
        } else {
            let lv = M.rubber(leverStart + translation.height / 72 - translation.width / 220, 0, 1, 0.22)
            lever.set(lv)
            if lv > 0.82 && !fired {
                fired = true
                pos.spring(to: pos.value(at: date).rounded() + 1, response: 0.42, damping: 0.5, velocity: 0, at: date)
                pull.impactOccurred()
            } else if lv < 0.3 {
                fired = false
            }
        }
    }

    func end(at date: Date, location p: CGPoint, velocity: CGSize, L: Lay) {
        ignoring = false
        let g = grab
        guard g != .none else { return }
        grab = .none
        lastTouch = date
        if g == .reel {
            let rx = p.x - L.cx, ry = p.y - L.cy
            let omega = (rx * velocity.height - ry * velocity.width) / max(400, rx * rx + ry * ry)
            let vel = -omega / M.stepRad
            let target = (pos.value(at: date) + vel * 0.12).rounded()
            pos.spring(to: target, response: 0.5, damping: 0.55, velocity: vel, at: date)
        } else {
            lever.spring(to: 0, response: 0.38, damping: 0.45, velocity: 0, at: date)
        }
    }
}
