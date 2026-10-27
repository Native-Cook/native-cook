import SwiftUI

/// Step 6: three frosted bot cards (182 × 236). The front one stands at x 121…303,
/// y 159; the other two lean back to the upper left and lower right.
struct TradingBotsProScene: View {
    @Binding var focus: Int
    let reducedMotion: Bool
    @State private var on = false

    private struct Slot { let x, y, angle, scale, opacity, blur: CGFloat; let z: Double }
    private let front = Slot(x: 212, y: 277, angle: 0, scale: 1, opacity: 1, blur: 0, z: 3)
    private let backLeft = Slot(x: 103, y: 220, angle: -9, scale: 0.8, opacity: 0.48, blur: 1.3, z: 1)
    private let backRight = Slot(x: 304, y: 323, angle: 10, scale: 0.8, opacity: 0.5, blur: 1.3, z: 2)

    var body: some View {
        ZStack {
            ForEach(0..<3, id: \.self) { bot in
                let slot = slot(for: bot)
                let entered = on || reducedMotion
                Button { select(bot) } label: { TradingBotsProCard(kind: bot, front: focus == bot) }
                    .buttonStyle(TradingBotsPressStyle())
                    .accessibilityLabel("Focus \(TradingBotsProCard.names[bot])")
                    .accessibilityValue(focus == bot ? "Selected" : "Background")
                    .blur(radius: slot.blur)
                    .scaleEffect(entered ? slot.scale : (focus == bot ? 0.32 : 0.5))
                    .rotationEffect(.degrees(entered ? slot.angle : 0))
                    .opacity(on ? slot.opacity : 0)
                    .position(x: entered ? slot.x : front.x, y: entered ? slot.y : 250)
                    .zIndex(slot.z)
                    .animation(reducedMotion ? .easeOut(duration: 0.2) : .spring(response: 0.7, dampingFraction: 0.84).delay(focus == bot ? 0 : 0.14), value: on)
            }

            VStack(spacing: 0) {
                HStack(spacing: 5) {
                    TradingBotsProBadge()
                    Text("Bots").font(.system(size: 12, weight: .semibold)).foregroundStyle(.white)
                }
                .scaleEffect(on || reducedMotion ? 1 : 0.6).opacity(on ? 1 : 0)
                .animation(reducedMotion ? nil : .spring(response: 0.45, dampingFraction: 0.65).delay(0.08), value: on)
                .offset(x: -0.5)
                .padding(.bottom, 8)
                HStack(spacing: 0) {
                    Text("More B")
                    TradingBotsMark(style: .outline, size: 20.5).padding(.horizontal, 1.2).offset(y: 3.2)
                    Text("ts")
                }
                .accessibilityElement(children: .ignore).accessibilityLabel("More Bots")
                .tbWipe(on, delay: 0.18, reduced: reducedMotion)
                Text("More Possibilities.").tbReveal(on, delay: 0.34, y: 8, blur: 10, reduced: reducedMotion)
                Text("Access more bots and trading strategies.")
                    .font(.system(size: 14.2)).tracking(-0.15).foregroundStyle(TradingBotsPalette.hex(0x7c7c7c))
                    .padding(.top, 8)
                    .tbReveal(on, delay: 0.45, y: 4, blur: 4, reduced: reducedMotion)
            }
            .font(.system(size: 32.3, weight: .regular)).tracking(-0.6).foregroundStyle(TradingBotsPalette.ink)
            .fixedSize().position(x: 200.5, y: 523)

            plan
                .tbReveal(on, delay: 0.0, y: 16, blur: 0, duration: 0.6, reduced: reducedMotion)
                .position(x: 200.5, y: 668.8)
        }
        .frame(width: 402, height: 874)
        .task { on = true }
    }

    private func slot(for bot: Int) -> Slot {
        if bot == focus {
            let anchors: [CGPoint] = [CGPoint(x: 207, y: 276), CGPoint(x: 215, y: 270), CGPoint(x: 211, y: 272)]
            return Slot(x: anchors[bot].x, y: anchors[bot].y, angle: 0, scale: 1, opacity: 1, blur: 0, z: 3)
        }
        return bot == (focus + 2) % 3 ? backLeft : backRight
    }

    private func select(_ bot: Int) {
        withAnimation(reducedMotion ? nil : .spring(response: 0.7, dampingFraction: 0.86)) { focus = bot }
    }

