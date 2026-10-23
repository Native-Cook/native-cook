import SwiftUI
import UIKit

/// A prompt field with a command menu. Typing "/" boxes the character as a token and opens the menu
/// right under it; more letters filter the list, the rows that don't match folding away while a
/// highlight keeps to the first one that does. Choosing a context flies its name down into the chip
/// row and takes the token out of the text; choosing a skill leaves it in the text as a tinted token.
struct SlashCommandInput: View {
    /// The field's text. Leave it out and the component keeps the text itself.
    var text: Binding<String>? = nil
    /// Types a prompt, picks a context and a skill from the menu, clears and starts over, in a loop.
    var autoPlay = false
    var placeholder = "Ask, or type / for commands"
    /// The chips in the bottom row before anything is chosen.
    var contexts = ["Design"]
    var contextTitle = "CONTEXT"
    /// Commands that become a chip when chosen.
    var contextCommands = ["design-file", "from-brief", "from-code", "past-output"]
    var skillTitle = "SKILLS"
    /// Commands that stay in the text as a token when chosen.
    var skillCommands = ["contrast-check", "give-variants", "critique", "ally-check"]
    /// Colour of the chips' dots, the skill tokens and the caret.
    var accent = Color(hex: 0x4f8bff)
    /// Called with the command's name when a row is chosen.
    var onCommand: ((String) -> Void)? = nil
    /// Called with the text and the chips when the keyboard's send key is pressed.
    var onSend: ((String, [String]) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var sim = Simulation()
    @State private var own = ""
    @State private var chips: [String] = []

    /// Per-frame state: the menu, its rows and everything that eases after them.
    private final class Simulation {
        enum Event { case type(Character), choose, reset }

        var last: Date? = nil
        var time = 0.0
        var clock = 0.0
        /// The next line of the autoPlay script.
        var step = 0
        var looping = false
        /// Seconds the loop still waits after a touch.
        var hold = 0.0
        /// The text in the field is the loop's.
        var scripted = false
        var lastEdit = -10.0

        var open = false
        var openAt = 0.0
        /// Left edge of the menu; it stays where it was while the menu closes.
        var menuX = 0.0
        /// The highlighted command.
        var highlight = 0
        var clearing = false
        var bornAt = [Double](repeating: -10, count: Metrics.maxChips)
        var flyAt = -10.0
        var flyLabel = ""
        var flyFrom = CGPoint.zero
        var flyTo = CGPoint.zero

        var menu = Chase(0)
        var box = Chase(1)
        var tint = 1.0
        var fade = 1.0
        var rows = [Double](repeating: 1, count: Metrics.maxRows)
        var headers = [1.0, 1.0]
        var divider = 1.0
        var bar = Chase(0)
        var barAlpha = 0.0
        // Where the menu's parts are this frame, from its top edge.
        var rowY = [Double](repeating: 0, count: Metrics.maxRows)
        var headerY = [0.0, 0.0]
        var dividerY = 0.0
        var height = 0.0
        var events: [Event] = []

        func touch() {
            hold = Metrics.resume
        }

        func move(_ by: Int, matches: [Bool]) {
            let shown = matches.indices.filter { matches[$0] }
            guard let at = shown.firstIndex(of: highlight) else { return }
            highlight = shown[min(max(at + by, 0), shown.count - 1)]
        }

        func advance(to date: Date, engaged: Bool, open: Bool, anchor: Double, matches: [Bool], split: Int, still: Bool) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            time += dt

            if engaged { hold = Metrics.resume }
            if hold > 0 {
                hold -= dt
                if hold <= 0, looping {
                    clock = 0
                    step = 0
                    events.append(.reset)
                }
            } else if looping {
                clock += dt
                if clock >= Metrics.loop {
                    clock -= Metrics.loop
                    step = 0
                }
                while step < script.count, clock >= script[step].t {
                    switch script[step].act {
                    case .type(let letter): events.append(.type(letter))
                    case .move(let by): move(by, matches: matches)
                    case .choose: events.append(.choose)
                    case .clear: clearing = true
                    }
                    step += 1
                }
            }

            let first = matches.prefix(split).contains(true)
            let second = matches.dropFirst(split).contains(true)
            if open, !self.open {
                openAt = time
                box = Chase(still ? 1 : 0.5)
                highlight = matches.firstIndex(of: true) ?? 0
                // A menu that opens shows its list as it is; only what is typed afterwards folds rows away.
                for i in matches.indices { rows[i] = matches[i] ? 1 : 0 }
                headers = [first ? 1 : 0, second ? 1 : 0]
                divider = first && second ? 1 : 0
            }
            self.open = open
            if open { menuX = anchor }
            if open, !(matches.indices.contains(highlight) && matches[highlight]) {
                highlight = matches.firstIndex(of: true) ?? 0
            }
            if clearing, fade < 0.03 {
                clearing = false
                events.append(.reset)
            }

            if still {
                menu.x = approach(menu.x, open ? 1 : 0, 14, dt)
            } else {
                menu.step(to: open ? 1 : 0, omega: 20, zeta: 0.65, dt)
            }
            box.step(to: 1, omega: 26, zeta: 0.45, dt)
            tint = approach(tint, 1, 9, dt)
            fade = approach(fade, clearing ? 0 : 1, 12, dt)

            // While the menu is closed its rows stay as they were, so it closes in one piece.
            if open {
                for i in matches.indices {
                    rows[i] = approach(rows[i], matches[i] ? 1 : 0, 16, dt)
                }
                headers[0] = approach(headers[0], first ? 1 : 0, 16, dt)
                headers[1] = approach(headers[1], second ? 1 : 0, 16, dt)
                divider = approach(divider, first && second ? 1 : 0, 16, dt)
            }
            var y = Metrics.menuPad
            for i in matches.indices {
                if i == 0 || i == split {
                    if i == split {
                        dividerY = y
                        y += Metrics.dividerHeight * divider
                    }
                    headerY[i == 0 ? 0 : 1] = y
                    y += Metrics.headerHeight * headers[i == 0 ? 0 : 1]
                }
                rowY[i] = y
                y += Metrics.rowHeight * rows[i]
            }
            height = y + Metrics.menuPad

            let target = matches.indices.contains(highlight) ? rowY[highlight] : 0
            if !open || still || barAlpha < 0.05 {
                bar = Chase(target)
            } else {
                bar.step(to: target, omega: 24, zeta: 0.8, dt)
            }
            barAlpha = approach(barAlpha, open && (first || second) ? 1 : 0, 14, dt)
        }
    }

