import CoreText
import SwiftUI
import UIKit

struct CityEventHeroNightScene: View {
    let frame: CityEventHeroMotion.Frame
    let size: CGSize
    let safeTop: Double

    var body: some View {
        let geometry = CityEventHeroSceneGeometry(size: size, safeTop: safeTop, frame: frame)
        ZStack(alignment: .topLeading) {
            Image(uiImage: CityEventHeroAssets.city)
                .resizable()
                .interpolation(.high)
                .frame(width: geometry.photoWidth, height: geometry.photoHeight)
                .offset(x: geometry.photoLeft, y: geometry.photoTop)

            scenePhoto(geometry: geometry, mode: 0)
                .opacity(geometry.sceneOpacity * frame.still)
                .offset(x: geometry.photoLeft, y: geometry.photoTop)

            CityEventHeroHourTitle(
                frame: frame,
                fontScale: geometry.fontScale,
                centerX: geometry.photoLeft + CityEventHeroMotion.LED.mid * geometry.cover,
                baseline: (CityEventHeroMotion.Title.base - CityEventHeroTheme.Layout.card.y) * geometry.unit
            )
            .frame(width: geometry.cardWidth, height: geometry.cardHeight, alignment: .topLeading)
            .offset(
                x: geometry.cameraX * CityEventHeroMotion.Space.title * geometry.cover,
                y: geometry.cameraY * CityEventHeroMotion.Space.title * geometry.cover
            )
            .opacity(frame.title)

            scenePhoto(geometry: geometry, mode: 1)
                .opacity(frame.title > 0.001 ? geometry.sceneOpacity * frame.still : 0)
                .offset(x: geometry.photoLeft, y: geometry.photoTop)

            Rectangle()
                .fill(.white)
                .frame(width: geometry.cardWidth, height: min(1_000 * geometry.unit, geometry.cardHeight), alignment: .topLeading)
                .colorEffect(
                    ShaderLibrary.cityEventHeroMist(
                        .float(geometry.unit),
                        .float(frame.drift * CityEventHeroMotion.Ambient.drift),
                        .float(
                            frame.mist * CityEventHeroMotion.Ambient.mist
                                * (1 - min(1, frame.night / CityEventHeroMotion.Night.grade))
                        )
                    )
                )
        }
        .frame(width: geometry.cardWidth, height: geometry.cardHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: CityEventHeroTheme.Radius.card * geometry.unit))
        .offset(y: geometry.cardTop)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func scenePhoto(geometry: CityEventHeroSceneGeometry, mode: Double) -> some View {
        Image(uiImage: CityEventHeroAssets.city)
            .resizable()
            .interpolation(.high)
            .frame(width: geometry.photoWidth, height: geometry.photoHeight)
            .layerEffect(
                sceneShader(geometry: geometry, mode: mode),
                maxSampleOffset: CGSize(width: geometry.photoWidth * 0.15, height: geometry.photoHeight * 0.15)
            )
    }

