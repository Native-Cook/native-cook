import SwiftUI

/// A search pill drawn as a partial rainbow outline, with colours that slide along it. A tap snaps it
/// open into an input card whose border carries a coloured streak round and round. On send the card
/// folds flat into a line of light that bows and fades, and the outline is drawn again from the right.
struct RainbowOutlineSearch: View {
    /// Controlled text. Leave it nil and the component keeps its own.
    var text: Binding<String>? = nil
    /// Opens the pill, types the script, sends it and starts over.
    var autoPlay = false
    var placeholder = "Ask anything"
    /// The question autoPlay types.
    var script = "Best hikes near Lisbon in May"
    /// Fill of the send button.
    var sendColor = Color(hex: 0x2F5FD0)
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the question when the arrow or the return key is pressed.
    var onSend: ((String) -> Void)? = nil
    var onOpen: (() -> Void)? = nil
    var onClose: (() -> Void)? = nil
    var onImage: (() -> Void)? = nil
    var onLens: (() -> Void)? = nil
    var onMic: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var inner = ""
    @State private var playing = false
    @State private var caretOn = true
    /// The arrow is held down by the script.
    @State private var pressed = false
    @State private var opened = false
    /// The card is folding or the pill is being drawn: taps wait.
    @State private var busy = false
    /// Counts closings, to start and cancel what follows one.
    @State private var run = 0
    /// 0 pill, 1 card.
    @State private var open = Tween(0)
    /// 0 card, 1 flat line.
    @State private var fold = Tween(0)
    /// How far the line has bowed and faded.
    @State private var gone = Tween(0)
    /// How much of the pill's outline is drawn.
    @State private var draw = Tween(1)
    @State private var started = Date()

