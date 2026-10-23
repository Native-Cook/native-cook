import SwiftUI
import CoreText
import UIKit

@MainActor
enum OwlAlarmRulerRenderer {
    private static let lidClose = 0.6
    private static let hours = ["12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"]
    private static let tens = ["0", "1", "2", "3", "4", "5", "0"]
    private static let ones = ["0", "5", "0"]

    private static let headlineFont = UIFont.systemFont(ofSize: OwlAlarmRulerTheme.type.headline.size, weight: .semibold)
    private static let timeFont: UIFont = {
        let font = UIFont.systemFont(ofSize: OwlAlarmRulerTheme.type.time.size, weight: .heavy)
        return font.fontDescriptor.withDesign(.rounded).map { UIFont(descriptor: $0, size: OwlAlarmRulerTheme.type.time.size) } ?? font
    }()
    private static let digitFont: UIFont = {
        let descriptor = timeFont.fontDescriptor.addingAttributes([
            .featureSettings: [[
                UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector
            ]]
        ])
        return UIFont(descriptor: descriptor, size: OwlAlarmRulerTheme.type.time.size)
    }()
    private static let meridiemFont = UIFont.systemFont(ofSize: OwlAlarmRulerTheme.type.meridiem.size, weight: .semibold)
    private static let captionFont = UIFont.systemFont(ofSize: OwlAlarmRulerTheme.type.caption.size, weight: .regular)
    private static let ctaFont: UIFont = {
        let base = UIFont.systemFont(ofSize: OwlAlarmRulerTheme.type.cta.size, weight: .medium)
        let descriptor = base.fontDescriptor.addingAttributes([
            .featureSettings: [[
                UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector
            ]]
        ])
        return UIFont(descriptor: descriptor, size: OwlAlarmRulerTheme.type.cta.size)
    }()

    private static let hourWidths = hours.map { measure($0, font: timeFont, kern: 0).width }
    private static let digitWidth = measure("0", font: digitFont, kern: 0).width
    private static let hourWidth = hourWidths.max() ?? 0
    private static let clockCellHeight = timeFont.lineHeight

    static func draw(
        context: CGContext,
        size: CGSize,
        top: Double,
        bottom: Double,
        frame: OwlAlarmRulerMotion.Frame,
        still: Bool,
        label: String,
        backPress: Double
    ) {
        let pose = OwlAlarmRulerMotion.poseOf(frame, still)
        let width = size.width
        let height = size.height

        drawBackdrop(context, width, height, top, pose.sundial, pose.lift)
        drawHeader(context, width, top, backPress)
        drawMascot(context, width, top, pose)
        drawCopy(context, width, top)
        drawOdometer(context, width, top, frame.pos)
        drawRuler(context, width, top, frame.pos, pose.needle)
        drawCaption(context, width, top)
        drawCTA(context, width, height, bottom, frame.press, pose.arrow, label)
    }

    private static func drawBackdrop(_ context: CGContext, _ width: CGFloat, _ height: CGFloat, _ top: Double, _ sundial: Double, _ lift: Double) {
        let layout = OwlAlarmRulerTheme.layout
        context.setFillColor(color(OwlAlarmRulerTheme.colors.page).cgColor)
        context.fill(CGRect(origin: .zero, size: CGSize(width: width, height: height)))

        let reach = width + height
        let y0 = CGFloat(top + layout.diagonal.y0)
        let path = CGMutablePath()
        path.move(to: CGPoint(x: -reach, y: y0 - reach * layout.diagonal.slope))
        path.addLine(to: CGPoint(x: reach, y: y0 + reach * layout.diagonal.slope))
        path.addLine(to: CGPoint(x: reach, y: -reach))
        path.addLine(to: CGPoint(x: -reach, y: -reach))
        path.closeSubpath()
        context.saveGState()
        context.translateBy(x: width / 2 + layout.sundialPivot.dx, y: top + layout.sundialPivot.y)
        context.rotate(by: CGFloat(sundial * .pi / 180))
        context.translateBy(x: -(width / 2 + layout.sundialPivot.dx), y: -(top + layout.sundialPivot.y))
        context.addPath(path)
        context.setFillColor(color(OwlAlarmRulerTheme.colors.band).cgColor)
        context.fillPath()
        context.restoreGState()

        let shadow = layout.groundShadow
        let scale = 1 - 0.3 * clamp01(lift)
        context.setFillColor(color(OwlAlarmRulerTheme.colors.shadow, alpha: 1 - 0.45 * clamp01(lift)).cgColor)
        context.fillEllipse(in: CGRect(
            x: width / 2 + shadow.dx - shadow.rx * scale,
            y: top + shadow.cy - shadow.ry * scale,
            width: shadow.rx * 2 * scale,
            height: shadow.ry * 2 * scale
        ))
    }

