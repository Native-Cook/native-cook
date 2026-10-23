import SwiftUI
import UIKit

struct TiltPosterParallaxPoster: View {
    let width: CGFloat
    let height: CGFloat
    let tiltX: Double
    let tiltY: Double
    let lift: Double
    let flip: Double
    let clock: Double
    let reduceMotion: Bool

    private var turn: Double {
        tiltX + (reduceMotion ? 0 : flip * .pi)
    }

    private var scale: CGFloat {
        CGFloat(
            lift
                * (1 + sin(flip * .pi) * TiltPosterParallaxTokens.Flip.rise)
        )
    }

    private var frontOpacity: Double {
        reduceMotion ? 1 - min(1, max(0, flip)) : (cos(turn) >= 0 ? 1 : 0)
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            posterGroup
                .frame(width: width, height: height)
                .scaleEffect(scale, anchor: .top)
                .offset(
                    x: TiltPosterParallaxTokens.Layout.cardMargin,
                    y: TiltPosterParallaxTokens.Layout.cardMargin + floatOffset
                )
        }
        .frame(
            width: width + TiltPosterParallaxTokens.Layout.cardMargin * 2,
            height: height * CGFloat(TiltPosterParallaxTokens.Tilt.lift) + TiltPosterParallaxTokens.Layout.cardMargin * 2,
            alignment: .topLeading
        )
        .accessibilityHidden(true)
    }

    private var posterGroup: some View {
        ZStack {
            glow
            shadow
            edges
            ZStack {
                TiltPosterParallaxPosterFront(width: width, height: height, tiltX: tiltX, tiltY: tiltY, flip: flip)
                    .opacity(frontOpacity)
                TiltPosterParallaxPosterBack(width: width, height: height, tiltX: tiltX, tiltY: tiltY)
                    .scaleEffect(x: reduceMotion ? 1 : -1, y: 1)
                    .opacity(1 - frontOpacity)
            }
            .frame(width: width, height: height)
            .rotation3DEffect(
                .radians(-tiltY),
                axis: (x: 1, y: 0, z: 0),
                perspective: width / TiltPosterParallaxTokens.Tilt.perspective
            )
            .rotation3DEffect(
                .radians(turn),
                axis: (x: 0, y: 1, z: 0),
                perspective: width / TiltPosterParallaxTokens.Tilt.perspective
            )
        }
        .frame(width: width, height: height)
    }

    private var floatOffset: CGFloat {
        -CGFloat((1 - cos(clock / TiltPosterParallaxTokens.Float.period * .pi * 2)) / 2)
            * TiltPosterParallaxTokens.Float.amplitude
    }

    private var glow: some View {
        let motion = TiltPosterParallaxTokens.Render.self
        let opacity = min(
            1,
            ((lift - 1) / (TiltPosterParallaxTokens.Tilt.lift - 1)) * motion.glowLiftGain
                + max(0, abs(tiltX) + abs(tiltY) - motion.glowDeadZone) * motion.glowTiltGain
        )
        return RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius)
            .fill(TiltPosterParallaxTokens.Palette.glow)
            .frame(width: width * motion.glowWidthRatio, height: height * motion.glowHeightRatio)
            .blur(radius: motion.glowBlur)
            .opacity(opacity)
            .offset(
                x: width * (motion.glowInsetXRatio + motion.glowWidthRatio / 2 - 0.5)
                    + CGFloat(tiltX) * motion.glowXGain,
                y: height * (motion.glowInsetYRatio + motion.glowHeightRatio / 2 - 0.5)
                    + motion.glowRestY
                    + CGFloat(tiltY) * motion.glowYGain
            )
    }

    private var shadow: some View {
        let motion = TiltPosterParallaxTokens.Render.self
        let narrow = CGFloat(max(Double(motion.shadowEdgeMinimum), abs(cos(reduceMotion ? 0 : flip * .pi))))
        return RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius)
            .fill(TiltPosterParallaxTokens.Palette.shadow)
            .frame(width: width, height: height)
            .scaleEffect(x: motion.shadowScale * narrow, y: motion.shadowScale)
            .blur(radius: motion.shadowBlur)
            .opacity(motion.shadowBaseOpacity + (lift - 1) * motion.shadowLiftGain)
            .offset(
                x: CGFloat(tiltX) * motion.shadowXGain,
                y: motion.shadowRestY + CGFloat(tiltY) * motion.shadowYGain
            )
    }

    private var edges: some View {
        let sine = sin(turn)
        let pitchSine = sin(-tiltY)
        return ZStack {
            TiltPosterParallaxAssets.edgeSide
                .resizable()
                .frame(width: TiltPosterParallaxTokens.Edge.depth, height: height)
                .offset(x: width / 2)
                .rotation3DEffect(.degrees(90), axis: (x: 0, y: 1, z: 0))
                .opacity(edgeOpacity(-cos(-tiltY) * sine))
            TiltPosterParallaxAssets.edgeSide
                .resizable()
                .frame(width: TiltPosterParallaxTokens.Edge.depth, height: height)
                .offset(x: -width / 2)
                .rotation3DEffect(.degrees(90), axis: (x: 0, y: 1, z: 0))
                .opacity(edgeOpacity(cos(-tiltY) * sine))
            TiltPosterParallaxAssets.edgeTop
                .resizable()
                .frame(width: width, height: TiltPosterParallaxTokens.Edge.depth)
                .offset(y: -height / 2)
                .rotation3DEffect(.degrees(-90), axis: (x: 1, y: 0, z: 0))
                .opacity(edgeOpacity(-pitchSine))
            TiltPosterParallaxAssets.edgeTop
                .resizable()
                .frame(width: width, height: TiltPosterParallaxTokens.Edge.depth)
                .offset(y: height / 2)
                .rotation3DEffect(.degrees(-90), axis: (x: 1, y: 0, z: 0))
                .opacity(edgeOpacity(pitchSine))
        }
        .frame(width: width, height: height)
        .rotation3DEffect(
            .radians(-tiltY),
            axis: (x: 1, y: 0, z: 0),
            perspective: width / TiltPosterParallaxTokens.Tilt.perspective
        )
        .rotation3DEffect(
            .radians(turn),
            axis: (x: 0, y: 1, z: 0),
            perspective: width / TiltPosterParallaxTokens.Tilt.perspective
        )
    }

    private func edgeOpacity(_ normal: Double) -> Double {
        let lower = sin(TiltPosterParallaxTokens.Edge.from)
        let upper = sin(TiltPosterParallaxTokens.Edge.to)
        return min(1, max(0, (normal - lower) / (upper - lower)))
    }
}

