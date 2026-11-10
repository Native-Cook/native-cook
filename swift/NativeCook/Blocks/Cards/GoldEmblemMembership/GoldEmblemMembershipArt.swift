import SwiftUI

enum GoldEmblemMembershipArt {
    static let ticket = Path { p in
        p.move(to: .init(x: 6, y: 6)); p.addLine(to: .init(x: 40, y: 6))
        p.addCurve(to: .init(x: 42, y: 8), control1: .init(x: 41.1045695, y: 6), control2: .init(x: 42, y: 6.8954305)); p.addLine(to: .init(x: 42, y: 12))
        p.addArc(center: .init(x: 42, y: 16), radius: 4, startAngle: .degrees(-90), endAngle: .degrees(-270), clockwise: true)
        p.addLine(to: .init(x: 42, y: 24)); p.addCurve(to: .init(x: 40, y: 26), control1: .init(x: 42, y: 25.1045695), control2: .init(x: 41.1045695, y: 26))
        p.addLine(to: .init(x: 6, y: 26)); p.addCurve(to: .init(x: 4, y: 24), control1: .init(x: 4.8954305, y: 26), control2: .init(x: 4, y: 25.1045695))
        p.addLine(to: .init(x: 4, y: 20))
        p.addArc(center: .init(x: 4, y: 16), radius: 4, startAngle: .degrees(90), endAngle: .degrees(-90), clockwise: true)
        p.addLine(to: .init(x: 4, y: 8)); p.addCurve(to: .init(x: 6, y: 6), control1: .init(x: 4, y: 6.8954305), control2: .init(x: 4.8954305, y: 6)); p.closeSubpath()
    }
    static let ticketDash = Path { p in p.move(to: .init(x: 16, y: 8)); p.addLine(to: .init(x: 16, y: 24)) }
    static let giftLines = Path { p in
        p.addRect(.init(x: 6, y: 10, width: 22, height: 6)); p.addRect(.init(x: 8, y: 16, width: 18, height: 13))
        p.move(to: .init(x: 17, y: 10)); p.addLine(to: .init(x: 17, y: 29))
        p.move(to: .init(x: 17, y: 10)); p.addCurve(to: .init(x: 12, y: 10), control1: .init(x: 13, y: 4), control2: .init(x: 9, y: 6))
        p.move(to: .init(x: 17, y: 10)); p.addCurve(to: .init(x: 22, y: 10), control1: .init(x: 21, y: 4), control2: .init(x: 25, y: 6))
    }
    static let welfareLines = Path { p in
        p.addRect(.init(x: 12, y: 3, width: 10, height: 6)); p.addEllipse(in: .init(x: 7, y: 9, width: 20, height: 20))
        p.move(to: .init(x: 13, y: 18)); p.addLine(to: .init(x: 17, y: 22)); p.addLine(to: .init(x: 21, y: 18))
    }
    static let redeemLines = Path { p in
        p.addEllipse(in: .init(x: 7.5, y: 3.5, width: 19, height: 19))
        p.move(to: .init(x: 11, y: 21)); for point in [CGPoint(x: 7, y: 28), .init(x: 11, y: 27.5), .init(x: 13, y: 31), .init(x: 15.5, y: 23)] { p.addLine(to: point) }
        p.move(to: .init(x: 23, y: 21)); for point in [CGPoint(x: 27, y: 28), .init(x: 23, y: 27.5), .init(x: 21, y: 31), .init(x: 18.5, y: 23)] { p.addLine(to: point) }
    }
    static let starSmall = polygon([(17,8.5),(18.4,11.5),(21.6,11.9),(19.2,14),(19.9,17.2),(17,15.6),(14.1,17.2),(14.8,14),(12.4,11.9),(15.6,11.5)])
    static let ribbonLeft = polygon([(10,22),(4,36),(11,33),(14,39),(19,26)])
    static let ribbonRight = polygon([(26,22),(32,36),(25,33),(22,39),(17,26)])
    static let starBadge = polygon([(18,9.5),(19.9,13.6),(24.3,14.1),(21,17.1),(21.9,21.5),(18,19.3),(14.1,21.5),(15,17.1),(11.7,14.1),(16.1,13.6)])
    private static func polygon(_ points: [(Double, Double)]) -> Path {
        Path { p in p.addLines(points.map { CGPoint(x: $0.0, y: $0.1) }); p.closeSubpath() }
    }
}
