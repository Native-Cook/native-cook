import SwiftUI
import UIKit

/// Paper Lantern Confirm — "Lantern Night", a wishes app's confirm sheet. A folded paper sky lantern
/// stands in the sheet with the wish written on it. Confirm and the wick lights, the paper glows from
/// inside, the lantern fills, lifts off out of the sheet and rises into the night among the other
/// lanterns, smaller with distance; a "Wish sent" toast drops in, and a new lantern unfolds.
///
/// Drawing: the sky is one Metal colour effect over the whole screen behind the sheet; each of the two
/// lanterns (the one in the sheet and the one in flight) is a second colour effect in a 180 × 240 pt
/// view under a scale and an offset, drawn above the sheet with the wish over it. Everything moving
/// reads one scene computed per frame (PaperLanternConfirmMotion.swift).
///
/// Touch: hold the lantern to light it (the wick catches in 0.5 s, then it fills in about a second);
/// drag it up while you hold it; let go of a full lantern above its seat, or flick it up, and it flies
/// off into the loop's rise. A lantern let go before it is full sinks back to its seat and folds
/// again; 2 s later it eases back to rest (0.9 s) and the loop restarts. Tap "Light and send" to play
/// the send. Reduced motion: no loop; the lantern stands lit and full in the sheet.
struct PaperLanternConfirm: View {
    /// Plays the 9 s light-fill-rise-toast-new-lantern loop.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = PaperLanternConfirmEngine()

    private typealias P = PaperLanternConfirmPalette
    private typealias M = PaperLanternConfirmMotion

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let L = PaperLanternConfirmLayout(width: size.width, height: size.height, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            P.night

            TimelineView(.animation(paused: reduced)) { timeline in
                let f = engine.step(to: timeline.date, layout: L, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.paperLanternConfirmSky(
                            .float2(size.width, size.height),
                            .float(L.sheetTop + 26),
                            .float(f.flicker)
                        ))
                        .frame(width: size.width, height: size.height)
                    header(size: size, insets: insets)
                    PaperLanternConfirmSheet(labelPos: f.labelPos, press: f.scene.press, bottom: insets.bottom)
                        .frame(width: size.width, height: size.height - L.sheetTop)
                        .offset(y: L.sheetTop)
                    lantern(f.scene.seat, flicker: f.flicker, seed: 3, ink: 0.82 * (1 - max(0, f.scene.seat.fold - M.restFold) / (1 - M.restFold)))
                    lantern(f.scene.fly, flicker: f.flicker, seed: 7, ink: 0.82)
                    toast(f.scene.toast, size: size, insets: insets)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            if !reduced {
                // The lantern's seat takes the hold and the drag.
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: 160, height: 190)
                    .offset(x: L.seatX - 80, y: L.seatY - 110)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                if !engine.fingerDown {
                                    engine.fingerDown = true
                                    engine.holdBegan()
                                }
                                engine.holdMoved(value.translation.height)
                            }
                            .onEnded { value in
                                engine.fingerDown = false
                                engine.holdEnded(velocityY: value.velocity.height)
                            }
                    )
                    .accessibilityElement()
                    .accessibilityLabel("Paper lantern. Hold to light it, then drag it up to let it fly.")

                // "Light and send".
                Color.clear
                    .contentShape(Capsule())
                    .frame(width: size.width - 40, height: 52)
                    .offset(x: 20, y: L.sheetTop + PaperLanternConfirmSheet.buttonTop)
                    .onTapGesture { engine.send() }
                    .accessibilityElement()
                    .accessibilityLabel("Light and send")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { engine.send() }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func header(size: CGSize, insets: EdgeInsets) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("LANTERN NIGHT")
                .font(.system(size: 11, weight: .heavy))
                .kerning(2.2)
                .foregroundStyle(P.hex(0xF7B266))
            Text("Riverside · Friday 8:40 pm")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color(red: 250 / 255, green: 236 / 255, blue: 220 / 255).opacity(0.8))
        }
        .frame(width: size.width - 48, alignment: .leading)
        .offset(x: 24, y: insets.top + 18)
    }

    /// One lantern: the paper (a shader in a 180 × 240 view whose middle is at (90, 130)) and the wish.
    private func lantern(_ lamp: PaperLanternConfirmLamp, flicker: Double, seed: Double, ink: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(Color.white.opacity(lamp.o))
                .colorEffect(ShaderLibrary.paperLanternConfirmLantern(
                    .float2(90, 130),
                    .float(lamp.fold),
                    .float(lamp.glow),
                    .float(flicker),
                    .float(seed)
                ))
            PaperLanternConfirmWish()
                .opacity(lamp.o * ink)
        }
        .frame(width: 180, height: 240, alignment: .topLeading)
        .scaleEffect(x: lamp.sx, y: lamp.sy, anchor: UnitPoint(x: 0.5, y: 130.0 / 240.0))
        .offset(x: lamp.x - 90, y: lamp.y - 130)
    }

    private func toast(_ k: Double, size: CGSize, insets: EdgeInsets) -> some View {
        HStack(spacing: 10) {
            ZStack {
                Circle().fill(P.hex(0xF7B266))
                Image(systemName: "checkmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(P.hex(0x2A1708))
            }
            .frame(width: 36, height: 36)
            VStack(alignment: .leading, spacing: 0) {
                Text("Wish sent")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(P.hex(0xFAEEDC))
                Text("Lantern 1,284 is in the sky")
                    .font(.system(size: 12, weight: .medium).monospacedDigit())
                    .foregroundStyle(P.hex(0xFAEEDC, 0.66))
            }
        }
        .padding(.leading, 8)
        .padding(.trailing, 18)
        .frame(height: 52)
        .background {
            Capsule()
                .fill(Color(red: 32 / 255, green: 24 / 255, blue: 44 / 255).opacity(0.94))
                .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.4), radius: 15, y: 12)
        }
        .overlay { Capsule().strokeBorder(P.hex(0xF7B266, 0.3), lineWidth: 1) }
        .offset(y: -70 * (1 - k))
        .opacity(k)
        .frame(width: size.width)
        .offset(y: insets.top + 10)
    }
}

