import SwiftUI
import UIKit

/*
 Glass Mood Bloom — the mood check-in of Petalog, an invented journal app.

 Look: a soft colour wash, a two-line title, a layered glass flower in the middle, the mood word, a capsule slider
 from "Very unpleasant" to "Very pleasant" and a Next button. Everything is drawn in code.
 Hero: the flower is six stacked glass plates, each a closed shape whose radius is a function of angle
 (r = R · (rv + (1 − rv) · ((1 + cos n(θ − rot)) / 2)^p)). Petal count, valley depth, petal profile, the twist
 between plates, the rotation and the hue are continuous functions of the mood value, so the morph is a real shape
 change: a calm blue circle (Neutral) becomes a green pentagon, a yellow star and an orange five-lobed flower, or a
 blue scalloped octagon and a violet eight-pointed star. Inner plates lead and outer plates follow 45 ms each, so a
 change blooms out of the core. Each plate has a radial fill, folded facets, an inner glow, a back edge for its
 thickness and a bright rim; a white core glows in the middle.
 Supporting: the background, the word (which slides when it changes), the slider and the Next button follow the hue.

 autoPlay: loop = 10 s; rest at Neutral → Slightly Pleasant → Pleasant → Very Pleasant (hold) → Neutral →
 Slightly Unpleasant → Unpleasant → Very Unpleasant (hold) → Neutral (rest). Frame 10 s equals frame 0.
 Touch: drag the slider, or drag across the flower (160 pt = half the range). Release springs to the nearest mood.
 A light haptic when the word changes. 2 s after the last touch the flower eases back to Neutral and the loop restarts.
 Reduced motion: no loop, no breathing, no lag; Neutral rests and dragging sets the shape directly.
*/
struct GlassMoodBloom: View {
    var autoPlay = false

