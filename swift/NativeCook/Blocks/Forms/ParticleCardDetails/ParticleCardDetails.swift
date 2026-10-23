import SwiftUI
import UIKit

struct ParticleCardDetailsCardDetails { let name: String; let securityCode: String }

private enum ParticleCardDetailsStyle {
    static let screen = Color.black, ink = Color.white, mute = Color(hex: 0x98989f), faint = Color(hex: 0x48484c), group = Color(hex: 0x2c2c2e), ghost = Color(hex: 0x35314c), hairline = Color(hex: 0x444447), closeFill = Color(hex: 0x222224)
    static let buttonOff = [56.0, 56.0, 58.0], buttonOffInk = [142.0, 142.0, 147.0], buttonOn = [242.0, 242.0, 247.0], buttonPressed = [185.0, 185.0, 191.0], buttonOnInk = [0.0, 0.0, 0.0], caret = Color(hex: 0x0a84ff)
    static let close = 44.0, closeLeft = 15.0, closeTop = 3.0, cardMargin = 20.0, cardTop = 75.0, cardRatio = 1.586, textGap = 76.0, textLeft = 38.0, texts = 71.0, fieldsGap = 29.0, fieldsMargin = 22.0, row = 48.5, valueLeft = 131.0, button = 51.0, buttonMargin = 38.0
    static let keyboardGap = 12.0, fieldsClear = 16.0, fieldsShrink = 0.9, fieldsRadius = 23.0, ghostBlur = 16.0, ghostSpill = 40.0, bottomMin = 16.0, bottomPad = 1.0
    static let title = 19.0, titleLine = 23.0, subtitle = 19.0, subtitleLine = 24.0, textRightExtra = 14.0, spinner = 16.0, spinnerGap = 8.0, spinnerBarWidth = 1.8, spinnerBarHeight = 4.6, spinnerBarRadius = 0.9, spinnerOrbit = -5.0
    static let manualTop = 18.0, manualText = 12.0, manualLine = 16.0, manualHit = 10.0, label = 14.0, labelLine = 18.0, input = 15.0, inputLine = 20.0, secureMirror = 22.0, secureTracking = 1.0, valueRight = 16.0, caretWidth = 2.0, caretHeight = 19.0, caretGap = 1.0, caretRadius = 1.0, closeIcon = 25.0
}

private enum ParticleCardDetailsMotion {
    static let tapAt = 0.8, typeAt = 1.05, letterGaps = [0.11, 0.09, 0.13, 0.1, 0.16, 0.12, 0.09, 0.11], nextFieldAfter = 0.3, codeAfter = 0.24, digitEvery = 0.17, pressAfter = 0.5, pressFor = 0.15, userHold = 3.0, idlePoll = 0.1
    static let textSwap = 0.14, fieldsOut = 0.28, chromeOut = 0.2, fieldsIn = 0.32, chromeAfter = 0.2, chromeIn = 0.26, spinStep = 0.1, bars = 8, keyboard = 0.25
    static let pressedFill = 0.08, releasedFill = 0.22, pressScale = 0.12, labelColor = 0.22, buttonScale = 0.97
    static let css = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.25, y: 0.1), endControlPoint: UnitPoint(x: 0.25, y: 1))
    static let outCubic = UnitCurve.bezier(startControlPoint: UnitPoint(x: 1 / 3, y: 1), endControlPoint: UnitPoint(x: 2 / 3, y: 1))
}

private enum ParticleCardDetailsMath {
    private static let trimCharacters = CharacterSet(charactersIn: "\u{0009}\u{000A}\u{000B}\u{000C}\u{000D}\u{0020}\u{00A0}\u{1680}\u{2000}\u{2001}\u{2002}\u{2003}\u{2004}\u{2005}\u{2006}\u{2007}\u{2008}\u{2009}\u{200A}\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}\u{FEFF}")
    static func trim(_ text: String) -> String { text.trimmingCharacters(in: trimCharacters) }
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ time: Double, _ from: Double, _ to: Double) -> Double { let t = clamp((time - from) / (to - from)); return t * t * (3 - 2 * t) }
    static func prefix(_ text: String, count: Int) -> String { String(decoding: text.utf16.prefix(count), as: UTF16.self) }
    static func groupColor(_ t: Double) -> Color {
        let from = [44.0, 44.0, 46.0], to = [53.0, 49.0, 76.0], t = clamp(t)
        if t == 0 { return ParticleCardDetailsStyle.group }; if t == 1 { return ParticleCardDetailsStyle.ghost }
        let channels = zip(from, to).map { a, b in (pow(pow(a / 255, 2.2) + (pow(b / 255, 2.2) - pow(a / 255, 2.2)) * t, 1 / 2.2) * 255).rounded() }
        return .rgba(channels[0], channels[1], channels[2], 1)
    }
}

