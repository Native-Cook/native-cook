import CoreText
import SwiftUI

private typealias M = GlowTipScrubberMotion
private enum S {
    static let page = Color(hex: 0x1b1b1d), well = Color(hex: 0x242426), pills = Color(hex: 0x2a2a2d), pillOn = Color(hex: 0x3a3a3d), ink = Color.white, soft = Color(hex: 0xc7c7cc), mute = Color(hex: 0x8a8a8e), faint = Color(hex: 0x6c6c70), red = Color(hex: 0xff5a5f), coin = Color(hex: 0xf7931a)
    static let margin = 16.0, bar = 44.0, coinSize = 48.0, gutter = 88.0, tagH = 23.0, tagGap = 8.0, chartPad = 16.0, chartMin = 220.0, pillsH = 44.0, pillInset = 4.0, tickEvery = 12.0, heldIn = 0.15, heldOut = 0.25, drawFor = 1.0, slotFor = 0.22
    static let nameSize = 15.0, nameLine = 20.0, priceSize = 34.0, priceLine = 41.0, priceTracking = -0.4, changeSize = 15.0, changeLine = 20.0, dateTop = 2.0, headerHeight = nameLine + priceLine + changeLine * 2 + dateTop
    static func quad(_ t: Double) -> Double { let u = M.clamp(t); return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
}
private struct GlowTipScrubberScalar {
    var from: Double, to: Double, at = Date.distantPast, duration = 0.0, curve = 0
    init(_ value: Double) { from = value; to = value }
    func value(_ date: Date) -> Double { let u = duration == 0 ? 1 : M.clamp(date.timeIntervalSince(at) / duration); let eased = curve == 1 ? M.ease(u) : (curve == 2 ? S.quad(u) : (curve == 3 ? pow(u, 3) : u)); return from + (to - from) * eased }
    mutating func target(_ value: Double, _ date: Date, _ duration: Double, _ curve: Int = 2) { from = self.value(date); to = value; at = date; self.duration = duration; self.curve = curve }
    var end: Date { at.addingTimeInterval(duration) }
}
private struct GlowTipScrubberHand {
    var tip = GlowTipScrubberScalar(0), held = GlowTipScrubberScalar(0), lit = GlowTipScrubberScalar(0), reveal = GlowTipScrubberScalar(1), shown = GlowTipScrubberScalar(1), marks = GlowTipScrubberScalar(1), down = 0.0
    init() {}
    init(_ f: GlowTipScrubberFilm) { tip = .init(f.tip); held = .init(f.held); lit = .init(f.lit); reveal = .init(f.reveal); shown = .init(f.shown); marks = .init(f.marks); down = f.down }
    func film(_ date: Date) -> GlowTipScrubberFilm { .init(tip: tip.value(date), held: held.value(date), down: down, lit: lit.value(date), reveal: reveal.value(date), shown: shown.value(date), marks: marks.value(date)) }
}

struct GlowTipScrubber: View {
    var symbol = "EMB", name = "Ember coin", currency = "£", mark = "E"
    var disclaimer = "Prices on this screen are made up for illustration. Crypto prices move fast and you could lose all the money you put in."
    var footnote = "This is the midpoint between buy and sell rates. The rate you get depends on whether you buy or sell."
    var ranges = GlowTipScrubberRange.samples
    var initialRange = 1
    var autoPlay = false
    var onScrub: ((Double?, Double?) -> Void)? = nil
    var onRangeChange: ((GlowTipScrubberRange, Int) -> Void)? = nil
    var onClose: (() -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var rangeIndex: Int? = nil
    @State private var hand = GlowTipScrubberHand()
    @State private var slot = GlowTipScrubberScalar(1)
    @State private var automatic = false
    @State private var origin = Date.now
    @State private var sampledAt = Date.now
    @State private var autoMoving = false
    @State private var busy = false
    @State private var busyUntil = Date.distantPast
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var lastTick = 0.0
    @State private var reels = GlowTipScrubberRollingPrice()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var tickHaptic = UISelectionFeedbackGenerator()
    private var index: Int { rangeIndex ?? initialRange }
    private func film(_ date: Date) -> GlowTipScrubberFilm { automatic ? M.filmAt(max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop), still) : hand.film(date) }
    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                HStack {
                    Button { onClose?() } label: { Image(systemName: "xmark").font(.system(size: 26)).foregroundStyle(S.ink).frame(width: 26, height: 26).padding(10).contentShape(Rectangle()) }.offset(x: -10).buttonStyle(.plain).accessibilityLabel("Close")
                    Spacer()
                    HStack(spacing: 24) { Image(systemName: "bell.badge"); Image(systemName: "star.fill") }.font(.system(size: 24)).foregroundStyle(S.ink)
                }.frame(height: S.bar).padding(.horizontal, 20)
                Text(disclaimer).font(.system(size: 13)).lineSpacing(18 - UIFont.systemFont(ofSize: 13).lineHeight).padding(.vertical, (18 - UIFont.systemFont(ofSize: 13).lineHeight) / 2).foregroundStyle(S.mute)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(12).background(S.well, in: RoundedRectangle(cornerRadius: 12, style: .continuous)).padding(.horizontal, S.margin).padding(.top, 12)
                TimelineView(.animation(paused: !(busy || autoMoving))) { timeline in
                    let date = busy || autoMoving ? timeline.date : sampledAt, f = film(date)
                    VStack(spacing: 0) {
                        Canvas { c, size in header(c, size.width, f, date) }.frame(height: S.headerHeight).padding(.horizontal, S.margin)
                            .accessibilityLabel("\(name) price")
                        Canvas { c, size in
                            let width = size.width - S.gutter, range = ranges[index], low = range.prices.min()!, high = range.prices.max()!, span = high - low == 0 ? 1 : high - low
                            GlowTipScrubberDrawChart(c, range, width, size.height, S.chartPad, f)
                            text(c, M.grouped(high), size.width - S.margin, S.chartPad - 8, 13, 16, S.mute.opacity(0.4 + 0.6 * f.marks), .regular, trailing: true)
                            text(c, M.grouped(low), size.width - S.margin, size.height - S.chartPad - 8, 13, 16, S.mute.opacity(0.4 + 0.6 * f.marks), .regular, trailing: true)
                            let rule = S.chartPad + (1 - (range.prices[0] - low) / span) * (size.height - 2 * S.chartPad), top = rule + S.tagGap + S.tagH <= size.height ? rule + S.tagGap : rule - S.tagGap - S.tagH, label = M.grouped(range.prices[0]), w = (label as NSString).size(withAttributes: [.font: UIFont.monospacedDigitSystemFont(ofSize: 13, weight: .regular)]).width + 16
                            var tag = c; tag.opacity *= f.marks; tag.fill(RoundedRectangle(cornerRadius: 6).path(in: CGRect(x: 4, y: top, width: w, height: S.tagH)), with: .color(S.well)); text(tag, label, 12, top + 3, 13, 17, S.soft)
                        }.frame(minHeight: S.chartMin).contentShape(Rectangle()).gesture(DragGesture(minimumDistance: 0).onChanged { value in scrub(value.location.x, proxy.size.width - S.margin - S.gutter) }.onEnded { _ in lift() })
                            .padding(.leading, S.margin).padding(.top, 20).accessibilityLabel("\(name) price chart, \(ranges[index].span)")
                        controls(date).padding(.top, 16)
                    }
                }.padding(.top, 16)
                Text(footnote).font(.system(size: 12)).lineSpacing(16 - UIFont.systemFont(ofSize: 12).lineHeight).padding(.vertical, (16 - UIFont.systemFont(ofSize: 12).lineHeight) / 2).foregroundStyle(S.faint).frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, S.margin).padding(.top, 16)
            }.padding(.bottom, max(0, 16 - proxy.safeAreaInsets.bottom)).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }.background(S.page.ignoresSafeArea()).preferredColorScheme(.dark)
            .onAppear { slot = .init(Double(initialRange)); tapHaptic.prepare(); tickHaptic.prepare() }
            .task(id: busyUntil) { do { try await wait(busyUntil); sampledAt = .now; busy = false } catch {} }
            .task(id: touches) { await autoplay() }.onChange(of: autoPlay) { _, _ in touches += 1 }
    }
    private func controls(_ date: Date) -> some View {
        HStack(spacing: 12) {
            GeometryReader { p in
                let width = (p.size.width - S.pillInset * 2) / Double(ranges.count)
                ZStack(alignment: .topLeading) {
                    Capsule().fill(S.pills)
                    Capsule().fill(S.pillOn).frame(width: width, height: S.pillsH - S.pillInset * 2).offset(x: S.pillInset + slot.value(date) * width, y: S.pillInset)
                    HStack(spacing: 0) { ForEach(ranges.indices, id: \.self) { i in Button { pick(i) } label: { Text(ranges[i].label).font(.system(size: 15, weight: .medium)).foregroundStyle(i == index ? S.ink : S.mute).frame(maxWidth: .infinity, maxHeight: .infinity) }.buttonStyle(.plain).accessibilityAddTraits(i == index ? .isSelected : []) } }.padding(.horizontal, S.pillInset)
                }.frame(width: p.size.width, height: S.pillsH, alignment: .topLeading)
            }.frame(height: S.pillsH)
            Image(systemName: "chart.xyaxis.line").font(.system(size: 20)).foregroundStyle(S.soft).frame(width: S.pillsH, height: S.pillsH).background(S.pills, in: Circle())
        }.padding(.horizontal, S.margin)
    }
    private func touch(_ date: Date) { lastTouch = date; touches += 1 }
    private func snapshot(_ date: Date) { if automatic { hand = .init(film(date)); automatic = false; autoMoving = false } }
    private func active(_ date: Date) { sampledAt = date; busyUntil = [hand.tip.end, hand.held.end, hand.lit.end, hand.reveal.end, hand.marks.end, slot.end, date.addingTimeInterval(still ? 0 : GlowTipScrubberRollingPrice.roll)].max()!; busy = busyUntil > date }
    private func scrub(_ x: Double, _ width: Double) {
        let date = Date.now, begin = automatic || hand.down == 0
        snapshot(date); hand.tip = .init(M.clamp(x / width))
        if begin { hand.reveal = .init(1); hand.shown = .init(1); hand.down = 1; hand.held.target(1, date, still ? 0 : S.heldIn); hand.lit.target(1, date, still ? 0 : S.heldIn); hand.marks.target(0, date, still ? 0 : S.heldIn); lastTick = x; touch(date) }
        else { if abs(x - lastTick) >= S.tickEvery { tickHaptic.selectionChanged(); tickHaptic.prepare(); lastTick = x }; let r = ranges[index]; onScrub?(M.priceAt(r.prices, hand.tip.value(date)), r.start + hand.tip.value(date) * (r.end - r.start)) }
        active(date)
    }
    private func lift() { let date = Date.now; hand.down = 0; hand.held.target(0, date, still ? 0 : S.heldOut); hand.lit.target(0, date, still ? 0 : S.heldOut); hand.marks.target(1, date, still ? 0 : S.heldOut * 2); onScrub?(nil, nil); touch(date); active(date) }
    private func show(_ next: Int, _ draw: Bool, _ date: Date) {
        rangeIndex = next; slot.target(Double(next), date, still ? 0 : S.slotFor, 1); hand.shown = .init(1)
        if still || !draw { hand.reveal = .init(1); return }
        hand.tip = .init(0); hand.tip.target(1, date, S.drawFor, 0); hand.reveal = .init(0); hand.reveal.target(1, date, S.drawFor, 0)
        hand.lit = .init(1); hand.lit.target(0, date, S.drawFor + 0.25, 3); hand.marks = .init(0); hand.marks.target(1, date, S.drawFor + 0.3, 3)
    }
    private func pick(_ next: Int) { let date = Date.now; snapshot(date); hand.down = 0; hand.held.target(0, date, still ? 0 : S.heldOut); show(next, true, date); tapHaptic.impactOccurred(); tapHaptic.prepare(); onRangeChange?(ranges[next], next); touch(date); active(date) }
    private func wait(_ date: Date) async throws { while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }; try Task.checkCancellation() }
    private func autoplay() async {
        guard autoPlay else { return }
        do {
            if touches > 0 { try await wait(lastTouch.addingTimeInterval(M.userHold)); guard hand.down == 0 else { return } }
            let now = Date.now; if index != initialRange { show(initialRange, false, now); active(now) }; origin = now; automatic = true; sampledAt = now
            let events: [(Double, Bool)] = still ? [(M.pressAt, false), (M.jumpTo, false)] : [(M.pressAt, true), (M.backTo + (M.jumpTo - M.backTo) / (M.overshoot + 1) + GlowTipScrubberRollingPrice.roll, false), (M.jumpTo, true), (M.jumpTo + M.lift, false), (M.fadeAt, true), (M.drawAt + M.drawFor + M.marksIn, false)]
            var start = now
            while true { for (t, moving) in events { try await wait(start.addingTimeInterval(t)); autoMoving = moving; sampledAt = .now }; start = start.addingTimeInterval(M.loop); try await wait(start); origin = start; sampledAt = .now }
        } catch {}
    }
    private func text(_ c: GraphicsContext, _ value: String, _ x: Double, _ top: Double, _ size: Double, _ height: Double, _ color: Color, _ weight: UIFont.Weight = .regular, trailing: Bool = false, tabular: Bool = true) {
        let font = tabular ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight) : UIFont.systemFont(ofSize: size, weight: weight)
        let baseline = top + (height + CTFontGetAscent(font as CTFont) - CTFontGetDescent(font as CTFont)) / 2
        c.draw(Text(value).font(Font(font)).foregroundStyle(color), at: CGPoint(x: x, y: baseline + CTFontGetDescent(font as CTFont)), anchor: trailing ? .bottomTrailing : .bottomLeading)
    }
    private func header(_ c: GraphicsContext, _ width: Double, _ f: GlowTipScrubberFilm, _ date: Date) {
        let r = ranges[index], price = f.down != 0 ? M.priceAt(r.prices, f.tip) : r.prices.last!, change = price - r.prices[0]
        text(c, "\(symbol) · \(name)", 0, 0, S.nameSize, S.nameLine, S.soft, tabular: false)
        let digits = String(Int(floor(r.prices.max()!))).count, zeros = String(repeating: "0", count: digits), whole = zeros.enumerated().map { i, char in (i > 0 && (digits - i) % 3 == 0 ? "," : "") + String(char) }.joined(), pattern = currency + whole + ".00"
        reels.draw(c, value: price, pattern: pattern, font: .monospacedDigitSystemFont(ofSize: S.priceSize, weight: .semibold), lineHeight: S.priceLine, tracking: S.priceTracking, color: S.ink, still: still, date: date, y: S.nameLine)
        let label = (change < 0 ? "−" : "+") + currency + M.grouped(change), percent = (change < 0 ? "▼ " : "▲ ") + M.fixed(abs(change / r.prices[0] * 100)) + "%", cw = (label as NSString).size(withAttributes: [.font: UIFont.monospacedDigitSystemFont(ofSize: 15, weight: .regular)]).width
        text(c, label, 0, 61, 15, 20, S.mute); text(c, percent, cw + 6, 61, 15, 20, change < 0 ? S.red : GlowTipScrubberGreen, .medium)
        text(c, f.down != 0 ? M.dateLabel(r.start + M.clamp(f.tip) * (r.end - r.start), r.hours) : r.span, 0, 83, 15, 20, S.mute)
        c.fill(Path(ellipseIn: CGRect(x: width - S.coinSize, y: 4, width: S.coinSize, height: S.coinSize)), with: .color(S.coin))
        var coin = c; coin.translateBy(x: width - S.coinSize / 2, y: 4 + S.coinSize / 2); coin.rotate(by: .degrees(-12)); coin.draw(Text(mark).font(.system(size: 26, weight: .heavy)).foregroundStyle(S.ink), at: .zero, anchor: .center)
    }
}
