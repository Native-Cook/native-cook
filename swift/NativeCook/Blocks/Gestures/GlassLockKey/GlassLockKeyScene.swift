import SwiftUI
import UIKit

/// SwiftUI and Canvas rendering of the active `GlassUpgrade.tsx` branch.
/// Scene coordinates are the React Native 402 pt stage; the registered source
/// images continue to use their 1206 × 2622 source-space affine transforms.
struct GlassLockKeyScene: View {
    private static let stageWidth = 402.0
    private static let sourceWidth = 1206.0

    let state: GlassLockKeySimulationState
    let size: CGSize
    let safeAreaInsets: EdgeInsets
    let reducedMotion: Bool
    let send: (GlassLockKeyInput) -> Void
    let command: (GlassLockKeyCommand) -> Void

    private var stageScale: Double { Double(size.width) / Self.stageWidth }
    private var sourceScale: Double { Double(size.width) / Self.sourceWidth }
    private var isOpen: Bool { state.mode == .open }
    private var sheetHeight: Double { Double(size.height) * 0.5 }

    var body: some View {
        ZStack(alignment: .topLeading) {
            GlassLockKeyCanvas(
                state: state,
                size: size,
                reducedMotion: reducedMotion
            )

            GlassLockKeyTouch(
                isOpen: isOpen,
                accessibilityFrame: keyAccessibilityFrame,
                onInput: send,
                onCommand: command
            )
            .frame(width: size.width, height: size.height)

            header
            hint
            vaultSheet
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(GlassLockKeySceneColor.background)
    }

    private var header: some View {
        let shackle = GlassLockKeySceneMath.clamp(state.shackle)
        return VStack(alignment: .leading, spacing: 0) {
            Text(GlassLockKeyConstants.Copy.eyebrow)
                .font(.system(size: 12, weight: .bold))
                .tracking(1.8)
                .foregroundStyle(GlassLockKeySceneColor.ink.opacity(0.5))

            ZStack(alignment: .topLeading) {
                Text(GlassLockKeyConstants.Copy.locked)
                    .font(.system(size: 36, weight: .bold))
                    .tracking(-0.8)
                    .foregroundStyle(GlassLockKeySceneColor.ink)
                    .opacity(1 - GlassLockKeySceneMath.clamp((shackle - 0.15) / 0.7))
                    .offset(y: -8 * shackle)

                Text(GlassLockKeyConstants.Copy.unlocked)
                    .font(.system(size: 36, weight: .bold))
                    .tracking(-0.8)
                    .foregroundStyle(GlassLockKeySceneColor.ink)
                    .opacity(GlassLockKeySceneMath.clamp((shackle - 0.15) / 0.7))
                    .offset(y: 8 * (1 - shackle))
            }
            .frame(height: 44, alignment: .topLeading)
            .clipped()
            .padding(.top, 8)

            Text(GlassLockKeyConstants.Copy.meta)
                .font(.system(size: 13))
                .foregroundStyle(GlassLockKeySceneColor.ink.opacity(0.5))
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
        .offset(y: safeAreaInsets.top + 24)
        .opacity(1 - state.sheet * 0.4)
        .allowsHitTesting(false)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(isOpen ? "VAULT, Unlocked, 3 items end-to-end encrypted" : "VAULT, Locked, 3 items end-to-end encrypted")
        .accessibilityAddTraits(.isHeader)
    }

    private var hint: some View {
        Text(GlassLockKeyConstants.Copy.hint)
            .font(.system(size: 14))
            .foregroundStyle(GlassLockKeySceneColor.ink.opacity(0.55))
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.horizontal, 24)
            .frame(maxHeight: .infinity, alignment: .bottom)
            .padding(.bottom, safeAreaInsets.bottom + 32)
            .opacity(1 - GlassLockKeySceneMath.clamp(state.sheet * 3))
            .allowsHitTesting(false)
    }

    private var vaultSheet: some View {
        let shown = GlassLockKeySceneMath.clamp(state.sheet)
        let travel = (1 - shown) * (sheetHeight + 40)
        return GlassLockKeyVaultSheet(
            sheet: shown,
            reducedMotion: reducedMotion,
            safeBottom: safeAreaInsets.bottom,
            height: sheetHeight,
            lock: { command(.lock) }
        )
        .padding(.horizontal, 8)
        .frame(maxHeight: .infinity, alignment: .bottom)
        .padding(.bottom, 8)
        .opacity(reducedMotion ? shown : 1)
        .offset(y: reducedMotion ? 0 : travel)
        .allowsHitTesting(isOpen)
        .accessibilityHidden(!isOpen)
    }

    private var keyAccessibilityFrame: CGRect {
        let scale = CGFloat(stageScale)
        return CGRect(
            x: 200.7 * scale - 44,
            y: 436.3 * scale - 70 * scale,
            width: 88,
            height: 140 * scale
        )
    }
}

private struct GlassLockKeyCanvas: View {
    let state: GlassLockKeySimulationState
    let size: CGSize
    let reducedMotion: Bool

