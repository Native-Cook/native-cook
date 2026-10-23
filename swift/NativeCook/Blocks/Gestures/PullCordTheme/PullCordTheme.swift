import CoreImage
import SwiftUI
import UIKit

/// The renderer's narrow, value-only boundary with the fixed-step controller.
/// Coordinates are in the containing view's points, so no drawing code needs to
/// retain or inspect the simulation itself.
struct PullCordThemeRenderValues {
    var rope: [CGPoint]
    var bead: CGPoint
    var bulbAngle: Double
    var bulbCenter: CGPoint
    var reveal: Double
    var revealFraction: Double
    var fade: Double
    var level: Double
    var neon: Double
    var side: Double
    var commitAge: Double
    var clock: Double
    var isLight: Bool
    var reducedMotion: Bool

    static let empty = Self(
        rope: [],
        bead: .zero,
        bulbAngle: 0,
        bulbCenter: .zero,
        reveal: 0,
        revealFraction: 0,
        fade: 0,
        level: 0,
        neon: 1,
        side: 0,
        commitAge: .infinity,
        clock: 0,
        isLight: false,
        reducedMotion: false
    )
}

struct PullCordTheme: View {
    static let loop: Double = PullCordThemeMotion.loopMS / 1_000

    @State private var controller: PullCordThemeController
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(autoPlay: Bool = false) {
        _controller = State(initialValue: PullCordThemeController(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { safeAreaProxy in
            GeometryReader { proxy in
                let size = proxy.size
                let values = controller.renderValues
                ZStack(alignment: .topLeading) {
                    PullCordThemeStage(values: values, size: size)
                    PullCordThemeCopy(values: values, size: size, bottomInset: safeAreaProxy.safeAreaInsets.bottom)
                    // UIKit delivers a no-delay one-finger stream while allowing the
                    // gallery's parent pan to recognise simultaneously.
                    PullCordThemeTouchView(controller: controller)
                        .frame(width: size.width, height: size.height)
                }
                .contentShape(.rect)
                .onAppear {
                    PullCordThemeAssets.assertAllLoaded()
                    controller.configure(width: size.width, height: size.height, reducedMotion: reduceMotion)
                    controller.start()
                }
                .onDisappear(perform: controller.stop)
                .onChange(of: size) { _, next in
                    controller.configure(width: next.width, height: next.height, reducedMotion: reduceMotion)
                }
                .onChange(of: reduceMotion) { _, next in
                    controller.configure(width: size.width, height: size.height, reducedMotion: next)
                }
            }
            .ignoresSafeArea()
        }
        // Matches Expo StatusBar: dark content in the warm room, light content in the dark room.
        .preferredColorScheme(controller.renderValues.isLight ? .light : .dark)
        .background(PullCordThemeColor.color(PullCordThemeTokens.Dark.wallTop))
    }
}

private struct PullCordThemeStage: View {
    let values: PullCordThemeRenderValues
    let size: CGSize

    var body: some View {
        ZStack(alignment: .topLeading) {
            PullCordThemeRoom(light: false, values: values, size: size)
            PullCordThemeRoom(light: true, values: values, size: size)
                .opacity(values.fade)
                .mask(PullCordThemeRevealMask(center: values.bulbCenter, radius: values.reveal))

            Circle()
                .stroke(PullCordThemeColor.color("rgba(255, 200, 130, 0.9)"), lineWidth: PullCordThemeMotion.Hero.edge)
                .frame(width: max(1, values.reveal * 2), height: max(1, values.reveal * 2))
                .position(values.bulbCenter)
                .blur(radius: PullCordThemeMotion.Hero.edge / 2)
                .opacity(values.reducedMotion ? 0 : 4 * values.revealFraction * (1 - values.revealFraction))

            PullCordThemeFlash(center: values.bulbCenter, opacity: flashOpacity, size: size)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private var flashOpacity: Double {
        guard values.isLight, values.commitAge < PullCordThemeMotion.Hero.flash else { return 0 }
        return PullCordThemeMotion.Hero.flashPeak * (1 - values.commitAge / PullCordThemeMotion.Hero.flash)
    }
}

private struct PullCordThemeRevealMask: View {
    let center: CGPoint
    let radius: Double

    var body: some View {
        RadialGradient(
            gradient: Gradient(stops: [
                .init(color: .white, location: 0),
                .init(color: .white, location: 0.994),
                .init(color: .clear, location: 1)
            ]),
            center: .center,
            startRadius: 0,
            endRadius: max(0.5, radius)
        )
        .frame(width: max(1, radius * 2), height: max(1, radius * 2))
        .position(center)
    }
}

private struct PullCordThemeRoom: View {
    let light: Bool
    let values: PullCordThemeRenderValues
    let size: CGSize

    private var scale: Double { size.width / PullCordThemeTokens.stageWidth }
    private var palette: PullCordThemeRoomPalette { light ? .light : .dark }
    private var socket: CGPoint {
        let socketTop = PullCordThemeTokens.ReplicaArt.socketTopSrc * PullCordThemeTokens.Fit.k * scale
        let pivotY = socketTop - PullCordThemeTokens.ReplicaArt.wire * scale
        let arm = socketTop - pivotY
        return CGPoint(
            x: PullCordThemeTokens.ReplicaArt.centreSrc * PullCordThemeTokens.Fit.k * scale + PullCordThemeTokens.Fit.cropX * scale + sin(values.bulbAngle) * arm,
            y: pivotY + cos(values.bulbAngle) * arm
        )
    }
    private var imageSize: CGSize {
        CGSize(
            width: PullCordThemeTokens.ReplicaArt.spriteW * PullCordThemeTokens.ReplicaArt.spriteScale * scale,
            height: PullCordThemeTokens.ReplicaArt.spriteH * PullCordThemeTokens.ReplicaArt.spriteScale * scale
        )
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            palette.wallTop
            PullCordThemeAssets.wall(light: light)
                .resizable()
                .frame(width: PullCordThemeTokens.ReplicaArt.wallW * scale, height: size.height)
                .offset(x: PullCordThemeTokens.ReplicaArt.wallX * scale)
                .clipped()

            if !light {
                PullCordThemeDarkLight(values: values, size: size, scale: scale)
            }

            Circle()
                .fill(RadialGradient(
                    colors: [PullCordThemeColor.color(PullCordThemeTokens.Liquid.spill), PullCordThemeColor.color(PullCordThemeTokens.Liquid.spillFar)],
                    center: .center,
                    startRadius: 0,
                    endRadius: PullCordThemeTokens.ReplicaArt.halo * 1.4 * scale
                ))
                .frame(width: PullCordThemeTokens.ReplicaArt.halo * 2.8 * scale, height: PullCordThemeTokens.ReplicaArt.halo * 2.8 * scale)
                .position(values.bulbCenter)
                .opacity(light ? 0.5 + 0.5 * values.level : 0.8 * values.level)

            PullCordThemeBulb(
                light: light,
                values: values,
                socket: socket,
                imageSize: imageSize,
                scale: scale
            )

            if light {
                PullCordThemeMotes(values: values, scale: scale, size: size)
                    .opacity(0.55 * (0.4 + 0.6 * values.level))
                    .blendMode(.screen)
            } else {
                PullCordThemeLightMap(values: values, size: size)
            }

            PullCordThemeCord(values: values, size: size, light: light)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
}

private struct PullCordThemeDarkLight: View {
    let values: PullCordThemeRenderValues
    let size: CGSize
    let scale: Double

    var body: some View {
        let on = values.neon * hum * (1 - 0.7 * values.level)
        let px = scale / 3
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                Rectangle()
                    .fill(RadialGradient(
                        colors: PullCordThemeTokens.ReplicaArt.ambient.map(PullCordThemeColor.color),
                        center: gradientCenter,
                        startRadius: 0,
                        endRadius: size.width * PullCordThemeTokens.ReplicaArt.ambientR
                    ))
                    .frame(width: size.width, height: size.height)
                Rectangle()
                    .fill(RadialGradient(
                        gradient: Gradient(stops: zip(
                            PullCordThemeTokens.ReplicaArt.spillFall,
                            PullCordThemeTokens.ReplicaArt.spillStops
                        ).map { .init(color: PullCordThemeColor.color($0.0), location: $0.1) }),
                        center: gradientCenter,
                        startRadius: 0,
                        endRadius: size.width * PullCordThemeTokens.ReplicaArt.spillR
                    ))
                    .frame(width: size.width, height: size.height)
                Circle()
                    .fill(RadialGradient(
                        colors: [PullCordThemeColor.color(PullCordThemeTokens.ReplicaArt.haloColor), PullCordThemeColor.color("rgba(31, 125, 255, 0)")],
                        center: .center,
                        startRadius: 0,
                        endRadius: PullCordThemeTokens.ReplicaArt.halo * scale
                    ))
                    .frame(width: PullCordThemeTokens.ReplicaArt.halo * 2 * scale, height: PullCordThemeTokens.ReplicaArt.halo * 2 * scale)
                    .position(values.bulbCenter)
                Rectangle()
                    .fill(PullCordThemeColor.color(PullCordThemeTokens.ReplicaArt.railColor))
                    .frame(
                        width: size.width,
                        height: (PullCordThemeTokens.ReplicaArt.railY[1] - PullCordThemeTokens.ReplicaArt.railY[0]) * px
                    )
                    .offset(y: PullCordThemeTokens.ReplicaArt.railY[0] * px)
            }
            .opacity(on)
            PullCordThemeStileGlow(
                x0: PullCordThemeTokens.ReplicaArt.stileLeft[0] * px,
                x1: PullCordThemeTokens.ReplicaArt.stileLeft[1] * px,
                opacity: on * (0.65 - 0.35 * values.side),
                px: px
            )
            PullCordThemeStileGlow(
                x0: PullCordThemeTokens.ReplicaArt.stileRight[0] * px,
                x1: PullCordThemeTokens.ReplicaArt.stileRight[1] * px,
                opacity: on * (0.65 + 0.35 * values.side),
                px: px
            )
        }
        .blendMode(.screen)
    }

    private var hum: Double {
        PullCordThemeMotion.Hero.Hum.low + (1 - PullCordThemeMotion.Hero.Hum.low) * (0.5 + 0.5 * sin(2 * .pi * values.clock / PullCordThemeMotion.Hero.Hum.period))
    }

    private var gradientCenter: UnitPoint {
        UnitPoint(
            x: size.width > 0 ? values.bulbCenter.x / size.width : 0.5,
            y: size.height > 0 ? values.bulbCenter.y / size.height : 0.5
        )
    }
}

private struct PullCordThemeLightMap: View {
    let values: PullCordThemeRenderValues
    let size: CGSize

    var body: some View {
        ZStack(alignment: .topLeading) {
            PullCordThemeAssets.image("PullCordTheme-light-mul.png")
                .resizable()
                .frame(width: size.width * 2, height: size.height)
                .blendMode(.multiply)
            PullCordThemeAssets.image("PullCordTheme-light-add.png")
                .resizable()
                .frame(width: size.width * 2, height: size.height)
                .blendMode(.plusLighter)
                .opacity(0.25)
        }
        .frame(width: size.width * 2, height: size.height, alignment: .topLeading)
        .offset(x: values.bulbCenter.x - size.width)
        .opacity(darkOn * PullCordThemeTokens.ReplicaArt.lightMap)
    }

    private var darkOn: Double {
        let hum = PullCordThemeMotion.Hero.Hum.low
            + (1 - PullCordThemeMotion.Hero.Hum.low)
            * (0.5 + 0.5 * sin(2 * .pi * values.clock / PullCordThemeMotion.Hero.Hum.period))
        return values.neon * hum * (1 - 0.7 * values.level)
    }
}

private struct PullCordThemeStileGlow: View {
    let x0: Double
    let x1: Double
    let opacity: Double
    let px: Double

    var body: some View {
        Rectangle()
            .fill(LinearGradient(
                colors: [.clear, PullCordThemeColor.color(PullCordThemeTokens.ReplicaArt.stileColor), .clear],
                startPoint: .top,
                endPoint: .bottom
            ))
            .frame(
                width: x1 - x0,
                height: (PullCordThemeTokens.ReplicaArt.stileGlow[2] - PullCordThemeTokens.ReplicaArt.stileGlow[0]) * px
            )
            .offset(x: x0, y: PullCordThemeTokens.ReplicaArt.stileGlow[0] * px)
            .blur(radius: 3)
            .opacity(opacity)
    }
}

private struct PullCordThemeBulb: View {
    let light: Bool
    let values: PullCordThemeRenderValues
    let socket: CGPoint
    let imageSize: CGSize
    let scale: Double

    private var anchor: UnitPoint {
        UnitPoint(
            x: PullCordThemeTokens.ReplicaArt.anchorX / PullCordThemeTokens.ReplicaArt.spriteW,
            y: PullCordThemeTokens.ReplicaArt.anchorY / PullCordThemeTokens.ReplicaArt.spriteH
        )
    }
    private var imageCenter: CGPoint {
        let anchorX = PullCordThemeTokens.ReplicaArt.anchorX * PullCordThemeTokens.ReplicaArt.spriteScale * scale
        let anchorY = PullCordThemeTokens.ReplicaArt.anchorY * PullCordThemeTokens.ReplicaArt.spriteScale * scale
        return CGPoint(
            x: socket.x + imageSize.width / 2 - anchorX,
            y: socket.y + imageSize.height / 2 - anchorY
        )
    }

    var body: some View {
        ZStack {
            // This stays in the socket-local transform, matching Skia's
            // translate(pivot) → rotate → translate(socket - pivot) sequence.
            Rectangle()
                .fill(PullCordThemeColor.color(light ? PullCordThemeTokens.Light.wire : PullCordThemeTokens.ReplicaArt.wireColor))
                .frame(width: PullCordThemeTokens.ReplicaArt.wireWidth, height: PullCordThemeTokens.ReplicaArt.wire * scale + 4)
                .position(
                    x: PullCordThemeTokens.ReplicaArt.anchorX * PullCordThemeTokens.ReplicaArt.spriteScale * scale,
                    y: PullCordThemeTokens.ReplicaArt.anchorY * PullCordThemeTokens.ReplicaArt.spriteScale * scale - PullCordThemeTokens.ReplicaArt.wire * scale / 2 + 2
                )

            if light {
                PullCordThemeSprite(image: PullCordThemeAssets.warmShadow, imageSize: imageSize)
                    .opacity(PullCordThemeMotion.Hero.Shadow.alpha)
                    .blur(radius: PullCordThemeMotion.Hero.Shadow.blur * scale)
                    .offset(x: PullCordThemeMotion.Hero.Shadow.dx * scale, y: PullCordThemeMotion.Hero.Shadow.dy * scale)
                PullCordThemeSprite(name: "PullCordTheme-bulb-warm.png", imageSize: imageSize)
                    .colorMultiply(warmTint)
                    .opacity(warmFlicker)
            } else {
                PullCordThemeSprite(name: "PullCordTheme-bulb-dim.png", imageSize: imageSize)
                    .blendMode(.screen)
                    .opacity(1 - darkOn)
                PullCordThemeSprite(image: PullCordThemeAssets.litInterior, imageSize: imageSize)
                    .blendMode(.screen)
                    .opacity(darkOn)
                PullCordThemeSprite(image: PullCordThemeAssets.litBrightPass, imageSize: imageSize)
                    .blur(radius: PullCordThemeTokens.ReplicaArt.bloom)
                    .blendMode(.screen)
                    .opacity(darkOn * PullCordThemeTokens.ReplicaArt.bloomOpacity)
            }

            PullCordThemeLiquid(values: values, imageSize: imageSize, scale: scale)
                .opacity(light ? PullCordThemeTokens.Liquid.lightAlpha : 1)

            RoundedRectangle(cornerRadius: 6 * scale)
                .fill(Color.white.opacity(0.32))
                .frame(width: 12 * scale, height: 52 * scale)
                .position(x: specX + 6 * scale, y: specY + 26 * scale)
                .blur(radius: 5)
                .blendMode(.screen)
        }
        .frame(width: imageSize.width, height: imageSize.height)
        .rotationEffect(.radians(-values.bulbAngle), anchor: anchor)
        .position(imageCenter)
    }

    private var darkOn: Double {
        let hum = PullCordThemeMotion.Hero.Hum.low + (1 - PullCordThemeMotion.Hero.Hum.low) * (0.5 + 0.5 * sin(2 * .pi * values.clock / PullCordThemeMotion.Hero.Hum.period))
        return values.neon * hum * (1 - 0.7 * values.level)
    }
    private var warmFlicker: Double {
        guard values.isLight else { return 1 }
        let flickers = PullCordThemeMotion.Hero.flickers
        for index in stride(from: 0, to: flickers.count, by: 2) where values.commitAge >= flickers[index] && values.commitAge < flickers[index + 1] {
            return 1 - PullCordThemeMotion.Hero.flickerDepth
        }
        return 1
    }
    private var warmTint: Color {
        let ember = max(0, min(1, values.commitAge / PullCordThemeMotion.Hero.ember))
        return Color(.sRGB, red: 1, green: 0.5 + 0.5 * ember, blue: 0.15 + 0.85 * ember, opacity: 1)
    }
    private var specX: Double {
        let source = (-PullCordThemeMotion.Hero.spec * values.side - 42) * scale
        return source + PullCordThemeTokens.ReplicaArt.anchorX * PullCordThemeTokens.ReplicaArt.spriteScale * scale
    }
    private var specY: Double {
        let spriteScale = PullCordThemeTokens.ReplicaArt.spriteScale * scale
        let glassTop = (PullCordThemeTokens.ReplicaArt.anchorY + 60) * spriteScale
        let glassBottom = (705 - PullCordThemeTokens.ReplicaArt.anchorY) * spriteScale
        let sourceLocalMid = (glassTop + glassBottom) / 2
        return sourceLocalMid + PullCordThemeTokens.ReplicaArt.anchorY * spriteScale - 40 * scale
    }
}

private struct PullCordThemeSprite: View {
    let sprite: Image
    let imageSize: CGSize

    init(name: String, imageSize: CGSize) {
        sprite = PullCordThemeAssets.image(name)
        self.imageSize = imageSize
    }

    init(image: Image, imageSize: CGSize) {
        sprite = image
        self.imageSize = imageSize
    }

    var body: some View {
        sprite
            .resizable()
            .interpolation(.high)
            .frame(width: imageSize.width, height: imageSize.height)
    }
}

private struct PullCordThemeLiquid: View {
    let values: PullCordThemeRenderValues
    let imageSize: CGSize
    let scale: Double

    var body: some View {
        let spriteScale = PullCordThemeTokens.ReplicaArt.spriteScale * scale
        // Convert Skia's socket-local glass bounds into this image-local view.
        let top = (2 * PullCordThemeTokens.ReplicaArt.anchorY + 60) * spriteScale
        let gradientBottom = 705 * spriteScale
        let liquidBottom = gradientBottom
        let levelY = gradientBottom - values.level * (gradientBottom - top)
        let gradientHeight = liquidBottom - top
        let bottomStop = (gradientBottom - top) / gradientHeight
        ZStack(alignment: .topLeading) {
            LinearGradient(
                gradient: Gradient(stops: [
                    .init(color: PullCordThemeColor.color(PullCordThemeTokens.Liquid.top), location: 0),
                    .init(color: PullCordThemeColor.color(PullCordThemeTokens.Liquid.bottom), location: bottomStop),
                    .init(color: PullCordThemeColor.color(PullCordThemeTokens.Liquid.bottom), location: 1)
                ]),
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: imageSize.width, height: gradientHeight, alignment: .top)
            .offset(y: top)
        }
        .frame(width: imageSize.width, height: imageSize.height, alignment: .topLeading)
        .mask(alignment: .topLeading) {
            Rectangle()
                .frame(width: imageSize.width, height: max(0, liquidBottom - levelY), alignment: .top)
                .offset(y: levelY)
        }
        .mask(
            PullCordThemeAssets.image("PullCordTheme-bulb-warm.png")
                .resizable()
                .frame(width: imageSize.width, height: imageSize.height)
        )
    }
}

private struct PullCordThemeCord: View {
    let values: PullCordThemeRenderValues
    let size: CGSize
    let light: Bool

    var body: some View {
        Canvas { context, _ in
            guard values.rope.count > 1 else { return }
            var path = Path()
            path.move(to: values.rope[0])
            if values.rope.count > 2 {
                for index in 1..<(values.rope.count - 1) {
                    let point = values.rope[index]
                    let next = values.rope[index + 1]
                    path.addQuadCurve(to: CGPoint(x: (point.x + next.x) / 2, y: (point.y + next.y) / 2), control: point)
                }
            }
            path.addLine(to: values.rope[values.rope.count - 1])
            let scale = size.width / PullCordThemeTokens.stageWidth
            context.stroke(
                path,
                with: .color(PullCordThemeColor.color(light ? PullCordThemeTokens.Light.cord : PullCordThemeTokens.Dark.cord)),
                style: StrokeStyle(
                    lineWidth: PullCordThemeTokens.Cord.width * scale,
                    lineCap: .round,
                    lineJoin: .round
                )
            )
        }
        .overlay(alignment: .topLeading) {
            PullCordThemeBead(values: values, scale: size.width / PullCordThemeTokens.stageWidth, light: light)
        }
    }
}

private struct PullCordThemeBead: View {
    let values: PullCordThemeRenderValues
    let scale: Double
    let light: Bool

    var body: some View {
        let previous = values.rope.dropLast().last ?? values.bead
        let angle = atan2(-(values.bead.x - previous.x), values.bead.y - previous.y)
        let height = (PullCordThemeTokens.Cord.crimpHeight + PullCordThemeTokens.Cord.beadHeight) * scale
        VStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 1.5 * scale)
                .fill(LinearGradient(gradient: Gradient(stops: gradientStops(metal, PullCordThemeTokens.Dark.metalStops)), startPoint: .leading, endPoint: .trailing))
                .frame(width: PullCordThemeTokens.Cord.crimpWidth * scale, height: PullCordThemeTokens.Cord.crimpHeight * scale)
            RoundedRectangle(cornerRadius: PullCordThemeTokens.Cord.beadRadius * scale)
                .fill(LinearGradient(gradient: Gradient(stops: gradientStops(brass, PullCordThemeTokens.Dark.brassStops)), startPoint: .leading, endPoint: .trailing))
                .overlay {
                    RoundedRectangle(cornerRadius: PullCordThemeTokens.Cord.beadRadius * scale)
                        .stroke(PullCordThemeColor.color(light ? PullCordThemeTokens.Light.cordLit : PullCordThemeTokens.Dark.cordLit), lineWidth: scale)
                        .blur(radius: 0.6)
                }
                .frame(width: PullCordThemeTokens.Cord.beadWidth * scale, height: PullCordThemeTokens.Cord.beadHeight * scale)
        }
        .rotationEffect(.radians(angle), anchor: UnitPoint(x: 0.5, y: scale / height))
        .position(
            x: values.bead.x,
            y: values.bead.y + 20 * scale
        )
    }

    private var metal: [Color] {
        (light ? PullCordThemeTokens.Light.metal : PullCordThemeTokens.Dark.metal).map(PullCordThemeColor.color)
    }
    private var brass: [Color] {
        (light ? PullCordThemeTokens.Light.brass : PullCordThemeTokens.Dark.brass).map(PullCordThemeColor.color)
    }
    private func gradientStops(_ colors: [Color], _ positions: [Double]) -> [Gradient.Stop] {
        zip(colors, positions).map { Gradient.Stop(color: $0.0, location: $0.1) }
    }
}

private struct PullCordThemeMotes: View {
    let values: PullCordThemeRenderValues
    let scale: Double
    let size: CGSize