    private var value: String { text?.wrappedValue ?? inner }
    private var ready: Bool { !value.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        TimelineView(.animation) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(started)
            let o = open.value(at: date)
            let f = fold.value(at: date)
            // Under reduced motion the shape jumps half way through a fade instead of morphing.
            let shape = reduceMotion ? (o < 0.5 ? 0.0 : 1.0) : o
            let veil = reduceMotion ? abs(2 * o - 1) : 1.0
            let drawn = draw.value(at: date)
            let content = clamp01((shape - 0.55) / 0.45) * clamp01(1 - f * 4) * veil
            let icon = clamp01(1 - shape * 3) * clamp01((drawn - 0.6) / 0.4) * veil

            ZStack {
                Canvas { context, _ in
                    paint(context, time: time, box: outlineBox(shape, f), o: shape, f: f, gone: gone.value(at: date), drawn: drawn, veil: veil)
                }
                .allowsHitTesting(false)

                searchIcon
                    .scaleEffect(0.8 + 0.2 * icon)
                    .opacity(icon)
                    .position(x: (Metrics.width - Metrics.pillWidth) / 2 + Metrics.iconX, y: Metrics.height / 2)
                    .allowsHitTesting(false)

                Color.clear
                    .frame(width: Metrics.pillWidth, height: Metrics.pillHeight)
                    .contentShape(Capsule(style: .circular))
                    .onTapGesture {
                        // A touch takes over from the script.
                        playing = false
                        openUp(byHand: true)
                    }
                    .allowsHitTesting(!opened && !busy)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel("Search")

                card
                    .frame(width: Metrics.cardWidth, height: Metrics.cardHeight)
                    .scaleEffect(0.94 + 0.06 * clamp01(shape))
                    .opacity(content)
                    .allowsHitTesting(opened && !busy)
                    .accessibilityHidden(!opened)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .onAppear { playing = autoPlay && text == nil }
        .onChange(of: focused) { _, now in
            // A touch takes the field over from the script.
            if now, playing {
                playing = false
                edit("")
            }
        }
        .task(id: playing) { await play() }
        .task(id: run) { await afterClose() }
        .task(id: "\(focused)|\(opened)|\(busy)|\(playing)") {
            // Left empty, the card closes; closed, the script resumes after a while.
            guard autoPlay, text == nil, !playing, !focused, !busy else { return }
            try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
            guard !Task.isCancelled, value.isEmpty else { return }
            if opened { close(sending: false, byHand: false) } else { playing = true }
        }
        .task(id: "\(value)|\(playing)|\(opened)") {
            // The caret stays solid while keys arrive and blinks once they stop.
            caretOn = true
            guard playing, opened else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.blink))
                if Task.isCancelled { return }
                caretOn.toggle()
            }
        }
    }

    /// What the open card holds: the field, a close button, and the tools along the bottom.
    private var card: some View {
        ZStack(alignment: .topLeading) {
            field
                .frame(width: Metrics.cardWidth - Metrics.textX - 56, height: 24, alignment: .leading)
                .offset(x: Metrics.textX, y: 16)
            tool(closeIcon, tint: Color(hex: 0x5F6368), label: "Close") { close(sending: false, byHand: true) }
                .position(x: Metrics.cardWidth - 28, y: 28)
            tool(imageIcon, tint: Metrics.ink, label: "Add an image") { onImage?() }
                .position(x: 32, y: Metrics.cardHeight - Metrics.toolY)
            tool(lensIcon, tint: Metrics.ink, label: "Search with the camera") { onLens?() }
                .position(x: 70, y: Metrics.cardHeight - Metrics.toolY)
            tool(micIcon, tint: Metrics.ink, label: "Dictate") { onMic?() }
                .position(x: Metrics.cardWidth - 88, y: Metrics.cardHeight - Metrics.toolY)
            Button {
                close(sending: true, byHand: true)
            } label: {
                arrowIcon
                    .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    .frame(width: 20, height: 20)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(sendColor))
                    .scaleEffect(pressed ? 0.88 : 1)
                    .animation(.easeOut(duration: Metrics.press), value: pressed)
            }
            .buttonStyle(RainbowOutlineSearchPress())
            .accessibilityLabel("Send")
            .position(x: Metrics.cardWidth - 36, y: Metrics.cardHeight - Metrics.toolY)
        }
    }

    private var field: some View {
        ZStack(alignment: .leading) {
            Text(placeholder)
                .font(Metrics.font)
                .foregroundStyle(Color(hex: 0x9AA0A6))
                .lineLimit(1)
                .opacity(value.isEmpty ? 1 : 0)
            // autoPlay's caret hangs off an invisible copy of the text; a focused field shows its own.
            Text(value.replacingOccurrences(of: " ", with: "\u{00A0}"))
                .font(Metrics.font)
                .lineLimit(1)
                .fixedSize()
                .hidden()
                .frame(height: 24)
                .overlay(alignment: .trailing) {
                    Color.clear.frame(width: 0, height: 24).overlay(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 1)
                            .fill(Metrics.caret)
                            .frame(width: 2, height: 20)
                            .opacity(playing && opened && !focused && caretOn ? 1 : 0)
                            .animation(.easeOut(duration: 0.08), value: caretOn)
                            // Before the placeholder's first letter, not on it.
                            .offset(x: value.isEmpty ? -2 : 1)
                    }
                }
            TextField("", text: Binding(get: { value }, set: { edit($0) }))
                .font(Metrics.font)
                .foregroundStyle(Metrics.ink)
                .tint(Metrics.caret)
                .submitLabel(.search)
                .focused($focused)
                .onSubmit { close(sending: true, byHand: true) }
                .accessibilityLabel(placeholder)
        }
    }

    private func tool(_ icon: Path, tint: Color, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            icon
                .stroke(tint, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                .frame(width: 20, height: 20)
                .frame(width: 36, height: 36)
                .contentShape(Rectangle())
        }
        .buttonStyle(RainbowOutlineSearchPress())
        .accessibilityLabel(label)
    }

    /// A magnifying glass open at the top right, where a four-point sparkle sits.
    private var searchIcon: some View {
        ZStack {
            glassIcon.stroke(Metrics.ink, style: StrokeStyle(lineWidth: 2.6, lineCap: .round))
            sparkleIcon.fill(Metrics.ink)
        }
        .frame(width: 28, height: 28)
    }

    private func edit(_ new: String) {
        guard new != value else { return }
        if let text { text.wrappedValue = new } else { inner = new }
        onChangeText?(new)
    }

    private func openUp(byHand: Bool) {
        guard !opened, !busy else { return }
        opened = true
        open.retarget(1, duration: Metrics.openTime, curve: reduceMotion ? Ease.out : Metrics.snap)
        onOpen?()
        if byHand {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            focused = true
        }
    }

    /// Folds the card away, after sending what it holds or because it was dismissed.
    private func close(sending: Bool, byHand: Bool) {
        if byHand {
            // A touch on the card's buttons takes over from the script as well.
            playing = false
            pressed = false
        }
        guard opened, !busy, !sending || ready else { return }
        if sending { onSend?(value.trimmingCharacters(in: .whitespaces)) } else { onClose?() }
        if byHand { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        focused = false
        pressed = false
        busy = true
        run += 1
    }

    /// The way back to the pill: fold flat, bow and fade, then draw the outline again.
    private func afterClose() async {
        guard busy else { return }
        if reduceMotion {
            open.retarget(0, duration: Metrics.openTime, curve: Ease.out)
            try? await Task.sleep(for: .seconds(Metrics.openTime))
            edit("")
        } else {
            fold.retarget(1, duration: Metrics.foldTime, curve: .easeInOut)
            try? await Task.sleep(for: .seconds(Metrics.foldTime))
            edit("")
            gone.retarget(1, duration: Metrics.goneTime, curve: Ease.out)
            try? await Task.sleep(for: .seconds(Metrics.goneTime + Metrics.drawAfter))
            // Nothing is on screen now: back to the pill, not yet drawn.
            open = Tween(0)
            fold = Tween(0)
            gone = Tween(0)
            draw = Tween(0)
            draw.retarget(1, duration: Metrics.drawTime, curve: Metrics.sweep)
            try? await Task.sleep(for: .seconds(Metrics.drawTime))
        }
        opened = false
        busy = false
    }

    private func play() async {
        guard playing else { return }
        var origin = Date()
        func wait(until at: Double) async {
            let wait = origin.addingTimeInterval(at).timeIntervalSinceNow
            if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
        }
        while !Task.isCancelled {
            // The colours and the streak run on the loop's clock, so every loop shows the same frames.
            started = origin
            await wait(until: Metrics.openAt)
            if Task.isCancelled { return }
            openUp(byHand: false)
            var at = Metrics.openAt + Metrics.typeAfter
            var typed = ""
            for character in script {
                await wait(until: at)
                if Task.isCancelled { return }
                typed.append(character)
                edit(typed)
                at += Metrics.key * Metrics.rhythm[(typed.count - 1) % Metrics.rhythm.count]
            }
            at += Metrics.sendAfter
            await wait(until: at - Metrics.press)
            if Task.isCancelled { return }
            pressed = true
            await wait(until: at)
            if Task.isCancelled { return }
            close(sending: true, byHand: false)
            let back = Metrics.foldTime + Metrics.goneTime + Metrics.drawAfter + Metrics.drawTime
            origin = origin.addingTimeInterval(max(Metrics.loop, at + back + Metrics.openAt))
            await wait(until: 0)
        }
    }

    /// The outline and everything made of light: shadow and fill of the card, the folded line, the two ribbons.
    private func paint(
        _ context: GraphicsContext, time: Double, box: RainbowOutlineSearchBox,
        o: Double, f: Double, gone: Double, drawn: Double, veil: Double
    ) {
        let centre = CGPoint(x: Metrics.width / 2, y: Metrics.height / 2)
        let rect = CGRect(x: centre.x - box.w / 2, y: centre.y - box.h / 2, width: box.w, height: box.h)
        let outline = Path(roundedRect: rect, cornerRadius: box.r, style: .circular)
        let card = clamp01(o) * veil
        let folded = clamp01(f)

        if card > 0, gone <= 0 {
            var shadow = context
            shadow.addFilter(.blur(radius: Metrics.shadowBlur))
            shadow.fill(outline.offsetBy(dx: 0, dy: Metrics.shadowDrop), with: .color(.black.opacity(0.1 * card * (1 - folded))))
            // The white card takes the colour of the line it folds into.
            context.fill(outline, with: .color(color(mix(white, blue, folded), card)))
            context.stroke(outline, with: .color(color(pale, card * (1 - folded))), lineWidth: 1.5)
        }

        // The folded card is a line of light: it glows, bows upwards and fades.
        if folded > 0 {
            var line = Path()
            line.move(to: CGPoint(x: rect.minX, y: centre.y))
            line.addQuadCurve(
                to: CGPoint(x: rect.maxX, y: centre.y),
                control: CGPoint(x: centre.x, y: centre.y - Metrics.lineBow * 2 * gone)
            )
            var glow = context
            glow.addFilter(.blur(radius: Metrics.glowBlur))
            glow.stroke(
                line, with: .color(color(blue, 0.6 * folded * (1 - gone) * veil)),
                style: StrokeStyle(lineWidth: Metrics.glowWidth, lineCap: .round)
            )
            if gone > 0 {
                context.stroke(
                    line, with: .color(color(blue, (1 - gone) * veil)),
                    style: StrokeStyle(lineWidth: Metrics.lineHeight, lineCap: .round)
                )
            }
        }

        // The pill: a ribbon over the top left, round the left cap and along the bottom, fading to the right.
        let closed = (1 - clamp01(o * 2.5)) * veil
        if closed > 0, drawn > 0 {
            // It is drawn from its right end; `front` is how far the drawing has come.
            let front = 1 - drawn * 1.1
            let lead = min(1, (1 - drawn) * 4)
            ribbon(context, box, centre, from: Metrics.ribbonFrom, to: Metrics.ribbonTo, pieces: Metrics.ribbonPieces) { q in
                let rest = smooth(0, 0.14, q) * (1 - smooth(0.45, 1, q))
                // While it is drawn, a bright head runs ahead of the faded ribbon it leaves behind.
                let head = lead * (1 - smooth(0, 0.3, q - front))
                return (ramp(q * Metrics.rampSpan - time / Metrics.loop), max(rest, head) * clamp01((q - front) / 0.1) * closed)
            }
        }

        // The card: a green, yellow and red streak travelling round the pale border.
        let streak = card * (1 - folded)
        if streak > 0 {
            let from = Metrics.streakFrom - Metrics.streakLaps * time / Metrics.loop
            ribbon(context, box, centre, from: from, to: from + Metrics.streakLength, pieces: Metrics.streakPieces) { q in
                let tone = q < 0.5 ? mix(green, yellow, q * 2) : mix(yellow, red, q * 2 - 1)
                return (tone, smooth(0, 0.3, q) * (1 - smooth(0.7, 1, q)) * streak)
            }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 170.0

    // The three shapes the outline takes, all centred in the box.
    static let pillWidth = 330.0
    static let pillHeight = 64.0
    static let cardWidth = 330.0
    static let cardHeight = 116.0
    static let cardRadius = 24.0
    static let lineWidth = 290.0
    static let lineHeight = 2.5
    /// How far the middle of the line rises as it fades.
    static let lineBow = 9.0
    static let glowWidth = 8.0
    static let glowBlur = 6.0
    static let shadowBlur = 14.0
    static let shadowDrop = 8.0
    static let stroke = 2.5

    /// Centre of the search icon from the pill's left end.
    static let iconX = 44.0
    static let textX = 20.0
    /// Centre of the tools from the card's bottom edge.
    static let toolY = 34.0
    static let font = Font.system(size: 16)
    static let ink = Color(hex: 0x1F1F1F)
    static let caret = Color(hex: 0x4285F4)

    // Positions on the outline run 0…1 from the right end of the top edge, leftwards, down the left
    // side, back along the bottom and up the right side.
    static let ribbonFrom = 0.27
    static let ribbonTo = 0.86
    static let ribbonPieces = 72
    /// How much of the colour wheel the ribbon shows at once; the wheel turns once per loop.
    static let rampSpan = 0.9
    static let streakFrom = 0.62
    static let streakLength = 0.24
    static let streakPieces = 30
    /// Whole laps of the streak per loop, so the loop closes.
    static let streakLaps = 4.0

    // autoPlay (seconds). Keys land `key` apart on average, stretched by `rhythm` in turn.
    static let openAt = 0.8
    /// From the tap on the pill to the first key.
    static let typeAfter = 0.75
    static let key = 0.052
    static let rhythm = [1.0, 0.72, 1.3, 0.85, 1.12, 0.64, 1.42, 0.95]
    /// From the last key to the tap on the arrow.
    static let sendAfter = 0.85
    static let loop = 7.6
    /// After the field is left empty, the card closes and the script waits this long.
    static let resumeAfter = 3.0

    static let blink = 0.53
    static let press = 0.12
    static let openTime = 0.45
    static let foldTime = 0.36
    static let goneTime = 0.55
    /// From the line fading out to the outline starting to draw.
    static let drawAfter = 0.15
    static let drawTime = 0.95
    /// Opens past its size and settles back.
    static let snap = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.34, y: 1.4), endControlPoint: UnitPoint(x: 0.64, y: 1))
    static let sweep = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.3, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))
}

