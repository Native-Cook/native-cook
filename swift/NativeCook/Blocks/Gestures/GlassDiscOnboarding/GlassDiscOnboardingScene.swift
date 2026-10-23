import CoreMotion
import CoreText
import Observation
import SwiftUI
import UIKit

/// Draws the measured RN composition. The model owns transitions; this file only maps its frame values to pixels.
struct GlassDiscOnboardingScene: View {
    let model: GlassDiscOnboardingModel
    let size: CGSize
    let reducedMotion: Bool

    @State private var tilt = GlassDiscOnboardingTiltReader()

    private typealias Theme = GlassDiscOnboardingTheme
    private typealias Motion = GlassDiscOnboardingMotion

    var body: some View {
        let headline = model.headIn
        let rest = model.restIn
        let rise = reducedMotion ? 0 : Motion.Upgrade.rise
        let headlineOffset = (1 - eased(headline)) * rise
        let restOffset = (1 - eased(rest)) * rise
        let late = clamped(rest * (1 + Motion.Upgrade.lineStagger / Motion.Fade.restMs) - Motion.Upgrade.lineStagger / Motion.Fade.restMs)
        let lateOffset = (1 - eased(late)) * rise

        ZStack(alignment: .topLeading) {
            backdrop

            intro
                .opacity(model.introAlpha)
                .offset(y: reducedMotion ? 0 : -(1 - model.introAlpha) * Motion.Upgrade.rise)

            communityCopy(headline: headline, rest: rest, late: late, headlineOffset: headlineOffset, restOffset: restOffset, lateOffset: lateOffset)

            GlassDiscOnboardingLensTexture(size: size, model: model, tilt: tilt.current, reducedMotion: reducedMotion)
                .allowsHitTesting(false)

            GlassDiscOnboardingCloud(size: size, model: model, reducedMotion: reducedMotion)
                .allowsHitTesting(false)

            GlassDiscOnboardingTouchView(model: model)
                .frame(width: size.width, height: size.height)

            hint
            buttons(rest: rest, offset: restOffset)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .onAppear { tilt.start() }
        .onDisappear { tilt.stop() }
    }

    private var backdrop: some View {
        Rectangle()
            .fill(.white)
            .colorEffect(
                ShaderLibrary.glassDiscOnboardingBackdrop(
                    .float2(size),
                    GlassDiscOnboardingColor.shader(Theme.Color.bgTL),
                    GlassDiscOnboardingColor.shader(Theme.Color.bgTR),
                    GlassDiscOnboardingColor.shader(Theme.Color.bgBL),
                    GlassDiscOnboardingColor.shader(Theme.Color.bgBR)
                )
            )
            .overlay {
                let x = size.width / 2 + (reducedMotion ? 0 : Motion.Upgrade.fieldDrift * sin(model.clock / Motion.Upgrade.fieldPeriodMs * .pi * 2))
                Circle()
                    .fill(RadialGradient(
                        stops: [
                            .init(color: GlassDiscOnboardingColor.rgba(Theme.Color.fieldA), location: 0),
                            .init(color: GlassDiscOnboardingColor.rgba(Theme.Color.fieldB), location: Theme.Render.fieldMiddle),
                            .init(color: GlassDiscOnboardingColor.rgba(Theme.Color.fieldClear), location: 1),
                        ],
                        center: .center,
                        startRadius: 0,
                        endRadius: size.width * Theme.Render.fieldRadius
                    ))
                    .frame(width: size.width * Theme.Render.fieldRadius * 2, height: size.width * Theme.Render.fieldRadius * 2)
                    .position(x: x, y: size.height * Theme.Layout.beadRestY)
                    .opacity(model.headIn)
            }
    }

    private var intro: some View {
        let top = size.height * Theme.Layout.introBaseline - (Theme.Typography.introLineHeight - Theme.Typography.introSize) / 2 - Theme.Typography.introSize * Theme.Render.ascent
        return VStack(spacing: 0) {
            ForEach(Theme.Copy.intro, id: \.self) { line in
                Text(line)
                    .font(.system(size: Theme.Typography.introSize, weight: .regular))
                    .kerning(Theme.Typography.introLetterSpacing)
                    .foregroundStyle(GlassDiscOnboardingColor.hex(Theme.Color.ink))
                    .frame(height: Theme.Typography.introLineHeight)
            }
        }
        .frame(width: size.width, alignment: .center)
        .position(x: size.width / 2, y: top + Theme.Typography.introLineHeight)
        .accessibilityHidden(true)
    }

    private func communityCopy(
        headline: Double,
        rest: Double,
        late: Double,
        headlineOffset: Double,
        restOffset: Double,
        lateOffset: Double
    ) -> some View {
        let top = size.height * Theme.Layout.headlineBaseline - (Theme.Typography.headlineLineHeight - Theme.Typography.headlineSize) / 2 - Theme.Typography.headlineSize * Theme.Render.ascent
        return VStack(spacing: 0) {
            line(Theme.Copy.headline).opacity(headline).offset(y: headlineOffset)
            line(Theme.Copy.sub[0]).opacity(rest).offset(y: restOffset)
            line(Theme.Copy.sub[1]).opacity(late).offset(y: lateOffset)
        }
        .frame(width: size.width, alignment: .center)
        .position(x: size.width / 2, y: top + Theme.Typography.headlineLineHeight * 1.5)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(model.stage == .community ? .isHeader : [])
        .accessibilityHidden(model.stage != .community)
    }

    private func line(_ text: String) -> some View {
        Text(text)
            .font(.system(size: Theme.Typography.headlineSize, weight: .regular))
            .kerning(Theme.Typography.headlineTracking)
            .foregroundStyle(text == Theme.Copy.headline ? GlassDiscOnboardingColor.hex(Theme.Color.ink) : GlassDiscOnboardingColor.hex(Theme.Color.inkSoft))
            .frame(height: Theme.Typography.headlineLineHeight)
    }

    private var hint: some View {
        let lineHeight = UIFont.systemFont(ofSize: Theme.Typography.hintSize, weight: .medium).lineHeight
        return Text(Theme.Copy.hint)
            .font(.system(size: Theme.Typography.hintSize, weight: .medium))
            .kerning(Theme.Typography.hintLetterSpacing)
            .foregroundStyle(GlassDiscOnboardingColor.hex(Theme.Color.hint))
            .frame(width: size.width, height: lineHeight, alignment: .top)
            .position(x: size.width / 2, y: size.height * Theme.Layout.hintY + lineHeight / 2)
            .opacity(model.hintAlpha * (Motion.Upgrade.hintBase + (1 - Motion.Upgrade.hintBase) * model.glint))
            .offset(y: model.discY - (size.height * Theme.Layout.domeTop + size.width * Theme.Layout.domeRadius))
            .allowsHitTesting(false)
    }

    private func buttons(rest: Double, offset: Double) -> some View {
        let darkY = size.height - Theme.Layout.buttonsBottom - Theme.Layout.buttonHeightDark
        let lightY = darkY - Theme.Layout.buttonGap - Theme.Layout.buttonHeight
        return VStack(spacing: Theme.Layout.buttonGap) {
            GlassDiscOnboardingButton(label: Theme.Copy.email, symbol: "envelope.fill", dark: false)
                .frame(height: Theme.Layout.buttonHeight)
            GlassDiscOnboardingButton(label: Theme.Copy.phone, symbol: "phone.fill", dark: true)
                .frame(height: Theme.Layout.buttonHeightDark)
        }
        .frame(width: size.width - Theme.Layout.gutter * 2)
        .position(x: size.width / 2, y: lightY + (Theme.Layout.buttonHeight + Theme.Layout.buttonGap + Theme.Layout.buttonHeightDark) / 2)
        .opacity(rest)
        .offset(y: offset)
        .allowsHitTesting(model.stage == .community)
    }

    private func clamped(_ value: Double) -> Double { min(max(value, 0), 1) }
    private func eased(_ value: Double) -> Double { 1 - pow(1 - clamped(value), 3) }
}

private struct GlassDiscOnboardingButton: View {
    let label: String
    let symbol: String
    let dark: Bool

