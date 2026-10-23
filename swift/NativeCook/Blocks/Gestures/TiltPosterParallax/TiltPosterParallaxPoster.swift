import SwiftUI
import UIKit

struct TiltPosterParallaxPoster: View {
    let width: CGFloat
    let height: CGFloat
    let art: TiltPosterParallaxArt
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
        CGFloat(lift * (1 + sin(flip * .pi) * TiltPosterParallaxTokens.Flip.rise))
    }

    private var frontOpacity: Double {
        reduceMotion ? 1 - min(1, max(0, flip)) : (cos(turn) >= 0 ? 1 : 0)
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            posterGroup
                .frame(width: width, height: height)
                .scaleEffect(scale)
                .offset(
                    x: TiltPosterParallaxTokens.Layout.cardMargin,
                    y: TiltPosterParallaxTokens.Layout.cardMargin + floatOffset
                )
        }
        .frame(
            width: width + TiltPosterParallaxTokens.Layout.cardMargin * 2,
            height: height + TiltPosterParallaxTokens.Layout.cardMargin * 2,
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
                TiltPosterParallaxPosterFront(
                    width: width,
                    height: height,
                    art: art,
                    tiltX: tiltX,
                    tiltY: tiltY,
                    flip: flip
                )
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
        guard !reduceMotion else { return 0 }
        return -CGFloat((1 - cos(clock / TiltPosterParallaxTokens.Float.period * .pi * 2)) / 2)
            * TiltPosterParallaxTokens.Float.amplitude
    }

    private var glow: some View {
        let motion = TiltPosterParallaxTokens.Render.self
        let liftRange = max(TiltPosterParallaxTokens.Tilt.lift - 1, 0.001)
        let opacity = min(
            1,
            ((lift - 1) / liftRange) * motion.glowLiftGain
                + max(0, abs(tiltX) + abs(tiltY) - motion.glowDeadZone) * motion.glowTiltGain
        )
        return RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius)
            .fill(TiltPosterParallaxTokens.Palette.glow)
            .frame(width: width * motion.glowWidthRatio, height: height * motion.glowHeightRatio)
            .blur(radius: motion.glowBlur)
            .opacity(opacity)
            .offset(
                x: width * motion.glowInsetXRatio + CGFloat(tiltX) * motion.glowXGain,
                y: height * motion.glowInsetYRatio + motion.glowRestY + CGFloat(tiltY) * motion.glowYGain
            )
    }

    private var shadow: some View {
        let motion = TiltPosterParallaxTokens.Render.self
        let narrow = CGFloat(max(Double(motion.shadowEdgeMinimum), abs(cos(reduceMotion ? 0 : flip * .pi))))
        return RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius)
            .fill(TiltPosterParallaxTokens.Palette.shadow)
            .frame(width: width, height: height)
            .scaleEffect(x: motion.shadowScaleX * narrow, y: motion.shadowScaleY)
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
            Rectangle()
                .fill(LinearGradient(colors: TiltPosterParallaxTokens.Palette.edge, startPoint: .leading, endPoint: .trailing))
                .frame(width: TiltPosterParallaxTokens.Edge.depth, height: height)
                .offset(x: width / 2)
                .rotation3DEffect(.degrees(90), axis: (x: 0, y: 1, z: 0))
                .opacity(edgeOpacity(-cos(-tiltY) * sine))
            Rectangle()
                .fill(LinearGradient(colors: TiltPosterParallaxTokens.Palette.edge, startPoint: .leading, endPoint: .trailing))
                .frame(width: TiltPosterParallaxTokens.Edge.depth, height: height)
                .offset(x: -width / 2)
                .rotation3DEffect(.degrees(90), axis: (x: 0, y: 1, z: 0))
                .opacity(edgeOpacity(cos(-tiltY) * sine))
            Rectangle()
                .fill(LinearGradient(colors: TiltPosterParallaxTokens.Palette.edge, startPoint: .top, endPoint: .bottom))
                .frame(width: width, height: TiltPosterParallaxTokens.Edge.depth)
                .offset(y: -height / 2)
                .rotation3DEffect(.degrees(-90), axis: (x: 1, y: 0, z: 0))
                .opacity(edgeOpacity(-pitchSine))
            Rectangle()
                .fill(LinearGradient(colors: TiltPosterParallaxTokens.Palette.edge, startPoint: .top, endPoint: .bottom))
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
    let art: TiltPosterParallaxArt
    let tiltX: Double
    let tiltY: Double
    let flip: Double

    var body: some View {
        ZStack {
            ForEach(Array(art.layers.enumerated()), id: \.offset) { index, image in
                Image(uiImage: image)
                    .resizable()
                    .frame(
                        width: width * TiltPosterParallaxArtFactory.overscan[index],
                        height: height * TiltPosterParallaxArtFactory.overscan[index]
                    )
                    .offset(
                        x: CGFloat(tiltX) * TiltPosterParallaxTokens.Parallax.layers[index],
                        y: CGFloat(tiltY) * TiltPosterParallaxTokens.Parallax.layers[index]
                    )
            }
            TiltPosterParallaxWordmark(width: width, height: height, tiltX: tiltX, tiltY: tiltY, flip: flip)
            TiltPosterParallaxPosterCredit(width: width, height: height)
            TiltPosterParallaxPosterGlare(width: width, height: height, tiltX: tiltX, tiltY: tiltY)
            RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius)
                .stroke(TiltPosterParallaxTokens.Palette.rim, lineWidth: TiltPosterParallaxTokens.Render.rimWidth)
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius))
    }
}

