import SwiftUI

/// A task composer that reads what is typed. A date word ("Wed", "tomorrow") turns blue and opens a
/// schedule menu under it; "/" opens a file menu whose row drops a file chip into the sentence. The
/// card grows a line at a time with a wobble of its bottom edge.
struct SmartTokenInput: View {
    /// Controlled text. Leave it nil and the component keeps its own.
    var text: Binding<String>? = nil
    /// Types the script, picks a date and a file, clears the field and starts over.
    var autoPlay = false
    var placeholder = "Add a task"
    /// What autoPlay types up to the date word.
    var script = "Send email to John on Wed"
    /// What autoPlay types after it picked the date, ending in the slash command.
    var scriptMore = " with a redesign proposal /f"
    var listLabel = "No list"
    var scheduleTitle = "Schedule"
    var attachTitle = "Attachment"
    var attachLabel = "File upload"
    /// Name of the file the upload row attaches.
    var fileName = "Redesign.doc"
    /// The day date words are counted from.
    var today = Date()
    var accent = Color(hex: 0x2F9BE0)
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the chosen day when a row of the schedule menu is tapped.
    var onSchedule: ((Date) -> Void)? = nil
    /// Called with the file's name when the upload row is tapped.
    var onAttach: ((String) -> Void)? = nil
    /// Called with the text when the return key is pressed; the field clears itself afterwards.
    var onSend: ((String) -> Void)? = nil
    var onCalendar: (() -> Void)? = nil
    var onList: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var inner = ""
    /// File chips, each sitting at a character offset of the text.
    @State private var files: [SmartTokenInputFile] = []
    @State private var nextFile = 0
    /// The date word that was confirmed, and its day.
    @State private var pick: SmartTokenInputPick? = nil
    /// Highlighted row of the schedule menu.
    @State private var row = 0
    /// The two days the schedule menu offers; kept while it fades out.
    @State private var days: [Date] = []
    @State private var playing = false
    @State private var caretOn = true
    /// The sentence is faded out, just before the field empties.
    @State private var wiped = false
    @State private var textHeight = Metrics.line
    /// How far the middle of the card's bottom edge hangs below its corners.
    @State private var bow = 0.0
    /// Where each piece of the sentence sits inside the component.
    @State private var origins: [String: CGPoint] = [:]
    @State private var scheduleAt = CGPoint(x: 0, y: Metrics.rest + Metrics.menuGap)
    @State private var attachAt = CGPoint(x: 0, y: Metrics.rest + Metrics.menuGap)

    private var value: String { text?.wrappedValue ?? inner }
    private var pieces: [SmartTokenInputPiece] { smartTokenInputPieces(value, files: files) }
    private var cardHeight: Double { textHeight + Metrics.rest - Metrics.line }

    /// The word the caret is in.
    private var open: SmartTokenInputPiece? {
        guard let last = pieces.last, last.file == nil, value.last?.isWhitespace == false else { return nil }
        return last
    }

    private var menu: SmartTokenInputMenu {
        guard let open else { return .none }
        if open.text.hasPrefix("/") { return .attach }
        if pick?.index != open.index, dayOffset(open.text) != nil { return .schedule }
        return .none
    }

