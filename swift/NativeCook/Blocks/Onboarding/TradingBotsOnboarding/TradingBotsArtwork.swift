import SwiftUI

/// Every position in this block is in points on a 402 × 874 canvas, the iPhone 17/18 Pro
/// screen the reference video was captured on. Other screens scale the canvas to fit.
enum TradingBotsPalette {
    static let ink = Color(white: 0.985)
    static let caption = Color(red: 0.54, green: 0.54, blue: 0.55)
    static let muted = Color(white: 0.48)
    static let ctaText = Color(white: 0.23)
    static let blue = Color(red: 0.24, green: 0.45, blue: 1)
    static func hex(_ value: UInt32, _ opacity: Double = 1) -> Color {
        Color(red: Double((value >> 16) & 0xff) / 255, green: Double((value >> 8) & 0xff) / 255,
              blue: Double(value & 0xff) / 255, opacity: opacity)
    }
}

/// Fades, lifts and unblurs a piece of a scene when it enters, like the reference's
/// staggered reveals. Reduced motion keeps only a short fade.
struct TradingBotsReveal: ViewModifier {
    let on: Bool
    var delay: Double = 0
    var y: CGFloat = 12
    var blur: CGFloat = 8
    var duration: Double = 0.55
    var reduced = false
    func body(content: Content) -> some View {
        content
            .opacity(on ? 1 : 0)
            .blur(radius: on || reduced ? 0 : blur)
            .offset(y: on || reduced ? 0 : y)
            .animation(reduced ? .easeOut(duration: 0.2) : .timingCurve(0.2, 0.7, 0.25, 1, duration: duration).delay(delay), value: on)
    }
}

extension View {
    func tbReveal(_ on: Bool, delay: Double = 0, y: CGFloat = 12, blur: CGFloat = 8, duration: Double = 0.55, reduced: Bool = false) -> some View {
        modifier(TradingBotsReveal(on: on, delay: delay, y: y, blur: blur, duration: duration, reduced: reduced))
    }
}

/// Two-line centred heading used by four of the six scenes (SF Pro Medium 32).
struct TradingBotsHeading: View {
    let first: String
    let second: String
    var on = true
    var reduced = false
    var body: some View {
        VStack(spacing: -3) {
            Text(first).tbReveal(on, delay: 0.05, y: 8, reduced: reduced)
            Text(second).tbReveal(on, delay: 0.22, y: 10, blur: 10, reduced: reduced)
        }
        .font(.system(size: 32.8, weight: .medium)).tracking(-0.8)
        .foregroundStyle(TradingBotsPalette.ink)
        .fixedSize()
    }
}

struct TradingBotsCaption: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 14.4)).tracking(-0.15).lineSpacing(2.8)
            .foregroundStyle(TradingBotsPalette.caption).multilineTextAlignment(.center)
            .fixedSize()
    }
}

struct TradingBotsAmount: View, Animatable {
    var value: Double
    var prefix = "+$"
    var decimals = 2
    var animatableData: Double { get { value } set { value = newValue } }
    var body: some View {
        Text(prefix + value.formatted(.number.locale(Locale(identifier: "en_US")).precision(.fractionLength(decimals))))
            .monospacedDigit()
    }
}

// MARK: - Controls

struct TradingBotsPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.brightness(configuration.isPressed ? -0.1 : 0).scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

