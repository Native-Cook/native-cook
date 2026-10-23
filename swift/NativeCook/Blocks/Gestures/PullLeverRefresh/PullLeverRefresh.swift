import SwiftUI
import UIKit

/// A physical pull-to-refresh lever. Its model owns input, autoplay, and the display clock;
/// this view is deliberately only the artboard and its accessibility surface.
struct PullLeverRefresh: View {
    static let loop: Double = PullLeverRefreshMotion.loopMS / 1_000

    var autoPlay = false
    var onRefresh: (@MainActor () async throws -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var model: PullLeverRefreshModel

    init(autoPlay: Bool = false, onRefresh: (@MainActor () async throws -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.onRefresh = onRefresh
        _model = State(initialValue: PullLeverRefreshModel(autoPlay: autoPlay, onRefresh: onRefresh))
    }

    var body: some View {
        PullLeverRefreshTouchView(model: model) {
            GeometryReader { proxy in
                let size = proxy.size
                let top = (size.height - PullLeverRefreshTheme.Artboard.height) / 2
                let leverTop = top
                    + PullLeverRefreshTheme.Artboard.leverCentreY
                    - PullLeverRefreshTheme.Lever.thickness / 2

                ZStack(alignment: .topLeading) {
                    PullLeverRefreshTheme.ColorPalette.background

                    PullLeverRefreshLever(model: model)
                        .frame(
                            width: PullLeverRefreshTheme.Lever.length,
                            height: PullLeverRefreshTheme.Lever.thickness,
                            alignment: .topLeading
                        )
                        .offset(
                            x: PullLeverRefreshTheme.Artboard.leverLeft,
                            y: leverTop
                        )

                    if !PullLeverRefreshMotion.replicaOnly {
                        PullLeverRefreshCaption(
                            text: captionText,
                            reduceMotion: reduceMotion
                        )
                        .frame(
                            width: PullLeverRefreshTheme.Artboard.leverLeft
                                - PullLeverRefreshTheme.Layout.captionGap,
                            height: PullLeverRefreshTheme.Typography.caption.lineHeight,
                            alignment: .trailing
                        )
                        .offset(
                            x: 0,
                            y: top
                                + PullLeverRefreshTheme.Artboard.leverCentreY
                                - PullLeverRefreshTheme.Typography.caption.lineHeight / 2
                        )
                    }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
        }
        .ignoresSafeArea()
        .statusBarHidden()
        .onAppear {
            model.configure(autoPlay: autoPlay, reduceMotion: reduceMotion, onRefresh: onRefresh)
            model.startClock()
        }
        .onChange(of: autoPlay) { _, value in
            model.configure(autoPlay: value, reduceMotion: reduceMotion, onRefresh: onRefresh)
        }
        .onChange(of: reduceMotion) { _, value in
            model.configure(autoPlay: autoPlay, reduceMotion: value, onRefresh: onRefresh)
        }
        .onChange(of: scenePhase) { _, value in
            if value == .active {
                model.startClock()
            } else {
                model.stopClock()
            }
        }
        .onDisappear { model.stopClock() }
    }

    private var captionText: String {
        switch model.label {
        case .none:
            ""
        case .refreshing:
            "Checking…"
        case .updated:
            "Updated just now"
        }
    }
}

private struct PullLeverRefreshLever: View {
    @Bindable var model: PullLeverRefreshModel

    private let theme = PullLeverRefreshTheme.self

    var body: some View {
        let t = CGFloat(theme.Lever.thickness)
        let length = CGFloat(theme.Lever.length)
        let knobDiameter = CGFloat(theme.Lever.knob)
        let knobCentreX = length - t / 2
        let knobLeft = knobCentreX - knobDiameter / 2
        let pivot = UnitPoint(x: t / 2 / length, y: 0.5)
        let activePulse = max(model.pulse, 0)
        let run = Double(knobCentreX - t / 2)

        ZStack(alignment: .topLeading) {
            if !PullLeverRefreshMotion.replicaOnly {
                PullLeverRefreshTrack(color: theme.ColorPalette.shadow)
                    .overlay(alignment: .topLeading) {
                        softDisc(color: theme.ColorPalette.shadow, left: knobLeft, size: knobDiameter)
                    }
                    .rotationEffect(.degrees(model.turn), anchor: pivot)
                    .offset(x: PullLeverRefreshMotion.Light.shadow.x, y: PullLeverRefreshMotion.Light.shadow.y)
                    .shadow(
                        color: theme.ColorPalette.shadow,
                        radius: PullLeverRefreshMotion.Light.shadow.blur,
                        x: 0,
                        y: 0
                    )
                    .opacity(PullLeverRefreshMotion.Light.shadow.opacity)

                Circle()
                    .fill(theme.ColorPalette.fill)
                    .frame(width: PullLeverRefreshMotion.Light.haloSize, height: PullLeverRefreshMotion.Light.haloSize)
                    .shadow(color: theme.ColorPalette.fill, radius: PullLeverRefreshMotion.Light.haloBlur)
                    .opacity(haloOpacity)
                    .scaleEffect(haloScale)
                    .offset(
                        x: t / 2 - PullLeverRefreshMotion.Light.haloSize / 2,
                        y: t / 2 - PullLeverRefreshMotion.Light.haloSize / 2
                    )

                PullLeverRefreshArcDots(lit: model.arcLit)
            }

            ZStack(alignment: .topLeading) {
                PullLeverRefreshTrack(color: theme.ColorPalette.track)

                PullLeverRefreshDots(color: theme.ColorPalette.trackDot)

                if model.pulse >= 0 {
                    LinearGradient(
                        colors: theme.ColorPalette.shaftLight,
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: t / 2 + CGFloat(run * activePulse), height: t)
                    .clipShape(Capsule())
                    .opacity(PullLeverRefreshMotion.Light.shaftLight)
                }

                ZStack(alignment: .topLeading) {
                    Capsule()
                        .fill(fillColor)
                        .frame(width: fillWidth, height: t)
                        .overlay(alignment: .topLeading) {
                            PullLeverRefreshDots(color: theme.ColorPalette.fillDot)
                        }
                        .clipShape(Capsule())
                }
                .frame(width: length, height: t, alignment: .topLeading)
                .scaleEffect(model.bloom, anchor: pivot)

                Circle()
                    .fill(theme.ColorPalette.pivot)
                    .frame(width: theme.Lever.pivotDot, height: theme.Lever.pivotDot)
                    .offset(x: (t - theme.Lever.pivotDot) / 2, y: (t - theme.Lever.pivotDot) / 2)

                Circle()
                    .strokeBorder(
                        theme.ColorPalette.fill,
                        lineWidth: PullLeverRefreshMotion.Light.ringWidth
                    )
                    .frame(width: knobDiameter, height: knobDiameter)
                    .scaleEffect(rippleScale)
                    .opacity(rippleOpacity)
                    .offset(x: knobLeft, y: (t - knobDiameter) / 2)

                knobWithHighlight(left: knobLeft, size: knobDiameter)

                PullLeverRefreshCometTrail(pulse: model.pulse)

                Circle()
                    .fill(theme.ColorPalette.fill)
                    .frame(width: PullLeverRefreshMotion.Light.cometHead, height: PullLeverRefreshMotion.Light.cometHead)
                    .shadow(color: theme.ColorPalette.fill, radius: PullLeverRefreshMotion.Light.cometBlur)
                    .opacity(model.pulse >= 0 ? 1 : 0)
                    .offset(
                        x: t / 2 + CGFloat(run * activePulse) - PullLeverRefreshMotion.Light.cometHead / 2,
                        y: (t - PullLeverRefreshMotion.Light.cometHead) / 2
                    )
            }
            .frame(width: length, height: t, alignment: .topLeading)
            .rotationEffect(.degrees(model.turn), anchor: pivot)
        }
        .frame(width: theme.Lever.length, height: t, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var fillWidth: CGFloat {
        if model.fill >= 1 { return CGFloat(theme.Lever.length) }
        let dots = floor(
            max(model.fill, 0) * Double(theme.Lever.dots)
                + PullLeverRefreshMotion.Interaction.quantizationEpsilon
        )
        return CGFloat(theme.Lever.thickness) + CGFloat(dots) * CGFloat(theme.Lever.pitch)
    }

    private var fillColor: Color {
        PullLeverRefreshColourInterpolation.gamma(
            from: theme.ColorPalette.dimFill,
            to: theme.ColorPalette.fill,
            amount: model.lit
        )
    }

    private var haloOpacity: Double {
        let breath = (model.breath + 1) / 2
        return model.glow * (PullLeverRefreshMotion.Light.haloMin + (PullLeverRefreshMotion.Light.haloMax - PullLeverRefreshMotion.Light.haloMin) * breath)
    }

    private var haloScale: Double {
        let breath = (model.breath + 1) / 2
        let light = PullLeverRefreshMotion.Light.self
        return (light.haloBaseScale + light.haloGlowScale * model.glow)
            * (1 + light.haloBreathScale * breath)
    }

    private var rippleOpacity: Double {
        guard model.ripple > 0, model.ripple < 1 else { return 0 }
        return PullLeverRefreshMotion.Light.rippleOpacity * (1 - model.ripple)
    }

    private var rippleScale: Double {
        1 + ((PullLeverRefreshMotion.Light.ripple / theme.Lever.knob) - 1) * model.ripple
    }

    private var highlightOffset: CGSize {
        let angle = (PullLeverRefreshMotion.Light.direction - model.turn) * .pi / 180
        let radius = theme.Lever.knob * PullLeverRefreshMotion.Light.highlightOffset
        return CGSize(width: cos(angle) * radius, height: sin(angle) * radius)
    }

    private var highlightOpacity: Double {
        min(
            abs(model.turn - PullLeverRefreshMotion.Light.restAngle)
                / PullLeverRefreshMotion.Light.highlightRamp,
            1
        )
    }

    @ViewBuilder
    private func softDisc(color: Color, left: CGFloat, size: CGFloat) -> some View {
        let soft = CGFloat(PullLeverRefreshMotion.Light.knobSoft)
        PullLeverRefreshSoftDisc(color: color, diameter: size)
            .offset(
                x: left - soft,
                y: (CGFloat(theme.Lever.thickness) - size) / 2 - soft
            )
    }

    private func knobWithHighlight(left: CGFloat, size: CGFloat) -> some View {
        ZStack {
            PullLeverRefreshSoftDisc(color: theme.ColorPalette.finger, diameter: size)

            PullLeverRefreshSoftDisc(color: theme.ColorPalette.knobLit, diameter: size)
                .opacity(model.knobLit)

            if !PullLeverRefreshMotion.replicaOnly {
                Circle()
                    .fill(theme.ColorPalette.specular)
                    .frame(
                        width: size * PullLeverRefreshMotion.Light.highlightSize,
                        height: size * PullLeverRefreshMotion.Light.highlightSize
                    )
                    .shadow(
                        color: theme.ColorPalette.fill.opacity(PullLeverRefreshMotion.Light.specularShadowOpacity),
                        radius: PullLeverRefreshMotion.Light.specularShadowBlur
                    )
                    .offset(highlightOffset)
                    .opacity(highlightOpacity)
            }
        }
        .frame(width: size, height: size)
        .offset(x: left, y: (CGFloat(theme.Lever.thickness) - size) / 2)
    }
}

private struct PullLeverRefreshSoftDisc: View {
    let color: Color
    let diameter: CGFloat

    var body: some View {
        let radius = diameter / 2
        let soft = CGFloat(PullLeverRefreshMotion.Light.knobSoft)
        let outerRadius = radius + soft
        let solidStop = (radius - soft) / outerRadius

        RadialGradient(
            stops: [
                .init(color: color, location: solidStop),
                .init(color: color.opacity(0), location: 1)
            ],
            center: .center,
            startRadius: 0,
            endRadius: outerRadius
        )
        .frame(width: diameter + 2 * soft, height: diameter + 2 * soft)
    }
}

private struct PullLeverRefreshTrack: View {
    let color: Color

    var body: some View {
        Capsule()
            .fill(color)
            .frame(
                width: PullLeverRefreshTheme.Lever.length,
                height: PullLeverRefreshTheme.Lever.thickness
            )
    }
}

private struct PullLeverRefreshDots: View {
    let color: Color

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(1...PullLeverRefreshTheme.Lever.dots, id: \.self) { index in
                Circle()
                    .fill(color)
                    .frame(
                        width: PullLeverRefreshTheme.Lever.dot,
                        height: PullLeverRefreshTheme.Lever.dot
                    )
                    .offset(
                        x: PullLeverRefreshTheme.Lever.thickness / 2
                            + PullLeverRefreshTheme.Lever.pitch * CGFloat(index)
                            - PullLeverRefreshTheme.Lever.dot / 2,
                        y: (PullLeverRefreshTheme.Lever.thickness - PullLeverRefreshTheme.Lever.dot) / 2
                    )
            }
        }
        .frame(
            width: PullLeverRefreshTheme.Lever.length,
            height: PullLeverRefreshTheme.Lever.thickness,
            alignment: .topLeading
        )
    }
}

private struct PullLeverRefreshArcDots: View {
    let lit: Double

    var body: some View {
        let theme = PullLeverRefreshTheme.self
        let light = PullLeverRefreshMotion.Light.self
        ZStack(alignment: .topLeading) {
            ForEach(0..<light.arc.dots, id: \.self) { index in
                let fraction = Double(index) / Double(light.arc.dots - 1)
                let degrees = light.arc.from + (light.arc.to - light.arc.from) * fraction
                let radians = degrees * .pi / 180
                let on = min(max(lit - Double(index), 0), 1)
                Circle()
                    .fill(PullLeverRefreshColourInterpolation.gamma(
                        from: theme.ColorPalette.arcOff,
                        to: theme.ColorPalette.fill,
                        amount: on
                    ))
                    .frame(width: light.arc.size, height: light.arc.size)
                    .scaleEffect(1 + light.arcLitScale * on)
                    .offset(
                        x: theme.Lever.thickness / 2 + cos(radians) * light.arc.radius - light.arc.size / 2,
                        y: theme.Lever.thickness / 2 + sin(radians) * light.arc.radius - light.arc.size / 2
                    )
            }
        }
        .frame(width: theme.Lever.length, height: theme.Lever.thickness, alignment: .topLeading)
    }
}

private struct PullLeverRefreshCometTrail: View {
    let pulse: Double

    var body: some View {
        let theme = PullLeverRefreshTheme.self
        let light = PullLeverRefreshMotion.Light.self
        let knobCentreX = theme.Lever.length - theme.Lever.thickness / 2
        let run = knobCentreX - theme.Lever.thickness / 2
        ZStack(alignment: .topLeading) {
            ForEach(1...light.cometTrail, id: \.self) { index in
                let progress = pulse - Double(index) * light.cometTrailPt
                    / Double(light.cometTrail) / run
                let visible = pulse >= 0 && progress >= 0
                Circle()
                    .fill(theme.ColorPalette.fill)
                    .frame(width: light.cometHead, height: light.cometHead)
                    .shadow(
                        color: theme.ColorPalette.fill.opacity(light.trailShadowOpacity),
                        radius: light.trailShadowBlur
                    )
                    .opacity(visible ? 1 - Double(index) / Double(light.cometTrail + 1) : 0)
                    .scaleEffect(1 - Double(index) / Double(light.cometTrail + 2))
                    .offset(
                        x: theme.Lever.thickness / 2 + run * max(progress, 0) - light.cometHead / 2,
                        y: (theme.Lever.thickness - light.cometHead) / 2
                    )
            }
        }
        .frame(width: theme.Lever.length, height: theme.Lever.thickness, alignment: .topLeading)
    }
}

private enum PullLeverRefreshColourInterpolation {
    /// Reanimated's default `interpolateColor` works in gamma-corrected sRGB (gamma 2.2).
    static func gamma(from: Color, to: Color, amount: Double) -> Color {
        let clamped = min(max(amount, 0), 1)
        let source = UIColor(from)
        let target = UIColor(to)
        var sourceRed = CGFloat.zero
        var sourceGreen = CGFloat.zero
        var sourceBlue = CGFloat.zero
        var sourceAlpha = CGFloat.zero
        var targetRed = CGFloat.zero
        var targetGreen = CGFloat.zero
        var targetBlue = CGFloat.zero
        var targetAlpha = CGFloat.zero
        source.getRed(&sourceRed, green: &sourceGreen, blue: &sourceBlue, alpha: &sourceAlpha)
        target.getRed(&targetRed, green: &targetGreen, blue: &targetBlue, alpha: &targetAlpha)
        func interpolate(_ a: CGFloat, _ b: CGFloat) -> CGFloat {
            let value = pow((1 - clamped) * pow(a, 2.2) + clamped * pow(b, 2.2), 1 / 2.2)
            return (value * 255).rounded() / 255
        }
        return Color(
            red: interpolate(sourceRed, targetRed),
            green: interpolate(sourceGreen, targetGreen),
            blue: interpolate(sourceBlue, targetBlue),
            opacity: sourceAlpha + (targetAlpha - sourceAlpha) * clamped
        )
    }
}
