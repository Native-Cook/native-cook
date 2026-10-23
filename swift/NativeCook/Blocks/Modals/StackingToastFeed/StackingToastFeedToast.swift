import SwiftUI

enum StackingToastFeedKind {
    case error, warning, info, success
}

struct StackingToastFeedMessage: Equatable {
    var kind: StackingToastFeedKind
    var title: String
    /// The text before the link, the link itself, and what comes after it.
    var message: String
    var link: String? = nil
    var tail: String? = nil
}

struct StackingToastFeedItem: Identifiable, Equatable {
    let id: Int
    let message: StackingToastFeedMessage
    /// Set while the toast leaves: 1 to the right, -1 to the left, 0 shrinks and fades.
    var leaving: Int? = nil
}

private typealias M = StackingToastFeedMotion

/// One toast. Its place comes from `index` alone: a spring moves `slot` there, so a toast that
/// enters or leaves lets the others reflow without separate animations.
struct StackingToastFeedToast: View {
    private struct Look {
        let band: Color
        let ink: Color
        let icon: String
    }

    private static func look(_ kind: StackingToastFeedKind) -> Look {
        switch kind {
        case .error: Look(band: Color(hex: 0xffdadd), ink: Color(hex: 0xd6212a), icon: "xmark.circle")
        case .warning: Look(band: Color(hex: 0xfff8c4), ink: Color(hex: 0x857400), icon: "exclamationmark.triangle")
        case .info: Look(band: Color(hex: 0xd8f1ff), ink: Color(hex: 0x0b4c8c), icon: "info.circle")
        case .success: Look(band: Color(hex: 0xddfbd7), ink: Color(hex: 0x1e8a1e), icon: "checkmark.circle")
        }
    }

    let date: Date
    let item: StackingToastFeedItem
    /// Place in the pile among the toasts that stay: 0 is the front.
    let index: Int
    /// 0 when the toasts are spread out, 1 when they are collapsed into a pile.
    let stack: Double
    let width: Double
    let screenWidth: Double
    let reduced: Bool
    let onLeave: (Int, Int) -> Void
    let onGone: (Int) -> Void

    @State private var slot: StackingToastFeedChannel
    @State private var enter = StackingToastFeedChannel(0)
    @State private var shake = StackingToastFeedChannel(0)
    @State private var drag = StackingToastFeedChannel(0)
    @State private var shrink = StackingToastFeedChannel(1)
    @State private var gone = StackingToastFeedChannel(0)
    @State private var panning = false
    @Environment(\.displayScale) private var displayScale

    init(
        date: Date, item: StackingToastFeedItem, index: Int, stack: Double, width: Double, screenWidth: Double, reduced: Bool,
        onLeave: @escaping (Int, Int) -> Void, onGone: @escaping (Int) -> Void
    ) {
        self.date = date
        self.item = item
        self.index = index
        self.stack = stack
        self.width = width
        self.screenWidth = screenWidth
        self.reduced = reduced
        self.onLeave = onLeave
        self.onGone = onGone
        _slot = State(initialValue: StackingToastFeedChannel(Double(index)))
    }

