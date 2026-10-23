import UIKit

struct CassetteDeckLayout: Equatable {
    let scale: CGFloat
    let origin: CGPoint

    init(size: CGSize, safeArea: UIEdgeInsets) {
        let usable = CGSize(
            width: size.width,
            height: max(1, size.height - safeArea.top - max(safeArea.bottom, 16))
        )
        scale = min(usable.width / CassetteDeckTheme.stage.width, usable.height / CassetteDeckTheme.stage.height)
        origin = CGPoint(
            x: (size.width - CassetteDeckTheme.stage.width * scale) / 2,
            y: safeArea.top + (usable.height - CassetteDeckTheme.stage.height * scale) / 2
        )
    }

    func stagePoint(_ point: CGPoint) -> CGPoint {
        CGPoint(x: (point.x - origin.x) / scale, y: (point.y - origin.y) / scale)
    }
}

final class CassetteDeckRenderer {
    private enum TextAlign { case left, center, right }

    private let layout: CassetteDeckLayout
    private let fonts: CassetteDeckFonts
    private let backdrop: UIImage
    private let foreground: UIImage

    init(layout: CassetteDeckLayout) {
        self.layout = layout
        let loadedFonts = CassetteDeckFonts()
        fonts = loadedFonts
        let format = UIGraphicsImageRendererFormat()
        format.opaque = false
        format.scale = min(UIScreen.main.scale, 3) * layout.scale
        let renderer = UIGraphicsImageRenderer(size: CassetteDeckTheme.stage, format: format)
        backdrop = renderer.image { context in
            let canvas = context.cgContext
            Self.drawPlate(canvas, fonts: loadedFonts)
            Self.drawWell(canvas)
            Self.drawCassette(canvas, fonts: loadedFonts)
            Self.drawReadout(canvas, fonts: loadedFonts)
            Self.drawKeySlots(canvas, fonts: loadedFonts)
        }
        foreground = renderer.image { context in Self.drawGlass(context.cgContext) }
    }

    func draw(in context: CGContext, model: CassetteDeckModel, bounds: CGRect) {
        context.saveGState()
        context.setFillColor(CassetteDeckTheme.plate.cgColor)
        context.fill(bounds)
        context.translateBy(x: layout.origin.x, y: layout.origin.y)
        context.scaleBy(x: layout.scale, y: layout.scale)
        UIGraphicsPushContext(context)
        backdrop.draw(in: CGRect(origin: .zero, size: CassetteDeckTheme.stage))
        drawTape(context, model: model)
        foreground.draw(in: CGRect(origin: .zero, size: CassetteDeckTheme.stage))
        drawCounter(context, model: model)
        drawMeters(context, model: model)
        drawKeys(context, model: model)
        drawHint(context, model: model)
        if model.ghostOpacity > 0.01 {
            let center = CGPoint(x: model.ghostX, y: model.ghostY)
            Self.fillEllipse(context, center: center, radius: CassetteDeckTheme.fingerSize / 2, color: CassetteDeckTheme.finger.withAlphaComponent(model.ghostOpacity))
            Self.strokeEllipse(context, center: center, radius: CassetteDeckTheme.fingerSize / 2 - 0.5, color: CassetteDeckTheme.fingerEdge.withAlphaComponent(model.ghostOpacity), width: 1)
        }
        UIGraphicsPopContext()
        context.restoreGState()
    }

