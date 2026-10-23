import SwiftUI
import UIKit

/// The four printed pages are static SwiftUI views. The curl surface snapshots each one once and samples those
/// textures while a sheet moves, so the paper grain and type are never rebuilt per frame.
struct PageCurlJournalPageView: View {
    let page: Int
    let size: CGSize

    var body: some View {
        let scale = size.width / PageCurlJournalTheme.referenceWidth
        ZStack(alignment: .topLeading) {
            PageCurlJournalPaper(scale: scale)
            switch page {
            case 0: PageCurlJournalTitlePage(scale: scale)
            case 1: PageCurlJournalPrefacePage(scale: scale)
            case 2: PageCurlJournalForewordPage(scale: scale)
            default: PageCurlJournalCoverPage(scale: scale)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(PageCurlJournalTheme.Paper.base)
        .clipped()
    }
}

private struct PageCurlJournalPaper: View {
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalTheme.Paper.base
            Image(uiImage: PageCurlJournalAssets.paper).resizable(resizingMode: .tile)
            LinearGradient(
                stops: [
                    .init(color: Color(red: 70 / 255, green: 48 / 255, blue: 24 / 255).opacity(0.24), location: 0),
                    .init(color: Color(red: 70 / 255, green: 48 / 255, blue: 24 / 255).opacity(0.07), location: 0.35),
                    .init(color: .clear, location: 1),
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: PageCurlJournalTheme.Book.gutter * scale)
            LinearGradient(
                colors: [.clear, Color(red: 70 / 255, green: 48 / 255, blue: 24 / 255).opacity(0.06)],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: 14 * scale)
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .allowsHitTesting(false)
    }
}

private typealias PCJTheme = PageCurlJournalTheme

struct PageCurlJournalTitlePage: View {
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            label(PCJTheme.Copy.presents, top: 150, size: 16, line: 22, color: .mute, italic: true)
            label(PCJTheme.Copy.title, top: 196, size: 46, line: 50, color: .ink, height: 100)
            PageCurlJournalOrnament(width: 132, scale: scale)
                .offset(x: 135 * scale, y: 318 * scale)
            label(PCJTheme.Copy.subtitle, top: 348, size: 17, line: 24, color: .ink, italic: true)
            PageCurlJournalSeal(scale: scale)
                .offset(x: 149 * scale, y: 470 * scale)
            label(PCJTheme.Copy.imprint, top: 700, size: 10, line: 14, color: .mute, tracking: 2.2)
            PageCurlJournalLabel(
                runs: [.init(PCJTheme.Copy.hint, size: 15, color: .mute, italic: true)],
                lineHeight: 18,
                alignment: .right,
                scale: scale
            )
            .frame(width: 306 * scale, height: 18 * scale)
            .offset(x: 48 * scale, y: 806 * scale)
        }
    }

    private func label(
        _ text: String,
        top: CGFloat,
        size: CGFloat,
        line: CGFloat,
        color: PageCurlJournalTextColor,
        italic: Bool = false,
        tracking: CGFloat = 0,
        height: CGFloat? = nil
    ) -> some View {
        PageCurlJournalLabel(
            runs: [.init(text, size: size, color: color, italic: italic, tracking: tracking)],
            lineHeight: line,
            alignment: .center,
            scale: scale
        )
        .frame(width: 402 * scale, height: (height ?? line) * scale)
        .offset(y: top * scale)
    }
}

struct PageCurlJournalPrefacePage: View {
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalFurniture(head: PCJTheme.Copy.headPreface, folio: "ii", scale: scale)
            centered(PCJTheme.Copy.preface, top: 150, size: 11, line: 15.4, color: .accent, tracking: 2.42)
            centered(PCJTheme.Copy.prefaceTitle, top: 176, size: 32, line: 38, color: .ink)
            PageCurlJournalOrnament(width: 64, scale: scale)
                .offset(x: 169 * scale, y: 238 * scale)
            ForEach(Array(PCJTheme.Copy.rows.enumerated()), id: \.offset) { index, row in
                PageCurlJournalLabel(
                    runs: [
                        .init(row.title, size: 12, color: .ink, tracking: 2.16),
                        .init("  \(row.body)", size: 16, color: .ink),
                    ],
                    lineHeight: 24,
                    alignment: .justified,
                    scale: scale
                )
                .frame(width: 306 * scale, height: 120 * scale, alignment: .topLeading)
                .offset(x: 48 * scale, y: CGFloat(284 + index * 132) * scale)
            }
            Rectangle()
                .fill(PCJTheme.Paper.rule)
                .frame(width: 64 * scale, height: 0.5)
                .offset(x: 48 * scale, y: 640 * scale)
            PageCurlJournalLabel(
                runs: [.init(PCJTheme.Copy.footnote, size: 12.5, color: .mute)],
                lineHeight: 18,
                alignment: .left,
                scale: scale
            )
            .frame(width: 306 * scale, height: 54 * scale, alignment: .topLeading)
            .offset(x: 48 * scale, y: 652 * scale)
        }
    }

    private func centered(
        _ text: String, top: CGFloat, size: CGFloat, line: CGFloat,
        color: PageCurlJournalTextColor, tracking: CGFloat = 0
    ) -> some View {
        PageCurlJournalLabel(
            runs: [.init(text, size: size, color: color, tracking: tracking)],
            lineHeight: line,
            alignment: .center,
            scale: scale
        )
        .frame(width: 402 * scale, height: line * scale)
        .offset(y: top * scale)
    }
}

