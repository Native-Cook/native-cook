import SwiftUI
import UIKit

// The reusable piece: a group of notifications that sits as a collapsed stack and fans out into a list.
// Tap the stack to open it, pull it down to peek, swipe a card left to clear it, Show less to restack.
// Every frame is drawn from a pose: a script (autoPlay) computes one from loop time, a finger moves the
// springs in NotificationStackExpandStackMotion, which are sampled against the timeline clock.

private typealias M = NotificationStackExpandMotion

struct NotificationStackExpandCardPose {
    /// Fan-out: 0 stacked, 1 in the list (springs overshoot a little past both).
    var e: Double = 0
    /// Place in the group: 0 front / top. Fractional while the cards close a gap.
    var slot: Double = 0
    /// Swipe offset, pt.
    var x: Double = 0
    var alpha: Double = 1
    /// Drops onto the stack from this many pt above (a returning card).
    var drop: Double = 0
}

struct NotificationStackExpandPose {
    var cards: [NotificationStackExpandCardPose]
    /// Drag-down peek, added to every card's fan-out.
    var peek: Double = 0
    /// Cards still in the group, fractional while one leaves.
    var count: Double
    /// Whole cards for the header's "4 new", the count it changed from and how long ago (s).
    var countN: Int
    var countPrev: Int
    var countAge: Double = .infinity

    static func rest(total: Int, open: Double) -> NotificationStackExpandPose {
        NotificationStackExpandPose(
            cards: (0..<M.maxCards).map { i in
                NotificationStackExpandCardPose(e: open, slot: Double(i), x: 0, alpha: i < total ? 1 : 0, drop: 0)
            },
            count: Double(total), countN: total, countPrev: total)
    }
}

/// The finger's springs for the stack. Writes happen only on touches; frames sample the tracks.
@Observable
final class NotificationStackExpandStackMotion {
    typealias Track = NotificationStackExpandTrack

    struct Card {
        var e = Track(0)
        var slot: Track
        var x = Track(0)
        var alpha = Track(1)
        var drop = Track(0)
    }

    var cards: [Card]
    var peek = Track(0)
    var count: Track
    var countN: Int
    var countPrev: Int
    var countChanged = Date.distantPast
    var open = false
    var gone: [Bool]
    /// The card resting open on its Clear button, or -1.
    var swiped = -1
    /// Reduce Motion: 200 ms timings instead of springs.
    var calm = false
    /// Until when something still moves (the timeline may pause after it).
    var busyUntil = Date.distantPast
    let total: Int

    init(total: Int) {
        let n = min(total, M.maxCards)
        self.total = n
        cards = (0..<M.maxCards).map { Card(slot: Track(Double($0))) }
        count = Track(Double(n))
        countN = n
        countPrev = n
        gone = (0..<M.maxCards).map { $0 >= n }
        for i in 0..<M.maxCards where gone[i] { cards[i].alpha.set(0) }
    }

    func pose(at date: Date) -> NotificationStackExpandPose {
        NotificationStackExpandPose(
            cards: cards.map {
                NotificationStackExpandCardPose(e: $0.e.value(at: date), slot: $0.slot.value(at: date),
                                                x: $0.x.value(at: date), alpha: $0.alpha.value(at: date),
                                                drop: $0.drop.value(at: date))
            },
            peek: peek.value(at: date), count: count.value(at: date), countN: countN, countPrev: countPrev,
            countAge: date.timeIntervalSince(countChanged))
    }

    /// Takes over a pose (from the script) as the tracks' starting point.
    func load(_ p: NotificationStackExpandPose, at date: Date) {
        for i in 0..<cards.count {
            let c = p.cards[i]
            cards[i].e.set(c.e, at: date)
            cards[i].slot.set(c.slot, at: date)
            cards[i].x.set(c.x, at: date)
            cards[i].alpha.set(c.alpha, at: date)
            cards[i].drop.set(c.drop, at: date)
        }
        peek.set(p.peek, at: date)
        count.set(p.count, at: date)
        countN = p.countN
        countPrev = p.countPrev
        countChanged = date.addingTimeInterval(-p.countAge)
    }

    private func settle(_ track: Track) {
        let end = track.settles
        if end > busyUntil { busyUntil = end }
    }

