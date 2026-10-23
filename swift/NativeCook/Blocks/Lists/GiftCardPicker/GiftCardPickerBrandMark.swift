import SwiftUI

struct GiftCardPickerBrandMark: View {
    let mark: GiftCardPickerStock.Mark
    private static let petals: [(Path, Color)] = [
        (GiftCardPickerSVG.path("M49 49 V27 a22 22 0 0 0 -22 22 z"), Color(hex: 0xF35B2C)),
        (GiftCardPickerSVG.path("M51 49 H73 a22 22 0 0 0 -22 -22 z"), Color(hex: 0x8CC63F)),
        (GiftCardPickerSVG.path("M49 51 H27 a22 22 0 0 0 22 22 z"), Color(hex: 0x19A9F0)),
        (GiftCardPickerSVG.path("M51 51 V73 a22 22 0 0 0 22 -22 z"), Color(hex: 0xFFC21A))
    ]
    private static let ribbon: [(Path, Color)] = [
        (GiftCardPickerSVG.path("M34 24 h10 v52 h-10 z"), Color(hex: 0xB20710)),
        (GiftCardPickerSVG.path("M44 24 h9 a14 14 0 0 1 0 28 h-9 z"), Color(hex: 0xE50914)),
        (GiftCardPickerSVG.path("M44 46 h9 l15 30 h-11 z"), Color(hex: 0xE50914)),
        (GiftCardPickerSVG.path("M44 24 h4 v52 h-4 z"), Color(hex: 0x8A050C))
    ]
    private static let lens = GiftCardPickerSVG.path("M18 50 C 32 30, 68 30, 82 50 C 68 70, 32 70, 18 50 z")
    private static let spiral = GiftCardPickerSVG.path("M50 50 m -3 0 a 3 3 0 1 1 6 0 a 8 8 0 1 1 -16 0 a 13 13 0 1 1 26 0")
    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 100, y: size.height / 100)
            switch mark {
            case .rings:
                for (x, center, angle) in [(18.0, 37.0, -12.0), (44.0, 63.0, 12.0)] {
                    var rotated = context
                    rotated.translateBy(x: center, y: 50)
                    rotated.rotate(by: .degrees(angle))
                    rotated.translateBy(x: -center, y: -50)
                    let path = Path(roundedRect: CGRect(x: x, y: 34, width: 38, height: 32), cornerRadius: 16)
                    rotated.stroke(path, with: .color(.white), lineWidth: 7.5)
                }
            case .petals: for (path, color) in Self.petals { context.fill(path, with: .color(color)) }
            case .ribbon: for (path, color) in Self.ribbon { context.fill(path, with: .color(color)) }
            case .lens:
                context.stroke(Self.lens, with: .color(.white), style: StrokeStyle(lineWidth: 6, lineJoin: .round))
                context.stroke(Self.spiral, with: .color(.white), style: StrokeStyle(lineWidth: 5.5, lineCap: .round))
            case .wordmark:
                let text = Text("NOVA").font(.system(size: 26, weight: .black)).italic().kerning(-0.5).foregroundStyle(.white)
                let resolved = context.resolve(text)
                let bounds = resolved.measure(in: CGSize(width: 100, height: 100))
                let baseline = resolved.firstBaseline(in: CGSize(width: 100, height: 100))
                context.draw(resolved, at: CGPoint(x: (100 - bounds.width) / 2, y: 59 - baseline), anchor: .topLeading)
            }
        }
        .accessibilityHidden(true)
    }
}
