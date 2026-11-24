import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

// Relay, a team inbox: the screen, the rest of the feed and the autoPlay script around the reusable
// NotificationStackExpandStack. Ported from the React Native block (its source is the reference).

private typealias M = NotificationStackExpandMotion
private typealias C = NotificationStackExpandColors

// Layout, in points.
private enum L {
    static let side: Double = 16
    static let brandH: Double = 28
    static let titleH: Double = 41
    static let filterH: Double = 32
    static let labelH: Double = 22
    /// From the top of the header block to the top of the group.
    static let aboveGroup = brandH + 4 + titleH + 12 + filterH + 20 + labelH + 8
    static let belowGap: Double = 14
    static let tabH: Double = 50
    static let ghost: Double = 44
}

/// The autoPlay script: every value is a pure function of loop time.
enum NotificationStackExpandScript {
    /// The ghost finger's drag left across the first card: slow start, fast at the release.
    static func swipeX(_ t: Double, width: Double) -> Double {
        if t <= M.T.swipeStart { return 0 }
        if t < M.T.swipeEnd {
            let u = (t - M.T.swipeStart) / (M.T.swipeEnd - M.T.swipeStart)
            return -M.swipeDrag * (0.35 * u + 0.65 * u * u)
        }
        let v = M.seg(t, M.T.swipeEnd, M.T.flingSpan)
        let travel = width + 24 - M.swipeDrag
        return -M.swipeDrag - travel * (0.4 * v + 0.6 * v * v)
    }

    static func pullDy(_ t: Double) -> Double {
        M.ghostPeek * M.easeInOutCubic(M.seg(t, M.T.peekStart, M.T.peekRelease - M.T.peekStart))
    }

    static func peekAt(_ t: Double) -> Double {
        if t < M.T.peekStart { return 0 }
        if t < M.T.peekRelease { return M.peekFromDrag(pullDy(t)) }
        return M.peekFromDrag(M.ghostPeek) * (1 - M.springAt(t - M.T.peekRelease, M.peekSpring))
    }

    /// Every card of the stack at loop time t.
    static func pose(_ t: Double, width: Double, total: Int) -> NotificationStackExpandPose {
        let gapShift = M.springAt(t - M.T.gap, M.gapSpring) - M.springAt(t - M.T.arrive - 0.04, M.pushSpring)
        let arrive = M.easeOutCubic(M.seg(t, M.T.arrive, M.T.arriveSpan))
        var p = NotificationStackExpandPose.rest(total: total, open: 0)
        for i in 0..<total {
            let open = M.springAt(t - M.T.expand - Double(i) * M.stagger, M.expandSpring)
            let close = M.springAt(t - M.T.collapse - Double(total - 1 - i) * M.stagger, M.collapseSpring)
            var c = NotificationStackExpandCardPose()
            c.e = open - close
            if i == 0 {
                c.slot = 0
                c.x = t < M.T.arrive ? swipeX(t, width: width) : 0
                c.alpha = t < M.T.arrive ? 1 - M.seg(t, M.T.swipeEnd + 0.12, 0.14) : arrive
                c.drop = t < M.T.arrive ? 0 : M.arriveDrop * (1 - arrive)
            } else {
                c.slot = Double(i) - gapShift
            }
            p.cards[i] = c
        }
        p.peek = peekAt(t)
        p.count = Double(total) - gapShift
        let removed = t >= M.T.gap && t < M.T.arrive
        p.countN = removed ? total - 1 : total
        p.countPrev = removed ? total : total - 1
        p.countAge = removed ? t - M.T.gap : (t >= M.T.arrive ? t - M.T.arrive : t + M.loop - M.T.arrive)
        return p
    }

    struct Point { let t: Double; let x: Double; let y: Double }

