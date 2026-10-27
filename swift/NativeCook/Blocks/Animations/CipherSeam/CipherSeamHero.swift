import SwiftUI

/// The numbers the layout is made of: the screen, its safe-area insets and the text scale, and
/// everything the React Native file derives from them (`heroWidth`, `heroHeight`, `cardHeight` and
/// the panel coordinates).
struct CipherSeamLayout: Equatable {
    /// The whole screen, safe areas included (`useWindowDimensions`).
    let size: CGSize
    let top: CGFloat
    let bottom: CGFloat
    /// React Native's `fontScale`.
    let fontScale: Double

    /// `Math.min(width, 560)`.
    var heroWidth: CGFloat { min(size.width, 560) }
    /// `height < 740 || fontScale > 1.3 ? 260 : 370`.
    var heroHeight: CGFloat { size.height < 740 || fontScale > 1.3 ? 260 : 370 }
    /// The hero's width over the 402 pt design width.
    var scale: CGFloat { heroWidth / 402 }
    /// `CARD_HEIGHT * heroWidth / 402`.
    var cardHeight: CGFloat { 135 * scale }
    /// The card's top inside the hero.
    var cardTop: CGFloat { (heroHeight - cardHeight) / 2 }

    // Panel coordinates: the panel reaches 60 pt under the nav and the hero starts 1 pt below that.
    var heroBottom: CGFloat { 61 + heroHeight }
    var cardBottom: CGFloat { 61 + heroHeight / 2 + cardHeight / 2 }
    var laneTop: CGFloat { cardBottom - cardHeight - 30 }
}

/// The hero: the lane of dust, the card and token plane, and the seam, all drawn from loop time.
/// `visual` of the React Native file. The clock runs only while `paused` is false.
struct CipherSeamHero: View {
    let layout: CipherSeamLayout
    let playback: CipherSeamPlayback
    let reduced: Bool
    let paused: Bool

    /// The card's left edge, in the source's 362 pt phone viewport, at the measured times.
    private static let knotTimes: [Double] = [0, 2.75, 3, 3.5, 4, 4.5, 5, 5.5, 6, 6.5, 7, 7.5, 8, 9, 9.8, 10.8]
    private static let knotX: [Double] = [-188, -188, -167, -131, -92, -55, -17, 16, 55, 82, 120, 158, 197, 273, 333, 409]
    /// The previous pass's token matrix, still leaving to the right as the loop starts.
    private static let previousTimes: [Double] = [0, 2.5, 10.55, 10.8]
    private static let previousX: [Double] = [226, 416, 226, 226]
    private static let previousOpacityTimes: [Double] = [0, 2.5, 2.51, 10.55, 10.8]
    private static let previousOpacity: [Double] = [1, 1, 0, 0, 1]

    var body: some View {
        TimelineView(.animation(paused: paused)) { timeline in
            scene(time: playback.time(at: timeline.date))
        }
        .frame(width: layout.heroWidth, height: layout.heroHeight, alignment: .topLeading)
    }

    private func scene(time: Double) -> some View {
        let width = Double(layout.heroWidth)
        let height = Double(layout.heroHeight)
        let cardTop = Double(layout.cardTop)
        let cardHeight = Double(layout.cardHeight)
        let drift = reduced ? 0 : time
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                CipherSeamDrawing.dust(
                    context, time: drift, width: width,
                    center: cardTop + cardHeight / 2 + 14, lane: cardHeight * 0.66
                )
            }
            .frame(width: width, height: height)
            track(time: time)
            Canvas { context, _ in
                CipherSeamDrawing.seam(
                    context, time: time, reduced: reduced,
                    width: width, cardTop: cardTop, cardHeight: cardHeight
                )
            }
            .frame(width: width, height: height)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipped()
    }

    /// `cardTrack`: the card and its token matrix, as three planes clipped at the seam.
    private func track(time: Double) -> some View {
        let width = layout.heroWidth
        let scale = layout.scale
        let centered = width / 2 - CipherSeamCardFace.width * scale / 2
        let sourceX = CipherSeamMath.interpolate(time, Self.knotTimes, Self.knotX)
        let position = reduced ? centered : CGFloat(sourceX / 362) * width
        let previousX = CipherSeamMath.interpolate(time, Self.previousTimes, Self.previousX)
        let previousPosition = reduced ? centered : CGFloat(previousX / 362) * width
        let previousOpacity = reduced ? 0 : CipherSeamMath.interpolate(time, Self.previousOpacityTimes, Self.previousOpacity)
        let phase = reduced ? 0 : Int(floor(time * 12)) % CipherSeamField.glyphRows.count
        return ZStack(alignment: .topLeading) {
            CipherSeamPlane(face: .card, left: position, isRight: false, width: width, scale: scale)
            CipherSeamPlane(face: .tokens(phase: phase), left: position, isRight: true, width: width, scale: scale)
            if previousOpacity > 0.001 {
                CipherSeamPlane(face: .tokens(phase: phase), left: previousPosition, isRight: true, width: width, scale: scale)
                    .opacity(previousOpacity)
            }
        }
        .frame(width: width, height: layout.cardHeight, alignment: .topLeading)
        .offset(y: layout.cardTop)
        .accessibilityHidden(true)
    }
}
