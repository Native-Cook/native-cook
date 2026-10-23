import SwiftUI

enum AttachmentTrayInputKind {
    case pdf, image, folder
}

struct AttachmentTrayInputFile: Equatable {
    var name: String
    var kind: AttachmentTrayInputKind
    /// The line under the name in the preview: type, size, length.
    var meta: String
    /// Heading and text of the summary in the preview.
    var title: String
    var summary: String
}

/// Prompt composer with a tray of attachments. Files drop into a strip that grows out of the top
/// of the text area, each one pushing the earlier chips along; holding a chip drops a preview card
/// out of it, and a chip's cross shrinks it away, the tray folding shut after the last one.
struct AttachmentTrayInput: View {
    /// The field's text when the parent owns it. Leave nil and the composer keeps its own.
    var text: Binding<String>? = nil
    /// Attaches three files, previews the newest and removes them again in a loop, without the
    /// keyboard, until the composer is touched.
    var autoPlay = false
    var placeholder = "Add instructions from attached images..."
    /// What the attach button adds, one per tap, in this order. autoPlay adds the first three.
    var files = [
        AttachmentTrayInputFile(
            name: "folder 3", kind: .folder, meta: "FOLDER · 12 ITEMS · 48MB", title: "Trip planning",
            summary: "Twelve files: three itineraries, two hotel confirmations, the car rental voucher and a shared budget sheet that was last edited on Jan 24."
        ),
        AttachmentTrayInputFile(
            name: "cloud.png", kind: .image, meta: "PNG · 1.1MB · 2048 × 1365", title: "Cloud over a ridge",
            summary: "A single cumulus cloud above a dark mountain ridge under a clear blue sky, lit from the left in the late afternoon. No people, no text."
        ),
        AttachmentTrayInputFile(
            name: "contract-agreement brief.pdf", kind: .pdf, meta: "PDF · 2.3MB · 93 LINES", title: "Service Agreement",
            summary: "This Service Agreement is entered into as of Jan 27, by and between the Client and the Provider, each with a mailing address on file. The Provider agrees to perform the services described in Exhibit A, in accordance with the terms and conditions of this Agreement, by the dates set out in its schedule."
        ),
    ]
    var modelLabel = "Nova 2.0"
    var summaryLabel = "Summary by AI"
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the trimmed text and the attached files, newest first.
    var onSend: ((String, [AttachmentTrayInputFile]) -> Void)? = nil
    var onAttach: ((AttachmentTrayInputFile) -> Void)? = nil
    var onRemove: ((AttachmentTrayInputFile) -> Void)? = nil
    /// Called when a chip has been held long enough to open its preview.
    var onPreview: ((AttachmentTrayInputFile) -> Void)? = nil
    /// Called with the index of the selection tool that was picked.
    var onTool: ((Int) -> Void)? = nil
    var onModel: (() -> Void)? = nil

    private struct Entry: Identifiable {
        let id: Int
        let file: AttachmentTrayInputFile
        var leaving = false
    }

    /// Where the chips are, in the composer's own space. Kept out of the view's state: it changes
    /// on every frame of a scroll and is read only when a preview opens.
    private final class Frames {
        var byID: [Int: CGRect] = [:]
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    /// The script is running.
    @State private var playing = false
    /// The chips in the tray, newest first; a chip on its way out stays until it has shrunk.
    @State private var entries: [Entry] = []
    @State private var serial = 0
    @State private var nextFile = 0
    @State private var frames = Frames()
    /// The file in the preview card, and where the card hangs: its left edge and the point of
    /// its top edge that sits under the chip.
    @State private var previewed: AttachmentTrayInputFile? = nil
    @State private var previewLeft = 0.0
    @State private var previewOrigin = 40.0
    @State private var previewOpen = false
    @State private var tool = 0
    /// The script's finger.
    @State private var heldAdd = false
    @State private var heldChip: Int? = nil
    @State private var heldCross: Int? = nil
    @State private var taps = 0
    @State private var thuds = 0
    @State private var resumes = 0

