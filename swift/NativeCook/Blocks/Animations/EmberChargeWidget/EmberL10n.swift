import Foundation

/// English/Vietnamese block strings; numeric grouping remains en-US in either language.
enum EmberL10n {
  private static let table: Bundle = {
    let first = Locale.preferredLanguages.first ?? "en"
    let code = Locale(identifier: first).language.languageCode?.identifier == "vi" ? "vi" : "en"
    guard let path = Bundle.main.path(forResource: code, ofType: "lproj"),
      let bundle = Bundle(path: path)
    else { return .main }
    return bundle
  }()

  static func t(_ key: String) -> String {
    table.localizedString(forKey: key, value: nil, table: "EmberChargeWidget")
  }

  static func number(_ n: Int) -> String {
    n.formatted(.number.locale(Locale(identifier: "en_US")))
  }
}
