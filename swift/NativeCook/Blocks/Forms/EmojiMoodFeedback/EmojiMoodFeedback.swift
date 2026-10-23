import SwiftUI

struct EmojiMoodFeedbackMood: Equatable {
    var label: String
    var word: String
    var background: Color
    var ink: Color
    var shade: Color
    var button: Color
    static let samples = [
        Self(label: "Bad", word: "BAD", background: Color(hex: 0xf67a66), ink: Color(hex: 0x6b0b0d), shade: Color(hex: 0xe65f4e), button: Color(hex: 0xb22423)),
        Self(label: "Not bad", word: "NOT BAD", background: Color(hex: 0xf7bc42), ink: Color(hex: 0x4a2605), shade: Color(hex: 0xe4a62c), button: Color(hex: 0x8a5e0f)),
        Self(label: "Good", word: "GOOD", background: Color(hex: 0xb8f25a), ink: Color(hex: 0x17390a), shade: Color(hex: 0xa1db43), button: Color(hex: 0x3d6a1c)),
    ]
}

private enum EmojiMoodFeedbackMetrics {
    static let eyeWidth = [62.0, 106.0, 136.0]
    static let eyeHeight = [62.0, 34.0, 136.0]
    static let eyeX = [58.0, 66.0, 74.0]
    static let eyeY = [-30.0, -46.0, -14.0]
    static let mouthWidth = [66.0, 62.0, 62.0]
    static let mouthBow = [-20.0, -16.0, 22.0]
    static let mouthY = [12.0, 18.0, 68.0]
    static let mouthStroke = 12.0
    static let browTilt = 24.0
    static let mouthTilt = 16.0
    static let eyeMin = 12.0
    static let face = 260.0
    static let side = 24.0
    static let header = 56.0
    static let icon = 44.0
    static let word = 60.0
    static let wordRow = 76.0
    static let trackInset = 48.0
    static let track = 4.0
    static let stop = 10.0
    static let thumb = 44.0
    static let thumbMouth = 16.0
    static let thumbBow = [-4.5, -3.0, 5.0]
    static let bar = 56.0
    static let submit = 128.0
    static let note = 148.0
    static let noteGap = 16.0
    static let noteSubmit = 44.0
    static let questionSize = 22.0
    static let questionHeight = 28.0
    static let questionLeading = questionHeight - UIFont.systemFont(ofSize: questionSize, weight: .medium).lineHeight
    static let labelSize = 14.0
    static let labelHeight = 20.0
    static let noteLabelSize = 15.0
    static let noteLabelHeight = 20.0
    static let submitSize = 15.0
    static let submitHeight = 20.0
    static let noteInputSize = 16.0
    static let noteInputHeight = 22.0
    static let noteMaxLength = 280
    static let thanksTitleSize = 34.0
    static let thanksTitleHeight = 40.0
    static let thanksTextSize = 16.0
    static let thanksTextHeight = 22.0
    static let thanksTextLeading = thanksTextHeight - UIFont.systemFont(ofSize: thanksTextSize, weight: .medium).lineHeight
    static let formHeight = wordRow + 40 + thumb + 8 + labelHeight + 64 + bar
}

private enum EmojiMoodFeedbackMotion {
    static let shapeSpring = (mass: 1.0, stiffness: 190.0, damping: 14.0)
    static let snapSpring = (mass: 1.0, stiffness: 260.0, damping: 20.0)
    static let hopSpring = (mass: 1.0, stiffness: 240.0, damping: 9.0)
    static let shapeStill = 0.22
    static let tone = 0.4
    static let word = 0.48
    static let heldScale = 1.12
    static let heldIn = 0.12
    static let heldOut = 0.18
    static let callOver = 0.14
    static let note = 0.32
    static let thanks = 0.38
    static let hop = 28.0
    static let hopDelay = 0.12
    static let hopOut = 0.17
    static let loop = 8.0
    static let drags: [(at: Double, to: Double, lasts: Double)] = [(0.8, 1, 0.6), (2.7, 0, 0.6), (4.7, 2, 1.1)]
    static let userHold = 3.0
    static let reset = 0.5
    static let resumeGap = 0.3
    static let busyPoll = 0.1
    static let keyboard = 0.22
    static let frameLimit = 0.064
    static let energyThreshold = 6e-9
    static let gamma = 2.2
    static let wordCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.3, y: 0), endControlPoint: UnitPoint(x: 0.15, y: 1))
    static func clamp(_ value: Double, from: Double = 0, to: Double = 1) -> Double { min(to, max(from, value)) }
    static func interpolate(_ value: Double, table: [Double]) -> Double {
        let first = value <= 1 ? 0 : 1
        return table[first] + (table[first + 1] - table[first]) * (value - Double(first))
    }
    static func bump(_ value: Double) -> Double { pow(sin(.pi * clamp(value)), 2) }
    static let trimCharacters: CharacterSet = {
        var set = CharacterSet(charactersIn: "\u{0009}\u{000A}\u{000B}\u{000C}\u{000D}\u{0020}\u{00A0}\u{1680}\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}\u{FEFF}")
        set.insert(charactersIn: "\u{2000}"..."\u{200A}")
        return set
    }()
}

