import SwiftUI
import UIKit

/// The text area's paper as images, made once: the note as it folds, the empty area for the reset, the map, and a
/// white and a black copy of the card's outline (blank paper if a snapshot is missing, and the panels' light).
@MainActor
final class PastePlacesImportFaces {
    let note: CGImage?
    let rest: CGImage?
    let map: CGImage?
    let white: CGImage?
    let black: CGImage?
    let scale: CGFloat

    init(layout: PastePlacesImportLayout, scale: CGFloat) {
        self.scale = scale
        let card = PastePlacesImportTheme.card
        let cardWidth = layout.px(card.width)
        let cardHeight = layout.px(card.height)
        let cardX = layout.px(card.x)
        let cardY = layout.y(card.y)
        let radius = layout.px(PastePlacesImportTheme.Radius.card)

        func render<Content: View>(_ content: Content) -> CGImage? {
            let renderer = ImageRenderer(content: content)
            renderer.scale = scale
            return renderer.cgImage
        }
        func face(_ time: Double) -> CGImage? {
            render(
                PastePlacesImportCardFace(time: time, layout: layout)
                    .offset(x: -cardX, y: -cardY)
                    .frame(width: cardWidth, height: cardHeight, alignment: .topLeading)
                    .clipped()
            )
        }
        // The note as the first fold begins, and the empty area (the loop's first frame).
        note = face(PastePlacesImportMotion.foldClose.0)
        rest = face(0)
        let unit = layout.unit
        map = render(
            Canvas { context, _ in PastePlacesImportMap.draw(&context, unit: unit) }
                .frame(width: cardWidth, height: cardHeight)
        )
        white = render(
            RoundedRectangle(cornerRadius: radius, style: .circular)
                .fill(Color.white)
                .frame(width: cardWidth, height: cardHeight)
        )
        black = render(
            RoundedRectangle(cornerRadius: radius, style: .circular)
                .fill(Color.black)
                .frame(width: cardWidth, height: cardHeight)
        )
    }
}
