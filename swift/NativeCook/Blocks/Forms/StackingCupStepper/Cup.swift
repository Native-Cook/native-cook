import SwiftUI

private enum StackingCupStepperCupStyle {
    static let flat = 0.27, base = 22.0, mouth = 30.0
    static let sleeve = 31.0, sleeveDrop = 15.0, sleeveSlide = 0.35, sleevePad = 1.2, shortest = 62.0
    static let rim = 35.5, rimHeight = 6.0, dome = 29.5, domeTop = 27.0, domeHeight = 7.5, lidTip = 0.008
    static let paper: [UInt32] = [0xc9bfae, 0xf7f2e8, 0xe6decf, 0xaea391]
    static let kraft: [UInt32] = [0x86542a, 0xbc844c, 0xa9713d, 0x744721]
    static let lid: [UInt32] = [0xd3cabb, 0xfcf9f2, 0xede6d9, 0xb9ae9c]
    static let shadeAt = [0.0, 0.3, 0.64, 1.0]
    static let lidTop: [UInt32] = [0xfffdf8, 0xe9e1d3], bean: UInt32 = 0x3a2315
    static let inside: [UInt32] = [0x6b4a30, 0x2a1c12]
    static let rimTop: [UInt32] = [0xe4dcce, 0xfbf8f1, 0xd6cdbe]
    static let sunken: [UInt32] = [0xe2dacb, 0xf6f1e7, 0xddd4c4], hole: UInt32 = 0x74685a
    static let ground = CGRect(x: -base - 9, y: -4, width: base * 2 + 22, height: 15)
    static let groundBlur = 5.0, groundAlpha = 0.5, shadowLength = 22.0, shadowBoxHeight = 34.0, shadowLift = 6.0
    static let markDepth = 0.96, beanWidth = 12.8, beanHeight = 18.0, beanStroke = 1.3
}

/// All cups in a pile share these paths for one frame.
struct StackingCupStepperCupShape {
    let paper: Path
    let sleeve: Path
    let markY: Double
    let mouthY: Double
    let lidY: Double
    let lidAngle: Double
    let shadowOpacity: Double
    let shadowBox: CGRect

    init(body: Double, lift: Double) {
        typealias S = StackingCupStepperCupStyle
        var paper = Path()
        paper.move(to: CGPoint(x: -S.mouth, y: -body))
        paper.addLine(to: CGPoint(x: -S.base, y: 0))
        StackingCupStepperCupArc(&paper, radius: S.base, y: 0, fromLeft: true)
        paper.addLine(to: CGPoint(x: S.mouth, y: -body))
        paper.closeSubpath()
        self.paper = paper
        let top = -body + S.sleeveDrop + S.sleeveSlide * (body - S.shortest)
        let bottom = top + S.sleeve
        func radius(_ y: Double) -> Double { S.base + (S.mouth - S.base) * -y / body }
        let wide = radius(top) + S.sleevePad, narrow = radius(bottom) + S.sleevePad
        var sleeve = Path()
        sleeve.move(to: CGPoint(x: -wide, y: top))
        StackingCupStepperCupArc(&sleeve, radius: wide, y: top, fromLeft: true)
        sleeve.addLine(to: CGPoint(x: narrow, y: bottom))
        StackingCupStepperCupArc(&sleeve, radius: narrow, y: bottom, fromLeft: false)
        sleeve.closeSubpath()
        self.sleeve = sleeve
        let middle = top + S.sleeve / 2
        markY = middle + (radius(middle) + S.sleevePad) * S.flat * S.markDepth
        mouthY = -body
        lidY = -body - lift
        lidAngle = lift * S.lidTip
        shadowOpacity = 1 / (1 + lift / S.shadowLift)
        shadowBox = CGRect(x: -S.mouth, y: -body, width: S.mouth * 2, height: S.shadowBoxHeight)
    }
}

private func StackingCupStepperCupArc(_ path: inout Path, radius: Double, y: Double, fromLeft: Bool) {
    path.addArc(
        center: .zero, radius: radius,
        startAngle: .degrees(fromLeft ? 180 : 0), endAngle: .degrees(fromLeft ? 0 : 180),
        clockwise: fromLeft,
        transform: CGAffineTransform(a: 1, b: 0, c: 0, d: StackingCupStepperCupStyle.flat, tx: 0, ty: y)
    )
}

