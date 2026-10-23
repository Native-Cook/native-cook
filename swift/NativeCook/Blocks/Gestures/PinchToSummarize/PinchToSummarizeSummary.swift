import SwiftUI
import UIKit

/// The revealed text and footer below the collapsed page. Every summary line is an independent view,
/// which preserves the source's streamed arrival instead of fading in a single paragraph at once.
struct PinchToSummarizeSummary: View {
    let stage: PinchToSummarizeStage
    let insetBottom: CGFloat
    let snapshot: PinchToSummarizeSnapshot
    let onShowArticle: () -> Void

    @State private var rows: [PinchToSummarizeSummaryRow]

    init(
        stage: PinchToSummarizeStage,
        insetBottom: CGFloat,
        snapshot: PinchToSummarizeSnapshot,
        onShowArticle: @escaping () -> Void
    ) {
        self.stage = stage
        self.insetBottom = insetBottom
        self.snapshot = snapshot
        self.onShowArticle = onShowArticle
        _rows = State(initialValue: PinchToSummarizeSummaryRows.make(width: stage.width))
    }

    static func revealTotal(for width: CGFloat) -> CGFloat {
        CGFloat(PinchToSummarizeSummaryRows.make(width: width).count)
            * CGFloat(PinchToSummarizeConstants.Timing.lineStagger)
            + CGFloat(PinchToSummarizeConstants.Timing.lineMs)
    }

    private var width: CGFloat {
        stage.width
    }

    private var revealTotal: CGFloat {
        CGFloat(rows.count) * CGFloat(PinchToSummarizeConstants.Timing.lineStagger)
            + CGFloat(PinchToSummarizeConstants.Timing.lineMs)
    }

    private var footerBottom: CGFloat {
        max(PinchToSummarizeTheme.Layout.footerBottom, insetBottom - PinchToSummarizeTheme.Layout.footerBottom)
    }

    var body: some View {
        let cardWidth = PinchToSummarizeGeometry.cardWidth(stage: stage)
        let titleOpacity = PinchToSummarizeGeometry.clamp01((snapshot.p - 0.9) / 0.1)
        let summaryTop = PinchToSummarizeGeometry.cardTop(lift: 1)
            + PinchToSummarizeTheme.Layout.cardHeight
            + PinchToSummarizeTheme.Layout.summaryGap
        let live = snapshot.mode == .summary

        ZStack(alignment: .topLeading) {
            PinchToSummarizeSummaryTitleRow()
                .frame(width: cardWidth, height: 16)
                .position(
                    x: width / 2,
                    y: PinchToSummarizeGeometry.cardTop(lift: snapshot.lift) - PinchToSummarizeTheme.Layout.titleRowGap + 8
                )
                .opacity(titleOpacity)
                .accessibilityHidden(true)
                .allowsHitTesting(false)

            PinchToSummarizeSummaryRowsView(
                rows: rows,
                snapshot: snapshot,
                revealTotal: revealTotal
            )
            .frame(width: width, height: max(0, stage.height - summaryTop - footerBottom - PinchToSummarizeTheme.Layout.footerHeight), alignment: .topLeading)
            .clipped()
            .offset(y: summaryTop)
            .accessibilityElement(children: live ? .contain : .ignore)
            .accessibilityLabel(live ? "Summary. \(PinchToSummarizeSummaryRows.summaryText)" : "")
            .accessibilityHidden(!live)
            .allowsHitTesting(false)

            PinchToSummarizeSummaryMorph(
                stage: stage,
                snapshot: snapshot,
                footerBottom: footerBottom
            )
            .accessibilityHidden(true)
            .allowsHitTesting(false)

            PinchToSummarizeSummaryFooter(
                snapshot: snapshot,
                footerBottom: footerBottom,
                revealTotal: revealTotal,
                onShowArticle: onShowArticle
            )
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
        .allowsHitTesting(live)
        .onChange(of: stage.width) { _, width in
            rows = PinchToSummarizeSummaryRows.make(width: width)
        }
    }
}

private struct PinchToSummarizeSummaryTitleRow: View {
    var body: some View {
        HStack(spacing: 6) {
            Text("M")
                .font(.system(size: 9, weight: .black))
                .foregroundStyle(PinchToSummarizeTheme.Colors.ink)
                .frame(width: 14, height: 14)
                .background(PinchToSummarizeTheme.Colors.summaryInk, in: .rect(cornerRadius: 3))
            Text(PinchToSummarizeTheme.Copy.pageTitle)
                .pinchToSummarizeSummaryText(PinchToSummarizeTheme.Typography.cardTitle)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct PinchToSummarizeSummaryRowsView: View {
    let rows: [PinchToSummarizeSummaryRow]
    let snapshot: PinchToSummarizeSnapshot
    let revealTotal: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(rows) { row in
                PinchToSummarizeSummaryLine(
                    row: row,
                    index: row.index,
                    reveal: snapshot.reveal,
                    alpha: snapshot.summaryAlpha,
                    revealTotal: revealTotal
                )
            }
        }
    }
}

private struct PinchToSummarizeSummaryLine: View {
    let row: PinchToSummarizeSummaryRow
    let index: Int
    let reveal: CGFloat
    let alpha: CGFloat
    let revealTotal: CGFloat

