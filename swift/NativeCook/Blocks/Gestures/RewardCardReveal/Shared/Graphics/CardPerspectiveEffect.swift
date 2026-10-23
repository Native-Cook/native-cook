import SwiftUI

/// Reproduces a React Native transform array of the form
/// `[{perspective}, {translateX}, {translateY}, {rotateX}, {rotateY}, {rotateZ}, {scale}]`
/// applied around the view centre. Animatable, so `withAnimation` interpolates every field.
struct CardPerspectiveEffect: GeometryEffect {
    var translateX: CGFloat = 0
    var translateY: CGFloat = 0
    /// Degrees.
    var rotateX: CGFloat = 0
    /// Degrees.
    var rotateY: CGFloat = 0
    /// Degrees.
    var rotateZ: CGFloat = 0
    var scale: CGFloat = 1
    /// Perspective distance in points (RN `perspective`).
    var perspective: CGFloat = 900

    var animatableData: AnimatablePair<
        AnimatablePair<AnimatablePair<CGFloat, CGFloat>, AnimatablePair<CGFloat, CGFloat>>,
        AnimatablePair<AnimatablePair<CGFloat, CGFloat>, CGFloat>
    > {
        get {
            AnimatablePair(
                AnimatablePair(AnimatablePair(translateX, translateY), AnimatablePair(rotateX, rotateY)),
                AnimatablePair(AnimatablePair(rotateZ, scale), perspective)
            )
        }
        set {
            translateX = newValue.first.first.first
            translateY = newValue.first.first.second
            rotateX = newValue.first.second.first
            rotateY = newValue.first.second.second
            rotateZ = newValue.second.first.first
            scale = newValue.second.first.second
            perspective = newValue.second.second
        }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let cx = size.width / 2
        let cy = size.height / 2
        func radians(_ degrees: CGFloat) -> CGFloat { degrees * .pi / 180 }

        // CATransform3DConcat(a, b) applies `a` first, then `b` (row-vector convention),
        // which lets us list the operations in the order they act on a point.
        var transform = CATransform3DMakeTranslation(-cx, -cy, 0)
        transform = CATransform3DConcat(transform, CATransform3DMakeScale(scale, scale, 1))
        transform = CATransform3DConcat(transform, CATransform3DMakeRotation(radians(rotateZ), 0, 0, 1))
        transform = CATransform3DConcat(transform, CATransform3DMakeRotation(radians(rotateY), 0, 1, 0))
        transform = CATransform3DConcat(transform, CATransform3DMakeRotation(radians(rotateX), 1, 0, 0))
        transform = CATransform3DConcat(transform, CATransform3DMakeTranslation(translateX, translateY, 0))
        var perspectiveMatrix = CATransform3DIdentity
        if perspective != 0 {
            perspectiveMatrix.m34 = -1 / perspective
        }
        transform = CATransform3DConcat(transform, perspectiveMatrix)
        transform = CATransform3DConcat(transform, CATransform3DMakeTranslation(cx, cy, 0))
        return ProjectionTransform(transform)
    }
}

extension GraphicsContext {
    /// Skia `<Group opacity blendMode>` equivalent: draws `content` into a transparent layer and
    /// composites it with the given opacity and blend mode.
    func drawGroup(opacity: Double = 1, blendMode: GraphicsContext.BlendMode = .normal, _ content: (inout GraphicsContext) -> Void) {
        var layer = self
        layer.opacity = opacity
        layer.blendMode = blendMode
        layer.drawLayer { inner in
            content(&inner)
        }
    }
}

/// Piecewise-linear interpolation with clamped extrapolation (Reanimated `interpolate`).
func interpolate(_ value: CGFloat, input: [CGFloat], output: [CGFloat]) -> CGFloat {
    guard input.count == output.count, input.count >= 2 else { return 0 }
    if value <= input[0] { return output[0] }
    if value >= input[input.count - 1] { return output[output.count - 1] }
    for index in 1..<input.count where value <= input[index] {
        let span = input[index] - input[index - 1]
        let t = span == 0 ? 0 : (value - input[index - 1]) / span
        return output[index - 1] + (output[index] - output[index - 1]) * t
    }
    return output[output.count - 1]
}

func clamp(_ value: CGFloat, _ lower: CGFloat, _ upper: CGFloat) -> CGFloat {
    min(max(value, lower), upper)
}
