import SwiftUI

/// The text area as paper: the card, the scan line, the placeholder, the note and the paste control, in screen
/// coordinates. The live screen draws it from the loop time; the fold snapshots it at two frozen times.
struct PastePlacesImportCardFace: View {
    let time: Double
    let layout: PastePlacesImportLayout
    var pastePress = 0.0
    var hasDraft = false

    var body: some View {
        ZStack(alignment: .topLeading) {
            textCard
            note
            pasteControl
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    private var textCard: some View {
        let card = PastePlacesImportTheme.card
        let scan = PastePlacesImportMotion.scan(at: time)
        let scanY = PastePlacesImportMotion.interpolate(
            PastePlacesImportTheme.Note.scanTop,
            PastePlacesImportTheme.Note.scanBottom,
            scan.progress
        )
        let trail = layout.px(PastePlacesImportTheme.Note.scanTrail)
        let stroke = layout.px(PastePlacesImportTheme.Note.scanStroke)
        let cardWidth = layout.px(card.width)
        let cardHeight = layout.px(card.height)

        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: layout.px(PastePlacesImportTheme.Radius.card), style: .circular)
                .fill(PastePlacesImportTheme.white)
                .overlay {
                    RoundedRectangle(cornerRadius: layout.px(PastePlacesImportTheme.Radius.card), style: .circular)
                        .strokeBorder(PastePlacesImportTheme.cardBorder, lineWidth: layout.px(PastePlacesImportTheme.hairline))
                }
                .frame(width: cardWidth, height: cardHeight)

            ZStack(alignment: .bottom) {
                LinearGradient(
                    colors: [.clear, PastePlacesImportTheme.detect.opacity(0.12)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                Rectangle()
                    .fill(PastePlacesImportTheme.detect.opacity(0.7))
                    .frame(height: stroke)
            }
            .frame(width: cardWidth, height: trail + stroke)
            .offset(y: layout.y(scanY) - layout.y(card.y) - trail)
            .opacity(scan.opacity)
        }
        .frame(width: cardWidth, height: cardHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: layout.px(PastePlacesImportTheme.Radius.card), style: .circular))
        .position(x: layout.px(card.x + card.width / 2), y: layout.y(card.y + card.height / 2))
    }

    private var note: some View {
        let boxes = layout.noteWordBoxes()
        let prefixes = layout.notePrefixWidths()
        let suffixes = layout.noteSuffixWidths()
        return ZStack(alignment: .topLeading) {
            placeholder
            ForEach(Array(PastePlacesImportTheme.noteLines.enumerated()), id: \.element) { index, line in
                noteLine(
                    line,
                    index: index,
                    wordBox: boxes[index],
                    prefixWidth: prefixes[index],
                    suffixWidth: suffixes[index]
                )
            }
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    private var placeholder: some View {
        let opacity = hasDraft ? 0 : PastePlacesImportMotion.placeholder(at: time)
        return ZStack(alignment: .topLeading) {
            ForEach(PastePlacesImportTheme.placeholderWords, id: \.self) { word in
                PastePlacesImportTextRun(
                    text: word.text,
                    fontName: PastePlacesImportTheme.textFontName,
                    size: PastePlacesImportTheme.placeholderSize,
                    tracking: word.tracking,
                    color: PastePlacesImportTheme.placeholder,
                    baseline: PastePlacesImportTheme.placeholderBaseline,
                    left: word.x,
                    layout: layout
                )
            }
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
        .opacity(opacity)
    }

    private func noteLine(
        _ line: PastePlacesImportTheme.NoteLine,
        index: Int,
        wordBox: PastePlacesImportLayout.Box,
        prefixWidth: Double,
        suffixWidth: Double
    ) -> some View {
        let lineBox = layout.lineBox(
            size: PastePlacesImportTheme.Note.size,
            baseline: PastePlacesImportTheme.Note.firstBaseline
                + Double(index) * PastePlacesImportTheme.Note.pitch
        )
        let amount = PastePlacesImportMotion.noteLine(at: time, index: index)
        let rise = PastePlacesImportMotion.noteInRise
        let mark = line.isCity
            ? PastePlacesImportMotion.cityLine(at: time)
            : PastePlacesImportMotion.highlight(at: time, pass: PastePlacesImportMotion.passes[index])
        let wordOpacity = line.isCity
            ? 1
            : PastePlacesImportMotion.word(at: time, pass: PastePlacesImportMotion.passes[index])

        return ZStack(alignment: .topLeading) {
            PastePlacesImportMeasuredText(
                text: line.prefix,
                fontName: PastePlacesImportTheme.textFontName,
                fontSize: lineBox.fontSize,
                color: PastePlacesImportTheme.noteInk,
                frame: CGRect(
                    x: layout.px(PastePlacesImportTheme.Note.left),
                    y: lineBox.top,
                    width: prefixWidth,
                    height: lineBox.lineHeight
                )
            )

            if line.isCity {
                Capsule()
                    .fill(PastePlacesImportTheme.detect)
                    .frame(
                        width: wordBox.width,
                        height: layout.px(PastePlacesImportTheme.Note.underlineStroke)
                    )
                    .scaleEffect(x: max(mark, 0.001), anchor: .leading)
                    .opacity(mark > 0 ? 1 : 0)
                    .position(
                        x: wordBox.x + wordBox.width / 2,
                        y: lineBox.top + lineBox.baselineInside
                            + layout.px(PastePlacesImportTheme.Note.underlineGap)
                            + layout.px(PastePlacesImportTheme.Note.underlineStroke) / 2
                    )
            } else {
                RoundedRectangle(
                    cornerRadius: layout.px(PastePlacesImportTheme.Note.markerRadius),
                    style: .circular
                )
                .fill(PastePlacesImportTheme.marker)
                .frame(
                    width: wordBox.width + 2 * layout.px(PastePlacesImportTheme.Note.markerOver),
                    height: lineBox.lineHeight
                        * (PastePlacesImportTheme.Note.markerBottom - PastePlacesImportTheme.Note.markerTop)
                )
                .scaleEffect(x: max(mark, 0.001), anchor: .leading)
                .opacity(mark > 0 ? 1 : 0)
                .position(
                    x: wordBox.x + wordBox.width / 2,
                    y: lineBox.top + lineBox.lineHeight
                        * (PastePlacesImportTheme.Note.markerTop + PastePlacesImportTheme.Note.markerBottom) / 2
                )
            }

            PastePlacesImportMeasuredText(
                text: line.place,
                fontName: PastePlacesImportTheme.textFontName,
                fontSize: lineBox.fontSize,
                color: PastePlacesImportTheme.noteInk,
                frame: CGRect(x: wordBox.x, y: lineBox.top, width: wordBox.width, height: lineBox.lineHeight)
            )
            .opacity(wordOpacity)

            PastePlacesImportMeasuredText(
                text: line.suffix,
                fontName: PastePlacesImportTheme.textFontName,
                fontSize: lineBox.fontSize,
                color: PastePlacesImportTheme.noteInk,
                frame: CGRect(
                    x: wordBox.x + wordBox.width,
                    y: lineBox.top,
                    width: suffixWidth,
                    height: lineBox.lineHeight
                )
            )
        }
        .opacity(amount)
        .offset(y: (1 - amount) * rise)
    }

    private var pasteControl: some View {
        let amount = pastePress
        let scale = PastePlacesImportMotion.interpolate(1, PastePlacesImportMotion.pressScale, amount)
        let clip = PastePlacesImportTheme.clipboard
        return ZStack(alignment: .topLeading) {
            PastePlacesImportTextRun(
                text: PastePlacesImportTheme.paste.text,
                systemWeight: PastePlacesImportTheme.paste.weight,
                size: PastePlacesImportTheme.paste.size,
                tracking: PastePlacesImportTheme.paste.tracking,
                color: PastePlacesImportTheme.ink,
                baseline: PastePlacesImportTheme.paste.baseline,
                left: PastePlacesImportTheme.paste.left,
                ascent: PastePlacesImportTheme.systemAscent,
                descent: PastePlacesImportTheme.systemDescent,
                layout: layout
            )
            PastePlacesImportClipboard(layout: layout)
                .frame(width: layout.px(clip.width), height: layout.px(clip.height))
                .position(x: layout.px(clip.x + clip.width / 2), y: layout.y(clip.y + clip.height / 2))
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
        .scaleEffect(scale, anchor: UnitPoint(
            x: layout.px(603) / layout.size.width,
            y: layout.y(2031) / layout.size.height
        ))
    }
}