    private var reducedMotion: Bool {
        UIAccessibility.isReduceMotionEnabled
    }

    private var progress: CGFloat {
        if reducedMotion {
            return PinchToSummarizeGeometry.clamp01(reveal / max(1, revealTotal))
        }
        return PinchToSummarizeGeometry.clamp01(
            (reveal - CGFloat(index) * CGFloat(PinchToSummarizeConstants.Timing.lineStagger))
                / CGFloat(PinchToSummarizeConstants.Timing.lineMs)
        )
    }

    private var textStyle: PinchToSummarizeTheme.Typography.Style {
        switch row.kind {
        case .lead: PinchToSummarizeTheme.Typography.lead
        case .title: PinchToSummarizeTheme.Typography.itemTitle
        case .body: PinchToSummarizeTheme.Typography.itemBody
        }
    }

    private var finalColor: Color {
        row.kind == .body ? PinchToSummarizeTheme.Colors.summaryInkSoft : PinchToSummarizeTheme.Colors.summaryInk
    }

    var body: some View {
        let u = progress
        let lineAlpha = u * alpha
        let rise = reducedMotion ? 0 : (1 - u) * CGFloat(PinchToSummarizeConstants.Timing.lineRise)
        let ghostAlpha = reducedMotion ? 0 : CGFloat(PinchToSummarizeConstants.Timing.ghostAlpha) * sin(.pi * u) * alpha

        ZStack(alignment: .topLeading) {
            PinchToSummarizeSummaryTextLine(text: row.text, style: textStyle, color: PinchToSummarizeTheme.Colors.summaryArrive)
                .opacity(ghostAlpha)
                .offset(x: -(1 - u) * CGFloat(PinchToSummarizeConstants.Timing.ghostOffset), y: rise)
            PinchToSummarizeSummaryTextLine(text: row.text, style: textStyle, color: PinchToSummarizeTheme.Colors.summaryArrive)
                .opacity(ghostAlpha)
                .offset(x: (1 - u) * CGFloat(PinchToSummarizeConstants.Timing.ghostOffset), y: rise)
            PinchToSummarizeSummaryTextLine(
                text: row.text,
                style: textStyle,
                color: PinchToSummarizeSummaryColor.mix(
                    PinchToSummarizeTheme.Colors.summaryArrive,
                    finalColor,
                    amount: PinchToSummarizeGeometry.clamp01((u - 0.35) / 0.65)
                )
            )
            .opacity(lineAlpha)
            .offset(y: rise)

            if let icon = row.icon {
                PinchToSummarizeIcon(name: icon, size: 13, color: PinchToSummarizeTheme.Colors.iconGlyph)
                    .frame(width: PinchToSummarizeTheme.Layout.iconDisc, height: PinchToSummarizeTheme.Layout.iconDisc)
                    .background(PinchToSummarizeTheme.Colors.iconDisc, in: Circle())
                    .scaleEffect(
                        reducedMotion ? 1 : PinchToSummarizeSummaryCurve.easeOutBack(u, overshoot: 1.9)
                            * (1 - CGFloat(PinchToSummarizeConstants.Timing.discFrom))
                            + CGFloat(PinchToSummarizeConstants.Timing.discFrom),
                        anchor: .center
                    )
                    .offset(
                        x: PinchToSummarizeTheme.Layout.textInset - row.x,
                        y: (PinchToSummarizeTheme.Typography.itemTitle.lineHeight - PinchToSummarizeTheme.Layout.iconDisc) / 2
                    )
                    .opacity(lineAlpha)
            }
        }
        .frame(width: row.width + 8, height: row.lineHeight, alignment: .topLeading)
        .offset(x: row.x, y: row.y)
    }
}

private struct PinchToSummarizeSummaryTextLine: View {
    let text: String
    let style: PinchToSummarizeTheme.Typography.Style
    let color: Color

