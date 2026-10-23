import SwiftUI

private struct ParticleSpec {
    let drift: CGFloat
    let duration: TimeInterval
    let id: Int
    let left: CGFloat
    let maxOpacity: CGFloat
    let phase: CGFloat
    let size: CGFloat
    let waveOffset: CGFloat
}

// Stable inputs keep the ambient field deterministic across renders.
private let particleCount = 26
private let particleSizes: [CGFloat] = [3, 4, 6, 3, 8, 5, 11, 4, 7, 3, 5, 9]
private let twoPi = CGFloat.pi * 2

private func fraction(_ index: Int, _ multiplier: Int, _ offset: Int) -> CGFloat {
    CGFloat((index * multiplier + offset) % 997) / 997
}

private let particles: [ParticleSpec] = {
    var specs: [ParticleSpec] = []
    for index in 0..<particleCount {
        let size: CGFloat = particleSizes[index % particleSizes.count]
        let direction: CGFloat = index % 2 == 0 ? -1 : 1
        let drift: CGFloat = direction * (4 + fraction(index, 179, 41) * 10)
        let duration: TimeInterval = 11.5 + Double(fraction(index, 263, 73)) * 6
        let left: CGFloat = 0.03 + fraction(index, 619, 17) * 0.94
        let opacityBase: CGFloat = 0.22 + size * 0.022 + fraction(index, 131, 59) * 0.08
        let maxOpacity: CGFloat = min(0.54, opacityBase)
        let phase: CGFloat = fraction(index, 397, 29)
        let waveOffset: CGFloat = fraction(index, 449, 97) * twoPi
        specs.append(ParticleSpec(
            drift: drift,
            duration: duration,
            id: index,
            left: left,
            maxOpacity: maxOpacity,
            phase: phase,
            size: size,
            waveOffset: waveOffset
        ))
    }
    return specs
}()

/// White particles drifting upwards behind the detail sheet, drawn in a single canvas.
struct RisingParticles: View {
    let height: CGFloat
    let width: CGFloat

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var startDate = Date()

    var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { timeline in
            let elapsed = timeline.date.timeIntervalSince(startDate)
            Canvas { context, _ in
                for particle in particles {
                    let cycle: CGFloat = reduceMotion
                        ? particle.phase
                        : (particle.phase + CGFloat(elapsed / particle.duration)).truncatingRemainder(dividingBy: 1)
                    let translateY = interpolate(cycle, input: [0, 1], output: [height + particle.size, -particle.size])
                    let opacity = interpolate(
                        cycle,
                        input: [0, 0.12, 0.58, 1],
                        output: reduceMotion
                            ? [0, particle.maxOpacity * 0.34, particle.maxOpacity * 0.24, 0]
                            : [0, particle.maxOpacity, particle.maxOpacity * 0.68, 0]
                    )
                    let translateX: CGFloat = reduceMotion
                        ? 0
                        : sin(cycle * twoPi + particle.waveOffset) * particle.drift
                    let x = particle.left * width - particle.size / 2 + translateX
                    let rect = CGRect(x: x, y: translateY, width: particle.size, height: particle.size)

                    var layer = context
                    layer.opacity = Double(opacity)
                    layer.addFilter(.shadow(color: Color(css: "rgba(255, 255, 255, 0.58)"), radius: max(4, particle.size) / 2))
                    layer.fill(Path(ellipseIn: rect), with: .color(Color(css: "rgba(255, 255, 255, 0.96)")))
                }
            }
        }
        .frame(width: width, height: height)
        .allowsHitTesting(false)
    }
}

#Preview {
    RisingParticles(height: 600, width: 390)
        .background(CollectionCardBackdropPalette.background)
}