private enum EmojiMoodFeedbackCurve {
    case quad, outQuad, outCubic, inOutCubic, word
    func value(_ t: Double) -> Double {
        let u = EmojiMoodFeedbackMotion.clamp(t)
        switch self {
        case .quad: return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
        case .outQuad: return 1 - pow(1 - u, 2)
        case .outCubic: return 1 - pow(1 - u, 3)
        case .inOutCubic: return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
        case .word: return EmojiMoodFeedbackMotion.wordCurve.value(at: u)
        }
    }
}

/// Springs and timed carries remain independent of the loop's future interaction timers.
private struct EmojiMoodFeedbackScalar {
    private(set) var value: Double
    private var target: Double
    private var from: Double
    private var velocity = 0.0
    private var energy = 0.0
    private var last = Date.distantPast
    private var start = Date.distantPast
    private var duration = 0.0
    private var curve = EmojiMoodFeedbackCurve.quad
    private var constants: (mass: Double, stiffness: Double, damping: Double)? = nil
    private var active = false
    init(_ value: Double) { self.value = value; target = value; from = value }
    var timingFinished: Bool { !active && constants == nil }
    mutating func jump(_ next: Double) { value = next; target = next; velocity = 0; constants = nil; active = false }
    mutating func time(to next: Double, duration: Double, curve: EmojiMoodFeedbackCurve = .quad, at date: Date = .now) {
        advance(at: date)
        if value == next { jump(next); return }
        let continuingTiming = active && constants == nil && target == next
        if !continuingTiming { from = value; start = date }
        target = next; self.duration = duration; self.curve = curve
        constants = nil; velocity = 0; active = true
    }
    mutating func spring(to next: Double, constants: (mass: Double, stiffness: Double, damping: Double), at date: Date = .now) {
        if self.constants != nil, target == next { return }
        let continuingSpring = self.constants != nil && active
        advance(at: date)
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; self.constants = constants
        energy = 0.5 * constants.stiffness * pow(value - target, 2)
        if !continuingSpring { last = date }
        active = true
    }
    mutating func advance(at date: Date) {
        guard active else { return }
        guard let constants else {
            let t = EmojiMoodFeedbackMotion.clamp(date.timeIntervalSince(start) / duration)
            value = from + (target - from) * curve.value(t)
            if t == 1 { active = false }
            return
        }
        let dt = min(EmojiMoodFeedbackMotion.frameLimit, max(0, date.timeIntervalSince(last)))
        last = date
        let omega = sqrt(constants.stiffness / constants.mass); let decay = constants.damping / (2 * constants.mass)
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
        let current = 0.5 * constants.stiffness * pow(value - target, 2) + 0.5 * constants.mass * velocity * velocity
        if energy == 0 || current / energy <= EmojiMoodFeedbackMotion.energyThreshold {
            value = target; velocity = 0; active = false
        }
    }
}