    var body: some View {
        let look = Self.look(item.message.kind)
        let p = max(slot.value(at: date), 0)
        let spreadY = M.spreadTop + (M.toastHeight + M.toastGap) * p
        let stackedY = M.spreadTop + M.stackStep * p
        let scale = (1 + (max(M.stackMinScale, 1 - M.stackShrink * p) - 1) * stack) * shrink.value(at: date)
        let entering = enter.value(at: date)
        let y = spreadY + (stackedY - spreadY) * stack - (1 - entering) * (M.spreadTop + M.toastHeight + 24)
        let dragged = drag.value(at: date)
        let tilt = reduced ? 0 : dragged / screenWidth * M.tiltDegrees
        let away = gone.value(at: date)
        let opacity = reduced
            ? min(1, entering) * (1 - away)
            : (1 - away * (item.leaving == 0 ? 1 : 0.9)) * min(1, entering * 4)

        HStack(spacing: 0) {
            Image(systemName: look.icon)
                .font(.system(size: 22))
                .foregroundStyle(look.ink)
                .frame(width: 52)
                .frame(maxHeight: .infinity)
                .background(look.band)
            VStack(alignment: .leading, spacing: 0) {
                Text(item.message.title)
                    .font(.system(size: 17, weight: .bold))
                    .tracking(-0.3)
                    .foregroundStyle(Color(hex: 0x111318))
                    .lineLimit(1)
                    .frame(height: 22)
                copy(item.message)
                    .font(.system(size: 14))
                    // 19 pt lines, as in the React Native version.
                    .lineSpacing(19 - UIFont.systemFont(ofSize: 14).lineHeight)
                    .foregroundStyle(Color(hex: 0x5f636b))
                    .lineLimit(2)
                    .padding(.top, 2)
            }
            .padding(.leading, 14)
            .padding(.trailing, 40)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .frame(width: width, height: M.toastHeight)
        .background(Color.white)
        .overlay(alignment: .topTrailing) {
            Button {
                onLeave(item.id, 0)
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color(hex: 0x8a8d93))
                    .frame(width: 28, height: 28)
                    .contentShape(Rectangle().inset(by: -8))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss")
            .padding(.top, 10)
            .padding(.trailing, 10)
        }
        .overlay(Color.black.opacity(M.stackDim * p * stack).allowsHitTesting(false))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(Color.rgba(17, 19, 24, 0.08), lineWidth: 1 / displayScale)
        )
        .shadow(color: Color.rgba(17, 19, 24, 0.08), radius: 1.5, y: 1)
        .shadow(color: Color.rgba(17, 19, 24, 0.14), radius: 14, y: 12)
        .scaleEffect(scale, anchor: .top)
        .rotationEffect(.degrees(tilt), anchor: .top)
        .opacity(opacity)
        .offset(x: 20 + shake.value(at: date) + dragged, y: y)
        .zIndex(Double(100 - index))
        .gesture(pan)
        .onAppear { enterAnimation() }
        .onChange(of: index) { _, now in
            guard item.leaving == nil else { return }
            slot.go(to: Double(now), reduced ? .timing(M.shrinkFor, M.inOutQuad) : .spring(M.slideSpring))
        }
        .task(id: item.leaving) { await leave() }
    }

    private func copy(_ message: StackingToastFeedMessage) -> Text {
        var text = Text(message.message)
        if let link = message.link { text = text + Text(link).underline().foregroundColor(Color(hex: 0x2b2e35)) }
        if let tail = message.tail { text = text + Text(tail) }
        return text
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 10)
            .onChanged { value in
                guard item.leaving == nil else { return }
                // A mostly vertical drag is not a swipe.
                if !panning {
                    guard abs(value.translation.width) > abs(value.translation.height) else { return }
                    panning = true
                }
                drag.set(value.translation.width)
            }
            .onEnded { value in
                guard panning else { return }
                panning = false
                if abs(value.translation.width) > M.swipeDistance || abs(value.velocity.width) > M.swipeSpeed {
                    onLeave(item.id, value.translation.width > 0 ? 1 : -1)
                } else {
                    drag.go(to: 0, .spring(Spring(duration: 0.35, bounce: 1 - 0.8)))
                }
            }
    }

    private func enterAnimation() {
        if reduced {
            enter.go(to: 1, .timing(M.fadeFor, M.inOutQuad))
            return
        }
        enter.go(to: 1, .spring(M.slideSpring))
        let points = [M.shakePoints, -M.shakePoints, M.shakePoints, -M.shakePoints, 0]
        shake.go(points.map { .init(to: $0, curve: .timing(M.shakeFor, M.inOutQuad)) })
    }

    /// Runs the exit when `leaving` is set, and tells the feed once the toast is gone.
    private func leave() async {
        guard let leaving = item.leaving else { return }
        let seconds: Double
        if leaving == 0 || reduced {
            seconds = reduced ? M.fadeFor : M.shrinkFor
            shrink.go(to: reduced ? 1 : M.shrinkTo, .timing(seconds, M.inOutQuad))
            gone.go(to: 1, .timing(seconds, M.inOutQuad))
        } else {
            // The swipe starts as a finger would: slowly, then it lets go.
            seconds = M.swipeFor
            drag.go(to: Double(leaving) * screenWidth * 1.1, .timing(seconds, M.inQuad))
            gone.go(to: 1, .timing(seconds, M.inCubic))
        }
        try? await Task.sleep(for: .seconds(seconds), tolerance: .zero)
        guard !Task.isCancelled else { return }
        onGone(item.id)
    }
}
