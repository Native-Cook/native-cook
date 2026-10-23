import CoreText
import SwiftUI
import UIKit

/// The live circuit sits above the standings, matching the transformed Skia group in the RN source.
struct RaceResultStandingsReplay: View {
    typealias Motion = RaceResultStandingsMotion

    let frame: Motion.Frame
    let trackPress: Double
    let size: CGSize
    let safeTop: Double
    let scale: Double
    let displayScale: Double

    private static let fonts: Void = {
        ["Medium", "SemiBold", "Bold", "ExtraBold"].forEach { weight in
            guard let url = Bundle.main.url(
                forResource: "RaceResultStandings-Montserrat-\(weight)",
                withExtension: "ttf"
            ) else { return }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static let trackSmall = image(named: "RaceResultStandings-track")
    private static let trackLarge = image(named: "RaceResultStandings-track-large")
    private static let points: [CGPoint] = stride(from: 0, to: RaceResultStandingsTrackLine.xy.count, by: 2).map {
        CGPoint(
            x: RaceResultStandingsTrackLine.xy[$0] / 3,
            y: RaceResultStandingsTrackLine.xy[$0 + 1] / 3
        )
    }

    var body: some View {
        let _ = Self.fonts
        let open = Motion.mapOpen(frame.replay)
        let mapScale = (1 + (Motion.mapScale - 1) * open) *
            (1 - (1 - Motion.mapPress) * trackPress)
        let trackSize = CGSize(
            width: RaceResultStandingsTheme.layout.track.w * scale,
            height: RaceResultStandingsTheme.layout.track.h * scale
        )
        let center = CGPoint(
            x: Motion.lerp(Motion.mapFrom.x, Motion.mapTo.x, open) * scale,
            y: safeTop + (Motion.lerp(Motion.mapFrom.y, Motion.mapTo.y, open) -
                RaceResultStandingsTheme.REF_TOP) * scale
        )
        let tilt = sin(.pi * Motion.clamp(open)) * Motion.mapTilt
        let perspective = max(trackSize.width, trackSize.height) / Motion.mapPerspective
        let clipHeight = safeTop + (372 - RaceResultStandingsTheme.REF_TOP) * scale

        ZStack(alignment: .topLeading) {
            Canvas(opaque: false, rendersAsynchronously: false) { context, _ in
                drawCircuit(&context, mapScale: mapScale)
            }
            .frame(width: trackSize.width, height: trackSize.height)
            .scaleEffect(mapScale)
            .rotation3DEffect(
                .degrees(tilt),
                axis: (x: 1, y: 0, z: 0),
                anchor: .center,
                perspective: perspective
            )
            .position(center)

            Canvas(opaque: false, rendersAsynchronously: false) { context, _ in
                drawScreenSpace(&context)
            }
            .frame(width: size.width, height: clipHeight, alignment: .topLeading)
        }
        .frame(width: size.width, height: clipHeight, alignment: .topLeading)
        .clipped()
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }

    private static func image(named name: String) -> Image? {
        guard let raw = UIImage(named: name) else { return nil }
        return Image(uiImage: raw.preparingForDisplay() ?? raw).interpolation(.high)
    }

    private func drawCircuit(_ context: inout GraphicsContext, mapScale: Double) {
        let bounds = CGRect(
            x: 0,
            y: 0,
            width: RaceResultStandingsTheme.layout.track.w * scale,
            height: RaceResultStandingsTheme.layout.track.h * scale
        )
        let open = Motion.mapOpen(frame.replay)
        let smallOpacity = 1 - Motion.clamp((Motion.mapScale - 1) * open / Motion.mapHiResFrom)
        if let large = Self.trackLarge, open > 0.0001 {
            context.draw(large, in: bounds)
        }
        if let small = Self.trackSmall, smallOpacity > 0.0001 {
            var layer = context
            layer.opacity = smallOpacity
            layer.draw(small, in: bounds)
        }

        drawComet(&context)
        drawCars(&context, mapScale: mapScale)
        drawFinishFlash(&context, mapScale: mapScale)
    }

    private func drawComet(_ context: inout GraphicsContext) {
        let place = Motion.lapPlace(frame.lap)
        let alpha = Motion.cometAlpha(frame.lap)
        if alpha > 0 {
            context.drawLayer { layer in
                layer.opacity = alpha
                var glow = layer
                glow.addFilter(.blur(radius: 3 * scale))
                glow.stroke(
                    singleLapSegment(from: Motion.lapPlace(frame.lap - Motion.glowLag), to: place),
                    with: .color(RaceResultStandingsTheme.color(
                        RaceResultStandingsTheme.colors.cometGlow,
                        opacity: 0.4
                    )),
                    style: StrokeStyle(lineWidth: 5 * scale, lineCap: .round)
                )
                for stroke in Motion.tail.reversed() {
                    layer.stroke(
                        singleLapSegment(from: Motion.lapPlace(frame.lap - stroke.lag), to: place),
                        with: .color(RaceResultStandingsTheme.color(
                            RaceResultStandingsTheme.colors.comet,
                            opacity: stroke.alpha
                        )),
                        style: StrokeStyle(lineWidth: stroke.width * scale, lineCap: .round)
                    )
                }
                let head = point(at: place)
                var headGlow = layer
                headGlow.addFilter(.blur(radius: 5 * scale))
                headGlow.fill(
                    Path(ellipseIn: CGRect(
                        x: head.x - 8 * scale,
                        y: head.y - 8 * scale,
                        width: 16 * scale,
                        height: 16 * scale
                    )),
                    with: .color(RaceResultStandingsTheme.color(
                        RaceResultStandingsTheme.colors.cometGlow,
                        opacity: 0.65
                    ))
                )
                layer.fill(
                    Path(ellipseIn: CGRect(
                        x: head.x - 3 * scale,
                        y: head.y - 3 * scale,
                        width: 6 * scale,
                        height: 6 * scale
                    )),
                    with: .color(RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.comet))
                )
            }
        }

        let flash = Motion.flash(frame.lap)
        if flash > 0 {
            let line = point(at: 0)
            let radius = (2 + 11 * Motion.easeOut(flash)) * scale
            context.stroke(
                Path(ellipseIn: CGRect(
                    x: line.x - radius,
                    y: line.y - radius,
                    width: radius * 2,
                    height: radius * 2
                )),
                with: .color(RaceResultStandingsTheme.color(
                    RaceResultStandingsTheme.colors.cometGlow,
                    opacity: 1 - flash
                )),
                lineWidth: 1.4 * scale
            )
        }
    }

    private func drawCars(_ context: inout GraphicsContext, mapScale: Double) {
        let alpha = Motion.carAlpha(frame.replay)
        guard alpha > 0.0001 else { return }

        for index in RaceResultStandingsData.drivers.indices {
            let color = RaceResultStandingsTheme.color(RaceResultStandingsData.carShade[index].fill)
            let end = Motion.carLaps(index, replay: frame.replay)
            for trail in Motion.raceTrails {
                let start = end - Motion.raceTrailLag * trail.part * Motion.leaderPace(frame.replay)
                context.stroke(
                    repeatedLapSegment(from: start, to: end),
                    with: .color(color.opacity(alpha * trail.alpha)),
                    style: StrokeStyle(
                        lineWidth: trail.width * scale / mapScale,
                        lineCap: .round
                    )
                )
            }
        }

        for index in RaceResultStandingsData.drivers.indices {
            let color = RaceResultStandingsTheme.color(RaceResultStandingsData.carShade[index].fill)
            let position = carPoint(
                laps: Motion.carLaps(index, replay: frame.replay),
                side: Motion.carLateral(index, replay: frame.replay) * scale
            )
            let glowRadius = Motion.carGlow * scale / mapScale
            var glow = context
            glow.addFilter(.blur(radius: Motion.carGlowBlur * scale / mapScale))
            glow.fill(
                Path(ellipseIn: CGRect(
                    x: position.x - glowRadius,
                    y: position.y - glowRadius,
                    width: glowRadius * 2,
                    height: glowRadius * 2
                )),
                with: .color(color.opacity(alpha * Motion.carGlowAlpha))
            )
            let core = Motion.carCore * scale / mapScale
            context.fill(
                Path(ellipseIn: CGRect(
                    x: position.x - core,
                    y: position.y - core,
                    width: core * 2,
                    height: core * 2
                )),
                with: .color(color.opacity(alpha))
            )
            let centre = Motion.carCentre * scale / mapScale
            context.fill(
                Path(ellipseIn: CGRect(
                    x: position.x - centre,
                    y: position.y - centre,
                    width: centre * 2,
                    height: centre * 2
                )),
                with: .color(RaceResultStandingsTheme.color(
                    RaceResultStandingsTheme.colors.ink,
                    opacity: alpha
                ))
            )
        }
    }

    private func drawFinishFlash(_ context: inout GraphicsContext, mapScale: Double) {
        let flash = Motion.finishFlash(frame.replay)
        guard flash > 0 else { return }
        let line = point(at: 0)
        let radius = (Motion.finishFlashFrom +
            (Motion.finishFlashTo - Motion.finishFlashFrom) * Motion.easeOut(flash)) * scale / mapScale
        context.stroke(
            Path(ellipseIn: CGRect(
                x: line.x - radius,
                y: line.y - radius,
                width: radius * 2,
                height: radius * 2
            )),
            with: .color(RaceResultStandingsTheme.color(
                RaceResultStandingsTheme.colors.cometGlow,
                opacity: 1 - flash
            )),
            lineWidth: Motion.finishFlashStroke * scale / mapScale
        )
    }

    private func drawScreenSpace(_ context: inout GraphicsContext) {
        drawTags(&context)
        drawLights(&context)
    }

    private func drawTags(_ context: inout GraphicsContext) {
        for index in RaceResultStandingsData.drivers.indices {
            let alpha = Motion.tagAlpha(index, replay: frame.replay) * Motion.carAlpha(frame.replay)
            guard alpha > 0.0001 else { continue }
            let driver = RaceResultStandingsData.drivers[index]
            let local = carPoint(
                laps: Motion.carLaps(index, replay: frame.replay),
                side: Motion.carLateral(index, replay: frame.replay) * scale
            )
            let half = CGPoint(
                x: RaceResultStandingsTheme.layout.track.w * scale / 2,
                y: RaceResultStandingsTheme.layout.track.h * scale / 2
            )
            let position = CGPoint(
                x: Motion.mapTo.x * scale + (local.x - half.x) * Motion.mapScale,
                y: safeTop + (Motion.mapTo.y - RaceResultStandingsTheme.REF_TOP) * scale +
                    (local.y - half.y) * Motion.mapScale
            )
            var text = context.resolve(
                Text(driver.code)
                    .font(.custom("Montserrat-ExtraBold", fixedSize: Motion.tagSize * scale))
            )
            text.shading = .color(RaceResultStandingsTheme.color(RaceResultStandingsData.carShade[index].ink))
            let measured = text.measure(in: CGSize(width: 200, height: 100))
            let width = measured.width + 2 * Motion.tagPadX * scale
            let height = Motion.tagHeight * scale
            let lift = (Motion.tagAbove + Motion.tagRise * (1 - alpha)) * scale
            let rect = CGRect(
                x: position.x - width / 2,
                y: position.y - lift - height,
                width: width,
                height: height
            )
            context.fill(
                Path(roundedRect: rect, cornerRadius: Motion.tagRadius * scale),
                with: .color(RaceResultStandingsTheme.color(RaceResultStandingsData.carShade[index].fill).opacity(alpha))
            )
            var textLayer = context
            textLayer.opacity *= alpha
            let baseline = position.y - lift - height / 2 + Motion.tagSize * scale * 0.36
            textLayer.draw(
                text,
                at: CGPoint(
                    x: rect.minX + Motion.tagPadX * scale,
                    y: baseline - text.firstBaseline(in: measured)
                ),
                anchor: .topLeading
            )
        }
    }

    private func drawLights(_ context: inout GraphicsContext) {
        let housing = Motion.lightsHousing(frame.replay)
        guard housing > 0.0001 else { return }
        let center = CGPoint(
            x: Motion.lightsCenter.x * scale,
            y: safeTop + (Motion.lightsCenter.y - RaceResultStandingsTheme.REF_TOP) * scale
        )
        let size = CGSize(
            width: Motion.lightsPill.width * scale,
            height: Motion.lightsPill.height * scale
        )
        let rect = CGRect(
            x: center.x - size.width / 2,
            y: center.y - size.height / 2,
            width: size.width,
            height: size.height
        )
        let pill = Path(roundedRect: rect, cornerRadius: size.height / 2)
        context.fill(
            pill,
            with: .color(RaceResultStandingsTheme.color(
                RaceResultStandingsTheme.colors.plate,
                opacity: housing
            ))
        )
        context.stroke(
            pill,
            with: .color(RaceResultStandingsTheme.color(
                RaceResultStandingsTheme.colors.plateEdge,
                opacity: housing
            )),
            lineWidth: 1 / max(displayScale, 1)
        )

        for index in 0..<Motion.lightsCount {
            let on = Motion.lightOn(index, replay: frame.replay)
            let lightCenter = CGPoint(
                x: center.x + (Double(index) - Double(Motion.lightsCount - 1) / 2) * Motion.lightsPitch * scale,
                y: center.y
            )
            let radius = Motion.lightsRadius * scale
            context.fill(
                Path(ellipseIn: CGRect(
                    x: lightCenter.x - radius,
                    y: lightCenter.y - radius,
                    width: radius * 2,
                    height: radius * 2
                )),
                with: .color(RaceResultStandingsTheme.color(
                    RaceResultStandingsTheme.colors.lightOff,
                    opacity: housing
                ))
            )
            if on > 0 {
                let glowRadius = radius * Motion.lightsGlowRadius
                var glow = context
                glow.addFilter(.blur(radius: Motion.lightsGlowBlur * scale))
                glow.fill(
                    Path(ellipseIn: CGRect(
                        x: lightCenter.x - glowRadius,
                        y: lightCenter.y - glowRadius,
                        width: glowRadius * 2,
                        height: glowRadius * 2
                    )),
                    with: .color(RaceResultStandingsTheme.color(
                        RaceResultStandingsTheme.colors.award,
                        opacity: housing * on * Motion.lightsGlowAlpha
                    ))
                )
                context.fill(
                    Path(ellipseIn: CGRect(
                        x: lightCenter.x - radius,
                        y: lightCenter.y - radius,
                        width: radius * 2,
                        height: radius * 2
                    )),
                    with: .color(RaceResultStandingsTheme.color(
                        RaceResultStandingsTheme.colors.award,
                        opacity: housing * on
                    ))
                )
            }
        }
    }

    private func point(at lap: Double) -> CGPoint {
        let count = Self.points.count
        let progress = ((lap.truncatingRemainder(dividingBy: 1)) + 1)
            .truncatingRemainder(dividingBy: 1) * Double(count)
        let index = min(count - 1, Int(floor(progress)))
        let amount = progress - Double(index)
        let next = (index + 1) % count
        return CGPoint(
            x: (Self.points[index].x + (Self.points[next].x - Self.points[index].x) * amount) * scale,
            y: (Self.points[index].y + (Self.points[next].y - Self.points[index].y) * amount) * scale
        )
    }

    private func carPoint(laps: Double, side: Double) -> CGPoint {
        let count = Self.points.count
        let progress = ((laps.truncatingRemainder(dividingBy: 1)) + 1)
            .truncatingRemainder(dividingBy: 1) * Double(count)
        let index = min(count - 1, Int(floor(progress)))
        let amount = progress - Double(index)
        let next = (index + 1) % count
        let x = (Self.points[index].x + (Self.points[next].x - Self.points[index].x) * amount) * scale
        let y = (Self.points[index].y + (Self.points[next].y - Self.points[index].y) * amount) * scale
        let tangentX = (Self.points[next].x - Self.points[index].x) * scale
        let tangentY = (Self.points[next].y - Self.points[index].y) * scale
        let length = max(0.0001, hypot(tangentX, tangentY))
        return CGPoint(x: x - tangentY / length * side, y: y + tangentX / length * side)
    }

    private func singleLapSegment(from: Double, to: Double) -> Path {
        segment(from: Motion.clamp(from), to: Motion.clamp(to), lapOffset: 0)
    }

    private func repeatedLapSegment(from: Double, to: Double) -> Path {
        let start = min(3.9999, max(0, from + 1))
        let end = min(3.9999, max(0, to + 1))
        return segment(from: start, to: end, lapOffset: 0)
    }

    private func segment(from: Double, to: Double, lapOffset: Double) -> Path {
        let count = Self.points.count
        let start = (from + lapOffset) * Double(count)
        let end = (to + lapOffset) * Double(count)
        var path = Path()
        path.move(to: pointForRepeatedIndex(start))
        guard end > start else { return path }
        var index = Int(floor(start)) + 1
        while Double(index) < end {
            path.addLine(to: pointForRepeatedIndex(Double(index)))
            index += 1
        }
        path.addLine(to: pointForRepeatedIndex(end))
        return path
    }

    private func pointForRepeatedIndex(_ value: Double) -> CGPoint {
        let count = Self.points.count
        let wrapped = value.truncatingRemainder(dividingBy: Double(count))
        let positive = wrapped < 0 ? wrapped + Double(count) : wrapped
        let index = min(count - 1, Int(floor(positive)))
        let amount = positive - Double(index)
        let next = (index + 1) % count
        return CGPoint(
            x: (Self.points[index].x + (Self.points[next].x - Self.points[index].x) * amount) * scale,
            y: (Self.points[index].y + (Self.points[next].y - Self.points[index].y) * amount) * scale
        )
    }
}
