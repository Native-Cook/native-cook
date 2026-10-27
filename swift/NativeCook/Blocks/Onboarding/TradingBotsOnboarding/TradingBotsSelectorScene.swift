import SwiftUI

/// Step 2: "Choose Your Trading" with strategy chips and a carousel of bot cards
/// (241.7 × 300 at x 79, y 328; neighbours 230 pt away at 72 %).
struct TradingBotsSelectorScene: View {
    @Binding var selection: Int
    let reducedMotion: Bool
    @State private var on = false
    private let names = ["RiskGuard", "@ScalpX", "TrendHunter"]
    private let labels = ["Market Analysis", "Trading", "AI Signals"]

    var body: some View {
        ZStack {
            VStack(alignment: .leading, spacing: 0) {
                Text("Choose Your").frame(height: 39, alignment: .center).tbWipe(on, delay: 0.0, reduced: reducedMotion)
                HStack(spacing: 7.5) {
                    Text("Trading")
                    TradingBotsMark(size: 33).offset(y: 3)
                        .scaleEffect(on || reducedMotion ? 1 : 0.4).opacity(on ? 1 : 0)
                        .animation(reducedMotion ? nil : .spring(response: 0.45, dampingFraction: 0.6).delay(0.32), value: on)
                }
                .frame(height: 39).tbWipe(on, delay: 0.16, reduced: reducedMotion)
            }
            .font(.system(size: 32.8, weight: .medium)).tracking(-0.8).foregroundStyle(TradingBotsPalette.ink)
            .fixedSize().frame(width: 300, alignment: .leading)
            .position(x: 19.3 + 150, y: 181.2)

            HStack(spacing: 8.3) {
                chip("Trading", bot: 1, width: 94.7) { TradingBotsCandles().frame(width: 16, height: 15) }
                chip("Market Analysis", bot: 0, width: 137.7) { TradingBotsBellCurve().stroke(TradingBotsPalette.hex(0x9a9a9a), lineWidth: 1.3).frame(width: 15, height: 14) }
                chip("AI Signals", bot: 2, width: 104) { TradingBotsBars().stroke(TradingBotsPalette.hex(0x9a9a9a), lineWidth: 1.3).frame(width: 14, height: 14) }
            }
            .tbReveal(on, delay: 0.32, y: 0, blur: 4, reduced: reducedMotion)
            .position(x: 19.3 + 177, y: 256.7)

            carousel
                .offset(x: on || reducedMotion ? 0 : 58)
                .opacity(on ? 1 : 0)
                .animation(reducedMotion ? .easeOut(duration: 0.2) : .spring(response: 0.7, dampingFraction: 0.92), value: on)
                .position(x: 201, y: 478)

            Text(names[selection]).font(.system(size: 15.2)).tracking(-0.2).foregroundStyle(TradingBotsPalette.hex(0xb4b4b4))
                .contentTransition(.opacity)
                .tbReveal(on, delay: 0.45, y: 4, blur: 4, reduced: reducedMotion)
                .position(x: 200, y: 650.7)
            HStack(spacing: 4) {
                ForEach(0..<3, id: \.self) { bot in
                    Capsule().fill(bot == selection ? TradingBotsPalette.hex(0xdcdcdc) : TradingBotsPalette.hex(0x343434))
                        .frame(width: bot == selection ? 14 : 8, height: 5)
                }
            }
            .animation(reducedMotion ? nil : .spring(response: 0.4, dampingFraction: 0.85), value: selection)
            .tbReveal(on, delay: 0.5, y: 0, blur: 0, reduced: reducedMotion)
            .position(x: 200.2, y: 690.3).accessibilityHidden(true)
            Text("Pick your strategy. Let AI handle the analysis").font(.system(size: 14.4)).tracking(-0.15)
                .foregroundStyle(TradingBotsPalette.hex(0x7a7a7a)).fixedSize()
                .tbReveal(on, delay: 0.5, y: 4, blur: 4, reduced: reducedMotion)
                .position(x: 200, y: 739.3)
        }
        .frame(width: 402, height: 874)
        .task { on = true }
    }

    private var carousel: some View {
        ZStack {
            ForEach(0..<3, id: \.self) { bot in
                let offset = position(of: bot)
                Button { choose(bot) } label: {
                    TradingBotsSelectorCard(bot: bot, label: labels[bot], active: bot == selection, on: on, reduced: reducedMotion)
                }
                .buttonStyle(TradingBotsPressStyle())
                .accessibilityLabel("Select \(names[bot])").accessibilityValue(bot == selection ? "Selected" : "")
                .scaleEffect(offset == 0 ? 1 : 0.72)
                .offset(x: CGFloat(offset) * 230, y: offset == 0 ? 0 : 3.7)
                .zIndex(offset == 0 ? 2 : 1)
            }
        }
        .frame(width: 402, height: 320)
        .contentShape(Rectangle())
        .highPriorityGesture(DragGesture(minimumDistance: 20).onEnded { drag in
            guard abs(drag.translation.width) > 30 else { return }
            choose((selection + (drag.translation.width < 0 ? 1 : 2)) % 3)
        })
    }

