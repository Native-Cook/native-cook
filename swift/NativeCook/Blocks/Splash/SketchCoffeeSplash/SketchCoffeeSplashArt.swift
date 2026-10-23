import SwiftUI

/// One hand-drawn stroke: points a Catmull-Rom curve passes through, drawn as an ink ribbon.
/// `fill`: 0 none, 1 filled with the ground (hides what is under it), 2 filled with ink.
struct SketchCoffeeSplashStroke {
    let seed: Int
    let width: Double
    let closed: Bool
    let fill: Int
    let jitter: Double
    let points: [Double]
}

/// A stroke turned into paths for one boil variant: what to fill under it, and the ribbon itself.
struct SketchCoffeeSplashInk {
    let fill: Path?
    let fillKind: Int
    let line: Path?
}

/// Turns strokes into uneven, tapered ink ribbons that wobble a little differently in each variant.
/// Everything here is deterministic, so every loop draws the same lines.
enum SketchCoffeeSplashSketch {
    static let variants = 4

    static func hash(_ n: Double) -> Double {
        let v = sin(n * 12.9898) * 43758.5453
        return v - floor(v)
    }

    /// Points along a Catmull-Rom curve through `c`, about `step` apart.
    static func spline(_ c: [CGPoint], closed: Bool, step: Double = 2) -> [CGPoint] {
        let n = c.count
        guard n > 1 else { return c }
        var out: [CGPoint] = []
        let segs = closed ? n : n - 1
        for i in 0..<segs {
            let p0 = closed ? c[(i - 1 + n) % n] : c[max(i - 1, 0)]
            let p1 = c[i % n]
            let p2 = c[(i + 1) % n]
            let p3 = closed ? c[(i + 2) % n] : c[min(i + 2, n - 1)]
            let d = hypot(p2.x - p1.x, p2.y - p1.y)
            let k = max(2, Int(ceil(d / step)))
            for j in 0..<k {
                let u = Double(j) / Double(k)
                let u2 = u * u, u3 = u2 * u
                let x = 0.5 * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3)
                let y = 0.5 * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3)
                out.append(CGPoint(x: x, y: y))
            }
        }
        if !closed { out.append(c[n - 1]) }
        return out
    }

    static func ink(_ stroke: SketchCoffeeSplashStroke, variant: Int) -> SketchCoffeeSplashInk {
        var ctrl: [CGPoint] = []
        var i = 0
        while i + 1 < stroke.points.count {
            ctrl.append(CGPoint(x: stroke.points[i], y: stroke.points[i + 1]))
            i += 2
        }
        let (outline, centre) = ribbon(ctrl, closed: stroke.closed, width: stroke.width, seed: stroke.seed, variant: variant, jitter: stroke.jitter)
        var fill: Path?
        if stroke.fill != 0, centre.count > 2 {
            var p = Path()
            p.addLines(centre)
            p.closeSubpath()
            fill = p
        }
        var line: Path?
        if stroke.width >= 0.1, outline.count > 2 {
            var p = Path()
            p.addLines(outline)
            p.closeSubpath()
            line = p
        }
        return SketchCoffeeSplashInk(fill: fill, fillKind: stroke.fill, line: line)
    }

    /// The ribbon's outline and its (wobbled) centre line. A closed stroke carries on a little past its
    /// start, like a pen that overshoots its loop; both ends taper.
    static func ribbon(_ ctrl: [CGPoint], closed: Bool, width: Double, seed: Int, variant: Int, jitter: Double) -> ([CGPoint], [CGPoint]) {
        var pts = spline(ctrl, closed: closed)
        let n0 = pts.count
        if closed {
            let over = max(3, Int(Double(n0) * 0.07))
            pts += pts[0...min(over, n0 - 1)]
        }
        var s: [Double] = [0]
        for k in 1..<pts.count {
            s.append(s[k - 1] + hypot(pts[k].x - pts[k - 1].x, pts[k].y - pts[k - 1].y))
        }
        let total = max(s[s.count - 1], 0.001)
        let b = Double(seed) * 7 + Double(variant) * 131
        let ph = (1...6).map { hash(b + Double($0)) * 2 * .pi }
        var jp: [CGPoint] = []
        jp.reserveCapacity(pts.count)
        for k in 0..<pts.count {
            let si = s[k]
            let jx = jitter * (0.8 * sin(si / 23 + ph[0]) + 0.45 * sin(si / 8.5 + ph[1]))
            let jy = jitter * (0.8 * sin(si / 19 + ph[2]) + 0.45 * sin(si / 7.5 + ph[3]))
            jp.append(CGPoint(x: pts[k].x + jx, y: pts[k].y + jy))
        }
        let tl = min(16, total * 0.3)
        var left: [CGPoint] = [], right: [CGPoint] = []
        let m = jp.count
        for k in 0..<m {
            let a = jp[max(k - 1, 0)], c = jp[min(k + 1, m - 1)]
            let dx = c.x - a.x, dy = c.y - a.y
            var d = hypot(dx, dy)
            if d == 0 { d = 1 }
            let nx = -dy / d, ny = dx / d
            let si = s[k]
            let tin = min(1, si / tl), tout = min(1, (total - si) / tl)
            let tap = 0.14 + 0.86 * sin(min(tin, tout) * .pi / 2)
            let w = width * (0.8 + 0.2 * sin(si / 11 + ph[4]) + 0.08 * sin(si / 4.3 + ph[5])) * tap
            let hw = w / 2
            left.append(CGPoint(x: jp[k].x + nx * hw, y: jp[k].y + ny * hw))
            right.append(CGPoint(x: jp[k].x - nx * hw, y: jp[k].y - ny * hw))
        }
        let centre = Array(jp.prefix(closed ? n0 : m))
        return (left + right.reversed(), centre)
    }

    /// Draws prepared ink with a transform.
    static func draw(_ inks: [SketchCoffeeSplashInk], in context: GraphicsContext, transform: CGAffineTransform, ground: Color, ink: Color) {
        for item in inks {
            if let fill = item.fill {
                context.fill(fill.applying(transform), with: .color(item.fillKind == 1 ? ground : ink))
            }
            if let line = item.line {
                context.fill(line.applying(transform), with: .color(ink))
            }
        }
    }
}

