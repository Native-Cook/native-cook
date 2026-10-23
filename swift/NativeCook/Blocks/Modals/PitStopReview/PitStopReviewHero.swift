import SwiftUI
import UIKit

/// The hero as a SwiftUI tree whose parameters are all derived from the scene time `scene` (one pure function of
/// time): the track shader, the Blender car (seven pitch frames crossfaded, a cool grade, horizontal blur and light
/// streaks while it races), the baked light trail screened over its rear, additive brake-disc glows, the tyre smoke
/// and the start-light gantry. Drawn in the hero's design points (402 × 220) and scaled to the screen.
struct PitStopReviewHero: View {
    typealias Motion = PitStopReviewMotion
    typealias H = PitStopReviewTheme.Hero
    typealias G = PitStopReviewTheme.Gantry
    typealias C = PitStopReviewTheme.Colors

    let width: CGFloat
    let scale: CGFloat
    let scene: Double

    /// Wheel centres in the car canvas (design pt) and how much of each disc the camera sees.
    private static let wheels: [(x: Double, y: Double, k: Double)] = [(217.2, 86.1, 1), (52.0, 86.1, 1), (210.9, 30.1, 0.35), (57.5, 31.9, 0.35)]
    private static let discCore = 7.5, discBloom = 17.0, discSquash = 0.72
    /// Tyre contact points in the car canvas (design pt), moved to the hero: x, y, seed, kind (0 front, 1 rear), side.
    private static let tyres: [Motion.Tyre] = [
        (215.2, 94.8, 11.0, 0, 1.0), (209.2, 39.5, 23.0, 0, -1.0), (54.1, 94.9, 37.0, 1, 1.0), (59.3, 41.3, 53.0, 1, -1.0),
    ].map { Motion.Tyre(x: H.car.minX + $0.0, y: H.car.minY + $0.1, seed: $0.2, kind: $0.3, side: $0.4) }
    /// The band the car can occupy (design pt): the car layer is clipped to it so its offscreen buffer stays small.
    private static let bandTop = H.car.minY - 6, bandHeight = H.car.height + 12

