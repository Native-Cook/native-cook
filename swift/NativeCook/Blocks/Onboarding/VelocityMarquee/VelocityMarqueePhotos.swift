import Foundation

/// One photo on a row: an Unsplash id and its orientation, which sets the tile's width.
struct VelocityMarqueePhoto {
    var id: String
    var portrait: Bool

    /// Photo URL at a width in pixels, cropped to the photo's own aspect.
    func url(width: Int) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(width)&q=80"
    }
}

private func velocityMarqueeP(_ id: String) -> VelocityMarqueePhoto { VelocityMarqueePhoto(id: "photo-\(id)", portrait: true) }
private func velocityMarqueeL(_ id: String) -> VelocityMarqueePhoto { VelocityMarqueePhoto(id: "photo-\(id)", portrait: false) }

extension VelocityMarqueePhoto {
    /// Three rows of twelve, top to bottom. Chrome-and-pastel snapshots, pastel still lifes and
    /// soft 3D renders are dealt across the rows so neighbours never share a palette.
    static let rows: [[VelocityMarqueePhoto]] = [
        [
            velocityMarqueeP("1568191009535-7d8b407d389a"),
            velocityMarqueeL("1690627931183-991bd45dc2f4"),
            velocityMarqueeL("1622737133809-d95047b9e673"),
            velocityMarqueeP("1619922800069-098b291b8f17"),
            velocityMarqueeL("1693225113452-8ab5cf5f6e7f"),
            velocityMarqueeP("1619045119136-349759036541"),
            velocityMarqueeL("1551499779-ee50f1aa4d25"),
            velocityMarqueeL("1622547748225-3fc4abd2cca0"),
            velocityMarqueeP("1724606854879-9321f9340a89"),
            velocityMarqueeL("1690627932216-3843a4b4764c"),
            velocityMarqueeP("1653481351422-91ceed9c94bd"),
            velocityMarqueeL("1499796683658-b659bc751db1"),
        ],
        [
            velocityMarqueeL("1627637819794-fba32f82be16"),
            velocityMarqueeP("1563291074-2bf8677ac0e5"),
            velocityMarqueeL("1702285566327-2df5bc5b4e67"),
            velocityMarqueeP("1712917841321-a491f1924d09"),
            velocityMarqueeL("1672080070762-764c74ee1227"),
            velocityMarqueeP("1562048048-86d659689440"),
            velocityMarqueeL("1544194215-541c2d3561a4"),
            velocityMarqueeL("1702285566373-bb7744adcbe7"),
            velocityMarqueeP("1521133573892-e44906baee46"),
            velocityMarqueeL("1651611243377-2c15b94ad613"),
            velocityMarqueeP("1743428287676-c3ea53dabf6d"),
            velocityMarqueeL("1650943574955-ac02c65cfc71"),
        ],
        [
            velocityMarqueeP("1579546928686-286c9fbde1ec"),
            velocityMarqueeL("1671519821564-ced7e41ee7ae"),
            velocityMarqueeL("1702285566188-00af76abde38"),
            velocityMarqueeP("1609414558965-3bec66c06de7"),
            velocityMarqueeL("1458682625221-3a45f8a844c7"),
            velocityMarqueeL("1617791160505-6f00504e3519"),
            velocityMarqueeP("1653549892798-c8596ad08ee6"),
            velocityMarqueeL("1671509429700-fe3137bb9ab9"),
            velocityMarqueeP("1629786270823-3440bd45d1d5"),
            velocityMarqueeL("1618172193763-c511deb635ca"),
            velocityMarqueeL("1680779487014-2b587dba40c6"),
            velocityMarqueeL("1757167301187-5777536c564c"),
        ],
    ]
}
