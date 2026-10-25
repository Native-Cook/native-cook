import SwiftUI
import UIKit

/*
 Zipper Unlock — the lock screen of "Pocket", a private journal: a denim cover with a brass zipper
 down the middle. Pull the tab down and the teeth part in a V above the slider while the two
 halves roll back at their edges, showing the journal underneath. The cover is one Metal shader;
 the teeth are three batched paths a frame; the brass and leather are bitmaps drawn once. The loop
 is in ZipperUnlockMotion.swift (loop = 9 s).

 Touch: the tab follows the finger (the slider rides the finger's height, rubber-banded past both
 stops); the tab points at the finger on a stiff spring (ω 24, ζ 0.9) and swings free as a
 pendulum when let go (ω 7, ζ 0.16). Released past 70 % (or flicked so its projection passes it)
 the slider springs to the bottom (ω 13, ζ 0.82) and the halves swing open in 0.6 s (ease-out);
 short of it, it zips back up on the same spring. A selection tick at 70 %, a light impact when
 the halves let go and when the zipper closes. 2 s after the last touch (or on the lock button)
 the halves come back in 0.35 s and the slider rises in 0.55 s, then the loop restarts.
 Reduced motion: no loop, critically damped springs, the halves fade open instead of swinging.
*/

struct ZipperUnlock: View {
    /// Plays the 9 s demo: grab, unzip, open the journal, zip back up.
    var autoPlay = false

    static let loop = ZipperUnlockMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = ZipperUnlockEngine()
    /// True while a touch or its springs still move things outside autoPlay.
    @State private var live = false

    typealias M = ZipperUnlockMotion

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
        let zip = M.Zip(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
        let art = ZipperUnlockArt.make(scale: min(3, displayScale))
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        engine.zip = zip
        engine.onRest = { live = false }

        return TimelineView(.animation(paused: !(auto || live))) { timeline in
            let s = engine.step(to: timeline.date, auto: auto, reduced: reduced)
            let open = reduced ? 0 : s.o
            let ys = zip.sliderY(s.p)
            let sliderX = zip.cx + (size.width / 2 + 70) * open
            ZStack(alignment: .topLeading) {
                ZipperUnlockJournal(since: s.since, pulled: s.p, insets: insets) {
                    live = true
                    engine.lock()
                }
                .frame(width: size.width, height: size.height)

                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.zipperUnlockDenim(
                            .float2(max(1, size.width), max(1, size.height)),
                            .float4(zip.yTop, ys, zip.yEnd, zip.gapMax),
                            .float2(open, 6 + 22 * s.o + 6 * M.smooth(s.p * 3))
                        ))
                        .frame(width: size.width, height: size.height)
                    cover(s, zip: zip, art: art, ys: ys, sliderX: sliderX, open: open, size: size)
                }
                .compositingGroup()
                .opacity(reduced ? 1 - s.o : 1)
                .allowsHitTesting(false)

                Text("Pull the tab down to open")
                    .font(.system(size: 13, weight: .semibold))
                    .kerning(0.4)
                    .foregroundStyle(Color.rgba(236, 226, 206, 0.62))
                    .frame(width: size.width - 48)
                    .frame(width: size.width, height: size.height - insets.bottom - 34, alignment: .bottom)
                    .opacity((1 - M.smooth(s.p * 8)) * (1 - s.o))
                    .allowsHitTesting(false)

                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: 110, height: 130)
                    .offset(x: sliderX - 55, y: ys - 22)
                    .gesture(
                        DragGesture(minimumDistance: 0, coordinateSpace: .named("zipperUnlock"))
                            .onChanged { v in
                                if engine.dragChanged(v) { live = true }
                            }
                            .onEnded { _ in engine.dragEnded() }
                    )
                    .accessibilityElement()
                    .accessibilityAddTraits(.allowsDirectInteraction)
                    .accessibilityLabel("Zipper pull. Drag down to open the journal.")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .coordinateSpace(name: "zipperUnlock")
        .background(Color(hex: 0x17243F))
        .clipped()
    }

    /// The patch, the teeth, the bottom stop, the slider and its tab, over the denim.
    private func cover(_ s: ZipperUnlockEngine.State, zip: M.Zip, art: ZipperUnlockArt.Pieces, ys: Double, sliderX: Double, open: Double, size: CGSize) -> some View {
        Canvas { ctx, _ in
            func draw(_ piece: ZipperUnlockArt.Piece, x: Double, y: Double, rotation: Double = 0, scale: Double = 1, opacity: Double = 1) {
                var c = ctx
                c.opacity = opacity
                c.translateBy(x: x, y: y)
                if rotation != 0 { c.rotate(by: .radians(rotation)) }
                if scale != 1 { c.scaleBy(x: scale, y: scale) }
                c.draw(piece.image, in: piece.rect)
            }
            let patchY = size.height * 0.34
            draw(art.patch, x: size.width * 0.25 - zip.gapAt(patchY, ys: ys, o: open), y: patchY, rotation: -0.04)

            let teeth = ZipperUnlockArt.teeth(zip, ys: ys, o: open)
            ctx.fill(teeth.shadow, with: .color(Color.rgba(4, 8, 22, 0.5)))
            ctx.fill(teeth.body, with: .color(Color(hex: 0xC89A4C)))
            ctx.stroke(teeth.body, with: .color(Color(hex: 0x6B4A1B)), lineWidth: 0.6)
            ctx.fill(teeth.light, with: .color(Color.rgba(250, 228, 168, 0.85)))

            draw(art.stop, x: zip.cx - zip.gapAt(zip.yEnd, ys: ys, o: open) + 1, y: zip.yEnd)
            draw(art.slider, x: sliderX, y: ys)
            draw(art.tabShadow, x: sliderX + 2 + 5 * s.lift, y: ys + M.tabPivot + 3 + 7 * s.lift, rotation: s.angle, opacity: 0.45 - 0.15 * s.lift)
            draw(art.tab, x: sliderX, y: ys + M.tabPivot, rotation: s.angle, scale: 1 + 0.06 * s.lift)
        }
        .frame(width: size.width, height: size.height)
    }
}

