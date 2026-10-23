import SwiftUI
import UIKit

struct PastePlacesImport: View {
    static let loop = PastePlacesImportMotion.loop
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @State private var playback = PastePlacesImportPlayback()
    @State private var timelinePaused = true
    @State private var wakeTask: Task<Void, Never>?
    @State private var reducedTask: Task<Void, Never>?
    @State private var reducedPhase = 0.0
    @State private var reducedOpacity = 1.0
    @State private var pastePress = 0.0
    @State private var startPress = 0.0
    @State private var closePress = 0.0
    @State private var lightHaptics = 0
    @State private var successHaptics = 0
    @State private var lastSuccessToken: Int?
    /// The text area's own text (typed, or pasted from the clipboard); the scripted note never goes in here.
    @State private var draft = ""
    @State private var focused = false
    @State private var resignToken = 0
    @State private var draftFill = 0.0
    @State private var faces: PastePlacesImportFaces?

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        PastePlacesImportAssets.prepare()
    }

    /// The loop is a demo: it stops for a screen reader as for Reduce Motion, and while the text area is the user's.
    private var loops: Bool {
        autoPlay && !reduceMotion && !voiceOver && draft.isEmpty && !focused
    }

    private func makeLayout(_ size: CGSize) -> PastePlacesImportLayout {
        let unit = size.width / PastePlacesImportTheme.referenceWidth
        let contentHeight = max(size.height, (2622 - PastePlacesImportTheme.referenceInset) * unit)
        // GeometryReader starts below the status bar. Reference y therefore only needs
        // the pin's safe-area subtraction; the container supplies the physical inset.
        return PastePlacesImportLayout(size: CGSize(width: size.width, height: contentHeight), safeTop: 0)
    }

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: timelinePaused || reduceMotion || scenePhase != .active)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let phase = reduceMotion ? reducedPhase : playback.phase(at: now)
                let layout = makeLayout(proxy.size)
                let automaticPaste = reduceMotion ? 0 : playback.scriptedPress(.paste, at: now)
                let automaticStart = reduceMotion ? 0 : playback.scriptedPress(.start, at: now)
                let automaticClose = reduceMotion ? 0 : playback.scriptedPress(.close, at: now)

                PastePlacesImportScene(
                    time: phase,
                    layout: layout,
                    pastePress: max(pastePress, automaticPaste),
                    startPress: max(startPress, automaticStart),
                    closePress: max(closePress, automaticClose),
                    draftCount: draft.count,
                    draftFill: draftFill,
                    faces: faces
                )
                .overlay(alignment: .topLeading) {
                    controls(layout: layout, phase: phase)
                }
                .contentShape(Rectangle())
                .simultaneousGesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            guard !playback.isHeld else { return }
                            if focused, !inTextArea(value.startLocation, layout: layout) { resignToken += 1 }
                            guard !reduceMotion else { return }
                            playback.beginTouch(at: clock)
                            refreshCadence()
                        }
                        .onEnded { _ in
                            guard playback.isHeld else { return }
                            playback.endTouchWithoutAction(at: clock)
                            resetPresses(animated: true)
                            refreshCadence()
                        }
                )
                .opacity(reducedOpacity)
                .onChange(of: playback.shouldTick(at: now)) { _, _ in refreshCadence() }
                .onChange(of: playback.successToken(at: now)) { _, token in
                    guard let token, token != lastSuccessToken, !reduceMotion else { return }
                    lastSuccessToken = token
                    successHaptics += 1
                }
            }
            .task(id: proxy.size.width) {
                // The fold snapshots the text area once; until then (or if it fails) it folds plain white paper.
                faces = PastePlacesImportFaces(layout: makeLayout(proxy.size), scale: displayScale)
            }
        }
        .background(PastePlacesImportTheme.color(0xededed).ignoresSafeArea())
        .ignoresSafeArea(.container, edges: .bottom)
        .onAppear {
            playback.start(autoPlay: loops, at: clock)
            playback.setActive(scenePhase == .active, at: clock)
            refreshCadence()
        }
        .onDisappear {
            wakeTask?.cancel()
            reducedTask?.cancel()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { resetPresses(animated: false) }
            playback.setActive(phase == .active, at: clock)
            refreshCadence()
        }
        .onChange(of: loops) { _, enabled in
            playback.setAutoPlay(enabled, at: clock)
            refreshCadence()
        }
        .onChange(of: draft.isEmpty) { _, empty in
            let to = empty ? 0.0 : 1.0
            if reduceMotion {
                draftFill = to
            } else {
                withAnimation(.timingCurve(0.77, 0, 0.175, 1, duration: PastePlacesImportMotion.fillIn.1 - PastePlacesImportMotion.fillIn.0)) {
                    draftFill = to
                }
            }
        }
        .onChange(of: reduceMotion) { _, _ in
            wakeTask?.cancel()
            reducedTask?.cancel()
            reducedOpacity = 1
            reducedPhase = 0
            lastSuccessToken = nil
            resetPresses(animated: false)
            playback.start(autoPlay: loops, at: clock)
            refreshCadence()
        }
        .sensoryFeedback(.impact(weight: .light), trigger: lightHaptics)
        .sensoryFeedback(.success, trigger: successHaptics)
    }

    @ViewBuilder
    private func controls(layout: PastePlacesImportLayout, phase: Double) -> some View {
        textArea(layout: layout, phase: phase)

        Button(action: { activate(.close) }) { Color.clear }
            .buttonStyle(PastePlacesImportPressStyle { pressed in
                pressChanged(.close, pressed: pressed, phase: phase)
            })
            .frame(width: layout.px(150), height: layout.px(150))
            .position(x: layout.px(1098), y: layout.y(225))
            .accessibilityLabel("Close")

        Button(action: { activate(.paste) }) { Color.clear }
            .buttonStyle(PastePlacesImportPressStyle { pressed in
                pressChanged(.paste, pressed: pressed, phase: phase)
            })
            .frame(width: layout.px(270), height: layout.px(140))
            .position(x: layout.px(605), y: layout.y(2030))
            .accessibilityLabel("Paste")

        Button(action: { activate(.start) }) { Color.clear }
            .buttonStyle(PastePlacesImportPressStyle { pressed in
                pressChanged(.start, pressed: pressed, phase: phase)
            })
            .frame(
                width: layout.px(PastePlacesImportTheme.button.width),
                height: layout.px(PastePlacesImportTheme.button.height)
            )
            .position(
                x: layout.px(PastePlacesImportTheme.button.x + PastePlacesImportTheme.button.width / 2),
                y: layout.y(PastePlacesImportTheme.button.y + PastePlacesImportTheme.button.height / 2)
            )
            .accessibilityLabel("Start import")
    }

    /// The real text field over the card: the note's font, size and left edge, live only at rest.
    private func textArea(layout: PastePlacesImportLayout, phase: Double) -> some View {
        let note = PastePlacesImportTheme.Note.self
        let box = layout.lineBox(size: note.size, baseline: note.firstBaseline)
        let font = PastePlacesImportAssets.font(PastePlacesImportTheme.textFontName, size: box.fontSize)
        // Fixed line heights put the extra space above the glyphs; move them to the painted note's half-leading.
        let shift = (box.lineHeight + font.descender) - box.baselineInside
        let width = layout.px(PastePlacesImportTheme.Input.right - PastePlacesImportTheme.Input.left)
        let height = layout.y(PastePlacesImportTheme.Input.bottom) - box.top
        return PastePlacesImportTextArea(
            text: $draft,
            focused: $focused,
            live: PastePlacesImportPlayback.atRest(phase),
            resignToken: resignToken,
            fontSize: box.fontSize,
            lineHeight: box.lineHeight,
            baselineOffset: shift
        )
        .frame(width: width, height: height)
        .position(x: layout.px(PastePlacesImportTheme.Input.left) + width / 2, y: box.top + height / 2)
    }

    private func inTextArea(_ point: CGPoint, layout: PastePlacesImportLayout) -> Bool {
        let input = PastePlacesImportTheme.Input.self
        let card = PastePlacesImportTheme.card
        let ref = (x: point.x / layout.unit, y: (point.y - layout.deltaY) / layout.unit)
        let inCard = ref.x >= card.x && ref.x <= card.x + card.width && ref.y >= card.y && ref.y <= card.y + card.height
        let onPaste = ref.x >= 470 && ref.x <= 740 && ref.y >= 1960 && ref.y <= 2100
        return (inCard && ref.y <= input.bottom + 30) || onPaste
    }

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    private func activate(_ control: PastePlacesImportPlayback.Control) {
        let hasDraft = !draft.isEmpty
        if reduceMotion {
            activateReduced(control)
            return
        }
        let current = playback.phase(at: clock)
        guard playback.isEnabled(control, at: current, hasDraft: hasDraft) else {
            playback.endTouchWithoutAction(at: clock)
            refreshCadence()
            return
        }
        switch control {
        case .paste:
            lightHaptics += 1
            // The clipboard decides: its text goes into the text area, or (empty) the sample note is pasted.
            if let text = clipboardText() {
                draft = String((draft + text).prefix(PastePlacesImportTheme.Input.max))
                playback.endTouchWithoutAction(at: clock)
                refreshCadence()
                return
            }
        case .start:
            // The demo only knows the sample note's places: the user's own text is cleared and the sample imported.
            if hasDraft {
                draft = ""
                resignToken += 1
            }
        case .close:
            lightHaptics += 1
        }
        playback.activate(control, at: clock, hasDraft: hasDraft)
        refreshCadence()
    }

    /// The clipboard's text, if it holds any (checked without a paste prompt first).
    private func clipboardText() -> String? {
        guard UIPasteboard.general.hasStrings, let text = UIPasteboard.general.string,
              !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return text
    }

    private func pressChanged(
        _ control: PastePlacesImportPlayback.Control,
        pressed: Bool,
        phase: Double
    ) {
        guard !reduceMotion else { return }
        if pressed { playback.beginTouch(at: clock) }
        let enabled = playback.isEnabled(control, at: phase, hasDraft: !draft.isEmpty)
        withAnimation(.timingCurve(
            0.23,
            1,
            0.32,
            1,
            duration: pressed ? PastePlacesImportMotion.pressIn : PastePlacesImportMotion.pressOut
        )) {
            let value = pressed && enabled ? 1.0 : 0.0
            switch control {
            case .paste: pastePress = value
            case .start: startPress = value
            case .close: closePress = value
            }
        }
        if !pressed, playback.isHeld { playback.endTouchWithoutAction(at: clock) }
        refreshCadence()
    }

    private func activateReduced(_ control: PastePlacesImportPlayback.Control) {
        guard playback.isEnabled(control, at: reducedPhase, hasDraft: !draft.isEmpty) else { return }
        let destination: Double
        switch control {
        case .paste:
            if let text = clipboardText() {
                draft = String((draft + text).prefix(PastePlacesImportTheme.Input.max))
                return
            }
            destination = PastePlacesImportMotion.filledAt
        case .start:
            if !draft.isEmpty {
                draft = ""
                resignToken += 1
            }
            destination = PastePlacesImportMotion.foundAtTime
        case .close: destination = 0
        }
        reducedTask?.cancel()
        reducedTask = Task { @MainActor in
            withAnimation(.linear(duration: PastePlacesImportMotion.reducedFade)) { reducedOpacity = 0 }
            do { try await Task.sleep(for: .seconds(PastePlacesImportMotion.reducedFade)) } catch { return }
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) { reducedPhase = destination }
            withAnimation(.linear(duration: PastePlacesImportMotion.reducedFade)) { reducedOpacity = 1 }
        }
    }

    private func resetPresses(animated: Bool) {
        let updates = {
            pastePress = 0
            startPress = 0
            closePress = 0
        }
        if animated {
            withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: PastePlacesImportMotion.pressOut)) {
                updates()
            }
        } else {
            updates()
        }
    }

    private func refreshCadence() {
        wakeTask?.cancel()
        let now = clock
        let moving = !reduceMotion && scenePhase == .active && playback.shouldTick(at: now)
        timelinePaused = !moving
        guard !moving, !reduceMotion, scenePhase == .active,
              let delay = playback.nextWakeDelay(at: now) else { return }
        wakeTask = Task { @MainActor in
            do { try await Task.sleep(for: .seconds(max(0.001, delay))) } catch { return }
            timelinePaused = false
        }
    }

}
