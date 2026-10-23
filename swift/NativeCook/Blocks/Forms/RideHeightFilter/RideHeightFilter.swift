import SwiftUI

struct RideHeightFilterClearance: Equatable {
    var label: String
    var heading: String
    var text: String
    var examples: String
    /// How high the car rides: 0 the lowest, 1 the highest.
    var height: Double
    var count: Int
    static let samples = [
        Self(label: "Sport", heading: "Up to 12 cm", text: "Made for race tracks and perfect roads. Speed bumps have to be taken very, very carefully.", examples: "coupés, roadsters, hot hatches", height: 0, count: 128),
        Self(label: "Low", heading: "From 12 to 15 cm", text: "These cars are built for good roads. Be a little careful with kerbs when you park.", examples: "saloons, estates, city hatchbacks", height: 0.28, count: 342),
        Self(label: "Medium", heading: "From 15 to 20 cm", text: "Kerbs stop being a worry, and a track through the woods is fine. Real off-road is still out of reach.", examples: "crossovers, compact SUVs", height: 0.55, count: 517),
        Self(label: "High", heading: "From 20 to 24 cm", text: "Proper off-roaders start here, next to quite ordinary city crossovers that only look the part.", examples: "large SUVs, pickups", height: 0.78, count: 266),
        Self(label: "Off-road", heading: "More than 24 cm", text: "Aerodynamics suffer, but it will go almost anywhere. This is the clearance of big 4x4s.", examples: "body-on-frame 4x4s, lifted pickups", height: 1, count: 94),
    ]
}

private enum RideHeightFilterPalette {
    static let backdrop = Color(hex: 0x101012)
    static let behind = Color(hex: 0x3a3a3e)
    static let sheet = Color.white
    static let ink = Color(hex: 0x141414)
    static let text = Color(hex: 0x222222)
    static let mute = Color(hex: 0x8a8a8f)
    static let grabber = Color(hex: 0xd6d6d9)
    static let chip = 239.0
    static let chipPressed = 220.0
    static let chipOn = 20.0
    static let link = Color(hex: 0x2ba6c9)
}

private enum RideHeightFilterMetrics {
    static let sheetTop = 14.0
    static let sheetRadius = 26.0
    static let margin = 16.0
    static let header = 52.0
    static let chipHeight = 46.0
    static let chipRadius = 13.0
    static let chipGap = 8.0
    static let apply = 56.0
    static let lift = 18.0
    static let pitch = 0.0045
    static let titleSize = 17.0
    static let titleHeight = 22.0
    static let resetSize = 16.0
    static let resetHeight = 21.0
    static let chipSize = 16.0
    static let chipTextHeight = 21.0
    static let headingSize = 19.0
    static let headingHeight = 24.0
    static let textSize = 16.0
    static let textHeight = 22.0
    static let textLeading = textHeight - UIFont.systemFont(ofSize: textSize).lineHeight
    static let examplesSize = 14.0
    static let examplesHeight = 19.0
    static let copyHeight = 128.0
    static let applySize = 17.0
    static let applyTextHeight = 22.0
}

private enum RideHeightFilterMotion {
    static let suspension = (mass: 1.0, stiffness: 170.0, damping: 13.0)
    static let lead = (mass: 1.0, stiffness: 330.0, damping: 24.0)
    static let chipFade = 0.3
    static let textFade = 0.16
    static let chipPress = 0.12
    static let applyPress = 0.11
    static let loop = 8.6
    static let firstAt = 0.8
    static let step = 1.4
    static let pressFor = 0.14
    static let tour = [4, 1, 2, 3, 0]
    static let userHold = 3.0
    static let returnFor = 0.9
    static let frameLimit = 0.064
    static let energyThreshold = 6e-9
    static let cssEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.25, y: 0.1), endControlPoint: UnitPoint(x: 0.25, y: 1))
    static func quad(_ t: Double) -> Double {
        let u = min(1, max(0, t))
        return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
    }
}