private enum ParticleCardDetailsField { case name, code }
private enum ParticleCardDetailsEventKind { case nameCaret, codeCaret, name(String), code(String), press, submit, cycle(Date) }
private struct ParticleCardDetailsEvent { let at: Date; let order: Int; let kind: ParticleCardDetailsEventKind }
private struct ParticleCardDetailsLoopKey: Equatable { let enabled: Bool; let touches: Int; let name: String; let code: String }

@MainActor
private final class ParticleCardDetailsState: ObservableObject {
    private typealias S = ParticleCardDetailsStyle
    private typealias M = ParticleCardDetailsMotion
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var name = "", code = ""
    private(set) var focused: ParticleCardDetailsField?, autoField: ParticleCardDetailsField?
    private(set) var autoPressed = false, busy = false, elapsed = -1.0
    private(set) var lift = Tween(0), raise = Tween(0)
    var codeLength = 3, processingDuration = 2.7, still = false
    var buttonRest = 0.0, fieldsBottom = 0.0
    var onSubmit: ((ParticleCardDetailsCardDetails) -> Void)?, onDone: ((ParticleCardDetailsCardDetails) -> Void)?, onManual: (() -> Void)?, onClose: (() -> Void)?
    private var sent = ParticleCardDetailsCardDetails(name: "", securityCode: "")
    private var origin: Date?, clockEnd = 0.0, past: Bool?
    private var twinkleOrigin: Date?, twinkleReduced = false
    var ready: Bool { ParticleCardDetailsMath.trim(name).utf16.count >= 2 && code.utf16.count == codeLength }
    func mount(_ date: Date) { if twinkleOrigin == nil { twinkleOrigin = date; twinkleReduced = still }; react() }
    func time(_ date: Date) -> Double {
        guard let twinkleOrigin else { return 0 }; let time = max(0, date.timeIntervalSince(twinkleOrigin))
        return twinkleReduced ? min(ParticleCardDetailsLOOP, time) : time.truncatingRemainder(dividingBy: ParticleCardDetailsLOOP)
    }
    func touched() { autoField = nil; autoPressed = false; touches += 1; revision &+= 1 }
    func requestFocus(_ field: ParticleCardDetailsField) { if focused != field { focused = field; revision &+= 1 } }
    func focus(_ field: ParticleCardDetailsField) { touched(); focused = field; revision &+= 1 }
    func blur(_ field: ParticleCardDetailsField) { if focused == field { focused = nil; revision &+= 1 } }
    func editName(_ value: String) { touched(); name = value; revision &+= 1 }
    func editCode(_ value: String) { touched(); code = String(decoding: value.utf16.filter { $0 >= 48 && $0 <= 57 }, as: UTF16.self); revision &+= 1 }
    func dismiss() { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
    func close() { touched(); dismiss(); onClose?() }
    func manual() { touched(); onManual?() }
    func submit(byHand: Bool, at date: Date = .now) {
        guard !busy && ready else { return }
        let end = processingDuration + M.chromeAfter + M.chromeIn
        if byHand { UIImpactFeedbackGenerator(style: .light).impactOccurred() }; dismiss()
        sent = ParticleCardDetailsCardDetails(name: ParticleCardDetailsMath.trim(name), securityCode: code); autoField = nil; busy = true
        onSubmit?(sent); elapsed = 0; origin = date; clockEnd = end; react(); revision &+= 1
    }
    private func react() {
        let next = elapsed >= processingDuration
        if next && past == false { name = ""; code = ""; onDone?(sent) }; past = next
    }
    func advance(_ date: Date) {
        if let origin {
            let age = max(0, date.timeIntervalSince(origin)); elapsed = min(clockEnd, age); react()
            if age >= clockEnd { self.origin = nil; elapsed = -1; busy = false; react() }
        } else { react() }; revision &+= 1
    }
    func keyboard(_ frame: CGRect?, duration: Double, at date: Date = .now) {
        let duration = still ? 0 : duration > 0 ? duration : M.keyboard
        let buttonTop = frame.map { Double($0.minY) - S.keyboardGap - S.button }
        raise.retarget(buttonTop.map { min(0, $0 - buttonRest) } ?? 0, at: date, duration: duration, curve: M.outCubic)
        lift.retarget(buttonTop.map { min(0, $0 - S.fieldsClear - fieldsBottom) } ?? 0, at: date, duration: duration, curve: M.outCubic); revision &+= 1
    }
    func autoplay(enabled: Bool, demoName: String, demoCode: String) async {
        guard enabled else { return }
        var queue: [ParticleCardDetailsEvent] = [], order = 0
        func add(_ at: Date, _ kind: ParticleCardDetailsEventKind) { order += 1; queue.append(ParticleCardDetailsEvent(at: at, order: order, kind: kind)) }
        func cycle(_ origin: Date) {
            add(origin.addingTimeInterval(M.tapAt), .nameCaret); var time = M.typeAt
            for index in 0..<demoName.utf16.count { if index > 0 { time += M.letterGaps[(index - 1) % M.letterGaps.count] }; add(origin.addingTimeInterval(time), .name(ParticleCardDetailsMath.prefix(demoName, count: index + 1))) }
            time += M.nextFieldAfter; add(origin.addingTimeInterval(time), .codeCaret); time += M.codeAfter
            for index in 0..<demoCode.utf16.count { if index > 0 { time += M.digitEvery }; add(origin.addingTimeInterval(time), .code(ParticleCardDetailsMath.prefix(demoCode, count: index + 1))) }
            time += M.pressAfter; add(origin.addingTimeInterval(time), .press); add(origin.addingTimeInterval(time + M.pressFor), .submit)
            add(origin.addingTimeInterval(ParticleCardDetailsLOOP), .cycle(origin.addingTimeInterval(ParticleCardDetailsLOOP)))
        }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(M.userHold * 1e9)); try Task.checkCancellation()
                while busy { try await Task.sleep(nanoseconds: UInt64(M.idlePoll * 1e9)); try Task.checkCancellation() }
                dismiss(); name = ""; code = ""; revision &+= 1
            }
            cycle(.now)
            while !Task.isCancelled {
                queue.sort { $0.at == $1.at ? $0.order < $1.order : $0.at < $1.at }; let event = queue.removeFirst()
                let wait = event.at.timeIntervalSinceNow; if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }; try Task.checkCancellation()
                switch event.kind {
                case .nameCaret: autoField = .name
                case .codeCaret: autoField = .code
                case let .name(value): name = value
                case let .code(value): code = value
                case .press: autoField = nil; autoPressed = true
                case .submit: autoPressed = false; submit(byHand: false)
                case let .cycle(origin): cycle(origin)
                }; revision &+= 1
            }
        } catch { return }
    }
}

