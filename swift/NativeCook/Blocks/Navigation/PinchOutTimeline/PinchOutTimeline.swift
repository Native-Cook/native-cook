import SwiftUI
import UIKit

struct PinchOutTimelineMessage {
    /// Written by the person holding the phone: a white bubble at the right.
    var mine = false
    var text: String? = nil
    /// A square photo sent instead of text.
    var photo: String? = nil
}

/// A piece of a card's title; a piece with `key` is underlined.
struct PinchOutTimelinePiece {
    var text: String
    var key = false
}

struct PinchOutTimelineDay {
    /// On the day's card: "Tuesday, Jun 25".
    var label: String
    /// The two lines shown alone on the page while the day opens.
    var weekday: String
    var date: String
    /// Above the conversation: "Tue, Jun 25 at 1:03 PM".
    var stamp: String
    /// The last lines of the message before, out of focus under the top bar.
    var earlier: [String] = []
    var title: [PinchOutTimelinePiece]
    /// Up to two photos that peek over the bottom edge of the card.
    var photos: [String] = []
    var messages: [PinchOutTimelineMessage]
}

/// A journal-like conversation that pinches out into a list of days. Closing two fingers shrinks the
/// conversation onto its day's card while the other days slide in around it; tapping a card (or
/// spreading two fingers on it) blows the cards up, shows the date alone for a moment, and brings
/// that day's conversation in message by message.
struct PinchOutTimeline: View {
    var days: [PinchOutTimelineDay] = PinchOutTimelineDay.samples
    /// The day whose conversation is open at the start.
    var initialDay = 1
    /// Pinches out to the days and taps back into the conversation by itself every `loop` seconds.
    var autoPlay = false
    var placeholder = "Share with Sol…"
    var footnote = "Chronicles are only available to subscribers"
    var unlockLabel = "Unlock your chronicles"
    /// The page, top to bottom: the first colour holds to 60% of the height.
    var pageColors: [Color] = [Color(hex: 0xE5E5E5), Color(hex: 0xEBD2CE), Color(hex: 0xD9ADA9)]
    var ink = Color(hex: 0x1E1C1F)
    /// Called when a day's conversation opens.
    var onSelect: ((Int, PinchOutTimelineDay) -> Void)? = nil
    var onUnlock: (() -> Void)? = nil
    var onMenu: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    // The day whose conversation is drawn and the card it shrinks onto; nil until one is opened.
    @State private var day: Int?
    @State private var picked: Int?
    // Which of the two screens takes the touches.
    @State private var timeline = false
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    // 0 with the conversation open, 1 with the days; follows the fingers in between.
    @State private var out = 0.0
    // Seconds since the cards landed.
    @State private var reveal = 0.0
    // Seconds since a card was tapped; 0 when no day is opening.
    @State private var enter = 0.0
    @State private var scrollY = 0.0
    // Changes when the loop wants the days scrolled back to the top.
    @State private var scrollHome = 0
    // What two fingers on the screen are doing: 0 nothing, 1 closing the conversation, 2 opening a card.
    @State private var grip = 0
    @State private var dragging = false
    // The loop's fingers: the two that close, the one that taps, and how far that one is pressed.
    @State private var fingers = 0.0
    @State private var tapShown = 0.0
    @State private var tapDown = 0.0
    // The loop's fingers are on the screen, half way out.
    @State private var held = false
    // Tells one way in from the next, for the moment it ends.
    @State private var run = 0

    private typealias M = PinchOutTimelineMotion

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private var home: Int { min(max(initialDay, 0), days.count - 1) }

