import CoreText
import SwiftUI

private typealias M = EnergyScrubAreaCardMotion
private enum S {
    static let data = [12.6, 11.6, 7.4, 6.2, 10.8, 11.4, 10.2, 15.6, 17.2, 14.4, 14.0, 16.0, 18.0], ecoScale = 0.55, ecoFloor = 2.0
    static let days = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
    static let page = Color(hex: 0xf6f6f6), card = Color(hex: 0xfcfcfc), ink = Color(hex: 0x111111), body = Color(hex: 0x4b5563), mute = Color(hex: 0x6b7280), axis = Color(hex: 0x9ca3af), rule = Color(hex: 0xe5e7eb), orange = Color(hex: 0xee480e), orangeSoft = Color(hex: 0xfdede6), pill = Color(hex: 0xf1f3f7)
    static let side = 16.0, pad = 24.0, radius = 40.0, chartTop = 12.0, plot = 96.0, baseGap = 10.0, tickGap = 9.0, tick = 5.0, labelGap = 6.0, line = 2.5, dot = 5.0, halo = 11.0, dash = [4.0, 4.0]
    static let bubbleW = 88.0, bubbleH = 34.0, bubbleGap = 12.0, bubbleLift = 8.0, sideFlip = 0.22, toggleW = 74.0, toggleH = 40.0, knob = 32.0, knobInset = (toggleH - knob) / 2, knobTravel = toggleW - knob - knobInset * 2, row = 56.0
    static let knobDamping = 14.0, knobStiffness = 220.0, knobMass = 0.8, dotDamping = 11.0, dotStiffness = 320.0, dotMass = 4.0, springEnergy = 6e-9
    static let header = 81.0, headerGap = 24.0, dayLine = 16.0, ecoTop = 24.0, ecoHeight = 49.0, actionTop = 16.0, shadowY = 24.0, shadowBlur = 48.0
    static let base = chartTop + plot + baseGap, chartHeight = base + tickGap + tick, chartBox = chartHeight + labelGap + dayLine, chartY = pad + header + headerGap, ecoY = chartY + chartBox + ecoTop, actionY = ecoY + ecoHeight + actionTop, cardHeight = actionY + row + pad
    static func out(_ t: Double) -> Double { 1 - pow(1 - M.clamp(t), 3) }
    static func quad(_ t: Double) -> Double { let u = M.clamp(t); return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
}

private struct EnergyScrubAreaCardScalar {
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
        let u = duration == 0 ? 1 : M.clamp(t / duration), eased = curve == 1 ? M.easeInOut(u) : (curve == 2 ? S.quad(u) : S.out(u))
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
private struct EnergyScrubAreaCardHand {
    var cursor = EnergyScrubAreaCardScalar(M.dayX(0)), held = EnergyScrubAreaCardScalar(0), eco = EnergyScrubAreaCardScalar(0), knob = EnergyScrubAreaCardScalar(0), down = 0.0
    init() {}
    init(_ film: EnergyScrubAreaCardFilm) { cursor = .init(film.cursor); held = .init(film.held); eco = .init(film.eco); knob = .init(film.knob); down = film.down }
    func film(_ date: Date) -> EnergyScrubAreaCardFilm { .init(cursor: cursor.sample(date).value, held: held.sample(date).value, down: down, eco: eco.sample(date).value, knob: knob.sample(date).value) }
}
private final class EnergyScrubAreaCardDrawing {
    let headline = EnergyScrubAreaCardRollingNumber(), cost = EnergyScrubAreaCardRollingNumber(), bubble = EnergyScrubAreaCardRollingNumber()
    var side = EnergyScrubAreaCardScalar(0), lastSide: Int? = nil
}
private struct EnergyScrubAreaCardPressStyle: ButtonStyle {
    @Binding var pressed: Bool
    func makeBody(configuration: Configuration) -> some View { configuration.label.onChange(of: configuration.isPressed) { _, value in pressed = value } }
}

struct EnergyScrubAreaCard: View {
    var data = S.data
    var ecoData: [Double]? = nil
    var range = [0.0, 20.0]
    var current = 1.2, ecoCurrent = 0.7, rate = 0.225
    var days = S.days
    var title = "Energy", subtitle = "This week", mode = "Comfort", place = "Home", unit = "kW", ecoLabel = "Eco mode", ecoHint = "Maximize savings", actionLabel = "Spot savings"
    var autoPlay = false
    var onScrub: ((Double?) -> Void)? = nil
    var onEcoChange: ((Bool) -> Void)? = nil
    var onAction: (() -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var hand = EnergyScrubAreaCardHand()
    @State private var automatic = false
    @State private var origin = Date.now
    @State private var sampleAt = Date.now
    @State private var autoMoving = false
    @State private var busy = true
    @State private var busyUntil = Date.now.addingTimeInterval(EnergyScrubAreaCardRollingNumber.reveal)
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var lastDay = -1
    @State private var ecoOn = false
    @State private var pressed = false
    @State private var drawing = EnergyScrubAreaCardDrawing()
    @State private var tickHaptic = UISelectionFeedbackGenerator()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    private var eco: [Double] { ecoData?.count == data.count ? ecoData! : data.map { $0 * S.ecoScale + S.ecoFloor } }
    private func film(_ date: Date) -> EnergyScrubAreaCardFilm { automatic ? M.filmAt(max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop), still) : hand.film(date) }
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.system(size: 34, weight: .bold)).tracking(-0.4).foregroundStyle(S.ink).frame(minHeight: 41, alignment: .leading)
            Text(subtitle).font(.system(size: 15)).foregroundStyle(S.mute).frame(height: 20)
            GeometryReader { proxy in
                let width = proxy.size.width + 16
                TimelineView(.animation(paused: !(busy || autoMoving))) { timeline in
                    let date = busy || autoMoving ? timeline.date : sampleAt
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: S.radius, style: .continuous).fill(S.card).shadow(color: .black.opacity(0.08), radius: S.shadowBlur / 2, x: 0, y: S.shadowY)
                        Canvas { c, size in draw(c, size.width, date) }
                        Color.clear.frame(width: width - S.pad * 2, height: S.chartBox).contentShape(Rectangle()).offset(x: S.pad, y: S.chartY)
                            .gesture(DragGesture(minimumDistance: 0).onChanged { value in scrub(value.location.x / (width - S.pad * 2)) }.onEnded { _ in lift() })
                            .accessibilityLabel("\(place) energy use this week")
                        Button { toggle() } label: { Color.clear.frame(width: S.toggleW + 16, height: S.toggleH + 16).contentShape(Rectangle()) }
                            .buttonStyle(.plain).offset(x: width - S.pad - S.toggleW - 8, y: S.ecoY + (S.ecoHeight - S.toggleH) / 2 - 8)
                            .accessibilityLabel(ecoLabel).accessibilityValue(ecoOn ? "On" : "Off")
                        Button { takeover(); onAction?() } label: { Color.clear.frame(width: width - S.pad * 2, height: S.row).contentShape(Capsule()) }
                            .buttonStyle(EnergyScrubAreaCardPressStyle(pressed: $pressed)).offset(x: S.pad, y: S.actionY).accessibilityLabel(actionLabel)
                    }.frame(width: width, height: S.cardHeight, alignment: .topLeading)
                }.frame(height: S.cardHeight).position(x: proxy.size.width / 2, y: max(0, proxy.size.height - 48) / 2)
            }
        }.padding(.horizontal, 24).padding(.top, 12).background(S.page.ignoresSafeArea()).preferredColorScheme(.light)
            .onAppear { tickHaptic.prepare(); tapHaptic.prepare(); busy = !still; busyUntil = .now.addingTimeInterval(still ? 0 : EnergyScrubAreaCardRollingNumber.reveal) }
            .task(id: busyUntil) { do { try await wait(busyUntil); sampleAt = .now; busy = false } catch {} }
            .task(id: touches) { await autoplay() }
            .onChange(of: autoPlay) { _, _ in touches += 1 }
    }
    private func active(_ date: Date, _ duration: Double) { sampleAt = date; busy = !still || duration > 0; busyUntil = still ? date.addingTimeInterval(duration) : [date.addingTimeInterval(duration), hand.held.end, hand.knob.end, hand.eco.end.addingTimeInterval(EnergyScrubAreaCardRollingNumber.reveal), hand.cursor.end.addingTimeInterval(EnergyScrubAreaCardRollingNumber.reveal)].max()! }
    private func touch(_ date: Date) { lastTouch = date; touches += 1 }
    private func snapshot(_ date: Date) { if automatic { hand = .init(film(date)); automatic = false; autoMoving = false } }
    private func takeover() {
        let now = Date.now, wasAuto = automatic; snapshot(now)
        if wasAuto { hand.down = 0; hand.held.target(0, now, still ? 0 : M.dotOut, 2) }
        touch(now); active(now, still ? 0 : M.dotOut)
    }
    private func scrub(_ x: Double) {
        let now = Date.now, begins = automatic || hand.down == 0
        snapshot(now); hand.cursor = .init(M.clamp(x))
        if begins { hand.down = 1; if still { hand.held = .init(1) } else { hand.held.targetSpring(1, now, S.dotDamping, S.dotStiffness, S.dotMass) }; touch(now); lastDay = Int(floor(M.clamp(x) * 7)) }
        else { let day = Int(floor(M.clamp(x) * 7)); if day != lastDay { tickHaptic.selectionChanged(); tickHaptic.prepare(); lastDay = day } }
        active(now, still ? 0 : EnergyScrubAreaCardRollingNumber.reveal)
        onScrub?(M.valueAt(M.mix(data, eco, hand.eco.sample(now).value), hand.cursor.sample(now).value))
    }
    private func lift() { let now = Date.now; hand.down = 0; hand.held.target(0, now, still ? 0 : M.dotOut, 2); touch(now); active(now, still ? 0 : M.dotOut); onScrub?(nil) }
    private func toggle() {
        takeover(); let now = Date.now, next = hand.knob.sample(now).value < 0.5; ecoOn = next
        hand.eco.target(next ? 1 : 0, now, still ? 0 : M.morphFor)
        if still { hand.knob = .init(next ? 1 : 0) } else { hand.knob.targetSpring(next ? 1 : 0, now, S.knobDamping, S.knobStiffness, S.knobMass) }
        tapHaptic.impactOccurred(); tapHaptic.prepare(); active(now, still ? 0 : 1.3); onEcoChange?(next)
    }
    private func wait(_ date: Date) async throws { while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }; try Task.checkCancellation() }
    private func autoplay() async {
        guard autoPlay else { return }
        do {
            if touches > 0 {
                try await wait(lastTouch.addingTimeInterval(M.userHold)); guard hand.down == 0 else { return }
                let now = Date.now; hand.held.target(0, now, still ? 0 : M.returnFor); hand.eco.target(0, now, still ? 0 : M.returnFor); hand.knob.target(0, now, still ? 0 : M.returnFor); hand.cursor.target(M.dayX(0), now, still ? 0 : M.returnFor); ecoOn = false; active(now, still ? 0 : M.returnFor + EnergyScrubAreaCardRollingNumber.reveal)
                try await wait(lastTouch.addingTimeInterval(M.userHold + M.returnFor))
            }
            origin = .now; automatic = true; sampleAt = origin
            let events: [(Double, Bool)] = still ? [(M.pressAt, false), (M.liftAt, false), (M.ecoOnAt, false), (M.ecoOffAt, false)] : [(M.pressAt, true), (M.glideAt + M.glideFor + M.backFor + EnergyScrubAreaCardRollingNumber.reveal, false), (M.liftAt, true), (M.liftAt + M.dotOut, false), (M.ecoOnAt, true), (M.ecoOnAt + M.morphFor + EnergyScrubAreaCardRollingNumber.reveal, false), (M.ecoOffAt, true), (M.ecoOffAt + M.morphFor + EnergyScrubAreaCardRollingNumber.reveal, false)]
            var start = origin
            while true {
                for (at, moving) in events { try await wait(start.addingTimeInterval(at)); autoMoving = moving; sampleAt = .now }
                start = start.addingTimeInterval(M.loop); try await wait(start); origin = start; sampleAt = .now
            }
        } catch {}
    }
    private func text(_ c: GraphicsContext, _ value: String, _ x: Double, _ top: Double, _ size: Double, _ lineHeight: Double, _ color: Color, _ weight: UIFont.Weight = .regular, _ tracking: Double = 0) {
        let font = UIFont.systemFont(ofSize: size, weight: weight), baseline = top + (lineHeight + CTFontGetAscent(font as CTFont) - CTFontGetDescent(font as CTFont)) / 2
        c.draw(Text(value).font(Font(font)).tracking(tracking).foregroundStyle(color), at: CGPoint(x: x, y: baseline + CTFontGetDescent(font as CTFont)), anchor: .bottomLeading)
    }
    private func icon(_ c: GraphicsContext, _ name: String, _ x: Double, _ y: Double, _ size: Double, _ color: Color) {
        if let image = UIImage(systemName: name, withConfiguration: UIImage.SymbolConfiguration(pointSize: size, weight: .regular)) { var resolved = c.resolve(Image(uiImage: image).renderingMode(.template)); resolved.shading = .color(color); c.draw(resolved, at: CGPoint(x: x, y: y), anchor: .center) }
    }
    private func draw(_ c: GraphicsContext, _ width: Double, _ date: Date) {
        let f = film(date), w = width - 2 * S.pad, values = M.mix(data, eco, f.eco), frame = EnergyScrubAreaCardFrame(width: w, top: S.chartTop, bottom: S.chartTop + S.plot, min: range[0], max: range[1])
        let scrubbed = M.valueAt(values, f.cursor), headline = f.down != 0 ? scrubbed : current + (ecoCurrent - current) * f.eco, shown = M.clamp(f.held)
        c.fill(Path(ellipseIn: CGRect(x: S.pad, y: S.pad, width: 32, height: 32)), with: .color(S.orangeSoft)); icon(c, "bolt.fill", S.pad + 16, S.pad + 16, 17, S.orange)
        text(c, mode, S.pad + 42, S.pad + 5, 17, 22, S.body, .medium); text(c, place, S.pad, S.pad + 40, 34, 41, S.ink, .semibold, -0.6)
        let unitWidth = (unit as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 17)]).width
        let numberW = drawing.headline.update(value: headline, decimals: 1, wholeDigits: 2, fontSize: 34, lineHeight: 41, weight: .semibold, color: S.ink, tracking: -0.6, still: still, date: date)
        drawing.headline.draw(c, x: width - S.pad - unitWidth - 3 - numberW, y: S.pad - 6, decimals: 1, date: date); text(c, unit, width - S.pad - unitWidth, S.pad - 6 + 41 - 5 - 22, 17, 22, S.mute)
        let costW = drawing.cost.update(value: headline * rate, decimals: 2, wholeDigits: 2, fontSize: 13, lineHeight: 18, weight: .medium, color: S.body, still: still, date: date)
        let prefix = ("~$" as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 13, weight: .medium)]).width, suffix = (" / hr" as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 13, weight: .medium)]).width, pillW = 16 + prefix + suffix + costW, pillX = width - S.pad - pillW, pillY = S.pad - 6 + 41 + 2
        c.fill(capsule(CGRect(x: pillX, y: pillY, width: pillW, height: 24)), with: .color(S.pill)); text(c, "~$", pillX + 8, pillY + 3, 13, 18, S.body, .medium); drawing.cost.draw(c, x: pillX + 8 + prefix, y: pillY + 3, decimals: 2, date: date); text(c, " / hr", pillX + 8 + prefix + costW, pillY + 3, 13, 18, S.body, .medium)
        var graph = c; graph.translateBy(x: S.pad, y: S.chartY); graph.clip(to: Path(CGRect(x: 0, y: 0, width: w, height: S.chartHeight)))
        let fill = Gradient(stops: [.init(color: .rgba(240, 96, 40, 0.2), location: 0), .init(color: .rgba(240, 96, 40, 0.07), location: 0.55), .init(color: .rgba(240, 96, 40, 0), location: 1)])
        graph.fill(M.curvePath(values, frame, S.base), with: .linearGradient(fill, startPoint: CGPoint(x: 0, y: S.chartTop), endPoint: CGPoint(x: 0, y: S.base)))
        var baseline = Path(); baseline.move(to: CGPoint(x: 0, y: S.base)); baseline.addLine(to: CGPoint(x: w, y: S.base)); graph.stroke(baseline, with: .color(S.rule), lineWidth: 1)
        for (i, day) in days.enumerated() { let x = M.dayX(i) * w; var tick = Path(); tick.move(to: CGPoint(x: x, y: S.base + S.tickGap)); tick.addLine(to: CGPoint(x: x, y: S.base + S.tickGap + S.tick)); graph.stroke(tick, with: .color(S.axis), lineWidth: 1); let tw = (day as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 13, weight: .medium), .kern: 0.3]).width; text(c, day, S.pad + x - tw / 2, S.chartY + S.chartHeight + S.labelGap, 13, 16, S.mute, .medium, 0.3) }
        let x = f.cursor * w, y = M.yOf(scrubbed, frame); var cursor = Path(); cursor.move(to: CGPoint(x: x, y: S.chartTop)); cursor.addLine(to: CGPoint(x: x, y: S.base - 2)); graph.stroke(cursor, with: .color(S.axis.opacity(shown)), style: StrokeStyle(lineWidth: 1, dash: S.dash.map { CGFloat($0) }))
        graph.stroke(M.curvePath(values, frame), with: .color(S.orange), style: StrokeStyle(lineWidth: S.line, lineCap: .round, lineJoin: .round))
        for (radius, alpha) in [(S.halo, 0.16), (S.dot, 1.0)] { let r = radius * max(0, f.held); graph.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(S.orange.opacity(alpha))) }
        let side = x + S.bubbleGap + S.bubbleW > w ? 1 : 0
        if drawing.lastSide == nil { drawing.side = .init(Double(side)) } else if side != drawing.lastSide { drawing.side.target(Double(side), date, still ? 0 : S.sideFlip) }; drawing.lastSide = side
        let left = x + S.bubbleGap + (-S.bubbleW - 2 * S.bubbleGap) * drawing.side.sample(date).value
        var top = y; for i in 0...4 { top = min(top, M.yOf(M.valueAt(values, (left + S.bubbleW * Double(i) / 4) / w), frame)) }
        let box = CGRect(x: S.pad + left, y: S.chartY + top - S.bubbleLift - S.bubbleH, width: S.bubbleW, height: S.bubbleH)
        var bubble = c.scaled(0.92 + 0.08 * shown, about: CGPoint(x: box.midX, y: box.midY)); bubble.opacity *= shown
        let bubblePath = RoundedRectangle(cornerRadius: 12, style: .continuous).path(in: box); bubble.fill(bubblePath, with: .color(.white)); bubble.stroke(RoundedRectangle(cornerRadius: 11.5, style: .continuous).path(in: box.insetBy(dx: 0.5, dy: 0.5)), with: .color(S.rule), lineWidth: 1)
        let bubbleW = drawing.bubble.update(value: scrubbed, decimals: 1, wholeDigits: 2, fontSize: 17, lineHeight: 22, weight: .semibold, color: S.ink, still: still, date: date), bu = " " + unit, buW = (bu as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 15)]).width, bx = box.midX - (bubbleW + buW) / 2
        drawing.bubble.draw(bubble, x: bx, y: box.midY - 11, decimals: 1, date: date); text(bubble, bu, bx + bubbleW, box.midY - 10, 15, 20, S.mute)
        text(c, ecoLabel, S.pad, S.ecoY, 20, 25, S.ink, .semibold); text(c, ecoHint, S.pad, S.ecoY + 29, 15, 20, S.mute)
        let k = M.clamp(f.knob), track = Color.rgba(237 + (238 - 237) * k, 238 + (72 - 238) * k, 242 + (14 - 242) * k, 1), tx = width - S.pad - S.toggleW, ty = S.ecoY + (S.ecoHeight - S.toggleH) / 2
        c.fill(capsule(CGRect(x: tx, y: ty, width: S.toggleW, height: S.toggleH)), with: .color(track))
        let knobBox = CGRect(x: tx + S.knobInset + S.knobTravel * f.knob, y: ty + S.knobInset, width: S.knob, height: S.knob)
        c.drawLayer { shadow in shadow.addFilter(.shadow(color: .black.opacity(0.14), radius: 3, x: 0, y: 2)); shadow.fill(Path(ellipseIn: knobBox), with: .color(.white)) }
        icon(c, "house", knobBox.midX, knobBox.midY, 19, S.ink.opacity(1 - k)); icon(c, "house.fill", knobBox.midX, knobBox.midY, 19, S.orange.opacity(k)); icon(c, "bolt.fill", knobBox.midX, knobBox.midY + 1, 8, .rgba(17 + 238 * k, 17 + 238 * k, 17 + 238 * k, 1))
        var action = c; action.opacity *= pressed ? 0.6 : 1
        let actionBox = CGRect(x: S.pad, y: S.actionY, width: w, height: S.row); action.fill(capsule(actionBox), with: .color(.white)); action.stroke(capsule(actionBox.insetBy(dx: 0.5, dy: 0.5)), with: .color(S.rule), lineWidth: 1)
        action.stroke(Path(ellipseIn: CGRect(x: S.pad + 7.5, y: S.actionY + 8.5, width: 39, height: 39)), with: .color(S.rule), lineWidth: 1); EnergyScrubAreaCardDrawSpotIcon(action, CGPoint(x: S.pad + 16, y: S.actionY + 17)); text(action, actionLabel, S.pad + 59, S.actionY + 16.5, 18, 23, S.ink); icon(action, "chevron.right", width - S.pad - 28, S.actionY + S.row / 2, 20, S.body)
    }
}
