import SwiftUI

/// A picture attached to the prompt.
struct ImagePromptCardImage: Equatable {
    var title: String
    /// Address of the photo; it is shown square.
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color
}

/// A prompt card that takes reference images. Each one flies in from the plus along a short arc and
/// lands in a row that opens above the text; a badge pops it out again and a long press holds it up
/// larger above the card. On send the thumbnails pile up and sink into the send button, which turns
/// a ring once.
struct ImagePromptCard: View {
    /// Controlled text. Leave it nil and the component keeps its own.
    var text: Binding<String>? = nil
    /// Adds three images, holds one up, types the script, sends and starts over.
    var autoPlay = false
    var placeholder = "Describe what to make"
    /// The prompt autoPlay types.
    var script = "Make a moodboard from these photos"
    var model = "Nova 2.1"
    var agent = "Creative Agent"
    /// The images the plus attaches, in turn.
    var images = [
        ImagePromptCardImage(
            title: "Temple on a lake, Bali",
            photo: "https://images.unsplash.com/photo-1537996194471-e657df975ab4?auto=format&fit=crop&w=400&h=400&q=80",
            tint: Color(hex: 0x999787)
        ),
        ImagePromptCardImage(
            title: "White houses, Santorini",
            photo: "https://images.unsplash.com/photo-1570077188670-e3a8d69ac5ff?auto=format&fit=crop&w=400&h=400&q=80",
            tint: Color(hex: 0x8F9AA2)
        ),
        ImagePromptCardImage(
            title: "Balloons over Cappadocia",
            photo: "https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=400&h=400&q=80",
            tint: Color(hex: 0x7C8986)
        ),
    ]
    /// Colour of the enhance wand.
    var accent = Color(hex: 0x6B4EE6)
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the prompt and its images when the arrow or the return key is pressed.
    var onSend: ((String, [ImagePromptCardImage]) -> Void)? = nil
    var onAdd: ((ImagePromptCardImage) -> Void)? = nil
    var onRemove: ((ImagePromptCardImage) -> Void)? = nil
    var onEnhance: (() -> Void)? = nil
    var onModel: (() -> Void)? = nil
    var onAgent: (() -> Void)? = nil
    /// Called with "settings", "history", "ideas", "explore", "web", "focus" or "mic" for the bare icons.
    var onTool: ((String) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var inner = ""
    @State private var playing = false
    @State private var caretOn = true
    /// autoPlay has put its caret in the field.
    @State private var caretShown = false
    /// The arrow is held down by the script.
    @State private var pressed = false
    @State private var thumbs: [ImagePromptCardThumb] = []
    @State private var nextThumb = 0
    /// Counts the images attached so far: it picks the next one and turns the plus.
    @State private var added = 0
    /// The thumbnail held up by a long press, and the one the preview shows while it fades.
    @State private var lifted: Int? = nil
    @State private var peek: ImagePromptCardThumb? = nil
    @State private var peekX = 0.0
    /// A send is under way: the thumbnails pile up, then sink into the button.
    @State private var busy = false
    @State private var stacked = false
    @State private var departed = false
    @State private var sending = false
    @State private var spun = false
    @State private var gulp = false
    /// The prompt is faded out, just before the field empties.
    @State private var wiped = false
    /// Counts sends and resets, to start and cancel what follows a send.
    @State private var run = 0
    /// Counts touches, to restart the wait before autoPlay resumes.
    @State private var touches = 0

    private var value: String { text?.wrappedValue ?? inner }
    private var live: [ImagePromptCardThumb] { thumbs.filter { !$0.leaving } }
    private var rowOpen: Bool { !live.isEmpty }
    private var shift: Double { rowOpen ? Metrics.rowHeight : 0 }

    var body: some View {
        ZStack(alignment: .topLeading) {
            header
            card.offset(y: Metrics.cardY)
            preview
        }
        .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        .onAppear { playing = autoPlay && text == nil }
        .onChange(of: focused) { _, now in
            // A touch takes the field over from the script and starts from an empty card.
            if now, playing {
                playing = false
                reset()
            }
        }
        .task(id: playing) { await play() }
        .task(id: run) { await afterSend() }
        .task(id: nextThumb) {
            // A new thumbnail is laid at the plus first; a frame later it takes off for its slot.
            try? await Task.sleep(for: .milliseconds(20))
            for index in thumbs.indices where !thumbs[index].landed { thumbs[index].landed = true }
        }
        .task(id: "\(touches)|\(focused)") {
            // Left alone with an empty field, the card empties itself and the script resumes.
            guard autoPlay, text == nil, !playing, !focused else { return }
            try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
            guard !Task.isCancelled, value.isEmpty, !busy else { return }
            for thumb in live { remove(thumb.id, byHand: false) }
            try? await Task.sleep(for: .seconds(Metrics.rowSettle))
            if !Task.isCancelled { playing = true }
        }
        .task(id: "\(value)|\(playing)|\(caretShown)") {
            // The caret stays solid while keys arrive and blinks once they stop.
            caretOn = true
            guard playing, caretShown else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.blink))
                if Task.isCancelled { return }
                caretOn.toggle()
            }
        }
    }

    /// The pickers above the card: model and agent on the left, two bare icons on the right.
    private var header: some View {
        HStack(spacing: 8) {
            Button {
                onModel?()
            } label: {
                HStack(spacing: 6) {
                    markIcon
                        .stroke(Metrics.ink, style: StrokeStyle(lineWidth: 1.7, lineCap: .round))
                        .frame(width: 16, height: 16)
                    Text(model)
                }
                .modifier(ImagePromptCardChip())
            }
            Button {
                onAgent?()
            } label: {
                HStack(spacing: 6) {
                    // A small lilac avatar: a head and shoulders.
                    ZStack {
                        Circle().fill(Color(hex: 0xDDD6FE))
                        Circle().fill(Color(hex: 0x8B7CF6)).frame(width: 6.4, height: 6.4).offset(y: -2)
                        Ellipse().fill(Color(hex: 0x8B7CF6)).frame(width: 12, height: 8).offset(y: 7)
                    }
                    .frame(width: 18, height: 18)
                    .clipShape(Circle())
                    Text(agent)
                }
                .modifier(ImagePromptCardChip())
            }
            Spacer(minLength: 0)
            tool(slidersIcon, "settings", label: "Settings")
            tool(clockIcon, "history", label: "History")
        }
        .buttonStyle(ImagePromptCardPress())
        .padding(.trailing, 2)
        .frame(width: Metrics.width, height: Metrics.headerHeight)
    }

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)
        return ZStack(alignment: .topLeading) {
            shape.fill(.white).shadow(color: .black.opacity(0.04), radius: 7, y: 4)
            shape.strokeBorder(Color(hex: 0xE4E2F0), lineWidth: 1)

            Button {
                touch()
                onEnhance?()
            } label: {
                wandIcon
                    .stroke(accent, style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                    .frame(width: 20, height: 20)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(ImagePromptCardPress())
            .accessibilityLabel("Enhance the prompt")
            .position(x: Metrics.width - 28, y: 28)

            field
                .frame(width: Metrics.width - Metrics.textX * 2 - (rowOpen ? 0 : 30), height: 24, alignment: .leading)
                .offset(x: Metrics.textX, y: Metrics.textY + shift)

            toolbar.offset(y: Metrics.restHeight + shift - Metrics.toolY * 2)

            ForEach(thumbs) { thumb in
                thumbView(thumb, slot: thumbs.prefix { $0.id != thumb.id }.filter { !$0.leaving }.count)
            }
        }
        .frame(width: Metrics.width, height: Metrics.restHeight + shift, alignment: .topLeading)
        .animation(reduceMotion ? nil : Metrics.row, value: rowOpen)
    }

    /// The real field, with autoPlay's caret hanging off an invisible copy of the text.
    private var field: some View {
        ZStack(alignment: .leading) {
            Text(placeholder)
                .font(Metrics.font)
                .foregroundStyle(Color(hex: 0xA2A2AD))
                .lineLimit(1)
                .opacity(value.isEmpty ? 1 : 0)
            Text(value.replacingOccurrences(of: " ", with: "\u{00A0}"))
                .font(Metrics.font)
                .lineLimit(1)
                .fixedSize()
                .hidden()
                .frame(height: 24)
                .overlay(alignment: .trailing) {
                    Color.clear.frame(width: 0, height: 24).overlay(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 0.75)
                            .fill(Metrics.ink)
                            .frame(width: 1.5, height: 20)
                            .opacity(playing && caretShown && !focused && caretOn ? 1 : 0)
                            .animation(.easeOut(duration: 0.08), value: caretOn)
                            // Before the placeholder's first letter, not on it.
                            .offset(x: value.isEmpty ? -2 : 1)
                    }
                }
            TextField("", text: Binding(get: { value }, set: { edit($0) }))
                .font(Metrics.font)
                .foregroundStyle(Metrics.ink)
                .tint(accent)
                .submitLabel(.send)
                .focused($focused)
                .onSubmit { send(byHand: true) }
                .opacity(wiped ? 0 : 1)
                .accessibilityLabel(placeholder)
        }
    }

    /// The bottom row: the plus, three tools, and on the right two more and the send button.
    private var toolbar: some View {
        let y = Metrics.toolY
        return ZStack {
            Button {
                touch()
                add(byHand: true)
            } label: {
                plusIcon
                    .stroke(Metrics.ink, style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                    .frame(width: 20, height: 20)
                    .rotationEffect(.degrees(Double(added) * 90))
                    .animation(reduceMotion ? nil : Metrics.turn, value: added)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(ImagePromptCardPress())
            .accessibilityLabel("Add an image")
            .position(x: Metrics.plusX, y: y)

            Rectangle().fill(Color(hex: 0xE6E6EC)).frame(width: 1, height: 20).position(x: 54, y: y)
            tool(bulbIcon, "ideas", label: "Ideas").position(x: 78, y: y)
            tool(scopeIcon, "explore", label: "Explore").position(x: 114, y: y)
            tool(globeIcon, "web", label: "Search the web").position(x: 150, y: y)
            tool(targetIcon, "focus", label: "Focus").position(x: 244, y: y)
            tool(micIcon, "mic", label: "Dictate").position(x: 280, y: y)

            Button {
                touch()
                send(byHand: true)
            } label: {
                ZStack {
                    arrowIcon
                        .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                        .frame(width: 20, height: 20)
                        .scaleEffect(sending && !reduceMotion ? 0.5 : 1)
                        .opacity(sending ? 0 : 1)
                    Circle()
                        .trim(from: 0, to: 0.72)
                        .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                        .frame(width: 15, height: 15)
                        .rotationEffect(.degrees(spun ? 360 : 0))
                        .animation(spun ? .easeInOut(duration: Metrics.spinTime) : nil, value: spun)
                        .scaleEffect(sending || reduceMotion ? 1 : 0.5)
                        .opacity(sending ? 1 : 0)
                }
                .frame(width: Metrics.sendSize, height: Metrics.sendSize)
                .background(RoundedRectangle(cornerRadius: 12, style: .circular).fill(Color(hex: 0x0F1115)))
                .animation(.easeOut(duration: 0.15), value: sending)
                .scaleEffect(pressed ? 0.9 : (gulp ? 1.1 : 1))
                .animation(.easeOut(duration: Metrics.press), value: pressed)
                .animation(gulp || reduceMotion ? .easeOut(duration: Metrics.press) : Metrics.settle, value: gulp)
            }
            .buttonStyle(ImagePromptCardPress())
            .accessibilityLabel("Send")
            .position(x: Metrics.sendX, y: y)
        }
        .frame(width: Metrics.width, height: y * 2)
    }

    private func tool(_ icon: Path, _ name: String, label: String) -> some View {
        Button {
            touch()
            onTool?(name)
        } label: {
            icon
                .stroke(Color(hex: 0x2B2B33), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                .frame(width: 20, height: 20)
                .frame(width: 34, height: 34)
                .contentShape(Rectangle())
        }
        .buttonStyle(ImagePromptCardPress())
        .accessibilityLabel(label)
    }

    /// One thumbnail, placed by its slot and moved by its flight in and its way out.
    private func thumbView(_ thumb: ImagePromptCardThumb, slot: Int) -> some View {
        let slotX = Metrics.thumbX + Double(slot) * Metrics.thumbStep
        let pileX = Metrics.thumbX + Double(slot) * Metrics.pileStep
        let homeX = Metrics.thumbX + Double(thumb.home) * Metrics.thumbStep
        let rest = CGPoint(x: stacked && !reduceMotion ? pileX : slotX, y: Metrics.thumbY)
        let sendY = Metrics.restHeight + Metrics.rowHeight - Metrics.toolY
        let held = lifted == thumb.id
        return ImagePromptCardProgress(value: thumb.landed ? 1 : 0) { a in
            ImagePromptCardProgress(value: departed ? 1 : 0) { d in
                // Under reduced motion a thumbnail fades in and out where it stands.
                let away = reduceMotion ? 0 : 1 - a
                let sunk = reduceMotion ? 0 : d
                tile(thumb, held: held)
                    // The pile's tilt turns the thumbnail only, not the path it dives along.
                    .rotationEffect(.degrees(stacked && !reduceMotion ? Double(slot - 1) * Metrics.pileTilt : 0))
                    .animation(reduceMotion ? nil : Metrics.slide, value: stacked)
                    .scaleEffect(lerp(1, Metrics.flyScale, away) * lerp(1, Metrics.sinkScale, sunk))
                    .rotationEffect(.degrees(Metrics.flyTilt * away))
                    .opacity(clamp01(a * 4) * clamp01((1 - d) / 0.25))
                    .offset(
                        x: (thumb.from.x - homeX) * away + Metrics.flyArc * sin(.pi * clamp01(1 - away)) * (reduceMotion ? 0 : 1)
                            + (Metrics.sendX - pileX) * sunk,
                        y: (thumb.from.y - Metrics.thumbY) * away + (sendY - Metrics.thumbY) * sunk
                            - Metrics.sinkArc * sin(.pi * clamp01(sunk))
                    )
            }
            .animation(
                reduceMotion ? .easeOut(duration: Metrics.pop) : Metrics.sink.delay(Double(slot) * Metrics.sinkStagger),
                value: departed
            )
        }
        .animation(reduceMotion ? .easeOut(duration: Metrics.pop) : Metrics.land, value: thumb.landed)
        .scaleEffect(thumb.leaving ? 0.6 : 1)
        .opacity(thumb.leaving ? 0 : 1)
        .zIndex(held ? 10 : Double(slot))
        .position(rest)
        .animation(reduceMotion ? nil : Metrics.slide, value: rest)
    }

    /// The thumbnail itself: the photo in a white frame, and its badge.
    private func tile(_ thumb: ImagePromptCardThumb, held: Bool) -> some View {
        ZStack(alignment: .topTrailing) {
            ImagePromptCardPhoto(image: thumb.image)
                .frame(width: Metrics.thumb - 4, height: Metrics.thumb - 4)
                .clipShape(RoundedRectangle(cornerRadius: 6.5, style: .circular))
                .padding(2)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .circular)
                        .fill(.white)
                        .shadow(color: .black.opacity(held ? 0.24 : 0.14), radius: held ? 9 : 4, y: held ? 6 : 2)
                )
                .scaleEffect(held && !reduceMotion ? Metrics.liftScale : 1)
                .gesture(
                    LongPressGesture(minimumDuration: Metrics.holdTime)
                        .sequenced(before: DragGesture(minimumDistance: 0))
                        .onChanged { stage in
                            if case .second = stage, lifted != thumb.id {
                                touch()
                                lift(thumb.id, byHand: true)
                            }
                        }
                        .onEnded { _ in lifted = nil }
                )
                .accessibilityLabel(thumb.image.title)
            Button {
                touch()
                remove(thumb.id, byHand: true)
            } label: {
                xIcon
                    .stroke(Color(hex: 0x2B2B33), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                    .frame(width: 10, height: 10)
                    .frame(width: 18, height: 18)
                    .background(Circle().fill(.white).shadow(color: .black.opacity(0.18), radius: 2, y: 1))
                    .frame(width: 28, height: 28)
                    .contentShape(Rectangle())
            }
            .buttonStyle(ImagePromptCardPress())
            .accessibilityLabel("Remove \(thumb.image.title)")
            .offset(x: 12, y: -12)
            .opacity(stacked || held ? 0 : 1)
        }
        .frame(width: Metrics.thumb, height: Metrics.thumb)
        .animation(reduceMotion ? .easeOut(duration: Metrics.pop) : Metrics.lift, value: held)
        .animation(.easeOut(duration: Metrics.pop), value: stacked)
    }

    /// The held thumbnail shown larger, standing on the card's top edge above it.
    private var preview: some View {
        let shown = lifted != nil
        return ZStack {
            // Every image waits here unseen, so the one that is held up is already loaded.
            ForEach(Array(images.enumerated()), id: \.offset) { _, image in
                ImagePromptCardPhoto(image: image)
                    .frame(width: Metrics.peek - 6, height: Metrics.peek - 6)
                    .clipShape(RoundedRectangle(cornerRadius: 19, style: .circular))
                    .opacity(image == peek?.image ? 1 : 0)
            }
        }
        .frame(width: Metrics.peek, height: Metrics.peek)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .circular)
                .fill(.white)
                .shadow(color: .black.opacity(0.22), radius: 14, y: 12)
        )
        .scaleEffect(shown || reduceMotion ? 1 : 0.3, anchor: .bottom)
        .opacity(shown ? 1 : 0)
        .animation(shown && !reduceMotion ? Metrics.lift : .easeOut(duration: Metrics.pop), value: shown)
        .position(
            x: min(max(peekX, Metrics.peek / 2 + 4), Metrics.width - Metrics.peek / 2 - 4),
            y: Metrics.cardY - Metrics.peekGap - Metrics.peek / 2
        )
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// Any touch outside the field stops the script where it is.
    private func touch() {
        playing = false
        caretShown = false
        pressed = false
        lifted = nil
        touches += 1
    }

    private func edit(_ new: String) {
        guard new != value else { return }
        if let text { text.wrappedValue = new } else { inner = new }
        onChangeText?(new)
    }

    /// Attaches the next image: it starts at the plus and flies to the end of the row.
    private func add(byHand: Bool) {
        guard !busy, !images.isEmpty, live.count < Metrics.most else { return }
        let image = images[added % images.count]
        let from = CGPoint(x: Metrics.plusX, y: Metrics.restHeight + shift - Metrics.toolY)
        thumbs.append(ImagePromptCardThumb(id: nextThumb, image: image, home: live.count, from: from))
        nextThumb += 1
        added += 1
        if byHand { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        onAdd?(image)
    }

    /// Pops a thumbnail out; the ones after it slide over, and the row closes after the last.
    private func remove(_ id: Int, byHand: Bool) {
        guard !busy, let index = thumbs.firstIndex(where: { $0.id == id && !$0.leaving }) else { return }
        let image = thumbs[index].image
        if byHand { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        onRemove?(image)
        withAnimation(.easeOut(duration: Metrics.pop)) {
            thumbs[index].leaving = true
        } completion: {
            thumbs.removeAll { $0.id == id }
        }
    }

    private func lift(_ id: Int, byHand: Bool) {
        guard !busy, let index = thumbs.firstIndex(where: { $0.id == id && !$0.leaving }) else { return }
        peek = thumbs[index]
        peekX = Metrics.thumbX + Double(thumbs.prefix(index).filter { !$0.leaving }.count) * Metrics.thumbStep
        lifted = id
        if byHand { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    }

    private func send(byHand: Bool) {
        let prompt = value.trimmingCharacters(in: .whitespaces)
        focused = false
        guard !busy, !(prompt.isEmpty && live.isEmpty) else { return }
        busy = true
        pressed = false
        caretShown = false
        lifted = nil
        if byHand { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        onSend?(prompt, live.map(\.image))
        run += 1
    }

    /// What a send sets off: the pile, its dive into the button, the ring, and the empty card.
    private func afterSend() async {
        guard busy else { return }
        let had = !thumbs.isEmpty && !reduceMotion
        withAnimation(.easeOut(duration: Metrics.wipe)) { wiped = true }
        if had {
            stacked = true
            try? await Task.sleep(for: .seconds(Metrics.stackTime))
            if Task.isCancelled { return }
        }
        departed = true
        try? await Task.sleep(for: .seconds(had ? Metrics.ringAfter : Metrics.wipe))
        if Task.isCancelled { return }
        edit("")
        wiped = false
        sending = true
        spun = true
        let dive = Metrics.sinkTime + Double(max(0, thumbs.count - 1)) * Metrics.sinkStagger
        try? await Task.sleep(for: .seconds(had ? dive - Metrics.ringAfter : Metrics.pop))
        if Task.isCancelled { return }
        // The pile is inside the button: it swallows, and the row closes.
        gulp = had
        thumbs = []
        stacked = false
        departed = false
        try? await Task.sleep(for: .seconds(Metrics.press))
        if Task.isCancelled { return }
        gulp = false
        try? await Task.sleep(for: .seconds(Metrics.spinTime - (had ? dive - Metrics.ringAfter : Metrics.pop) - Metrics.press))
        if Task.isCancelled { return }
        sending = false
        spun = false
        busy = false
    }

    /// Back to the empty card at once, whatever was going on.
    private func reset() {
        busy = false
        pressed = false
        caretShown = false
        lifted = nil
        thumbs = []
        stacked = false
        departed = false
        sending = false
        spun = false
        gulp = false
        wiped = false
        run += 1
        edit("")
    }

    private func play() async {
        guard playing else { return }
        var origin = Date()
        func wait(until at: Double) async {
            let wait = origin.addingTimeInterval(at).timeIntervalSinceNow
            if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
        }
        while !Task.isCancelled {
            for at in Metrics.addAt {
                await wait(until: at)
                if Task.isCancelled { return }
                add(byHand: false)
            }
            await wait(until: Metrics.liftAt)
            if Task.isCancelled { return }
            if live.count > 1 { lift(live[1].id, byHand: false) }
            await wait(until: Metrics.dropAt)
            if Task.isCancelled { return }
            lifted = nil
            var at = Metrics.typeAt
            await wait(until: at - Metrics.caretLead)
            if Task.isCancelled { return }
            caretShown = true
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
            send(byHand: false)
            origin = origin.addingTimeInterval(max(Metrics.loop, at + Metrics.stackTime + Metrics.ringAfter + Metrics.spinTime + Metrics.rowSettle + Metrics.addAt[0]))
            await wait(until: 0)
        }
    }
}

private enum Metrics {
    static let width = 350.0
    /// The box the component is laid out in: the pickers, and the card with its row of images open.
    static let height = 208.0
    static let headerHeight = 30.0
    static let cardY = 42.0
    /// Height of the card without images; the row adds `rowHeight`.
    static let restHeight = 110.0
    static let rowHeight = 56.0
    static let radius = 24.0

    static let thumb = 44.0
    /// Centre of the first thumbnail, and the distance to the next.
    static let thumbX = 38.0
    static let thumbY = 38.0
    static let thumbStep = 54.0
    /// The most thumbnails the row holds.
    static let most = 5
    static let textX = 18.0
    static let textY = 15.0
    /// Centre of the bottom row from the card's bottom edge.
    static let toolY = 30.0
    static let plusX = 30.0
    static let sendX = 319.0
    static let sendSize = 38.0
    static let peek = 136.0
    /// Room between the preview and the card's top edge.
    static let peekGap = 8.0

    static let font = Font.system(size: 16)
    static let ink = Color(hex: 0x111111)

    // autoPlay (seconds). Keys land `key` apart on average, stretched by `rhythm` in turn.
    static let addAt = [0.8, 1.25, 1.7]
    static let liftAt = 2.6
    static let dropAt = 3.5
    static let typeAt = 4.0
    /// The caret shows in the field this long before the first key.
    static let caretLead = 0.35
    static let key = 0.048
    static let rhythm = [1.0, 0.72, 1.3, 0.85, 1.12, 0.64, 1.42, 0.95]
    /// From the last key to the tap on the arrow.
    static let sendAfter = 0.5
    static let loop = 9.4
    /// After the last touch, the card empties itself and the script waits this long.
    static let resumeAfter = 4.0
    /// Time the row takes to close.
    static let rowSettle = 0.5

    static let blink = 0.53
    static let press = 0.12
    static let pop = 0.16
    static let wipe = 0.18
    static let holdTime = 0.3

    // A thumbnail's flight in: from 0.3 scale and a tilt, bulging to the right of a straight path.
    static let flyScale = 0.3
    static let flyTilt = -20.0
    static let flyArc = 26.0
    static let liftScale = 1.1

    // A send: the thumbnails close up into a fanned pile, then dive into the button one after another.
    static let pileStep = 7.0
    static let pileTilt = 7.0
    static let stackTime = 0.26
    static let sinkTime = 0.42
    static let sinkStagger = 0.05
    static let sinkScale = 0.15
    static let sinkArc = 22.0
    /// From the first thumbnail diving to the arrow turning into a ring.
    static let ringAfter = 0.3
    static let spinTime = 0.7

    static let land = Animation.interpolatingSpring(mass: 1, stiffness: 210, damping: 19)
    static let slide = Animation.interpolatingSpring(mass: 1, stiffness: 320, damping: 26)
    static let row = Animation.interpolatingSpring(mass: 1, stiffness: 260, damping: 24)
    static let lift = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 20)
    static let turn = Animation.interpolatingSpring(mass: 1, stiffness: 260, damping: 18)
    static let settle = Animation.interpolatingSpring(mass: 1, stiffness: 420, damping: 14)
    static let sink = Animation.timingCurve(0.5, 0, 0.85, 0.6, duration: sinkTime)
}

private struct ImagePromptCardThumb: Identifiable {
    let id: Int
    let image: ImagePromptCardImage
    /// The slot it flew to, and where the plus was when it took off.
    let home: Int
    let from: CGPoint
    var landed = false
    /// Popped out by its badge, about to be dropped from the row.
    var leaving = false
}

/// A remote photo over its flat tint, faded in when it arrives.
private struct ImagePromptCardPhoto: View {
    let image: ImagePromptCardImage

    var body: some View {
        AsyncImage(url: URL(string: image.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
            if let photo = phase.image {
                photo.resizable().scaledToFill()
            } else {
                image.tint
            }
        }
    }
}

/// The look of a picker above the card: a white chip with a 13 pt label.
private struct ImagePromptCardChip: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(Metrics.ink)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 10)
            .frame(height: Metrics.headerHeight)
            .background(RoundedRectangle(cornerRadius: 10, style: .circular).fill(.white))
    }
}

/// Hands its content an animated number, so one animation can drive a whole flight.
private struct ImagePromptCardProgress<Content: View>: View, Animatable {
    var value: Double
    @ViewBuilder let content: (Double) -> Content

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        content(value)
    }
}