    private var current: String { text?.wrappedValue ?? own }
    private var commands: [String] { contextCommands + skillCommands }

    private var field: Binding<String> {
        Binding(
            get: { current },
            set: { new in
                guard new != current else { return }
                store(new)
                sim.scripted = false
                sim.lastEdit = sim.time
            }
        )
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            let date = timeline.date
            let parsed = parse(current)
            let query = String((parsed.token ?? "/").dropFirst()).lowercased()
            let matches = commands.prefix(Metrics.maxRows).map { query.isEmpty || $0.lowercased().contains(query) }
            let engaged = focused || (!sim.scripted && (!current.isEmpty || chips.count > contexts.count))
            let _ = sim.advance(
                to: date, engaged: engaged, open: parsed.token != nil, anchor: menuLeft(parsed.prefix), matches: matches,
                split: min(contextCommands.count, Metrics.maxRows), still: reduceMotion
            )
            let s = sim
            let prefix = measure(parsed.prefix)
            let sinceEdit = s.time - s.lastEdit
            let blink = sinceEdit < 0.5 ? 1 : min(1, max(0, 0.5 + 3 * sin(tau * sinceEdit / Metrics.blink)))

            ZStack(alignment: .topLeading) {
                // A faint blue light under the composer's right corner, as on the reference's backdrop.
                Circle()
                    .fill(RadialGradient(colors: [accent.opacity(0.15), accent.opacity(0)], center: .center, startRadius: 0, endRadius: 150))
                    .frame(width: 300, height: 300)
                    .scaleEffect(x: 1, y: 0.55)
                    .offset(x: 100, y: -20)
                    .allowsHitTesting(false)

                RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)
                    .fill(Color(hex: 0x1b1b1b))
                    .overlay(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular).strokeBorder(Color(hex: 0x2c2c2c), lineWidth: 1))
                    .frame(width: Metrics.width, height: Metrics.composerHeight)