struct PageCurlJournalForewordPage: View {
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalFurniture(head: PCJTheme.Copy.headForeword, folio: "iii", scale: scale)
            centered(PCJTheme.Copy.foreword, top: 150, size: 11, line: 15.4, color: .accent, tracking: 2.42)
            centered(PCJTheme.Copy.forewordTitle, top: 176, size: 32, line: 38, color: .ink)
            PageCurlJournalOrnament(width: 64, scale: scale)
                .offset(x: 169 * scale, y: 238 * scale)
            PageCurlJournalLabel(
                runs: [
                    .init(PCJTheme.Copy.bodyLead, size: 12.5, color: .accent, tracking: 2.25),
                    .init(PCJTheme.Copy.body[0], size: 16, color: .ink),
                    .init("\n \(PCJTheme.Copy.body[1])", size: 16, color: .ink),
                    .init("\n \(PCJTheme.Copy.body[2])", size: 16, color: .ink),
                ],
                lineHeight: 24,
                alignment: .justified,
                scale: scale
            )
            .frame(width: 306 * scale, height: 264 * scale, alignment: .topLeading)
            .offset(x: 48 * scale, y: 284 * scale)
            PageCurlJournalLabel(
                runs: [.init(PCJTheme.Copy.signoff, size: 15, color: .mute, italic: true)],
                lineHeight: 22,
                alignment: .right,
                scale: scale
            )
            .frame(width: 306 * scale, height: 22 * scale)
            .offset(x: 48 * scale, y: 552 * scale)
            PageCurlJournalOrnament(width: 64, scale: scale)
                .offset(x: 169 * scale, y: 620 * scale)
        }
    }

    private func centered(
        _ text: String, top: CGFloat, size: CGFloat, line: CGFloat,
        color: PageCurlJournalTextColor, tracking: CGFloat = 0
    ) -> some View {
        PageCurlJournalLabel(
            runs: [.init(text, size: size, color: color, tracking: tracking)],
            lineHeight: line,
            alignment: .center,
            scale: scale
        )
        .frame(width: 402 * scale, height: line * scale)
        .offset(y: top * scale)
    }
}

struct PageCurlJournalCoverPage: View {
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalLabel(
                runs: [.init(PCJTheme.Copy.title, size: 40, color: .foil)],
                lineHeight: 46,
                alignment: .center,
                scale: scale
            )
            .frame(width: 402 * scale, height: 92 * scale)
            .offset(y: 168 * scale)
            PageCurlJournalOrnament(width: 132, scale: scale)
                .offset(x: 135 * scale, y: 290 * scale)
            centered(PCJTheme.Copy.belongs, top: 384, size: 10.5, line: 14.7, color: .mute, tracking: 2.31)
            centered(PCJTheme.Copy.author, top: 414, size: 34, line: 42, color: .ink, italic: true)
            Rectangle()
                .fill(PCJTheme.Paper.rule)
                .frame(width: 258 * scale, height: 0.5)
                .offset(x: 72 * scale, y: 462 * scale)
            centered(PCJTheme.Copy.begun, top: 486, size: 15, line: 22, color: .mute, italic: true)
            PageCurlJournalSeal(scale: scale)
                .offset(x: 149 * scale, y: 600 * scale)
        }
    }

    private func centered(
        _ text: String, top: CGFloat, size: CGFloat, line: CGFloat,
        color: PageCurlJournalTextColor, italic: Bool = false, tracking: CGFloat = 0
    ) -> some View {
        PageCurlJournalLabel(
            runs: [.init(text, size: size, color: color, italic: italic, tracking: tracking)],
            lineHeight: line,
            alignment: .center,
            scale: scale
        )
        .frame(width: 402 * scale, height: line * scale)
        .offset(y: top * scale)
    }
}

private struct PageCurlJournalFurniture: View {
    let head: String
    let folio: String
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalLabel(
                runs: [.init(head, size: 9.5, color: .mute, tracking: 2.09)],
                lineHeight: 13.3,
                alignment: .center,
                scale: scale
            )
            .frame(width: 402 * scale, height: 14 * scale)
            .offset(y: PCJTheme.Book.head * scale)
            PageCurlJournalLabel(
                runs: [.init(folio, size: 13, color: .mute, italic: true)],
                lineHeight: 18,
                alignment: .center,
                scale: scale
            )
            .frame(width: 402 * scale, height: 18 * scale)
            .offset(y: PCJTheme.Book.folio * scale)
        }
    }
}

