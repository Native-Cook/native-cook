import SwiftUI
import UIKit

struct FindFriendsInvite: View {
    typealias M = FindFriendsInviteMotion
    static let loop = M.loop
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var playback: FindFriendsInvitePlayback
    @State private var timelineActive: Bool
    @State private var mount = Date.timeIntervalSinceReferenceDate
    @State private var inviteTouch = false
    @State private var scanTouch = false
    @State private var settleTask: Task<Void, Never>?
    @State private var inviteHapticTask: Task<Void, Never>?
    @State private var scanHapticTask: Task<Void, Never>?
    @State private var inviteHapticDelivered = true
    @State private var scanHapticDelivered = true

    init(autoPlay: Bool = false) {
        FindFriendsInviteAssets.prepare()
        self.autoPlay = autoPlay
        _playback = State(initialValue: FindFriendsInvitePlayback(autoPlay: autoPlay, at: Date.timeIntervalSinceReferenceDate))
        _timelineActive = State(initialValue: autoPlay)
    }

    /// Reduce Motion and the screen reader, followed live: either stops the loop.
    private var calm: Bool { reduceMotion || voiceOver }
    private var looping: Bool { autoPlay && !calm }
    private static let warmSeconds = Double(M.Warm.frames + 1) / 60

    private var scheduleKind: FindFriendsInviteSchedule.Kind {
        guard timelineActive, scenePhase == .active else { return .paused }
        if let anchor = playback.loopAnchor { return .loop(anchor: anchor) }
        return .running
    }

    var body: some View {
        GeometryReader { geometry in
            let top = geometry.safeAreaInsets.top
            let height = geometry.size.height + top + geometry.safeAreaInsets.bottom
            TimelineView(FindFriendsInviteSchedule(kind: scheduleKind, warmUntil: mount + Self.warmSeconds)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                FindFriendsInviteFrame(width: geometry.size.width, height: height,
                                       safeTop: top, reduced: reduceMotion,
                                       warm: now < mount + Self.warmSeconds,
                                       clocks: playback.clocks(at: now, reduced: reduceMotion),
                                       onInviteIn: beginInvite, onInviteOut: releaseInvite,
                                       onScanIn: beginScan, onScanOut: releaseScan,
                                       onCupTap: tapCup)
                    .frame(width: geometry.size.width, height: height, alignment: .top)
                    .offset(y: -top)
            }
        }
        .background(FindFriendsInviteTheme.ColorToken.card2.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            let now = Date.timeIntervalSinceReferenceDate
            mount = now
            playback.setAutoPlay(looping, at: now, reduced: reduceMotion)
            playback.restart(at: now)
            playback.setForeground(scenePhase == .active, at: now)
            timelineActive = looping && scenePhase == .active
        }
        .onDisappear {
            settleTask?.cancel()
            inviteHapticTask?.cancel()
            scanHapticTask?.cancel()
            settleTask = nil
            inviteHapticTask = nil
            scanHapticTask = nil
            timelineActive = false
        }
        .onChange(of: looping) { _, on in
            let now = Date.timeIntervalSinceReferenceDate
            playback.setAutoPlay(on, at: now, reduced: reduceMotion)
            timelineActive = scenePhase == .active && (on || playback.finish(at: now, reduced: reduceMotion) != nil)
            scheduleSettle(at: now)
        }
        .onChange(of: scenePhase) { _, phase in
            let now = Date.timeIntervalSinceReferenceDate
            playback.setForeground(phase == .active, at: now)
            if phase == .active {
                timelineActive = looping || playback.finish(at: now, reduced: reduceMotion) != nil
                scheduleSettle(at: now)
                scheduleInviteHaptic(at: now)
                scheduleScanHaptic(at: now)
            } else {
                timelineActive = false
                settleTask?.cancel()
                inviteHapticTask?.cancel()
                scanHapticTask?.cancel()
            }
        }
        .onChange(of: reduceMotion) { _, _ in
            // Pending haptics were timed for the other mode's clocks.
            let now = Date.timeIntervalSinceReferenceDate
            inviteHapticTask?.cancel()
            scanHapticTask?.cancel()
            inviteHapticDelivered = true
            scanHapticDelivered = true
            scheduleSettle(at: now)
        }
    }

    private func beginInvite() {
        guard !inviteTouch else { return }
        let now = Date.timeIntervalSinceReferenceDate
        let accepted = playback.beginInvite(at: now, reduced: reduceMotion)
        timelineActive = scenePhase == .active
        settleTask?.cancel()
        guard accepted else {
            scheduleSettle(at: now)
            return
        }
        inviteTouch = true
        inviteHapticDelivered = false
        if reduceMotion { scheduleInviteHaptic(at: now) }
    }