private struct ParticleCardDetailsInput: UIViewRepresentable {
    let value: String
    let label: String
    let focused: Bool
    let secure: Bool
    let maxLength: Int?
    let fontSize: Double
    let lineHeight: Double
    let onChange: (String) -> Void
    let onFocus: () -> Void
    let onBlur: () -> Void
    let onReturn: () -> Void
    func makeCoordinator() -> ParticleCardDetailsInputCoordinator { ParticleCardDetailsInputCoordinator(self) }
    func makeUIView(context: Context) -> UITextField {
        let field = UITextField(); field.delegate = context.coordinator
        field.borderStyle = .none; field.backgroundColor = .clear; field.keyboardAppearance = .dark; field.autocapitalizationType = .words; field.autocorrectionType = .no; field.spellCheckingType = .no
        field.keyboardType = secure ? .numberPad : .default; field.isSecureTextEntry = secure; field.returnKeyType = secure ? .done : .next
        field.textContentType = secure ? .creditCardSecurityCode : .creditCardName; field.tintColor = UIColor(ParticleCardDetailsStyle.caret); field.accessibilityLabel = label
        field.addTarget(context.coordinator, action: #selector(ParticleCardDetailsInputCoordinator.changed(_:)), for: .editingChanged)
        return field
    }
    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.parent = self
        let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = lineHeight; paragraph.maximumLineHeight = lineHeight
        field.font = .systemFont(ofSize: fontSize); field.textColor = .white; field.defaultTextAttributes = [.font: UIFont.systemFont(ofSize: fontSize), .foregroundColor: UIColor.white, .paragraphStyle: paragraph]
        if field.text != value { field.text = value }; field.accessibilityLabel = label
        if focused && !field.isFirstResponder { field.becomeFirstResponder() }
        if !focused && field.isFirstResponder { field.resignFirstResponder() }
    }
    final class ParticleCardDetailsInputCoordinator: NSObject, UITextFieldDelegate {
        var parent: ParticleCardDetailsInput
        init(_ parent: ParticleCardDetailsInput) { self.parent = parent }
        @objc func changed(_ field: UITextField) { parent.onChange(field.text ?? "") }
        func textFieldDidBeginEditing(_ textField: UITextField) { parent.onFocus() }
        func textFieldDidEndEditing(_ textField: UITextField) { parent.onBlur() }
        func textFieldShouldReturn(_ textField: UITextField) -> Bool { textField.resignFirstResponder(); parent.onReturn(); return true }
        func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange, replacementString replacement: String) -> Bool {
            guard let maxLength = parent.maxLength else { return true }
            let current = (textField.text ?? "") as NSString, inserted = replacement as NSString
            var available = maxLength - current.length + range.length
            if available > 0 && inserted.length > available {
                let character = inserted.rangeOfComposedCharacterSequence(at: available - 1)
                if character.location + character.length > available { available = character.location }
            }
            guard available > 0 else { return false }
            guard inserted.length > available else { return true }
            let accepted = inserted.substring(to: available)
            textField.text = current.replacingCharacters(in: range, with: accepted)
            if let caret = textField.position(from: textField.beginningOfDocument, offset: range.location + available) { textField.selectedTextRange = textField.textRange(from: caret, to: caret) }
            changed(textField); return false
        }
    }
}