/// The big white CTA: a pill with a pale lower lip, so it reads as a raised key.
/// Pressing swells it slightly, as the reference does under the finger.
struct TradingBotsPrimaryButton: View {
    let title: String
    var pro = false
    var dimmed = false
    /// Shows the pressed swell without a touch (the QA walkthrough).
    var pressed = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 27, style: .continuous)
                    .fill(LinearGradient(colors: pro ? [TradingBotsPalette.hex(0xd8c9a8), TradingBotsPalette.hex(0x9fb8cc)]
                                                       : [TradingBotsPalette.hex(0xe4e4e4), TradingBotsPalette.hex(0xcdcdcd)],
                                         startPoint: pro ? .leading : .top, endPoint: pro ? .trailing : .bottom))
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(face)
                    .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous)
                        .strokeBorder(LinearGradient(colors: [.white.opacity(0.9), .white.opacity(0)], startPoint: .top, endPoint: .center), lineWidth: 1))
                    .padding(.bottom, 3)
                HStack(spacing: 14) {
                    Text(title).font(.system(size: pro ? 15.6 : 17, weight: pro ? .bold : .semibold)).tracking(-0.2)
                    if pro { Image(systemName: "chevron.right").font(.system(size: 14, weight: .bold)) }
                }
                .foregroundStyle(pro ? Color.black : TradingBotsPalette.ctaText)
                .padding(.bottom, 2)
            }
            .frame(width: 343, height: 62)
            .overlay(RoundedRectangle(cornerRadius: 27, style: .continuous).fill(.black.opacity(dimmed ? 0.42 : 0)))
            .shadow(color: .white.opacity(pro ? 0.04 : 0.07), radius: 14)
            .scaleEffect(pressed ? 1.06 : 1)
            .shadow(color: .white.opacity(pressed ? 0.22 : 0), radius: 18)
        }
        .buttonStyle(TradingBotsCTAStyle())
    }
    private var face: LinearGradient {
        pro ? LinearGradient(colors: [TradingBotsPalette.hex(0xf1e2c4), TradingBotsPalette.hex(0xdcdcd2), TradingBotsPalette.hex(0xb6cfe2)], startPoint: .leading, endPoint: .trailing)
            : LinearGradient(colors: [TradingBotsPalette.hex(0xfefefe), TradingBotsPalette.hex(0xfdfdfd), TradingBotsPalette.hex(0xf3f3f3)], startPoint: .top, endPoint: .bottom)
    }
}

private struct TradingBotsCTAStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 1.06 : 1)
            .shadow(color: .white.opacity(configuration.isPressed ? 0.22 : 0), radius: 18)
            .animation(.spring(response: 0.28, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

struct TradingBotsBackArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.maxX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
        path.move(to: CGPoint(x: rect.minX + rect.height / 2, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.minX + rect.height / 2, y: rect.maxY))
        return path
    }
}

// MARK: - Marks

