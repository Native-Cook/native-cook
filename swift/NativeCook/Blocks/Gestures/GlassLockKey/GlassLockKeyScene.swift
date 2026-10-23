import SwiftUI
import UIKit

struct GlassLockKeyScene: View {
    let state: GlassLockKeySimulationState
    let geometry: GlassLockKeyGeometry
    let size: CGSize
    let safeAreaInsets: EdgeInsets
    let reducedMotion: Bool
    let send: (GlassLockKeyInput) -> Void
    let command: (GlassLockKeyCommand) -> Void

    private var sheetHeight: CGFloat { size.height * GlassLockKeyConstants.Sheet.heightRatio }

    var body: some View {
        ZStack(alignment: .topLeading) {
            GlassLockKeyArtwork(state: state, geometry: geometry)
                .frame(width: size.width, height: size.height)
                .accessibilityHidden(true)

            GlassLockKeyTouch(
                isOpen: state.isOpen,
                accessibilityFrame: keyAccessibilityFrame,
                onInput: send,
                onCommand: command
            )
            .frame(width: size.width, height: size.height)

            header
            hint
            sheet
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color(uiColor: GlassLockKeyPalette.bgTop))
    }

    private var header: some View {
        let locked = 1 - GlassLockKeyMath.clamp((state.shackle - 0.1) / 0.3)
        let opened = GlassLockKeyMath.clamp((state.shackle - 0.5) / 0.3)
        return VStack(alignment: .leading, spacing: 0) {
            Text(GlassLockKeyConstants.Copy.eyebrow)
                .font(GlassLockKeyAssets.medium(11))
                .tracking(GlassLockKeyConstants.Typography.eyebrowTracking)
                .foregroundStyle(Color(uiColor: GlassLockKeyPalette.mute))
                .frame(height: 16, alignment: .topLeading)

            ZStack(alignment: .topLeading) {
                Text(GlassLockKeyConstants.Copy.locked)
                    .opacity(locked)
                    .offset(y: (reducedMotion ? 0 : -6) * (1 - locked))
                Text(GlassLockKeyConstants.Copy.unlocked)
                    .opacity(opened)
                    .offset(y: (reducedMotion ? 0 : 6) * (1 - opened))
            }
            .font(GlassLockKeyAssets.serif(GlassLockKeyConstants.Typography.titleSize))
            .tracking(-0.5)
            .foregroundStyle(Color(uiColor: GlassLockKeyPalette.ink))
            .frame(height: GlassLockKeyConstants.Typography.titleLine, alignment: .topLeading)
            .padding(.top, GlassLockKeyConstants.Typography.gap)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(state.isOpen ? GlassLockKeyConstants.Copy.unlocked : GlassLockKeyConstants.Copy.locked)
            .accessibilityAddTraits(.isHeader)
        }
        .frame(width: size.width - GlassLockKeyConstants.Typography.side * 2, alignment: .leading)
        .offset(
            x: GlassLockKeyConstants.Typography.side,
            y: safeAreaInsets.top + GlassLockKeyConstants.Typography.top
        )
        .allowsHitTesting(false)
    }

    private var hint: some View {
        Text(state.wrongHint ? GlassLockKeyConstants.Copy.wrongWay : GlassLockKeyConstants.Copy.hint)
            .font(GlassLockKeyAssets.regular(GlassLockKeyConstants.Typography.hintSize))
            .foregroundStyle(Color(uiColor: state.wrongHint ? GlassLockKeyPalette.error : GlassLockKeyPalette.mute))
            .frame(width: size.width - GlassLockKeyConstants.Typography.side * 2, height: GlassLockKeyConstants.Typography.hintLine)
            .position(
                x: size.width / 2,
                y: size.height - safeAreaInsets.bottom - GlassLockKeyConstants.Typography.bottom
                    - GlassLockKeyConstants.Typography.hintLine / 2
            )
            .opacity(1 - GlassLockKeyMath.clamp(state.sheet * 3))
            .allowsHitTesting(false)
    }

    private var sheet: some View {
        let shown = GlassLockKeyMath.clamp(state.sheet)
        let travel = (1 - shown) * (sheetHeight + 40)
        return GlassLockKeyVaultSheet(
            progress: shown,
            reducedMotion: reducedMotion,
            safeBottom: safeAreaInsets.bottom,
            lock: { command(.lock) }
        )
        .frame(width: size.width, height: sheetHeight)
        .frame(width: size.width, height: size.height, alignment: .bottom)
        .opacity(reducedMotion ? shown : 1)
        .offset(y: reducedMotion ? 0 : travel)
        .allowsHitTesting(state.isOpen)
        .accessibilityHidden(!state.isOpen)
    }

    private var keyAccessibilityFrame: CGRect {
        CGRect(
            x: geometry.restX - 44,
            y: geometry.restY,
            width: 88,
            height: (GlassLockKeyConstants.Key.blade + GlassLockKeyConstants.Key.ring) * geometry.scale
        )
    }
}

