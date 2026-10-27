import SwiftUI

/// Step 5: a luminous blue results card (280 × 152, tilted 4.5°) resting on a frosted
/// card and a dark tray with Risk Overview / Configure Bot, above "Your Trading Co-pilot".
struct TradingBotsResultsScene: View {
    let reducedMotion: Bool
    let configure: () -> Void
    let overview: () -> Void
    @State private var on = false
    @State private var settled = false
    @State private var amount = 1_022.11

    var body: some View {
        ZStack {
            // The reference lifts the card out of its tray in two moves: it rises flat
            // with the tray (0 – 0.6 s), then keeps climbing as it tips into its pose.
            tray
                .opacity(on ? 1 : 0)
                .offset(y: on || reducedMotion ? 0 : 24)
                .animation(reducedMotion ? .easeOut(duration: 0.2) : .spring(response: 0.6, dampingFraction: 0.92), value: on)
                .position(x: 200, y: 401)

            ghostCard
                .rotationEffect(.degrees(settled || reducedMotion ? -5 : 0))
                .offset(y: settled || reducedMotion ? 0 : 40)
                .opacity(settled || reducedMotion ? 1 : 0)
                .animation(reducedMotion ? nil : .timingCurve(0.4, 0, 0.2, 1, duration: 1.0), value: settled)
                .position(x: 204, y: 344)

            resultCard
                .rotation3DEffect(.degrees(settled || reducedMotion ? 7 : 0), axis: (x: 1, y: 0.25, z: 0), perspective: 0.6)
                .rotationEffect(.degrees(settled || reducedMotion ? 4.5 : 0))
                .scaleEffect(settled || reducedMotion ? 1 : on ? 0.95 : 0.84)
                .offset(y: settled || reducedMotion ? 0 : on ? 78 : 150)
                .opacity(on ? 1 : 0)
                .animation(reducedMotion ? .easeOut(duration: 0.2) : .spring(response: 0.55, dampingFraction: 0.95), value: on)
                .animation(reducedMotion ? nil : .timingCurve(0.4, 0, 0.2, 1, duration: 1.0), value: settled)
                .position(x: 195, y: 303)

            VStack(spacing: -3) {
                HStack(spacing: 7) {
                    Text("Your")
                    TradingBotsMark(size: 33).offset(y: 2)
                        .scaleEffect(on || reducedMotion ? 1 : 0.4).opacity(on ? 1 : 0)
                        .animation(reducedMotion ? nil : .spring(response: 0.45, dampingFraction: 0.6).delay(0.12), value: on)
                    Text("Trading").tbReveal(on, delay: 0.2, y: 4, reduced: reducedMotion)
                }
                .frame(height: 39)
                Text("Co-pilot").tbReveal(on, delay: 0.36, y: 10, blur: 10, reduced: reducedMotion)
            }
            .font(.system(size: 32.8, weight: .medium)).tracking(-0.8).foregroundStyle(TradingBotsPalette.ink)
            .fixedSize().position(x: 201, y: 603.5)

            TradingBotsCaption(text: "Analyze the market, develop smarter strategies, and\ncontrol every trade.")
                .tbReveal(on, delay: 0.42, y: 6, blur: 4, reduced: reducedMotion)
                .position(x: 201, y: 681.7)
        }
        .frame(width: 402, height: 874)
        .task {
            on = true
            guard !reducedMotion else { settled = true; amount = 1_284.50; return }
            try? await Task.sleep(for: .seconds(0.6))
            settled = true
        }
        .task {
            guard !reducedMotion else { return }
            try? await Task.sleep(for: .seconds(0.05))
            await tradingBotsSettle(from: 1_022.11, to: 1_284.50, fastShare: 0.97, fast: 0.1, slow: 0.4, duration: 2.0) { amount = $0 }
        }
    }

