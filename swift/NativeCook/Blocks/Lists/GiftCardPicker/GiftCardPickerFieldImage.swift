import SwiftUI
import UIKit
import CoreImage

struct GiftCardPickerFieldImage: View {
    let name: String
    let samples: [[UInt32]]
    let width: CGFloat
    let height: CGFloat
    let blur: CGFloat
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        Image(uiImage: Renderer.image(name: name, samples: samples, size: CGSize(width: width, height: height), scale: displayScale, blur: blur))
            .resizable()
            .frame(width: width, height: height)
            .allowsHitTesting(false)
    }

    @MainActor private enum Renderer {
        static let context = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
        static var cache: [String: UIImage] = [:]

        static func image(name: String, samples: [[UInt32]], size: CGSize, scale: CGFloat, blur: CGFloat) -> UIImage {
            let w = max(1, Int((size.width * scale).rounded()))
            let h = max(1, Int((size.height * scale).rounded()))
            let key = "\(name)-\(w)-\(h)-\(scale)-\(blur)"
            if let cached = cache[key] { return cached }
            let cols = samples[0].count
            let rows = samples.count
            var bytes = [UInt8](repeating: 255, count: cols * rows * 4)
            for y in 0..<rows {
                for x in 0..<cols {
                    for channel in 0..<3 {
                        let shift = UInt32((2 - channel) * 8)
                        bytes[(y * cols + x) * 4 + channel] = UInt8((samples[y][x] >> shift) & 255)
                    }
                }
            }
            let space = CGColorSpace(name: CGColorSpace.sRGB)!
            let source = CIImage(bitmapData: Data(bytes), bytesPerRow: cols * 4, size: CGSize(width: cols, height: rows), format: .RGBA8, colorSpace: space)
            // Like Skia, stretch the tiny RGBA field with bilinear sampling, then clamp blur.
            let extent = CGRect(x: 0, y: 0, width: w, height: h)
            let stretched = source.samplingLinear().clampedToExtent().transformed(by: CGAffineTransform(scaleX: CGFloat(w) / CGFloat(cols), y: CGFloat(h) / CGFloat(rows)))
            let filtered = stretched.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: blur * scale]).cropped(to: extent)
            guard let cg = context.createCGImage(filtered, from: extent, format: .RGBA8, colorSpace: space) else {
                assertionFailure("Could not render Gift Card Picker field \(name)")
                return UIImage()
            }
            let image = UIImage(cgImage: cg, scale: scale, orientation: .up)
            cache[key] = image
            return image
        }
    }
}
