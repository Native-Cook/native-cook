import SwiftUI

/// One perspective projection per face, matching the accepted React Native transform matrix.
struct EventPassShareProjection: GeometryEffect {
    var tiltX: CGFloat
    var turnY: CGFloat
    var perspective: CGFloat

    var animatableData: AnimatablePair<AnimatablePair<CGFloat, CGFloat>, CGFloat> {
        get { AnimatablePair(AnimatablePair(tiltX, turnY), perspective) }
        set {
            tiltX = newValue.first.first
            turnY = newValue.first.second
            perspective = newValue.second
        }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let centerX = size.width / 2
        let centerY = size.height / 2
        var transform = CATransform3DMakeTranslation(-centerX, -centerY, 0)
        transform = CATransform3DConcat(
            transform,
            CATransform3DMakeRotation(turnY * .pi / 180, 0, 1, 0)
        )
        transform = CATransform3DConcat(
            transform,
            CATransform3DMakeRotation(tiltX * .pi / 180, 1, 0, 0)
        )
        var lens = CATransform3DIdentity
        lens.m34 = -1 / perspective
        transform = CATransform3DConcat(transform, lens)
        transform = CATransform3DConcat(
            transform,
            CATransform3DMakeTranslation(centerX, centerY, 0)
        )
        return ProjectionTransform(transform)
    }
}