    private func sceneShader(geometry: CityEventHeroSceneGeometry, mode: Double) -> Shader {
        let sun = CityEventHeroMotion.sun(day: frame.day)
        let fire = CityEventHeroMotion.fire(of: frame.show)
        let fireFade = min(1, frame.night / CityEventHeroMotion.Night.fireFade)
        let led = CityEventHeroMotion.led(show: frame.show)
        let activeFire = fire >= 0
        let burstColors = CityEventHeroMotion.Fire.bursts.map(\.color)
        let flashes = CityEventHeroMotion.Fire.bursts.map { burst -> SIMD3<Float> in
            let center = geometry.burstCenter(burst)
            return SIMD3(
                Float(center.x),
                Float(center.y),
                Float(activeFire ? CityEventHeroMotion.flash(at: burst, time: fire - burst.at) * fireFade : 0)
            )
        }
        let smoke = CityEventHeroMotion.Fire.bursts.map { burst in
            activeFire && fire > burst.at ? Float(fire - burst.at) : -1
        }

        return ShaderLibrary.cityEventHeroScene(
            .image(Image(uiImage: CityEventHeroAssets.sceneAtlas)),
            .float(mode),
            .float(frame.night),
            .float(geometry.cover),
            .float3(Float(geometry.cameraX), Float(geometry.cameraY), Float(frame.camZ)),
            .float4(
                Float(sun.azimuth),
                Float(sun.elevation),
                Float(sun.amount * (1 - min(1, frame.night / CityEventHeroMotion.Night.grade))),
                0
            ),
            .float3(flashes[0].x, flashes[0].y, flashes[0].z),
            .float3(flashes[1].x, flashes[1].y, flashes[1].z),
            .float3(flashes[2].x, flashes[2].y, flashes[2].z),
            .float3(Float(burstColors[0].x), Float(burstColors[0].y), Float(burstColors[0].z)),
            .float3(Float(burstColors[1].x), Float(burstColors[1].y), Float(burstColors[1].z)),
            .float3(Float(burstColors[2].x), Float(burstColors[2].y), Float(burstColors[2].z)),
            .float3(smoke[0], smoke[1], smoke[2]),
            .float3(
                Float(geometry.tip.x),
                Float(geometry.tip.y),
                Float(activeFire ? CityEventHeroMotion.flare(at: fire) * fireFade : 0)
            ),
            .float4(Float(led[0] * fireFade), Float(led[1]), Float(led[2]), Float(led[3])),
            .float4(Float(led[4]), Float(led[5]), Float(led[6]), Float(led[7])),
            .float4(Float(led[8]), Float(led[9]), Float(led[10]), Float(led[11])),
            .float4(Float(led[12]), Float(fireFade), 0, 0),
            .float(frame.drift)
        )
    }
}

private struct CityEventHeroSceneGeometry {
    let unit: Double
    let fontScale: Double
    let cardTop: Double
    let cardWidth: Double
    let cardHeight: Double
    let photoWidth: Double
    let photoHeight: Double
    let photoLeft: Double
    let photoTop: Double
    let cover: Double
    let cameraX: Double
    let cameraY: Double
    let cameraScale: Double
    let tip: CGPoint
    let sceneOpacity: Double

    init(size: CGSize, safeTop: Double, frame: CityEventHeroMotion.Frame) {
        unit = size.width / CityEventHeroTheme.referenceWidth
        fontScale = size.width / CityEventHeroTheme.referencePoints
        cardTop = safeTop + (CityEventHeroTheme.Layout.card.y - CityEventHeroTheme.referenceInset) * unit
        let cardBottom = size.height - (
            CityEventHeroTheme.referenceHeight
                - CityEventHeroTheme.Layout.card.y
                - CityEventHeroTheme.Layout.card.height
        ) * unit
        cardWidth = CityEventHeroTheme.Layout.card.width * unit
        cardHeight = cardBottom - cardTop
        cover = max(cardWidth / 850, cardHeight / 1_850)
        photoWidth = 850 * cover
        photoHeight = 1_850 * cover
        photoLeft = (cardWidth - photoWidth) / 2
        photoTop = -(photoHeight - cardHeight) * CityEventHeroTheme.Layout.photoShift
        cameraX = frame.camX * CityEventHeroMotion.Space.ampX
        cameraY = frame.camY * CityEventHeroMotion.Space.ampY
        let cameraZoom = frame.camZ * CityEventHeroMotion.Space.dolly
            + abs(cameraX) * CityEventHeroMotion.Space.coverX
            + abs(cameraY) * CityEventHeroMotion.Space.coverY
        cameraScale = 1 + cameraZoom * CityEventHeroMotion.Space.tower
        let focus = CGPoint(x: CityEventHeroMotion.Space.focusX, y: CityEventHeroMotion.Space.focusY)
        tip = CGPoint(
            x: focus.x + (CityEventHeroMotion.Tower.tipX - focus.x) * cameraScale
                + cameraX * CityEventHeroMotion.Space.tower,
            y: focus.y + (CityEventHeroMotion.Tower.tipY - focus.y) * cameraScale
                + cameraY * CityEventHeroMotion.Space.tower
        )
        let showActive = frame.show >= 0 && frame.show < CityEventHeroMotion.SHOW_END ? 1.0 : 0.0
        let amount = [
            frame.day,
            frame.night,
            abs(cameraX) / CityEventHeroMotion.Space.ampX,
            abs(cameraY) / CityEventHeroMotion.Space.ampY,
            frame.camZ,
            showActive
        ].max() ?? 0
        sceneOpacity = min(1, amount / 0.02)
    }

