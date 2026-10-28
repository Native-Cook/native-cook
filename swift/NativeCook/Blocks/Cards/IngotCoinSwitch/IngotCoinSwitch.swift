import SwiftUI
import UIKit

/*
 Ingot Coin Switch — the Explore screen of an invented investing app ("kesta").

 Look: a warm off-white page, a headline and a subline, and in the middle a ring of ten metal ingots
 (gold, copper, silver, platinum, palladium) standing on edge and leaning outward like a crown, turning
 slowly. Under it a ticker for the piece in front, the "Stocks / Commodities" switch and a dark CTA.
 Everything is drawn in code: each piece is a 3D body projected with perspective and shaded face by face,
 with a highlight band that slides across its face as it turns (IngotCoinSwitchModel.swift).

 Hero: switching to "Stocks" morphs every ingot into a thick frosted coin with an embossed letter: the
 outline goes from a rounded bar to a circle, the body thins, and each piece turns a quarter turn so the
 coins stand like the pages of a book, going translucent at the peak. The morph runs as a wave from the
 front piece to the sides; switching back undoes it.

 autoPlay: loop = 10 s. Rest on ingots (2.4 s), tap "Stocks" and morph, hold the coins, tap
 "Commodities" at 6.4 s and morph back, rest. The ring turns half a turn per loop (its pieces repeat
 every five), so the last frame is the first.
 Touch: tap a pill to switch; drag sideways to spin the ring, it keeps the finger's speed and slows
 down. 2 s after the last touch the ring turns home, the pieces go back to ingots and the loop restarts.
 Reduced motion: the ring stands still; the switch crossfades the shapes and the copy.
*/
struct IngotCoinSwitch: View {
    /// Plays the 10 s switch loop until touched.
    var autoPlay = false

    typealias M = IngotCoinSwitchModel
    typealias A = IngotCoinSwitchArt
    typealias RGB = IngotCoinSwitchRGB

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = IngotCoinSwitchDriver()
    @State private var live = false
    @State private var token = 0
    @State private var grab: IngotCoinSwitchGrab?

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
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
        .background(Self.color(Self.paper).ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = IngotCoinSwitchGeo(w: max(1, size.width), h: max(1, size.height),
                                   top: insets.top, bottom: insets.bottom)
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
        .accessibilityLabel("Explore. Switch between commodities and stocks.")
        .accessibilityAction(named: "Show stocks") { tap(1, auto: auto, reduced: reduced) }
        .accessibilityAction(named: "Show commodities") { tap(0, auto: auto, reduced: reduced) }
        .onChange(of: auto) { driver.reset() }
    }

    // MARK: Colours

    static let paper: UInt32 = 0xF5F4F1
    static let ink: UInt32 = 0x16181B
    static let muted: UInt32 = 0x6F6B65

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    static func color(_ c: RGB, _ a: Double = 1) -> Color {
        Color(.sRGB, red: c.r, green: c.g, blue: c.b, opacity: a)
    }

    // MARK: Copy

    static let headlines = [["Own real metal,", "from $1."], ["A slice of 2,000+", "companies."]]
    static let sublines = [["Gold, silver, copper and more, held in", "insured vaults. Capital at risk."],
                           ["Fractional shares from $1 and no fees on", "your first 50 trades. Capital at risk."]]
    static let ctas = ["Explore commodities", "Explore stocks"]

    // MARK: Drawing