    private var controlled: Bool { text != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }
    private var value: String { text?.wrappedValue ?? draft }
    private var attached: [Entry] { entries.filter { !$0.leaving } }
    private var trayOpen: Bool { !attached.isEmpty }
    private var canSend: Bool { !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || trayOpen }

    var body: some View {
        VStack(spacing: 0) {
            tray
            card
        }
        .padding(Metrics.pad)
        .background(
            RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)
                .fill(Color(hex: 0xf5f5f5))
                .shadow(color: .black.opacity(0.05), radius: 12, y: 6)
        )
        .overlay(
            RoundedRectangle(cornerRadius: Metrics.radius, style: .circular)
                .strokeBorder(Color(hex: 0xe8e8e8), lineWidth: 1)
        )
        // The whole container moves with the tray, not the strip alone.
        .animation(trayAnimation, value: trayOpen)
        .frame(width: Metrics.width, height: Metrics.height, alignment: .bottom)
        // The preview hangs over the text area and past the composer's bottom edge.
        .overlay(alignment: .topLeading) {
            preview
                .offset(x: previewLeft, y: Metrics.previewTop)
                .allowsHitTesting(false)
        }
        .coordinateSpace(.named(Metrics.space))
        .sensoryFeedback(.impact(weight: .light), trigger: taps)
        .sensoryFeedback(.impact(weight: .medium), trigger: thuds)
        .onAppear { playing = auto }
        .onChange(of: auto) { playing = auto }
        .onChange(of: focused) { _, now in
            if now { interrupt() } else { resumes += 1 }
        }
        .task(id: playing) {
            guard playing else { return }
            await play()
        }
        .task(id: resumes) {
            // The loop starts over from an empty tray a few seconds after the last touch.
            guard resumes > 0, auto, !playing else { return }
            try? await Task.sleep(for: .seconds(Metrics.resume))
            guard !Task.isCancelled, !focused, value.isEmpty else { return }
            closePreview()
            for entry in attached { leave(entry.id) }
            try? await Task.sleep(for: .seconds(0.9))
            guard !Task.isCancelled, !focused, value.isEmpty else { return }
            playing = true
        }
    }

    // MARK: Tray