    var body: some View {
        let pieces = self.pieces
        let menu = self.menu
        ZStack(alignment: .topLeading) {
            card(pieces, menu: menu)
            SmartTokenInputMenuView(
                title: scheduleTitle, rows: days.map(dayLabel), icon: calendarIcon, open: menu == .schedule,
                highlighted: row, still: reduceMotion
            ) { choose($0, byHand: true) }
                .offset(x: scheduleAt.x, y: scheduleAt.y)
            SmartTokenInputMenuView(
                title: attachTitle, rows: [attachLabel], icon: uploadIcon, open: menu == .attach,
                highlighted: 0, still: reduceMotion
            ) { _ in attach(byHand: true) }
                .offset(x: attachAt.x, y: attachAt.y)
        }
        .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        .coordinateSpace(.named(Metrics.space))
        .onAppear { playing = autoPlay && text == nil }
        .onChange(of: menu) { _, now in
            if now == .schedule, let open, let offset = dayOffset(open.text) {
                row = 0
                days = [0, 7].compactMap { Calendar.current.date(byAdding: .day, value: offset + $0, to: today) }
            }
            placeMenu()
        }
        .onChange(of: origins) { placeMenu() }
        .onChange(of: textHeight) { old, now in
            placeMenu()
            wobble(grew: now > old)
        }
        .onChange(of: focused) { _, now in
            // A touch takes the field over from the script.
            if now, playing {
                playing = false
                reset()
            }
        }
        .task(id: playing) { await play() }
        .task(id: focused) {
            // The script resumes once the field has been left empty for a while.
            guard autoPlay, text == nil, !focused, !playing else { return }
            try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
            if !Task.isCancelled, value.isEmpty, files.isEmpty { playing = true }
        }
        .task(id: "\(value)|\(files.count)|\(focused)|\(playing)") {
            // The caret stays solid while keys arrive and blinks once they stop.
            caretOn = true
            guard focused || playing else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.blink))
                if Task.isCancelled { return }
                caretOn.toggle()
            }
        }
    }

    private func card(_ pieces: [SmartTokenInputPiece], menu: SmartTokenInputMenu) -> some View {
        let shape = SmartTokenInputCard(bow: bow)
        return HStack(alignment: .top, spacing: 0) {
            RoundedRectangle(cornerRadius: 5, style: .circular)
                .fill(Color(hex: 0xEDEFF1))
                .frame(width: 18, height: 18)
                .padding(.top, 13)
            field(pieces, menu: menu)
                .padding(.leading, 10)
                .padding(.trailing, 6)
                .padding(.top, Metrics.textTop)
            calendarButton.padding(.top, 8)
            listButton.padding(.top, 8).padding(.leading, 5)
        }
        .padding(.leading, 12)
        .padding(.trailing, 6)
        .frame(width: Metrics.width, height: cardHeight, alignment: .top)
        .clipShape(shape)
        .background(shape.fill(.white).shadow(color: .black.opacity(0.08), radius: 10, y: 8))
        .animation(reduceMotion ? nil : Metrics.grow, value: cardHeight)
    }

    private func field(_ pieces: [SmartTokenInputPiece], menu: SmartTokenInputMenu) -> some View {
        let caret = focused || (playing && !pieces.isEmpty)
        let spaced = value.last?.isWhitespace == true || pieces.last?.file != nil
        return ZStack(alignment: .topLeading) {
            Text(placeholder)
                .font(Metrics.font)
                .foregroundStyle(Color(hex: 0xA3A8B0))
                .lineLimit(1)
                .frame(height: Metrics.line)
                .overlay(alignment: .leading) { caretMark(caret && pieces.isEmpty) }
                .opacity(pieces.isEmpty ? 1 : 0)
                .animation(pieces.isEmpty ? .easeOut(duration: 0.2) : nil, value: pieces.isEmpty)

            SmartTokenInputFlow(spacing: Metrics.gap, line: Metrics.line) {
                ForEach(pieces) { piece in
                    pieceView(piece, menu: menu)
                        .overlay(alignment: .trailing) {
                            if piece.id == pieces.last?.id {
                                // Caret and ghost completion hang off the last piece without taking room in the line.
                                Color.clear.frame(width: 0, height: Metrics.line).overlay(alignment: .leading) {
                                    HStack(spacing: 0) {
                                        caretMark(caret)
                                        Text(ghost).font(Metrics.font).foregroundStyle(Color(hex: 0xB4B9C0))
                                    }
                                    .fixedSize()
                                    .offset(x: spaced ? Metrics.gap : 0.5)
                                }
                            }
                        }
                        .onGeometryChange(for: CGPoint.self) { proxy in
                            proxy.frame(in: .named(Metrics.space)).origin
                        } action: { origin in
                            origins[piece.id] = origin
                        }
                }
            }
            .onGeometryChange(for: Double.self) { proxy in
                proxy.size.height
            } action: { height in
                textHeight = max(Metrics.line, height)
            }
            .opacity(wiped ? 0 : 1)

            // The real field: it takes the keyboard, the sentence above is drawn from its text.
            TextField("", text: Binding(get: { value }, set: { edit($0) }))
                .font(Metrics.font)
                .foregroundStyle(.clear)
                .tint(.clear)
                .autocorrectionDisabled()
                .submitLabel(.done)
                .focused($focused)
                .onSubmit { send() }
                .frame(height: Metrics.line)
                .accessibilityLabel(placeholder)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
    }

    @ViewBuilder
    private func pieceView(_ piece: SmartTokenInputPiece, menu: SmartTokenInputMenu) -> some View {
        if let file = piece.file {
            SmartTokenInputChip(name: file.name, still: reduceMotion)
        } else {
            let live = piece.id == open?.id && menu != .none
            let tinted = live || pick?.index == piece.index
            Text(piece.text)
                .font(Metrics.font)
                .foregroundStyle(tinted ? accent : Metrics.ink)
                .lineLimit(1)
                .fixedSize()
                .frame(height: Metrics.line)
        }
    }

    private func caretMark(_ visible: Bool) -> some View {
        RoundedRectangle(cornerRadius: 0.75)
            .fill(Metrics.ink)
            .frame(width: 1.5, height: 17)
            .opacity(visible && caretOn ? 1 : 0)
            .animation(.easeOut(duration: 0.08), value: caretOn)
    }

    private var calendarButton: some View {
        Button {
            onCalendar?()
        } label: {
            calendarIcon
                .stroke(Metrics.icon, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                .frame(width: 16, height: 16)
                .frame(width: 28, height: 28)
                .background(RoundedRectangle(cornerRadius: 7, style: .circular).fill(Metrics.chip))
                .overlay {
                    // The dot that says a day is set.
                    Circle()
                        .fill(accent)
                        .frame(width: 7, height: 7)
                        .overlay(Circle().stroke(.white, lineWidth: 1.5))
                        .scaleEffect(pick != nil || reduceMotion ? 1 : 0)
                        .opacity(pick != nil ? 1 : 0)
                        .animation(
                            reduceMotion ? .easeOut(duration: 0.15)
                                : pick != nil ? Metrics.dot.delay(Metrics.dotDelay) : .easeOut(duration: 0.15),
                            value: pick != nil
                        )
                        .offset(x: 11.5, y: -11.5)
                }
        }
        .buttonStyle(SmartTokenInputPress())
        .accessibilityLabel("Schedule")
    }

    private var listButton: some View {
        Button {
            onList?()
        } label: {
            HStack(spacing: 0) {
                Circle().stroke(Color(hex: 0xB4B9C0), lineWidth: 1.5).frame(width: 8.5, height: 8.5)
                Text(listLabel)
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(Metrics.ink)
                    .lineLimit(1)
                    .fixedSize()
                    .padding(.leading, 6)
                    .padding(.trailing, 5)
                chevronIcon
                    .stroke(Metrics.icon, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .frame(width: 10, height: 10)
            }
            .padding(.horizontal, 7)
            .frame(height: 28)
            .background(RoundedRectangle(cornerRadius: 8, style: .circular).fill(Metrics.chip))
        }
        .buttonStyle(SmartTokenInputPress())
    }

    /// The rest of the slash command, shown in grey after the caret.
    private var ghost: String {
        guard let word = open?.text.lowercased(), word.hasPrefix("/"), Metrics.command.hasPrefix(word) else { return "" }
        return String(Metrics.command.dropFirst(word.count))
    }

    /// Days from `today` to the day a word names, or nil when it is not a date word.
    private func dayOffset(_ word: String) -> Int? {
        let key = word.lowercased().trimmingCharacters(in: .punctuationCharacters)
        if key == "today" { return 0 }
        if key == "tomorrow" { return 1 }
        guard let target = dayNames.firstIndex(where: { key == $0.lowercased() || key == $0.prefix(3).lowercased() }) else {
            return nil
        }
        let now = Calendar.current.component(.weekday, from: today) - 1
        let ahead = (target - now + 7) % 7
        return ahead == 0 ? 7 : ahead
    }

    private func dayLabel(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.weekday, .month, .day], from: date)
        let day = dayNames[(parts.weekday ?? 1) - 1].prefix(3)
        let month = monthNames[(parts.month ?? 1) - 1]
        return "\(day), \(month) \(parts.day ?? 1)"
    }

    /// Hangs the open menu under the word the caret is in, kept inside the card's width.
    private func placeMenu() {
        guard let open, let origin = origins[open.id] else { return }
        let x = min(max(origin.x + Metrics.menuLead - Metrics.menuWidth / 2, 4), Metrics.width - Metrics.menuWidth - 4)
        let at = CGPoint(x: x, y: cardHeight + Metrics.menuGap)
        switch menu {
        case .schedule: scheduleAt = at
        case .attach: attachAt = at
        case .none: break
        }
    }

    /// The bottom edge bows out as the card gains a line, then rings back to straight.
    private func wobble(grew: Bool) {
        guard !reduceMotion else { return }
        withAnimation(.easeOut(duration: Metrics.bowIn)) {
            bow = grew ? Metrics.bow : -Metrics.bow / 2
        } completion: {
            withAnimation(Metrics.bowOut) { bow = 0 }
        }
    }

    private func edit(_ new: String) {
        guard new != value else { return }
        // A chip goes with the text in front of it.
        files.removeAll { $0.at > new.count }
        if let pick, smartTokenInputPieces(new, files: files).first(where: { $0.index == pick.index })?.text != pick.word {
            self.pick = nil
        }
        if let text { text.wrappedValue = new } else { inner = new }
        onChangeText?(new)
    }

    private func choose(_ chosen: Int, byHand: Bool) {
        guard menu == .schedule, let open, days.indices.contains(chosen) else { return }
        row = chosen
        pick = SmartTokenInputPick(index: open.index, word: open.text)
        if byHand { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        onSchedule?(days[chosen])
    }

    /// Swaps the slash command for a file chip.
    private func attach(byHand: Bool) {
        guard menu == .attach, let open else { return }
        let kept = String(value.prefix(open.start))
        files.append(SmartTokenInputFile(id: nextFile, at: kept.count, name: fileName))
        nextFile += 1
        edit(kept)
        if byHand { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        onAttach?(fileName)
    }

    private func send() {
        focused = false
        guard !pieces.isEmpty else { return }
        onSend?(value)
        wipe()
    }

    /// Fades the sentence out, then empties the field.
    private func wipe() {
        withAnimation(.easeOut(duration: Metrics.wipe)) {
            wiped = true
        } completion: {
            reset()
        }
    }

    private func reset() {
        files = []
        pick = nil
        wiped = false
        edit("")
    }

    private func play() async {
        guard playing else { return }
        let steps = smartTokenInputSteps(script, scriptMore)
        let period = max(Metrics.loop, (steps.last?.at ?? 0) + Metrics.typeAt)
        var origin = Date()
        while !Task.isCancelled {
            for step in steps {
                let wait = origin.addingTimeInterval(step.at).timeIntervalSinceNow
                if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
                if Task.isCancelled { return }
                switch step.act {
                case .type(let typed): edit(typed)
                case .row(let next): row = next
                case .date: choose(row, byHand: false)
                case .file: attach(byHand: false)
                case .clear: wipe()
                }
            }
            origin = origin.addingTimeInterval(period)
            let wait = origin.timeIntervalSinceNow
            if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    /// The box the component is laid out in: the card at its top, the menus under it.
    static let height = 200.0
    /// Height of the card with one line of text.
    static let rest = 44.0
    static let radius = 14.0
    static let line = 20.0
    static let textTop = 12.0
    /// Room between two words, and between the last one and the caret after a space.
    static let gap = 3.6
    static let font = Font.system(size: 13.5)

    static let menuWidth = 172.0
    static let menuGap = 8.0
    /// The menu is centred this far into the word it hangs under.
    static let menuLead = 12.0
    static let menuHeader = 28.0
    static let menuRow = 34.0

    static let ink = Color(hex: 0x1D1D1F)
    static let icon = Color(hex: 0x3A3F47)
    static let chip = Color(hex: 0xF1F3F5)
    static let command = "/file"
    static let space = "SmartTokenInput"

    // autoPlay (seconds). Keys land `key` apart on average, stretched by `rhythm` in turn.
    static let typeAt = 0.8
    static let key = 0.058
    static let rhythm = [1.0, 0.72, 1.3, 0.85, 1.12, 0.64, 1.42, 0.95]
    /// From the last key of the date word to the highlight moving down, then to the tap on that row.
    static let rowAfter = 0.75
    static let pickAfter = 0.65
    /// From the tap to the next key.
    static let moreAfter = 0.55
    /// From the last key of the slash command to the tap on the upload row.
    static let fileAfter = 0.8
    /// The chip loads, closes its ring and turns into a paperclip before the field clears.
    static let clearAfter = 2.2
    static let loop = 9.6
    /// After the field is left empty, the script waits this long before it starts again.
    static let resumeAfter = 3.0

    static let blink = 0.53
    static let wipe = 0.18
    static let chipLoad = 0.8
    static let chipClose = 0.3
    static let chipHold = 0.5
    static let spin = 0.8
    static let dotDelay = 0.12

    /// Depth of the bow in the card's bottom edge, how long it takes to form, and the spring that lets it go.
    static let bow = 5.0
    static let bowIn = 0.12
    static let bowOut = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 9)
    static let grow = Animation.interpolatingSpring(mass: 1, stiffness: 320, damping: 22)
    static let menuIn = Animation.interpolatingSpring(mass: 1, stiffness: 420, damping: 26)
    static let menuOut = 0.14
    static let rowMove = Animation.interpolatingSpring(mass: 1, stiffness: 480, damping: 34)
    static let dot = Animation.interpolatingSpring(mass: 1, stiffness: 520, damping: 15)
    static let chipIn = Animation.interpolatingSpring(mass: 1, stiffness: 420, damping: 20)
    static let clipIn = Animation.interpolatingSpring(mass: 1, stiffness: 480, damping: 18)
}

private let dayNames = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
private let monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

// Icons, as outlines in a 16 pt box (the chevron in 10 pt, the paperclip in 14 pt).
private let calendarIcon = smartTokenInputPath(
    "M5.25 2.75 L10.75 2.75 C12.13 2.75 13.25 3.87 13.25 5.25 L13.25 10.75 C13.25 12.13 12.13 13.25 10.75 13.25 "
        + "L5.25 13.25 C3.87 13.25 2.75 12.13 2.75 10.75 L2.75 5.25 C2.75 3.87 3.87 2.75 5.25 2.75 Z M2.75 6.5 L13.25 6.5"
)
private let chevronIcon = smartTokenInputPath("M2.5 3.75 L5 6.25 L7.5 3.75")
private let uploadIcon = smartTokenInputPath("M8 10.5 L8 3 M4.75 6.25 L8 3 L11.25 6.25 M3.25 13 L12.75 13")
private let clipIcon = smartTokenInputPath(
    "M11.03 7.49 L7.71 10.82 C6.46 12.07 4.43 12.07 3.18 10.82 C1.93 9.57 1.93 7.54 3.18 6.29 L6.93 2.55 "
        + "C7.75 1.73 9.08 1.73 9.9 2.55 C10.72 3.37 10.72 4.69 9.9 5.52 L6.29 9.12 C5.88 9.53 5.22 9.53 4.81 9.12 "
        + "C4.4 8.71 4.4 8.05 4.81 7.64 L7.78 4.67"
)

/// Builds a path from the part of SVG path data the icons use: absolute M, L, C and Z.
private func smartTokenInputPath(_ data: String) -> Path {
    var path = Path()
    let scanner = Scanner(string: data)
    var command: Character = "M"
    func point() -> CGPoint {
        CGPoint(x: scanner.scanDouble() ?? 0, y: scanner.scanDouble() ?? 0)
    }
    while !scanner.isAtEnd {
        if let letter = scanner.scanCharacters(from: .letters)?.first { command = letter }
        switch command {
        case "M":
            path.move(to: point())
            command = "L"
        case "L":
            path.addLine(to: point())
        case "C":
            let first = point()
            let second = point()
            path.addCurve(to: point(), control1: first, control2: second)
        default:
            path.closeSubpath()
        }
    }
    return path
}

private enum SmartTokenInputMenu {
    case none, schedule, attach
}

private struct SmartTokenInputFile: Equatable {
    let id: Int
    /// Character offset of the text the chip sits at.
    let at: Int
    let name: String
}

private struct SmartTokenInputPick {
    /// Which word of the text was confirmed, and what it said then.
    let index: Int
    let word: String
}

/// A word of the sentence, or a file chip between two of them.
private struct SmartTokenInputPiece: Identifiable {
    let id: String
    let text: String
    /// Position among the words; -1 for a chip.
    let index: Int
    /// Character offset of the word's first letter.
    let start: Int
    let file: SmartTokenInputFile?
}

private func smartTokenInputPieces(_ value: String, files: [SmartTokenInputFile]) -> [SmartTokenInputPiece] {
    var pieces: [SmartTokenInputPiece] = []
    var word = ""
    var start = 0
    var words = 0
    func close() {
        guard !word.isEmpty else { return }
        pieces.append(SmartTokenInputPiece(id: "w\(words)", text: word, index: words, start: start, file: nil))
        words += 1
        word = ""
    }
    func chips(at offset: Int) {
        for file in files where file.at == offset {
            close()
            pieces.append(SmartTokenInputPiece(id: "f\(file.id)", text: file.name, index: -1, start: offset, file: file))
        }
    }
    for (offset, character) in value.enumerated() {
        chips(at: offset)
        if character.isWhitespace {
            close()
        } else {
            if word.isEmpty { start = offset }
            word.append(character)
        }
    }
    chips(at: value.count)
    close()
    return pieces
}

private struct SmartTokenInputStep {
    enum Act {
        case type(String), row(Int), date, file, clear
    }

    let at: Double
    let act: Act
}

/// The autoPlay timeline: every key with the text it leaves behind, then the taps.
private func smartTokenInputSteps(_ script: String, _ more: String) -> [SmartTokenInputStep] {
    var steps: [SmartTokenInputStep] = []
    var at = Metrics.typeAt
    var typed = ""
    func type(_ text: String) {
        for character in text {
            typed.append(character)
            steps.append(SmartTokenInputStep(at: at, act: .type(typed)))
            at += Metrics.key * Metrics.rhythm[(typed.count - 1) % Metrics.rhythm.count]
        }
    }
    type(script)
    at += Metrics.rowAfter
    steps.append(SmartTokenInputStep(at: at, act: .row(1)))
    at += Metrics.pickAfter
    steps.append(SmartTokenInputStep(at: at, act: .date))
    at += Metrics.moreAfter
    type(more)
    at += Metrics.fileAfter
    steps.append(SmartTokenInputStep(at: at, act: .file))
    at += Metrics.clearAfter
    steps.append(SmartTokenInputStep(at: at, act: .clear))
    return steps
}

/// Lays words out like text: left to right, wrapping whole pieces, every line `line` tall.
private struct SmartTokenInputFlow: Layout {
    let spacing: Double
    let line: Double

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let last = origins(subviews, width: width).last
        return CGSize(width: width, height: last.map { $0.y + line } ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for (subview, origin) in zip(subviews, origins(subviews, width: bounds.width)) {
            subview.place(
                at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y + line / 2),
                anchor: .leading, proposal: .unspecified
            )
        }
    }

    private func origins(_ subviews: Subviews, width: Double) -> [CGPoint] {
        var x = 0.0
        var y = 0.0
        return subviews.map { subview in
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                x = 0
                y += line
            }
            defer { x += size.width + spacing }
            return CGPoint(x: x, y: y)
        }
    }
}

/// The card's outline: a rounded rectangle whose bottom edge can bow.
private struct SmartTokenInputCard: Shape {
    var bow: Double

    var animatableData: Double {
        get { bow }
        set { bow = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let r = Metrics.radius
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + r, y: rect.minY))
        path.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.minY), tangent2End: CGPoint(x: rect.maxX, y: rect.maxY), radius: r)
        path.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.maxY), tangent2End: CGPoint(x: rect.minX, y: rect.maxY), radius: r)
        // A curve through the bowed middle instead of a straight bottom edge.
        path.addQuadCurve(to: CGPoint(x: rect.minX + r, y: rect.maxY), control: CGPoint(x: rect.midX, y: rect.maxY + bow * 2))
        path.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.maxY), tangent2End: CGPoint(x: rect.minX, y: rect.minY), radius: r)
        path.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.minY), tangent2End: CGPoint(x: rect.maxX, y: rect.minY), radius: r)
        path.closeSubpath()
        return path
    }
}

