import SwiftUI
import UIKit

/// A cassette's place on the strip: the source's `perspective, translateX, translateY, rotateY` about the
/// view's centre. The shift comes before the perspective, so a cassette far to the side is foreshortened.
struct CassetteDeckPose: GeometryEffect {
    var x: Double
    var y: Double
    var yaw: Double
    var distance: Double

    var animatableData: AnimatablePair<AnimatablePair<Double, Double>, AnimatablePair<Double, Double>> {
        get { AnimatablePair(AnimatablePair(x, y), AnimatablePair(yaw, distance)) }
        set {
            x = newValue.first.first
            y = newValue.first.second
            yaw = newValue.second.first
            distance = newValue.second.second
        }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let cx = size.width / 2
        let cy = size.height / 2
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / distance
        // Points go: to the centre, yaw, shift, perspective, back.
        var transform = CATransform3DMakeTranslation(-cx, -cy, 0)
        transform = CATransform3DConcat(transform, CATransform3DMakeRotation(yaw * .pi / 180, 0, 1, 0))
        transform = CATransform3DConcat(transform, CATransform3DMakeTranslation(x, y, 0))
        transform = CATransform3DConcat(transform, perspective)
        transform = CATransform3DConcat(transform, CATransform3DMakeTranslation(cx, cy, 0))
        return ProjectionTransform(transform)
    }
}
