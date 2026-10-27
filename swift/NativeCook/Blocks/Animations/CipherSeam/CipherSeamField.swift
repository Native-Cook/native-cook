import Foundation

/// The seeded data of Cipher Seam, generated with the same formulas as the React Native file so
/// every dust point, spark and token glyph lands where it does there.
enum CipherSeamField {
    /// One dust point (`DUST[i]`, minus the layer, which is the array it sits in).
    struct Dust {
        let x: Double
        let y: Double
        let radius: Double
        let phase: Double
    }

    /// One spark (`SPARKS[i]`); `index` is `i`, which the spark's wobble reads.
    struct Spark {
        let index: Int
        let y: Double
        let speed: Double
        let offset: Double
        let radius: Double
        let reach: Double
    }

    /// 660 fine points in three brightness layers: layer `k` holds the points with `i % 3 == k`.
    static let dust: [[Dust]] = (0..<3).map { layer in
        stride(from: layer, to: 660, by: 3).map { i in
            Dust(
                x: CipherSeamMath.hash(i + 1),
                y: CipherSeamMath.hash(i + 501) + CipherSeamMath.hash(i + 701) - 1,
                radius: 0.42 + pow(CipherSeamMath.hash(i + 901), 4.0) * 0.7,
                phase: CipherSeamMath.hash(i + 1301) * Double.pi * 2
            )
        }
    }

    /// 320 sparks that peel off the seam while the card crosses.
    static let sparks: [Spark] = (0..<320).map { i in
        Spark(
            index: i,
            y: CipherSeamMath.hash(i + 2101),
            speed: 0.7 + CipherSeamMath.hash(i + 2301) * 1.1,
            offset: CipherSeamMath.hash(i + 2501),
            radius: 0.5 + CipherSeamMath.hash(i + 2701) * 0.8,
            reach: CipherSeamMath.hash(i + 2901)
        )
    }

    /// `CHARACTERS`.
    private static let characters: [Character] = Array("08#0B8D0#8O08B#0")

    /// `GLYPHS`: eight phases of the token matrix, each 16 rows of 35 glyphs.
    static let glyphRows: [[String]] = (0..<8).map { phase in
        (0..<16).map { row in
            CipherSeamField.rowString(row: row, phase: phase)
        }
    }

    private static func rowString(row: Int, phase: Int) -> String {
        var glyphs: [Character] = []
        for column in 0..<35 {
            let index = (row * 7 + column * 11 + row * column + phase * (column % 3)) % 16
            glyphs.append(characters[index])
        }
        return String(glyphs)
    }
}