    static let loop: Double = 10
    typealias M = GlassMoodBloomModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = GlassMoodBloomDriver()
    @State private var live = false
    @State private var token = 0
    @State private var drag: GlassMoodBloomDrag?
    @State private var pressingNext = false

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(M.moods[3].bgTop.color().ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func stage(size: CGSize, top: CGFloat, bottom: CGFloat) -> some View {
        let lay = GlassMoodBloomLayout(w: size.width, h: size.height, top: top, bottom: bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        driver.auto = auto
        driver.reduced = reduced
        return TimelineView(.animation(paused: !auto && !live)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let f = GlassMoodBloomFrame(driver: driver, now: now, reduced: reduced)
            let look = M.look(f.v)
            ZStack(alignment: .topLeading) {
                Canvas { c, _ in
                    GlassMoodBloomArt.draw(&c, f: f, lay: lay, held: drag != nil, nextDown: pressingNext)
                }
                .frame(width: lay.w, height: lay.h)
                texts(f: f, look: look, lay: lay)
            }
            .frame(width: lay.w, height: lay.h, alignment: .topLeading)
        }
        .contentShape(Rectangle())
        .gesture(touch(lay: lay))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Choose how you're feeling right now")
        .accessibilityValue(M.moods[M.bucket(driver.value(Date.timeIntervalSinceReferenceDate))].name)
        .accessibilityAdjustableAction { direction in
            let now = Date.timeIntervalSinceReferenceDate
            let v = driver.value(now)
            let step = direction == .increment ? 1.0 / 3 : -1.0 / 3
            _ = driver.touchDown(now)
            driver.touchMove(now, (v * 3).rounded() / 3 + step)
            driver.touchUp(now)
            wake()
        }
        .onChange(of: auto) { driver.reset() }
        .onChange(of: reduced) { driver.reset() }
    }

    // MARK: Text

    @ViewBuilder
    private func texts(f: GlassMoodBloomFrame, look: M.Look, lay: GlassMoodBloomLayout) -> some View {
        let ink = M.RGB(0x2A3038)
        let nav = ink.mix(look.label, 0.35)
        Group {
            HStack(spacing: 3) {
                Image(systemName: "chevron.left").font(.system(size: 17, weight: .medium))
                Text("Back").font(.system(size: 17))
            }
            .foregroundStyle(nav.color(0.78))
            .position(x: 12 + 26, y: lay.navY)
            Text("Petalog")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(nav.color())
                .position(x: lay.w / 2, y: lay.navY)
            Text("Cancel")
                .font(.system(size: 17))
                .foregroundStyle(nav.color(0.78))
                .fixedSize()
                .frame(width: 120, alignment: .trailing)
                .position(x: lay.w - 16 - 60, y: lay.navY)
            Text("Choose how you\u{2019}re\nfeeling right now")
                .font(.system(size: 26, weight: .semibold))
                .lineSpacing(2)
                .multilineTextAlignment(.center)
                .foregroundStyle(M.RGB(0x23272E).mix(look.label, 0.25).color())
                .fixedSize()
                .position(x: lay.w / 2, y: lay.titleY)
            moodWord(f.labelPrev, look: look, alpha: f.prevAlpha, dy: f.prevDy, lay: lay)
            moodWord(f.label, look: look, alpha: f.nowAlpha, dy: f.nowDy, lay: lay)
            Text("VERY UNPLEASANT")
                .font(.system(size: 11, weight: .semibold)).tracking(0.5)
                .foregroundStyle(M.RGB(0x3E4650).mix(look.label, 0.3).color(0.72))
                .fixedSize()
                .frame(width: 160, alignment: .leading)
                .position(x: 36 + 80, y: lay.captionY)
            Text("VERY PLEASANT")
                .font(.system(size: 11, weight: .semibold)).tracking(0.5)
                .foregroundStyle(M.RGB(0x3E4650).mix(look.label, 0.3).color(0.72))
                .fixedSize()
                .frame(width: 160, alignment: .trailing)
                .position(x: lay.w - 36 - 80, y: lay.captionY)
            Text("Next")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.white)
                .scaleEffect(pressingNext ? 0.97 : 1)
                .position(x: lay.w / 2, y: lay.nextY)
        }
        .allowsHitTesting(false)
    }

    private func moodWord(_ i: Int, look: M.Look, alpha: Double, dy: Double, lay: GlassMoodBloomLayout) -> some View {
        Text(M.moods[i].name)
            .font(.system(size: 27, weight: .bold))
            .foregroundStyle(look.label.color())
            .fixedSize()
            .opacity(alpha)
            .position(x: lay.w / 2, y: lay.labelY + dy)
    }

    // MARK: Touch

    private func wake() {
        live = true
        token += 1
        let mine = token
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 4_500_000_000)
            if token == mine && drag == nil { live = false }
        }
    }

    private func touch(lay: GlassMoodBloomLayout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { g in
                let now = Date.timeIntervalSinceReferenceDate
                if drag == nil {
                    let p = g.startLocation
                    let kind: GlassMoodBloomDrag.Kind
                    if abs(p.y - lay.sliderY) < 30 && p.x > lay.trackX0 - 12 && p.x < lay.trackX1 + 12 {
                        kind = .slider
                    } else if hypot(p.x - lay.cx, p.y - lay.cy) < lay.radius * 1.15 {
                        kind = .flower
                    } else if abs(p.y - lay.nextY) < 30 && p.x > lay.trackX0 && p.x < lay.trackX1 {
                        pressingNext = true
                        drag = GlassMoodBloomDrag(kind: .next, startV: 0, bucket: 0)
                        return
                    } else {
                        drag = GlassMoodBloomDrag(kind: .none, startV: 0, bucket: 0)
                        return
                    }
                    let v0 = driver.touchDown(now)
                    drag = GlassMoodBloomDrag(kind: kind, startV: v0, bucket: M.bucket(v0))
                    wake()
                }
                guard var d = drag, d.kind == .slider || d.kind == .flower else { return }
                let v: Double
                if d.kind == .slider {
                    v = lay.value(atX: g.location.x)
                } else {
                    v = d.startV + Double(g.translation.width) / 160
                }
                let clamped = min(max(v, -1), 1)
                driver.touchMove(now, clamped)
                let b = M.bucket(clamped)
                if b != d.bucket {
                    d.bucket = b
                    drag = d
                    Self.impact()
                }
            }
            .onEnded { _ in
                let kind = drag?.kind
                drag = nil
                pressingNext = false
                if kind == .slider || kind == .flower {
                    driver.touchUp(Date.timeIntervalSinceReferenceDate)
                    wake()
                } else if kind == .next {
                    Self.impact()
                }
            }
    }

    static func impact() {
        let g = UIImpactFeedbackGenerator(style: .light)
        g.impactOccurred(intensity: 0.7)
    }
}