    // The dark base (300 × 278 at x 50, y 262): a dotted tray and two controls.
    private var tray: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 34, style: .continuous).fill(TradingBotsPalette.hex(0x0e0e0e))
            RoundedRectangle(cornerRadius: 34, style: .continuous).strokeBorder(.white.opacity(0.07), lineWidth: 1)
            ZStack {
                RoundedRectangle(cornerRadius: 28, style: .continuous).fill(TradingBotsPalette.hex(0x080808))
                TradingBotsDotField(spacing: 5, opacity: 0.05).clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(.white.opacity(0.04), lineWidth: 1)
            }
            .frame(width: 284, height: 200).offset(y: 6)
            TradingBotsDashboardControls(configure: configure, overview: overview, width: 278, height: 46, fontSize: 9.4)
                .offset(y: 220)
        }
        .frame(width: 300, height: 278)
    }

    private var ghostCard: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(LinearGradient(colors: [TradingBotsPalette.hex(0x5a6070, 0.55), TradingBotsPalette.hex(0x2a2c32, 0.75)], startPoint: .top, endPoint: .bottom))
            RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(.white.opacity(0.12), lineWidth: 1)
            Text("+$1,284.50").font(.system(size: 38, weight: .semibold)).foregroundStyle(.white.opacity(0.25))
                .blur(radius: 3).offset(y: 30)
        }
        .frame(width: 282, height: 152)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
    }

    private var resultCard: some View {
        ZStack {
            LinearGradient(stops: [.init(color: TradingBotsPalette.hex(0x2a62e0), location: 0),
                                   .init(color: TradingBotsPalette.hex(0x2f66e2), location: 0.35),
                                   .init(color: TradingBotsPalette.hex(0x4f78d9), location: 0.62),
                                   .init(color: TradingBotsPalette.hex(0x7690c8), location: 1)],
                           startPoint: .top, endPoint: .bottom)
            RadialGradient(colors: [TradingBotsPalette.hex(0xc8d2ea, 0.28), .clear], center: UnitPoint(x: 0.15, y: 1), startRadius: 0, endRadius: 150)
            RadialGradient(colors: [TradingBotsPalette.hex(0x1d4fff, 0.45), .clear], center: UnitPoint(x: 0.52, y: 0.3), startRadius: 0, endRadius: 60)

            ZStack {
                ForEach(0..<4, id: \.self) { i in
                    Circle().fill(.white.opacity(0.75)).frame(width: 2, height: 2)
                        .offset(x: [0, -28, 30, 22][i], y: [-30, 4, 6, -22][i])
                }
                TradingBotsMark(style: .white, size: 38).shadow(color: .white.opacity(0.35), radius: 10)
            }
            .position(x: 140, y: 40)

            Image(systemName: "square.and.arrow.up").font(.system(size: 14, weight: .medium)).foregroundStyle(.white)
                .frame(width: 33, height: 33)
                .background(TradingBotsPalette.hex(0x0b0b0b), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                .position(x: 250, y: 30)
                .onTapGesture(perform: overview)
                .accessibilityAddTraits(.isButton).accessibilityLabel("View simulated results")

            Text("Simulated Trading Results").font(.system(size: 12.4)).tracking(-0.15).foregroundStyle(.white.opacity(0.88))
                .position(x: 140, y: 83)
            HStack(spacing: 1) {
                Text("+").foregroundStyle(.white.opacity(0.45))
                TradingBotsAmount(value: amount, prefix: "$").foregroundStyle(.white)
            }
            .font(.system(size: 31, weight: .semibold)).tracking(-0.3)
            .fixedSize().position(x: 140, y: 113)
        }
        .frame(width: 280, height: 174)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous)
            .strokeBorder(LinearGradient(colors: [.white.opacity(0.3), .white.opacity(0.05), .white.opacity(0.4)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
        .shadow(color: .black.opacity(0.5), radius: 16, y: 12)
        .accessibilityElement(children: .contain)
    }
}