    /// -1 left, 0 centre, 1 right, so the three cards wrap around the selection.
    private func position(of bot: Int) -> Int {
        let raw = (bot - selection + 3) % 3
        return raw == 2 ? -1 : raw
    }

    private func choose(_ bot: Int) {
        withAnimation(reducedMotion ? nil : .spring(response: 0.5, dampingFraction: 0.86)) { selection = bot }
    }

    private func chip<Icon: View>(_ title: String, bot: Int, width: CGFloat, @ViewBuilder icon: () -> Icon) -> some View {
        Button { choose(bot) } label: {
            HStack(spacing: 9) {
                icon().scaleEffect(0.85)
                Text(title).font(.system(size: 13)).tracking(-0.2)
            }
            .foregroundStyle(TradingBotsPalette.hex(selection == bot ? 0xb0b0b0 : 0x9a9a9a))
            .frame(width: width, height: 34.7)
            .background(TradingBotsPalette.hex(selection == bot ? 0x151515 : 0x101010), in: Capsule())
        }
        .buttonStyle(TradingBotsPressStyle())
        .accessibilityAddTraits(selection == bot ? .isSelected : [])
    }
}

/// One bot in the carousel: an outer 241.7 × 300 shell, a dotted screen with the
/// character, and a label pill.
private struct TradingBotsSelectorCard: View {
    let bot: Int
    let label: String
    let active: Bool
    let on: Bool
    let reduced: Bool
    var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 40, style: .continuous)
                .fill(LinearGradient(colors: [TradingBotsPalette.hex(0x1a1a1a), TradingBotsPalette.hex(0x141414)], startPoint: .top, endPoint: .bottom))
            RoundedRectangle(cornerRadius: 40, style: .continuous)
                .strokeBorder(LinearGradient(colors: [.white.opacity(0.07), .white.opacity(0.02)], startPoint: .top, endPoint: .bottom), lineWidth: 1)
            ZStack {
                RoundedRectangle(cornerRadius: 31, style: .continuous).fill(TradingBotsPalette.hex(0x0e0e0e))
                TradingBotsDotField(spacing: 5, opacity: 0.045).clipShape(RoundedRectangle(cornerRadius: 31, style: .continuous))
                RoundedRectangle(cornerRadius: 31, style: .continuous).strokeBorder(.white.opacity(0.03), lineWidth: 1)
                character
            }
            .frame(width: 223.3, height: 213).offset(y: 9)
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous).fill(TradingBotsPalette.hex(0x1a1a1a))
                    TradingBotsCandles().frame(width: 18, height: 17).foregroundStyle(TradingBotsPalette.hex(0x9a9a9a))
                }
                .frame(width: 32.7, height: 31.3)
                Text(label).font(.system(size: 12.5)).tracking(-0.15).foregroundStyle(TradingBotsPalette.hex(0x8a8a8a))
                    .fixedSize().opacity(active ? 1 : 0).accessibilityHidden(!active)
            }
            .padding(.leading, 2).padding(.trailing, 11)
            .frame(height: 37.3)
            .background(TradingBotsPalette.hex(0x0b0b0b), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous).strokeBorder(.white.opacity(0.06), lineWidth: 1))
            .tbReveal(on, delay: 0.5, y: 4, blur: 4, reduced: reduced)
            .offset(y: 238.7)
        }
        .frame(width: 241.7, height: 300)
        .shadow(color: .black.opacity(0.6), radius: 18, y: 10)
    }

    @ViewBuilder private var character: some View {
        if bot == 1 {
            TradingBotsLivingOrb(reduced: reduced).frame(width: 150, height: 150).offset(x: 1.5, y: -2)
                .opacity(active ? 1 : 0.3).saturation(active ? 1 : 0.25)
        } else {
            TradingBotsCharacter(kind: bot == 0 ? .green : .red, size: bot == 0 ? 128 : 112)
                .opacity(active ? 1 : 0.3).saturation(active ? 1 : 0.25)
        }
    }
}

