import CoreImage
import UIKit

/// Pre-render the finite text/blur states at layout time; no filters, glyph shaping,
/// image decoding, or image allocation takes place in the display-link callback.
@MainActor enum HorizonTextRaster {
  private static let context = CIContext(options: [.cacheIntermediates: false])
  static func images(_ text: String, font: UIFont, width: Double, line: Double, scale: Double,
                     color: UIColor? = nil) -> [UIImage] {
    let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.opaque = false
    let size = CGSize(width: width + 16, height: line + 16)
    let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
      let style = NSMutableParagraphStyle(); style.lineBreakMode = .byTruncatingTail
      (text as NSString).draw(in: CGRect(x: 8, y: 8 + (line - font.lineHeight) / 2,
        width: width, height: max(line, font.lineHeight)), withAttributes: [.font: font, .foregroundColor: color ?? HorizonResources.ink, .paragraphStyle: style])
    }
    guard let input = CIImage(image: image) else { return Array(repeating: image, count: 9) }
    var result = [image]
    for level in 1...8 {
      let blur = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: Double(level) * 0.5 * scale])
      if let cg = context.createCGImage(blur, from: input.extent) { result.append(UIImage(cgImage: cg, scale: scale, orientation: .up)) }
      else { result.append(image) }
    }
    return result
  }
}
