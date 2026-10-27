import SwiftUI
import UIKit

// MARK: - Palette

/// The colours of the block (hex from the React Native styles and `TourButton`).
enum QuietSignalOnboardingPalette {
    static func hex(_ value: UInt32, _ opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// `styles.root` background.
    static let ground = hex(0x070709)
    /// `styles.heading`.
    static let heading = hex(0xFFFFFF)
    /// `styles.body`.
    static let body = hex(0x7C7C80)
    /// `styles.cta`.
    static let cta = hex(0xF2F2F7)
    /// The Continue button's fill.
    static let continueFill = hex(0x24242B)
    /// `styles.eyebrow`.
    static let eyebrow = hex(0x8A8A96)
    /// `styles.sessionTitle`.
    static let sessionTitle = hex(0xE8E8F0)
    /// `styles.sessionTime`.
    static let sessionTime = hex(0xD8D8E2)
    /// `styles.previewCaption`.
    static let previewCaption = hex(0x8E8E9C)
    /// `styles.previewNote`.
    static let previewNote = hex(0x777783)
    /// A chosen session mode: fill and label.
    static let modeSelectedFill = hex(0xD8D8E0)
    static let modeSelectedInk = hex(0x141419)
    /// A session mode that is not chosen: fill and label.
    static let modeFill = hex(0x212126)
    static let modeInk = hex(0xB7B7C2)
    /// "Back to the introduction": its fill, and `TourButton`'s default label colour.
    static let backFill = hex(0x25252D)
    static let tourInk = hex(0xEAF1FF)
}

// MARK: - Type

/// React Native's `fontScale` on iOS for each text size the user can pick (its fixed table of multipliers), so a
/// line grows by the same amount in both stacks. `maxFontSizeMultiplier` caps it per text.
enum QuietSignalOnboardingType {
    static func fontScale(_ size: DynamicTypeSize) -> Double {
        switch size {
        case .xSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .xLarge: return 1.118
        case .xxLarge: return 1.235
        case .xxxLarge: return 1.353
        case .accessibility1: return 1.786
        case .accessibility2: return 2.143
        case .accessibility3: return 2.643
        case .accessibility4: return 3.143
        case .accessibility5: return 3.571
        @unknown default: return 1
        }
    }
}

/// One `StyleSheet` text style: size, line height and letter spacing in points before the user's text size
/// is applied.
struct QuietSignalOnboardingTextStyle {
    var size: Double
    var lineHeight: Double?
    var kerning: Double = 0
    var weight: Font.Weight = .regular
    var uiWeight: UIFont.Weight = .regular
    var color: Color
    /// `maxFontSizeMultiplier`: the most the user's text size may enlarge this text; nil is no cap.
    var maxScale: Double?
    /// `allowFontScaling`.
    var scales = true
    /// `fontVariant: ['tabular-nums']`.
    var tabularDigits = false

    /// `styles.heading`: 20/27 semibold, -0.2 tracking, capped at 1.6x.
    static let heading = QuietSignalOnboardingTextStyle(
        size: 20, lineHeight: 27, kerning: -0.2, weight: .semibold, uiWeight: .semibold,
        color: QuietSignalOnboardingPalette.heading, maxScale: 1.6)
    /// `styles.body`: 20.5/27, capped at 1.9x.
    static let body = QuietSignalOnboardingTextStyle(
        size: 20.5, lineHeight: 27, color: QuietSignalOnboardingPalette.body, maxScale: 1.9)
    /// `styles.cta`: 15/20 medium, capped at 1.6x.
    static let cta = QuietSignalOnboardingTextStyle(
        size: 15, lineHeight: 20, weight: .medium, uiWeight: .medium,
        color: QuietSignalOnboardingPalette.cta, maxScale: 1.6)
    /// `styles.eyebrow`: 11 with 3 tracking; `allowFontScaling={false}`.
    static let eyebrow = QuietSignalOnboardingTextStyle(
        size: 11, kerning: 3, color: QuietSignalOnboardingPalette.eyebrow, scales: false)
    /// `styles.sessionTitle`: 28/35, -0.7 tracking.
    static let sessionTitle = QuietSignalOnboardingTextStyle(
        size: 28, lineHeight: 35, kerning: -0.7, color: QuietSignalOnboardingPalette.sessionTitle)
    /// `styles.sessionTime`: 43 light, tabular digits.
    static let sessionTime = QuietSignalOnboardingTextStyle(
        size: 43, weight: .light, uiWeight: .light, color: QuietSignalOnboardingPalette.sessionTime, tabularDigits: true)
    /// `styles.previewCaption`: 12.
    static let previewCaption = QuietSignalOnboardingTextStyle(size: 12, color: QuietSignalOnboardingPalette.previewCaption)
    /// `styles.previewNote`: 12/19.
    static let previewNote = QuietSignalOnboardingTextStyle(
        size: 12, lineHeight: 19, color: QuietSignalOnboardingPalette.previewNote)