private struct GlassLockKeyArtwork: UIViewRepresentable {
    let state: GlassLockKeySimulationState
    let geometry: GlassLockKeyGeometry

    func makeUIView(context: Context) -> GlassLockKeyArtworkView {
        GlassLockKeyArtworkView()
    }

    func updateUIView(_ view: GlassLockKeyArtworkView, context: Context) {
        view.state = state
        view.geometry = geometry
        view.setNeedsDisplay()
    }
}

private final class GlassLockKeyArtworkView: UIView {
    var state: GlassLockKeySimulationState?
    var geometry: GlassLockKeyGeometry?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true
        backgroundColor = GlassLockKeyPalette.bgTop
        contentMode = .redraw
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is unavailable")
    }

    override func draw(_ rect: CGRect) {
        guard let state, let geometry, let context = UIGraphicsGetCurrentContext() else { return }
        GlassLockKeyPainter(state: state, geometry: geometry, size: bounds.size).draw(in: context)
    }
}

private struct GlassLockKeyPainter {
    let state: GlassLockKeySimulationState
    let geometry: GlassLockKeyGeometry
    let size: CGSize

    private var scale: CGFloat { geometry.scale }
    private var inserted: CGFloat {
        switch state.mode {
        case .inserting, .seated, .open, .closing: 1
        case .idle, .carrying: 0
        }
    }
    private var shake: CGFloat {
        guard state.shakeTime >= 0 else { return 0 }
        let progress = state.shakeTime / GlassLockKeyConstants.Shake.duration
        return GlassLockKeyConstants.Shake.amplitude * (1 - progress)
            * sin(2 * .pi * GlassLockKeyConstants.Shake.cycles * progress)
    }
    private var bob: CGFloat {
        guard state.mode == .idle, !state.reduced else { return 0 }
        return sin(2 * .pi * state.clock / GlassLockKeyConstants.Carry.bobPeriod)
            * GlassLockKeyConstants.Carry.bob * scale
    }

    func draw(in context: CGContext) {
        let poolY = geometry.lockTop + GlassLockKeyConstants.Lock.height * geometry.scale / 2
        GlassLockKeyBackgroundCache.image(size: size, poolY: poolY, scale: scale).draw(in: CGRect(origin: .zero, size: size))

        context.saveGState()
        let centerX = geometry.width / 2
        let centerY = geometry.lockTop + GlassLockKeyConstants.Lock.height * geometry.scale / 2
        let rigScale = 1 - (1 - GlassLockKeyConstants.Sheet.shrink) * state.sheet
        context.translateBy(
            x: centerX,
            y: centerY - GlassLockKeyConstants.Sheet.rise * geometry.height * state.sheet
        )
        context.scaleBy(x: rigScale, y: rigScale)
        context.translateBy(x: -centerX, y: -centerY)

        drawShadows(in: context)
        if inserted > 0 { drawKey(in: context, opacity: inserted) }
        drawLock(in: context)
        if inserted < 1 { drawKey(in: context, opacity: 1 - inserted) }
        context.restoreGState()
    }

    private func drawShadows(in context: CGContext) {
        context.saveGState()
        context.setShadow(offset: .zero, blur: 16 * scale, color: GlassLockKeyPalette.shadow.cgColor)
        context.setFillColor(GlassLockKeyPalette.shadow.withAlphaComponent(0.45).cgColor)
        context.fillEllipse(in: CGRect(
            x: geometry.faceX + 6 * scale,
            y: geometry.lockBottom + 30 * scale,
            width: (GlassLockKeyConstants.Lock.face + 10) * scale,
            height: 20 * scale
        ))
        context.restoreGState()

        context.saveGState()
        context.translateBy(
            x: state.keyX,
            y: state.keyY + (GlassLockKeyConstants.Key.blade + GlassLockKeyConstants.Key.ring + 18) * scale
        )
        let shadowScale = scale * (1 - 0.2 * state.lift)
        context.scaleBy(x: shadowScale, y: shadowScale)
        context.setShadow(offset: .zero, blur: 8, color: GlassLockKeyPalette.shadow.cgColor)
        context.setFillColor(GlassLockKeyPalette.shadow.withAlphaComponent(0.55).cgColor)
        context.fillEllipse(in: CGRect(x: -24, y: 0, width: 48, height: 10))
        context.restoreGState()
    }