    private func releaseInvite() {
        guard inviteTouch else { return }
        inviteTouch = false
        let now = Date.timeIntervalSinceReferenceDate
        if playback.releaseInvite(at: now, reduced: reduceMotion) { scheduleInviteHaptic(at: now) }
        scheduleSettle(at: now)
    }

    private func beginScan() {
        guard !scanTouch else { return }
        let now = Date.timeIntervalSinceReferenceDate
        let accepted = playback.beginScan(at: now, reduced: reduceMotion)
        timelineActive = scenePhase == .active
        settleTask?.cancel()
        guard accepted else {
            scheduleSettle(at: now)
            return
        }
        scanTouch = true
        scanHapticDelivered = false
        if reduceMotion { scheduleScanHaptic(at: now) }
    }

    private func releaseScan() {
        guard scanTouch else { return }
        scanTouch = false
        let now = Date.timeIntervalSinceReferenceDate
        if playback.releaseScan(at: now, reduced: reduceMotion) { scheduleScanHaptic(at: now) }
        scheduleSettle(at: now)
    }

    private func tapCup() {
        let now = Date.timeIntervalSinceReferenceDate
        if playback.tapCup(at: now, reduced: reduceMotion) {
            Self.impact()
            timelineActive = scenePhase == .active
        }
        scheduleSettle(at: now)
    }

    /// After the last touch has played out, the loop comes back 2 s later (only while the loop is on).
    private func scheduleSettle(at now: TimeInterval) {
        settleTask?.cancel()
        guard let finish = playback.finish(at: now, reduced: reduceMotion) else { return }
        settleTask = Task { @MainActor in
            let delay = max(0, finish - Date.timeIntervalSinceReferenceDate)
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled else { return }
            timelineActive = false
            guard looping, scenePhase == .active else { return }
            try? await Task.sleep(nanoseconds: UInt64(M.resumeAfter * 1_000_000_000))
            guard !Task.isCancelled, looping else { return }
            let again = Date.timeIntervalSinceReferenceDate
            playback.setAutoPlay(true, at: again, reduced: reduceMotion)
            playback.restart(at: again)
            timelineActive = true
        }
    }

