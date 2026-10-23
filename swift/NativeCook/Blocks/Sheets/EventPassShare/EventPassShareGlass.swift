import SwiftUI

enum EventPassShareGlassKind {
    case clear
    case prominent
}

/// iOS 26 Liquid Glass with the accepted blur/tint and Reduce Transparency fallbacks.
struct EventPassShareGlassSurface: View {
    var kind: EventPassShareGlassKind
    var radius: Double

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        if reduceTransparency {
            solid
                .clipShape(shape)
                .overlay(shape.strokeBorder(.white.opacity(0.22), lineWidth: 1))
        } else if #available(iOS 26, *) {
            Color.clear
                .glassEffect(glass.interactive(), in: shape)
                .overlay(shape.strokeBorder(rim(opacityScale: 0.6), lineWidth: 1))
        } else {
            ZStack {
                Rectangle().fill(.ultraThinMaterial)
                fallback
            }
            .clipShape(shape)
            .overlay(shape.strokeBorder(rim(opacityScale: 1), lineWidth: 1))
        }
    }

    @available(iOS 26, *)
    private var glass: Glass {
        switch kind {
        case .clear:
            .clear.tint(Color(red: 24 / 255, green: 26 / 255, blue: 30 / 255).opacity(0.28))
        case .prominent:
            .regular.tint(.white.opacity(0.90))
        }
    }

    private var solid: Color {
        switch kind {
        case .clear: Color(red: 46 / 255, green: 45 / 255, blue: 48 / 255)
        case .prominent: Color(red: 250 / 255, green: 250 / 255, blue: 250 / 255)
        }
    }

    private var fallback: Color {
        switch kind {
        case .clear: Color(red: 30 / 255, green: 32 / 255, blue: 36 / 255).opacity(0.46)
        case .prominent: Color(red: 250 / 255, green: 250 / 255, blue: 252 / 255).opacity(0.94)
        }
    }

    private func rim(opacityScale: Double) -> LinearGradient {
        let stops: [Gradient.Stop]
        switch kind {
        case .clear:
            stops = [
                .init(color: .white.opacity(0.50 * opacityScale), location: 0),
                .init(color: Color(red: 174 / 255, green: 235 / 255, blue: 1).opacity(0.08 * opacityScale), location: 0.55),
                .init(color: Color(red: 1, green: 194 / 255, blue: 230 / 255).opacity(0.22 * opacityScale), location: 1),
            ]
        case .prominent:
            stops = [
                .init(color: .white.opacity(opacityScale), location: 0),
                .init(color: Color(red: 189 / 255, green: 239 / 255, blue: 1).opacity(0.35 * opacityScale), location: 0.55),
                .init(color: Color(red: 1, green: 210 / 255, blue: 234 / 255).opacity(0.70 * opacityScale), location: 1),
            ]
        }
        return LinearGradient(stops: stops, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}
