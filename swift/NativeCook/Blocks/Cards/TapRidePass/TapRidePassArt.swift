import QuartzCore
import SwiftUI

/// Ordered drawing layers for the night station, reader, acrylic card and printed ticket.
/// Geometry uses points; card rotations are radians. Static text and artwork come from the resource cache.
struct TapRidePassArt {
    typealias Resources = TapRidePassResources
    typealias Motion = TapRidePassMotion
    let layout: TapRidePassGeometry
    let driver: TapRidePassDriver
    let drawing: TapRidePassDrawing
    let reduceMotion: Bool
    let resources = Resources.shared
    func box(_ x: Double, _ y: Double, _ width: Double, _ height: Double) -> CGRect {
        .init(x: x, y: y, width: width, height: height)
    }
    func oval(_ pulseScale: Double) -> Path {
        pulseScale == 1
            ? resources.readerPath
            : pulseScale == 0.97
                ? resources.readerInsetPath
                : Path(
                    ellipseIn: box(
                        layout.readerX - layout.readerRadiusX * pulseScale,
                        layout.readerEllipseY - layout.readerRadiusY * pulseScale,
                        layout.readerRadiusX * 2 * pulseScale, layout.readerRadiusY * 2 * pulseScale))
    }
    func drawImage(_ context: GraphicsContext, _ image: UIImage, _ rect: CGRect) {
        context.draw(Image(uiImage: image), in: rect)
    }
    /// Gradient stops are (sRGB hex, opacity, normalized location).
    func gradient(_ stops: [(Int, Double, Double)]) -> Gradient {
        Gradient(stops: stops.map { .init(color: Resources.color($0.0, $0.1), location: $0.2) })
    }
    func radial(_ stops: [(Int, Double, Double)], _ x: Double, _ y: Double, _ radius: Double)
        -> GraphicsContext.Shading
    {
        .radialGradient(gradient(stops), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: radius)
    }
    /// Draws the station parallax, validator, breathing ring and read-confirmation light in that order.
    func background(_ context: GraphicsContext) {
        context.fill(resources.backgroundPath, with: .color(Resources.color(0x070708)))
        let parallaxX = reduceMotion ? 0 : (drawing.cardX - layout.width / 2) * (-0.04)
        let parallaxY = reduceMotion ? 0 : (drawing.cardY - layout.homeY) * (-0.015)
        // Skia fit=cover crops centrally inside the oversized backdrop rectangle.
        var backdropContext = context
        backdropContext.clip(
            to: Path(
                box(
                    -layout.width * 0.04 + parallaxX, -layout.height * 0.02 + parallaxY, layout.width * 1.08,
                    layout.height * 1.04)))
        let backdropScale = max(layout.width * 1.08 / 1206, layout.height * 1.04 / 2622)
        let imageWidth = 1206 * backdropScale
        let imageHeight = 2622 * backdropScale
        drawImage(
            backdropContext, resources.backdrop,
            box(
                layout.width / 2 - imageWidth / 2 + parallaxX, layout.height / 2 - imageHeight / 2 + parallaxY,
                imageWidth, imageHeight))
        context.fill(
            resources.shadePath,
            with: .linearGradient(
                gradient([(0x060608, 0.92, 0), (0x060608, 0.55, 0.55), (0x060608, 0, 1)]), startPoint: .zero,
                endPoint: CGPoint(x: 0, y: layout.safeTop + 170)))
        var light = context
        light.blendMode = .plusLighter
        light.opacity = drawing.flash * 0.1
        light.fill(resources.backgroundPath, with: .color(Resources.color(0xFFD9A8)))
        drawImage(
            context, resources.validator,
            box(layout.validatorX, layout.validatorY, layout.validatorWidth, layout.validatorHeight))
        context.fill(
            oval(0.97),
            with: radial(
                [(0x16161A, 0.35, 0), (0x08080A, 0.55, 1)], layout.readerX,
                layout.readerEllipseY - layout.readerRadiusY * 0.3, layout.readerRadiusX))
        context.stroke(
            oval(0.97),
            with: .linearGradient(
                gradient([(0x000000, 0.6, 0), (0xffffff, 0.12, 1)]),
                startPoint: CGPoint(x: 0, y: layout.readerEllipseY - layout.readerRadiusY),
                endPoint: CGPoint(x: 0, y: layout.readerEllipseY + layout.readerRadiusY)), lineWidth: 1)
        let amberRGB = [255.0, 178, 94]
        let successRGB = [98.0, 227, 166]
        let ring = Color(
            .sRGB, red: (amberRGB[0] + (successRGB[0] - amberRGB[0]) * drawing.ringSuccess) / 255,
            green: (amberRGB[1] + (successRGB[1] - amberRGB[1]) * drawing.ringSuccess) / 255,
            blue: (amberRGB[2] + (successRGB[2] - amberRGB[2]) * drawing.ringSuccess) / 255,
            opacity: drawing.ringAlpha)
        var glow = context
        glow.addFilter(.blur(radius: 7))
        glow.stroke(oval(1), with: .color(ring), lineWidth: 6)
        context.stroke(oval(1), with: .color(ring), lineWidth: 1.4)
        context.stroke(
            oval(drawing.rippleScale), with: .color(Resources.color(0xFFB25E, drawing.rippleAlpha)), lineWidth: 1.2
        )
        if drawing.flash > 0 {
            var flash = context
            flash.blendMode = .plusLighter
            flash.opacity = drawing.flash
            flash.translateBy(x: layout.readerX, y: layout.readerEllipseY)
            flash.scaleBy(x: 1, y: layout.readerRadiusY / layout.readerRadiusX)
            let flashRadius = layout.readerRadiusX * (1.1 + 0.9 * drawing.flash)
            flash.fill(
                Path(ellipseIn: box(-flashRadius, -flashRadius, flashRadius * 2, flashRadius * 2)),
                with: radial([(0xFFF4DC, 1, 0), (0xFFB25E, 0.55, 0.4), (0xFFB25E, 0, 1)], 0, 0, flashRadius))
        }
        if drawing.shockAlpha > 0 {
            var shock = context
            shock.addFilter(.blur(radius: 2))
            shock.stroke(
                oval(drawing.shockScale), with: .color(Resources.color(0xFFE2B8, drawing.shockAlpha)), lineWidth: 2
            )
        }
    }
    /// Adds the powered antenna glow to the reader beneath the projected card.
    func spill(_ context: GraphicsContext) {
        var spillContext = context
        spillContext.blendMode = .plusLighter
        spillContext.opacity = drawing.coil * Motion.smooth(0.5, 1, drawing.dockProgress) * 0.55
        spillContext.fill(
            oval(1.45),
            with: radial(
                [(0xFFAA50, 0.9, 0), (0xFF8C32, 0.25, 0.5), (0xFF8C32, 0, 1)], layout.readerX,
                layout.readerEllipseY, layout.readerRadiusX * 1.45))
    }
    /// Draws the translucent card body before its additive antenna and printed face.
    func card(_ context: GraphicsContext) {
        context.fill(resources.cardSmokePath, with: .color(Resources.color(0x0C0D12, 0.42)))
        drawImage(context, resources.card, box(0, 0, layout.cardWidth, layout.cardHeight))
    }
    /// Overlays the cached card lettering after the two powered antenna layers.
    func cardPrinting(_ context: GraphicsContext) {
        drawImage(context, resources.cardFace, box(0, 0, layout.cardWidth, layout.cardHeight))
    }
    /// Crossfades headers, clips ticket text at the slot, rolls the balance and pulses the open-gate dot.
    func labels(_ context: GraphicsContext) {
        drawImage(context, resources.eyebrow, box(24, layout.safeTop + 14, layout.width - 48, 20))
        let restFade = Motion.smooth(0.4, 0.7, driver.ticketProgress)
        let successFade = Motion.smooth(0.62, 0.92, driver.ticketProgress)
        var restHeaderContext = context
        restHeaderContext.opacity = 1 - restFade
        drawImage(
            restHeaderContext, resources.headerA,
            box(24, layout.safeTop + 34 - 6 * restFade, layout.width - 48, 90))
        var successHeaderContext = context
        successHeaderContext.opacity = successFade
        drawImage(
            successHeaderContext, resources.headerB,
            box(24, layout.safeTop + 34 + 6 * (1 - successFade), layout.width - 48, 90))
        if driver.ticketProgress > 0.0005 {
            var face = context
            if drawing.inSlot {
                if reduceMotion { return }
                face.clip(to: Path(box(0, 0, layout.width, layout.slotY)))
            }
            face.translateBy(
                x: drawing.paperX - drawing.paperWidth / 2, y: drawing.paperY - drawing.paperHeight / 2)
            face.scaleBy(x: drawing.paperWidth / layout.ticketWidth, y: drawing.paperWidth / layout.ticketWidth)
            if reduceMotion { face.opacity = Motion.smooth(0.5, 1, driver.ticketProgress) }
            drawImage(face, resources.ticketFace, box(0, 0, layout.ticketWidth, layout.ticketHeight))
            var balance = face
            balance.clip(
                to: Path(
                    box(
                        layout.ticketWidth / 2 + 16 + Resources.pixel, resources.ticketRollY,
                        layout.ticketWidth / 2 - 38, 30)))
            var old = balance
            old.opacity *= 1 - driver.roll
            drawImage(
                old, resources.oldBalance,
                box(
                    layout.ticketWidth / 2 + 16 + Resources.pixel, resources.ticketRollY - 16 * driver.roll,
                    resources.oldBalance.size.width, 30))
            var new = balance
            new.opacity *= driver.roll
            drawImage(
                new, resources.newBalance,
                box(
                    layout.ticketWidth / 2 + 16 + Resources.pixel, resources.ticketRollY + 16 * (1 - driver.roll),
                    resources.newBalance.size.width, 30))
            if !reduceMotion {
                let pulseProgress = driver.breathingTime.truncatingRemainder(dividingBy: 1.5) / 1.5
                let pulseScale = 1 + 1.6 * Motion.easeOut(pulseProgress)
                let gateHeight =
                    ceil(resources.font("SemiBold", 18).lineHeight / Resources.pixel) * Resources.pixel
                let gateCenterY = layout.ticketHeight * 0.74 + 20 + gateHeight / 2
                face.fill(
                    Path(
                        ellipseIn: box(
                            26.5 - 4.5 * pulseScale, gateCenterY - 4.5 * pulseScale, 9 * pulseScale, 9 * pulseScale
                        )), with: .color(Resources.color(0x1FAF6A, 0.5 * (1 - pulseProgress))))
            }
        }
    }
    /// Builds the centred card transform. Scale is unitless; perspective distance is 1100 points.
    func projection(_ scale: Double) -> ProjectionTransform {
        // Column-vector order is translate × perspective × rotateX × rotateZ × scale.
        // CATransform3D uses row vectors: concatenate in reverse order around the card centre.
        var transform = CATransform3DMakeTranslation(-layout.cardWidth / 2, -layout.cardHeight / 2, 0)
        transform = CATransform3DConcat(transform, CATransform3DMakeScale(scale, scale, scale))
        transform = CATransform3DConcat(transform, CATransform3DMakeRotation(drawing.rotationZ, 0, 0, 1))
        transform = CATransform3DConcat(transform, CATransform3DMakeRotation(drawing.rotationX, 1, 0, 0))
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / 1100.0
        transform = CATransform3DConcat(transform, perspective)
        transform = CATransform3DConcat(
            transform, CATransform3DMakeTranslation(layout.cardWidth / 2, layout.cardHeight / 2, 0))
        return ProjectionTransform(transform)
    }
}

