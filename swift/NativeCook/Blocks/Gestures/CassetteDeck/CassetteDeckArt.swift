import UIKit

/// The registered artwork, bundled flat as `CassetteDeck-<file>`. A missing file stops a debug build.
enum CassetteDeckArt {
    static let cassette = image("cassette")
    static let reel = image("reel")
    static let keyRed = image("key-red")
    static let keyRedDown = image("key-red-down")
    static let keyDark = image("key-dark")
    static let keyDarkDown = image("key-dark-down")
    static let covers = [image("cover-1"), image("cover-2"), image("cover-3")]

    private static func image(_ file: String) -> UIImage {
        guard let image = UIImage(named: "CassetteDeck-\(file).png") else {
            assertionFailure("Missing Cassette Deck asset: \(file)")
            return UIImage()
        }
        return image
    }
}
