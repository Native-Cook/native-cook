import SwiftUI

/// The illustrated deck (`IllustratedDeck`): the puzzled-character cover in front with two covers
/// peeking above it before the tutorial, which turns edge-on to reveal the first white card; after the
/// tutorial the same layer returns as the "This or that" deck, rising from below.
///
/// It is `width - 10` x `height - 10` points, 5 pt in from the white cards, turned about its vertical
/// axis under a 1600 pt perspective.
struct PaperDeckTutorialCover: View {
    let pose: PaperDeckTutorialPose
    let width: CGFloat
    let height: CGFloat
    let reduced: Bool
    let introTravel: Double

    /// `borderRadius: 27` on every cover.
    private static let radius: CGFloat = 27

    var body: some View {
        let style = PaperDeckTutorialScript.coverStyle(pose: pose, reduced: reduced, introTravel: introTravel)
        let w = width - 10
        let h = height - 10
        let projection = PaperDeckTutorialScript.face(
            size: CGSize(width: w, height: h),
            x: 0,
            y: style.translateY,
            scale: 1,
            roll: 0,
            yaw: style.rotateY
        )
        ZStack(alignment: .topLeading) {
            // The cards behind the cover peek above it: sunny then night before the tutorial,
            // thinking then sunny after it.
            ZStack(alignment: .topLeading) {
                coverBack(
                    base: "cover-sunny", outro: "cover-thinking", shaded: false,
                    scale: 0.67, lift: introTravel * 0.58, outroOpacity: style.outroBackOpacity, w: w, h: h
                )
                coverBack(
                    base: "cover-night", outro: "cover-sunny", shaded: true,
                    scale: 0.83, lift: introTravel * 0.3504, outroOpacity: style.outroBackOpacity, w: w, h: h
                )
            }
            .frame(width: w, height: h, alignment: .topLeading)
            .opacity(style.backsOpacity)

            ZStack {
                PaperDeckTutorialPalette.coverFront
                art("cover-thinking", w: w, h: h)
                art("choice-food", w: w, h: h)
                    .opacity(style.choiceOpacity)
            }
            .frame(width: w, height: h)
            .clipShape(RoundedRectangle(cornerRadius: Self.radius))
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .opacity(style.opacity)
        .projectionEffect(projection)
        .offset(x: 5, y: 5)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// A cover behind the front one: scaled about its top centre, then moved up by `lift`.
    private func coverBack(
        base: String, outro: String, shaded: Bool,
        scale: CGFloat, lift: Double, outroOpacity: Double, w: CGFloat, h: CGFloat
    ) -> some View {
        ZStack {
            PaperDeckTutorialPalette.coverBack
            art(base, w: w, h: h)
            art(outro, w: w, h: h)
                .opacity(outroOpacity)
            if shaded {
                PaperDeckTutorialPalette.backShade
            }
        }
        .frame(width: w, height: h)
        .clipShape(RoundedRectangle(cornerRadius: Self.radius))
        .scaleEffect(scale, anchor: .top)
        .offset(y: -CGFloat(lift))
    }

    /// An illustration filling the cover (`resizeMode="cover"`).
    private func art(_ name: String, w: CGFloat, h: CGFloat) -> some View {
        PaperDeckTutorialAssets.image(name)
            .resizable()
            .scaledToFill()
            .frame(width: w, height: h)
            .clipped()
    }
}
