import SwiftUI
import UIKit

/// The Continue / Replay welcome button: `styles.cta` plus `PressScale`.
///
/// The surface answers the finger at once: it settles to 0.96 in 150 ms on the strong ease-out
/// and returns on the same curve (under Reduce Motion it dims to 0.72 instead). A button that is
/// not ready (before 2.25 s and after 7.2 s of the loop) never moves.
struct DaylightIconRevealButton: View {
    let label: String
    /// `width: '66%'` of the copy column, at most 300.
    let width: CGFloat
    let ready: Bool
    let pressed: Bool
    let reduced: Bool
    let fontScale: Double
    let onPressIn: () -> Void
    let onPressOut: () -> Void
    let onPress: () -> Void

    private static let radius: CGFloat = 16
    /// `PressScale`: 150 ms on cubic-bezier(0.23, 1, 0.32, 1).
    private static let pressCurve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.15)

    var body: some View {
        let down = pressed && ready
        surface
            .scaleEffect(down && !reduced ? 0.96 : 1)
            .opacity(reduced && down ? 0.72 : 1)
            .animation(Self.pressCurve, value: down)
            .frame(width: width)
            .overlay {
                DaylightIconRevealPressSurface(
                    isEnabled: ready, onPressIn: onPressIn, onPressOut: onPressOut, onPress: onPress
                )
                .accessibilityHidden(true)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: label))
            .accessibilityAddTraits(ready ? .isButton : .isStaticText)
            .accessibilityAction {
                if ready { onPress() }
            }
    }

    /// The filled button: minHeight 48, radius 16, padding 13 x 25, with the inner highlight and the
    /// soft shadow from `boxShadow`.
    private var surface: some View {
        DaylightIconRevealLabel(text: label, style: .button, fontScale: fontScale)
            .padding(.horizontal, 25)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(
                RoundedRectangle(cornerRadius: Self.radius, style: .circular)
                    .fill(DaylightIconRevealPalette.button)
            )
            .overlay {
                // `0px 1px 0px rgba(255,255,255,0.18) inset`: a 1 pt highlight along the top edge.
                Canvas { context, size in
                    let box = CGRect(origin: .zero, size: size)
                    context.clip(to: Path(roundedRect: box, cornerRadius: Self.radius))
                    var path = Path()
                    path.addRect(box.insetBy(dx: -20, dy: -20))
                    path.addRoundedRect(
                        in: box.offsetBy(dx: 0, dy: 1),
                        cornerSize: CGSize(width: Self.radius, height: Self.radius)
                    )
                    context.fill(path, with: .color(.white.opacity(0.18)), style: FillStyle(eoFill: true))
                }
                .allowsHitTesting(false)
            }
            .background {
                // `0px 12px 22px -12px rgba(80,58,212,0.75)`: the box shrunk by the 12 pt spread
                // (so its corners are 4 pt), 12 pt lower, blurred by 22 pt (a 11 pt Gaussian).
                RoundedRectangle(cornerRadius: Self.radius - 12, style: .circular)
                    .fill(DaylightIconRevealPalette.rgb(0x503AD4, opacity: 0.75))
                    .padding(12)
                    .blur(radius: 11)
                    .offset(y: 12)
            }
    }
}

// MARK: - Touch

/// A zero-delay press recogniser over the button, standing in for the `Pressable`: `onPressIn` when
/// the finger lands, `onPressOut` when it leaves the button's region (the button plus 16 pt, the
/// `pressRetentionOffset`) or lifts, and `onPress` after the lift inside the region.
///
/// It is a recogniser that allows the others beside it, not raw touches, so the gallery's
/// back-swipe (or a scroll view around the block) does not end the press while the finger is
/// still down (the approach of `EmberTouchView`).
struct DaylightIconRevealPressSurface: UIViewRepresentable {
    var isEnabled: Bool
    var onPressIn: () -> Void
    var onPressOut: () -> Void
    var onPress: () -> Void

    func makeUIView(context: Context) -> DaylightIconRevealPressView {
        DaylightIconRevealPressView()
    }

    func updateUIView(_ view: DaylightIconRevealPressView, context: Context) {
        view.isPressEnabled = isEnabled
        view.onPressIn = onPressIn
        view.onPressOut = onPressOut
        view.onPress = onPress
    }
}

final class DaylightIconRevealPressView: UIView, UIGestureRecognizerDelegate {
    var isPressEnabled = false
    var onPressIn: () -> Void = {}
    var onPressOut: () -> Void = {}
    var onPress: () -> Void = {}

    /// This press began while the button was ready.
    private var active = false
    /// The finger is inside the press region.
    private var inside = false

    /// `pressRetentionOffset={16}`.
    private let retention: CGFloat = 16

    override init(frame: CGRect) {
        super.init(frame: frame)
        isAccessibilityElement = false
        backgroundColor = .clear
        let press = UILongPressGestureRecognizer(target: self, action: #selector(track))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = false
        press.delegate = self
        addGestureRecognizer(press)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("DaylightIconRevealPressView is created in code")
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
            guard isPressEnabled else {
                active = false
                return
            }
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