    private static func drawPlate(_ context: CGContext, fonts: CassetteDeckFonts) {
        let frame = CGRect(x: -200, y: -200, width: CassetteDeckTheme.stage.width + 400, height: CassetteDeckTheme.stage.height + 600)
        linear(context, rect: frame, colors: [CassetteDeckTheme.plateLight, CassetteDeckTheme.plate, CassetteDeckTheme.plateDeep], locations: [0, 0.45, 1])
        for index in 0..<Int(frame.height * 3.2) {
            let y = frame.minY + hash(Double(index) + 1) * frame.height
            let x = frame.minX + hash(Double(index * 3) + 1) * frame.width * 0.6 - 20
            let length = 60 + hash(Double(index * 7) + 1) * frame.width
            let light = hash(Double(index * 11) + 1) > 0.5
            let alpha = 0.05 + hash(Double(index * 13) + 1) * (light ? 0.12 : 0.06)
            fill(context, CGRect(x: x, y: y, width: length, height: 0.35), light ? .white.withAlphaComponent(alpha) : UIColor(hexValue: 0x5A5F66, alpha: alpha))
        }
        engraved(context, CassetteDeckTheme.brand, x: CassetteDeckTheme.edge, baseline: CassetteDeckTheme.brandBaseline, font: fonts.semibold, size: 22, tracking: 5)
        engraved(context, CassetteDeckTheme.model, x: CassetteDeckTheme.edge, baseline: CassetteDeckTheme.modelBaseline, font: fonts.medium, size: 10, tracking: 1.6, color: CassetteDeckTheme.engraveMuted)
        radial(context, center: CGPoint(x: 370, y: 40), radius: 9, colors: [CassetteDeckTheme.led.withAlphaComponent(0.35), CassetteDeckTheme.led.withAlphaComponent(0)])
        radial(context, center: CGPoint(x: 369, y: 39), radius: 5, colors: [UIColor(hexValue: 0xFFE2B8), CassetteDeckTheme.led, UIColor(hexValue: 0xB4520E)], locations: [0, 0.5, 1])
        strokeEllipse(context, center: CGPoint(x: 370, y: 40), radius: 5, color: UIColor(hexValue: 0x5A5F66, alpha: 0.6), width: 1)
        engraved(context, "POWER", x: 370, baseline: 62, font: fonts.medium, size: 9, tracking: 1.4, align: .center, color: CassetteDeckTheme.engraveMuted)
    }

    private static func drawWell(_ context: CGContext) {
        let well = CassetteDeckTheme.well
        linear(context, path: rounded(well.cgRect.insetBy(dx: -2, dy: -2), radius: well.radius + 2), bounds: well.cgRect.insetBy(dx: -2, dy: -2), colors: [UIColor(hexValue: 0x7D8288), UIColor(hexValue: 0xF4F5F6)])
        fill(context, rounded(well.cgRect, radius: well.radius), CassetteDeckTheme.wellColor)
        context.saveGState()
        context.addPath(rounded(well.cgRect, radius: well.radius)); context.clip()
        context.setShadow(offset: CGSize(width: 0, height: 8), blur: 10, color: UIColor.black.withAlphaComponent(0.7).cgColor)
        fill(context, CGRect(x: well.x, y: well.y - 12, width: well.width, height: 30), .black)
        context.restoreGState()
    }