    var body: some View {
        GeometryReader { proxy in
            let frame = PinchOutTimelineFrame(proxy: proxy)
            PinchOutTimelineScene(
                out: out, reveal: reveal, enter: enter,
                fingers: fingers, tapShown: tapShown, tapDown: tapDown,
                days: days, day: day ?? home, picked: picked ?? home, home: home,
                timeline: timeline, still: reduceMotion, showsFingers: autoPlay,
                frame: frame, scrollY: scrollY, scrollHome: scrollHome,
                placeholder: placeholder, footnote: footnote, unlockLabel: unlockLabel,
                pageColors: pageColors, ink: ink,
                onCard: { index in
                    touched()
                    if enter == 0 { open(index, auto: false) }
                },
                onToggle: {
                    touched()
                    guard enter == 0 else { return }
                    if timeline {
                        open(picked ?? home, auto: false)
                    } else {
                        UISelectionFeedbackGenerator().selectionChanged()
                        release(toDays: true)
                    }
                },
                onUnlock: {
                    touched()
                    onUnlock?()
                },
                onMenu: {
                    touched()
                    onMenu?()
                },
                onScroll: { scrollY = $0 }
            )
            .ignoresSafeArea()
            .simultaneousGesture(
                MagnifyGesture()
                    .onChanged { pinchChanged($0, frame: frame) }
                    .onEnded { pinchEnded($0) }
            )
            // A scroll of the days is a touch too.
            .simultaneousGesture(
                DragGesture(minimumDistance: 8)
                    .onChanged { _ in
                        if !dragging {
                            dragging = true
                            touched()
                        }
                    }
                    .onEnded { _ in dragging = false }
            )
        }
        .preferredColorScheme(.light)
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The moves

    private func still(_ change: () -> Void) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction, change)
    }

    /// The fingers lift: the spring carries the screen on to the days, or back to the conversation.
    private func release(toDays: Bool) {
        held = false
        timeline = toDays
        withAnimation(reduceMotion ? .easeInOut(duration: M.fade) : M.spring) { out = toDays ? 1 : 0 }
        if toDays {
            withAnimation(.linear(duration: M.revealEnd).delay(M.revealAfter)) { reveal = M.revealEnd }
        } else {
            withAnimation(.linear(duration: M.revealOut)) { reveal = 0 }
        }
    }

    /// Plays the way in from `from` seconds, and ends on the conversation.
    private func play(from: Double) {
        run += 1
        let id = run
        withAnimation(.linear(duration: M.enterEnd - from), completionCriteria: .logicallyComplete) {
            enter = M.enterEnd
        } completion: {
            guard id == run else { return }
            // The conversation is complete under the last frame of the way in: swap the two at once.
            still {
                out = 0
                reveal = 0
                enter = 0
                timeline = false
            }
        }
    }

    /// A day opens, from its card.
    private func open(_ index: Int, auto: Bool, from: Double = 0) {
        if !auto { UISelectionFeedbackGenerator().selectionChanged() }
        picked = index
        day = index
        onSelect?(index, days[index])
        if reduceMotion {
            release(toDays: false)
        } else {
            play(from: from)
        }
    }

    /// The state every loop starts from, set at once.
    private func rest() {
        run += 1
        still {
            held = false
            out = 0
            reveal = 0
            enter = 0
            fingers = 0
            tapShown = 0
            tapDown = 0
            picked = home
            day = home
            timeline = false
        }
        if scrollY != 0 { scrollHome += 1 }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        still {
            fingers = 0
            tapShown = 0
        }
        if held { release(toDays: true) }
        touches += 1
    }

    private func pinchChanged(_ value: MagnifyGesture.Value, frame: PinchOutTimelineFrame) {
        if grip == 0 {
            touched()
            guard enter == 0 else {
                grip = 3
                return
            }
            if !timeline {
                grip = 1
            } else {
                // The card under the fingers is the one that opens.
                let index = Int(((value.startLocation.y + scrollY - frame.cardsTop) / frame.pitch).rounded(.down))
                guard days.indices.contains(index) else {
                    grip = 3
                    return
                }
                picked = index
                day = index
                grip = 2
            }
        }
        let scale = value.magnification
        if grip == 1 {
            out = min(max((1 - scale) / (1 - M.sheetScale), 0), 1)
        } else if grip == 2, !reduceMotion {
            enter = min(max((scale - 1) / (M.grow - 1), 0), 1) * M.blow * M.scrub
        }
    }

    private func pinchEnded(_ value: MagnifyGesture.Value) {
        if grip == 1 {
            release(toDays: out > M.commit)
        } else if grip == 2 {
            let index = picked ?? home
            if reduceMotion {
                if value.magnification > 1.2 { open(index, auto: false) }
            } else if enter > M.blow * M.scrub * 0.5 {
                open(index, auto: false, from: enter)
            } else {
                // A spread that did not go far enough: the cards come back.
                withAnimation(.easeOut(duration: max(enter, 0.05))) { enter = 0 }
            }
        }
        grip = 0
    }

    // MARK: The loop

    @MainActor
    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        do {
            if touches > 0 {
                // After a touch: back to the first day's conversation, then the loop again.
                try await clock.sleep(for: .seconds(M.userHold), tolerance: .zero)
                while enter > 0 { try await clock.sleep(for: .milliseconds(100)) }
                if timeline {
                    picked = home
                    day = home
                    release(toDays: false)
                    try await clock.sleep(for: .seconds(M.home), tolerance: .zero)
                }
            }
            var origin = clock.now
            while true {
                rest()
                try await clock.sleep(until: origin + .seconds(M.pinchAt - M.fingerIn), tolerance: .zero)
                withAnimation(.easeInOut(duration: M.fingerIn)) { fingers = 1 }

                try await clock.sleep(until: origin + .seconds(M.pinchAt), tolerance: .zero)
                held = true
                withAnimation(.timingCurve(0.455, 0.03, 0.515, 0.955, duration: M.pinchFor)) { out = M.pinchTo }

                try await clock.sleep(until: origin + .seconds(M.pinchAt + M.pinchFor), tolerance: .zero)
                withAnimation(.easeInOut(duration: M.fingerOut)) { fingers = 0 }
                release(toDays: true)

                try await clock.sleep(until: origin + .seconds(M.tapAt - M.fingerIn), tolerance: .zero)
                withAnimation(.easeInOut(duration: M.fingerIn)) { tapShown = 1 }

                try await clock.sleep(until: origin + .seconds(M.tapAt), tolerance: .zero)
                withAnimation(.easeInOut(duration: 0.09)) { tapDown = 1 }
                // The step sets what it starts from: the days, landed and written.
                still {
                    out = 1
                    reveal = M.revealEnd
                    timeline = true
                }
                open(home, auto: true)

                // The finger lifts in a step of its own: a press and its release set in one go would cancel out.
                try await clock.sleep(until: origin + .seconds(M.tapAt + 0.1), tolerance: .zero)
                withAnimation(.easeInOut(duration: M.fingerOut)) {
                    tapDown = 0
                    tapShown = 0
                }

                origin += .seconds(M.loop)
                try await clock.sleep(until: origin, tolerance: .zero)
            }
        } catch {}
    }
}

