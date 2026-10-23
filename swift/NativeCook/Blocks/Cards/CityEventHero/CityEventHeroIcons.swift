import CoreGraphics
import UIKit

enum CityEventHeroIcon: CaseIterable {
    case gift, filter, search, heart, heartFill, share, more, chevronView, chevronCard
    case home, chat, plus, bell, person

    var box: CityEventHeroTheme.Box {
        switch self {
        case .gift: .init(x: 627, y: 258, width: 39, height: 40)
        case .filter: .init(x: 768, y: 262, width: 39, height: 36)
        case .search: .init(x: 1088, y: 261, width: 37, height: 37)
        case .heart, .heartFill: .init(x: 97, y: 2260, width: 39, height: 36)
        case .share: .init(x: 285, y: 2256, width: 36, height: 39)
        case .more: .init(x: 409, y: 2274, width: 33, height: 7)
        case .chevronView: .init(x: 1090, y: 2263, width: 15, height: 28)
        case .chevronCard: .init(x: 1070, y: 2050, width: 20, height: 36)
        case .home: .init(x: 127, y: 2426, width: 65, height: 69)
        case .chat: .init(x: 349, y: 2429, width: 65, height: 65)
        case .plus: .init(x: 568, y: 2427, width: 69, height: 69)
        case .bell: .init(x: 792, y: 2427, width: 65, height: 68)
        case .person: .init(x: 1014, y: 2427, width: 63, height: 66)
        }
    }

    func draw(in context: CGContext, x: Double, y: Double, scale: Double, color: UIColor = .white) {
        context.saveGState()
        context.translateBy(x: x, y: y)
        context.scaleBy(x: scale, y: scale)
        context.setStrokeColor(color.cgColor)
        context.setFillColor(color.cgColor)
        context.setLineCap(.round)
        context.setLineJoin(.round)
        switch self {
        case .gift: drawGift(context)
        case .filter: drawFilter(context)
        case .search: drawSearch(context)
        case .heart: drawHeart(context, filled: false)
        case .heartFill: drawHeart(context, filled: true)
        case .share: drawShare(context)
        case .more: drawMore(context)
        case .chevronView: drawChevron(context, endX: 12, endY: 24.8, middleY: 14, width: 4)
        case .chevronCard: drawChevron(context, endX: 16, endY: 32.8, middleY: 18, width: 4)
        case .home: drawHome(context)
        case .chat: drawChat(context)
        case .plus: drawPlus(context)
        case .bell: drawBell(context)
        case .person: drawPerson(context)
        }
        context.restoreGState()
    }

    private func drawGift(_ context: CGContext) {
        context.setLineWidth(4.8)
        context.addPath(UIBezierPath(roundedRect: CGRect(x: 3, y: 9, width: 33, height: 9), cornerRadius: 3).cgPath)
        context.strokePath()
        let body = UIBezierPath()
        body.move(to: CGPoint(x: 6.5, y: 18)); body.addLine(to: CGPoint(x: 6.5, y: 33.5))
        body.addCurve(to: CGPoint(x: 10.5, y: 37.5), controlPoint1: CGPoint(x: 6.5, y: 35.7), controlPoint2: CGPoint(x: 8.3, y: 37.5))
        body.addLine(to: CGPoint(x: 28.5, y: 37.5))
        body.addCurve(to: CGPoint(x: 32.5, y: 33.5), controlPoint1: CGPoint(x: 30.7, y: 37.5), controlPoint2: CGPoint(x: 32.5, y: 35.7))
        body.addLine(to: CGPoint(x: 32.5, y: 18))
        context.addPath(body.cgPath); context.strokePath()
        context.setLineCap(.butt); context.move(to: CGPoint(x: 19.5, y: 9)); context.addLine(to: CGPoint(x: 19.5, y: 37.5)); context.strokePath()
        context.fillEllipse(in: CGRect(x: 13.6, y: 1, width: 3.2, height: 3.2))
        context.fillEllipse(in: CGRect(x: 22.2, y: 1, width: 3.2, height: 3.2))
    }

    private func drawFilter(_ context: CGContext) {
        context.setLineWidth(5)
        context.move(to: CGPoint(x: 18, y: 7.3)); context.addLine(to: CGPoint(x: 37, y: 7.3)); context.strokePath()
        context.setLineWidth(4.6); context.strokeEllipse(in: CGRect(x: 5, y: 2.3, width: 10.4, height: 10.4))
        context.setLineWidth(5); context.move(to: CGPoint(x: 2, y: 28.3)); context.addLine(to: CGPoint(x: 19.5, y: 28.3)); context.strokePath()
        context.setLineWidth(4.6); context.strokeEllipse(in: CGRect(x: 23.5, y: 23, width: 10.4, height: 10.4))
    }

