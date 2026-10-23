import SwiftUI
import UIKit

struct ReferralRewardFriend {
  let name: String
  let color: UInt32
  let lamps: (UInt32, UInt32, UInt32)
  var image: UIImage {
    let image = UIImage(named: "referral-reward-" + name.lowercased())!
    guard let cg = image.cgImage,
      let space = CGColorSpace(name: CGColorSpace.sRGB),
      let context = CGContext(
        data: nil, width: cg.width, height: cg.height, bitsPerComponent: 8,
        bytesPerRow: cg.width * 4, space: space,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
    else { return image }
    context.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
    guard let decoded = context.makeImage() else { return image }
    return UIImage(cgImage: decoded, scale: 1, orientation: .up)
  }
  static let all: [Self] = [
    .init(name: "Mika", color: 0xFF8A3D, lamps: (0xFF8A3D, 0xFF2E7E, 0x5B2EFF)),
    .init(name: "Juno", color: 0x7BE0B0, lamps: (0x7BE0B0, 0x19B8FF, 0x2E5BFF)),
    .init(name: "Otto", color: 0x8B9CFF, lamps: (0x8B9CFF, 0xB44DFF, 0x1E9BFF)),
    .init(name: "Nori", color: 0xFF8FC7, lamps: (0xFF8FC7, 0xFF6A3D, 0x8A3DFF)),
    .init(name: "Pim", color: 0x6EC6FF, lamps: (0x6EC6FF, 0x5B5BFF, 0x19D9C0)),
    .init(name: "Zuzu", color: 0xFFD966, lamps: (0xFFAE2B, 0xFF7A1F, 0xFF2E7E)),
    .init(name: "Rafa", color: 0xC7A6FF, lamps: (0xC7A6FF, 0xFF5CC8, 0x3D6BFF)),
  ]
  static func rgb(_ hex: UInt32) -> SIMD3<Float> {
    SIMD3(Float((hex >> 16) & 255) / 255, Float((hex >> 8) & 255) / 255, Float(hex & 255) / 255)
  }
  static func blend(_ scroll: Double, lamp: Int = -1) -> SIMD3<Float> {
    let at = ReferralRewardMath.wrap(scroll)
    let i = Int(at)
    let t = Float(at - Double(i))
    func color(_ i: Int) -> SIMD3<Float> {
      let f = all[i % 7]
      return rgb(lamp == 0 ? f.lamps.0 : lamp == 1 ? f.lamps.1 : lamp == 2 ? f.lamps.2 : f.color)
    }
    return color(i) * (1 - t) + color(i + 1) * t
  }
}

enum ReferralRewardL10n {
  static var vietnamese: Bool {
    Locale(identifier: Locale.preferredLanguages.first ?? "en").language.languageCode?.identifier
      == "vi"
  }
  private static let bundle: Bundle = {
    guard let p = Bundle.main.path(forResource: vietnamese ? "vi" : "en", ofType: "lproj"),
      let b = Bundle(path: p)
    else { return .main }
    return b
  }()
  private static let copy: [String: String] = {
    let keys = [
      "title", "subtitle", "hint", "refer", "reset", "invite", "friends", "unjoined",
      "shortUnjoined", "joined", "rewardNote", "rewards", "points", "joinAnnouncement",
      "friendJoined", "friendUnjoined",
    ]
    return Dictionary(
      uniqueKeysWithValues: keys.map {
        ($0, bundle.localizedString(forKey: $0, value: nil, table: "ReferralRewardCard"))
      })
  }()
  static func t(_ key: String) -> String { copy[key] ?? key }
  static func format(_ key: String, _ args: CVarArg...) -> String {
    String(format: t(key), arguments: args)
  }
}

extension Color {
  init(referralReward rgb: SIMD3<Float>, alpha: Double = 1) {
    self.init(.sRGB, red: Double(rgb.x), green: Double(rgb.y), blue: Double(rgb.z), opacity: alpha)
  }
}

/// Yoga-compatible measurements, resolved once per size/type-scale change.
struct ReferralRewardLayout: Equatable {
  let width: Double
  let fontScale: Double
  let stacked: Bool
  let copyWidth: Double
  let titleHeight: Double
  let subtitleHeight: Double
  let copyHeight: Double
  let headerHeight: Double
  let railTop: Double
  let lensY: Double
  let footerTop: Double
  let buttonWidth: Double
  let buttonHeight: Double
  let height: Double
  let actionWidth: Double
  let actionX: Double
  let markX: Double
  let markY: Double
  let digitAdvance: Double
  var origin: ReferralRewardPoint { .init(x: width / 2, y: footerTop + buttonHeight / 2) }
  var centre: ReferralRewardPoint { .init(x: width / 2, y: lensY) }

