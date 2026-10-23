import CoreText
import UIKit

@MainActor
final class CityEventHeroRenderView: UIView, UIGestureRecognizerDelegate {
    enum Pass { case content, backdrop, glows, fireworks }
    private let pass: Pass
    weak var controller: CityEventHeroController?
    private var motionFrame = CityEventHeroMotion.Frame()
    var reduceMotion = false
    private var layoutSafeTop = 0.0

    private var touchStart = CGPoint.zero
    private var touchHit = CityEventHeroController.Hit.none
    private var touchTime = 0.0
    private var lastTouchPoint = CGPoint.zero
    private var lastTouchTime = 0.0
    private var skyVelocity = 0.0
    private var skyVelocityX = 0.0
    private var skyVelocityY = 0.0
    private var skyDragging = false
    private var tapCancelled = false
    private var actionElements: [CityEventHeroActionElement] = []
    private var textElements: [UIAccessibilityElement] = []
    private var accessibilitySignature = ""
    private var staticOverlay: UIImage?
    private var staticOverlaySize = CGSize.zero
    private var staticOverlaySafeTop = -1.0

    init(pass: Pass = .content) {
        self.pass = pass
        super.init(frame: .zero)
        isOpaque = false
        backgroundColor = .clear
        contentMode = .redraw
        isAccessibilityElement = false
        guard pass == .content else {
            isUserInteractionEnabled = false
            accessibilityElementsHidden = true
            return
        }
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        actionElements = (0..<4).map { _ in CityEventHeroActionElement(accessibilityContainer: self) }
        textElements = (0..<8).map { _ in UIAccessibilityElement(accessibilityContainer: self) }
        accessibilityElements = [textElements[0], actionElements[0], actionElements[1],
            textElements[1], textElements[2], textElements[3], textElements[4], textElements[5],
            actionElements[2], actionElements[3], textElements[6], textElements[7]]
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        if bounds.size != staticOverlaySize { staticOverlay = nil }
        updateAccessibility()
    }

    func update(frame: CityEventHeroMotion.Frame, controller: CityEventHeroController, reduceMotion: Bool, safeTop: Double) {
        motionFrame = frame
        self.controller = controller
        self.reduceMotion = reduceMotion
        if abs(layoutSafeTop - safeTop) > 0.25 {
            layoutSafeTop = safeTop
            staticOverlay = nil
        }
        updateAccessibility()
        if pass == .content || pass == .fireworks || staticOverlaySafeTop != safeTop {
            setNeedsDisplay()
            if pass != .content { staticOverlaySafeTop = safeTop }
        }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
        guard let controller else { return }
        let point = recognizer.location(in: self)
        let now = Date.timeIntervalSinceReferenceDate
        switch recognizer.state {
        case .began:
            touchStart = point
            touchTime = now
            touchHit = hit(at: point)
            lastTouchPoint = point
            lastTouchTime = now
            skyVelocity = 0
            skyVelocityX = 0
            skyVelocityY = 0
            skyDragging = false
            tapCancelled = false
            controller.begin(touchHit, at: now)
        case .changed:
            let dx = point.x - touchStart.x
            let dy = point.y - touchStart.y
            if hypot(dx, dy) >= 10 { tapCancelled = true }
            guard touchHit == .sky else { break }
            if !skyDragging, hypot(dx, dy) >= 10 {
                skyDragging = true
                controller.beginSkyDrag(at: now)
            }
            if skyDragging {
                let elapsed = max(0.001, now - lastTouchTime)
                let velocityX = (point.x - lastTouchPoint.x) / elapsed
                let velocityY = (point.y - lastTouchPoint.y) / elapsed
                skyVelocityX = velocityX
                skyVelocityY = velocityY
                let metrics = Metrics(bounds: bounds, safeTop: layoutSafeTop)
                let cardWidth = CityEventHeroTheme.Layout.card.width * metrics.unit
                let cardHeight = metrics.y(CityEventHeroTheme.Layout.card.y + CityEventHeroTheme.Layout.card.height)
                    - metrics.y(CityEventHeroTheme.Layout.card.y)
                skyVelocity = -velocityX / (CityEventHeroMotion.Night.scrubX * cardWidth)
                    + velocityY / (CityEventHeroMotion.Night.scrub * cardHeight)
                controller.scrubSky(
                    translationX: dx,
                    translationY: dy,
                    cardWidth: cardWidth,
                    cardHeight: cardHeight,
                    at: now
                )
            }
            lastTouchPoint = point
            lastTouchTime = now
        case .ended, .cancelled, .failed:
            let distance = hypot(point.x - touchStart.x, point.y - touchStart.y)
            let sameTarget = hit(at: point) == touchHit
            let succeeded = recognizer.state == .ended && !tapCancelled && distance < 10 && sameTarget
            if touchHit == .sky, skyDragging, recognizer.state == .ended {
                controller.endSkyDrag(
                    velocity: skyVelocity,
                    velocityX: skyVelocityX,
                    velocityY: skyVelocityY,
                    at: now
                )
            } else {
                controller.end(touchHit, guestSlot: guest(at: point), succeeded: succeeded, at: now)
            }
        default: break
        }
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let metrics = Metrics(bounds: bounds, safeTop: layoutSafeTop)
        if pass != .content {
            drawBackgroundPass(in: context, metrics: metrics)
            return
        }
        if usesStaticOverlay {
            staticOverlayImage(metrics: metrics).draw(in: bounds)
            drawGuests(in: context, metrics: metrics, drawBase: false)
            drawEventText(in: context, metrics: metrics, drawBase: false)
            return
        }
        drawCard(in: context, metrics: metrics)
        drawTopBar(in: context, metrics: metrics)
        drawGuests(in: context, metrics: metrics)
        drawEventText(in: context, metrics: metrics)
        drawPresented(in: context, metrics: metrics)
        drawActions(in: context, metrics: metrics)
        drawTabs(in: context, metrics: metrics)
    }