/// Two physical springs are sampled on one frame clock, preserving their gap for the body's lean.
private struct RideHeightFilterSpring {
    private(set) var value: Double
    private var target: Double
    private var velocity = 0.0
    private var energy = 0.0
    private var last = Date.distantPast
    private var active = false
    private let mass: Double
    private let stiffness: Double
    private let damping: Double
    init(_ value: Double, constants: (mass: Double, stiffness: Double, damping: Double)) {
        self.value = value; target = value
        mass = constants.mass; stiffness = constants.stiffness; damping = constants.damping
    }
    mutating func retarget(_ next: Double, at date: Date = .now, still: Bool) {
        if still { value = next; target = next; velocity = 0; active = false; return }
        guard target != next else { return }
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next
        energy = 0.5 * stiffness * pow(value - target, 2)
        if !active { last = date }
        active = true
    }
    mutating func advance(at date: Date) {
        guard active else { return }
        let dt = min(RideHeightFilterMotion.frameLimit, max(0, date.timeIntervalSince(last)))
        last = date
        let omega = sqrt(stiffness / mass); let decay = damping / (2 * mass)
        let x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay)
            let b = (velocity + decay * x) / damped
            let wave = x * cos(damped * dt) + b * sin(damped * dt)
            let attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt)))
            value = target + attenuation * wave
        } else {
            let b = velocity + omega * x; let attenuation = exp(-omega * dt)
            value = target + attenuation * (x + b * dt)
            velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * stiffness * pow(value - target, 2) + 0.5 * mass * velocity * velocity
        if energy == 0 || current / energy <= RideHeightFilterMotion.energyThreshold {
            value = target; velocity = 0; active = false
        }
    }
}

/// A clearance filter whose car body rides up and down while the wheel remains on the ground.
struct RideHeightFilter: View {
    var options: [RideHeightFilterClearance]
    var selected: Int
    var perRow: Int
    var autoPlay: Bool
    var title: String
    var resetLabel: String
    var examplesLabel: String
    var applyLabel: (Int) -> String
    var linkColor: Color
    var onChange: ((Int, RideHeightFilterClearance) -> Void)?
    var onReset: (() -> Void)?
    var onApply: ((Int, RideHeightFilterClearance) -> Void)?
    var onClose: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var chosen: Int
    @State private var autoPressed: Int? = nil
    @State private var touches = 0
    @State private var lift: RideHeightFilterSpring
    @State private var lead: RideHeightFilterSpring
    @State private var entering: Date? = nil
    @State private var departing: [Copy] = []
    @State private var sequence = 0
    @State private var selectionHaptic = UISelectionFeedbackGenerator()
    private typealias S = RideHeightFilterMetrics
    private typealias M = RideHeightFilterMotion
    private typealias P = RideHeightFilterPalette

