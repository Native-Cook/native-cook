import SwiftUI

struct PrismSphereGeneratingDemo: View {
    var body: some View {
        PrismSphereGenerating(autoPlay: true)
    }
}

/// A frosted, pleated shell traced analytically in Metal, with a ten-second self-playing loop.
struct PrismSphereGenerating: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var motion = PrismSphereGeneratingMotion(autoPlay: false)

    var body: some View {
        GeometryReader { geometry in
            let layout = PrismSphereGeneratingLayout(size: geometry.size)
            TimelineView(.animation(minimumInterval: 1 / 60, paused: scenePhase != .active)) { timeline in
                let frame = sample(timeline.date)
                ZStack(alignment: .topLeading) {
                    Canvas(opaque: true, colorMode: .nonLinear) { context, size in
                        draw(context, size: size, layout: layout, frame: frame)
                    }
                    .opacity(PrismSphereGeneratingTimeline.canvasOpacity(frame.clock))
                    .accessibilityHidden(true)
                    .allowsHitTesting(false)

                    headline(layout: layout, frame: frame)
                    caption(layout: layout, frame: frame)

                    PrismSphereGeneratingTouch(motion: motion, radius: layout.radius, k: layout.k)
                        .frame(width: 272 * layout.k, height: 272 * layout.k)
                        .position(layout.centre)
                }
                .frame(width: geometry.size.width, height: geometry.size.height)
            }
        }
        .background(.black)
        .ignoresSafeArea()
        .statusBarHidden()
    }

    private func sample(_ date: Date) -> PrismSphereGeneratingFrame {
        motion.autoPlay = autoPlay
        motion.reduced = reduceMotion
        return motion.frame(at: motion.elapsed(date))
    }

    private func headline(layout: PrismSphereGeneratingLayout, frame: PrismSphereGeneratingFrame) -> some View {
        let entered = PrismSphereGeneratingTimeline.entrance((frame.clock - 0.1) / 0.7)
        return VStack(spacing: 0) {
            Text("We are creating")
                .frame(height: layout.rounded(44))
            Text("your digital art.")
                .frame(height: layout.rounded(44))
        }
        .font(.system(size: layout.rounded(36), weight: .medium))
        .tracking(-0.6 * layout.k)
        .foregroundStyle(.white)
        .lineLimit(1)
        .fixedSize()
        .position(x: layout.centre.x, y: layout.top + 196 * layout.k + layout.rounded(44))
        .offset(y: reduceMotion ? 0 : 12 * layout.k * (1 - entered))
        .opacity(entered)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("We are creating your digital art.")
        .accessibilityAddTraits(.isHeader)
    }

    private func caption(layout: PrismSphereGeneratingLayout, frame: PrismSphereGeneratingFrame) -> some View {
        let envelope = PrismSphereGeneratingTimeline.caption(frame.clock, reduced: reduceMotion)
        let copy = Int(floor(frame.clock / 5)) % 2 == 0 ? "Please stand by…" : "Shaping the light…"
        let progress = envelope.progress
        return ZStack {
            Text(copy).opacity(max(0, 1 - 2 * progress))
            Text(copy).blur(radius: 4 * layout.k).opacity(1 - abs(2 * progress - 1))
            Text(copy).blur(radius: 9 * layout.k).opacity(max(0, 2 * progress - 1))
        }
        .font(.system(size: layout.rounded(17), weight: .regular))
        .tracking(-0.2 * layout.k)
        .foregroundStyle(.white)
        .lineLimit(1)
        .frame(width: 402 * layout.k, height: layout.rounded(24))
        .opacity(0.92 * envelope.opacity * PrismSphereGeneratingTimeline.canvasOpacity(frame.clock))
        .position(x: layout.centre.x, y: layout.top + 660 * layout.k + layout.rounded(24) / 2)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(copy)
    }

    private func draw(_ context: GraphicsContext, size: CGSize, layout: PrismSphereGeneratingLayout,
                      frame: PrismSphereGeneratingFrame) {
        let matrix = frame.rotation
        let value = frame.values
        let arguments: [Shader.Argument] = [
            .float2(size), .float2(layout.centre), .float(layout.radius),
            .float(frame.scale), .float(frame.sceneClock), .float(value.energy),
            .float(value.charge), .float(value.flash), .float(value.ring),
            .float3(Float(matrix.columns.0.x), Float(matrix.columns.0.y), Float(matrix.columns.0.z)),
            .float3(Float(matrix.columns.1.x), Float(matrix.columns.1.y), Float(matrix.columns.1.z)),
            .float3(Float(matrix.columns.2.x), Float(matrix.columns.2.y), Float(matrix.columns.2.z)),
            .float(value.fan), .float(value.sweep), .float(value.sweepPower),
            .float2(Float(frame.light.x), Float(frame.light.y)),
        ]
        let bounds = CGRect(origin: .zero, size: size)
        let backdrop = Shader(function: ShaderFunction(library: .default, name: "prismSphereGeneratingBackdrop"), arguments: arguments)
        context.fill(Path(bounds), with: .shader(backdrop, bounds: bounds))
        PrismSphereGeneratingMote.draw(context, frame: frame, centre: layout.centre, k: layout.k, back: true)
        let sphere = Shader(function: ShaderFunction(library: .default, name: "prismSphereGeneratingSphere"), arguments: arguments)
        let extent = layout.radius * 1.12 * 1.05
        let sphereBounds = CGRect(x: layout.centre.x - extent, y: layout.centre.y - extent, width: 2 * extent, height: 2 * extent)
        context.fill(Path(sphereBounds), with: .shader(sphere, bounds: bounds))
        PrismSphereGeneratingMote.draw(context, frame: frame, centre: layout.centre, k: layout.k, back: false)
    }
}

struct PrismSphereGeneratingLayout {
    let k: Double
    let top: Double
    let centre: CGPoint
    let radius: Double

    init(size: CGSize) {
        k = min(size.width / 402, size.height / 874)
        let left = (size.width - 402 * k) / 2
        top = (size.height - 874 * k) / 2
        centre = CGPoint(x: left + 201 * k, y: top + 472 * k)
        radius = 124 * k
    }

    func rounded(_ value: Double) -> Double { (value * k * 2).rounded() / 2 }
}

#Preview {
    PrismSphereGeneratingDemo()
}
