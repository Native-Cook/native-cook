import SwiftUI
import UIKit

/// The header: "Tutorial" with its count in the middle, a gear and a lock disc at the sides while the
/// illustrated cover deck shows, and a white close disc on the right while the tutorial runs.
struct PaperDeckTutorialHeader: View {
    let controller: PaperDeckTutorialController
    let metrics: PaperDeckTutorialMetrics
    let reduced: Bool

    var body: some View {
        TimelineView(.animation(paused: !controller.isMoving)) { timeline in
            let pose = controller.pose(at: timeline.date, screenWidth: Double(metrics.size.width))
            // Gear and lock frame the illustrated deck (the cover before the tutorial, the deck after it).
            let onDeck = pose.step == 6 || (pose.step == 0 && pose.time < 0.85)
            copy(step: pose.step)
                .padding(.top, 26)
                .frame(width: metrics.size.width - 20, height: metrics.headerHeight, alignment: .top)
                .overlay(alignment: .topLeading) {
                    if onDeck {
                        gear
                            .offset(y: -29)
                    }
                }
                .overlay(alignment: .topTrailing) {
                    lock(onDeck: onDeck)
                        .offset(y: -29)
                }
                .frame(width: metrics.size.width, height: metrics.headerHeight, alignment: .top)
        }
    }

    // MARK: Title and count

    private func copy(step: Int) -> some View {
        // `maxFontSizeMultiplier`: 1.6 on the title, 1.8 on the count.
        let titleScale = CGFloat(min(metrics.fontScale, 1.6))
        let countScale = CGFloat(min(metrics.fontScale, 1.8))
        let titleStyle = PaperDeckTutorialLineStyle(
            font: PaperDeckTutorialFonts.inter(bold: true, size: 22 * titleScale),
            lineHeight: 27 * titleScale,
            kern: -0.4,
            color: PaperDeckTutorialPalette.headerTitle
        )
        let countStyle = PaperDeckTutorialLineStyle(
            font: PaperDeckTutorialFonts.inter(bold: false, size: 15 * countScale),
            lineHeight: 19 * countScale,
            color: PaperDeckTutorialPalette.progress,
            monospacedDigits: true
        )
        return VStack(spacing: 0) {
            PaperDeckTutorialSwapText(
                text: step == 6 ? "This or that" : "Tutorial",
                style: titleStyle, delay: 0, reduced: reduced
            )
            PaperDeckTutorialSwapText(
                text: step == 6 ? "1 / 200" : "\(step + 1) / 6",
                style: countStyle, delay: 0.03, reduced: reduced
            )
            .padding(.top, 6)
        }
    }

    // MARK: Buttons

    /// The gear: back to the first card.
    private var gear: some View {
        Button {
            controller.restart()
        } label: {
            chromeDisc {
                glyph("\u{F56D}", size: 13)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Restart tutorial")
    }

    /// The lock while the deck shows (it does nothing), the close disc while the tutorial runs.
    private func lock(onDeck: Bool) -> some View {
        Button {
            if onDeck { return }
            controller.finish()
        } label: {
            if onDeck {
                chromeDisc {
                    glyph("\u{F3C7}", size: 12)
                }
            } else {
                closeDisc
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(onDeck ? "Unlock all decks" : "Finish tutorial")
    }

    /// A 27 pt disc in a 48 pt touch area (`close` and `chromeDisc`).
    private func chromeDisc<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        ZStack {
            Circle()
                .fill(PaperDeckTutorialPalette.chromeDisc)
                .frame(width: 27, height: 27)
            content()
        }
        .frame(width: 48, height: 48)
        .contentShape(Rectangle())
    }

    /// An Ionicons glyph, which scales with the text size like any other React Native `Text`.
    private func glyph(_ character: String, size: CGFloat) -> some View {
        Text(character)
            .font(PaperDeckTutorialFonts.ionicons(size * CGFloat(metrics.fontScale)))
            .foregroundStyle(PaperDeckTutorialPalette.chromeGlyph)
    }

    private var closeDisc: some View {
        ZStack {
            Circle()
                .fill(PaperDeckTutorialPalette.closeDisc)
                .frame(width: 27, height: 27)
            PaperDeckTutorialCloseMark()
                .stroke(PaperDeckTutorialPalette.closeMark, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                .frame(width: 12, height: 12)
        }
        .frame(width: 48, height: 48)
        .contentShape(Rectangle())
    }
}

/// The cross of the close disc: `M2 2l8 8M10 2 2 10` in a 12 x 12 box.
struct PaperDeckTutorialCloseMark: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 2, y: rect.minY + 2))
        path.addLine(to: CGPoint(x: rect.minX + 10, y: rect.minY + 10))
        path.move(to: CGPoint(x: rect.minX + 10, y: rect.minY + 2))
        path.addLine(to: CGPoint(x: rect.minX + 2, y: rect.minY + 10))
        return path
    }
}

/// Text that changes in place (`SwapText`). The old words leave fast (140 ms) and the new ones
/// settle a beat later from 10 pt below (260 ms, after 60 ms plus `delay`), so a headline reads as one
/// sentence being rewritten rather than a cut. Under Reduce Motion it is a plain cross-fade. The
/// first render does not animate.
struct PaperDeckTutorialSwapText: View {
    let text: String
    let style: PaperDeckTutorialLineStyle
    /// Extra seconds before the new words enter (`delay` is in milliseconds in React Native).
    let delay: Double
    let reduced: Bool

    /// `SETTLE`: `Easing.bezier(0.23, 1, 0.32, 1)`.
    private func settle(_ duration: Double) -> Animation {
        .timingCurve(0.23, 1, 0.32, 1, duration: duration)
    }

    /// `Easing.inOut(Easing.quad)`, Reanimated's default easing for `FadeIn` and `FadeOut`.
    private func inOutQuad(_ duration: Double) -> Animation {
        .timingCurve(0.455, 0.03, 0.515, 0.955, duration: duration)
    }

    private var insertion: AnyTransition {
        if reduced {
            return AnyTransition.opacity.animation(inOutQuad(0.18))
        }
        return AnyTransition.opacity
            .combined(with: .offset(x: 0, y: 10))
            .animation(settle(0.26).delay(0.06 + delay))
    }

    private var removal: AnyTransition {
        if reduced {
            return AnyTransition.opacity.animation(inOutQuad(0.12))
        }
        return AnyTransition.opacity.animation(settle(0.14))
    }

    var body: some View {
        ZStack {
            PaperDeckTutorialLine(text: text, style: style)
                .id(text)
                .transition(.asymmetric(insertion: insertion, removal: removal))
        }
    }
}
