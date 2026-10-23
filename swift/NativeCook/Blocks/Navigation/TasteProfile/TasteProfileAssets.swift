import CoreText
import UIKit

enum TasteProfileAssets {
    static let profile = image("profile", "jpg")
    static let mutual1 = image("mutual-1", "jpg")
    static let mutual2 = image("mutual-2", "jpg")
    static let grid5 = image("grid-5", "jpg")
    static let grid6 = image("grid-6", "jpg")

    /// The strip's photos: the pin's four, then six per city (TasteProfileMotion.stripPhotos indexes this list).
    static let photos: [UIImage] = {
        var names = (1...4).map { ("grid-\($0)", "jpg") }
        for city in ["ny", "ld", "pa"] { names += (1...6).map { ("\(city)-\($0)", "webp") } }
        return names.map { image($0.0, $0.1) }
    }()

    static let coinFrames = sprite("coin", "webp", frameWidth: 168, frameHeight: 170, count: 28)
    static let gemFrames = sprite("gem", "webp", frameWidth: 208, frameHeight: 173, count: 12)

    private static let registered: Void = {
        for name in ["Anybody-Body", "Anybody-SemiBold", "Anybody-ExtraBold"] {
            guard let url = Bundle.main.url(forResource: "TasteProfile-\(name)", withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func register() { _ = registered }

    /// Decoded once, so the first flap never waits on a JPEG or WebP decode.
    private static func image(_ name: String, _ extensionName: String) -> UIImage {
        guard let url = Bundle.main.url(forResource: "TasteProfile-\(name)", withExtension: extensionName),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }

    private static func sprite(_ name: String, _ extensionName: String, frameWidth: Int, frameHeight: Int, count: Int) -> [UIImage] {
        guard let source = image(name, extensionName).cgImage else { return [] }
        return (0..<count).compactMap { index in
            let rect = CGRect(x: index * frameWidth, y: 0, width: frameWidth, height: frameHeight)
            guard let frame = source.cropping(to: rect) else { return nil }
            return UIImage(cgImage: frame, scale: 1, orientation: .up)
        }
    }
}