private struct TiltPosterParallaxPosterFront: View {
    let width: CGFloat
    let height: CGFloat
    let tiltX: Double
    let tiltY: Double
    let flip: Double

    var body: some View {
        ZStack {
            TiltPosterParallaxAssets.plate
                .resizable()
                .scaledToFill()
                .frame(
                    width: width * TiltPosterParallaxTokens.Layout.plateOverscan,
                    height: height * TiltPosterParallaxTokens.Layout.plateOverscan
                )
                .offset(
                    x: CGFloat(tiltX) * TiltPosterParallaxTokens.Parallax.plate,
                    y: CGFloat(tiltY) * TiltPosterParallaxTokens.Parallax.plate
                )
            TiltPosterParallaxAssets.figure
                .resizable()
                .scaledToFill()
                .frame(width: width, height: height)
                .offset(
                    x: CGFloat(tiltX) * TiltPosterParallaxTokens.Parallax.figure,
                    y: CGFloat(tiltY) * TiltPosterParallaxTokens.Parallax.figure
                )
            TiltPosterParallaxWordmark(width: width, height: height, tiltX: tiltX, tiltY: tiltY, flip: flip)
            glare
            RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius)
                .stroke(TiltPosterParallaxTokens.Palette.rim, lineWidth: TiltPosterParallaxTokens.Render.rimWidth)
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius))
    }

    private var glare: some View {
        let x = width * (0.5 + CGFloat(tiltX) * TiltPosterParallaxTokens.Parallax.glare)
        let y = height * (TiltPosterParallaxTokens.Render.glareRestY + CGFloat(tiltY) * TiltPosterParallaxTokens.Parallax.glare)
        let opacity = TiltPosterParallaxTokens.Render.glareRestOpacity
            + min(
                TiltPosterParallaxTokens.Render.glareRange,
                (abs(tiltX) + abs(tiltY)) * TiltPosterParallaxTokens.Render.glareGain
            )
        return Circle()
            .fill(
                RadialGradient(
                    colors: [TiltPosterParallaxTokens.Palette.glare, .clear],
                    center: .center,
                    startRadius: 0,
                    endRadius: width * TiltPosterParallaxTokens.Render.glareRadiusRatio
                )
            )
            .frame(
                width: width * TiltPosterParallaxTokens.Render.glareRadiusRatio * 2,
                height: width * TiltPosterParallaxTokens.Render.glareRadiusRatio * 2
            )
            .blendMode(.screen)
            .opacity(opacity)
            .position(x: x, y: y)
    }
}

