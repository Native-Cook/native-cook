import SwiftUI
import UIKit

/*
 Tile Disc Radio — the now-playing screen of an invented radio app ("parquet.fm").

 Look: the page is the station's colour. A square cover of rounded "petal" tiles fills the top, and on
 it a big disc made of the same kind of tiles in another palette, separated by thin coloured lines, with
 the station's words set over it like a poster. Under it the station, the show, a tuner dial and the
 transport. Everything is drawn in code.

 Hero: changing station turns the disc a quarter turn (a 4° lean back, then a spring with a little
 overshoot) while each row of tiles runs like a conveyor, carrying the old station's tiles out and the
 new station's in, rows alternating direction with a 60 ms stagger and the gap lines widening as they
 slide. The background tiles recolour in a diagonal wave, the page colour follows, the words re-set
 one by one and the tuner needle scans to the new frequency.

 autoPlay: loop = 9 s. Rest on Lumen Sounds, "next" at 1.5 s, 4.3 s and 7.1 s (three stations), so
 the loop ends where it began; the disc has turned three quarter turns, and since every station's
 layout is drawn turned with it, the last frame is the first.
 Touch: swipe sideways on the cover to turn the disc with the finger: past 22° (or thrown) it changes
 station that way, else it springs back. Tap ⏮ / ⏭ to change, the middle button to pause. 2 s after
 the last touch it goes back to the first station and the loop restarts.
 Reduced motion: no turn and no conveyor; a change crossfades in 0.3 s, and the loop is off.
*/
struct TileDiscRadio: View {
    /// Plays the 9 s station-change loop until touched.
    var autoPlay = false

    typealias M = TileDiscRadioModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = TileDiscRadioDriver()
    @State private var live = false
    @State private var token = 0
    @State private var grab = TileDiscRadioGrab.none

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
        .background(Self.color(M.stations[0].frame).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = TileDiscRadioGeo(w: max(1, size.width), h: max(1, size.height), top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return TimelineView(.animation(paused: !auto && !live)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let f = driver.frame(at: now, auto: auto, reduced: reduced)
            Canvas { c, _ in
                Self.draw(&c, g, f)
            }
            .frame(width: size.width, height: size.height)
        }
        .contentShape(Rectangle())
        .gesture(touch(g, auto: auto, reduced: reduced))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Now playing. Swipe the cover or use the buttons to change station.")
        .accessibilityAction(named: "Next station") { tap(1, auto: auto, reduced: reduced) }
        .accessibilityAction(named: "Previous station") { tap(-1, auto: auto, reduced: reduced) }
        .accessibilityAction(named: "Play or pause") { tap(0, auto: auto, reduced: reduced) }
        .onChange(of: auto) { driver.reset() }
    }

    // MARK: Colours

    static let ink: UInt32 = 0xFFF8F2
    static let coverInk: UInt32 = 0x160D22
    static let liveRed: UInt32 = 0xFF3B4A

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    /// Two colours mixed in sRGB.
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double, _ alpha: Double = 1) -> Color {
        func ch(_ v: UInt32, _ s: UInt32) -> Double { Double((v >> s) & 0xFF) / 255 }
        return Color(.sRGB, red: M.lerp(ch(a, 16), ch(b, 16), t), green: M.lerp(ch(a, 8), ch(b, 8), t),
                     blue: M.lerp(ch(a, 0), ch(b, 0), t), opacity: alpha)
    }

    static func text(_ s: String, _ size: CGFloat, _ weight: Font.Weight, _ color: Color, tracking: CGFloat = 0) -> Text {
        Text(s).font(.system(size: size, weight: weight)).tracking(tracking).foregroundColor(color)
    }

    // MARK: Drawing