private struct ImagePromptCardPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

private func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double {
    from + (to - from) * t
}

private func clamp01(_ t: Double) -> Double {
    min(1, max(0, t))
}

// Icons, as outlines in a 20 pt box (the model's mark in 16 pt, the badge's cross in 10 pt).
private let markIcon = imagePromptCardPath("M8 2.5 L8 13.5 M3.24 5.25 L12.76 10.75 M3.24 10.75 L12.76 5.25")
private let xIcon = imagePromptCardPath("M3 3 L7 7 M7 3 L3 7")
private let plusIcon = imagePromptCardPath("M10 4.5 L10 15.5 M4.5 10 L15.5 10")
private let arrowIcon = imagePromptCardPath("M10 15.5 L10 4.5 M5.5 9 L10 4.5 L14.5 9")
private let bulbIcon = imagePromptCardPath(
    "M7 12.3 C5.28 11.01 4.58 8.76 5.26 6.72 C5.94 4.68 7.85 3.3 10 3.3 C12.15 3.3 14.06 4.68 14.74 6.72 "
        + "C15.42 8.76 14.72 11.01 13 12.3 L13 14.5 L7 14.5 Z M8.25 17 L11.75 17"
)
private let scopeIcon = imagePromptCardPath(
    "M3.6 10.2 L11.6 6 L13.1 8.9 L5.1 13.1 Z M11.6 6 L15 4.2 L16.5 7.1 L13.1 8.9 M8.4 11.6 L6 17 M8.4 11.6 L11 17"
)
private let globeIcon = imagePromptCardPath(
    "M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z "
        + "M3.5 10 L16.5 10 M10 3.5 C7.4 5.5 7.4 14.5 10 16.5 M10 3.5 C12.6 5.5 12.6 14.5 10 16.5"
)
private let targetIcon = imagePromptCardPath(
    "M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z "
        + "M12.5 10 C12.5 11.38 11.38 12.5 10 12.5 C8.62 12.5 7.5 11.38 7.5 10 C7.5 8.62 8.62 7.5 10 7.5 C11.38 7.5 12.5 8.62 12.5 10 Z"
)
private let micIcon = imagePromptCardPath(
    "M10 2.5 C11.38 2.5 12.5 3.62 12.5 5 L12.5 9.5 C12.5 10.88 11.38 12 10 12 C8.62 12 7.5 10.88 7.5 9.5 L7.5 5 "
        + "C7.5 3.62 8.62 2.5 10 2.5 Z M5 9.5 C5 12.26 7.24 14.5 10 14.5 C12.76 14.5 15 12.26 15 9.5 M10 14.5 L10 17.5"
)
private let wandIcon = imagePromptCardPath(
    "M3.5 16.5 L13.5 6.5 M15.5 2.5 L15.5 5.5 M14 4 L17 4 M16.75 10 L16.75 12.5 M15.5 11.25 L18 11.25 M8 2.75 L8 5.25 M6.75 4 L9.25 4"
)
private let slidersIcon = imagePromptCardPath(
    "M3.5 6.5 L10.5 6.5 M14.5 6.5 L16.5 6.5 M3.5 13.5 L5.5 13.5 M9.5 13.5 L16.5 13.5 "
        + "M14.5 6.5 C14.5 7.6 13.6 8.5 12.5 8.5 C11.4 8.5 10.5 7.6 10.5 6.5 C10.5 5.4 11.4 4.5 12.5 4.5 C13.6 4.5 14.5 5.4 14.5 6.5 Z "
        + "M9.5 13.5 C9.5 14.6 8.6 15.5 7.5 15.5 C6.4 15.5 5.5 14.6 5.5 13.5 C5.5 12.4 6.4 11.5 7.5 11.5 C8.6 11.5 9.5 12.4 9.5 13.5 Z"
)
private let clockIcon = imagePromptCardPath(
    "M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z "
        + "M10 6.5 L10 10 L12.5 11.5"
)

/// Builds a path from the part of SVG path data the icons use: absolute M, L, C and Z.
private func imagePromptCardPath(_ data: String) -> Path {
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

#Preview {
    ZStack {
        Color(hex: 0xF3F3F5).ignoresSafeArea()
        ImagePromptCard(autoPlay: true)
    }
}