    private func drawLock(in context: CGContext) {
        context.saveGState()
        context.translateBy(
            x: geometry.faceX + shake * scale,
            y: geometry.lockTop + state.bounce * scale
        )
        context.scaleBy(x: scale, y: scale)

        drawShackle(in: context)
        let face = GlassLockKeyPaths.face(inset: 0)
        for step in [1.0, 0.85, 0.7, 0.55, 0.4, 0.25, 0.1] {
            context.saveGState()
            context.translateBy(x: GlassLockKeyConstants.Lock.side * step, y: -GlassLockKeyConstants.Lock.side * 0.5 * step)
            GlassLockKeyDrawing.fillLinear(
                face, in: context,
                colors: [GlassLockKeyPalette.brassSide, GlassLockKeyPalette.brassSideDeep],
                locations: [0, 1],
                start: .zero,
                end: CGPoint(x: 0, y: GlassLockKeyConstants.Lock.height)
            )
            context.restoreGState()
        }

        GlassLockKeyDrawing.fillLinear(
            face, in: context,
            colors: [GlassLockKeyPalette.brassHigh, GlassLockKeyPalette.brass, GlassLockKeyPalette.brassDeep],
            locations: [0, 0.45, 1],
            start: .zero,
            end: CGPoint(x: GlassLockKeyConstants.Lock.face * 0.4, y: GlassLockKeyConstants.Lock.height)
        )
        GlassLockKeyDrawing.fillRadial(
            face, in: context,
            colors: [UIColor(red: 1, green: 240 / 255, blue: 200 / 255, alpha: 0.55), .clear],
            locations: [0, 1],
            center: CGPoint(x: GlassLockKeyConstants.Lock.face * 0.3, y: GlassLockKeyConstants.Lock.height * 0.22),
            radius: GlassLockKeyConstants.Lock.face * 0.7
        )

        context.saveGState()
        context.addPath(face)
        context.clip()
        for y in stride(from: 0.0, to: GlassLockKeyConstants.Lock.height, by: 3.0) {
            let light = Int(y / 3).isMultiple(of: 2)
            context.setStrokeColor((light ? UIColor(red: 1, green: 240 / 255, blue: 205 / 255, alpha: 0.10) : UIColor(red: 80 / 255, green: 50 / 255, blue: 12 / 255, alpha: 0.08)).cgColor)
            context.setLineWidth(0.6)
            context.move(to: CGPoint(x: 0, y: y))
            context.addLine(to: CGPoint(x: GlassLockKeyConstants.Lock.face, y: y + 0.6))
            context.strokePath()
        }
        if state.glintTime >= 0 {
            let progress = state.glintTime / GlassLockKeyConstants.Glint.duration
            let alpha = sin(.pi * progress)
            if alpha > 0 {
                let x = -60 + (GlassLockKeyConstants.Lock.face + 120) * progress
                context.saveGState()
                context.concatenate(CGAffineTransform(a: 1, b: 0, c: -0.35, d: 1, tx: 0, ty: 0))
                context.setShadow(offset: .zero, blur: 9, color: GlassLockKeyPalette.glint.withAlphaComponent(alpha).cgColor)
                context.setFillColor(GlassLockKeyPalette.glint.withAlphaComponent(alpha * 0.7).cgColor)
                context.fill(CGRect(x: x, y: -20, width: 30, height: GlassLockKeyConstants.Lock.height + 40))
                context.restoreGState()
            }
        }
        context.restoreGState()

        drawMark(in: context)
        GlassLockKeyDrawing.strokeLinear(
            face, in: context, lineWidth: 1.4,
            colors: [GlassLockKeyPalette.brassEdge, UIColor(red: 1, green: 236 / 255, blue: 190 / 255, alpha: 0.15), UIColor(red: 60 / 255, green: 36 / 255, blue: 8 / 255, alpha: 0.4)],
            locations: [0, 0.5, 1], start: .zero,
            end: CGPoint(x: 0, y: GlassLockKeyConstants.Lock.height)
        )
        context.addPath(GlassLockKeyPaths.face(inset: 5))
        context.setStrokeColor(UIColor(red: 1, green: 236 / 255, blue: 190 / 255, alpha: 0.22).cgColor)
        context.setLineWidth(0.8)
        context.strokePath()

        drawPlug(in: context)
        context.restoreGState()
    }

    private func drawShackle(in context: CGContext) {
        context.saveGState()
        let hingeX = GlassLockKeyConstants.Lock.face / 2
            + GlassLockKeyConstants.Lock.opening / 2
            + GlassLockKeyConstants.Lock.shackle / 2
        context.translateBy(x: 0, y: -GlassLockKeyConstants.Lock.openLift * state.shackle)
        context.translateBy(x: hingeX, y: GlassLockKeyConstants.Lock.longLeg)
        context.rotate(by: GlassLockKeyConstants.Lock.openTilt * state.shackle)
        context.translateBy(x: -hingeX, y: -GlassLockKeyConstants.Lock.longLeg)

        let path = GlassLockKeyPaths.shackle()
        GlassLockKeyDrawing.strokeLinear(
            path, in: context, lineWidth: GlassLockKeyConstants.Lock.shackle,
            colors: [GlassLockKeyPalette.steelHigh, GlassLockKeyPalette.steel, GlassLockKeyPalette.steelDeep],
            locations: [0, 0.45, 1],
            start: CGPoint(x: 0, y: -GlassLockKeyConstants.Lock.shackleRise),
            end: CGPoint(x: GlassLockKeyConstants.Lock.face, y: GlassLockKeyConstants.Lock.longLeg)
        )
        context.saveGState()
        context.translateBy(x: -8, y: -4)
        context.addPath(path)
        context.setLineWidth(3)
        context.setLineCap(.butt)
        context.setStrokeColor(GlassLockKeyPalette.steelEdge.withAlphaComponent(0.8).cgColor)
        context.setShadow(offset: .zero, blur: 1.2, color: GlassLockKeyPalette.steelEdge.withAlphaComponent(0.5).cgColor)
        context.strokePath()
        context.restoreGState()
        context.saveGState()
        context.translateBy(x: 9, y: 2)
        context.addPath(path)
        context.setLineWidth(2)
        context.setLineCap(.butt)
        context.setStrokeColor(UIColor(red: 20 / 255, green: 24 / 255, blue: 28 / 255, alpha: 0.35).cgColor)
        context.strokePath()
        context.restoreGState()
        context.restoreGState()
    }

