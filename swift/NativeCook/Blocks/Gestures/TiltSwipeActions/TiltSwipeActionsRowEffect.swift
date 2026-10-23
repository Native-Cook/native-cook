import SwiftUI
import QuartzCore

/// A row's content transform, in the React Native order: perspective 700, translateX, rotateY, scale,
/// about the content's centre.
struct TiltSwipeActionsRowEffect: GeometryEffect {
    var x: Double
    /// Degrees.
    var tilt: Double
    var scale: Double

    func effectValue(size: CGSize) -> ProjectionTransform {
        let centre = CGPoint(x: size.width / 2, y: size.height / 2)
        // CATransform3D multiplies row vectors: the first factor acts first.
        var matrix = CATransform3DMakeTranslation(-centre.x, -centre.y, 0)
        matrix = CATransform3DConcat(matrix, CATransform3DMakeScale(scale, scale, 1))
        matrix = CATransform3DConcat(matrix, CATransform3DMakeRotation(tilt * .pi / 180, 0, 1, 0))
        matrix = CATransform3DConcat(matrix, CATransform3DMakeTranslation(x, 0, 0))
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / TiltSwipeActionsMotion.Tilt.perspective
        matrix = CATransform3DConcat(matrix, perspective)
        matrix = CATransform3DConcat(matrix, CATransform3DMakeTranslation(centre.x, centre.y, 0))
        return ProjectionTransform(matrix)
    }
}