    private let stageWidth = 402.0
    private let sourceWidth = 1206.0

    var body: some View {
        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, canvasSize in
            GlassLockKeyScenePainter(
                state: state,
                size: canvasSize,
                reducedMotion: reducedMotion,
                stageScale: Double(canvasSize.width) / stageWidth,
                sourceScale: Double(canvasSize.width) / sourceWidth
            ).draw(in: &context)
        }
        .accessibilityHidden(true)
    }
}

private struct GlassLockKeyScenePainter {
    let state: GlassLockKeySimulationState
    let size: CGSize
    let reducedMotion: Bool
    let stageScale: Double
    let sourceScale: Double

    private let lockCentre = CGPoint(x: 201, y: 445)
    private let plug = CGPoint(x: 145.3, y: 461)
    private let keyFront = CGPoint(x: 200.7, y: 436.3)

    func draw(in context: inout GraphicsContext) {
        let screen = CGRect(origin: .zero, size: size)
        context.fill(Path(screen), with: .color(GlassLockKeySceneColor.background))

        let insert = insertProgress
        let seated = GlassLockKeySceneMath.clamp((insert - 0.6) / 0.4)
        let lockOpacity = max(state.show, seated)
        drawGlow(in: &context, opacity: lockOpacity * (0.6 + 0.4 * state.sheet))

        var rig = context
        let centre = scaled(lockCentre)
        let recoil = recoilOffset
        let shake = shakeOffset
        rig.translateBy(
            x: centre.x + shake,
            y: centre.y - CGFloat(150 * stageScale * state.sheet) + recoil
        )
        let shrink = 1 - (1 - 0.72) * state.sheet
        rig.scaleBy(x: shrink, y: shrink)
        rig.translateBy(x: -centre.x, y: -centre.y)

        drawLock(in: &rig, opacity: lockOpacity)
        drawTick(in: &rig)
        drawSeatedKey(in: &rig, seatedOpacity: seated)
        drawLooseKey(in: &context, insert: insert)
    }

    private var insertProgress: Double {
        if state.mode == .inserting {
            return reducedMotion ? 1 : GlassLockKeySceneMath.easeInOut(
                GlassLockKeySceneMath.clamp(state.modeT / GlassLockKeyConstants.UpgradeMotion.insert)
            )
        }
        switch state.mode {
        case .seated, .open, .closing:
            return 1
        default:
            return 0
        }
    }

    private var recoilOffset: CGFloat {
        guard state.glint >= 0, !reducedMotion else { return 0 }
        let recoil = GlassLockKeyConstants.UpgradeMotion.recoil
        return CGFloat(recoil.amp * exp(-recoil.decay * state.glint) * sin(2 * .pi * recoil.hz * state.glint) * stageScale)
    }