private enum EmojiMoodFeedbackDrawing {
    private typealias S = EmojiMoodFeedbackMetrics
    private typealias M = EmojiMoodFeedbackMotion
    static func mouth(x: Double, y: Double, width: Double, bow: Double) -> Path {
        let half = width / 2; let endY = y - bow / 2; let handle = endY + bow / 0.75
        var path = Path(); path.move(to: CGPoint(x: x - half, y: endY))
        path.addCurve(to: CGPoint(x: x + half, y: endY), control1: CGPoint(x: x - half, y: handle), control2: CGPoint(x: x + half, y: handle))
        return path
    }
    static func stroke(_ path: Path, color: Color, width: Double, in context: GraphicsContext) {
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
    static func face(_ shape: Double, width: Double, ink: Color, still: Bool, in context: GraphicsContext) {
        let eyeW = max(S.eyeMin, M.interpolate(shape, table: S.eyeWidth))
        let eyeH = max(S.eyeMin, M.interpolate(shape, table: S.eyeHeight))
        let eyeY = S.face / 2 + M.interpolate(shape, table: S.eyeY)
        let eyeX = M.interpolate(shape, table: S.eyeX)
        let brow = still ? 0 : M.bump(shape) * S.browTilt * .pi / 180
        for direction in [-1.0, 1.0] {
            let x = width / 2 + direction * eyeX
            var eye = context; eye.translateBy(x: x, y: eyeY); eye.rotate(by: .radians(direction * brow))
            eye.fill(Path(roundedRect: CGRect(x: -eyeW / 2, y: -eyeH / 2, width: eyeW, height: eyeH), cornerRadius: min(eyeW, eyeH) / 2), with: .color(ink))
        }
        let mouthY = S.face / 2 + M.interpolate(shape, table: S.mouthY)
        let turn = still ? 0 : M.bump(shape - 1) * S.mouthTilt * .pi / 180
        var mouth = context; mouth.translateBy(x: width / 2, y: mouthY); mouth.rotate(by: .radians(turn))
        stroke(Self.mouth(x: 0, y: 0, width: M.interpolate(shape, table: S.mouthWidth), bow: M.interpolate(shape, table: S.mouthBow)), color: ink, width: S.mouthStroke, in: mouth)
    }
    static func close(in context: GraphicsContext, color: Color) {
        var path = Path(); path.move(to: CGPoint(x: 4, y: 4)); path.addLine(to: CGPoint(x: 16, y: 16))
        path.move(to: CGPoint(x: 16, y: 4)); path.addLine(to: CGPoint(x: 4, y: 16))
        stroke(path, color: color, width: 2, in: context)
    }
    static func info(in context: GraphicsContext, color: Color) {
        context.stroke(Path(ellipseIn: CGRect(x: 1.5, y: 1.5, width: 17, height: 17)), with: .color(color), lineWidth: 1.8)
        var path = Path(); path.move(to: CGPoint(x: 10, y: 9.2)); path.addLine(to: CGPoint(x: 10, y: 14.2))
        stroke(path, color: color, width: 1.8, in: context)
        context.fill(Path(ellipseIn: CGRect(x: 8.9, y: 5, width: 2.2, height: 2.2)), with: .color(color))
    }
    static func send(in context: GraphicsContext, color: Color) {
        var path = Path(); path.move(to: CGPoint(x: 1, y: 6)); path.addLine(to: CGPoint(x: 15, y: 6))
        path.move(to: CGPoint(x: 10.5, y: 1.5)); path.addLine(to: CGPoint(x: 15, y: 6)); path.addLine(to: CGPoint(x: 10.5, y: 10.5))
        stroke(path, color: color, width: 1.8, in: context)
    }
    static func color(_ value: Double, palette: [Color]) -> Color {
        let t = M.clamp(value, from: 0, to: 2)
        let first = t <= 1 ? 0 : 1; let blend = t - Double(first)
        func rgb(_ color: Color) -> [Double] {
            var r = CGFloat(0); var g = CGFloat(0); var b = CGFloat(0); var a = CGFloat(0)
            UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
            return [Double(r), Double(g), Double(b)]
        }
        let a = rgb(palette[first]); let b = rgb(palette[first + 1])
        let values = zip(a, b).map { floor(pow(pow($0, M.gamma) * (1 - blend) + pow($1, M.gamma) * blend, 1 / M.gamma) * 255 + 0.5) }
        return Color.rgba(values[0], values[1], values[2], 1)
    }
}

/// The nearest stop drives the face, while the carried thumb and all color clocks keep their own motion.
struct EmojiMoodFeedback: View {
    var moods: [EmojiMoodFeedbackMood]
    var initialMood: Double
    var question: String
    var noteLabel: String
    var submitLabel: String
    var thanksTitle: String
    var thanksText: String
    var thanksDuration: Double
    var autoPlay: Bool
    var onMoodChange: ((Int, EmojiMoodFeedbackMood) -> Void)?
    var onSubmit: ((Int, String) -> Void)?
    var onClose: (() -> Void)?
    var onInfo: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var index: Int
    @State private var configuredStart: Int
    @State private var note = ""
    @State private var noteOpen = false
    @State private var sent = false
    @State private var touches = 0
    @State private var thumb: EmojiMoodFeedbackScalar
    @State private var shape: EmojiMoodFeedbackScalar
    @State private var tone: EmojiMoodFeedbackScalar
    @State private var slide: EmojiMoodFeedbackScalar
    @State private var held = EmojiMoodFeedbackScalar(0)
    @State private var noteShown = EmojiMoodFeedbackScalar(0)
    @State private var thanks = EmojiMoodFeedbackScalar(0)
    @State private var hop = EmojiMoodFeedbackScalar(0)
    @State private var keyboard = EmojiMoodFeedbackScalar(0)
    @State private var byHand = false
    @State private var grip = 0.0
    @State private var panBegan = false
    @GestureState private var panActive = false
    @State private var dragRelease = false
    @State private var hopDown: Date? = nil
    @State private var hopLand: Date? = nil
    @State private var questionHeight = 56.0
    @State private var fullHeight = 0.0
    @State private var layoutHeight = 0.0
    @State private var selectionHaptic = UISelectionFeedbackGenerator()
    @State private var successHaptic = UINotificationFeedbackGenerator()
    private typealias S = EmojiMoodFeedbackMetrics
    private typealias M = EmojiMoodFeedbackMotion
    private typealias D = EmojiMoodFeedbackDrawing

