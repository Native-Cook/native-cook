import CoreText
import SwiftUI
import UIKit

// MARK: - Fonts

/// Inter 24pt Regular and Bold (SIL OFL 1.1, `Assets/TactileCalculatorWelcome-Inter-OFL.txt`),
/// the files `useFonts` loads in the React Native component. The face is registered once for
/// the process; a view that uses it calls `registered` first.
enum TactileCalculatorWelcomeFonts {
    /// PostScript names of the bundled files.
    static let regular = "Inter24pt-Regular"
    static let bold = "Inter24pt-Bold"

    static let registered: Bool = {
        for name in ["TactileCalculatorWelcome-Inter-Regular", "TactileCalculatorWelcome-Inter-Bold"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        return true
    }()
}

// MARK: - Copy

/// The words on screen, character for character as in the React Native component. The minus,
/// times and divide signs are written as scalars: U+2212, U+00D7 and U+00F7.
enum TactileCalculatorWelcomeCopy {
    /// `expression`: what the headline says until Continue is pressed.
    static func expression(for symbol: TactileCalculatorWelcomeSymbol?) -> String {
        switch symbol {
        case .plus: return "8 + 4 = 12"
        case .minus: return "8 \u{2212} 4 = 4"
        case .multiply: return "8 \u{00D7} 4 = 32"
        case .divide: return "8 \u{00F7} 4 = 2"
        case .equals: return "8 + 4 = 12"
        case nil: return "Welcome to"
        }
    }

    static func headline(started: Bool, symbol: TactileCalculatorWelcomeSymbol?) -> String {
        started ? "A little clarity, every day." : expression(for: symbol)
    }

    static func title(started: Bool) -> String {
        started ? "It all adds up." : "Tally Calculator"
    }

    static func button(started: Bool) -> String {
        started ? "Start again" : "Continue"
    }
}

// MARK: - Type styles

/// One `StyleSheet` text style: size, line height and letter spacing in points before the
/// user's text size is applied.
struct TactileCalculatorWelcomeTextStyle {
    let fontName: String
    let size: CGFloat
    let lineHeight: CGFloat
    let kerning: CGFloat
    let color: Color
    /// `maxFontSizeMultiplier`: the most the user's text size may enlarge this text.
    let maxScale: Double

    /// `styles.welcome`: Inter Regular 36 / 44, -0.7 tracking, capped at 1.6.
    static let welcome = TactileCalculatorWelcomeTextStyle(
        fontName: TactileCalculatorWelcomeFonts.regular, size: 36, lineHeight: 44, kerning: -0.7,
        color: TactileCalculatorWelcomePalette.welcome, maxScale: 1.6)

    /// `styles.title`: Inter Bold 36 / 44, -0.8 tracking, capped at 1.5.
    static let title = TactileCalculatorWelcomeTextStyle(
        fontName: TactileCalculatorWelcomeFonts.bold, size: 36, lineHeight: 44, kerning: -0.8,
        color: TactileCalculatorWelcomePalette.title, maxScale: 1.5)

    /// `styles.ctaText`: Inter Bold 21 / 26, -0.3 tracking, white, capped at 1.6.
    static let button = TactileCalculatorWelcomeTextStyle(
        fontName: TactileCalculatorWelcomeFonts.bold, size: 21, lineHeight: 26, kerning: -0.3,
        color: .white, maxScale: 1.6)
}

/// React Native's `fontScale` on iOS for each text size the user can pick (its fixed table of
/// multipliers), so type grows by the same amount in both stacks.
func tactileCalculatorWelcomeFontScale(_ size: DynamicTypeSize) -> Double {
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

// MARK: - Curves

/// The curves of the block.
enum TactileCalculatorWelcomeEase {
    /// `Easing.bezier(0.23, 1, 0.32, 1)`: `EASE` in the component, `SETTLE` in `SwapText` and
    /// the `PressScale` transition.
    static let settle = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )

    /// `Easing.inOut(Easing.quad)`, Reanimated's default for timing animations.
    static func quadInOut(_ p: Double) -> Double {
        p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
    }

    static func clamp(_ value: Double) -> Double {
        min(1, max(0, value))
    }
}

// MARK: - Text

/// Gives a text the exact line height React Native sets with `lineHeight`. From iOS 26 the system
/// has `lineHeight(.exact)`; before it, the missing space is added as line spacing and split
/// above and below (a line cannot be made shorter than the font's own).
struct TactileCalculatorWelcomeLineHeight: ViewModifier {
    let points: CGFloat
    let fontSize: CGFloat
    let fontName: String

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.lineHeight(.exact(points: points))
        } else {
            let font = UIFont(name: fontName, size: fontSize) ?? UIFont.systemFont(ofSize: fontSize)
            let natural = ceil(font.lineHeight)
            let extra = max(0, points - natural)
            content
                .lineSpacing(extra)
                .padding(.vertical, extra / 2)
        }
    }
}

/// A centred text in one of the block's styles, as wide as it is offered. The user's text size
/// scales it like React Native does (`fontScale`, up to the style's cap), line height included;
/// letter spacing stays as set. `tabular` is `fontVariant: ['tabular-nums']`.
struct TactileCalculatorWelcomeLabel: View {
    let text: String
    let style: TactileCalculatorWelcomeTextStyle
    let fontScale: Double
    var tabular = false

