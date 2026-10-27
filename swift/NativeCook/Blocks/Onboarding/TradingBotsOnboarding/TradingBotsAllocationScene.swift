import SwiftUI

/// Step 4: the orange TrendHunter card, tilted −5°, over a dark Risk Per Trade card
/// tilted +4.5°, above "Your Bots. / Your Control.".
struct TradingBotsAllocationScene: View {
    @Binding var allocation: Double
    @Binding var risk: TradingBotsFlow.Risk
    @Binding var autoTrading: Bool
    @Binding var maxLoss: Bool
    let reducedMotion: Bool
    @State private var on = false
    @State private var reveal = 0.49

    var body: some View {
        ZStack {
            riskCard
                .rotationEffect(.degrees(4.5))
                .opacity(on ? 1 : 0)
                .offset(y: on || reducedMotion ? 0 : 26)
                .animation(reducedMotion ? .easeOut(duration: 0.2) : .spring(response: 0.6, dampingFraction: 0.9), value: on)
                .position(x: 200, y: 466)

            allocationCard
                .rotationEffect(.degrees(on || reducedMotion ? -5 : -11))
                .rotation3DEffect(.degrees(on || reducedMotion ? 0 : 24), axis: (x: 1, y: 0, z: 0), perspective: 0.5)
                .scaleEffect(on || reducedMotion ? 1 : 1.06)
                .offset(y: on || reducedMotion ? 0 : -46)
                .opacity(on ? 1 : 0)
                .animation(reducedMotion ? .easeOut(duration: 0.2) : .spring(response: 0.72, dampingFraction: 0.82).delay(0.22), value: on)
                .position(x: 198, y: 301)

            TradingBotsHeading(first: "Your Bots.", second: "Your Control.", on: on, reduced: reducedMotion)
                .position(x: 200, y: 603.5)
            TradingBotsCaption(text: "Monitor, configure, and manage your\ntrading agents.")
                .tbReveal(on, delay: 0.38, y: 6, blur: 4, reduced: reducedMotion)
                .position(x: 200, y: 681.7)
        }
        .frame(width: 402, height: 874)
        .task {
            on = true
            guard !reducedMotion else { reveal = 1; return }
            try? await Task.sleep(for: .seconds(0.35))
            await tradingBotsSettle(from: 0.49, to: 1, fastShare: 0.7, fast: 0.12, slow: 0.6, duration: 2.0) { reveal = $0 }
        }
    }

    // MARK: Orange card (318 × 184 frame, 306 × 136 face)