    static func draw(_ c: inout GraphicsContext, _ g: IngotCoinSwitchGeo, _ f: IngotCoinSwitchFrame) {
        c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(color(paper)))
        header(&c, g)
        copy(&c, g, f)
        floor(&c, g, f)
        ring(&c, g, f)
        ticker(&c, g, f)
        toggle(&c, g, f)
        cta(&c, g, f)
    }

    static func text(_ s: String, _ size: CGFloat, _ weight: Font.Weight, _ color: Color, tracking: CGFloat = 0) -> Text {
        Text(s).font(.system(size: size, weight: weight)).tracking(tracking).foregroundColor(color)
    }

    static func header(_ c: inout GraphicsContext, _ g: IngotCoinSwitchGeo) {
        let y = g.top + 26
        c.draw(text("kesta", 22, .heavy, color(ink), tracking: -0.6), at: CGPoint(x: 24, y: y), anchor: .leading)
        let ax = g.w - 41
        c.fill(Path(ellipseIn: CGRect(x: ax - 17, y: y - 17, width: 34, height: 34)), with: .color(color(0xE7E4DE)))
        c.draw(text("AL", 13, .semibold, color(ink)), at: CGPoint(x: ax, y: y), anchor: .center)
        c.draw(text("EXPLORE", 12, .semibold, color(0x8B8780), tracking: 1.6), at: CGPoint(x: g.cx, y: g.top + 74), anchor: .center)
    }

    /// The headline, subline and CTA label of one mode, at an opacity and a rise.
    static func copy(_ c: inout GraphicsContext, _ g: IngotCoinSwitchGeo, _ f: IngotCoinSwitchFrame) {
        for (mode, alpha, rise) in copyLayers(f) {
            var l = c
            l.opacity = alpha
            l.translateBy(x: 0, y: rise)
            let lines = headlines[mode]
            l.draw(text(lines[0], 31, .bold, color(ink), tracking: -0.6), at: CGPoint(x: g.cx, y: g.top + 106), anchor: .center)
            l.draw(text(lines[1], 31, .bold, color(ink), tracking: -0.6), at: CGPoint(x: g.cx, y: g.top + 142), anchor: .center)
            let sub = sublines[mode]
            l.draw(text(sub[0], 15, .regular, color(muted)), at: CGPoint(x: g.cx, y: g.top + 184), anchor: .center)
            l.draw(text(sub[1], 15, .regular, color(muted)), at: CGPoint(x: g.cx, y: g.top + 204), anchor: .center)
        }
    }

    static func copyLayers(_ f: IngotCoinSwitchFrame) -> [(Int, Double, Double)] {
        if f.copyFrom == f.copyTo { return [(f.copyTo, 1, 0)] }
        let k = M.copyFade(f.copyU)
        var out: [(Int, Double, Double)] = []
        if k.out > 0.001 { out.append((f.copyFrom, k.out, 0)) }
        if k.inn > 0.001 { out.append((f.copyTo, k.inn, k.rise)) }
        return out
    }

    static func meanMorph(_ f: IngotCoinSwitchFrame) -> Double {
        f.m.reduce(0, +) / Double(f.m.count)
    }

    /// A soft shadow on the floor under the ring.
    static func floor(_ c: inout GraphicsContext, _ g: IngotCoinSwitchGeo, _ f: IngotCoinSwitchFrame) {
        let m = meanMorph(f)
        let y = g.ringY + M.lerp(124, 100, m)
        let w = M.lerp(310, 340, m)
        var l = c
        l.addFilter(.blur(radius: 14))
        l.fill(Path(ellipseIn: CGRect(x: g.cx - w / 2, y: y - 16, width: w, height: 32)), with: .color(.black.opacity(0.13)))
        var t = c
        t.addFilter(.blur(radius: 5))
        t.fill(Path(ellipseIn: CGRect(x: g.cx - w * 0.36, y: y - 5, width: w * 0.72, height: 10)), with: .color(.black.opacity(0.1)))
    }

    static func ring(_ c: inout GraphicsContext, _ g: IngotCoinSwitchGeo, _ f: IngotCoinSwitchFrame) {
        let pieces = M.pieces(f, cx: g.cx, cy: g.ringY)
        for p in pieces {
            if p.opacity < 0.999 {
                c.drawLayer { l in
                    l.opacity = p.opacity
                    piece(&l, p)
                }
            } else {
                piece(&c, p)
            }
        }
    }

    static func polygon(_ pts: [CGPoint]) -> Path {
        var path = Path()
        path.addLines(pts)
        path.closeSubpath()
        return path
    }

    static func piece(_ c: inout GraphicsContext, _ p: IngotCoinSwitchPiece) {
        c.fill(polygon(p.silhouette), with: .color(color(p.silhouetteColor)))
        for q in p.quads {
            c.fill(polygon(q.points), with: .color(color(q.color)))
        }
        let style = p.index % M.styles
        for cap in p.caps {
            let path = polygon(cap.points)
            let stops = zip(cap.colors, M.capStops).map { Gradient.Stop(color: color($0.0), location: $0.1) }
            c.fill(path, with: .linearGradient(Gradient(stops: stops), startPoint: cap.from, endPoint: cap.to))
            c.fill(path, with: .linearGradient(
                Gradient(stops: [.init(color: .white.opacity(0.14), location: 0),
                                 .init(color: .white.opacity(0), location: 0.45),
                                 .init(color: .black.opacity(0.07), location: 1)]),
                startPoint: cap.top, endPoint: cap.bottom))
            if cap.ingotAlpha > 0.01 || cap.coinAlpha > 0.01 {
                var l = c
                l.concatenate(cap.transform)
                if cap.ingotAlpha > 0.01 {
                    var a = l
                    a.opacity = cap.ingotAlpha
                    let s = A.ingotSize
                    a.draw(A.ingotMarks[style], in: CGRect(x: -s.width / 2, y: -s.height / 2, width: s.width, height: s.height))
                }
                if cap.coinAlpha > 0.01 {
                    var a = l
                    a.opacity = cap.coinAlpha
                    let s = A.coinSize
                    a.draw(A.coinMarks[style], in: CGRect(x: -s.width / 2, y: -s.height / 2, width: s.width, height: s.height))
                }
            }
        }
    }

    /// The price of the piece in front.
    static func ticker(_ c: inout GraphicsContext, _ g: IngotCoinSwitchGeo, _ f: IngotCoinSwitchFrame) {
        let y = g.tickerY
        let w = 214.0
        let chip = Path(roundedRect: CGRect(x: g.cx - w / 2, y: y - 16, width: w, height: 32), cornerRadius: 16)
        c.fill(chip, with: .color(.white.opacity(0.85)))
        c.stroke(chip, with: .color(color(0xE3DFD8)), lineWidth: 1)
        let fr = M.front(f)
        let style = fr.index % M.styles
        let stock = f.m[fr.index] >= 0.5
        let base = stock ? RGB(hex: M.coinHex[style]) : RGB(hex: M.ingotHex[style])
        let name = stock ? M.tickers[style] : M.metals[style]
        let price = stock ? M.stockPrices[style] : M.metalPrices[style]
        let move = stock ? M.stockMoves[style] : M.metalMoves[style]

        var l = c
        l.opacity = fr.alpha
        let tName = l.resolve(text(name, 14, .semibold, color(ink)))
        let tPrice = l.resolve(text(price, 14, .regular, color(muted)))
        let tMove = l.resolve(text(move, 14, .semibold, color(0x2F9E6A)))
        let box = CGSize(width: 300, height: 40)
        let wn = tName.measure(in: box).width
        let wp = tPrice.measure(in: box).width
        let wm = tMove.measure(in: box).width
        let total = 8 + 7 + wn + 9 + wp + 9 + wm
        var x = g.cx - total / 2
        l.fill(Path(ellipseIn: CGRect(x: x, y: y - 4, width: 8, height: 8)), with: .color(color(base.mix(RGB(0, 0, 0), 0.08))))
        x += 15
        l.draw(tName, at: CGPoint(x: x, y: y), anchor: .leading)
        x += wn + 9
        l.draw(tPrice, at: CGPoint(x: x, y: y), anchor: .leading)
        x += wp + 9
        l.draw(tMove, at: CGPoint(x: x, y: y), anchor: .leading)
    }

    /// "Stocks / Commodities": outlined pills with a dark capsule sliding between them.
    static func toggle(_ c: inout GraphicsContext, _ g: IngotCoinSwitchGeo, _ f: IngotCoinSwitchFrame) {
        let stocks = g.stocksRect
        let comm = g.commRect
        for r in [stocks, comm] {
            c.stroke(Path(roundedRect: r.insetBy(dx: 0.75, dy: 0.75), cornerRadius: 22), with: .color(color(ink)), lineWidth: 1.5)
        }
        let s = f.sel
        let x = M.lerp(comm.minX, stocks.minX, s)
        let w = M.lerp(comm.width, stocks.width, s)
        let capsule = Path(roundedRect: CGRect(x: x, y: stocks.minY, width: max(44, w), height: 44), cornerRadius: 22)
        // Dark labels everywhere, white ones only inside the capsule.
        var inside = c
        inside.clip(to: capsule)
        for (label, r) in [("Stocks", stocks), ("Commodities", comm)] {
            let p = CGPoint(x: r.midX, y: r.midY)
            c.draw(text(label, 16, .semibold, color(ink)), at: p, anchor: .center)
        }
        c.fill(capsule, with: .color(color(ink)))
        for (label, r) in [("Stocks", stocks), ("Commodities", comm)] {
            inside.draw(text(label, 16, .semibold, .white), at: CGPoint(x: r.midX, y: r.midY), anchor: .center)
        }
    }

    static func cta(_ c: inout GraphicsContext, _ g: IngotCoinSwitchGeo, _ f: IngotCoinSwitchFrame) {
        let r = CGRect(x: 24, y: g.ctaY - 28, width: g.w - 48, height: 56)
        let pill = Path(roundedRect: r, cornerRadius: 28)
        var sh = c
        sh.addFilter(.shadow(color: .black.opacity(0.18), radius: 14, x: 0, y: 8))
        sh.fill(pill, with: .color(color(ink)))
        c.stroke(Path(roundedRect: r.insetBy(dx: 0.5, dy: 0.5).offsetBy(dx: 0, dy: 0.5), cornerRadius: 28),
                 with: .color(.white.opacity(0.12)), lineWidth: 1)
        for (mode, alpha, rise) in copyLayers(f) {
            var l = c
            l.opacity = alpha
            l.draw(text(ctas[mode], 17, .semibold, .white), at: CGPoint(x: g.cx, y: g.ctaY + rise), anchor: .center)
        }
    }

    // MARK: Touch

    private func bump() {
        live = true
        token += 1
        let mine = token
        DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
            if mine == token && !driver.dragging { live = false }
        }
    }

    private func haptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func tap(_ mode: Int, auto: Bool, reduced: Bool) {
        let now = Date().timeIntervalSinceReferenceDate
        if driver.tap(mode, at: now, auto: auto, reduced: reduced) { haptic() }
        bump()
    }

    private func touch(_ g: IngotCoinSwitchGeo, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if grab == nil {
                    if let mode = g.pill(at: v.startLocation) {
                        grab = .pill(mode)
                    } else if !reduced {
                        grab = .spin
                        driver.beginDrag(at: now, auto: auto, reduced: reduced)
                        bump()
                    } else {
                        grab = .ignore
                    }
                }
                if grab == .spin {
                    driver.drag(v.translation.width, at: now)
                }
            }
            .onEnded { v in
                defer { grab = nil }
                switch grab {
                case .pill(let mode):
                    if abs(v.translation.width) < 12 && abs(v.translation.height) < 12 {
                        tap(mode, auto: auto, reduced: reduced)
                    }
                case .spin:
                    driver.endDrag(velocity: v.velocity.width, at: Date().timeIntervalSinceReferenceDate)
                    bump()
                default:
                    break
                }
            }
    }
}

