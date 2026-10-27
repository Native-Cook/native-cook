import SwiftUI

/// How one star is drawn at one instant of the loop.
struct EmberDayReviewStarPose: Equatable {
    var opacity: Double
    /// `rotateZ`, degrees.
    var roll: Double
    /// `scaleX`: the star turns edge-on and back, never fully flat.
    var scaleX: Double
    /// `scale`: it grows from the centre as it emerges.
    var scale: Double
    /// `translateY`, points.
    var lift: Double
}

/// The star's entrance (EmberDayReview.tsx `Star`): edge-on, face, edge-on, then settled face. The
/// stagger changes the phase of the turn, not just the alpha of a static star.
enum EmberDayReviewStarMotion {
    /// `index` 0 is the small star, 1 the main one and 2 the right one.
    static func pose(index: Int, t: Double, reduced: Bool) -> EmberDayReviewStarPose {
        if reduced {
            return EmberDayReviewStarPose(opacity: 1, roll: 0, scaleX: 1, scale: 1, lift: 0)
        }
        let math = EmberDayReviewMath.self
        let offset = index == 1 ? 0.0 : (index == 0 ? 0.12 : 0.2)
        let local = t - offset
        let emerge = math.interpolate(local, [0.48, 0.6, 0.82, 1.4], [0, 0.25, 1, 1])
        let turn = math.interpolate(
            local,
            [0.48, 0.6, 0.82, 1.02, 1.19, 1.43, 1.65],
            [90, 86, 20, -30, -88, -180, -180]
        )
        let roll = math.interpolate(local, [0.48, 0.8, 1.03, 1.2, 1.45, 1.65], [18, -28, 28, 12, -4, 0])
        let fadeOut = math.interpolate(t, [9.6, 9.95], [0, 1])
        return EmberDayReviewStarPose(
            opacity: min(1, emerge * 3) * (1 - fadeOut),
            roll: roll,
            scaleX: max(0.025, abs(cos(turn * Double.pi / 180))),
            scale: emerge,
            lift: (1 - emerge) * 12
        )
    }
}

/// One glossy star. The box is `size` square; the artwork is 1.29 x 1.06 of it, overhanging the
/// box by 0.145 on the left and 0.03 on top, and stretched to that rectangle.
struct EmberDayReviewStar: View {
    let size: CGFloat
    let pose: EmberDayReviewStarPose

    var body: some View {
        Group {
            if let image = EmberDayReviewArt.star {
                Image(uiImage: image)
                    .resizable()
                    .frame(width: size * 1.29, height: size * 1.06)
                    .offset(x: -size * 0.145, y: -size * 0.03)
            } else {
                Color.clear
            }
        }
        .frame(width: size, height: size, alignment: .topLeading)
        // The React Native list is [rotateZ, scaleX, scale, translateY]; the first entry is the outermost.
        .offset(y: pose.lift)
        .scaleEffect(max(0.0001, pose.scale))
        .scaleEffect(x: pose.scaleX, y: 1)
        .rotationEffect(.degrees(pose.roll))
        .opacity(pose.opacity)
        .accessibilityHidden(true)
    }
}

/// The hero: three stars in a halo, on a surface that opens from the top (`heroStyle`) while the
/// halo grows and fades. It clips its stars to its own height, which animates from 0.
struct EmberDayReviewHero: View {
    let width: CGFloat
    /// `compact ? 200 : 262`.
    let baseHeight: CGFloat
    /// 0 to 1: how far the hero has opened.
    let progress: Double
    let haloOpacity: Double
    let haloScale: Double
    let poses: [EmberDayReviewStarPose]

    var body: some View {
        let height = baseHeight * progress
        ZStack(alignment: .topLeading) {
            halo
                .position(x: width / 2, y: height / 2)
            // `starSmall`: left 23%, top 25%.
            EmberDayReviewStar(size: 46, pose: poses[0])
                .position(x: width * 0.23 + 23, y: height * 0.25 + 23)
            // `starMain`: centred, nudged up by 3.
            EmberDayReviewStar(size: 138, pose: poses[1])
                .position(x: width / 2, y: height / 2 - 3)
            // `starRight`: right 17%, top 47%.
            EmberDayReviewStar(size: 80, pose: poses[2])
                .position(x: width * 0.83 - 40, y: height * 0.47 + 40)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipped()
        .frame(width: width, height: baseHeight, alignment: .topLeading)
    }

    /// 300 pt ring of orange light: `ember-halo`, stops at 0.5, 0.73 and 1.
    private var halo: some View {
        let orange = EmberDayReviewPalette.rgb(0xFF9C4D)
        return Circle()
            .fill(
                RadialGradient(
                    stops: [
                        .init(color: orange.opacity(0), location: 0),
                        .init(color: orange.opacity(0), location: 0.5),
                        .init(color: orange.opacity(0.5), location: 0.73),
                        .init(color: orange.opacity(0), location: 1),
                    ],
                    center: .center,
                    startRadius: 0,
                    endRadius: 150
                )
            )
            .frame(width: 300, height: 300)
            .scaleEffect(haloScale)
            .opacity(haloOpacity)
            .allowsHitTesting(false)
    }
}

/// The soft peach light behind the whole day surface: `ember-glow`, 460 pt across.
struct EmberDayReviewGlow: View {
    var body: some View {
        Circle()
            .fill(
                RadialGradient(
                    stops: [
                        .init(color: EmberDayReviewPalette.rgb(0xFFD9C2, 0.95), location: 0),
                        .init(color: EmberDayReviewPalette.rgb(0xFFE6D7, 0.7), location: 0.55),
                        .init(color: EmberDayReviewPalette.rgb(0xFFF4EE, 0), location: 1),
                    ],
                    center: .center,
                    startRadius: 0,
                    endRadius: 230
                )
            )
            .frame(width: 460, height: 460)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