    private var allocationCard: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 31, style: .continuous)
                .fill(LinearGradient(colors: [TradingBotsPalette.hex(0x2e2e2e, 0.92), TradingBotsPalette.hex(0x161616, 0.96)], startPoint: .top, endPoint: .bottom))
            RoundedRectangle(cornerRadius: 31, style: .continuous)
                .strokeBorder(LinearGradient(colors: [.white.opacity(0.22), .white.opacity(0.06), .white.opacity(0.14)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1.2)
            face.frame(width: 306, height: 136).offset(y: 5)
            HStack(spacing: 8) {
                checkPill("Auto-Trading", width: 98, selected: $autoTrading)
                checkPill("Max Daily Loss", width: 104, selected: $maxLoss)
            }
            .offset(x: -4, y: 150)
        }
        .frame(width: 318, height: 184)
        .shadow(color: .black.opacity(0.85), radius: 22, y: 18)
    }

    private var face: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [.init(color: TradingBotsPalette.hex(0xff5c00), location: 0),
                                   .init(color: TradingBotsPalette.hex(0xff7a06), location: 0.45),
                                   .init(color: TradingBotsPalette.hex(0xff9a24), location: 0.75),
                                   .init(color: TradingBotsPalette.hex(0xffc568), location: 1)],
                           startPoint: .top, endPoint: .bottom)
            RadialGradient(colors: [TradingBotsPalette.hex(0xff4a00, 0.55), .clear], center: .topTrailing, startRadius: 0, endRadius: 190)
            RadialGradient(colors: [TradingBotsPalette.hex(0xffe3a8, 0.5), .clear], center: UnitPoint(x: 0.3, y: 1.1), startRadius: 0, endRadius: 150)

            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous).fill(TradingBotsPalette.hex(0x0d0d0d))
                TradingBotsMark(style: .white, size: 38).rotationEffect(.degrees(-8))
            }
            .frame(width: 58, height: 56).position(x: 37, y: 38)

            VStack(alignment: .leading, spacing: 9) {
                Text("TrendHunter").font(.system(size: 15.5, weight: .medium)).foregroundStyle(.white)
                Text("BTC/USDT · Trend Analysis").font(.system(size: 10.7)).foregroundStyle(.white.opacity(0.55))
            }
            .fixedSize().frame(width: 180, alignment: .leading).position(x: 82 + 90, y: 36)

            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous).fill(TradingBotsPalette.hex(0x0d0d0d))
                TradingBotsHexNut().stroke(.white, lineWidth: 1.5).frame(width: 15, height: 16)
            }
            .frame(width: 33, height: 31).position(x: 275, y: 26)

            Text("Capital Allocation").font(.system(size: 15, weight: .medium)).foregroundStyle(.white)
                .fixedSize().frame(width: 180, alignment: .leading).position(x: 15 + 90, y: 86)

            HStack(spacing: 0) {
                TradingBotsAmount(value: allocation * reveal, prefix: "", decimals: 0)
                Text("%")
            }
            .font(.system(size: 12.5, weight: .semibold)).foregroundStyle(.white)
            .frame(width: 33, height: 18)
            .background(.white.opacity(0.2), in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            .position(x: 276, y: 84)

            TradingBotsAllocationMeter(value: allocation * reveal)
                .frame(width: 286, height: 25)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { drag in allocation = min(100, max(0, drag.location.x / 286 * 100)) })
                .accessibilityElement().accessibilityLabel("Capital allocation").accessibilityValue("\(Int(allocation)) percent")
                .accessibilityAdjustableAction { direction in
                    allocation = min(100, max(0, allocation + (direction == .increment ? 5 : -5)))
                }
                .position(x: 16 + 143, y: 110)
        }
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous)
            .strokeBorder(LinearGradient(colors: [.white.opacity(0.35), .white.opacity(0.05), .white.opacity(0.25)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
    }

    private func checkPill(_ title: String, width: CGFloat, selected: Binding<Bool>) -> some View {
        Button { selected.wrappedValue.toggle() } label: {
            HStack(spacing: 7) {
                Image(systemName: "checkmark").font(.system(size: 9, weight: .bold))
                    .foregroundStyle(TradingBotsPalette.hex(0xb4b4b4)).opacity(selected.wrappedValue ? 1 : 0.15)
                Text(title).font(.system(size: 12)).tracking(-0.15).foregroundStyle(TradingBotsPalette.hex(0xe6e6e6))
            }
            .frame(width: width, height: 27)
            .background(TradingBotsPalette.hex(0x1a1a1a), in: Capsule())
            .overlay(Capsule().strokeBorder(.white.opacity(0.07), lineWidth: 1))
        }
        .buttonStyle(TradingBotsPressStyle()).accessibilityValue(selected.wrappedValue ? "On" : "Off")
    }

    // MARK: Risk card (304 × 130)

    private var riskCard: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 28, style: .continuous).fill(TradingBotsPalette.hex(0x040404))
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .strokeBorder(LinearGradient(colors: [TradingBotsPalette.hex(0x3a3a3a), TradingBotsPalette.hex(0x262626)], startPoint: .top, endPoint: .bottom), lineWidth: 3)
            Text("Risk Per Trade").font(.system(size: 13.5)).foregroundStyle(TradingBotsPalette.hex(0x6c6c6c))
                .fixedSize().frame(width: 200, alignment: .leading).position(x: 14 + 100, y: 21)
            Text("Maximum Planned Risk Per Position").font(.system(size: 10.4)).foregroundStyle(TradingBotsPalette.hex(0x2c2c2c))
                .fixedSize().frame(width: 260, alignment: .leading).position(x: 14 + 130, y: 41)
            HStack(spacing: 9) {
                ForEach(TradingBotsFlow.Risk.allCases) { choice in
                    Button { risk = choice } label: {
                        Text(choice.rawValue).font(.system(size: 10.5, weight: .medium)).foregroundStyle(.white)
                            .frame(width: 87, height: 38)
                            .background(TradingBotsPalette.hex(risk == choice ? 0x121212 : 0x050505), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(.white.opacity(risk == choice ? 0.12 : 0.06), lineWidth: 1))
                    }
                    .buttonStyle(TradingBotsPressStyle())
                    .accessibilityLabel("Risk per trade \(choice.rawValue)")
                    .accessibilityAddTraits(risk == choice ? .isSelected : [])
                }
            }
            .position(x: 152, y: 84)
        }
        .frame(width: 304, height: 130)
        .shadow(color: .black.opacity(0.6), radius: 14, y: 10)
    }
}

/// White bars up to the allocation, darker amber bars after it.
private struct TradingBotsAllocationMeter: View, Animatable {
    var value: Double
    var animatableData: Double { get { value } set { value = newValue } }
    var body: some View {
        Canvas { context, size in
            let count = 83
            let pitch = size.width / CGFloat(count)
            let filled = Int((value / 100 * Double(count)).rounded())
            for index in 0..<count {
                let rect = CGRect(x: CGFloat(index) * pitch, y: 0, width: pitch * 0.68, height: size.height)
                let bar = Path(roundedRect: rect, cornerRadius: rect.width / 2)
                if index < filled {
                    context.fill(bar, with: .color(.white))
                } else {
                    context.fill(bar, with: .color(TradingBotsPalette.hex(0x8a3a00, 0.55)))
                }
            }
        }
        .background(alignment: .leading) {
            Capsule().fill(.white.opacity(0.35)).blur(radius: 8)
                .frame(width: 286 * value / 100, height: 22)
        }
    }
}

struct TradingBotsHexNut: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        for i in 0...6 {
            let angle = Double(i) / 6 * .pi * 2 - .pi / 2
            let point = CGPoint(x: rect.midX + cos(angle) * rect.width / 2, y: rect.midY + sin(angle) * rect.height / 2)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.addEllipse(in: CGRect(x: rect.midX - rect.width * 0.17, y: rect.midY - rect.width * 0.17, width: rect.width * 0.34, height: rect.width * 0.34))
        return path
    }
}
