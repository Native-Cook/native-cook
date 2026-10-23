import CoreText
import UIKit

/// Block-local assets and the label’s on-demand font, with prefixed bundle names to avoid collisions.
@MainActor enum HorizonResources {
  private static var images: [String: UIImage] = [:]
  private static let registered: Void = {
    guard let url = Bundle.main.url(forResource: "HorizonUpload-Manrope_500Medium", withExtension: "ttf") else { return }
    CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
  }()
  // Only the status label is custom type in the approved RN rendering.
  static func labelFont(_ size: Double) -> UIFont {
    _ = registered
    return UIFont(name: "Manrope-Medium", size: size) ?? .systemFont(ofSize: size, weight: .medium)
  }
  static func image(_ name: String) -> UIImage {
    if let image = images[name] { return image }
    guard let url = Bundle.main.url(forResource: "horizon-upload-" + name, withExtension: "png"),
      let data = try? Data(contentsOf: url), let image = UIImage(data: data) else {
      assertionFailure("Missing Horizon Upload image: \(name)"); return UIImage()
    }
    images[name] = image; return image
  }
  static let ink = UIColor(red: 244 / 255, green: 247 / 255, blue: 1, alpha: 1)
}
