import CoreGraphics
import CoreText
import ImageIO
import UIKit

enum CityEventHeroAssets {
    static let city = image("CityEventHero-city")
    static let fill = image("CityEventHero-fill")
    static let backDepth = image("CityEventHero-backdepth", extension: "png")
    static let depth = image("CityEventHero-depth", extension: "png")
    static let near = image("CityEventHero-near", extension: "png")
    static let tower = image("CityEventHero-tower", extension: "png")
    static let facade = image("CityEventHero-facade", extension: "png")
    static let glyphs = image("CityEventHero-glyphs", extension: "png")
    static let guests = (1...7).map { image("CityEventHero-guest-\($0)") }
    static let towerMask = tower
    static let nearMask = near
    /// The fourth guest is the portrait the canonical scene raises up the tower.
    static let viewer = guests[3]
    static let headlineFont = font("CityEventHero-BricolageHeadline")
    static let wordmarkFont = font("CityEventHero-ArchivoWordmark")
    static let lights = image("CityEventHero-lights", extension: "png")
    static let key = image("CityEventHero-key", extension: "png")
    static let bloom = image("CityEventHero-bloom", extension: "png")
    static let bloomKey = image("CityEventHero-bloomkey", extension: "png")
    static let sceneAtlas = makeSceneAtlas()

    static func prepare() {
        _ = city
        _ = fill
        _ = backDepth
        _ = depth
        _ = near
        _ = tower
        _ = facade
        _ = glyphs
        _ = guests
        _ = towerMask
        _ = nearMask
        _ = viewer
        _ = headlineFont
        _ = wordmarkFont
        _ = lights
        _ = key
        _ = bloom
        _ = bloomKey
        _ = sceneAtlas
    }

    private static func image(_ name: String, extension fileExtension: String = "jpg") -> UIImage {
        guard let url = Bundle.main.url(forResource: name, withExtension: fileExtension),
              let decoded = decodedImage(at: url) else {
            preconditionFailure("Missing CityEventHero asset: \(name)")
        }
        return UIImage(cgImage: decoded, scale: 1, orientation: .up)
    }

    private static func font(_ name: String) -> CGFont {
        guard let url = Bundle.main.url(forResource: name, withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL),
              let font = CGFont(provider) else {
            preconditionFailure("Missing CityEventHero font: \(name)")
        }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        return font
    }

    /// Decode at the source's pixel dimensions so sampled depth and LED maps stay aligned.
    private static func decodedImage(at url: URL) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options = [
            kCGImageSourceShouldCache: true,
            kCGImageSourceShouldCacheImmediately: true,
        ] as CFDictionary
        return CGImageSourceCreateImageAtIndex(source, 0, options)
    }

    /// SwiftUI accepts one explicit image argument per shader. The photo remains the layer-effect
    /// input; every other scene image is packed at its decoded source dimensions into this atlas.
    /// RGB art is sRGB. Scalar maps are stored in alpha, which avoids a color transfer changing
    /// their raw grey values when the atlas is resolved for SwiftUI.
    private static func makeSceneAtlas() -> UIImage {
        let width = 1701
        let height = 4162
        let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(
                  data: nil,
                  width: width,
                  height: height,
                  bitsPerComponent: 8,
                  bytesPerRow: width * 4,
                  space: colorSpace,
                  bitmapInfo: bitmapInfo
              ) else {
            preconditionFailure("Could not create CityEventHero scene atlas")
        }
        context.clear(CGRect(x: 0, y: 0, width: width, height: height))
        context.interpolationQuality = .none
        // CGContext's native origin is bottom-left; convert tile rectangles while leaving
        // CGImage row orientation untouched (flipping the CTM would flip every source map).
        func draw(_ image: CGImage, in rect: CGRect) {
            context.draw(image, in: CGRect(x: rect.minX, y: CGFloat(height) - rect.maxY, width: rect.width, height: rect.height))
        }

        draw(scalarMap(tower), in: CGRect(x: 0, y: 0, width: 850, height: 1850))
        draw(scalarMap(near), in: CGRect(x: 0, y: 1850, width: 850, height: 1850))
        draw(scalarMap(lights), in: CGRect(x: 850, y: 0, width: 850, height: 1850))
        draw(cgImage(fill), in: CGRect(x: 850, y: 1850, width: 425, height: 925))
        draw(scalarMap(backDepth), in: CGRect(x: 1275, y: 1850, width: 425, height: 925))
        draw(scalarMap(depth), in: CGRect(x: 850, y: 2775, width: 425, height: 925))
        draw(scalarMap(key), in: CGRect(x: 1275, y: 2775, width: 425, height: 925))
        draw(scalarMap(bloom), in: CGRect(x: 0, y: 3700, width: 212, height: 462))
        draw(scalarMap(bloomKey), in: CGRect(x: 212, y: 3700, width: 212, height: 462))
        draw(scalarMap(glyphs), in: CGRect(x: 424, y: 3700, width: 110, height: 7))
        draw(cgImage(viewer), in: CGRect(x: 534, y: 3700, width: 180, height: 180))
        draw(scalarMap(facade), in: CGRect(x: 1700, y: 0, width: 1, height: 1850))

        guard let atlas = context.makeImage() else {
            preconditionFailure("Could not finalize CityEventHero scene atlas")
        }
        return UIImage(cgImage: atlas, scale: 1, orientation: .up)
    }

    private static func cgImage(_ image: UIImage) -> CGImage {
        guard let cgImage = image.cgImage else {
            preconditionFailure("Could not decode CityEventHero image pixels")
        }
        return cgImage
    }

    /// Copies an 8-bit monochrome source map into premultiplied RGBA with each component set to
    /// the original byte. The scene shader reads alpha, whose value remains byte-for-byte scalar.
    private static func scalarMap(_ image: UIImage) -> CGImage {
        let sourceImage = cgImage(image)
        guard sourceImage.bitsPerComponent == 8,
              sourceImage.bitsPerPixel == 8,
              sourceImage.colorSpace?.model == .monochrome,
              let sourceData = sourceImage.dataProvider?.data,
              let sourceBytes = CFDataGetBytePtr(sourceData),
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else {
            preconditionFailure("CityEventHero scalar map must be 8-bit monochrome")
        }
        let width = sourceImage.width
        let height = sourceImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        for y in 0..<height {
            let sourceRow = sourceBytes.advanced(by: y * sourceImage.bytesPerRow)
            for x in 0..<width {
                let value = sourceRow[x]
                let offset = (y * width + x) * 4
                pixels[offset] = value
                pixels[offset + 1] = value
                pixels[offset + 2] = value
                pixels[offset + 3] = value
            }
        }
        let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        let encoded = pixels.withUnsafeMutableBytes { bytes in
            CGContext(
                data: bytes.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: colorSpace,
                bitmapInfo: bitmapInfo
            )?.makeImage()
        }
        guard let encoded else {
            preconditionFailure("Could not encode CityEventHero scalar map")
        }
        return encoded
    }
}
