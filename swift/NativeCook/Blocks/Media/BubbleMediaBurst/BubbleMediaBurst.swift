import SwiftUI

private typealias M = BubbleMediaBurstMotion

private func bubbleMediaBurstPhoto(_ id: String, _ size: Int) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=\(size)&h=\(size)&q=80"
}

extension BubbleMediaBurstItem {
    static let defaults: [BubbleMediaBurstItem] = [
        BubbleMediaBurstItem(title: "Portrait", photo: bubbleMediaBurstPhoto("1517841905240-472988babdf9", 640), tint: Color(hex: 0x3F6F86), glow: Color(hex: 0xBFE3F2)),
        BubbleMediaBurstItem(title: "Balloons", photo: bubbleMediaBurstPhoto("1641128324972-af3212f0f6bd", 320), tint: Color(hex: 0x2F7FD0), glow: Color(hex: 0xFFD1B8)),
        BubbleMediaBurstItem(title: "Forest", photo: bubbleMediaBurstPhoto("1441974231531-c6227db76b6e", 320), tint: Color(hex: 0x5D6B3A), glow: Color(hex: 0xE3F0A8)),
        BubbleMediaBurstItem(title: "City lights", photo: bubbleMediaBurstPhoto("1496442226666-8d4d0e62e6e9", 320), tint: Color(hex: 0x3A2A26), glow: Color(hex: 0xFFB38A)),
        BubbleMediaBurstItem(title: "Sky", photo: bubbleMediaBurstPhoto("1506905925346-21bda4d32df4", 120), tint: Color(hex: 0x9AA6C4), glow: Color(hex: 0xFFD6E4)),
    ]
}

private enum Metrics {
    static let background = Color(hex: 0xEFEFEF)
    static let accent = Color(hex: 0xB9B4CF)
    static let ink = Color(hex: 0x1A1A1A)
    static let mute = Color(hex: 0x8A8A8A)
    // Light from above on the "+": a white wash fading to a faint shade at the bottom.
    static let buttonSheen = Gradient(colors: [.white.opacity(0.22), .white.opacity(0), .black.opacity(0.06)])
    static let buttonRim = Color.white.opacity(0.3)
    static let buttonShadow = Color.rgba(70, 60, 110, 0.18)
    static let buttonShadowY = 8.0
    static let buttonShadowRadius = 12.0

    static let side = 24.0
    static let headTop = 24.0
    static let titleLine = 34.0
    static let hintGap = 6.0
    static let hintLine = 20.0
    /// The centre of the "+" sits this far above the bottom safe area.
    static let buttonLift = 120.0
    /// Room kept between the hint and the top of the cluster.
    static let clusterGap = 24.0
    /// The "+" at cluster scale 1: its diameter, and the length and weight of its strokes.
    static let button = 56.0
    static let plus = 20.0
    static let plusStroke = 2.0
    /// The cluster grows with the screen up to this scale, and shrinks on small ones down to the other.
    static let maxScale = 1.3
    static let minScale = 0.6
}

