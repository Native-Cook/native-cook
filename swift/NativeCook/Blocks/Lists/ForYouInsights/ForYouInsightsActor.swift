import QuartzCore
import SwiftUI

/// Everything the actor needs for one frame, in screen points (ActorFrame).
struct ForYouInsightsActorFrame {
    var visible = 0.0
    var x = 0.0, y = 0.0, w = 0.0, h = 0.0, r = 0.0
    var scale = 1.0
    var tiltX = 0.0, tiltY = 0.0
    var lift = 0.0, sep = 0.0
}

/// The tapped article's illustration as a stack of depth layers: a shadow that grows with the lift, the card tilted in
/// perspective, each layer shifted by its depth, and a sheen that follows the tilt.
struct ForYouInsightsActor: View {
    typealias M = ForYouInsightsMotion
    typealias T = ForYouInsightsTheme
    var actor: ForYouInsightsActorFrame
    var article: Int
    var size: CGSize

    var body: some View {
        let a = actor
        ZStack(alignment: .topLeading) {
            shadow(a)
            // The transform is anchored at this 1 pt box in the screen's corner, and the card is drawn in screen
            // coordinates around it, so the matrix below is the RN group transform (translate · perspective · rotate · scale).
            Color.clear
                .frame(width: 1, height: 1)
                .overlay(alignment: .topLeading) { card(a) }
                .projectionEffect(ProjectionTransform(Self.matrix(a)))
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .opacity(a.visible)
        .allowsHitTesting(false)
    }

    private func shadow(_ a: ForYouInsightsActorFrame) -> some View {
        let spread = 1 + 0.25 * a.lift
        return Ellipse()
            .fill(EllipticalGradient(stops: [
                .init(color: T.Colors.shadow, location: 0),
                .init(color: T.Colors.shadow, location: 0.55),
                .init(color: T.Colors.shadow.opacity(0), location: 1),
            ]))
            .frame(width: a.w * spread, height: a.h * spread)
            .opacity(M.Depth.shadowOpacity * a.lift)
            .offset(x: a.x + a.w / 2 - a.w * spread / 2,
                    y: a.y + a.h / 2 + M.Depth.shadowOffsetMax * a.lift - a.h * spread / 2)
    }

    private func card(_ a: ForYouInsightsActorFrame) -> some View {
        let depths = ForYouInsightsContent.articles[article].depths
        let images = ForYouInsightsAssets.layers[article]
        let side = max(a.w, a.h) * (1 + M.Depth.overscan * a.sep)
        let sheen = M.Depth.sheenOpacity * min(1, abs(a.tiltY) / M.Depth.driftTiltY * 0.7 + a.lift * 0.6) * a.sep
        let sheenX = a.x - a.w * (a.tiltY / 30)
        return ZStack(alignment: .topLeading) {
            ForEach(0..<3, id: \.self) { i in
                let shift = M.layerShift(depth: depths[i], sep: a.sep, tiltX: a.tiltX, tiltY: a.tiltY, width: a.w)
                let s = side * shift.scale
                images[i]
                    .resizable()
                    .frame(width: s, height: s)
                    .offset(x: a.x + a.w / 2 - s / 2 + shift.dx, y: a.y + a.h / 2 - s / 2 + shift.dy)
            }
            Rectangle()
                .fill(LinearGradient(stops: [
                    .init(color: .white.opacity(0), location: 0.3),
                    .init(color: .white.opacity(0.9), location: 0.5),
                    .init(color: .white.opacity(0), location: 0.7),
                ], startPoint: UnitPoint(x: 0.25, y: 0), endPoint: UnitPoint(x: 0.75, y: 1)))
                .frame(width: a.w * 2, height: a.h)
                .opacity(sheen)
                .offset(x: sheenX - a.w * 0.5, y: a.y)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .mask(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: a.r, style: .circular)
                .frame(width: a.w, height: a.h)
                .offset(x: a.x, y: a.y)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
    }

    /// M = T(c) · P · Rx · Ry · S · T(-c) in column form; CATransform3D concatenates the other way round.
    static func matrix(_ a: ForYouInsightsActorFrame) -> CATransform3D {
        let cx = a.x + a.w / 2, cy = a.y + a.h / 2
        let deg = Double.pi / 180
        var persp = CATransform3DIdentity
        persp.m34 = -1 / M.Depth.perspective
        var t = CATransform3DMakeTranslation(-cx, -cy, 0)
        t = CATransform3DConcat(t, CATransform3DMakeScale(a.scale, a.scale, 1))
        t = CATransform3DConcat(t, CATransform3DMakeRotation(a.tiltY * deg, 0, 1, 0))
        t = CATransform3DConcat(t, CATransform3DMakeRotation(a.tiltX * deg, 1, 0, 0))
        t = CATransform3DConcat(t, persp)
        t = CATransform3DConcat(t, CATransform3DMakeTranslation(cx, cy, 0))
        return t
    }
}
