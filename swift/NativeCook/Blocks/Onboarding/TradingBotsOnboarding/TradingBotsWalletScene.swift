import SwiftUI

/// Step 1: the wallet card (275 × 375 at x 66.7, y 161.7) over "Your Wallet. / Your Strategy".
struct TradingBotsWalletScene: View {
    let reducedMotion: Bool
    @State private var on = false
    @State private var balance = 12_035.0
    var body: some View {
        ZStack {
            TradingBotsWalletCard(balance: balance)
                .opacity(on ? 1 : 0)
                .offset(y: on || reducedMotion ? 0 : 46)
                .scaleEffect(on || reducedMotion ? 1 : 0.94, anchor: .top)
                .animation(reducedMotion ? .easeOut(duration: 0.2) : .spring(response: 0.75, dampingFraction: 0.9), value: on)
                .position(x: 66.7 + 137.5, y: 161.7 + 187.5)
            TradingBotsHeading(first: "Your Wallet.", second: "Your Strategy", on: on, reduced: reducedMotion)
                .position(x: 200, y: 603.5)
            TradingBotsCaption(text: "Connect your wallet and access the tools\nyou need to manage your trades.")
                .tbReveal(on, delay: 0.35, y: 6, blur: 4, reduced: reducedMotion)
                .position(x: 201, y: 681.7)
        }
        .frame(width: 402, height: 874)
        .task {
            on = true
            guard !reducedMotion else { balance = 12_485.32; return }
            await tradingBotsSettle(from: 12_035, to: 12_485.32, fastShare: 0.89, fast: 0.06, slow: 0.45, duration: 2.2) { balance = $0 }
        }
    }
}