    var body: some View {
        Canvas { context, _ in
            for index in 0..<Int(PullCordThemeMotion.Hero.motes) {
                let mote = PullCordThemeMote(index: index)
                let y = (mote.y + values.clock * 0.02 * mote.speed).truncatingRemainder(dividingBy: 1)
                let spread = 40 + y * 140
                let x = values.bulbCenter.x + (mote.x - 0.5) * 2 * spread * scale + sin(values.clock * mote.speed + mote.phase) * 8 * scale
                let point = CGPoint(x: x, y: values.bulbCenter.y + (40 + y * 380) * scale)
                context.fill(Path(ellipseIn: CGRect(x: point.x - mote.size * scale, y: point.y - mote.size * scale, width: mote.size * 2 * scale, height: mote.size * 2 * scale)), with: .color(PullCordThemeColor.color("#FFF3D6")))
            }
        }
        .frame(width: size.width, height: size.height)
        .blur(radius: 1.2)
    }
}

private struct PullCordThemeMote {
    let x: Double
    let y: Double
    let speed: Double
    let phase: Double
    let size: Double

    init(index: Int) {
        func random(_ key: Double) -> Double {
            let value = sin(Double(index) * 127.1 + key * 311.7) * 43_758.5453
            return value - floor(value)
        }
        x = random(1)
        y = random(2)
        speed = 0.4 + random(3) * 0.8
        phase = random(4) * 2 * .pi
        size = 0.8 + random(5) * 1.6
    }
}

private struct PullCordThemeFlash: View {
    let center: CGPoint
    let opacity: Double
    let size: CGSize