struct GlassMoodBloomDrag {
    enum Kind { case slider, flower, next, none }
    var kind: Kind
    var startV: Double
    var bucket: Int
}

// MARK: Layout

struct GlassMoodBloomLayout {
    let w: CGFloat, h: CGFloat
    let navY: CGFloat, titleY: CGFloat
    let nextY: CGFloat, sliderY: CGFloat, captionY: CGFloat, labelY: CGFloat
    let cx: CGFloat, cy: CGFloat, radius: CGFloat
    let trackX0: CGFloat, trackX1: CGFloat

    init(w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat) {
        self.w = w
        self.h = h
        navY = top + 29
        titleY = top + 129
        nextY = h - bottom - 32
        sliderY = nextY - 101
        captionY = sliderY + 35
        labelY = sliderY - 71
        let titleBottom = top + 163
        let labelTop = labelY - 18
        cx = w / 2
        cy = (titleBottom + labelTop) / 2
        radius = max(60, min(148, (labelTop - titleBottom) / 2 - 40, (w - 48) / 2))
        trackX0 = 32
        trackX1 = w - 32
    }

    var knobX0: CGFloat { trackX0 + 19 }
    var knobX1: CGFloat { trackX1 - 19 }

    func knobX(_ v: Double) -> CGFloat {
        knobX0 + (knobX1 - knobX0) * CGFloat((min(max(v, -1), 1) + 1) / 2)
    }

    func value(atX x: CGFloat) -> Double {
        Double((x - knobX0) / max(knobX1 - knobX0, 1)) * 2 - 1
    }
}

// MARK: Frame (everything a frame needs, from time)

struct GlassMoodBloomFrame {
    typealias M = GlassMoodBloomModel

    let v: Double
    let layerV: [Double]
    let swell: [Double]
    let loopT: Double
    let breath: Double
    let label: Int, labelPrev: Int
    let nowAlpha: Double, nowDy: Double, prevAlpha: Double, prevDy: Double

    init(driver: GlassMoodBloomDriver, now: Double, reduced: Bool) {
        driver.begin(now)
        v = driver.value(now)
        layerV = (0..<6).map { i in
            reduced ? driver.value(now) : driver.value(now - Double(5 - i) * M.layerLag)
        }
        // Plates swell a little while they change and settle when they arrive
        swell = (0..<6).map { i in
            if reduced { return 0 }
            let t = now - Double(5 - i) * M.layerLag
            return min(0.06, 0.5 * abs(driver.value(t) - driver.value(t - 0.1)))
        }
        loopT = driver.loopTime(now)
        breath = driver.breatheWeight(now)
        let b = M.bucket(v)
        label = b
        var change: Double?
        var prev = b
        for k in 1...18 {
            let t = now - Double(k) / 60
            let bb = M.bucket(driver.value(t))
            if bb != b {
                change = t + 1.0 / 120
                prev = bb
                break
            }
        }
        labelPrev = prev
        if let c = change {
            let e = now - c
            let out = min(max(e / 0.14, 0), 1)
            prevAlpha = 1 - out * out
            prevDy = -10 * out * out
            let inn = M.easeOut((e - 0.06) / 0.24)
            nowAlpha = inn
            nowDy = 10 * (1 - inn)
        } else {
            prevAlpha = 0
            prevDy = 0
            nowAlpha = 1
            nowDy = 0
        }
    }
}