enum IngotCoinSwitchGrab: Equatable {
    case pill(Int)
    case spin
    case ignore
}

struct IngotCoinSwitchGeo {
    var w = 393.0
    var h = 852.0
    var top = 59.0
    var bottom = 34.0

    var cx: Double { w / 2 }
    var ringY: Double { top + 386 }
    var tickerY: Double { top + 562 }
    var toggleY: Double { top + 620 }
    var ctaY: Double { h - max(bottom, 16) - 30 }

    var stocksRect: CGRect { CGRect(x: cx - 134, y: toggleY - 22, width: 108, height: 44) }
    var commRect: CGRect { CGRect(x: cx - 16, y: toggleY - 22, width: 150, height: 44) }

    /// 1 for "Stocks", 0 for "Commodities", nil elsewhere.
    func pill(at p: CGPoint) -> Int? {
        if stocksRect.insetBy(dx: -4, dy: -8).contains(p) { return 1 }
        if commRect.insetBy(dx: -4, dy: -8).contains(p) { return 0 }
        return nil
    }
}

/// Turns the clock and the finger into frames: the loop, or the touch state and its way back to rest.
final class IngotCoinSwitchDriver {
    typealias M = IngotCoinSwitchModel

    private var autoStart: Double?
    private(set) var manual = false
    private(set) var dragging = false
    private var lastTouch = -1e9

