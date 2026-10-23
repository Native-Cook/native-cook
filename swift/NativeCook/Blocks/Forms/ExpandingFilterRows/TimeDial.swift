import SwiftUI
import UIKit

let ExpandingFilterRowsTimes = 48
let ExpandingFilterRowsTimePanel = 218.0

private enum ExpandingFilterRowsDialStyle {
    static let blue = Color(hex: 0x2f6bff), ink = Color.white, hairline = Color(hex: 0x232326)
    static let options = 58.0, chip = 28.0, dot = 9.0, clock = 118.0, clockLeft = 16.0
    static let tickFrom = 46.0, tickTo = 52.0, arc = 51.0, arcStroke = 4.0
    static let longHand = 40.0, shortHand = 24.0, line = 23.0, column = 92.0, columnShift = 48.0
    static let fadeAt = [0.0, 1, 2, 2.7], fade = [1.0, 0.72, 0.32, 0]
    static let flick = 0.14, chipDuration = 0.22, hitSlop = 6.0, tapDuration = 0.5
    static let optionsPadding = 14.0, chipGap = 8.0, chipPadding = 11.0, chipBorder = 1.0, dotGap = 7.0
    static let chipFont = 13.5, chipLine = 18.0, wheelFont = 17.0, endEntrance = 28.0
    static let tickWidth = 1.5, longWidth = 2.0, shortWidth = 3.5, pin = 3.0
    static let chipInk = Color(hex: 0xececee), chipFill = Color(hex: 0x1a1a1d), chipEdge = Color(hex: 0x29292d)
    static let majorTick = Color(hex: 0x77777d), minorTick = Color(hex: 0x434348)
    static let face = Gradient(stops: [.init(color: Color(hex: 0x242427), location: 0), .init(color: Color(hex: 0x202023), location: 0.8), .init(color: Color(hex: 0x1b1b1e), location: 1)])
    static let chipCurve = UnitCurve.bezier(startControlPoint: .zero, endControlPoint: UnitPoint(x: 0.58, y: 1))
    static func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double { min(high, max(low, value)) }
    static func rounded(_ value: Double) -> Int { let low = floor(value); return Int(value - low < 0.5 ? low : low + 1) }
    static func lineOpacity(_ away: Double) -> Double {
        let distance = abs(away)
        for index in 1..<fadeAt.count where distance <= fadeAt[index] {
            let t = (distance - fadeAt[index - 1]) / (fadeAt[index] - fadeAt[index - 1])
            return fade[index - 1] + (fade[index] - fade[index - 1]) * t
        }
        return 0
    }
}

func ExpandingFilterRowsTimeLabel(_ half: Int) -> String {
    let hour = Int(floor(Double(half) / 2))
    return "\(((hour + 11) % 12) + 1):\(half % 2 != 0 ? "30" : "00") \(hour < 12 ? "am" : "pm")"
}

struct ExpandingFilterRowsTimeDial: View {
    @ObservedObject var start: ExpandingFilterRowsWheel
    @ObservedObject var end: ExpandingFilterRowsWheel
    let range: Double
    let ranged: Bool
    let singleLabel: String
    let rangeLabel: String
    let onMode: (Bool) -> Void
    let onStart: (Int, Bool) -> Void
    let onEnd: (Int, Bool) -> Void
    let onTouch: () -> Void
    let shown: Double
    @Environment(\.displayScale) private var displayScale
    private typealias S = ExpandingFilterRowsDialStyle