/// The scalloped "bot" seal: a soft nine-lobed blob with two slanted eyes.
struct TradingBotsSeal: Shape {
    var lobes = 9
    var depth = 0.045
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let steps = 216
        for i in 0...steps {
            let angle = Double(i) / Double(steps) * .pi * 2 - .pi / 2
            let radius = (0.5 - depth + depth * cos(angle * Double(lobes))) * min(rect.width, rect.height)
            let point = CGPoint(x: rect.midX + cos(angle) * radius, y: rect.midY + sin(angle) * radius)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}

struct TradingBotsMark: View {
    enum Style { case blue, white, outline }
    var style: Style = .blue
    var size: CGFloat = 33
    var body: some View {
        ZStack {
            switch style {
            case .blue:
                TradingBotsSeal().fill(LinearGradient(colors: [TradingBotsPalette.hex(0x2f62ff), TradingBotsPalette.hex(0x5d93ff), TradingBotsPalette.hex(0x87b6ff)], startPoint: .top, endPoint: .bottom))
            case .white:
                TradingBotsSeal().fill(LinearGradient(colors: [.white, Color(white: 0.9)], startPoint: .top, endPoint: .bottom))
            case .outline:
                TradingBotsSeal().fill(Color(white: 0.06))
                TradingBotsSeal().stroke(.white, lineWidth: size * 0.09)
            }
            HStack(spacing: size * (style == .outline ? 0.1 : 0.12)) {
                Capsule().frame(width: size * 0.075, height: size * 0.27)
                Capsule().frame(width: size * 0.075, height: size * 0.27)
            }
            .foregroundStyle(style == .outline ? Color.white : Color(white: 0.08))
            .rotationEffect(.degrees(style == .outline ? 18 : 0))
        }
        .frame(width: size, height: size).accessibilityHidden(true)
    }
}

/// Faint 1 pt dot grid used inside the bot screens and the results tray.
struct TradingBotsDotField: View {
    var spacing: CGFloat = 5
    var opacity: Double = 0.05
    var body: some View {
        Canvas { context, size in
            for x in stride(from: spacing / 2, to: size.width, by: spacing) {
                for y in stride(from: spacing / 2, to: size.height, by: spacing) {
                    context.fill(Path(ellipseIn: CGRect(x: x - 0.45, y: y - 0.45, width: 0.9, height: 0.9)), with: .color(.white.opacity(opacity)))
                }
            }
        }
        .allowsHitTesting(false).accessibilityHidden(true)
    }
}

// MARK: - Characters

/// The bots. The selector's orb is a live fragment shader (`TradingBotsMaterials.metal`);
/// the other bodies are rendered images. Eyes stay vector so they can look around and blink.
struct TradingBotsCharacter: View {
    enum Kind: Int { case orb, green, purple, red, avatar }
    let kind: Kind
    var size: CGFloat = 130
    var phase: Double = 0
    var look: CGSize = .zero
    var blink: Double = 0
    var body: some View {
        ZStack {
            if kind == .orb {
                Rectangle().colorEffect(ShaderLibrary.tradingBotOrb(.float2(Float(size), Float(size)), .float(Float(phase))))
            } else {
                Image(decorative: imageName).resizable().interpolation(.high).scaledToFit()
                if kind == .green {
                    // the reference's ball is milky over its upper left
                    Circle().fill(LinearGradient(colors: [.white.opacity(0.6), .white.opacity(0.18), .clear], startPoint: .topLeading, endPoint: UnitPoint(x: 0.62, y: 0.62)))
                        .padding(size * 0.045).blendMode(.screen)
                }
            }
            eyes.offset(x: look.width * size, y: look.height * size)
        }
        .frame(width: size, height: size).accessibilityHidden(true)
    }
    private var imageName: String {
        switch kind {
        case .green: "TradingBots-bot-green"
        case .purple: "TradingBots-bot-purple"
        case .red: "TradingBots-bot-red"
        default: "TradingBots-bot-avatar"
        }
    }
    @ViewBuilder private var eyes: some View {
        switch kind {
        case .orb:
            HStack(spacing: size * 0.04) { pill(0.135, 0.075, tilt: 4); pill(0.14, 0.075, tilt: -3) }
                .offset(x: -size * 0.065, y: -size * 0.06)
        case .purple:
            HStack(spacing: size * 0.06) { pill(0.19, 0.085, tilt: 0); pill(0.19, 0.085, tilt: 0) }
        case .green:
            HStack(spacing: size * 0.08) { pill(0.085, 0.115, tilt: -4); pill(0.085, 0.115, tilt: 4) }
                .offset(x: -size * 0.025, y: -size * 0.01)
        case .red:
            HStack(spacing: size * 0.07) { pill(0.1, 0.13, tilt: -8); pill(0.1, 0.13, tilt: 8).offset(y: size * 0.03) }
                .offset(x: -size * 0.02, y: size * 0.02)
        case .avatar:
            HStack(spacing: size * 0.09) { pill(0.11, 0.15, tilt: 0); pill(0.11, 0.15, tilt: 0) }
                .offset(x: -size * 0.04)
        }
    }
    private func pill(_ width: CGFloat, _ height: CGFloat, tilt: Double) -> some View {
        Capsule()
            .fill(LinearGradient(colors: [.white, Color(white: 0.93), Color(white: 0.68)], startPoint: .top, endPoint: .bottom))
            .overlay(Capsule().strokeBorder(.white, lineWidth: max(0.8, size * 0.011)))
            .frame(width: size * width, height: max(1, size * height * (1 - 0.88 * blink)))
            .rotationEffect(.degrees(tilt))
            .shadow(color: .white.opacity(0.45), radius: size * 0.012)
            .shadow(color: .black.opacity(0.35), radius: size * 0.01, y: size * 0.006)
    }
}

/// Drives a counter the way the reference's numbers settle: most of the distance in a
/// fast first move (`fastShare` with time constant `fast`), then a long tail (`slow`).
@MainActor func tradingBotsSettle(from start: Double, to target: Double, fastShare: Double, fast: Double, slow: Double,
                                  duration: Double, set: (Double) -> Void) async {
    let began = Date()
    while !Task.isCancelled {
        let t = Date().timeIntervalSince(began)
        if t >= duration { break }
        let remaining = fastShare * exp(-t / fast) + (1 - fastShare) * exp(-t / slow)
        set(target - (target - start) * remaining)
        try? await Task.sleep(for: .milliseconds(16))
    }
    set(target)
}