// MARK: Drawing

enum GlassMoodBloomArt {
    typealias M = GlassMoodBloomModel

    static func draw(_ c: inout GraphicsContext, f: GlassMoodBloomFrame, lay: GlassMoodBloomLayout,
                     held: Bool, nextDown: Bool) {
        let look = M.look(f.v)
        let rect = CGRect(x: 0, y: 0, width: lay.w, height: lay.h)
        let center = CGPoint(x: lay.cx, y: lay.cy)
        let R = Double(lay.radius)

        // Background wash and the halo behind the flower
        c.fill(Path(rect), with: .linearGradient(
            Gradient(colors: [look.bgTop.color(), look.bgTop.mix(look.bgBottom, 0.55).color(), look.bgBottom.color()]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: lay.h)))
        let halo = R * 1.7
        c.fill(Path(ellipseIn: CGRect(x: center.x - halo, y: center.y - halo, width: halo * 2, height: halo * 2)),
               with: .radialGradient(Gradient(colors: [M.RGB.white.color(0.55), M.RGB.white.color(0.18),
                                                       M.RGB.white.color(0)]),
                                     center: center, startRadius: 0, endRadius: halo))

        drawFlower(&c, f: f, center: center, R: R)
        drawSlider(&c, f: f, look: look, lay: lay, held: held)

        // Next button
        let bw = lay.trackX1 - lay.trackX0
        let s: CGFloat = nextDown ? 0.97 : 1
        let br = CGRect(x: lay.cx - bw * s / 2, y: lay.nextY - 26 * s, width: bw * s, height: 52 * s)
        let capsule = Path(roundedRect: br, cornerRadius: 26 * s)
        c.drawLayer { l in
            l.addFilter(.shadow(color: look.button.color(0.28), radius: 10, x: 0, y: 6))
            l.fill(capsule, with: .color(look.button.color()))
        }
        c.fill(capsule, with: .linearGradient(
            Gradient(colors: [M.RGB.white.color(0.16), M.RGB.white.color(0)]),
            startPoint: CGPoint(x: 0, y: br.minY), endPoint: CGPoint(x: 0, y: br.midY)))
    }

    static func drawSlider(_ c: inout GraphicsContext, f: GlassMoodBloomFrame, look: M.Look,
                           lay: GlassMoodBloomLayout, held: Bool) {
        let track = CGRect(x: lay.trackX0, y: lay.sliderY - 19, width: lay.trackX1 - lay.trackX0, height: 38)
        let tp = Path(roundedRect: track, cornerRadius: 19)
        c.fill(tp, with: .color(M.RGB.white.color(0.34)))
        c.fill(tp, with: .color(look.deep.color(0.10)))
        c.stroke(tp, with: .color(M.RGB.white.color(0.55)), lineWidth: 0.6)
        let kx = lay.knobX(f.v)
        let kr: CGFloat = held ? 18.4 : 17
        let knob = Path(ellipseIn: CGRect(x: kx - kr, y: lay.sliderY - kr, width: kr * 2, height: kr * 2))
        c.drawLayer { l in
            l.addFilter(.shadow(color: Color.black.opacity(0.10), radius: 1.5, x: 0, y: 1))
            l.addFilter(.shadow(color: look.label.color(0.16), radius: 6, x: 0, y: 3))
            l.fill(knob, with: .color(.white))
        }
    }