    private func drawMark(in context: CGContext) {
        let mark = "HALDEN"
        let font = GlassLockKeyAssets.uiFont("Inter-SemiBold", size: 13)
        let smallFont = GlassLockKeyAssets.uiFont("Inter-Medium", size: 7.5)
        let widths = mark.map { String($0).size(withAttributes: [.font: font]).width }
        let total = widths.reduce(0, +) + CGFloat(max(mark.count - 1, 0)) * 4
        var x = GlassLockKeyConstants.Lock.face / 2 - total / 2
        let baseline = GlassLockKeyConstants.Lock.height * 0.42
        for (index, character) in mark.enumerated() {
            let string = String(character) as NSString
            string.draw(
                at: CGPoint(x: x, y: baseline + 0.8 - font.ascender),
                withAttributes: [.font: font, .foregroundColor: GlassLockKeyPalette.engraveLight]
            )
            string.draw(
                at: CGPoint(x: x, y: baseline - font.ascender),
                withAttributes: [.font: font, .foregroundColor: GlassLockKeyPalette.engrave]
            )
            x += widths[index] + 4
        }
        let size = "40 MM · HARDENED" as NSString
        let sizeWidth = size.size(withAttributes: [.font: smallFont]).width
        size.draw(
            at: CGPoint(x: GlassLockKeyConstants.Lock.face / 2 - sizeWidth / 2, y: baseline + 16 - smallFont.ascender),
            withAttributes: [.font: smallFont, .foregroundColor: GlassLockKeyPalette.engrave]
        )
    }

    private func drawPlug(in context: CGContext) {
        let centerX = GlassLockKeyConstants.Lock.face / 2
        let near: Double
        if state.mode == .carrying {
            near = GlassLockKeyMath.clamp(
                1 - hypot(state.keyX - geometry.plugX, state.keyY - geometry.plugY)
                    / (GlassLockKeyConstants.Carry.magnet * 2 * geometry.scale)
            ) * 0.8
        } else if state.mode == .seated {
            near = 0.45 + 0.35 * GlassLockKeyMath.clamp(state.turn / 1.57)
        } else {
            near = 0
        }
        if near > 0 {
            context.saveGState()
            context.setShadow(offset: .zero, blur: 16, color: GlassLockKeyPalette.glow.withAlphaComponent(near).cgColor)
            context.setFillColor(GlassLockKeyPalette.glow.withAlphaComponent(near * 0.55).cgColor)
            context.fillEllipse(in: CGRect(
                x: centerX - GlassLockKeyConstants.Lock.glowRadius,
                y: GlassLockKeyConstants.Lock.height + GlassLockKeyConstants.Lock.plugHeight / 2 - GlassLockKeyConstants.Lock.glowRadius,
                width: GlassLockKeyConstants.Lock.glowRadius * 2,
                height: GlassLockKeyConstants.Lock.glowRadius * 2
            ))
            context.restoreGState()
        }
        let plug = UIBezierPath(
            roundedRect: CGRect(
                x: centerX - GlassLockKeyConstants.Lock.plugWidth / 2,
                y: GlassLockKeyConstants.Lock.height - 3,
                width: GlassLockKeyConstants.Lock.plugWidth,
                height: GlassLockKeyConstants.Lock.plugHeight + 3
            ),
            cornerRadius: 4
        ).cgPath
        GlassLockKeyDrawing.fillLinear(
            plug, in: context,
            colors: [GlassLockKeyPalette.brassDeep, GlassLockKeyPalette.plug, GlassLockKeyPalette.brassDeep],
            locations: [0, 0.5, 1],
            start: CGPoint(x: centerX - GlassLockKeyConstants.Lock.plugWidth / 2, y: 0),
            end: CGPoint(x: centerX + GlassLockKeyConstants.Lock.plugWidth / 2, y: 0)
        )
        context.setFillColor(GlassLockKeyPalette.slot.cgColor)
        context.fill(CGRect(
            x: centerX - GlassLockKeyConstants.Lock.slot / 2,
            y: GlassLockKeyConstants.Lock.height + 1,
            width: GlassLockKeyConstants.Lock.slot,
            height: GlassLockKeyConstants.Lock.plugHeight - 2
        ))
    }