    // The ring: an anchor angle, the release speed (decays) and the slow turn.
    private var phiAnchor = 0.0
    private var anchorAt = 0.0
    private var release = 0.0
    private var dragPhi0 = 0.0
    private var spinning = false

    // The morph towards `target` (0 ingots, 1 coins).
    private var target = 0
    private var switchAt = -1e9
    private var m0 = [Double](repeating: 0, count: IngotCoinSwitchModel.count)
    private var delays = [Double](repeating: 0, count: IngotCoinSwitchModel.count)

    // The switch's capsule and the copy.
    private var selAt = -1e9
    private var sel0 = 0.0
    private var selV0 = 0.0
    private var copyFrom = 0
    private var copyAt = -1e9

    // The way home after the last touch.
    private var returnAt: Double?
    private var retPhi0 = 0.0
    private var retV0 = 0.0
    private var retPhi1 = 0.0

    static let decay = 0.31
    static let returnDur = 1.4
    static let pointsPerRadian = 150.0

    func reset() {
        autoStart = nil
        manual = false
        dragging = false
        returnAt = nil
        target = 0
        switchAt = -1e9
        m0 = [Double](repeating: 0, count: M.count)
        sel0 = 0
        selAt = -1e9
        copyFrom = 0
        copyAt = -1e9
        phiAnchor = 0
        release = 0
        spinning = false
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> IngotCoinSwitchFrame {
        if auto && manual && !dragging {
            if returnAt == nil && now >= lastTouch + M.idleBeforeBack {
                goHome(at: now)
            }
            if let r = returnAt, now >= r + Self.returnDur {
                manual = false
                returnAt = nil
                autoStart = now
            }
        }
        var f: IngotCoinSwitchFrame
        if auto && !manual {
            if autoStart == nil { autoStart = now }
            let t = (now - autoStart!).truncatingRemainder(dividingBy: M.loop)
            f = M.autoFrame(t)
        } else {
            f = manualFrame(at: now, auto: auto, reduced: reduced)
        }
        f.reduced = reduced
        if reduced { f.phi = 0 }
        return f
    }

    private func omega(_ auto: Bool) -> Double { auto ? M.omega : 0 }

    private func phi(at now: Double, auto: Bool) -> Double {
        if let r = returnAt {
            // Cubic Hermite from the current angle and speed to home, arriving at the loop's speed.
            let T = Self.returnDur
            let u = M.clamp01((now - r) / T)
            let u2 = u * u, u3 = u2 * u
            return (2 * u3 - 3 * u2 + 1) * retPhi0 + (u3 - 2 * u2 + u) * T * retV0
                + (-2 * u3 + 3 * u2) * retPhi1 + (u3 - u2) * T * M.omega
        }
        if dragging { return phiAnchor }
        let d = max(0, now - anchorAt)
        return phiAnchor + release * Self.decay * (1 - exp(-d / Self.decay)) + omega(auto) * d
    }

    private func phiVelocity(at now: Double, auto: Bool) -> Double {
        if dragging { return 0 }
        let d = max(0, now - anchorAt)
        return release * exp(-d / Self.decay) + omega(auto)
    }

    private func morphs(at now: Double, reduced: Bool) -> [Double] {
        let dur = reduced ? 0.3 : M.morphDur
        return (0..<M.count).map { i in
            M.morph(now - switchAt, delay: reduced ? 0 : delays[i], from: m0[i], to: Double(target), duration: dur)
        }
    }

    private func sel(at now: Double) -> Double {
        M.spring(now - selAt, x0: sel0, v0: selV0, to: Double(target), response: M.selResponse, damping: M.selDamping)
    }

    private func manualFrame(at now: Double, auto: Bool, reduced: Bool) -> IngotCoinSwitchFrame {
        var f = IngotCoinSwitchFrame()
        f.phi = phi(at: now, auto: auto)
        f.m = morphs(at: now, reduced: reduced)
        f.sel = sel(at: now)
        f.copyFrom = copyFrom
        f.copyTo = target
        f.copyU = now - copyAt
        return f
    }

    /// Takes over from the loop at its current frame.
    private func capture(at now: Double, auto: Bool, reduced: Bool) {
        guard !manual else { return }
        let f = frame(at: now, auto: auto, reduced: reduced)
        manual = true
        phiAnchor = f.phi.truncatingRemainder(dividingBy: M.home)
        anchorAt = now
        release = 0
        target = f.copyTo
        m0 = f.m
        switchAt = now
        delays = [Double](repeating: 0, count: M.count)
        sel0 = f.sel
        selV0 = 0
        selAt = now
        copyFrom = f.copyFrom
        copyAt = now - f.copyU
    }

    /// Stops the way home where it is, keeping the angle.
    private func cancelReturn(at now: Double, auto: Bool) {
        guard returnAt != nil else { return }
        let p = phi(at: now, auto: auto)
        returnAt = nil
        phiAnchor = p
        anchorAt = now
        release = 0
    }

    private func goHome(at now: Double) {
        retPhi0 = phi(at: now, auto: true)
        retV0 = phiVelocity(at: now, auto: true)
        let guess = retPhi0 + M.omega * Self.returnDur + (retV0 - M.omega) * Self.decay
        retPhi1 = (guess / M.home).rounded() * M.home
        if target != 0 { switchTo(0, at: now, auto: true, reduced: false) }
        returnAt = now
    }

    private func switchTo(_ mode: Int, at now: Double, auto: Bool, reduced: Bool) {
        let cur = manualFrame(at: now, auto: auto, reduced: reduced)
        let ahead = manualFrame(at: now + 0.01, auto: auto, reduced: reduced)
        m0 = cur.m
        selV0 = (ahead.sel - cur.sel) / 0.01
        sel0 = cur.sel
        selAt = now
        copyFrom = target
        copyAt = now
        target = mode
        switchAt = now + (reduced ? 0 : M.morphLag)
        delays = (0..<M.count).map { M.delay($0, cur.phi) }
    }

    /// A tap on a pill. Returns true when it changed the mode.
    @discardableResult
    func tap(_ mode: Int, at now: Double, auto: Bool, reduced: Bool) -> Bool {
        capture(at: now, auto: auto, reduced: reduced)
        cancelReturn(at: now, auto: auto)
        lastTouch = now
        guard mode != target else { return false }
        switchTo(mode, at: now, auto: auto, reduced: reduced)
        return true
    }

    func beginDrag(at now: Double, auto: Bool, reduced: Bool) {
        capture(at: now, auto: auto, reduced: reduced)
        cancelReturn(at: now, auto: auto)
        dragPhi0 = phi(at: now, auto: auto)
        phiAnchor = dragPhi0
        anchorAt = now
        release = 0
        dragging = true
        lastTouch = now
    }

    func drag(_ dx: Double, at now: Double) {
        guard dragging else { return }
        phiAnchor = dragPhi0 + dx / Self.pointsPerRadian
        anchorAt = now
        lastTouch = now
    }

    func endDrag(velocity: Double, at now: Double) {
        guard dragging else { return }
        dragging = false
        anchorAt = now
        release = min(max(velocity / Self.pointsPerRadian, -7), 7)
        lastTouch = now
    }
}
