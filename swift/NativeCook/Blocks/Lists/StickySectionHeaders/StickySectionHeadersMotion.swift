import Foundation

/// Sticky Section Headers — everything is a function of one number, the list's scroll offset `s` (content pt).
/// The autoplay loop only scripts `s`; the sticky header, its pill, the rolling total and the month index all read
/// `s`, so a real scroll drives exactly what the loop shows (port of `stickyMotion.ts`).
enum StickySectionHeadersMotion {
    static let headerH: Double = 56
    static let rowH: Double = 68
    static let footerH: Double = 420

    static let loop: Double = 10
    static let glide1 = (1.2, 3.6)
    static let glide2 = (4.4, 6.4)
    static let back = (7.0, 8.8)

    static let shrink: Double = 52
    static let pillTop: Double = 8
    static let pillH: Double = 36
    static let pillW: Double = 236
    static let rollLine: Double = pillTop + pillH
    static let rollSpan: Double = 30
    static let idle: Double = 2
    static let returnLen: Double = 1.2

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func mix(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }

    /// Everything the sticky pieces need about the data, built once.
    struct Geometry {
        let tops: [Double]
        let running: [Int]
        let runOffsets: [Int]
        let rowCounts: [Int]
        let sectionMonth: [Int]
        let monthTops: [Double]
        let stop1: Double
        let stop2: Double
        let contentH: Double

        init(sections: [StickySectionHeadersSection], months: Int, listTopH: Double) {
            var tops: [Double] = []
            var running: [Int] = []
            var runOffsets: [Int] = []
            var y = listTopH
            for s in sections {
                tops.append(y)
                y += headerH + Double(s.txns.count) * rowH
                runOffsets.append(running.count)
                var acc = 0
                running.append(0)
                for t in s.txns {
                    acc += t.cents < 0 ? -t.cents : 0
                    running.append(acc)
                }
            }
            tops.append(y)
            var monthTops: [Double] = []
            for m in 0..<months {
                if let i = sections.firstIndex(where: { $0.month == m }) { monthTops.append(tops[i]) } else { monthTops.append(y) }
            }
            let at: (Int) -> Double = { i in tops[min(i, sections.count - 1)] + headerH + rowH * 1.6 }
            self.tops = tops
            self.running = running
            self.runOffsets = runOffsets
            self.rowCounts = sections.map { $0.txns.count }
            self.sectionMonth = sections.map { $0.month }
            self.monthTops = monthTops
            self.stop1 = at(1)
            self.stop2 = at(4)
            self.contentH = y + footerH
        }
    }

    /// The scripted scroll offset at loop time t.
    static func scrollAt(_ t: Double, _ g: Geometry) -> Double {
        let g1 = easeInOutCubic(seg(t, glide1.0, glide1.1))
        let g2 = easeInOutCubic(seg(t, glide2.0, glide2.1))
        let b = easeInOutCubic(seg(t, back.0, back.1))
        let y = g.stop1 * g1 + (g.stop2 - g.stop1) * g2
        return y * (1 - b)
    }

    /// Ghost finger for the scripted scroll: (opacity, y as a fraction of the list height).
    static func ghostAt(_ t: Double) -> (Double, Double) {
        let glides = [glide1, glide2, back]
        for (i, gl) in glides.enumerated() {
            let (a, b) = gl
            if t > a - 0.3 && t < b + 0.3 {
                let show = easeOutCubic(seg(t, a - 0.3, a)) * (1 - easeOutCubic(seg(t, b - 0.15, b + 0.3)))
                let u = easeInOutCubic(seg(t, a, b))
                return (show, i < 2 ? mix(0.78, 0.3, u) : mix(0.3, 0.78, u))
            }
        }
        return (0, 0.78)
    }

    /// Index of the section whose header has reached the top (−1 above the first).
    static func sectionAt(_ s: Double, _ g: Geometry) -> Int {
        var k = -1
        for i in 0..<(g.tops.count - 1) where g.tops[i] <= s { k = i }
        return k
    }

    /// 0 = full header, 1 = pill.
    static func pillAmount(_ s: Double, _ k: Int, _ g: Geometry) -> Double {
        k < 0 ? 0 : easeInOutCubic(clamp01((s - g.tops[k]) / shrink))
    }

    /// Vertical offset of the stuck header: 0 while stuck, negative while the next header pushes it up.
    static func pushOffset(_ s: Double, _ k: Int, _ p: Double, _ g: Geometry) -> Double {
        if k < 0 { return 0 }
        let occupied = mix(headerH, pillTop + pillH + 8, p)
        return min(0, g.tops[k + 1] - s - occupied)
    }

    /// The running total of section k at offset s: (from cents, to cents, u).
    static func runningAt(_ s: Double, _ k: Int, _ g: Geometry) -> (Int, Int, Double) {
        if k < 0 { return (0, 0, 0) }
        let base = g.runOffsets[k]
        let n = g.rowCounts[k]
        let line = s + rollLine
        var done = 0
        for j in 0..<n {
            let mid = g.tops[k] + headerH + Double(j) * rowH + rowH * 0.5
            let u = clamp01((line - mid) / rollSpan)
            if u >= 1 {
                done = j + 1
            } else if u > 0 {
                return (g.running[base + j], g.running[base + j + 1], easeInOutCubic(u))
            }
        }
        return (g.running[base + done], g.running[base + done], 0)
    }

    /// Position of one odometer wheel (0..19, two strips of 0–9) for a place value in cents.
    static func wheelAt(_ from: Int, _ to: Int, _ u: Double, _ place: Int) -> Double {
        let d0 = (from / place) % 10
        var d1 = (to / place) % 10
        if to > from && d1 < d0 { d1 += 10 }
        return mix(Double(d0), Double(d1), u)
    }

    /// Month index position (fractional while a month boundary passes the top).
    static func monthAt(_ s: Double, _ g: Geometry) -> Double {
        let k = sectionAt(s, g)
        if k < 0 { return 0 }
        let m = g.sectionMonth[k]
        if m + 1 >= g.monthTops.count { return Double(m) }
        let next = g.monthTops[m + 1]
        return Double(m) + easeInOutCubic(clamp01((s - (next - headerH * 2)) / (headerH * 2)))
    }
}
