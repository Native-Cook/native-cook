import SwiftUI
import UIKit

private typealias M = NocturneProgramCarouselMotion
private typealias P = NocturneProgramCarouselPalette

/// `TourButton`: a pill-ish blue button whose whole surface settles to 0.97 in 120 ms on the strong
/// ease-out while pressed and returns on the same curve (under Reduce Motion it does not move at
/// all). The surface is 48 pt tall at least, `minHeight` is the outer box it sits at the top of.
struct NocturneProgramCarouselTourButton: View {
    let label: String
    /// The surface colour (`background`): `#256BFF` unless the call says otherwise.
    var surface: Color = NocturneProgramCarouselPalette.rgb(0x256BFF)
    /// The outer `style.minHeight`; the surface keeps its own height at the top of it.
    var minHeight: CGFloat = 48
    let reduced: Bool
    let fontScale: Double
    let onPressIn: () -> Void
    let onPressOut: () -> Void
    let onPress: () -> Void

    @State private var pressed = false

    var body: some View {
        let scaled = pressed && !reduced
        // `transitionDuration: reduced ? 0 : 120`, `cubicBezier(.23, 1, .32, 1)`.
        let curve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: reduced ? 0 : 0.12)
        NocturneProgramCarouselLabel(text: label, style: .button, fontScale: fontScale)
            .padding(.horizontal, 15)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background {
                RoundedRectangle(cornerRadius: 18).fill(surface)
            }
            .overlay {
                NocturneProgramCarouselPressSurface(
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
            .frame(minHeight: minHeight, alignment: .top)
            .scaleEffect(scaled ? 0.97 : 1)
            .animation(curve, value: pressed)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: label))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction {
                onPress()
            }
    }
}

/// The footer of the tour: the "Build my evening plan" button with a light sweep across it, and the
/// invisible tab bar that keeps the button where the real one leaves room for it.
struct NocturneProgramCarouselFooter: View {
    let frame: NocturneProgramCarouselFrame
    let metrics: NocturneProgramCarouselMetrics
    let onPressIn: () -> Void
    let onPressOut: () -> Void
    let onPress: () -> Void

    var body: some View {
        VStack(spacing: 34) {
            planButton
            NocturneProgramCarouselTabBar()
                .opacity(0)
                .allowsHitTesting(false)
        }
        .padding(.horizontal, 30)
        .padding(.bottom, metrics.footerBottomInset)
    }

    /// The button in a 28 pt rounded wrapper that clips the sheen.
    private var planButton: some View {
        NocturneProgramCarouselTourButton(
            label: "Build my evening plan",
            surface: P.rgb(0x005EFE),
            minHeight: 55,
            reduced: frame.reduced,
            fontScale: metrics.fontScale,
            onPressIn: onPressIn,
            onPressOut: onPressOut,
            onPress: onPress
        )
        .overlay(alignment: .topLeading) {
            sheen
        }
        .clipShape(RoundedRectangle(cornerRadius: 28))
    }

    /// `sheenStyle`: a 64 pt wide, 18 degree slanted band of light that crosses the button from
    /// 2.4 s and again from 8.9 s, 0.65 s each, at 45% opacity. It starts at `left: -64` and is
    /// 200% of the wrapper tall, centred on it (`top: -50%`); it turns about its own centre and is
    /// then moved by `(window width + 64) * progress`.
    private var sheen: some View {
        let start = frame.t < 6 ? 2.4 : 8.9
        let progress = M.segment(frame.t, start, start + 0.65)
        let hidden = frame.reduced || !frame.timelineDriven || frame.t < start || frame.t > start + 0.65
        let travel = (Double(metrics.size.width) + 64) * progress
        return LinearGradient(
            colors: [P.rgba(0xFFFFFF00), P.rgba(0xFFFFFFAA), P.rgba(0xFFFFFF00)],
            startPoint: .leading,
            endPoint: .trailing
        )
        .frame(width: 64, height: 110)
        .rotationEffect(.degrees(18))
        .offset(x: -64 + CGFloat(travel), y: -27.5)
        .opacity(hidden ? 0 : 0.45)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