    var body: some View {
        Text(text)
            .pinchToSummarizeSummaryText(style, color: color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
    }
}

private struct PinchToSummarizeSummaryMorph: View {
    let stage: PinchToSummarizeStage
    let snapshot: PinchToSummarizeSnapshot
    let footerBottom: CGFloat

    var body: some View {
        let t = PinchToSummarizeGeometry.clamp01(snapshot.p)
        let e = t * t * (3 - 2 * t)
        let start = PinchToSummarizeTheme.Layout.toolbarPill
        let end = PinchToSummarizeTheme.Layout.creditPill
        let startX = stage.width / 2 - start.width / 2
        let startY = stage.height - PinchToSummarizeTheme.Layout.toolbarCenterFromBottom - start.height / 2
        let endX = PinchToSummarizeTheme.Layout.textInset - PinchToSummarizeTheme.Layout.creditPad
        let endY = stage.height - footerBottom - (PinchToSummarizeTheme.Layout.footerHeight - end.height) / 2 - end.height
        let width = start.width + (end.width - start.width) * e
        let height = start.height + (end.height - start.height) * e

        RoundedRectangle(cornerRadius: height / 2)
            .fill(PinchToSummarizeSummaryColor.mix(
                PinchToSummarizeTheme.Colors.toolbar,
                PinchToSummarizeTheme.Colors.closeFill,
                amount: e
            ))
            .frame(width: width, height: height)
            .offset(x: startX + (endX - startX) * e, y: startY + (endY - startY) * e)
            .opacity(
                PinchToSummarizeConstants.qaHold == nil
                    ? (UIAccessibility.isReduceMotionEnabled
                        ? PinchToSummarizeGeometry.clamp01((t - 0.9) / 0.1)
                        : PinchToSummarizeGeometry.clamp01(t / 0.25))
                    : 0
            )
    }
}

private struct PinchToSummarizeSummaryFooter: View {
    let snapshot: PinchToSummarizeSnapshot
    let footerBottom: CGFloat
    let revealTotal: CGFloat
    let onShowArticle: () -> Void

    private var footerOpacity: CGFloat {
        if PinchToSummarizeConstants.qaHold != nil {
            return snapshot.summaryAlpha * PinchToSummarizeGeometry.clamp01(
                (snapshot.reveal - revealTotal + CGFloat(PinchToSummarizeConstants.Timing.lineMs))
                    / CGFloat(PinchToSummarizeConstants.Timing.lineMs)
            )
        }
        return PinchToSummarizeGeometry.clamp01((snapshot.p - 0.92) / 0.08)
    }

    private var closeOpacity: CGFloat {
        PinchToSummarizeGeometry.clamp01((snapshot.p - 0.9) / 0.1)
    }

    var body: some View {
        HStack {
            ZStack(alignment: .leading) {
                HStack(spacing: 8) {
                    ZStack {
                        LinearGradient(
                            colors: PinchToSummarizeTheme.Colors.creditIcon,
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                        PinchToSummarizeIcon(name: "sparkles", size: 12, color: PinchToSummarizeTheme.Colors.closeInk)
                    }
                    .frame(width: 24, height: 24)
                    .clipShape(.rect(cornerRadius: 7))

                    VStack(alignment: .leading, spacing: 0) {
                        Text(PinchToSummarizeTheme.Copy.credit[0])
                            .pinchToSummarizeSummaryText(PinchToSummarizeTheme.Typography.credit)
                        Text(PinchToSummarizeTheme.Copy.credit[1])
                            .pinchToSummarizeSummaryText(PinchToSummarizeTheme.Typography.credit)
                    }
                }
                PinchToSummarizeSummarySweep(snapshot: snapshot, revealTotal: revealTotal)
                    .frame(
                        width: PinchToSummarizeTheme.Layout.creditPill.width,
                        height: PinchToSummarizeTheme.Layout.creditPill.height
                    )
                    .clipShape(Capsule())
                    .offset(x: -PinchToSummarizeTheme.Layout.creditPad)
                    .allowsHitTesting(false)
            }
            .frame(width: PinchToSummarizeTheme.Layout.creditPill.width, height: PinchToSummarizeTheme.Layout.creditPill.height, alignment: .leading)
            .opacity(footerOpacity)

            Spacer(minLength: 0)

            Button(action: onShowArticle) {
                PinchToSummarizeIcon(name: "close", size: 16, color: PinchToSummarizeTheme.Colors.closeInk)
            }
                .frame(width: PinchToSummarizeTheme.Layout.closeSize, height: PinchToSummarizeTheme.Layout.closeSize)
                .background(PinchToSummarizeTheme.Colors.closeFill, in: Circle())
                .contentShape(Rectangle().inset(by: -6))
                .accessibilityLabel("Close summary")
                .accessibilityHint("Shows the full article. You can also pinch out")
                .opacity(closeOpacity)
        }
        .padding(.trailing, 20)
        .frame(height: PinchToSummarizeTheme.Layout.footerHeight)
        .padding(.leading, PinchToSummarizeTheme.Layout.textInset)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .padding(.bottom, footerBottom)
    }
}

private struct PinchToSummarizeSummarySweep: View {
    let snapshot: PinchToSummarizeSnapshot
    let revealTotal: CGFloat

