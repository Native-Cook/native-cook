import SwiftUI

/*
 Address Lookup Map — the data: the addresses the lookup knows and the invented town it draws.

 Everything is made up: street names, districts, postcodes. The town is laid out in "world units"
 (a 1200 × 1200 square); AddressLookupMapStreetMap draws it and the camera looks at it with a
 centre and a scale.
*/

struct AddressLookupMapAddress: Hashable {
    /// House number and street, as typed and shown in the field.
    let street: String
    /// Second line of a suggestion.
    let area: String
    let unit: String
    let postcode: String
    let city: String
    /// Front door in world units.
    let x: Double
    let y: Double
}

enum AddressLookupMapData {
    static let addresses: [AddressLookupMapAddress] = [
        .init(street: "48 Larkspur Lane", area: "Fernhill, Ashby", unit: "Flat 3B", postcode: "2041", city: "Ashby", x: 606, y: 541),
        .init(street: "48 Larkfield Mews", area: "Ashby North", unit: "Unit 2", postcode: "2043", city: "Ashby", x: 541, y: 742),
        .init(street: "148 Larkin Street", area: "Old Quay, Ashby", unit: "Apt 12", postcode: "2038", city: "Ashby", x: 428, y: 821),
        .init(street: "48 Lark Rise", area: "Wendle, Bramley", unit: "House", postcode: "2050", city: "Bramley", x: 806, y: 872),
        .init(street: "4 Linton Parade", area: "Fernhill, Ashby", unit: "Shop 4", postcode: "2041", city: "Ashby", x: 214, y: 681),
        .init(street: "248 Lowther Road", area: "Bramley", unit: "Apt 7", postcode: "2046", city: "Bramley", x: 978, y: 961),
        .init(street: "40 Larch Close", area: "Fernhill, Ashby", unit: "Flat 1", postcode: "2041", city: "Ashby", x: 921, y: 446),
        .init(street: "84 Lantern Row", area: "Old Quay, Ashby", unit: "Loft 5", postcode: "2038", city: "Ashby", x: 161, y: 604),
    ]

    static let maxRows = 4

    private static func houseNumber(_ street: String) -> Int {
        let digits = street.prefix { $0.isNumber }
        return Int(digits) ?? 1_000_000
    }