    init(
        moods: [EmojiMoodFeedbackMood] = EmojiMoodFeedbackMood.samples, initialMood: Double = 2,
        question: String = "How was your shopping\nexperience?", noteLabel: String = "Add note", submitLabel: String = "Submit",
        thanksTitle: String = "Thank you!", thanksText: String = "Your feedback helps us get better.",
        thanksDuration: Double = 2.4, autoPlay: Bool = false,
        onMoodChange: ((Int, EmojiMoodFeedbackMood) -> Void)? = nil, onSubmit: ((Int, String) -> Void)? = nil,
        onClose: (() -> Void)? = nil, onInfo: (() -> Void)? = nil
    ) {
        self.moods = moods; self.initialMood = initialMood; self.question = question
        self.noteLabel = noteLabel; self.submitLabel = submitLabel; self.thanksTitle = thanksTitle; self.thanksText = thanksText
        self.thanksDuration = thanksDuration; self.autoPlay = autoPlay
        self.onMoodChange = onMoodChange; self.onSubmit = onSubmit; self.onClose = onClose; self.onInfo = onInfo
        let start = Self.start(initialMood)
        _index = State(initialValue: start); _configuredStart = State(initialValue: start)
        _thumb = State(initialValue: EmojiMoodFeedbackScalar(Double(start)))
        _shape = State(initialValue: EmojiMoodFeedbackScalar(Double(start)))
        _tone = State(initialValue: EmojiMoodFeedbackScalar(Double(start)))
        _slide = State(initialValue: EmojiMoodFeedbackScalar(Double(start)))
    }
    private static func start(_ initial: Double) -> Int { Int(min(2, max(0, floor(initial + 0.5)))) }
    private var mood: EmojiMoodFeedbackMood { moods[index] }
    private var background: Color { D.color(tone.value, palette: moods.map(\.background)) }
    private var ink: Color { D.color(tone.value, palette: moods.map(\.ink)) }
    private var shade: Color { D.color(tone.value, palette: moods.map(\.shade)) }
    private var button: Color { D.color(tone.value, palette: moods.map(\.button)) }
    private var form: Bool { !noteOpen && !sent }
    private var formOpacity: Double { (1 - noteShown.value) * (1 - thanks.value) }
    private var trimmedNote: String { note.trimmingCharacters(in: M.trimCharacters) }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let top = insets.top + 14
            let bottom = max(insets.bottom, 16) + 6
            let faceTop = top + S.header + 30 + questionHeight
            let faceHeight = max(0, height - faceTop - S.formHeight - bottom)
            let faceCentre = faceTop + faceHeight / 2
            let fit = min(1, faceHeight / (S.face - 40))
            let cardTop = height - max(keyboard.value, bottom) - S.noteGap - S.note
            let noteRoomTop = top + S.header
            let n = noteShown.value * (1 - thanks.value)
            let squeeze = min(1, (cardTop - noteRoomTop) / (S.face - 30))
            let faceShift = ((noteRoomTop + cardTop) / 2 - faceCentre) * n + (height * 0.42 - faceCentre) * thanks.value + hop.value
            TimelineView(.animation) { timeline in
                ZStack(alignment: .topLeading) {
                    background
                    VStack(spacing: 0) {
                        header.opacity(1 - thanks.value).allowsHitTesting(!sent)
                        Text(question).font(.system(size: S.questionSize, weight: .medium)).tracking(-0.2).foregroundStyle(ink)
                            .lineSpacing(S.questionLeading).padding(.vertical, S.questionLeading / 2).multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true).frame(maxWidth: .infinity).padding(.horizontal, 40)
                            .background(GeometryReader { measured in
                                Color.clear.onAppear { questionHeight = measured.size.height }
                                    .onChange(of: measured.size.height) { _, next in questionHeight = next }
                            })
                            .padding(.top, 30).opacity(formOpacity).accessibilityAddTraits(.isHeader)
                        ZStack {
                            Canvas { context, _ in D.face(shape.value, width: width, ink: ink, still: reduceMotion, in: context) }
                                .frame(width: width, height: S.face)
                                .scaleEffect(fit * (1 + (squeeze - 1) * n)).offset(y: faceShift)
                                .accessibilityElement(children: .ignore).accessibilityAddTraits(.isImage).accessibilityLabel(mood.label + " face")
                                .allowsHitTesting(false)
                            Color.clear.frame(width: width, height: faceHeight).contentShape(Rectangle())
                                .onTapGesture { if noteOpen { closeNote() } }
                                .allowsHitTesting(noteOpen).accessibilityHidden(true)
                        }
                        .frame(width: width, height: faceHeight)
                        formControls(width: width).opacity(formOpacity).allowsHitTesting(form)
                    }
                    .padding(.top, top).padding(.bottom, bottom)
                    .frame(width: width, height: height, alignment: .top)
                    noteCard(width: width)
                        .opacity(noteShown.value * (1 - thanks.value))
                        .offset(x: S.side, y: cardTop + (reduceMotion ? 0 : (1 - noteShown.value) * 32))
                        .allowsHitTesting(noteOpen).accessibilityHidden(!noteOpen)
                    VStack(spacing: 8) {
                        Text(thanksTitle).font(.system(size: S.thanksTitleSize, weight: .heavy)).tracking(-0.6).foregroundStyle(ink)
                            .multilineTextAlignment(.center).frame(minHeight: S.thanksTitleHeight)
                        Text(thanksText).font(.system(size: S.thanksTextSize, weight: .medium)).foregroundStyle(ink).opacity(0.75)
                            .lineSpacing(S.thanksTextLeading).padding(.vertical, S.thanksTextLeading / 2).multilineTextAlignment(.center)
                    }
                    .frame(width: width - 64)
                    .offset(x: 32, y: height * 0.42 + S.face / 2 * fit - 4 + (reduceMotion ? 0 : (1 - thanks.value) * 18))
                    .opacity(thanks.value).allowsHitTesting(false).accessibilityElement(children: .combine).accessibilityHidden(!sent)
                }
                .frame(width: width, height: height).clipped()
                .offset(x: -insets.leading, y: -insets.top)
                .onChange(of: timeline.date) { _, date in advance(at: date) }
                .onAppear { laidOut(height) }
                .onChange(of: height) { _, next in laidOut(next) }
            }
        }
        .ignoresSafeArea(.keyboard)
        .preferredColorScheme(.light)
        .onAppear { selectionHaptic.prepare(); successHaptic.prepare() }
        .onChange(of: Self.start(initialMood)) { _, next in configuredStart = next }
        .onChange(of: panActive) { _, active in
            if !active { DispatchQueue.main.async { if !panActive { liftFinger() } } }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { moveKeyboard($0, shown: true) }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { moveKeyboard($0, shown: false) }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
        .task(id: ThanksKey(sent: sent, duration: thanksDuration)) {
            guard sent, thanksDuration > 0 else { return }
            try? await Task.sleep(for: .seconds(thanksDuration))
            guard !Task.isCancelled else { return }
            reset()
        }
    }
    private var header: some View {
        HStack {
            Button {
                touched()
                if noteOpen { closeNote() } else { onClose?() }
            } label: {
                Canvas { context, _ in D.close(in: context, color: ink) }.frame(width: 20, height: 20)
                    .frame(width: S.icon, height: S.icon).background(Circle().fill(.white.opacity(0.12)))
                    .contentShape(Rectangle().inset(by: -6))
            }.buttonStyle(PlainStyle()).accessibilityLabel("Close")
            Spacer()
            Button {
                touched(); onInfo?()
            } label: {
                Canvas { context, _ in D.info(in: context, color: ink) }.frame(width: 20, height: 20)
                    .frame(width: S.icon, height: S.icon).background(Circle().fill(.white.opacity(0.12)))
                    .contentShape(Rectangle().inset(by: -6))
            }.buttonStyle(PlainStyle()).accessibilityLabel("About this question")
        }
        .padding(.horizontal, S.side).frame(height: S.header)
    }
    private func formControls(width: Double) -> some View {
        VStack(spacing: 0) {
            ZStack {
                ForEach(0..<3, id: \.self) { i in
                    let away = Double(i) - slide.value
                    Text(moods[i].word).font(.system(size: S.word, weight: .black)).tracking(-2.5).foregroundStyle(shade)
                        .lineLimit(1).minimumScaleFactor(0.01).frame(width: width - 24, height: S.wordRow)
                        .opacity(reduceMotion ? 1 - min(1, abs(away)) : 1)
                        .offset(x: reduceMotion ? 0 : away * width)
                }
            }.frame(width: width, height: S.wordRow).accessibilityHidden(true)
            slider(width: width).padding(.top, 40)
            HStack(spacing: 0) {
                ForEach(0..<3, id: \.self) { i in
                    Button { select(i) } label: {
                        Text(moods[i].label).font(.system(size: S.labelSize, weight: .medium)).foregroundStyle(ink).lineLimit(1)
                            .frame(height: S.labelHeight).opacity(1 - 0.55 * min(1, abs(tone.value - Double(i))))
                            .frame(maxWidth: .infinity, alignment: i == 0 ? .leading : i == 2 ? .trailing : .center)
                            .contentShape(Rectangle().inset(by: -10))
                    }
                    .buttonStyle(PlainStyle()).accessibilityAddTraits(i == index ? .isSelected : [])
                }
            }.padding(.horizontal, S.trackInset - S.thumb / 2).padding(.top, 8)
            HStack(spacing: 0) {
                Button(action: openNote) {
                    Text(trimmedNote.isEmpty ? noteLabel : trimmedNote).font(.system(size: S.noteLabelSize, weight: .medium)).foregroundStyle(ink).lineLimit(1)
                        .frame(height: S.noteLabelHeight).padding(.leading, 26).padding(.trailing, 12)
                        .frame(maxWidth: .infinity, alignment: .leading).frame(height: S.bar).contentShape(Rectangle())
                }.buttonStyle(PlainStyle())
                submitButton(width: S.submit, height: S.bar)
            }
            .frame(height: S.bar).background(Capsule().fill(ink.opacity(0.08))).clipShape(Capsule())
            .padding(.horizontal, S.side).padding(.top, 64)
        }
    }
    private func slider(width: Double) -> some View {
        let span = width - S.trackInset * 2; let step = span / 2
        let sliderWidth = width - (S.trackInset - S.thumb / 2) * 2
        return ZStack(alignment: .topLeading) {
            Capsule().fill(ink.opacity(0.16)).frame(width: span, height: S.track)
                .offset(x: S.thumb / 2, y: (S.thumb - S.track) / 2)
            ForEach(0..<3, id: \.self) { stop in
                Circle().fill(ink.opacity(0.22)).frame(width: S.stop, height: S.stop)
                    .offset(x: S.thumb / 2 - S.stop / 2 + Double(stop) * step, y: (S.thumb - S.stop) / 2)
            }
            Circle().fill(ink).frame(width: S.thumb, height: S.thumb)
                .overlay(Canvas { context, _ in
                    D.stroke(D.mouth(x: S.thumb / 2, y: S.thumb / 2 + 5, width: S.thumbMouth, bow: M.interpolate(shape.value, table: S.thumbBow)), color: background, width: 2.6, in: context)
                })
                .scaleEffect(1 + (M.heldScale - 1) * held.value).offset(x: thumb.value * step)
        }
        .frame(width: sliderWidth, height: S.thumb).contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 0)
            .updating($panActive) { _, active, _ in active = true }
            .onChanged { event in
                if !panBegan {
                    panBegan = true; thumb.jump(thumb.value); dragRelease = false; byHand = true
                    held.time(to: 1, duration: M.heldIn)
                    let centre = S.thumb / 2 + thumb.value * step
                    let onThumb = abs(event.startLocation.x - centre) <= S.thumb / 2
                    grip = onThumb ? centre - event.startLocation.x : 0
                    if !onThumb {
                        let to = M.clamp((event.startLocation.x - S.thumb / 2) / step, from: 0, to: 2)
                        thumb.time(to: to, duration: M.callOver, curve: .outQuad)
                    }
                    touched()
                } else {
                    thumb.jump(M.clamp((event.location.x + grip - S.thumb / 2) / step, from: 0, to: 2))
                }
            }
            .onEnded { _ in liftFinger() }
        )
        .accessibilityElement(children: .ignore).accessibilityLabel(accessibleQuestion).accessibilityValue(mood.label)
        .accessibilityAdjustableAction { direction in select(min(2, max(0, index + (direction == .increment ? 1 : -1)))) }
    }
    private var accessibleQuestion: String {
        guard let newline = question.range(of: "\n") else { return question }
        return question.replacingCharacters(in: newline, with: " ")
    }
    private func submitButton(width: Double, height: Double) -> some View {
        Button(action: submit) {
            HStack(spacing: 10) {
                Text(submitLabel).font(.system(size: S.submitSize, weight: .semibold)).foregroundStyle(background).frame(height: S.submitHeight)
                Canvas { context, _ in D.send(in: context, color: background) }.frame(width: 16, height: 12)
            }
            .frame(width: width, height: height).background(Capsule().fill(button))
        }.buttonStyle(SubmitStyle()).accessibilityLabel(submitLabel)
    }
    private func noteCard(width: Double) -> some View {
        ZStack(alignment: .bottomTrailing) {
            EmojiMoodFeedbackNoteInput(text: $note, open: noteOpen, label: noteLabel, ink: mood.ink, onEdit: touched)
            submitButton(width: 112, height: S.noteSubmit).padding(10)
        }
        .padding(1.5)
        .frame(width: width - S.side * 2, height: S.note)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(.white.opacity(0.2)))
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(mood.ink.opacity(51.0 / 255), lineWidth: 1.5))
    }
    private func touched() { touches += 1 }
    private func select(_ to: Int) {
        touched(); byHand = true; dragRelease = false
        thumb.spring(to: Double(to), constants: M.snapSpring)
    }
    private func dragTo(_ to: Double, seconds: Double) {
        let now = Date.now; byHand = false
        held.time(to: 1, duration: M.heldIn, at: now)
        thumb.time(to: to, duration: seconds, curve: .inOutCubic, at: now)
        dragRelease = !thumb.timingFinished
        if !dragRelease { held.time(to: 0, duration: M.heldOut, at: now) }
    }
    private func liftFinger() {
        guard panBegan else { return }
        panBegan = false
        held.time(to: 0, duration: M.heldOut)
        let to = M.clamp(floor(thumb.value + 0.5), from: 0, to: 2)
        thumb.spring(to: to, constants: M.snapSpring); touched()
    }
    private func openNote() {
        touched(); noteOpen = true
        noteShown.time(to: 1, duration: M.note, curve: .outCubic)
    }
    private func closeNote() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        noteOpen = false
        noteShown.time(to: 0, duration: M.note, curve: .outCubic)
    }
    private func submit() {
        guard !sent else { return }
        let now = Date.now
        touched(); successHaptic.notificationOccurred(.success); closeNote(); sent = true
        onSubmit?(index, trimmedNote)
        thanks.time(to: 1, duration: M.thanks, curve: .outCubic, at: now)
        if !reduceMotion {
            hopDown = now.addingTimeInterval(M.hopDelay)
        }
    }
    private func reset() {
        sent = false; note = ""
        thanks.time(to: 0, duration: M.thanks, curve: .outCubic)
        byHand = false; dragRelease = false
        thumb.time(to: Double(configuredStart), duration: M.reset, curve: .inOutCubic)
    }
    private func laidOut(_ height: Double) { fullHeight = max(fullHeight, height); layoutHeight = height }
    private func moveKeyboard(_ notification: Notification, shown: Bool) {
        let height = shown ? (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue.height ?? 0 : 0
        let duration = (notification.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? NSNumber)?.doubleValue ?? 0
        let covered = max(0, height - max(0, fullHeight - layoutHeight))
        keyboard.time(to: covered, duration: duration == 0 ? M.keyboard : duration, curve: .outCubic)
    }
    private func advance(at date: Date) {
        thumb.advance(at: date); held.advance(at: date); hop.advance(at: date)
        if let down = hopDown, date >= down {
            hop.time(to: -M.hop, duration: M.hopOut, curve: .outQuad, at: down); hopDown = nil
            hopLand = down.addingTimeInterval(M.hopOut)
            hop.advance(at: date)
        }
        if let land = hopLand, date >= land {
            hop.advance(at: land); hop.spring(to: 0, constants: M.hopSpring, at: land); hopLand = nil
            hop.advance(at: date)
        }
        if dragRelease, thumb.timingFinished {
            held.time(to: 0, duration: M.heldOut, at: date); dragRelease = false
        }
        let next = Int(floor(thumb.value + 0.5))
        if next != index {
            index = next
            if reduceMotion { shape.time(to: Double(next), duration: M.shapeStill, at: date) }
            else { shape.spring(to: Double(next), constants: M.shapeSpring, at: date) }
            tone.time(to: Double(next), duration: M.tone, curve: .outQuad, at: date)
            slide.time(to: Double(next), duration: M.word, curve: .word, at: date)
            if byHand { selectionHaptic.selectionChanged() }
            onMoodChange?(next, moods[next])
        }
        shape.advance(at: date); tone.advance(at: date); slide.advance(at: date)
        noteShown.advance(at: date); thanks.advance(at: date); keyboard.advance(at: date)
    }
    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            while noteOpen || sent {
                try? await Task.sleep(for: .seconds(M.busyPoll))
                guard !Task.isCancelled else { return }
            }
            reset()
            try? await Task.sleep(for: .seconds(M.reset + M.resumeGap))
            guard !Task.isCancelled else { return }
        }
        let clock = ContinuousClock(); var origin = clock.now
        while !Task.isCancelled {
            for drag in M.drags {
                try? await clock.sleep(until: origin + .seconds(drag.at))
                guard !Task.isCancelled else { return }
                dragTo(drag.to, seconds: drag.lasts)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }
    private struct LoopKey: Equatable { let on: Bool; let touches: Int }
    private struct ThanksKey: Equatable { let sent: Bool; let duration: Double }
    private struct PlainStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View { configuration.label }
    }
    private struct SubmitStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View { configuration.label.opacity(configuration.isPressed ? 0.8 : 1) }
    }
}

