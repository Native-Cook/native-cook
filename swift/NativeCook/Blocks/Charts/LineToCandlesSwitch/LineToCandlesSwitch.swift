import CoreText
import SwiftUI

private typealias M = LineToCandlesSwitchMotion
private enum S {
    static let page = Color.black, ink = Color(hex: 0xe7e9ea), mute = Color(hex: 0x71767b), surface = Color(hex: 0x16181c), pillOn = Color(hex: 0x2f3336), rule = Color(hex: 0x2f3336), menu = Color.rgba(32, 35, 39, 0.97), hover = Color.white.opacity(0.12), green = Color(hex: 0x00b96b), red = Color(hex: 0xf6465d)
    static let margin = 16.0, logo = 40.0, chartPad = 14.0, chartMin = 200.0, dateH = 28.0, pillW = 46.0, pillH = 30.0, viewW = 58.0, menuW = 184.0, menuRow = 44.0, menuGap = 8.0
    static let menuDamping = 20.0, menuStiffness = 380.0, menuMass = 0.8, menuFrom = 0.8, menuOut = 0.14, slide = 0.22, heldIn = 0.15, heldOut = 0.22, springEnergy = 6e-9, headerH = 133.0
    static func out(_ t: Double) -> Double { 1 - pow(1 - M.clamp(t), 3) }
    static func quad(_ t: Double) -> Double { let u = M.clamp(t); return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
}
private struct LineToCandlesSwitchScalar {
    var from: Double, to: Double, at = Date.distantPast, duration = 0.0, curve = 0
    var spring: (damping: Double, stiffness: Double, mass: Double, velocity: Double)? = nil
    init(_ value: Double) { from = value; to = value }
    func sample(_ date: Date) -> (value: Double, velocity: Double) {
        let t = max(0, date.timeIntervalSince(at))
        if let spring {
            let z = spring.damping / (2 * spring.mass), w = sqrt(spring.stiffness / spring.mass - z * z), a = from - to, b = (spring.velocity + z * a) / w
            let delta = exp(-z * t) * (a * cos(w * t) + b * sin(w * t))
            let v = exp(-z * t) * (spring.velocity * cos(w * t) + (-z * b - w * a) * sin(w * t))
            let initial = spring.mass * spring.velocity * spring.velocity + spring.stiffness * a * a
            if initial == 0 || (spring.mass * v * v + spring.stiffness * delta * delta) / initial < S.springEnergy { return (to, 0) }
            return (to + delta, v)
        }
        let u = duration == 0 ? 1 : M.clamp(t / duration), eased = curve == 1 ? M.ease(u) : (curve == 2 ? S.quad(u) : S.out(u))
        return (from + (to - from) * eased, 0)
    }
    var end: Date {
        guard spring != nil else { return at.addingTimeInterval(duration) }
        var lo = 0.0, hi = 1.0
        while sample(at.addingTimeInterval(hi)).velocity != 0 { hi *= 2 }
        for _ in 0..<48 { let mid = (lo + hi) / 2; if sample(at.addingTimeInterval(mid)).velocity == 0 { hi = mid } else { lo = mid } }
        return at.addingTimeInterval(hi)
    }
    mutating func target(_ value: Double, _ date: Date, _ duration: Double, _ curve: Int = 1) { from = sample(date).value; to = value; at = date; self.duration = duration; self.curve = curve; spring = nil }
    mutating func targetSpring(_ value: Double, _ date: Date, _ damping: Double, _ stiffness: Double, _ mass: Double) { let now = sample(date); from = now.value; to = value; at = date; spring = (damping, stiffness, mass, now.velocity) }
}
private struct LineToCandlesSwitchTransition {
    var from: LineToCandlesSwitchFrame, to: LineToCandlesSwitchFrame, at: Date, length: Double
    func time(_ date: Date) -> Double { length == 0 ? 1 : max(0, date.timeIntervalSince(at)) }
    func frame(_ date: Date) -> LineToCandlesSwitchFrame { LineToCandlesSwitchFrameAt(from, to, time(date), max(length, 0.001)) }
    var end: Date { at.addingTimeInterval(length) }
}
private struct LineToCandlesSwitchControlsAnchor: PreferenceKey {
    static var defaultValue: Anchor<CGRect>? = nil
    static func reduce(value: inout Anchor<CGRect>?, nextValue: () -> Anchor<CGRect>?) { value = nextValue() ?? value }
}
private struct LineToCandlesSwitchRowStyle: ButtonStyle {
    let hover: Bool
    func makeBody(configuration: Configuration) -> some View { configuration.label.background(configuration.isPressed || hover ? S.hover : .clear) }
}

struct LineToCandlesSwitch: View {
    var name = "Northwind Corp", ticker = "NWND", currency = "$", quote = "USD", afterHours = "After hours $198.52 (−0.18%)"
    var ranges = LineToCandlesSwitchRange.samples
    var autoPlay = false
    var noteAuthor = "Market note", note = "Northwind closed the week up almost ten percent after its quarterly results beat estimates."
    var onRangeChange: ((LineToCandlesSwitchRange, Int) -> Void)? = nil
    var onViewChange: ((String) -> Void)? = nil
    var onBack: (() -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.displayScale) private var scale
    @State private var rangeIndex = 1
    @State private var viewIndex = 0
    @State private var menuState = -1
    @State private var menu = LineToCandlesSwitchScalar(0)
    @State private var slot = LineToCandlesSwitchScalar(1)
    @State private var check = LineToCandlesSwitchScalar(0)
    @State private var cursor = 0.5
    @State private var held = LineToCandlesSwitchScalar(0)
    @State private var down = false
    @State private var transition: LineToCandlesSwitchTransition? = nil
    @State private var automatic = false
    @State private var origin = Date.now
    @State private var sampleAt = Date.now
    @State private var autoScrubbing = false
    @State private var busy = false
    @State private var busyUntil = Date.distantPast
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var reels = LineToCandlesSwitchRollingNumber()
    @State private var haptic = UISelectionFeedbackGenerator()
    private func picture(_ date: Date) -> LineToCandlesSwitchTransition {
        if let transition { return transition }
        let base = M.frames(ranges[1], 2)[0]
        return .init(from: base, to: base, at: .distantPast, length: 0)
    }
    private func scrubState(_ date: Date) -> (x: Double, held: Double, down: Bool) {
        if automatic { let f = M.scrubAt(max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop)); return (still ? M.restX : f.x, still ? f.down : f.held, f.down != 0) }
        return (cursor, held.sample(date).value, down)
    }
    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                HStack { Button { onBack?() } label: { Image(systemName: "arrow.left").font(.system(size: 24)).foregroundStyle(S.ink).frame(width: 24, height: 24).padding(10).contentShape(Rectangle()) }.offset(x: -10).buttonStyle(.plain).accessibilityLabel("Back"); Spacer() }.frame(height: 44).padding(.horizontal, S.margin)
                TimelineView(.animation(paused: !(busy || autoScrubbing))) { timeline in
                    let date = busy || autoScrubbing ? timeline.date : sampleAt, p = picture(date), f = scrubState(date)
                    VStack(spacing: 0) {
                        Canvas { c, size in header(c, size.width, p.to.line, f.x, f.down, date) }.frame(height: S.headerH).padding(.horizontal, S.margin).accessibilityLabel("\(name) price")
                        Canvas { c, size in
                            let height = size.height - S.dateH
                            LineToCandlesSwitchDrawChart(c, p.from, p.to, p.time(date), max(p.length, 0.001), size.width, height, S.chartPad, f.x, f.held)
                            let r = ranges[rangeIndex], label = M.dateLabel(r.start + f.x * (r.end - r.start), r.hours), width = (label as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 14)]).width
                            text(c, label, (size.width - width) / 2, height + 5, 14, 18, S.mute.opacity(f.held))
                        }.frame(minHeight: S.chartMin + S.dateH).contentShape(Rectangle()).gesture(DragGesture(minimumDistance: 0).onChanged { value in scrub(value.location.x / proxy.size.width) }.onEnded { _ in lift() }).padding(.top, 12).accessibilityLabel("\(name) chart, \(ranges[rangeIndex].label), \(M.views[viewIndex].label)")
                        controls(date).padding(.top, 12)
                    }
                }.padding(.top, 4)
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "newspaper").font(.system(size: 20)).foregroundStyle(S.ink).frame(width: S.logo, height: S.logo).background(S.surface, in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(noteAuthor).font(.system(size: 15, weight: .bold)).foregroundStyle(S.ink).frame(height: 20)
                        Text(note).font(.system(size: 15)).lineSpacing(20 - UIFont.systemFont(ofSize: 15).lineHeight).padding(.vertical, (20 - UIFont.systemFont(ofSize: 15).lineHeight) / 2).foregroundStyle(S.ink).lineLimit(2).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }.padding(.horizontal, S.margin).padding(.top, 14).frame(maxWidth: .infinity, alignment: .leading).overlay(alignment: .top) { Rectangle().fill(S.rule).frame(height: 1 / scale) }.padding(.top, 20)
            }.padding(.bottom, max(0, 16 - proxy.safeAreaInsets.bottom)).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .overlayPreferenceValue(LineToCandlesSwitchControlsAnchor.self) { anchor in
                    if let anchor { GeometryReader { geometry in
                        let rect = geometry[anchor]
                        TimelineView(.animation(paused: !(busy || autoScrubbing))) { timeline in
                            let date = busy || autoScrubbing ? timeline.date : sampleAt, value = menu.sample(date).value
                            if menuState >= 0 || value > 0 { menuRows(date).frame(width: S.menuW, height: S.menuRow * 4, alignment: .topLeading).background(S.menu, in: RoundedRectangle(cornerRadius: 14, style: .continuous)).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous)).shadow(color: .black.opacity(0.6), radius: 12, x: 0, y: 8).opacity(M.clamp(value)).scaleEffect(S.menuFrom + (1 - S.menuFrom) * value, anchor: .bottomTrailing).allowsHitTesting(menuState >= 0).position(x: rect.maxX - S.menuW / 2, y: rect.minY - S.menuGap - S.menuRow * 2) }
                        }
                    } }
                }
        }.background(S.page.ignoresSafeArea()).preferredColorScheme(.dark)
            .onAppear { haptic.prepare(); let date = Date.now, p = picture(date); transition = p; if !still && p.to.line.last! < 100 { busy = true; busyUntil = date.addingTimeInterval(LineToCandlesSwitchRollingNumber.reveal) } }
            .task(id: busyUntil) { do { try await wait(busyUntil); sampleAt = .now; busy = false } catch {} }
            .task(id: touches) { await autoplay() }.onChange(of: autoPlay) { _, _ in touches += 1 }
    }
    private func controls(_ date: Date) -> some View {
        HStack {
            ZStack(alignment: .topLeading) {
                Capsule().fill(S.pillOn).frame(width: S.pillW, height: S.pillH).offset(x: slot.sample(date).value * S.pillW)
                HStack(spacing: 0) { ForEach(ranges.indices, id: \.self) { i in Button { pickRange(i) } label: { Text(ranges[i].label).font(.system(size: 14, weight: .semibold)).foregroundStyle(i == rangeIndex ? S.ink : S.mute).frame(width: S.pillW, height: S.pillH) }.buttonStyle(.plain).accessibilityAddTraits(i == rangeIndex ? .isSelected : []) } }
            }.frame(width: S.pillW * Double(ranges.count), height: S.pillH, alignment: .topLeading)
            Spacer()
            Button { let date = Date.now; takeover(date); setMenu(menuState >= 0 ? -1 : 4, date); touch(date); active(date) } label: { HStack(spacing: 4) { glyph(M.views[viewIndex].group, S.ink); Image(systemName: "chevron.up.chevron.down").font(.system(size: 13)).foregroundStyle(S.mute) }.frame(width: S.viewW, height: S.pillH).background(S.surface, in: Capsule()) }.buttonStyle(.plain).accessibilityLabel("Chart type, \(M.views[viewIndex].label)").accessibilityValue(menuState >= 0 ? "Expanded" : "Collapsed")
        }.anchorPreference(key: LineToCandlesSwitchControlsAnchor.self, value: .bounds) { $0 }.padding(.horizontal, S.margin)
    }
    private func glyph(_ group: Int, _ color: Color) -> some View {
        Canvas { c, _ in
            if group == 0 { var p = Path(); p.move(to: CGPoint(x: 1, y: 12)); for point in [CGPoint(x: 5, y: 6), CGPoint(x: 9, y: 9), CGPoint(x: 15, y: 2)] { p.addLine(to: point) }; c.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round)) }
            else { for i in 0..<2 { let x = 1.5 + Double(i) * 8, y = Double(i) * 3; c.fill(Path(CGRect(x: x + 2, y: y, width: 1, height: 13)), with: .color(color)); c.fill(RoundedRectangle(cornerRadius: 1).path(in: CGRect(x: x, y: y + 2, width: 5, height: 8)), with: .color(color)) } }
        }.frame(width: 16, height: 16)
    }
    private func menuRows(_ date: Date) -> some View {
        VStack(spacing: 0) { ForEach(M.views.indices, id: \.self) { i in Button { pickView(i) } label: { HStack(spacing: 0) { Image(systemName: "checkmark").font(.system(size: 17)).foregroundStyle(S.ink).opacity(M.clamp(1 - abs(check.sample(date).value - Double(i)) * 1.5)).frame(width: 26, alignment: .leading); Text(M.views[i].label).font(.system(size: 16)).foregroundStyle(S.ink).frame(maxWidth: .infinity, alignment: .leading); glyph(M.views[i].group, S.ink) }.padding(.leading, 10).padding(.trailing, 16).frame(height: S.menuRow).overlay(alignment: .top) { if i > 0 { Rectangle().fill(S.rule).frame(height: 1 / scale) } } }.buttonStyle(LineToCandlesSwitchRowStyle(hover: menuState == i)).accessibilityLabel(M.views[i].label) } }
    }
    private func touch(_ date: Date) { lastTouch = date; touches += 1 }
    private func takeover(_ date: Date) { let f = scrubState(date); cursor = f.x; held = .init(f.held); automatic = false; autoScrubbing = false; down = false; held.target(0, date, still ? 0 : S.heldOut, 2) }
    private func active(_ date: Date) { sampleAt = date; busyUntil = [picture(date).end, slot.end, check.end, menu.end, held.end, date.addingTimeInterval(still ? 0 : LineToCandlesSwitchRollingNumber.reveal)].max()!; busy = busyUntil > date }
    private func show(_ r: Int, _ v: Int, _ date: Date) { let current = picture(date).frame(date), next = M.frames(ranges[r], UInt32(r + 1))[v], d = still ? 0 : M.duration(current, next); transition = .init(from: current, to: next, at: date, length: d); rangeIndex = r; viewIndex = v; slot.target(Double(r), date, still ? 0 : S.slide); check.target(Double(v), date, still ? 0 : S.slide) }
    private func setMenu(_ state: Int, _ date: Date) { menuState = state; if still { menu = .init(state >= 0 ? 1 : 0) } else if state >= 0 { menu.targetSpring(1, date, S.menuDamping, S.menuStiffness, S.menuMass) } else { menu.target(0, date, S.menuOut, 2) } }
    private func pickRange(_ r: Int) { let date = Date.now; takeover(date); if menuState >= 0 { setMenu(-1, date) }; show(r, viewIndex, date); touch(date); haptic.selectionChanged(); haptic.prepare(); onRangeChange?(ranges[r], r); active(date) }
    private func pickView(_ v: Int) { let date = Date.now; takeover(date); setMenu(-1, date); show(rangeIndex, v, date); touch(date); haptic.selectionChanged(); haptic.prepare(); onViewChange?(M.views[v].label); active(date) }
    private func scrub(_ x: Double) { let date = Date.now, begin = automatic || !down; if automatic { let f = scrubState(date); held = .init(f.held); automatic = false; autoScrubbing = false }; if begin { if menuState >= 0 { setMenu(-1, date) }; down = true; held.target(1, date, still ? 0 : S.heldIn, 2); touch(date) }; cursor = M.clamp(x); active(date) }
    private func lift() { let date = Date.now; down = false; held.target(0, date, still ? 0 : S.heldOut, 2); touch(date); active(date) }
    private func wait(_ date: Date) async throws { while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }; try Task.checkCancellation() }
    private func autoplay() async {
        guard autoPlay else { return }
        do {
            if touches > 0 { try await wait(lastTouch.addingTimeInterval(M.userHold)); guard !down else { return } }
            let start = Date.now; origin = start; automatic = true; sampleAt = start
            let events = (M.steps.map { ($0.at, Optional($0)) } + [(M.pressAt, nil), (M.liftAt, nil), (M.liftAt + M.heldFor, nil)]).sorted { $0.0 < $1.0 }
            var loopStart = start
            while true {
                for (at, step) in events { try await wait(loopStart.addingTimeInterval(at)); let now = Date.now; if let step { if rangeIndex != step.range || viewIndex != step.view { show(step.range, step.view, now) }; if menuState != step.menu { setMenu(step.menu, now) }; active(now) }; if at == M.pressAt { autoScrubbing = !still }; if at == M.liftAt + M.heldFor { autoScrubbing = false }; sampleAt = now }
                loopStart = loopStart.addingTimeInterval(M.loop); try await wait(loopStart); origin = loopStart; sampleAt = .now
            }
        } catch {}
    }
    private func text(_ c: GraphicsContext, _ value: String, _ x: Double, _ top: Double, _ size: Double, _ height: Double, _ color: Color, _ weight: UIFont.Weight = .regular, tracking: Double = 0, tabular: Bool = false) {
        let font = tabular ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight) : UIFont.systemFont(ofSize: size, weight: weight)
        let baseline = top + (height + CTFontGetAscent(font as CTFont) - CTFontGetDescent(font as CTFont)) / 2
        c.draw(Text(value).font(Font(font)).tracking(tracking).foregroundStyle(color), at: CGPoint(x: x, y: baseline + CTFontGetDescent(font as CTFont)), anchor: .bottomLeading)
    }
    private func header(_ c: GraphicsContext, _ width: Double, _ line: [Double], _ x: Double, _ down: Bool, _ date: Date) {
        c.fill(RoundedRectangle(cornerRadius: 12, style: .continuous).path(in: CGRect(x: 0, y: 0, width: S.logo, height: S.logo)), with: .color(LineToCandlesSwitchLineGreen)); c.draw(Text(String(name.prefix(1))).font(.system(size: 20, weight: .heavy)).foregroundStyle(.white), at: CGPoint(x: 20, y: 20), anchor: .center)
        text(c, name, 52, 0, 17, 22, S.ink, .semibold); text(c, "\(ticker) · \(quote)", 52, 22, 14, 18, S.mute)
        let value = down ? M.lineAt(line, x) : line.last!, change = value - line[0], font = UIFont.monospacedDigitSystemFont(ofSize: 34, weight: .bold), currencyW = (currency as NSString).size(withAttributes: [.font: font, .kern: -0.5]).width
        text(c, currency, 0, 50, 34, 41, S.ink, .bold, tracking: -0.5)
        _ = reels.update(value: value, decimals: 2, wholeDigits: 3, fontSize: 34, lineHeight: 41, weight: .bold, color: S.ink, tracking: -0.5, still: still, date: date); reels.draw(c, x: currencyW, y: 50, decimals: 2, date: date)
        let label = (change < 0 ? "▼ " : "▲ ") + currency + M.fixed(abs(change)) + " (" + (change < 0 ? "−" : "+") + M.fixed(abs(change / line[0] * 100)) + "%)"
        text(c, label, 0, 93, 15, 20, change < 0 ? S.red : S.green, .medium, tabular: true); text(c, afterHours, 0, 115, 13, 18, S.mute)
    }
}