// MARK: - Numbers

/// The timeline and the curves, the same numbers as the React Native and Compose versions.
enum PinchOutTimelineMotion {
    // Going out: the conversation shrinks to this scale on its day's card while the fingers close;
    // the spring finishes the move when they lift. The other cards arrive from `slide` points away.
    static let sheetScale = 0.44
    static let sheetBlur = 14.0
    static let spring = Animation.interpolatingSpring(mass: 1, stiffness: 180, damping: 19)
    // How far the fingers must have taken it for the release to go on to the days.
    static let commit = 0.3
    static let slide = 80.0
    static let slideScale = 0.9
    // What is on the cards shows after they have landed, top card first.
    static let revealAfter = 0.32
    static let revealStagger = 0.06
    static let revealIn = 0.3
    static let revealBlur = 6.0
    static let revealEnd = 0.75
    static let revealOut = 0.15
    static let thumbRise = 34.0

    // Going in, in seconds from the tap: the cards blow up and blur away, the date shows alone, the
    // conversation arrives message by message.
    static let blow = 0.32
    static let grow = 2.3
    static let fly = 0.55
    static let blowBlur = 16.0
    static let dateAt = 0.22
    static let dateFade = 0.2
    static let dateHold = 0.35
    static let chatAt = 0.85
    static let chatStagger = 0.03
    static let chatItemIn = 0.25
    static let chatClear = 0.35
    static let chatFrom = 0.97
    static let chatBlur = 10.0
    // The stagger stops growing after this many messages, so a long day still arrives in time.
    static let chatItems = 8
    static let enterEnd = 1.4
    // A spread of the fingers takes a card this far into the blow-up, and past half of it the day opens.
    static let scrub = 0.8
    // With reduced motion the two screens cross-fade.
    static let fade = 0.25

    // The loop: fingers close on the conversation, lift, and later one taps the day's card.
    static let loop = 9.0
    static let pinchAt = 1.2
    static let pinchFor = 0.5
    static let pinchTo = 0.62
    static let tapAt = 5.0
    static let fingerIn = 0.25
    static let fingerOut = 0.2
    static let fingerSize = 44.0
    // Half the distance between the two fingers before they close, across and down.
    static let fingerReach = CGSize(width: 58, height: 78)
    static let userHold = 3.0
    static let home = 0.7

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

    static func smooth(_ x: Double, _ from: Double, _ to: Double) -> Double {
        let t = clamp01((x - from) / (to - from))
        return t * t * (3 - 2 * t)
    }

    static func easeIn(_ t: Double) -> Double {
        let c = clamp01(t)
        return c * c * c
    }

    static func easeOut(_ t: Double) -> Double {
        let c = 1 - clamp01(t)
        return 1 - c * c * c
    }

    /// How visible the conversation's piece number `index` is, `enter` seconds after a card was tapped.
    static func arrived(_ enter: Double, _ index: Int) -> Double {
        smooth(enter - chatAt - Double(min(index, chatItems)) * chatStagger, 0, chatItemIn)
    }
}

/// Where things are on a screen of a given size.
struct PinchOutTimelineFrame {
    // The conversation.
    static let side = 22.0
    // From the safe area's top edge to the time stamp, and between two messages.
    static let chatTop = 70.0
    static let messageGap = 22.0
    static let textTrail = 56.0
    static let bubbleRadius = 18.0
    static let photo = 90.0
    static let input = 40.0
    static let inputSide = 11.0
    static let inputLift = 2.0
    static let down = 34.0
    static let downLift = 22.0
    // The days.
    static let margin = 16.0
    static let cardsLead = 25.0
    static let gap = 8.0
    static let radius = 20.0
    static let cardMin = 150.0
    static let cardMax = 240.0
    static let cardPad = 22.0
    static let thumb = 84.0
    static let thumbs: [(left: Double, turn: Double)] = [(72, -8), (165, 6)]
    // The room the two lines under the cards take, above the safe area's bottom edge.
    static let footer = 52.0

