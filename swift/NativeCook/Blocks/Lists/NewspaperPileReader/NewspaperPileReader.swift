import SwiftUI
import UIKit

/*
 Newspaper Pile Reader — the morning digest of an invented news app, as a pile of folded papers.

 Look: a warm white page, a date pill, and six invented papers (mastheads, headlines, columns, photos,
 grain, fold and a curled corner, all drawn in code once into bitmaps) piled down the screen, each a
 little rotated and offset, each lower one in front. Bottom: a dark tab pill and a pile / reader toggle.

 Hero (object, "lift"): a paper is picked out of the pile. It dips, lifts (its shadow grows), straightens
 and grows to reading size in the middle of the screen while the others are tossed out to the edges with
 a stagger; the paper's headline and summary fade in under it. Putting it back reverses it: the paper
 settles into its slot and the others slide in over it, re-forming the pile.

 autoPlay: loop = 9 s. Rest; a riffle wave runs down the pile (1.0 s); rest; the second paper is picked
 (2.6 s), read (3.5–6.0 s), put back (6.0–7.0 s); rest to 9 s. Every value is a pure function of the
 loop time and both ends of the loop are the pile at rest.
 Touch: tap a paper to open it; in the reader swipe sideways to page, swipe down or tap to put it back;
 drag the pile sideways to riffle it. 2 s after the last touch it goes back to the pile and the loop restarts.
 Reduced motion: no riffle and no flight; opening and closing crossfade; the loop is off.
*/
struct NewspaperPileReader: View {
    static let loop = 9.0

    /// Plays the 9 s pick-and-put-back loop until touched.
    var autoPlay = false

    typealias M = NewspaperPileReaderModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var driver = NewspaperPileReaderDriver()
    @State private var ready = false
    @State private var live = false
    @State private var token = 0

    static let bg: UInt32 = 0xF6F5F1
    static let ink: UInt32 = 0x1C1B19
    static let grey: UInt32 = 0x9C9A92

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color { NewspaperPileReaderArt.color(hex, a) }

