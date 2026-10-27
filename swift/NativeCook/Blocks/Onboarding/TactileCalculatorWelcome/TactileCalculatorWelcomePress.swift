import SwiftUI
import UIKit

// MARK: - Touch

/// A zero-delay press recogniser, standing in for a React Native `Pressable`: `onPressIn` when the
/// finger lands, `onPressOut` when it leaves the press region (the view plus `retention`) or
/// lifts, and `onPress` after the lift inside the region. `onPressOut` is held back until
/// `minPressDuration` has passed since `onPressIn`, as `Pressability` does (130 ms for the
/// core `Pressable`, so a quick tap still shows its pressed state); `onPress` is not.
///
/// It is a recogniser that allows the others beside it, not raw touches, so the gallery's
/// back-swipe (or the scroll view around the block) does not end the press while the finger is
/// still down (the approach of `EmberTouchView`).
struct TactileCalculatorWelcomePressSurface: UIViewRepresentable {
    var isEnabled: Bool
    /// `pressRetentionOffset`: how far outside the view the finger may wander and still press.
    var retention: CGFloat
    /// `minPressDuration` in seconds.
    var minPressDuration: TimeInterval
    var onPressIn: () -> Void
    var onPressOut: () -> Void
    var onPress: () -> Void

    func makeUIView(context: Context) -> TactileCalculatorWelcomePressView {
        TactileCalculatorWelcomePressView()
    }

    func updateUIView(_ view: TactileCalculatorWelcomePressView, context: Context) {
        view.isPressEnabled = isEnabled
        view.retention = retention
        view.minPressDuration = minPressDuration
        view.onPressIn = onPressIn
        view.onPressOut = onPressOut
        view.onPress = onPress
    }
}

final class TactileCalculatorWelcomePressView: UIView, UIGestureRecognizerDelegate {
    var isPressEnabled = true
    var retention: CGFloat = 0
    var minPressDuration: TimeInterval = 0
    var onPressIn: () -> Void = {}
    var onPressOut: () -> Void = {}
    var onPress: () -> Void = {}

    /// A press began while the surface was enabled.
    private var active = false
    /// The finger is inside the press region.
    private var inside = false
    /// When the last `onPressIn` was sent.
    private var activatedAt: CFTimeInterval = 0
    /// An `onPressOut` waiting for `minPressDuration`.
    private var pendingOut: DispatchWorkItem?

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
        fatalError("TactileCalculatorWelcomePressView is created in code")
    }

    func gestureRecognizer(
        _ recognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }

    /// `_activate`: a new press cancels an `onPressOut` that was still waiting.
    private func activate() {
        pendingOut?.cancel()
        pendingOut = nil
        activatedAt = CACurrentMediaTime()
        inside = true
        onPressIn()
    }

    /// `_deactivate`: `onPressOut` now, or when the minimum press duration is over.
    private func deactivate() {
        inside = false
        let wait = minPressDuration - (CACurrentMediaTime() - activatedAt)
        let callback = onPressOut
        if wait > 0 {
            let work = DispatchWorkItem { [weak self] in
                self?.pendingOut = nil
                callback()
            }
            pendingOut = work
            DispatchQueue.main.asyncAfter(deadline: .now() + wait, execute: work)
        } else {
            callback()
        }
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
            activate()
        case .changed:
            guard active else { return }
            let within = region.contains(press.location(in: self))
            if inside && !within {
                deactivate()
            } else if !inside && within {
                activate()
            }
        case .ended:
            guard active else { return }
            active = false
            if inside {
                // Pressability deactivates first, then calls onPress.
                deactivate()
                onPress()
            }
        case .cancelled, .failed:
            guard active else { return }
            active = false
            if inside {
                deactivate()
            }
        default:
            break
        }
    }
}

// MARK: - Button

/// The Continue / Start again pill: `styles.cta` plus `PressScale`.
///
/// The whole surface settles to 0.96 in 150 ms on the strong ease-out and returns on the same
/// curve; under Reduce Motion it dims to 0.72 instead. The pressed state follows the
/// `onPressIn` and `onPressOut` of the press surface, so it lasts at least 130 ms.
struct TactileCalculatorWelcomeButton: View {
    let label: String
    let reduced: Bool
    let fontScale: Double
    let isEnabled: Bool
    let onPressIn: () -> Void
    let onPressOut: () -> Void
    let onPress: () -> Void

    /// `PressScale`'s `pressed` state.
    @State private var pressed = false

    /// `transitionDuration: 150` on `cubicBezier(0.23, 1, 0.32, 1)`.
    private static let pressCurve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.15)

    var body: some View {
        let down = pressed
        surface
            .scaleEffect(down && !reduced ? 0.96 : 1)
            .opacity(reduced && down ? 0.72 : 1)
            .animation(Self.pressCurve, value: down)
            .overlay {
                TactileCalculatorWelcomePressSurface(
                    isEnabled: isEnabled,
                    retention: 16,
                    minPressDuration: 0.13,
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
            .accessibilityAction {
                if isEnabled { onPress() }
            }
    }

    /// `styles.cta`: minHeight 56, radius 28, padding 20 x 14, charcoal; the label is Inter Bold
    /// 21 / 26, white. The 30 pt side margins and the 10 pt top margin belong to the column.
    private var surface: some View {
        TactileCalculatorWelcomeLabel(text: label, style: .button, fontScale: fontScale)
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .circular)
                    .fill(TactileCalculatorWelcomePalette.button)
            )
    }
}
