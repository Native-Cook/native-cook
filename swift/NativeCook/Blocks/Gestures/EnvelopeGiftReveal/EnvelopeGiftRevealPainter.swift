import SwiftUI

/// Draws the scene: the page, the envelope (back, folding flap, card inside, front pocket, seal), the gift card
/// on its way out and in place, and the confetti. Port of `EnvelopeScene.tsx`.
struct EnvelopeGiftRevealPainter {
    private typealias M = EnvelopeGiftRevealMetrics
    private typealias C = EnvelopeGiftRevealColors
    private typealias Open = EnvelopeGiftRevealOpen

    let width: Double
    let height: Double
    let lay: EnvelopeGiftRevealScreenLayout
    let pose: EnvelopeGiftRevealPose
    /// The confetti state: eight numbers per petal (x, y, vx, vy, angle, spin, life, colour).
    let confetti: [Double]

    private static let ew = M.envW
    private static let eh = M.envH
    private static let cw = M.cardW
    private static let ch = M.cardH
    private static let flapTipY = M.envH * M.flapTip
    /// The flap folds where its upper part ends; the lower part curls about this line.
    private static let fold = M.envH * M.flapTip * EnvelopeGiftRevealTokens.Scene.flapFoldShare
    private static let amountY = M.cardH / 2 - EnvelopeGiftRevealTokens.Scene.amountBaselineInset

    private static func color(_ value: (red: Double, green: Double, blue: Double, alpha: Double)) -> Color {
        Color.rgba(value.red, value.green, value.blue, value.alpha)
    }
    private static let numberFont = "HelveticaNeue-Bold"

    // MARK: Shapes

    /// The flap: a wide triangle with a rounded tip, flattened to a polygon so it can be projected.
    private static let flapPolygon: [CGPoint] = {
        let tip = flapTipY
        let shoulder = EnvelopeGiftRevealTokens.Scene.flapShoulder
        let tipInset = EnvelopeGiftRevealTokens.Scene.flapTipInset
        var points = [
            CGPoint(x: 0, y: 0), CGPoint(x: ew, y: 0),
            CGPoint(x: ew / 2 + shoulder, y: tip - tipInset)
        ]
        let start = CGPoint(x: ew / 2 + shoulder, y: tip - tipInset)
        let control = CGPoint(x: ew / 2, y: tip + EnvelopeGiftRevealTokens.Scene.flapTipControl)
        let end = CGPoint(x: ew / 2 - shoulder, y: tip - tipInset)
        let steps = EnvelopeGiftRevealTokens.Scene.flapCurveSteps
        for i in 1...steps {
            let t = Double(i) / Double(steps)
            let a = (1 - t) * (1 - t)
            let b = 2 * (1 - t) * t
            let c = t * t
            points.append(CGPoint(x: a * start.x + b * control.x + c * end.x, y: a * start.y + b * control.y + c * end.y))
        }
        return points
    }()