    private var tray: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    Color.clear.frame(width: Metrics.trayInset, height: 1).id(Edge.leading)
                    ForEach(entries) { entry in
                        AttachmentTrayInputChip(
                            file: entry.file,
                            leaving: entry.leaving,
                            held: heldChip == entry.id,
                            crossHeld: heldCross == entry.id,
                            reduceMotion: reduceMotion,
                            onHold: { holding in
                                interrupt()
                                if holding { openPreview(entry.id, byUser: true) } else { closePreview() }
                            },
                            onRemove: {
                                interrupt()
                                taps += 1
                                onRemove?(entry.file)
                                leave(entry.id)
                            },
                            onGone: { entries.removeAll { $0.id == entry.id } },
                            onFrame: { frames.byID[entry.id] = $0 }
                        )
                    }
                }
                .frame(height: Metrics.trayHeight)
            }
            .onChange(of: serial) {
                // A new chip lands at the front, so the row goes back to its start.
                withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo(Edge.leading, anchor: .leading) }
            }
        }
        .frame(height: Metrics.trayHeight)
        // The strip grows out of the top edge of the text area, and folds back into it.
        .frame(height: trayOpen ? Metrics.trayHeight : 0, alignment: .bottom)
        .clipped()
    }

    private var trayAnimation: Animation {
        if reduceMotion { return .easeOut(duration: 0.2) }
        return trayOpen ? Metrics.traySpring : Metrics.settle.delay(Metrics.foldDelay)
    }

    // MARK: Text area

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                Text(placeholder)
                    .foregroundStyle(Color(hex: 0xb5b5b5))
                    .lineLimit(1)
                    .opacity(value.isEmpty ? 1 : 0)
                    .allowsHitTesting(false)
                TextField("", text: Binding(get: { value }, set: { write($0) }), axis: .vertical)
                    .lineLimit(1...2)
                    .focused($focused)
                    .foregroundStyle(Color(hex: 0x111111))
                    .tint(Color(hex: 0x111111))
                    .accessibilityLabel(placeholder)
            }
            .font(.system(size: 17))
            .frame(height: Metrics.textHeight, alignment: .top)
            .padding(.horizontal, 16)
            .padding(.top, 14)

            toolbar
                .padding(12)
        }
        .background(
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular)
                .fill(.white)
                .shadow(color: .black.opacity(0.04), radius: 3, y: 1)
        )
        .overlay(
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular)
                .strokeBorder(Color(hex: 0xe6e6e6), lineWidth: 1)
        )
    }

    private var toolbar: some View {
        HStack(spacing: 8) {
            Button {
                interrupt()
                attach(byUser: true)
            } label: {
                AttachmentTrayInputGlyph(d: Glyph.note)
                    .foregroundStyle(Color(hex: 0x111111))
                    .frame(width: Metrics.control, height: Metrics.control)
                    .background(bezel)
            }
            .buttonStyle(AttachmentTrayInputPress(held: heldAdd))
            .accessibilityLabel("Attach a file")

            ZStack(alignment: .leading) {
                // The white tile slides under the tool that is picked.
                RoundedRectangle(cornerRadius: 8, style: .circular)
                    .fill(.white)
                    .shadow(color: .black.opacity(0.1), radius: 1.5, y: 1)
                    .frame(width: Metrics.tool, height: Metrics.tool)
                    .offset(x: Double(tool) * Metrics.tool)
                    .animation(reduceMotion ? .easeOut(duration: 0.2) : Metrics.tileSpring, value: tool)
                HStack(spacing: 0) {
                    ForEach(Array(Glyph.tools.enumerated()), id: \.offset) { slot, glyph in
                        Button {
                            tool = slot
                            taps += 1
                            onTool?(slot)
                        } label: {
                            ZStack {
                                AttachmentTrayInputGlyph(d: glyph.dashed, dash: [2.3, 2.5])
                                AttachmentTrayInputGlyph(d: glyph.solid)
                            }
                            .foregroundStyle(Color(hex: slot == tool ? 0x111111 : 0x8d8d8d))
                            .animation(.easeOut(duration: 0.2), value: tool)
                            .frame(width: Metrics.tool, height: Metrics.tool)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(glyph.label)
                    }
                }
            }
            .padding(3)
            .background(RoundedRectangle(cornerRadius: 10, style: .circular).fill(Color(hex: 0xf3f3f3)))

            Button {
                onModel?()
            } label: {
                HStack(spacing: 6) {
                    AttachmentTrayInputGlyph(d: Glyph.mark)
                    Text(modelLabel)
                        .font(.system(size: 15, weight: .medium))
                        .lineLimit(1)
                }
                .foregroundStyle(Color(hex: 0x111111))
                .padding(.leading, 8)
                .padding(.trailing, 11)
                .frame(height: Metrics.control)
                .background(bezel)
            }
            .buttonStyle(AttachmentTrayInputPress(held: false))

            Spacer(minLength: 0)

            Button(action: send) {
                AttachmentTrayInputGlyph(d: Glyph.arrow, lineWidth: 1.9)
                    .foregroundStyle(.white)
                    .frame(width: Metrics.control, height: Metrics.control)
                    .background(Circle().fill(Color(hex: canSend ? 0x111111 : 0xd6d6d6)))
                    .animation(.easeOut(duration: 0.2), value: canSend)
            }
            .buttonStyle(AttachmentTrayInputPress(held: false))
            .disabled(!canSend)
            .accessibilityLabel("Send")
        }
    }

    /// The white rounded square behind the attach button and the model label.
    private var bezel: some View {
        RoundedRectangle(cornerRadius: 10, style: .circular)
            .fill(.white)
            .shadow(color: .black.opacity(0.05), radius: 1.5, y: 1)
            .overlay(RoundedRectangle(cornerRadius: 10, style: .circular).strokeBorder(Color(hex: 0xe8e8e8), lineWidth: 1))
    }

    // MARK: Preview

    private var preview: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(previewed?.name ?? "")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color(hex: 0x111111))
                .lineLimit(1)
                .frame(height: 22)
                .padding(.leading, 4)
                .modifier(rise(0))

            HStack(spacing: 6) {
                AttachmentTrayInputFileIcon(kind: previewed?.kind ?? .pdf)
                    .scaleEffect(0.78)
                    .frame(width: 14, height: 14)
                Text(previewed?.meta ?? "")
                    .font(.system(size: 12, design: .monospaced))
                    .tracking(0.4)
                    .foregroundStyle(Color(hex: 0x8a8a8a))
                    .lineLimit(1)
            }
            .frame(height: 16)
            .padding(.leading, 4)
            .padding(.top, 4)
            .modifier(rise(1))

            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 4) {
                    attachmentTrayInputPath(Glyph.spark)
                        .fill(Color(hex: 0x8b5cf6))
                        .frame(width: 12, height: 12)
                    Text(summaryLabel)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(
                            LinearGradient(colors: [Color(hex: 0x8b5cf6), Color(hex: 0xf59e0b)], startPoint: .leading, endPoint: .trailing)
                        )
                }
                .frame(height: 14)
                Text(previewed?.title ?? "")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x2a2a2e))
                    .lineLimit(1)
                    .frame(height: 20)
                    .padding(.top, 8)
                Text(previewed?.summary ?? "")
                    .font(.system(size: 12.5))
                    .lineSpacing(2)
                    .foregroundStyle(Color(hex: 0x6f6f73))
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 6)
            }
            .padding(14)
            .frame(height: Metrics.summaryHeight, alignment: .top)
            // The text runs out of the box and fades before its edge.
            .overlay(alignment: .bottom) {
                LinearGradient(colors: [.white.opacity(0), .white], startPoint: .top, endPoint: .bottom)
                    .frame(height: 44)
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .circular))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .circular).strokeBorder(Color(hex: 0xececec), lineWidth: 1))
            .padding(.top, 10)
            .modifier(rise(2))
        }
        .padding(12)
        .frame(width: Metrics.previewWidth, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .circular)
                .fill(.white)
                .shadow(color: .black.opacity(0.12), radius: 20, y: 12)
        )
        .overlay(RoundedRectangle(cornerRadius: 20, style: .circular).strokeBorder(Color(hex: 0xededed), lineWidth: 1))
        // It drops out of the chip it belongs to, and is pulled back into it.
        .scaleEffect(previewOpen || reduceMotion ? 1 : 0.6, anchor: UnitPoint(x: previewOrigin / Metrics.previewWidth, y: 0))
        .offset(y: previewOpen || reduceMotion ? 0 : -10)
        .opacity(previewOpen ? 1 : 0)
        .animation(
            reduceMotion ? .easeOut(duration: 0.2) : (previewOpen ? Metrics.previewSpring : .easeIn(duration: Metrics.retract)),
            value: previewOpen
        )
        .accessibilityHidden(!previewOpen)
    }

    /// The rows of the preview come up one after another behind the card.
    private func rise(_ row: Int) -> AttachmentTrayInputRise {
        AttachmentTrayInputRise(shown: previewOpen, delay: 0.1 + Double(row) * 0.06, still: reduceMotion)
    }

    // MARK: Actions

    private func write(_ next: String) {
        if let text { text.wrappedValue = next } else { draft = next }
        onChangeText?(next)
    }

    private func attach(byUser: Bool) {
        guard !files.isEmpty else { return }
        let file = files[nextFile % files.count]
        nextFile += 1
        serial += 1
        entries.insert(Entry(id: serial, file: file), at: 0)
        if byUser {
            taps += 1
            onAttach?(file)
        }
    }

    private func leave(_ id: Int) {
        guard let slot = entries.firstIndex(where: { $0.id == id }) else { return }
        entries[slot].leaving = true
    }

    private func openPreview(_ id: Int, byUser: Bool) {
        guard let entry = entries.first(where: { $0.id == id }), let frame = frames.byID[id] else { return }
        previewLeft = min(max(frame.minX - 6, 0), Metrics.width - Metrics.previewWidth)
        previewOrigin = min(max(frame.midX - previewLeft, 0), Metrics.previewWidth)
        previewed = entry.file
        previewOpen = true
        if byUser {
            thuds += 1
            onPreview?(entry.file)
        }
    }

    private func closePreview() {
        previewOpen = false
    }

    private func send() {
        interrupt()
        guard canSend else { return }
        taps += 1
        onSend?(value.trimmingCharacters(in: .whitespacesAndNewlines), attached.map(\.file))
        write("")
        for entry in attached { leave(entry.id) }
    }

    /// Any touch takes the composer away from the script.
    private func interrupt() {
        resumes += 1
        guard playing else { return }
        playing = false
        heldAdd = false
        heldChip = nil
        heldCross = nil
        closePreview()
    }

    /// One loop after another, every step at its own moment after the loop began.
    private func play() async {
        while !Task.isCancelled {
            let start = ContinuousClock.now
            func reach(_ seconds: Double) async -> Bool {
                try? await Task.sleep(until: start + .seconds(seconds), clock: .continuous)
                return !Task.isCancelled
            }

            nextFile = 0
            for at in Metrics.addAt {
                guard await reach(at - Metrics.pressLead) else { return }
                heldAdd = true
                guard await reach(at) else { return }
                heldAdd = false
                attach(byUser: false)
            }
            guard await reach(Metrics.holdAt) else { return }
            let newest = attached.first?.id
            heldChip = newest
            guard await reach(Metrics.holdAt + Metrics.holdTime) else { return }
            if let newest { openPreview(newest, byUser: false) }
            guard await reach(Metrics.releaseAt) else { return }
            heldChip = nil
            closePreview()
            for at in Metrics.removeAt {
                guard await reach(at - Metrics.pressLead) else { return }
                heldCross = attached.first?.id
                guard await reach(at) else { return }
                if let id = heldCross { leave(id) }
                heldCross = nil
            }
            guard await reach(Metrics.loop) else { return }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    /// The composer with its tray open; the tray grows upwards inside this box.
    static let height = 174.0
    static let radius = 24.0
    /// Inset of the tray and of the text area from the outer edge.
    static let pad = 4.0
    static let trayHeight = 40.0
    static let trayInset = 4.0
    static let chipHeight = 32.0
    static let chipGap = 6.0
    static let nameMax = 118.0
    static let cardRadius = 20.0
    static let textHeight = 48.0
    static let control = 36.0
    static let tool = 30.0
    static let previewWidth = 300.0
    /// The preview hangs from the bottom edge of the chips, over the text area.
    static let previewTop = 44.0
    static let summaryHeight = 138.0
    /// How far above its place a chip starts.
    static let dropHeight = 22.0
    static let hold = 0.3
    static let retract = 0.16
    static let foldDelay = 0.15

    static let space = "attachment-tray-input"

    static let slotSpring = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 26)
    static let dropSpring = Animation.interpolatingSpring(mass: 1, stiffness: 380, damping: 20)
    static let traySpring = Animation.interpolatingSpring(mass: 1, stiffness: 320, damping: 24)
    static let previewSpring = Animation.interpolatingSpring(mass: 1, stiffness: 320, damping: 24)
    static let tileSpring = Animation.interpolatingSpring(mass: 1, stiffness: 420, damping: 30)
    /// Closing movements end without a bounce.
    static let settle = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.3)

    // autoPlay loop, in seconds from its start.
    static let addAt = [0.95, 1.85, 2.75]
    /// A scripted tap holds its button down this long before it acts.
    static let pressLead = 0.15
    static let holdAt = 3.9
    static let holdTime = 0.3
    static let releaseAt = 6.5
    static let removeAt = [7.3, 7.85, 8.4]
    static let loop = 10.0
    /// After the last touch, the loop waits this long before it starts again.
    static let resume = 3.0
}

