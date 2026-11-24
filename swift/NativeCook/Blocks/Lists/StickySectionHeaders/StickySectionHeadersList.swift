import Observation
import SwiftUI
import UIKit

/// The one scroll value every sticky piece reads (the SwiftUI side of RN's `scrollY` shared value). Only the small
/// overlay views read it; rows never do, so rows never update while the list scrolls.
@Observable
final class StickySectionHeadersScroll {
    /// Content offset of the list (pt from its top).
    var s: Double = 0
    /// Autoplay clock while the loop plays (for the ghost finger); nil once a finger has taken over.
    var ghostT: Double?
    /// The month under a finger on the index (−1 while none), and how present its bubble is (animated 0…1).
    var railMonth = -1
    var railActive: Double = 0
    /// Height of the list's viewport.
    var viewH: Double = 600
}

/// Moves the real scroll view: the loop scripts its offset from a display link, a finger takes over (drag, fling,
/// index), and 2 s after the last touch it eases back to the top and the loop restarts at t = 0. Nothing here is
/// observed by SwiftUI; it only writes the offset, which comes back through `onScrollGeometryChange`.
final class StickySectionHeadersDriver: NSObject {
    typealias M = StickySectionHeadersMotion
    private enum Mode { case auto, touch, back }

    let geo: M.Geometry
    let scroll: StickySectionHeadersScroll
    var autoPlay = true
    var reducedMotion = false
    /// True while a finger is on the month index.
    var railDragging = false

    weak var scrollView: UIScrollView?
    private var link: CADisplayLink?
    private var last: CFTimeInterval = 0
    private var now: Double = 0
    private var clock: Double = 0
    private var mode = Mode.auto
    private var lastTouch: Double = 0
    private var returnFrom: Double = 0
    private var returnAt: Double = 0
    private var lastSection = -2
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    private var auto: Bool { autoPlay && !reducedMotion }

    init(geo: M.Geometry, scroll: StickySectionHeadersScroll) {
        self.geo = geo
        self.scroll = scroll
    }

    func start() {
        guard link == nil else { return }
        let l = CADisplayLink(target: self, selector: #selector(tick(_:)))
        l.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        l.add(to: .main, forMode: .common)
        link = l
        last = 0
    }

    func stop() {
        link?.invalidate()
        link = nil
    }

    /// Offset of the content in the scroll view's own terms (the top inset folded in).
    private var offset: Double {
        guard let sv = scrollView else { return scroll.s }
        return sv.contentOffset.y + sv.adjustedContentInset.top
    }

    private func setOffset(_ s: Double) {
        guard let sv = scrollView else { return }
        let y = s - sv.adjustedContentInset.top
        if abs(sv.contentOffset.y - y) > 0.01 { sv.contentOffset.y = y }
    }

    private var userActive: Bool {
        guard let sv = scrollView else { return railDragging }
        return sv.isTracking || sv.isDragging || sv.isDecelerating || railDragging
    }

    @objc private func tick(_ l: CADisplayLink) {
        let dt = last == 0 ? 1.0 / 60 : min(l.timestamp - last, 0.05)
        last = l.timestamp
        now += dt
        if #unavailable(iOS 18.0) { observe(offset) }

        if userActive {
            if mode != .touch { mode = .touch; scroll.ghostT = nil }
            lastTouch = now
            return
        }
        switch mode {
        case .auto:
            guard auto else { return }
            clock = (clock + dt).truncatingRemainder(dividingBy: M.loop)
            setOffset(M.scrollAt(clock, geo))
            scroll.ghostT = clock
        case .touch:
            if auto && now - lastTouch > M.idle {
                returnFrom = offset
                returnAt = now
                mode = .back
            }
        case .back:
            let u = min(1, (now - returnAt) / M.returnLen)
            setOffset(returnFrom * (1 - M.easeInOutCubic(u)))
            if u >= 1 {
                clock = 0
                mode = .auto
            }
        }
    }

    /// Every new offset, from `onScrollGeometryChange` (or the display link before iOS 18).
    func observe(_ s: Double) {
        // Momentum after a fling is the finger's too: the idle return waits for it to stop, so any movement while
        // the person has the list counts as activity.
        if mode == .touch, abs(scroll.s - s) > 0.001 { lastTouch = now }
        if abs(scroll.s - s) > 0.001 { scroll.s = s }
        // A light tick when the stuck header changes hands under a dragging finger (not in the loop).
        let k = M.sectionAt(s, geo)
        if k != lastSection {
            if lastSection != -2, k >= 0, mode == .touch, scrollView?.isTracking == true, !railDragging { haptic.impactOccurred() }
            lastSection = k
        }
    }

    // MARK: month index

    func railBegan() {
        railDragging = true
        mode = .touch
        lastTouch = now
        scroll.ghostT = nil
    }

    func railJump(to month: Int) {
        guard let sv = scrollView else { return }
        let maxS = max(0, sv.contentSize.height + sv.adjustedContentInset.top + sv.adjustedContentInset.bottom - sv.bounds.height)
        // Stop any fling first, then jump.
        sv.setContentOffset(sv.contentOffset, animated: false)
        setOffset(min(geo.monthTops[month], maxS))
        haptic.impactOccurred()
    }

    func railEnded() {
        railDragging = false
        lastTouch = now
    }
}

/// Finds the scroll view around the content it is placed in.
private struct StickySectionHeadersScrollFinder: UIViewRepresentable {
    let driver: StickySectionHeadersDriver

