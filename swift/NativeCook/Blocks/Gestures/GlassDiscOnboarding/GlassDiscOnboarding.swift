import SwiftUI

/// A glass dome that becomes an onboarding portal when it is pulled upward.
struct GlassDiscOnboarding: View {
    // GlassDiscOnboardingMotion.loopMS / 1000; a literal so the catalog check can read it
    static let loop: Double = 12.5

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model: GlassDiscOnboardingModel?
    @State private var previousFrame: Date?

    var body: some View {
        GeometryReader { geometry in
            let insets = geometry.safeAreaInsets
            let screenSize = CGSize(
                width: geometry.size.width + insets.leading + insets.trailing,
                height: geometry.size.height + insets.top + insets.bottom
            )

            if let model {
                TimelineView(.animation(minimumInterval: 1 / 60, paused: shouldPauseClock(model))) { timeline in
                    Color.clear
                        .onChange(of: timeline.date) { _, date in
                            let delta = date.timeIntervalSince(previousFrame ?? date) * 1_000
                            model.update(
                                wallTimeMS: date.timeIntervalSinceReferenceDate * 1_000,
                                deltaMS: delta
                            )
                            previousFrame = date
                        }
                        .overlay {
                            GlassDiscOnboardingScene(model: model, size: screenSize, reducedMotion: reduceMotion)
                        }
                }
                .frame(width: screenSize.width, height: screenSize.height)
                .offset(x: -insets.leading, y: -insets.top)
                .onChange(of: reduceMotion) { _, value in
                    recreateModel(size: screenSize, reducedMotion: value)
                }
                .onChange(of: screenSize) { _, newSize in
                    recreateModel(size: newSize, reducedMotion: reduceMotion)
                }
                .onChange(of: model.isAnimating) { _, isAnimating in
                    // Any wake (a touch, accessibility action, or sparse ambient glint) begins from a fresh frame.
                    if isAnimating { previousFrame = nil }
                }
            } else {
                GlassDiscOnboardingColor.hex(GlassDiscOnboardingTheme.Color.bgTL)
                    .task { recreateModel(size: screenSize, reducedMotion: reduceMotion) }
            }
        }
        .background(GlassDiscOnboardingColor.hex(GlassDiscOnboardingTheme.Color.bgTL).ignoresSafeArea())
        .preferredColorScheme(.light)
        .statusBarHidden(false)
    }

    private func shouldPauseClock(_ model: GlassDiscOnboardingModel) -> Bool {
        // The model tells us whether an autoplay or a transition is active. An idle manual screen must not redraw.
        !model.isAnimating
    }

    private func recreateModel(size: CGSize, reducedMotion: Bool) {
        guard size.width > 0, size.height > 0 else { return }
        model = GlassDiscOnboardingModel(
            width: size.width,
            height: size.height,
            autoPlay: autoPlay,
            reduceMotion: reducedMotion
        )
        previousFrame = nil
    }
}

enum GlassDiscOnboardingColor {
    static func hex(_ value: String) -> Color {
        let raw = value.drop(while: { $0 == "#" })
        guard let number = UInt64(raw, radix: 16) else { return .clear }
        return Color(
            red: Double((number >> 16) & 0xFF) / 255,
            green: Double((number >> 8) & 0xFF) / 255,
            blue: Double(number & 0xFF) / 255
        )
    }

    static func rgba(_ value: String) -> Color {
        guard value.hasPrefix("rgba("), value.hasSuffix(")") else { return hex(value) }
        let fields = value.dropFirst(5).dropLast().split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard fields.count == 4 else { return .clear }
        return Color(red: fields[0] / 255, green: fields[1] / 255, blue: fields[2] / 255, opacity: fields[3])
    }

    static func opaque(_ value: String) -> Color {
        let color = vector(value)
        return Color(red: Double(color.x), green: Double(color.y), blue: Double(color.z))
    }

    static func vector(_ value: String) -> SIMD4<Float> {
        let color = UIColor(glassDiscOnboardingHex: value)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return SIMD4(Float(red), Float(green), Float(blue), Float(alpha))
    }

    static func shader(_ value: String) -> Shader.Argument {
        let color = vector(value)
        return .float4(color.x, color.y, color.z, color.w)
    }
}

extension UIColor {
    convenience init(glassDiscOnboardingHex hex: String) {
        let raw = hex.drop(while: { $0 == "#" })
        let number = UInt64(raw, radix: 16) ?? 0
        self.init(
            red: CGFloat((number >> 16) & 0xFF) / 255,
            green: CGFloat((number >> 8) & 0xFF) / 255,
            blue: CGFloat(number & 0xFF) / 255,
            alpha: 1
        )
    }
}