/// Outlines, the same strings in the three stacks: 20 pt boxes for the toolbar, 18 pt boxes for
/// the file icons, 12 pt boxes for the cross and the spark.
private enum Glyph {
    static let note = "M 16.4 9.4 L 16.4 13.4 C 16.4 15.06 15.06 16.4 13.4 16.4 L 6.6 16.4 C 4.94 16.4 3.6 15.06 3.6 13.4 L 3.6 6.6 C 3.6 4.94 4.94 3.6 6.6 3.6 L 10.6 3.6 M 15.2 2.4 L 15.2 7.2 M 12.8 4.8 L 17.6 4.8"
    static let wand = "M 4 16 L 11.4 8.6 M 14.4 2.2 C 14.4 4.58 15.42 5.6 17.8 5.6 C 15.42 5.6 14.4 6.62 14.4 9 C 14.4 6.62 13.38 5.6 11 5.6 C 13.38 5.6 14.4 4.58 14.4 2.2 Z M 7.2 4.6 L 7.21 4.6 M 15.8 12.4 L 15.81 12.4"
    static let lasso = "M 16.4 10 C 16.4 13.53 13.53 16.4 10 16.4 C 6.47 16.4 3.6 13.53 3.6 10 C 3.6 6.47 6.47 3.6 10 3.6 C 13.53 3.6 16.4 6.47 16.4 10 Z"
    static let marquee = "M 10.2 3.8 L 6.2 3.8 C 4.87 3.8 3.8 4.87 3.8 6.2 L 3.8 10.2 M 3.8 13.2 C 3.8 14.53 4.87 15.6 6.2 15.6 L 8.4 15.6 M 13.2 3.8 C 14.53 3.8 15.6 4.87 15.6 6.2 L 15.6 8.4"
    static let cursor = "M 10.2 10.2 L 17.2 12.6 L 14 14 L 12.6 17.2 Z"
    static let mark = "M 12.2 10 C 12.2 12.54 10.14 14.6 7.6 14.6 C 5.06 14.6 3 12.54 3 10 C 3 7.46 5.06 5.4 7.6 5.4 C 10.14 5.4 12.2 7.46 12.2 10 Z M 17 10 C 17 12.54 14.94 14.6 12.4 14.6 C 9.86 14.6 7.8 12.54 7.8 10 C 7.8 7.46 9.86 5.4 12.4 5.4 C 14.94 5.4 17 7.46 17 10 Z"
    static let arrow = "M 10 15.2 L 10 5 M 5.8 9.2 L 10 5 L 14.2 9.2"
    static let cross = "M 3.2 3.2 L 8.8 8.8 M 8.8 3.2 L 3.2 8.8"
    static let spark = "M 5 2.8 C 5 5.88 6.32 7.2 9.4 7.2 C 6.32 7.2 5 8.52 5 11.6 C 5 8.52 3.68 7.2 0.6 7.2 C 3.68 7.2 5 5.88 5 2.8 Z M 9.8 0.8 C 9.8 2.34 10.46 3 12 3 C 10.46 3 9.8 3.66 9.8 5.2 C 9.8 3.66 9.14 3 7.6 3 C 9.14 3 9.8 2.34 9.8 0.8 Z"

