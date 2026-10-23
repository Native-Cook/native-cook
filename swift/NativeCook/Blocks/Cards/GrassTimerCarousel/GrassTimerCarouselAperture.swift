import SwiftUI
import UIKit

/// The only continuously redrawn region: one cached photo clipped from its resting card into a lens.
struct GrassTimerCarouselAperture: View {
    var image: UIImage
    var frame: GrassTimerCarouselFrame
    var scale: Double

    var body: some View {
        Canvas { context, _ in
            let aperture = scaled(frame.aperture)
            let path = Path(roundedRect: aperture, cornerRadius: frame.aperture.cornerRadius * scale, style: .continuous)
            context.clip(to: path)
            let size = image.size
            guard size.width > 0, size.height > 0 else {
                context.fill(path, with: .color(Color(hex: 0x26331c)))
                return
            }
            let fill = max(aperture.width / size.width, aperture.height / size.height)
            let drawn = CGSize(width: size.width * fill, height: size.height * fill)
            let translation = frame.imageParallax * scale
            context.draw(
                Image(uiImage: image).interpolation(.medium),
                in: CGRect(
                    x: aperture.midX - drawn.width * 0.5,
                    y: aperture.midY - drawn.height * 0.5 + translation,
                    width: drawn.width,
                    height: drawn.height
                )
            )
            context.fill(
                path,
                with: .linearGradient(
                    Gradient(colors: [
                        .black.opacity(0.16 * frame.timerOpacity),
                        .black.opacity(0.12 + 0.16 * frame.timerOpacity)
                    ]),
                    startPoint: CGPoint(x: aperture.midX, y: aperture.minY),
                    endPoint: CGPoint(x: aperture.midX, y: aperture.maxY)
                )
            )
        }
        .frame(width: 402 * scale, height: 392 * scale, alignment: .topLeading)
        .offset(y: -62 * scale)
        .allowsHitTesting(false)
    }

    private func scaled(_ rect: GrassTimerCarouselRect) -> CGRect {
        // Draw within positive Canvas bounds; its offset reaches behind the safe-area chrome.
        CGRect(
            x: rect.x * scale,
            y: rect.y * scale,
            width: rect.width * scale,
            height: rect.height * scale
        )
    }
}
