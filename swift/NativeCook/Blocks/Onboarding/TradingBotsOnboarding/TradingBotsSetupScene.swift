import SwiftUI

/// Step 3: a miniature phone (316.7 pt wide at x 41.7, y 196) rises over a field of
/// blurred dashboard labels, above "Ai Bots, / Ready To Go".
struct TradingBotsSetupScene: View {
    let reducedMotion: Bool
    let configure: () -> Void
    let overview: () -> Void
    @State private var on = false

    private let labels: [(String, CGPoint, Double, Bool)] = [
        ("Risk Settings", CGPoint(x: 204, y: 70), 0, false),
        ("Overview", CGPoint(x: 330, y: 86), 5, true),
        ("Configuration", CGPoint(x: 80, y: 104), -6, false),
        ("Win Rate", CGPoint(x: 205, y: 143), 5, false),
        ("Risk Settings", CGPoint(x: 328, y: 151), 5, true),
        ("Risk Settings", CGPoint(x: 78, y: 167), -5, false),
        ("Protected", CGPoint(x: 204, y: 196), 3, false),
        ("Risk Settings", CGPoint(x: 335, y: 205), 4, false),
        ("Overview", CGPoint(x: 64, y: 225), -4, false)
    ]

    var body: some View {
        ZStack {
            ZStack {
                ForEach(labels.indices, id: \.self) { index in
                    let label = labels[index]
                    HStack(spacing: 7) {
                        if label.3 { Image(systemName: "circle.lefthalf.filled").font(.system(size: 15)) }
                        Text(label.0).font(.system(size: 15, weight: .medium))
                    }
                    .padding(.horizontal, label.3 ? 14 : 0).padding(.vertical, label.3 ? 9 : 0)
                    .background(label.3 ? Color.white.opacity(0.025) : .clear, in: Capsule())
                    .rotationEffect(.degrees(label.2))
                    .position(label.1)
                }
            }
            .foregroundStyle(.white.opacity(0.19))
            .blur(radius: 1.0)
            .frame(width: 402, height: 874)
            .opacity(on ? 1 : 0)
            .animation(reducedMotion ? nil : .easeOut(duration: 0.9).delay(0.15), value: on)
            .accessibilityHidden(true)

            TradingBotsMiniPhone(configure: configure, overview: overview)
                .opacity(on ? 1 : 0)
                .offset(y: on || reducedMotion ? 0 : 70)
                .animation(reducedMotion ? .easeOut(duration: 0.2) : .spring(response: 0.8, dampingFraction: 0.92), value: on)
                .position(x: 200, y: 196 + 190)

            VStack(spacing: -3) {
                HStack(spacing: 12) {
                    Image(decorative: "TradingBots-toggle-tile").resizable().interpolation(.high).scaledToFit().frame(width: 45, height: 45).shadow(color: .white.opacity(0.12), radius: 6).accessibilityHidden(true).offset(y: 1)
                        .scaleEffect(on || reducedMotion ? 1 : 0.5).opacity(on ? 1 : 0)
                        .animation(reducedMotion ? nil : .spring(response: 0.45, dampingFraction: 0.62).delay(0.05), value: on)
                    Text("Ai Bots,").tbReveal(on, delay: 0.15, y: 6, reduced: reducedMotion)
                }
                .frame(height: 39)
                .offset(x: -2.5)
                Text("Ready To Go").tbReveal(on, delay: 0.32, y: 10, blur: 10, reduced: reducedMotion)
            }
            .font(.system(size: 32.8, weight: .medium)).tracking(-0.8).foregroundStyle(TradingBotsPalette.ink)
            .fixedSize()
            .position(x: 200, y: 603.5)

            TradingBotsCaption(text: "Fine-tune your bots, build strategies, and manage trades\neffortlessly")
                .tbReveal(on, delay: 0.4, y: 6, blur: 4, reduced: reducedMotion)
                .position(x: 200, y: 681)
        }
        .frame(width: 402, height: 874)
        .task { on = true }
    }
}

