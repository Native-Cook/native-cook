import SwiftUI
import UIKit

// The small amount of chrome Flare shares with the other showcase specimens
// (`ui.tsx`): a quiet instruction line, a graphite pill button, and how RN's
// <Text> sizes the system face.

/// RN's iOS text scaling (`allowFontScaling`, on by default): font sizes and
/// line heights are multiplied by `RCTFontSizeMultiplier()` for the current
/// content size category; letter spacing is not. 1 at the default (Large).
func flareRnFontSizeMultiplier(_ size: DynamicTypeSize) -> Double {
  switch size {
  case .xSmall: 0.823
  case .small: 0.882
  case .medium: 0.941
  case .large: 1.0
  case .xLarge: 1.118
  case .xxLarge: 1.235
  case .xxxLarge: 1.353
  case .accessibility1: 1.786
  case .accessibility2: 2.143
  case .accessibility3: 2.643
  case .accessibility4: 3.143
  case .accessibility5: 3.571
  @unknown default: 1.0
  }
}

/// An RN `<Text>` style on the system face (`fontFamily: "System"`).
struct FlareRNTextStyle {
  var size: Double
  var weight: UIFont.Weight = .regular
  /// nil: the font's own line height.
  var lineHeight: Double?
  var letterSpacing: Double = 0

  var swiftUIWeight: Font.Weight {
    switch weight {
    case .bold: .bold
    case .medium: .medium
    case .semibold: .semibold
    default: .regular
    }
  }
}

/// One RN `<Text>` (single line unless it has to wrap). RN sets a line
/// height as min = max line height and, when it is taller than the font's,
/// shifts the baseline by half the difference, so the glyphs sit centered in
/// the line box. When it is shorter (the 22/26 title) no shift is applied
/// and the line keeps its descent at the bottom.
struct FlareRNText: View {
  let text: String
  let style: FlareRNTextStyle
  let color: Color
  var alignment: TextAlignment = .leading
  @Environment(\.dynamicTypeSize) private var dynamicType

  var body: some View {
    let m = flareRnFontSizeMultiplier(dynamicType)
    let size = style.size * m
    let font = UIFont.systemFont(ofSize: size, weight: style.weight)
    let base = Text(text)
      .font(.system(size: size, weight: style.swiftUIWeight))
      .kerning(style.letterSpacing)
      .foregroundStyle(color)
    if let lh = style.lineHeight.map({ $0 * m }) {
      let extra = lh - font.lineHeight
      if extra >= 0 {
        base
          .lineSpacing(extra)
          .multilineTextAlignment(alignment)
          .padding(.vertical, extra / 2)
      } else {
        base
          .lineLimit(1)
          .frame(height: lh, alignment: .bottom)
      }
    } else {
      base.multilineTextAlignment(alignment)
    }
  }
}

/// `Hint`: a quiet centered line, 20 in from each side.
struct FlareHint: View {
  let text: String
  let color: FlareRGBA

  var body: some View {
    FlareRNText(text: text, style: FlareRNTextStyle(size: 13, lineHeight: 18), color: color.color, alignment: .center)
      .frame(maxWidth: .infinity)
      .padding(.horizontal, 20)
      .allowsHitTesting(false)
  }
}

/// `Pill` from `ui.tsx`: the graphite "Deliver again" button. RN Pressable:
/// pressed (scale 0.96, instant) from touch-down, released when the finger
/// leaves the press rect (pressRetentionOffset 20 / 20 / 20 / 30), at least
/// 130 ms long (minPressDuration); onPress on a release inside the rect.
struct FlareReplayPill: View {
  let label: String
  let onPress: () -> Void

  @State private var pressed = false
  @State private var press = FlarePressTracker()