    private func drawKey(in context: CGContext, opacity: CGFloat) {
        context.saveGState()
        context.setAlpha(opacity)
        let tilt = state.mode == .carrying
            ? GlassLockKeyMath.clamp(state.keyVelocityX * 0.0003, lower: -0.12, upper: 0.12)
            : 0
        context.translateBy(
            x: state.keyX + shake * scale * inserted,
            y: state.keyY + bob
        )
        context.rotate(by: tilt)
        let keyScale = scale * (1 + (GlassLockKeyConstants.Carry.lift - 1) * state.lift)
        context.scaleBy(x: keyScale, y: keyScale)

        let blade = GlassLockKeyPaths.blade()
        GlassLockKeyDrawing.fillLinear(
            blade, in: context,
            colors: [GlassLockKeyPalette.steelDeep, GlassLockKeyPalette.steelHigh, GlassLockKeyPalette.steel, GlassLockKeyPalette.steelDeep],
            locations: [0, 0.35, 0.6, 1],
            start: CGPoint(x: -GlassLockKeyConstants.Key.shaft, y: 0),
            end: CGPoint(x: GlassLockKeyConstants.Key.shaft + GlassLockKeyConstants.Key.bitWidth, y: 0)
        )
        context.addPath(blade)
        context.setLineWidth(0.8)
        context.setStrokeColor(UIColor(red: 30 / 255, green: 34 / 255, blue: 38 / 255, alpha: 0.5).cgColor)
        context.strokePath()

        let angle = state.turn * GlassLockKeyConstants.Turn.visual
        let cosine = cos(angle)
        let sine = sin(angle)
        let bowScale = max(0.02, abs(cosine))
        let edgeAlpha = min(1, abs(sine) * 1.6)
        if edgeAlpha > 0 {
            drawBow(in: context, offsetX: 7 * sine, scaleX: bowScale, color: GlassLockKeyPalette.steelDeep.withAlphaComponent(edgeAlpha))
            drawBow(in: context, offsetX: 3.5 * sine, scaleX: bowScale, color: GlassLockKeyPalette.steel.withAlphaComponent(edgeAlpha))
        }
        drawBowFront(in: context, scaleX: bowScale)
        context.restoreGState()
    }

    private func drawBow(in context: CGContext, offsetX: CGFloat, scaleX: CGFloat, color: UIColor) {
        context.saveGState()
        let centerY = GlassLockKeyConstants.Key.blade + GlassLockKeyConstants.Key.ring / 2 - 2
        context.translateBy(x: offsetX, y: centerY)
        context.scaleBy(x: scaleX, y: 1)
        context.translateBy(x: 0, y: -centerY)
        context.addPath(GlassLockKeyPaths.ring())
        context.setFillColor(color.cgColor)
        context.drawPath(using: .eoFill)
        context.restoreGState()
    }

    private func drawBowFront(in context: CGContext, scaleX: CGFloat) {
        context.saveGState()
        let centerY = GlassLockKeyConstants.Key.blade + GlassLockKeyConstants.Key.ring / 2 - 2
        context.translateBy(x: 0, y: centerY)
        context.scaleBy(x: scaleX, y: 1)
        context.translateBy(x: 0, y: -centerY)
        let ring = GlassLockKeyPaths.ring()
        GlassLockKeyDrawing.fillLinear(
            ring, in: context,
            colors: [GlassLockKeyPalette.steelHigh, GlassLockKeyPalette.steel, GlassLockKeyPalette.steelDeep],
            locations: [0, 0.5, 1],
            start: CGPoint(x: -GlassLockKeyConstants.Key.ring / 2, y: centerY - GlassLockKeyConstants.Key.ring / 2),
            end: CGPoint(x: GlassLockKeyConstants.Key.ring / 2, y: centerY + GlassLockKeyConstants.Key.ring / 2),
            evenOdd: true
        )
        context.addPath(ring)
        context.setLineWidth(1)
        context.setStrokeColor(GlassLockKeyPalette.steelEdge.withAlphaComponent(0.7).cgColor)
        context.strokePath()
        context.restoreGState()
    }
}

private enum GlassLockKeyPaths {
    static func face(inset: CGFloat) -> CGPath {
        UIBezierPath(
            roundedRect: CGRect(
                x: inset,
                y: inset,
                width: GlassLockKeyConstants.Lock.face - inset * 2,
                height: GlassLockKeyConstants.Lock.height - inset * 2
            ),
            cornerRadius: max(4, GlassLockKeyConstants.Lock.radius - inset)
        ).cgPath
    }

