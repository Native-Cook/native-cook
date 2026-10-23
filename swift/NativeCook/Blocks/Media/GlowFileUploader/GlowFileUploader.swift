import SwiftUI

private typealias M = GlowFileUploaderMotion
private typealias P = GlowFileUploaderPalette
private typealias RowMetrics = GlowFileUploaderRowMetrics

private enum Metrics {
    static let side = 20.0
    static let titleTop = 28.0
    static let titleSize = 50.0
    static let titleLine = 54.0
    static let titleTracking = -1.75
    static let titleGap = 16.0
    static let zoneHeight = 162.0
    static let zoneRadius = 28.0
    static let listGap = 24.0
    // The drop zone's content, from its top.
    static let badge = 32.0
    static let badgeCenter = 34.0
    static let badgeBorder = 1.5
    static let badgeGlyph = 17.0
    static let promptGap = 12.0
    static let promptSize = 15.0
    static let hintGap = 2.0
    static let hintSize = 10.5
    static let pickGap = 13.0
    static let pickSize = 11.5
    static let pickWidth = 104.0
    static let pickHeight = 34.0
    // The buttons: their bottom this far above the bottom safe area's edge, or above the screen's
    // bottom when there is no inset.
    static let buttonHeight = 48.0
    static let buttonLabel = 12.5
    static let buttonRadius = 22.0
    static let cancelWidth = 93.0
    static let buttonGap = 8.0
    static let bottomInset = 0.0
    static let bottomBare = 16.0
}

/// A file upload screen in the dark: a two-line grey heading, a drop zone lit from inside its edge,
/// the files being sent in rows below it (a ring while sending, a green check when done, a red
/// "Upload failed" pill with "Retry" when not), and "Cancel" and "Continue" at the bottom.
struct GlowFileUploader: View {
    var titleLines = ["Upload a file", "to start"]
    var prompt = "Add files to upload"
    var pick = "Select files"
    /// Adds files, retries the one that fails and continues by itself every `loop` seconds.
    var autoPlay = false
    /// Called with the files' names when "Continue" is pressed.
    var onContinue: (([String]) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    /// The events made by hand; the autoPlay's come from its loop start, nil while a touch holds it off.
    @State private var hand = M.Events()
    @State private var loopStart: Double? = nil
    /// Where the ambient clock (glow, hairlines, halo) counts from: the first loop's start.
    @State private var origin = Date.now.timeIntervalSinceReferenceDate
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Keeps the clock running after a touch until everything is still.
    @State private var awake = false
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var noticeHaptic = UINotificationFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let content = width - Metrics.side * 2
            let titleTop = insets.top + Metrics.titleTop
            let zoneTop = titleTop + Metrics.titleLine * Double(titleLines.count) + Metrics.titleGap
            let listTop = zoneTop + Metrics.zoneHeight + Metrics.listGap
            let buttonsBottom = height - (insets.bottom > 0 ? insets.bottom - Metrics.bottomInset : Metrics.bottomBare)

            ZStack(alignment: .topLeading) {
                P.background

                title(width: content)
                    .offset(x: Metrics.side, y: titleTop)

                TimelineView(.animation(paused: loopStart == nil && !awake)) { timeline in
                    let now = timeline.date.timeIntervalSinceReferenceDate
                    let e = events(at: now)
                    ZStack(alignment: .topLeading) {
                        zone(width: content, now: now, e: e)
                            .offset(x: Metrics.side, y: zoneTop)
                        rows(width: content, now: now, e: e)
                            .offset(x: Metrics.side, y: listTop)
                        buttons(width: content, now: now, e: e)
                            .offset(x: Metrics.side, y: buttonsBottom - Metrics.buttonHeight)
                    }
                    .frame(width: width, height: height, alignment: .topLeading)
                    .onChange(of: M.statusKey(e, at: now)) { old, new in feel(old, new) }
                }
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            tapHaptic.prepare()
            noticeHaptic.prepare()
        }
        .task(id: GlowFileUploaderLoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: Parts

    /// The heading, one gradient across both lines: dim grey at the top to light grey at the bottom.
    private func title(width: Double) -> some View {
        LinearGradient(colors: [P.titleTop, P.titleBottom], startPoint: .top, endPoint: .bottom)
            .frame(width: width, height: Metrics.titleLine * Double(titleLines.count))
            .mask(alignment: .topLeading) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(titleLines.indices, id: \.self) { i in
                        Text(titleLines[i])
                            .font(.system(size: Metrics.titleSize, weight: .medium))
                            .tracking(Metrics.titleTracking)
                            .lineLimit(1)
                            .minimumScaleFactor(0.5)
                            // The line box is shorter than the font's own, so let the text keep its
                            // height (else it is shrunk to fit it) and only shrink for a narrow screen.
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(height: Metrics.titleLine)
                    }
                }
            }
            .accessibilityElement()
            .accessibilityLabel(titleLines.joined(separator: " "))
            .accessibilityAddTraits(.isHeader)
    }