private typealias Tone = (r: Double, g: Double, b: Double)

private let green: Tone = (52, 168, 83)
private let yellow: Tone = (251, 188, 4)
private let red: Tone = (234, 67, 53)
private let violet: Tone = (161, 66, 244)
private let blue: Tone = (66, 133, 244)
private let white: Tone = (255, 255, 255)
/// The card's resting border.
private let pale: Tone = (201, 215, 245)
private let wheel = [green, yellow, red, violet, blue]

private func mix(_ from: Tone, _ to: Tone, _ t: Double) -> Tone {
    (from.r + (to.r - from.r) * t, from.g + (to.g - from.g) * t, from.b + (to.b - from.b) * t)
}

private func color(_ tone: Tone, _ alpha: Double) -> Color {
    Color.rgba(tone.r, tone.g, tone.b, alpha)
}

/// The colour wheel at `x` turns: green, yellow, red, violet, blue and round to green again.
private func ramp(_ x: Double) -> Tone {
    let at = (x - x.rounded(.down)) * Double(wheel.count)
    let index = min(wheel.count - 1, Int(at))
    return mix(wheel[index], wheel[(index + 1) % wheel.count], at - Double(index))
}

private func clamp01(_ t: Double) -> Double {
    min(1, max(0, t))
}

private func smooth(_ from: Double, _ to: Double, _ x: Double) -> Double {
    let t = clamp01((x - from) / (to - from))
    return t * t * (3 - 2 * t)
}