    func makeUIView(context: Context) -> Finder { Finder(driver: driver) }
    func updateUIView(_ view: Finder, context: Context) {}

    final class Finder: UIView {
        let driver: StickySectionHeadersDriver
        init(driver: StickySectionHeadersDriver) {
            self.driver = driver
            super.init(frame: .zero)
            isUserInteractionEnabled = false
        }
        required init?(coder: NSCoder) { fatalError() }
        override func didMoveToWindow() {
            super.didMoveToWindow()
            var v = superview
            while let cur = v {
                if let sv = cur as? UIScrollView {
                    driver.scrollView = sv
                    sv.showsVerticalScrollIndicator = false
                    return
                }
                v = cur.superview
            }
        }
    }
}

/// The reusable piece: a sectioned lazy list whose headers stick, get pushed by the next one and, once stuck, shrink
/// into a frosted pill with a rolling running total; a month index on the right follows and can be dragged to jump.
struct StickySectionHeadersList<Top: View, Footer: View>: View {
    typealias M = StickySectionHeadersMotion
    typealias P = StickySectionHeadersPalette

    let sections: [StickySectionHeadersSection]
    let months: [String]
    let monthNames: [String]
    let listTopHeight: Double
    let listTop: Top
    let footer: Footer
    let scroll: StickySectionHeadersScroll
    let driver: StickySectionHeadersDriver

    var body: some View {
        GeometryReader { box in
            ZStack(alignment: .topLeading) {
                list
                StickySectionHeadersOverlay(sections: sections, geo: driver.geo, scroll: scroll, width: box.size.width, reducedMotion: driver.reducedMotion)
                StickySectionHeadersRail(months: months, monthNames: monthNames, geo: driver.geo, scroll: scroll, driver: driver)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                    .padding(.trailing, 4)
                StickySectionHeadersGhost(scroll: scroll, width: box.size.width)
            }
            .onAppear { scroll.viewH = box.size.height }
            .onChange(of: box.size.height) { _, h in scroll.viewH = h }
        }
        .clipped()
    }

    @ViewBuilder
    private var list: some View {
        let content = ScrollView(.vertical) {
            LazyVStack(spacing: 0) {
                listTop.frame(height: listTopHeight)
                    .background(StickySectionHeadersScrollFinder(driver: driver))
                ForEach(sections) { section in
                    StickySectionHeadersHeaderRow(section: section)
                    ForEach(Array(section.txns.enumerated()), id: \.element.id) { i, txn in
                        StickySectionHeadersRow(txn: txn, last: i == section.txns.count - 1)
                    }
                }
                footer.frame(height: M.footerH, alignment: .top)
            }
        }
        .scrollIndicators(.hidden)
        if #available(iOS 18.0, *) {
            content.onScrollGeometryChange(for: Double.self) { g in
                Double(g.contentOffset.y + g.contentInsets.top)
            } action: { _, s in
                driver.observe(s)
            }
        } else {
            content
        }
    }
}

