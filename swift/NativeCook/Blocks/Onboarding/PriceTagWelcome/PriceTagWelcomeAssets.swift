import UIKit

enum PriceTagWelcomeAssets {
    private static let images: [String: UIImage] = {
        let names = [
            "bag", "bag-shadow", "camera", "camera-shadow", "card-icon",
            "headphones", "headphones-shadow", "laptop", "laptop-shadow",
            "shirt", "shirt-shadow"
        ]
        return Dictionary(uniqueKeysWithValues: names.map { name in
            (name, load(name))
        })
    }()

    static func image(_ name: String) -> UIImage {
        images[name] ?? UIImage()
    }

    private static func load(_ name: String) -> UIImage {
        guard let path = Bundle.main.path(
            forResource: "PriceTagWelcome-\(name)", ofType: "png"
        ), let image = UIImage(contentsOfFile: path) else {
            assertionFailure("Missing PriceTagWelcome asset: \(name)")
            return UIImage()
        }
        return image.preparingForDisplay() ?? image
    }
}