enum StackingCupStepperCup {
    private typealias S = StackingCupStepperCupStyle
    static let width = S.rim * 2
    static func height(_ body: Double) -> Double {
        body + S.rimHeight + S.domeHeight + S.domeTop * S.flat + S.base * S.flat
    }
    private static func oval(_ y: Double, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: -radius, y: y - radius * S.flat, width: radius * 2, height: radius * 2 * S.flat))
    }
    private static func gradient(_ colors: [UInt32], stops: [Double]? = nil) -> Gradient {
        if let stops { return Gradient(stops: zip(colors, stops).map { Gradient.Stop(color: Color(hex: $0.0), location: $0.1) }) }
        return Gradient(colors: colors.map { Color(hex: $0) })
    }
    private static let paperGradient = gradient(S.paper, stops: S.shadeAt)
    private static let kraftGradient = gradient(S.kraft, stops: S.shadeAt)
    private static let lidGradient = gradient(S.lid, stops: S.shadeAt)
    private static let rimGradient = gradient(S.rimTop)
    private static let topGradient = gradient(S.lidTop)
    private static let sunkenGradient = gradient(S.sunken)
    private static let insideGradient = gradient(S.inside)
    private static let shadowGradient = Gradient(colors: [Color.rgba(40, 28, 14, 0.42), Color.rgba(40, 28, 14, 0)])
    private static let rimWall: Path = {
        var path = Path()
        path.move(to: CGPoint(x: -S.rim, y: -S.rimHeight)); path.addLine(to: CGPoint(x: -S.rim, y: 1))
        StackingCupStepperCupArc(&path, radius: S.rim, y: 1, fromLeft: true)
        path.addLine(to: CGPoint(x: S.rim, y: -S.rimHeight))
        StackingCupStepperCupArc(&path, radius: S.rim, y: -S.rimHeight, fromLeft: false)
        path.closeSubpath(); return path
    }()
    private static let domeWall: Path = {
        var path = Path()
        path.move(to: CGPoint(x: -S.dome, y: -S.rimHeight))
        StackingCupStepperCupArc(&path, radius: S.dome, y: -S.rimHeight, fromLeft: true)
        path.addLine(to: CGPoint(x: S.domeTop, y: -S.rimHeight - S.domeHeight))
        StackingCupStepperCupArc(&path, radius: S.domeTop, y: -S.rimHeight - S.domeHeight, fromLeft: false)
        path.closeSubpath(); return path
    }()
    private static let beanLine: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 0.6, y: -8.4))
        path.addCurve(to: CGPoint(x: -0.4, y: 8.4), control1: CGPoint(x: 4, y: -3.4), control2: CGPoint(x: -3.8, y: 2.6))
        return path
    }()
    private static let bean = Path(ellipseIn: CGRect(x: -S.beanWidth / 2, y: -S.beanHeight / 2, width: S.beanWidth, height: S.beanHeight))

    static func draw(_ context: inout GraphicsContext, shape: StackingCupStepperCupShape) {
        context.drawLayer { shadow in
            shadow.addFilter(.blur(radius: S.groundBlur))
            shadow.fill(Path(ellipseIn: S.ground), with: .color(.black.opacity(S.groundAlpha)))
        }
        context.fill(shape.paper, with: .linearGradient(paperGradient, startPoint: CGPoint(x: -S.mouth, y: 0), endPoint: CGPoint(x: S.mouth, y: 0)))
        var shade = context
        shade.clip(to: shape.paper)
        shade.opacity *= shape.shadowOpacity
        shade.fill(Path(shape.shadowBox), with: .linearGradient(shadowGradient, startPoint: CGPoint(x: 0, y: shape.mouthY), endPoint: CGPoint(x: 0, y: shape.mouthY + S.shadowLength)))
        context.fill(shape.sleeve, with: .linearGradient(kraftGradient, startPoint: CGPoint(x: -S.mouth, y: 0), endPoint: CGPoint(x: S.mouth, y: 0)))
        var mark = context
        mark.translateBy(x: 0, y: shape.markY)
        drawBean(&mark, x: -5.5, y: 0, angle: -0.42)
        drawBean(&mark, x: 6.5, y: 0.5, angle: 0.36)

        var mouth = context
        mouth.translateBy(x: 0, y: shape.mouthY)
        mouth.fill(oval(0, S.mouth), with: .color(Color(hex: S.paper[1])))
        mouth.fill(oval(0.6, S.mouth - 2.4), with: .linearGradient(insideGradient, startPoint: CGPoint(x: 0, y: -S.mouth * S.flat), endPoint: CGPoint(x: 0, y: S.mouth * S.flat)))

        var lid = context
        lid.translateBy(x: 0, y: shape.lidY)
        lid.rotate(by: .radians(shape.lidAngle))
        lid.fill(rimWall, with: .linearGradient(lidGradient, startPoint: CGPoint(x: -S.rim, y: 0), endPoint: CGPoint(x: S.rim, y: 0)))
        lid.fill(oval(-S.rimHeight, S.rim), with: .linearGradient(rimGradient, startPoint: CGPoint(x: -S.rim, y: 0), endPoint: CGPoint(x: S.rim, y: 0)))
        lid.fill(domeWall, with: .linearGradient(lidGradient, startPoint: CGPoint(x: -S.dome, y: 0), endPoint: CGPoint(x: S.dome, y: 0)))
        let top = -S.rimHeight - S.domeHeight
        lid.fill(oval(top, S.domeTop), with: .linearGradient(topGradient, startPoint: CGPoint(x: -S.domeTop, y: top - 6), endPoint: CGPoint(x: S.domeTop, y: top + 6)))
        lid.fill(oval(top + 0.7, S.domeTop - 5.5), with: .linearGradient(sunkenGradient, startPoint: CGPoint(x: -S.domeTop, y: 0), endPoint: CGPoint(x: S.domeTop, y: 0)))
        lid.fill(Path(ellipseIn: CGRect(x: -4.2, y: top - S.domeTop * S.flat * 0.62 - 1.5, width: 8.4, height: 3)), with: .color(Color(hex: S.hole)))
    }

    private static func drawBean(_ context: inout GraphicsContext, x: Double, y: Double, angle: Double) {
        var beanContext = context
        beanContext.translateBy(x: x, y: y)
        beanContext.rotate(by: .radians(angle))
        beanContext.fill(bean, with: .color(Color(hex: S.bean)))
        beanContext.stroke(beanLine, with: .color(Color(hex: S.kraft[2])), style: StrokeStyle(lineWidth: S.beanStroke, lineCap: .round))
    }
}