    init(
        options: [RideHeightFilterClearance] = RideHeightFilterClearance.samples,
        selected: Int = 0, perRow: Int = 3, autoPlay: Bool = false,
        title: String = "Ground clearance", resetLabel: String = "Reset", examplesLabel: String = "Typical cars",
        applyLabel: @escaping (Int) -> String = { "Show \($0) cars" },
        linkColor: Color = RideHeightFilterPalette.link,
        onChange: ((Int, RideHeightFilterClearance) -> Void)? = nil,
        onReset: (() -> Void)? = nil, onApply: ((Int, RideHeightFilterClearance) -> Void)? = nil,
        onClose: (() -> Void)? = nil
    ) {
        self.options = options; self.selected = selected; self.perRow = perRow; self.autoPlay = autoPlay
        self.title = title; self.resetLabel = resetLabel; self.examplesLabel = examplesLabel
        self.applyLabel = applyLabel; self.linkColor = linkColor
        self.onChange = onChange; self.onReset = onReset; self.onApply = onApply; self.onClose = onClose
        _chosen = State(initialValue: selected)
        let target = options[selected].height * RideHeightFilterMetrics.lift
        _lift = State(initialValue: RideHeightFilterSpring(target, constants: RideHeightFilterMotion.suspension))
        _lead = State(initialValue: RideHeightFilterSpring(target, constants: RideHeightFilterMotion.lead))
    }
    private var option: RideHeightFilterClearance { options[chosen] }
    private var copyKey: CopyKey {
        CopyKey(chosen: chosen, option: option, examples: examplesLabel, apply: applyLabel(option.count))
    }
    private var rows: [[Int]] {
        let size = max(1, perRow)
        return stride(from: 0, to: options.count, by: size).map { Array($0..<min($0 + size, options.count)) }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let top = insets.top + S.sheetTop
            TimelineView(.animation) { timeline in
                ZStack(alignment: .topLeading) {
                    P.backdrop
                    UnevenRoundedRectangle(topLeadingRadius: 18, topTrailingRadius: 18, style: .continuous)
                        .fill(P.behind).frame(width: width - S.margin * 2, height: 40)
                        .offset(x: S.margin, y: top - 10)
                    sheet(width: width, bottom: max(insets.bottom, 16) + 8, date: timeline.date)
                        .frame(width: width, height: height - top, alignment: .top)
                        .background(P.sheet)
                        .clipShape(UnevenRoundedRectangle(topLeadingRadius: S.sheetRadius, topTrailingRadius: S.sheetRadius, style: .continuous))
                        .offset(y: top)
                }
                .frame(width: width, height: height)
                .offset(x: -insets.leading, y: -insets.top)
                .onChange(of: timeline.date) { _, date in
                    lift.advance(at: date); lead.advance(at: date)
                    departing.removeAll { date.timeIntervalSince($0.start) >= M.textFade }
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            selectionHaptic.prepare()
            entering = reduceMotion ? nil : .now
        }
        .onChange(of: copyKey) { old, next in
            guard old.chosen != next.chosen else { return }
            if reduceMotion { entering = nil; departing = [] }
            else {
                let now = Date.now
                departing.append(Copy(id: sequence, option: old.option, examples: old.examples, apply: old.apply, start: now))
                sequence += 1; entering = now
            }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches, options: options, selected: selected)) { await loop() }
    }
    private func sheet(width: Double, bottom: Double, date: Date) -> some View {
        let opacity = entering.map { M.quad(date.timeIntervalSince($0) / M.textFade) } ?? 1
        return VStack(spacing: 0) {
            Capsule().fill(P.grabber).frame(width: 40, height: 5).padding(.top, 8)
            ZStack {
                Text(title).font(.system(size: S.titleSize, weight: .bold)).foregroundStyle(P.ink).lineLimit(1).frame(height: S.titleHeight)
                Button { touched(); onClose?() } label: {
                    Image(systemName: "xmark").font(.system(size: 28)).foregroundStyle(P.ink)
                        .frame(width: 28, height: S.header).contentShape(Rectangle().inset(by: -8))
                }
                .buttonStyle(DimStyle()).accessibilityLabel("Close")
                .frame(maxWidth: .infinity, alignment: .leading).padding(.leading, S.margin - 4)
                Button {
                    touched(); selectionHaptic.selectionChanged(); choose(selected, notify: true); onReset?()
                } label: {
                    Text(resetLabel).font(.system(size: S.resetSize)).foregroundStyle(linkColor).lineLimit(1)
                        .frame(height: S.resetHeight).frame(height: S.header).contentShape(Rectangle().inset(by: -8))
                }
                .buttonStyle(DimStyle()).frame(maxWidth: .infinity, alignment: .trailing).padding(.trailing, S.margin)
            }.frame(height: S.header)
            ZStack {
                RideHeightFilterCarPicture(width: width, lift: lift.value, pitch: (lead.value - lift.value) * S.pitch, maxLift: S.lift)
            }
            .frame(width: width, height: RideHeightFilterCarMetrics.boxHeight / RideHeightFilterCarMetrics.boxWidth * width)
            .accessibilityElement(children: .ignore).accessibilityAddTraits(.isImage)
            .accessibilityLabel("A car with " + option.label + " clearance beside a kerb")
            .padding(.top, 18)
            VStack(alignment: .leading, spacing: S.chipGap) {
                ForEach(rows, id: \.first) { row in
                    HStack(spacing: S.chipGap) {
                        ForEach(row, id: \.self) { index in
                            RideHeightFilterChip(label: options[index].label, on: chosen == index, held: autoPressed == index, date: date) {
                                touched(); selectionHaptic.selectionChanged(); choose(index, notify: true)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, S.margin).padding(.top, 26)
            ZStack(alignment: .topLeading) {
                ForEach(departing) { old in
                    copy(old.option, examples: old.examples).opacity(1 - M.quad(date.timeIntervalSince(old.start) / M.textFade))
                }
                copy(option, examples: examplesLabel).opacity(opacity)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading).frame(height: S.copyHeight, alignment: .topLeading)
            .padding(.horizontal, S.margin).padding(.top, 30)
            Spacer(minLength: 0)
            Button {
                touched(); selectionHaptic.selectionChanged(); onApply?(chosen, option)
            } label: {
                ZStack {
                    ForEach(departing) { old in
                        applyText(old.apply).opacity(1 - M.quad(date.timeIntervalSince(old.start) / M.textFade))
                    }
                    applyText(applyLabel(option.count)).opacity(opacity)
                }
                .frame(maxWidth: .infinity).frame(height: S.apply)
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(P.ink))
            }
            .buttonStyle(ApplyStyle()).padding(.horizontal, S.margin)
        }
        .padding(.bottom, bottom)
    }
    private func copy(_ option: RideHeightFilterClearance, examples: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(option.heading).font(.system(size: S.headingSize, weight: .bold)).foregroundStyle(P.ink).lineLimit(1).frame(height: S.headingHeight)
            Text(option.text).font(.system(size: S.textSize)).foregroundStyle(P.text).lineSpacing(S.textLeading)
                .padding(.vertical, S.textLeading / 2).lineLimit(3).fixedSize(horizontal: false, vertical: true).padding(.top, 4)
            Text(examples + ": " + option.examples).font(.system(size: S.examplesSize)).foregroundStyle(P.mute).lineLimit(1)
                .frame(height: S.examplesHeight).padding(.top, 10)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
    private func applyText(_ text: String) -> some View {
        Text(text).font(.system(size: S.applySize, weight: .semibold)).foregroundStyle(.white).lineLimit(1).frame(height: S.applyTextHeight)
    }
    private func choose(_ index: Int, notify: Bool) {
        chosen = index
        let target = options[index].height * S.lift
        lift.retarget(target, still: reduceMotion); lead.retarget(target, still: reduceMotion)
        if notify { onChange?(index, options[index]) }
    }
    private func touched() { autoPressed = nil; touches += 1 }
    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            choose(selected, notify: false)
            try? await Task.sleep(for: .seconds(M.returnFor))
            guard !Task.isCancelled else { return }
        }
        let clock = ContinuousClock(); var origin = clock.now
        while !Task.isCancelled {
            for (order, stop) in M.tour.enumerated() {
                let at = M.firstAt + Double(order) * M.step
                let index = stop % options.count
                try? await clock.sleep(until: origin + .seconds(at - M.pressFor))
                guard !Task.isCancelled else { return }
                autoPressed = index
                try? await clock.sleep(until: origin + .seconds(at))
                guard !Task.isCancelled else { return }
                autoPressed = nil; choose(index, notify: false)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }
    private struct Copy: Identifiable {
        let id: Int; let option: RideHeightFilterClearance; let examples: String; let apply: String; let start: Date
    }
    private struct CopyKey: Equatable {
        let chosen: Int; let option: RideHeightFilterClearance; let examples: String; let apply: String
    }
    private struct LoopKey: Equatable { let on: Bool; let touches: Int; let options: [RideHeightFilterClearance]; let selected: Int }
    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View { configuration.label.opacity(configuration.isPressed ? 0.4 : 1) }
    }
    private struct ApplyStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.75 : 1)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.applyPress), value: configuration.isPressed)
        }
    }
}

