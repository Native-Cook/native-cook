import UIKit

/// expo-haptics on iOS: each call makes a fresh generator, prepares it and
/// fires, exactly as `selectionAsync()` / `impactAsync(style)` do.
@MainActor
enum GlowknobHaptics {
  enum ImpactStyle { case light, soft, medium }

  static func selection() {
    let generator = UISelectionFeedbackGenerator()
    generator.prepare()
    generator.selectionChanged()
  }

  static func impact(_ style: ImpactStyle) {
    let generator: UIImpactFeedbackGenerator
    switch style {
    case .light: generator = UIImpactFeedbackGenerator(style: .light)
    case .soft: generator = UIImpactFeedbackGenerator(style: .soft)
    case .medium: generator = UIImpactFeedbackGenerator(style: .medium)
    }
    generator.prepare()
    generator.impactOccurred()
  }
}