    private func choose(_ next: Bool) {
        onTouch()
        if next != ranged { UISelectionFeedbackGenerator().selectionChanged(); onMode(next) }
    }
    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let bodyHeight = ExpandingFilterRowsTimePanel - S.options
            let wheelsWidth = max(0, width - S.clockLeft - S.clock)
            let there = S.clamp((shown - 0.45) / 0.55)
            ZStack(alignment: .topLeading) {
                HStack(spacing: S.chipGap) {
                    ExpandingFilterRowsTimeOption(label: singleLabel, chosen: !ranged) { choose(false) }
                    ExpandingFilterRowsTimeOption(label: rangeLabel, chosen: ranged) { choose(true) }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, S.optionsPadding).frame(width: width, height: S.options)
                .overlay(alignment: .bottom) { S.hairline.frame(height: 1 / displayScale) }
                .opacity(S.clamp((shown - 0.15) / 0.45))
                .accessibilityElement(children: .contain)

                ZStack(alignment: .topLeading) {
                    ExpandingFilterRowsClock(start: start.value, end: end.value, range: range)
                        .frame(width: S.clock, height: S.clock)
                        .offset(x: S.clockLeft, y: (bodyHeight - S.clock) / 2)
                    ExpandingFilterRowsTimeWheel(position: start, label: ranged ? "Start time" : "Time", onTouch: onTouch, onSettle: onStart)
                        .frame(width: S.column, height: bodyHeight)
                        .offset(x: S.clockLeft + S.clock + (wheelsWidth - S.column) / 2 - S.columnShift * range)
                    ExpandingFilterRowsTimeWheel(position: end, label: "End time", onTouch: onTouch, onSettle: onEnd)
                        .frame(width: S.column, height: bodyHeight)
                        .offset(x: S.clockLeft + S.clock + (wheelsWidth - S.column) / 2 + S.columnShift + (1 - range) * S.endEntrance)
                        .opacity(range).allowsHitTesting(ranged).accessibilityHidden(!ranged)
                }
                .frame(width: width, height: bodyHeight, alignment: .topLeading).scaleEffect(0.96 + 0.04 * there)
                .opacity(there).offset(y: S.options)
            }
        }.frame(height: ExpandingFilterRowsTimePanel)
    }
}

private struct ExpandingFilterRowsTimeOption: View {
    let label: String
    let chosen: Bool
    let action: () -> Void
    @State private var dot: Tween
    @ScaledMetric(relativeTo: .body) private var fontSize = ExpandingFilterRowsDialStyle.chipFont
    private typealias S = ExpandingFilterRowsDialStyle
    init(label: String, chosen: Bool, action: @escaping () -> Void) {
        self.label = label; self.chosen = chosen; self.action = action
        _dot = State(initialValue: Tween(chosen ? 1 : 0))
    }
    var body: some View {
        TimelineView(.animation) { clock in option(dot.value(at: clock.date)) }
            .fixedSize(horizontal: true, vertical: true)
            .onChange(of: chosen) { _, next in dot.retarget(next ? 1 : 0, duration: S.chipDuration, curve: S.chipCurve) }
    }
    private func option(_ fraction: Double) -> some View {
        HStack(spacing: 0) {
                RoundedRectangle(cornerRadius: S.dot / 2, style: .circular).fill(S.blue).frame(width: S.dot * fraction, height: S.dot)
                    .opacity(fraction).padding(.trailing, S.dotGap * fraction)
                Text(label).font(.system(size: fontSize, weight: .medium)).foregroundStyle(S.chipInk)
                    .lineLimit(1).frame(height: S.chipLine)
            }
            .padding(.horizontal, S.chipPadding + S.chipBorder).frame(height: S.chip)
            .background(S.chipFill, in: RoundedRectangle(cornerRadius: S.chip / 2, style: .circular))
            .overlay { RoundedRectangle(cornerRadius: S.chip / 2, style: .circular).strokeBorder(S.chipEdge, lineWidth: S.chipBorder) }
            .overlay {
                GeometryReader { geometry in
                    ExpandingFilterRowsHit(width: geometry.size.width + S.hitSlop * 2, height: S.chip + S.hitSlop * 2, label: label, selected: chosen, activate: action)
                        .offset(x: -S.hitSlop, y: -S.hitSlop)
                }
            }
            .accessibilityElement(children: .ignore).accessibilityLabel(label)
            .accessibilityAddTraits(.isButton)
            .accessibilityAddTraits(chosen ? .isSelected : [])
            .accessibilityAction { action() }
    }
}