    private static func drawHeader(_ context: CGContext, _ width: CGFloat, _ top: Double, _ backPress: Double) {
        let layout = OwlAlarmRulerTheme.layout
        let space = OwlAlarmRulerTheme.space
        let back = layout.back
        let backRect = CGRect(x: space.backInset, y: top + space.headerTop, width: back.d, height: back.d)
        context.saveGState()
        let scale = 1 - (1 - 0.97) * clamp01(backPress)
        context.translateBy(x: backRect.midX, y: backRect.midY)
        context.scaleBy(x: scale, y: scale)
        context.translateBy(x: -backRect.midX, y: -backRect.midY)
        context.setFillColor(color(OwlAlarmRulerTheme.colors.backFill).cgColor)
        context.fillEllipse(in: backRect)
        let arrow = back.arrow
        context.setStrokeColor(color(OwlAlarmRulerTheme.colors.ink).cgColor)
        context.setLineWidth(arrow.stroke)
        context.setLineCap(.round)
        context.setLineJoin(.round)
        let centerY = backRect.midY
        let path = CGMutablePath()
        path.move(to: CGPoint(x: backRect.minX + arrow.armX, y: centerY - arrow.armDy))
        path.addLine(to: CGPoint(x: backRect.minX + arrow.tipX, y: centerY))
        path.addLine(to: CGPoint(x: backRect.minX + arrow.armX, y: centerY + arrow.armDy))
        path.move(to: CGPoint(x: backRect.minX + arrow.tipX, y: centerY))
        path.addLine(to: CGPoint(x: backRect.minX + arrow.endX, y: centerY))
        context.addPath(path)
        context.strokePath()
        context.restoreGState()

        let track = layout.track
        let trackRect = CGRect(x: (width - track.w) / 2, y: top + space.headerTop + back.d / 2 - track.h / 2, width: track.w, height: track.h)
        context.saveGState()
        context.addPath(UIBezierPath(roundedRect: trackRect, cornerRadius: track.h / 2).cgPath)
        context.clip()
        context.setFillColor(color(OwlAlarmRulerTheme.colors.track).cgColor)
        context.fill(trackRect)
        context.setFillColor(color(OwlAlarmRulerTheme.colors.ink).cgColor)
        let fillRect = CGRect(x: trackRect.minX, y: trackRect.minY, width: track.fill, height: track.h)
        context.addPath(UIBezierPath(roundedRect: fillRect, cornerRadius: track.h / 2).cgPath)
        context.fillPath()
        context.restoreGState()
    }