/// UIKit keeps the note's real selection, multiline input and exact internal padding.
private struct EmojiMoodFeedbackNoteInput: UIViewRepresentable {
    @Binding var text: String
    let open: Bool
    let label: String
    let ink: Color
    let onEdit: () -> Void
    private typealias S = EmojiMoodFeedbackMetrics
    func makeCoordinator() -> EmojiMoodFeedbackNoteCoordinator { EmojiMoodFeedbackNoteCoordinator(self) }
    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator; view.backgroundColor = .clear
        view.font = .systemFont(ofSize: S.noteInputSize)
        view.textContainerInset = UIEdgeInsets(top: 16, left: 20, bottom: S.noteSubmit + 16, right: 20)
        view.textContainer.lineFragmentPadding = 0
        view.autocapitalizationType = .sentences; view.autocorrectionType = .default
        let placeholder = context.coordinator.placeholder
        placeholder.font = .systemFont(ofSize: S.noteInputSize)
        placeholder.numberOfLines = 0; placeholder.isUserInteractionEnabled = false
        placeholder.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(placeholder)
        NSLayoutConstraint.activate([
            placeholder.leadingAnchor.constraint(equalTo: view.frameLayoutGuide.leadingAnchor, constant: 20),
            placeholder.trailingAnchor.constraint(equalTo: view.frameLayoutGuide.trailingAnchor, constant: -20),
            placeholder.topAnchor.constraint(equalTo: view.frameLayoutGuide.topAnchor, constant: 16),
        ])
        return view
    }
    func updateUIView(_ view: UITextView, context: Context) {
        context.coordinator.parent = self
        let color = UIColor(ink)
        let paragraph = NSMutableParagraphStyle()
        paragraph.minimumLineHeight = S.noteInputHeight; paragraph.maximumLineHeight = S.noteInputHeight
        view.typingAttributes = [.font: UIFont.systemFont(ofSize: S.noteInputSize), .foregroundColor: color, .paragraphStyle: paragraph]
        view.textColor = color; view.tintColor = color; view.accessibilityLabel = label
        if view.text != text {
            view.attributedText = NSAttributedString(string: text, attributes: view.typingAttributes)
        }
        let placeholder = context.coordinator.placeholder
        placeholder.attributedText = NSAttributedString(string: label, attributes: [.font: UIFont.systemFont(ofSize: S.noteInputSize), .foregroundColor: color.withAlphaComponent(128.0 / 255), .paragraphStyle: paragraph])
        placeholder.isHidden = !text.isEmpty
        if open, !view.isFirstResponder {
            DispatchQueue.main.async { if context.coordinator.parent.open { view.becomeFirstResponder() } }
        } else if !open, view.isFirstResponder { view.resignFirstResponder() }
    }
    final class EmojiMoodFeedbackNoteCoordinator: NSObject, UITextViewDelegate {
        var parent: EmojiMoodFeedbackNoteInput
        let placeholder = UILabel()
        init(_ parent: EmojiMoodFeedbackNoteInput) { self.parent = parent }
        func textViewDidChange(_ textView: UITextView) {
            parent.onEdit(); parent.text = textView.text
            placeholder.isHidden = !textView.text.isEmpty
        }
        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
            let old = textView.text as NSString
            let room = S.noteMaxLength - (old.length - range.length)
            guard text.utf16.count > room else { return true }
            guard room > 0 else { return false }
            var units = Array(text.utf16.prefix(room))
            if let last = units.last, (0xd800...0xdbff).contains(last) { units.removeLast() }
            let clipped = String(decoding: units, as: UTF16.self)
            textView.text = old.replacingCharacters(in: range, with: clipped)
            textView.selectedRange = NSRange(location: range.location + clipped.utf16.count, length: 0)
            textViewDidChange(textView)
            return false
        }
    }
}

#Preview { EmojiMoodFeedback(autoPlay: true) }