    @State private var lensX = 0.0
    @State private var pressed = false
    @Environment(\.displayScale) private var displayScale

    private typealias Theme = GlassDiscOnboardingTheme
    private typealias Motion = GlassDiscOnboardingMotion

    var body: some View {
        Button {} label: {
            GeometryReader { proxy in
                ZStack {
                    Capsule()
                        .fill(dark ? GlassDiscOnboardingColor.hex(Theme.Color.buttonDark) : GlassDiscOnboardingColor.hex(Theme.Color.buttonLight))
                        .overlay {
                            if !dark {
                                Capsule().strokeBorder(GlassDiscOnboardingColor.rgba(Theme.Color.buttonLightEdge), lineWidth: 1 / displayScale)
                            }
                        }

                    LinearGradient(
                        colors: dark
                            ? [.clear, .white.opacity(Theme.Render.buttonLensDarkAlpha), .clear]
                            : [.clear, .white.opacity(Theme.Render.buttonLensLightAlpha), .clear],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: Theme.Render.buttonLensWidth, height: proxy.size.height)
                    .offset(x: lensX - proxy.size.width / 2)
                    .opacity(pressed ? 1 : 0)
                    .clipShape(Capsule())

                    if !dark {
                        Rectangle()
                            .fill(.white.opacity(Theme.Render.buttonRimAlpha))
                            .frame(height: Theme.Render.buttonRimHairlines / displayScale)
                            .padding(.horizontal, Theme.Space.xl)
                            .frame(maxHeight: .infinity, alignment: .top)
                            .clipShape(Capsule())
                    }

                    Image(systemName: symbol)
                        .font(.system(size: Theme.Layout.buttonIcon, weight: .medium))
                        .foregroundStyle(dark ? GlassDiscOnboardingColor.hex(Theme.Color.buttonDarkInk) : GlassDiscOnboardingColor.hex(Theme.Color.buttonLightInk))
                        .frame(width: Theme.Layout.buttonIcon, height: Theme.Layout.buttonIcon)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.leading, Theme.Layout.buttonIconInset)
                    Text(label)
                        .font(.system(size: Theme.Typography.buttonSize, weight: dark ? .semibold : .regular))
                        .kerning(Theme.Typography.buttonLetterSpacing)
                        .foregroundStyle(dark ? GlassDiscOnboardingColor.hex(Theme.Color.buttonDarkInk) : GlassDiscOnboardingColor.hex(Theme.Color.buttonLightInk))
                }
                .clipShape(Capsule())
                .scaleEffect(pressed ? Motion.Upgrade.pressScale : 1)
                .shadow(
                    color: dark ? .clear : GlassDiscOnboardingColor.hex(Theme.Color.buttonLightShadow).opacity(Theme.Render.buttonShadowAlpha),
                    radius: Theme.Render.buttonShadowRadius,
                    y: Theme.Render.buttonShadowY
                )
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: pressed ? Motion.Upgrade.pressIn / 1_000 : Motion.Upgrade.pressOut / 1_000), value: pressed)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            lensX = value.location.x
                            pressed = true
                        }
                        .onEnded { _ in pressed = false }
                )
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private struct GlassDiscOnboardingLensTexture: View {
    let size: CGSize
    let model: GlassDiscOnboardingModel
    let tilt: CGPoint
    let reducedMotion: Bool

    private typealias Theme = GlassDiscOnboardingTheme

    var body: some View {
        let radius = model.discR * (1 - model.squash)
        let usesLoneHeadline = model.texMode < 0.5
        let origin = usesLoneHeadline
            ? CGPoint(x: size.width / 2 - Theme.Layout.lens.tx * radius, y: size.height * Theme.Render.lensHeadlineBaseline - Theme.Layout.lens.ty * radius)
            : CGPoint(x: model.discX, y: model.discY - model.nudge)

        GlassDiscOnboardingTextureCanvas(size: size)
            .frame(width: size.width, height: size.height * 2, alignment: .top)
            // The rising lens samples its lone headline from y = 1.5H in the transparent texture.
            .layerEffect(shader(radius: radius, origin: origin), maxSampleOffset: CGSize(width: size.width, height: size.height * 2))
            .frame(width: size.width, height: size.height, alignment: .top)
            .clipped()
    }

    private func shader(radius: Double, origin: CGPoint) -> Shader {
        let lightX = min(max(
            tilt.x * Theme.Render.tiltMultiplier
                + (model.finger.alpha > Theme.Render.fingerLightThreshold ? (model.finger.x - size.width / 2) / size.width : 0),
            -1
        ), 1)
        let lightY = min(max(tilt.y * Theme.Render.tiltMultiplier, -1), 1)
        let layout = Theme.Layout.self
        return ShaderLibrary.glassDiscOnboardingLens(
            .float2(size),
            .float4(Float(model.discX), Float(model.discY - model.nudge), Float(radius), Float(reducedMotion ? 0 : model.stretch)),
            .float4(Float(reducedMotion ? 0 : model.glint), Float(model.glintAng), Float(lightX), Float(lightY)),
            .float4(Float(GlassDiscOnboardingMotion.Upgrade.glow), Float(reducedMotion ? 0 : model.spreadBoost), Float(model.reveal), Float(model.ring)),
            .float2(origin),
            .float4(Float(layout.lens.fold), Float(layout.lens.foldBack), Float(layout.lens.foldShape), Float(layout.lens.kY)),
            .float4(Float(layout.lens.kX), Float(layout.lens.squeezeX), Float(layout.lens.spread), Float(layout.lens.fringeFrom)),
            .float4(Float(layout.lens.fade), Float(layout.lens.mirrorFloor), Float(layout.ringFrom), Float(layout.ringTo)),
            .float4(Float(layout.rimLight), Float(layout.rimWidth), Float(layout.shadowAt), Float(layout.shadowWidth)),
            .float4(Float(layout.shadowAlpha), Float(layout.bead.from), Float(layout.bead.span), Float(layout.bead.frost)),
            .float2(Float(layout.domeFrost * model.introAlpha), 1),
            GlassDiscOnboardingColor.shader(Theme.Color.bgTL), GlassDiscOnboardingColor.shader(Theme.Color.bgTR),
            GlassDiscOnboardingColor.shader(Theme.Color.bgBL), GlassDiscOnboardingColor.shader(Theme.Color.bgBR),
            GlassDiscOnboardingColor.shader(Theme.Color.shadow), GlassDiscOnboardingColor.shader(Theme.Color.ringInner),
            GlassDiscOnboardingColor.shader(Theme.Color.ringCore), GlassDiscOnboardingColor.shader(Theme.Color.ringOuter),
            GlassDiscOnboardingColor.shader(Theme.Color.ringBlush)
        )
    }
}