    private func scheduleInviteHaptic(at now: TimeInterval) {
        inviteHapticTask?.cancel()
        guard !inviteHapticDelivered,
              let delay = playback.inviteHapticDelay(at: now, reduced: reduceMotion) else { return }
        inviteHapticTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled else { return }
            inviteHapticDelivered = true
            Self.impact()
        }
    }

    private func scheduleScanHaptic(at now: TimeInterval) {
        scanHapticTask?.cancel()
        guard !scanHapticDelivered,
              let delay = playback.scanHapticDelay(at: now, reduced: reduceMotion) else { return }
        scanHapticTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled else { return }
            scanHapticDelivered = true
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    private static func impact() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

/// The timeline's dates: every frame while something moves, a single frame while nothing does. In
/// the loop, the stretch of rest at its end and start (6.9 s to 0.5 s) is skipped.
struct FindFriendsInviteSchedule: TimelineSchedule {
    enum Kind { case paused, running, loop(anchor: TimeInterval) }
    let kind: Kind
    let warmUntil: TimeInterval

    func entries(from startDate: Date, mode: TimelineScheduleMode) -> Entries {
        Entries(current: startDate, kind: kind, warmUntil: warmUntil)
    }

    struct Entries: Sequence, IteratorProtocol {
        var current: Date?
        let kind: Kind
        let warmUntil: TimeInterval
        private static let step = 1.0 / 60

        init(current: Date, kind: Kind, warmUntil: TimeInterval) {
            self.current = current
            self.kind = kind
            self.warmUntil = warmUntil
        }

        private func loopTime(_ d: Date, _ anchor: TimeInterval) -> Double {
            let loop = FindFriendsInviteMotion.loop
            return ((d.timeIntervalSinceReferenceDate - anchor).truncatingRemainder(dividingBy: loop) + loop)
                .truncatingRemainder(dividingBy: loop)
        }

        private func resting(_ t: Double) -> Bool {
            t >= FindFriendsInviteMotion.restFrom || t < FindFriendsInviteMotion.restUntil
        }

        mutating func next() -> Date? {
            guard let d = current else { return nil }
            let following = d.addingTimeInterval(Self.step)
            if following.timeIntervalSinceReferenceDate < warmUntil {
                current = following
                return d
            }
            switch kind {
            case .paused:
                current = nil
            case .running:
                current = following
            case .loop(let anchor):
                if !resting(loopTime(following, anchor)) {
                    current = following
                } else if resting(loopTime(d, anchor)) {
                    let t = loopTime(d, anchor)
                    let loop = FindFriendsInviteMotion.loop
                    let wait = t >= FindFriendsInviteMotion.restFrom
                        ? loop - t + FindFriendsInviteMotion.restUntil
                        : FindFriendsInviteMotion.restUntil - t
                    current = d.addingTimeInterval(wait)
                } else {
                    current = following
                }
            }
            return d
        }
    }
}

private struct FindFriendsInviteFrame: View {
    typealias T = FindFriendsInviteTheme
    typealias M = FindFriendsInviteMotion
    typealias Fold = FindFriendsInviteFold
    typealias Goo = FindFriendsInviteGoo

    let width: Double
    let height: Double
    let safeTop: Double
    let reduced: Bool
    let warm: Bool
    let clocks: M.Clocks
    let onInviteIn: () -> Void
    let onInviteOut: () -> Void
    let onScanIn: () -> Void
    let onScanOut: () -> Void
    let onCupTap: () -> Void

    private var dx: Double { width / 2 - T.referenceWidth / 2 }
    private var dy: Double { safeTop - T.referenceTop }
    private func y(_ reference: Double) -> Double { reference + dy }
    private var cardWidth: Double { width - 2 * T.Space.cardMargin }
    private var inviteRect: CGRect {
        CGRect(x: T.Space.cardMargin + T.Space.cardPadInvite,
               y: y(T.Box.invite.y), width: cardWidth - 2 * T.Space.cardPadInvite, height: T.Box.invite.height)
    }
    private var scanRect: CGRect {
        CGRect(x: T.Space.cardMargin + T.Space.cardPadCheck,
               y: y(T.Box.check.y), width: cardWidth - 2 * T.Space.cardPadCheck, height: T.Box.check.height)
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            pageBackground
            searchField
            RoundedRectangle(cornerRadius: T.Radius.card).fill(T.ColorToken.card1)
                .frame(width: cardWidth, height: T.Box.card1.height)
                .position(x: width / 2, y: y(T.Box.card1.y + T.Box.card1.height / 2))
                .accessibilityHidden(true)
            cup
            line(T.Copy.h1, style: T.TypeToken.h1, baseline: T.Box.h1Baseline, centerX: width / 2)
            inviteButton
            RoundedRectangle(cornerRadius: T.Radius.card).fill(T.ColorToken.card2)
                .frame(width: cardWidth, height: T.Box.card2.height)
                .position(x: width / 2, y: y(T.Box.card2.y + T.Box.card2.height / 2))
                .accessibilityHidden(true)
            avatars
            line(T.Copy.h2, style: T.TypeToken.h2, baseline: T.Box.h2Baseline, centerX: width / 2)
            bodyCopy
            scanButton
            overlay
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var pageBackground: some View {
        let stops = T.ColorToken.pageStops.map { position, color in
            Gradient.Stop(color: color,
                          location: min(1, (position + dy * position / 874) / max(height, 874)))
        }
        return LinearGradient(stops: stops, startPoint: .top, endPoint: .bottom)
            .frame(width: width, height: height)
            .accessibilityHidden(true)
    }

    private var searchField: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: T.Radius.pill)
                .fill(T.ColorToken.ink)
                .frame(width: T.Box.pill.width, height: T.Box.pill.height)
                .shadow(color: .black.opacity(T.Box.pillShadow.opacity), radius: T.Box.pillShadow.blur,
                        x: 0, y: T.Box.pillShadow.dy)
                .position(x: T.Box.pill.midX, y: y(T.Box.pill.midY))
            line(T.Copy.placeholder, style: T.TypeToken.placeholder,
                 baseline: T.Box.placeholder.baseline, leadingX: T.Box.placeholder.x)
            FindFriendsInviteCloseShape()
                .stroke(T.ColorToken.ink, style: StrokeStyle(lineWidth: T.Box.close.stroke, lineCap: .round))
                .frame(width: T.Box.close.size, height: T.Box.close.size)
                .position(x: T.Box.close.cx, y: y(T.Box.close.cy))
                .accessibilityHidden(true)
        }
    }

    private var cup: some View {
        let angle = reduced ? 0 :
            M.Cup.amp * M.kick(clocks.cup, period: M.Cup.period, decay: M.Cup.decay, fadeFrom: M.Cup.fadeFrom, end: M.Cup.end) +
            M.Cup.tapAmp * M.kick(clocks.cupTap, period: M.Cup.period, decay: M.Cup.decay, fadeFrom: M.Cup.fadeFrom, end: M.Cup.end)
        let shift = reduced ? 0 : M.Cup.shiftX * M.kick(clocks.cup - M.Cup.shiftLag, period: M.Cup.period,
                                                        decay: M.Cup.decay, fadeFrom: M.Cup.fadeFrom, end: M.Cup.end)
        return Button(action: onCupTap) {
            Image(uiImage: FindFriendsInviteAssets.image("cup"))
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: T.Box.cup.width, height: T.Box.cup.height)
                .rotationEffect(.degrees(angle))
                .offset(x: shift)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .frame(width: T.Box.cup.width, height: T.Box.cup.height)
        .position(x: T.Box.cup.cx + dx, y: y(T.Box.cup.cy))
        .accessibilityLabel("Coffee cup")
    }

    private var avatars: some View {
        let shown = reduced || Goo.shown(avatarsAt: clocks.scan)
        return ForEach(Array(T.Box.avatars.enumerated()), id: \.element.id) { _, avatar in
            ZStack {
                Circle().fill(avatar.background)
                Image(uiImage: FindFriendsInviteAssets.image(avatar.id))
                    .resizable().aspectRatio(contentMode: .fill)
                    .frame(width: avatar.diameter * avatar.head, height: avatar.diameter * avatar.head)
                    .offset(y: avatar.dy * avatar.diameter)
            }
            .frame(width: avatar.diameter, height: avatar.diameter)
            .clipShape(Circle())
            .opacity(shown ? 1 : 0)
            .position(x: avatar.centerX + dx, y: y(avatar.centerY))
            .accessibilityHidden(true)
        }
    }

    private var bodyCopy: some View {
        let medium = T.TypeToken.body
        let strong = T.TypeToken.bodyStrong
        return ZStack(alignment: .topLeading) {
            line(T.Copy.body1, style: medium, baseline: T.Box.bodyBaselines[0], centerX: width / 2)
            styledLine(Text(T.Copy.body2).font(.custom(medium.font, fixedSize: medium.size)) +
                       Text(T.Copy.body2Strong).font(.custom(strong.font, fixedSize: medium.size)),
                       baseline: T.Box.bodyBaselines[1], style: medium, centerX: width / 2)
            styledLine(Text(T.Copy.body3).font(.custom(strong.font, fixedSize: medium.size)) +
                       Text(T.Copy.lock).font(.system(size: medium.size)),
                       baseline: T.Box.bodyBaselines[2], style: medium, centerX: width / 2)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("See who's already on mellow by checking your contacts. We never upload or store your contacts.")
    }

    private var inviteButton: some View {
        let rect = inviteRect
        let tau = clocks.invite
        let scale = reduced ? 1 : Fold.pressScale(tau, pressIn: M.Invite.pressIn, release: M.Invite.release, to: M.Invite.pressScale)
        let radius = reduced ? T.Radius.invite : Fold.sheetRadius(tau, pill: T.Radius.invite)
        let shown = reduced || Fold.buttonShown(tau)
        let label = reduced ? (1.0, 1.0) : Fold.labelState(tau)
        let slotOpacity = reduced ? 1 - (1 - M.Reduced.planeDip) * M.triangle(tau / M.Reduced.inviteEnd) : 1
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: radius)
                .fill(LinearGradient(stops: T.ColorToken.inviteStops.map { Gradient.Stop(color: $0.1, location: $0.0) },
                                     startPoint: .top, endPoint: .bottom))
                .frame(width: rect.width, height: rect.height)
            ZStack(alignment: .topLeading) {
                FindFriendsInvitePlaneGlyph(fill: T.ColorToken.white, fold: T.ColorToken.planeFold, size: T.Box.plane.size)
                    .opacity(slotOpacity)
                    .offset(x: T.Box.plane.x + dx - rect.minX - 1, y: y(T.Box.plane.y) - rect.minY - 1)
                line(T.Copy.invite, style: T.TypeToken.invite,
                     baseline: T.Box.inviteLabel.baseline - T.Box.invite.y,
                     leadingX: T.Box.inviteLabel.x + dx - rect.minX, yOffset: 0)
            }
            .frame(width: rect.width, height: rect.height, alignment: .topLeading)
            .scaleEffect(label.1)
            .opacity(label.0)
        }
        .frame(width: rect.width, height: rect.height, alignment: .topLeading)
        .opacity(shown ? 1 : 0)
        .scaleEffect(scale)
        .position(x: rect.midX, y: rect.midY)
        .overlay {
            Button(action: { onInviteIn(); onInviteOut() }) { Color.clear.contentShape(Rectangle()) }
                .buttonStyle(FindFriendsInvitePressStyle { pressed in
                    if pressed { onInviteIn() } else { onInviteOut() }
                })
                .frame(width: rect.width, height: rect.height)
                .position(x: rect.midX, y: rect.midY)
                .accessibilityLabel(T.Copy.invite)
        }
    }

    private var scanButton: some View {
        let rect = scanRect
        let tau = clocks.scan
        let scale = reduced ? 1 : Fold.pressScale(tau, pressIn: M.Scan.pressIn, release: M.Scan.release, to: M.Scan.pressScale)
        let fillEnd = M.Scan.fillStart + M.Scan.fill
        let progress = M.seg(tau, M.Scan.fillStart, fillEnd)
        let fillOpacity = reduced ? M.Scan.fillOpacity * M.triangle(tau / M.Reduced.scanEnd) :
            (tau <= M.Scan.fillStart ? 0 : M.Scan.fillOpacity * (1 - M.bezier(M.easeOut, M.seg(tau, fillEnd, fillEnd + M.Scan.fadeDur))))
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: T.Radius.check).fill(T.ColorToken.buttonBlack)
            T.ColorToken.white.opacity(fillOpacity)
                .frame(width: rect.width, height: rect.height)
                .offset(x: reduced ? 0 : -rect.width * (1 - progress))
                .clipShape(RoundedRectangle(cornerRadius: T.Radius.check))
            line(T.Copy.check, style: T.TypeToken.check,
                 baseline: T.Box.checkLabelBaseline - T.Box.check.y,
                 centerX: rect.width / 2, yOffset: 0)
            Button(action: { onScanIn(); onScanOut() }) { Color.clear.contentShape(Rectangle()) }
                .buttonStyle(FindFriendsInvitePressStyle { pressed in
                    if pressed { onScanIn() } else { onScanOut() }
                })
                .frame(width: rect.width, height: rect.height)
                .accessibilityLabel(T.Copy.check)
        }
        .frame(width: rect.width, height: rect.height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: T.Radius.check))
        .scaleEffect(scale)
        .position(x: rect.midX, y: rect.midY)
    }

    // MARK: the folded paper and the liquid contacts

    private var paperGeo: Fold.Geo {
        let r = inviteRect
        let start = M.Flight.points[0]
        return Fold.Geo(W: r.width, H: r.height, left: r.minX, top: r.minY,
                        camX: M.Flight.cameraAt.0 + dx, camY: M.Flight.cameraAt.1 + dy,
                        offX: r.minX + 3 * r.width / 4 - start.0, offY: r.minY + r.height / 2 - start.1)
    }

    private var gooGeo: Goo.Geo {
        let rest = T.Box.avatars.map { Goo.Ball(x: $0.centerX + dx, y: y($0.centerY), r: $0.diameter / 2) }
        var ax = 0.0, ay = 0.0, aw = 0.0
        for b in rest { ax += b.x * b.r * b.r; ay += b.y * b.r * b.r; aw += b.r * b.r }
        return Goo.Geo(rest: rest, cx: ax / aw, cy: ay / aw, width: width)
    }

    private static let gooColors = Goo.colors([0xF298B9, 0xADEEA9, 0xCCC0FB, 0xD5D5D8])

    @ViewBuilder private var overlay: some View {
        if !reduced {
            let inv = warm ? M.Warm.inv : clocks.invite
            let scan = warm ? M.Warm.scan : clocks.scan
            let faces = T.Box.avatars.map {
                FindFriendsInviteFaceSpec(key: $0.id, cx: $0.centerX + dx, cy: y($0.centerY), d: $0.diameter, head: $0.head, dy: $0.dy)
            }
            let drops = M.Goo.drops.enumerated().map { j, dr in
                let key = M.Goo.dropHeads[j]
                let a = T.Box.avatars.first { $0.id == key } ?? T.Box.avatars[0]
                return FindFriendsInviteDropSpec(key: key, head: a.head, dy: a.dy, mirror: M.Goo.dropMirror[j], r: dr.r)
            }
            FindFriendsInviteOverlay(
                paper: Fold.paperOn(inv) ? Fold.frame(inv, paperGeo, pill: T.Radius.invite) : Fold.Frame(),
                paperWidth: inviteRect.width, paperHeight: inviteRect.height,
                goo: Goo.frame(scan, gooGeo, Self.gooColors), gooGeo: gooGeo,
                faces: faces, dropFaces: drops, width: width, height: height, warm: warm)
        }
    }

    private func line(_ value: String, style: T.TypeStyle, baseline: Double,
                      centerX: Double? = nil, leadingX: Double? = nil, yOffset: Double? = nil) -> some View {
        styledLine(Text(value).font(.custom(style.font, fixedSize: style.size)), baseline: baseline,
                   style: style, centerX: centerX, leadingX: leadingX, yOffset: yOffset)
    }

    private func styledLine(_ value: Text, baseline: Double, style: T.TypeStyle,
                            centerX: Double? = nil, leadingX: Double? = nil, yOffset: Double? = nil) -> some View {
        let lineHeight = style.lineHeight ?? (style.size * 1.3).rounded()
        let top = baseline - ((lineHeight - (T.ascent + T.descent) * style.size) / 2 + T.ascent * style.size)
        return value
            .tracking(style.letterSpacing)
            .foregroundStyle(style.color)
            .fixedSize(horizontal: true, vertical: true)
            .position(x: centerX ?? 0, y: (yOffset ?? dy) + top + lineHeight / 2)
            .offset(x: leadingX == nil ? 0 : (leadingX! + valueWidth(style: style) / 2 - (centerX ?? 0)))
    }

    private func valueWidth(style: T.TypeStyle) -> Double {
        // Align left-anchored labels with the actual registered font instead of a guessed advance.
        let font = UIFont(name: style.font, size: style.size) ?? .systemFont(ofSize: style.size)
        // Only the placeholder and invite label use this branch; their plain strings are explicit.
        let string = style.font == T.TypeToken.placeholder.font ? T.Copy.placeholder : T.Copy.invite
        return (string as NSString).size(withAttributes: [.font: font]).width
    }
}