    static func draw(_ c: inout GraphicsContext, _ g: TileDiscRadioGeo, _ f: TileDiscRadioFrame) {
        let a = M.stations[f.ch.from]
        let b = M.stations[f.ch.to]
        let page = f.reduced ? M.fade(f.ct) : M.pageMix(f.ct)
        c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(mix(a.frame, b.frame, page)))
        header(&c, g, f)
        background(&c, g, f)
        disc(&c, g, f)
        // The disc's edge catches a little light at the top.
        let rim = Path(ellipseIn: CGRect(x: g.cx - g.radius + 0.5, y: g.discY - g.radius + 0.5,
                                         width: g.radius * 2 - 1, height: g.radius * 2 - 1))
        c.stroke(rim, with: .linearGradient(Gradient(colors: [.white.opacity(0.35), .white.opacity(0), .black.opacity(0.12)]),
                                            startPoint: CGPoint(x: g.cx, y: g.discY - g.radius),
                                            endPoint: CGPoint(x: g.cx, y: g.discY + g.radius)), lineWidth: 1)
        words(&c, g, f)
        titles(&c, g, f)
        tuner(&c, g, f)
        transport(&c, g, f, page)
    }

    static func header(_ c: inout GraphicsContext, _ g: TileDiscRadioGeo, _ f: TileDiscRadioFrame) {
        let y = g.top + 24
        c.draw(text("parquet.fm", 20, .heavy, color(ink), tracking: -0.4), at: CGPoint(x: 20, y: y), anchor: .leading)
        let w = 84.0
        let x = g.w - 20 - w
        let pill = Path(roundedRect: CGRect(x: x, y: y - 14, width: w, height: 28), cornerRadius: 14)
        c.stroke(pill, with: .color(color(ink, 0.35)), lineWidth: 1.5)
        // The dot breathes once a second (nine times per loop).
        let pulse = f.playing ? 0.55 + 0.45 * pow(cos(Double.pi * f.clock), 2) : 1
        let dot = f.playing ? color(liveRed, pulse) : color(ink, 0.45)
        c.fill(Path(ellipseIn: CGRect(x: x + 14 - 4, y: y - 4, width: 8, height: 8)), with: .color(dot))
        if f.playing {
            c.fill(Path(ellipseIn: CGRect(x: x + 14 - 8, y: y - 8, width: 16, height: 16)),
                   with: .color(color(liveRed, 0.25 * (1 - pulse))))
        }
        c.draw(text(f.playing ? "LIVE" : "PAUSED", 12, .bold, color(ink), tracking: 1.2),
               at: CGPoint(x: x + 26, y: y), anchor: .leading)
    }

    /// A rounded rect with a big radius on the masked corners.
    static func tilePath(_ r: CGRect, mask: Int, small: Double, big: Double) -> Path {
        func rad(_ bit: Int) -> Double { mask & bit != 0 ? big : small }
        return UnevenRoundedRectangle(topLeadingRadius: rad(1), bottomLeadingRadius: rad(8),
                                      bottomTrailingRadius: rad(4), topTrailingRadius: rad(2), style: .circular)
            .path(in: r)
    }

    static func cellTiles(_ code: Int, x: Double, y: Double, size: Double, inset: Double, small: Double) -> [(Path)] {
        M.tiles(code).map { t in
            let r = CGRect(x: x + t.x * size + inset, y: y + t.y * size + inset,
                           width: t.w * size - 2 * inset, height: t.h * size - 2 * inset)
            let big = max(0, min(r.width, r.height) / 2)
            return tilePath(r, mask: t.mask, small: min(small, big), big: big)
        }
    }

    static func background(_ c: inout GraphicsContext, _ g: TileDiscRadioGeo, _ f: TileDiscRadioFrame) {
        let a = M.stations[f.ch.from]
        let b = M.stations[f.ch.to]
        for j in 0..<4 {
            for i in 0..<4 {
                let t = f.reduced ? M.fade(f.ct) : M.bgMix(f.ct, i, j, f.ch.dir)
                let x = g.coverX + 7 + Double(i) * g.bgCell
                let y = g.coverY + 7 + Double(j) * g.bgCell
                let col = mix(a.bgTile, b.bgTile, t)
                for p in cellTiles(M.bgLayout[j * 4 + i], x: x, y: y, size: g.bgCell, inset: 1.5, small: 6) {
                    c.fill(p, with: .color(col))
                }
            }
        }
    }

    static func disc(_ c: inout GraphicsContext, _ g: TileDiscRadioGeo, _ f: TileDiscRadioFrame) {
        let a = M.stations[f.ch.from]
        let b = M.stations[f.ch.to]
        let circle = Path(ellipseIn: CGRect(x: g.cx - g.radius, y: g.discY - g.radius, width: g.radius * 2, height: g.radius * 2))
        // Two shadows: a soft ambient one and a tight contact one.
        var s1 = c
        s1.addFilter(.blur(radius: 18))
        s1.fill(circle.offsetBy(dx: 0, dy: 10), with: .color(.black.opacity(0.22)))
        var s2 = c
        s2.addFilter(.blur(radius: 3))
        s2.fill(circle.offsetBy(dx: 0, dy: 2), with: .color(.black.opacity(0.25)))

        var d = c
        d.clip(to: circle)
        let gap = f.reduced ? M.fade(f.ct) : M.gapMix(f.ct)
        d.fill(circle, with: .color(mix(a.line, b.line, gap)))
        d.translateBy(x: g.cx, y: g.discY)
        d.rotate(by: .degrees(f.angle))
        let cell = g.cell
        if f.reduced {
            layer(&d, station: f.ch.from, k: f.ch.kFrom, color: color(a.tile), cell: cell)
            var n = d
            n.opacity = M.fade(f.ct)
            layer(&n, station: f.ch.to, k: f.ch.kTo, color: color(b.tile), cell: cell)
            return
        }
        for r in 0..<4 {
            let p = f.ch.dir == 0 ? 1 : M.rowP(f.ct, r)
            let sgn = Double(r % 2 == 0 ? 1 : -1) * Double(f.ch.dir == 0 ? 1 : f.ch.dir)
            let inset = 1.5 + 2.5 * sin(Double.pi * p)
            let y = (Double(r) - 2) * cell
            for col in 0..<4 {
                let x = (Double(col) - 2) * cell
                if p < 1 {
                    let ox = x + sgn * 4 * cell * p
                    for path in cellTiles(M.localCode(f.ch.from, f.ch.kFrom, col, r), x: ox, y: y, size: cell, inset: inset, small: 5) {
                        d.fill(path, with: .color(color(a.tile)))
                    }
                }
                if p > 0 {
                    let nx = x - sgn * 4 * cell * (1 - p)
                    for path in cellTiles(M.localCode(f.ch.to, f.ch.kTo, col, r), x: nx, y: y, size: cell, inset: inset, small: 5) {
                        d.fill(path, with: .color(color(b.tile)))
                    }
                }
            }
        }
    }

    static func layer(_ d: inout GraphicsContext, station: Int, k: Int, color: Color, cell: Double) {
        for r in 0..<4 {
            for col in 0..<4 {
                for path in cellTiles(M.localCode(station, k, col, r), x: (Double(col) - 2) * cell, y: (Double(r) - 2) * cell,
                                      size: cell, inset: 1.5, small: 5) {
                    d.fill(path, with: .color(color))
                }
            }
        }
    }

    /// The poster's words: station, ×, call sign, frequency, volume.
    static func posterWords(_ s: TileDiscRadioStation) -> [(String, Double, Double)] {
        let parts = s.name.uppercased().split(separator: " ").map(String.init)
        return [
            (parts[0], 0.05, 0.355),
            (parts.count > 1 ? parts[1] : "", 0.05, 0.395),
            ("×", 0.29, 0.355),
            (s.call, 0.52, 0.355),
            (String(format: "%.1f FM", s.freq), 0.755, 0.355),
            (s.vol, 0.80, 0.935),
        ]
    }

    static func words(_ c: inout GraphicsContext, _ g: TileDiscRadioGeo, _ f: TileDiscRadioFrame) {
        let size = max(10, g.side * 0.034)
        func put(_ s: TileDiscRadioStation, alpha: (Int) -> Double, rise: (Int) -> Double) {
            for (n, w) in posterWords(s).enumerated() {
                let al = alpha(n)
                if al <= 0.001 || w.0.isEmpty { continue }
                c.draw(text(w.0, size, .medium, color(coverInk, al), tracking: 0.2),
                       at: CGPoint(x: g.coverX + w.1 * g.side, y: g.coverY + w.2 * g.side + rise(n)), anchor: .leading)
            }
        }
        let a = M.stations[f.ch.from]
        let b = M.stations[f.ch.to]
        if f.ch.from == f.ch.to {
            put(b, alpha: { _ in 1 }, rise: { _ in 0 })
            return
        }
        if f.reduced {
            let t = M.fade(f.ct)
            put(a, alpha: { _ in 1 - t }, rise: { _ in 0 })
            put(b, alpha: { _ in t }, rise: { _ in 0 })
            return
        }
        let out = M.wordsOut(f.ct)
        put(a, alpha: { _ in out }, rise: { _ in 0 })
        put(b, alpha: { n in M.wordIn(f.ct, n) }, rise: { n in 6 * (1 - M.wordIn(f.ct, n)) })
    }

    static func titles(_ c: inout GraphicsContext, _ g: TileDiscRadioGeo, _ f: TileDiscRadioFrame) {
        func block(_ s: TileDiscRadioStation, dx: Double, alpha: Double) {
            guard alpha > 0.001 else { return }
            var l = c
            l.opacity = alpha
            l.translateBy(x: dx, y: 0)
            l.draw(text("NOW ON AIR · \(s.call) \(String(format: "%.1f", s.freq)) FM", 12, .semibold, color(ink, 0.72), tracking: 1.4),
                   at: CGPoint(x: 20, y: g.y0 + 34), anchor: .leading)
            l.draw(text(s.name, 32, .bold, color(ink), tracking: -0.6), at: CGPoint(x: 20, y: g.y0 + 66), anchor: .leading)
            l.draw(text("\(s.show) · \(s.listening) listening", 15, .regular, color(ink, 0.72)),
                   at: CGPoint(x: 20, y: g.y0 + 97), anchor: .leading)
        }
        let a = M.stations[f.ch.from]
        let b = M.stations[f.ch.to]
        if f.ch.from == f.ch.to {
            block(b, dx: 0, alpha: 1)
            return
        }
        let dir = Double(f.ch.dir)
        if f.reduced {
            let t = M.fade(f.ct)
            block(a, dx: 0, alpha: 1 - t)
            block(b, dx: 0, alpha: t)
            return
        }
        let o = M.titleOut(f.ct)
        block(a, dx: -20 * dir * o, alpha: 1 - o)
        let i = M.titleIn(f.ct)
        block(b, dx: 24 * dir * (1 - i), alpha: i)
    }

    static func tuner(_ c: inout GraphicsContext, _ g: TileDiscRadioGeo, _ f: TileDiscRadioFrame) {
        let a = M.stations[f.ch.from]
        let b = M.stations[f.ch.to]
        let t = f.reduced ? M.fade(f.ct) : M.freqMix(f.ct)
        let freq = M.lerp(a.freq, b.freq, t)
        let y = g.tunerY
        let half = g.w / 2 - 20
        let perMHz = 40.0
        let lo = Int(floor((freq - half / perMHz) * 5)) - 1
        let hi = Int(ceil((freq + half / perMHz) * 5)) + 1
        for n in lo...hi {
            let mhz = Double(n) / 5
            if mhz < 87.5 || mhz > 108.0 { continue }
            let dx = (mhz - freq) * perMHz
            if abs(dx) > half { continue }
            let fade = 1 - pow(abs(dx) / half, 3)
            let major = n % 5 == 0
            let h = major ? 16.0 : 8.0
            c.fill(Path(CGRect(x: g.cx + dx - 0.75, y: y + 8 - h, width: 1.5, height: h)),
                   with: .color(color(ink, (major ? 0.8 : 0.4) * fade)))
            if major && (n / 5) % 2 == 0 {
                c.draw(text("\(n / 5)", 11, .semibold, color(ink, 0.6 * fade)), at: CGPoint(x: g.cx + dx, y: y + 24), anchor: .center)
            }
        }
        // The needle in the disc's tile colour.
        let needle = mix(a.tile, b.tile, t)
        c.fill(Path(roundedRect: CGRect(x: g.cx - 1, y: y - 20, width: 2, height: 32), cornerRadius: 1), with: .color(needle))
        c.fill(Path(ellipseIn: CGRect(x: g.cx - 4, y: y - 24, width: 8, height: 8)), with: .color(needle))
    }

    static func transport(_ c: inout GraphicsContext, _ g: TileDiscRadioGeo, _ f: TileDiscRadioFrame, _ page: Double) {
        let a = M.stations[f.ch.from]
        let b = M.stations[f.ch.to]
        let y = g.transportY
        // Play / pause.
        c.fill(Path(ellipseIn: CGRect(x: g.cx - 36, y: y - 36, width: 72, height: 72)), with: .color(color(ink)))
        let glyph = mix(a.frame, b.frame, page)
        if f.playing {
            c.fill(Path(roundedRect: CGRect(x: g.cx - 9, y: y - 12, width: 6, height: 24), cornerRadius: 2), with: .color(glyph))
            c.fill(Path(roundedRect: CGRect(x: g.cx + 3, y: y - 12, width: 6, height: 24), cornerRadius: 2), with: .color(glyph))
        } else {
            var p = Path()
            p.move(to: CGPoint(x: g.cx - 8, y: y - 13))
            p.addLine(to: CGPoint(x: g.cx + 13, y: y))
            p.addLine(to: CGPoint(x: g.cx - 8, y: y + 13))
            p.closeSubpath()
            c.fill(p, with: .color(glyph))
        }
        for side in [-1, 1] {
            let pressed = f.reduced ? 1 : (f.ch.press == side ? M.press(f.ct) : 1)
            var l = c
            l.translateBy(x: g.cx + Double(side) * 108, y: y)
            l.scaleBy(x: pressed, y: pressed)
            l.fill(skip(Double(side)), with: .color(color(ink)))
        }
    }

    /// ⏭ (side +1) or ⏮ (side −1), centred on 0.
    static func skip(_ side: Double) -> Path {
        var p = Path()
        for k in [0.0, 1.0] {
            let x0 = (-13 + k * 12) * side
            p.move(to: CGPoint(x: x0, y: -8))
            p.addLine(to: CGPoint(x: x0 + 12 * side, y: 0))
            p.addLine(to: CGPoint(x: x0, y: 8))
            p.closeSubpath()
        }
        p.addRect(CGRect(x: side > 0 ? 11 : -13, y: -8, width: 2.5, height: 16))
        return p
    }

    // MARK: Touch

    private func bump() {
        live = true
        token += 1
        let mine = token
        Task { @MainActor in
            while true {
                try? await Task.sleep(nanoseconds: 300_000_000)
                guard mine == token else { return }
                if !driver.live(at: Date().timeIntervalSinceReferenceDate) {
                    live = false
                    return
                }
            }
        }
    }

    private func haptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func tap(_ button: Int, auto: Bool, reduced: Bool) {
        let now = Date().timeIntervalSinceReferenceDate
        if driver.tap(button, at: now, auto: auto, reduced: reduced) { haptic() }
        bump()
    }

    private func touch(_ g: TileDiscRadioGeo, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if grab == .none {
                    let target = g.target(at: v.startLocation)
                    grab = target
                    if target == .disc {
                        if reduced || !driver.beginDrag(at: now, auto: auto) { grab = .ignored }
                        bump()
                    }
                }
                if grab == .disc {
                    if driver.drag(v.translation.width, radius: g.radius, at: now) { haptic() }
                }
            }
            .onEnded { v in
                let now = Date().timeIntervalSinceReferenceDate
                let moved = hypot(v.translation.width, v.translation.height)
                switch grab {
                case .disc:
                    if driver.endDrag(velocity: v.velocity.width, at: now) { haptic() }
                    bump()
                case .prev where moved < 12: tap(-1, auto: auto, reduced: reduced)
                case .next where moved < 12: tap(1, auto: auto, reduced: reduced)
                case .play where moved < 12: tap(0, auto: auto, reduced: reduced)
                default: break
                }
                grab = .none
            }
    }
}

enum TileDiscRadioGrab {
    case none, ignored, disc, prev, next, play
}

struct TileDiscRadioGeo {
    let w: Double
    let h: Double
    let top: Double
    let bottom: Double

    var cx: Double { w / 2 }
    var coverX: Double { 16 }
    var coverY: Double { top + 52 }
    var side: Double { w - 32 }
    var bgCell: Double { (side - 14) / 4 }
    var discY: Double { coverY + side / 2 }
    var radius: Double { side * 0.45 }
    var cell: Double { radius * 2 / 3.4 }
    var y0: Double { coverY + side }
    var tunerY: Double { (y0 + 97 + transportY) / 2 + 4 }
    var transportY: Double { h - max(bottom, 16) - 62 }

    func target(at p: CGPoint) -> TileDiscRadioGrab {
        if p.y >= coverY && p.y <= coverY + side { return .disc }
        if abs(p.y - transportY) <= 40 {
            if abs(p.x - cx) <= 40 { return .play }
            if abs(p.x - (cx - 108)) <= 34 { return .prev }
            if abs(p.x - (cx + 108)) <= 34 { return .next }
        }
        return .ignored
    }
}