    private var usesStaticOverlay: Bool {
        let epsilon = 0.0001
        return uiLight().amount < epsilon
            && motionFrame.fan.allSatisfy { abs($0) < epsilon }
            && motionFrame.spot.allSatisfy { abs($0) < epsilon }
            && motionFrame.nameFrom == CityEventHeroMotion.restName
            && motionFrame.nameTo == CityEventHeroMotion.restName
            && abs(motionFrame.nameProgress) < epsilon
            && abs(motionFrame.like) < epsilon
            && abs(motionFrame.pop - 1) < epsilon
            && abs(motionFrame.ring) < epsilon
            && abs(motionFrame.count) < epsilon
            && abs(motionFrame.press) < epsilon
            && abs(motionFrame.chevron) < epsilon
            && abs(motionFrame.sweep) < epsilon
            && abs(motionFrame.guestFade - 1) < epsilon
    }

    private func staticOverlayImage(metrics: Metrics) -> UIImage {
        if let staticOverlay,
           staticOverlaySize == bounds.size,
           abs(staticOverlaySafeTop - metrics.safeTop) < 0.25 { return staticOverlay }

        let format = UIGraphicsImageRendererFormat()
        format.scale = contentScaleFactor
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: bounds.size, format: format).image { renderer in
            let context = renderer.cgContext
            drawStaticCard(in: context, metrics: metrics)
            drawTopBar(in: context, metrics: metrics)
            drawGuests(in: context, metrics: metrics, includeFireGlow: false)
            drawEventText(in: context, metrics: metrics, includeFireGlow: false)
            drawPresented(in: context, metrics: metrics)
            drawActions(in: context, metrics: metrics)
            drawTabs(in: context, metrics: metrics)
        }
        staticOverlay = image
        staticOverlaySize = bounds.size
        staticOverlaySafeTop = metrics.safeTop
        return image
    }

    /// Separate passes preserve Skia's blend order: sparks add to the photo, then the
    /// scrims cover them, and the static glow adds to that composite before the UI.
    private func drawBackgroundPass(in context: CGContext, metrics: Metrics) {
        let layout = CityEventHeroTheme.Layout.self
        let cardTop = metrics.y(layout.card.y)
        let cardBottom = metrics.y(layout.card.y + layout.card.height)
        let card = CGRect(x: 0, y: cardTop, width: bounds.width, height: cardBottom - cardTop)
        context.saveGState()
        context.addPath(UIBezierPath(roundedRect: card, cornerRadius: CityEventHeroTheme.Radius.card * metrics.unit).cgPath)
        context.clip()
        switch pass {
        case .backdrop:
            drawTopScrim(in: context, metrics: metrics, cardTop: cardTop)
            drawBottomBackdrop(in: context, metrics: metrics)
        case .glows: drawGlows(in: context, metrics: metrics)
        case .fireworks: drawFireworks(in: context, metrics: metrics, card: card)
        case .content: break
        }
        context.restoreGState()
    }

    private func drawStaticCard(in context: CGContext, metrics: Metrics) {
        drawWordmark(in: context, metrics: metrics)
    }

    private func drawCard(in context: CGContext, metrics: Metrics) {
        drawWordmark(in: context, metrics: metrics)
    }

    private func drawFireworks(in context: CGContext, metrics: Metrics, card: CGRect) {
        let fire = CityEventHeroMotion.fire(of: motionFrame.show)
        guard fire >= 0, fire < CityEventHeroMotion.Fire.end else { return }
        let fireOpacity = min(1, motionFrame.night / CityEventHeroMotion.Night.fireFade)
        guard fireOpacity > 0.0001 else { return }

        let photoSize = CityEventHeroAssets.city.size
        let cover = max(card.width / photoSize.width, card.height / photoSize.height)
        let photoWidth = photoSize.width * cover
        let photoHeight = photoSize.height * cover
        let photoLeft = (card.width - photoWidth) / 2
        let photoTop = card.minY - (photoHeight - card.height) * CityEventHeroTheme.Layout.photoShift
        let cameraX = motionFrame.camX * CityEventHeroMotion.Space.ampX
        let cameraY = motionFrame.camY * CityEventHeroMotion.Space.ampY
        let zoom = motionFrame.camZ * CityEventHeroMotion.Space.dolly
            + abs(cameraX) * CityEventHeroMotion.Space.coverX
            + abs(cameraY) * CityEventHeroMotion.Space.coverY
        let towerScale = 1 + zoom * CityEventHeroMotion.Space.tower
        let focus = CGPoint(x: CityEventHeroMotion.Space.focusX, y: CityEventHeroMotion.Space.focusY)
        let tipPhoto = CGPoint(
            x: focus.x + (CityEventHeroMotion.Tower.tipX - focus.x) * towerScale
                + cameraX * CityEventHeroMotion.Space.tower,
            y: focus.y + (CityEventHeroMotion.Tower.tipY - focus.y) * towerScale
                + cameraY * CityEventHeroMotion.Space.tower
        )
        let tip = CGPoint(x: photoLeft + tipPhoto.x * cover, y: photoTop + tipPhoto.y * cover)

        context.saveGState()
        context.translateBy(x: tip.x, y: tip.y)
        context.scaleBy(x: metrics.unit * towerScale, y: metrics.unit * towerScale)
        context.setBlendMode(.plusLighter)

        for (burstIndex, burst) in CityEventHeroMotion.Fire.bursts.enumerated() {
            let burstTime = fire - burst.at
            guard burstTime > 0 else { continue }
            let color = CityEventHeroMotion.burstColor(burst, fire: fire)
            for bucket in 0..<3 {
                let core = CGMutablePath()
                let glow = CGMutablePath()
                for spark in 0..<burst.count {
                    let life = burst.life * (
                        CityEventHeroMotion.Spark.lifeBase
                            + CityEventHeroMotion.Spark.lifeSpread
                            * CityEventHeroMotion.hash3(burst: burstIndex, spark: spark, channel: 1)
                    )
                    guard burstTime < life else { continue }
                    let remaining = 1 - burstTime / life
                    let actualBucket = remaining > 0.5 ? 0 : remaining > burst.crackle ? 1 : 2
                    guard actualBucket == bucket else { continue }
                    if bucket == 2,
                       CityEventHeroMotion.hash3(
                           burst: burstIndex,
                           spark: spark,
                           channel: 10 + Int(floor(burstTime * CityEventHeroMotion.Spark.flicker))
                       ) > CityEventHeroMotion.Spark.flickerKeep { continue }

                    let launch = CityEventHeroMotion.sparkLaunch(burst: burstIndex, spark: spark)
                    let tail = CityEventHeroMotion.sparkOffset(
                        speed: launch.speed,
                        angle: launch.angle,
                        time: max(0, burstTime - burst.trail),
                        drag: burst.drag,
                        gravity: burst.gravity
                    )
                    let coreTail = CityEventHeroMotion.sparkOffset(
                        speed: launch.speed,
                        angle: launch.angle,
                        time: max(0, burstTime - burst.trail * CityEventHeroMotion.Spark.coreShare),
                        drag: burst.drag,
                        gravity: burst.gravity
                    )
                    let head = CityEventHeroMotion.sparkOffset(
                        speed: launch.speed,
                        angle: launch.angle,
                        time: burstTime,
                        drag: burst.drag,
                        gravity: burst.gravity
                    )
                    let origin = CGPoint(
                        x: burst.dx + cos(launch.angle) * CityEventHeroMotion.Spark.breakRadius,
                        y: burst.dy + sin(launch.angle) * CityEventHeroMotion.Spark.breakRadius
                    )
                    core.move(to: CGPoint(x: origin.x + coreTail.x, y: origin.y + coreTail.y))
                    core.addLine(to: CGPoint(x: origin.x + head.x, y: origin.y + head.y))
                    glow.move(to: CGPoint(x: origin.x + tail.x, y: origin.y + tail.y))
                    glow.addLine(to: CGPoint(x: origin.x + head.x, y: origin.y + head.y))
                }
                let glowIn = CityEventHeroMotion.clamp(
                    (burstTime - CityEventHeroMotion.Spark.glowDelay) / CityEventHeroMotion.Spark.glowIn
                )
                strokeFirework(
                    glow,
                    color: color,
                    width: CityEventHeroMotion.Spark.glowWidth[bucket],
                    alpha: CityEventHeroMotion.Spark.glowAlpha[bucket] * glowIn * fireOpacity,
                    blur: CityEventHeroMotion.Spark.glowBlur,
                    context: context
                )
                let coreIn = CityEventHeroMotion.clamp(burstTime / CityEventHeroMotion.Spark.coreIn)
                strokeFirework(
                    core,
                    color: color,
                    width: CityEventHeroMotion.Spark.coreWidth[bucket],
                    alpha: CityEventHeroMotion.Spark.coreAlpha[bucket] * coreIn * fireOpacity,
                    blur: 0,
                    context: context
                )
            }
        }

        let shellCore = CGMutablePath()
        let shellGlow = CGMutablePath()
        for burst in CityEventHeroMotion.Fire.bursts where burst.launchDX != 0 || burst.launchDY != 0 {
            let shellTime = fire - (burst.at - CityEventHeroMotion.Fire.rise)
            guard shellTime > 0, shellTime < CityEventHeroMotion.Fire.rise else { continue }
            let headShare = CityEventHeroMotion.shellLeft(at: shellTime)
            let coreShare = CityEventHeroMotion.shellLeft(at: max(0, shellTime - CityEventHeroMotion.Spark.shellCore))
            let trailShare = CityEventHeroMotion.shellLeft(at: max(0, shellTime - CityEventHeroMotion.Spark.shellTrail))
            let head = CGPoint(x: burst.dx + burst.launchDX * headShare, y: burst.dy + burst.launchDY * headShare)
            shellCore.move(to: CGPoint(x: burst.dx + burst.launchDX * coreShare, y: burst.dy + burst.launchDY * coreShare))
            shellCore.addLine(to: head)
            shellGlow.move(to: CGPoint(x: burst.dx + burst.launchDX * trailShare, y: burst.dy + burst.launchDY * trailShare))
            shellGlow.addLine(to: head)
        }
        let shellColor = CityEventHeroMotion.Fire.bursts[0].hot
        strokeFirework(shellGlow, color: shellColor, width: CityEventHeroMotion.Spark.glowWidth[0], alpha: CityEventHeroMotion.Spark.glowAlpha[0] * fireOpacity, blur: CityEventHeroMotion.Spark.glowBlur, context: context)
        strokeFirework(shellCore, color: shellColor, width: CityEventHeroMotion.Spark.shellWidth, alpha: fireOpacity, blur: 0, context: context)
        context.restoreGState()
    }

    private func strokeFirework(_ path: CGPath, color: SIMD3<Double>, width: Double, alpha: Double, blur: Double, context: CGContext) {
        guard !path.isEmpty, alpha > 0.0001 else { return }
        let uiColor = UIColor(red: color.x, green: color.y, blue: color.z, alpha: alpha)
        context.saveGState()
        context.addPath(path)
        context.setStrokeColor(uiColor.cgColor)
        context.setLineWidth(width)
        context.setLineCap(.round)
        if blur > 0 { context.setShadow(offset: .zero, blur: blur, color: uiColor.cgColor) }
        context.strokePath()
        context.restoreGState()
    }

    private func drawTopScrim(in context: CGContext, metrics: Metrics, cardTop: Double) {
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        let gradient = CGGradient(
            colorsSpace: colorSpace,
            colors: [CityEventHeroTheme.Color.scrimTop.cgColor, UIColor(red: 4 / 255, green: 9 / 255, blue: 11 / 255, alpha: 0).cgColor] as CFArray,
            locations: [0, 1]
        )!
        context.drawLinearGradient(
            gradient,
            start: CGPoint(x: 0, y: cardTop),
            end: CGPoint(x: 0, y: metrics.y(CityEventHeroTheme.Backdrop.topFadeTo)),
            options: []
        )
    }

    private func drawBottomBackdrop(in context: CGContext, metrics: Metrics) {
        let backdrop = CityEventHeroTheme.Backdrop.self
        let end = CityEventHeroTheme.Layout.card.y + CityEventHeroTheme.Layout.card.height
        let height = end - backdrop.fadeFrom
        var colors: [CGColor] = [backdrop.knots[0].color.withAlphaComponent(0).cgColor, backdrop.knots[0].color.cgColor]
        colors.append(contentsOf: backdrop.knots.dropFirst().map(\.color.cgColor))
        colors.append(backdrop.knots.last!.color.cgColor)
        var locations: [CGFloat] = [0, (backdrop.solidFrom - backdrop.fadeFrom) / height]
        locations.append(contentsOf: backdrop.knots.dropFirst().map { ($0.y - backdrop.fadeFrom) / height })
        locations.append(1)
        let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!, colors: colors as CFArray, locations: locations)!
        context.drawLinearGradient(
            gradient,
            start: CGPoint(x: 0, y: metrics.y(backdrop.fadeFrom)),
            end: CGPoint(x: 0, y: metrics.y(end)),
            options: []
        )
    }

    private func drawGlows(in context: CGContext, metrics: Metrics) {
        context.saveGState()
        context.setBlendMode(.plusLighter)
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        for glow in CityEventHeroTheme.Backdrop.glows {
            let colors = CityEventHeroTheme.Backdrop.gaussian.map {
                UIColor(red: glow.red / 255, green: glow.green / 255, blue: glow.blue / 255, alpha: $0).cgColor
            }
            let locations = CityEventHeroTheme.Backdrop.gaussian.indices.map { CGFloat($0) / 6 }
            let gradient = CGGradient(colorsSpace: colorSpace, colors: colors as CFArray, locations: locations)!
            context.saveGState()
            context.translateBy(x: glow.centerX * metrics.unit, y: metrics.y(glow.centerY))
            context.scaleBy(x: 1, y: glow.sigmaY / glow.sigmaX)
            context.drawRadialGradient(
                gradient,
                startCenter: .zero,
                startRadius: 0,
                endCenter: .zero,
                endRadius: 3 * glow.sigmaX * metrics.unit,
                options: [.drawsAfterEndLocation]
            )
            context.restoreGState()
        }
        context.restoreGState()
    }

    private func drawWordmark(in context: CGContext, metrics: Metrics) {
        let spec = CityEventHeroTheme.Layout.wordmark
        let font = CTFontCreateWithGraphicsFont(CityEventHeroAssets.wordmarkFont, spec.size * metrics.unit, nil, nil)
        let x = spec.x * metrics.unit
        let baseline = metrics.y(spec.baseline)
        drawLine(CityEventHeroTheme.Copy.wordmark, font: font, tracking: 0, color: CityEventHeroTheme.Color.wordOutline, x: x, baseline: baseline, context: context, strokeWidth: spec.outline * 2 * metrics.unit)
        drawLine(CityEventHeroTheme.Copy.wordmark, font: font, tracking: 0, color: CityEventHeroTheme.Color.wordFill, x: x, baseline: baseline, context: context)
    }

    private func drawTopBar(in context: CGContext, metrics: Metrics) {
        drawPill(CityEventHeroTheme.Layout.gift, color: CityEventHeroTheme.Color.glassTop, metrics: metrics, context: context)
        drawPill(CityEventHeroTheme.Layout.byDatePill, color: CityEventHeroTheme.Color.glassTop, metrics: metrics, context: context)
        drawPill(CityEventHeroTheme.Layout.search, color: CityEventHeroTheme.Color.glassTop, metrics: metrics, context: context)
        drawIcon(.gift, in: context, metrics: metrics)
        drawIcon(.filter, in: context, metrics: metrics)
        drawIcon(.search, in: context, metrics: metrics)
        drawText(CityEventHeroTheme.Copy.byDate, style: CityEventHeroTheme.TypeStyle.pill, x: CityEventHeroTheme.Layout.byDate.x - 3, baseline: CityEventHeroTheme.Layout.byDate.baseline, metrics: metrics, context: context)
    }

    private func drawGuests(in context: CGContext, metrics: Metrics, drawBase: Bool = true, includeFireGlow: Bool = true) {
        for guest in CityEventHeroTheme.Layout.guests {
            guard let slot = CityEventHeroMotion.slots.firstIndex(of: guest) else { continue }
            let target = CityEventHeroMotion.fanned[slot]
            let fan = motionFrame.fan[slot]
            let flight = CityEventHeroMotion.flight(fan)
            let spot = motionFrame.spot[slot]
            let center = CGPoint(
                x: (guest.centerX + (target.centerX - guest.centerX) * fan) * metrics.unit,
                y: metrics.y(guest.centerY + (target.centerY - guest.centerY) * fan) - (CityEventHeroMotion.Fan.flightLift * flight + CityEventHeroMotion.Spot.lift * spot) * metrics.unit
            )
            let scale = 1 + CityEventHeroMotion.Fan.flightScale * flight + CityEventHeroMotion.Spot.scale * spot
            let diameter = guest.diameter * metrics.unit
            context.saveGState()
            context.setAlpha(motionFrame.guestFade)
            context.translateBy(x: center.x, y: center.y)
            context.rotate(by: target.rotation * fan * .pi / 180)
            context.scaleBy(x: scale, y: scale)
            let circle = CGRect(x: -diameter / 2, y: -diameter / 2, width: diameter, height: diameter)
            context.setLineWidth(CityEventHeroTheme.Space.ring * metrics.unit)
            if drawBase {
                context.saveGState()
                context.addEllipse(in: circle); context.clip()
                CityEventHeroAssets.guests[guest.image].draw(in: circle)
                context.restoreGState()
                context.setStrokeColor(CityEventHeroTheme.Color.white.cgColor)
                context.strokeEllipse(in: circle.insetBy(dx: CityEventHeroTheme.Space.ring * metrics.unit / 2, dy: CityEventHeroTheme.Space.ring * metrics.unit / 2))
            }
            let light = uiLight()
            if includeFireGlow, light.amount > 0.0001 {
                context.setAlpha(0.85 * light.amount)
                context.setStrokeColor(light.color.cgColor)
                context.strokeEllipse(in: circle.insetBy(dx: CityEventHeroTheme.Space.ring * metrics.unit / 2, dy: CityEventHeroTheme.Space.ring * metrics.unit / 2))
            }
            context.restoreGState()
        }
    }

    private func drawEventText(in context: CGContext, metrics: Metrics, drawBase: Bool = true, includeFireGlow: Bool = true) {
        if drawBase {
            let nameLines = CityEventHeroTheme.Copy.names.map { $0 + CityEventHeroTheme.Copy.moreSuffix }
            for index in nameLines.indices {
                let roll = roll(index: index, from: motionFrame.nameFrom, to: motionFrame.nameTo, progress: motionFrame.nameProgress, rise: reduceMotion ? 0 : CityEventHeroMotion.Roll.nameRise)
                guard roll.opacity > 0.001 else { continue }
                drawText(nameLines[index], style: CityEventHeroTheme.TypeStyle.mute, centerX: CityEventHeroTheme.Layout.mute.centerX, baseline: CityEventHeroTheme.Layout.mute.baseline + roll.offset, alpha: roll.opacity, metrics: metrics, context: context)
            }
        }
        let headlineGlow = max(0.004, CityEventHeroMotion.fireLight(at: CityEventHeroMotion.fire(of: motionFrame.show))
            * min(1, motionFrame.night / CityEventHeroMotion.Night.fireFade))
        if includeFireGlow, headlineGlow > 0.004 {
            context.saveGState()
            context.setShadow(
                offset: .zero,
                blur: CityEventHeroTheme.Night.headlineGlowRadius * metrics.fontScale,
                color: CityEventHeroTheme.Night.headlineGlow.cgColor
            )
            drawText(CityEventHeroTheme.Copy.headline, style: CityEventHeroTheme.TypeStyle.headline, centerX: CityEventHeroTheme.Layout.headline.centerX, baseline: CityEventHeroTheme.Layout.headline.baseline, alpha: headlineGlow, metrics: metrics, context: context)
            context.restoreGState()
        }
        if drawBase {
            drawText(CityEventHeroTheme.Copy.headline, style: CityEventHeroTheme.TypeStyle.headline, centerX: CityEventHeroTheme.Layout.headline.centerX, baseline: CityEventHeroTheme.Layout.headline.baseline, metrics: metrics, context: context)
            drawText(CityEventHeroTheme.Copy.date, style: CityEventHeroTheme.TypeStyle.date, centerX: CityEventHeroTheme.Layout.date.centerX, baseline: CityEventHeroTheme.Layout.date.baseline, metrics: metrics, context: context)
        }
    }

    private func drawPresented(in context: CGContext, metrics: Metrics) {
        drawRounded(CityEventHeroTheme.Layout.presented, radius: CityEventHeroTheme.Radius.glassCard, color: CityEventHeroTheme.Color.glassBottom, metrics: metrics, context: context)
        let thumb = metrics.rect(CityEventHeroTheme.Layout.thumb)
        context.saveGState()
        context.addPath(UIBezierPath(roundedRect: thumb, cornerRadius: CityEventHeroTheme.Radius.thumb * metrics.unit).cgPath); context.clip()
        let crop = CityEventHeroTheme.Layout.thumbCrop
        let factor = CityEventHeroTheme.Layout.thumb.width / crop.size
        let imageRect = CGRect(
            x: thumb.minX - crop.x * factor * metrics.unit,
            y: thumb.minY - crop.y * factor * metrics.unit,
            width: 850 * factor * metrics.unit,
            height: 1850 * factor * metrics.unit
        )
        CityEventHeroAssets.city.draw(in: imageRect)
        context.restoreGState()
        drawText(CityEventHeroTheme.Copy.presentedBy, style: CityEventHeroTheme.TypeStyle.label, x: CityEventHeroTheme.Layout.presentedBy.x - 4, baseline: CityEventHeroTheme.Layout.presentedBy.baseline, metrics: metrics, context: context)
        drawText(CityEventHeroTheme.Copy.presentedTitle, style: CityEventHeroTheme.TypeStyle.title, x: CityEventHeroTheme.Layout.presentedTitle.x - 4, baseline: CityEventHeroTheme.Layout.presentedTitle.baseline, metrics: metrics, context: context)
        drawIcon(.chevronCard, in: context, metrics: metrics, color: CityEventHeroTheme.Color.chevronCard)
    }

    private func drawActions(in context: CGContext, metrics: Metrics) {
        drawPill(CityEventHeroTheme.Layout.like, color: CityEventHeroTheme.Color.glassBottom, metrics: metrics, context: context)
        drawPill(CityEventHeroTheme.Layout.share, color: CityEventHeroTheme.Color.glassBottom, metrics: metrics, context: context)
        drawPill(CityEventHeroTheme.Layout.more, color: CityEventHeroTheme.Color.glassBottom, metrics: metrics, context: context)
        drawLike(in: context, metrics: metrics)
        drawIcon(.share, in: context, metrics: metrics)
        drawIcon(.more, in: context, metrics: metrics)
        drawCount(in: context, metrics: metrics)
        drawViewButton(in: context, metrics: metrics)
    }

    private func drawLike(in context: CGContext, metrics: Metrics) {
        let icon = CityEventHeroIcon.heart.box
        let center = CGPoint(x: (icon.x + icon.width / 2) * metrics.unit, y: metrics.y(icon.y + icon.height / 2))
        if motionFrame.ring > 0 && motionFrame.ring < 1 {
            let baseRadius = icon.width / 2
            let radius = baseRadius * (CityEventHeroMotion.Like.ringFrom + (CityEventHeroMotion.Like.ringTo - CityEventHeroMotion.Like.ringFrom) * motionFrame.ring) * metrics.unit
            context.saveGState()
            context.setAlpha(0.9 * (1 - motionFrame.ring))
            context.setStrokeColor(CityEventHeroTheme.Color.white.cgColor)
            context.setLineWidth(CityEventHeroMotion.Like.ringStroke * (1 - motionFrame.ring) * metrics.unit)
            context.strokeEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
            context.restoreGState()
        }
        context.saveGState()
        context.translateBy(x: center.x, y: center.y)
        context.scaleBy(x: motionFrame.pop, y: motionFrame.pop)
        context.translateBy(x: -center.x, y: -center.y)
        drawIcon(.heart, in: context, metrics: metrics)
        if motionFrame.like > 0.001 {
            context.saveGState(); context.setAlpha(motionFrame.like); drawIcon(.heartFill, in: context, metrics: metrics); context.restoreGState()
        }
        context.restoreGState()
    }

    private func drawCount(in context: CGContext, metrics: Metrics) {
        let layout = CityEventHeroTheme.Layout.self
        let clip = CGRect(
            x: (layout.likeCount.x - 20) * metrics.unit,
            y: metrics.y(layout.rowY + layout.countInset),
            width: (layout.like.x + layout.like.width - layout.likeCount.x) * metrics.unit,
            height: (layout.rowHeight - 2 * layout.countInset) * metrics.unit
        )
        context.saveGState(); context.clip(to: clip)
        for (index, text) in [CityEventHeroTheme.Copy.likes, CityEventHeroTheme.Copy.likesNext].enumerated() {
            let roll = roll(index: index, from: 0, to: 1, progress: motionFrame.count, rise: reduceMotion ? 0 : CityEventHeroMotion.Roll.countRise)
            drawText(text, style: CityEventHeroTheme.TypeStyle.pill, x: layout.likeCount.x - 3, baseline: layout.likeCount.baseline + roll.offset, alpha: roll.opacity, metrics: metrics, context: context)
        }
        context.restoreGState()
    }

    private func drawViewButton(in context: CGContext, metrics: Metrics) {
        let box = CityEventHeroTheme.Layout.view
        let rect = metrics.rect(box)
        let scale = reduceMotion ? 1 : 1 - CityEventHeroMotion.View.pressScale * motionFrame.press
        context.saveGState()
        context.setAlpha(reduceMotion ? 1 - 0.3 * motionFrame.press : 1)
        context.translateBy(x: rect.midX, y: rect.midY)
        context.scaleBy(x: scale, y: scale)
        context.translateBy(x: -rect.midX, y: -rect.midY)
        context.setFillColor(CityEventHeroTheme.Color.glassBottom.cgColor)
        context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: rect.height / 2).cgPath); context.fillPath()
        drawGlassLight(in: rect, radius: rect.height / 2, context: context)
        if motionFrame.sweep > 0 && motionFrame.sweep < 1 {
            context.saveGState(); context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: rect.height / 2).cgPath); context.clip()
            let band = CityEventHeroMotion.View.band * metrics.unit
            let x = rect.minX - CityEventHeroMotion.View.bandLead * band + (rect.width + (CityEventHeroMotion.View.bandLead + 0.6) * band) * motionFrame.sweep
            let slope = tan(-CityEventHeroMotion.View.bandSkew * .pi / 180)
            let colors = [UIColor.clear.cgColor, UIColor.white.withAlphaComponent(0.08).cgColor, UIColor.white.withAlphaComponent(0.30).cgColor, UIColor.white.withAlphaComponent(0.08).cgColor, UIColor.clear.cgColor] as CFArray
            let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!, colors: colors, locations: [0, 0.3, 0.5, 0.7, 1])!
            context.drawLinearGradient(gradient, start: CGPoint(x: x, y: rect.minY), end: CGPoint(x: x + band, y: rect.minY + band * slope), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            context.restoreGState()
        }
        drawText(CityEventHeroTheme.Copy.view, style: CityEventHeroTheme.TypeStyle.pillSmall, x: CityEventHeroTheme.Layout.viewText.x - 3, baseline: CityEventHeroTheme.Layout.viewText.baseline, metrics: metrics, context: context)
        drawIcon(.chevronView, in: context, metrics: metrics, xOffset: CityEventHeroMotion.View.nudge * motionFrame.chevron, color: CityEventHeroTheme.Color.chevronView)
        context.restoreGState()
    }

    private func drawTabs(in context: CGContext, metrics: Metrics) {
        drawIcon(.home, in: context, metrics: metrics)
        drawIcon(.chat, in: context, metrics: metrics)
        drawIcon(.plus, in: context, metrics: metrics)
        drawIcon(.bell, in: context, metrics: metrics)
        drawIcon(.person, in: context, metrics: metrics)
    }

    private func drawPill(_ box: CityEventHeroTheme.Box, color: UIColor, metrics: Metrics, context: CGContext) {
        drawRounded(box, radius: min(box.width, box.height) / 2, color: color, metrics: metrics, context: context)
    }

    private func drawRounded(_ box: CityEventHeroTheme.Box, radius: Double, color: UIColor, metrics: Metrics, context: CGContext) {
        let rect = metrics.rect(box)
        context.setFillColor(color.cgColor)
        context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: radius * metrics.unit).cgPath)
        context.fillPath()
        drawGlassLight(in: rect, radius: radius * metrics.unit, context: context)
    }

    private func drawGlassLight(in rect: CGRect, radius: Double, context: CGContext) {
        let light = uiLight()
        guard light.amount > 0.0001 else { return }
        context.saveGState()
        context.setFillColor(light.color.withAlphaComponent(0.16 * light.amount).cgColor)
        context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: radius).cgPath)
        context.fillPath()
        context.restoreGState()
    }

    private func uiLight() -> (amount: Double, color: UIColor) {
        let fire = CityEventHeroMotion.fire(of: motionFrame.show)
        let fade = min(1, motionFrame.night / CityEventHeroMotion.Night.fireFade)
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var weight = 0.0
        if fire >= 0 {
            for (index, burst) in CityEventHeroMotion.Fire.bursts.enumerated() {
                let amount = CityEventHeroMotion.flash(at: burst, time: fire - burst.at)
                    + (index == 0 ? 0.6 * CityEventHeroMotion.flare(at: fire) : 0)
                red += burst.color.x * 255 * amount
                green += burst.color.y * 255 * amount
                blue += burst.color.z * 255 * amount
                weight += amount
            }
        }
        let led = CityEventHeroMotion.led(show: motionFrame.show)
        let spill = led[11] * 0.35
        red += Double(CityEventHeroTheme.Scene.spillText.x) * 255 * spill
        green += Double(CityEventHeroTheme.Scene.spillText.y) * 255 * spill
        blue += Double(CityEventHeroTheme.Scene.spillText.z) * 255 * spill
        weight += spill
        let amount = (min(1, (CityEventHeroMotion.fireLight(at: fire) + spill) * fade) * 64).rounded() / 64
        guard weight > 0 else { return (amount, CityEventHeroTheme.Night.rim) }
        func quantize(_ value: Double) -> Double {
            min(255, (value / weight / 32).rounded() * 32) / 255
        }
        return (
            amount,
            UIColor(red: quantize(red), green: quantize(green), blue: quantize(blue), alpha: 1)
        )
    }

    private func drawIcon(_ icon: CityEventHeroIcon, in context: CGContext, metrics: Metrics, xOffset: Double = 0, color: UIColor = .white) {
        let box = icon.box
        icon.draw(in: context, x: (box.x + xOffset) * metrics.unit, y: metrics.y(box.y), scale: metrics.unit, color: color)
    }

    private func drawText(_ text: String, style: CityEventHeroTheme.TextStyle, x: Double? = nil, centerX: Double? = nil, baseline: Double, alpha: Double = 1, metrics: Metrics, context: CGContext) {
        let size = style.size * metrics.fontScale
        let font: CTFont
        switch style.face {
        case .system: font = UIFont.systemFont(ofSize: size, weight: style.weight) as CTFont
        case .headline: font = CTFontCreateWithGraphicsFont(CityEventHeroAssets.headlineFont, size, nil, nil)
        }
        let tracking = style.tracking * metrics.fontScale
        let line = makeLine(text, font: font, tracking: tracking, color: style.color)
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)
        let originX = centerX.map { $0 * metrics.unit - width / 2 } ?? ((x ?? 0) * metrics.unit)
        context.saveGState(); context.setAlpha(alpha)
        draw(line, x: originX, baseline: metrics.y(baseline), context: context)
        context.restoreGState()
    }

    private func drawLine(_ text: String, font: CTFont, tracking: Double, color: UIColor, x: Double, baseline: Double, context: CGContext, strokeWidth: Double? = nil) {
        let line = makeLine(text, font: font, tracking: tracking, color: color, strokeWidth: strokeWidth)
        draw(line, x: x, baseline: baseline, context: context)
    }

    private func makeLine(_ text: String, font: CTFont, tracking: Double, color: UIColor, strokeWidth: Double? = nil) -> CTLine {
        var attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): color.cgColor,
            NSAttributedString.Key(kCTKernAttributeName as String): tracking,
        ]
        if let strokeWidth {
            attributes[.strokeColor] = color
            attributes[.strokeWidth] = 100 * strokeWidth / CTFontGetSize(font)
        }
        return CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
    }

    private func draw(_ line: CTLine, x: Double, baseline: Double, context: CGContext) {
        context.saveGState()
        context.translateBy(x: x, y: baseline)
        context.scaleBy(x: 1, y: -1)
        context.textPosition = .zero
        CTLineDraw(line, context)
        context.restoreGState()
    }

    private func roll(index: Int, from: Int, to: Int, progress: Double, rise: Double) -> (opacity: Double, offset: Double) {
        if from == to { return index == from ? (1, 0) : (0, 0) }
        if index == from { return (1 - progress, -rise * progress) }
        if index == to { return (progress, rise * (1 - progress)) }
        return (0, 0)
    }

    private func hit(at point: CGPoint) -> CityEventHeroController.Hit {
        let metrics = Metrics(bounds: bounds, safeTop: layoutSafeTop)
        let skyEnd = metrics.y(CityEventHeroTheme.Layout.guestBand.start)
        if point.y >= metrics.y(CityEventHeroTheme.Layout.skyBandStart), point.y < skyEnd { return .sky }
        if point.y >= metrics.y(CityEventHeroTheme.Layout.guestBand.start), point.y <= metrics.y(CityEventHeroTheme.Layout.guestBand.end) { return .guests }
        if metrics.rect(CityEventHeroTheme.Layout.like).contains(point) { return .like }
        if metrics.rect(CityEventHeroTheme.Layout.view).contains(point) { return .view }
        return .none
    }

    private func guest(at point: CGPoint) -> Int? {
        let metrics = Metrics(bounds: bounds, safeTop: layoutSafeTop)
        var best: (slot: Int, distance: Double)?
        for slot in CityEventHeroMotion.slots.indices {
            let position = CityEventHeroMotion.fanned[slot]
            let center = CGPoint(x: position.centerX * metrics.unit, y: metrics.y(position.centerY))
            let distance = hypot(point.x - center.x, point.y - center.y)
            let radius = CityEventHeroMotion.slots[slot].diameter / 2 * metrics.unit + 6
            if distance <= radius, distance < (best?.distance ?? .greatestFiniteMagnitude) { best = (slot, distance) }
        }
        return best?.slot
    }

    private func updateAccessibility() {
        guard actionElements.count == 4, bounds.width > 0 else { return }
        let nameIndex = motionFrame.nameProgress > 0.5 ? motionFrame.nameTo : motionFrame.nameFrom
        let signature = "\(bounds)-\(layoutSafeTop)-\(nameIndex)-\(motionFrame.count > 0.5)"
        guard signature != accessibilitySignature else { return }
        accessibilitySignature = signature
        let metrics = Metrics(bounds: bounds, safeTop: layoutSafeTop)
        let layout = CityEventHeroTheme.Layout.self
        let copy = CityEventHeroTheme.Copy.self
        let text: [(String, Double, Double, Double, Double)] = [
            (copy.byDate, layout.byDate.x - 3, layout.byDate.baseline, 180, 17),
            (copy.names[nameIndex] + copy.moreSuffix, 59, layout.mute.baseline, 1100, 16),
            (copy.headline, 55, layout.headline.baseline, 1100, 34.33),
            (copy.date, 59, layout.date.baseline, 1100, 16),
            (copy.presentedBy, layout.presentedBy.x - 4, layout.presentedBy.baseline, 260, 14),
            (copy.presentedTitle, layout.presentedTitle.x - 4, layout.presentedTitle.baseline, 500, 16),
            (motionFrame.count > 0.5 ? copy.likesNext : copy.likes, layout.likeCount.x - 3, layout.likeCount.baseline, 30, 17),
            (copy.view, layout.viewText.x - 3, layout.viewText.baseline, 220, 15),
        ]
        for (index, item) in text.enumerated() {
            let size = item.4 * metrics.fontScale
            textElements[index].accessibilityLabel = item.0
            textElements[index].accessibilityTraits = .staticText
            textElements[index].accessibilityFrameInContainerSpace = CGRect(
                x: item.1 * metrics.unit, y: metrics.y(item.2) - size,
                width: item.3 * metrics.unit, height: size * 1.25)
        }
        let skyRect = CGRect(
            x: 0,
            y: metrics.y(CityEventHeroTheme.Layout.skyBandStart),
            width: bounds.width,
            height: metrics.y(CityEventHeroTheme.Layout.guestBand.start) - metrics.y(CityEventHeroTheme.Layout.skyBandStart)
        )
        let guestRect = CGRect(x: 0, y: metrics.y(CityEventHeroTheme.Layout.guestBand.start), width: bounds.width, height: metrics.y(CityEventHeroTheme.Layout.guestBand.end) - metrics.y(CityEventHeroTheme.Layout.guestBand.start))
        let labels = [
            "City sky. Run the day into the night.",
            "Guests: Amira and 17 more. Show guests.",
            "Like",
            "View event. Lights up the tower.",
        ]
        let frames = [skyRect, guestRect, metrics.rect(CityEventHeroTheme.Layout.like), metrics.rect(CityEventHeroTheme.Layout.view)]
        for index in actionElements.indices {
            actionElements[index].accessibilityLabel = labels[index]
            actionElements[index].accessibilityTraits = .button
            actionElements[index].accessibilityFrameInContainerSpace = frames[index]
        }
        actionElements[0].action = { [weak controller] in controller?.accessibilitySky(at: Date.timeIntervalSinceReferenceDate) }
        actionElements[1].action = { [weak controller] in controller?.accessibilityGuests(at: Date.timeIntervalSinceReferenceDate) }
        actionElements[2].action = { [weak controller] in controller?.accessibilityLike(at: Date.timeIntervalSinceReferenceDate) }
        actionElements[3].action = { [weak controller] in controller?.accessibilityView(at: Date.timeIntervalSinceReferenceDate) }
    }

    private struct Metrics {
        let bounds: CGRect
        let safeTop: Double
        var unit: Double { bounds.width / CityEventHeroTheme.referenceWidth }
        var fontScale: Double { bounds.width / CityEventHeroTheme.referencePoints }

        func y(_ referenceY: Double) -> Double {
            if referenceY < CityEventHeroTheme.topGroupMaximum {
                return safeTop + (referenceY - CityEventHeroTheme.referenceInset) * unit
            }
            return bounds.height - (CityEventHeroTheme.referenceHeight - referenceY) * unit
        }

        func rect(_ box: CityEventHeroTheme.Box) -> CGRect {
            CGRect(x: box.x * unit, y: y(box.y), width: box.width * unit, height: box.height * unit)
        }
    }
}

@MainActor
final class CityEventHeroActionElement: UIAccessibilityElement {
    var action: (() -> Void)?
    override func accessibilityActivate() -> Bool { action?(); return action != nil }
}