    private func zone(width: Double, now: Double, e: M.Events) -> some View {
        let flare = M.flare(e, now: now, still: still)
        let full = M.selectFull(e, now: now)
        let canAdd = M.present(e, at: now).count < M.maxRows
        let shape = RoundedRectangle(cornerRadius: Metrics.zoneRadius, style: .continuous)
        return Button(action: addFile) {
            ZStack(alignment: .top) {
                GlowFileUploaderBackdrop(
                    origin: origin,
                    flare: flare,
                    still: still,
                    radius: Metrics.zoneRadius,
                    badgeCenter: Metrics.badgeCenter,
                    badgeSize: Metrics.badge
                )
                VStack(spacing: 0) {
                    badge(now: now, e: e)
                        .padding(.top, Metrics.badgeCenter - Metrics.badge / 2)
                    Text(prompt)
                        .font(.system(size: Metrics.promptSize, weight: .semibold))
                        .foregroundStyle(P.ink)
                        .padding(.top, Metrics.promptGap)
                    Text("or \(Text("browse").underline().foregroundStyle(P.link)), 4 MB max")
                        .font(.system(size: Metrics.hintSize))
                        .foregroundStyle(P.hint)
                        .padding(.top, Metrics.hintGap)
                    pickPill(full: full, now: now, e: e)
                        .padding(.top, Metrics.pickGap)
                }
            }
            .frame(width: width, height: Metrics.zoneHeight)
            .contentShape(shape)
        }
        .buttonStyle(GlowFileUploaderPressStyle { down in press(\.select, down) })
        .disabled(!canAdd)
        .scaleEffect(1 + M.flareScale * flare)
        .accessibilityLabel(pick)
    }

    private func badge(now: Double, e: M.Events) -> some View {
        ZStack {
            if let pulse = M.pulse(e, now: now, still: still) {
                Circle()
                    .stroke(.white, lineWidth: 1.5)
                    .frame(width: Metrics.badge * pulse.scale, height: Metrics.badge * pulse.scale)
                    .opacity(pulse.opacity)
            }
            ZStack {
                Circle().fill(P.badge)
                Circle().strokeBorder(P.badgeBorder, lineWidth: Metrics.badgeBorder)
                GlowFileUploaderClip()
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
                    .frame(width: Metrics.badgeGlyph, height: Metrics.badgeGlyph)
            }
            .frame(width: Metrics.badge, height: Metrics.badge)
            .rotationEffect(.degrees(M.badgeTurn(e, now: now, still: still)))
        }
        .frame(width: Metrics.badge, height: Metrics.badge)
    }

    private func pickPill(full: Double, now: Double, e: M.Events) -> some View {
        let dim = 1 - (1 - M.disabledOpacity) * full
        return Text(pick)
            .font(.system(size: Metrics.pickSize, weight: .medium))
            .foregroundStyle(P.ink)
            .opacity(dim)
            .frame(width: Metrics.pickWidth, height: Metrics.pickHeight)
            .background(Capsule().fill(P.pill))
            .overlay(Capsule().strokeBorder(P.pillBorder.opacity(dim), lineWidth: 1))
            .scaleEffect(M.pressScale(e.select, now: now, scale: M.selectPressScale))
    }