/// The selector's orb with its idle life: the shell's colour turns, the eyes wander and blink.
private struct TradingBotsLivingOrb: View {
    let reduced: Bool
    @State private var start = Date()
    var body: some View {
        TimelineView(.animation(paused: reduced)) { timeline in
            let t = reduced ? 0 : timeline.date.timeIntervalSince(start)
            let cycle = (t + 2.3).truncatingRemainder(dividingBy: 3.4)
            let blink = cycle < 0.18 ? sin(cycle / 0.18 * .pi) : 0
            ZStack {
                Circle().fill(Color.black.opacity(0.35)).frame(width: 143, height: 143)
                Circle().strokeBorder(.white.opacity(0.2), lineWidth: 2).blur(radius: 2.5).frame(width: 142, height: 142)
                TradingBotsCharacter(kind: .orb, size: 136, phase: (0.7 - t * 0.45).truncatingRemainder(dividingBy: 2 * .pi),
                                     look: CGSize(width: 0.02 * sin(t * 0.9), height: 0.012 * sin(t * 0.7 + 1)), blink: blink)
            }
        }
    }
}

// MARK: - Line icons

struct TradingBotsCandles: View {
    var body: some View {
        Canvas { context, size in
            let w = size.width, h = size.height
            var axis = Path()
            axis.move(to: CGPoint(x: 0.7, y: 0)); axis.addLine(to: CGPoint(x: 0.7, y: h - 0.7)); axis.addLine(to: CGPoint(x: w, y: h - 0.7))
            context.stroke(axis, with: .foreground, style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
            for (x, top, bottom) in [(0.38, 0.12, 0.62), (0.72, 0.0, 0.5)] {
                var wick = Path(); wick.move(to: CGPoint(x: w * x, y: h * top)); wick.addLine(to: CGPoint(x: w * x, y: h * bottom + 0.1 * h))
                context.stroke(wick, with: .foreground, lineWidth: 1.1)
                context.stroke(Path(roundedRect: CGRect(x: w * x - w * 0.11, y: h * (top + 0.15), width: w * 0.22, height: h * 0.32), cornerRadius: 1.2), with: .foreground, lineWidth: 1.2)
            }
        }
    }
}

struct TradingBotsBellCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.move(to: CGPoint(x: rect.minX + 1, y: rect.maxY - 1))
        path.addCurve(to: CGPoint(x: rect.midX + 1, y: rect.minY + rect.height * 0.2),
                      control1: CGPoint(x: rect.minX + rect.width * 0.35, y: rect.maxY - 2), control2: CGPoint(x: rect.midX - 2, y: rect.minY + rect.height * 0.2))
        path.addCurve(to: CGPoint(x: rect.maxX, y: rect.maxY - 1),
                      control1: CGPoint(x: rect.midX + 4, y: rect.minY + rect.height * 0.2), control2: CGPoint(x: rect.maxX - rect.width * 0.25, y: rect.maxY - 1))
        return path
    }
}

struct TradingBotsBars: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width / 3
        path.addRoundedRect(in: CGRect(x: rect.minX, y: rect.minY + rect.height * 0.45, width: w, height: rect.height * 0.55), cornerSize: CGSize(width: 1, height: 1))
        path.addRoundedRect(in: CGRect(x: rect.minX + w, y: rect.minY, width: w, height: rect.height), cornerSize: CGSize(width: 1, height: 1))
        path.addRoundedRect(in: CGRect(x: rect.minX + 2 * w, y: rect.minY + rect.height * 0.3, width: w, height: rect.height * 0.7), cornerSize: CGSize(width: 1, height: 1))
        return path
    }
}

/// Reveals text left to right behind a soft edge, as the reference's headings type in.
struct TradingBotsWipe: ViewModifier {
    let on: Bool
    var delay: Double = 0
    var duration: Double = 0.5
    var reduced = false
    func body(content: Content) -> some View {
        content.mask {
            GeometryReader { geometry in
                LinearGradient(stops: [.init(color: .white, location: 0), .init(color: .white, location: 0.5), .init(color: .clear, location: 0.62)],
                               startPoint: .leading, endPoint: .trailing)
                    .frame(width: geometry.size.width * 3)
                    .offset(x: on || reduced ? 0 : -geometry.size.width * 1.9)
                    .animation(reduced ? nil : .timingCurve(0.3, 0.1, 0.3, 1, duration: duration).delay(delay), value: on)
            }
        }
        .opacity(on ? 1 : 0)
        .animation(.easeOut(duration: reduced ? 0.2 : 0.25).delay(reduced ? 0 : delay), value: on)
    }
}

extension View {
    func tbWipe(_ on: Bool, delay: Double = 0, duration: Double = 0.5, reduced: Bool = false) -> some View {
        modifier(TradingBotsWipe(on: on, delay: delay, duration: duration, reduced: reduced))
    }
}