/// Draws the offscreen, transparent 2H source for the lens. CoreText keeps baseline placement identical to RN/Skia.
private struct GlassDiscOnboardingTextureCanvas: View {
    let size: CGSize
    @Environment(\.displayScale) private var displayScale
    @State private var cache = GlassDiscOnboardingTextureCache()

    var body: some View {
        Canvas { context, _ in
            let image = cache.image(size: size, scale: displayScale)
            context.draw(Image(uiImage: image), in: CGRect(x: 0, y: 0, width: size.width, height: size.height * 2))
        }
    }
}

@Observable @MainActor
private final class GlassDiscOnboardingTextureCache {
    @ObservationIgnored private var cachedSize = CGSize.zero
    @ObservationIgnored private var cachedScale = 0.0
    @ObservationIgnored private var cachedImage: UIImage?

    func image(size: CGSize, scale: CGFloat) -> UIImage {
        if cachedSize == size, cachedScale == scale, let cachedImage { return cachedImage }
        let format = UIGraphicsImageRendererFormat()
        format.scale = min(scale, GlassDiscOnboardingTheme.Render.textureScaleMax)
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: size.width, height: size.height * 2), format: format).image { renderer in
            let context = renderer.cgContext
            let theme = GlassDiscOnboardingTheme.self
            GlassDiscOnboardingTextureCache.drawCentered(theme.Copy.headline, size: theme.Typography.headlineSize, weight: .regular, baseline: size.height * theme.Layout.headlineBaseline, color: UIColor(glassDiscOnboardingHex: theme.Color.lensInk), in: context, width: size.width)
            for (index, line) in theme.Copy.sub.enumerated() {
                GlassDiscOnboardingTextureCache.drawCentered(line, size: theme.Typography.headlineSize, weight: .regular, baseline: size.height * theme.Layout.headlineBaseline + theme.Typography.headlineLineHeight * Double(index + 1), color: UIColor(glassDiscOnboardingHex: theme.Color.lensInk), in: context, width: size.width)
            }
            let darkY = size.height - theme.Layout.buttonsBottom - theme.Layout.buttonHeightDark
            let lightY = darkY - theme.Layout.buttonGap - theme.Layout.buttonHeight
            let rect = CGRect(x: theme.Layout.gutter, y: lightY, width: size.width - theme.Layout.gutter * 2, height: theme.Layout.buttonHeight)
            context.setFillColor(UIColor(glassDiscOnboardingHex: theme.Color.buttonLight).cgColor)
            context.addPath(CGPath(roundedRect: rect, cornerWidth: theme.Layout.buttonHeight / 2, cornerHeight: theme.Layout.buttonHeight / 2, transform: nil))
            context.fillPath()
            let darkRect = CGRect(x: theme.Layout.gutter, y: darkY, width: size.width - theme.Layout.gutter * 2, height: theme.Layout.buttonHeightDark)
            context.addPath(CGPath(roundedRect: darkRect, cornerWidth: theme.Layout.buttonHeightDark / 2, cornerHeight: theme.Layout.buttonHeightDark / 2, transform: nil)); context.setFillColor(UIColor(glassDiscOnboardingHex: theme.Color.buttonDark).cgColor); context.fillPath()
            GlassDiscOnboardingTextureCache.drawCentered(theme.Copy.email, size: theme.Typography.buttonSize, weight: .medium, baseline: lightY + theme.Layout.buttonHeight / 2 + theme.Typography.buttonSize * theme.Render.buttonLabelBaseline, color: UIColor(glassDiscOnboardingHex: theme.Color.lensInk), in: context, width: size.width)
            GlassDiscOnboardingTextureCache.drawCentered(theme.Copy.phone, size: theme.Typography.buttonSize, weight: .medium, baseline: darkY + theme.Layout.buttonHeightDark / 2 + theme.Typography.buttonSize * theme.Render.buttonLabelBaseline, color: UIColor(glassDiscOnboardingHex: theme.Color.buttonDarkInk), in: context, width: size.width)
            GlassDiscOnboardingTextureCache.drawCentered(theme.Copy.lens, size: theme.Typography.lensSize, weight: .medium, baseline: size.height * theme.Render.lensHeadlineBaseline, color: UIColor(glassDiscOnboardingHex: theme.Color.lensInk), in: context, width: size.width)
        }
        cachedSize = size
        cachedScale = scale
        cachedImage = image
        return image
    }

    private static func drawCentered(_ string: String, size: Double, weight: UIFont.Weight, baseline: Double, color: UIColor, in context: CGContext, width: Double) {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        let ctFont: CTFont = font as CTFont
        let attributes: [NSAttributedString.Key: Any] = [.font: ctFont, .foregroundColor: color.cgColor]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: attributes))
        let bounds = CTLineGetBoundsWithOptions(line, .useOpticalBounds)
        context.saveGState()
        let height = context.boundingBoxOfClipPath.height
        context.translateBy(x: 0, y: height)
        context.scaleBy(x: 1, y: -1)
        context.textMatrix = .identity
        context.textPosition = CGPoint(x: (width - bounds.width) / 2 - bounds.minX, y: height - baseline)
        CTLineDraw(line, context)
        context.restoreGState()
    }
}

