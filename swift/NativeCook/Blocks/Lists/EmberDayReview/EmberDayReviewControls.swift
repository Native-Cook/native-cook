import SwiftUI

/// A button that shows nothing of its own: no highlight, no dimming. React Native's bare `Pressable`.
struct EmberDayReviewFlatButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
    }
}

/// `PressScale`: the surface settles to `scale` in 150 ms on `bezier(0.23, 1, 0.32, 1)` and returns on
/// the same curve. Under Reduce Motion it dims to 0.72 instead.
struct EmberDayReviewPressScaleStyle: ButtonStyle {
    var scale = 0.98
    var reduced: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(!reduced && configuration.isPressed ? scale : 1)
            .opacity(reduced && configuration.isPressed ? 0.72 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
    }
}

/// The footer button: `opacity: pressed ? 0.6 : 1`, at once.
struct EmberDayReviewDimButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// `SwapText`: text that changes in place. The old words leave in 140 ms and the new ones settle
/// 60 ms later from 10 pt below in 260 ms, both on `bezier(0.23, 1, 0.32, 1)`. Under Reduce Motion
/// it is a plain cross-fade (120 ms out, 180 ms in). The first render does not animate.
struct EmberDayReviewSwapText: View {
    let text: String
    let reduced: Bool
    let size: CGFloat
    let weight: Font.Weight
    let color: Color
    let cap: Double

    /// The text the animated copy last settled on; while it differs from `text`, the swap has just begun.
    @State private var settled: String
    @State private var leaving: String?
    @State private var changedAt: Date?
    @State private var animating = false

    init(text: String, reduced: Bool, size: CGFloat, weight: Font.Weight, color: Color, cap: Double) {
        self.text = text
        self.reduced = reduced
        self.size = size
        self.weight = weight
        self.color = color
        self.cap = cap
        _settled = State(initialValue: text)
    }

    var body: some View {
        // The ghost line sizes the box; the animated copies are laid over it.
        label(text)
            .opacity(0)
            .overlay(alignment: .topLeading) {
                TimelineView(.animation(paused: !animating)) { timeline in
                    copies(at: timeline.date)
                }
            }
            .onChange(of: text) { old, new in
                let started = Date()
                leaving = old
                settled = new
                changedAt = started
                animating = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                    if changedAt == started {
                        animating = false
                        leaving = nil
                    }
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(text)
    }

    private func label(_ value: String) -> some View {
        EmberDayReviewLabel(value, size: size, weight: weight, color: color, cap: cap, lineLimit: 1)
            .fixedSize()
    }

    private func copies(at now: Date) -> some View {
        // The frame in which `text` changes is drawn before `onChange` runs: treat it as the start of the swap.
        let pending = settled != text
        let showsChange = pending || changedAt != nil
        let outgoing = pending ? settled : leaving
        let elapsed = pending ? 0 : (changedAt.map { max(0, now.timeIntervalSince($0)) } ?? 10)
        let curve = EmberDayReviewBezier.out
        let exitDuration = reduced ? 0.12 : 0.14
        let enterDelay = reduced ? 0.0 : 0.06
        let enterDuration = reduced ? 0.18 : 0.26
        let exitProgress = EmberDayReviewMath.clamp(elapsed / exitDuration)
        let exitOpacity = 1 - (reduced ? exitProgress : curve.value(at: exitProgress))
        let enterProgress = EmberDayReviewMath.clamp((elapsed - enterDelay) / enterDuration)
        let enterEased = reduced ? enterProgress : curve.value(at: enterProgress)
        return ZStack(alignment: .topLeading) {
            if let outgoing, elapsed < exitDuration {
                label(outgoing).opacity(exitOpacity)
            }
            label(text)
                .opacity(showsChange ? enterEased : 1)
                .offset(y: !showsChange || reduced ? 0 : 10 * (1 - enterEased))
        }
    }
}

/// `flexDirection: 'row'` with `flexWrap: 'wrap'` and a gap, used for the day tabs at large text sizes.
struct EmberDayReviewFlow: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: proposal.width ?? rows.width, height: rows.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = arrange(width: bounds.width, subviews: subviews)
        for (index, frame) in rows.frames.enumerated() {
            subviews[index].place(
                at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                proposal: ProposedViewSize(frame.size)
            )
        }
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> (frames: [CGRect], width: CGFloat, height: CGFloat) {
        var frames: [CGRect] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var widest: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            frames.append(CGRect(origin: CGPoint(x: x, y: y), size: size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            widest = max(widest, x - spacing)
        }
        return (frames, widest, y + rowHeight)
    }
}