    let width: Double
    let height: Double
    let top: Double
    let bottom: Double

    init(proxy: GeometryProxy) {
        let insets = proxy.safeAreaInsets
        width = proxy.size.width + insets.leading + insets.trailing
        height = proxy.size.height + insets.top + insets.bottom
        top = insets.top
        bottom = max(insets.bottom, 12)
    }

    var cardsTop: Double { top + Self.cardsLead }
    var cardHeight: Double {
        min(Self.cardMax, max(Self.cardMin, (height - cardsTop - bottom - Self.footer - Self.gap * 2) / 3))
    }
    var cardWidth: Double { width - Self.margin * 2 }
    var pitch: Double { cardHeight + Self.gap }
    var inputTop: Double { height - bottom - Self.inputLift - Self.input }
    func cardTop(_ index: Int) -> Double { cardsTop + Double(index) * pitch }
}

// MARK: - The screen

private struct PinchOutTimelineScene: View, Animatable {
    var out: Double
    var reveal: Double
    var enter: Double
    let fingers: Double
    let tapShown: Double
    let tapDown: Double
    let days: [PinchOutTimelineDay]
    let day: Int
    let picked: Int
    let home: Int
    let timeline: Bool
    let still: Bool
    let showsFingers: Bool
    let frame: PinchOutTimelineFrame
    let scrollY: Double
    let scrollHome: Int
    let placeholder: String
    let footnote: String
    let unlockLabel: String
    let pageColors: [Color]
    let ink: Color
    let onCard: (Int) -> Void
    let onToggle: () -> Void
    let onUnlock: () -> Void
    let onMenu: () -> Void
    let onScroll: (Double) -> Void

    private typealias M = PinchOutTimelineMotion
    private typealias F = PinchOutTimelineFrame

    private enum Palette {
        static let mute = Color(hex: 0x8E8A8C)
        static let placeholder = Color(hex: 0xA39A9B)
        static let glass = Color.white.opacity(0.45)
        static let cardFill = Color.white.opacity(0.38)
        static let cardEdge = Color.white.opacity(0.5)
        static let photoTint = Color(hex: 0xD8CFC9)
        static let finger = Color.rgba(30, 28, 31, 0.16)
        static let fingerEdge = Color.white.opacity(0.7)
        static let underline = Color.rgba(30, 28, 31, 0.45)
    }

    private enum Faces {
        static let reply = PinchOutTimelineFace(size: 16, lineHeight: 22, serif: true)
        static let bubble = PinchOutTimelineFace(size: 15, lineHeight: 20)
        static let stamp = PinchOutTimelineFace(size: 12, lineHeight: 16)
        static let placeholder = PinchOutTimelineFace(size: 15, lineHeight: 20)
        static let cardLabel = PinchOutTimelineFace(size: 11, lineHeight: 14)
        static let cardTitle = PinchOutTimelineFace(size: 19, lineHeight: 26, serif: true)
        static let date = PinchOutTimelineFace(size: 26, lineHeight: 34, serif: true)
        static let footnote = PinchOutTimelineFace(size: 10, lineHeight: 14)
        static let unlock = PinchOutTimelineFace(size: 11, lineHeight: 16, weight: .semibold)
    }

    private struct ScrollKey: PreferenceKey {
        static let defaultValue = 0.0
        static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
    }

    var animatableData: AnimatablePair<Double, AnimatablePair<Double, Double>> {
        get { AnimatablePair(out, AnimatablePair(reveal, enter)) }
        set {
            out = newValue.first
            reveal = newValue.second.first
            enter = newValue.second.second
        }
    }

    private var shown: PinchOutTimelineDay { days[min(day, days.count - 1)] }
    private var last: Int { shown.messages.count + 1 }

    var body: some View {
        let width = frame.width
        let height = frame.height
        ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: pageColors[0], location: 0),
                    .init(color: pageColors[0], location: 0.6),
                    .init(color: pageColors[1], location: 0.85),
                    .init(color: pageColors[2], location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )

            chat
                .allowsHitTesting(!timeline)
                .accessibilityHidden(timeline)

            cards
                .allowsHitTesting(timeline)
                .accessibilityHidden(!timeline)

            VStack(spacing: 0) {
                Text(shown.weekday).face(Faces.date)
                Text(shown.date).face(Faces.date)
            }
            .foregroundStyle(ink)
            .frame(width: width)
            .offset(y: height * 0.453 - 34)
            .opacity(
                M.smooth(enter, M.dateAt, M.dateAt + M.dateFade)
                    * (1 - M.smooth(enter, M.dateAt + M.dateFade + M.dateHold, M.dateAt + M.dateFade * 2 + M.dateHold))
            )
            .allowsHitTesting(false)