    private static func drawCassette(_ context: CGContext, fonts: CassetteDeckFonts) {
        let cassette = CassetteDeckTheme.cassette
        context.saveGState()
        context.setShadow(offset: CGSize(width: 2, height: 6), blur: 6, color: UIColor.black.withAlphaComponent(0.6).cgColor)
        fill(context, rounded(cassette.cgRect, radius: cassette.radius), .black)
        context.restoreGState()
        linear(context, path: rounded(cassette.cgRect, radius: cassette.radius), bounds: cassette.cgRect, colors: [CassetteDeckTheme.shellLight, CassetteDeckTheme.shell, UIColor(hexValue: 0x1E1F22)], locations: [0, 0.4, 1])
        stroke(context, rounded(cassette.cgRect.insetBy(dx: 0.5, dy: 0.5), radius: cassette.radius), .white.withAlphaComponent(0.12), width: 1)
        let bottomY = cassette.y + cassette.height - 42
        let bottom = CGMutablePath()
        bottom.move(to: CGPoint(x: cassette.x + 62, y: bottomY))
        bottom.addLine(to: CGPoint(x: cassette.x + cassette.width - 62, y: bottomY))
        bottom.addLine(to: CGPoint(x: cassette.x + cassette.width - 40, y: cassette.y + cassette.height))
        bottom.addLine(to: CGPoint(x: cassette.x + 40, y: cassette.y + cassette.height)); bottom.closeSubpath()
        linear(context, path: bottom, bounds: CGRect(x: cassette.x, y: bottomY, width: cassette.width, height: 42), colors: [UIColor(hexValue: 0x36373B), UIColor(hexValue: 0x232427)])
        line(context, from: CGPoint(x: cassette.x + 62, y: bottomY + 0.5), to: CGPoint(x: cassette.x + cassette.width - 62, y: bottomY + 0.5), color: .white.withAlphaComponent(0.14), width: 1)
        let holes: [(CGFloat, CGFloat)] = [(cassette.x + 86, 22), (cassette.x + 128, 18), (cassette.x + cassette.width / 2 - 15, 30), (cassette.x + cassette.width - 146, 18), (cassette.x + cassette.width - 108, 22)]
        for (x, width) in holes {
            fill(context, rounded(CGRect(x: x, y: bottomY + 14, width: width, height: 20), radius: 3), UIColor(hexValue: 0x0B0B0C))
            fill(context, CGRect(x: x, y: bottomY + 26, width: width, height: 3), CassetteDeckTheme.tape)
            fill(context, CGRect(x: x, y: bottomY + 26, width: width, height: 0.8), CassetteDeckTheme.tapeLight.withAlphaComponent(0.8))
        }
        let screws = [(cassette.x + 12, cassette.y + 12), (cassette.x + cassette.width - 12, cassette.y + 12), (cassette.x + 12, cassette.y + cassette.height - 12), (cassette.x + cassette.width - 12, cassette.y + cassette.height - 12), (cassette.x + cassette.width / 2, cassette.y + cassette.height - 10)]
        for (x, y) in screws { screw(context, x: x, y: y, radius: 3.4, angle: hash(Double(x + y)) * 2) }
        let label = CassetteDeckTheme.labelRect
        linear(context, path: rounded(label.cgRect, radius: label.radius), bounds: label.cgRect, colors: [UIColor(hexValue: 0xF6EEDC), CassetteDeckTheme.label, UIColor(hexValue: 0xE9DEC5)], locations: [0, 0.5, 1], horizontal: true)
        for index in 0..<3 { fill(context, CGRect(x: label.x + 44, y: label.y + 18 + CGFloat(index) * 12, width: label.width - 58, height: 0.6), CassetteDeckTheme.labelLine) }
        fill(context, CGRect(x: label.x, y: label.y + label.height - 22, width: label.width, height: 22), CassetteDeckTheme.labelBand)
        fill(context, CGRect(x: label.x, y: label.y + label.height - 22, width: label.width, height: 1), .white.withAlphaComponent(0.35))
        strokeEllipse(context, center: CGPoint(x: label.x + 22, y: label.y + 30), radius: 13, color: CassetteDeckTheme.pen.withAlphaComponent(0.85), width: 1.4)
        text(context, CassetteDeckTheme.side, x: label.x + 22, baseline: label.y + 39, font: fonts.handwriting, size: 27, color: CassetteDeckTheme.pen.withAlphaComponent(0.9), align: .center)
        context.saveGState()
        let pivot = CGPoint(x: label.x + 50, y: label.y + 40)
        context.translateBy(x: pivot.x, y: pivot.y)
        context.rotate(by: -.pi * 1.6 / 180)
        context.translateBy(x: -pivot.x, y: -pivot.y)
        text(context, CassetteDeckTheme.title, x: label.x + 46, baseline: label.y + 34, font: fonts.handwriting, size: 27, color: CassetteDeckTheme.pen.withAlphaComponent(0.92))
        text(context, CassetteDeckTheme.subtitle, x: label.x + 52, baseline: label.y + 54, font: fonts.handwriting, size: 18, color: CassetteDeckTheme.pen.withAlphaComponent(0.8))
        context.restoreGState()
        let window = CassetteDeckTheme.window
        fill(context, rounded(window.cgRect.insetBy(dx: -3, dy: -3), radius: window.radius + 3), UIColor(hexValue: 0x1A1B1D))
        fill(context, rounded(window.cgRect, radius: window.radius), UIColor(hexValue: 0x0D0D0E))
    }

