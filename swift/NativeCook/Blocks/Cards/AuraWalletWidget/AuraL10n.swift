// Device-language copy, with the same English fallback as the source.
import Foundation

enum AuraL10n {
  private static let bundle: Bundle = {
    let vietnamese =
      Locale(identifier: Locale.preferredLanguages.first ?? "en")
      .language.languageCode?.identifier == "vi"
    guard let path = Bundle.main.path(forResource: vietnamese ? "vi" : "en", ofType: "lproj"),
      let bundle = Bundle(path: path)
    else { return .main }
    return bundle
  }()

  static func t(_ key: String) -> String {
    bundle.localizedString(forKey: key, value: nil, table: "AuraWalletWidget")
  }

  static func format(_ key: String, _ arguments: CVarArg...) -> String {
    String(format: t(key), arguments: arguments)
  }

  private static let whole: NumberFormatter = {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_US")
    formatter.numberStyle = .decimal
    formatter.maximumFractionDigits = 0
    return formatter
  }()

  static func cents(_ value: Int) -> String {
    let absolute = abs(value)
    let integer = whole.string(from: NSNumber(value: absolute / 100)) ?? "0"
    return "\(value < 0 ? "−" : "")\(integer).\(String(format: "%02d", absolute % 100))"
  }
}