    static let pdfPage = "M 5.4 1.6 L 10.4 1.6 L 14.6 5.8 L 14.6 14.4 C 14.6 15.5 13.7 16.4 12.6 16.4 L 5.4 16.4 C 4.3 16.4 3.4 15.5 3.4 14.4 L 3.4 3.6 C 3.4 2.5 4.3 1.6 5.4 1.6 Z"
    static let pdfFold = "M 10.4 1.6 L 14.6 5.8 L 11.8 5.8 C 11.03 5.8 10.4 5.17 10.4 4.4 Z"
    static let pdfLines = "M 6.2 9.6 L 11.8 9.6 M 6.2 12.6 L 9.8 12.6"
    static let imageTile = "M 6.2 2.2 L 11.8 2.2 C 14.01 2.2 15.8 3.99 15.8 6.2 L 15.8 11.8 C 15.8 14.01 14.01 15.8 11.8 15.8 L 6.2 15.8 C 3.99 15.8 2.2 14.01 2.2 11.8 L 2.2 6.2 C 2.2 3.99 3.99 2.2 6.2 2.2 Z"
    static let imageHills = "M 4.8 12.4 L 7.6 9 L 9.8 11.4 L 11.4 9.8 L 13.4 12.4 M 11.6 6.2 L 11.61 6.2"
    static let folderTab = "M 1.8 8 L 1.8 4.4 C 1.8 3.52 2.52 2.8 3.4 2.8 L 6.6 2.8 L 8.4 4.8 L 14.6 4.8 C 15.48 4.8 16.2 5.52 16.2 6.4 L 16.2 8 Z"
    static let folderBody = "M 4 6 L 14 6 C 15.22 6 16.2 6.98 16.2 8.2 L 16.2 13.6 C 16.2 14.82 15.22 15.8 14 15.8 L 4 15.8 C 2.78 15.8 1.8 14.82 1.8 13.6 L 1.8 8.2 C 1.8 6.98 2.78 6 4 6 Z"