    private static func drawMascot(_ context: CGContext, _ width: CGFloat, _ top: Double, _ pose: OwlAlarmRulerMotion.Pose) {
        let layout = OwlAlarmRulerTheme.layout
        let mascot = layout.mascot
        let left = width / 2 + mascot.dx - mascot.w / 2
        let imageRect = CGRect(x: left, y: top + mascot.y, width: mascot.w, height: mascot.h)
        let pivot = CGPoint(x: imageRect.midX, y: imageRect.minY + mascot.h * mascot.pivotY)
        context.saveGState()
        context.translateBy(x: pivot.x, y: pivot.y + pose.jumpY)
        context.rotate(by: CGFloat(pose.lean * .pi / 180))
        context.scaleBy(x: pose.sx, y: pose.sy)
        context.translateBy(x: -pivot.x, y: -pivot.y)
        draw(image: OwlAlarmRulerAssets.mascot, context: context, rect: imageRect)

        for (index, eye) in layout.eyes.enumerated() {
            drawEye(context, imageRect, eye, index, pose)
        }
        context.restoreGState()
    }

    private static func drawEye(
        _ context: CGContext,
        _ imageRect: CGRect,
        _ eye: OwlAlarmRulerTheme.Eye,
        _ index: Int,
        _ pose: OwlAlarmRulerMotion.Pose
    ) {
        let r = eye.r
        let eyeRect = CGRect(x: imageRect.minX + eye.cx - r, y: imageRect.minY + eye.cy - r, width: r * 2, height: r * 2)
        context.saveGState()
        context.addEllipse(in: eyeRect)
        context.clip()
        let rest = OwlAlarmRulerTheme.layout.pupil.rest[index]
        let diameter = OwlAlarmRulerTheme.layout.pupil.d
        let pupilRect = CGRect(
            x: eyeRect.minX + r - diameter / 2 + rest.x + pose.pupilDX,
            y: eyeRect.minY + r - diameter / 2 + rest.y + pose.pupilDY,
            width: diameter,
            height: diameter
        )
        context.saveGState()
        context.translateBy(x: pupilRect.midX, y: pupilRect.midY)
        context.scaleBy(x: pose.pupilScale, y: pose.pupilScale)
        context.translateBy(x: -pupilRect.midX, y: -pupilRect.midY)
        draw(image: OwlAlarmRulerAssets.pupil, context: context, rect: pupilRect)
        context.restoreGState()

        let blink = clamp01(pose.blink)
        let upperY = eyeRect.minY - 2 * r - 1 + (2 * r + 1 - 2 * r * (1 - lidClose)) * blink
        let lowerY = eyeRect.minY + 2 * r + 1 - (2 * r + 1 - 2 * r * lidClose) * blink
        context.setFillColor(color(OwlAlarmRulerTheme.colors.lid).cgColor)
        context.fill(CGRect(x: eyeRect.minX, y: upperY, width: eyeRect.width, height: eyeRect.height))
        let lash = OwlAlarmRulerTheme.layout.lash / 3
        context.setFillColor(color(OwlAlarmRulerTheme.colors.lash).cgColor)
        context.fill(CGRect(x: eyeRect.minX, y: upperY + eyeRect.height - lash, width: eyeRect.width, height: lash))
        context.setFillColor(color(OwlAlarmRulerTheme.colors.lid).cgColor)
        context.fill(CGRect(x: eyeRect.minX, y: lowerY, width: eyeRect.width, height: eyeRect.height))
        context.restoreGState()
    }

    private static func drawCopy(_ context: CGContext, _ width: CGFloat, _ top: Double) {
        let type = OwlAlarmRulerTheme.type.headline
        let attributes = textAttributes(font: headlineFont, color: type.color, kern: type.ls)
        for (index, item) in [OwlAlarmRulerTheme.copy.line1, OwlAlarmRulerTheme.copy.line2].enumerated() {
            let baseline = OwlAlarmRulerTheme.layout.headline[index].base
            drawCentered(item, context, width / 2, top + baseline - OwlAlarmRulerTheme.ASCENT * type.size, attributes)
        }
    }