            footer
                .frame(width: width)
                .offset(y: height - frame.bottom - 14 - 32)
                .opacity(still ? M.clamp01(out) : M.smooth(out, 0.7, 1) * (1 - M.smooth(enter, 0, 0.15)))
                .allowsHitTesting(timeline)

            // The round button stays through the pinch, and leaves and returns with the conversation's last piece.
            Circle()
                .fill(Color.white.opacity(0.6))
                .overlay { PinchOutTimelineGlyph(kind: .down).stroke(ink, style: Self.stroke(1.5)).frame(width: 14, height: 14) }
                .frame(width: F.down, height: F.down)
                .offset(x: (width - F.down) / 2, y: frame.inputTop - F.downLift - F.down)
                .opacity(enter > 0 ? max(1 - M.smooth(enter, 0, 0.15), M.arrived(enter, last)) : 1)
                .allowsHitTesting(false)

            bar

            if showsFingers { discs }
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private static func stroke(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    // MARK: The conversation

    private func pieceOpacity(_ index: Int, early: Bool = false) -> Double {
        if enter > 0 { return M.arrived(enter, index) }
        return early ? 1 - M.smooth(out, 0, 0.3) : 1
    }

    private var chat: some View {
        let width = frame.width
        let height = frame.height
        var opacity = 1.0
        var shift = 0.0
        var scale = 1.0
        var blur = 0.0
        if still {
            opacity = 1 - M.clamp01(out)
        } else if enter > 0 {
            // Arriving: its pieces fade in by themselves while it comes into focus.
            let clear = M.smooth(enter, M.chatAt, M.chatAt + M.chatClear)
            scale = M.chatFrom + (1 - M.chatFrom) * clear
            blur = M.chatBlur * (1 - clear)
        } else {
            // Leaving: it shrinks towards the middle of its day's card.
            let middle = frame.cardTop(picked) - scrollY + frame.cardHeight / 2
            opacity = 1 - M.smooth(out, 0.12, 0.62)
            shift = (middle - height / 2) * out
            scale = 1 - (1 - M.sheetScale) * out
            blur = M.sheetBlur * M.smooth(out, 0, 0.5)
        }
        let earlier = Array(shown.earlier.suffix(2))

        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(earlier.enumerated()), id: \.offset) { at, line in
                    Text(line)
                        .face(Faces.reply)
                        .lineLimit(1)
                        .foregroundStyle(ink.opacity(at == earlier.count - 1 ? 0.4 : 0.1))
                }
            }
            .blur(radius: 2.5)
            .frame(width: width - F.side * 2, alignment: .leading)
            .offset(x: F.side, y: frame.top - 10)
            .opacity(pieceOpacity(0))

            VStack(alignment: .leading, spacing: F.messageGap) {
                Text(shown.stamp)
                    .face(Faces.stamp)
                    .lineLimit(1)
                    .foregroundStyle(Palette.mute)
                    .frame(width: width)
                    .opacity(pieceOpacity(0, early: true))
                ForEach(Array(shown.messages.enumerated()), id: \.offset) { at, message in
                    bubble(message)
                        .opacity(pieceOpacity(at + 1))
                }
            }
            .padding(.top, frame.top + F.chatTop)
            .frame(width: width, height: frame.inputTop - 6, alignment: .top)
            .clipped()