    /// The three selection tools: the part drawn dashed, the part drawn solid.
    static let tools: [(dashed: String, solid: String, label: String)] = [
        ("", wand, "Magic select"),
        (lasso, "", "Lasso"),
        (marquee, cursor, "Marquee"),
    ]
}

/// Builds a path from "M x y L x y C x1 y1 x2 y2 x y Z": absolute commands, separated by spaces.
private func attachmentTrayInputPath(_ d: String) -> Path {
    var path = Path()
    var command: Character = "M"
    var numbers: [Double] = []
    for token in d.split(separator: " ") {
        guard let number = Double(token) else {
            command = token.first ?? "M"
            numbers = []
            if command == "Z" { path.closeSubpath() }
            continue
        }
        numbers.append(number)
        switch (command, numbers.count) {
        case ("M", 2):
            path.move(to: CGPoint(x: numbers[0], y: numbers[1]))
        case ("L", 2):
            path.addLine(to: CGPoint(x: numbers[0], y: numbers[1]))
        case ("C", 6):
            path.addCurve(
                to: CGPoint(x: numbers[4], y: numbers[5]),
                control1: CGPoint(x: numbers[0], y: numbers[1]),
                control2: CGPoint(x: numbers[2], y: numbers[3])
            )
        default:
            break
        }
    }
    return path
}

