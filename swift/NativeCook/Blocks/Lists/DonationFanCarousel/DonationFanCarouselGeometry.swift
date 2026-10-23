import SwiftUI

/// What `DonationFanCarousel` computes from `useWindowDimensions()`: the stage that is centred on
/// the window, the scale that maps the 402 pt reference onto it and the active portrait's size.
struct DonationFanCarouselGeometry {
    /// `const stageWidth = width - 16`.
    let stageWidth: CGFloat
    /// `const stageHeight = (462 / 748) * stageWidth`.
    let stageHeight: CGFloat
    /// `const scale = stageWidth / 402`.
    let scale: CGFloat
    /// `const size = layout.deck.card * scale`.
    let cardSize: CGFloat
    /// `left: (width - stageWidth) / 2` of `styles.stage`.
    let stageLeft: CGFloat
    /// `top: (height - stageHeight) / 2` of `styles.stage`.
    let stageTop: CGFloat

    init(window: CGSize) {
        typealias Layout = DonationFanCarouselTheme.Layout
        stageWidth = window.width - Layout.stageInset
        stageHeight = (Layout.stageAspectHeight / Layout.sourceWidth) * stageWidth
        scale = stageWidth / Layout.referenceWidth
        cardSize = Layout.Deck.card * scale
        stageLeft = (window.width - stageWidth) / 2
        stageTop = (window.height - stageHeight) / 2
    }

    /// `left: layout.deck.centerX * scale - size / 2`: where the active portrait's box starts.
    var deckLeft: CGFloat {
        DonationFanCarouselTheme.Layout.Deck.centerX * scale - cardSize / 2
    }

    /// `top: layout.deck.centerY * scale - size / 2`.
    var deckTop: CGFloat {
        DonationFanCarouselTheme.Layout.Deck.centerY * scale - cardSize / 2
    }

    /// `borderRadius: layout.deck.radius * scale`.
    var cardRadius: CGFloat {
        DonationFanCarouselTheme.Layout.Deck.radius * scale
    }
}
