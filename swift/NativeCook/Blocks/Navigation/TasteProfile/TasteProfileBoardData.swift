import Foundation

/// The numbers the board shows per city, laid out like the pin's.
enum TasteProfileBoardData {
    typealias Glyph = TasteProfileTheme.Glyph
    typealias G = TasteProfileTheme.GlyphToken

    /// Each tile's number per city: the pin's glyphs for "all", laid out like the pin for the cities.
    static let statGlyphs: [[[Glyph]]] = G.statNumbers.enumerated().map { tile, run in
        TasteProfileMotion.stats.enumerated().map { city, row in
            city == 0 ? run.glyphs : TasteProfileTheme.layoutNumber(row[tile], x0: run.glyphs[0].inkLeft, digitH: run.digitH)
        }
    }

    static let countRun = G.c1470

    /// The photo count per city, right-aligned on the pin's last digit.
    static let countGlyphs: [[Glyph]] = {
        let last = countRun.glyphs[3]
        let right = last.inkLeft + last.inkWidth
        return TasteProfileMotion.photoCount.enumerated().map { city, value in
            if city == 0 { return countRun.glyphs }
            let k = countRun.digitH / 36
            var width = 0.0
            for ch in value { width += (G.digitWidth[ch, default: 33] * k).rounded() + G.digitGap }
            return TasteProfileTheme.layoutNumber(value, x0: right - (width - G.digitGap), digitH: countRun.digitH)
        }
    }()
}
