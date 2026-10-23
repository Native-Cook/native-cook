import SwiftUI

struct GiftCardPickerStockBackground: View {
    let index: Int
    let width: CGFloat
    let height: CGFloat
    var body: some View {
        GiftCardPickerFieldImage(name: "backdrop-\(index)", samples: GiftCardPickerFields.backdrops[index], width: width, height: height, blur: 18)
            .background(GiftCardPickerData.stocks[index].backdropTint)
            .allowsHitTesting(false)
    }
}

struct GiftCardPickerDust: View {
    let width: CGFloat
    let height: CGFloat
    var body: some View {
        Canvas { context, _ in
            for index in 0..<46 {
                let a = sin(Double(index) * 12.9898) * 43758.5453
                let b = sin(Double(index) * 78.233) * 12345.6789
                let x = (a - floor(a)) * width
                let y = (0.18 + (b - floor(b)) * 0.66) * height
                let size = 1 + CGFloat((index * 7) % 3) * 0.5
                context.fill(Path(ellipseIn: CGRect(x: x, y: y, width: size, height: size)), with: .color(.white.opacity(0.25 + Double((index * 13) % 5) * 0.1)))
            }
        }
        .frame(width: width, height: height)
    }
}

struct GiftCardPickerCardPose: ViewModifier {
    let distance: Double
    func body(content: Content) -> some View {
        let side = min(1, max(-1, distance))
        let scale = 1 - 0.12 * abs(side)
        let skew = tan(side * -3.3 * .pi / 180)
        // RN transforms act about the card centre, in translate → skewX → scale order.
        let transform = CGAffineTransform(a: scale, b: 0, c: skew * scale, d: scale,
                                          tx: 98 * (1 - scale) - 130 * skew * scale,
                                          ty: 130 * (1 - scale))
        content.projectionEffect(ProjectionTransform(transform)).offset(x: distance * 227)
    }
}

struct GiftCardPickerCardTrack: View {
    let width: CGFloat
    let scroll: Double
    let reveal: Double
    let onCard: (Int) -> Void
    var body: some View {
        ZStack {
            ForEach(Array(GiftCardPickerData.stocks.enumerated()), id: \.element.id) { index, stock in
                let distance = Double(index) - scroll
                Button(action: { onCard(index) }) { GiftCardPickerStockCard(stock: stock, reveal: reveal) }
                    .buttonStyle(.plain)
                    .frame(width: 196, height: 260)
                    .modifier(GiftCardPickerCardPose(distance: distance))
                    .opacity(abs(distance) > 2.2 ? 0 : 1)
                    .zIndex((10 - abs(distance) * 2).rounded())
                    .accessibilityLabel("\(stock.name), \(stock.change > 0 ? "up" : "down") \(abs(stock.change))% past year")
            }
        }
        .frame(width: width, height: 300)
    }
}

struct GiftCardPickerStockCard: View {
    let stock: GiftCardPickerStock
    let reveal: Double
    @Environment(\.displayScale) private var displayScale
    var body: some View {
        let logo = min(1, max(0, reveal - 1))
        let art = reveal < 1 ? 0.4 + reveal * 0.35 : 0.75 + (reveal - 1) * 0.25
        ZStack(alignment: .topLeading) {
            GiftCardPickerImage(name: stock.imageName).resizable().scaledToFill()
                .frame(width: 196, height: 260).clipped().opacity(art)
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(.white.opacity(0.22), lineWidth: 1 / displayScale)
            GiftCardPickerBrandMark(mark: stock.mark).frame(width: 96, height: 96)
                .background(stock.markBackground, in: Circle()).clipShape(Circle())
                .scaleEffect(0.9 + 0.1 * logo).opacity(logo).position(x: 98, y: 93)
            Text(stock.name).font(.system(size: 28, weight: .bold)).kerning(-0.3).foregroundStyle(.white)
                .lineLimit(1).frame(width: 184, height: 34).position(x: 98, y: 178)
            Text("\(stock.change > 0 ? "↑" : "↓") \(abs(stock.change))% past year")
                .font(.system(size: 15)).foregroundStyle(stock.change > 0 ? Color(hex: 0x5EE06A) : Color(hex: 0xFF4F86))
                .frame(width: 196, height: 20).position(x: 98, y: 207)
        }
        .frame(width: 196, height: 260, alignment: .topLeading)
        .background(Color(hex: 0x101018))
        .clipShape(.rect(cornerRadius: 18, style: .continuous))
    }
}

struct GiftCardPickerDescription: View {
    let stock: GiftCardPickerStock
    private var fadedLine: AttributedString {
        var text = AttributedString(stock.lines.1)
        text.foregroundColor = .white.opacity(0.86)
        for (index, letter) in stock.tail.enumerated() {
            var suffix = AttributedString(String(letter))
            suffix.foregroundColor = .white.opacity(0.5 - Double(index) * 0.3)
            text.append(suffix)
        }
        return text
    }
    var body: some View {
        VStack(spacing: 0) {
            Text(stock.lines.0).foregroundStyle(.white.opacity(0.86)).lineLimit(1).frame(height: 21)
            HStack(spacing: 6) {
                Text(fadedLine).lineLimit(1).fixedSize()
                Text("more").underline().foregroundStyle(.white)
            }.frame(height: 21)
        }
        .font(.system(size: 16)).multilineTextAlignment(.center)
    }
}