    private static func drawOdometer(_ context: CGContext, _ width: CGFloat, _ top: Double, _ pos: Double) {
        let time = OwlAlarmRulerTheme.layout.time
        let style = OwlAlarmRulerTheme.type.time
        let wheel = OwlAlarmRulerMotion.wheels(pos)
        let hourIndex = Int(floor(wheel.hour))
        let hourFraction = wheel.hour - floor(wheel.hour)
        let currentHourWidth = hourWidths[min(hourIndex, hourWidths.count - 1)] +
            (hourWidths[min(hourIndex + 1, hourWidths.count - 1)] - hourWidths[min(hourIndex, hourWidths.count - 1)]) * hourFraction
        let colonWidth = measure(":", font: timeFont, kern: 0).width
        let totalWidth = hourWidth + time.colonGap + colonWidth + time.tight + digitWidth + time.tight + digitWidth
        let center = width / 2 + time.dx - (hourWidth - currentHourWidth) / 2
        let rowLeft = center - totalWidth / 2
        let rowTop = top + time.base - OwlAlarmRulerTheme.ASCENT * style.size
        let hourAttributes = textAttributes(font: timeFont, color: style.color, kern: style.ls)
        let digitAttributes = textAttributes(font: digitFont, color: style.color, kern: style.ls)

        drawWheel(hours, wheel.hour, context, CGRect(x: rowLeft, y: rowTop, width: hourWidth, height: clockCellHeight), .right, timeFont, hourAttributes)
        let colonX = rowLeft + hourWidth + time.colonGap
        drawString(":", context, CGPoint(x: colonX, y: rowTop - time.colonRise), hourAttributes)
        let tensX = colonX + colonWidth + time.tight
        drawWheel(tens, wheel.tens, context, CGRect(x: tensX, y: rowTop, width: digitWidth, height: clockCellHeight), .center, digitFont, digitAttributes)
        let onesX = tensX + digitWidth + time.tight
        drawWheel(ones, wheel.ones, context, CGRect(x: onesX, y: rowTop, width: digitWidth, height: clockCellHeight), .center, digitFont, digitAttributes)

    }

    private static func drawWheel(_ values: [String], _ value: Double, _ context: CGContext, _ rect: CGRect, _ alignment: NSTextAlignment, _ font: UIFont, _ attributes: [NSAttributedString.Key: Any]) {
        context.saveGState()
        context.clip(to: rect)
        let translation = -value * clockCellHeight
        for (index, string) in values.enumerated() {
            let measured = measure(string, font: font, kern: 0)
            let x: CGFloat
            switch alignment {
            case .right: x = rect.maxX - measured.width
            default: x = rect.midX - measured.width / 2
            }
            drawString(string, context, CGPoint(x: x, y: rect.minY + CGFloat(index) * clockCellHeight + translation), attributes)
        }
        context.restoreGState()
    }

    static func updateMeridiem(
        pm: UILabel,
        am: UILabel,
        width: Double,
        top: Double,
        frame: OwlAlarmRulerMotion.Frame,
        still: Bool
    ) {
        let type = OwlAlarmRulerTheme.type.meridiem
        let layout = OwlAlarmRulerTheme.layout.meridiem
        let center = CGPoint(x: width / 2 + layout.dx, y: top + layout.base - OwlAlarmRulerTheme.ASCENT * type.size + meridiemFont.lineHeight / 2)
        let bounds = CGRect(x: 0, y: 0, width: layout.w, height: meridiemFont.lineHeight)
        for (label, text) in [(pm, "PM"), (am, "AM")] {
            label.text = text
            label.font = meridiemFont
            label.textColor = UIColor(type.color)
            label.textAlignment = .center
            label.adjustsFontForContentSizeCategory = false
            label.isUserInteractionEnabled = false
            label.bounds = bounds
            label.center = center
            label.layer.isDoubleSided = false
        }

        let cosine = cos(frame.flip * .pi / 180)
        if still {
            pm.alpha = max(0, cosine)
            am.alpha = max(0, -cosine)
            pm.layer.transform = CATransform3DIdentity
            am.layer.transform = CATransform3DIdentity
            return
        }

        pm.alpha = cosine > 0 ? 1 : 0
        am.alpha = cosine < 0 ? 1 : 0
        pm.layer.transform = xRotation(-frame.flip)
        am.layer.transform = xRotation(180 - frame.flip)
    }

