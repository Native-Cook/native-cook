// Replaces expo-content-transition's ExpoView glue (NumericTextView.swift +
// ContentTransitionModule.swift): the same prop → spec mapping as
// `NumericTextView.applyProps()` and the JS `resolveNumericTextStyle`, hosted
// in a UIViewRepresentable. MIT License, see THIRD_PARTY.md.
import SwiftUI
import UIKit

/// The props the RN call sites pass to `<GlowknobNumericText>`.
struct GlowknobNumericTextProps: Equatable {
  var value: String
  /// "start" / "center" / "end" (from textAlign left/center/right).
  var alignment: String?
  var direction: String?
  var animated: Bool?
  /// Milliseconds, as in JS; the native side gets seconds.
  var duration: Double?
  var bounce: Double?
  var blur: Bool?
  var maxBlurRadius: Double?
  var travel: Double?
  var enterScale: Double?
  var fontSize: Double
  var fontWeight: String?
  var fontFamily: String? = "System"
  var monospacedDigits: Bool = false
  var color: GlowknobRGBA
}

struct GlowknobNumericText: UIViewRepresentable {
  let props: GlowknobNumericTextProps

  func makeUIView(context: Context) -> GlowknobNumericTextHost { GlowknobNumericTextHost() }

  func updateUIView(_ view: GlowknobNumericTextHost, context: Context) {
    GlowknobPerf.count("GlowknobNumericText.update")  // Glowknob: perf probe
    view.apply(props)
  }
}

/// `NumericTextView` without ExpoView: a plain UIView that lays the label out
/// over its bounds. The RN call sites give explicit width/height styles, so
/// Expo's content-size reporting never resizes the view; it is dropped here.
final class GlowknobNumericTextHost: UIView {
  private let label = GlowknobNumericTextLabel()
  private var applied: GlowknobNumericTextProps?
  private var cachedKey: TypographyKey?
  private var cachedTypesetter: GlowknobGlyphTypesetter?

  override init(frame: CGRect) {
    super.init(frame: frame)
    clipsToBounds = false
    isUserInteractionEnabled = false
    isAccessibilityElement = false
    addSubview(label)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

  override func layoutSubviews() {
    super.layoutSubviews()
    label.frame = bounds
  }

  func apply(_ props: GlowknobNumericTextProps) {
    guard props != applied else { return }
    GlowknobPerf.count("GlowknobNumericText.apply")  // Glowknob: perf probe
    applied = props
    let spec = GlowknobNumericTextSpec(
      text: props.value,
      alignment: GlowknobNumericTextAlignment(propValue: props.alignment),
      direction: GlowknobTransitionDirection(rawValue: props.direction ?? "") ?? .auto,
      decimalSeparator: ".",
      blurEnabled: props.blur ?? true,
      shape: GlowknobTransitionShape(
        blurIntensity: 1,
        maxBlurRadius: props.maxBlurRadius.map { CGFloat(max($0, 0)) } ?? .greatestFiniteMagnitude,
        enterScale: CGFloat(min(max(props.enterScale ?? 0.4, 0), 2)),
        travelRatio: CGFloat(min(max(props.travel ?? 1.0 / 3.0, 0), 3))
      ),
      clipEnabled: true,
      animationsEnabled: props.animated ?? true,
      duration: props.duration.map { $0 / 1000 } ?? GlowknobTransitionSprings.referenceDuration,
      bounce: props.bounce ?? GlowknobTransitionSprings.defaultBounce
    )
    label.update(spec: spec, typesetter: typesetter(props), color: props.color.uiColor)
  }

  private func typesetter(_ props: GlowknobNumericTextProps) -> GlowknobGlyphTypesetter {
    let key = TypographyKey(family: props.fontFamily, size: props.fontSize, weight: props.fontWeight,
                            monospacedDigits: props.monospacedDigits)
    if key == cachedKey, let cachedTypesetter { return cachedTypesetter }
    let font = GlowknobFontResolver.resolve(family: key.family, size: CGFloat(key.size),
                                    weight: GlowknobFontResolver.weight(from: key.weight), italic: false,
                                    monospacedDigits: key.monospacedDigits)
    let typesetter = GlowknobGlyphTypesetter(font: font, letterSpacing: 0)
    cachedKey = key
    cachedTypesetter = typesetter
    return typesetter
  }

  private struct TypographyKey: Equatable {
    let family: String?
    let size: Double
    let weight: String?
    let monospacedDigits: Bool
  }
}