private struct ExpandingFilterRowsTimeWheel: View {
    @ObservedObject var position: ExpandingFilterRowsWheel
    let label: String
    let onTouch: () -> Void
    let onSettle: (Int, Bool) -> Void
    @State private var grabbed = 0.0
    @State private var previous: Int?
    @ScaledMetric(relativeTo: .body) private var fontSize = ExpandingFilterRowsDialStyle.wheelFont
    private typealias S = ExpandingFilterRowsDialStyle
    private func step(_ by: Int) {
        onTouch(); position.byHand = true
        position.spring(Double(min(ExpandingFilterRowsTimes - 1, max(0, S.rounded(position.value) + by))))
    }
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                ForEach(0..<ExpandingFilterRowsTimes, id: \.self) { index in
                    let away = Double(index) - position.value
                    Text(ExpandingFilterRowsTimeLabel(index)).font(.system(size: fontSize, weight: .semibold)).monospacedDigit()
                        .foregroundStyle(S.ink).lineLimit(1).frame(width: S.column, height: S.line)
                        .opacity(S.lineOpacity(away)).position(x: S.column / 2, y: geometry.size.height / 2 + away * S.line)
                }
                ExpandingFilterRowsPanSurface(begin: {
                    position.cancel(); grabbed = position.value; position.byHand = true; onTouch()
                }, update: { translation in
                    let to = grabbed - translation / S.line
                    let over = to < 0 ? to : to > Double(ExpandingFilterRowsTimes - 1) ? to - Double(ExpandingFilterRowsTimes - 1) : 0
                    position.set(to - over + over / (1 + abs(over)) / 2)
                }, finalize: { velocity in
                    let carried = position.value - velocity * S.flick / S.line
                    position.spring(Double(min(ExpandingFilterRowsTimes - 1, max(0, S.rounded(carried)))))
                    onTouch()
                })
            }
        }
        .clipped()
        .accessibilityElement(children: .ignore).accessibilityLabel(label)
        .accessibilityValue(ExpandingFilterRowsTimeLabel(min(ExpandingFilterRowsTimes - 1, max(0, S.rounded(position.value)))))
        .accessibilityAdjustableAction { direction in if direction == .increment { step(1) } else if direction == .decrement { step(-1) } }
        .onReceive(position.$value) { value in
            let next = min(ExpandingFilterRowsTimes - 1, max(0, S.rounded(value)))
            if let previous, previous != next { onSettle(next, position.byHand) }
            previous = next
        }
    }
}

private struct ExpandingFilterRowsClock: View {
    let start: Double
    let end: Double
    let range: Double
    private typealias S = ExpandingFilterRowsDialStyle
    var body: some View {
        Canvas { context, _ in
            let center = CGPoint(x: S.clock / 2, y: S.clock / 2)
            context.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: S.clock, height: S.clock)), with: .radialGradient(S.face, center: center, startRadius: 0, endRadius: S.clock / 2))
            for hour in 0..<12 {
                let angle = Double(hour) * .pi / 6
                var tick = Path()
                tick.move(to: CGPoint(x: center.x + sin(angle) * S.tickFrom, y: center.y - cos(angle) * S.tickFrom))
                tick.addLine(to: CGPoint(x: center.x + sin(angle) * S.tickTo, y: center.y - cos(angle) * S.tickTo))
                context.stroke(tick, with: .color(hour % 3 == 0 ? S.majorTick : S.minorTick), style: StrokeStyle(lineWidth: S.tickWidth, lineCap: .round))
            }
            let from = start * .pi / 12, sweep = S.clamp((end - start) * 15, 0.1, 359.9) * .pi / 180
            var arc = Path()
            arc.addArc(center: center, radius: S.arc, startAngle: .radians(from - .pi / 2), endAngle: .radians(from + sweep - .pi / 2), clockwise: false)
            var rim = context; rim.opacity = range
            rim.stroke(arc, with: .color(S.blue), style: StrokeStyle(lineWidth: S.arcStroke, lineCap: .round))
            let minutes = (start * 180).truncatingRemainder(dividingBy: 360) + 360
            let hours = (end * 15).truncatingRemainder(dividingBy: 360) + 360
            let way = (hours - minutes + 540).truncatingRemainder(dividingBy: 360) - 180
            for (angle, length, width) in [((minutes + way * range) * .pi / 180, S.longHand, S.longWidth), (from, S.shortHand, S.shortWidth)] {
                var hand = Path(); hand.move(to: center)
                hand.addLine(to: CGPoint(x: center.x + sin(angle) * length, y: center.y - cos(angle) * length))
                context.stroke(hand, with: .color(S.ink), style: StrokeStyle(lineWidth: width, lineCap: .round))
            }
            context.fill(Path(ellipseIn: CGRect(x: center.x - S.pin, y: center.y - S.pin, width: S.pin * 2, height: S.pin * 2)), with: .color(S.ink))
        }.accessibilityHidden(true)
    }
}