/// A file in the sentence: a ring that spins while it loads, closes, and turns into a paperclip.
private struct SmartTokenInputChip: View {
    let name: String
    let still: Bool

    @State private var shown = false
    @State private var spinning = false
    /// 0 loading, 1 ring closed, 2 paperclip.
    @State private var phase = 0

    var body: some View {
        HStack(spacing: 4) {
            ZStack {
                Circle()
                    .trim(from: 0, to: phase == 0 ? 0.72 : 1)
                    .stroke(Color(hex: 0x8A9099), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                    .frame(width: 10.5, height: 10.5)
                    .rotationEffect(.degrees(spinning ? 360 : 0))
                    .scaleEffect(phase == 2 && !still ? 0.4 : 1)
                    .opacity(phase == 2 ? 0 : 1)
                clipIcon
                    .stroke(Color(hex: 0x6B717A), style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
                    .frame(width: 14, height: 14)
                    .scaleEffect(phase == 2 || still ? 1 : 0.4)
                    .opacity(phase == 2 ? 1 : 0)
            }
            .frame(width: 14, height: 14)
            Text(name)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color(hex: 0x555A62))
                .lineLimit(1)
                .fixedSize()
        }
        .padding(.leading, 5)
        .padding(.trailing, 7)
        .frame(height: Metrics.line)
        .background(RoundedRectangle(cornerRadius: 6, style: .circular).fill(Metrics.chip))
        .scaleEffect(shown || still ? 1 : 0.8)
        .opacity(shown ? 1 : 0)
        .task {
            withAnimation(still ? .easeOut(duration: 0.15) : Metrics.chipIn) { shown = true }
            if !still {
                withAnimation(.linear(duration: Metrics.spin).repeatForever(autoreverses: false)) { spinning = true }
            }
            try? await Task.sleep(for: .seconds(Metrics.chipLoad))
            withAnimation(.easeOut(duration: Metrics.chipClose)) { phase = 1 }
            try? await Task.sleep(for: .seconds(Metrics.chipHold))
            withAnimation(still ? .easeOut(duration: 0.15) : Metrics.clipIn) { phase = 2 }
        }
    }
}

/// A small menu hanging under the card: a grey heading and rows, one of them highlighted.
private struct SmartTokenInputMenuView: View {
    let title: String
    let rows: [String]
    let icon: Path
    let open: Bool
    let highlighted: Int
    let still: Bool
    let onPick: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color(hex: 0x9AA0A8))
                .lineLimit(1)
                .padding(.leading, 12)
                .padding(.top, 2)
                .frame(height: Metrics.menuHeader)
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 8, style: .circular)
                    .fill(Metrics.chip)
                    .frame(height: Metrics.menuRow)
                    .offset(y: Double(highlighted) * Metrics.menuRow)
                    .animation(still ? nil : Metrics.rowMove, value: highlighted)
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { index, label in
                        Button {
                            onPick(index)
                        } label: {
                            HStack(spacing: 9) {
                                icon
                                    .stroke(Metrics.icon, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                                    .frame(width: 16, height: 16)
                                Text(label)
                                    .font(.system(size: 13.5))
                                    .monospacedDigit()
                                    .foregroundStyle(Metrics.ink)
                                    .lineLimit(1)
                                Spacer(minLength: 0)
                            }
                            .padding(.leading, 9)
                            .frame(height: Metrics.menuRow)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(SmartTokenInputPress())
                    }
                }
            }
            .padding(.horizontal, 4)
        }
        .padding(.bottom, 4)
        .frame(width: Metrics.menuWidth, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .circular)
                .fill(.white)
                .shadow(color: .black.opacity(0.12), radius: 12, y: 8)
        )
        .scaleEffect(open || still ? 1 : 0.95, anchor: .top)
        .opacity(open ? 1 : 0)
        .animation(open && !still ? Metrics.menuIn : .easeOut(duration: Metrics.menuOut), value: open)
        .allowsHitTesting(open)
        .accessibilityHidden(!open)
    }
}

private struct SmartTokenInputPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

#Preview {
    ZStack {
        Color(hex: 0xCFD4D9).ignoresSafeArea()
        SmartTokenInput(autoPlay: true)
    }
}