    private static func drawReadout(_ context: CGContext, fonts: CassetteDeckFonts) {
        let y = CassetteDeckTheme.readoutY, height = CassetteDeckTheme.readoutHeight
        engraved(context, CassetteDeckTheme.counterLabel, x: 16, baseline: y - 10, font: fonts.medium, size: 9, tracking: 1.4, color: CassetteDeckTheme.engraveMuted)
        linear(context, path: rounded(CGRect(x: 14.5, y: y - 1.5, width: 103, height: height + 3), radius: 9), bounds: CGRect(x: 14.5, y: y - 1.5, width: 103, height: height + 3), colors: [UIColor(hexValue: 0x80858B), UIColor(hexValue: 0xF2F3F4)])
        fill(context, rounded(CGRect(x: 16, y: y, width: 100, height: height), radius: 8), CassetteDeckTheme.counter)
        engraved(context, CassetteDeckTheme.memo, x: 132, baseline: y + 52, font: fonts.medium, size: 9, tracking: 1.4, color: CassetteDeckTheme.engraveMuted)
        let marks = ["-20", "-10", "-5", "0", "+3"], segments = [0, 4, 7, 9, 11]
        for index in marks.indices {
            let x = CassetteDeckTheme.metersX + 22 + CGFloat(segments[index]) * ((CassetteDeckTheme.metersWidth - 22) / 12) + 6
            text(context, marks[index], x: x, baseline: y - 10, font: fonts.medium, size: 9, color: CassetteDeckTheme.engraveMuted, align: .center)
        }
        let meterFrame = CGRect(x: CassetteDeckTheme.metersX - 1.5, y: y - 1.5, width: CassetteDeckTheme.metersWidth + 3, height: height + 3)
        linear(context, path: rounded(meterFrame, radius: 9), bounds: meterFrame, colors: [UIColor(hexValue: 0x80858B), UIColor(hexValue: 0xF2F3F4)])
        fill(context, rounded(CGRect(x: CassetteDeckTheme.metersX, y: y, width: CassetteDeckTheme.metersWidth, height: height), radius: 8), CassetteDeckTheme.counter)
        text(context, "L", x: CassetteDeckTheme.metersX + 10, baseline: y + 26, font: fonts.medium, size: 9, color: UIColor(hexValue: 0x9AA0A6))
        text(context, "R", x: CassetteDeckTheme.metersX + 10, baseline: y + 50, font: fonts.medium, size: 9, color: UIColor(hexValue: 0x9AA0A6))
    }

    private static func drawKeySlots(_ context: CGContext, fonts: CassetteDeckFonts) {
        let y = CassetteDeckTheme.keysY, width = CassetteDeckTheme.stage.width - 2 * CassetteDeckTheme.edge
        fill(context, rounded(CGRect(x: 12, y: y - 4, width: width + 8, height: CassetteDeckTheme.keysHeight + 8), radius: 12), UIColor(hexValue: 0x16171A))
        stroke(context, rounded(CGRect(x: 10, y: y - 6, width: width + 12, height: CassetteDeckTheme.keysHeight + 12), radius: 14), .white.withAlphaComponent(0.5), width: 1)
        for (index, name) in ["REW", "PLAY", "FF", "STOP", "PAUSE"].enumerated() {
            engraved(context, name, x: CassetteDeckTheme.keyX(index) + CassetteDeckTheme.keyWidth / 2, baseline: y - 14, font: fonts.medium, size: 9, tracking: 1.4, align: .center, color: CassetteDeckTheme.engraveMuted)
        }
    }

    private static func drawGlass(_ context: CGContext) {
        let window = CassetteDeckTheme.window
        stroke(context, rounded(window.cgRect.insetBy(dx: 0.5, dy: 0.5), radius: window.radius), .white.withAlphaComponent(0.28), width: 1)
        for index in 0...10 {
            let x = window.x + 40 + CGFloat(index) * ((window.width - 80) / 10)
            line(context, from: CGPoint(x: x, y: window.y + 3), to: CGPoint(x: x, y: window.y + (index % 5 == 0 ? 9 : 6)), color: .white.withAlphaComponent(0.35), width: 0.7)
        }
        let door = CassetteDeckTheme.well
        context.saveGState(); context.addPath(rounded(door.cgRect, radius: door.radius)); context.clip()
        fill(context, door.cgRect, UIColor(hexValue: 0x2A2018, alpha: 0.16))
        reflection(context, startX: door.x + 150, width: 70, alpha: 0.07)
        reflection(context, startX: door.x + 250, width: 16, alpha: 0.08)
        fill(context, CGRect(x: door.x + 10, y: door.y + 1, width: door.width - 20, height: 1), .white.withAlphaComponent(0.35))
        context.restoreGState()
        stroke(context, rounded(door.cgRect.insetBy(dx: 0.5, dy: 0.5), radius: door.radius), .white.withAlphaComponent(0.16), width: 1)
    }

    private static func reflection(_ context: CGContext, startX: CGFloat, width: CGFloat, alpha: CGFloat) {
        let door = CassetteDeckTheme.well, path = CGMutablePath()
        path.move(to: CGPoint(x: startX, y: door.y - 10)); path.addLine(to: CGPoint(x: startX + width, y: door.y - 10))
        path.addLine(to: CGPoint(x: startX + width - 150, y: door.y + door.height + 10)); path.addLine(to: CGPoint(x: startX - 150, y: door.y + door.height + 10)); path.closeSubpath()
        context.saveGState()
        context.setShadow(offset: .zero, blur: 6, color: UIColor.white.withAlphaComponent(alpha).cgColor)
        fill(context, path, .white.withAlphaComponent(alpha * 0.25))
        context.restoreGState()
    }