private struct TradingBotsWalletCard: View {
    let balance: Double
    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 32, style: .continuous)
                .fill(LinearGradient(colors: [TradingBotsPalette.hex(0x1a1a1a), TradingBotsPalette.hex(0x161616), TradingBotsPalette.hex(0x121212)], startPoint: .top, endPoint: .bottom))
            header
            panel.offset(x: 6, y: 64)
        }
        .frame(width: 275, height: 375)
        .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 32, style: .continuous)
            .strokeBorder(LinearGradient(colors: [.white.opacity(0.09), .white.opacity(0.03), .white.opacity(0.07)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
        .shadow(color: .black.opacity(0.9), radius: 24, y: 16)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Secure wallet connection. Total wallet balance \(Int(balance)) dollars")
    }

    private var header: some View {
        ZStack(alignment: .topLeading) {
            Image(systemName: "powerplug").font(.system(size: 13, weight: .regular)).rotationEffect(.degrees(-45))
                .frame(width: 16, height: 16).position(x: 21.8, y: 34.8)
            Text("Secure Wallet Connection").font(.system(size: 13.6, weight: .medium)).tracking(-0.1)
                .fixedSize().frame(width: 200, alignment: .leading).position(x: 35 + 100, y: 34.8)
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous).fill(TradingBotsPalette.hex(0x141414))
                RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(.white.opacity(0.13), lineWidth: 1)
                Image(systemName: "plus").font(.system(size: 11, weight: .semibold))
            }
            .frame(width: 31, height: 31).position(x: 243.8, y: 33.8)
        }
        .foregroundStyle(TradingBotsPalette.ink)
        .frame(width: 275, height: 64, alignment: .topLeading)
    }

    private var panel: some View {
        ZStack(alignment: .topLeading) {
            Rectangle().colorEffect(ShaderLibrary.tradingBotWalletGradient(.float2(263, 311)))
            connectRow.frame(width: 263, height: 52)
            balanceInset.frame(width: 257, height: 89).offset(x: 1.5, y: 60.3)
            holdings.frame(width: 260, height: 160).offset(x: -0.5, y: 156)
        }
        .frame(width: 263, height: 311, alignment: .topLeading)
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 24, bottomLeadingRadius: 26, bottomTrailingRadius: 26, topTrailingRadius: 24, style: .continuous))
    }

    private var connectRow: some View {
        ZStack(alignment: .topLeading) {
            ZStack {
                RoundedRectangle(cornerRadius: 4, style: .continuous).fill(.white.opacity(0.88))
                RoundedRectangle(cornerRadius: 2).fill(TradingBotsPalette.hex(0x7d8ff4)).frame(width: 10, height: 6).offset(y: 2)
                Image(systemName: "chevron.up").font(.system(size: 5, weight: .bold)).foregroundStyle(.white).offset(y: 2)
            }
            .frame(width: 14.6, height: 14.6).position(x: 17.3, y: 25.3)
            Text("Connect Any Wallet").font(.system(size: 12, weight: .regular)).tracking(-0.05).foregroundStyle(.white)
                .fixedSize().frame(width: 150, alignment: .leading).position(x: 31.6 + 75, y: 25.3)
            walletLogos.position(x: 209.6, y: 25.3)
        }
    }

    private var walletLogos: some View {
        HStack(spacing: -4.1) {
            logo(fill: .white) {
                HStack(spacing: 2.6) {
                    Capsule().fill(.black).frame(width: 3.4, height: 6.4)
                    Capsule().fill(.black).frame(width: 3.4, height: 6.4)
                }.offset(x: -1, y: -0.5)
            }
            logo(fill: TradingBotsPalette.hex(0x1b1b1b)) {
                Image(systemName: "shield.fill").font(.system(size: 13)).foregroundStyle(LinearGradient(colors: [TradingBotsPalette.hex(0xff6a2a), TradingBotsPalette.hex(0xe0301a)], startPoint: .top, endPoint: .bottom))
            }
            logo(fill: TradingBotsPalette.hex(0x5236f2)) {
                Text("S").font(.system(size: 14, weight: .black)).italic().foregroundStyle(.white)
            }
            logo(fill: TradingBotsPalette.hex(0x101010)) {
                Image(systemName: "mountain.2.fill").font(.system(size: 9)).foregroundStyle(.white)
            }
        }
    }

    private func logo<Content: View>(fill: Color, @ViewBuilder content: () -> Content) -> some View {
        ZStack { Circle().fill(fill); content() }
            .frame(width: 24.3, height: 24.3)
            .overlay(Circle().strokeBorder(.black, lineWidth: 1.6))
    }

    private var balanceInset: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 26, style: .continuous).fill(.black)
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .stroke(TradingBotsPalette.hex(0x8696ff, 0.28), lineWidth: 4).blur(radius: 4)
                .mask(RoundedRectangle(cornerRadius: 26, style: .continuous))
            Image(decorative: "TradingBots-wallet-icon").resizable().interpolation(.high).scaledToFit().frame(width: 48, height: 48).position(x: 16.7 + 22.2, y: 20.7 + 23)
            HStack(alignment: .firstTextBaseline, spacing: 2.5) {
                Text("$").foregroundStyle(TradingBotsPalette.hex(0x8a8a8a))
                TradingBotsAmount(value: balance, prefix: "", decimals: 2).foregroundStyle(TradingBotsPalette.ink)
            }
            .font(.system(size: 18.5, weight: .semibold)).tracking(-0.1)
            .fixedSize().frame(width: 140, alignment: .leading).position(x: 75 + 70, y: 33.3)
            Text("Total Wallet Balance").font(.system(size: 9)).foregroundStyle(TradingBotsPalette.hex(0x8c8c8c))
                .fixedSize().frame(width: 140, alignment: .leading).position(x: 75 + 70, y: 58)
        }
    }

    private var holdings: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 26, style: .continuous).fill(TradingBotsPalette.hex(0x0a0a0a))
            Text("Your holdings").font(.system(size: 9.2)).foregroundStyle(TradingBotsPalette.hex(0xe4e4e4))
                .fixedSize().position(x: 133, y: 23.3)
            holding("Tether", ticker: "USDT", value: "0.22%", tint: TradingBotsPalette.hex(0x4fd1b5), dim: false)
                .position(x: 19 + 114.7, y: 43.6 + 23.35)
            holding("SushiSwap", ticker: "SUSHI", value: "5.12%", tint: TradingBotsPalette.hex(0xd2647e), dim: true)
                .position(x: 19 + 114.7, y: 100.6 + 23.35)
            LinearGradient(colors: [.clear, TradingBotsPalette.hex(0x0a0a0a, 0.55)], startPoint: .init(x: 0.5, y: 0.6), endPoint: .bottom)
                .allowsHitTesting(false)
        }
    }

    private func holding(_ name: String, ticker: String, value: String, tint: Color, dim: Bool) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(TradingBotsPalette.hex(0x1c1c1c))
            ZStack {
                Circle().fill(TradingBotsPalette.hex(0x262626))
                if dim {
                    Capsule().fill(LinearGradient(colors: [TradingBotsPalette.hex(0xff8fb0), TradingBotsPalette.hex(0x7c5cff)], startPoint: .leading, endPoint: .trailing))
                        .frame(width: 11, height: 7).rotationEffect(.degrees(-25))
                } else {
                    Image(systemName: "shield.fill").font(.system(size: 13)).foregroundStyle(TradingBotsPalette.hex(0x45d6b2)).rotationEffect(.degrees(180))
                    Text("T").font(.system(size: 8, weight: .heavy)).foregroundStyle(.black).offset(y: -1)
                }
            }
            .frame(width: 25, height: 25).position(x: 22.6, y: 23.35)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(name).font(.system(size: 10.7)).foregroundStyle(TradingBotsPalette.hex(0xe2e2e2))
                Text(ticker).font(.system(size: 6.5, weight: .medium)).foregroundStyle(TradingBotsPalette.hex(0x5a5a5a))
            }
            .fixedSize().frame(width: 150, alignment: .leading).position(x: 42.6 + 75, y: 23.35)
            Text(value).font(.system(size: 8.2, weight: .medium)).foregroundStyle(tint)
                .frame(width: 32.3, height: 15.3)
                .background(TradingBotsPalette.hex(0x2a2a2a), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                .position(x: 204.1, y: 23.35)
        }
        .frame(width: 229.3, height: 46.7)
        .opacity(dim ? 0.62 : 1)
    }
}