    static func ghostAt(_ path: [Point], _ t: Double) -> CGPoint {
        if t <= path[0].t { return CGPoint(x: path[0].x, y: path[0].y) }
        for i in 1..<path.count where t <= path[i].t {
            let u = M.easeInOutCubic((t - path[i - 1].t) / (path[i].t - path[i - 1].t))
            return CGPoint(x: M.mix(u, path[i - 1].x, path[i].x), y: M.mix(u, path[i - 1].y, path[i].y))
        }
        return CGPoint(x: path[path.count - 1].x, y: path[path.count - 1].y)
    }

    static func ghostOpacity(_ t: Double) -> Double {
        let a = M.seg(t, M.T.ghostIn, 0.18) * (1 - M.seg(t, M.T.tapOpen + 0.15, 0.2))
        let b = M.seg(t, M.T.swipeStart - 0.35, 0.2) * (1 - M.seg(t, M.T.swipeEnd + 0.05, 0.18))
        let c = M.seg(t, M.T.tapLess - 0.35, 0.2) * (1 - M.seg(t, M.T.tapLess + 0.1, 0.2))
        let d = M.seg(t, M.T.peekStart - 0.35, 0.2) * (1 - M.seg(t, M.T.peekRelease + 0.05, 0.2))
        return max(a, b, c, d)
    }

    static func ghostPress(_ t: Double) -> Double {
        var press = 0.0
        for at in [M.T.tapOpen, M.T.tapLess] {
            let d = t - at
            if d > -0.1 && d < 0.12 { press = max(press, d < 0 ? 1 + d / 0.1 : 1 - d / 0.12) }
        }
        let drag = M.seg(t, M.T.swipeStart - 0.08, 0.08) * (1 - M.seg(t, M.T.swipeEnd, 0.1))
        let pull = M.seg(t, M.T.peekStart - 0.08, 0.08) * (1 - M.seg(t, M.T.peekRelease, 0.1))
        return max(press, drag, pull)
    }
}

/// Who drives the stack: the loop, a finger, or the return to rest between them.
@Observable
final class NotificationStackExpandDirector {
    enum Mode { case auto, manual, returning }

    var mode: Mode = .manual
    var clockStart = Date.now
    /// Bumped to re-evaluate whether the timeline may pause.
    var poke = 0
    let motion: NotificationStackExpandStackMotion
    var playing = false
    var rowWidth: Double = 361
    private var fingers = 0
    private var resume: DispatchWorkItem?

    init(total: Int) {
        motion = NotificationStackExpandStackMotion(total: total)
    }

    func start(playing: Bool, reduced: Bool) {
        self.playing = playing
        motion.calm = reduced
        resume?.cancel()
        if playing {
            mode = .auto
            clockStart = .now
        } else {
            mode = .manual
            motion.load(.rest(total: motion.total, open: reduced ? 1 : 0), at: .now)
            motion.open = reduced
        }
    }

    func loopTime(_ date: Date) -> Double {
        let t = date.timeIntervalSince(clockStart).truncatingRemainder(dividingBy: M.loop)
        return t < 0 ? t + M.loop : t
    }

    /// A finger came down anywhere: the loop hands its pose to the finger.
    func touchBegan() {
        resume?.cancel()
        guard mode != .manual else { return }
        let now = Date.now
        if mode == .auto {
            motion.load(NotificationStackExpandScript.pose(loopTime(now), width: rowWidth, total: motion.total), at: now)
            motion.adopt(at: now)
        }
        mode = .manual
    }

    func fingersChanged(_ n: Int) {
        let was = fingers
        fingers = n
        if n > 0 && was == 0 { touchBegan() }
        if n == 0 { scheduleResume() }
    }

    func grab() {
        touchBegan()
    }

    func letGo() {
        if fingers == 0 { scheduleResume() }
    }

    private func scheduleResume() {
        resume?.cancel()
        guard playing else {
            DispatchQueue.main.asyncAfter(deadline: .now() + max(0, motion.busyUntil.timeIntervalSinceNow) + 0.05) { [weak self] in
                self?.poke += 1
            }
            return
        }
        let item = DispatchWorkItem { [weak self] in self?.returnToRest() }
        resume = item
        DispatchQueue.main.asyncAfter(deadline: .now() + M.resumeAfter, execute: item)
    }

