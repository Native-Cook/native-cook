import SwiftUI

enum AuxeticFitLabArt {
    private typealias M = AuxeticFitLabMath
    private typealias R = AuxeticFitLabResources
    static func draw(_ context: inout GraphicsContext, frame: AuxeticFitLabFrame, logicalHeight: Double) {
        let resources = R.shared
        let geometry = AuxeticFitLabGeometry(angle: frame.angle)
        context.fill(Path(CGRect(x: 0, y: 0, width: 393, height: logicalHeight)), with: .color(R.color(0xF6F8F4)))
        var paper = context
        paper.clip(to: Path(CGRect(x: 0, y: 0, width: 393, height: logicalHeight)))
        paper.draw(resources.paper, in: resources.paperRect)
        // Composite plate-shaped Gaussian coverage at full opacity, then apply
        // 6% to that isolated union once. Open holes have no rectangular backing.
        var ambient = context
        ambient.opacity = 0.06
        ambient.drawLayer { layer in
            layer.opacity = 1
            for row in 0..<3 { for column in 0..<4 {
                let center = geometry.center(column: column, row: row)
                var cell = layer
                cell.translateBy(x: center.x, y: center.y + 7)
                cell.rotate(by: .degrees((column + row).isMultiple(of: 2) ? frame.angle : -frame.angle))
                cell.draw(resources.ambientShadow, in: resources.ambientRect)
            }}
        }
        for row in 0..<3 { for column in 0..<4 {
            let center = geometry.center(column: column, row: row)
            let angle = (column + row).isMultiple(of: 2) ? frame.angle : -frame.angle
            // The light offset is applied in world coordinates before rotation.
            var shadow = context
            shadow.translateBy(x: center.x, y: center.y + 1)
            shadow.rotate(by: .degrees(angle))
            shadow.draw(resources.tightShadow, in: resources.shadowRect)
        }}
        for row in 0..<3 { for column in 0..<4 {
            let center = geometry.center(column: column, row: row)
            var plate = context
            plate.translateBy(x: center.x, y: center.y)
            plate.rotate(by: .degrees((column + row).isMultiple(of: 2) ? frame.angle : -frame.angle))
            plate.draw(resources.plate, in: resources.plateRect)
        }}
        // Seventeen shared identities and fourteen boundary identities, once each.
        for pin in geometry.pins() {
            context.fill(Path(ellipseIn: CGRect(x: pin.x - 1.7, y: pin.y - 1.7, width: 3.4, height: 3.4)),
                         with: .color(R.color(0x2B9794)))
        }
        let handles = M.handles(for: frame)
        let d = geometry.spacing
        let vertical = R.color(0x2B9794, alpha: 0.60 * frame.arrowGain)
        arrow(&context, start: CGPoint(x: 196.5, y: 399 - (1.5 * d + 7)),
              tip: CGPoint(x: 196.5, y: max(0.5, 399 - (1.5 * d + 23))),
              length: 4, breadth: 5, color: vertical)
        arrow(&context, start: CGPoint(x: 196.5, y: 399 + (1.5 * d + 7)),
              tip: CGPoint(x: 196.5, y: min(851.5, 399 + (1.5 * d + 23))),
              length: 4, breadth: 5, color: vertical)
        let horizontal = R.color(0xB88655, alpha: 0.78 * frame.arrowGain)
        let leftEdge = handles.leftCenter.x - 11 * frame.left.scale
        let rightEdge = handles.rightCenter.x + 11 * frame.right.scale
        arrow(&context, start: CGPoint(x: max(0.5, leftEdge - 2), y: 399),
              tip: CGPoint(x: max(0.5, leftEdge - 15), y: 399),
              length: 4, breadth: 5, color: horizontal)
        arrow(&context, start: CGPoint(x: min(392.5, rightEdge + 2), y: 399),
              tip: CGPoint(x: min(392.5, rightEdge + 15), y: 399),
              length: 4, breadth: 5, color: horizontal)
        housing(&context, center: handles.leftCenter, scale: frame.left.scale)
        housing(&context, center: handles.rightCenter, scale: frame.right.scale)
    }
    private static func housing(_ context: inout GraphicsContext, center: CGPoint, scale: Double) {
        let radius = 11 * scale
        context.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                                           width: 2 * radius, height: 2 * radius)), with: .color(R.color(0x2B9794)))
        var grip = Path()
        grip.move(to: CGPoint(x: center.x - 1.5 * scale, y: center.y))
        grip.addLine(to: CGPoint(x: center.x + 1.5 * scale, y: center.y))
        context.stroke(grip, with: .color(.white), style: StrokeStyle(lineWidth: 1.5 * scale, lineCap: .round))
    }
    private static func arrow(_ context: inout GraphicsContext, start: CGPoint, tip: CGPoint,
                              length: Double, breadth: Double, color: Color) {
        let distance = hypot(tip.x - start.x, tip.y - start.y)
        guard distance > 0.01 else { return }
        let ux = (tip.x - start.x) / distance, uy = (tip.y - start.y) / distance
        let head = min(length, distance)
        let base = CGPoint(x: tip.x - ux * head, y: tip.y - uy * head)
        var line = Path(); line.move(to: start); line.addLine(to: base)
        context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
        var triangle = Path(); triangle.move(to: tip)
        triangle.addLine(to: CGPoint(x: base.x - uy * breadth / 2, y: base.y + ux * breadth / 2))
        triangle.addLine(to: CGPoint(x: base.x + uy * breadth / 2, y: base.y - ux * breadth / 2))
        triangle.closeSubpath(); context.fill(triangle, with: .color(color))
    }
}
