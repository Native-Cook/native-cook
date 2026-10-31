import SwiftUI

/// Canvas artwork in screen points, with rotations in radians and normalized pose progress.
/// Items cross behind the mesh at flight progress 0.86; the layer order makes the bag contain them.
struct MarketBagCheckoutArt {
    let geometry: MarketBagCheckoutGeometry
    private func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(uiColor: MarketBagCheckoutResources.color(hex, alpha))
    }

    /// Draws the cached counter first, then bag shadow, cords, packed items, mesh, flying items,
    /// row labels and payment feedback. Bag deformation comes from the shared closed-form physics.
    func draw(_ context: GraphicsContext, _ pose: MarketBagCheckoutMotion.Pose, _ holding: Bool) {
        let resources = MarketBagCheckoutResources.shared
        context.draw(
            resources.page(geometry), in: CGRect(x: 0, y: 0, width: geometry.width, height: geometry.height))
        let stretch = MarketBagCheckoutMotion.bagStretch(pose)
        let cinch =
            pose.paid >= 0
            ? MarketBagCheckoutMotion.easeInOut(MarketBagCheckoutMotion.seg(pose.paid, 0, 0.35)) * (1 - pose.reset)
            : 0
        let hopProgress = pose.paid >= 0 ? MarketBagCheckoutMotion.seg(pose.paid, 0.3, 0.85) : 0
        let hop = sin(Double.pi * hopProgress) * 16 * (1 - pose.reset)
        let bagLength = geometry.bagLength + 34 * stretch
        let rim = 58 - 34 * cinch
        let belly = 52 + 38 * min(1, stretch * 1.3)
        let top = geometry.rimY - hop
        let fill = Double(pose.land.filter { $0 >= 0 }.count) * 0.2
        var outline = Path()
        for i in 0...24 {
            let v = Double(i) / 24
            let halfWidth = MarketBagCheckoutMotion.halfWidth(v, rim, belly)
            let point = CGPoint(x: geometry.centerX - halfWidth, y: top + v * bagLength)
            if i == 0 { outline.move(to: point) } else { outline.addLine(to: point) }
        }
        for i in stride(from: 24, through: 0, by: -1) {
            let v = Double(i) / 24
            outline.addLine(
                to: CGPoint(
                    x: geometry.centerX + MarketBagCheckoutMotion.halfWidth(v, rim, belly), y: top + v * bagLength)
            )
        }
        outline.closeSubpath()
        var points: [CGPoint] = []
        for row in 0...9 {
            let v = Double(row) / 9
            let halfWidth = MarketBagCheckoutMotion.halfWidth(v, rim, belly)
            let columnOffset = row % 2 == 0 ? 0.0 : 0.5
            for col in 0...9 {
                let u = min(1, (Double(col) + columnOffset) / 9)
                let a = (u - 0.5) * Double.pi
                points.append(
                    CGPoint(
                        x: geometry.centerX + sin(a) * halfWidth,
                        y: top + v * bagLength + cos(a) * 3 * v * (0.5 + fill))
                )
            }
        }
        var net = Path()
        var knots = Path()
        for row in 0..<9 {
            for col in 0...9 {
                let a = row * 10 + col
                let even = row % 2 == 0
                let leftColumn = even ? col - 1 : col
                let rightColumn = even ? col : col + 1
                if leftColumn >= 0 {
                    net.move(to: points[a])
                    net.addLine(to: points[(row + 1) * 10 + leftColumn])
                }
                if rightColumn <= 9 {
                    net.move(to: points[a])
                    net.addLine(to: points[(row + 1) * 10 + rightColumn])
                }
                if row > 0 && row < 9 {
                    knots.addEllipse(in: CGRect(x: points[a].x - 1.5, y: points[a].y - 1.5, width: 3, height: 3))
                }
            }
        }
        var hem = Path()
        hem.move(to: CGPoint(x: geometry.centerX - rim, y: top))
        hem.addQuadCurve(
            to: CGPoint(x: geometry.centerX + rim, y: top),
            control: CGPoint(x: geometry.centerX, y: top + 6 + 4 * cinch))
        var handles = Path()
        handles.move(to: CGPoint(x: geometry.centerX - rim + 4, y: top))
        handles.addCurve(
            to: CGPoint(x: geometry.centerX - 2, y: geometry.hookY),
            control1: CGPoint(x: geometry.centerX - rim - 10, y: top - 30),
            control2: CGPoint(x: geometry.centerX - 18, y: geometry.hookY - 8 - hop * 0.2))
        handles.move(to: CGPoint(x: geometry.centerX + rim - 4, y: top))
        handles.addCurve(
            to: CGPoint(x: geometry.centerX + 2, y: geometry.hookY),
            control1: CGPoint(x: geometry.centerX + rim + 10, y: top - 30),
            control2: CGPoint(x: geometry.centerX + 18, y: geometry.hookY - 8 - hop * 0.2))
        var shadow = context
        shadow.translateBy(x: 10, y: 12 + hop * 0.3)
        shadow.addFilter(.blur(radius: 14))
        shadow.fill(outline, with: .color(color(0x3C321E, 0.16)))
        context.stroke(handles, with: .color(color(0xB9A988)), style: StrokeStyle(lineWidth: 4, lineCap: .round))
        context.stroke(handles, with: .color(color(0xEDE3CC)), style: StrokeStyle(lineWidth: 2.6, lineCap: .round))
        /// Follows a quadratic flight arc from the row icon to its slot inside the bag.
        /// The image fits its point-sized square before the flight scale and radian rotation are applied.
        func drawItem(_ i: Int) {
            let item = MarketBagCheckoutMotion.items[i]
            let f = pose.fly[i]
            let sx = geometry.centerX + item.slotX * belly * 1.5
            let sy = top + item.slotY * bagLength + item.size * 0.22
            let fx = 49.0
            let fy = geometry.rowY(i)
            let apex = min(fy, sy) - 70
            let x = fx + (sx - fx) * f
            let y = (1 - f) * (1 - f) * fy + 2 * (1 - f) * f * apex + f * f * sy
            let scale = (38 + (item.size - 38) * min(1, f * 1.4)) / item.size
            let rotation = item.rotation * f + sin(Double.pi * f) * 0.6
            var itemContext = context
            itemContext.translateBy(x: x, y: y)
            itemContext.rotate(by: .radians(rotation))
            itemContext.scaleBy(x: scale, y: scale)
            let image = resources.images[i]
            let imageScale = min(item.size / image.size.width, item.size / image.size.height)
            itemContext.draw(
                resources.canvasImages[i],
                in: CGRect(
                    x: -image.size.width * imageScale / 2, y: -image.size.height * imageScale / 2,
                    width: image.size.width * imageScale,
                    height: image.size.height * imageScale))
        }
        for i in [3, 1, 0, 2, 4] where pose.fly[i] > 0.86 { drawItem(i) }
        context.fill(outline, with: .color(color(0xF2EAD8, 0.10)))
        var netShadow = context
        netShadow.translateBy(x: 1.2, y: 1.6)
        netShadow.stroke(net, with: .color(color(0x3C2D19, 0.35)), lineWidth: 1.6)
        context.stroke(net, with: .color(color(0xF2EAD8)), lineWidth: 1.5)
        context.fill(knots, with: .color(color(0xFBF5E6)))
        context.stroke(hem, with: .color(color(0xB9A988)), style: StrokeStyle(lineWidth: 5, lineCap: .round))
        context.stroke(hem, with: .color(color(0xEDE3CC)), style: StrokeStyle(lineWidth: 3.6, lineCap: .round))
        context.fill(
            Path(
                roundedRect: CGRect(x: geometry.centerX + 6, y: top + 10 + 10 * cinch, width: 9, height: 14),
                cornerRadius: 4), with: .color(color(0x9C6B3C)))
        for i in 0..<5 where pose.fly[i] <= 0.86 { drawItem(i) }
        for i in 0..<5 {
            let y = geometry.rowY(i)
            let name = resources.labels["name\(i)"]!
            let meta = resources.labels["meta\(i)"]!
            let price = resources.labels["price\(i)"]!
            var row = context
            row.opacity = 1 - 0.55 * MarketBagCheckoutMotion.smooth(0, 0.6, pose.fly[i])
            let textY = y - (name.height + 1 + meta.height) / 2
            resources.drawLabel(row, "name\(i)", 82, textY)
            resources.drawLabel(row, "meta\(i)", 82, textY + name.height + 1)
            resources.drawLabel(row, "price\(i)", geometry.width - 34, y - price.height / 2, align: 1)
            let transitionProgress = MarketBagCheckoutMotion.smooth(0.7, 1, pose.fly[i])
            var tick = context
            tick.opacity = transitionProgress
            tick.translateBy(x: 69, y: geometry.cardY + 12 + Double(i) * 46 + 15)
            tick.scaleBy(x: 0.6 + 0.4 * transitionProgress, y: 0.6 + 0.4 * transitionProgress)
            tick.fill(resources.tickCircle, with: .color(color(0x3F7A57)))
            tick.stroke(
                resources.tickCheck, with: .color(.white),
                style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
        }
        var button = context
        if holding {
            button.translateBy(x: geometry.width / 2, y: geometry.buttonY + 28)
            button.scaleBy(x: 0.97, y: 0.97)
            button.translateBy(x: -geometry.width / 2, y: -geometry.buttonY - 28)
        }
        let buttonPath = resources.buttonPath
        button.fill(buttonPath, with: .color(color(0x1F3A2C)))
        button.clip(to: buttonPath)
        button.fill(
            Path(
                CGRect(x: 16, y: geometry.buttonY, width: geometry.cardWidth * max(0.0001, pose.hold), height: 56)),
            with: .color(color(0x3F7A57)))
        let transitionProgress =
            pose.paid >= 0 ? MarketBagCheckoutMotion.smooth(0.15, 0.4, pose.paid) * (1 - pose.reset) : 0
        var pay = button
        pay.opacity = 1 - transitionProgress
        resources.drawLabel(
            pay, "pay", geometry.centerX,
            geometry.buttonY + (56 - resources.labels["pay"]!.height) / 2 - 8 * transitionProgress, align: 0.5)
        let paidProgress =
            pose.paid >= 0 ? MarketBagCheckoutMotion.smooth(0.25, 0.55, pose.paid) * (1 - pose.reset) : 0
        var paid = button
        paid.opacity = paidProgress
        let textWidth = resources.labels["paid"]!.width
        let start = geometry.centerX - (18 + 8 + textWidth) / 2
        let y = geometry.buttonY + 28 + 8 * (1 - paidProgress)
        var check = Path()
        check.move(to: CGPoint(x: start + 4, y: y + 0.4))
        check.addLine(to: CGPoint(x: start + 7.6, y: y + 3.8))
        check.addLine(to: CGPoint(x: start + 14.2, y: y - 3.4))
        paid.stroke(
            check, with: .color(color(0xF2EAD8)),
            style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
        resources.drawLabel(paid, "paid", start + 26, y - resources.labels["paid"]!.height / 2)
    }
}
