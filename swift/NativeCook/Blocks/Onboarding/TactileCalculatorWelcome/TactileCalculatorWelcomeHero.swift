import SwiftUI

/// The stack of five keys: `heroHeight` points tall and clipped (the `overflow: 'hidden'` View of
/// the React Native file), with each key placed by fractions of the window.
///
/// The keys are drawn in one timeline that runs only while the loop or a press moves; the
/// press surfaces sit above all of them, in key order, so a later key wins where two hit areas
/// overlap (as sibling `Pressable`s do).
struct TactileCalculatorWelcomeHero: View {
    let controller: TactileCalculatorWelcomeController
    /// The window's width: `sceneWidth`.
    let width: CGFloat
    /// `height * compactScale`: the height the keys' `top` fractions refer to (`sceneHeight`).
    let sceneHeight: CGFloat
    /// `heroHeight`.
    let heroHeight: CGFloat
    /// The key plate scale, `scale` in the React Native file.
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: !controller.moving)) { timeline in
                let now = timeline.date
                ZStack(alignment: .topLeading) {
                    ForEach(TactileCalculatorWelcomeKey.all) { key in
                        art(key, now: now)
                    }
                }
                .frame(width: width, height: heroHeight, alignment: .topLeading)
            }
            .allowsHitTesting(false)
            ForEach(TactileCalculatorWelcomeKey.all) { key in
                hitArea(key)
            }
        }
        .frame(width: width, height: heroHeight, alignment: .topLeading)
        .clipped()
    }

    /// The top-left corner of the key's 216 x 150 plate: `centerX * sceneWidth - 108 * scale`
    /// and `top * sceneHeight - 8 * scale`.
    private func origin(_ key: TactileCalculatorWelcomeKey) -> CGPoint {
        let x: CGFloat = CGFloat(key.centerX) * width - 108 * scale
        let y: CGFloat = CGFloat(key.top) * sceneHeight - 8 * scale
        return CGPoint(x: x, y: y)
    }

    /// One key's drawing at `now`.
    private func art(_ key: TactileCalculatorWelcomeKey, now: Date) -> some View {
        let pressure = controller.pressure(of: key.symbol, at: now)
        let point = origin(key)
        let plateScale = Double(scale)
        return Canvas { context, _ in
            TactileCalculatorWelcomeKeyArt.draw(context, key: key, pressure: pressure, scale: plateScale)
        }
        .frame(width: 216 * scale, height: 150 * scale)
        .offset(x: point.x, y: point.y)
    }

    /// The hit area is the face's inscribed rectangle (116 x 78 at 50, 30 of the plate), so
    /// neighbouring plates never steal a press.
    private func hitArea(_ key: TactileCalculatorWelcomeKey) -> some View {
        let point = origin(key)
        return Color.clear
            .frame(width: 116 * scale, height: 78 * scale)
            .contentShape(Rectangle())
            .overlay {
                TactileCalculatorWelcomePressSurface(
                    isEnabled: controller.isInteractive,
                    retention: 0,
                    minPressDuration: 0,
                    onPressIn: { controller.keyPressIn(key.symbol) },
                    onPressOut: { controller.keyPressOut(key.symbol) },
                    onPress: { controller.keySelect(key.symbol) }
                )
                .accessibilityHidden(true)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: key.label))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction {
                controller.activateKey(key.symbol)
            }
            .offset(x: point.x + 50 * scale, y: point.y + 30 * scale)
    }
}