    /// The suggestions for a query: streets that start with it first (by house number), then those
    /// that contain it, at most `maxRows`. Indices into `list`.
    static func rank(_ query: String, _ list: [AddressLookupMapAddress] = addresses) -> [Int] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        if q.isEmpty { return [] }
        var starts: [Int] = []
        var contains: [Int] = []
        for (i, a) in list.enumerated() {
            let s = a.street.lowercased()
            if s.hasPrefix(q) { starts.append(i) }
            else if s.contains(q) || a.area.lowercased().contains(q) { contains.append(i) }
        }
        starts.sort { a, b in
            let na = houseNumber(list[a].street), nb = houseNumber(list[b].street)
            return na != nb ? na < nb : a < b
        }
        return Array((starts + contains).prefix(maxRows))
    }

    /// The stretch of `text` that matches the query (character offsets), for the bold letters.
    static func matchRange(_ text: String, _ query: String) -> Range<Int>? {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        if q.isEmpty { return nil }
        guard let r = text.lowercased().range(of: q) else { return nil }
        let lower = text.lowercased()
        let a = lower.distance(from: lower.startIndex, to: r.lowerBound)
        return a ..< a + q.count
    }

    // MARK: The town

    static let world: Double = 1200

    struct Road {
        let name: String
        let points: [CGPoint]
        let label: CGPoint
        let angle: Double
    }

    private static func road(_ name: String, _ pts: [(Double, Double)], _ label: (Double, Double), _ angle: Double) -> Road {
        Road(name: name, points: pts.map { CGPoint(x: $0.0, y: $0.1) }, label: CGPoint(x: label.0, y: label.1), angle: angle)
    }

    /// Residential streets, 12 units wide. Labels sit on the road, rotated along it (degrees).
    static let streets: [Road] = [
        road("Quay Road", [(40, 300), (1160, 300)], (430, 300), 0),
        road("Ferry Street", [(60, 380), (1140, 380)], (430, 380), 0),
        road("Larkspur Lane", [(60, 520), (1140, 520)], (430, 520), 0),
        road("Linton Parade", [(60, 660), (1140, 660)], (430, 660), 0),
        road("Larkin Street", [(60, 800), (1140, 800)], (620, 800), 0),
        road("Lowther Road", [(60, 940), (1140, 940)], (620, 940), 0),
        road("Mill Row", [(60, 1080), (1140, 1080)], (620, 1080), 0),
        road("Lantern Row", [(140, 300), (140, 1160)], (140, 590), -90),
        road("Wendle Way", [(330, 300), (330, 1160)], (330, 590), -90),
        road("Larkfield Mews", [(520, 380), (520, 1160)], (520, 730), -90),
        road("Orchard Walk", [(710, 300), (710, 1160)], (710, 450), -90),
        road("Larch Close", [(900, 380), (900, 520)], (900, 450), -90),
        road("Bell Street", [(1080, 300), (1080, 1160)], (1080, 590), -90),
    ]

    /// The crescent of Lark Rise, a quadratic curve inside its block.
    static let crescentFrom = CGPoint(x: 730, y: 830)
    static let crescentCtrl = CGPoint(x: 805, y: 905)
    static let crescentTo = CGPoint(x: 880, y: 830)

    /// The one main road, wider and yellow, crossing the grid.
    static let mainRoad = road("Ashby Road", [(0, 1150), (1200, 470)], (880, 652), -29.5)

    /// Blocks between streets that are parks (x0, y0, x1, y1 between street centres).
    static let parks: [(Double, Double, Double, Double, String)] = [
        (710, 520, 900, 660, "Fernhill Green"),
        (140, 940, 330, 1080, "Mill Fields"),
    ]

    static let districts: [(name: String, x: Double, y: Double)] = [
        ("Old Quay", 600, 112),
        ("Fernhill", 420, 600),
        ("Ashby", 880, 1010),
        ("Bramley", 236, 880),
    ]

    /// River banks: a band of water across the north of the town.
    static let riverNorth: [CGPoint] = [(-20, 196), (300, 236), (620, 168), (900, 214), (1220, 184)].map { CGPoint(x: $0.0, y: $0.1) }
    static let riverSouth: [CGPoint] = [(-20, 262), (300, 290), (620, 236), (900, 270), (1220, 246)].map { CGPoint(x: $0.0, y: $0.1) }
    static let riverLabel = CGPoint(x: 760, y: 228)

    /// Seeded hash, 0 ..< 1 (the same sine hash as the React Native version).
    static func hash(_ n: Double) -> Double {
        let s = sin(n * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    private static let xs: [Double] = [140, 330, 520, 710, 900, 1080]
    private static let ys: [Double] = [300, 380, 520, 660, 800, 940, 1080]

    /// House footprints: rows of small rectangles along the edges of every block, seeded so the
    /// town is the same each time. [x, y, w, h] in world units.
    static func houses() -> [CGRect] {
        var out: [CGRect] = []
        let edge = 13.0
        let depth = 26.0
        var seed = 1.0
        func next() -> Double { let v = hash(seed); seed += 1; return v }
        func along(_ x0: Double, _ y0: Double, _ len: Double, _ horizontal: Bool, _ inward: Double) {
            var p = 0.0
            while p < len - 16 {
                let w = min(18 + next() * 22, len - p)
                let d = depth - next() * 7
                if next() > 0.1 {
                    if horizontal { out.append(CGRect(x: x0 + p, y: inward > 0 ? y0 : y0 - d, width: w, height: d)) }
                    else { out.append(CGRect(x: inward > 0 ? x0 : x0 - d, y: y0 + p, width: d, height: w)) }
                }
                p += w + 3 + next() * 3
            }
        }
        var cols: [(Double, Double)] = []
        for i in 0 ..< xs.count - 1 { cols.append((xs[i], xs[i + 1])) }
        cols.append((40, 140))
        cols.append((1080, 1160))
        for (xa, xb) in cols {
            for j in 0 ..< ys.count - 1 {
                let ya = ys[j], yb = ys[j + 1]
                if xa == 900 && ya == 380 { continue } // Larch Close splits this block; its houses are below.
                if parks.contains(where: { $0.0 == xa && $0.1 == ya }) { continue }
                if j == 0 && (xa == 40 || xa == 1080) { continue }
                let x0 = xa + edge, x1 = xb - edge, y0 = ya + edge, y1 = yb - edge
                if x1 - x0 < 40 || y1 - y0 < 40 { continue }
                let crescent = xa == 710 && ya == 800 // Lark Rise curves through this block's north half.
                if !crescent { along(x0, y0, x1 - x0, true, 1) }
                along(x0, y1, x1 - x0, true, -1)
                if !crescent && y1 - y0 > depth * 2 + 20 {
                    along(x0, y0 + depth + 4, y1 - y0 - depth * 2 - 8, false, 1)
                    along(x1, y0 + depth + 4, y1 - y0 - depth * 2 - 8, false, -1)
                }
            }
        }
        // Larch Close: two short rows either side of the cul-de-sac.
        along(913, 393, 154, true, 1)
        along(913, 507, 154, true, -1)
        // Lark Rise: houses facing the crescent from its outer side.
        for k in 0 ..< 6 {
            let u = (Double(k) + 0.5) / 6
            let a = (1 - u) * (1 - u), b = 2 * u * (1 - u), c = u * u
            let x = a * crescentFrom.x + b * crescentCtrl.x + c * crescentTo.x
            let y = a * crescentFrom.y + b * crescentCtrl.y + c * crescentTo.y
            out.append(CGRect(x: x - 10, y: y + 12, width: 20, height: 22 - hash(Double(k) + 300) * 5))
        }
        // Old Quay: warehouses north of the river.
        for i in 0 ..< 9 {
            let d = Double(i)
            let x = 30 + d * 130 + hash(d + 400) * 20
            out.append(CGRect(x: x, y: 40 + hash(d + 410) * 30, width: 80 + hash(d + 420) * 30, height: 60 + hash(d + 430) * 40))
        }
        return out
    }
}