    private var shakeOffset: CGFloat {
        guard state.shake >= 0 else { return 0 }
        let shake = GlassLockKeyConstants.UpgradeMotion.shake
        let u = state.shake / shake.duration
        return CGFloat(shake.amp * (1 - u) * sin(2 * .pi * Double(shake.cycles) * u) * stageScale)
    }

    private func drawGlow(in context: inout GraphicsContext, opacity: Double) {
        let centre = scaled(lockCentre)
        let radius = CGFloat(260 * stageScale)
        var glow = context
        glow.opacity = opacity
        glow.fill(
            Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2)),
            with: .radialGradient(
                Gradient(colors: [GlassLockKeySceneColor.glow, .clear]),
                center: centre,
                startRadius: 0,
                endRadius: radius
            )
        )
    }

    private func drawLock(in context: inout GraphicsContext, opacity: Double) {
        let appear = reducedMotion ? 1 : GlassLockKeyConstants.UpgradeMotion.appear + (1 - GlassLockKeyConstants.UpgradeMotion.appear) * opacity
        var lock = context
        let plug = scaled(plug)
        lock.opacity = opacity
        lock.translateBy(x: plug.x, y: plug.y)
        lock.scaleBy(x: appear, y: appear)
        lock.translateBy(x: -plug.x, y: -plug.y)
        applyFit(GlassLockKeySceneFit.lock, centre: GlassLockKeySceneFit.lockCentre, to: &lock)

        lock.draw(GlassLockKeySceneAssets.shadow, in: screen)

        let shackle = state.shackle
        let closed = GlassLockKeySceneMath.clamp(1 - shackle)
        if closed > 0 {
            var layer = lock
            layer.opacity = opacity * closed
            layer.translateBy(x: CGFloat((3 + 23 * shackle) * sourceScale), y: CGFloat((-90 * shackle) * sourceScale))
            layer.draw(GlassLockKeySceneAssets.shackleClosed, in: screen)
        }
        let opened = GlassLockKeySceneMath.clamp(shackle)
        if opened > 0 {
            var layer = lock
            layer.opacity = opacity * opened
            layer.translateBy(x: CGFloat((-4 - 23 * (1 - shackle)) * sourceScale), y: CGFloat((1 + 90 * (1 - shackle)) * sourceScale))
            layer.draw(GlassLockKeySceneAssets.shackleOpen, in: screen)
        }

        lock.drawLayer { body in
            body.draw(GlassLockKeySceneAssets.padlockBody, in: screen)
            guard state.glint >= 0 else { return }
            let u = state.glint / GlassLockKeyConstants.UpgradeMotion.glint
            var glint = body
            // `drawLayer` composites this transparency layer through the lock context's opacity.
            // Applying `opacity` again here would square the RN lock alpha during materialisation.
            glint.opacity = sin(.pi * u) * 0.8
            glint.blendMode = .sourceAtop
            glint.translateBy(
                x: CGFloat((lockCentre.x - 120 + 240 * u) * stageScale),
                y: CGFloat((lockCentre.y - 140) * stageScale)
            )
            glint.concatenate(CGAffineTransform(a: 1, b: 0, c: tan(-0.35), d: 1, tx: 0, ty: 0))
            let band = CGRect(x: 0, y: 0, width: CGFloat(36 * stageScale), height: CGFloat(280 * stageScale))
            glint.fill(
                Path(band),
                with: .linearGradient(
                    Gradient(colors: [.clear, Color.white.opacity(0.75), .clear]),
                    startPoint: CGPoint(x: 0, y: 0),
                    endPoint: CGPoint(x: band.width, y: 0)
                )
            )
        }
    }

    private func drawTick(in context: inout GraphicsContext) {
        guard state.tick >= 0 else { return }
        let u = state.tick / GlassLockKeyConstants.UpgradeMotion.tick.duration
        guard u < 1 else { return }
        let centre = scaled(plug)
        let radius = CGFloat(40 * stageScale)
        var tick = context
        tick.opacity = GlassLockKeyConstants.UpgradeMotion.tick.alpha * pow(1 - u, 2)
        tick.fill(
            Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2)),
            with: .radialGradient(
                Gradient(colors: [.white, .clear]), center: centre, startRadius: 0, endRadius: radius
            )
        )
    }

    private func drawSeatedKey(in context: inout GraphicsContext, seatedOpacity: Double) {
        guard seatedOpacity > 0 else { return }
        let turn = GlassLockKeySceneMath.clamp(state.th)
        let fit = GlassLockKeySceneFit.interpolate(
            GlassLockKeySceneFit.keyClosed,
            GlassLockKeySceneFit.keyOpen,
            progress: GlassLockKeySceneMath.clamp(state.th, lower: -0.15, upper: 1)
        )
        var key = context
        applyFit(fit, centre: GlassLockKeySceneFit.keyCentre, to: &key)
        if turn < 1 {
            var closed = key
            closed.opacity = seatedOpacity * (1 - turn)
            closed.draw(GlassLockKeySceneAssets.keyInA, in: screen)
        }
        if turn > 0 {
            var open = key
            open.opacity = seatedOpacity * turn
            open.draw(GlassLockKeySceneAssets.keyIn, in: screen)
        }
    }

    private func drawLooseKey(in context: inout GraphicsContext, insert: Double) {
        let looseOpacity = 1 - GlassLockKeySceneMath.clamp((insert - 0.6) / 0.4)
        guard looseOpacity > 0 else { return }
        let resting = state.mode == .idle && state.lift < 0.01 && !reducedMotion
        let spinTiming = GlassLockKeyConstants.UpgradeMotion.spin
        let spinTime = resting ? state.clock.truncatingRemainder(dividingBy: spinTiming.every) : 0
        let spin = max(0.06, abs(cos(.pi * GlassLockKeySceneMath.clamp(spinTime / spinTiming.turn))))
        let start = CGPoint(x: state.kx, y: state.ky)
        let plug = scaled(plug)
        let x = start.x + (Double(plug.x) - start.x) * insert
        let y = start.y + (Double(plug.y) - start.y) * insert

        var key = context
        key.opacity = looseOpacity
        key.translateBy(x: CGFloat(x), y: CGFloat(y))
        key.rotate(by: .radians(GlassLockKeyConstants.UpgradeLayout.insertAngle * insert))
        let liftScale = 1 + 0.05 * state.lift
        key.scaleBy(x: liftScale * spin, y: liftScale)
        let front = scaled(keyFront)
        key.translateBy(x: -front.x, y: -front.y)
        applyFit(GlassLockKeySceneFit.front, centre: GlassLockKeySceneFit.frontCentre, to: &key)
        key.draw(GlassLockKeySceneAssets.keyFront, in: screen)
    }

    private var screen: CGRect { CGRect(origin: .zero, size: size) }

    private func scaled(_ point: CGPoint) -> CGPoint {
        CGPoint(x: point.x * stageScale, y: point.y * stageScale)
    }

    private func applyFit(_ fit: GlassLockKeySceneFit.Value, centre: CGPoint, to context: inout GraphicsContext) {
        let c = CGPoint(x: centre.x * sourceScale, y: centre.y * sourceScale)
        context.translateBy(x: c.x + fit.dx * sourceScale, y: c.y + fit.dy * sourceScale)
        context.rotate(by: .degrees(-fit.rotation))
        context.concatenate(CGAffineTransform(a: 1, b: 0, c: -fit.shear / fit.scaleY, d: 1, tx: 0, ty: 0))
        context.scaleBy(x: fit.scaleX, y: fit.scaleY)
        context.translateBy(x: -c.x, y: -c.y)
    }
}

