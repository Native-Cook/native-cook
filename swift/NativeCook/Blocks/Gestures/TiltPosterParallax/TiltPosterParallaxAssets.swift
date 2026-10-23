import SwiftUI
import UIKit

enum TiltPosterParallaxAssets {
    static let backdrop = load("TiltPosterParallax-backdrop.jpg")
    static let plate = load("TiltPosterParallax-poster-plate.jpg")
    static let figure = load("TiltPosterParallax-poster-figure.png")
    static let edgeSide = load("TiltPosterParallax-edge-side.png")
    static let edgeTop = load("TiltPosterParallax-edge-top.png")

    private static func load(_ name: String) -> Image {
        let image = UIImage(named: name)
        assert(image != nil, "Missing TiltPosterParallax asset: \(name)")
        return Image(uiImage: image ?? UIImage())
    }
}