    /// Two seconds after the last touch: restack, then restart the loop at t = 0 once still.
    private func returnToRest() {
        guard mode == .manual, fingers == 0 else { return }
        let now = Date.now
        motion.restore(at: now)
        mode = .returning
        let wait = max(0.3, motion.busyUntil.timeIntervalSince(now))
        let item = DispatchWorkItem { [weak self] in
            guard let self, self.mode == .returning else { return }
            self.clockStart = .now
            self.mode = .auto
        }
        resume = item
        DispatchQueue.main.asyncAfter(deadline: .now() + wait, execute: item)
    }
}

struct NotificationStackExpand: View {
    static let loop = M.loop

    var autoPlay: Bool = true
    var group: NotificationStackExpandGroup = NotificationStackExpandData.launch

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var director: NotificationStackExpandDirector
    @State private var filter = 0

    init(autoPlay: Bool = true, group: NotificationStackExpandGroup = NotificationStackExpandData.launch) {
        self.autoPlay = autoPlay
        self.group = group
        _director = State(initialValue: NotificationStackExpandDirector(total: min(group.notices.count, M.maxCards)))
    }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            screen(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(NotificationStackExpandTouchProbe { director.fingersChanged($0) })
        .onAppear { director.start(playing: autoPlay && !reduceMotion, reduced: reduceMotion) }
        .onChange(of: reduceMotion) { _, r in director.start(playing: autoPlay && !r, reduced: r) }
    }

    @ViewBuilder
    private func screen(size: CGSize, top: Double, bottom: Double) -> some View {
        let width = size.width
        let rowW = width - L.side * 2
        let total = min(group.notices.count, M.maxCards)
        let headerTop = top + 6
        let groupTop = headerTop + L.aboveGroup
        let belowTop = groupTop + M.groupHeight(0, Double(total)) + L.belowGap
        let tabBottom = max(bottom, 8)
        let d = director
        let _ = d.poke
        let paused = d.mode == .manual && Date.now > d.motion.busyUntil

        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [.init(color: Color(hex: C.bg[0]), location: 0),
                                   .init(color: Color(hex: C.bg[1]), location: 0.5),
                                   .init(color: Color(hex: C.bg[2]), location: 1)],
                           startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: size.height)

            header(width: rowW)
                .offset(x: L.side, y: headerTop)