    var body: some View {
        RadialGradient(colors: [.white, .clear], center: .center, startRadius: 0, endRadius: size.width)
            .frame(width: size.width * 2, height: size.width * 2)
            .position(center)
            .opacity(opacity)
    }
}

private struct PullCordThemeCopy: View {
    let values: PullCordThemeRenderValues
    let size: CGSize
    let bottomInset: Double

    var body: some View {
        let bottom = bottomInset + PullCordThemeTokens.Typography.bottom
        let top = size.height - bottom - PullCordThemeTokens.Typography.hintLine - PullCordThemeTokens.Typography.gap - PullCordThemeTokens.Typography.titleLine - PullCordThemeTokens.Typography.gap - PullCordThemeTokens.Typography.eyebrowSize
        let eyebrowMix = mix(y: top)
        let titleMix = mix(y: top + 30)
        let hintMix = mix(y: top + 80)
        VStack(alignment: .leading, spacing: 0) {
            Text(PullCordThemeTokens.Copy.eyebrow)
                .font(.system(size: PullCordThemeTokens.Typography.eyebrowSize, weight: .semibold))
                .tracking(PullCordThemeTokens.Typography.eyebrowTracking)
                .foregroundStyle(PullCordThemeColor.interpolateGamma(PullCordThemeTokens.Dark.mute, PullCordThemeTokens.Light.mute, amount: eyebrowMix))
            ZStack(alignment: .topLeading) {
                Text(PullCordThemeTokens.Copy.dark)
                    .foregroundStyle(PullCordThemeColor.color(PullCordThemeTokens.Dark.ink))
                    .offset(y: values.reducedMotion ? 0 : -PullCordThemeTokens.Typography.titleLine * titleMix)
                    .opacity(1 - titleMix)
                Text(PullCordThemeTokens.Copy.light)
                    .foregroundStyle(PullCordThemeColor.color(PullCordThemeTokens.Light.ink))
                    .offset(y: values.reducedMotion ? 0 : PullCordThemeTokens.Typography.titleLine * (1 - titleMix))
                    .opacity(titleMix)
            }
            .font(.custom(PullCordThemeTokens.Typography.titleFamily, size: PullCordThemeTokens.Typography.titleSize))
            .frame(height: PullCordThemeTokens.Typography.titleLine, alignment: .center)
            .clipped()
            .padding(.top, PullCordThemeTokens.Typography.gap)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(values.isLight ? PullCordThemeTokens.Copy.light : PullCordThemeTokens.Copy.dark) theme")
            .accessibilityAddTraits(.isHeader)
            Text(PullCordThemeTokens.Copy.hint)
                .font(.system(size: PullCordThemeTokens.Typography.hintSize))
                .foregroundStyle(PullCordThemeColor.interpolateGamma(PullCordThemeTokens.Dark.mute, PullCordThemeTokens.Light.mute, amount: hintMix))
                .frame(height: PullCordThemeTokens.Typography.hintLine, alignment: .center)
                .padding(.top, PullCordThemeTokens.Typography.gap)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .padding(.leading, PullCordThemeTokens.Typography.margin)
        .padding(.trailing, PullCordThemeTokens.Typography.margin)
        .padding(.bottom, bottom)
        .allowsHitTesting(false)
    }

    private func mix(y: Double) -> Double {
        guard !values.reducedMotion else { return values.fade }
        let distance = hypot(PullCordThemeTokens.Typography.margin + 40 - values.bulbCenter.x, y - values.bulbCenter.y)
        return max(0, min(1, (values.reveal - distance + PullCordThemeMotion.Reveal.textBand / 2) / PullCordThemeMotion.Reveal.textBand))
    }
}

private enum PullCordThemeRoomPalette {
    case dark
    case light

