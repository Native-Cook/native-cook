import SwiftUI
import UIKit

/// The three articles' flat illustrations and their three painted depth layers, decoded once.
@MainActor
enum ForYouInsightsAssets {
    private static let keys = ["morning", "food", "run"]

    static let flats: [Image] = keys.map { Image(uiImage: load("ForYouInsights-\($0)", "jpg")) }
    /// Back, middle, front.
    static let layers: [[Image]] = keys.map { key in
        [
            Image(uiImage: load("ForYouInsights-\(key)-L0", "jpg")),
            Image(uiImage: load("ForYouInsights-\(key)-L1", "webp")),
            Image(uiImage: load("ForYouInsights-\(key)-L2", "webp")),
        ]
    }

    private static func load(_ name: String, _ ext: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: name, ofType: ext),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
}

/// Resolves and draws every depth layer once so the first flight never waits for a texture upload.
struct ForYouInsightsWarmLayers: View, Equatable {
    static func == (_ lhs: Self, _ rhs: Self) -> Bool { true }

    var body: some View {
        Canvas { context, _ in
            for (article, images) in ForYouInsightsAssets.layers.enumerated() {
                for (layer, image) in images.enumerated() {
                    context.draw(
                        context.resolve(image),
                        in: CGRect(x: layer, y: article, width: 1, height: 1)
                    )
                }
            }
        }
        .frame(width: 3, height: 3)
        .opacity(0.004)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
