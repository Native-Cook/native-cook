import Foundation
import CoreGraphics
import ImageIO

/// SwiftUI binds one image per Shader. Keep the four original maps in one cached sRGB atlas.
/// Regions describe the original, unscaled texels; a one-texel gutter preserves wrap-X/clamp-Y filtering.
final class RaceGlobeCalendarAtlas {
    let image: CGImage
    let regions: [CGRect]

    init(urls: [URL]) {
        precondition(urls.count == 4)
        let sizes = urls.map { url -> CGSize in
            guard let source = CGImageSourceCreateWithURL(url as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary),
                  let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as NSDictionary?,
                  let width = properties[kCGImagePropertyPixelWidth] as? NSNumber,
                  let height = properties[kCGImagePropertyPixelHeight] as? NSNumber else {
                preconditionFailure("Race Globe Calendar: missing map metadata for " + url.lastPathComponent)
            }
            return CGSize(width: width.intValue, height: height.intValue)
        }
        let row2Y = sizes[0].height + 3
        let row3Y = row2Y + max(sizes[1].height, sizes[2].height) + 2
        let layouts = [
            CGRect(origin: CGPoint(x: 1, y: 1), size: sizes[0]),
            CGRect(origin: CGPoint(x: 1, y: row2Y), size: sizes[1]),
            CGRect(origin: CGPoint(x: sizes[1].width + 3, y: row2Y), size: sizes[2]),
            CGRect(origin: CGPoint(x: 1, y: row3Y), size: sizes[3])
        ]
        regions = layouts
        let width = Int(max(sizes[0].width + 2, sizes[1].width + sizes[2].width + 4, sizes[3].width + 2))
        let height = Int(layouts.map(\.maxY).max()!) + 1
        let info = CGBitmapInfo.byteOrder32Big.union(CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue))
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                                      bytesPerRow: width * 4, space: space, bitmapInfo: info.rawValue) else {
            preconditionFailure("Race Globe Calendar: could not allocate map atlas")
        }
        context.interpolationQuality = .none
        context.setShouldAntialias(false)
        context.setBlendMode(.copy)

        // CGContext uses bottom-left destination coordinates. The atlas regions and texture UVs use top-left.
        // Draw CGImages directly in the converted destination rect: their original row order stays intact.
        func draw(_ source: CGImage, in rect: CGRect) {
            let quartzRect = CGRect(x: rect.minX, y: CGFloat(height) - rect.maxY, width: rect.width, height: rect.height)
            context.draw(source, in: quartzRect)
        }
        for (index, url) in urls.enumerated() {
            // Decode one original at a time; none of the four source images remains retained by the atlas.
            autoreleasepool {
                guard let source = CGImageSourceCreateWithURL(url as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary),
                      let map = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCache: false] as CFDictionary) else {
                    preconditionFailure("Race Globe Calendar: could not decode " + url.lastPathComponent)
                }
                let region = layouts[index], w = CGFloat(map.width), h = CGFloat(map.height)
                draw(map, in: region)
                func gutter(_ crop: CGRect, _ destination: CGRect) {
                    guard let texels = map.cropping(to: crop) else { preconditionFailure("Race Globe Calendar: invalid atlas gutter") }
                    draw(texels, in: destination)
                }
                gutter(CGRect(x: w - 1, y: 0, width: 1, height: h), CGRect(x: region.minX - 1, y: region.minY, width: 1, height: h))
                gutter(CGRect(x: 0, y: 0, width: 1, height: h), CGRect(x: region.maxX, y: region.minY, width: 1, height: h))
                gutter(CGRect(x: 0, y: 0, width: w, height: 1), CGRect(x: region.minX, y: region.minY - 1, width: w, height: 1))
                gutter(CGRect(x: 0, y: h - 1, width: w, height: 1), CGRect(x: region.minX, y: region.maxY, width: w, height: 1))
                for (sx, dx) in [(w - 1, region.minX - 1), (0, region.maxX)] {
                    for (sy, dy) in [(CGFloat(0), region.minY - 1), (h - 1, region.maxY)] {
                        gutter(CGRect(x: sx, y: sy, width: 1, height: 1), CGRect(x: dx, y: dy, width: 1, height: 1))
                    }
                }
            }
        }
        guard let atlas = context.makeImage() else { preconditionFailure("Race Globe Calendar: could not finish map atlas") }
        image = atlas
    }
}