private struct TiltPosterParallaxWordmark: View {
    let width: CGFloat
    let height: CGFloat
    let tiltX: Double
    let tiltY: Double
    let flip: Double

    private var titleWidth: CGFloat {
        let font = UIFont(name: TiltPosterParallaxTokens.Typeface.script, size: TiltPosterParallaxTokens.Typeface.posterTitle)
            ?? UIFont.systemFont(ofSize: TiltPosterParallaxTokens.Typeface.posterTitle, weight: .bold)
        return (TiltPosterParallaxTokens.Copy.title as NSString).size(withAttributes: [.font: font]).width
    }

    var body: some View {
        ZStack {
            titleText
                .foregroundStyle(TiltPosterParallaxTokens.Render.titleShadow)
                .offset(x: TiltPosterParallaxTokens.Render.titleShadowX, y: TiltPosterParallaxTokens.Render.titleShadowY)
            titleText
                .foregroundStyle(
                    LinearGradient(colors: TiltPosterParallaxTokens.Palette.gold, startPoint: .topLeading, endPoint: .bottomTrailing)
                )
            Rectangle()
                .fill(
                    LinearGradient(
                        colors: TiltPosterParallaxTokens.Palette.holoMirrored,
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .frame(
                    width: titleWidth * TiltPosterParallaxTokens.Holo.period * 10,
                    height: TiltPosterParallaxTokens.Typeface.posterTitle * 1.5
                )
                .offset(x: holoOffset)
                .mask(titleText)
                .blendMode(.overlay)
                .opacity(holoOpacity)
            Rectangle()
                .fill(
                    LinearGradient(
                        colors: [.clear, TiltPosterParallaxTokens.Palette.foil, .clear],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .frame(width: TiltPosterParallaxTokens.Render.foilHalfWidth * 2, height: TiltPosterParallaxTokens.Typeface.posterTitle * 1.5)
                .offset(x: foilOffset)
                .blendMode(.screen)
                .mask(titleText)
        }
        .frame(width: titleWidth + TiltPosterParallaxTokens.Render.foilHalfWidth * 2, height: TiltPosterParallaxTokens.Typeface.posterTitle * 1.5)
        .transformEffect(CGAffineTransform(a: 1, b: 0, c: TiltPosterParallaxTokens.Layout.titleSkew, d: 1, tx: 0, ty: 0))
        .position(
            x: width / 2 + CGFloat(tiltX) * TiltPosterParallaxTokens.Parallax.title,
            y: height * TiltPosterParallaxTokens.Layout.titleY - TiltPosterParallaxTokens.Typeface.posterTitle * 0.35
                + CGFloat(tiltY) * TiltPosterParallaxTokens.Parallax.title
        )
    }

    private var titleText: some View {
        Text(TiltPosterParallaxTokens.Copy.title)
            .font(.custom(TiltPosterParallaxTokens.Typeface.script, fixedSize: TiltPosterParallaxTokens.Typeface.posterTitle).weight(.bold))
            .fixedSize()
    }

    private var holoOpacity: Double {
        TiltPosterParallaxTokens.Holo.rest
            + min(
                TiltPosterParallaxTokens.Holo.max - TiltPosterParallaxTokens.Holo.rest,
                (abs(tiltX) + abs(tiltY)) * TiltPosterParallaxTokens.Holo.gain
            )
    }

    private var holoOffset: CGFloat {
        CGFloat(
            tiltX * Double(TiltPosterParallaxTokens.Holo.travelX)
                + tiltY * Double(TiltPosterParallaxTokens.Holo.travelY)
                + flip * Double(TiltPosterParallaxTokens.Holo.flipTravel)
        ) * titleWidth
    }

    private var foilOffset: CGFloat {
        let at = 0.5 + CGFloat((tiltX + tiltY) / 2) * TiltPosterParallaxTokens.Parallax.foil
        return titleWidth * (at - 0.5)
    }
}

private struct TiltPosterParallaxPosterBack: View {
    let width: CGFloat
    let height: CGFloat
    let tiltX: Double
    let tiltY: Double

    var body: some View {
        ZStack {
            TiltPosterParallaxAssets.plate
                .resizable()
                .scaledToFill()
                .frame(
                    width: width * TiltPosterParallaxTokens.Layout.plateOverscan,
                    height: height * TiltPosterParallaxTokens.Layout.plateOverscan
                )
                .opacity(TiltPosterParallaxTokens.Back.plateOpacity)
                .blur(radius: TiltPosterParallaxTokens.Back.plateBlur)
            LinearGradient(colors: TiltPosterParallaxTokens.Palette.back, startPoint: .top, endPoint: .bottom)
            Text(TiltPosterParallaxTokens.Copy.title)
                .font(.custom(TiltPosterParallaxTokens.Typeface.script, fixedSize: TiltPosterParallaxTokens.Typeface.backTitle).weight(.bold))
                .foregroundStyle(
                    LinearGradient(colors: TiltPosterParallaxTokens.Palette.gold, startPoint: .topLeading, endPoint: .bottomTrailing)
                )
                .position(x: width / 2, y: height * TiltPosterParallaxTokens.Back.titleY - TiltPosterParallaxTokens.Typeface.backTitle * 0.3)
            Capsule()
                .fill(TiltPosterParallaxTokens.Palette.backRule)
                .frame(width: TiltPosterParallaxTokens.Back.rule, height: 1)
                .position(x: width / 2, y: height * TiltPosterParallaxTokens.Back.ruleY)
            ForEach(TiltPosterParallaxTokens.Copy.credits) { credit in
                Text(credit.text)
                    .font(
                        .custom(TiltPosterParallaxTokens.Typeface.ui, fixedSize: TiltPosterParallaxTokens.Typeface.credit)
                            .weight(credit.strong ? .bold : .medium)
                    )
                    .foregroundStyle(credit.strong ? TiltPosterParallaxTokens.Palette.backInk : TiltPosterParallaxTokens.Palette.backMute)
                    .position(
                        x: width / 2,
                        y: height * TiltPosterParallaxTokens.Back.creditsY
                            + CGFloat(credit.id) * TiltPosterParallaxTokens.Back.lineStep
                            + credit.gap
                    )
            }
            glare
            RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius)
                .stroke(TiltPosterParallaxTokens.Palette.rim, lineWidth: TiltPosterParallaxTokens.Render.rimWidth)
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius))
    }

    private var glare: some View {
        let x = width * (0.5 + CGFloat(tiltX) * TiltPosterParallaxTokens.Parallax.glare)
        let y = height * (TiltPosterParallaxTokens.Render.glareRestY + CGFloat(tiltY) * TiltPosterParallaxTokens.Parallax.glare)
        let opacity = TiltPosterParallaxTokens.Render.glareRestOpacity
            + min(
                TiltPosterParallaxTokens.Render.glareRange,
                (abs(tiltX) + abs(tiltY)) * TiltPosterParallaxTokens.Render.glareGain
            )
        return Circle()
            .fill(
                RadialGradient(
                    colors: [TiltPosterParallaxTokens.Palette.glare, .clear],
                    center: .center,
                    startRadius: 0,
                    endRadius: width * TiltPosterParallaxTokens.Render.glareRadiusRatio
                )
            )
            .frame(
                width: width * TiltPosterParallaxTokens.Render.glareRadiusRatio * 2,
                height: width * TiltPosterParallaxTokens.Render.glareRadiusRatio * 2
            )
            .blendMode(.screen)
            .opacity(opacity)
            .position(x: x, y: y)
    }
}
