import QuartzCore
import SwiftUI

/// Layout in points, with the 1206 × 2622 wall image as the source coordinate system.
/// The wall scale converts its pixels to points; the header and sheet use safe-area insets.
struct ParcelLockerPickupGeometry {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
    var wallScale: Double {
        width / 1206
    }
    var wallOriginY: Double {
        top + 118 - 329 * wallScale
    }
    var wall: CGRect {
        CGRect(x: 0, y: wallOriginY, width: width, height: 2622 * wallScale)
    }
    var doorX: Double {
        435 * wallScale
    }
    var doorY: Double {
        wallOriginY + 1057 * wallScale
    }
    var doorWidth: Double {
        336 * wallScale
    }
    var doorHeight: Double {
        335 * wallScale
    }
    var door: CGRect {
        CGRect(x: doorX, y: doorY, width: doorWidth, height: doorHeight)
    }
    var doorCenterY: Double {
        doorY + doorHeight / 2
    }
    var doorCenterX: Double {
        doorX + doorWidth / 2
    }
    var sheetBottom: Double {
        height - bottom - 14
    }
    var parcelWidth: Double {
        doorWidth * 0.8
    }
    var parcelHeight: Double {
        parcelWidth * 523 / 640
    }
    var parcelCenterY: Double {
        doorY + doorHeight - parcelHeight / 2 - doorHeight * 0.07
    }
    var thumbnailCenterY: Double {
        sheetBottom - 300 + 121
    }

    /// Sheet bounds for pickup-to-receipt progress: 0 is 248 pt high, 1 is 300 pt high.
    func sheet(_ sheetProgress: Double) -> CGRect {
        CGRect(x: 16, y: sheetBottom - 248 - 52 * sheetProgress, width: width - 32, height: 248 + 52 * sheetProgress)
    }

    /// Projects a door point from its hinge; angle is degrees and distances/thickness are points.
    /// The 1000 pt focal distance is shared by the door face, edge and LED.
    func project(_ angleDegrees: Double, _ hingeDistance: Double, _ verticalPosition: Double, _ thickness: Double = 0)
        -> CGPoint
    {
        let angleRadians = angleDegrees * Double.pi / 180
        let x = doorX + hingeDistance * cos(angleRadians) + thickness * sin(angleRadians)
        let depth = hingeDistance * sin(angleRadians) - thickness * cos(angleRadians)
        let perspectiveScale = 1000 / (1000 - depth)
        return CGPoint(
            x: doorX + (x - doorX) * perspectiveScale,
            y: doorCenterY + (verticalPosition - doorCenterY) * perspectiveScale)
    }

    /// Maps the entire Canvas door face through the same hinge perspective as project.
    func projection(_ angleDegrees: Double) -> ProjectionTransform {
        let angleRadians = angleDegrees * Double.pi / 180
        let perspectiveFactor = sin(angleRadians) / 1000
        let cosine = cos(angleRadians)
        var transform = CATransform3DIdentity
        transform.m11 = cosine - doorX * perspectiveFactor
        transform.m12 = -doorCenterY * perspectiveFactor
        transform.m14 = -perspectiveFactor
        transform.m41 = doorX * (1 - cosine) + doorX * doorX * perspectiveFactor
        transform.m42 = doorCenterY * doorX * perspectiveFactor
        transform.m44 = 1 + doorX * perspectiveFactor
        return ProjectionTransform(transform)
    }
}