/// A stroked outline in the current foreground colour.
private struct AttachmentTrayInputGlyph: View {
    let d: String
    var size = 20.0
    var lineWidth = 1.5
    var dash: [CGFloat] = []

    var body: some View {
        attachmentTrayInputPath(d)
            .stroke(style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round, dash: dash))
            .frame(width: size, height: size)
    }
}

/// A red page, a blue picture or a yellow folder, in an 18 pt box.
private struct AttachmentTrayInputFileIcon: View {
    let kind: AttachmentTrayInputKind

    var body: some View {
        ZStack {
            switch kind {
            case .pdf:
                attachmentTrayInputPath(Glyph.pdfPage).fill(Color(hex: 0xff5b4f))
                attachmentTrayInputPath(Glyph.pdfFold).fill(Color(hex: 0xffc2bd))
                line(Glyph.pdfLines, width: 1.3)
            case .image:
                attachmentTrayInputPath(Glyph.imageTile).fill(Color(hex: 0x8ecbff))
                line(Glyph.imageHills, width: 1.3)
            case .folder:
                attachmentTrayInputPath(Glyph.folderTab).fill(Color(hex: 0xf5a623))
                attachmentTrayInputPath(Glyph.folderBody).fill(Color(hex: 0xffc83d))
            }
        }
        .frame(width: 18, height: 18)
    }