  init(width: Double, fontScale: Double) {
    self.width = width
    self.fontScale = fontScale
    stacked = width - 48 < 152 * fontScale + 12 + 126
    copyWidth = min(180 * fontScale, stacked ? width - 48 : width - 48 - 12 - 126)
    titleHeight = Self.measure(
      ReferralRewardL10n.t("title"), size: 26, weight: .bold, line: 32, width: copyWidth,
      tracking: -0.5, scale: fontScale)
    subtitleHeight = Self.measure(
      ReferralRewardL10n.t("subtitle"), size: 16, weight: .regular, line: 24, width: copyWidth,
      scale: fontScale)
    copyHeight = titleHeight + 4 + subtitleHeight
    headerHeight =
      stacked ? copyHeight + 12 + max(36, 16 * fontScale) : max(copyHeight, 39 + 16 * fontScale)
    railTop = 24 + headerHeight + 20
    lensY = railTop + 112
    footerTop = railTop + 212
    let font = UIFont.systemFont(ofSize: 16 * fontScale, weight: .medium)
    let a = (ReferralRewardL10n.t("refer") as NSString).size(withAttributes: [
      .font: font, .kern: -0.2,
    ]).width
    let b = (ReferralRewardL10n.t("reset") as NSString).size(withAttributes: [
      .font: font, .kern: -0.2,
    ]).width
    buttonWidth = min(width - 48, 2 * ceil((max(a, b) + 48) / 2))
    let h1 = Self.measure(
      ReferralRewardL10n.t("refer"), size: 16, weight: .medium, line: 22, width: buttonWidth - 48,
      tracking: -0.2, scale: fontScale)
    let h2 = Self.measure(
      ReferralRewardL10n.t("reset"), size: 16, weight: .medium, line: 22, width: buttonWidth - 48,
      tracking: -0.2, scale: fontScale)
    buttonHeight = max(48, 2 * ceil((max(h1, h2) + 24) / 2))
    height = footerTop + buttonHeight + 24
    actionWidth = max(
      44,
      2
        * ceil(
          ((ReferralRewardL10n.t("invite") as NSString).size(withAttributes: [
            .font: UIFont.systemFont(ofSize: 14 * fontScale, weight: .semibold)
          ]).width + 24) / 2))
    actionX = width - 44 - actionWidth / 2
    markX = stacked ? 24 : width - 24 - 126
    markY = stacked ? 24 + copyHeight + 12 + max(36, 16 * fontScale) / 2 : 40
    digitAdvance =
      2
      * ceil(
        ("0" as NSString).size(withAttributes: [
          .font: UIFont.monospacedDigitSystemFont(ofSize: 30 * fontScale, weight: .bold)
        ]).width / 2)
  }
  func textHeight(
    _ text: String, size: Double, weight: UIFont.Weight, line: Double, width: Double,
    tracking: Double = 0
  ) -> Double {
    Self.measure(
      text, size: size, weight: weight, line: line, width: width, tracking: tracking,
      scale: fontScale)
  }
  private static func measure(
    _ text: String, size: Double, weight: UIFont.Weight, line: Double, width: Double,
    tracking: Double = 0, scale: Double
  ) -> Double {
    let font = UIFont.systemFont(ofSize: size * scale, weight: weight)
    let rect = (text as NSString).boundingRect(
      with: CGSize(width: max(width, 1), height: 2000),
      options: [.usesLineFragmentOrigin, .usesFontLeading],
      attributes: [.font: font, .kern: tracking], context: nil)
    return max(1, ceil(rect.height / font.lineHeight)) * line * scale
  }
}