    /// A spring, or under Reduce Motion a 200 ms timing.
    private func move(_ track: inout Track, to: Double, _ s: NotificationStackExpandSpring, velocity: Double = 0,
                      delay: Double = 0, at date: Date) {
        if calm {
            track.timing(to: to, duration: 0.2, delay: 0, at: date)
        } else {
            track.spring(to: to, s, velocity: velocity, delay: delay, at: date)
        }
        settle(track)
    }

    private func fade(_ track: inout Track, to: Double, duration: Double, delay: Double = 0, at date: Date) {
        track.timing(to: to, duration: duration, delay: delay, at: date)
        settle(track)
    }

    private func setCount(_ n: Int, at date: Date) {
        guard n != countN else { return }
        countPrev = countN
        countN = n
        countChanged = date
        busyUntil = max(busyUntil, date.addingTimeInterval(0.4))
    }

    /// Re-ranks the cards still in the group and springs each to its place.
    private func settleSlots(at date: Date) {
        var rank = 0
        for i in 0..<cards.count where !gone[i] {
            move(&cards[i].slot, to: Double(rank), M.gapSpring, at: date)
            rank += 1
        }
        move(&count, to: Double(rank), M.gapSpring, at: date)
        setCount(rank, at: date)
    }

    /// Fans the cards out (top first) or restacks them (bottom first), on staggered springs.
    func setOpen(_ isOpen: Bool, velocity: Double = 0, at date: Date) {
        open = isOpen
        let n = cards.count
        for i in 0..<n {
            if swiped == i || cards[i].x.value(at: date) > 0 { move(&cards[i].x, to: 0, M.swipeSpring, at: date) }
            let order = isOpen ? i : n - 1 - i
            let s = isOpen ? M.expandSpring : M.collapseSpring
            move(&cards[i].e, to: isOpen ? 1 : 0, s, velocity: velocity, delay: Double(order) * M.stagger, at: date)
        }
        swiped = -1
    }

    /// Throws card i off to the left and closes the gap.
    func clearCard(_ i: Int, velocity: Double = 0, at date: Date) {
        let off = -560.0
        if calm {
            fade(&cards[i].alpha, to: 0, duration: 0.2, at: date)
            fade(&cards[i].x, to: off, duration: 0, delay: 0.22, at: date)
        } else {
            let speed = max(abs(velocity), 1400)
            let x = cards[i].x.value(at: date)
            fade(&cards[i].x, to: off, duration: min(0.26, max(0.14, (x - off) / speed)), at: date)
            fade(&cards[i].alpha, to: 0, duration: 0.14, delay: 0.14, at: date)
        }
        gone[i] = true
        if swiped == i { swiped = -1 }
        settleSlots(at: date)
        if !gone.contains(false) { open = false }
        NotificationStackExpandHaptics.light()
    }

    /// The header's clear mark: every card leaves, top first.
    func clearAll(at date: Date) {
        var order = 0
        for i in 0..<cards.count where !gone[i] {
            let delay = Double(order) * M.stagger
            fade(&cards[i].alpha, to: 0, duration: 0.18, delay: delay, at: date)
            fade(&cards[i].drop, to: -12, duration: 0.18, delay: delay, at: date)
            fade(&cards[i].x, to: -560, duration: 0, delay: delay + 0.2, at: date)
            gone[i] = true
            order += 1
        }
        open = false
        swiped = -1
        move(&count, to: 0, M.gapSpring, at: date)
        setCount(0, at: date)
        NotificationStackExpandHaptics.light()
    }

    /// A script let go: whatever pose it left becomes the finger's starting point.
    func adopt(at date: Date) {
        let isOpen = cards[0].e.value(at: date) > 0.5
        for i in 0..<cards.count where !gone[i] {
            if cards[i].alpha.value(at: date) < 0.5 || cards[i].x.value(at: date) < -260 { gone[i] = true }
        }
        open = isOpen
        swiped = -1
        move(&peek, to: 0, M.peekSpring, at: date)
        for i in 0..<cards.count {
            move(&cards[i].e, to: isOpen ? 1 : 0, M.expandSpring, at: date)
            move(&cards[i].drop, to: 0, M.swipeSpring, at: date)
            if gone[i] {
                fade(&cards[i].alpha, to: 0, duration: 0.12, at: date)
            } else {
                move(&cards[i].x, to: 0, M.swipeSpring, at: date)
                fade(&cards[i].alpha, to: 1, duration: 0.12, at: date)
            }
        }
        settleSlots(at: date)
    }