/// Ordered Canvas drawing passes for the locker wall, lighting, parcel and sheet content.
/// Each copied graphics context keeps its clipping, opacity and blend mode local to that pass.
struct ParcelLockerPickupArt {
    typealias Motion = ParcelLockerPickupMotion
    let geometry: ParcelLockerPickupGeometry
    var resources = ParcelLockerPickupResources.shared

    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        ParcelLockerPickupResources.color(hex, alpha)
    }

    func fill(_ context: inout GraphicsContext, _ rect: CGRect, _ color: Color, _ radius: Double = 0) {
        context.fill(Path(roundedRect: rect, cornerRadius: radius), with: .color(color))
    }

    func image(_ context: inout GraphicsContext, _ image: UIImage, _ rect: CGRect) {
        context.draw(Image(uiImage: image), in: rect)
    }

    func gradient(_ context: inout GraphicsContext, _ rect: CGRect, _ colors: [Color], _ start: CGPoint, _ end: CGPoint)
    {
        context.fill(Path(rect), with: .linearGradient(Gradient(colors: colors), startPoint: start, endPoint: end))
    }

    /// Draws a measured label bitmap; its 4 pt padding preserves glyph overhangs.
    func label(_ context: inout GraphicsContext, _ key: String, _ x: Double, _ y: Double, _ centered: Bool = false) {
        guard let label = resources.labels[key] else {
            return
        }
        image(
            &context, label.image,
            CGRect(
                x: x - 4 - (centered ? label.size.width / 2 : 0), y: y - 4, width: label.size.width + 8,
                height: label.size.height + 8))
    }

    /// Tints a cached rounded-rectangle mask. Radius and Gaussian sigma are in points.
    func blurredShape(
        _ context: inout GraphicsContext, _ rect: CGRect, _ radius: Double, _ sigma: Double, _ color: Color
    ) {
        let padding = ceil(sigma * 4)
        let image = resources.mask(rect.width, rect.height, radius, sigma)
        var resolved = context.resolve(Image(uiImage: image))
        resolved.shading = .color(color)
        context.draw(resolved, in: rect.insetBy(dx: -padding, dy: -padding))
    }

    /// Wall and compartment first, then the seated parcel, tube light, spill and cast shadow.
    func base(_ context: inout GraphicsContext, _ pose: Motion.Pose) {
        fill(&context, CGRect(x: 0, y: 0, width: geometry.width, height: geometry.height), Self.color(0x141312))
        image(&context, resources.wall, geometry.wall)
        var inside = context
        inside.clip(to: Path(geometry.door))
        image(&inside, resources.compartment, geometry.door)
        let darkness = 1 - 0.82 * pose.light
        fill(&inside, geometry.door, Self.color(0x05060A, darkness))
        if pose.parcel < 0.02 {
            var shadow = inside
            shadow.opacity = 0.5 * (1 - Motion.smooth(0, 0.25, pose.parcel))
            blurredShape(
                &shadow,
                CGRect(
                    x: geometry.doorCenterX - geometry.parcelWidth * 0.46,
                    y: geometry.parcelCenterY + geometry.parcelHeight * 0.36, width: geometry.parcelWidth * 0.92,
                    height: geometry.parcelHeight * 0.2), geometry.parcelHeight * 0.1, 6, .black)
            let rect = CGRect(
                x: geometry.doorCenterX - geometry.parcelWidth / 2,
                y: geometry.parcelCenterY - geometry.parcelHeight / 2, width: geometry.parcelWidth,
                height: geometry.parcelHeight)
            image(&inside, resources.parcel, rect)
            fill(&inside, rect, Self.color(0x05060A, darkness))
        }
        var tube = inside
        tube.blendMode = .plusLighter
        tube.opacity = pose.light
        gradient(
            &tube,
            CGRect(x: geometry.doorX, y: geometry.doorY, width: geometry.doorWidth, height: geometry.doorHeight * 0.35),
            [Self.color(0xAAC8FF, 0.22), Self.color(0xAAC8FF, 0)], CGPoint(x: 0, y: geometry.doorY),
            CGPoint(x: 0, y: geometry.doorY + geometry.doorHeight * 0.35))
        let angleRadians = pose.angle * Double.pi / 180
        var spill = context
        spill.blendMode = .plusLighter
        spill.opacity = pose.light * min(1, sin(angleRadians) * 1.4) * 0.5
        blurredShape(
            &spill,
            CGRect(
                x: geometry.doorX - 26, y: geometry.doorY - 18, width: geometry.doorWidth + 52,
                height: geometry.doorHeight + 52), 20, 26, Self.color(0xB9CDFF, 0.55))
        context.stroke(Path(geometry.door), with: .color(Self.color(0, 0.55)), lineWidth: 2)
        var cast = context
        cast.opacity = max(0, sin(angleRadians)) * 0.45
        gradient(
            &cast,
            CGRect(
                x: geometry.doorX - geometry.doorWidth * 0.75, y: geometry.doorY - 4, width: geometry.doorWidth * 0.75,
                height: geometry.doorHeight + 8), [Self.color(0, 0.85), Self.color(0, 0)],
            CGPoint(x: geometry.doorX, y: 0), CGPoint(x: geometry.doorX - geometry.doorWidth * 0.75, y: 0))
    }

    /// The wall's own pixels form the front; past 90 degrees the inner face becomes visible.
    func door(_ context: inout GraphicsContext, _ pose: Motion.Pose) {
        let angleRadians = pose.angle * Double.pi / 180
        if angleRadians <= Double.pi / 2 {
            context.clip(to: Path(geometry.door))
            image(&context, resources.wall, geometry.wall)
            fill(&context, geometry.door, Self.color(0, min(0.4, 0.3 * sin(angleRadians))))
            context.blendMode = .plusLighter
            context.opacity = max(0, sin(angleRadians)) * 0.45
            gradient(
                &context, geometry.door, [Self.color(0xFFAA5A, 0.55), Self.color(0xFFAA5A, 0.05)],
                CGPoint(x: geometry.doorX, y: geometry.doorY),
                CGPoint(x: geometry.doorX + geometry.doorWidth, y: geometry.doorY + geometry.doorHeight))
        } else {
            gradient(
                &context, geometry.door, [Self.color(0x3A3E45), Self.color(0x24272C)],
                CGPoint(x: geometry.doorX, y: geometry.doorY),
                CGPoint(x: geometry.doorX + geometry.doorWidth, y: geometry.doorY + geometry.doorHeight))
            fill(
                &context,
                CGRect(
                    x: geometry.doorX + geometry.doorWidth * 0.08, y: geometry.doorY + geometry.doorHeight * 0.36,
                    width: geometry.doorWidth * 0.16, height: geometry.doorHeight * 0.28), Self.color(0x15171A))
            context.blendMode = .plusLighter
            fill(&context, geometry.door, Self.color(0xB9CDFF, 0.16 * pose.light))
        }
    }

    /// Connects the projected front and back corners with a 5 pt thick metal edge.
    func sceneEdge(_ context: inout GraphicsContext, _ pose: Motion.Pose) {
        let angleRadians = pose.angle * Double.pi / 180
        let corners = [
            geometry.project(pose.angle, geometry.doorWidth, geometry.doorY),
            geometry.project(pose.angle, geometry.doorWidth, geometry.doorY, 5),
            geometry.project(pose.angle, geometry.doorWidth, geometry.doorY + geometry.doorHeight, 5),
            geometry.project(pose.angle, geometry.doorWidth, geometry.doorY + geometry.doorHeight),
        ]
        var path = Path()
        path.addLines(corners)
        path.closeSubpath()
        var edge = context
        edge.opacity = min(1, sin(angleRadians) * 3)
        edge.fill(
            path,
            with: .linearGradient(
                Gradient(colors: [Self.color(0xA9AEB6), Self.color(0x5E636B)]),
                startPoint: CGPoint(x: 0, y: geometry.doorY),
                endPoint: CGPoint(x: 0, y: geometry.doorY + geometry.doorHeight)))
    }

    /// LED states: 0 amber with a 2 s pulse, 1 steady green, 2 off. Time is seconds.
    func ledAlpha(_ pose: Motion.Pose, _ time: Double) -> Double {
        pose.led == 2
            ? 0
            : pose.led == 1 ? 1 : 0.25 + 0.75 * pow(sin(Double.pi * (time.truncatingRemainder(dividingBy: 2) / 2)), 2)
    }

    /// Keeps the blurred LED halo separate so the scene can blend it additively.
    func sceneGlow(_ context: inout GraphicsContext, _ pose: Motion.Pose, _ time: Double) {
        let led = geometry.project(pose.angle, 306 * geometry.wallScale, geometry.doorY + 35 * geometry.wallScale)
        context.opacity = ledAlpha(pose, time) * 0.8
        blurredShape(
            &context, CGRect(x: led.x - 7, y: led.y - 7, width: 14, height: 14), 7, 6,
            Self.color(pose.led == 1 ? 0x5BE39A : 0xFFB25E))
    }

    /// Places the sharp LED above its halo, then darkens the header area for legibility.
    func sceneFront(_ context: inout GraphicsContext, _ pose: Motion.Pose, _ time: Double) {
        let led = geometry.project(pose.angle, 306 * geometry.wallScale, geometry.doorY + 35 * geometry.wallScale)
        let alpha = ledAlpha(pose, time)
        let color = Self.color(pose.led == 1 ? 0x5BE39A : 0xFFB25E)
        var bulb = context
        bulb.opacity = alpha
        fill(&bulb, CGRect(x: led.x - 2.4, y: led.y - 2.4, width: 4.8, height: 4.8), color, 2.4)
        context.fill(
            Path(CGRect(x: 0, y: 0, width: geometry.width, height: geometry.top + 190)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Self.color(0x0E0D0C, 0.98), location: 0),
                    .init(color: Self.color(0x0E0D0C, 0.82), location: 0.55),
                    .init(color: Self.color(0x0E0D0C, 0), location: 1),
                ]), startPoint: .zero, endPoint: CGPoint(x: 0, y: geometry.top + 190)))
    }

    /// Tints and outlines the sheet after the view has supplied its blurred scene backdrop.
    func panel(_ context: inout GraphicsContext, _ pose: Motion.Pose) {
        let sheet = Path(roundedRect: geometry.sheet(pose.sheet), cornerRadius: 28)
        context.fill(sheet, with: .color(Self.color(0x161413, 0.62)))
        context.stroke(
            sheet,
            with: .linearGradient(
                Gradient(colors: [Self.color(0xFFFFFF, 0.22), Self.color(0xFFFFFF, 0.05)]),
                startPoint: CGPoint(x: 0, y: geometry.sheetBottom - 300),
                endPoint: CGPoint(x: 0, y: geometry.sheetBottom)), lineWidth: 1)
    }

    /// Lifts the parcel, follows a quadratic path to the receipt, then paints text above it.
    func overlay(_ context: inout GraphicsContext, _ pose: Motion.Pose) {
        if pose.parcel >= 0.02 {
            let lift = Motion.smooth(0, 0.35, pose.parcel)
            let fly = Motion.smooth(0.3, 1, pose.parcel)
            let liftedY = geometry.parcelCenterY - 10 * lift
            let liftedScale = 1 + 0.32 * lift
            let controlX = (geometry.doorCenterX + 70) / 2 - 40
            let controlY = min(liftedY, geometry.thumbnailCenterY) - 30
            let x = pow(1 - fly, 2) * geometry.doorCenterX + 2 * (1 - fly) * fly * controlX + fly * fly * 70
            let y = pow(1 - fly, 2) * liftedY + 2 * (1 - fly) * fly * controlY + fly * fly * geometry.thumbnailCenterY
            let parcelScale = liftedScale + (64 * 0.92 / geometry.parcelWidth - liftedScale) * fly
            var parcel = context
            parcel.opacity = 1 - pose.fold
            parcel.translateBy(x: x, y: y)
            parcel.rotate(by: .radians(-0.12 * sin(Double.pi * fly)))
            parcel.scaleBy(x: parcelScale, y: parcelScale)
            image(
                &parcel, resources.parcel,
                CGRect(
                    x: -geometry.parcelWidth / 2, y: -geometry.parcelHeight / 2, width: geometry.parcelWidth,
                    height: geometry.parcelHeight))
        }
        header(&context, pose)
        content(&context, pose)
    }

    /// Crossfades the locker instructions into the collection confirmation as the sheet grows.
    func header(_ context: inout GraphicsContext, _ pose: Motion.Pose) {
        let y = geometry.top + 14
        label(&context, "eyebrow", 24, y)
        let headingY = y + (resources.labels["eyebrow"]?.size.height ?? 15) + 10
        var pickupContext = context
        pickupContext.opacity = 1 - Motion.smooth(0.3, 0.7, pose.sheet)
        label(&pickupContext, "titleA", 24, headingY)
        label(&pickupContext, "subA", 24, headingY + (resources.labels["titleA"]?.size.height ?? 40) + 4)
        var receiptContext = context
        receiptContext.opacity = Motion.smooth(0.5, 0.9, pose.sheet)
        label(&receiptContext, "titleB", 24, headingY)
        label(&receiptContext, "subB", 24, headingY + (resources.labels["titleB"]?.size.height ?? 40) + 4)
    }

    /// Clips both sheet faces together while their text, code cells and checkmark crossfade.
    /// All local offsets are points from the sheet's top edge.
    func content(_ context: inout GraphicsContext, _ pose: Motion.Pose) {
        let rect = geometry.sheet(pose.sheet)
        let x = 38.0
        let width = geometry.width - 76
        var clip = context
        clip.clip(to: Path(rect))
        let pickupFade = Motion.smooth(0, 0.45, pose.sheet)
        let receiptFade = Motion.smooth(0.45, 1, pose.sheet)
        var pickup = clip
        pickup.opacity = 1 - pickupFade
        pickup.drawLayer { layer in
            var pickupContext = layer
            pickupContext.translateBy(x: 0, y: rect.minY - 12 * pickupFade)
            label(&pickupContext, "brand", x, 20)
            label(&pickupContext, "weight", geometry.width - 38 - (resources.labels["weight"]?.size.width ?? 0), 20)
            let brandHeight = resources.labels["brand"]?.size.height ?? 14
            let titleY = 20 + brandHeight + 12
            let subY = titleY + (resources.labels["ready"]?.size.height ?? 28) + 2
            let codeY = subY + (resources.labels["order"]?.size.height ?? 19) + 16
            let buttonY = codeY + 50 + 18
            label(&pickupContext, "ready", x, titleY)
            label(&pickupContext, "order", x, subY)
            for (i, digit) in ["4", "8", "1", "7"].enumerated() {
                let digitLight = min(1, max(0, pose.digits - Double(i)))
                let digitX = x + Double(i) * 64
                let digitY = codeY - 8 * digitLight * (1 - digitLight)
                let digitRect = CGRect(x: digitX, y: digitY, width: 54, height: 50)
                fill(&pickupContext, digitRect, Self.color(0xFFB25E, 0.06 + 0.16 * digitLight), 14)
                pickupContext.stroke(
                    Path(roundedRect: digitRect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 13.5),
                    with: .color(Self.color(0xFFB25E, 0.18 + 0.62 * digitLight)), lineWidth: 1)
                label(
                    &pickupContext, digit, digitX + 27,
                    digitY + (50 - (resources.labels[digit]?.size.height ?? 32)) / 2, true)
            }
            var button = pickupContext
            let scale = 1 - 0.035 * pose.press
            // RN scales the wrapper that includes the CTA's 18 pt top margin.
            let center = CGPoint(x: geometry.width / 2, y: codeY + 50 + 35)
            button.translateBy(x: center.x, y: center.y)
            button.scaleBy(x: scale, y: scale)
            button.translateBy(x: -center.x, y: -center.y)
            fill(&button, CGRect(x: x, y: buttonY, width: width, height: 52), Self.color(0xFFB25E), 26)
            label(
                &button, "open", geometry.width / 2, buttonY + (52 - (resources.labels["open"]?.size.height ?? 22)) / 2,
                true)
        }
        var receipt = clip
        receipt.opacity = receiptFade
        receipt.drawLayer { layer in
            var receiptContext = layer
            receiptContext.translateBy(x: 0, y: rect.minY + 14 * (1 - receiptFade))
            let pickedHeight = resources.labels["picked"]?.size.height ?? 32
            let receiptRowHeight = max(28, pickedHeight)
            let checkProgress = Motion.smooth(0.55, 1, pose.sheet)
            let checkScale = 0.6 + 0.4 * checkProgress
            var check = receiptContext
            check.opacity *= checkProgress
            check.translateBy(x: x + 14, y: 22 + receiptRowHeight / 2)
            check.scaleBy(x: checkScale, y: checkScale)
            fill(&check, CGRect(x: -14, y: -14, width: 28, height: 28), Self.color(0x5BE39A), 14)
            var tick = Path()
            tick.move(to: CGPoint(x: -4.5, y: 0.4))
            tick.addLine(to: CGPoint(x: -1.4, y: 3.3))
            tick.addLine(to: CGPoint(x: 4.6, y: -3.1))
            check.stroke(
                tick, with: .color(Self.color(0x0E1A13)),
                style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            label(&receiptContext, "picked", x + 38, 22 + (receiptRowHeight - pickedHeight) / 2)
            let subtitleY = 22 + receiptRowHeight + 2
            let itemY = subtitleY + (resources.labels["closed"]?.size.height ?? 19) + 18
            label(&receiptContext, "closed", x, subtitleY)
            let thumb = CGRect(x: x, y: itemY, width: 64, height: 64)
            fill(&receiptContext, thumb, Self.color(0xFFFFFF, 0.06), 16)
            receiptContext.stroke(
                Path(roundedRect: thumb.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 15.5),
                with: .color(Self.color(0xFFFFFF, 0.1)), lineWidth: 1)
            let itemHeight = resources.labels["item"]?.size.height ?? 22
            let metadataHeight = resources.labels["meta"]?.size.height ?? 17
            label(&receiptContext, "item", x + 78, itemY + (64 - itemHeight - metadataHeight - 3) / 2)
            label(&receiptContext, "meta", x + 78, itemY + (64 - itemHeight - metadataHeight - 3) / 2 + itemHeight + 3)
            let doneY = itemY + 64 + 20
            let ghost = CGRect(x: x, y: doneY, width: width, height: 50)
            fill(&receiptContext, ghost, Self.color(0xFFFFFF, 0.1), 25)
            receiptContext.stroke(
                Path(roundedRect: ghost.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 24.5),
                with: .color(Self.color(0xFFFFFF, 0.16)), lineWidth: 1)
            label(
                &receiptContext, "done", geometry.width / 2,
                doneY + (50 - (resources.labels["done"]?.size.height ?? 21)) / 2, true)
        }
    }
}
