import SwiftUI

struct GiftCardPickerGlyph: View {
    enum Kind: Hashable { case close, back, deposit, analyze, transfer, home, pay, invest, chat }
    let kind: Kind
    var color = GiftCardPickerTheme.ink

    private struct Stroke {
        let path: Path
        let orange: Bool
        let width: CGFloat
        let roundCap: Bool
        init(_ data: String, orange: Bool = false, width: CGFloat = 2.2, roundCap: Bool = false) {
            path = GiftCardPickerSVG.path(data)
            self.orange = orange
            self.width = width
            self.roundCap = roundCap
        }
    }
    private static let strokes: [Kind: [Stroke]] = [
        .close: [.init("M2 2 L16 16 M16 2 L2 16", width: 2, roundCap: true)],
        .back: [.init("M13 3 L6 10 L13 17", roundCap: true)],
        .deposit: [
            .init("M15 7.5 h2.5 a6.5 6.5 0 0 1 0 13 H15", orange: true, roundCap: true),
            .init("M7.5 6.5 h4 v4.5 h4.5 v4 h-4.5 v4.5 h-4 v-4.5 H3 v-4 h4.5 z")
        ],
        .analyze: [
            .init("M13 6.5 a9 9 0 1 0 9 9 H13 z"),
            .init("M16 3.5 a8.5 8.5 0 0 1 8.5 8.5 H16 z", orange: true)
        ],
        .transfer: [
            .init("M13 7 L20.5 14 L13 21", orange: true, roundCap: true),
            .init("M5 7.5 v13 a1 1 0 0 0 1.6 0.8 L14 15 a1.3 1.3 0 0 0 0 -2 L6.6 6.7 A1 1 0 0 0 5 7.5 z")
        ],
        .home: [.init("M4 11.5 L13 4 L22 11.5 V21.5 a1 1 0 0 1 -1 1 H16 v-6 h-6 v6 H5 a1 1 0 0 1 -1 -1 z", width: 2.1)],
        .pay: [.init("M3.5 6 v14 L12 13 z M12 6 v14 L21 13 z", width: 2.1)],
        .invest: [
            .init("M5 22 v-5 M10 22 v-8 M15 22 v-10 M20 22 v-13", orange: true, width: 2.4, roundCap: true),
            .init("M4 13 C 10 11, 15 8, 21 3.5", orange: true, roundCap: true)
        ],
        .chat: [.init("M5 5 h16 a2 2 0 0 1 2 2 v10 a2 2 0 0 1 -2 2 H11 l-4.5 3.5 V19 H5 a2 2 0 0 1 -2 -2 V7 a2 2 0 0 1 2 -2 z", width: 2.1)]
    ]
    private var viewBox: CGFloat {
        switch kind {
        case .close: 18
        case .back: 20
        case .deposit, .analyze, .transfer: 28
        default: 26
        }
    }
    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / viewBox, y: size.height / viewBox)
            for stroke in Self.strokes[kind] ?? [] {
                context.stroke(stroke.path, with: .color(stroke.orange ? GiftCardPickerTheme.orange : color), style: StrokeStyle(lineWidth: stroke.width, lineCap: stroke.roundCap ? .round : .butt, lineJoin: .round))
            }
            if kind == .chat {
                for x in [8.5, 13.0, 17.5] {
                    context.fill(Path(ellipseIn: CGRect(x: x - 1.3, y: 10.7, width: 2.6, height: 2.6)), with: .color(color))
                }
            }
        }
        .accessibilityHidden(true)
    }
}