    /// The label of a `TourButton` given a string: 16/22 semibold, capped at 1.6x.
    static func tourLabel(color: Color) -> QuietSignalOnboardingTextStyle {
        QuietSignalOnboardingTextStyle(
            size: 16, lineHeight: 22, weight: .semibold, uiWeight: .semibold, color: color, maxScale: 1.6)
    }
}

/// Gives a text the exact line height React Native sets with `lineHeight`, centred in its line box. From iOS 26
/// the system has `lineHeight(.exact)`; before it, the missing space is added as line spacing and split above
/// and below (a line cannot be made shorter than the font's own).
private struct QuietSignalOnboardingLineHeight: ViewModifier {
    let points: Double
    let fontSize: Double
    let weight: UIFont.Weight

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.lineHeight(.exact(points: points))
        } else {
            let natural = ceil(UIFont.systemFont(ofSize: fontSize, weight: weight).lineHeight)
            let extra = max(0, points - natural)
            content
                .lineSpacing(extra)
                .padding(.vertical, extra / 2)
        }
    }
}

/// A centred text in one of the block's styles. The user's text size scales it like React Native does
/// (`fontScale`, up to the style's cap), line height included.
struct QuietSignalOnboardingLabel: View {
    let text: String
    let style: QuietSignalOnboardingTextStyle

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private func makeFont(_ size: Double) -> Font {
        let base = Font.system(size: size, weight: style.weight)
        return style.tabularDigits ? base.monospacedDigit() : base
    }

    var body: some View {
        let fontScale = style.scales ? QuietSignalOnboardingType.fontScale(dynamicTypeSize) : 1
        let scale = min(fontScale, style.maxScale ?? .infinity)
        let size = style.size * scale
        let line = Text(verbatim: text)
            .font(makeFont(size))
            .kerning(style.kerning)
            .foregroundStyle(style.color)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
        if let lineHeight = style.lineHeight {
            line.modifier(QuietSignalOnboardingLineHeight(points: lineHeight * scale, fontSize: size, weight: style.uiWeight))
        } else {
            line
        }
    }
}

// MARK: - SwapText

/// `SwapText` (shared/SwapText.tsx): text that changes in place. An invisible copy of the new text sizes the box;
/// the old words leave fast (140 ms) and the new ones settle a beat later from 10 pt below (260 ms), so a line
/// reads as one sentence being rewritten rather than a cut. Under Reduce Motion it is a plain cross-fade. The
/// first render does not animate.
struct QuietSignalOnboardingSwapText: View {
    let text: String
    let style: QuietSignalOnboardingTextStyle
    let reduced: Bool

    @State private var shown: String
    @State private var leaving: String?
    @State private var changedAt: Date?

    init(text: String, style: QuietSignalOnboardingTextStyle, reduced: Bool) {
        self.text = text
        self.style = style
        self.reduced = reduced
        _shown = State(initialValue: text)
    }

    var body: some View {
        QuietSignalOnboardingLabel(text: text, style: style)
            .opacity(0)
            .accessibilityHidden(true)
            .overlay {
                TimelineView(.animation(paused: changedAt == nil || leaving == nil)) { timeline in
                    layers(at: timeline.date)
                }
            }
            .onChange(of: text) { _, next in
                leaving = shown
                shown = next
                changedAt = Date()
            }
            .task(id: changedAt) {
                guard changedAt != nil else { return }
                do {
                    try await Task.sleep(nanoseconds: 450_000_000)
                } catch {
                    return
                }
                leaving = nil
            }
    }

    @ViewBuilder
    private func layers(at date: Date) -> some View {
        let values = Self.values(elapsed: changedAt.map { date.timeIntervalSince($0) }, reduced: reduced)
        ZStack {
            if let leaving, values.outgoing > 0 {
                QuietSignalOnboardingLabel(text: leaving, style: style)
                    .opacity(values.outgoing)
                    .accessibilityHidden(true)
            }
            QuietSignalOnboardingLabel(text: shown, style: style)
                .opacity(values.incoming)
                .offset(y: values.lift)
        }
    }

    /// The enter (`FadeInDown`, 260 ms after 60 ms, from 10 pt below, on `SETTLE`) and exit (`FadeOut`, 140 ms)
    /// of the two layers; under Reduce Motion a 180 ms fade in and a 120 ms fade out on Reanimated's default
    /// ease. `elapsed` is nil before the first change.
    static func values(elapsed: Double?, reduced: Bool) -> (incoming: Double, lift: Double, outgoing: Double) {
        typealias M = QuietSignalOnboardingMotion
        guard let elapsed else { return (1, 0, 0) }
        if reduced {
            let out = M.quadInOut(elapsed / 0.12)
            let into = M.quadInOut(elapsed / 0.18)
            return (into, 0, 1 - out)
        }
        let out = M.enter(elapsed / 0.14)
        let into = M.enter((elapsed - 0.06) / 0.26)
        return (into, 10 * (1 - into), 1 - out)
    }
}