/// The zipper, stepped once a frame like the React Native frame callback.
@MainActor
final class ZipperUnlockEngine {
    typealias M = ZipperUnlockMotion

    struct State {
        var p = 0.0
        var o = 0.0
        var lift = 0.0
        var angle = 0.0
        var since = -1.0
    }

    var zip = ZipperUnlockMotion.Zip()
    var onRest: () -> Void = {}

    /// 0 loop, 1 dragging, 2 settling, 3 closing.
    private var mode = 0
    private var s = State()
    private var pV = 0.0
    private var oLin = 0.0
    private var angleV = 0.0
    private var target = 0.0
    private var idle = 0.0
    private var ret = 0.0
    private var fromP = 0.0
    private var fromO = 0.0
    private var startYs = 0.0
    private var finger = CGPoint.zero
    private var touching = false
    private var grabbing = false
    private var armed = false
    private var loopStart: Date?
    private var last: Date?
    private var wasAuto: Bool?
    private var auto = false
    private var reduced = false
    private let tick = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    func step(to date: Date, auto: Bool, reduced: Bool) -> State {
        self.auto = auto
        self.reduced = reduced
        if wasAuto != auto {
            wasAuto = auto
            loopStart = date
            if mode != 1 { mode = 0 }
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 1.0 / 20)

        if mode == 0 {
            if !auto {
                s = State()
                return s
            }
            let t = max(0, date.timeIntervalSince(loopStart ?? date)).truncatingRemainder(dividingBy: M.loop)
            let sc = M.script(t)
            s = State(p: sc.p, o: sc.o, lift: sc.lift, angle: sc.angle, since: sc.since)
            return s
        }
        // The pull tab: aims at the finger while held, a free pendulum otherwise.
        let ys = zip.sliderY(s.p)
        let sx = zip.cx + (zip.width / 2 + 70) * s.o
        var aim = 0.0
        var w = 7.0
        var z = reduced ? 1 : 0.16
        if mode == 1 {
            aim = M.clamp(atan2(-(finger.x - sx), max(18, finger.y - ys - M.tabPivot)), -1.1, 1.1)
            w = 24
            z = 0.9
        }
        let acc = -w * w * (s.angle - aim) - 2 * z * w * angleV
        angleV += acc * dt
        s.angle += angleV * dt
        s.lift += ((mode == 1 ? 1 : 0) - s.lift) * min(1, dt * 12)

        if mode == 2 {
            let goal = target
            let sw = 13.0
            let sz = reduced ? 1 : 0.82
            let a = -sw * sw * (s.p - goal) - 2 * sz * sw * pV
            pV += a * dt
            s.p += pV * dt
            if goal == 1 { s.p = min(s.p, 1) }
            if goal == 1 && s.p > 0.985 && oLin < 1 {
                if oLin == 0 { light.impactOccurred() }
                oLin = min(1, oLin + dt / M.openFor)
                s.o = M.easeOut(oLin)
            }
            if goal == 0 && armed && s.p < 0.01 {
                armed = false
                light.impactOccurred()
            }
            if s.o > 0 { s.since = s.since < 0 ? 0 : s.since + dt }
            let settled = abs(s.p - goal) < 0.004 && abs(pV) < 0.03 && (goal == 0 || oLin >= 1)
            if settled { idle += dt }
            if idle > M.idleBeforeReturn { close() }
        } else if mode == 3 {
            ret += dt
            s.o = fromO * (1 - M.easeInOut(ret / M.closeHalvesFor))
            let k = fromO > 0 ? M.closeHalvesFor : 0
            s.p = fromP * (1 - M.easeInOut((ret - k) / M.closeSliderFor))
            if s.o <= 0.001 { s.since = -1 } else { s.since += dt }
            if ret >= k + M.closeSliderFor {
                mode = 0
                loopStart = date
                oLin = 0
                s.o = 0
                s.p = 0
                if !auto { DispatchQueue.main.async { self.onRest() } }
            }
        }
        return s
    }