/// A phone outline at 316.7 × 380 holding a bot dashboard; it fades out downward.
private struct TradingBotsMiniPhone: View {
    let configure: () -> Void
    let overview: () -> Void
    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 54, style: .continuous).fill(.black)
            RoundedRectangle(cornerRadius: 54, style: .continuous)
                .strokeBorder(LinearGradient(colors: [TradingBotsPalette.hex(0x4a4a4a), TradingBotsPalette.hex(0x2c2c2c)], startPoint: .top, endPoint: .bottom), lineWidth: 2.4)
            statusBar
            dashboard.offset(x: 22.3, y: 59)
        }
        .frame(width: 316.7, height: 380, alignment: .topLeading)
        .buttonStyle(TradingBotsPressStyle())
        .mask(LinearGradient(stops: [.init(color: .white, location: 0), .init(color: .white, location: 0.74), .init(color: .clear, location: 0.95)], startPoint: .top, endPoint: .bottom))
    }

    private var statusBar: some View {
        ZStack(alignment: .topLeading) {
            Text("9:41").font(.system(size: 13, weight: .semibold)).position(x: 56.6, y: 33)
            Capsule().fill(.black).overlay(Capsule().strokeBorder(TradingBotsPalette.hex(0x3a3a3a), lineWidth: 1.6))
                .frame(width: 90.7, height: 20).position(x: 157, y: 33.3)
            HStack(spacing: 5) {
                Image(systemName: "cellularbars"); Image(systemName: "wifi"); Image(systemName: "battery.100percent")
            }
            .font(.system(size: 12, weight: .semibold)).position(x: 256, y: 33)
        }
        .foregroundStyle(.white)
        .frame(width: 316.7, height: 60, alignment: .topLeading)
    }

    private var dashboard: some View {
        VStack(alignment: .leading, spacing: 10.5) {
            VStack(spacing: 6) {
                Button(action: configure) {
                    HStack(spacing: 8.3) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 14, style: .continuous).fill(TradingBotsPalette.hex(0x1e1e1e))
                            TradingBotsCharacter(kind: .avatar, size: 35)
                        }
                        .frame(width: 49.7, height: 50.7)
                        VStack(alignment: .leading, spacing: 6) {
                            Text("AlphaMind").font(.system(size: 11.8, weight: .medium)).foregroundStyle(.white)
                            Text("Scalping · BTC/USDT").font(.system(size: 8.9)).foregroundStyle(TradingBotsPalette.hex(0x404040))
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "arrow.right").font(.system(size: 15, weight: .regular)).foregroundStyle(.white).padding(.trailing, 6)
                    }
                    .padding(.horizontal, 11).frame(width: 259.3, height: 75)
                    .background(TradingBotsPalette.hex(0x090909), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(TradingBotsPalette.hex(0x2a2a2a), lineWidth: 1.6))
                }
                .accessibilityLabel("Configure AlphaMind")
                TradingBotsDashboardControls(configure: configure, overview: overview, width: 259.3, fontSize: 8.8)
            }
            .padding(4).background(TradingBotsPalette.hex(0x101010), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .offset(x: -4)
            row("Live Execution Monitor", icon: "waveform.path.ecg.rectangle.fill", action: overview)
            row("Performance Overview", icon: "chart.bar.doc.horizontal", action: overview)
            row("View Trading Logs", icon: "doc.text", action: overview)
        }
    }

    private func row(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 11)).foregroundStyle(TradingBotsPalette.hex(0x8a8a8a)).frame(width: 15)
                Text(title).font(.system(size: 10.3, weight: .semibold)).tracking(-0.15).foregroundStyle(.white.opacity(0.82))
                Spacer()
            }
            .padding(.leading, 14).frame(width: 261, height: 56.7)
            .background(TradingBotsPalette.hex(0x0e0e0e), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(.white.opacity(0.03), lineWidth: 1))
        }
        .offset(x: -4)
    }
}

/// Risk Overview and Configure Bot, shared by the setup phone and the results stack.
struct TradingBotsDashboardControls: View {
    let configure: () -> Void
    let overview: () -> Void
    var width: CGFloat = 278
    var height: CGFloat = 46
    var fontSize: CGFloat = 10
    var body: some View {
        HStack(spacing: 7) {
            control("Risk Overview", action: overview) { TradingBotsBellCurve().stroke(.white, lineWidth: 1.2).frame(width: fontSize * 1.45, height: fontSize * 1.45) }
            control("Configure Bot", action: configure) { TradingBotsSliderGlyph().stroke(.white, lineWidth: 1.3).frame(width: fontSize * 1.6, height: fontSize * 0.75) }
        }
        .frame(width: width)
    }
    private func control<Icon: View>(_ title: String, action: @escaping () -> Void, @ViewBuilder icon: () -> Icon) -> some View {
        Button(action: action) {
            HStack(spacing: 8) { icon(); Text(title).font(.system(size: fontSize, weight: .medium)).tracking(-0.2).foregroundStyle(TradingBotsPalette.hex(0xe2e2e2)) }
                .frame(maxWidth: .infinity).frame(height: height)
                .background(TradingBotsPalette.hex(0x050505), in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 17, style: .continuous).strokeBorder(.white.opacity(0.05), lineWidth: 1))
        }
        .buttonStyle(TradingBotsPressStyle())
    }
}

struct TradingBotsSliderGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let r = rect.height / 2
        path.move(to: CGPoint(x: rect.minX, y: rect.midY)); path.addLine(to: CGPoint(x: rect.midX - r, y: rect.midY))
        path.addEllipse(in: CGRect(x: rect.midX - r, y: rect.minY, width: rect.height, height: rect.height))
        path.move(to: CGPoint(x: rect.midX + r, y: rect.midY)); path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        return path
    }
}
