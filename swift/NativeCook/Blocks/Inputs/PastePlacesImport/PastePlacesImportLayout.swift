import SwiftUI
import UIKit

struct PastePlacesImportLayout {
    struct LineBox {
        let fontSize: Double
        let lineHeight: Double
        let top: Double
        let baselineInside: Double
    }

    struct Box {
        let x: Double
        let y: Double
        let width: Double
        let height: Double
    }

    /// A chip's own layout, pt, chip-local: size, the pin emoji's centre x and the label's left x.
    struct ChipSize {
        let width: Double
        let height: Double
        let pinCenterX: Double
        let labelX: Double
        let pinWidth: Double
    }

    private final class Geometry: NSObject {
        let noteWords: [Box]
        let chips: [ChipSize]
        let notePrefixes: [Double]
        let noteSuffixes: [Double]

        init(noteWords: [Box], chips: [ChipSize], notePrefixes: [Double], noteSuffixes: [Double]) {
            self.noteWords = noteWords
            self.chips = chips
            self.notePrefixes = notePrefixes
            self.noteSuffixes = noteSuffixes
        }
    }

    private static let geometryCache = NSCache<NSNumber, Geometry>()

    let size: CGSize
    let safeTop: Double
    let unit: Double
    let pointScale: Double
    let deltaY: Double

    init(size: CGSize, safeTop: Double) {
        self.size = size
        self.safeTop = safeTop
        unit = size.width / PastePlacesImportTheme.referenceWidth
        pointScale = size.width / PastePlacesImportTheme.referencePoints
        deltaY = safeTop - PastePlacesImportTheme.referenceInset * unit
    }

    func px(_ value: Double) -> Double { value * unit }
    func y(_ value: Double) -> Double { value * unit + deltaY }

    func lineBox(
        size: Double,
        baseline: Double,
        ascent: Double = PastePlacesImportTheme.anybodyAscent,
        descent: Double = PastePlacesImportTheme.anybodyDescent
    ) -> LineBox {
        let fontSize = size * pointScale
        let lineHeight = (fontSize * 1.4).rounded()
        let inset = (lineHeight - (ascent + descent) * fontSize) / 2
        let baselineInside = inset + ascent * fontSize
        return LineBox(
            fontSize: fontSize,
            lineHeight: lineHeight,
            top: y(baseline) - baselineInside,
            baselineInside: baselineInside
        )
    }

    func anybodyFont(name: String, size: Double) -> UIFont {
        PastePlacesImportAssets.font(name, size: size * pointScale)
    }

    func noteWordBoxes() -> [Box] { geometry().noteWords }
    func chipSizes() -> [ChipSize] { geometry().chips }
    func notePrefixWidths() -> [Double] { geometry().notePrefixes }
    func noteSuffixWidths() -> [Double] { geometry().noteSuffixes }

    private func geometry() -> Geometry {
        let key = NSNumber(value: (size.width * 1_000).rounded())
        if let cached = Self.geometryCache.object(forKey: key) { return cached }
        let noteFont = anybodyFont(
            name: PastePlacesImportTheme.textFontName,
            size: PastePlacesImportTheme.Note.size
        )
        let measured = Geometry(
            noteWords: makeNoteWordBoxes(),
            chips: makeChipSizes(),
            notePrefixes: PastePlacesImportTheme.noteLines.map {
                PastePlacesImportAssets.textWidth($0.prefix, font: noteFont)
            },
            noteSuffixes: PastePlacesImportTheme.noteLines.map {
                PastePlacesImportAssets.textWidth($0.suffix, font: noteFont)
            }
        )
        Self.geometryCache.setObject(measured, forKey: key)
        return measured
    }

    private func makeNoteWordBoxes() -> [Box] {
        let font = anybodyFont(name: PastePlacesImportTheme.textFontName, size: PastePlacesImportTheme.Note.size)
        return PastePlacesImportTheme.noteLines.enumerated().map { index, line in
            let box = lineBox(
                size: PastePlacesImportTheme.Note.size,
                baseline: PastePlacesImportTheme.Note.firstBaseline
                    + Double(index) * PastePlacesImportTheme.Note.pitch
            )
            let prefixWidth = PastePlacesImportAssets.textWidth(line.prefix, font: font)
            let wordWidth = PastePlacesImportAssets.textWidth(line.place, font: font)
            return Box(
                x: px(PastePlacesImportTheme.Note.left) + prefixWidth,
                y: box.top,
                width: wordWidth,
                height: box.lineHeight
            )
        }
    }

    private func makeChipSizes() -> [ChipSize] {
        let font = anybodyFont(name: PastePlacesImportTheme.textFontName, size: PastePlacesImportTheme.Chip.size)
        let pinFont = UIFont.systemFont(ofSize: PastePlacesImportTheme.Chip.pinSize * pointScale)
        let pinWidth = PastePlacesImportAssets.textWidth(PastePlacesImportTheme.pin.text, font: pinFont)
        return PastePlacesImportTheme.noteLines.dropFirst().map { line in
            let labelX = px(PastePlacesImportTheme.Chip.padLeft) + pinWidth + px(PastePlacesImportTheme.Chip.pinGap)
            return ChipSize(
                width: labelX
                    + PastePlacesImportAssets.textWidth(line.place, font: font)
                    + px(PastePlacesImportTheme.Chip.padRight),
                height: px(PastePlacesImportTheme.Chip.height),
                pinCenterX: px(PastePlacesImportTheme.Chip.padLeft) + pinWidth / 2,
                labelX: labelX,
                pinWidth: pinWidth
            )
        }
    }
}