    private func drawSearch(_ context: CGContext) {
        context.setLineWidth(4.6); context.strokeEllipse(in: CGRect(x: 1.9, y: 1.9, width: 25.8, height: 25.8))
        context.setLineWidth(4.8); context.move(to: CGPoint(x: 24.4, y: 24.4)); context.addLine(to: CGPoint(x: 34.2, y: 34.2)); context.strokePath()
    }

    private func drawHeart(_ context: CGContext, filled: Bool) {
        let path = UIBezierPath()
        path.move(to: CGPoint(x: 19.5, y: 33))
        path.addCurve(to: CGPoint(x: 3.2, y: 12.8), controlPoint1: CGPoint(x: 9, y: 25.5), controlPoint2: CGPoint(x: 3.2, y: 20.2))
        path.addCurve(to: CGPoint(x: 11.8, y: 3.2), controlPoint1: CGPoint(x: 3.2, y: 7.6), controlPoint2: CGPoint(x: 6.8, y: 3.2))
        path.addCurve(to: CGPoint(x: 19.5, y: 8), controlPoint1: CGPoint(x: 15, y: 3.2), controlPoint2: CGPoint(x: 17.8, y: 5))
        path.addCurve(to: CGPoint(x: 27.2, y: 3.2), controlPoint1: CGPoint(x: 21.2, y: 5), controlPoint2: CGPoint(x: 24, y: 3.2))
        path.addCurve(to: CGPoint(x: 35.8, y: 12.8), controlPoint1: CGPoint(x: 32.2, y: 3.2), controlPoint2: CGPoint(x: 35.8, y: 7.6))
        path.addCurve(to: CGPoint(x: 19.5, y: 33), controlPoint1: CGPoint(x: 35.8, y: 20.2), controlPoint2: CGPoint(x: 30, y: 25.5))
        path.close()
        context.addPath(path.cgPath); context.setLineWidth(4.4)
        if filled { context.drawPath(using: .fillStroke) } else { context.strokePath() }
    }

    private func drawShare(_ context: CGContext) {
        context.setLineWidth(4.6)
        let box = UIBezierPath()
        box.move(to: CGPoint(x: 3, y: 22)); box.addLine(to: CGPoint(x: 3, y: 31.5))
        box.addCurve(to: CGPoint(x: 8, y: 36.5), controlPoint1: CGPoint(x: 3, y: 34.3), controlPoint2: CGPoint(x: 5.2, y: 36.5))
        box.addLine(to: CGPoint(x: 28, y: 36.5)); box.addCurve(to: CGPoint(x: 33, y: 31.5), controlPoint1: CGPoint(x: 30.8, y: 36.5), controlPoint2: CGPoint(x: 33, y: 34.3))
        box.addLine(to: CGPoint(x: 33, y: 22)); context.addPath(box.cgPath); context.strokePath()
        context.move(to: CGPoint(x: 18, y: 24)); context.addLine(to: CGPoint(x: 18, y: 3.5)); context.strokePath()
        context.move(to: CGPoint(x: 9.5, y: 11.5)); context.addLine(to: CGPoint(x: 18, y: 3.2)); context.addLine(to: CGPoint(x: 26.5, y: 11.5)); context.strokePath()
    }

    private func drawMore(_ context: CGContext) {
        for centerX in [3.5, 16.5, 29.5] { context.fillEllipse(in: CGRect(x: centerX - 3.5, y: 0, width: 7, height: 7)) }
    }

    private func drawChevron(_ context: CGContext, endX: Double, endY: Double, middleY: Double, width: Double) {
        context.setLineWidth(width)
        context.move(to: CGPoint(x: 3.2, y: 3.2)); context.addLine(to: CGPoint(x: endX, y: middleY)); context.addLine(to: CGPoint(x: 3.2, y: endY)); context.strokePath()
    }

    private func drawHome(_ context: CGContext) {
        let path = UIBezierPath()
        path.move(to: CGPoint(x: 32.5, y: 0))
        path.addCurve(to: CGPoint(x: 37.5, y: 1.9), controlPoint1: CGPoint(x: 34.4, y: 0), controlPoint2: CGPoint(x: 36, y: 0.7))
        path.addLine(to: CGPoint(x: 61, y: 20.5)); path.addCurve(to: CGPoint(x: 64.8, y: 28), controlPoint1: CGPoint(x: 63.3, y: 22.3), controlPoint2: CGPoint(x: 64.8, y: 25))
        path.addLine(to: CGPoint(x: 64.8, y: 54.5)); path.addCurve(to: CGPoint(x: 52, y: 68.6), controlPoint1: CGPoint(x: 64.8, y: 62.6), controlPoint2: CGPoint(x: 59.5, y: 68.6))
        path.addLine(to: CGPoint(x: 13, y: 68.6)); path.addCurve(to: CGPoint(x: 0.2, y: 54.5), controlPoint1: CGPoint(x: 5.6, y: 68.6), controlPoint2: CGPoint(x: 0.2, y: 62.6))
        path.addLine(to: CGPoint(x: 0.2, y: 28)); path.addCurve(to: CGPoint(x: 4, y: 20.5), controlPoint1: CGPoint(x: 0.2, y: 25), controlPoint2: CGPoint(x: 1.7, y: 22.3))
        path.addLine(to: CGPoint(x: 27.5, y: 1.9)); path.addCurve(to: CGPoint(x: 32.5, y: 0), controlPoint1: CGPoint(x: 29, y: 0.7), controlPoint2: CGPoint(x: 30.6, y: 0)); path.close()
        context.addPath(path.cgPath); context.fillPath()
        context.setStrokeColor(UIColor.black.cgColor); context.setLineWidth(5.2)
        let smile = UIBezierPath(); smile.move(to: CGPoint(x: 21.8, y: 38.8)); smile.addQuadCurve(to: CGPoint(x: 43.2, y: 38.8), controlPoint: CGPoint(x: 32.5, y: 47.5))
        context.addPath(smile.cgPath); context.strokePath()
    }

