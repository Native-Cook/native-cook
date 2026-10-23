import SwiftUI
import UIKit

/// Image resources are decoded once through UIKit's named-image cache before the hero starts.
@MainActor
enum GrassTimerCarouselAssets {
    private static let images: [String: UIImage] = [
        "card-grass": prepared("GrassTimerCarousel-card-grass"),
        "card-quiet": prepared("GrassTimerCarousel-card-quiet"),
        "orb-ocean": prepared("GrassTimerCarousel-orb-ocean"),
        "orb-waves": prepared("GrassTimerCarousel-orb-waves"),
        "orb-airplane": prepared("GrassTimerCarousel-orb-airplane"),
        "story-mountains": prepared("GrassTimerCarousel-story-mountains"),
        "story-clouds": prepared("GrassTimerCarousel-story-clouds"),
    ]

    static func image(_ name: String) -> UIImage { images[name] ?? UIImage() }

    private static func prepared(_ name: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: name, ofType: "jpg"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
}
