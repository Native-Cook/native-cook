import Foundation

/// What a card shows: a photo printed in black and white or in lime, or a piece of the identity.
struct DepthFieldGalleryCard {
    enum Print {
        case mono
        case lime
    }

    enum Glyph {
        case search
        case expand
        case figure
        case basket
    }

    /// A photo held at a size fit for the field, and the full-size copy fetched for the card in view.
    struct Photo {
        var uri: String
        var full: String?
    }

    enum Kind {
        case photo(Photo, print: Print)
        /// A print in a frame: two lines of type over a portrait.
        case poster(Photo, headline: [String])
        case dots(pattern: Int)
        case glyph(Glyph)
        /// Two glassy rows, each led by a dot.
        case chip(rows: [String])
        case mark(word: String)
    }

    var title: String
    var note: String
    var kind: Kind

    var photo: Photo? {
        switch kind {
        case let .photo(photo, _), let .poster(photo, _): photo
        default: nil
        }
    }

    /// The print a photo is set in; a poster's portrait is always black and white.
    var print: Print {
        if case let .photo(_, print) = kind { return print }
        return .mono
    }

    /// Its width in units (about a card's width) and its height over its width.
    var size: (width: Double, aspect: Double) {
        switch kind {
        case .photo: (0.9, 1.22)
        case .poster: (0.85, 1.32)
        case .dots: (0.78, 1.12)
        case .glyph: (0.8, 1.18)
        case .chip: (0.72, 0.78)
        case .mark: (1, 0.62)
        }
    }
}

/// A dot of a card's art, in card widths about the card's middle.
struct DepthFieldGalleryDot {
    var x: Double
    var y: Double
    var r: Double
}

enum DepthFieldGalleryArt {
    // Three clusters of dots, bigger towards the top right.
    static let patterns: [[DepthFieldGalleryDot]] = [
        [
            .init(x: -0.3, y: -0.16, r: 0.035),
            .init(x: -0.12, y: -0.17, r: 0.075),
            .init(x: 0.14, y: -0.19, r: 0.115),
            .init(x: -0.24, y: 0, r: 0.05),
            .init(x: -0.02, y: 0.01, r: 0.075),
            .init(x: 0.21, y: 0.04, r: 0.075),
            .init(x: -0.17, y: 0.18, r: 0.035),
            .init(x: 0.04, y: 0.21, r: 0.05),
            .init(x: 0.21, y: 0.24, r: 0.035),
        ],
        [
            .init(x: -0.26, y: -0.2, r: 0.03),
            .init(x: -0.05, y: -0.2, r: 0.055),
            .init(x: 0.17, y: -0.16, r: 0.09),
            .init(x: -0.18, y: -0.03, r: 0.07),
            .init(x: 0.06, y: 0.02, r: 0.1),
            .init(x: -0.27, y: 0.14, r: 0.035),
            .init(x: -0.06, y: 0.19, r: 0.05),
            .init(x: 0.18, y: 0.2, r: 0.04),
        ],
        [
            .init(x: -0.2, y: -0.22, r: 0.05),
            .init(x: 0.04, y: -0.2, r: 0.035),
            .init(x: 0.2, y: -0.12, r: 0.06),
            .init(x: -0.25, y: -0.04, r: 0.035),
            .init(x: -0.05, y: -0.02, r: 0.11),
            .init(x: 0.2, y: 0.08, r: 0.075),
            .init(x: -0.2, y: 0.17, r: 0.06),
            .init(x: 0.02, y: 0.22, r: 0.035),
        ],
    ]