private struct GlassLockKeyVaultSheet: View {
    let sheet: Double
    let reducedMotion: Bool
    let safeBottom: CGFloat
    let height: Double
    let lock: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            Rectangle().fill(.ultraThinMaterial)
            Rectangle().fill(Color.white.opacity(0.45))
            VStack(spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    Text(GlassLockKeyConstants.Copy.head)
                        .font(.system(size: 20, weight: .bold))
                        .tracking(-0.4)
                        .foregroundStyle(GlassLockKeySceneColor.ink)
                    Spacer()
                    Text(GlassLockKeyConstants.Copy.sheetMeta)
                        .font(.system(size: 12, design: .default).monospacedDigit())
                        .foregroundStyle(GlassLockKeySceneColor.ink.opacity(0.5))
                }
                .padding(.horizontal, 4)
                .padding(.bottom, 12)

                VStack(spacing: 0) {
                    GlassLockKeyVaultRow(index: 0, sheet: sheet, reducedMotion: reducedMotion, symbol: GlassLockKeyConstants.Copy.rows[0].symbol, title: GlassLockKeyConstants.Copy.rows[0].title, subtitle: GlassLockKeyConstants.Copy.rows[0].sub)
                    GlassLockKeyVaultRow(index: 1, sheet: sheet, reducedMotion: reducedMotion, symbol: GlassLockKeyConstants.Copy.rows[1].symbol, title: GlassLockKeyConstants.Copy.rows[1].title, subtitle: GlassLockKeyConstants.Copy.rows[1].sub)
                    GlassLockKeyVaultRow(index: 2, sheet: sheet, reducedMotion: reducedMotion, symbol: GlassLockKeyConstants.Copy.rows[2].symbol, title: GlassLockKeyConstants.Copy.rows[2].title, subtitle: GlassLockKeyConstants.Copy.rows[2].sub)
                }
                .background(Color.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 16, style: .circular))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .circular))

                Button(action: lock) {
                    HStack(spacing: 8) {
                        Image(systemName: "lock.fill")
                            .font(.system(size: 15))
                        Text(GlassLockKeyConstants.Copy.lock)
                            .font(.system(size: 16, weight: .semibold))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(GlassLockKeySceneColor.ink, in: Capsule())
                }
                .buttonStyle(GlassLockKeyPressStyle(reducedMotion: reducedMotion))
                .accessibilityLabel(GlassLockKeyConstants.Copy.lock)
                .padding(.top, 16)
                .padding(.bottom, safeBottom + 16)
            }
            .padding(.horizontal, 16)
            .padding(.top, 24)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height, alignment: .top)
        .clipShape(RoundedRectangle(cornerRadius: 32, style: .circular))
        .overlay(alignment: .top) {
            Rectangle().fill(Color.white.opacity(0.95)).frame(height: 1)
        }
    }
}

