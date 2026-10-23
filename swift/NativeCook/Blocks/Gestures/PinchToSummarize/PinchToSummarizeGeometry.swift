import SwiftUI

struct PinchToSummarizeStage: Equatable {
    var width: CGFloat
    var height: CGFloat
    var insetTop: CGFloat
}

enum PinchToSummarizeMode: Equatable {
    case reading
    case thinking
    case summary
}

struct PinchToSummarizeSnapshot: Equatable {
    var p: CGFloat
    var lift: CGFloat
    var driftX: CGFloat
    var driftY: CGFloat
    var think: CGFloat
    var rim: CGFloat
    var reveal: CGFloat
    var summaryAlpha: CGFloat
    var pageAlpha: CGFloat
    var clock: CGFloat
    var bloomAt: CGFloat
    var fingerAX: CGFloat
    var fingerAY: CGFloat
    var fingerBX: CGFloat
    var fingerBY: CGFloat
    var fingerAlpha: CGFloat
    var mode: PinchToSummarizeMode
}

struct PinchToSummarizePageFrame: Equatable {
    var x: CGFloat
    var y: CGFloat
    var scale: CGFloat
    var clipHeight: CGFloat
    var width: CGFloat
    var height: CGFloat
    var radius: CGFloat
    var trim: CGFloat
}

enum PinchToSummarizeGeometry {
    static func mix(_ from: CGFloat, _ to: CGFloat, _ progress: CGFloat) -> CGFloat {
        from + (to - from) * progress
    }

    static func clamp01(_ value: CGFloat) -> CGFloat {
        min(1, max(0, value))
    }

    static func cardWidth(stage: PinchToSummarizeStage, lift: CGFloat = 1) -> CGFloat {
        stage.width * mix(
            PinchToSummarizeTheme.Layout.thinkWidthRatio,
            PinchToSummarizeTheme.Layout.cardWidthRatio,
            lift
        )
    }

    static func cardHeight(lift: CGFloat = 1) -> CGFloat {
        mix(
            PinchToSummarizeTheme.Layout.thinkHeight,
            PinchToSummarizeTheme.Layout.cardHeight,
            lift
        )
    }

    static func cardScale(stage: PinchToSummarizeStage, lift: CGFloat = 1) -> CGFloat {
        guard stage.width > 0 else { return 1 }
        return cardWidth(stage: stage, lift: lift) / stage.width
    }

    static func topTrim(stage: PinchToSummarizeStage) -> CGFloat {
        max(0, stage.insetTop - PinchToSummarizeTheme.Layout.cardTopTrim)
    }

    static func pageFrame(
        stage: PinchToSummarizeStage,
        p: CGFloat,
        lift: CGFloat,
        driftX: CGFloat,
        driftY: CGFloat,
        breath: CGFloat = 0
    ) -> PinchToSummarizePageFrame {
        let scaleAtRest = cardScale(stage: stage, lift: lift)
        let scale = mix(1, scaleAtRest, p) * (1 + breath)
        let top = mix(
            PinchToSummarizeTheme.Layout.thinkTop,
            PinchToSummarizeTheme.Layout.summaryTop,
            lift
        )
        let clipHeight = max(1, mix(stage.height, cardHeight(lift: lift) / scaleAtRest, p))
        let grow = mix(1, scaleAtRest, p) * breath
        let x = mix(0, (stage.width - cardWidth(stage: stage, lift: lift)) / 2, p)
            + driftX - (stage.width * grow) / 2
        let y = mix(0, top, p) + driftY - (clipHeight * grow) / 2
        let radius = clamp01(abs(p) / 0.06) * mix(
            PinchToSummarizeTheme.Layout.deviceRadius,
            PinchToSummarizeTheme.Layout.cardRadius,
            clamp01(p)
        )

        return PinchToSummarizePageFrame(
            x: x,
            y: y,
            scale: scale,
            clipHeight: clipHeight,
            width: stage.width * scale,
            height: clipHeight * scale,
            radius: radius,
            trim: topTrim(stage: stage) * clamp01(p)
        )
    }

    static func breathing(think: CGFloat, clock: CGFloat, amplitude: CGFloat, period: CGFloat) -> CGFloat {
        guard period > 0 else { return 0 }
        return think * amplitude * sin((clock / period) * .pi * 2)
    }

    static func cardTop(lift: CGFloat) -> CGFloat {
        mix(
            PinchToSummarizeTheme.Layout.thinkTop,
            PinchToSummarizeTheme.Layout.summaryTop,
            lift
        )
    }
}