/// UIKit preserves the source's default iOS pan origin and finalizes failed pans too.
struct ExpandingFilterRowsPanSurface: UIViewRepresentable {
    let begin: () -> Void
    let update: (Double) -> Void
    let finalize: (Double) -> Void
    var tap: ((CGPoint) -> Void)? = nil
    func makeUIView(context: Context) -> ExpandingFilterRowsPanView { ExpandingFilterRowsPanView() }
    func updateUIView(_ view: ExpandingFilterRowsPanView, context: Context) {
        view.pan.begin = begin; view.pan.update = update; view.pan.finalize = finalize; view.onTap = tap
    }
}

final class ExpandingFilterRowsPanView: UIView {
    let pan = ExpandingFilterRowsPanRecognizer()
    private let tap = ExpandingFilterRowsTapRecognizer()
    var onTap: ((CGPoint) -> Void)? { didSet { tap.isEnabled = onTap != nil } }
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear; isAccessibilityElement = false; isMultipleTouchEnabled = true
        addGestureRecognizer(pan); addGestureRecognizer(tap)
        tap.require(toFail: pan); tap.addTarget(self, action: #selector(tapped))
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    @objc private func tapped() { if tap.state == .ended { onTap?(tap.point) } }
}

final class ExpandingFilterRowsPanRecognizer: UIPanGestureRecognizer {
    var begin: () -> Void = {}
    var update: (Double) -> Void = { _ in }
    var finalize: (Double) -> Void = { _ in }
    private var started = false
    private var finished = false
    init() { super.init(target: nil, action: nil); cancelsTouchesInView = false }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        if !started { started = true; finished = false; begin() }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        if state == .began || state == .changed { update(translation(in: view?.window).y) }
    }
    private func finish(_ velocity: Double) {
        if started && !finished { finished = true; finalize(velocity) }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        let velocity = velocity(in: view?.window).y
        super.touchesEnded(touches, with: event)
        if numberOfTouches == 0 { finish(velocity) }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        let velocity = velocity(in: view?.window).y
        super.touchesCancelled(touches, with: event); finish(velocity)
    }
    override func reset() {
        finish(velocity(in: view?.window).y)
        super.reset(); started = false; finished = false
    }
}

private final class ExpandingFilterRowsTapRecognizer: UIGestureRecognizer {
    var point = CGPoint.zero
    private var timeout: Timer?
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        timeout?.invalidate()
        timeout = Timer.scheduledTimer(withTimeInterval: ExpandingFilterRowsDialStyle.tapDuration, repeats: false) { [weak self] _ in self?.state = .failed }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        if let view, !view.bounds.contains(location(in: view)) { state = .failed }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        point = location(in: view)
        timeout?.invalidate()
        state = view?.bounds.contains(point) == true ? .ended : .failed
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event); state = .cancelled
    }
    override func reset() { timeout?.invalidate(); timeout = nil; super.reset() }
}