    /// An icon drawn in evenly spaced dots.
    static func dotted(_ glyph: DepthFieldGalleryCard.Glyph) -> [DepthFieldGalleryDot] {
        var dots: [DepthFieldGalleryDot] = []
        let size = 0.017
        func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ n: Int) {
            for i in 0..<n {
                let t = n == 1 ? 0 : Double(i) / Double(n - 1)
                dots.append(.init(x: x0 + (x1 - x0) * t, y: y0 + (y1 - y0) * t, r: size))
            }
        }
        func ring(_ cx: Double, _ cy: Double, _ radius: Double, _ n: Int) {
            for i in 0..<n {
                let a = Double(i) / Double(n) * .pi * 2
                dots.append(.init(x: cx + cos(a) * radius, y: cy + sin(a) * radius, r: size))
            }
        }
        switch glyph {
        case .search:
            ring(0.03, -0.04, 0.2, 18)
            line(-0.15, 0.14, -0.25, 0.24, 3)
        case .expand:
            line(-0.07, -0.07, 0.07, -0.07, 4)
            line(-0.07, 0.07, 0.07, 0.07, 4)
            line(-0.07, -0.023, -0.07, 0.023, 2)
            line(0.07, -0.023, 0.07, 0.023, 2)
            for (sx, sy) in [(1.0, 1.0), (1, -1), (-1, 1), (-1, -1)] {
                line(sx * 0.12, sy * 0.12, sx * 0.28, sy * 0.28, 4)
                dots.append(.init(x: sx * 0.2, y: sy * 0.28, r: size))
                dots.append(.init(x: sx * 0.28, y: sy * 0.2, r: size))
            }
        case .figure:
            ring(0, -0.13, 0.1, 12)
            line(0, 0.03, 0, 0.27, 5)
            dots.append(.init(x: -0.06, y: 0.21, r: size))
            dots.append(.init(x: 0.06, y: 0.21, r: size))
        case .basket:
            line(-0.27, -0.02, 0.27, -0.02, 9)
            line(-0.24, 0.05, -0.18, 0.22, 3)
            line(0.24, 0.05, 0.18, 0.22, 3)
            line(-0.11, 0.22, 0.11, 0.22, 3)
            line(-0.1, -0.09, -0.04, -0.21, 3)
            line(0.1, -0.09, 0.04, -0.21, 3)
        }
        return dots
    }

    /// The dots a card is drawn with, if it is drawn in dots.
    static func dots(for card: DepthFieldGalleryCard) -> [DepthFieldGalleryDot] {
        switch card.kind {
        case let .dots(pattern): patterns[pattern % patterns.count]
        case let .glyph(glyph): dotted(glyph)
        default: []
        }
    }
}

// Every photo is held at a size fit for the field; the card the camera stops at is fetched again
// at full size, so it is sharp across most of the screen without holding fifty large photos.
private func depthFieldGalleryUnsplash(_ id: String, _ w: Int) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(w)&h=\(Int((Double(w) * 1.25).rounded()))&q=82"
}

private func depthFieldGalleryPhoto(_ id: String) -> DepthFieldGalleryCard.Photo {
    .init(uri: depthFieldGalleryUnsplash(id, 480), full: depthFieldGalleryUnsplash(id, 1000))
}