    /// Back to the resting stack with every card in it: cleared cards drop back on in order.
    func restore(at date: Date) {
        setOpen(false, at: date)
        move(&peek, to: 0, M.peekSpring, at: date)
        for i in 0..<min(total, cards.count) {
            if gone[i] {
                cards[i].x.set(0, at: date)
                cards[i].drop.set(M.arriveDrop, at: date)
                move(&cards[i].drop, to: 0, M.swipeSpring, at: date)
                fade(&cards[i].alpha, to: 1, duration: 0.26, delay: 0.12, at: date)
                gone[i] = false
            } else {
                move(&cards[i].x, to: 0, M.swipeSpring, at: date)
            }
        }
        settleSlots(at: date)
    }

    // MARK: Finger

    func dragX(_ i: Int, to x: Double, at date: Date) {
        cards[i].x.set(M.rubberRight(x), at: date)
    }

    func releaseX(_ i: Int, velocity: Double, width: Double, at date: Date) {
        let x = cards[i].x.value(at: date)
        if x < -width * M.flingFraction || (velocity < -M.flingVelocity && x < -24) {
            clearCard(i, velocity: velocity, at: date)
        } else if x < -M.clearW * M.openFraction && velocity < 400 {
            move(&cards[i].x, to: -M.clearW - 8, M.swipeSpring, velocity: velocity, at: date)
            swiped = i
        } else {
            move(&cards[i].x, to: 0, M.swipeSpring, velocity: velocity, at: date)
            if swiped == i { swiped = -1 }
        }
    }

    func closeSwiped(except i: Int = -1, at date: Date) {
        if swiped >= 0 && swiped != i {
            move(&cards[swiped].x, to: 0, M.swipeSpring, at: date)
            swiped = -1
        }
    }

    func pull(_ dy: Double, at date: Date) {
        peek.set(M.peekFromDrag(dy), at: date)
    }

    func releasePull(dy: Double, velocity: Double, at date: Date) {
        let unit = velocity * M.peekMax / M.peekRange
        if dy > M.peekOpenDistance || velocity > M.peekOpenVelocity {
            // The peek becomes the start of the fan-out, carrying the finger's speed.
            let p = peek.value(at: date)
            peek.set(0, at: date)
            for i in 0..<cards.count { cards[i].e.set(cards[i].e.value(at: date) + p, at: date) }
            setOpen(true, velocity: unit, at: date)
            NotificationStackExpandHaptics.light()
        } else {
            move(&peek, to: 0, M.peekSpring, velocity: unit, at: date)
        }
    }
}

enum NotificationStackExpandHaptics {
    static func light() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

/// Text that changes in place: the old words leave fast (140 ms), the new ones settle a beat later from
/// 10 pt below (260 ms). `age` is the time since the change; the first render does not animate.
struct NotificationStackExpandSwapText: View {
    let text: String
    let previous: String
    let age: Double
    let font: Font
    let color: Color
    var reduced = false

    var body: some View {
        let outU = M.clamp01(age / (reduced ? 0.12 : 0.14))
        let inU = reduced ? M.clamp01(age / 0.18) : M.clamp01((age - 0.06) / 0.26)
        let out = reduced ? outU : Ease.out.value(at: outU)
        let into = reduced ? inU : Ease.out.value(at: inU)
        ZStack(alignment: .leading) {
            if out < 1 && previous != text {
                Text(previous).font(font).monospacedDigit().foregroundStyle(color).opacity(1 - out)
            }
            Text(text).font(font).monospacedDigit().foregroundStyle(color)
                .opacity(previous == text ? 1 : into)
                .offset(y: reduced || previous == text ? 0 : (1 - into) * 10)
        }
        .fixedSize()
    }
}

private struct NotificationStackExpandRow: View {
    let index: Int
    let notice: NotificationStackExpandNotice
    let pose: NotificationStackExpandPose
    let width: Double
    let motion: NotificationStackExpandStackMotion
    let grab: () -> Void
    let letGo: () -> Void