private struct ParticleCardDetailsHit: View {
    let width: Double
    let height: Double
    let label: String
    var disabled = false
    var link = false
    var hold: ((Bool) -> Void)? = nil
    let activate: () -> Void
    @State private var began = false
    @State private var inside = false
    @State private var activatedAt: Date?
    @State private var pendingOut: Task<Void, Never>?
    @GestureState private var active = false
    private static let retention = 20.0, bottomRetention = 30.0, minimumPress = 0.13
    private func contains(_ point: CGPoint) -> Bool { point.x > -Self.retention && point.x < width + Self.retention && point.y > -Self.retention && point.y < height + Self.bottomRetention }
    private func pressIn() { inside = true; activatedAt = .now; hold?(true) }
    private func pressOut() {
        inside = false; guard let hold else { return }; let wait = max(0, Self.minimumPress - Date.now.timeIntervalSince(activatedAt ?? .now))
        if wait == 0 { hold(false); return }
        pendingOut = Task { @MainActor in do { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)); try Task.checkCancellation(); hold(false) } catch { return } }
    }
    var body: some View {
        Color.clear.frame(width: width, height: height).contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).updating($active) { _, active, _ in active = true }
                .onChanged { event in if !began { pendingOut?.cancel(); began = true; pressIn() }; let next = contains(event.location); if next != inside { if next { pressIn() } else { pressOut() } } }
                .onEnded { _ in guard began else { return }; began = false; let perform = inside; if inside { pressOut() }; if perform { activate() } })
            .onChange(of: active) { _, next in if !next { DispatchQueue.main.async { if !active && began { began = false; if inside { pressOut() } } } } }
            .onDisappear { pendingOut?.cancel(); hold?(false) }.allowsHitTesting(!disabled).disabled(disabled)
            .accessibilityElement(children: .ignore).accessibilityAddTraits(link ? .isLink : .isButton).accessibilityLabel(label).accessibilityAction { if !disabled { activate() } }
    }
}

private enum ParticleCardDetailsText {
    static func width(_ text: String, size: Double, weight: UIFont.Weight, tracking: Double = 0, pixel: Double) -> Double {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        let width = text.components(separatedBy: .newlines).map { ($0 as NSString).size(withAttributes: [.font: font, .kern: tracking]).width }.max() ?? 0
        return ceil(width * pixel) / pixel
    }
    static func height(_ text: String, size: Double, line: Double, weight: UIFont.Weight, width: Double, lines: Int? = nil) -> Double {
        let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line
        let height = (text as NSString).boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: [.font: UIFont.systemFont(ofSize: size, weight: weight), .paragraphStyle: paragraph], context: nil).height
        let measured = max(line, ceil(height / line) * line); return lines.map { min(Double($0) * line, measured) } ?? measured
    }
}