private func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double {
    from + (to - from) * t
}

private struct RainbowOutlineSearchBox {
    let w: Double
    let h: Double
    let r: Double
}

/// The outline `o` of the way from pill to card and `f` of the way from there to the flat line.
private func outlineBox(_ o: Double, _ f: Double) -> RainbowOutlineSearchBox {
    let w = lerp(lerp(Metrics.pillWidth, Metrics.cardWidth, o), Metrics.lineWidth, f)
    let h = lerp(lerp(Metrics.pillHeight, Metrics.cardHeight, o), Metrics.lineHeight, f)
    let r = lerp(lerp(Metrics.pillHeight / 2, Metrics.cardRadius, o), Metrics.lineHeight / 2, f)
    return RainbowOutlineSearchBox(w: w, h: h, r: max(0.01, min(r, h / 2, w / 2)))
}

/// The point `u` (in turns) of the way round a rounded rectangle centred on `centre`.
private func outlinePoint(_ u: Double, _ box: RainbowOutlineSearchBox, _ centre: CGPoint) -> CGPoint {
    let r = box.r
    let across = max(0, box.w - 2 * r)
    let down = max(0, box.h - 2 * r)
    let corner = Double.pi / 2 * r
    let left = centre.x - box.w / 2
    let right = centre.x + box.w / 2
    let top = centre.y - box.h / 2
    let bottom = centre.y + box.h / 2
    var s = (u - u.rounded(.down)) * (2 * across + 2 * down + 4 * corner)
    if s < across { return CGPoint(x: right - r - s, y: top) }
    s -= across
    if s < corner { return CGPoint(x: left + r - r * sin(s / r), y: top + r - r * cos(s / r)) }
    s -= corner
    if s < down { return CGPoint(x: left, y: top + r + s) }
    s -= down
    if s < corner { return CGPoint(x: left + r - r * cos(s / r), y: bottom - r + r * sin(s / r)) }
    s -= corner
    if s < across { return CGPoint(x: left + r + s, y: bottom) }
    s -= across
    if s < corner { return CGPoint(x: right - r + r * sin(s / r), y: bottom - r + r * cos(s / r)) }
    s -= corner
    if s < down { return CGPoint(x: right, y: bottom - r - s) }
    s -= down
    return CGPoint(x: right - r + r * cos(s / r), y: top + r - r * sin(s / r))
}