            TimelineView(.animation(paused: paused)) { context in
                let now = context.date
                let auto = d.mode == .auto
                let t = d.loopTime(now)
                let pose = auto ? NotificationStackExpandScript.pose(t, width: rowW, total: total) : d.motion.pose(at: now)
                let last = pose.cards[max(0, total - 1)]
                let shift = M.groupHeight(last.e + pose.peek, pose.count) - M.groupHeight(0, Double(total))

                ZStack(alignment: .topLeading) {
                    feedBelow(width: rowW)
                        .offset(x: L.side, y: belowTop + shift)

                    NotificationStackExpandStack(group: group, pose: pose, motion: d.motion, width: rowW,
                                                 reduced: reduceMotion, grab: { d.grab() }, letGo: { d.letGo() })
                        .offset(x: L.side, y: groupTop)

                    tabBar(width: width, tabBottom: tabBottom)
                        .offset(y: size.height - L.tabH - tabBottom)

                    if auto {
                        ghost(t: t, rowW: rowW, groupTop: groupTop)
                    }
                }
                .frame(width: width, height: size.height, alignment: .topLeading)
            }
        }
        .frame(width: width, height: size.height, alignment: .topLeading)
        .clipped()
        .onAppear { d.rowWidth = rowW }
        .onChange(of: rowW) { _, w in d.rowWidth = w }
    }

    // MARK: Header

    private func header(width: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                NotificationStackExpandBrandMark()
                Text("Relay")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(C.ink)
                    .padding(.leading, 7)
                Text("Fernway")
                    .font(.system(size: 15))
                    .foregroundStyle(C.mute)
                    .padding(.leading, 8)
                Spacer(minLength: 0)
                Text("AL")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(Circle().fill(LinearGradient(colors: [Color(hex: 0xC7D3E0), Color(hex: 0x8EA2B8), Color(hex: 0x64788F)],
                                                             startPoint: .top, endPoint: .bottom)))
            }
            .frame(height: L.brandH)

            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text("Inbox")
                    .font(.system(size: 34, weight: .bold))
                    .tracking(0.2)
                    .foregroundStyle(C.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                Text("9 unread")
                    .font(.system(size: 15, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(C.mute)
            }
            .frame(height: L.titleH)
            .padding(.top, 4)

            Picker("Filter", selection: $filter) {
                ForEach(Array(NotificationStackExpandData.filters.enumerated()), id: \.offset) { i, label in
                    Text(label).tag(i)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .environment(\.colorScheme, .light)
            .frame(width: width, height: L.filterH)
            .padding(.top, 12)

            sectionLabel("Today")
                .padding(.top, 20)
        }
        .frame(width: width, alignment: .leading)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(C.mute)
            .padding(.leading, 4)
            .frame(height: L.labelH, alignment: .leading)
    }

    // MARK: The rest of the feed

    private func feedBelow(width: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            NotificationStackExpandNoticeCard(notice: NotificationStackExpandData.standup)
            Color.clear.frame(height: 12)
            NotificationStackExpandStillStack(group: NotificationStackExpandData.crit, width: width)
            sectionLabel("Yesterday")
                .padding(.top, 22)
                .padding(.bottom, 8)
            NotificationStackExpandNoticeCard(notice: NotificationStackExpandData.yesterday[0])
            Color.clear.frame(height: 8)
            NotificationStackExpandNoticeCard(notice: NotificationStackExpandData.yesterday[1])
        }
        .frame(width: width, alignment: .topLeading)
    }

    // MARK: Tab bar

    private func tabBar(width: Double, tabBottom: Double) -> some View {
        let tabs: [(NotificationStackExpandTab, String)] = [(.inbox, "Inbox"), (.channels, "Channels"), (.search, "Search"), (.you, "You")]
        return HStack(spacing: 0) {
            ForEach(Array(tabs.enumerated()), id: \.offset) { i, tab in
                let on = i == 0
                VStack(spacing: 2) {
                    NotificationStackExpandTabGlyph(kind: tab.0, color: on ? C.accent : Color(hex: 0x8A9099))
                    Text(tab.1)
                        .font(.system(size: 10.5, weight: on ? .semibold : .medium))
                        .foregroundStyle(on ? C.accent : Color(hex: 0x8A9099))
                }
                .padding(.top, 6)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .overlay(alignment: .top) {
                    if on {
                        Text("9")
                            .font(.system(size: 11, weight: .bold))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 17, minHeight: 17)
                            .background(Capsule().fill(Color(hex: 0xE2484D)))
                            .offset(x: 6 + 17 / 2, y: 3)
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(on ? [.isSelected] : [])
            }
        }
        .frame(width: width, height: L.tabH)
        .padding(.bottom, tabBottom)
        .background {
            ZStack {
                Rectangle().fill(.ultraThinMaterial)
                Rectangle().fill(Color.rgba(244, 245, 247, 0.6))
            }
            .environment(\.colorScheme, .light)
        }
        .overlay(alignment: .top) {
            Rectangle().fill(Color.rgba(27, 31, 36, 0.16)).frame(height: 1 / UIScreen.main.scale)
        }
    }

    // MARK: Ghost finger

    private func ghost(t: Double, rowW: Double, groupTop: Double) -> some View {
        let S = NotificationStackExpandScript.self
        let cx = L.side + rowW * 0.56
        let cy = groupTop + M.cardH / 2
        let c0y = groupTop + M.headerH + M.cardH / 2
        let grabX = L.side + rowW - 56
        let lessX = L.side + rowW - NotificationStackExpandStack.closeW - 8 - NotificationStackExpandStack.showLessW / 2
        let lessY = groupTop + (M.headerH - 8) / 2
        let pullY = cy - 8
        let T = M.T.self
        let path: [NotificationStackExpandScript.Point] = [
            .init(t: T.ghostIn, x: cx + 28, y: cy + 40),
            .init(t: T.tapOpen - 0.08, x: cx, y: cy),
            .init(t: T.tapOpen + 0.12, x: cx, y: cy),
            .init(t: T.tapOpen + 0.4, x: cx + 12, y: cy + 30),
            .init(t: T.swipeStart - 0.35, x: grabX + 18, y: c0y + 30),
            .init(t: T.swipeStart - 0.04, x: grabX, y: c0y),
            .init(t: T.swipeStart, x: grabX, y: c0y),
            .init(t: T.swipeEnd, x: grabX - M.swipeDrag, y: c0y),
            .init(t: T.swipeEnd + 0.24, x: grabX - M.swipeDrag - 70, y: c0y - 10),
            .init(t: T.tapLess - 0.35, x: lessX + 20, y: lessY + 40),
            .init(t: T.tapLess - 0.08, x: lessX, y: lessY),
            .init(t: T.tapLess + 0.12, x: lessX, y: lessY),
            .init(t: T.tapLess + 0.4, x: lessX + 10, y: lessY + 30),
            .init(t: T.peekStart - 0.35, x: cx + 20, y: pullY + 34),
            .init(t: T.peekStart - 0.04, x: cx, y: pullY),
            .init(t: T.peekStart, x: cx, y: pullY),
            .init(t: T.peekRelease, x: cx, y: pullY + M.ghostPeek),
            .init(t: T.peekRelease + 0.3, x: cx + 10, y: pullY + M.ghostPeek + 14),
        ]
        let inSwipe = t > T.swipeStart && t < T.swipeEnd
        let inPull = t > T.peekStart && t < T.peekRelease
        let p = S.ghostAt(path, t)
        let x = inSwipe ? grabX + S.swipeX(t, width: rowW) : p.x
        let y = inPull ? pullY + S.pullDy(t) : p.y
        return Circle()
            .fill(Color.rgba(110, 118, 130, 0.32))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.75), lineWidth: 1))
            .frame(width: L.ghost, height: L.ghost)
            .scaleEffect(1 - 0.16 * S.ghostPress(t))
            .opacity(S.ghostOpacity(t) * 0.9)
            .offset(x: x - L.ghost / 2, y: y - L.ghost / 2)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// A group that stays stacked: its back cards peek under the front one.