    var body: some View {
        let elapsed = snapshot.bloomAt < 0
            ? 0
            : (snapshot.clock - snapshot.bloomAt - revealTotal - CGFloat(PinchToSummarizeConstants.Timing.sweepDelay))
        let u = PinchToSummarizeGeometry.clamp01(elapsed / CGFloat(PinchToSummarizeConstants.Timing.sweepMs))
        let eased = u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2

        LinearGradient(
            colors: PinchToSummarizeTheme.Colors.sweep,
            startPoint: .leading,
            endPoint: .trailing
        )
        .frame(width: PinchToSummarizeTheme.Layout.sweepWidth)
        .offset(x: -PinchToSummarizeTheme.Layout.sweepWidth + eased * (PinchToSummarizeTheme.Layout.creditPill.width + PinchToSummarizeTheme.Layout.sweepWidth))
        .opacity(u > 0 && u < 1 ? 1 : 0)
        .frame(
            maxWidth: .infinity,
            maxHeight: .infinity,
            alignment: .leading
        )
    }
}

private struct PinchToSummarizeSummaryRow: Identifiable {
    enum Kind {
        case lead
        case title
        case body
    }

    let id: String
    let index: Int
    let text: String
    let x: CGFloat
    let y: CGFloat
    let width: CGFloat
    let lineHeight: CGFloat
    let kind: Kind
    let icon: String?
}

private enum PinchToSummarizeSummaryRows {
    static let summaryText = [
        PinchToSummarizeTheme.Copy.lead,
        PinchToSummarizeTheme.Copy.items
            .map { "\($0.title): \($0.body)" }
            .joined(separator: " ")
    ].joined(separator: " ")

    static func make(width: CGFloat) -> [PinchToSummarizeSummaryRow] {
        let left = PinchToSummarizeTheme.Layout.textInset
        let textWidth = width - left * 2
        let itemWidth = textWidth - PinchToSummarizeTheme.Layout.itemIndent
        var rows: [PinchToSummarizeSummaryRow] = []
        var y: CGFloat = 0

        append(
            split(PinchToSummarizeTheme.Copy.lead, width: textWidth, style: PinchToSummarizeTheme.Typography.lead),
            kind: .lead,
            x: left,
            width: textWidth,
            lineHeight: PinchToSummarizeTheme.Typography.lead.lineHeight,
            icon: nil,
            y: &y,
            rows: &rows
        )
        y += PinchToSummarizeTheme.Layout.leadGap

        for item in PinchToSummarizeTheme.Copy.items {
            append(
                split(item.title, width: itemWidth, style: PinchToSummarizeTheme.Typography.itemTitle),
                kind: .title,
                x: left + PinchToSummarizeTheme.Layout.itemIndent,
                width: itemWidth,
                lineHeight: PinchToSummarizeTheme.Typography.itemTitle.lineHeight,
                icon: item.icon,
                y: &y,
                rows: &rows
            )
            append(
                split(item.body, width: itemWidth, style: PinchToSummarizeTheme.Typography.itemBody),
                kind: .body,
                x: left + PinchToSummarizeTheme.Layout.itemIndent,
                width: itemWidth,
                lineHeight: PinchToSummarizeTheme.Typography.itemBody.lineHeight,
                icon: nil,
                y: &y,
                rows: &rows
            )
            y += PinchToSummarizeTheme.Layout.itemGap
        }
        return rows
    }

    private static func append(
        _ lines: [String],
        kind: PinchToSummarizeSummaryRow.Kind,
        x: CGFloat,
        width: CGFloat,
        lineHeight: CGFloat,
        icon: String?,
        y: inout CGFloat,
        rows: inout [PinchToSummarizeSummaryRow]
    ) {
        for (lineIndex, line) in lines.enumerated() {
            rows.append(PinchToSummarizeSummaryRow(
                id: "\(rows.count)-\(line)",
                index: rows.count,
                text: line,
                x: x,
                y: y,
                width: width,
                lineHeight: lineHeight,
                kind: kind,
                icon: lineIndex == 0 ? icon : nil
            ))
            y += lineHeight
        }
    }