private struct GlassLockKeyVaultRow: View {
    let index: Int
    let sheet: Double
    let reducedMotion: Bool
    let symbol: String
    let title: String
    let subtitle: String

    @Environment(\.displayScale) private var displayScale

    private var reveal: Double {
        let rows = GlassLockKeyConstants.UpgradeMotion.rows
        return GlassLockKeySceneMath.clamp((sheet - 0.1 - Double(index) * rows.stagger) / rows.span)
    }

    private var eased: Double { 1 - pow(1 - reveal, 3) }

    var body: some View {
        HStack(spacing: 0) {
            Image(systemName: symbol)
                .font(.system(size: 17))
                .foregroundStyle(GlassLockKeySceneColor.keyBlue)
                .frame(width: 32, height: 32)
                .background(GlassLockKeySceneColor.keyBlue.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .circular))

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(GlassLockKeySceneColor.ink)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(GlassLockKeySceneColor.ink.opacity(0.5))
                    .lineLimit(1)
            }
            .padding(.leading, 12)
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.system(size: 12))
                .foregroundStyle(GlassLockKeySceneColor.ink.opacity(0.3))
        }
        .padding(.horizontal, 16)
        .frame(height: 60)
        .background(alignment: .bottom) {
            if index < 2 {
                Rectangle()
                    .fill(GlassLockKeySceneColor.ink.opacity(0.12))
                    .frame(height: 1 / displayScale)
                    .padding(.leading, 60)
            }
        }
        .opacity(eased)
        .offset(y: reducedMotion ? 0 : GlassLockKeyConstants.UpgradeMotion.rows.rise * (1 - eased))
    }
}