private struct GlassDiscOnboardingCloud: View {
    let size: CGSize
    let model: GlassDiscOnboardingModel
    let reducedMotion: Bool

    var body: some View {
        Canvas { context, _ in
            drawPulses(in: &context)
            drawStickers(in: &context)
            drawFinger(in: &context)
        }
    }

    private func drawPulses(in context: inout GraphicsContext) {
        guard !reducedMotion else { return }
        let since = model.clock - model.landAt - GlassDiscOnboardingMotion.Burst.delayMs
        for start in GlassDiscOnboardingMotion.Upgrade.pulses {
            let progress = (since - start) / GlassDiscOnboardingMotion.Upgrade.pulseMs
            guard progress > 0, progress < 1 else { continue }
            let eased = 1 - pow(1 - progress, 3)
            let radius = model.discR + GlassDiscOnboardingMotion.Upgrade.pulseGrow * eased
            let rect = CGRect(x: model.discX - radius, y: model.discY - radius, width: radius * 2, height: radius * 2)
            let render = GlassDiscOnboardingTheme.Render.self
            context.stroke(
                Path(ellipseIn: rect),
                with: .color(GlassDiscOnboardingColor.hex(GlassDiscOnboardingTheme.Color.pulse).opacity(render.pressureAlpha * (1 - progress))),
                lineWidth: render.pressureStrokeStart * (1 - progress) + render.pressureStrokeEnd
            )
        }
    }