    private var plan: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 22, style: .continuous).fill(TradingBotsPalette.hex(0x161616))
            RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.06), lineWidth: 1)
            VStack(alignment: .leading, spacing: 7) {
                Text("Pro").font(.system(size: 15.2, weight: .medium)).foregroundStyle(.white)
                Text("Access to all AI trading bots").font(.system(size: 11.4)).tracking(-0.1).foregroundStyle(TradingBotsPalette.hex(0x6c6c6c))
            }
            .frame(width: 310, alignment: .leading).offset(x: -1)
            HStack(spacing: 0) {
                Text("$9.99").foregroundStyle(.white)
                Text(" /Mo").foregroundStyle(TradingBotsPalette.hex(0x6c6c6c))
            }
            .font(.system(size: 16.3, weight: .medium)).tracking(-0.3)
            .frame(width: 310, alignment: .trailing).offset(x: 1)
        }
        .frame(width: 342.4, height: 72.3)
        .accessibilityElement(children: .combine)
    }
}

private struct TradingBotsProBadge: View {
    var body: some View {
        HStack(spacing: 2) {
            Image(systemName: "diamond.fill").font(.system(size: 9, weight: .bold)).rotationEffect(.degrees(0))
                .scaleEffect(x: 1.25, y: 1)
            Text("PRO").font(.system(size: 14, weight: .medium)).tracking(-0.3)
        }
        .foregroundStyle(TradingBotsPalette.hex(0x141414))
        .frame(width: 46, height: 19.3)
        .background(LinearGradient(colors: [TradingBotsPalette.hex(0xeec6dc), TradingBotsPalette.hex(0xf4d9c6)], startPoint: .leading, endPoint: .trailing), in: Capsule())
    }
}

private struct TradingBotsProCard: View {
    static let names = ["@ScalpX", "RiskGuard", "TrendHunter"]
    let kind: Int
    let front: Bool
    var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 30, style: .continuous).fill(.ultraThinMaterial)
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(LinearGradient(colors: [.white.opacity(0.07), .white.opacity(0.13)], startPoint: .top, endPoint: .bottom))
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .strokeBorder(LinearGradient(colors: [.white.opacity(0.22), .white.opacity(0.06), .white.opacity(0.14)], startPoint: .top, endPoint: .bottom), lineWidth: 1.2)
            screen.frame(width: 172, height: 160).offset(y: 6)
            Text(Self.names[kind]).font(.system(size: kind == 0 ? 12.5 : 11.6, weight: .semibold)).foregroundStyle(.white)
                .offset(y: 177)
            Text(kind == 0 ? "Fast Trade Detection" : "Analyzes short-term price\nmovements for quick trade setups.")
                .font(.system(size: kind == 0 ? 10.3 : 9.5)).tracking(-0.1).lineSpacing(-1)
                .multilineTextAlignment(.center).foregroundStyle(TradingBotsPalette.hex(0x7a7a7a))
                .fixedSize()
                .offset(y: kind == 0 ? 201 : 194)
        }
        .frame(width: 182, height: 236)
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .shadow(color: .black.opacity(0.6), radius: 20, y: 14)
        .contentShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
    }

    private var screen: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 26, style: .continuous).fill(TradingBotsPalette.hex(0x0b0b0b))
            TradingBotsDotField(spacing: 5, opacity: 0.05).clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            character.frame(width: 172, height: 160)
            if kind == 0 {
                ZStack {
                    RoundedRectangle(cornerRadius: 9, style: .continuous).fill(.white.opacity(0.05))
                    RoundedRectangle(cornerRadius: 9, style: .continuous).strokeBorder(.white.opacity(0.16), lineWidth: 1)
                    Image(systemName: "lock").font(.system(size: 12, weight: .regular)).foregroundStyle(.white)
                }
                .frame(width: 31, height: 32).offset(x: 12, y: 8)
            }
        }
    }

    /// Milky light over the upper left, so the gummy reads frosted like the reference.
    private func frost(_ amount: Double, size: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
            .fill(LinearGradient(colors: [.white.opacity(amount), .white.opacity(amount * 0.25), .clear], startPoint: .topLeading, endPoint: UnitPoint(x: 0.7, y: 0.75)))
            .frame(width: size * 0.9, height: size * 0.9).blendMode(.screen).allowsHitTesting(false)
    }

    @ViewBuilder private var character: some View {
        switch kind {
        case 0: TradingBotsCharacter(kind: .purple, size: 100).saturation(0.82).overlay(frost(0.4, size: 100))
        case 1: TradingBotsCharacter(kind: .green, size: 101).saturation(0.8).brightness(-0.04).offset(x: 2, y: -2)
        default: TradingBotsCharacter(kind: .red, size: 90)
        }
    }
}
