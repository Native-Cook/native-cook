import SwiftUI

struct CashbackCloudCardArt: View {
    var frame: CashbackCloudCardMotion.Frame
    var top: Double
    var reduced: Bool
    private let art = CashbackCloudCardResources.shared
    private func c(_ hex: UInt32) -> Color { CashbackCloudCardResources.color(hex) }
    var body: some View {
        Canvas { ctx, size in
            let w = size.width - 32
            let y = top + 120
            func amount(_ value: String, _ x: Double, _ y: Double, _ font: Font) {
                ctx.draw(Text(value).font(font).monospacedDigit().foregroundColor(c(0x1E2A4A)), at: CGPoint(x: x, y: y), anchor: .topLeading)
            }
            amount(art.mainText(1284.5 + 0.36 * frame.fraction), art.balanceX + 1.0 / 3, top + 68, art.balanceFont)
            amount(String(format: "$%.2f", 0.36 * (1 - frame.fraction)), 42, y + 306, art.cashFont)
            let base = y + 262
            if frame.rainU >= 0 {
                for i in 0..<6 {
                    let k = CashbackCloudCardMotion.coinK(frame.rainU, i)
                    if k <= 0 || k >= 1 { continue }
                    let cx = 16 + w * (0.36 + Double((i * 37) % 30) / 100)
                    let cy = base - 30 + 78 * k * k
                    let spin = reduced ? 1 : cos(k * .pi * 3 + Double(i))
                    let sx = abs(spin) < 0.3 ? 1 : abs(spin)
                    var coin = ctx
                    coin.opacity = reduced ? 1 - k : 1 - CashbackCloudCardMotion.clamp((k - 0.85) / 0.15)
                    coin.draw(abs(spin) < 0.3 ? art.edge : art.coin, in: CGRect(x: cx - 23 * sx, y: cy - 23, width: 46 * sx, height: 46))
                }
            }
            let sq = reduced ? 0 : 0.16 * frame.squeeze + frame.release * 0.16
            let cw = w * 0.92 * (1 + sq * 0.6)
            let ch = cw * (560.0 / 900) * (1 - sq)
            ctx.draw(art.cloud, in: CGRect(x: 16 + w / 2 - cw / 2, y: base - ch, width: cw, height: ch))
            let a = max(frame.rainU >= 0 ? 0 : 1, frame.refill)
            if a > 0 {
                var coin = ctx; coin.opacity = a
                let sz = 64 * (0.6 + 0.4 * a)
                coin.draw(art.coin, in: CGRect(x: 16 + w * 0.6 - sz / 2, y: base - ch * 0.92 - sz * 0.2, width: sz, height: sz))
            }

        }
        .accessibilityElement(children: .contain)
        .overlay(alignment: .topLeading) {
            Color.clear.frame(width: 180, height: 25).offset(x: 20, y: top + 68)
                .accessibilityLabel("Main balance")
            Color.clear.frame(width: 180, height: 60).offset(x: 42, y: top + 426)
                .accessibilityLabel("Cashback this week")
        }
    }
}
