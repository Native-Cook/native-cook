import SwiftUI

/// Reveals a layer left to right as it is etched (a finger writing in the frost); it melts away
/// (fades and sinks) on a thaw. The width is measured at runtime by the mask's GeometryReader.
struct PriceTagWelcomeEtch<Content: View>: View {
    let timer: Bool
    let look: PriceTagWelcomeMotion.Look
    let reduced: Bool
    let drop: Double
    @ViewBuilder let content: Content

    var body: some View {
        let window = timer ? look.timer : look.wait
        let reveal = reduced ? 1 : window
        let alpha = reduced ? window : 1 - look.out
        content
            .mask {
                GeometryReader { proxy in
                    Rectangle()
                        .frame(width: proxy.size.width * reveal, height: proxy.size.height)
                        .frame(width: proxy.size.width, height: proxy.size.height, alignment: .leading)
                }
            }
            .offset(y: look.out * drop)
            .opacity(alpha)
    }
}

/// Text with a hard shadow under it, as React Native's textShadow with no blur.
struct PriceTagWelcomeEdgedText: View {
    let text: String
    let size: Double
    let weight: Font.Weight
    let tracking: Double
    let color: Color
    let edge: Color
    let edgeOffset: Double
    var tabular = false
    var digits = false

    var body: some View {
        ZStack {
            label.foregroundStyle(edge).offset(y: edgeOffset)
            label.foregroundStyle(color)
        }
    }

    private var label: some View {
        Text(text)
            .font(.system(size: size, weight: weight).monospacedDigit(tabular))
            .tracking(tracking)
            .fixedSize()
    }
}

private extension Font {
    /// Tabular figures when asked (React Native's fontVariant tabular-nums), else the default ones.
    func monospacedDigit(_ on: Bool) -> Font { on ? monospacedDigit() : self }
}