    private func drawChat(_ context: CGContext) {
        let path = UIBezierPath()
        path.move(to: CGPoint(x: 32.5, y: 2.4))
        path.addCurve(to: CGPoint(x: 62.6, y: 32.5), controlPoint1: CGPoint(x: 49.8, y: 2.4), controlPoint2: CGPoint(x: 62.6, y: 15.2))
        path.addCurve(to: CGPoint(x: 32.5, y: 62.6), controlPoint1: CGPoint(x: 62.6, y: 49.8), controlPoint2: CGPoint(x: 49.8, y: 62.6))
        path.addLine(to: CGPoint(x: 13.5, y: 62.6)); path.addCurve(to: CGPoint(x: 2.4, y: 51.5), controlPoint1: CGPoint(x: 7, y: 62.6), controlPoint2: CGPoint(x: 2.4, y: 58))
        path.addLine(to: CGPoint(x: 2.4, y: 32.5)); path.addCurve(to: CGPoint(x: 32.5, y: 2.4), controlPoint1: CGPoint(x: 2.4, y: 15.2), controlPoint2: CGPoint(x: 15.2, y: 2.4))
        context.addPath(path.cgPath); context.setLineWidth(5); context.strokePath()
        context.fillEllipse(in: CGRect(x: 20.4, y: 28.7, width: 6.6, height: 6.6)); context.fillEllipse(in: CGRect(x: 37.9, y: 28.7, width: 6.6, height: 6.6))
    }

    private func drawPlus(_ context: CGContext) {
        context.setLineWidth(5); context.strokeEllipse(in: CGRect(x: 2.9, y: 2.9, width: 63.2, height: 63.2))
        context.move(to: CGPoint(x: 34.5, y: 22.5)); context.addLine(to: CGPoint(x: 34.5, y: 46.5)); context.move(to: CGPoint(x: 22.5, y: 34.5)); context.addLine(to: CGPoint(x: 46.5, y: 34.5)); context.strokePath()
    }

    private func drawBell(_ context: CGContext) {
        context.setLineWidth(5)
        let bell = UIBezierPath(); bell.move(to: CGPoint(x: 2.4, y: 51.5)); bell.addLine(to: CGPoint(x: 6, y: 40.5)); bell.addLine(to: CGPoint(x: 6, y: 29))
        bell.addArc(withCenter: CGPoint(x: 32.5, y: 29), radius: 26.5, startAngle: .pi, endAngle: 2 * .pi, clockwise: true)
        bell.addLine(to: CGPoint(x: 59, y: 40.5)); bell.addLine(to: CGPoint(x: 62.6, y: 51.5)); bell.close()
        context.addPath(bell.cgPath); context.strokePath()
        // SVG's A12 11.5 lower half-ellipse, including round stroke caps.
        context.saveGState()
        context.translateBy(x: 32.5, y: 55.5)
        let bottom = UIBezierPath()
        bottom.addArc(withCenter: .zero, radius: 12, startAngle: .pi, endAngle: 0, clockwise: false)
        bottom.apply(CGAffineTransform(scaleX: 1, y: 11.5 / 12))
        context.addPath(bottom.cgPath); context.strokePath()
        context.restoreGState()
    }

    private func drawPerson(_ context: CGContext) {
        context.setLineWidth(5); context.strokeEllipse(in: CGRect(x: 17.7, y: 2.5, width: 27.6, height: 27.6))
        let body = UIBezierPath(); body.move(to: CGPoint(x: 3.4, y: 62.6)); body.addCurve(to: CGPoint(x: 59.6, y: 62.6), controlPoint1: CGPoint(x: 7, y: 34), controlPoint2: CGPoint(x: 56, y: 34))
        context.addPath(body.cgPath); context.strokePath()
    }
}