struct NotificationStackExpandStillStack: View {
    let group: NotificationStackExpandGroup
    let width: Double

    var body: some View {
        let shown = Array(group.notices.prefix(3))
        ZStack(alignment: .topLeading) {
            ForEach(Array(shown.enumerated().reversed()), id: \.element.id) { i, notice in
                ZStack {
                    if i == 0 {
                        NotificationStackExpandFace(notice: notice)
                    } else {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(C.backTint)
                            .opacity(0.32 * Double(i))
                    }
                }
                .frame(width: width, height: M.cardH)
                .background(NotificationStackExpandSurface())
                .scaleEffect(1 - M.depthScale * Double(i))
                .offset(y: M.collapsedY(Double(i)))
            }
        }
        .frame(width: width, height: M.groupHeight(0, Double(shown.count)), alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(group.title), \(shown.count) notifications")
    }
}

private struct NotificationStackExpandBrandMark: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 4.5).fill(Color(hex: 0x8FC2B6)).frame(width: 14, height: 14).offset(x: 1, y: 5)
            RoundedRectangle(cornerRadius: 4.5).fill(C.accent).frame(width: 14, height: 14).offset(x: 7, y: 2)
            NotificationStackExpandGlyph(d: "M10.5 9 H17.5 M10.5 12 H15", size: CGSize(width: 22, height: 22),
                                         color: Color(hex: 0xE9F3F0), width: 1.6)
        }
        .frame(width: 22, height: 22, alignment: .topLeading)
    }
}

enum NotificationStackExpandTab { case inbox, channels, search, you }