    var body: some View {
        let s = scene
        let carLeft = H.car.minX + Motion.carX(s)
        let blur = Motion.carBlur(s)
        ZStack(alignment: .topLeading) {
            world(s)
            Image(uiImage: PitStopReviewAssets.carShadow).resizable()
                .frame(width: H.car.width, height: H.car.height)
                .opacity(0.9 * (1 - min(1, blur / 4)))
                .offset(x: carLeft, y: H.car.minY)
            car(s, left: carLeft, blur: blur)
            trail(s)
            discs(s, left: carLeft)
            smoke(s)
            gantry(s)
        }
        .frame(width: H.width, height: H.height, alignment: .topLeading)
        .compositingGroup()
        .scaleEffect(scale, anchor: .topLeading)
        .frame(width: width, height: H.height * scale, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func worldUniforms(_ s: Double) -> (Shader.Argument, Shader.Argument) {
        let w0 = Shader.Argument.float4(Float(Motion.worldX(s)), Float(Motion.worldBlur(s)), Float(Motion.haze(s)), Float(Motion.speedLines(s)))
        let w1 = Shader.Argument.float4(Float(H.car.minX + Motion.carX(s) + 4), Float(s), 1, 0)
        return (w0, w1)
    }

    private func world(_ s: Double) -> some View {
        let (w0, w1) = worldUniforms(s)
        return Rectangle().fill(.white).frame(width: H.width, height: H.height)
            .colorEffect(ShaderLibrary.pitStopReviewWorld(w0, w1))
    }

    private func car(_ s: Double, left: Double, blur: Double) -> some View {
        let pose = min(Double(PitStopReviewAssets.carFrames.count) - 1.0001, max(0, (Motion.pitch(s) - PitStopReviewAssets.pitchFirst) / PitStopReviewAssets.pitchStep))
        let index = Int(pose.rounded(.down)), mix = pose - Double(index)
        let streaks = Motion.carStreaks(s)
        let (w0, w1) = worldUniforms(s)
        let size = CGSize(width: H.width, height: Self.bandHeight)
        return ZStack(alignment: .topLeading) {
            Image(uiImage: PitStopReviewAssets.carFrames[index]).resizable()
                .frame(width: H.car.width, height: H.car.height).offset(x: left, y: 6)
            Image(uiImage: PitStopReviewAssets.carFrames[index + 1]).resizable()
                .frame(width: H.car.width, height: H.car.height).opacity(mix).offset(x: left, y: 6)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .modifier(PitStopReviewStreaks(enabled: streaks > 0.001, origin: CGPoint(x: 0, y: Self.bandTop), w0: w0, w1: w1, streaks: Float(streaks)))
        .layerEffect(ShaderLibrary.pitStopReviewCarGrade(.float2(size), .float(Float(blur))),
                     maxSampleOffset: CGSize(width: max(1, blur * 3), height: 0))
        .offset(y: Self.bandTop)
    }

    private func trail(_ s: Double) -> some View {
        let strength = Motion.trail(s)
        let len = max(0.2, strength)
        let box = PitStopReviewTrail.box
        return Image(uiImage: PitStopReviewTrail.image).resizable()
            .frame(width: box.width * len, height: box.height)
            .opacity(strength * strength)
            .blendMode(.screen)
            .offset(x: H.car.minX + Motion.carX(s) + (box.minX - H.car.minX) * len, y: box.minY)
    }

    private func discs(_ s: Double, left: Double) -> some View {
        Canvas { context, _ in
            context.translateBy(x: left, y: H.car.minY)
            for wheel in Self.wheels {
                var layer = context
                layer.opacity = wheel.k
                layer.translateBy(x: wheel.x, y: wheel.y)
                layer.scaleBy(x: 1, y: Self.discSquash)
                let bloom = Path(ellipseIn: CGRect(x: -Self.discBloom, y: -Self.discBloom, width: Self.discBloom * 2, height: Self.discBloom * 2))
                layer.opacity = wheel.k * 0.8
                layer.fill(bloom, with: .radialGradient(Gradient(colors: [C.discBloom, Color(.sRGB, red: 226 / 255, green: 52 / 255, blue: 12 / 255, opacity: 0)]),
                                                        center: .zero, startRadius: 0, endRadius: Self.discBloom))
                let core = Path(ellipseIn: CGRect(x: -Self.discCore, y: -Self.discCore, width: Self.discCore * 2, height: Self.discCore * 2))
                layer.opacity = wheel.k
                layer.fill(core, with: .radialGradient(Gradient(stops: [
                    .init(color: Color(.sRGB, red: 1, green: 226 / 255, blue: 168 / 255, opacity: 1), location: 0),
                    .init(color: C.discHot, location: 0.5),
                    .init(color: Color(.sRGB, red: 1, green: 106 / 255, blue: 26 / 255, opacity: 0), location: 1),
                ]), center: .zero, startRadius: 0, endRadius: Self.discCore))
            }
        }
        .frame(width: H.width, height: H.height)
        .opacity(Motion.disc(s))
        .blendMode(.plusLighter)
    }

    @ViewBuilder private func smoke(_ s: Double) -> some View {
        let packed = Motion.packPuffs(s, Self.tyres)
        if packed.count > 0 {
            Rectangle().fill(.white).frame(width: H.width, height: H.height)
                .colorEffect(ShaderLibrary.pitStopReviewSmoke(.floatArray(packed.data)))
        }
    }

    private func gantry(_ s: Double) -> some View {
        let half = 2 * G.pitch + G.radius + G.housingPad
        let on = Motion.gantry(s)
        return Canvas { context, _ in
            let housing = Path(roundedRect: CGRect(x: G.cx - half, y: G.cy - G.housingH / 2, width: half * 2, height: G.housingH), cornerRadius: G.housingH / 2)
            context.fill(housing, with: .color(Color(.sRGB, red: 12 / 255, green: 11 / 255, blue: 13 / 255, opacity: 0.92)))
            context.stroke(housing, with: .color(.white.opacity(0.16)), lineWidth: 0.6)
            for i in 0..<5 {
                let x = G.cx + Double(i - 2) * G.pitch
                context.fill(Path(ellipseIn: CGRect(x: x - G.radius, y: G.cy - G.radius, width: G.radius * 2, height: G.radius * 2)), with: .color(C.lightOff))
                let lit = Motion.lightOn(s, i) * (1 - Motion.lightAway(s, i))
                if lit > 0.001 {
                    var lamp = context
                    lamp.opacity = lit
                    let bloomR = G.radius * 2.6
                    lamp.fill(Path(ellipseIn: CGRect(x: x - bloomR, y: G.cy - bloomR, width: bloomR * 2, height: bloomR * 2)),
                              with: .radialGradient(Gradient(colors: [Color(.sRGB, red: 1, green: 48 / 255, blue: 32 / 255, opacity: 0.55), Color(.sRGB, red: 1, green: 48 / 255, blue: 32 / 255, opacity: 0)]),
                                                    center: CGPoint(x: x, y: G.cy), startRadius: 0, endRadius: bloomR))
                    lamp.fill(Path(ellipseIn: CGRect(x: x - G.radius, y: G.cy - G.radius, width: G.radius * 2, height: G.radius * 2)),
                              with: .radialGradient(Gradient(stops: [.init(color: C.lightCore, location: 0), .init(color: C.light, location: 0.45),
                                                                     .init(color: Color(.sRGB, red: 196 / 255, green: 23 / 255, blue: 14 / 255, opacity: 1), location: 1)]),
                                                    center: CGPoint(x: x - G.radius * 0.15, y: G.cy - G.radius * 0.2), startRadius: 0, endRadius: G.radius))
                }
            }
        }
        .frame(width: H.width, height: H.height)
        .opacity(on)
    }
}

/// The light streaks over the car body (the world's coverage source-atop the car); a no-op when the car is parked.
private struct PitStopReviewStreaks: ViewModifier {
    let enabled: Bool
    let origin: CGPoint
    let w0: Shader.Argument
    let w1: Shader.Argument
    let streaks: Float

    @ViewBuilder func body(content: Content) -> some View {
        if enabled {
            content.colorEffect(ShaderLibrary.pitStopReviewCarStreaks(.float2(origin), w0, w1, .float(streaks)))
        } else {
            content
        }
    }
}

/// The pin's light trail at full strength (design pt, car at rest): red spill over y 44–170 to x ≈ 150, a white-hot core
/// over y 70–100 to x ≈ 110, fine streaks through it. Baked once into a raster and only scaled and faded per frame.
@MainActor
enum PitStopReviewTrail {
    static let box = CGRect(x: -120, y: 0, width: 300, height: 200)

    static let image: UIImage = {
        let renderer = ImageRenderer(content: PitStopReviewTrailArt().frame(width: box.width, height: box.height, alignment: .topLeading))
        renderer.scale = 2
        renderer.isOpaque = false
        return renderer.uiImage ?? UIImage()
    }()
}

private struct PitStopReviewTrailArt: View {
    typealias H = PitStopReviewTheme.Hero
    private let box = PitStopReviewTrail.box

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(H.blooms.enumerated()), id: \.offset) { _, o in
                Ellipse().fill(Color(.sRGB, red: Double((o.color >> 16) & 255) / 255, green: Double((o.color >> 8) & 255) / 255, blue: Double(o.color & 255) / 255, opacity: o.alpha))
                    .frame(width: o.w, height: o.h).blur(radius: o.blur).offset(x: o.x - box.minX, y: o.y - box.minY)
            }
            Ellipse().fill(LinearGradient(stops: [
                .init(color: Color(.sRGB, red: 1, green: 1, blue: 1, opacity: 1), location: 0),
                .init(color: Color(.sRGB, red: 1, green: 1, blue: 250 / 255, opacity: 1), location: 0.45),
                .init(color: Color(.sRGB, red: 1, green: 236 / 255, blue: 170 / 255, opacity: 0.95), location: 0.68),
                .init(color: Color(.sRGB, red: 1, green: 128 / 255, blue: 30 / 255, opacity: 0.55), location: 0.86),
                .init(color: Color(.sRGB, red: 1, green: 60 / 255, blue: 20 / 255, opacity: 0), location: 1),
            ], startPoint: .leading, endPoint: .trailing))
            .frame(width: H.core.w, height: H.core.h).blur(radius: H.core.blur).offset(x: H.core.x - box.minX, y: H.core.y - box.minY)
            Canvas { context, _ in
                for i in 0..<H.streaks {
                    let y = H.streakTop + Double((i * 37) % 100) / 100 * H.streakSpan - box.minY
                    let x0 = -45.0 - Double((i * 13) % 37) - box.minX
                    let x1 = 55.0 + Double((i * 29) % 70) - box.minX
                    var path = Path()
                    path.move(to: CGPoint(x: x0, y: y))
                    path.addLine(to: CGPoint(x: x1, y: y))
                    context.stroke(path, with: .linearGradient(Gradient(stops: [
                        .init(color: .white.opacity(i % 3 == 0 ? 0.95 : 0.5), location: 0),
                        .init(color: Color(.sRGB, red: 1, green: 210 / 255, blue: 90 / 255, opacity: 0.6), location: 0.6),
                        .init(color: Color(.sRGB, red: 1, green: 90 / 255, blue: 30 / 255, opacity: 0), location: 1),
                    ]), startPoint: CGPoint(x: x0, y: y), endPoint: CGPoint(x: x1, y: y)),
                                   style: StrokeStyle(lineWidth: i % 3 == 0 ? 1.6 : 0.8, lineCap: .round))
                }
            }
        }
    }
}