/// Thermal-paper material, cutouts and perforation. Frame dimensions are points.
/// While printing, the parent masks it at the slot; once raised, it receives the soft shadow.
struct TapRidePassPaper: View {
    let layout: TapRidePassGeometry
    let drawing: TapRidePassDrawing
    var shadow = false
    var body: some View {
        let paths = TapRidePassResources.shared.paperPaths(
            drawing.paperWidth, drawing.paperHeight, layout.ticketWidth)
        ZStack(alignment: .topLeading) {
            if shadow {
                RoundedRectangle(cornerRadius: 18 * drawing.paperWidth / layout.ticketWidth).fill(
                    .black.opacity(0.55 * drawing.paperExpansion)
                ).frame(width: drawing.paperWidth - 12, height: drawing.paperHeight).offset(
                    x: 6, y: 18 * drawing.paperExpansion
                ).blur(radius: 22)
            }
            Rectangle().fill(.white).colorEffect(
                ShaderLibrary.tapRidePassPaper(.float2(Float(drawing.paperWidth), Float(drawing.paperHeight)))
            )
            .mask {
                paths.1.fill(style: FillStyle(eoFill: true)).clipShape(
                    RoundedRectangle(cornerRadius: 18 * drawing.paperWidth / layout.ticketWidth))
            }
            Canvas { context, _ in
                context.clip(to: paths.0)
                context.fill(
                    Path(
                        CGRect(
                            x: 0, y: 0, width: drawing.paperWidth,
                            height: 6 * drawing.paperWidth / layout.ticketWidth)),
                    with: .color(TapRidePassResources.color(0xE8452C)))
                context.stroke(
                    paths.2, with: .color(TapRidePassResources.color(0xC5BCAB)),
                    style: StrokeStyle(lineWidth: shadow ? 1.3 : 1, dash: shadow ? [5, 5] : [4, 4]))
            }
        }.frame(width: drawing.paperWidth, height: drawing.paperHeight, alignment: .topLeading).position(
            x: drawing.paperX, y: drawing.paperY)
    }
}