enum StickySectionHeadersPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255, opacity: a)
    }
    static let bg = hex(0xf3f2ee)
    static let ink = hex(0x1c1b19)
    static let mute = hex(0x77736b)
    static let income = hex(0x2c8650)
    static let hair = Color(.sRGB, red: 40 / 255, green: 34 / 255, blue: 26 / 255, opacity: 0.12)
    static let shadow = hex(0x2a2218)
    /// Room on the right for the month index, and the side margin.
    static let railW: CGFloat = 40
    static let side: CGFloat = 20
    /// Header text positions, shared by the in-list header and the sticky copy so they line up to the point.
    static let lblY: CGFloat = 8
    static let lblLH: CGFloat = 22
    static let subY: CGFloat = 31
    static let subLH: CGFloat = 17
    static let totY: CGFloat = 18
    static let totLH: CGFloat = 20
    static let hairline: CGFloat = 1 / 3

    static func blend(_ a: Color, _ b: Color, _ u: Double) -> Color {
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        UIColor(a).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        UIColor(b).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let t = CGFloat(u)
        return Color(.sRGB, red: r1 + (r2 - r1) * t, green: g1 + (g2 - g1) * t, blue: b1 + (b2 - b1) * t, opacity: a1 + (a2 - a1) * t)
    }
}

// MARK: rows (they never read the scroll value)

struct StickySectionHeadersHeaderRow: View {
    typealias P = StickySectionHeadersPalette
    let section: StickySectionHeadersSection

    var body: some View {
        ZStack(alignment: .topLeading) {
            P.bg
            StickySectionHeadersLabel(text: section.label)
                .offset(x: P.side, y: P.lblY)
            StickySectionHeadersSub(text: section.countText)
                .offset(x: P.side, y: P.subY)
            StickySectionHeadersTotal(text: StickySectionHeadersData.formatMoney(-section.spent, signed: true))
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding(.trailing, P.railW + 4)
                .offset(y: P.totY)
        }
        .frame(height: StickySectionHeadersMotion.headerH)
    }
}

struct StickySectionHeadersLabel: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 17, weight: .bold))
            .tracking(-0.2)
            .foregroundStyle(StickySectionHeadersPalette.ink)
            .lineLimit(1)
            .frame(height: StickySectionHeadersPalette.lblLH)
    }
}

struct StickySectionHeadersSub: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 13))
            .monospacedDigit()
            .foregroundStyle(StickySectionHeadersPalette.mute)
            .lineLimit(1)
            .frame(height: StickySectionHeadersPalette.subLH)
    }
}

struct StickySectionHeadersTotal: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 15, weight: .semibold))
            .monospacedDigit()
            .foregroundStyle(StickySectionHeadersPalette.mute)
            .lineLimit(1)
            .frame(height: StickySectionHeadersPalette.totLH)
    }
}

struct StickySectionHeadersRow: View {
    typealias P = StickySectionHeadersPalette
    let txn: StickySectionHeadersTxn
    let last: Bool

