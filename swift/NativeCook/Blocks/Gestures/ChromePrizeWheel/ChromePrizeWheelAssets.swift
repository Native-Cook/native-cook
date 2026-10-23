import CoreImage
import CoreText
import UIKit

final class ChromePrizeWheelAssets {
    private let imageContext = CIContext(options: [.cacheIntermediates: false])
    private var blurredStars: [Int: UIImage] = [:]
    private var images: [String: UIImage] = [:]
    let stars: [UIImage]
    let badges: [UIImage]
    let fontName: String

    init() {
        let names = [
            "wheel-backdrop.jpg", "wheel-env.png", "wheel-env-soft.png", "wheel-prizes.png",
            "wheel-pointer.png", "wheel-pointer-shadow.png", "wheel-particles.png", "wheel-badges.png",
            "wheel-swipe.png", "prize-plate.png", "prize-chest.png", "prize-tray.png", "prize-box.png",
            "prize-coffee.png", "prize-stack.png",
        ]
        var loaded: [String: UIImage] = [:]
        for name in names {
            let url = Self.url(name)
            guard let image = UIImage(contentsOfFile: url.path) else {
                preconditionFailure("Missing Chrome Prize Wheel asset: \(name)")
            }
            loaded[name] = image
        }
        images = loaded
        let atlas = loaded["wheel-particles.png"]!.cgImage!
        stars = (0..<4).map {
            UIImage(cgImage: atlas.cropping(to: CGRect(x: $0 * 192, y: 0, width: 192, height: 192))!)
        }
        let badgeAtlas = loaded["wheel-badges.png"]!.cgImage!
        badges = (0..<6).map {
            UIImage(cgImage: badgeAtlas.cropping(to: CGRect(x: $0 * 128, y: 0, width: 128, height: 128))!)
        }
        let fontURL = Self.url("ArchivoExpanded-Black.ttf")
        CTFontManagerRegisterFontsForURL(fontURL as CFURL, .process, nil)
        let provider = CGDataProvider(url: fontURL as CFURL)!
        fontName = CGFont(provider)!.postScriptName! as String
    }

    static func url(_ name: String) -> URL {
        let full = "ChromePrizeWheel-" + name
        if let url = Bundle.main.url(forResource: full, withExtension: nil) { return url }
        if let url = Bundle.main.url(forResource: full, withExtension: nil, subdirectory: "Assets") { return url }
        preconditionFailure("Missing bundled asset \(full)")
    }

    func star(_ tile: Int, blur: Double, size: Double) -> UIImage {
        let sigma = min(24, max(0, Int((blur * 192 / max(1, size)).rounded())))
        guard sigma > 0 else { return stars[tile] }
        let key = tile * 25 + sigma
        if let image = blurredStars[key] { return image }
        let input = CIImage(cgImage: stars[tile].cgImage!)
        let filtered = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma])
        guard let result = imageContext.createCGImage(filtered, from: input.extent) else { return stars[tile] }
        let image = UIImage(cgImage: result)
        blurredStars[key] = image
        return image
    }

    func image(_ name: String) -> UIImage { images[name]! }
    func font(_ size: Double) -> UIFont { UIFont(name: fontName, size: size)! }
}