    private static func xRotation(_ degrees: Double) -> CATransform3D {
        var transform = CATransform3DIdentity
        transform.m34 = -1 / 260
        return CATransform3DRotate(transform, degrees * .pi / 180, 1, 0, 0)
    }

    private static func drawRuler(_ context: CGContext, _ width: CGFloat, _ top: Double, _ position: Double, _ bend: Double) {
        let ruler = OwlAlarmRulerTheme.layout.ruler
        let centerX = width / 2
        let baseline = top + ruler.base
        let limit = width / 2 - OwlAlarmRulerTheme.space.screenInset - ruler.tickW / 2 + 0.01
        let reach = Int(ceil(limit / ruler.spacing + ruler.fade)) + 1
        let zero = Int(floor(position + 0.5))
        for tick in (zero - reach)...(zero + reach) {
            let distance = Double(tick) - position
            let x = centerX + distance * ruler.spacing
            let over = (abs(x - centerX) - limit) / (ruler.fade * ruler.spacing)
            let alpha = 1 - clamp01(over)
            guard alpha > 0 else { continue }
            let shade = OwlAlarmRulerMotion.tickShade(distance)
            let dark = OwlAlarmRulerTheme.colors.tickDark
            let light = OwlAlarmRulerTheme.colors.tickLight
            let red = round(dark[0] + (light[0] - dark[0]) * shade) / 255
            let green = round(dark[1] + (light[1] - dark[1]) * shade) / 255
            let blue = round(dark[2] + (light[2] - dark[2]) * shade) / 255
            let roundedAlpha = round(alpha * 10_000) / 10_000
            let tickColor = UIColor(red: red, green: green, blue: blue, alpha: roundedAlpha)
            let tickHeight = OwlAlarmRulerMotion.tickHeight(distance)
            let tickRect = CGRect(x: x - ruler.tickW / 2, y: baseline - tickHeight, width: ruler.tickW, height: tickHeight)
            context.setFillColor(tickColor.cgColor)
            context.addPath(UIBezierPath(roundedRect: tickRect, cornerRadius: ruler.tickW / 2).cgPath)
            context.fillPath()
        }

        let needle = ruler.needle
        let foot = top + needle.cy + needle.h / 2
        context.saveGState()
        context.translateBy(x: centerX, y: foot)
        context.rotate(by: CGFloat(bend * .pi / 180))
        context.translateBy(x: -centerX, y: -foot)
        let needleRect = CGRect(x: centerX - needle.w / 2, y: foot - needle.h, width: needle.w, height: needle.h)
        context.setFillColor(color(OwlAlarmRulerTheme.colors.needle).cgColor)
        context.addPath(UIBezierPath(roundedRect: needleRect, cornerRadius: needle.w / 2).cgPath)
        context.fillPath()
        context.restoreGState()
    }

    private static func drawCaption(_ context: CGContext, _ width: CGFloat, _ top: Double) {
        let style = OwlAlarmRulerTheme.type.caption
        let y = top + OwlAlarmRulerTheme.layout.caption.base - OwlAlarmRulerTheme.ASCENT * style.size
        drawCentered(OwlAlarmRulerTheme.copy.caption, context, width / 2, y, textAttributes(font: captionFont, color: style.color, kern: style.ls))
    }

