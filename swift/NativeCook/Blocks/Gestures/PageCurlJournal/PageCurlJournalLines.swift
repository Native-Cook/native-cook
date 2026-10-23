import SwiftUI

/// Text laid out the way the React Native block does: every line in a box of exactly `line` points, the glyphs
/// centred in it. The copy carries its own line breaks.
struct PageCurlJournalLines: View {
    let lines: [String]
    let style: PageCurlJournalTheme.Line
    let scale: CGFloat
    let color: Color
    let width: CGFloat
    var alignment: Alignment = .center
    var tracking: CGFloat = 0

    init(
        _ lines: [String], style: PageCurlJournalTheme.Line, scale: CGFloat, color: Color, width: CGFloat,
        alignment: Alignment = .center, tracking: CGFloat = 0
    ) {
        self.lines = lines
        self.style = style
        self.scale = scale
        self.color = color
        self.width = width
        self.alignment = alignment
        self.tracking = tracking
    }

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, text in
                Text(text)
                    .font(.system(size: style.size * scale, weight: style.weight))
                    .kerning(tracking)
                    .foregroundStyle(color)
                    .lineLimit(1)
                    .fixedSize()
                    .frame(width: width, height: style.line * scale, alignment: alignment)
            }
        }
        .frame(width: width, alignment: alignment)
    }
}