    var wallTop: Color {
        PullCordThemeColor.color(self == .light ? PullCordThemeTokens.Light.wallTop : "#0D0E10")
    }
}

private enum PullCordThemeAssets {
    private static let sRGB = CGColorSpace(name: CGColorSpace.sRGB)!
    private static let context = CIContext(options: [.workingColorSpace: sRGB, .outputColorSpace: sRGB])
    private static let litSource = uiImage("PullCordTheme-bulb-lit.png")
    private static let warmSource = uiImage("PullCordTheme-bulb-warm.png")
    private static let wallSource = uiImage("PullCordTheme-wall.jpg")
    /// These two sprite transforms directly port Skia's 4×5 colour matrices.
    /// Core Image is used once per process, rather than applying approximate
    /// SwiftUI colour modifiers every animation frame.
    static let litInterior = Image(uiImage: colorMatrix(litSource, PullCordThemeTokens.ReplicaArt.interior))
    static let litBrightPass = Image(uiImage: colorMatrix(litSource, PullCordThemeTokens.ReplicaArt.brightPass))
    static let darkWall = Image(uiImage: colorMatrix(wallSource, PullCordThemeTokens.ReplicaArt.wallTint))
    static let warmShadow = Image(uiImage: colorMatrix(warmSource, [
        0, 0, 0, 0, 0.12,
        0, 0, 0, 0, 0.08,
        0, 0, 0, 0, 0.04,
        0, 0, 0, 1, 0
    ]))

