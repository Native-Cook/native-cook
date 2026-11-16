import Foundation

enum BreathVolumeLungsGlyphs {
    // The reference's 9-bit, seven-row masks; each code is leaving * 512 + arriving.
    static let masks: [Double] = [40, 130, 257, 257, 257, 130, 40, 32, 40, 34, 32, 32, 32, 32, 40, 130, 128, 32, 8, 2, 341, 40, 130, 128, 40, 128, 130, 40, 64, 80, 68, 65, 341, 64, 64, 340, 1, 1, 42, 256, 130, 40, 80, 4, 1, 85, 257, 130, 40, 341, 256, 64, 32, 8, 8, 8, 40, 130, 130, 40, 130, 130, 40, 40, 130, 257, 340, 256, 64, 20, 0, 0, 0, 0, 0, 40, 40, 0, 0, 0, 0, 0, 0, 0]
    static func pack(_ from: String, _ to: String) -> [Double] {
        let a = Array(from.suffix(4)), b = Array(to.suffix(4))
        let advances = b.map { $0 == "." ? 3.2 : 5.4 }
        let mid = advances.reduce(0, +) / 2
        var centers: [Double] = [], acc = 0.0
        for advance in advances { centers.append(acc + advance / 2 - mid); acc += advance }
        func index(_ ch: Character) -> Int { ch == "." ? 10 : Int(String(ch)) ?? 11 }
        var out = [Double](repeating: 0, count: 40)
        for slot in 0..<4 {
            let ti = slot - (4 - b.count), fi = slot - (4 - a.count)
            let old = fi >= 0 ? index(a[fi]) : 11, new = ti >= 0 ? index(b[ti]) : 11
            for row in 0..<7 { out[slot * 8 + row] = masks[old * 7 + row] * 512 + masks[new * 7 + row] }
            out[32 + slot] = ti >= 0 ? centers[ti] : 0
            out[36 + slot] = ti >= 0 && b[ti] == "." ? 1 : 0
        }
        return out
    }
}