    private func drawStickers(in context: inout GraphicsContext) {
        guard model.stickers.phase != .hidden else { return }
        let count = min(model.stickers.x.count, model.stickers.cell.count)
        for index in 0..<count {
            let radius = model.stickers.r[index] * model.stickers.scale[index]
            guard radius >= GlassDiscOnboardingTheme.Render.minimumBubbleRadius else { continue }
            let cell = model.stickers.cell[index]
            let column = cell % GlassDiscOnboardingTheme.Atlas.columns
            let row = (cell / GlassDiscOnboardingTheme.Atlas.columns) % GlassDiscOnboardingTheme.Atlas.rows
            guard let image = GlassDiscOnboardingAtlas.cell(column: column, row: row) else { continue }
            let speed = hypot(model.stickers.vx[index], model.stickers.vy[index])
            let stretch = reducedMotion ? 0 : min(GlassDiscOnboardingMotion.Upgrade.wobbleMax, speed * GlassDiscOnboardingMotion.Upgrade.wobblePerSpeed)
            let angle = atan2(model.stickers.vy[index], model.stickers.vx[index])
            var bubble = context
            bubble.translateBy(x: model.stickers.x[index], y: model.stickers.y[index])
            if stretch > GlassDiscOnboardingTheme.Render.minimumWobble {
                bubble.rotate(by: .radians(angle))
                bubble.scaleBy(x: 1 + stretch, y: 1 - stretch)
                bubble.rotate(by: .radians(-angle))
            }
            bubble.draw(Image(decorative: image, scale: 1), in: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2))
        }
    }

    private func drawFinger(in context: inout GraphicsContext) {
        guard model.finger.alpha > 0.01 else { return }
        let radius = GlassDiscOnboardingTheme.Layout.fingerRadius
        let rect = CGRect(x: model.finger.x - radius, y: model.finger.y - radius, width: radius * 2, height: radius * 2)
        let render = GlassDiscOnboardingTheme.Render.self
        context.fill(
            Path(ellipseIn: rect),
            with: .color(GlassDiscOnboardingColor.opaque(GlassDiscOnboardingTheme.Color.finger).opacity(model.finger.alpha * render.fingerFillAlpha))
        )
        context.stroke(
            Path(ellipseIn: rect),
            with: .color(GlassDiscOnboardingColor.opaque(GlassDiscOnboardingTheme.Color.fingerRing).opacity(model.finger.alpha * render.fingerStrokeAlpha)),
            lineWidth: render.fingerStrokeWidth
        )
    }
}