    static func drawFlower(_ c: inout GraphicsContext, f: GlassMoodBloomFrame, center: CGPoint, R: Double) {
        let core = M.look(f.v)
        // Soft coloured glow under the plates
        let glow = R * 1.25
        c.fill(Path(ellipseIn: CGRect(x: center.x - glow, y: center.y - glow, width: glow * 2, height: glow * 2)),
               with: .radialGradient(Gradient(stops: [
                   .init(color: core.deep.color(0.30), location: 0),
                   .init(color: core.deep.color(0.16), location: 0.45),
                   .init(color: core.deep.color(0), location: 1),
               ]), center: center, startRadius: 0, endRadius: glow))

        for i in 0..<6 {
            let v = f.layerV[i]
            let look = M.look(v)
            let k = Double(i) / 5
            let side = i % 2 == 0 ? 1.0 : -1.0
            let wob = 1.6 * side * M.wobble(f.loopT, i) * f.breath
            let rotDeg = 24 * v - (i == 0 ? look.twist : 0) + wob
            let rot = -Double.pi / 2 + rotDeg * .pi / 180
            let radius = R * look.size * M.layerScale[i] * (1 + 0.012 * M.breathe(f.loopT, i) * f.breath + f.swell[i])
            let path = M.shape(center: center, radius: radius, look: look, rot: rot)
            let col = M.layerColor(look, k)
            let a = M.layerAlpha[i]

            // Back edge: the plate's thickness, a thin darker outline just below and right
            c.stroke(path.offsetBy(dx: 1.2, dy: 2.0), with: .color(look.deep.color(0.38)), lineWidth: 1)

            // Glass fill
            c.fill(path, with: .radialGradient(
                Gradient(colors: [col.mix(.white, 0.35).color(a), col.color(min(1, a * 1.15))]),
                center: center, startRadius: 0, endRadius: radius))

            var inner = c
            inner.clip(to: path)
            // Folded facets: each petal lit on one side of its ridge, shaded on the other
            let strength = (1 - M.valley(look)) * (0.3 + 0.7 * k)
            if strength > 0.01 {
                let n = Int(look.n)
                var stops: [Gradient.Stop] = []
                let light = M.RGB.white.color(0.42 * strength)
                let dark = look.deep.color(0.30 * strength)
                let clear = M.RGB.white.color(0)
                for j in 0..<n {
                    let s = Double(j) / Double(n), w = 1 / Double(n)
                    stops.append(.init(color: dark, location: s + 0.0005))
                    stops.append(.init(color: clear, location: s + w * 0.5))
                    stops.append(.init(color: light, location: s + w - 0.0005))
                }
                inner.fill(path, with: .conicGradient(Gradient(stops: stops), center: center,
                                                      angle: .radians(rot)))
            }
            // Inner glow along the rim
            inner.stroke(path, with: .color(look.deep.color(0.14 + 0.08 * k)), lineWidth: 12)
            inner.stroke(path, with: .color(look.deep.color(0.10)), lineWidth: 5)

            // Bright rim, lit from the top left
            let bb = path.boundingRect
            c.stroke(path, with: .linearGradient(
                Gradient(colors: [M.RGB.white.color(0.95), M.RGB.white.color(0.55), M.RGB.white.color(0.30)]),
                startPoint: CGPoint(x: bb.minX, y: bb.minY), endPoint: CGPoint(x: bb.maxX, y: bb.maxY)),
                lineWidth: 1.3)
        }

        // Glowing white core in the same shape
        let cg = 26.0
        c.fill(Path(ellipseIn: CGRect(x: center.x - cg, y: center.y - cg, width: cg * 2, height: cg * 2)),
               with: .radialGradient(Gradient(colors: [M.RGB.white.color(0.85), M.RGB.white.color(0.25),
                                                       M.RGB.white.color(0)]),
                                     center: center, startRadius: 0, endRadius: cg))
        let crot = -Double.pi / 2 + 24 * f.v * .pi / 180
        c.fill(M.shape(center: center, radius: 6.5, look: core, rot: crot, samples: 80),
               with: .color(.white))
    }
}