private struct ParticleCardDetailsSpinner: View {
    let elapsed: Double
    private typealias S = ParticleCardDetailsStyle
    private typealias M = ParticleCardDetailsMotion
    var body: some View {
        ZStack {
            ForEach(0..<M.bars, id: \.self) { index in
                RoundedRectangle(cornerRadius: S.spinnerBarRadius, style: .circular).fill(S.mute).frame(width: S.spinnerBarWidth, height: S.spinnerBarHeight)
                    .opacity(0.2 + 0.8 * Double(index) / Double(M.bars - 1)).offset(y: S.spinnerOrbit).rotationEffect(.degrees(Double(index) * 360 / Double(M.bars)))
            }
        }.frame(width: S.spinner, height: S.spinner).rotationEffect(.degrees(floor(max(0, elapsed) / M.spinStep).truncatingRemainder(dividingBy: Double(M.bars)) * 360 / Double(M.bars))).accessibilityHidden(true)
    }
}

private struct ParticleCardDetailsGhost: View {
    let width: Double
    let height: Double
    let gone: Double
    let still: Bool
    private typealias S = ParticleCardDetailsStyle
    var body: some View {
        Canvas { context, _ in
            let center = CGPoint(x: S.ghostSpill + width / 2, y: S.ghostSpill + height / 2), scale = still ? 1 : 1 - (1 - S.fieldsShrink) * gone
            var shape = context.scaled(scale, about: center)
            shape.opacity = ParticleCardDetailsMath.smooth(gone, 0, 0.35) * (1 - ParticleCardDetailsMath.smooth(gone, 0.4, 1))
            shape.addFilter(.blur(radius: 1 + S.ghostBlur * gone))
            shape.fill(Path(roundedRect: CGRect(x: S.ghostSpill, y: S.ghostSpill, width: width, height: height), cornerRadius: S.fieldsRadius), with: .color(S.ghost))
        }.frame(width: width + S.ghostSpill * 2, height: height + S.ghostSpill * 2).allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// Real fields take over from the keyboard-free mirror; the submission clock drives every layer.
struct ParticleCardDetails: View {
    var autoPlay: Bool
    var demoName: String
    var demoSecurityCode: String
    var securityCodeLength: Int
    var processingDuration: Double
    var title: String
    var subtitle: String
    var nameLabel: String
    var securityCodeLabel: String
    var continueLabel: String
    var addingTitle: String
    var addingSubtitle: String
    var manualLabel: String
    var onSubmit: ((ParticleCardDetailsCardDetails) -> Void)?
    var onDone: ((ParticleCardDetailsCardDetails) -> Void)?
    var onManual: (() -> Void)?
    var onClose: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var fontScale = 1.0
    @StateObject private var state = ParticleCardDetailsState()
    @State private var held = false
    @State private var fill = ParticleCardDetailsStyle.buttonOff.map(Tween.init)
    @State private var labelTone = ParticleCardDetailsStyle.buttonOffInk.map(Tween.init)
    @State private var scale = Tween(1)
    private typealias S = ParticleCardDetailsStyle
    private typealias M = ParticleCardDetailsMotion
    private typealias F = ParticleCardDetailsMath
    private typealias T = ParticleCardDetailsText
    init(autoPlay: Bool = false, demoName: String = "Maya Chen", demoSecurityCode: String = "482", securityCodeLength: Int = 3, processingDuration: Double = 2.7, title: String = "Card Details", subtitle: String = "Verify and complete your card information.", nameLabel: String = "Name", securityCodeLabel: String = "Security Code", continueLabel: String = "Continue", addingTitle: String = "Adding Card", addingSubtitle: String = "Contacting the Card Issuer…", manualLabel: String = "Enter Card Details Manually", onSubmit: ((ParticleCardDetailsCardDetails) -> Void)? = nil, onDone: ((ParticleCardDetailsCardDetails) -> Void)? = nil, onManual: (() -> Void)? = nil, onClose: (() -> Void)? = nil) {
        self.autoPlay = autoPlay; self.demoName = demoName; self.demoSecurityCode = demoSecurityCode; self.securityCodeLength = securityCodeLength; self.processingDuration = processingDuration; self.title = title; self.subtitle = subtitle; self.nameLabel = nameLabel; self.securityCodeLabel = securityCodeLabel; self.continueLabel = continueLabel; self.addingTitle = addingTitle; self.addingSubtitle = addingSubtitle; self.manualLabel = manualLabel; self.onSubmit = onSubmit; self.onDone = onDone; self.onManual = onManual; self.onClose = onClose
    }
    private var pressed: Bool { state.ready && (held || state.autoPressed) }
    private var fillMode: Int { state.ready ? pressed ? 2 : 1 : 0 }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets, width = proxy.size.width + insets.leading + insets.trailing, height = proxy.size.height + insets.top + insets.bottom
            let cardWidth = width - S.cardMargin * 2, cardHeight = cardWidth / S.cardRatio, fieldsHeight = S.row * 2 + 1 / displayScale
            let cardTop = insets.top + S.cardTop, gap = min(S.textGap, max(0, height - cardTop - cardHeight - S.texts - S.fieldsGap - fieldsHeight)), textTop = cardTop + cardHeight + gap, fieldsTop = textTop + S.texts + S.fieldsGap
            let bottom = max(insets.bottom, S.bottomMin) + S.bottomPad, buttonRest = height - bottom - S.button
            TimelineView(.animation) { timeline in
                let _ = configure(buttonRest: buttonRest, fieldsBottom: fieldsTop + fieldsHeight)
                let e = state.elapsed, ask = e < 0 ? 1 : e < processingDuration ? 1 - F.smooth(e, 0, M.textSwap) : F.smooth(e, processingDuration, processingDuration + M.textSwap)
                let adding = e < 0 ? 0 : e < processingDuration ? F.smooth(e, 0, M.textSwap) : 1 - F.smooth(e, processingDuration, processingDuration + M.textSwap)
                let gone = e < 0 ? 0 : e < processingDuration ? F.smooth(e, 0, M.fieldsOut) : 1 - F.smooth(e, processingDuration, processingDuration + M.fieldsIn)
                let chrome = e < 0 ? 1 : e < processingDuration ? 1 - F.smooth(e, 0, M.chromeOut) : F.smooth(e, processingDuration + M.chromeAfter, processingDuration + M.chromeAfter + M.chromeIn)
                ZStack(alignment: .topLeading) {
                    ZStack(alignment: .topLeading) {
                        Color.clear.frame(width: width, height: height).contentShape(Rectangle()).onTapGesture { state.dismiss() }.accessibilityHidden(true)
                        ParticleCardDetailsParticleCard(width: cardWidth, height: cardHeight, time: state.time(timeline.date), period: ParticleCardDetailsLOOP, elapsed: e, still: reduceMotion)
                            .frame(width: cardWidth, height: cardHeight, alignment: .topLeading).offset(x: S.cardMargin, y: cardTop).allowsHitTesting(false)
                        texts(width: width - S.textLeft * 2 - S.textRightExtra, ask: ask, adding: adding).offset(x: S.textLeft, y: textTop)
                        if state.busy { ParticleCardDetailsGhost(width: width - S.fieldsMargin * 2, height: fieldsHeight, gone: gone, still: reduceMotion).offset(x: S.fieldsMargin - S.ghostSpill, y: fieldsTop - S.ghostSpill) }
                        fields(width: width - S.fieldsMargin * 2, height: fieldsHeight).background(F.groupColor(min(1, gone * 2)), in: RoundedRectangle(cornerRadius: S.fieldsRadius, style: .continuous))
                            .opacity(1 - F.smooth(gone, 0, 0.55)).scaleEffect(reduceMotion ? 1 : 1 - (1 - S.fieldsShrink) * gone)
                            .offset(x: S.fieldsMargin, y: fieldsTop).allowsHitTesting(!state.busy)
                    }.frame(width: width, height: height, alignment: .topLeading).offset(y: state.lift.value(at: timeline.date))
                    close.opacity(chrome).offset(x: S.closeLeft, y: insets.top + S.closeTop).allowsHitTesting(!state.busy)
                    button(width: width - S.buttonMargin * 2, date: timeline.date).opacity(chrome).offset(x: S.buttonMargin, y: buttonRest + state.raise.value(at: timeline.date)).allowsHitTesting(!state.busy)
                }.frame(width: width, height: height, alignment: .topLeading).clipped().offset(x: -insets.leading, y: -insets.top)
                    .onChange(of: timeline.date) { _, date in state.advance(date) }
            }.onAppear { state.mount(.now) }
        }.background(S.screen.ignoresSafeArea()).preferredColorScheme(.dark).ignoresSafeArea(.keyboard)
            .task(id: ParticleCardDetailsLoopKey(enabled: autoPlay, touches: state.touches, name: demoName, code: demoSecurityCode)) { await state.autoplay(enabled: autoPlay, demoName: demoName, demoCode: demoSecurityCode) }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { keyboard($0, showing: true) }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { keyboard($0, showing: false) }
            .onChange(of: fillMode) { _, next in updateFill(next) }
            .onChange(of: pressed) { _, next in scale.retarget(next ? M.buttonScale : 1, duration: M.pressScale, curve: M.css) }
            .onChange(of: state.ready) { _, next in for index in labelTone.indices { labelTone[index].retarget((next ? S.buttonOnInk : S.buttonOffInk)[index], duration: M.labelColor, curve: M.css) } }
    }
    private func configure(buttonRest: Double, fieldsBottom: Double) {
        state.codeLength = securityCodeLength; state.processingDuration = processingDuration; state.still = reduceMotion; state.buttonRest = buttonRest; state.fieldsBottom = fieldsBottom
        state.onSubmit = onSubmit; state.onDone = onDone; state.onManual = onManual; state.onClose = onClose
    }
    private func keyboard(_ notification: Notification, showing: Bool) {
        let frame = showing ? notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect : nil
        let duration = notification.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? M.keyboard
        state.keyboard(frame, duration: duration)
    }
    private func updateFill(_ mode: Int) {
        let color = mode == 0 ? S.buttonOff : mode == 1 ? S.buttonOn : S.buttonPressed
        for index in fill.indices { fill[index].retarget(color[index], duration: pressed ? M.pressedFill : M.releasedFill, curve: M.css) }
    }
    private func text(_ value: String, size: Double, line: Double, weight: UIFont.Weight = .regular, color: Color = S.ink, width: Double, lines: Int? = nil, tracking: Double = 0) -> some View {
        let font = UIFont.systemFont(ofSize: size * fontScale, weight: weight), line = line * fontScale, height = T.height(value, size: size * fontScale, line: line, weight: weight, width: width, lines: lines)
        return Text(value).font(Font(font)).foregroundStyle(color).tracking(tracking).lineSpacing(line - font.lineHeight).lineLimit(lines).padding(.vertical, (line - font.lineHeight) / 2).frame(width: width, height: height, alignment: .topLeading)
    }
    private func texts(width: Double, ask: Double, adding: Double) -> some View {
        let titleWidth = min(width, T.width(addingTitle, size: S.title * fontScale, weight: .bold, pixel: displayScale))
        let manualWidth = min(width, T.width(manualLabel, size: S.manualText * fontScale, weight: .regular, pixel: displayScale)), manualHeight = T.height(manualLabel, size: S.manualText * fontScale, line: S.manualLine * fontScale, weight: .regular, width: manualWidth)
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                text(title, size: S.title, line: S.titleLine, weight: .bold, width: width, lines: 1)
                text(subtitle, size: S.subtitle, line: S.subtitleLine, color: S.mute, width: width, lines: 2)
            }.opacity(ask).allowsHitTesting(false).accessibilityHidden(state.busy)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: S.spinnerGap) {
                    text(addingTitle, size: S.title, line: S.titleLine, weight: .bold, width: titleWidth, lines: 1)
                    ParticleCardDetailsSpinner(elapsed: state.elapsed)
                }.allowsHitTesting(false)
                text(addingSubtitle, size: S.subtitle, line: S.subtitleLine, color: S.mute, width: width, lines: 1).allowsHitTesting(false)
                ZStack(alignment: .topLeading) {
                    text(manualLabel, size: S.manualText, line: S.manualLine, color: S.faint, width: manualWidth).accessibilityHidden(true)
                    ParticleCardDetailsHit(width: manualWidth + S.manualHit * 2, height: manualHeight + S.manualHit * 2, label: manualLabel, link: true, activate: state.manual).offset(x: -S.manualHit, y: -S.manualHit)
                }.frame(width: manualWidth, height: manualHeight, alignment: .topLeading).padding(.top, S.manualTop)
            }.opacity(adding).allowsHitTesting(state.busy).accessibilityHidden(!state.busy)
        }.frame(width: width, height: S.texts, alignment: .topLeading)
    }
    private func fields(width: Double, height: Double) -> some View {
        VStack(spacing: 0) {
            row(.name, label: nameLabel, value: state.name, width: width)
            S.hairline.frame(width: width - (S.textLeft - S.fieldsMargin) * 2, height: 1 / displayScale)
            row(.code, label: securityCodeLabel, value: state.code, width: width)
        }.frame(width: width, height: height)
    }
    private func row(_ field: ParticleCardDetailsField, label: String, value: String, width: Double) -> some View {
        let secure = field == .code, focused = state.focused == field, valueWidth = width - S.valueLeft - S.valueRight
        let shown = secure ? String(repeating: "•", count: value.utf16.count) : value, size = secure ? S.secureMirror : S.input, tracking = secure ? S.secureTracking : 0
        let mirrorWidth = min(valueWidth, T.width(shown, size: size * fontScale, weight: .regular, tracking: tracking, pixel: displayScale))
        return ZStack(alignment: .leading) {
            Color.clear.contentShape(Rectangle()).onTapGesture { state.requestFocus(field) }.accessibilityHidden(true)
            text(label, size: S.label, line: S.labelLine, weight: .semibold, width: S.valueLeft - (S.textLeft - S.fieldsMargin), lines: 1).offset(x: S.textLeft - S.fieldsMargin).accessibilityHidden(true)
            ZStack(alignment: .leading) {
                if !focused {
                    HStack(spacing: S.caretGap) {
                        text(shown, size: size, line: S.inputLine, width: mirrorWidth, lines: 1, tracking: tracking)
                        if state.autoField == field { RoundedRectangle(cornerRadius: S.caretRadius, style: .circular).fill(S.caret).frame(width: S.caretWidth, height: S.caretHeight) }
                    }.allowsHitTesting(false).accessibilityHidden(true)
                }
                ParticleCardDetailsInput(value: value, label: label, focused: focused, secure: secure, maxLength: secure ? max(0, securityCodeLength) : nil, fontSize: S.input * fontScale, lineHeight: S.inputLine * fontScale, onChange: secure ? state.editCode : state.editName, onFocus: { state.focus(field) }, onBlur: { state.blur(field) }, onReturn: { if secure { state.dismiss() } else { state.requestFocus(.code) } })
                    .frame(width: valueWidth, height: S.row).opacity(focused ? 1 : 0)
            }.frame(width: valueWidth, height: S.row, alignment: .leading).offset(x: S.valueLeft)
        }.frame(width: width, height: S.row)
    }
    private var close: some View {
        ZStack {
            Circle().fill(S.closeFill)
            Image(systemName: "xmark").font(.system(size: S.closeIcon, weight: .regular)).foregroundStyle(S.ink).accessibilityHidden(true)
            ParticleCardDetailsHit(width: S.close, height: S.close, label: "Close", activate: state.close)
        }.frame(width: S.close, height: S.close)
    }
    private func button(width: Double, date: Date) -> some View {
        let channels = fill.map { $0.value(at: date).rounded() }, label = labelTone.map { $0.value(at: date).rounded() }
        return ZStack {
            RoundedRectangle(cornerRadius: S.button / 2, style: .circular).fill(Color.rgba(channels[0], channels[1], channels[2], 1))
                .overlay { text(continueLabel, size: S.input, line: S.inputLine, weight: .semibold, color: .rgba(label[0], label[1], label[2], 1), width: min(width, T.width(continueLabel, size: S.input * fontScale, weight: .semibold, pixel: displayScale)), lines: 1) }
                .scaleEffect(scale.value(at: date)).accessibilityHidden(true)
            ParticleCardDetailsHit(width: width, height: S.button, label: continueLabel, disabled: !state.ready, hold: { held = $0 }) { state.touched(); state.submit(byHand: true) }
        }.frame(width: width, height: S.button)
    }
}