    static func shackle() -> CGPath {
        let half = GlassLockKeyConstants.Lock.opening / 2 + GlassLockKeyConstants.Lock.shackle / 2
        let centerX = GlassLockKeyConstants.Lock.face / 2
        let top = -GlassLockKeyConstants.Lock.shackleRise + GlassLockKeyConstants.Lock.shackle / 2
        let middleY = top + half
        let control = half * 0.55228475
        let path = CGMutablePath()
        path.move(to: CGPoint(x: centerX - half, y: GlassLockKeyConstants.Lock.shortLeg))
        path.addLine(to: CGPoint(x: centerX - half, y: middleY))
        path.addCurve(
            to: CGPoint(x: centerX, y: top),
            control1: CGPoint(x: centerX - half, y: middleY - control),
            control2: CGPoint(x: centerX - control, y: top)
        )
        path.addCurve(
            to: CGPoint(x: centerX + half, y: middleY),
            control1: CGPoint(x: centerX + control, y: top),
            control2: CGPoint(x: centerX + half, y: middleY - control)
        )
        path.addLine(to: CGPoint(x: centerX + half, y: GlassLockKeyConstants.Lock.longLeg))
        return path
    }

    static func ring() -> CGPath {
        let path = CGMutablePath()
        let centerY = GlassLockKeyConstants.Key.blade + GlassLockKeyConstants.Key.ring / 2 - 2
        path.addEllipse(in: CGRect(
            x: -GlassLockKeyConstants.Key.ring / 2,
            y: centerY - GlassLockKeyConstants.Key.ring / 2,
            width: GlassLockKeyConstants.Key.ring,
            height: GlassLockKeyConstants.Key.ring
        ))
        let inner = GlassLockKeyConstants.Key.ring - GlassLockKeyConstants.Key.rim * 2
        path.addEllipse(in: CGRect(x: -inner / 2, y: centerY - inner / 2, width: inner, height: inner))
        return path
    }

    static func blade() -> CGPath {
        let key = GlassLockKeyConstants.Key.self
        let half = key.shaft / 2
        let path = CGMutablePath()
        path.move(to: CGPoint(x: -half, y: key.blade))
        path.addLine(to: CGPoint(x: -half, y: 4))
        path.addQuadCurve(to: .zero, control: CGPoint(x: -half, y: 0))
        path.addQuadCurve(to: CGPoint(x: half, y: 4), control: CGPoint(x: half, y: 0))
        path.addLine(to: CGPoint(x: half, y: key.bitFromTip))
        path.addLine(to: CGPoint(x: half + key.bitWidth, y: key.bitFromTip))
        path.addLine(to: CGPoint(x: half + key.bitWidth, y: key.bitFromTip + 9))
        path.addLine(to: CGPoint(x: half + key.bitWidth - 6, y: key.bitFromTip + 9))
        path.addLine(to: CGPoint(x: half + key.bitWidth - 6, y: key.bitFromTip + 15))
        path.addLine(to: CGPoint(x: half + key.bitWidth, y: key.bitFromTip + 15))
        path.addLine(to: CGPoint(x: half + key.bitWidth, y: key.bitFromTip + key.bitHeight))
        path.addLine(to: CGPoint(x: half, y: key.bitFromTip + key.bitHeight))
        path.addLine(to: CGPoint(x: half, y: key.blade))
        path.closeSubpath()
        path.addPath(UIBezierPath(
            roundedRect: CGRect(x: -key.collar / 2, y: key.blade - 8, width: key.collar, height: 8),
            cornerRadius: 3
        ).cgPath)
        return path
    }
}

private enum GlassLockKeyDrawing {
    static func fillLinear(
        _ path: CGPath,
        in context: CGContext,
        colors: [UIColor],
        locations: [CGFloat],
        start: CGPoint,
        end: CGPoint,
        evenOdd: Bool = false
    ) {
        context.saveGState()
        context.addPath(path)
        context.clip(using: evenOdd ? .evenOdd : .winding)
        drawLinear(in: context, colors: colors, locations: locations, start: start, end: end)
        context.restoreGState()
    }

    static func fillRadial(
        _ path: CGPath,
        in context: CGContext,
        colors: [UIColor],
        locations: [CGFloat],
        center: CGPoint,
        radius: CGFloat
    ) {
        guard let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: colors.map(\.cgColor) as CFArray,
            locations: locations
        ) else { return }
        context.saveGState()
        context.addPath(path)
        context.clip()
        context.drawRadialGradient(
            gradient,
            startCenter: center,
            startRadius: 0,
            endCenter: center,
            endRadius: radius,
            options: [.drawsAfterEndLocation]
        )
        context.restoreGState()
    }

    static func strokeLinear(
        _ path: CGPath,
        in context: CGContext,
        lineWidth: CGFloat,
        colors: [UIColor],
        locations: [CGFloat],
        start: CGPoint,
        end: CGPoint
    ) {
        context.saveGState()
        context.addPath(path)
        context.setLineWidth(lineWidth)
        context.setLineCap(.butt)
        context.replacePathWithStrokedPath()
        context.clip()
        drawLinear(in: context, colors: colors, locations: locations, start: start, end: end)
        context.restoreGState()
    }

    private static func drawLinear(
        in context: CGContext,
        colors: [UIColor],
        locations: [CGFloat],
        start: CGPoint,
        end: CGPoint
    ) {
        guard let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: colors.map(\.cgColor) as CFArray,
            locations: locations
        ) else { return }
        context.drawLinearGradient(
            gradient,
            start: start,
            end: end,
            options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
        )
    }
}

