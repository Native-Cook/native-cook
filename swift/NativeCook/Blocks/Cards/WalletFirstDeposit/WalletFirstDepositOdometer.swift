import SwiftUI
import UIKit

/// Two visible glyphs per reel, measured with the same native font as the endpoint labels.
struct WalletFirstDepositOdometer: View {
    enum Kind { case balance, spent }
    let kind: Kind
    let progress: Double
    let scale: Double

    private struct Slot {
        var character: String? = nil
        var end = 0
        var cycles = 0
        var enters = false
        var grey = false
    }

    private var slots: [Slot] {
        switch kind {
        case .balance:
            [Slot(character: "$"), Slot(end: 6, enters: true), Slot(character: ",", enters: true),
             Slot(end: 7, enters: true), Slot(end: 9, enters: true), Slot(cycles: 1),
             Slot(character: ".", grey: true), Slot(end: 2, cycles: 1, grey: true), Slot(end: 2, cycles: 1, grey: true)]
        case .spent:
            [Slot(character: "$"), Slot(end: 9, enters: true), Slot(end: 8, enters: true), Slot(end: 3)]
        }
    }

    var body: some View {
        let size = (kind == .balance ? 49.0 : 19.4) * scale
        let tracking = (kind == .balance ? -3.4 : -1.2) * scale
        let lineHeight = ceil(size * 1.2)
        let font = UIFont.systemFont(ofSize: size, weight: .medium)
        let advances = Dictionary(uniqueKeysWithValues: "0123456789$,.".map {
            let glyph = String($0)
            return (glyph, (glyph as NSString).size(withAttributes: [.font: font, .kern: tracking]).width)
        })
        let slots = slots
        Canvas { context, _ in
            let span = kind == .balance ? 1.0 : 0.8
            let duration = 1 - Double(slots.count - 1) * 0.03 / span
            var x = 0.0
            for (index, slot) in slots.enumerated() {
                let q = WalletFirstDepositMotion.clamp((progress - Double(slots.count - 1 - index) * 0.03 / span) / duration)
                let presence = slot.enters ? 1 + 2.2 * pow(q - 1, 3) + 1.2 * pow(q - 1, 2) : 1
                var reel = context
                reel.opacity = slot.enters ? min(1, q * 1.6) : 1
                reel.clip(to: Path(CGRect(x: x, y: 0, width: size, height: lineHeight)))
                let color = slot.grey ? WalletFirstDepositMetrics.decimals : .black
                func glyph(_ value: String, _ y: Double) {
                    reel.draw(Text(value).font(.system(size: size, weight: .medium)).tracking(tracking).foregroundStyle(color),
                              at: CGPoint(x: x, y: y), anchor: .topLeading)
                }
                let width: Double
                if let character = slot.character {
                    glyph(character, 0)
                    width = advances[character, default: 0]
                } else {
                    let value = Double(slot.end + 10 * slot.cycles) * WalletFirstDepositMotion.out(q)
                    let number = value.truncatingRemainder(dividingBy: 10)
                    let low = Int(floor(number))
                    let fraction = number - Double(low)
                    glyph(String(low), -fraction * lineHeight)
                    glyph(String((low + 1) % 10), (1 - fraction) * lineHeight)
                    width = advances[String(low), default: 0] * (1 - fraction) + advances[String((low + 1) % 10), default: 0] * fraction
                }
                x += width * presence
            }
        }
        .frame(width: size * 9, height: lineHeight)
        .accessibilityHidden(true)
    }
}