private struct TiltPosterParallaxWordmark: View {
    let width: CGFloat
    let height: CGFloat
    let tiltX: Double
    let tiltY: Double
    let flip: Double

    private var titleWidth: CGFloat {
        let font = UIFont(name: TiltPosterParallaxTokens.Typeface.serif, size: TiltPosterParallaxTokens.Typeface.posterTitle)
            ?? UIFont.systemFont(ofSize: TiltPosterParallaxTokens.Typeface.posterTitle)
        return TiltPosterParallaxTokens.Copy.posterTitle
            .map { ($0 as NSString).size(withAttributes: [.font: font]).width }
            .max() ?? 0
    }

    var body: some View {
        ZStack {
            Rectangle()
                .fill(TiltPosterParallaxTokens.Palette.embossDark)
                .mask(titleMask)
                .offset(
                    x: TiltPosterParallaxTokens.Render.titleShadowX - CGFloat(tiltX) * 4,
                    y: TiltPosterParallaxTokens.Render.titleShadowY - CGFloat(tiltY) * 4
                )
            Rectangle()
                .fill(TiltPosterParallaxTokens.Palette.embossLight)
                .mask(titleMask)
                .offset(
                    x: TiltPosterParallaxTokens.Render.titleLightX + CGFloat(tiltX) * 2,
                    y: TiltPosterParallaxTokens.Render.titleLightY + CGFloat(tiltY) * 2
                )
            LinearGradient(colors: TiltPosterParallaxTokens.Palette.gold, startPoint: .topLeading, endPoint: .bottomTrailing)
                .mask(titleMask)
            Rectangle()
                .fill(LinearGradient(colors: TiltPosterParallaxTokens.Palette.holoMirrored, startPoint: .leading, endPoint: .trailing))
                .frame(width: titleWidth * TiltPosterParallaxTokens.Holo.period * 10, height: height)
                .offset(x: holoOffset)
                .mask(titleMask)
                .blendMode(.overlay)
                .opacity(holoOpacity)
            Rectangle()
                .fill(LinearGradient(colors: [.clear, TiltPosterParallaxTokens.Palette.foil, .clear], startPoint: .leading, endPoint: .trailing))
                .frame(width: TiltPosterParallaxTokens.Render.foilHalfWidth * 2, height: height)
                .offset(x: foilOffset)
                .mask(titleMask)
                .blendMode(.screen)
        }
        .frame(width: width, height: height)
        .offset(
            x: CGFloat(tiltX) * TiltPosterParallaxTokens.Parallax.title,
            y: CGFloat(tiltY) * TiltPosterParallaxTokens.Parallax.title
        )
    }