private struct FindFriendsInvitePressStyle: ButtonStyle {
    let onPressedChange: (Bool) -> Void

    init(_ onPressedChange: @escaping (Bool) -> Void) {
        self.onPressedChange = onPressedChange
    }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, pressed in
                onPressedChange(pressed)
            }
    }
}

private struct FindFriendsInviteCloseShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.move(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        return path
    }
}

private struct FindFriendsInvitePlaneGlyph: View {
    let fill: Color
    let fold: Color
    let size: Double

    var body: some View {
        let unit = (size + 2) / 58
        ZStack {
            FindFriendsInvitePlaneWing(first: true)
                .fill(fill)
                .overlay(FindFriendsInvitePlaneWing(first: true).stroke(fill, style: StrokeStyle(lineWidth: 1.5 * unit, lineJoin: .round)))
            FindFriendsInvitePlaneWing(first: false)
                .fill(fill)
                .overlay(FindFriendsInvitePlaneWing(first: false).stroke(fill, style: StrokeStyle(lineWidth: 1.5 * unit, lineJoin: .round)))
            FindFriendsInvitePlaneFold().stroke(fold, style: StrokeStyle(lineWidth: 1.6 * unit, lineCap: .round))
        }
        .frame(width: size + 2, height: size + 2)
        .accessibilityHidden(true)
    }
}

private struct FindFriendsInvitePlaneWing: Shape {
    let first: Bool

    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width / 58, rect.height / 57)
        let left = (rect.width - 58 * scale) / 2
        let top = (rect.height - 57 * scale) / 2
        func point(_ x: Double, _ y: Double) -> CGPoint {
            CGPoint(x: left + (x + 1) * scale, y: top + (y + 1) * scale)
        }
        var path = Path()
        path.move(to: point(56, 0))
        path.addLine(to: first ? point(0, 22) : point(24, 31))
        path.addLine(to: first ? point(24, 31) : point(33, 55))
        path.closeSubpath()
        return path
    }
}

private struct FindFriendsInvitePlaneFold: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width / 58, rect.height / 57)
        let left = (rect.width - 58 * scale) / 2
        let top = (rect.height - 57 * scale) / 2
        var path = Path()
        path.move(to: CGPoint(x: left + 56 * scale, y: top + 2 * scale))
        path.addLine(to: CGPoint(x: left + 26 * scale, y: top + 31 * scale))
        return path
    }
}