    /// Mirrors the native iOS text engine's word wrapping so each streamed row is exactly one rendered line.
    private static func split(
        _ text: String,
        width: CGFloat,
        style: PinchToSummarizeTheme.Typography.Style
    ) -> [String] {
        let font = PinchToSummarizeSummaryFont.uiFont(for: style)
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineBreakMode = .byWordWrapping
        let storage = NSTextStorage(attributedString: NSAttributedString(
            string: text,
            attributes: [
                .font: font,
                .kern: style.tracking,
                .paragraphStyle: paragraph
            ]
        ))
        let layout = NSLayoutManager()
        let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        container.lineBreakMode = .byWordWrapping
        layout.addTextContainer(container)
        storage.addLayoutManager(layout)

        let glyphRange = layout.glyphRange(for: container)
        var lines: [String] = []
        layout.enumerateLineFragments(forGlyphRange: glyphRange) { _, _, _, lineGlyphRange, _ in
            let characterRange = layout.characterRange(forGlyphRange: lineGlyphRange, actualGlyphRange: nil)
            let line = (text as NSString).substring(with: characterRange)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !line.isEmpty {
                lines.append(line)
            }
        }
        return lines.isEmpty ? [text] : lines
    }
}

private enum PinchToSummarizeSummaryFont {
    static func uiFont(for style: PinchToSummarizeTheme.Typography.Style) -> UIFont {
        if style.family == "Georgia" {
            let name: String
            switch style.weight {
            case .bold, .heavy, .black:
                name = "Georgia-Bold"
            default:
                name = "Georgia"
            }
            return UIFont(name: name, size: style.size) ?? UIFont.systemFont(ofSize: style.size, weight: fontWeight(style.weight))
        }
        return UIFont.systemFont(ofSize: style.size, weight: fontWeight(style.weight))
    }

    static func additionalLeading(for style: PinchToSummarizeTheme.Typography.Style) -> CGFloat {
        guard style.lineHeight > 0 else { return 0 }
        return max(0, style.lineHeight - uiFont(for: style).lineHeight)
    }

    private static func fontWeight(_ weight: Font.Weight) -> UIFont.Weight {
        switch weight {
        case .ultraLight: .ultraLight
        case .thin: .thin
        case .light: .light
        case .medium: .medium
        case .semibold: .semibold
        case .bold: .bold
        case .heavy: .heavy
        case .black: .black
        default: .regular
        }
    }
}

private enum PinchToSummarizeSummaryCurve {
    static func easeOutBack(_ value: CGFloat, overshoot: CGFloat) -> CGFloat {
        let u = value - 1
        return 1 + (overshoot + 1) * u * u * u + overshoot * u * u
    }
}

private enum PinchToSummarizeSummaryColor {
    static func mix(_ from: Color, _ to: Color, amount: CGFloat) -> Color {
        let source = UIColor(from)
        let destination = UIColor(to)
        var sr: CGFloat = 0
        var sg: CGFloat = 0
        var sb: CGFloat = 0
        var sa: CGFloat = 0
        var dr: CGFloat = 0
        var dg: CGFloat = 0
        var db: CGFloat = 0
        var da: CGFloat = 0
        source.getRed(&sr, green: &sg, blue: &sb, alpha: &sa)
        destination.getRed(&dr, green: &dg, blue: &db, alpha: &da)
        return Color(
            red: gammaMix(sr, dr, amount),
            green: gammaMix(sg, dg, amount),
            blue: gammaMix(sb, db, amount),
            opacity: sa + (da - sa) * amount
        )
    }

    private static func gammaMix(_ from: CGFloat, _ to: CGFloat, _ amount: CGFloat) -> CGFloat {
        let gamma: CGFloat = 2.2
        return pow(pow(from, gamma) + (pow(to, gamma) - pow(from, gamma)) * amount, 1 / gamma)
    }
}

private extension View {
    func pinchToSummarizeSummaryText(
        _ style: PinchToSummarizeTheme.Typography.Style,
        color: Color? = nil
    ) -> some View {
        font(style.family.map { .custom($0, size: style.size).weight(style.weight) } ?? .system(size: style.size, weight: style.weight))
            .tracking(style.tracking)
            .lineSpacing(PinchToSummarizeSummaryFont.additionalLeading(for: style))
            .foregroundStyle(color ?? style.color)
    }
}
