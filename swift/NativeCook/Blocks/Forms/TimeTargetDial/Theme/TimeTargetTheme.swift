import SwiftUI

extension Color {
    /// `Color(timeTargetHex: 0x00655C)` — same hex values as the React Native StyleSheets.
    init(timeTargetHex hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

extension Font {
    /// Equivalent of the bundled `SF-Pro-Rounded-Bold.otf` – iOS ships SF Pro Rounded as the `.rounded` design.
    static func rounded(_ size: CGFloat, weight: Font.Weight = .bold) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }
}

/// Equivalent of React Native's `TouchableOpacity`.
struct TouchableOpacity: ButtonStyle {
    var activeOpacity: Double = 0.2

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? activeOpacity : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

/// Equivalent of Reanimated's `FadeInUp.duration(ms)` entering animation.
struct FadeInUpModifier: ViewModifier {
    let duration: Double
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 25)
            .onAppear {
                withAnimation(.easeOut(duration: duration)) {
                    shown = true
                }
            }
    }
}

extension View {
    func fadeInUp(duration: Double) -> some View {
        modifier(FadeInUpModifier(duration: duration))
    }
}