    private var titleMask: some View {
        TiltPosterParallaxTitleMask(
            lines: TiltPosterParallaxTokens.Copy.posterTitle,
            width: width,
            height: height,
            firstBaseline: height * TiltPosterParallaxTokens.Layout.titleY,
            lineStep: TiltPosterParallaxTokens.Typeface.posterLine,
            fontName: TiltPosterParallaxTokens.Typeface.serif,
            fontSize: TiltPosterParallaxTokens.Typeface.posterTitle
        )
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

private struct TiltPosterParallaxTitleMask: View {
    let lines: [String]
    let width: CGFloat
    let height: CGFloat
    let firstBaseline: CGFloat
    let lineStep: CGFloat
    let fontName: String
    let fontSize: CGFloat

    var body: some View {
        Canvas { context, _ in
            let uiFont = UIFont(name: fontName, size: fontSize) ?? UIFont.systemFont(ofSize: fontSize)
            for (index, line) in lines.enumerated() {
                let text = context.resolve(Text(line).font(.custom(fontName, fixedSize: fontSize)).foregroundStyle(.white))
                let measured = text.measure(in: CGSize(
                    width: CGFloat.greatestFiniteMagnitude,
                    height: CGFloat.greatestFiniteMagnitude
                ))
                context.draw(
                    text,
                    at: CGPoint(
                        x: (width - measured.width) / 2,
                        y: firstBaseline + CGFloat(index) * lineStep - uiFont.ascender
                    ),
                    anchor: .topLeading
                )
            }
        }
        .frame(width: width, height: height)
    }
}

private struct TiltPosterParallaxPosterCredit: View {
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        Canvas { context, _ in
            let name = TiltPosterParallaxTokens.Typeface.medium
            let size = TiltPosterParallaxTokens.Typeface.credit
            let uiFont = UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size, weight: .medium)
            let text = context.resolve(
                Text(TiltPosterParallaxTokens.Copy.posterCredit)
                    .font(.custom(name, fixedSize: size))
                    .foregroundStyle(TiltPosterParallaxTokens.Palette.posterInk)
            )
            let measured = text.measure(in: CGSize(
                width: CGFloat.greatestFiniteMagnitude,
                height: CGFloat.greatestFiniteMagnitude
            ))
            context.draw(
                text,
                at: CGPoint(x: (width - measured.width) / 2, y: height - 18 - uiFont.ascender),
                anchor: .topLeading
            )
        }
        .frame(width: width, height: height)
    }
}

private struct TiltPosterParallaxPosterBack: View {
    let width: CGFloat
    let height: CGFloat
    let tiltX: Double
    let tiltY: Double

    var body: some View {
        ZStack {
            LinearGradient(colors: TiltPosterParallaxTokens.Palette.back, startPoint: .top, endPoint: .bottom)
            Text(TiltPosterParallaxTokens.Copy.title)
                .font(.custom(TiltPosterParallaxTokens.Typeface.serif, fixedSize: TiltPosterParallaxTokens.Typeface.backTitle))
                .foregroundStyle(LinearGradient(colors: TiltPosterParallaxTokens.Palette.gold, startPoint: .topLeading, endPoint: .bottomTrailing))
                .position(x: width / 2, y: height * TiltPosterParallaxTokens.Back.titleY - 6)
            Capsule()
                .fill(TiltPosterParallaxTokens.Palette.backRule)
                .frame(width: TiltPosterParallaxTokens.Back.rule, height: 1)
                .position(x: width / 2, y: height * TiltPosterParallaxTokens.Back.ruleY)
            ForEach(TiltPosterParallaxTokens.Copy.credits) { credit in
                Text(credit.text)
                    .font(.custom(
                        credit.strong ? TiltPosterParallaxTokens.Typeface.semibold : TiltPosterParallaxTokens.Typeface.medium,
                        fixedSize: TiltPosterParallaxTokens.Typeface.credit
                    ))
                    .foregroundStyle(credit.strong ? TiltPosterParallaxTokens.Palette.backInk : TiltPosterParallaxTokens.Palette.backMute)
                    .position(
                        x: width / 2,
                        y: height * TiltPosterParallaxTokens.Back.creditsY
                            + CGFloat(credit.id) * TiltPosterParallaxTokens.Back.lineStep
                            + credit.gap
                    )
            }
            TiltPosterParallaxPosterGlare(width: width, height: height, tiltX: tiltX, tiltY: tiltY)
            RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius)
                .stroke(TiltPosterParallaxTokens.Palette.rim, lineWidth: TiltPosterParallaxTokens.Render.rimWidth)
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.cardRadius))
    }
}

private struct TiltPosterParallaxPosterGlare: View {
    let width: CGFloat
    let height: CGFloat
    let tiltX: Double
    let tiltY: Double

    var body: some View {
        let x = width * (0.5 + CGFloat(tiltX) * TiltPosterParallaxTokens.Parallax.glare)
        let y = height * (TiltPosterParallaxTokens.Render.glareRestY + CGFloat(tiltY) * TiltPosterParallaxTokens.Parallax.glare)
        let opacity = TiltPosterParallaxTokens.Render.glareRestOpacity
            + min(
                TiltPosterParallaxTokens.Render.glareRange,
                (abs(tiltX) + abs(tiltY)) * TiltPosterParallaxTokens.Render.glareGain
            )
        Circle()
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