@Observable @MainActor
private final class GlassDiscOnboardingTiltReader {
    private(set) var current = CGPoint.zero
    private let manager = CMMotionManager()

    func start() {
        guard manager.isDeviceMotionAvailable, !manager.isDeviceMotionActive else { return }
        manager.deviceMotionUpdateInterval = GlassDiscOnboardingTheme.Render.tiltInterval
        manager.startDeviceMotionUpdates(to: .main) { [weak self] motion, _ in
            guard let attitude = motion?.attitude else { return }
            self?.current = CGPoint(x: attitude.roll, y: attitude.pitch)
        }
    }

    func stop() { manager.stopDeviceMotionUpdates() }
}

/// The atlas is decoded once and split into 24 source cells, so the frame loop only draws small images.
private enum GlassDiscOnboardingAtlas {
    private static let source: CGImage? = {
        guard let image = UIImage(named: "GlassDiscOnboarding-bubbles")?.cgImage else {
            assertionFailure("Missing GlassDiscOnboarding-bubbles atlas")
            return nil
        }
        return image
    }()

    private static let cells: [CGImage?] = (0..<(GlassDiscOnboardingTheme.Atlas.columns * GlassDiscOnboardingTheme.Atlas.rows)).map { index in
        guard let source else { return nil }
        let column = index % GlassDiscOnboardingTheme.Atlas.columns
        let row = index / GlassDiscOnboardingTheme.Atlas.columns
        let side = Int(GlassDiscOnboardingTheme.Atlas.cell)
        return source.cropping(to: CGRect(x: column * side, y: row * side, width: side, height: side))
    }

    static func cell(column: Int, row: Int) -> CGImage? {
        let index = row * GlassDiscOnboardingTheme.Atlas.columns + column
        return cells.indices.contains(index) ? cells[index] : nil
    }
}