    private func line(_ d: String, width: Double) -> some View {
        attachmentTrayInputPath(d).stroke(.white, style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
}

/// One attachment in the tray. It opens its own room in the row, drops into it from above, and
/// gives the room back when it leaves.
private struct AttachmentTrayInputChip: View {
    let file: AttachmentTrayInputFile
    let leaving: Bool
    /// The script is holding the chip, or its cross.
    let held: Bool
    let crossHeld: Bool
    let reduceMotion: Bool
    let onHold: (Bool) -> Void
    let onRemove: () -> Void
    let onGone: () -> Void
    let onFrame: (CGRect) -> Void

    @GestureState private var holding = false
    @State private var natural = 0.0
    /// Share of its width the chip takes in the row, how far it has dropped in, how far it has gone.
    @State private var room = 0.0
    @State private var drop = 0.0
    @State private var gone = 0.0

    var body: some View {
        HStack(spacing: 0) {
            AttachmentTrayInputFileIcon(kind: file.kind)
            Text(file.name)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Color(hex: 0x111111))
                .lineLimit(1)
                .frame(maxWidth: Metrics.nameMax, alignment: .leading)
                .fixedSize()
                .padding(.leading, 6)
            Button(action: onRemove) {
                AttachmentTrayInputGlyph(d: Glyph.cross, size: 12)
                    .foregroundStyle(Color(hex: 0x111111))
                    .frame(width: 26, height: 26)
                    .contentShape(Rectangle())
            }
            .buttonStyle(AttachmentTrayInputPress(held: crossHeld, scale: 0.8))
            .accessibilityLabel("Remove \(file.name)")
        }
        .padding(.leading, 8)
        .padding(.trailing, 3)
        .frame(height: Metrics.chipHeight)
        .background(
            Capsule()
                .fill(Color(hex: holding || held ? 0xf0f0f0 : 0xffffff))
                .shadow(color: .black.opacity(0.06), radius: 1.5, y: 1)
        )
        .overlay(Capsule().strokeBorder(Color(hex: 0xebebeb), lineWidth: 1))
        .animation(.easeOut(duration: 0.15), value: holding || held)
        .contentShape(Capsule())
        // Held long enough, the chip shows its preview until the finger lifts.
        .gesture(
            LongPressGesture(minimumDuration: Metrics.hold, maximumDistance: 24)
                .sequenced(before: DragGesture(minimumDistance: 0))
                .updating($holding) { value, state, _ in
                    if case .second = value { state = true }
                }
        )
        .onChange(of: holding) { _, now in onHold(now) }
        .fixedSize()
        .onGeometryChange(for: Double.self) { $0.size.width } action: { natural = $0 }
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Metrics.space)) } action: { onFrame($0) }
        .scaleEffect(reduceMotion ? 1 : (0.8 + 0.2 * drop) * (1 - 0.4 * gone))
        .offset(y: reduceMotion ? 0 : -Metrics.dropHeight * (1 - drop))
        .opacity(drop * (1 - gone))
        .frame(width: max(0, (natural + Metrics.chipGap) * room), alignment: .leading)
        .onAppear {
            // The row opens first, then the chip lands in the gap.
            withAnimation(reduceMotion ? .easeOut(duration: 0.2) : Metrics.slotSpring) { room = 1 }
            withAnimation(reduceMotion ? .easeOut(duration: 0.2) : Metrics.dropSpring.delay(0.06)) { drop = 1 }
        }
        .onChange(of: leaving) { _, now in
            guard now else { return }
            withAnimation(.easeIn(duration: 0.18)) { gone = 1 }
            withAnimation(Metrics.settle.delay(0.08)) { room = 0 }
            Task {
                try? await Task.sleep(for: .seconds(0.45))
                onGone()
            }
        }
    }
}

/// A row of the preview: it comes up a few points and fades in after `delay`, and leaves at once.
private struct AttachmentTrayInputRise: ViewModifier {
    let shown: Bool
    let delay: Double
    let still: Bool

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown || still ? 0 : 8)
            .animation(shown ? .easeOut(duration: 0.26).delay(delay) : .easeOut(duration: 0.1), value: shown)
    }
}

/// A button dips under a finger, or under the script's.
private struct AttachmentTrayInputPress: ButtonStyle {
    let held: Bool
    var scale = 0.92

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed || held
        configuration.label
            .scaleEffect(down ? scale : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: down)
    }
}