extension DepthFieldGalleryCard {
    static let samples: [DepthFieldGalleryCard] = [
        .init(title: "Signal", note: "Pattern, lime", kind: .dots(pattern: 0)),
        .init(title: "Grey Room", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1684598273432-8f8e882cd16f"), print: .mono)),
        .init(title: "Outline", note: "Profile, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1676195470090-7c90bf539b3b"), print: .lime)),
        .init(title: "Ninety", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1685041552051-13de9e56228c"), print: .mono)),
        .init(title: "Antique", note: "Sculpture, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1729008712499-5e6b58420789"), print: .mono)),
        .init(title: "Atelier", note: "Fashion, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1717765698239-88731a968d20"), print: .mono)),
        .init(title: "Find", note: "Icon, dotted", kind: .glyph(.search)),
        .init(title: "Low Key", note: "Portrait, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1587987746776-302404b98970"), print: .lime)),
        .init(title: "Edge", note: "Poster, framed", kind: .poster(depthFieldGalleryPhoto("photo-1508153460964-48ffffcb0829"), headline: ["Light finds", "its level"])),
        .init(title: "Aloft", note: "Wordmark", kind: .mark(word: "aloft")),
        .init(title: "Relic", note: "Sculpture, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1565170556450-bdfc81864ca1"), print: .mono)),
        .init(title: "Noir", note: "Fashion, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1592833578500-1082e18665a3"), print: .lime)),
        .init(title: "Plaster", note: "Sculpture, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1604854574894-1be73ca43cb8"), print: .lime)),
        .init(title: "Gaze", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1506863530036-1efeddceb993"), print: .mono)),
        .init(title: "Contour", note: "Profile, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1592479950461-2c8ef29f2a14"), print: .mono)),
        .init(title: "Controls", note: "Interface", kind: .chip(rows: ["Lift off", "Glide"])),
        .init(title: "Bust", note: "Sculpture, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1612753666134-2792aa809104"), print: .lime)),
        .init(title: "Contrast", note: "Fashion, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1754315052701-0b98f6ba786b"), print: .mono)),
        .init(title: "Expand", note: "Icon, dotted", kind: .glyph(.expand)),
        .init(title: "Linen", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1606143412458-acc5f86de897"), print: .mono)),
        .init(title: "Shadow", note: "Profile, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1611034540516-665df2bbdfd9"), print: .lime)),
        .init(title: "Focus", note: "Poster, framed", kind: .poster(depthFieldGalleryPhoto("photo-1629511565591-a1d494ad6c58"), headline: ["Where depth", "meets focus"])),
        .init(title: "Classic", note: "Sculpture, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1673707180677-6e3605339daa"), print: .mono)),
        .init(title: "Grain", note: "Fashion, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1565754121773-b185234efb37"), print: .mono)),
        .init(title: "Swarm", note: "Pattern, lime", kind: .dots(pattern: 1)),
        .init(title: "Dusk", note: "Portrait, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1690376647869-45b2e9987018"), print: .lime)),
        .init(title: "Silhouette", note: "Profile, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1540304801084-0944beb20de7"), print: .mono)),
        .init(title: "Profile", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1657412235453-8adb25a45cd0"), print: .mono)),
        .init(title: "Cast", note: "Sculpture, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1781707870248-e2607e82bac0"), print: .mono)),
        .init(title: "Cut Out", note: "Poster, framed", kind: .poster(depthFieldGalleryPhoto("photo-1574015974293-817f0ebebb74"), headline: ["Still air,", "clear sight"])),
        .init(title: "Pilot", note: "Icon, dotted", kind: .glyph(.figure)),
        .init(title: "Hush", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1563170446-9c3c0622d8a9"), print: .mono)),
        .init(title: "Turn", note: "Profile, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1584462746497-276f4aeb9fca"), print: .mono)),
        .init(title: "Stem", note: "Still life, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1471086569966-db3eebc25a59"), print: .lime)),
        .init(title: "Chalk", note: "Sculpture, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1637246344338-2afcfb02e766"), print: .lime)),
        .init(title: "Swing", note: "Fashion, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1596993100471-c3905dafa78e"), print: .mono)),
        .init(title: "Status", note: "Interface", kind: .chip(rows: ["Ascend", "Hold"])),
        .init(title: "Glance", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1612928414075-bc722ade44f1"), print: .mono)),
        .init(title: "Side", note: "Profile, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1663232176328-5430c668593e"), print: .lime)),
        .init(title: "Mask", note: "Sculpture, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1561910377-c2a014ffd1cd"), print: .mono)),
        .init(title: "Gesso", note: "Sculpture, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1682422911441-a4afa4ddf659"), print: .mono)),
        .init(title: "Halo", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1535713875002-d1d0cf377fde"), print: .mono)),
        .init(title: "Carry", note: "Icon, dotted", kind: .glyph(.basket)),
        .init(title: "Line", note: "Profile, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1704202632056-de2c85caf4cf"), print: .lime)),
        .init(title: "Matte", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1768247695735-e10baa73e917"), print: .mono)),
        .init(title: "Pulse", note: "Pattern, lime", kind: .dots(pattern: 2)),
        .init(title: "Rim", note: "Profile, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1627626487034-0a3dc7278c18"), print: .mono)),
        .init(title: "Studio", note: "Portrait, lime print", kind: .photo(depthFieldGalleryPhoto("photo-1631002724711-d1ae3cf651b0"), print: .lime)),
        .init(title: "Soft Box", note: "Portrait, black and white", kind: .photo(depthFieldGalleryPhoto("photo-1649058047388-b49c028df9ac"), print: .mono)),
    ]
}