    private func close() {
        mode = 3
        ret = 0
        fromP = s.p
        fromO = s.o
    }

    func lock() {
        if mode == 2 && s.o > 0 { close() }
    }

    private func rubber(_ raw: Double) -> Double {
        let range = zip.range
        if raw < 0 {
            let over = -raw * range
            return -over / (1 + over / 60) / range
        }
        if raw > 1 {
            let over = (raw - 1) * range
            return 1 + over / (1 + over / 40) / range
        }
        return raw
    }

    /// Returns true when the tab is held (the timeline must run).
    func dragChanged(_ v: DragGesture.Value) -> Bool {
        if !touching {
            touching = true
            // The tab can't be caught while the halves are swinging open.
            grabbing = s.o <= 0.02
            guard grabbing else { return false }
            if mode == 0 {
                angleV = 0
                pV = 0
            }
            mode = 1
            startYs = zip.sliderY(s.p)
            finger = v.location
            tick.prepare()
            light.prepare()
            return true
        }
        guard grabbing else { return false }
        let before = s.p
        let next = rubber((startYs + v.translation.height - zip.ys0) / zip.range)
        s.p = next
        pV = v.velocity.height / zip.range
        finger = v.location
        if (before - M.openAt) * (next - M.openAt) < 0 { tick.selectionChanged() }
        if next > 0.05 { armed = true }
        return true
    }

    func dragEnded() {
        defer { touching = false }
        guard grabbing else { return }
        grabbing = false
        let projected = s.p + pV * 0.15
        target = projected >= M.openAt ? 1 : 0
        oLin = 0
        idle = 0
        mode = 2
    }
}