    var body: some View {
        let scale = CGFloat(min(fontScale, style.maxScale))
        let size: CGFloat = style.size * scale
        let base = Font.custom(style.fontName, fixedSize: size)
        Text(verbatim: text)
            .font(tabular ? base.monospacedDigit() : base)
            .kerning(style.kerning)
            .foregroundStyle(style.color)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .modifier(
                TactileCalculatorWelcomeLineHeight(
                    points: style.lineHeight * scale, fontSize: size, fontName: style.fontName)
            )
            .frame(maxWidth: .infinity)
    }
}

// MARK: - SwapText

/// `SwapText`: text that changes in place. The old words leave fast (140 ms) and the new ones
/// settle a beat later from 10 pt below (260 ms), so a headline reads as one sentence being
/// rewritten rather than a cut. Under Reduce Motion it is a plain cross-fade (120 ms out,
/// 180 ms in). The first render does not animate.
///
/// An invisible copy of the new text sizes the box; the new words sit on top of it and the old
/// ones on top of that while they fade out. Every opacity is worked out from the seconds since
/// the change, in a timeline that runs only while something moves.
struct TactileCalculatorWelcomeSwapText: View {
    let text: String
    var tabular = false
    let style: TactileCalculatorWelcomeTextStyle
    let fontScale: Double
    let reduced: Bool
    /// The `delay` prop in seconds (`delay` / 1000 in the React Native call).
    var delay: Double = 0

    /// What the label shows; a change of either part is a new `key` in the React Native file.
    private struct Shown: Equatable {
        var text: String
        var tabular: Bool
    }

    /// Old words on their way out.
    private struct Leaving: Identifiable {
        let id: Int
        let shown: Shown
        let start: Date
    }

    @State private var changedAt: Date?
    @State private var leaving: [Leaving] = []
    @State private var animating = false
    @State private var counter = 0
    @State private var cleanup: Task<Void, Never>?

    var body: some View {
        let shown = Shown(text: text, tabular: tabular)
        TimelineView(.animation(paused: !animating)) { timeline in
            let now = timeline.date
            let elapsed: Double? = changedAt.map { now.timeIntervalSince($0) }
            let incoming = Self.incoming(elapsed: elapsed, reduced: reduced, delay: delay)
            label(shown)
                .opacity(0)
                .accessibilityHidden(true)
                .overlay(alignment: .top) {
                    label(shown)
                        .opacity(incoming.opacity)
                        .offset(y: incoming.lift)
                }
                .overlay(alignment: .top) {
                    ForEach(leaving) { item in
                        label(item.shown)
                            .opacity(Self.outgoing(elapsed: now.timeIntervalSince(item.start), reduced: reduced))
                            .accessibilityHidden(true)
                            .allowsHitTesting(false)
                    }
                }
        }
        .onChange(of: shown) { old, _ in
            change(from: old)
        }
    }

    private func label(_ shown: Shown) -> some View {
        TactileCalculatorWelcomeLabel(
            text: shown.text, style: style, fontScale: fontScale, tabular: shown.tabular)
    }

    /// The text changed: keep the old words for the fade-out and start the new ones.
    private func change(from old: Shown) {
        let now = Date()
        counter += 1
        leaving.append(Leaving(id: counter, shown: old, start: now))
        changedAt = now
        animating = true
        cleanup?.cancel()
        let settle = Self.settleTime(delay: delay)
        cleanup = Task { @MainActor in
            try? await Task.sleep(for: .seconds(settle))
            guard !Task.isCancelled else { return }
            leaving.removeAll()
            animating = false
        }
    }

    /// The seconds after which a swap has fully settled (the longest delay plus 260 ms, with a margin).
    private static func settleTime(delay: Double) -> Double {
        0.06 + delay + 0.26 + 0.1
    }

    /// The new words: nothing until `60 + delay` ms, then 260 ms up from 10 pt below on the
    /// strong ease-out (`FadeInDown`); under Reduce Motion a 180 ms fade (`FadeIn`).
    private static func incoming(
        elapsed: Double?, reduced: Bool, delay: Double
    ) -> (opacity: Double, lift: Double) {
        guard let elapsed else { return (1, 0) }
        if reduced {
            return (TactileCalculatorWelcomeEase.quadInOut(TactileCalculatorWelcomeEase.clamp(elapsed / 0.18)), 0)
        }
        let progress = TactileCalculatorWelcomeEase.clamp((elapsed - (0.06 + delay)) / 0.26)
        let eased = TactileCalculatorWelcomeEase.settle.value(at: progress)
        return (eased, 10 * (1 - eased))
    }

    /// The old words: 140 ms of `FadeOut` on the strong ease-out; under Reduce Motion 120 ms.
    private static func outgoing(elapsed: Double, reduced: Bool) -> Double {
        if reduced {
            return 1 - TactileCalculatorWelcomeEase.quadInOut(TactileCalculatorWelcomeEase.clamp(elapsed / 0.12))
        }
        let eased = TactileCalculatorWelcomeEase.settle.value(at: TactileCalculatorWelcomeEase.clamp(elapsed / 0.14))
        return 1 - eased
    }
}
