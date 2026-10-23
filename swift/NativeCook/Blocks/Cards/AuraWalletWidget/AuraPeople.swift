// Original portraits, decoded once before the arrival clock starts.
import UIKit

enum AuraPeople {
  static let names = ["Kofi", "Maya", "Theo", "Raj", "Skye"]
  static let owner = portrait("ana")
  static let friends = ["kofi", "maya", "theo", "raj", "skye"].map(portrait)

  private static func portrait(_ name: String) -> UIImage {
    guard let image = UIImage(named: "aura-wallet-" + name) else {
      assertionFailure("Missing Aura portrait: \(name)")
      return UIImage()
    }
    return image.preparingForDisplay() ?? image
  }
}
