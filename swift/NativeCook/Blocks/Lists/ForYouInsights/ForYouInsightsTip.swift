import CoreText
import SwiftUI
import UIKit

/// The tip's lines as the text engine breaks them at the column width (RN read them from onTextLayout).
struct ForYouInsightsTipLine {
    var text: String
    /// Typographic width in pt (not scaled).
    var width: Double
}

@MainActor
enum ForYouInsightsTipLayout {
    private static var cache: [Int: [ForYouInsightsTipLine]] = [:]

    static func lines(k: Double) -> [ForYouInsightsTipLine] {
        let key = Int((k * 1000).rounded())
        if let hit = cache[key] { return hit }
        typealias T = ForYouInsightsTheme
        let style = T.Typo.tip
        let font = UIFont.systemFont(ofSize: style.size * k, weight: style.weight.ui)
        let text = ForYouInsightsContent.tip as NSString
        let attributed = NSAttributedString(string: text as String, attributes: [.font: font, .kern: style.tracking * k])
        let setter = CTFramesetterCreateWithAttributedString(attributed)
        let path = CGPath(rect: CGRect(x: 0, y: 0, width: T.Layout.columnWidth * k, height: 10_000), transform: nil)
        let frame = CTFramesetterCreateFrame(setter, CFRange(location: 0, length: 0), path, nil)
        var result: [ForYouInsightsTipLine] = []
        for line in (CTFrameGetLines(frame) as? [CTLine]) ?? [] {
            let range = CTLineGetStringRange(line)
            let piece = text.substring(with: NSRange(location: range.location, length: range.length))
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let width = CTLineGetTypographicBounds(line, nil, nil, nil) - CTLineGetTrailingWhitespaceWidth(line)
            result.append(ForYouInsightsTipLine(text: piece, width: width / k))
        }
        cache[key] = result
        return result
    }
}

/// The AI tip. The text is a mask; under it an ink layer (the ghost while it regenerates), one ink strip per line
/// whose tinted head writes the line, and a shimmer band. At rest every strip covers its line.
struct ForYouInsightsTip: View {
    typealias T = ForYouInsightsTheme
    typealias M = ForYouInsightsMotion
    var k: Double
    var frame: ForYouInsightsTipFrame

    var body: some View {
        let lines = ForYouInsightsTipLayout.lines(k: k)
        let width = T.Layout.columnWidth * k
        let lineH = T.Typo.tip.line! * k
        let height = Double(T.Layout.tipLines) * lineH
        let heads = M.streamHeads(frame.stream, lines.map(\.width))
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in
                    let ink = T.Colors.ink
                    ctx.fill(Path(CGRect(x: 0, y: 0, width: width, height: height)), with: .color(ink.opacity(frame.ghost)))
                    let head = M.Stream.head * k
                    for (i, line) in lines.enumerated() {
                        let w = line.width * k
                        let y = Double(i) * lineH
                        let hx = heads[i] * k
                        let inkEnd = min(w, max(0, hx - head))
                        if inkEnd > 0 { ctx.fill(Path(CGRect(x: 0, y: y, width: inkEnd, height: lineH)), with: .color(ink)) }
                        let g0 = max(0, hx - head), g1 = min(w, hx)
                        if g1 > g0 {
                            let shading = GraphicsContext.Shading.linearGradient(
                                Gradient(stops: [.init(color: ink, location: 0), .init(color: T.Colors.pink, location: 0.55),
                                                 .init(color: T.Colors.violet, location: 1)]),
                                startPoint: CGPoint(x: hx - head, y: y), endPoint: CGPoint(x: hx, y: y))
                            ctx.fill(Path(CGRect(x: g0, y: y, width: g1 - g0, height: lineH)), with: shading)
                        }
                    }
                }
                .frame(width: width, height: height)
                shimmer(width: width, height: height)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .mask(alignment: .topLeading) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(lines.enumerated()), id: \.element.text) { _, line in
                        Text(line.text)
                            .font(.system(size: T.Typo.tip.size * k, weight: T.Typo.tip.weight.font))
                            .kerning(T.Typo.tip.tracking * k)
                            .fixedSize()
                            .frame(height: lineH, alignment: .leading)
                    }
                }
                .frame(width: width, height: height, alignment: .topLeading)
            }
            caret(lines: lines, heads: heads, lineH: lineH)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(ForYouInsightsContent.tip)
    }

    @ViewBuilder private func shimmer(width: Double, height: Double) -> some View {
        let s = frame.shimmer
        let w = M.Stream.shimmerWidth * k
        Rectangle()
            .fill(LinearGradient(colors: T.Colors.shimmer, startPoint: .leading, endPoint: .trailing))
            .frame(width: w, height: height * 1.6)
            .rotationEffect(.degrees(M.Stream.shimmerSkew))
            .offset(x: M.lerp(-w * 1.4, width + w * 0.4, max(0, s)), y: -height * 0.3)
            .opacity(s < 0 ? 0 : 1)
    }

    @ViewBuilder private func caret(lines: [ForYouInsightsTipLine], heads: [Double], lineH: Double) -> some View {
        if frame.caret > 0, !lines.isEmpty {
            let line = lines.indices.first { heads[$0] < lines[$0].width } ?? lines.count - 1
            let x = min(heads[line], lines[line].width) * k
            let w = M.Stream.caretWidth * k
            RoundedRectangle(cornerRadius: w, style: .continuous)
                .fill(LinearGradient(colors: [T.Colors.pink, T.Colors.violet], startPoint: .top, endPoint: .bottom))
                .frame(width: w, height: (T.Typo.tip.line! - 9) * k)
                .shadow(color: T.Colors.caretGlow.opacity(0.9), radius: 7 * k)
                .opacity(frame.caret)
                .offset(x: x + M.Stream.caretInset * 0.5 * k, y: Double(line) * lineH + 5 * k)
        }
    }
}