    func burstCenter(_ burst: CityEventHeroMotion.Burst) -> CGPoint {
        let referenceToPhoto = unit / cover
        return CGPoint(
            x: tip.x + burst.dx * referenceToPhoto * cameraScale,
            y: tip.y + burst.dy * referenceToPhoto * cameraScale
        )
    }
}

private struct CityEventHeroHourTitle: UIViewRepresentable {
    let frame: CityEventHeroMotion.Frame
    let fontScale: Double
    let centerX: Double
    let baseline: Double

    func makeUIView(context: Context) -> CityEventHeroHourTitleRenderView {
        CityEventHeroHourTitleRenderView()
    }

    func updateUIView(_ view: CityEventHeroHourTitleRenderView, context: Context) {
        view.frameValue = frame
        view.fontScale = fontScale
        view.centerX = centerX
        view.baseline = baseline
        view.setNeedsDisplay()
    }
}

@MainActor
private final class CityEventHeroHourTitleRenderView: UIView {
    var frameValue = CityEventHeroMotion.Frame()
    var fontScale = 1.0
    var centerX = 0.0
    var baseline = 0.0

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let titleFont = CTFontCreateWithGraphicsFont(
            CityEventHeroAssets.headlineFont,
            CityEventHeroMotion.Title.size * fontScale,
            nil,
            nil
        )
        let suffixFont = CTFontCreateWithGraphicsFont(
            CityEventHeroAssets.headlineFont,
            CityEventHeroMotion.Title.suffix * fontScale,
            nil,
            nil
        )
        let clock = CityEventHeroMotion.clock(
            hour: CityEventHeroMotion.hour(day: frameValue.day, night: frameValue.night)
        )
        let digitWidths = (0...9).map { advance("\($0)", font: titleFont) }
        let digitWidth = digitWidths.max() ?? 0
        let colonWidth = advance(":", font: titleFont)
        let separation = CityEventHeroMotion.Title.separation * fontScale
        let suffixGap = 6 * fontScale
        let minuteTens = clock.minute / 10
        let minuteOnes = clock.minute % 10
        let hourOnes = clock.hour % 10

        draw("\(hourOnes)", font: titleFont, x: centerX - separation - digitWidth + (digitWidth - digitWidths[hourOnes]) / 2, context: context)
        if clock.hour >= 10 {
            draw("1", font: titleFont, x: centerX - separation - 2 * digitWidth + (digitWidth - digitWidths[1]) / 2, context: context)
        }
        draw(":", font: titleFont, x: centerX - colonWidth / 2, context: context)
        draw("\(minuteTens)", font: titleFont, x: centerX + separation + (digitWidth - digitWidths[minuteTens]) / 2, context: context)
        draw("\(minuteOnes)", font: titleFont, x: centerX + separation + digitWidth + (digitWidth - digitWidths[minuteOnes]) / 2, context: context)
        let suffix = clock.isPM ? "PM" : "AM"
        draw(
            suffix,
            font: suffixFont,
            x: centerX + separation + 2 * digitWidth + suffixGap,
            alpha: 0.62,
            context: context
        )
    }

    private func advance(_ text: String, font: CTFont) -> Double {
        CTLineGetTypographicBounds(line(text, font: font, alpha: 1), nil, nil, nil)
    }

    private func line(_ text: String, font: CTFont, alpha: Double) -> CTLine {
        CTLineCreateWithAttributedString(
            NSAttributedString(
                string: text,
                attributes: [
                    NSAttributedString.Key(kCTFontAttributeName as String): font,
                    NSAttributedString.Key(kCTForegroundColorAttributeName as String): UIColor.white.withAlphaComponent(alpha).cgColor,
                ]
            )
        )
    }

    private func draw(_ text: String, font: CTFont, x: Double, alpha: Double = 0.9, context: CGContext) {
        let textLine = line(text, font: font, alpha: alpha)
        context.saveGState()
        context.translateBy(x: x, y: baseline)
        context.scaleBy(x: 1, y: -1)
        context.textPosition = .zero
        CTLineDraw(textLine, context)
        context.restoreGState()
    }
}