            HStack(spacing: 8) {
                Circle()
                    .fill(Palette.glass)
                    .overlay { PinchOutTimelineGlyph(kind: .plus).stroke(Palette.placeholder, style: Self.stroke(1.5)).frame(width: 16, height: 16) }
                    .frame(width: F.input, height: F.input)
                HStack(spacing: 0) {
                    Text(placeholder)
                        .face(Faces.placeholder)
                        .lineLimit(1)
                        .foregroundStyle(Palette.placeholder)
                    Spacer(minLength: 8)
                    PinchOutTimelineGlyph(kind: .wave).stroke(Palette.placeholder, style: Self.stroke(1.5)).frame(width: 16, height: 16)
                }
                .padding(.leading, 16)
                .padding(.trailing, 14)
                .frame(height: F.input)
                .background(Capsule().fill(Palette.glass))
            }
            .frame(width: width - F.inputSide * 2, height: F.input)
            .offset(x: F.inputSide, y: frame.inputTop)
            .opacity(pieceOpacity(last, early: true))
            .accessibilityHidden(true)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .scaleEffect(scale)
        .offset(y: shift)
        .blur(radius: blur)
        .opacity(opacity)
    }

    @ViewBuilder
    private func bubble(_ message: PinchOutTimelineMessage) -> some View {
        let width = frame.width
        if let photo = message.photo {
            PinchOutTimelinePhoto(url: photo, tint: Palette.photoTint)
                .frame(width: F.photo, height: F.photo)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                .frame(width: width - F.side * 2, alignment: .trailing)
                .padding(.leading, F.side)
        } else if message.mine {
            HStack(spacing: 0) {
                Spacer(minLength: width * 0.3 - F.side)
                Text(message.text ?? "")
                    .face(Faces.bubble)
                    .foregroundStyle(ink)
                    .padding(.horizontal, 15)
                    .padding(.vertical, 8)
                    .frame(minHeight: 36)
                    .background(alignment: .bottomTrailing) {
                        ZStack(alignment: .bottomTrailing) {
                            RoundedRectangle(cornerRadius: F.bubbleRadius).fill(.white)
                            PinchOutTimelineTail().fill(.white).frame(width: 14, height: 13).offset(x: 4, y: 1)
                        }
                    }
            }
            .frame(width: width - F.side * 2)
            .padding(.leading, F.side)
        } else {
            Text(message.text ?? "")
                .face(Faces.reply)
                .foregroundStyle(ink)
                .frame(width: width - F.side - F.textTrail, alignment: .leading)
                .padding(.leading, F.side)
        }
    }

    // MARK: The days

    private var cards: some View {
        let width = frame.width
        let content = frame.cardTop(days.count) - F.gap + F.footer + frame.bottom
        return ScrollViewReader { reader in
            ScrollView(.vertical, showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    Color.clear
                        .frame(width: width, height: 1)
                        .id("top")
                    ForEach(days.indices, id: \.self) { index in
                        card(index)
                    }
                }
                .frame(width: width, height: content, alignment: .topLeading)
                .background(
                    GeometryReader { inner in
                        Color.clear.preference(key: ScrollKey.self, value: -inner.frame(in: .named("PinchOutTimelineDays")).minY)
                    }
                )
            }
            .coordinateSpace(name: "PinchOutTimelineDays")
            .scrollDisabled(!timeline || enter > 0)
            .onPreferenceChange(ScrollKey.self) { onScroll($0) }
            .onChange(of: scrollHome) { reader.scrollTo("top", anchor: .top) }
        }
        .frame(width: width, height: frame.height)
        .blur(radius: still ? 0 : M.blowBlur * M.easeIn(enter / M.blow))
    }

    private func card(_ index: Int) -> some View {
        let day = days[index]
        let cardWidth = frame.cardWidth
        let cardHeight = frame.cardHeight
        let top = frame.cardTop(index)
        var rect = CGRect(x: F.margin, y: top, width: cardWidth, height: cardHeight)
        var radius = F.radius
        var opacity = M.clamp01(out)
        var shift = 0.0
        var scale = 1.0
        if !still {
            let k = M.easeIn(enter / M.blow)
            let leaving = 1 - M.smooth(enter, M.blow * 0.3, M.blow)
            if picked == index {
                // The sheet: the whole screen at 0, this card at 1; opening, it grows over the screen again.
                let middle = top - scrollY + cardHeight / 2
                rect = CGRect(
                    x: F.margin * out,
                    y: scrollY + (top - scrollY) * out,
                    width: frame.width + (cardWidth - frame.width) * out,
                    height: frame.height + (cardHeight - frame.height) * out
                )
                radius = F.radius * M.smooth(out, 0, 0.5)
                opacity = M.smooth(out, 0.1, 0.7) * leaving
                shift = (frame.height / 2 - middle) * k
                scale = 1 + (M.grow - 1) * k
            } else {
                // The others come in from above and below, and leave the same way.
                let side: Double = index < picked ? -1 : 1
                opacity = M.smooth(out, 0.25, 0.85) * leaving
                shift = side * (M.slide * (1 - out) + frame.height * M.fly * k)
                scale = (M.slideScale + (1 - M.slideScale) * out) * (1 + (M.grow - 1) * 0.3 * k)
            }
        }
        let written = still ? 1 : M.smooth(reveal - Double(index) * M.revealStagger, 0, M.revealIn)
        let up = still ? 1 : M.easeOut((reveal - 0.1) / 0.45)
        let shape = RoundedRectangle(cornerRadius: max(radius, 0), style: .continuous)

        return ZStack(alignment: .topLeading) {
            shape.fill(Palette.cardFill)

            VStack(alignment: .leading, spacing: 8) {
                Text(day.label)
                    .face(Faces.cardLabel)
                    .lineLimit(1)
                    .foregroundStyle(Palette.mute)
                day.title
                    .reduce(Text("")) { $0 + ($1.key ? Text($1.text).underline(true, color: Palette.underline) : Text($1.text)) }
                    .face(Faces.cardTitle)
                    .lineLimit(2)
                    .foregroundStyle(ink)
            }
            .padding(.top, F.cardPad)
            .padding(.horizontal, F.cardPad)
            .frame(width: cardWidth, height: cardHeight, alignment: .topLeading)
            .blur(radius: M.revealBlur * (1 - written))
            .opacity(written)

            ForEach(Array(day.photos.prefix(F.thumbs.count).enumerated()), id: \.offset) { at, photo in
                PinchOutTimelinePhoto(url: photo, tint: Palette.photoTint)
                    .frame(width: F.thumb, height: F.thumb)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .rotationEffect(.degrees(F.thumbs[at].turn))
                    .offset(x: F.thumbs[at].left, y: cardHeight - F.thumb / 2 + M.thumbRise * (1 - up))
                    .opacity(M.clamp01(up * 2))
            }
        }
        .frame(width: max(rect.width, 0), height: max(rect.height, 0), alignment: .topLeading)
        .clipShape(shape)
        .overlay(shape.strokeBorder(Palette.cardEdge, lineWidth: 1))
        .contentShape(Rectangle())
        .onTapGesture { onCard(index) }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("\(day.label). \(day.title.map(\.text).joined())")
        .scaleEffect(scale)
        .offset(x: rect.minX, y: rect.minY + shift)
        .opacity(opacity)
    }

    private var footer: some View {
        VStack(spacing: 2) {
            Text(footnote)
                .face(Faces.footnote)
                .foregroundStyle(Palette.mute)
            Button(action: onUnlock) {
                Text("\(unlockLabel) ›")
                    .face(Faces.unlock)
                    .foregroundStyle(ink)
            }
            .buttonStyle(.plain)
        }
        .lineLimit(1)
    }

    // MARK: The bar and the loop's fingers

    private var bar: some View {
        ZStack(alignment: .topLeading) {
            Button(action: onToggle) {
                PinchOutTimelineGlyph(kind: .collapse)
                    .stroke(Palette.mute, style: Self.stroke(1.4))
                    .frame(width: 16, height: 16)
                    .padding(14)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(timeline ? "Open the day" : "Show all days")
            .offset(x: F.side - 14, y: frame.top + 1 - 14)

            Button(action: onMenu) {
                PinchOutTimelineGlyph(kind: .dots)
                    .fill(Palette.mute)
                    .frame(width: 16, height: 16)
                    .padding(14)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("More")
            .offset(x: frame.width - F.side - 16 - 14, y: frame.top + 1 - 14)
        }
    }

    private var discs: some View {
        let reach = 1 - (1 - M.sheetScale) * min(out, M.pinchTo)
        let center = CGPoint(x: frame.width / 2, y: frame.height * 0.52)
        let target = CGPoint(x: frame.width / 2, y: frame.cardTop(home) + frame.cardHeight / 2)
        let half = M.fingerSize / 2
        return ZStack(alignment: .topLeading) {
            disc
                .offset(x: center.x - half - M.fingerReach.width * reach, y: center.y - half - M.fingerReach.height * reach)
                .opacity(fingers)
            disc
                .offset(x: center.x - half + M.fingerReach.width * reach, y: center.y - half + M.fingerReach.height * reach)
                .opacity(fingers)
            disc
                .scaleEffect(1 - 0.18 * tapDown)
                .offset(x: target.x - half, y: target.y - half)
                .opacity(tapShown)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var disc: some View {
        Circle()
            .fill(Palette.finger)
            .overlay(Circle().strokeBorder(Palette.fingerEdge, lineWidth: 1))
            .frame(width: M.fingerSize, height: M.fingerSize)
    }
}

// MARK: - Parts

/// A system face with the line height the other stacks give it.
private struct PinchOutTimelineFace {
    let font: Font
    /// What the line height adds to the face's own.
    let extra: Double

    init(size: Double, lineHeight: Double, weight: UIFont.Weight = .regular, serif: Bool = false) {
        var face = UIFont.systemFont(ofSize: size, weight: weight)
        if serif, let descriptor = face.fontDescriptor.withDesign(.serif) {
            face = UIFont(descriptor: descriptor, size: size)
        }
        font = Font(face as CTFont)
        extra = max(0, lineHeight - face.lineHeight)
    }
}

private extension View {
    func face(_ face: PinchOutTimelineFace) -> some View {
        font(face.font).lineSpacing(face.extra).padding(.vertical, face.extra / 2)
    }
}

private struct PinchOutTimelinePhoto: View {
    let url: String
    let tint: Color

    var body: some View {
        tint.overlay {
            AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                }
            }
        }
    }
}

/// The icons, drawn in boxes of 16 points.
private struct PinchOutTimelineGlyph: Shape {
    enum Kind { case collapse, down, plus, wave, dots }
    let kind: Kind

    func path(in rect: CGRect) -> Path {
        var path = Path()
        switch kind {
        case .collapse:
            Self.lines(&path, [[1, 1], [6.5, 6.5]], [[6.5, 2.5], [6.5, 6.5], [2.5, 6.5]], [[15, 15], [9.5, 9.5]], [[9.5, 13.5], [9.5, 9.5], [13.5, 9.5]])
        case .down:
            Self.lines(&path, [[8, 2.5], [8, 13.5]], [[3, 8.5], [8, 13.5], [13, 8.5]])
        case .plus:
            Self.lines(&path, [[8, 2], [8, 14]], [[2, 8], [14, 8]])
        case .wave:
            Self.lines(&path, [[2, 6.5], [2, 9.5]], [[5, 4], [5, 12]], [[8, 1.5], [8, 14.5]], [[11, 4.5], [11, 11.5]], [[14, 6.5], [14, 9.5]])
        case .dots:
            for x in [2.5, 8, 13.5] {
                path.addEllipse(in: CGRect(x: x - 1.4, y: 6.6, width: 2.8, height: 2.8))
            }
        }
        return path.applying(CGAffineTransform(scaleX: rect.width / 16, y: rect.height / 16))
    }

    private static func lines(_ path: inout Path, _ strokes: [[Double]]...) {
        for stroke in strokes {
            path.move(to: CGPoint(x: stroke[0][0], y: stroke[0][1]))
            for point in stroke.dropFirst() {
                path.addLine(to: CGPoint(x: point[0], y: point[1]))
            }
        }
    }
}

/// The bubble's tail, in a box of 14 × 13 that hangs 4 points over its bottom right corner.
private struct PinchOutTimelineTail: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 4, y: 0))
        path.addCurve(to: CGPoint(x: 14, y: 12), control1: CGPoint(x: 4, y: 6), control2: CGPoint(x: 7, y: 10))
        path.addCurve(to: CGPoint(x: 0, y: 8), control1: CGPoint(x: 8, y: 13), control2: CGPoint(x: 3, y: 11))
        path.closeSubpath()
        return path
    }
}