    private func drawTape(_ context: CGContext, model: CassetteDeckModel) {
        context.saveGState(); context.addPath(Self.rounded(CassetteDeckTheme.window.cgRect, radius: CassetteDeckTheme.window.radius)); context.clip()
        let angles = CassetteDeckModel.reelAngles(model.tapePosition)
        drawReel(context, point: CassetteDeckTheme.reels[0], pack: CassetteDeckModel.packLeft(model.tapePosition), angle: angles.0)
        drawReel(context, point: CassetteDeckTheme.reels[1], pack: CassetteDeckModel.packRight(model.tapePosition), angle: angles.1)
        context.restoreGState()
    }

    private func drawReel(_ context: CGContext, point: CassetteDeckPoint, pack: Double, angle: Double) {
        Self.radial(context, center: CGPoint(x: point.x, y: point.y), radius: pack, colors: [CassetteDeckTheme.tape, CassetteDeckTheme.tape, UIColor(hexValue: 0x2A170E)], locations: [0, 0.85, 1])
        var ring = CassetteDeckTheme.hubRadius + 4
        while ring < pack - 1 { Self.strokeEllipse(context, center: CGPoint(x: point.x, y: point.y), radius: ring, color: CassetteDeckTheme.tapeLight.withAlphaComponent(0.18), width: 0.5); ring += 3.2 }
        Self.strokeEllipse(context, center: CGPoint(x: point.x, y: point.y), radius: pack - 0.6, color: CassetteDeckTheme.tapeLight.withAlphaComponent(0.5), width: 1)
        let hub = CassetteDeckTheme.hubRadius
        context.saveGState()
        context.addEllipse(in: CGRect(x: point.x - hub, y: point.y - hub, width: hub * 2, height: hub * 2))
        context.clip()
        Self.radial(context, center: CGPoint(x: point.x - 3, y: point.y - 4), radius: hub * 1.6, colors: [.white, CassetteDeckTheme.hub, UIColor(hexValue: 0xBDB8AE)], locations: [0, 0.5, 1])
        context.restoreGState()
        Self.fillEllipse(context, center: CGPoint(x: point.x, y: point.y), radius: hub * 0.55, color: UIColor(hexValue: 0x0D0D0E))
        for index in 0..<6 {
            let a = angle + Double(index) * .pi / 3, path = CGMutablePath()
            path.move(to: CGPoint(x: point.x + cos(a - 0.16) * hub * 0.55, y: point.y + sin(a - 0.16) * hub * 0.55))
            path.addLine(to: CGPoint(x: point.x + cos(a) * hub * 0.3, y: point.y + sin(a) * hub * 0.3))
            path.addLine(to: CGPoint(x: point.x + cos(a + 0.16) * hub * 0.55, y: point.y + sin(a + 0.16) * hub * 0.55)); path.closeSubpath()
            Self.fill(context, path, CassetteDeckTheme.hub)
        }
    }

