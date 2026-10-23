import Foundation

/// `usePreferences().t(english, vietnamese)` from `i18n.ts`:
/// English unless the device's first preferred language is Vietnamese. The
/// strings live in `{en,vi}.lproj/FlareNotifications.strings`; the table
/// is chosen by the same rule rather than by Bundle's own matching (which
/// would also pick Vietnamese when it is only a later preference).
enum FlareL10n {
  static var isVietnamese: Bool {
    guard let first = Locale.preferredLanguages.first else { return false }
    return Locale(identifier: first).language.languageCode?.identifier == "vi"
  }

  private static let table: Bundle = {
    let code = isVietnamese ? "vi" : "en"
    guard let path = Bundle.main.path(forResource: code, ofType: "lproj"), let bundle = Bundle(path: path)
    else { return .main }
    return bundle
  }()

  static func t(_ key: String) -> String {
    table.localizedString(forKey: key, value: nil, table: "FlareNotifications")
  }

  static var hint: String { t("hint") }
  static var deliverAgain: String { t("deliver") }
  static var seen: String { t("seen") }
  static var pillHint: String { t("a11y.pill.hint") }
}