private struct GlassLockKeyPressStyle: ButtonStyle {
    let reducedMotion: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reducedMotion ? 0.97 : 1)
            .animation(
                reducedMotion
                    ? .none
                    : .timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? 0.12 : 0.16),
                value: configuration.isPressed
            )
    }
}

private enum GlassLockKeySceneFit {
    struct Value {
        let scaleX: Double
        let scaleY: Double
        let rotation: Double
        let dx: Double
        let dy: Double
        let shear: Double
    }

    static let lockCentre = CGPoint(x: 660, y: 1416)
    static let keyCentre = CGPoint(x: 436, y: 1383)
    static let frontCentre = CGPoint(x: 602, y: 1309)
    static let lock = Value(scaleX: 1.01, scaleY: 1.01, rotation: 0, dx: 0, dy: 0, shear: 0)
    static let keyClosed = Value(scaleX: 1, scaleY: 1, rotation: 0, dx: 0, dy: 0, shear: 0)
    static let keyOpen = Value(scaleX: 0.95, scaleY: 0.885, rotation: 0, dx: 39, dy: 25.5, shear: -0.075)
    static let front = Value(scaleX: 1.01, scaleY: 0.99, rotation: 0, dx: 0, dy: 2, shear: 0)

    static func interpolate(_ from: Value, _ to: Value, progress: Double) -> Value {
        Value(
            scaleX: from.scaleX + (to.scaleX - from.scaleX) * progress,
            scaleY: from.scaleY + (to.scaleY - from.scaleY) * progress,
            rotation: from.rotation + (to.rotation - from.rotation) * progress,
            dx: from.dx + (to.dx - from.dx) * progress,
            dy: from.dy + (to.dy - from.dy) * progress,
            shear: from.shear + (to.shear - from.shear) * progress
        )
    }
}

enum GlassLockKeySceneAssets {
    static let shadow = image("GlassLockKey-shadow.png")
    static let shackleClosed = image("GlassLockKey-shackle-closed.png")
    static let shackleOpen = image("GlassLockKey-shackle-open.png")
    static let padlockBody = image("GlassLockKey-padlock-body.png")
    static let keyIn = image("GlassLockKey-key-in.png")
    static let keyInA = image("GlassLockKey-key-in-a.png")
    static let keyFront = image("GlassLockKey-key-front.png")

    static func assertAllLoaded() {
        #if DEBUG
        [
            "GlassLockKey-shadow.png", "GlassLockKey-shackle-closed.png", "GlassLockKey-shackle-open.png",
            "GlassLockKey-padlock-body.png", "GlassLockKey-key-in.png", "GlassLockKey-key-in-a.png",
            "GlassLockKey-key-front.png"
        ].forEach { name in
            assert(UIImage(named: name) != nil, "Missing Glass Lock Key asset: \(name)")
        }
        #endif
    }

    private static func image(_ name: String) -> Image {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Glass Lock Key asset: \(name)")
            return Image(uiImage: UIImage())
        }
        return Image(uiImage: image)
    }
}

private enum GlassLockKeySceneColor {
    static let background = Color(red: 239 / 255, green: 239 / 255, blue: 239 / 255)
    static let glow = Color(red: 80 / 255, green: 96 / 255, blue: 1, opacity: 0.35)
    static let ink = Color(red: 22 / 255, green: 24 / 255, blue: 46 / 255)
    static let keyBlue = Color(red: 61 / 255, green: 70 / 255, blue: 224 / 255)
}

private enum GlassLockKeySceneMath {
    static func clamp(_ value: Double, lower: Double = 0, upper: Double = 1) -> Double {
        min(max(value, lower), upper)
    }

    static func easeInOut(_ value: Double) -> Double {
        let value = clamp(value)
        return value < 0.5 ? 4 * value * value * value : 1 - pow(-2 * value + 2, 3) / 2
    }
}