// MARK: - Sample days

extension PinchOutTimelineDay {
    private static let balloons = "https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=360&h=360&q=80"
    private static let lagoon = "https://images.unsplash.com/photo-1573843981267-be1999ff37cd?auto=format&fit=crop&w=360&h=360&q=80"

    static let samples: [PinchOutTimelineDay] = [
        PinchOutTimelineDay(
            label: "Saturday, Jun 22",
            weekday: "Saturday",
            date: "June 22",
            stamp: "Sat, Jun 22 at 9:12 AM",
            title: [.init(text: "Introduction to "), .init(text: "Mira’s values", key: true)],
            messages: [
                .init(mine: true, text: "Hi"),
                .init(text: "Hello Mira, I’m Sol. I’d love to know what matters most to you these days."),
                .init(mine: true, text: "Slow mornings"),
                .init(text: "That sounds lovely. Unhurried time has a way of making room for everything else. What does a slow morning look like when it goes just right?"),
                .init(mine: true, text: "Tea, a walk, no phone"),
                .init(text: "A warm cup, some fresh air and nothing asking for your attention. I’ll remember that. Is there anything that tends to get in the way of mornings like these?"),
            ]
        ),
        PinchOutTimelineDay(
            label: "Tuesday, Jun 25",
            weekday: "Tuesday",
            date: "June 25",
            stamp: "Tue, Jun 25 at 1:03 PM",
            earlier: ["and I hope that feeling of ease stays close,", "a small calm to carry through your week."],
            title: [.init(text: "Shared a "), .init(text: "balloon sunrise", key: true), .init(text: ", playing "), .init(text: "Harbor Racers", key: true)],
            photos: [lagoon, balloons],
            messages: [
                .init(mine: true, text: "Yo"),
                .init(text: "Hey Mira, good to see you. How is your day going so far?"),
                .init(mine: true, text: "Doing well"),
                .init(text: "I’m so glad to hear it! A day that starts gently often stays that way, and I know how much a calm rhythm matters to you. What has been helping you feel settled today?"),
                .init(mine: true, photo: balloons),
                .init(text: "Oh, that’s a lovely one! All those balloons drifting over the valley in the first light, in no hurry at all. I can see why you kept it. Are you still up for a round of Harbor Racers tonight, or is today for slower things?"),
            ]
        ),
        PinchOutTimelineDay(
            label: "Wednesday, Jun 26",
            weekday: "Wednesday",
            date: "June 26",
            stamp: "Wed, Jun 26 at 8:40 PM",
            earlier: ["Sleep well, Mira. The balloons will still be", "there in the morning, and so will I."],
            title: [.init(text: "Options for discussing "), .init(text: "Harbor Racers", key: true)],
            messages: [
                .init(mine: true, text: "Quick question"),
                .init(text: "Of course, Mira. What’s on your mind this evening?"),
                .init(mine: true, text: "How do I tell Jo I need a break from Harbor Racers?"),
                .init(text: "It’s kind of you to think it through first. You could say it plainly and warmly: you love playing together, and you need a few quiet evenings. Or offer a smaller ritual in its place, like one short race on Sundays."),
                .init(mine: true, text: "The Sunday idea"),
                .init(text: "I like it too. It keeps what you share, at a gentler pace. Shall we try the words out together first?"),
            ]
        ),
    ]
}