    private func drawCounter(_ context: CGContext, model: CassetteDeckModel) {
        let y = CassetteDeckTheme.readoutY, height = CassetteDeckTheme.readoutHeight
        var value = CassetteDeckModel.counterAt(model.tapePosition).truncatingRemainder(dividingBy: 1000); if value < 0 { value += 1000 }
        context.saveGState(); context.addPath(Self.rounded(CGRect(x: 16, y: y, width: 100, height: height), radius: 8)); context.clip()
        for index in 0..<3 {
            let place = pow(10.0, Double(2 - index)), raw = value / place, digit = Int(floor(raw)) % 10, below = value.truncatingRemainder(dividingBy: place) / place
            let roll = index == 2 ? raw - floor(raw) : (below > 0.9 ? (below - 0.9) * 10 : 0)
            let centerX = 24 + CGFloat(index) * 28 + 12, centerY = y + height / 2 + 9
            Self.fill(context, Self.rounded(CGRect(x: centerX - 12, y: y + 8, width: 24, height: height - 16), radius: 3), UIColor(hexValue: 0x1B1C1E))
            context.saveGState(); context.clip(to: CGRect(x: centerX - 12, y: y + 8, width: 24, height: height - 16))
            Self.text(context, "\(digit)", x: centerX, baseline: centerY - CGFloat(roll) * 34, font: fonts.semibold, size: 26, color: CassetteDeckTheme.digit, align: .center)
            Self.text(context, "\((digit + 1) % 10)", x: centerX, baseline: centerY + CGFloat(1 - roll) * 34, font: fonts.semibold, size: 26, color: CassetteDeckTheme.digit, align: .center)
            context.restoreGState()
        }
        Self.linear(context, rect: CGRect(x: 16, y: y, width: 100, height: height), colors: [.black.withAlphaComponent(0.85), .clear, .clear, .black.withAlphaComponent(0.85)], locations: [0, 0.3, 0.7, 1])
        context.restoreGState()
        let lamp = CGPoint(x: 138, y: y + 30)
        let colors = model.memory ? [UIColor(hexValue: 0xFFE2B8), CassetteDeckTheme.led, UIColor(hexValue: 0xB4520E)] : [UIColor(hexValue: 0x5A4334), CassetteDeckTheme.ledOff, UIColor(hexValue: 0x21170F)]
        Self.radial(context, center: CGPoint(x: lamp.x - 1, y: lamp.y - 1), radius: 5, colors: colors, locations: [0, 0.5, 1])
        if model.memory { Self.radial(context, center: lamp, radius: 10, colors: [CassetteDeckTheme.led.withAlphaComponent(0.4), CassetteDeckTheme.led.withAlphaComponent(0)]) }
    }

    private func drawMeters(_ context: CGContext, model: CassetteDeckModel) {
        let x0 = CassetteDeckTheme.metersX + 22, segmentWidth = (CassetteDeckTheme.metersWidth - 32) / 12
        for row in 0..<2 {
            let level = row == 0 ? model.leftLevel : model.rightLevel, y = CassetteDeckTheme.readoutY + (row == 0 ? 16 : 40)
            for index in 0..<12 {
                let on = level * 12 > Double(index) + 0.3
                let color = index >= 11 ? CassetteDeckTheme.meterPeak : (index >= 9 ? CassetteDeckTheme.meterHot : CassetteDeckTheme.meterOn)
                let x = x0 + CGFloat(index) * segmentWidth
                if on { context.saveGState(); context.setShadow(offset: .zero, blur: 3, color: color.withAlphaComponent(0.35).cgColor); Self.fill(context, Self.rounded(CGRect(x: x - 2, y: y - 2, width: segmentWidth + 1, height: 16), radius: 4), color.withAlphaComponent(0.25)); context.restoreGState() }
                Self.fill(context, Self.rounded(CGRect(x: x, y: y, width: segmentWidth - 3, height: 12), radius: 2), on ? color : CassetteDeckTheme.meterOff)
            }
        }
    }

    private func drawKeys(_ context: CGContext, model: CassetteDeckModel) {
        for index in 0..<5 {
            let depth = model.keyDepth[index], x = CassetteDeckTheme.keyX(index), sink = 12 * depth, frontHeight = Double(CassetteDeckTheme.keyTop) * (1 - 0.75 * depth)
            let top = Double(CassetteDeckTheme.keysY) + sink, faceHeight = Double(CassetteDeckTheme.keysHeight - CassetteDeckTheme.keyTop - 4)
            context.saveGState(); context.setShadow(offset: CGSize(width: 2, height: 6), blur: 5, color: UIColor.black.withAlphaComponent(0.55 - 0.3 * depth).cgColor)
            Self.fill(context, Self.rounded(CGRect(x: x + 2, y: top + 6, width: CassetteDeckTheme.keyWidth - 4, height: faceHeight + frontHeight), radius: 8), .black); context.restoreGState()
            let front = CGRect(x: x, y: top + faceHeight - 8, width: CassetteDeckTheme.keyWidth, height: frontHeight + 8)
            Self.linear(context, path: Self.rounded(front, radius: 8), bounds: front, colors: [CassetteDeckTheme.keyFront, CassetteDeckTheme.keyDeep])
            let face = CGRect(x: x, y: top, width: CassetteDeckTheme.keyWidth, height: faceHeight)
            Self.linear(context, path: Self.rounded(face, radius: 8), bounds: face, colors: [CassetteDeckTheme.keyTopColor, UIColor(hexValue: 0xCDD1D5)])
            Self.fill(context, Self.rounded(face, radius: 8), .black.withAlphaComponent(0.12 * depth))
            for lineIndex in 0..<14 { let lineY = CGFloat(top + 6 + Double(lineIndex) * ((faceHeight - 12) / 14)); Self.fill(context, CGRect(x: x + 6, y: lineY, width: CassetteDeckTheme.keyWidth - 12, height: 0.4), .white.withAlphaComponent(0.25)) }
            Self.stroke(context, Self.rounded(face.insetBy(dx: 0.5, dy: 0.5), radius: 8), .white.withAlphaComponent(0.8), width: 1)
            drawKeySymbol(context, index: index, center: CGPoint(x: x + CassetteDeckTheme.keyWidth / 2, y: top + faceHeight / 2))
        }
    }