    /// Two colours mixed in sRGB.
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func ch(_ v: UInt32, _ s: UInt32) -> Double { Double((v >> s) & 0xFF) / 255 }
        return Color(.sRGB, red: M.lerp(ch(a, 16), ch(b, 16), t), green: M.lerp(ch(a, 8), ch(b, 8), t),
                     blue: M.lerp(ch(a, 0), ch(b, 0), t), opacity: 1)
    }

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
        .background(Self.color(Self.bg).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            if driver.images == nil { driver.images = NewspaperPileReaderArt.make(scale: displayScale) }
            ready = true
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = NewspaperPileReaderGeo(w: max(1, size.width), h: max(1, size.height), top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return TimelineView(.animation(paused: (!auto && !live) || !ready)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let f = driver.frame(at: now, auto: auto, reduced: reduced, geo: g)
            ZStack(alignment: .topLeading) {
                header(g)
                Canvas { c, _ in
                    if let images = driver.images { Self.drawPapers(&c, f, images) }
                }
                .frame(width: g.w, height: g.h)
                Canvas { c, _ in Self.drawChrome(&c, g, f) }
                    .frame(width: g.w, height: g.h)
                caption(g, f)
            }
            .frame(width: g.w, height: g.h, alignment: .topLeading)
        }
        .contentShape(Rectangle())
        .gesture(touch(g, auto: auto, reduced: reduced))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Today's papers. Tap a paper to read it, swipe it down to put it back.")
        .accessibilityAction(named: "Open the next paper") {
            let now = Date().timeIntervalSinceReferenceDate
            driver.accessibilityToggle(at: now, auto: auto, reduced: reduced, geo: g)
            bump()
        }
        .onChange(of: auto) { driver.reset() }
    }

    // MARK: Header and caption

    private func header(_ g: NewspaperPileReaderGeo) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Self.color(0xECEAE4))
                .frame(width: g.w - 32, height: 44)
                .offset(x: 16, y: g.top + 8)
            Text("Tuesday, June 4")
                .font(.system(size: 22, weight: .regular))
                .tracking(-0.3)
                .foregroundColor(Self.color(Self.ink))
                .frame(height: 44)
                .offset(x: 34, y: g.top + 8)
            Canvas { c, _ in
                let x = g.w - 42 - 9
                let y = g.top + 8 + 22 - 8.5
                let r = CGRect(x: x, y: y + 2, width: 18, height: 15)
                c.stroke(Path(roundedRect: r, cornerRadius: 3), with: .color(Self.color(0x6E6C66)), lineWidth: 1.5)
                c.fill(Path(CGRect(x: x, y: y + 6, width: 18, height: 1.5)), with: .color(Self.color(0x6E6C66)))
                for k in [5.0, 13.0] {
                    c.fill(Path(roundedRect: CGRect(x: x + k - 0.75, y: y, width: 1.5, height: 4.5), cornerRadius: 0.75),
                           with: .color(Self.color(0x6E6C66)))
                }
            }
            .frame(width: g.w, height: g.top + 60)
            Text("Last update · Today 7:30 AM · 6 papers")
                .font(.system(size: 12))
                .foregroundColor(Self.color(Self.grey))
                .frame(width: g.w, height: 16)
                .offset(y: g.top + 64)
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
    }

    private func caption(_ g: NewspaperPileReaderGeo, _ f: NewspaperPileReaderFrame) -> some View {
        let p = M.papers[f.caption]
        let w = g.w - 48
        return VStack(alignment: .leading, spacing: 0) {
            Text(p.kicker)
                .font(.system(size: 11, weight: .semibold))
                .tracking(1.1)
                .foregroundColor(Self.color(0x8F8C84))
                .lineLimit(1)
                .opacity(f.rows[0])
                .offset(y: f.rise[0])
            Text(p.title)
                .font(NewspaperPileReaderFonts.serif(26))
                .foregroundColor(Self.color(Self.ink))
                .lineSpacing(0)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
                .opacity(f.rows[1])
                .offset(y: f.rise[1])
            Text(p.summary)
                .font(.system(size: 15))
                .foregroundColor(Self.color(0x6B6960))
                .lineSpacing(3)
                .lineLimit(3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 10)
                .opacity(f.rows[2])
                .offset(y: f.rise[2])
        }
        .frame(width: w, alignment: .leading)
        .offset(x: 24, y: g.captionTop - 8)
        .allowsHitTesting(false)
    }

    // MARK: Drawing

    static func drawPapers(_ c: inout GraphicsContext, _ f: NewspaperPileReaderFrame, _ img: NewspaperPileReaderImages) {
        var base = c
        base.opacity = f.papersAlpha
        for i in f.sheets.indices {
            sheet(&base, f.sheets[i], img.papers[i], img, alpha: 1)
        }
        if f.raised >= 0 && f.raisedAlpha > 0.001 {
            sheet(&base, f.sheets[f.raised], img.papers[f.raised], img, alpha: f.raisedAlpha)
        }
    }

    static func sheet(_ c: inout GraphicsContext, _ s: NewspaperPileReaderSheet, _ paper: Image,
                      _ img: NewspaperPileReaderImages, alpha: Double) {
        let L = min(max(s.lift, 0), 1.2)
        let w = M.paperW
        let h = M.paperH
        // Ambient shadow: grows, drops and darkens a little as the paper lifts.
        var a = c
        a.opacity *= alpha * M.lerp(0.12, 0.22, L)
        a.translateBy(x: s.x, y: s.y + M.lerp(8, 26, L))
        a.rotate(by: .degrees(s.rot))
        let ak = s.scale * M.lerp(1, 1.06, L)
        a.scaleBy(x: ak, y: ak)
        let ap = NewspaperPileReaderArt.ambientPad
        a.draw(img.ambient, in: CGRect(x: -w / 2 - ap, y: -h / 2 - ap, width: w + 2 * ap, height: h + 2 * ap))
        // Contact shadow: tight, fades as the paper leaves the pile.
        var k = c
        k.opacity *= alpha * M.lerp(0.22, 0.06, min(L, 1))
        k.translateBy(x: s.x, y: s.y + 1.5)
        k.rotate(by: .degrees(s.rot))
        k.scaleBy(x: s.scale, y: s.scale)
        let cp = NewspaperPileReaderArt.contactPad
        k.draw(img.contact, in: CGRect(x: -w / 2 - cp, y: -h / 2 - cp, width: w + 2 * cp, height: h + 2 * cp))
        // The paper.
        var p = c
        p.opacity *= alpha
        p.translateBy(x: s.x, y: s.y)
        p.rotate(by: .degrees(s.rot))
        p.scaleBy(x: s.scale, y: s.scale)
        let pad = NewspaperPileReaderArt.pad
        p.draw(paper, in: CGRect(x: -w / 2 - pad, y: -h / 2 - pad, width: w + 2 * pad, height: h + 2 * pad))
    }

    /// The fog over the pile's foot, the page dashes and the bottom bar.
    static func drawChrome(_ c: inout GraphicsContext, _ g: NewspaperPileReaderGeo, _ f: NewspaperPileReaderFrame) {
        let fog = CGRect(x: 0, y: g.h - 210, width: g.w, height: 210)
        c.fill(Path(fog), with: .linearGradient(
            Gradient(stops: [.init(color: color(bg, 0), location: 0), .init(color: color(bg, 0.92), location: 90.0 / 210),
                             .init(color: color(bg, 1), location: 1)]),
            startPoint: CGPoint(x: 0, y: fog.minY), endPoint: CGPoint(x: 0, y: fog.maxY)))

        // Page dashes.
        if f.rows[3] > 0.001 {
            var d = c
            d.opacity = f.rows[3]
            let n = M.papers.count
            let total = Double(n - 1) * 20 + 28
            var x = g.cx - total / 2
            for i in 0..<n {
                let on = max(0, 1 - abs(Double(i) - f.page))
                let wd = 14 + 14 * on
                d.fill(Path(roundedRect: CGRect(x: x, y: g.dashY + f.rise[3] - 1.5, width: wd, height: 3), cornerRadius: 1.5),
                       with: .color(mix(0xCFCCC4, ink, on)))
                x += wd + 6
            }
        }

        // Tab pill.
        let y = g.barY
        c.fill(Path(roundedRect: CGRect(x: 20, y: y - 28, width: 150, height: 56), cornerRadius: 28), with: .color(color(0x161513)))
        var stack = Path()
        stack.addRoundedRect(in: CGRect(x: 58 - 9, y: y - 7, width: 18, height: 13), cornerSize: CGSize(width: 2.5, height: 2.5))
        stack.move(to: CGPoint(x: 58 - 6, y: y - 10.5))
        stack.addLine(to: CGPoint(x: 58 + 6, y: y - 10.5))
        c.stroke(stack, with: .color(color(0xF4F3EE)), lineWidth: 1.6)
        c.fill(Path(ellipseIn: CGRect(x: 58 - 1.5, y: y + 12, width: 3, height: 3)), with: .color(color(0xF4F3EE)))
        var mark = Path()
        mark.move(to: CGPoint(x: 95 - 6, y: y - 9))
        mark.addLine(to: CGPoint(x: 95 + 6, y: y - 9))
        mark.addLine(to: CGPoint(x: 95 + 6, y: y + 9))
        mark.addLine(to: CGPoint(x: 95, y: y + 4))
        mark.addLine(to: CGPoint(x: 95 - 6, y: y + 9))
        mark.closeSubpath()
        c.stroke(mark, with: .color(color(0x8A8780)), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
        var sliders = Path()
        for (k, kx) in [(-4.0, -3.0), (4.0, 3.0)] {
            sliders.move(to: CGPoint(x: 132 - 8, y: y + k))
            sliders.addLine(to: CGPoint(x: 132 + 8, y: y + k))
            sliders.addEllipse(in: CGRect(x: 132 + kx * 1.5 - 2.5, y: y + k - 2.5, width: 5, height: 5))
        }
        c.stroke(sliders, with: .color(color(0x8A8780)), lineWidth: 1.6)

        // Mode toggle: reader (three dots on a line) | pile (three lines).
        let mx = g.w - 20 - 108
        var sh = c
        sh.addFilter(.shadow(color: .black.opacity(0.10), radius: 9, x: 0, y: 6))
        sh.fill(Path(roundedRect: CGRect(x: mx, y: y - 28, width: 108, height: 56), cornerRadius: 28), with: .color(.white))
        let hx = M.lerp(mx + 78, mx + 30, f.toggle)
        c.fill(Path(ellipseIn: CGRect(x: hx - 22, y: y - 22, width: 44, height: 44)), with: .color(color(0xECEAE4)))
        let readerCol = mix(0x8A8780, ink, f.toggle)
        let pileCol = mix(ink, 0x8A8780, f.toggle)
        var reader = Path()
        reader.move(to: CGPoint(x: mx + 30 - 10, y: y))
        reader.addLine(to: CGPoint(x: mx + 30 + 10, y: y))
        c.stroke(reader, with: .color(readerCol), lineWidth: 1.2)
        for k in [-8.0, 0, 8] {
            c.fill(Path(ellipseIn: CGRect(x: mx + 30 + k - 3, y: y - 3, width: 6, height: 6)), with: .color(readerCol))
        }
        var pile = Path()
        for k in [-6.0, 0, 6] {
            pile.addRoundedRect(in: CGRect(x: mx + 78 - 9, y: y + k - 1, width: 18, height: 2), cornerSize: CGSize(width: 1, height: 1))
        }
        c.fill(pile, with: .color(pileCol))
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

    private func touch(_ g: NewspaperPileReaderGeo, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if driver.change(start: v.startLocation, translation: v.translation, velocity: v.velocity,
                                 at: now, auto: auto, reduced: reduced, geo: g) {
                    haptic()
                }
                bump()
            }
            .onEnded { v in
                let now = Date().timeIntervalSinceReferenceDate
                if driver.end(at: v.location, translation: v.translation, velocity: v.velocity,
                              now: now, auto: auto, reduced: reduced, geo: g) {
                    haptic()
                }
                bump()
            }
    }

    private func haptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

/// Holds the touch state and turns it, or the loop, into a `NewspaperPileReaderState` for each frame.
final class NewspaperPileReaderDriver {
    typealias M = NewspaperPileReaderModel

    private var autoStart: Double?
    private var manual = false
    private var s = NewspaperPileReaderState()
    /// The pre-rendered papers and shadows (made on first appearance).
    var images: NewspaperPileReaderImages?
    private var lastTouch = -1e9
    private var mode = 0 // 0 undecided, 1 riffle, 2 page, 3 down, 4 ignored
    private var touching = false
    private var pageBase = 0.0
    private var lastFrame = NewspaperPileReaderFrame()

    func reset() {
        autoStart = nil
        manual = false
        s = NewspaperPileReaderState()
        lastTouch = -1e9
        mode = 0
    }

    private func isOpen() -> Bool { s.openAt != nil && s.closeAt == nil }

    /// True while something still moves after a touch (keeps the timeline running without autoPlay).
    func live(at now: Double) -> Bool {
        if mode != 0 { return true }
        if now - lastTouch < M.idleBeforeBack + 1.5 { return true }
        if let b = s.closeAt, now - b < 1.2 { return true }
        return false
    }

    func frame(at now: Double, auto: Bool, reduced: Bool, geo: NewspaperPileReaderGeo) -> NewspaperPileReaderFrame {
        s.reduced = reduced
        if auto && manual && mode == 0 && now - lastTouch > M.idleBeforeBack {
            if isOpen() {
                close(at: now, geo: geo)
            } else if settled(at: now) {
                manual = false
                autoStart = now
            }
        }
        let f: NewspaperPileReaderFrame
        if auto && !manual {
            if autoStart == nil { autoStart = now }
            let tau = (now - autoStart!).truncatingRemainder(dividingBy: M.loop)
            var a = M.autoState()
            a.reduced = reduced
            f = M.frame(a, tau, geo)
        } else {
            f = M.frame(s, now, geo)
        }
        lastFrame = f
        return f
    }

    private func settled(at now: Double) -> Bool {
        if let b = s.closeAt, now - b < 1.0 { return false }
        if now - s.riffleAt < 0.7 || now - s.pageAt < 0.6 || now - s.dragAt < 0.5 { return false }
        if let a = s.autoRiffle, now - a < 1.0 { return false }
        return true
    }

    /// Takes over from the loop where it is, so nothing jumps.
    private func capture(at now: Double, auto: Bool) {
        guard auto, !manual, let start = autoStart else {
            manual = true
            return
        }
        manual = true
        let tau = (now - start).truncatingRemainder(dividingBy: M.loop)
        let shift = now - tau
        var a = NewspaperPileReaderState()
        a.reduced = s.reduced
        a.autoRiffle = M.riffleAt + shift
        if tau >= M.openAt { a.openAt = M.openAt + shift }
        if tau >= M.closeAt { a.closeAt = M.closeAt + shift }
        s = a
    }

    private func open(_ i: Int, at now: Double) {
        let keep = s
        s = NewspaperPileReaderState()
        s.reduced = keep.reduced
        s.riffleFromDx = keep.riffleFromDx
        s.riffleFromRot = keep.riffleFromRot
        s.riffleFromLift = keep.riffleFromLift
        s.riffleAt = keep.riffleAt
        s.autoRiffle = keep.autoRiffle
        s.openAt = now
        s.sel = i
        s.closeSel = i
        s.pageFrom = Double(i)
        s.pageTo = Double(i)
    }

    private func close(at now: Double, geo: NewspaperPileReaderGeo) {
        let p = M.page(s, now)
        s.closeDrag = M.dragOffset(s, now)
        s.dragLive = nil
        s.pageLive = nil
        s.pageFrom = p
        s.pageTo = p
        s.closeSel = min(max(Int(p.rounded()), 0), M.papers.count - 1)
        s.closeAt = now
    }

    /// Returns true when a haptic should play.
    func change(start: CGPoint, translation: CGSize, velocity: CGSize, at now: Double,
                auto: Bool, reduced: Bool, geo: NewspaperPileReaderGeo) -> Bool {
        if !touching {
            touching = true
            capture(at: now, auto: auto)
        }
        lastTouch = now
        let dx = translation.width
        let dy = translation.height
        if mode == 0 {
            guard hypot(dx, dy) > 8 else { return false }
            if isOpen() {
                if abs(dx) > abs(dy) {
                    mode = 2
                    pageBase = M.page(s, now)
                } else if dy > 0 {
                    mode = 3
                } else {
                    mode = 4
                }
            } else if s.closeAt == nil || settled(at: now) {
                if abs(dx) > abs(dy) && !reduced {
                    // Freeze the release spring where it is, then follow the finger.
                    mode = 1
                    s.riffleOn = true
                    s.riffleY = start.y
                } else {
                    mode = 4
                }
            } else {
                mode = 4
            }
        }
        switch mode {
        case 1:
            s.riffleDx = dx
            s.riffleVx = velocity.width
        case 2:
            let n = Double(M.papers.count - 1)
            var p = pageBase - dx / M.pageStride
            if p < 0 { p = -(-p) / (1 + (-p) * 2) }
            if p > n { p = n + (p - n) / (1 + (p - n) * 2) }
            s.pageLive = p
        case 3:
            s.dragLive = dy > 0 ? dy / (1 + dy / 400) : 0
        default:
            break
        }
        return false
    }

    func end(at location: CGPoint, translation: CGSize, velocity: CGSize, now: Double,
             auto: Bool, reduced: Bool, geo: NewspaperPileReaderGeo) -> Bool {
        lastTouch = now
        if !touching { capture(at: now, auto: auto) }
        defer {
            mode = 0
            touching = false
        }
        let moved = hypot(translation.width, translation.height)
        switch mode {
        case 1:
            for i in 0..<M.papers.count {
                let (dx, rot, lift) = M.fingerRiffle(s, i, geo)
                s.riffleFromDx[i] = dx
                s.riffleFromRot[i] = rot
                s.riffleFromLift[i] = lift
            }
            s.riffleOn = false
            s.riffleAt = now
            return false
        case 2:
            let p = s.pageLive ?? M.page(s, now)
            let from = Int(pageBase.rounded())
            var target = (p - velocity.width / M.pageStride * 0.25).rounded()
            target = min(max(target, Double(from - 1)), Double(from + 1))
            target = min(max(target, 0), Double(M.papers.count - 1))
            s.pageLive = nil
            s.pageFrom = p
            s.pageTo = target
            s.pageAt = now
            return Int(target) != from
        case 3:
            let d = s.dragLive ?? 0
            if d > 90 || velocity.height > 700 {
                close(at: now, geo: geo)
                return true
            }
            s.dragLive = nil
            s.dragFrom = d
            s.dragAt = now
            return false
        case 4:
            return false
        default:
            guard moved < 10 else { return false }
            if isOpen() {
                close(at: now, geo: geo)
                return true
            }
            if let b = s.closeAt, now - b < 0.9 { return false }
            if let i = M.hit(lastFrame, location.x, location.y) {
                open(i, at: now)
                return true
            }
            return false
        }
    }

    func accessibilityToggle(at now: Double, auto: Bool, reduced: Bool, geo: NewspaperPileReaderGeo) {
        capture(at: now, auto: auto)
        lastTouch = now
        if isOpen() { close(at: now, geo: geo) } else { open(1, at: now) }
    }
}
