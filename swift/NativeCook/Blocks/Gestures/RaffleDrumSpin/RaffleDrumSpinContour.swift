import SwiftUI

/// Arc length for the reference's contour-measured travelling border highlight.
enum RaffleDrumSpinContour {
    private static let lengths = NSCache<NSString, NSNumber>()

    static func segment(_ path: Path, key: String, from: Double, to: Double) -> Path {
        let length: Double
        if let cached = lengths.object(forKey: key as NSString) { length = cached.doubleValue }
        else {
            length = measure(path.cgPath)
            lengths.setObject(NSNumber(value: length), forKey: key as NSString)
        }
        guard length > 0 else { return Path() }
        func normalized(_ value: Double) -> Double {
            let amount = value.truncatingRemainder(dividingBy: length)
            return (amount < 0 ? amount + length : amount) / length
        }
        let p0 = normalized(from * length)
        let p1 = normalized(to * length)
        if p1 > p0 { return path.trimmedPath(from: p0, to: p1) }
        var result = path.trimmedPath(from: p0, to: 1)
        result.addPath(path.trimmedPath(from: 0, to: p1))
        return result
    }

    static func highlight(_ path: Path, key: String, sweep: Double, trail: Double) -> Path {
        let length: Double
        if let cached = lengths.object(forKey: key as NSString) { length = cached.doubleValue }
        else {
            length = measure(path.cgPath)
            lengths.setObject(NSNumber(value: length), forKey: key as NSString)
        }
        guard length > 0 else { return Path() }
        let end = sweep + 0.62
        return segment(path, key: key, from: end - trail / length, to: end)
    }

    private static func distance(_ a: CGPoint, _ b: CGPoint) -> Double { hypot(a.x - b.x, a.y - b.y) }
    private static func midpoint(_ a: CGPoint, _ b: CGPoint) -> CGPoint { CGPoint(x: (a.x+b.x)/2, y: (a.y+b.y)/2) }

    private static func quad(_ a: CGPoint, _ b: CGPoint, _ c: CGPoint, depth: Int = 0) -> Double {
        let chord = distance(a,c), polygon = distance(a,b) + distance(b,c)
        if depth == 14 || polygon - chord < 0.0001 { return (polygon + chord) / 2 }
        let ab = midpoint(a,b), bc = midpoint(b,c), mid = midpoint(ab,bc)
        return quad(a,ab,mid,depth:depth+1) + quad(mid,bc,c,depth:depth+1)
    }

    private static func cubic(_ a: CGPoint, _ b: CGPoint, _ c: CGPoint, _ d: CGPoint, depth: Int = 0) -> Double {
        let chord = distance(a,d), polygon = distance(a,b) + distance(b,c) + distance(c,d)
        if depth == 14 || polygon - chord < 0.0001 { return (polygon + chord) / 2 }
        let ab = midpoint(a,b), bc = midpoint(b,c), cd = midpoint(c,d)
        let abc = midpoint(ab,bc), bcd = midpoint(bc,cd), mid = midpoint(abc,bcd)
        return cubic(a,ab,abc,mid,depth:depth+1) + cubic(mid,bcd,cd,d,depth:depth+1)
    }

    private static func measure(_ path: CGPath) -> Double {
        var current = CGPoint.zero, first = CGPoint.zero, length = 0.0
        path.applyWithBlock { pointer in
            let element = pointer.pointee
            switch element.type {
            case .moveToPoint: current = element.points[0]; first = current
            case .addLineToPoint:
                length += distance(current,element.points[0]); current = element.points[0]
            case .addQuadCurveToPoint:
                length += quad(current,element.points[0],element.points[1]); current = element.points[1]
            case .addCurveToPoint:
                length += cubic(current,element.points[0],element.points[1],element.points[2]); current = element.points[2]
            case .closeSubpath: length += distance(current,first); current = first
            @unknown default: break
            }
        }
        return length
    }
}
