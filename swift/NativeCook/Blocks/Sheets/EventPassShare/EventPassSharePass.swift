import SwiftUI

struct EventPassSharePass: View {
    var frame: EventPassShareFrame
    var scale: Double
    var onAccessibilityTap: (() -> Void)?

    init(frame: EventPassShareFrame, scale: Double, onAccessibilityTap: (() -> Void)? = nil) {
        self.frame = frame
        self.scale = scale
        self.onAccessibilityTap = onAccessibilityTap
    }

    var body: some View {
        ZStack {
            frontFace
                .accessibilityHidden(true)
                .modifier(EventPassShareProjection(
                    tiltX: frame.quietCrossfade ? 0 : frame.tiltX,
                    turnY: frame.quietCrossfade ? 0 : frame.tiltY + frame.flipAngle,
                    perspective: 650 * scale
                ))
                .opacity(max(0.004, frame.frontOpacity))
                .shadow(color: .black.opacity(0.25 * frame.frontOpacity), radius: 18 * scale, y: 12 * scale)
                .accessibilityHidden(true)

            backFace
                .accessibilityHidden(true)
                .modifier(EventPassShareProjection(
                    tiltX: frame.quietCrossfade ? 0 : frame.tiltX,
                    turnY: frame.quietCrossfade ? 0 : frame.tiltY + frame.flipAngle - 180,
                    perspective: 650 * scale
                ))
                .opacity(frame.backOpacity)
                .shadow(color: .black.opacity(0.25 * frame.backOpacity), radius: 18 * scale, y: 12 * scale)
                .accessibilityHidden(true)
        }
        .frame(width: 270.3 * scale, height: 382 * scale, alignment: .topLeading)
        .scaleEffect(frame.pressScale)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(frame.backOpacity > frame.frontOpacity
            ? "Event pass details. Ticket COMMON-0718. Tap to return to poster."
            : "Event poster. Tap to show event pass and QR code.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onAccessibilityTap?() }
    }

    private var glass: some View {
        RoundedRectangle(cornerRadius: 23 * scale, style: .continuous)
            .fill(Color(red: 56 / 255, green: 58 / 255, blue: 64 / 255).opacity(0.40))
            .overlay(RoundedRectangle(cornerRadius: 23 * scale, style: .continuous).strokeBorder(.white.opacity(0.18), lineWidth: 1 * scale))
    }

    private var posterScale: Double { scale * 250.4 / 251 }

    private var frontFace: some View {
        ZStack(alignment: .topLeading) {
            glass
            EventPassSharePoster(scale: posterScale)
                .offset(x: 9.1 * scale, y: 9.5 * scale)
            EventPassShareFoil(frame: frame, scale: scale)
                .offset(x: 9.1 * scale, y: 9.5 * scale)
            Text(EventPassShareContent.title)
                .font(.system(size: 22.7 * scale, weight: .semibold))
                .tracking(-0.55 * scale)
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.78)
                .frame(width: 238 * scale, height: 27 * scale, alignment: .topLeading)
                .offset(x: 16.7 * scale, y: 272 * scale)
            (Text("Jul 18  ").fontWeight(.semibold).foregroundColor(.white.opacity(0.92)) +
             Text("Los Angeles").fontWeight(.medium).foregroundColor(.white.opacity(0.62)))
                .font(.system(size: 13 * scale))
                .lineLimit(1)
                .frame(width: 210 * scale, height: 18 * scale, alignment: .topLeading)
                .offset(x: 17.3 * scale, y: 345 * scale)
        }
        .frame(width: 270.3 * scale, height: 382 * scale, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 23 * scale, style: .continuous))
    }

    private var backFace: some View {
        ZStack(alignment: .topLeading) {
            glass
            LinearGradient(colors: [.white.opacity(0.04), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)
                .clipShape(RoundedRectangle(cornerRadius: 23 * scale, style: .continuous))
            VStack(spacing: 11 * scale) {
                Text("COMMUNITY PASS")
                    .font(.system(size: 10 * scale, weight: .bold))
                    .tracking(1.6 * scale)
                    .foregroundStyle(.white.opacity(0.80))
                    .frame(height: 13 * scale)
                Image(uiImage: EventPassShareAssets.qrCode)
                    .interpolation(.none)
                    .resizable()
                    .frame(width: 166 * scale, height: 166 * scale)
                    .padding(9 * scale)
                    .background(Color(hex: 0xFAF9F7), in: RoundedRectangle(cornerRadius: 13 * scale, style: .continuous))
                Text(EventPassShareContent.title)
                    .font(.system(size: 17 * scale, weight: .semibold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .frame(height: 21 * scale)
                Text(EventPassShareContent.ticket)
                    .font(.system(size: 11 * scale, weight: .semibold, design: .monospaced))
                    .tracking(1.0 * scale)
                    .foregroundStyle(.white.opacity(0.78))
                    .frame(height: 14 * scale)
                Text("↩︎  Tap to return")
                    .font(.system(size: 10 * scale, weight: .medium))
                    .foregroundStyle(.white.opacity(0.58))
                    .frame(height: 13 * scale)
            }
            .frame(width: 270.3 * scale)
            .offset(y: 25 * scale)
        }
        .frame(width: 270.3 * scale, height: 382 * scale, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 23 * scale, style: .continuous))
    }

}

struct EventPassShareFoil: View {
    var frame: EventPassShareFrame
    var scale: Double

    var body: some View {
        let side = 250.4 * scale
        let posterScale = scale * 250.4 / 251
        let turn = frame.quietCrossfade ? 0 : frame.flipAngle
        let uniform = EventPassShareFoilUniform.sample(
            tiltX: frame.tiltX,
            tiltY: frame.tiltY,
            flipAngle: turn,
            scale: scale
        )
        EventPassSharePoster(scale: posterScale)
            .layerEffect(
                ShaderLibrary.eventPassShareFoil(
                    .float2(side, side),
                    .float4(uniform.tiltX, uniform.shadeY, uniform.amount, uniform.scale)
                ),
                maxSampleOffset: CGSize(width: scale, height: scale)
            )
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