/// Strokes the outline between two positions in short pieces, each in the colour and opacity `tone` gives for its place (0…1) along the stretch.
private func ribbon(
    _ context: GraphicsContext, _ box: RainbowOutlineSearchBox, _ centre: CGPoint,
    from: Double, to: Double, pieces: Int, tone: (Double) -> (Tone, Double)
) {
    let step = (to - from) / Double(pieces)
    for index in 0..<pieces {
        let (tint, alpha) = tone((Double(index) + 0.5) / Double(pieces))
        guard alpha > 0.004 else { continue }
        var piece = Path()
        piece.move(to: outlinePoint(from + step * Double(index), box, centre))
        // A hair longer than its share, so no seam shows between two pieces.
        piece.addLine(to: outlinePoint(from + step * (Double(index) + 1.08), box, centre))
        context.stroke(piece, with: .color(color(tint, alpha)), lineWidth: Metrics.stroke)
    }
}

// Icons, as outlines in a 20 pt box (the magnifying glass and its sparkle in 28 pt).
private let glassIcon = rainbowOutlineSearchPath(
    "M19.47 13.65 C19.2 16.73 17.07 19.33 14.1 20.2 C11.13 21.06 7.93 20.02 6.05 17.56 C4.17 15.11 3.98 11.75 5.59 9.11 "
        + "C7.19 6.46 10.25 5.07 13.3 5.61 M17.6 18.6 L23.4 24.4"
)
private let sparkleIcon = rainbowOutlineSearchPath(
    "M21 1.5 C21.6 4.57 23.43 6.4 26.5 7 C23.43 7.6 21.6 9.43 21 12.5 C20.4 9.43 18.57 7.6 15.5 7 C18.57 6.4 20.4 4.57 21 1.5 Z"
)
private let closeIcon = rainbowOutlineSearchPath("M5.5 5.5 L14.5 14.5 M14.5 5.5 L5.5 14.5")
private let imageIcon = rainbowOutlineSearchPath(
    "M5 3.5 L15 3.5 C15.83 3.5 16.5 4.17 16.5 5 L16.5 15 C16.5 15.83 15.83 16.5 15 16.5 L5 16.5 C4.17 16.5 3.5 15.83 3.5 15 "
        + "L3.5 5 C3.5 4.17 4.17 3.5 5 3.5 Z M3.5 13.5 L7 10 L10 13 L12.5 10.5 L16.5 14.5"
)
private let lensIcon = rainbowOutlineSearchPath(
    "M3.5 7 L3.5 5.5 C3.5 4.4 4.4 3.5 5.5 3.5 L7 3.5 M13 3.5 L14.5 3.5 C15.6 3.5 16.5 4.4 16.5 5.5 L16.5 7 "
        + "M16.5 13 L16.5 14.5 C16.5 15.6 15.6 16.5 14.5 16.5 L13 16.5 M7 16.5 L5.5 16.5 C4.4 16.5 3.5 15.6 3.5 14.5 L3.5 13 "
        + "M12.6 10 C12.6 11.44 11.44 12.6 10 12.6 C8.56 12.6 7.4 11.44 7.4 10 C7.4 8.56 8.56 7.4 10 7.4 C11.44 7.4 12.6 8.56 12.6 10 Z"
)
private let micIcon = rainbowOutlineSearchPath(
    "M10 2.5 C11.38 2.5 12.5 3.62 12.5 5 L12.5 9.5 C12.5 10.88 11.38 12 10 12 C8.62 12 7.5 10.88 7.5 9.5 L7.5 5 "
        + "C7.5 3.62 8.62 2.5 10 2.5 Z M5 9.5 C5 12.26 7.24 14.5 10 14.5 C12.76 14.5 15 12.26 15 9.5 M10 14.5 L10 17.5"
)
private let arrowIcon = rainbowOutlineSearchPath("M10 15.5 L10 4.5 M5.5 9 L10 4.5 L14.5 9")

/// Builds a path from the part of SVG path data the icons use: absolute M, L, C and Z.
private func rainbowOutlineSearchPath(_ data: String) -> Path {
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

private struct RainbowOutlineSearchPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

#Preview {
    ZStack {
        Color(hex: 0xF3F3F3).ignoresSafeArea()
        RainbowOutlineSearch(autoPlay: true)
    }
}