private enum GlassLockKeyBackgroundCache {
    private static let cache = NSCache<NSString, UIImage>()

    static func image(size: CGSize, poolY: CGFloat, scale: CGFloat) -> UIImage {
        let key = "\(Int(size.width.rounded()))x\(Int(size.height.rounded()))" as NSString
        if let image = cache.object(forKey: key) { return image }
        let format = UIGraphicsImageRendererFormat.preferred()
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            let context = renderer.cgContext
            GlassLockKeyDrawing.fillLinear(
                CGPath(rect: CGRect(origin: .zero, size: size), transform: nil),
                in: context,
                colors: [GlassLockKeyPalette.bgTop, GlassLockKeyPalette.bgBottom],
                locations: [0, 1],
                start: .zero,
                end: CGPoint(x: 0, y: size.height)
            )
            guard let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: [GlassLockKeyPalette.pool.cgColor, UIColor.clear.cgColor] as CFArray,
                locations: [0, 1]
            ) else { return }
            let center = CGPoint(x: size.width / 2, y: poolY - 40 * scale)
            context.drawRadialGradient(
                gradient,
                startCenter: center,
                startRadius: 0,
                endCenter: center,
                endRadius: size.width * 0.85,
                options: [.drawsAfterEndLocation]
            )
        }
        cache.setObject(image, forKey: key)
        return image
    }
}

private struct GlassLockKeyVaultSheet: View {
    let progress: Double
    let reducedMotion: Bool
    let safeBottom: CGFloat
    let lock: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(Color.white.opacity(0.16))
                .frame(width: 36, height: 4)
                .padding(.bottom, 16)

            HStack(alignment: .firstTextBaseline) {
                Text(GlassLockKeyConstants.Copy.head)
                    .font(GlassLockKeyAssets.semibold(17))
                    .foregroundStyle(Color(uiColor: GlassLockKeyPalette.ink))
                Spacer(minLength: 12)
                Text(GlassLockKeyConstants.Copy.meta)
                    .font(GlassLockKeyAssets.regular(13))
                    .foregroundStyle(Color(uiColor: GlassLockKeyPalette.mute))
                    .lineLimit(1)
            }

            VStack(spacing: 0) {
                ForEach(Array(GlassLockKeyConstants.Copy.rows.enumerated()), id: \.element.id) { index, row in
                    GlassLockKeySheetRow(
                        row: row,
                        showDivider: index < GlassLockKeyConstants.Copy.rows.count - 1,
                        progress: rowProgress(index)
                    )
                }
            }
            .padding(.top, 8)

            Spacer(minLength: 0)

            Button(action: lock) {
                HStack(spacing: 8) {
                    GlassLockKeyLockGlyph()
                        .frame(width: 16, height: 16)
                    Text(GlassLockKeyConstants.Copy.lock)
                        .font(GlassLockKeyAssets.semibold(16))
                }
                .foregroundStyle(Color(uiColor: GlassLockKeyPalette.buttonInk))
                .frame(maxWidth: .infinity, minHeight: GlassLockKeyConstants.Sheet.button)
                .background(Color(uiColor: GlassLockKeyPalette.button))
                .clipShape(Capsule())
            }
            .buttonStyle(GlassLockKeyButtonStyle())
            .accessibilityLabel(GlassLockKeyConstants.Copy.lock)
        }
        .padding(.horizontal, GlassLockKeyConstants.Sheet.pad)
        .padding(.top, 8)
        .padding(.bottom, safeBottom + GlassLockKeyConstants.Sheet.pad)
        .background(
            UnevenRoundedRectangle(
                topLeadingRadius: GlassLockKeyConstants.Sheet.radius,
                bottomLeadingRadius: 0,
                bottomTrailingRadius: 0,
                topTrailingRadius: GlassLockKeyConstants.Sheet.radius
            )
            .fill(Color(uiColor: GlassLockKeyPalette.sheet))
            .overlay {
                UnevenRoundedRectangle(
                    topLeadingRadius: GlassLockKeyConstants.Sheet.radius,
                    bottomLeadingRadius: 0,
                    bottomTrailingRadius: 0,
                    topTrailingRadius: GlassLockKeyConstants.Sheet.radius
                )
                .stroke(Color(uiColor: GlassLockKeyPalette.sheetEdge), lineWidth: 0.5)
            }
        )
    }

    private func rowProgress(_ index: Int) -> Double {
        GlassLockKeyMath.clamp(
            (progress - 0.35 - Double(index) * GlassLockKeyConstants.SheetMotion.stagger * 2) / 0.45
        )
    }
}

private struct GlassLockKeySheetRow: View {
    let row: GlassLockKeyConstants.Copy.Row
    let showDivider: Bool
    let progress: Double