    private enum Axis { case x, y }
    @State private var active = false
    @State private var axis: Axis?
    @State private var startX = 0.0
    @GestureState private var pressing = false

    var body: some View {
        let c = pose.cards[index]
        let s = c.slot
        let e = c.e + pose.peek
        let eu = M.clamp01(e)
        let depth = min(max(s, 0), M.maxDepth)
        let y = M.mix(e, M.collapsedY(s), M.expandedY(s))
        let k = M.mix(eu, 1 - M.depthScale * depth, 1)
        let stacked = s <= M.maxDepth ? 1 : M.clamp01(M.maxDepth + 1 - s)
        let faceOpacity = M.mix(eu, M.clamp01(1 - s), 1)
        let tint = (1 - eu) * depth * 0.32
        let clearW = c.alpha < 0.05 ? 0 : min(max(-c.x - 8, 0), width)
        let labelOpacity = M.clamp01((-c.x - 8 - 48) / 20)

        ZStack(alignment: .topTrailing) {
            Button {
                motion.clearCard(index, at: .now)
            } label: {
                Text("Clear")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xFFF6F5))
                    .lineLimit(1)
                    .fixedSize()
                    .frame(width: 44)
                    .opacity(labelOpacity)
                    .padding(.trailing, 20)
                    .frame(width: clearW, height: M.cardH, alignment: .trailing)
                    .background(
                        RoundedRectangle(cornerRadius: M.radius, style: .continuous)
                            .fill(NotificationStackExpandColors.danger)
                            .overlay(
                                RoundedRectangle(cornerRadius: M.radius, style: .continuous).inset(by: 0.5)
                                    .stroke(LinearGradient(stops: [.init(color: .white.opacity(0.25), location: 0),
                                                                   .init(color: .white.opacity(0), location: 0.05)],
                                                           startPoint: .top, endPoint: .bottom), lineWidth: 1)
                            )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: M.radius, style: .continuous))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .frame(width: clearW, height: M.cardH)
            .opacity(M.clamp01(clearW / 36))
            .allowsHitTesting(clearW > 1)
            .accessibilityHidden(true)

            ZStack {
                NotificationStackExpandFace(notice: notice)
                    .opacity(faceOpacity)
                RoundedRectangle(cornerRadius: M.radius, style: .continuous)
                    .fill(NotificationStackExpandColors.backTint)
                    .opacity(tint)
                    .allowsHitTesting(false)
            }
            .frame(width: width, height: M.cardH)
            .background(NotificationStackExpandSurface())
            .contentShape(RoundedRectangle(cornerRadius: M.radius, style: .continuous))
            .offset(x: c.x)
            .gesture(drag)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(notice.from), \(notice.time). \(notice.body)")
            .accessibilityAction(named: "Show all") { grab(); motion.setOpen(!motion.open, at: .now); letGo() }
            .accessibilityAction(named: "Clear") { grab(); motion.clearCard(index, at: .now); letGo() }
        }
        .frame(width: width, height: M.cardH, alignment: .topTrailing)
        .scaleEffect(k * (1 + M.arriveScale * c.drop / M.arriveDrop))
        .offset(y: y - c.drop)
        .opacity(c.alpha * M.mix(eu, stacked, 1))
        .allowsHitTesting(c.alpha > 0.05)
        .zIndex(Double(M.maxCards - index))
        .onChange(of: pressing) { _, now in
            // A cancelled touch never reaches onEnded.
            if !now && active { finish(nil) }
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .updating($pressing) { _, state, _ in state = true }
            .onChanged { v in
                let now = Date()
                if !active {
                    active = true
                    axis = nil
                    grab()
                }
                let dx = Double(v.translation.width)
                let dy = Double(v.translation.height)
                if axis == nil {
                    if abs(dx) > 14 && abs(dx) >= abs(dy) {
                        axis = .x
                        motion.closeSwiped(except: index, at: now)
                        startX = motion.cards[index].x.value(at: now)
                    } else if abs(dy) > 14 {
                        axis = .y
                    }
                }
                switch axis {
                case .x:
                    if motion.open { motion.dragX(index, to: startX + dx, at: now) }
                case .y:
                    if !motion.open { motion.pull(dy, at: now) }
                case nil:
                    break
                }
            }
            .onEnded { v in finish(v) }
    }

