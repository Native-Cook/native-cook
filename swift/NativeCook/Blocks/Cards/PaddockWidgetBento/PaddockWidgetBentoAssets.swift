import UIKit

/// Loads each bundled Paddock image once from the block's flat Assets directory.
@MainActor
enum PaddockWidgetBentoAssets {
    private static let cache = NSCache<NSString, UIImage>()

    static let statsStack = load("PaddockWidgetBento-stats-stack", extension: "png")
    static let bannerPhoto = load("PaddockWidgetBento-banner-photo", extension: "jpg")

    private static func load(_ name: String, extension fileExtension: String) -> UIImage {
        let key = name as NSString
        if let cached = cache.object(forKey: key) { return cached }
        guard let path = Bundle.main.path(forResource: name, ofType: fileExtension),
              let image = UIImage(contentsOfFile: path) else {
            assertionFailure("Missing Paddock Widget Bento image: \(name).\(fileExtension)")
            return UIImage()
        }
        let prepared = image.preparingForDisplay() ?? image
        cache.setObject(prepared, forKey: key)
        return prepared
    }
}