    var body: some View {
        HStack(spacing: 0) {
            GlassLockKeyRowGlyph(kind: row.icon)
                .frame(width: 20, height: 20)
                .frame(width: GlassLockKeyConstants.Sheet.icon, height: GlassLockKeyConstants.Sheet.icon)
                .background(Color(uiColor: GlassLockKeyPalette.rowIcon))
                .clipShape(RoundedRectangle(cornerRadius: GlassLockKeyConstants.Sheet.iconRadius))

            VStack(alignment: .leading, spacing: 1) {
                Text(row.title)
                    .font(GlassLockKeyAssets.medium(16))
                    .foregroundStyle(Color(uiColor: GlassLockKeyPalette.ink))
                    .frame(height: 21, alignment: .leading)
                Text(row.subtitle)
                    .font(GlassLockKeyAssets.regular(13))
                    .foregroundStyle(Color(uiColor: GlassLockKeyPalette.mute))
                    .frame(height: 18, alignment: .leading)
                    .lineLimit(1)
            }
            .padding(.leading, 12)

            Spacer(minLength: 8)

            Path { path in
                path.move(to: CGPoint(x: 0, y: 0))
                path.addLine(to: CGPoint(x: 8, y: 0))
                path.addLine(to: CGPoint(x: 8, y: 8))
            }
            .stroke(Color(uiColor: GlassLockKeyPalette.faint), style: StrokeStyle(lineWidth: 1.5, lineCap: .square, lineJoin: .miter))
            .frame(width: 8, height: 8)
            .rotationEffect(.degrees(45))
            .padding(.trailing, 4)
        }
        .frame(height: GlassLockKeyConstants.Sheet.row)
        .overlay(alignment: .bottom) {
            if showDivider {
                Rectangle()
                    .fill(Color(uiColor: GlassLockKeyPalette.sheetLine))
                    .frame(height: 0.5)
            }
        }
        .opacity(progress)
        .offset(y: (1 - progress) * 16)
    }
}

private struct GlassLockKeyRowGlyph: View {
    let kind: String

    var body: some View {
        Canvas { context, _ in
            let color = Color(uiColor: GlassLockKeyPalette.rowIconInk)
            if kind == "codes" {
                for row in 0..<3 {
                    for column in 0..<3 {
                        let rect = CGRect(x: 3 + Double(column) * 5.2, y: 3 + Double(row) * 5.2, width: 3.6, height: 3.6)
                        context.fill(
                            Path(roundedRect: rect, cornerRadius: 1),
                            with: .color(color.opacity((row + column).isMultiple(of: 2) ? 1 : 0.55))
                        )
                    }
                }
            } else if kind == "doc" {
                var outline = Path()
                outline.move(to: CGPoint(x: 5, y: 2.5))
                outline.addLine(to: CGPoint(x: 11.5, y: 2.5))
                outline.addLine(to: CGPoint(x: 15, y: 6))
                outline.addLine(to: CGPoint(x: 15, y: 17.5))
                outline.addLine(to: CGPoint(x: 5, y: 17.5))
                outline.closeSubpath()
                context.stroke(outline, with: .color(color), style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
                var lines = Path()
                lines.move(to: CGPoint(x: 8, y: 10)); lines.addLine(to: CGPoint(x: 12.5, y: 10))
                lines.move(to: CGPoint(x: 8, y: 13)); lines.addLine(to: CGPoint(x: 12.5, y: 13))
                context.stroke(lines, with: .color(color), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
            } else {
                for arc in [
                    (CGRect(x: 3, y: 4, width: 14, height: 8), Angle.degrees(210), Angle.degrees(330)),
                    (CGRect(x: 5.5, y: 8, width: 9, height: 5.5), Angle.degrees(210), Angle.degrees(330)),
                    (CGRect(x: 8, y: 12, width: 4, height: 3), Angle.degrees(210), Angle.degrees(330)),
                ] {
                    var path = Path()
                    path.addArc(center: CGPoint(x: arc.0.midX, y: arc.0.maxY), radius: arc.0.width / 2, startAngle: arc.1, endAngle: arc.2, clockwise: false)
                    context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                }
                context.fill(Path(ellipseIn: CGRect(x: 8.7, y: 14.7, width: 2.6, height: 2.6)), with: .color(color))
            }
        }
    }
}

private struct GlassLockKeyLockGlyph: View {
    var body: some View {
        Canvas { context, _ in
            let color = Color(uiColor: GlassLockKeyPalette.buttonInk)
            context.fill(Path(roundedRect: CGRect(x: 2.5, y: 7, width: 11, height: 7.5), cornerRadius: 2), with: .color(color))
            var shackle = Path()
            shackle.move(to: CGPoint(x: 5, y: 7))
            shackle.addLine(to: CGPoint(x: 5, y: 5))
            shackle.addCurve(to: CGPoint(x: 11, y: 5), control1: CGPoint(x: 5, y: 1), control2: CGPoint(x: 11, y: 1))
            shackle.addLine(to: CGPoint(x: 11, y: 7))
            context.stroke(shackle, with: .color(color), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
        }
    }
}

private struct GlassLockKeyButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

enum GlassLockKeyMath {
    static func clamp(_ value: Double, lower: Double = 0, upper: Double = 1) -> Double {
        min(max(value, lower), upper)
    }
}