    var body: some View {
        HStack(spacing: 0) {
            ZStack(alignment: .top) {
                LinearGradient(stops: [.init(color: txn.tint.0, location: 0), .init(color: txn.tint.1, location: 0.7), .init(color: txn.tint.1, location: 1)],
                               startPoint: UnitPoint(x: 0.2, y: 0), endPoint: UnitPoint(x: 0.8, y: 1))
                Capsule().fill(Color.white.opacity(0.45)).frame(height: 1).padding(.horizontal, 6).padding(.top, 1)
                Text(txn.initial)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(P.hex(0xfffdf8))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .frame(width: 40, height: 40)
            .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(txn.name)
                    .font(.system(size: 16, weight: .semibold))
                    .tracking(-0.1)
                    .foregroundStyle(P.ink)
                    .lineLimit(1)
                Text(txn.meta)
                    .font(.system(size: 13))
                    .foregroundStyle(P.mute)
                    .lineLimit(1)
            }
            .padding(.leading, 12)
            .padding(.trailing, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(StickySectionHeadersData.formatMoney(txn.cents, signed: true))
                .font(.system(size: 16, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(txn.cents > 0 ? P.income : P.ink)
                .lineLimit(1)
                .fixedSize()
        }
        .padding(.leading, P.side)
        .padding(.trailing, P.railW + 4)
        .frame(height: StickySectionHeadersMotion.rowH)
        .background(P.bg)
        .overlay(alignment: .bottom) {
            if !last {
                Rectangle().fill(P.hair).frame(height: P.hairline).padding(.leading, P.side + 52)
            }
        }
    }
}

// MARK: the sticky overlay

/// One copy of the header for every section, above the list: it shows the current section's label, gets pushed up by
/// the next header and shrinks into the frosted pill with the odometer. It reads the scroll value; nothing else does.
private struct StickySectionHeadersOverlay: View {
    typealias M = StickySectionHeadersMotion
    typealias P = StickySectionHeadersPalette

    let sections: [StickySectionHeadersSection]
    let geo: M.Geometry
    let scroll: StickySectionHeadersScroll
    let width: CGFloat
    let reducedMotion: Bool

    private static let labelScale = 0.84
    private static let lblW: CGFloat = 200
    private static let barFadeFrom = 46.0
    private static let barFadeTo = 64.0

    var body: some View {
        let s = scroll.s
        let k = M.sectionAt(s, geo)
        let p: Double = reducedMotion ? (k >= 0 && s > geo.tops[k] + 4 ? 1 : 0) : M.pillAmount(s, k, geo)
        let push = M.pushOffset(s, k, p, geo)
        let bar: Double = reducedMotion ? 1 - p : (k < 0 ? 1 : 1 - M.clamp01((s - geo.tops[k] - Self.barFadeFrom) / (Self.barFadeTo - Self.barFadeFrom)))
        let sec = sections[max(0, k)]
        let W = Double(width)
        let pillLeft = (W - M.pillW) / 2
        let sc = M.mix(1, Self.labelScale, p)
        let xT = M.mix(Double(P.side), pillLeft + 16, p)
        let yT = M.mix(Double(P.lblY), M.pillTop + (M.pillH - Double(P.lblLH) * Self.labelScale) / 2, p)
        let leave = 1 - M.clamp01(p * 1.6)

        ZStack(alignment: .topLeading) {
            // The full bar (page colour, hairline): stays until the in-list header has scrolled out from under it.
            ZStack(alignment: .topLeading) {
                P.bg
                StickySectionHeadersSub(text: sec.countText)
                    .frame(width: 160, alignment: .leading)
                    .opacity(leave)
                    .offset(x: P.side, y: P.subY)
                StickySectionHeadersTotal(text: StickySectionHeadersData.formatMoney(-sec.spent, signed: true))
                    .frame(width: 110, alignment: .trailing)
                    .opacity(leave)
                    .offset(x: width - P.railW - 4 - 110, y: P.totY)
                Rectangle().fill(P.hair).frame(height: P.hairline)
                    .offset(y: M.headerH - P.hairline)
            }
            .frame(width: width, height: M.headerH, alignment: .topLeading)
            .opacity(bar)

            // Once the bar is gone, a short paper band behind and around the pill keeps rows from reading through it.
            LinearGradient(stops: [.init(color: P.bg, location: 0), .init(color: P.bg, location: 0.8), .init(color: P.bg.opacity(0), location: 1)],
                           startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: M.pillTop + M.pillH + 6)
                .opacity(1 - bar)

            StickySectionHeadersPill()
                .frame(width: M.mix(W - 8, M.pillW, p), height: M.mix(M.headerH, M.pillH, p))
                .frame(width: width, height: M.pillH)
                .offset(y: M.pillTop)
                .opacity(M.easeInOutCubic(M.clamp01(p * 1.25)))

            StickySectionHeadersLabel(text: sec.label)
                .frame(width: Self.lblW, height: P.lblLH, alignment: .leading)
                .clipped()
                .scaleEffect(sc, anchor: .topLeading)
                .offset(x: xT, y: yT)

            StickySectionHeadersOdometer(run: M.runningAt(s, k, geo), pillLeft: pillLeft)
                .opacity(M.easeInOutCubic(M.clamp01((p - 0.35) / 0.65)))
                .offset(y: (1 - p) * 6)
        }
        .frame(width: width, height: M.headerH, alignment: .topLeading)
        .offset(y: push)
        .opacity(k < 0 ? 0 : 1)
        .allowsHitTesting(false)
        // Hidden from accessibility, as in React Native: it repeats the in-list header under it, and its odometer is
        // a column of digits (the odometer itself is one element with the amount, should this ever be exposed).
        .accessibilityHidden(true)
    }
}

/// The frosted capsule: a real material blur under a paper tint, hairline edge, top highlight and two shadows drawn
/// only outside the glass.
private struct StickySectionHeadersPill: View {
    typealias P = StickySectionHeadersPalette

    var body: some View {
        ZStack(alignment: .top) {
            Capsule()
                .fill(Color.white)
                .shadow(color: P.shadow.opacity(0.14), radius: 18, y: 8)
                .shadow(color: P.shadow.opacity(0.2), radius: 2, y: 1)
                .mask {
                    Rectangle().padding(-60)
                        .overlay(Capsule().blendMode(.destinationOut))
                        .compositingGroup()
                }
            Capsule().fill(.ultraThinMaterial)
            Capsule().fill(P.hex(0xf3f2ee, 0.35))
            Capsule().fill(P.hex(0xfaf9f6, 0.74))
            Capsule().strokeBorder(Color(.sRGB, red: 40 / 255, green: 34 / 255, blue: 26 / 255, opacity: 0.14), lineWidth: P.hairline)
            Rectangle().fill(Color.white.opacity(0.85)).frame(height: 1).padding(.horizontal, 14)
        }
        .environment(\.colorScheme, .light)
    }
}

/// The running total in the pill: wheels for $100s, $10s, $1s, dimes and cents rolling through the digits between.
private struct StickySectionHeadersOdometer: View {
    typealias M = StickySectionHeadersMotion
    typealias P = StickySectionHeadersPalette
    let run: (Int, Int, Double)
    let pillLeft: Double

    static let digitW: CGFloat = 9.6
    static let digitH: CGFloat = 20
    static let dotW: CGFloat = 5
    static let dollarW: CGFloat = 9.5

    var body: some View {
        let dW = Self.digitW
        let odoRight = pillLeft + M.pillW - 14
        let xCent = odoRight - dW
        let xDime = xCent - dW
        let xDot = xDime - Self.dotW
        let xOnes = xDot - dW
        let xTens = xOnes - dW
        let xHund = xTens - dW
        let xDollar = xHund - Self.dollarW
        let top = M.pillTop + (M.pillH - Double(Self.digitH)) / 2
        let v = M.mix(Double(run.0), Double(run.1), run.2)
        let hundreds = M.clamp01((v - 9000) / 1000)
        let tens = M.clamp01((v - 900) / 100)
        let hidden = 2 - hundreds - tens

        ZStack(alignment: .topLeading) {
            glyph("$").frame(width: Self.dollarW).offset(x: xDollar + hidden * dW, y: top)
            wheel(10000).opacity(hundreds).offset(x: xHund, y: top)
            wheel(1000).opacity(tens).offset(x: xTens, y: top)
            wheel(100).offset(x: xOnes, y: top)
            glyph(".").frame(width: Self.dotW).offset(x: xDot, y: top)
            wheel(10).offset(x: xDime, y: top)
            wheel(1).offset(x: xCent, y: top)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(StickySectionHeadersData.formatMoney(run.1, signed: false))
    }

    private func glyph(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 15, weight: .semibold))
            .monospacedDigit()
            .foregroundStyle(P.ink)
            .frame(height: Self.digitH)
    }

    private func wheel(_ place: Int) -> some View {
        let pos = M.wheelAt(run.0, run.1, run.2, place)
        return VStack(spacing: 0) {
            ForEach(0..<20, id: \.self) { i in
                Text(String(i % 10))
                    .font(.system(size: 15, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(P.ink)
                    .frame(width: Self.digitW, height: Self.digitH)
            }
        }
        .offset(y: -pos * Self.digitH)
        .frame(width: Self.digitW, height: Self.digitH, alignment: .top)
        .clipped()
    }
}

// MARK: month index

private struct StickySectionHeadersRail: View {
    typealias M = StickySectionHeadersMotion
    typealias P = StickySectionHeadersPalette
    let months: [String]
    let monthNames: [String]
    let geo: M.Geometry
    let scroll: StickySectionHeadersScroll
    let driver: StickySectionHeadersDriver

    private static let itemH: CGFloat = 26
    private static let bubbleLH: CGFloat = 26
    private static let slop: CGFloat = 12

    var body: some View {
        let a = scroll.railActive
        let rm = scroll.railMonth
        let month = a > 0.5 && rm >= 0 ? Double(rm) : M.monthAt(scroll.s, geo)
        let railH = CGFloat(months.count) * Self.itemH
        let w = P.railW - 6

        ZStack(alignment: .topLeading) {
            Capsule()
                .fill(P.ink)
                .shadow(color: P.shadow.opacity(0.18), radius: 6, y: 3)
                .frame(width: w - 4, height: Self.itemH)
                .offset(x: Self.slop + 2, y: 8 + month * Self.itemH)
            VStack(spacing: 0) {
                ForEach(0..<months.count, id: \.self) { i in
                    Text(months[i])
                        .font(.system(size: 12, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(P.blend(P.bg, P.mute, M.clamp01(abs(month - Double(i)))))
                        .frame(width: w, height: Self.itemH)
                }
            }
            .offset(x: Self.slop, y: 8)

        }
        .frame(width: Self.slop + w, height: railH + 16, alignment: .topLeading)
        .contentShape(Rectangle())
        .overlay(alignment: .topTrailing) {
            // The bubble beside the finger with the full month name.
            Text(monthNames[max(0, rm)])
                .font(.system(size: 19, weight: .bold))
                .foregroundStyle(P.bg)
                .frame(minWidth: 96, alignment: .leading)
                .frame(height: Self.bubbleLH)
                .padding(.horizontal, 18)
                .frame(height: 44)
                .background(Capsule().fill(P.ink).shadow(color: P.shadow.opacity(0.2), radius: 16, y: 8))
                .fixedSize()
                .scaleEffect(M.mix(0.92, 1, a))
                .opacity(a)
                .offset(x: -(P.railW + 4) + (1 - a) * 10, y: 8 + (Self.itemH - 44) / 2 + CGFloat(max(0, rm)) * Self.itemH)
                .allowsHitTesting(false)
        }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { v in
                    if !driver.railDragging {
                        driver.railBegan()
                        scroll.railMonth = -1
                        withAnimation(.easeInOut(duration: 0.14)) { scroll.railActive = 1 }
                    }
                    pick(v.location.y - 8)
                }
                .onEnded { _ in
                    driver.railEnded()
                    withAnimation(.easeInOut(duration: 0.26)) { scroll.railActive = 0 }
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Month index")
        .accessibilityValue(monthNames[min(monthNames.count - 1, max(0, Int(month.rounded())))])
    }

    private func pick(_ y: CGFloat) {
        let i = max(0, min(months.count - 1, Int(floor(y / Self.itemH))))
        if i != scroll.railMonth {
            scroll.railMonth = i
            driver.railJump(to: i)
        }
    }
}

/// The ghost finger of the scripted scroll.
private struct StickySectionHeadersGhost: View {
    let scroll: StickySectionHeadersScroll
    let width: CGFloat

    var body: some View {
        let g = scroll.ghostT.map { StickySectionHeadersMotion.ghostAt($0) } ?? (0, 0.78)
        Circle()
            .fill(Color.white.opacity(0.5))
            .overlay(Circle().strokeBorder(Color(.sRGB, red: 40 / 255, green: 34 / 255, blue: 26 / 255, opacity: 0.16), lineWidth: 1))
            .shadow(color: StickySectionHeadersPalette.shadow.opacity(0.16), radius: 10, y: 4)
            .frame(width: 44, height: 44)
            .opacity(g.0 * 0.85)
            .offset(x: width * 0.5 - 22, y: g.1 * scroll.viewH - 22)
            .allowsHitTesting(false)
    }
}