    static func image(_ name: String) -> Image {
        Image(uiImage: uiImage(name))
    }

    static func wall(light: Bool) -> Image {
        light ? image("PullCordTheme-wall-light.jpg") : darkWall
    }

    static func assertAllLoaded() {
        #if DEBUG
        [
            "PullCordTheme-wall.jpg",
            "PullCordTheme-wall-light.jpg",
            "PullCordTheme-bulb-lit.png",
            "PullCordTheme-bulb-dim.png",
            "PullCordTheme-bulb-warm.png",
            "PullCordTheme-light-add.png",
            "PullCordTheme-light-mul.png"
        ].forEach { name in
            assert(UIImage(named: name) != nil, "Missing Pull Cord Theme asset: \(name)")
        }
        #endif
    }

    private static func uiImage(_ name: String) -> UIImage {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Pull Cord Theme asset: \(name)")
            return UIImage()
        }
        return image
    }

    private static func colorMatrix(_ image: UIImage, _ matrix: [Double]) -> UIImage {
        guard
            matrix.count == 20,
            let input = CIImage(image: image),
            let filter = CIFilter(name: "CIColorMatrix")
        else {
            return image
        }
        filter.setValue(input, forKey: kCIInputImageKey)
        filter.setValue(CIVector(x: matrix[0], y: matrix[1], z: matrix[2], w: matrix[3]), forKey: "inputRVector")
        filter.setValue(CIVector(x: matrix[5], y: matrix[6], z: matrix[7], w: matrix[8]), forKey: "inputGVector")
        filter.setValue(CIVector(x: matrix[10], y: matrix[11], z: matrix[12], w: matrix[13]), forKey: "inputBVector")
        filter.setValue(CIVector(x: matrix[15], y: matrix[16], z: matrix[17], w: matrix[18]), forKey: "inputAVector")
        filter.setValue(CIVector(x: matrix[4], y: matrix[9], z: matrix[14], w: matrix[19]), forKey: "inputBiasVector")
        guard let output = filter.outputImage, let cgImage = context.createCGImage(output, from: input.extent) else {
            return image
        }
        return UIImage(cgImage: cgImage, scale: image.scale, orientation: image.imageOrientation)
    }
}

private enum PullCordThemeColor {
    struct RGBA {
        let red: Double
        let green: Double
        let blue: Double
        let alpha: Double
    }