/// A media "add" screen: a round "+" that opens a cluster of photos in glass bubbles. They are born
/// behind the "+", travel up along a curve to their places with a springy overshoot, float there, and
/// fall back into it one after the other when it is pressed again; tapping a bubble picks it.
struct BubbleMediaBurst: View {
    var title = "Add to your mix"
    var hint = "Tap + to open your library"
    /// Up to five photos, from the big bubble to the tiny one.
    var items: [BubbleMediaBurstItem] = BubbleMediaBurstItem.defaults
    var background = Metrics.background
    /// The fill of the "+".
    var accent = Metrics.accent
    /// Opens the bubbles, picks one and closes them again by itself every `loop` seconds.
    var autoPlay = false
    var openLabel = "Open library"
    var closeLabel = "Close library"
    var onOpenChange: ((Bool) -> Void)? = nil
    var onSelect: ((BubbleMediaBurstItem, Bool) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    /// The events made by hand; the autoPlay's come from its loop start, nil while a touch holds it off.
    @State private var hand = M.Events(count: M.slots.count)
    @State private var loopStart: Double? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Keeps the clock running after a touch until everything is still.
    @State private var awake = false
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    private var shown: [BubbleMediaBurstItem] { Array(items.prefix(M.slots.count)) }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let button = CGPoint(x: width / 2, y: height - insets.bottom - Metrics.buttonLift)
            let headBottom = insets.top + Metrics.headTop + Metrics.titleLine + Metrics.hintGap + Metrics.hintLine
            let scale = max(
                Metrics.minScale,
                min(Metrics.maxScale, (width - Metrics.side * 2) / M.clusterWidth, (button.y - headBottom - Metrics.clusterGap) / M.clusterHeight)
            )
            let places = M.slots.map { slot -> M.Place in
                let rest = CGPoint(x: button.x + slot.x * scale, y: button.y - slot.y * scale)
                // The path bows to the left of the straight line from the "+", so the bubbles swirl out.
                let dx = rest.x - button.x
                let dy = rest.y - button.y
                return M.Place(rest: rest, control: CGPoint(x: button.x + dx / 2 + M.curve * dy, y: button.y + dy / 2 - M.curve * dx), button: button)
            }

            TimelineView(.animation(paused: loopStart == nil && !awake)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let e = events(at: now)

                ZStack(alignment: .topLeading) {
                    background

                    VStack(alignment: .leading, spacing: Metrics.hintGap) {
                        Text(title)
                            .font(.system(size: 28, weight: .semibold))
                            .tracking(-0.4)
                            .foregroundStyle(Metrics.ink)
                            .lineLimit(1)
                            .frame(height: Metrics.titleLine)
                            .accessibilityAddTraits(.isHeader)
                        Text(hint)
                            .font(.system(size: 15))
                            .foregroundStyle(Metrics.mute)
                            .lineLimit(1)
                            .frame(height: Metrics.hintLine)
                    }
                    .padding(.top, insets.top + Metrics.headTop)
                    .padding(.horizontal, Metrics.side)

                    ForEach(shown.indices, id: \.self) { i in
                        bubble(i, place: places[i], scale: scale, now: now, e: e)
                    }

                    plusButton(size: Metrics.button * scale, scale: scale, now: now, e: e)
                        .position(button)
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .onChange(of: e.isOpen) { _, open in onOpenChange?(open) }
                .onChange(of: e.pickOn) { old, new in
                    for i in new.indices where i < old.count && old[i] != new[i] && i < shown.count {
                        onSelect?(shown[i], new[i])
                    }
                }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            tapHaptic.prepare()
            selectHaptic.prepare()
        }
        .task(id: BubbleMediaBurstLoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func bubble(_ i: Int, place: M.Place, scale: Double, now: Double, e: M.Events) -> some View {
        let slot = M.slots[i]
        let p = M.pose(slot, place, now: now, openAt: e.openAt, closeAt: e.closeAt, still: still)
        let pick = M.pickAmount(on: e.pickOn[i], at: e.pickAt[i], now: now, still: still)
        let item = shown[i]
        return Button {
            selectHaptic.selectionChanged()
            takeOver()
            hand.pickOn[i].toggle()
            hand.pickAt[i] = Date.now.timeIntervalSinceReferenceDate
        } label: {
            BubbleMediaBurstBubble(item: item, size: slot.size * scale, pick: pick)
                .contentShape(Circle())
        }
        .buttonStyle(BubbleMediaBurstPressStyle())
        .scaleEffect(max(0.001, p.scale * (1 + (M.pickScale - 1) * (still ? 0 : pick))))
        .opacity(p.opacity)
        .position(x: p.x, y: p.y)
        .allowsHitTesting(e.isOpen)
        .accessibilityHidden(!e.isOpen)
        .accessibilityLabel(item.title)
        .accessibilityAddTraits(e.pickOn[i] ? .isSelected : [])
    }

    private func plusButton(size: Double, scale: Double, now: Double, e: M.Events) -> some View {
        let arm = Metrics.plus * scale
        let stroke = Metrics.plusStroke * scale
        return Button {
            takeOver()
            let at = Date.now.timeIntervalSinceReferenceDate
            if hand.isOpen { hand.closeAt = at } else { hand.openAt = at }
        } label: {
            Circle()
                .fill(accent)
                .overlay(Circle().fill(LinearGradient(gradient: Metrics.buttonSheen, startPoint: .top, endPoint: .bottom)))
                .overlay(Circle().strokeBorder(Metrics.buttonRim, lineWidth: 1))
                .overlay {
                    ZStack {
                        RoundedRectangle(cornerRadius: stroke / 2).frame(width: arm, height: stroke)
                        RoundedRectangle(cornerRadius: stroke / 2).frame(width: stroke, height: arm)
                    }
                    .foregroundStyle(.white)
                    .rotationEffect(.degrees(M.plusTurn(e, now: now, still: still)))
                }
                .frame(width: size, height: size)
                .shadow(color: Metrics.buttonShadow, radius: Metrics.buttonShadowRadius, y: Metrics.buttonShadowY)
                .contentShape(Circle())
        }
        .buttonStyle(BubbleMediaBurstPressStyle { down in
            if down { tapHaptic.impactOccurred() }
            takeOver()
            if down { hand.downAt = Date.now.timeIntervalSinceReferenceDate } else { hand.upAt = Date.now.timeIntervalSinceReferenceDate }
        })
        .scaleEffect(M.buttonScale(e, now: now, still: still))
        .accessibilityLabel(e.isOpen ? closeLabel : openLabel)
    }

    private func events(at now: Double) -> M.Events {
        guard let start = loopStart else { return hand }
        return M.autoEvents(now: now, start: start, count: M.slots.count)
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func takeOver() {
        if let start = loopStart {
            hand = M.autoEvents(now: Date.now.timeIntervalSinceReferenceDate, start: start, count: M.slots.count)
            loopStart = nil
        }
        awake = true
        touches += 1
    }

    private func loop() async {
        guard autoPlay else {
            // Let the clock stop once a touch has played out and the bubbles are away.
            guard touches > 0 else { return }
            try? await Task.sleep(for: .seconds(M.settle), tolerance: .zero)
            if !Task.isCancelled, !hand.isOpen { awake = false }
            return
        }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            // Back to rest the way a tap on "+" goes there, then the loop starts again.
            if hand.isOpen {
                hand.closeAt = Date.now.timeIntervalSinceReferenceDate
                try? await Task.sleep(for: .seconds(M.settle), tolerance: .zero)
                guard !Task.isCancelled else { return }
            }
        }
        hand = M.Events(count: M.slots.count)
        loopStart = Date.now.timeIntervalSinceReferenceDate
    }
}

private struct BubbleMediaBurstLoopKey: Equatable {
    let on: Bool
    let touches: Int
}

/// Shows the label as it is and reports when the finger goes down and comes up.
private struct BubbleMediaBurstPressStyle: ButtonStyle {
    var onPress: (Bool) -> Void = { _ in }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of: configuration.isPressed) { _, down in onPress(down) }
    }
}

#Preview {
    BubbleMediaBurst(autoPlay: true)
}
