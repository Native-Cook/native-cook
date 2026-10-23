import Foundation

/// Match the RN preferred-language rule, independently of the gallery's locale.
enum HorizonL10n {
  private static let bundle: Bundle = {
    let vi = Locale(identifier: Locale.preferredLanguages.first ?? "en").language.languageCode?.identifier == "vi"
    guard let path = Bundle.main.path(forResource: vi ? "vi" : "en", ofType: "lproj"), let bundle = Bundle(path: path) else { return .main }
    return bundle
  }()
  static func t(_ key: String) -> String { bundle.localizedString(forKey: key, value: key, table: "HorizonUpload") }
}