    private static func drawCTA(_ context: CGContext, _ width: CGFloat, _ height: CGFloat, _ bottom: Double, _ press: Double, _ arrowOffset: Double, _ label: String) {
        let layout = OwlAlarmRulerTheme.layout
        let space = OwlAlarmRulerTheme.space
        let rect = CGRect(x: space.screenInset, y: height - bottom - space.ctaBottom - layout.cta.h, width: width - 2 * space.screenInset, height: layout.cta.h)
        let scale = 1 - (1 - 0.97) * clamp01(press)
        context.saveGState()
        context.translateBy(x: rect.midX, y: rect.midY)
        context.scaleBy(x: scale, y: scale)
        context.translateBy(x: -rect.midX, y: -rect.midY)
        context.setFillColor(color(OwlAlarmRulerTheme.colors.cta).cgColor)
        context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: OwlAlarmRulerTheme.radius.cta).cgPath)
        context.fillPath()
        let textY = rect.minY + layout.cta.base - OwlAlarmRulerTheme.ASCENT * OwlAlarmRulerTheme.type.cta.size
        drawString("\(OwlAlarmRulerTheme.copy.cta) \(label)", context, CGPoint(x: rect.minX + space.ctaPadStart, y: textY), textAttributes(font: ctaFont, color: OwlAlarmRulerTheme.type.cta.color, kern: OwlAlarmRulerTheme.type.cta.ls))

        let arrow = layout.ctaArrow
        let shaft = arrow.shaft + arrow.stroke / 2
        let originX = rect.maxX - space.ctaPadEnd - shaft - arrow.headW + arrowOffset
        let originY = rect.midY - arrow.headH / 2
        context.setStrokeColor(color(OwlAlarmRulerTheme.colors.white).cgColor)
        context.setFillColor(color(OwlAlarmRulerTheme.colors.white).cgColor)
        context.setLineWidth(arrow.stroke)
        context.setLineCap(.round)
        context.move(to: CGPoint(x: originX + arrow.stroke / 2, y: originY + arrow.headH / 2))
        context.addLine(to: CGPoint(x: originX + shaft + arrow.headW * 0.4, y: originY + arrow.headH / 2))
        context.strokePath()
        let head = CGMutablePath()
        head.move(to: CGPoint(x: originX + shaft + arrow.headR, y: originY + arrow.headR))
        head.addLine(to: CGPoint(x: originX + shaft + arrow.headW - arrow.headR, y: originY + arrow.headH / 2))
        head.addLine(to: CGPoint(x: originX + shaft + arrow.headR, y: originY + arrow.headH - arrow.headR))
        head.closeSubpath()
        context.addPath(head)
        context.fillPath()
        context.setLineWidth(2 * arrow.headR)
        context.setLineJoin(.round)
        context.addPath(head)
        context.strokePath()
        context.restoreGState()
    }

    private static func textAttributes(font: UIFont, color: Color, kern: Double) -> [NSAttributedString.Key: Any] {
        [.font: font, .foregroundColor: UIColor(color), .kern: kern]
    }

    private static func measure(_ string: String, font: UIFont, kern: Double) -> CGSize {
        (string as NSString).size(withAttributes: textAttributes(font: font, color: OwlAlarmRulerTheme.colors.ink, kern: kern))
    }

    private static func drawString(_ string: String, _ context: CGContext, _ point: CGPoint, _ attributes: [NSAttributedString.Key: Any]) {
        UIGraphicsPushContext(context)
        (string as NSString).draw(at: point, withAttributes: attributes)
        UIGraphicsPopContext()
    }

    private static func drawCentered(_ string: String, _ context: CGContext, _ centerX: CGFloat, _ y: CGFloat, _ attributes: [NSAttributedString.Key: Any]) {
        let width = (string as NSString).size(withAttributes: attributes).width
        drawString(string, context, CGPoint(x: centerX - width / 2, y: y), attributes)
    }

    private static func draw(image: UIImage, context: CGContext, rect: CGRect) {
        UIGraphicsPushContext(context)
        image.draw(in: rect)
        UIGraphicsPopContext()
    }

    private static func color(_ color: Color, alpha: Double = 1) -> UIColor {
        UIColor(color).withAlphaComponent(alpha)
    }

    private static func clamp01(_ value: Double) -> Double {
        min(1, max(0, value))
    }
}