private struct RideHeightFilterChip: View {
    let label: String
    let on: Bool
    let held: Bool
    let date: Date
    let action: () -> Void
    @State private var pressed = false
    @State private var tone: Tween
    @State private var ink: Tween
    @State private var scale: Tween
    private typealias P = RideHeightFilterPalette
    private typealias M = RideHeightFilterMotion
    private typealias S = RideHeightFilterMetrics
    init(label: String, on: Bool, held: Bool, date: Date, action: @escaping () -> Void) {
        self.label = label; self.on = on; self.held = held; self.date = date; self.action = action
        _tone = State(initialValue: Tween(on ? P.chipOn : held ? P.chipPressed : P.chip))
        _ink = State(initialValue: Tween(on ? 255 : 34)); _scale = State(initialValue: Tween(held ? 0.96 : 1))
    }
    private var targetTone: Double { on ? P.chipOn : pressed || held ? P.chipPressed : P.chip }
    var body: some View {
        let rgb = ink.value(at: date); let fill = tone.value(at: date)
        Button(action: action) {
            ZStack {
                Text(label).font(.system(size: S.chipSize)).lineLimit(1).frame(height: S.chipTextHeight)
                    .foregroundStyle(Color.rgba(rgb, rgb, rgb, 1)).padding(.horizontal, 22).frame(height: S.chipHeight)
                    .background(RoundedRectangle(cornerRadius: S.chipRadius, style: .continuous).fill(Color.rgba(fill, fill, fill, 1)))
                    .scaleEffect(scale.value(at: date))
            }
            .frame(height: S.chipHeight).contentShape(Rectangle())
        }
        .buttonStyle(PressStyle { pressed = $0 })
        .accessibilityAddTraits(on ? .isSelected : [])
        .onChange(of: targetTone) { _, target in tone.retarget(target, duration: M.chipFade, curve: M.cssEase) }
        .onChange(of: on) { _, next in ink.retarget(next ? 255 : 34, duration: M.chipFade, curve: M.cssEase) }
        .onChange(of: pressed || held) { _, next in scale.retarget(next ? 0.96 : 1, duration: M.chipPress, curve: M.cssEase) }
    }
    private struct PressStyle: ButtonStyle {
        let pressed: (Bool) -> Void
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.onChange(of: configuration.isPressed) { _, next in pressed(next) }
        }
    }
}

#Preview { RideHeightFilter(autoPlay: true) }