/// The artwork, drawn by hand as point lists (in points). The cup and its splash are drawn around the
/// centre of the cup's rim, the top-view cup around its centre, the hand around the pinch that holds the spoon.
enum SketchCoffeeSplashArt {
    static let cupRadius = 46.0
    static let stirRadius = 17.0
    static let pinchRest = CGPoint(x: 52, y: -50)
    static let armDirection = CGPoint(x: 0.75, y: 0.66)
    static let armAway = 460.0

    static let cup: [SketchCoffeeSplashStroke] = [
        .init(seed: 1, width: 6.6, closed: false, fill: 0, jitter: 1.4, points: [-119.2, -8.3, -92.6, -20, -47.6, -27.7, 6.8, -30, 59.8, -26.3, 101, -17.4, 122.2, -5.1, 119.2, 8.3, 92.6, 20, 47.6, 27.7, -6.8, 30, -59.8, 26.3, -101, 17.4, -122.2, 5.1]),
        .init(seed: 2, width: 4.2, closed: false, fill: 0, jitter: 1.4, points: [-112.5, 13.3, -101, 19.7, -82.3, 25.2, -57.7, 29.4, -28.9, 32.1, 2, 33, 32.9, 32.1, 61.7, 29.4, 86.3, 25.2, 105, 19.7, 116.5, 13.3]),
        .init(seed: 3, width: 6.2, closed: false, fill: 0, jitter: 1.4, points: [-122, 6, -112, 64, -100, 132, -73, 226]),
        .init(seed: 4, width: 6.2, closed: false, fill: 0, jitter: 1.4, points: [122, 4, 114, 66, 100, 140, 73, 224]),
        .init(seed: 5, width: 5.5, closed: false, fill: 0, jitter: 1.4, points: [71.6, 224.4, 68.5, 230.6, 53.5, 236, 29.3, 239.7, 0, 241, -29.3, 239.7, -53.5, 236, -68.5, 230.6, -71.6, 224.4]),
        .init(seed: 6, width: 3.1, closed: false, fill: 0, jitter: 1.1, points: [-28.5, 218.8, -42.5, 217.3, -54.7, 215.1, -64.5, 212.5, -71.4, 209.5]),
        .init(seed: 7, width: 2.6, closed: false, fill: 0, jitter: 1.1, points: [-49.3, 199.4, -61.9, 196.8, -71.5, 193.6, -77.6, 190.1]),
        .init(seed: 8, width: 2.6, closed: false, fill: 0, jitter: 1.1, points: [-90.1, 124, -96, 121.2, -100.3, 118.3, -103, 115.3]),
        .init(seed: 9, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-107.8, 34, -102.3, 49.9]),
        .init(seed: 10, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-96.8, 41, -91.3, 56.9]),
        .init(seed: 11, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-104.4, 49, -98.9, 63.2]),
        .init(seed: 12, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-93.4, 56, -87.9, 70.2]),
        .init(seed: 13, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-101.1, 64, -95.6, 78.3]),
        .init(seed: 14, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-90.1, 71, -84.6, 85.3]),
        .init(seed: 15, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-97.7, 79, -92.2, 95.2]),
        .init(seed: 16, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-86.7, 86, -81.2, 102.2]),
        .init(seed: 17, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-94.4, 94, -88.9, 111]),
        .init(seed: 18, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-83.4, 101, -77.9, 118]),
        .init(seed: 19, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-91.1, 109, -85.5, 123.7]),
        .init(seed: 20, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-80.1, 116, -74.5, 130.7]),
        .init(seed: 21, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-87.7, 124, -82.2, 138]),
        .init(seed: 22, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-76.7, 131, -71.2, 145]),
        .init(seed: 23, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-84.4, 139, -78.9, 154.2]),
        .init(seed: 24, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-73.4, 146, -67.9, 161.2]),
        .init(seed: 25, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-81, 154, -75.5, 171.8]),
        .init(seed: 26, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-70, 161, -64.5, 178.8]),
        .init(seed: 27, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-77.7, 169, -72.2, 184.6]),
        .init(seed: 28, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-66.7, 176, -61.2, 191.6]),
        .init(seed: 29, width: 2.9, closed: false, fill: 0, jitter: 0.7, points: [-74.4, 184, -68.8, 198.1]),
        .init(seed: 30, width: 4, closed: false, fill: 0, jitter: 1.4, points: [92, 150, 64, 178]),
        .init(seed: 31, width: 4, closed: false, fill: 0, jitter: 1.4, points: [92, 172, 70, 194]),
        .init(seed: 32, width: 3.5, closed: false, fill: 0, jitter: 1.4, points: [88, 194, 78, 204]),
        .init(seed: 33, width: 3.3, closed: false, fill: 0, jitter: 1.4, points: [104, 98, 96, 110]),
    ]
    static let splash: [SketchCoffeeSplashStroke] = [
        .init(seed: 34, width: 3.8, closed: true, fill: 2, jitter: 1.6, points: [-69.9, -32.3, -73, -35.7, -77.3, -40.1, -81.9, -44.9, -86.4, -50, -90.3, -55, -93.2, -59.9, -95.2, -64.3, -97.2, -69.7, -99, -75.5, -100.6, -81.6, -102, -87.9, -103.1, -94.2, -103.9, -100.4, -104.4, -106.6, -104.6, -112.9, -104.6, -119.4, -104.3, -125.9, -103.7, -132.4, -102.9, -138.9, -101.9, -145.2, -100.6, -151.5, -99, -157.9, -97.1, -164.4, -95, -170.7, -92.6, -176.9, -90.2, -182.7, -87.7, -188, -84.5, -193.8, -80.9, -199.3, -77.1, -204.4, -73.1, -209.3, -69, -213.8, -65.2, -217.7, -60.5, -221.9, -55.7, -225.5, -51, -228.4, -46.5, -230.7, -43, -232.3, -37.3, -233, -32.6, -232.1, -29.3, -230.6, -27.3, -227.7, -26.2, -223, -25.9, -219.8, -30.4, -216.5, -36, -214.4, -35.1, -211.5, -28.7, -212.8, -21.9, -216.7, -20.1, -223, -20.5, -229.8, -23.4, -236, -28.9, -240.3, -36.2, -242.9, -44.6, -243, -50.5, -241.8, -56.3, -239.9, -62.5, -237.1, -68.8, -233.6, -74.8, -229.4, -79.9, -225.4, -85, -221, -90.2, -216, -95.3, -210.5, -100.1, -204.5, -104.6, -198, -108.3, -192, -111.7, -185.6, -115, -179, -118, -172, -120.7, -164.9, -123.2, -157.7, -125.3, -150.5, -127, -143.3, -128.5, -136, -129.7, -128.5, -130.5, -120.9, -131, -113.4, -131.2, -105.8, -131, -98.3, -130.5, -90.8, -129.5, -83.3, -128.3, -75.7, -126.6, -68.2, -124.7, -60.9, -122.3, -53.8, -119.3, -46.7, -114.9, -38.8, -109.5, -31.4, -104, -24.9, -98.9, -19.4, -94.8, -15.2, -92.1, -12.2]),
        .init(seed: 35, width: 0, closed: true, fill: 1, jitter: 1.6, points: [-81.8, -32.2, -84.3, -35.4, -87.8, -39.7, -91.7, -44.7, -95.3, -50.2, -98.2, -55.8, -100.1, -60.8, -101.8, -66.3, -103.2, -72.3, -104.5, -78.5, -105.4, -84.8, -106.1, -91.3, -106.5, -97, -106.7, -103.1, -106.7, -109.2, -106.6, -115.5, -106.2, -121.7, -105.7, -127.7, -105, -133.5, -103.7, -140.3, -102, -147.3, -99.9, -154.3, -97.9, -160.7, -96.2, -166, -95.1, -170, -96.2, -170.4, -97.7, -166.6, -99.8, -161.3, -102.2, -155.1, -104.7, -148.2, -106.9, -141.1, -108.6, -134.1, -109.7, -128.3, -110.6, -122.1, -111.3, -115.8, -111.9, -109.4, -112.2, -103.1, -112.4, -96.9, -112.3, -90.9, -111.9, -84.2, -111.2, -77.5, -110.2, -70.9, -109, -64.5, -107.4, -58.5, -105.6, -52.7, -102.5, -46.1, -98.7, -39.9, -94.7, -34.4, -91.3, -30, -89, -26.8]),
        .init(seed: 36, width: 0, closed: true, fill: 1, jitter: 1.6, points: [-92.2, -166.7, -90.5, -170.7, -88, -176.3, -84.9, -182.6, -81.6, -188.9, -78.3, -194.3, -73.5, -200.2, -68, -206, -62.9, -211, -59.5, -214.5, -60.1, -215.2, -63.8, -211.9, -69.1, -207.2, -74.9, -201.6, -80.1, -195.6, -83.9, -190.2, -87.6, -184, -91, -177.8, -94, -172.5, -96.2, -168.8]),
        .init(seed: 37, width: 3.4, closed: true, fill: 2, jitter: 1.6, points: [-88.5, -120.9, -84.4, -123, -78.4, -125.7, -71.5, -129.2, -65.1, -133.3, -60.3, -137.4, -55.6, -141.9, -51, -146.7, -46.9, -151.6, -43.4, -156.3, -40, -162, -37.3, -167.8, -35.5, -173.2, -34.8, -178.2, -37.5, -184.6, -41.3, -188.3, -42.4, -187.1, -39.2, -183.6, -37.3, -177.8, -38.4, -174.1, -40.5, -169.2, -43.4, -164, -47, -158.9, -50.6, -154.8, -54.9, -150.5, -59.6, -146.3, -64.4, -142.4, -69, -139.2, -75, -136, -81.6, -133.4, -87.7, -131.5, -92.1, -130.2]),
        .init(seed: 38, width: 3.1, closed: true, fill: 2, jitter: 1.6, points: [-113.8, -112, -117, -115.4, -121.4, -120.8, -126.5, -126.3, -131.7, -130.3, -138.5, -132.1, -145, -131.7, -150.4, -129.6, -154.4, -125.6, -156.9, -120.3, -157.9, -115.2, -155.1, -109.2, -151.5, -105.2, -150.4, -106.1, -153.5, -110.2, -155.6, -115.5, -154.4, -119.3, -151.8, -123.6, -148.5, -126.4, -144.3, -127.5, -139.1, -127.5, -134.2, -125.7, -130.7, -122.2, -126.5, -116.8, -122.7, -111.2, -120.1, -107.1]),
        .init(seed: 39, width: 3.4, closed: true, fill: 2, jitter: 1.6, points: [-54.9, -13.2, -54, -17.4, -52.5, -23, -50.6, -29.5, -48.4, -36.4, -46.1, -43.2, -43.6, -49.2, -41, -54.9, -38, -60.6, -34.8, -66.3, -31.5, -71.7, -28.1, -76.7, -24.8, -81.2, -20.4, -86.1, -15.8, -90.6, -11.1, -94.6, -6.6, -97.9, -2.8, -100.3, 2.8, -102.3, 7.5, -102.4, 10.1, -101.7, 10.8, -97.3, 9.8, -92.3, 11.4, -92, 12.7, -97.2, 12.1, -103, 7.9, -105, 2.4, -105.3, -4.1, -103.4, -8.6, -101, -13.5, -97.9, -18.7, -94, -23.8, -89.6, -28.7, -84.6, -32.6, -80.1, -36.5, -75.1, -40.3, -69.7, -43.9, -64.1, -47.3, -58.3, -50.5, -52.6, -53.6, -46.3, -56.5, -39.4, -59.2, -32.4, -61.6, -25.9, -63.7, -20.5, -65.4, -16.8]),
        .init(seed: 40, width: 2.9, closed: true, fill: 2, jitter: 1.6, points: [-17.5, -13.4, -15.7, -17.2, -12.9, -22.4, -9.6, -28.3, -6.1, -34, -2.7, -38.7, 2, -43.7, 7, -48.2, 12, -52, 16.4, -54.6, 21.6, -55.5, 26.4, -54.5, 29.9, -53.8, 30.2, -54.9, 26.8, -56.2, 21.7, -57.6, 15.6, -57, 10.4, -54.6, 4.9, -51.1, -0.8, -46.7, -6.1, -41.8, -10.3, -37, -14.5, -31.4, -18.4, -25.7, -21.7, -20.9, -24.3, -17.6]),
        .init(seed: 41, width: 2.9, closed: true, fill: 2, jitter: 1.6, points: [62.5, -24.4, 64.7, -28, 68.2, -32.9, 72, -38, 75.7, -41.9, 80.9, -45.4, 86.4, -47.8, 90.3, -48.6, 93.9, -44.7, 96, -39.6, 97, -40, 95.3, -45.5, 91.1, -50.4, 85.7, -50.2, 79.6, -48, 73.6, -44.6, 69.1, -40.7, 64.6, -35.8, 60.7, -31.1, 57.8, -28.1]),
        .init(seed: 42, width: 3.8, closed: false, fill: 0, jitter: 1.6, points: [-70.3, -7.2, -38.3, -11.6, -2.9, -13.1, 34.9, -16, 70.3, -17.5, 102.8, -15.6]),
        .init(seed: 43, width: 3.6, closed: false, fill: 0, jitter: 1.6, points: [22.7, -20.3, 25.2, -23.3, 28.8, -24.5, 32.6, -23.5, 35.3, -20.8, 36.1, -17]),
        .init(seed: 44, width: 3.4, closed: false, fill: 0, jitter: 1.6, points: [40.6, -21.9, 42.7, -24.4, 45.9, -25.4, 49.1, -24.6, 51.4, -22.2, 52.1, -19]),
    ]
    static let drops: [SketchCoffeeSplashStroke] = [
        .init(seed: 45, width: 3.4, closed: true, fill: 2, jitter: 1, points: [26.8, -262.6, 28.9, -256.4, 25.7, -248.7, 18.9, -243.1, 11.5, -242.3, 7.1, -246.6, 7.7, -254, 13, -261, 20.5, -264.4]),
        .init(seed: 46, width: 3.4, closed: true, fill: 2, jitter: 1, points: [66.2, -223.6, 64.7, -216.9, 57.6, -211.4, 48.3, -209.9, 41, -213, 39.2, -219.3, 43.7, -225.7, 52.5, -229.4, 61.4, -228.6]),
        .init(seed: 47, width: 2.8, closed: true, fill: 2, jitter: 1, points: [-17.8, -210.3, -17, -214.2, -14.1, -216.8, -10.3, -217.4, -6.8, -215.7, -4.9, -212.2, -5.4, -208.4, -7.9, -205.4, -11.7, -204.5, -23.4, -198.4]),
        .init(seed: 48, width: 2.6, closed: true, fill: 2, jitter: 1, points: [13.5, -169.8, 10.2, -166.2, 5.1, -166.4, 1.8, -170.2, 3, -174.9, 7.7, -176.8, 12.3, -174.5]),
        .init(seed: 49, width: 3.6, closed: true, fill: 2, jitter: 1, points: [102.6, -129.3, 102.7, -122.2, 97.2, -115.1, 88.3, -110.7, 79.4, -110.8, 73.8, -115.3, 73.7, -122.4, 79.2, -129.5, 88.1, -133.9, 97, -133.8]),
        .init(seed: 50, width: 3.2, closed: true, fill: 2, jitter: 1, points: [-165.1, -142.3, -169.6, -144.1, -172.2, -148.1, -172.1, -152.9, -169.2, -156.7, -164.7, -158.3, -160.1, -156.9, -157.1, -153.1, -156.8, -148.3, -151, -131.7]),
        .init(seed: 51, width: 2.6, closed: true, fill: 2, jitter: 1, points: [35.9, -99.3, 36, -102.9, 38.1, -105.8, 41.5, -106.9, 45, -105.9, 47.3, -103.1, 47.5, -99.5, 45.6, -96.4, 42.3, -95, 32.1, -86.8]),
        .init(seed: 52, width: 2.4, closed: true, fill: 2, jitter: 1, points: [-126.2, -186.1, -128.6, -183.4, -132.5, -183.6, -135, -186.5, -134.1, -189.8, -130.6, -191.2, -127.1, -189.5]),
        .init(seed: 53, width: 2.4, closed: true, fill: 2, jitter: 1, points: [-7.4, -249.2, -9.8, -246.6, -13.7, -246.7, -16.2, -249.6, -15.3, -253, -11.8, -254.3, -8.3, -252.6]),
        .init(seed: 54, width: 2.4, closed: true, fill: 2, jitter: 1, points: [113.3, -68.9, 113.9, -71.9, 116.1, -73.9, 119.1, -74.4, 121.8, -73, 123.2, -70.4, 122.9, -67.4, 120.9, -65.2, 118, -64.5, 108.6, -59.4]),
    ]
    static let top: [SketchCoffeeSplashStroke] = [
        .init(seed: 56, width: 6.4, closed: false, fill: 0, jitter: 1, points: [-43.2, -15.7, -32.1, -32.9, -14.7, -43.6, 5.7, -45.6, 25, -38.6, 39.3, -24, 45.8, -4.6, 43.2, 15.7, 32.1, 32.9, 14.7, 43.6, -5.7, 45.6, -25, 38.6, -39.3, 24, -45.8, 4.6]),
        .init(seed: 57, width: 3.8, closed: false, fill: 0, jitter: 1, points: [47, -6, 62, -6, 65, 0, 62, 6, 47, 6]),
    ]
    static let swirl: [SketchCoffeeSplashStroke] = [
        .init(seed: 55, width: 3.4, closed: false, fill: 0, jitter: 1, points: [14.9, 18.8, 8.7, 22.4, 1.7, 23.9, -5.5, 23.4, -12.1, 20.7, -17.7, 16.2]),
    ]
    static let hand: [SketchCoffeeSplashStroke] = [
        .init(seed: 58, width: 0, closed: true, fill: 1, jitter: 0, points: [0, -10, 26, -16, 70, 6, 140, 50, 240, 128, 360, 234, 480, 350, 460, 436, 350, 324, 230, 212, 130, 122, 60, 60, 16, 24]),
        .init(seed: 59, width: 3.8, closed: false, fill: 0, jitter: 1.3, points: [26, -16, 70, 6, 140, 50, 240, 128, 360, 234, 480, 350]),
        .init(seed: 60, width: 3.8, closed: false, fill: 0, jitter: 1.3, points: [16, 24, 60, 60, 130, 122, 230, 212, 350, 324, 460, 436]),
        .init(seed: 61, width: 3.4, closed: true, fill: 1, jitter: 1, points: [-0.7, -36.1, -4.8, -36.5, -8.2, -38.9, -9.9, -42.7, -9.5, -46.8, -7.1, -50.2, -3.3, -51.9, 32.7, -57.9, 36.8, -57.5, 40.2, -55.1, 41.9, -51.3, 41.5, -47.2, 39.1, -43.8, 35.3, -42.1]),
        .init(seed: 62, width: 3.6, closed: true, fill: 1, jitter: 1, points: [-14.2, -18.7, -19.1, -19, -23.2, -21.8, -25.3, -26.2, -25, -31.1, -22.2, -35.2, -17.8, -37.3, 24.2, -45.3, 29.1, -45, 33.2, -42.2, 35.3, -37.8, 35, -32.9, 32.2, -28.8, 27.8, -26.7]),
        .init(seed: 63, width: 4, closed: false, fill: 1, jitter: 1, points: [16.3, 22.9, 5.6, 26.8, -5.8, 25.9, -15.7, 20.3, -22.6, 11.2, -25, 0.1, -22.6, -11.1, -15.9, -20.2, -5.9, -25.8, 5.4, -26.8, 16.2, -23, 24.4, -15.1]),
    ]
    static let thumb: [SketchCoffeeSplashStroke] = [
        .init(seed: 64, width: 3.6, closed: false, fill: 0, jitter: 1, points: [-6, 9.5, -11.6, 2.8, -10.4, -5.5, -3.1, -10.6, 6, -9.5, 11.6, -2.8, 10.4, 5.5, 3.1, 10.6]),
    ]


    /// Each group in every boil variant, built once.
    static let cupInk = prepare(cup)
    static let splashInk = prepare(splash)
    static let dropInk = prepare(drops)
    static let topInk = prepare(top)
    static let swirlInk = prepare(swirl)
    static let handInk = prepare(hand)
    static let thumbInk = prepare(thumb)

    /// Where each drop rests, in the cup's frame.
    static let dropCentres: [CGPoint] = drops.map { stroke in
        var x = 0.0, y = 0.0
        let n = stroke.points.count / 2
        for i in 0..<n {
            x += stroke.points[2 * i]
            y += stroke.points[2 * i + 1]
        }
        return CGPoint(x: x / Double(n), y: y / Double(n))
    }

    private static func prepare(_ strokes: [SketchCoffeeSplashStroke]) -> [[SketchCoffeeSplashInk]] {
        (0..<SketchCoffeeSplashSketch.variants).map { v in strokes.map { SketchCoffeeSplashSketch.ink($0, variant: v) } }
    }
}
