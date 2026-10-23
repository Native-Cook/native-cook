import SwiftUI

private let rotationDuration: TimeInterval = 38
private let entryAnimationDuration: TimeInterval = 2.6
private let startAngle: Double = 0
private let entryScale: CGFloat = 1.5
private let restingScale: CGFloat = 1
private let backdropBlur: CGFloat = 16

/// Slowly rotating, blurred card-back artwork with rising particles behind the detail sheet.
struct CollectionCardBackdrop: View {
    let height: CGFloat
    let width: CGFloat

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var rotation: Double = startAngle
    @State private var zoom: CGFloat = restingScale

    var body: some View {
        let planeSize = ceil(hypot(width, height) * 1.08)
        let artSize = max(width * 1.55, height * 0.82)
        let artOffset = (planeSize - artSize) / 2
        let scaleX = artSize / collectionCardArtWidth
        let scaleY = artSize / collectionCardArtHeight

        CollectionCardBackdropPalette.background
            .frame(width: width, height: height)
            // An overlay keeps the oversized rotating plane out of the viewport's layout.
            .overlay {
                Canvas { context, _ in
                    context.fill(Path(CGRect(x: 0, y: 0, width: planeSize, height: planeSize)), with: .color(CollectionCardBackdropPalette.background))
                    var blurred = context
                    blurred.addFilter(.blur(radius: backdropBlur))
                    blurred.drawLayer { layer in
                        layer.translateBy(x: artOffset, y: artOffset)
                        layer.scaleBy(x: scaleX, y: scaleY)
                        drawCollectionCardArtwork(
                            in: &layer,
                            backgroundColor: CollectionCardBackdropPalette.background,
                            patternColor: CollectionCardBackdropPalette.pattern,
                            sealColor: CollectionCardBackdropPalette.seal,
                            collectorMark: .hidden
                        )
                    }
                }
                .frame(width: planeSize, height: planeSize)
                .rotationEffect(.degrees(rotation))
                .scaleEffect(zoom)
            }
            .overlay {
                RisingParticles(height: height, width: width)
            }
            .clipped()
            .allowsHitTesting(false)
            .onAppear(perform: restartAnimation)
            .onChange(of: reduceMotion) { _, _ in restartAnimation() }
    }

    private func restartAnimation() {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            rotation = startAngle
            zoom = reduceMotion ? restingScale : entryScale
        }
        guard !reduceMotion else { return }
        withAnimation(.linear(duration: rotationDuration).repeatForever(autoreverses: false)) {
            rotation = startAngle - 360
        }
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: entryAnimationDuration)) {
            zoom = restingScale
        }
    }
}

#Preview {
    CollectionCardBackdrop(height: 700, width: 390)
}
