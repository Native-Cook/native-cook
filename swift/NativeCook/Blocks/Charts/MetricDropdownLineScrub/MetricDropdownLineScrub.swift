import CoreText
import SwiftUI

private typealias D = MetricDropdownLineScrubData
private enum M {
    static let loop = 10.0, firstDown = 0.8, firstMove = [1.0, 3.2], firstPath = [0.06, 0.94], firstUp = 3.5, rangeOpen = 3.9, rangeHover = 4.45, rangePick = 4.65, metricOpen = 5.4, metricHover = 5.95, metricPick = 6.15, secondDown = 7.0, secondMove = [7.15, 8.4], secondPath = [0.92, 0.12], secondUp = 8.65, reset = 9.05, autoRange = 1, autoMetric = 1, userHold = 3.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func ease(_ t: Double) -> Double { let u = clamp(t); return u < 0.5 ? 4 * pow(u, 3) : 1 - pow(-2 * u + 2, 3) / 2 }
    static func glide(_ t: Double) -> Double { let u = clamp(t); return u * u * (3 - 2 * u) }
    static func fraction(_ t: Double, _ rest: Double) -> Double { if t >= secondDown { return secondPath[0] + (secondPath[1] - secondPath[0]) * glide((t - secondMove[0]) / (secondMove[1] - secondMove[0])) }; if t >= firstDown { return firstPath[0] + (firstPath[1] - firstPath[0]) * glide((t - firstMove[0]) / (firstMove[1] - firstMove[0])) }; return rest }
    static func on(_ t: Double) -> Bool { (t >= firstDown && t < firstUp) || (t >= secondDown && t < secondUp) }
    static func fade(_ t: Double, _ still: Bool) -> Double { if still { return on(t) ? 1 : 0 }; if t >= secondDown { return t < secondUp ? S.quad((t - secondDown) / S.scrubFade) : 1 - S.quad((t - secondUp) / S.scrubFade) }; return t < firstUp ? S.quad((t - firstDown) / S.scrubFade) : 1 - S.quad((t - firstUp) / S.scrubFade) }
    static func crossings(_ count: Int, _ times: [Double], _ path: [Double]) -> [Double] { guard count > 1 else { return [] }; return (0..<(count - 1)).compactMap { i in let target = (Double(i) + 0.5) / Double(count - 1), p = (target - path[0]) / (path[1] - path[0]); guard p > 0 && p < 1 else { return nil }; var lo = 0.0, hi = 1.0; for _ in 0..<48 { let mid = (lo + hi) / 2; if glide(mid) < p { lo = mid } else { hi = mid } }; return times[0] + hi * (times[1] - times[0]) + 1e-7 } }
}
private enum S {
    static let background = Color(hex: 0x0e0e0f), ink = Color.white, mute = Color(hex: 0x8a8a8e), button = Color(hex: 0x1e1e21), card = Color(hex: 0x17171a), tooltip = Color(hex: 0x2a2a2e), edge = Color.white.opacity(0.07), caption = Color(hex: 0xb5b5ba), menu = Color(hex: 0x242428), pressed = Color(hex: 0x34343a), separator = Color(hex: 0x36363c), tabIdle = Color(hex: 0x6b6b70)
    static let margin = 20.0, cardInset = 16.0, headerButton = 40.0, pillHeight = 40.0, pillGap = 10.0, pillsGap = 22.0, cardGap = 24.0, cardRadius = 24.0, plotTop = 62.0, plotBottom = 40.0, plotLeft = 16.0, plotRight = 60.0, grid = [0.0, 0.25, 0.5, 0.75, 1.0], tooltipW = 184.0, tooltipH = 88.0, tooltipRise = 46.0, menuW = 220.0, menuItem = 50.0, menuGap = 8.0, tabBar = 56.0
    static let morph = 0.5, resetMorph = 0.8, scrubFade = 0.16, menuIn = 0.2, labelFade = 0.22, jumpDip = 0.35, jumpFade = 0.22, followDamping = 24.0, followStiffness = 340.0, followMass = 0.7, springEnergy = 6e-9
    static let css = UnitCurve.bezier(startControlPoint: .init(x: 0.25, y: 0.1), endControlPoint: .init(x: 0.25, y: 1)), cssOut = UnitCurve.bezier(startControlPoint: .zero, endControlPoint: .init(x: 0.58, y: 1))
    static func quad(_ x: Double) -> Double { let u = M.clamp(x); return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
    static func out(_ x: Double) -> Double { 1 - pow(1 - M.clamp(x), 3) }
    static func pillColor(_ x: Double) -> Color { .rgba(floor(38 + 176 * x + 0.5), floor(38 + 176 * x + 0.5), floor(42 + 176 * x + 0.5), 1) }
}
private struct MetricDropdownLineScrubScalar {
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
        let u = duration == 0 ? 1 : M.clamp(t / duration), eased = curve == 1 ? M.ease(u) : (curve == 2 ? S.quad(u) : (curve == 4 ? S.css.value(at: u) : (curve == 5 ? S.cssOut.value(at: u) : S.out(u))))
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
private final class MetricDropdownLineScrubDrawing {
    let roller = MetricDropdownLineScrubRoller()
    var follow = MetricDropdownLineScrubScalar(0), lastX: Double? = nil, lastOn = false
    func update(_ x: Double, _ on: Bool, _ still: Bool, _ date: Date) { if still || !on || !lastOn || lastX == nil { follow = .init(x) } else if x != lastX { follow.targetSpring(x, date, S.followDamping, S.followStiffness, S.followMass) }; lastOn = on; lastX = x }
}
private struct MetricDropdownLineScrubGhost { var index: Int; var at: Date }
private struct MetricDropdownLineScrubGeometry { var width: Double; var height: Double; var headerH: Double
    var pillsY: Double { 16 + headerH + S.pillsGap }
    var cardY: Double { pillsY + S.pillHeight + S.cardGap }
    var cardW: Double { width - 2 * S.cardInset }
    var cardH: Double { max(0, height - cardY - 8 - S.tabBar) }
    var plot: MetricDropdownLineScrubPlot { .init(left: S.plotLeft, top: S.plotTop, width: max(0, cardW - S.plotLeft - S.plotRight), height: max(0, cardH - S.plotTop - S.plotBottom)) }
}
private struct MetricDropdownLineScrubFrame { var points: [Double]; var pick: Int; var x: Double; var y: Double; var on: Bool; var alpha: Double }
private struct MetricDropdownLineScrubPressStyle: ButtonStyle { @Binding var pressed: Int?; let row: Int
    func makeBody(configuration: Configuration) -> some View { configuration.label.onChange(of: configuration.isPressed) { _, value in pressed = value ? row : nil } }
}

struct MetricDropdownLineScrub: View {
    var title = "Deadlift", subtitle = "You're viewing demo data. This section will fill up with your data as you log workouts."
    var sessions = D.sessions
    var metrics = MetricDropdownLineScrubMetric.samples
    var ranges = MetricDropdownLineScrubRange.samples
    var initialMetric = 0, initialRange = 3
    var todayLabel = "Today"
    var autoPlay = false
    var onMetricChange: ((MetricDropdownLineScrubMetric, Int) -> Void)? = nil
    var onRangeChange: ((MetricDropdownLineScrubRange, Int) -> Void)? = nil
    var onScrub: ((String, Double) -> Void)? = nil
    var onHeaderPress: (() -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.displayScale) private var scale
    @State private var metricChoice: Int? = nil
    @State private var rangeChoice: Int? = nil
    @State private var from: [Double]? = nil
    @State private var to: [Double]? = nil
    @State private var morph = MetricDropdownLineScrubScalar(1)
    @State private var jump = MetricDropdownLineScrubScalar(1)
    @State private var scrub = MetricDropdownLineScrubScalar(0)
    @State private var menus = [MetricDropdownLineScrubScalar(0), MetricDropdownLineScrubScalar(0)]
    @State private var pills = [MetricDropdownLineScrubScalar(0), MetricDropdownLineScrubScalar(0)]
    @State private var arrow = [MetricDropdownLineScrubScalar(0), MetricDropdownLineScrubScalar(0)]
    @State private var menuKind: Int? = nil
    @State private var hover: Int? = nil
    @State private var pressed: Int? = nil
    @State private var raw = 0.0
    @State private var holding = false
    @State private var automatic = false
    @State private var origin = Date.now
    @State private var restRaw = 0.0
    @State private var sampleAt = Date.now
    @State private var busy = false
    @State private var hardUntil = Date.distantPast
    @State private var animation = 0
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var labelAt = [Date.now, Date.now]
    @State private var axisAt = Date.now
    @State private var datesAt = Date.now
    @State private var axisGhosts: [MetricDropdownLineScrubGhost] = []
    @State private var dateGhosts: [MetricDropdownLineScrubGhost] = []
    @State private var drawing = MetricDropdownLineScrubDrawing()
    @State private var tick = UISelectionFeedbackGenerator()
    @State private var press = UIImpactFeedbackGenerator(style: .light)
    private var mi: Int { metricChoice ?? initialMetric }
    private var ri: Int { rangeChoice ?? initialRange }
    private var start: Int { D.rangeStart(sessions, ranges[ri]) }
    private var count: Int { sessions.count - start }
    private func fraction(_ date: Date) -> Double { automatic ? M.fraction(max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop), restRaw) : raw }
    private func isOn(_ date: Date) -> Bool { automatic ? M.on(max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop)) : holding }
    private func alpha(_ date: Date) -> Double { automatic ? M.fade(max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop), still) : scrub.sample(date).value }
    private func normalized(_ date: Date) -> [Double] { let b = to ?? D.layoutPoints(metrics[initialMetric], D.rangeStart(sessions, ranges[initialRange]), sessions.count), a = from ?? b, p = morph.sample(date).value; return a.indices.map { a[$0] + (b[$0] - a[$0]) * p } }
    private func frame(_ date: Date, _ geo: MetricDropdownLineScrubGeometry) -> MetricDropdownLineScrubFrame { let p = normalized(date), plot = geo.plot, pick = count <= 1 ? 0 : Int(floor(M.clamp(fraction(date)) * Double(count - 1) + 0.5)), slot = count <= 1 ? 0 : Int(floor(Double(pick * (sessions.count - 1)) / Double(count - 1) + 0.5)); var pts: [Double] = []; for i in stride(from: 0, to: p.count, by: 2) { pts += [plot.left + p[i] * plot.width, plot.top + (1 - p[i + 1]) * plot.height] }; return .init(points: pts, pick: pick, x: pts[slot * 2], y: pts[slot * 2 + 1], on: isOn(date), alpha: alpha(date)) }
    var body: some View {
        GeometryReader { proxy in
            let subtitleLines = wrapped(subtitle, 13, proxy.size.width - 96, 2), headerH = max(58, 38 + 6 + Double(subtitleLines.count) * 18), geo = MetricDropdownLineScrubGeometry(width: proxy.size.width, height: proxy.size.height, headerH: headerH)
            TimelineView(.animation(paused: !busy)) { timeline in
                let date = busy ? timeline.date : sampleAt, f = frame(date, geo)
                ZStack(alignment: .topLeading) {
                    Canvas { c, _ in draw(c, geo, f, date, subtitleLines) }
                    Button { touched(); onHeaderPress?() } label: { Color.clear.frame(width: S.headerButton, height: S.headerButton).contentShape(Circle()) }.buttonStyle(.plain).offset(x: geo.width - S.margin - S.headerButton, y: 34).accessibilityLabel("More")
                    ForEach(0..<2, id: \.self) { kind in Button { touched(); press.impactOccurred(); press.prepare(); setMenu(menuKind == kind ? nil : kind, .now); activate(.now) } label: { Color.clear.frame(width: (geo.width - 40 - S.pillGap) / 2, height: S.pillHeight).contentShape(Capsule()) }.buttonStyle(.plain).offset(x: S.margin + Double(kind) * ((geo.width - 40 - S.pillGap) / 2 + S.pillGap), y: geo.pillsY).accessibilityLabel(kind == 0 ? metrics[mi].label : ranges[ri].label).accessibilityValue(menuKind == kind ? "Expanded" : "Collapsed") }
                    Color.clear.frame(width: geo.cardW, height: geo.cardH).contentShape(RoundedRectangle(cornerRadius: S.cardRadius)).offset(x: S.cardInset, y: geo.cardY).gesture(DragGesture(minimumDistance: 0).onChanged { value in let now = Date.now; if !holding { stop(now); holding = true; scrub.target(1, now, still ? 0 : S.scrubFade, 2); touched() }; raw = geo.plot.width > 0 ? (value.location.x - geo.plot.left) / geo.plot.width : 0; activate(now) }.onEnded { _ in holding = false; scrub.target(0, .now, still ? 0 : S.scrubFade, 2); touched(); activate(.now) }).accessibilityLabel("\(metrics[mi].label), \(ranges[ri].label)")
                    if let kind = menuKind { Button { touched(); press.impactOccurred(); setMenu(nil, .now); activate(.now) } label: { Color.clear.frame(width: geo.width, height: geo.height).contentShape(Rectangle()) }.buttonStyle(.plain).accessibilityLabel("Close menu")
                        ForEach(0..<(kind == 0 ? metrics.count : ranges.count), id: \.self) { i in Button { touched(); choose(kind, i, false, .now) } label: { Color.clear.frame(width: S.menuW - 12, height: S.menuItem).contentShape(RoundedRectangle(cornerRadius: 12)) }.buttonStyle(MetricDropdownLineScrubPressStyle(pressed: $pressed, row: i)).offset(x: menuX(kind, geo) + 6, y: geo.pillsY + S.pillHeight + S.menuGap + 6 + 1 / scale + Double(i) * S.menuItem).accessibilityLabel(kind == 0 ? metrics[i].label : ranges[i].label) }
                    }
                }.frame(width: geo.width, height: geo.height, alignment: .topLeading).onChange(of: f.pick) { _, index in let session = start + min(index, count - 1); if !automatic && holding { tick.selectionChanged(); tick.prepare() }; onScrub?(sessions[session], metrics[mi].values[session]); activate(.now) }
            }
        }.background(S.background.ignoresSafeArea()).preferredColorScheme(.dark).onAppear { tick.prepare(); press.prepare(); let now = Date.now; axisAt = now; datesAt = now; labelAt = [now, now]; activate(now, still ? 0 : S.labelFade) }
            .task(id: animation) { do { await Task.yield(); while true { let end = max(hardUntil, max(drawing.follow.end, drawing.roller.end)); if end <= .now { sampleAt = .now; busy = false; break }; try await wait(end) } } catch {} }
            .task(id: touches) { await autoplay() }.onChange(of: autoPlay) { _, _ in touches += 1 }
    }
    private func stop(_ date: Date) { if automatic { raw = fraction(date); let a = alpha(date); automatic = false; scrub = .init(a) } }
    private func touched() { let now = Date.now; stop(now); if !holding { scrub.target(0, now, still ? 0 : S.scrubFade, 2) }; hover = nil; lastTouch = now; touches += 1; activate(now) }
    private func activate(_ date: Date, _ extra: Double = MetricDropdownLineScrubRoller.roll) { sampleAt = date; let ends = [date.addingTimeInterval(extra), morph.end, jump.end, scrub.end] + menus.map(\.end) + pills.map(\.end) + arrow.map(\.end) + [axisAt.addingTimeInterval(still ? 0 : S.labelFade), datesAt.addingTimeInterval(still ? 0 : S.labelFade)]; hardUntil = ends.max()!; animation += 1; busy = hardUntil > date }
    private func setMenu(_ kind: Int?, _ date: Date) { menuKind = kind; for i in 0..<2 { menus[i].target(kind == i ? 1 : 0, date, S.menuIn, 5); pills[i].target(kind == i ? 1 : 0, date, S.menuIn, 4); arrow[i].target(kind == i ? 1 : 0, date, still ? 0 : S.menuIn, 4) } }
    private func show(_ metric: Int, _ range: Int, _ duration: Double, _ date: Date) { let now = normalized(date); if metric != mi { axisGhosts = axisGhosts.filter { date.timeIntervalSince($0.at) < S.labelFade } + [.init(index: mi, at: date)]; axisAt = date; labelAt[0] = date }; if range != ri { dateGhosts = dateGhosts.filter { date.timeIntervalSince($0.at) < S.labelFade } + [.init(index: ri, at: date)]; datesAt = date; labelAt[1] = date }; let first = D.rangeStart(sessions, ranges[range]); from = now; to = D.layoutPoints(metrics[metric], first, sessions.count); metricChoice = metric; rangeChoice = range; if still { morph = .init(1); jump = .init(S.jumpDip); jump.target(1, date, S.jumpFade, 2) } else { morph = .init(0); morph.target(1, date, duration, 1) } }
    private func choose(_ kind: Int, _ index: Int, _ auto: Bool, _ date: Date) { setMenu(nil, date); hover = nil; if !auto { press.impactOccurred(); press.prepare() }; if kind == 0 && index != mi { show(index, ri, S.morph, date); onMetricChange?(metrics[index], index) }; if kind == 1 && index != ri { show(mi, index, S.morph, date); onRangeChange?(ranges[index], index) }; activate(date) }
    private func rest(_ date: Date) { setMenu(nil, date); hover = nil; scrub.target(0, date, still ? 0 : S.scrubFade, 2); if mi != initialMetric || ri != initialRange { show(initialMetric, initialRange, S.resetMorph, date) }; activate(date) }
    private func wait(_ date: Date) async throws { while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }; try Task.checkCancellation() }
    private func autoplay() async { guard autoPlay else { return }; do { if touches > 0 { try await wait(lastTouch.addingTimeInterval(M.userHold)); guard !holding else { return }; rest(.now); try await wait(lastTouch.addingTimeInterval(M.userHold + S.resetMorph + 0.2)) }; automatic = true; restRaw = raw; origin = .now; var begin = origin
        let n1 = sessions.count - D.rangeStart(sessions, ranges[initialRange]), n2 = sessions.count - D.rangeStart(sessions, ranges[M.autoRange])
        let events = ([M.firstDown, M.firstUp, M.firstUp + S.scrubFade, M.rangeOpen, M.rangeHover, M.rangePick, M.metricOpen, M.metricHover, M.metricPick, M.secondDown, M.secondUp, M.secondUp + S.scrubFade, M.reset] + M.crossings(n1, M.firstMove, M.firstPath) + M.crossings(n2, M.secondMove, M.secondPath)).sorted()
        while true { for at in events { try await wait(begin.addingTimeInterval(at)); let now = Date.now; if at == M.rangeOpen { setMenu(1, now) }; if at == M.rangeHover { hover = M.autoRange }; if at == M.rangePick { choose(1, M.autoRange, true, now) }; if at == M.metricOpen { setMenu(0, now) }; if at == M.metricHover { hover = M.autoMetric }; if at == M.metricPick { choose(0, M.autoMetric, true, now) }; if at == M.reset { rest(now) }; activate(now) }; begin = begin.addingTimeInterval(M.loop); try await wait(begin); origin = begin; restRaw = M.secondPath[1]; activate(.now) }
    } catch {} }
    private func menuX(_ kind: Int, _ geo: MetricDropdownLineScrubGeometry) -> Double { kind == 0 ? S.margin : min(S.margin + (geo.width - 40 - S.pillGap) / 2 + S.pillGap, geo.width - S.margin - S.menuW) }
    private func font(_ size: Double, _ weight: UIFont.Weight, _ tabular: Bool = false) -> UIFont { tabular ? .monospacedDigitSystemFont(ofSize: size, weight: weight) : .systemFont(ofSize: size, weight: weight) }
    private func width(_ value: String, _ size: Double, _ weight: UIFont.Weight = .regular) -> Double { (value as NSString).size(withAttributes: [.font: font(size, weight)]).width }
    private func ellipsis(_ value: String, _ size: Double, _ max: Double, _ weight: UIFont.Weight = .regular) -> String { if width(value, size, weight) <= max { return value }; var text = value; while !text.isEmpty && width(text + "…", size, weight) > max { text.removeLast() }; return text + "…" }
    private func wrapped(_ value: String, _ size: Double, _ max: Double, _ lines: Int) -> [String] { var out: [String] = [], line = ""; for word in value.split(separator: " ") { let next = line.isEmpty ? String(word) : line + " " + word; if width(next, size) > max && !line.isEmpty && out.count < lines - 1 { out.append(line); line = String(word) } else { line = next } }; if !line.isEmpty { out.append(ellipsis(line, size, max)) }; return out }
    private func text(_ c: GraphicsContext, _ value: String, _ x: Double, _ y: Double, _ size: Double, _ height: Double, _ color: Color, _ weight: UIFont.Weight = .regular, _ anchor: UnitPoint = .bottomLeading, tabular: Bool = false, tracking: Double = 0) { let f = font(size, weight, tabular), baseline = y + (height + CTFontGetAscent(f as CTFont) - CTFontGetDescent(f as CTFont)) / 2 + CTFontGetDescent(f as CTFont); c.draw(Text(value).font(Font(f)).tracking(tracking).foregroundStyle(color), at: CGPoint(x: x, y: baseline), anchor: anchor) }
    private func icon(_ c: GraphicsContext, _ name: String, _ x: Double, _ y: Double, _ size: Double, _ color: Color, angle: Double = 0) { if let image = UIImage(systemName: name, withConfiguration: UIImage.SymbolConfiguration(pointSize: size, weight: .regular)) { var resolved = c.resolve(Image(uiImage: image).renderingMode(.template)); resolved.shading = .color(color); var ctx = c; ctx.translateBy(x: x, y: y); ctx.rotate(by: .degrees(angle)); ctx.draw(resolved, at: .zero, anchor: .center) } }
    private func draw(_ c: GraphicsContext, _ geo: MetricDropdownLineScrubGeometry, _ f: MetricDropdownLineScrubFrame, _ date: Date, _ subtitleLines: [String]) {
        text(c, ellipsis(title, 32, geo.width - 96, .bold), S.margin, 16, 32, 38, S.ink, .bold, tracking: -0.6); for (i, line) in subtitleLines.enumerated() { text(c, line, S.margin, 60 + Double(i) * 18, 13, 18, S.mute) }
        c.fill(Path(ellipseIn: CGRect(x: geo.width - 60, y: 34, width: 40, height: 40)), with: .color(S.button)); icon(c, "chevron.down", geo.width - 40, 54, 20, S.ink)
        let pillW = (geo.width - 40 - S.pillGap) / 2
        for kind in 0..<2 { let x = S.margin + Double(kind) * (pillW + S.pillGap), open = menuKind == kind, color = open ? Color(hex: 0x141416) : S.ink; c.fill(capsule(CGRect(x: x, y: geo.pillsY, width: pillW, height: 40)), with: .color(S.pillColor(pills[kind].sample(date).value))); let label = kind == 0 ? metrics[mi].label : ranges[ri].label; text(c, ellipsis(label, 15, pillW - 48, .medium), x + 18, geo.pillsY + 10, 15, 20, color.opacity(still ? 1 : S.quad(date.timeIntervalSince(labelAt[kind]) / S.labelFade)), .medium); icon(c, "chevron.down", x + pillW - 22, geo.pillsY + 20, 16, color, angle: arrow[kind].sample(date).value * 180) }
        let card = CGRect(x: S.cardInset, y: geo.cardY, width: geo.cardW, height: geo.cardH), path = RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).path(in: card); c.fill(path, with: .color(S.card)); var chart = c; chart.clip(to: path); chart.translateBy(x: S.cardInset, y: geo.cardY)
        MetricDropdownLineScrubDrawChart(chart, geo.plot, S.grid, f.points, f.x, f.y, f.alpha, S.tooltipH - S.tooltipRise, jump.sample(date).value)
        func axes(_ index: Int, _ alpha: Double) { let metric = metrics[index]; for at in S.grid { text(chart, D.axisValue(metric.min + (metric.max - metric.min) * at) + " " + metric.unit, geo.cardW - 14, geo.plot.top + geo.plot.height * (1 - at) - 7, 11, 14, S.mute.opacity(alpha), .regular, .bottomTrailing, tabular: true) } }
        func dates(_ index: Int, _ alpha: Double) { let from = D.rangeStart(sessions, ranges[index]), n = sessions.count - from, middle = from + Int(floor(Double(n - 1) / 2 + 0.5)); let y = geo.cardH - 28; text(chart, D.shortDate(sessions[from]), geo.plot.left, y, 11, 14, S.mute.opacity(alpha)); text(chart, D.shortDate(sessions[middle]), geo.plot.left + geo.plot.width / 2, y, 11, 14, S.mute.opacity(alpha), .regular, .bottom); text(chart, todayLabel, geo.cardW - S.plotRight + 8, y, 11, 14, S.mute.opacity(alpha), .regular, .bottomTrailing) }
        if !still { for ghost in axisGhosts { axes(ghost.index, 1 - S.quad(date.timeIntervalSince(ghost.at) / S.labelFade)) }; for ghost in dateGhosts { dates(ghost.index, 1 - S.quad(date.timeIntervalSince(ghost.at) / S.labelFade)) } }; axes(mi, still ? 1 : S.quad(date.timeIntervalSince(axisAt) / S.labelFade)); dates(ri, still ? 1 : S.quad(date.timeIntervalSince(datesAt) / S.labelFade))
        for (i, name) in ["square.grid.2x2", "calendar", "chart.bar.fill", "bubble.left"].enumerated() { let x = geo.width * (Double(i) + 0.5) / 4, y = geo.height - S.tabBar / 2; icon(c, name, x, y, 22, i == 2 ? S.ink : S.tabIdle); if i == 3 { c.fill(Path(ellipseIn: CGRect(x: x + 7, y: geo.height - S.tabBar + 15, width: 7, height: 7)), with: .color(S.ink)) } }
        drawing.update(f.x, f.on, still, date)
        let left = min(geo.width - S.cardInset - S.tooltipW, max(S.cardInset, S.cardInset - S.tooltipW / 2 + drawing.follow.sample(date).value)), box = CGRect(x: left, y: geo.cardY - S.tooltipRise, width: S.tooltipW, height: S.tooltipH)
        var bubble = c.scaled(0.96 + 0.04 * f.alpha, about: CGPoint(x: box.midX, y: box.midY)); bubble.opacity *= f.alpha; let bubblePath = RoundedRectangle(cornerRadius: 16, style: .continuous).path(in: box); bubble.drawLayer { shadow in shadow.addFilter(.shadow(color: .black.opacity(0.45), radius: 12, x: 0, y: 8)); shadow.fill(bubblePath, with: .color(S.tooltip)) }; bubble.stroke(RoundedRectangle(cornerRadius: 16, style: .continuous).path(in: box.insetBy(dx: 0.5 / scale, dy: 0.5 / scale)), with: .color(S.edge), lineWidth: 1 / scale)
        let metric = metrics[mi], session = start + min(f.pick, count - 1), value = D.format(metric.values[session], metric.decimals), valueW = drawing.roller.draw(bubble, value, date, still, box.minX + 14, box.minY + 12)
        text(bubble, metric.unit, box.minX + 14 + valueW + 4, box.minY + 20, 14, 18, S.caption, .medium); text(bubble, ellipsis(metric.caption, 12, S.tooltipW - 28), box.minX + 14, box.minY + 44, 12, 16, S.caption); text(bubble, ellipsis(D.longDate(sessions[session]), 10, S.tooltipW - 28), box.minX + 14, box.minY + 62, 10, 14, S.mute)
        for kind in 0..<2 { let value = menus[kind].sample(date).value; if value <= 0 { continue }; let items = kind == 0 ? metrics.map(\.label) : ranges.map(\.label), current = kind == 0 ? mi : ri, x = menuX(kind, geo), y = geo.pillsY + 40 + S.menuGap - (still ? 0 : 6 * (1 - value)), h = Double(items.count) * S.menuItem + 12 + 2 / scale, rect = CGRect(x: x, y: y, width: S.menuW, height: h); var menu = c.scaled(still ? 1 : 0.94 + 0.06 * value, about: CGPoint(x: rect.midX, y: rect.minY)); menu.opacity *= value; let menuPath = RoundedRectangle(cornerRadius: 18, style: .continuous).path(in: rect); menu.drawLayer { shadow in shadow.addFilter(.shadow(color: .black.opacity(0.5), radius: 16, x: 0, y: 12)); shadow.fill(menuPath, with: .color(S.menu)) }; menu.stroke(menuPath, with: .color(S.edge), lineWidth: 1 / scale)
            for (i, label) in items.enumerated() { let row = CGRect(x: x + 6 + 1 / scale, y: y + 6 + 1 / scale + Double(i) * S.menuItem, width: S.menuW - 12 - 2 / scale, height: S.menuItem); if menuKind == kind && (hover == i || pressed == i) { menu.fill(RoundedRectangle(cornerRadius: 12).path(in: row), with: .color(S.pressed)) }; icon(menu, kind == 0 ? "chart.bar" : "calendar", row.minX + 20.5, row.midY, 17, i == current ? S.ink : S.caption); text(menu, ellipsis(label, 15, row.width - 69, .medium), row.minX + 41, row.minY + 15, 15, 20, i == current ? S.ink : S.caption, .medium); if i == current { icon(menu, "checkmark", row.maxX - 20, row.midY, 16, S.ink) }; if i < items.count - 1 { menu.fill(Path(CGRect(x: row.minX + 41, y: row.maxY - 1 / scale, width: row.width - 53, height: 1 / scale)), with: .color(S.separator)) } }
        }
    }
}