                // The boxes behind the tokens: the one being typed, and the skills already chosen.
                if let token = parsed.token {
                    tokenBox(fill: Color(hex: 0x262626), line: Color(hex: 0x3a3a3a))
                        .frame(width: measure(token) + Metrics.tokenPad * 2, height: Metrics.tokenHeight)
                        .scaleEffect(s.box.x)
                        .opacity(min(1, max(0, s.box.x * 2 - 1)) * s.fade)
                        .offset(x: Metrics.inset + prefix - Metrics.tokenPad, y: Metrics.textY - Metrics.tokenHeight / 2)
                }
                ForEach(Array(parsed.skills.enumerated()), id: \.offset) { _, skill in
                    tokenBox(fill: accent.opacity(0.16), line: accent.opacity(0.5))
                        .frame(width: measure(skill.token) + Metrics.tokenPad * 2, height: Metrics.tokenHeight)
                        .opacity(s.tint * s.fade)
                        .offset(x: Metrics.inset + measure(skill.before) - Metrics.tokenPad, y: Metrics.textY - Metrics.tokenHeight / 2)
                }

                TextField("", text: field, prompt: Text(placeholder).foregroundStyle(Color(hex: 0x6f6f6f)))
                    .font(.system(size: Metrics.fontSize))
                    .foregroundStyle(Color(hex: 0xe6e6e6))
                    .tint(accent)
                    .autocorrectionDisabled()
                    .focused($focused)
                    .submitLabel(.send)
                    .onSubmit(submit)
                    .frame(width: Metrics.width - Metrics.inset * 2, height: 28)
                    .offset(x: Metrics.inset, y: Metrics.textY - 14)
                    .opacity(s.fade)

                // The loop's own caret, right after the last letter it typed.
                RoundedRectangle(cornerRadius: 1, style: .circular)
                    .fill(accent)
                    .frame(width: 2, height: 21)
                    .offset(x: Metrics.inset + measure(current) + 1, y: Metrics.textY - 10.5)
                    .opacity(s.scripted && !focused && !current.isEmpty ? blink * s.fade : 0)
                    .allowsHitTesting(false)

                HStack(spacing: Metrics.chipGap) {
                    ForEach(Array(chips.enumerated()), id: \.offset) { index, label in
                        let pop = reduceMotion ? 1 : settle(s.time - s.bornAt[index], omega: 18, zeta: 0.5)
                        Button {
                            sim.touch()
                            remove(index)
                        } label: {
                            chip(label)
                        }
                        .buttonStyle(.plain)
                        .scaleEffect(0.6 + 0.4 * pop)
                        .opacity((s.time >= s.bornAt[index] ? min(1, max(0, pop * 2)) : 0) * (index < contexts.count ? 1 : s.fade))
                    }
                }
                .fixedSize()
                .frame(width: Metrics.width - Metrics.chipInset * 2, height: Metrics.chipHeight, alignment: .leading)
                .clipped()
                .offset(x: Metrics.chipInset, y: Metrics.chipY)

                menu(s, matches: matches)
                    .offset(x: s.menuX, y: Metrics.menuY)