    /// Side panels run from the top corners down to the pocket's top corners (source frame 010).
    private static let leftPanel: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 0))
        p.addLine(to: CGPoint(x: ew * EnvelopeGiftRevealTokens.Scene.sideInnerX, y: eh * EnvelopeGiftRevealTokens.Scene.sideTopY))
        p.addLine(to: CGPoint(x: ew * EnvelopeGiftRevealTokens.Scene.sideInnerX, y: eh))
        p.addLine(to: CGPoint(x: 0, y: eh))
        p.closeSubpath()
        return p
    }()

    private static let rightPanel: Path = {
        var p = Path()
        p.move(to: CGPoint(x: ew, y: 0))
        p.addLine(to: CGPoint(x: ew * EnvelopeGiftRevealTokens.Scene.sideOuterX, y: eh * EnvelopeGiftRevealTokens.Scene.sideTopY))
        p.addLine(to: CGPoint(x: ew * EnvelopeGiftRevealTokens.Scene.sideOuterX, y: eh))
        p.addLine(to: CGPoint(x: ew, y: eh))
        p.closeSubpath()
        return p
    }()

    /// The front pocket: a trapezoid whose flat top sits at the apex (source frames 001 and 010).
    private static let pocket: Path = {
        let top = Open.pocketApex * eh
        var p = Path()
        p.move(to: CGPoint(x: 0, y: eh))
        p.addLine(to: CGPoint(x: ew * EnvelopeGiftRevealTokens.Scene.sideInnerX, y: top))
        p.addLine(to: CGPoint(x: ew * EnvelopeGiftRevealTokens.Scene.sideOuterX, y: top))
        p.addLine(to: CGPoint(x: ew, y: eh))
        p.closeSubpath()
        return p
    }()

    private static func polygon(_ points: [CGPoint]) -> Path {
        var p = Path()
        guard let first = points.first else { return p }
        p.move(to: first)
        for point in points.dropFirst() { p.addLine(to: point) }
        p.closeSubpath()
        return p
    }

    // MARK: Drawing

    func draw(_ context: GraphicsContext, art: GraphicsContext.ResolvedSymbol?) {
        let screen = Path(CGRect(x: 0, y: 0, width: width, height: height))
        context.fill(screen, with: .color(pose.page))
        if pose.glowOpacity > 0 {
            var glow = context
            glow.opacity = pose.glowOpacity
            let light = Gradient(colors: [
                Color.white.opacity(EnvelopeGiftRevealTokens.Scene.glowWhiteAlpha), Color.white.opacity(0)
            ])
            glow.fill(
                screen,
                with: .radialGradient(
                    light, center: pose.glowCentre, startRadius: 0,
                    endRadius: width * EnvelopeGiftRevealTokens.Scene.glowRadiusWidth
                )
            )
        }
        if pose.envOpacity > 0.001 {
            var env = context
            env.opacity = pose.envOpacity
            let place = { (layer: inout GraphicsContext) in
                layer.translateBy(x: (width - Self.ew) / 2, y: pose.envY)
                layer.scaleBy(x: pose.envScale, y: pose.envScale)
                drawEnvelope(layer, art: art)
            }
            if pose.envOpacity >= 0.999 {
                place(&env)
            } else {
                env.drawLayer { layer in place(&layer) }
            }
        }
        if pose.cardFrontOpacity > 0.001 {
            var card = context
            card.opacity = pose.cardFrontOpacity
            card.translateBy(x: width / 2, y: pose.cardY)
            card.rotate(by: .degrees(pose.cardTilt))
            card.scaleBy(x: pose.cardScale, y: pose.cardScale)
            drawCard(card, art: art)
        }
        drawConfetti(context)
    }

    private func drawEnvelope(_ env: GraphicsContext, art: GraphicsContext.ResolvedSymbol?) {
        let ew = Self.ew
        let eh = Self.eh
        let body = Path(
            roundedRect: CGRect(x: 0, y: 0, width: ew, height: eh),
            cornerRadius: EnvelopeGiftRevealTokens.Scene.envelopeCorner
        )

        var shadow = env
        shadow.addFilter(.blur(radius: EnvelopeGiftRevealTokens.Scene.envelopeShadowBlur))
        shadow.fill(
            Path(
                roundedRect: CGRect(
                    x: EnvelopeGiftRevealTokens.Scene.envelopeShadowX,
                    y: EnvelopeGiftRevealTokens.Scene.envelopeShadowY,
                    width: ew - EnvelopeGiftRevealTokens.Scene.envelopeShadowWidthInset,
                    height: eh - EnvelopeGiftRevealTokens.Scene.envelopeShadowHeightInset
                ),
                cornerRadius: EnvelopeGiftRevealTokens.Scene.envelopeShadowCorner
            ),
            with: .color(C.shadow)
        )
        env.fill(body, with: .linearGradient(
            Gradient(colors: [C.sideHigh, C.bottomLow]), startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: eh)
        ))

        let flapMap = flapHomography()
        if pose.flapBack > 0.001 {
            var back = env
            back.opacity = pose.flapBack
            back.fill(Self.polygon(Self.flapPolygon.map(flapMap.apply)), with: .linearGradient(
                Gradient(colors: [C.limeInside, C.limeLow]), startPoint: flapMap.apply(0, 0), endPoint: flapMap.apply(0, Self.flapTipY)
            ))
        }

        if pose.cardInsideOpacity > 0.001 {
            var inside = env
            inside.opacity = pose.cardInsideOpacity
            inside.translateBy(x: ew / 2, y: pose.cardY - lay.envTop)
            inside.rotate(by: .degrees(pose.cardTilt))
            inside.scaleBy(x: pose.cardScale, y: pose.cardScale)
            drawCard(inside, art: art)
        }

        drawPocket(env, body: body)

        if pose.flapFront > 0.001 { drawFrontFlap(env, flapMap: flapMap) }
    }

    /// The side panels and the front pocket, clipped to the envelope's outline.
    private func drawPocket(_ env: GraphicsContext, body: Path) {
        let ew = Self.ew
        let eh = Self.eh
        var inner = env
        inner.clip(to: body)
        inner.fill(Self.leftPanel, with: .linearGradient(
            Gradient(colors: [C.sideLow, C.sideHigh]), startPoint: CGPoint(x: 0, y: eh),
            endPoint: CGPoint(
                x: ew * EnvelopeGiftRevealTokens.Scene.leftGradientX,
                y: eh * EnvelopeGiftRevealTokens.Scene.leftGradientY
            )
        ))
        inner.fill(Self.rightPanel, with: .color(C.sideRight))
        inner.fill(Self.pocket, with: .linearGradient(
            Gradient(colors: [C.bottomLow, C.bottomHigh]), startPoint: CGPoint(x: 0, y: eh), endPoint: CGPoint(x: 0, y: 0)
        ))
        // Key light from the top left catches the pocket's upper-left area, as in the source.
        let pocketLight = EnvelopeGiftRevealTokens.Scene.pocketLight
        let keyLight = Gradient(colors: [Self.color(pocketLight), Self.color((
            red: pocketLight.red, green: pocketLight.green, blue: pocketLight.blue, alpha: 0
        ))])
        inner.fill(
            Self.pocket,
            with: .radialGradient(
                keyLight,
                center: CGPoint(
                    x: ew * EnvelopeGiftRevealTokens.Scene.pocketLightX,
                    y: eh * EnvelopeGiftRevealTokens.Scene.pocketLightY
                ),
                startRadius: 0,
                endRadius: ew * EnvelopeGiftRevealTokens.Scene.pocketLightRadius
            )
        )
        // Soft creases where the paper folds, not drawn outlines.
        var crease = inner
        crease.addFilter(.blur(radius: EnvelopeGiftRevealTokens.Scene.creaseBlur))
        crease.stroke(Self.pocket, with: .color(C.crease), lineWidth: EnvelopeGiftRevealTokens.Scene.creaseWidth)
    }

    // MARK: Flap

    /// `[perspective 900, rotateX -angle]`, as the 3x3 map Skia keeps for the group.
    private func flapHomography() -> EnvelopeGiftRevealHomography {
        let radians = -pose.angle * Double.pi / 180
        return (
            EnvelopeGiftRevealMatrix4.perspective(EnvelopeGiftRevealTokens.Scene.flapPerspective)
                * EnvelopeGiftRevealMatrix4.rotationX(radians)
        ).homography
    }

    /// The lower part of the flap lags the fold: `[translateY fold, perspective 700, rotateX lag, translateY -fold]`.
    private func tipHomography() -> EnvelopeGiftRevealHomography {
        let radians = pose.curlDegrees * Double.pi / 180
        let m = EnvelopeGiftRevealMatrix4.translation(y: Self.fold)
            * EnvelopeGiftRevealMatrix4.perspective(EnvelopeGiftRevealTokens.Scene.curlPerspective)
            * EnvelopeGiftRevealMatrix4.rotationX(radians)
            * EnvelopeGiftRevealMatrix4.translation(y: -Self.fold)
        return m.homography
    }

    private func drawFrontFlap(_ env: GraphicsContext, flapMap: EnvelopeGiftRevealHomography) {
        var front = env
        front.opacity = pose.flapFront

        var shade = front
        shade.addFilter(.blur(radius: EnvelopeGiftRevealTokens.Scene.flapShadowBlur))
        let lowered = EnvelopeGiftRevealPolygon.shifted(
            Self.flapPolygon, dy: EnvelopeGiftRevealTokens.Scene.flapShadowY
        )
        shade.fill(
            Self.polygon(lowered.map(flapMap.apply)),
            with: .color(Self.color(EnvelopeGiftRevealTokens.Scene.flapShadow))
        )

        // The upper piece runs a hair past the fold so the two pieces' anti-aliased edges leave no seam.
        let upper = EnvelopeGiftRevealPolygon.clip(Self.flapPolygon, minY: 0, maxY: Self.fold + 0.75)
        paintPaper(front, local: upper, through: flapMap)

        let tipMap = EnvelopeGiftRevealHomography.nested(outer: flapMap, inner: tipHomography())
        let lower = EnvelopeGiftRevealPolygon.clip(Self.flapPolygon, minY: Self.fold, maxY: Self.fold + Self.eh)
        paintPaper(front, local: lower, through: tipMap)

        drawSeal(front, through: tipMap)
    }

    /// One piece of the flap's paper: its gradient, and the soft light on its upper left.
    private func paintPaper(_ ctx: GraphicsContext, local: [CGPoint], through map: EnvelopeGiftRevealHomography) {
        guard local.count >= 3 else { return }
        let projected = local.map(map.apply)
        let outline = Self.polygon(projected)
        ctx.fill(outline, with: .linearGradient(
            Gradient(colors: [C.flapHigh, C.flapLow]), startPoint: map.apply(0, 0), endPoint: map.apply(Self.ew, Self.flapTipY)
        ))
        // The highlight is a circle in the flap's own plane: draw it through the map's slope at its centre.
        let centre = CGPoint(
            x: Self.ew * EnvelopeGiftRevealTokens.Scene.flapLightX,
            y: Self.eh * EnvelopeGiftRevealTokens.Scene.flapLightY
        )
        let slope = map.linearisation(at: centre)
        guard abs(slope.a * slope.d - slope.b * slope.c) > 1e-4 else { return }
        let back = slope.inverted()
        var light = ctx
        light.concatenate(slope)
        let flapLight = EnvelopeGiftRevealTokens.Scene.flapLight
        let glow = Gradient(colors: [Self.color(flapLight), Self.color((
            red: flapLight.red, green: flapLight.green, blue: flapLight.blue, alpha: 0
        ))])
        light.fill(Self.polygon(projected.map { $0.applying(back) }), with: .radialGradient(
            glow, center: .zero, startRadius: 0,
            endRadius: Self.ew * EnvelopeGiftRevealTokens.Scene.flapLightRadius
        ))
    }

    // MARK: Seal

    /// The wax seal: as the flap starts to lift it swells, then cracks into two halves along the flap's line.
    private func drawSeal(_ ctx: GraphicsContext, through map: EnvelopeGiftRevealHomography) {
        let r = M.sealD / 2
        let split = pose.split
        let halves = 1 - EnvelopeGiftRevealEase.clamp01(
            (split - EnvelopeGiftRevealTokens.Scene.sealHalfFadeStart)
                / EnvelopeGiftRevealTokens.Scene.sealHalfFadeSpan
        )
        guard halves > 0.001 else { return }

        // Drawn flat on the flap: the seal sits where the flap is still nearly square to the viewer.
        var seal = ctx
        seal.concatenate(map.linearisation(at: CGPoint(x: Self.ew / 2, y: Self.eh * M.sealY)))

        var shade = seal
        shade.opacity = halves
        shade.addFilter(.blur(radius: EnvelopeGiftRevealTokens.Scene.sealShadowBlur))
        shade.fill(
            Path(ellipseIn: CGRect(
                x: -r,
                y: EnvelopeGiftRevealTokens.Scene.sealShadowY - r,
                width: r * 2,
                height: r * 2
            )),
            with: .color(Self.color(EnvelopeGiftRevealTokens.Scene.sealShadow))
        )

        let pop = 1 + EnvelopeGiftRevealTokens.Scene.sealPop * sin(
            Double.pi * EnvelopeGiftRevealEase.clamp01(split * EnvelopeGiftRevealTokens.Scene.sealPopRate)
        )
        var pair = seal
        pair.scaleBy(x: pop, y: pop)
        pair.opacity = halves
        let face = sealFace(radius: r)
        pair.drawLayer { layer in
            var left = layer
            left.translateBy(
                x: -EnvelopeGiftRevealTokens.Scene.sealSplitX * split,
                y: EnvelopeGiftRevealTokens.Scene.sealLeftY * split * split
            )
            left.rotate(by: .radians(EnvelopeGiftRevealTokens.Scene.sealLeftRotation * split))
            left.clip(to: Path(CGRect(x: -r - 2, y: -r - 2, width: r + 2.5, height: r * 2 + 4)))
            face(left)

            var right = layer
            right.translateBy(
                x: EnvelopeGiftRevealTokens.Scene.sealSplitX * split,
                y: EnvelopeGiftRevealTokens.Scene.sealRightY * split * split
            )
            right.rotate(by: .radians(EnvelopeGiftRevealTokens.Scene.sealRightRotation * split))
            right.clip(to: Path(CGRect(x: 0, y: -r - 2, width: r + 2, height: r * 2 + 4)))
            face(right)
        }
    }

    private func sealFace(radius r: Double) -> (GraphicsContext) -> Void {
        let mark = EnvelopeGiftRevealGlyphs.run(
            EnvelopeGiftRevealCopy.sealText,
            fontName: Self.numberFont,
            size: EnvelopeGiftRevealTokens.Scene.sealFontSize
        )
        return { ctx in
            ctx.fill(Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2)), with: .color(C.ink))
            var text = ctx
            text.translateBy(
                x: -mark.width / 2 - EnvelopeGiftRevealTokens.Scene.sealTextXCorrection,
                y: EnvelopeGiftRevealTokens.Scene.sealBaseline
            )
            text.fill(mark.path, with: .color(C.sealMark))
        }
    }

    // MARK: Gift card

    /// The card, drawn about its own centre.
    private func drawCard(_ ctx: GraphicsContext, art: GraphicsContext.ResolvedSymbol?) {
        let cw = Self.cw
        let ch = Self.ch
        let face = CGRect(x: -cw / 2, y: -ch / 2, width: cw, height: ch)

        var shadow = ctx
        shadow.addFilter(.blur(radius: EnvelopeGiftRevealTokens.Scene.cardShadowBlur))
        shadow.fill(
            Path(
                roundedRect: CGRect(
                    x: -cw / 2 + EnvelopeGiftRevealTokens.Scene.cardShadowX,
                    y: -ch / 2 + EnvelopeGiftRevealTokens.Scene.cardShadowY,
                    width: cw - EnvelopeGiftRevealTokens.Scene.cardShadowWidthInset,
                    height: ch - EnvelopeGiftRevealTokens.Scene.cardShadowHeightInset
                ),
                cornerRadius: M.cardR
            ),
            with: .color(Self.color(EnvelopeGiftRevealTokens.Scene.cardShadow))
        )

        var card = ctx
        card.clip(to: Path(roundedRect: face, cornerRadius: M.cardR))
        card.fill(Path(face), with: .color(C.cardPink))
        if let art { card.draw(art, in: face) }
        card.fill(Path(face), with: .linearGradient(
            Gradient(colors: [
                Color.white.opacity(EnvelopeGiftRevealTokens.Scene.cardHighlightAlpha),
                Color.white.opacity(0), Color.white.opacity(0)
            ]),
            startPoint: CGPoint(x: -cw / 2, y: -ch / 2), endPoint: CGPoint(x: cw / 2, y: ch / 2)
        ))
        // A band of light crosses the glossy art: on arrival, then it rests where device tilt puts it.
        var band = card
        band.translateBy(
            x: (pose.sweep - 0.5) * cw * EnvelopeGiftRevealTokens.Scene.sweepTranslation,
            y: 0
        )
        band.blendMode = .softLight
        band.fill(Path(CGRect(
            x: cw * EnvelopeGiftRevealTokens.Scene.sweepRectX,
            y: -ch / 2,
            width: cw * EnvelopeGiftRevealTokens.Scene.sweepRectWidth,
            height: ch
        )), with: .linearGradient(
            Gradient(colors: [
                Color.white.opacity(0),
                Color.white.opacity(EnvelopeGiftRevealTokens.Scene.sweepAlpha),
                Color.white.opacity(0)
            ]),
            startPoint: CGPoint(x: cw * EnvelopeGiftRevealTokens.Scene.sweepGradientStartX, y: -ch / 2),
            endPoint: CGPoint(x: cw * EnvelopeGiftRevealTokens.Scene.sweepGradientEndX, y: ch / 2)
        ))

        let price = EnvelopeGiftRevealGlyphs.run(
            pose.amount,
            fontName: Self.numberFont,
            size: EnvelopeGiftRevealTokens.Scene.amountFontSize
        )
        var text = ctx
        text.translateBy(
            x: -cw / 2 + EnvelopeGiftRevealTokens.Scene.amountSideInset,
            y: Self.amountY
        )
        text.fill(price.path, with: .color(.white))
    }

    // MARK: Confetti

    /// Petals: a lens shape whose width follows its spin, so each one flutters as it falls.
    private func drawConfetti(_ ctx: GraphicsContext) {
        for which in 0..<C.confetti.count {
            var petals = Path()
            for o in stride(from: 0, to: confetti.count, by: 8) {
                if confetti[o + 6] <= 0 || Int(confetti[o + 7]) != which { continue }
                let a = confetti[o + 4]
                let f = min(1, confetti[o + 6] / EnvelopeGiftRevealTokens.Scene.petalFadeLife)
                let hw = EnvelopeGiftRevealTokens.Scene.petalHalfWidth * f
                let minimumSquash = EnvelopeGiftRevealTokens.Scene.petalMinimumSquash
                let hh = EnvelopeGiftRevealTokens.Scene.petalHalfHeight * f
                    * (minimumSquash + (1 - minimumSquash) * abs(
                        cos(a * EnvelopeGiftRevealTokens.Scene.petalSpinShape)
                    ))
                let ca = cos(a)
                let sa = sin(a)
                let x = confetti[o]
                let y = confetti[o + 1]
                petals.move(to: CGPoint(x: x - ca * hw, y: y - sa * hw))
                petals.addQuadCurve(to: CGPoint(x: x + ca * hw, y: y + sa * hw), control: CGPoint(x: x - sa * hh * 2, y: y + ca * hh * 2))
                petals.addQuadCurve(to: CGPoint(x: x - ca * hw, y: y - sa * hw), control: CGPoint(x: x + sa * hh * 2, y: y - ca * hh * 2))
                petals.closeSubpath()
            }
            if !petals.isEmpty { ctx.fill(petals, with: .color(C.confetti[which])) }
        }
    }
}

/// The canvas: the painter's drawing, with the card art as a symbol so it is decoded and sized once.
struct EnvelopeGiftRevealScene: View {
    let painter: EnvelopeGiftRevealPainter

    var body: some View {
        Canvas { context, _ in
            painter.draw(context, art: context.resolveSymbol(id: 0))
        } symbols: {
            Image(
                decorative: EnvelopeGiftRevealAssets.cardArt,
                scale: CGFloat(EnvelopeGiftRevealTokens.Assets.pixelsPerPoint)
            )
                .resizable()
                .interpolation(.high)
                .frame(width: EnvelopeGiftRevealMetrics.cardW, height: EnvelopeGiftRevealMetrics.cardH)
                .tag(0)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