    private func drawKeySymbol(_ context: CGContext, index: Int, center: CGPoint) {
        let color = index == 1 ? CassetteDeckTheme.playMark : CassetteDeckTheme.engrave
        switch index {
        case 1: triangle(context, x: center.x - 5, y: center.y, direction: 1, color: color)
        case 2: triangle(context, x: center.x - 10, y: center.y, direction: 1, color: color); triangle(context, x: center.x, y: center.y, direction: 1, color: color)
        case 0: triangle(context, x: center.x + 10, y: center.y, direction: -1, color: color); triangle(context, x: center.x, y: center.y, direction: -1, color: color)
        case 3: Self.fill(context, CGRect(x: center.x - 6.5, y: center.y - 6.5, width: 13, height: 13), color)
        default: Self.fill(context, CGRect(x: center.x - 7, y: center.y - 7, width: 4.5, height: 14), color); Self.fill(context, CGRect(x: center.x + 2.5, y: center.y - 7, width: 4.5, height: 14), color)
        }
    }

    private func triangle(_ context: CGContext, x: CGFloat, y: CGFloat, direction: CGFloat, color: UIColor) {
        let path = CGMutablePath(); path.move(to: CGPoint(x: x, y: y - 7)); path.addLine(to: CGPoint(x: x + 10 * direction, y: y)); path.addLine(to: CGPoint(x: x, y: y + 7)); path.closeSubpath(); Self.fill(context, path, color)
    }

    private func drawHint(_ context: CGContext, model: CassetteDeckModel) {
        let hint: String
        switch model.mode {
        case .play: hint = CassetteDeckTheme.hints[1]
        case .fastForward: hint = CassetteDeckTheme.hints[2]
        case .rewind: hint = model.memory ? CassetteDeckTheme.hints[3] : "Rewinding"
        case .pause: hint = CassetteDeckTheme.hints[4]
        case .stop: hint = model.tapePosition > CassetteDeckModel.tapeStart + 0.001 ? "Stopped" : CassetteDeckTheme.hints[0]
        }
        Self.text(context, hint, x: CassetteDeckTheme.stage.width / 2, baseline: CassetteDeckTheme.hintBaseline, font: fonts.medium, size: 14, color: CassetteDeckTheme.engrave.withAlphaComponent(0.9), align: .center)
    }

    private static func screw(_ context: CGContext, x: CGFloat, y: CGFloat, radius: CGFloat, angle: Double) {
        radial(context, center: CGPoint(x: x - radius * 0.3, y: y - radius * 0.3), radius: radius * 1.6, colors: [UIColor(hexValue: 0x8C9096), UIColor(hexValue: 0x54585E), UIColor(hexValue: 0x2E3135)], locations: [0, 0.6, 1])
        let c = CGFloat(cos(angle)) * radius * 0.7, s = CGFloat(sin(angle)) * radius * 0.7
        line(context, from: CGPoint(x: x - c, y: y - s), to: CGPoint(x: x + c, y: y + s), color: UIColor(hexValue: 0x1B1D20), width: 1.1)
        line(context, from: CGPoint(x: x - s, y: y + c), to: CGPoint(x: x + s, y: y - c), color: UIColor(hexValue: 0x1B1D20), width: 1.1)
    }

    private static func text(_ context: CGContext, _ value: String, x: CGFloat, baseline: CGFloat, font: UIFont, size: CGFloat, color: UIColor, align: TextAlign = .left) {
        guard !value.isEmpty, color.cgColor.alpha > 0.004 else { return }
        let sized = font.withSize(size), attrs: [NSAttributedString.Key: Any] = [.font: sized, .foregroundColor: color]
        let width = (value as NSString).size(withAttributes: attrs).width
        let left = align == .left ? x : (align == .center ? x - width / 2 : x - width)
        (value as NSString).draw(at: CGPoint(x: left, y: baseline - sized.ascender), withAttributes: attrs)
    }