                // The chosen context's name, on its way from the menu to the chip row.
                Text(s.flyLabel)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .fixedSize()
                    .offset(flight(s))
                    // Under reduced motion nothing flies: the chip simply appears in the row.
                    .opacity(reduceMotion ? 0 : flightAlpha(s))
                    .allowsHitTesting(false)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
            // One piece for the parent: when the keyboard moves the layout, the layers move together.
            .geometryGroup()
            .onChange(of: date) { drain() }
        }
        .onAppear {
            chips = Array(contexts.prefix(Metrics.maxChips))
            sim.looping = autoPlay && !reduceMotion
        }
        .onChange(of: autoPlay) { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: reduceMotion) { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: focused) {
            // A tap on the field takes it over from the loop: what the loop typed and chose is cleared.
            if focused, sim.scripted { reset() }
        }
    }

    private func tokenBox(fill: Color, line: Color) -> some View {
        RoundedRectangle(cornerRadius: 6, style: .circular)
            .fill(fill)
            .overlay(RoundedRectangle(cornerRadius: 6, style: .circular).strokeBorder(line, lineWidth: 1))
            .allowsHitTesting(false)
    }

    private func chip(_ label: String) -> some View {
        HStack(spacing: 7) {
            Canvas { context, _ in
                context.fill(
                    Path(ellipseIn: CGRect(x: 0, y: 0, width: 20, height: 20)),
                    with: .linearGradient(
                        Gradient(colors: [Color(hex: 0x86b1ff), accent, Color(hex: 0x3566d6)]),
                        startPoint: CGPoint(x: 4, y: 0), endPoint: CGPoint(x: 16, y: 20)
                    )
                )
                var check = Path()
                check.move(to: CGPoint(x: 6, y: 10.4))
                check.addLine(to: CGPoint(x: 8.9, y: 13.2))
                check.addLine(to: CGPoint(x: 14.2, y: 7.2))
                context.stroke(check, with: .color(.white), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
            }
            .frame(width: 20, height: 20)
            Text(label)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
            Canvas { context, _ in
                var cross = Path()
                cross.move(to: CGPoint(x: 1.5, y: 1.5))
                cross.addLine(to: CGPoint(x: 7.5, y: 7.5))
                cross.move(to: CGPoint(x: 7.5, y: 1.5))
                cross.addLine(to: CGPoint(x: 1.5, y: 7.5))
                context.stroke(cross, with: .color(Color(hex: 0x9aa3b8)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
            }
            .frame(width: 9, height: 9)
            .padding(.leading, 3)
        }
        .padding(.leading, 7)
        .padding(.trailing, 12)
        .frame(height: Metrics.chipHeight)
        .background(Capsule().fill(Color(hex: 0x232a3a)))
        .overlay(Capsule().strokeBorder(Color(hex: 0x2f3a52), lineWidth: 1))
        .accessibilityLabel(Text(verbatim: "Remove \(label)"))
    }

    /// The command menu: two titled groups, a hairline between them, and the highlight behind one row.
    private func menu(_ s: Simulation, matches: [Bool]) -> some View {
        let shown = min(1, max(0, s.menu.x))
        let split = min(contextCommands.count, Metrics.maxRows)
        // Rows fade in one after another as the menu opens.
        let order = matches.indices.map { index in matches.prefix(index).filter { $0 }.count }
        let rows = matches.indices.map { index in
            MenuRow(
                id: index, name: commands[index], y: s.rowY[index], height: Metrics.rowHeight * s.rows[index],
                alpha: s.rows[index] * min(1, max(0, (s.time - s.openAt - Double(order[index]) * Metrics.rowStagger) / Metrics.rowFade))
            )
        }
        let shape = RoundedRectangle(cornerRadius: 16, style: .circular)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 8, style: .circular)
                .fill(.white.opacity(0.08))
                .frame(width: Metrics.menuWidth - 12, height: Metrics.rowHeight)
                .offset(x: 6, y: s.bar.x)
                .opacity(s.barAlpha)

            ForEach(Array([contextTitle, skillTitle].enumerated()), id: \.offset) { group, title in
                Text(title)
                    .font(.system(size: 11, weight: .medium))
                    .tracking(0.9)
                    .foregroundStyle(Color(hex: 0x777777))
                    .lineLimit(1)
                    .frame(width: Metrics.menuWidth - Metrics.rowInset * 2, height: Metrics.headerHeight, alignment: .leading)
                    .offset(x: Metrics.rowInset, y: s.headerY[group])
                    .opacity(s.headers[group] * s.headers[group])
            }
            Rectangle()
                .fill(Color(hex: 0x333333))
                .frame(width: Metrics.menuWidth, height: 1)
                .offset(y: s.dividerY + Metrics.dividerHeight / 2)
                .opacity(s.divider * s.divider)

            ForEach(rows) { row in
                Button {
                    sim.touch()
                    sim.highlight = row.id
                    choose(row.id, byUser: true)
                } label: {
                    Text(row.name)
                        .font(.system(size: 15))
                        .foregroundStyle(Color(hex: 0xededed))
                        .lineLimit(1)
                        .frame(width: Metrics.menuWidth - Metrics.rowInset * 2, height: Metrics.rowHeight, alignment: .leading)
                        .padding(.horizontal, Metrics.rowInset)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                // A row that folds away is cut off at its shrinking height, so it never runs into the next one.
                .frame(height: row.height, alignment: .top)
                .clipped()
                .offset(y: row.y)
                .opacity(row.alpha * row.alpha)
                .allowsHitTesting(matches[row.id] && s.open)
            }
        }
        .frame(width: Metrics.menuWidth, height: max(Metrics.menuPad * 2, s.height), alignment: .topLeading)
        .background(shape.fill(Color(hex: 0x1e1e1e)))
        .clipShape(shape)
        .overlay(shape.strokeBorder(Color(hex: 0x333333), lineWidth: 1))
        .shadow(color: .black.opacity(0.45), radius: 18, y: 12)
        // It grows out of the token above its top-left corner.
        .scaleEffect(reduceMotion ? 1 : 0.7 + 0.3 * s.menu.x, anchor: .topLeading)
        .opacity(min(1, shown * 1.6))
        .allowsHitTesting(s.open)
    }

    private struct MenuRow: Identifiable {
        let id: Int
        let name: String
        let y: Double
        let height: Double
        let alpha: Double
    }

    private func flight(_ s: Simulation) -> CGSize {
        let p = min(1, max(0, (s.time - s.flyAt) / Metrics.flyTime))
        // Sideways first, then down: the name swings out of the menu and drops into the row.
        let across = 1 - pow(1 - p, 3)
        let down = p * p * (3 - 2 * p)
        return CGSize(
            width: s.flyFrom.x + (s.flyTo.x - s.flyFrom.x) * across,
            height: s.flyFrom.y + (s.flyTo.y - s.flyFrom.y) * down - 9
        )
    }

    private func flightAlpha(_ s: Simulation) -> Double {
        let p = (s.time - s.flyAt) / Metrics.flyTime
        return p < 0 || p >= 1 ? 0 : min(1, (1 - p) / 0.25)
    }

    /// Left edge of the menu: it hangs from the token, but never leaves the composer.
    private func menuLeft(_ prefix: String) -> Double {
        min(Metrics.inset + measure(prefix) - 6, Metrics.width - Metrics.menuWidth - 8)
    }

    /// Width of a piece of the field's text, in points.
    private func measure(_ string: String) -> Double {
        (string as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: Metrics.fontSize)]).width
    }

    /// The token being typed (a word that starts with "/" at the end of the text) and the chosen skills.
    private func parse(_ string: String) -> (prefix: String, token: String?, skills: [(before: String, token: String)]) {
        var prefix = string
        var token: String? = nil
        var skills: [(before: String, token: String)] = []
        var before = ""
        let words = string.split(separator: " ", omittingEmptySubsequences: false)
        for (index, word) in words.enumerated() {
            if word.hasPrefix("/") {
                if index == words.count - 1 {
                    prefix = before
                    token = String(word)
                } else if skillCommands.contains(String(word.dropFirst())) {
                    skills.append((before, String(word)))
                }
            }
            before += word + " "
        }
        return (prefix, token, skills)
    }

    private func store(_ new: String) {
        if let text { text.wrappedValue = new } else { own = new }
    }

    private func choose(_ index: Int, byUser: Bool) {
        let parsed = parse(current)
        guard parsed.token != nil, commands.indices.contains(index) else { return }
        let name = commands[index]
        if index < contextCommands.count {
            // The row holds what fits: the oldest chosen context makes room for a new one.
            var kept = chips
            while kept.count > contexts.count, kept.reduce(chipWidth(name), { $0 + chipWidth($1) + Metrics.chipGap }) > Metrics.width - Metrics.chipInset * 2 {
                kept.remove(at: contexts.count)
                sim.bornAt.remove(at: contexts.count)
                sim.bornAt.append(-10)
            }
            guard kept.count < Metrics.maxChips else { return }
            store(parsed.prefix)
            // The name lands about where its chip will be; the chip pops in under it.
            let slot = kept.reduce(Metrics.chipInset) { $0 + chipWidth($1) + Metrics.chipGap }
            sim.flyLabel = name
            sim.flyFrom = CGPoint(x: sim.menuX + Metrics.rowInset, y: Metrics.menuY + sim.rowY[index] + Metrics.rowHeight / 2)
            sim.flyTo = CGPoint(x: slot + 34, y: Metrics.chipY + Metrics.chipHeight / 2)
            sim.flyAt = sim.time
            sim.bornAt[kept.count] = sim.time + (reduceMotion ? 0 : Metrics.flyTime * 0.75)
            chips = kept + [name]
        } else {
            store(parsed.prefix + "/" + name + " ")
            sim.tint = 0
        }
        sim.lastEdit = sim.time
        if byUser {
            sim.scripted = false
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            onCommand?(name)
        }
    }

    private func remove(_ index: Int) {
        guard chips.indices.contains(index) else { return }
        chips.remove(at: index)
        sim.bornAt.remove(at: index)
        sim.bornAt.append(-10)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    /// The send key picks the highlighted row while the menu is open, and sends the prompt otherwise.
    private func submit() {
        if sim.open {
            choose(sim.highlight, byUser: true)
            focused = true
            return
        }
        let sent = current.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !sent.isEmpty else { return }
        onSend?(sent, chips)
        sim.clearing = true
    }

    private func reset() {
        store("")
        chips = Array(contexts.prefix(Metrics.maxChips))
        sim.scripted = false
        sim.clearing = false
        sim.bornAt = [Double](repeating: -10, count: Metrics.maxChips)
    }

    /// Applies what the last frame decided: state can't change while the frame is being drawn.
    private func drain() {
        guard !sim.events.isEmpty else { return }
        let events = sim.events
        sim.events.removeAll()
        for event in events {
            switch event {
            case .type(let letter):
                store(current + String(letter))
                sim.scripted = true
                sim.lastEdit = sim.time
            case .choose:
                choose(sim.highlight, byUser: false)
            case .reset:
                reset()
            }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    /// The composer, and the whole box with the menu open under it.
    static let composerHeight = 116.0
    static let height = 392.0
    static let radius = 28.0
    static let inset = 22.0
    static let fontSize = 18.0
    /// Centre line of the text row.
    static let textY = 35.0
    static let tokenHeight = 27.0
    /// A token's box is this much wider than its letters on each side.
    static let tokenPad = 5.0

    static let maxChips = 3
    static let chipInset = 18.0
    static let chipY = 66.0
    static let chipHeight = 34.0
    static let chipGap = 8.0

    static let maxRows = 12
    static let menuY = 56.0
    static let menuWidth = 196.0
    static let menuPad = 8.0
    static let rowHeight = 30.0
    static let rowInset = 18.0
    static let headerHeight = 26.0
    /// The hairline between the groups, with the space above and below it.
    static let dividerHeight = 13.0
    // Rows fade in this long, each this much after the one before, when the menu opens.
    static let rowFade = 0.14
    static let rowStagger = 0.03

    /// How long a chosen context's name takes to reach the chip row.
    static let flyTime = 0.42
    static let blink = 1.1

    /// autoPlay period, seconds.
    static let loop = 9.6
    /// After a touch, or once the field is left empty, the loop waits this long before it starts over.
    static let resume = 3.0
}

private enum ScriptAct {
    case type(Character), move(Int), choose, clear
}

/// Seconds between letters as the loop types: uneven like a hand, the same every time.
private let typingRhythm = [0.09, 0.07, 0.11, 0.06, 0.13, 0.08, 0.06, 0.1, 0.07, 0.12]

/// The autoPlay loop: type up to the slash, walk the highlight down, filter the list and step back
/// up to a context and pick it, then type a second slash, filter to a skill and pick that, and clear.
private let script: [(t: Double, act: ScriptAct)] = {
    func typing(_ string: String, at start: Double) -> [(t: Double, act: ScriptAct)] {
        var t = start
        return string.enumerated().map { index, letter in
            defer { t += typingRhythm[index % typingRhythm.count] + (letter == " " ? 0.08 : 0) }
            return (t, .type(letter))
        }
    }
    var steps = typing("I need help with /", at: 0.8)
    steps.append((3.0, .move(1)))
    steps.append((3.25, .move(1)))
    steps += typing("de", at: 3.7)
    steps.append((4.25, .move(-1)))
    steps.append((4.7, .choose))
    steps += typing("/cr", at: 5.5)
    steps.append((6.6, .choose))
    steps.append((8.3, .clear))
    return steps
}()

/// A value that follows its target like a spring (mass 1, stiffness omega², damping 2·zeta·omega).
private struct Chase {
    var x: Double
    var v = 0.0

    init(_ x: Double) {
        self.x = x
    }

    mutating func step(to target: Double, omega: Double, zeta: Double, _ dt: Double) {
        v += (omega * omega * (target - x) - 2 * zeta * omega * v) * dt
        x += v * dt
    }
}

/// Moves `x` towards `target`, covering the same share of the distance in the same time at any frame rate.
private func approach(_ x: Double, _ target: Double, _ rate: Double, _ dt: Double) -> Double {
    x + (target - x) * (1 - exp(-rate * dt))
}

/// How far a spring released `t` seconds ago has come from 0 to 1, overshoot included.
private func settle(_ t: Double, omega: Double, zeta: Double) -> Double {
    guard t > 0 else { return 0 }
    let damped = omega * (1 - zeta * zeta).squareRoot()
    return 1 - exp(-zeta * omega * t) * (cos(damped * t) + zeta * omega / damped * sin(damped * t))
}

/// About how wide a chip is for its label: enough to aim the flying name at the next free place.
private func chipWidth(_ label: String) -> Double {
    58 + 8.3 * Double(label.count)
}

#Preview {
    ZStack {
        Color(hex: 0x141414).ignoresSafeArea()
        SlashCommandInput(autoPlay: true)
    }
}
