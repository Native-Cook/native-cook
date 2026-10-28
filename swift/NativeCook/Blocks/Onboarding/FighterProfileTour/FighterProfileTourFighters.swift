import SwiftUI
import UIKit

/// The five fighters and their stats, ported value for value from `fighters.ts`.
struct FighterProfileTourFighter {
    let key: String
    let name: String
    let tier: String
    let series: String
    /// SF Symbol standing in for the Ionicons glyph of the series.
    let seriesIcon: String
    let art: String
    /// Art size in pixels, for fitting the full figure.
    let artSize: CGSize
    /// The bust a roster card shows: the art's rect inside the 149×112 art box at the card's right.
    let rowFit: CGRect
    /// Weight, run speed, dash speed, air speed, fall speed: displayed value and 0–1 bar.
    let stats: [(value: String, amount: Double)]
    /// Matchup metrics Weight, Air, Run, Dash (0–1), compared against the selected fighter.
    let metrics: [Double]
    let counters: [String]
    /// Page colour (top, middle, bottom).
    let page: [UInt32]
    /// The light accent used for icons and secondary text.
    let accent: UInt32

    var image: UIImage { UIImage(named: art) ?? UIImage() }
}

enum FighterProfileTourData {
    static let statLabels = ["Weight", "Run speed", "Dash speed", "Air speed", "Fall speed"]
    /// SF Symbols for Ionicons barbell, walk, chevron-forward, paper-plane, arrow-down.
    static let statIcons = ["dumbbell.fill", "figure.walk", "chevron.forward", "paperplane.fill", "arrow.down"]
    static let metricLabels = ["Weight", "Air", "Run", "Dash"]
    private static let tierNote = ["B": "above average", "C": "average", "D": "below average", "E": "well below average"]

    static let fighters: [FighterProfileTourFighter] = [
        FighterProfileTourFighter(key: "villager", name: "Villager", tier: "E", series: "Animal Crossing", seriesIcon: "leaf.fill",
            art: "FighterProfileTour-villager", artSize: CGSize(width: 1254, height: 1254),
            rowFit: CGRect(x: -18, y: -3, width: 157, height: 157),
            stats: [("92", 0.43), ("1.40", 0.30), ("1.70", 0.61), ("0.99", 0.46), ("1.42", 0.40)],
            metrics: [0.43, 0.46, 0.30, 0.61], counters: ["bowser", "puff"],
            page: [0x2544c5, 0x233fc0, 0x213aaf], accent: 0x82b5ff),
        FighterProfileTourFighter(key: "lucas", name: "Lucas", tier: "C", series: "EarthBound", seriesIcon: "music.note",
            art: "FighterProfileTour-lucas", artSize: CGSize(width: 1254, height: 1254),
            rowFit: CGRect(x: -13, y: -2, width: 175, height: 188),
            stats: [("82", 0.38), ("1.55", 0.45), ("1.82", 0.68), ("1.03", 0.50), ("1.58", 0.52)],
            metrics: [0.38, 0.50, 0.45, 0.68], counters: ["rosa", "bowser"],
            page: [0xc4473b, 0xb63e34, 0x9e332b], accent: 0xffc0b5),
        FighterProfileTourFighter(key: "puff", name: "Jigglypuff", tier: "D", series: "Pokémon", seriesIcon: "sparkles",
            art: "FighterProfileTour-jigglypuff", artSize: CGSize(width: 890, height: 854),
            rowFit: CGRect(x: 0, y: 0, width: 150, height: 144),
            stats: [("68", 0.12), ("1.10", 0.18), ("1.43", 0.45), ("1.35", 1), ("0.98", 0.10)],
            metrics: [0.12, 1, 0.18, 0.45], counters: ["bowser", "lucas"],
            page: [0xc24a96, 0xb4428a, 0x9c3776], accent: 0xffc6ea),
        FighterProfileTourFighter(key: "rosa", name: "Rosalina", tier: "D", series: "Super Mario Galaxy", seriesIcon: "star.fill",
            art: "FighterProfileTour-rosalina", artSize: CGSize(width: 1376, height: 1606),
            rowFit: CGRect(x: -22, y: -8, width: 193, height: 225),
            stats: [("82", 0.38), ("1.80", 0.62), ("2.00", 0.78), ("1.24", 0.80), ("1.17", 0.22)],
            metrics: [0.38, 0.80, 0.62, 0.78], counters: ["lucas", "villager"],
            page: [0x238f9c, 0x208491, 0x1a707c], accent: 0xa2eef5),
        FighterProfileTourFighter(key: "bowser", name: "Bowser", tier: "C", series: "Super Mario", seriesIcon: "flame.fill",
            art: "FighterProfileTour-bowser", artSize: CGSize(width: 1386, height: 1384),
            rowFit: CGRect(x: -12, y: -8, width: 174, height: 174),
            stats: [("135", 1), ("1.80", 0.62), ("2.05", 0.80), ("1.10", 0.63), ("1.86", 0.75)],
            metrics: [1, 0.63, 0.62, 0.80], counters: ["puff", "rosa"],
            page: [0x2f7f52, 0x2a744b, 0x226240], accent: 0xaef0c4),
    ]

    static let index: [String: Int] = ["villager": 0, "lucas": 1, "puff": 2, "rosa": 3, "bowser": 4]

    static func summary(_ fighter: FighterProfileTourFighter) -> String {
        let article = fighter.tier == "E" ? "an" : "a"
        let note = tierNote[fighter.tier] ?? ""
        return "Super Smash Bros. Ultimate \(fighter.name) is from the \(fighter.series) series and ranks as \(article) \(fighter.tier) Tier pick (\(note))."
    }

    /// The full figure, contained in `box` and standing on its bottom edge.
    static func standIn(_ fighter: FighterProfileTourFighter, _ box: CGRect) -> CGRect {
        let ratio = fighter.artSize.width / fighter.artSize.height
        let width = min(box.width, box.height * ratio)
        let height = width / ratio
        return CGRect(x: box.minX + (box.width - width) / 2, y: box.minY + box.height - height, width: width, height: height)
    }

    /// The figure contained in `box` and centred in it.
    static func containIn(_ fighter: FighterProfileTourFighter, _ box: CGRect) -> CGRect {
        let ratio = fighter.artSize.width / fighter.artSize.height
        let width = min(box.width, box.height * ratio)
        let height = width / ratio
        return CGRect(x: box.minX + (box.width - width) / 2, y: box.minY + (box.height - height) / 2, width: width, height: height)
    }
}