    private static func engraved(_ context: CGContext, _ value: String, x: CGFloat, baseline: CGFloat, font: UIFont, size: CGFloat, tracking: CGFloat, align: TextAlign = .left, color: UIColor = CassetteDeckTheme.engrave) {
        let sized = font.withSize(size)
        let widths = value.map { (String($0) as NSString).size(withAttributes: [.font: sized]).width }
        let total = widths.reduce(0, +) + CGFloat(max(0, value.count - 1)) * tracking
        var cursor = align == .left ? x : (align == .center ? x - total / 2 : x - total)
        for (index, character) in value.enumerated() {
            let glyph = String(character); text(context, glyph, x: cursor, baseline: baseline + 0.7, font: font, size: size, color: .white.withAlphaComponent(0.7)); text(context, glyph, x: cursor, baseline: baseline, font: font, size: size, color: color)
            cursor += widths[index] + (index < value.count - 1 ? tracking : 0)
        }
    }

    private static func rounded(_ rect: CGRect, radius: CGFloat) -> CGPath { UIBezierPath(roundedRect: rect, cornerRadius: radius).cgPath }
    private static func fill(_ context: CGContext, _ rect: CGRect, _ color: UIColor) { context.setFillColor(color.cgColor); context.fill(rect) }
    private static func fill(_ context: CGContext, _ path: CGPath, _ color: UIColor) { context.addPath(path); context.setFillColor(color.cgColor); context.fillPath() }
    private static func stroke(_ context: CGContext, _ path: CGPath, _ color: UIColor, width: CGFloat) { context.addPath(path); context.setStrokeColor(color.cgColor); context.setLineWidth(width); context.setLineCap(.round); context.strokePath() }
    private static func line(_ context: CGContext, from: CGPoint, to: CGPoint, color: UIColor, width: CGFloat) { context.beginPath(); context.move(to: from); context.addLine(to: to); context.setStrokeColor(color.cgColor); context.setLineWidth(width); context.setLineCap(.round); context.strokePath() }
    private static func fillEllipse(_ context: CGContext, center: CGPoint, radius: CGFloat, color: UIColor) { context.setFillColor(color.cgColor); context.fillEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)) }
    private static func strokeEllipse(_ context: CGContext, center: CGPoint, radius: CGFloat, color: UIColor, width: CGFloat) { context.setStrokeColor(color.cgColor); context.setLineWidth(width); context.strokeEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)) }

    private static func linear(_ context: CGContext, rect: CGRect, colors: [UIColor], locations: [CGFloat]? = nil, horizontal: Bool = false) {
        context.saveGState(); context.clip(to: rect); drawLinear(context, bounds: rect, colors: colors, locations: locations, horizontal: horizontal); context.restoreGState()
    }
    private static func linear(_ context: CGContext, path: CGPath, bounds: CGRect, colors: [UIColor], locations: [CGFloat]? = nil, horizontal: Bool = false) {
        context.saveGState(); context.addPath(path); context.clip(); drawLinear(context, bounds: bounds, colors: colors, locations: locations, horizontal: horizontal); context.restoreGState()
    }
    private static func drawLinear(_ context: CGContext, bounds: CGRect, colors: [UIColor], locations: [CGFloat]?, horizontal: Bool) {
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: locations) else { return }
        context.drawLinearGradient(gradient, start: horizontal ? CGPoint(x: bounds.minX, y: bounds.midY) : CGPoint(x: bounds.midX, y: bounds.minY), end: horizontal ? CGPoint(x: bounds.maxX, y: bounds.midY) : CGPoint(x: bounds.midX, y: bounds.maxY), options: [])
    }
    private static func radial(_ context: CGContext, center: CGPoint, radius: CGFloat, colors: [UIColor], locations: [CGFloat]? = nil) {
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: locations) else { return }
        context.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius, options: [])
    }
    private static func hash(_ value: Double) -> CGFloat { let raw = sin(value * 127.1 + 311.7) * 43758.5453; return CGFloat(raw - floor(raw)) }
}

private extension UIColor {
    convenience init(hexValue: Int, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hexValue >> 16) & 0xFF) / 255, green: CGFloat((hexValue >> 8) & 0xFF) / 255, blue: CGFloat(hexValue & 0xFF) / 255, alpha: alpha)
    }
}