// MARK: - Buttons

/// `TourButton` (TourButton.tsx): a 48 pt minimum, radius 18 fill with its label centred. The whole button
/// settles to 0.97 in 120 ms on the strong ease-out while pressed (no motion under Reduce Motion).
struct QuietSignalOnboardingTourButton<Content: View>: View {
    let label: String
    let reduced: Bool
    let background: Color
    let onPressIn: () -> Void
    let onPressOut: () -> Void
    let onPress: () -> Void
    let content: Content

    @State private var pressed = false

    /// `transitionDuration: 120` on `cubicBezier(.23, 1, .32, 1)`.
    private static var pressCurve: Animation {
        Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.12)
    }

    init(
        label: String,
        reduced: Bool,
        background: Color,
        onPressIn: @escaping () -> Void,
        onPressOut: @escaping () -> Void,
        onPress: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.label = label
        self.reduced = reduced
        self.background = background
        self.onPressIn = onPressIn
        self.onPressOut = onPressOut
        self.onPress = onPress
        self.content = content()
    }

    var body: some View {
        content
            .padding(.horizontal, 15)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(RoundedRectangle(cornerRadius: 18, style: .circular).fill(background))
            .scaleEffect(pressed && !reduced ? 0.97 : 1)
            .animation(reduced ? nil : Self.pressCurve, value: pressed)
            .overlay {
                QuietSignalOnboardingPressSurface(
                    onPressIn: {
                        pressed = true
                        onPressIn()
                    },
                    onPressOut: {
                        pressed = false
                        onPressOut()
                    },
                    onPress: onPress
                )
                .accessibilityHidden(true)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: label))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { onPress() }
    }
}

/// A zero-delay press recogniser over a button, standing in for the `Pressable`: `onPressIn` when the finger
/// lands, `onPressOut` when it leaves the button's region (the button plus 16 pt, the `pressRetentionOffset`) or
/// lifts, and `onPress` after the lift inside the region (after `onPressOut`, as React Native orders them).
///
/// It is a recogniser that allows the others beside it, not raw touches, so the gallery's back-swipe (or the
/// scroll view around the session screen) does not end the press while the finger is still down.
struct QuietSignalOnboardingPressSurface: UIViewRepresentable {
    var onPressIn: () -> Void
    var onPressOut: () -> Void
    var onPress: () -> Void

    func makeUIView(context: Context) -> QuietSignalOnboardingPressView {
        QuietSignalOnboardingPressView()
    }

    func updateUIView(_ view: QuietSignalOnboardingPressView, context: Context) {
        view.onPressIn = onPressIn
        view.onPressOut = onPressOut
        view.onPress = onPress
    }
}

final class QuietSignalOnboardingPressView: UIView, UIGestureRecognizerDelegate {
    var onPressIn: () -> Void = {}
    var onPressOut: () -> Void = {}
    var onPress: () -> Void = {}

    /// The finger is inside the press region.
    private var inside = false
    /// A press is in progress.
    private var active = false

    /// `pressRetentionOffset={16}`.
    private let retention: CGFloat = 16

    override init(frame: CGRect) {
        super.init(frame: frame)
        isAccessibilityElement = false
        backgroundColor = .clear
        let press = UILongPressGestureRecognizer(target: self, action: #selector(track(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = false
        press.delegate = self
        addGestureRecognizer(press)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("QuietSignalOnboardingPressView is created in code")
    }

    func gestureRecognizer(
        _ recognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func track(_ press: UILongPressGestureRecognizer) {
        let region = bounds.insetBy(dx: -retention, dy: -retention)
        switch press.state {
        case .began:
            active = true
            inside = true
            onPressIn()
        case .changed:
            guard active else { return }
            let within = region.contains(press.location(in: self))
            if inside && !within {
                inside = false
                onPressOut()
            } else if !inside && within {
                inside = true
                onPressIn()
            }
        case .ended:
            guard active else { return }
            active = false
            if inside {
                inside = false
                onPressOut()
                onPress()
            }
        case .cancelled, .failed:
            guard active else { return }
            active = false
            if inside {
                inside = false
                onPressOut()
            }
        default:
            break
        }
    }
}

// MARK: - Session entrance

/// `rise(order)` (QuietSignalOnboarding.tsx:78): the session screen arrives as a short cascade, 70 ms a group,
/// each settling 12 pt on `cubic-bezier(.23, 1, .32, 1)` over 420 ms after 100 ms. Under Reduce Motion it is
/// simply there.
struct QuietSignalOnboardingRise: ViewModifier {
    let order: Int
    let reduced: Bool

    @State private var entered = false

    func body(content: Content) -> some View {
        let settled = entered || reduced
        let curve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.42).delay(0.1 + Double(order) * 0.07)
        content
            .opacity(settled ? 1 : 0)
            .offset(y: settled ? 0 : 12)
            .animation(reduced ? nil : curve, value: entered)
            .onAppear { entered = true }
    }
}