    private func finish(_ v: DragGesture.Value?) {
        let now = Date()
        defer {
            active = false
            axis = nil
            letGo()
        }
        guard let v else {
            if axis == .x && motion.open { motion.releaseX(index, velocity: 0, width: width, at: now) }
            if axis == .y && !motion.open { motion.releasePull(dy: 0, velocity: 0, at: now) }
            return
        }
        let dist = hypot(v.translation.width, v.translation.height)
        switch axis {
        case .x:
            if motion.open { motion.releaseX(index, velocity: Double(v.velocity.width), width: width, at: now) }
        case .y:
            if !motion.open {
                motion.releasePull(dy: Double(v.translation.height), velocity: Double(v.velocity.height), at: now)
            }
        case nil:
            guard dist <= 10 else { return }
            if !motion.open {
                motion.setOpen(true, at: now)
                NotificationStackExpandHaptics.light()
            } else if motion.swiped >= 0 {
                motion.closeSwiped(at: now)
            }
        }
    }
}

/// A notification group: a stack that fans out into a list with a header (count, Show less, clear).
struct NotificationStackExpandStack: View {
    let group: NotificationStackExpandGroup
    let pose: NotificationStackExpandPose
    let motion: NotificationStackExpandStackMotion
    let width: Double
    var reduced = false
    /// A finger on the stack (hands it over from a script) and off it again.
    var grab: () -> Void = {}
    var letGo: () -> Void = {}

    static let showLessW: Double = 88
    static let closeW: Double = 28

    private func countLabel(_ n: Int) -> String { n == 0 ? "Cleared" : "\(n) new" }

    var body: some View {
        let notices = Array(group.notices.prefix(M.maxCards))
        let e0 = pose.cards[0].e
        let shown = M.clamp01((e0 - 0.35) / 0.5) * M.clamp01(pose.count)

        ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(group.title)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(NotificationStackExpandColors.ink)
                        .lineLimit(1)
                        .layoutPriority(-1)
                    NotificationStackExpandSwapText(text: countLabel(pose.countN), previous: countLabel(pose.countPrev),
                                                    age: pose.countAge, font: .system(size: 14, weight: .medium),
                                                    color: NotificationStackExpandColors.accent, reduced: reduced)
                }
                .padding(.leading, 4)
                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                Button {
                    NotificationStackExpandHaptics.light()
                    grab()
                    motion.setOpen(false, at: .now)
                    letGo()
                } label: {
                    Text("Show less")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(NotificationStackExpandColors.ink)
                        .frame(width: Self.showLessW, height: 30)
                        .background(Capsule().fill(Color.rgba(27, 31, 36, 0.07)))
                        .contentShape(Capsule().inset(by: -6))
                }
                .buttonStyle(.plain)
                .padding(.leading, 8)
                Button {
                    grab()
                    motion.clearAll(at: .now)
                    letGo()
                } label: {
                    NotificationStackExpandGlyph(d: "M1.5 1.5 L8.5 8.5 M8.5 1.5 L1.5 8.5", size: CGSize(width: 10, height: 10),
                                                 color: NotificationStackExpandColors.ink, width: 1.6)
                        .frame(width: Self.closeW, height: Self.closeW)
                        .background(Circle().fill(Color.rgba(27, 31, 36, 0.07)))
                        .contentShape(Circle().inset(by: -8))
                }
                .buttonStyle(.plain)
                .padding(.leading, 8)
                .accessibilityLabel("Clear \(group.title)")
            }
            .frame(width: width, height: M.headerH - 8)
            .opacity(shown)
            .offset(y: (1 - M.clamp01(e0)) * 10)
            .allowsHitTesting(shown > 0.5)

            ForEach(Array(notices.enumerated()), id: \.element.id) { index, notice in
                NotificationStackExpandRow(index: index, notice: notice, pose: pose, width: width, motion: motion,
                                           grab: grab, letGo: letGo)
            }
        }
        .frame(width: width, height: M.groupHeight(1, Double(notices.count)), alignment: .topLeading)
    }
}