private struct NotificationStackExpandTabGlyph: View {
    let kind: NotificationStackExpandTab
    let color: Color

    var body: some View {
        let size = CGSize(width: 26, height: 24)
        Group {
            switch kind {
            case .inbox:
                NotificationStackExpandGlyph(
                    d: "M4 13 L6.5 5.2 A2 2 0 0 1 8.4 3.8 H17.6 A2 2 0 0 1 19.5 5.2 L22 13 V18 A2 2 0 0 1 20 20 H6 A2 2 0 0 1 4 18 Z M4 13 H9 L10.5 15.5 H15.5 L17 13 H22",
                    size: size, color: color, width: 1.8)
            case .channels:
                NotificationStackExpandGlyph(d: "M10 3.5 L8.4 20.5 M17.6 3.5 L16 20.5 M5 8.5 H21.5 M4.5 15.5 H21",
                                             size: size, color: color, width: 1.8)
            case .search:
                ZStack(alignment: .topLeading) {
                    Circle().stroke(color, lineWidth: 1.8).frame(width: 13, height: 13).offset(x: 5, y: 4)
                    NotificationStackExpandGlyph(d: "M16.3 15.3 L21 20", size: size, color: color, width: 1.8)
                }
            case .you:
                ZStack(alignment: .topLeading) {
                    Circle().stroke(color, lineWidth: 1.8).frame(width: 8.4, height: 8.4).offset(x: 8.8, y: 4.3)
                    NotificationStackExpandGlyph(d: "M5 20.5 C6.3 16.4 9.3 14.6 13 14.6 C16.7 14.6 19.7 16.4 21 20.5",
                                                 size: size, color: color, width: 1.8)
                }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

/// Watches every finger on the window without taking part in any gesture, so a touch anywhere hands the
/// stack to the finger and the last finger lifting starts the 2 s countdown to autoPlay.
private struct NotificationStackExpandTouchProbe: UIViewRepresentable {
    let onChange: (Int) -> Void

    func makeUIView(context: Context) -> Host { Host(onChange: onChange) }
    func updateUIView(_ view: Host, context: Context) { view.probe.onChange = onChange }
    static func dismantleUIView(_ view: Host, coordinator: ()) { view.detach() }

    final class Probe: UIGestureRecognizer, UIGestureRecognizerDelegate {
        var onChange: (Int) -> Void
        private var touches = Set<UITouch>()

        init(onChange: @escaping (Int) -> Void) {
            self.onChange = onChange
            super.init(target: nil, action: nil)
            cancelsTouchesInView = false
            delaysTouchesBegan = false
            delaysTouchesEnded = false
            delegate = self
        }

        override func touchesBegan(_ t: Set<UITouch>, with event: UIEvent) {
            touches.formUnion(t)
            onChange(touches.count)
        }
        override func touchesEnded(_ t: Set<UITouch>, with event: UIEvent) { lift(t) }
        override func touchesCancelled(_ t: Set<UITouch>, with event: UIEvent) { lift(t) }
        private func lift(_ t: Set<UITouch>) {
            touches.subtract(t)
            onChange(touches.count)
            if touches.isEmpty { state = .failed }
        }
        override func reset() {
            super.reset()
            if !touches.isEmpty {
                touches.removeAll()
                onChange(0)
            }
        }
        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
        override func canPrevent(_ preventedGestureRecognizer: UIGestureRecognizer) -> Bool { false }
        override func canBePrevented(by preventingGestureRecognizer: UIGestureRecognizer) -> Bool { false }
    }

    final class Host: UIView {
        let probe: Probe
        init(onChange: @escaping (Int) -> Void) {
            probe = Probe(onChange: onChange)
            super.init(frame: .zero)
            isUserInteractionEnabled = false
        }
        required init?(coder: NSCoder) { fatalError() }
        override func didMoveToWindow() {
            super.didMoveToWindow()
            probe.view?.removeGestureRecognizer(probe)
            window?.addGestureRecognizer(probe)
        }
        func detach() { probe.view?.removeGestureRecognizer(probe) }
    }
}
