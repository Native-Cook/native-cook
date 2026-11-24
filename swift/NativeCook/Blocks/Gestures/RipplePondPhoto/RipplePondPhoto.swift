// Ripple Pond Photo — a travel journal's photo page whose water answers the finger.
//
// The page is a full-bleed photo of a boat on still water with the entry's title over it. The water
// is a real 2D wave simulation on the GPU (RipplePondPhotoWater, constants in RipplePondPhotoMotion):
// a tap drops a ring, a drag leaves a wake, rings bounce off the screen's edges and die down, and the
// photo is seen through the surface. The title and the notes are native text above the water, so
// they stay sharp.
//
// autoPlay: loop = 10 s. A ghost finger taps twice, drags a wake across, taps once more, then the
// water is damped flat and cleared at t = 0, so frame 10 s equals frame 0. A touch takes over at once
// (the ghost fades); 2 s after the last touch the water is calmed and the loop restarts at t = 0.
// Reduced motion: the still photo, no water and no loop.

import SwiftUI
import UIKit

struct RipplePondPhoto: View {
    static let loop: Double = RipplePondPhotoMotion.loop

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var pond = RipplePondPhotoPond()
    @State private var touching = false

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private static let ink = Color(hex: 0xF4FBFA)
    private static let deep = Color(hex: 0x062E33)
    private static func ink(_ a: Double) -> Color { .rgba(244, 251, 250, a) }
    private static let ghost = 46.0
    private static let photo: UIImage? = Bundle.main.url(forResource: "RipplePondPhoto-cove", withExtension: "jpg")
        .flatMap { UIImage(contentsOfFile: $0.path) }

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let w = proxy.size.width, h = proxy.size.height + top + bottom
            page(w, h, top, bottom)
                .frame(width: w, height: h, alignment: .topLeading)
                .clipped()
                .contentShape(Rectangle())
                .gesture(reduced ? nil : touch)
                .offset(y: -top)
        }
        .background(Self.deep.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if touching {
                    pond.touchMoved(x: value.location.x, y: value.location.y)
                } else {
                    touching = true
                    pond.touchBegan(x: value.location.x, y: value.location.y)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
            .onEnded { _ in
                touching = false
                pond.touchEnded()
            }
    }

    private func page(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) -> some View {
        let narrow = h < 700
        let titleSize: Double = narrow ? 28 : 32
        let scrim: Double = narrow ? 170 : 200
        let _ = pond.auto = autoPlay && !reduced
        return ZStack(alignment: .topLeading) {
            if let photo = Self.photo {
                Image(uiImage: photo)
                    .resizable()
                    .scaledToFill()
                    .frame(width: w, height: h)
                    .clipped()
                    .accessibilityLabel("A blue boat on clear turquoise water")
                if !reduced, let cg = photo.cgImage {
                    RipplePondPhotoWater(pond: pond, photo: cg, width: w, height: h)
                        .frame(width: w, height: h)
                        .accessibilityHidden(true)
                }
            }

            // the page's text sits above the water, on two soft scrims
            LinearGradient(
                stops: [.init(color: .rgba(6, 46, 51, 0.62), location: 0), .init(color: .rgba(6, 46, 51, 0.3), location: 0.55), .init(color: .rgba(6, 46, 51, 0), location: 1)],
                startPoint: .top, endPoint: .bottom
            )
            .frame(width: w, height: top + scrim)
            LinearGradient(
                stops: [.init(color: .rgba(4, 38, 44, 0), location: 0), .init(color: .rgba(4, 38, 44, 0.34), location: 0.5), .init(color: .rgba(4, 38, 44, 0.7), location: 1)],
                startPoint: .top, endPoint: .bottom
            )
            .frame(width: w, height: bottom + scrim)
            .offset(y: h - bottom - scrim)

            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Halcyon")
                        .font(.system(size: 15, weight: .semibold))
                        .tracking(0.2)
                        .foregroundStyle(Self.ink)
                    Spacer(minLength: 0)
                    Text("Day 6 of 11")
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(Self.ink(0.74))
                }
                .frame(height: 18)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Still water at").frame(height: titleSize + 4)
                    Text("Cala Brisa").frame(height: titleSize + 4)
                }
                .font(.system(size: titleSize, weight: .bold))
                .tracking(-0.6)
                .foregroundStyle(Self.ink)
                // the 36 pt line box sits a point higher than the font's own
                .offset(y: -1)
                .padding(.top, 22)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isHeader)
                Text("Thursday 12 June · 7:40 am")
                    .font(.system(size: 15, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.ink(0.8))
                    .frame(height: 18)
                    .padding(.top, 8)
            }
            .frame(width: w - 48, alignment: .leading)
            .offset(x: 24, y: top + 12)

            VStack(alignment: .leading, spacing: 0) {
                Text("Anchored off the south beach. Not a breath of wind until nine, and the bottom clear at four metres.")
                    .font(.system(size: 15))
                    .lineSpacing(3.1)
                    .lineLimit(3)
                    .foregroundStyle(Self.ink(0.9))
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.vertical, 1.55)
                HStack {
                    Text("Water 23° · Wind calm")
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(Self.ink(0.74))
                    Spacer(minLength: 0)
                    if reduced {
                        EmptyView()
                    } else {
                        TimelineView(.animation) { _ in
                            Text("Touch the water")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(Self.ink)
                                .opacity(pond.hint() * 0.62)
                        }
                    }
                }
                .frame(height: 16)
                .padding(.top, 12)
            }
            .frame(width: w - 48, alignment: .leading)
            .frame(width: w, height: h - max(bottom, 14) - 14, alignment: .bottom)

            if !reduced {
                TimelineView(.animation) { _ in
                    let g = pond.ghost()
                    finger
                        .scaleEffect(1 - 0.16 * g.press)
                        .opacity(g.alpha)
                        .offset(x: g.x - Self.ghost / 2, y: g.y - Self.ghost / 2)
                }
                .frame(width: w, height: h, alignment: .topLeading)
                .accessibilityHidden(true)
            }
        }
        .allowsHitTesting(false)
    }

    /// the RN ghost: a 10 % disc, a 1.5 pt ring, an outer and an inset glow, and a 14 pt glowing core
    private var finger: some View {
        ZStack {
            Circle().fill(Color.white.opacity(0.1))
            Circle()
                .stroke(Color.white.opacity(0.22), lineWidth: 10)
                .blur(radius: 5)
                .clipShape(Circle())
            Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1.5)
            Circle()
                .fill(Color.white.opacity(0.62))
                .frame(width: 14, height: 14)
                // the glows overflow without taking part in the layout
                .background(Self.outerGlow(diameter: 14, blur: 4, alpha: 0.6))
        }
        .frame(width: Self.ghost, height: Self.ghost)
        .background(Self.outerGlow(diameter: Self.ghost, blur: 9, alpha: 0.38))
        .compositingGroup()
    }

    /// a CSS box-shadow: blurred, and drawn only outside the shape (blur = CSS blur / 2)
    private static func outerGlow(diameter: Double, blur: Double, alpha: Double) -> some View {
        let box = diameter + blur * 8
        return Circle()
            .fill(Color.white.opacity(alpha))
            .frame(width: diameter, height: diameter)
            .blur(radius: blur)
            .frame(width: box, height: box)
            .mask(
                Path { path in
                    path.addRect(CGRect(x: 0, y: 0, width: box, height: box))
                    path.addEllipse(in: CGRect(x: (box - diameter) / 2, y: (box - diameter) / 2, width: diameter, height: diameter))
                }
                .fill(style: FillStyle(eoFill: true))
            )
    }
}