private struct PageCurlJournalOrnament: View {
    let width: CGFloat
    let scale: CGFloat

    var body: some View {
        Canvas { context, size in
            let y = size.height / 2
            var lines = Path()
            lines.move(to: CGPoint(x: 0, y: y))
            lines.addLine(to: CGPoint(x: size.width / 2 - 10 * scale, y: y))
            lines.move(to: CGPoint(x: size.width / 2 + 10 * scale, y: y))
            lines.addLine(to: CGPoint(x: size.width, y: y))
            context.stroke(lines, with: .color(PCJTheme.Paper.rule), lineWidth: 0.8 * scale)

            let cx = size.width / 2
            var diamond = Path()
            diamond.move(to: CGPoint(x: cx, y: 1 * scale))
            diamond.addLine(to: CGPoint(x: cx + 4 * scale, y: 5 * scale))
            diamond.addLine(to: CGPoint(x: cx, y: 9 * scale))
            diamond.addLine(to: CGPoint(x: cx - 4 * scale, y: 5 * scale))
            diamond.closeSubpath()
            context.fill(diamond, with: .color(PCJTheme.Paper.accent))
            for dx in [-7.0, 7.0] {
                let dot = CGRect(x: cx + dx * scale - scale, y: y - scale, width: 2 * scale, height: 2 * scale)
                context.fill(Path(ellipseIn: dot), with: .color(PCJTheme.Paper.accent))
            }
        }
        .frame(width: width * scale, height: 10 * scale)
    }
}

private struct PageCurlJournalSeal: View {
    let scale: CGFloat

    var body: some View {
        ZStack {
            Circle().stroke(PCJTheme.Paper.rule, lineWidth: scale)
            Circle()
                .stroke(PCJTheme.Paper.rule, lineWidth: 0.6 * scale)
                .padding(5 * scale)
            VStack(spacing: 0) {
                PageCurlJournalLabel(
                    runs: [.init(PCJTheme.Copy.sealTop, size: 8.5, color: .accent, tracking: 1.87)],
                    lineHeight: 12,
                    alignment: .center,
                    scale: scale
                )
                .frame(height: 12 * scale)
                PageCurlJournalLabel(
                    runs: [.init(PCJTheme.Copy.sealMid, size: 19, color: .accent, italic: true)],
                    lineHeight: 24,
                    alignment: .center,
                    scale: scale
                )
                .frame(height: 24 * scale)
                PageCurlJournalLabel(
                    runs: [.init(PCJTheme.Copy.sealBottom, size: 8.5, color: .accent, tracking: 1.87)],
                    lineHeight: 12,
                    alignment: .center,
                    scale: scale
                )
                .frame(height: 12 * scale)
            }
        }
        .frame(width: 104 * scale, height: 104 * scale)
    }
}

enum PageCurlJournalTextColor {
    case ink, mute, accent, foil

    var color: UIColor {
        switch self {
        case .ink: UIColor(red: 43 / 255, green: 36 / 255, blue: 28 / 255, alpha: 1)
        case .mute: UIColor(red: 124 / 255, green: 110 / 255, blue: 91 / 255, alpha: 1)
        case .accent: UIColor(red: 138 / 255, green: 59 / 255, blue: 46 / 255, alpha: 1)
        case .foil: UIColor(red: 107 / 255, green: 78 / 255, blue: 31 / 255, alpha: 1)
        }
    }
}

struct PageCurlJournalTextRun {
    let text: String
    let size: CGFloat
    let color: PageCurlJournalTextColor
    let italic: Bool
    let tracking: CGFloat

    init(_ text: String, size: CGFloat, color: PageCurlJournalTextColor, italic: Bool = false, tracking: CGFloat = 0) {
        self.text = text
        self.size = size
        self.color = color
        self.italic = italic
        self.tracking = tracking
    }
}

/// UILabel gives the port fixed line boxes, inline run-in capitals and true justified paragraphs like React Native.
struct PageCurlJournalLabel: UIViewRepresentable {
    let runs: [PageCurlJournalTextRun]
    let lineHeight: CGFloat
    let alignment: NSTextAlignment
    let scale: CGFloat

    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.backgroundColor = .clear
        label.numberOfLines = 0
        label.lineBreakMode = .byWordWrapping
        label.adjustsFontForContentSizeCategory = false
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return label
    }

    func updateUIView(_ label: UILabel, context: Context) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.minimumLineHeight = lineHeight * scale
        paragraph.maximumLineHeight = lineHeight * scale
        paragraph.alignment = alignment
        paragraph.lineBreakMode = .byWordWrapping

        let text = NSMutableAttributedString()
        for run in runs {
            text.append(NSAttributedString(
                string: run.text,
                attributes: [
                    .font: PageCurlJournalAssets.font(size: run.size * scale, italic: run.italic),
                    .foregroundColor: run.color.color,
                    .kern: run.tracking * scale,
                    .paragraphStyle: paragraph,
                ]
            ))
        }
        label.attributedText = text
        label.textAlignment = alignment
    }
}