    static func color(_ literal: String) -> Color {
        let rgba = parse(literal)
        return Color(.sRGB, red: rgba.red, green: rgba.green, blue: rgba.blue, opacity: rgba.alpha)
    }

    /// Reanimated's interpolateColor defaults to gamma 2.2. Interpolate in its
    /// linearized colour space so the travelling text tint matches the RN front.
    static func interpolateGamma(_ from: String, _ to: String, amount: Double) -> Color {
        let a = parse(from)
        let b = parse(to)
        let t = max(0, min(1, amount))
        let gamma = 2.2
        func mix(_ lhs: Double, _ rhs: Double) -> Double {
            pow(pow(lhs, gamma) + (pow(rhs, gamma) - pow(lhs, gamma)) * t, 1 / gamma)
        }
        return Color(.sRGB, red: mix(a.red, b.red), green: mix(a.green, b.green), blue: mix(a.blue, b.blue), opacity: a.alpha + (b.alpha - a.alpha) * t)
    }

    private static func parse(_ literal: String) -> RGBA {
        if literal.hasPrefix("#") {
            let value = UInt64(literal.dropFirst(), radix: 16) ?? 0
            return RGBA(red: Double((value >> 16) & 0xff) / 255, green: Double((value >> 8) & 0xff) / 255, blue: Double(value & 0xff) / 255, alpha: 1)
        }
        let numbers = literal
            .replacingOccurrences(of: "rgba(", with: "")
            .replacingOccurrences(of: ")", with: "")
            .split(separator: ",")
            .compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard numbers.count == 4 else { return RGBA(red: 0, green: 0, blue: 0, alpha: 0) }
        return RGBA(red: numbers[0] / 255, green: numbers[1] / 255, blue: numbers[2] / 255, alpha: numbers[3])
    }
}