  var body: some View {
    HStack(spacing: 7) {
      FlareSymbolImage(name: "arrow.counterclockwise", tint: FlareTone.onGraphite.uiColor)
        .frame(width: 13, height: 13)
        .accessibilityHidden(true)
      FlareRNText(text: label, style: FlareRNTextStyle(size: 13, weight: .medium), color: FlareTone.onGraphite.color)
        .lineLimit(1)
        .fixedSize()
    }
    .padding(.horizontal, 15)
    .frame(minHeight: 36)
    .background(Capsule().fill(FlareTone.graphite.color))
    .scaleEffect(pressed ? 0.96 : 1)
    .overlay(
      GeometryReader { geo in
        Color.clear
          .contentShape(Rectangle())
          .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .local)
              .onChanged { value in
                press.moved(to: value.location, in: geo.size, setPressed: setPressed)
              }
              .onEnded { value in
                if press.ended(at: value.location, in: geo.size, setPressed: setPressed) { onPress() }
              }
          )
      }
    )
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(label)
    .accessibilityAddTraits(.isButton)
    .accessibilityAction { onPress() }
  }

  private func setPressed(_ on: Bool) { pressed = on }
}

/// RN Pressability's press-in / press-out / press decisions.
@MainActor
final class FlarePressTracker {
  static let minPressDuration = 0.130
  /// pressRetentionOffset: top, left, right, bottom.
  static let retention = (top: 20.0, left: 20.0, right: 20.0, bottom: 30.0)

  private var tracking = false
  private var inside = false
  private var pressedAt: CFTimeInterval = 0
  private var release: DispatchWorkItem?

  static func inPressRect(_ p: CGPoint, size: CGSize) -> Bool {
    p.x >= -retention.left && p.x <= size.width + retention.right
      && p.y >= -retention.top && p.y <= size.height + retention.bottom
  }

  func moved(to p: CGPoint, in size: CGSize, setPressed: @escaping (Bool) -> Void) {
    if !tracking {
      tracking = true
      inside = true
      pressIn(setPressed)
      return
    }
    let now = Self.inPressRect(p, size: size)
    if now != inside {
      inside = now
      if now { pressIn(setPressed) } else { pressOut(setPressed) }
    }
  }

  /// Returns whether this release is a press.
  func ended(at p: CGPoint, in size: CGSize, setPressed: @escaping (Bool) -> Void) -> Bool {
    guard tracking else { return false }
    tracking = false
    let wasInside = inside && Self.inPressRect(p, size: size)
    if inside {
      inside = false
      pressOut(setPressed)
    }
    return wasInside
  }

  private func pressIn(_ setPressed: (Bool) -> Void) {
    release?.cancel()
    release = nil
    pressedAt = CACurrentMediaTime()
    setPressed(true)
  }

  private func pressOut(_ setPressed: @escaping (Bool) -> Void) {
    let wait = Self.minPressDuration - (CACurrentMediaTime() - pressedAt)
    if wait > 0 {
      let item = DispatchWorkItem { MainActor.assumeIsolated { setPressed(false) } }
      release = item
      DispatchQueue.main.asyncAfter(deadline: .now() + wait, execute: item)
    } else {
      setPressed(false)
    }
  }
}

/// expo-symbols' `SymbolView` on iOS: a UIImageView filling the `size` box
/// (13 × 13 here), aspect-fit, with the symbol at the system font size
/// (17 pt), regular weight, monochrome, tinted.
struct FlareSymbolImage: UIViewRepresentable {
  let name: String
  let tint: UIColor

  func makeUIView(context: Context) -> UIImageView {
    let view = UIImageView()
    view.isUserInteractionEnabled = false
    view.isAccessibilityElement = false
    view.contentMode = .scaleAspectFit
    view.setContentHuggingPriority(.defaultLow, for: .horizontal)
    view.setContentHuggingPriority(.defaultLow, for: .vertical)
    view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
    view.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
    if let image = UIImage(systemName: name) {
      view.image = image.withTintColor(tint, renderingMode: .alwaysOriginal)
    }
    view.preferredSymbolConfiguration = UIImage.SymbolConfiguration(
      pointSize: UIFont.systemFontSize, weight: .regular, scale: .unspecified
    ).applying(UIImage.SymbolConfiguration.preferringMonochrome())
    return view
  }

  func updateUIView(_ view: UIImageView, context: Context) {}
}