    private func rows(width: Double, now: Double, e: M.Events) -> some View {
        var y = 0.0
        var placed: [(row: M.Row, pose: M.RowPose, y: Double)] = []
        for r in e.rows {
            let pose = M.rowPose(r, now: now, still: still)
            placed.append((r, pose, y))
            y += (RowMetrics.height + RowMetrics.gap) * pose.slot
        }
        return ZStack(alignment: .topLeading) {
            ForEach(placed, id: \.row.id) { item in
                GlowFileUploaderRow(
                    name: M.files[item.row.file].name,
                    pose: item.pose,
                    width: width,
                    retryScale: M.pressScale(item.row.retry, now: now, scale: M.retryPressScale),
                    onRetryPress: { down in pressRetry(item.row.id, down) },
                    onRetry: { retry(item.row.id) }
                )
                .offset(x: item.pose.shake)
                .scaleEffect(item.pose.scale)
                .offset(y: item.y + item.pose.y)
                .opacity(item.pose.opacity)
                .allowsHitTesting(!item.pose.leaving)
                .accessibilityHidden(item.pose.leaving)
            }
        }
        .frame(width: width, alignment: .topLeading)
    }

    private func buttons(width: Double, now: Double, e: M.Events) -> some View {
        let cancelOn = M.cancelOn(e, now: now)
        let go = M.proceed(e, now: now, still: still)
        let shape = RoundedRectangle(cornerRadius: Metrics.buttonRadius, style: .continuous)
        return HStack(spacing: Metrics.buttonGap) {
            Button(action: cancel) {
                Text("Cancel")
                    .font(.system(size: Metrics.buttonLabel))
                    .foregroundStyle(P.text)
                    .opacity(1 - (1 - M.disabledOpacity) * (1 - cancelOn))
                    .frame(width: Metrics.cancelWidth, height: Metrics.buttonHeight)
                    .background(shape.fill(P.cancel))
                    .overlay(shape.strokeBorder(P.cancelBorder, lineWidth: 1))
                    .contentShape(shape)
            }
            .buttonStyle(GlowFileUploaderPressStyle { down in press(\.cancel, down) })
            .disabled(cancelOn < 1 && M.present(e, at: now).isEmpty)
            .scaleEffect(M.pressScale(e.cancel, now: now, scale: M.buttonPressScale))

            Button(action: proceed) {
                ZStack {
                    Text("Continue")
                        .font(.system(size: Metrics.buttonLabel, weight: .medium))
                        .foregroundStyle(P.mix(P.proceedLabelOff, P.proceedLabelOn, go.on))
                        .opacity(1 - go.uploaded)
                        .accessibilityHidden(go.uploaded >= 0.5)
                    HStack(spacing: 6) {
                        GlowFileUploaderCheck()
                            .stroke(P.green, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                            .frame(width: 15, height: 15)
                        Text("Uploaded")
                            .font(.system(size: Metrics.buttonLabel, weight: .medium))
                            .foregroundStyle(.white)
                    }
                    .opacity(go.uploaded)
                    .accessibilityHidden(go.uploaded < 0.5)
                }
                .frame(maxWidth: .infinity)
                .frame(height: Metrics.buttonHeight)
                .background(shape.fill(P.mix(P.proceedOff, P.proceedOn, go.on)))
                .overlay {
                    if let k = go.sheen {
                        GeometryReader { g in
                            let travel = g.size.width + M.sheenWidth * 2
                            LinearGradient(
                                colors: [.white.opacity(0), .white.opacity(0.12), .white.opacity(0)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                            .frame(width: M.sheenWidth, height: g.size.height * 2)
                            .rotationEffect(.degrees(20))
                            .position(x: -M.sheenWidth / 2 + travel * k - M.sheenWidth / 2, y: g.size.height / 2)
                        }
                        .clipShape(shape)
                        .allowsHitTesting(false)
                    }
                }
                .contentShape(shape)
            }
            .buttonStyle(GlowFileUploaderPressStyle { down in press(\.proceed, down) })
            .disabled(!go.enabled)
            .scaleEffect(M.pressScale(e.proceed, now: now, scale: M.buttonPressScale))
            .accessibilityLabel(go.uploaded > 0.5 ? "Uploaded" : "Continue")
        }
        .frame(width: width)
    }

    // MARK: Touch

    private func events(at now: Double) -> M.Events {
        guard let start = loopStart else { return hand }
        return M.autoEvents(now: now, start: start)
    }

    private func press(_ key: WritableKeyPath<M.Events, M.Press>, _ down: Bool) {
        takeOver()
        let now = Date.now.timeIntervalSinceReferenceDate
        if down { hand[keyPath: key].downAt = now } else { hand[keyPath: key].upAt = now }
    }

    private func pressRetry(_ id: Int, _ down: Bool) {
        takeOver()
        guard let i = hand.rows.firstIndex(where: { $0.id == id }), !hand.rows[i].retried else { return }
        let now = Date.now.timeIntervalSinceReferenceDate
        if down { hand.rows[i].retry.downAt = now } else { hand.rows[i].retry.upAt = now }
    }

    private func addFile() {
        takeOver()
        let now = Date.now.timeIntervalSinceReferenceDate
        guard M.present(hand, at: now).count < M.maxRows else { return }
        tapHaptic.impactOccurred()
        hand.select.upAt = now
        hand.rows.append(M.Row(id: hand.nextID, file: hand.nextFile, addedAt: now))
        hand.nextID += 1
        hand.nextFile = (hand.nextFile + 1) % M.files.count
    }

    private func retry(_ id: Int) {
        takeOver()
        let now = Date.now.timeIntervalSinceReferenceDate
        guard let i = hand.rows.firstIndex(where: { $0.id == id }),
              M.rowPose(hand.rows[i], now: now, still: still).canRetry else { return }
        tapHaptic.impactOccurred()
        hand.rows[i].retriedAt = now
    }

    private func cancel() {
        takeOver()
        let now = Date.now.timeIntervalSinceReferenceDate
        guard !M.present(hand, at: now).isEmpty else { return }
        tapHaptic.impactOccurred()
        hand = M.leave(hand, batch: now, now: now)
    }

    private func proceed() {
        takeOver()
        let now = Date.now.timeIntervalSinceReferenceDate
        guard M.proceed(hand, now: now, still: still).enabled else { return }
        tapHaptic.impactOccurred()
        onContinue?(M.present(hand, at: now).map { M.files[$0.file].name })
        hand.uploadedAt = now
        hand = M.leave(hand, batch: now + M.uploadedFor, now: now)
    }

    /// A file finishing or failing is felt, when a hand is on the screen (never in the autoPlay).
    private func feel(_ old: [Int], _ new: [Int]) {
        guard loopStart == nil else { return }
        let before = Dictionary(old.map { ($0 / 4, $0 % 4) }, uniquingKeysWith: { a, _ in a })
        for code in new {
            guard before[code / 4] == M.Status.uploading.rawValue else { continue }
            if code % 4 == M.Status.done.rawValue { noticeHaptic.notificationOccurred(.success) }
            if code % 4 == M.Status.failed.rawValue { noticeHaptic.notificationOccurred(.error) }
        }
    }

    /// A touch takes over from the autoPlay loop, keeping what is on screen; the loop starts again
    /// from rest a while after the last touch.
    private func takeOver() {
        let now = Date.now.timeIntervalSinceReferenceDate
        if let start = loopStart {
            hand = M.autoEvents(now: now, start: start)
            loopStart = nil
        }
        hand.rows.removeAll { now >= $0.leaveAt + M.collapseFor }
        awake = true
        touches += 1
    }

    private func sleep(_ seconds: Double) async {
        guard seconds > 0 else { return }
        try? await Task.sleep(for: .seconds(seconds), tolerance: .zero)
    }

    private func loop() async {
        guard autoPlay else {
            // Let the clock stop once a touch has played out.
            guard touches > 0 else { return }
            await sleep(M.quietAt(hand) - Date.now.timeIntervalSinceReferenceDate)
            if !Task.isCancelled, !M.anyPressed(hand) { awake = false }
            return
        }
        if touches > 0 {
            await sleep(M.userHold)
            // Wait for every upload (and a "Continue" in progress) to finish; a finger still down
            // comes back here when it lifts.
            while !Task.isCancelled {
                if M.anyPressed(hand) { return }
                let now = Date.now.timeIntervalSinceReferenceDate
                let until = M.busyUntil(hand, now: now)
                if until <= now { break }
                await sleep(until - now + 0.05)
            }
            guard !Task.isCancelled else { return }
            // Back to rest the way "Cancel" goes there, then the loop starts again.
            let now = Date.now.timeIntervalSinceReferenceDate
            if !M.present(hand, at: now).isEmpty { hand = M.leave(hand, batch: now, now: now) }
            await sleep(M.goneAt(hand) - Date.now.timeIntervalSinceReferenceDate)
            guard !Task.isCancelled else { return }
        }
        let now = Date.now.timeIntervalSinceReferenceDate
        if touches == 0 { origin = now }
        hand = M.Events()
        loopStart = now
    }
}

private struct GlowFileUploaderLoopKey: Equatable {
    let on: Bool
    let touches: Int
}

#Preview {
    GlowFileUploader(autoPlay: true)
}
