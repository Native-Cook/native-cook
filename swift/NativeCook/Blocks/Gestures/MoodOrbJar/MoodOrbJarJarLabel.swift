import SwiftUI

/// The month and the count under the jar's shoulder. The count rolls like an odometer when an orb
/// joins or leaves.
struct MoodOrbJarJarLabel: View {
    typealias Motion = MoodOrbJarTokens.Motion
    typealias Palette = MoodOrbJarTokens.Palette

    let controller: MoodOrbJarController
    let geometry: MoodOrbJarGeometry

    var body: some View {
        let count = controller.count
        VStack(spacing: 0) {
            Text(MoodOrbJarTokens.Copy.month)
                .font(.system(size: 11, weight: .bold))
                .tracking(2.2)
                .foregroundStyle(Palette.mute)
            ZStack {
                Text("\(count)")
                    .font(.system(size: 24, weight: .heavy))
                    .monospacedDigit()
                    .foregroundStyle(Palette.ink)
                    .frame(height: 30)
                    .id(count)
                    .transition(transition)
            }
            .frame(height: 30)
            .padding(.top, MoodOrbJarTokens.Space.extraSmall)
            .clipped()
        }
        .frame(width: geometry.width)
        .offset(y: geometry.mouthY + MoodOrbJarTokens.Layout.jarHeight * MoodOrbJarTokens.Layout.labelY * geometry.scale)
        .allowsHitTesting(false)
    }

    /// FadeInDown 260 ms in, FadeOutUp 200 ms out; none under Reduce Motion.
    private var transition: AnyTransition {
        guard !controller.reducedMotion else { return .identity }
        let travel = Motion.rowFadeOffset
        let enter = AnyTransition.opacity.combined(with: .offset(y: -travel))
            .animation(.timingCurve(0.455, 0.03, 0.515, 0.955, duration: Motion.countEnter))
        let exit = AnyTransition.opacity.combined(with: .offset(y: -travel))
            .animation(.timingCurve(0.455, 0.03, 0.515, 0.955, duration: Motion.countExit))
        return .asymmetric(insertion: enter, removal: exit)
    }
}