enum PaperLanternConfirmPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255, opacity: a)
    }

    static let night = hex(0x0A0D22)
    static let paper = hex(0xF5EDDF)
    static let ink = hex(0x2E2119)
    static let inkSoft = hex(0x2E2119, 0.6)
    static let amber = hex(0xE9822F)
}

/// The wish on the paper, its baselines where the React Native version draws them: 22 pt above the
/// lantern's middle, then 24 and 50 pt below that.
private struct PaperLanternConfirmWish: View, Equatable {
    private static let wishFont = UIFont(name: "Georgia-Italic", size: 19) ?? .italicSystemFont(ofSize: 19)
    private static let signFont = UIFont(name: "Georgia-Italic", size: 13) ?? .italicSystemFont(ofSize: 13)

    var body: some View {
        Canvas { ctx, _ in
            func line(_ s: String, _ font: UIFont, _ color: UInt32, baseline: Double) {
                let text = ctx.resolve(Text(s).font(Font(font as CTFont)).foregroundStyle(PaperLanternConfirmPalette.hex(color)))
                let anchorY = font.ascender / font.lineHeight
                ctx.draw(text, at: CGPoint(x: 90, y: 130 + baseline), anchor: UnitPoint(x: 0.5, y: anchorY))
            }
            line("Safe travels", Self.wishFont, 0x5B3B28, baseline: -22)
            line("home, Amara", Self.wishFont, 0x5B3B28, baseline: 2)
            line("love, June", Self.signFont, 0x7A5640, baseline: 28)
        }
        .frame(width: 180, height: 240)
    }
}

/// The confirm sheet (the lantern is drawn over it, not in it).
private struct PaperLanternConfirmSheet: View {
    let labelPos: Double
    let press: Double
    let bottom: Double

    private typealias P = PaperLanternConfirmPalette
    private static let labels = ["Light and send", "Lighting the wick…", "Wish sent"]
    /// The button's top below the sheet's top: grabber 8 + 5, title 14 + 29, subtitle 4 + 17, the
    /// lantern's space 206, chips 30 + 14 (= 327).
    static let buttonTop: Double = 327

    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(P.ink.opacity(0.18))
                .frame(width: 40, height: 5)
                .padding(.top, 8)
            Text("Send your wish?")
                .font(.system(size: 24, weight: .heavy))
                .kerning(-0.4)
                .foregroundStyle(P.ink)
                .frame(height: 29)
                .padding(.top, 14)
            Text("It will rise with 1,283 other lanterns tonight")
                .font(.system(size: 14, weight: .medium).monospacedDigit())
                .foregroundStyle(P.inkSoft)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(height: 17)
                .padding(.top, 4)
            Color.clear.frame(height: 206)
            HStack(spacing: 8) {
                chip("person.fill", "From June")
                chip("heart.fill", "$5 · River Fund")
            }
            .padding(.bottom, 14)
            button
            Text("Edit wish")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(P.inkSoft)
                .padding(.vertical, 12)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, bottom)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background {
            UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous)
                .fill(P.paper)
                .shadow(color: Color(red: 5 / 255, green: 5 / 255, blue: 20 / 255).opacity(0.45), radius: 25, y: -20)
                .shadow(color: .white.opacity(0.6), radius: 0, y: -1)
        }
    }

    private var button: some View {
        ZStack {
            ForEach(-1...3, id: \.self) { i in
                let d = Double(i) - labelPos
                Text(Self.labels[(i + 3) % 3])
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(P.hex(0x2A1708))
                    .lineLimit(1)
                    .frame(height: 24)
                    .offset(y: d * 24)
                    .opacity(max(0, 1 - abs(d) * 1.2))
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 24)
        .clipped()
        .padding(.horizontal, 20)
        .frame(height: 52)
        .background {
            Capsule()
                .fill(LinearGradient(
                    stops: [.init(color: P.hex(0xF4A24A), location: 0), .init(color: P.hex(0xE9822F), location: 0.5), .init(color: P.hex(0xC9561F), location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                ))
                .shadow(color: Color(red: 90 / 255, green: 40 / 255, blue: 10 / 255).opacity(0.3), radius: 0.5, y: 1)
                .shadow(color: P.amber.opacity(0.35), radius: 11, y: 10)
        }
        .overlay {
            Capsule()
                .inset(by: 0.5)
                .stroke(LinearGradient(colors: [Color(red: 1, green: 230 / 255, blue: 190 / 255).opacity(0.6), .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.3)), lineWidth: 1)
        }
        .scaleEffect(1 - 0.03 * press)
    }

    private func chip(_ icon: String, _ label: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 11))
                .foregroundStyle(P.amber)
            Text(label)
                .font(.system(size: 13, weight: .semibold).monospacedDigit())
                .foregroundStyle(P.ink)
        }
        .padding(.horizontal, 12)
        .frame(height: 30)
        .background(P.amber.opacity(0.1), in: Capsule())
    }
}
