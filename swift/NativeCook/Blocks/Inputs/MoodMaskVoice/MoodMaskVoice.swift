import SwiftUI
import UIKit
import CoreText

// Port of the React Native reference. All distances are points.
// Loop (8 s): 0–1 rest; 1–3.4 listen (0.3 s fades); 3.4–4 warm;
// 3.8–6.4 speak (0.3 s fades); 6.4–7.4 cool; 7.4–8 rest.
// Hold anywhere to listen. Release resumes the loop at 3.4 s, the reply,
// exactly as the reference does, rather than delaying that answer.
struct MoodMaskVoice: View {
    static let loop: Double = 8

    var name = "Mira"
    var idle = "Hold to talk"
    var listening = "Listening…"
    var reply = "That’s wonderful news. Tell me everything!"
    var autoPlay = false

    private static let faceWidth = 236.0
    private static let faceHeight = 354.0
    private static let replyAt = 3.4
    private static let bars = 28

    private enum Typography {
        static let family = "InstrumentSerif-Italic"
        static let cache = NSCache<NSString, UIImage>()
        static let registered: Void = {
            if let url = Bundle.main.url(forResource: "MoodMaskVoice-InstrumentSerif-Italic",
                                         withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }()

        // RN uses TextKit with fixed line heights, including heights below the
        // font's natural leading. Cache that layout instead of SwiftUI Text,
        // which clamps negative line spacing for this face.
        static func image(_ text: String, size: CGFloat, lineHeight: CGFloat,
                          width: CGFloat? = nil, height: CGFloat, color: Color) -> UIImage {
            _ = registered
            let key = "\(text)|\(size)|\(lineHeight)|\(width ?? 0)|\(height)|\(color)" as NSString
            if let cached = cache.object(forKey: key) { return cached }
            let font = UIFont(name: family, size: size) ?? .systemFont(ofSize: size)
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            paragraph.alignment = .center
            let storage = NSTextStorage(string: text, attributes: [
                .font: font, .foregroundColor: UIColor(color), .paragraphStyle: paragraph,
            ])
            let format = UIGraphicsImageRendererFormat()
            let textWidth = width ?? ceil(storage.size().width * format.scale) / format.scale
            let manager = NSLayoutManager()
            manager.usesFontLeading = false
            let container = NSTextContainer(size: CGSize(width: textWidth, height: height))
            container.lineFragmentPadding = 0
            container.lineBreakMode = .byClipping
            manager.addTextContainer(container)
            storage.addLayoutManager(manager)
            let image = UIGraphicsImageRenderer(size: container.size, format: format).image { _ in
                let range = manager.glyphRange(for: container)
                manager.drawGlyphs(forGlyphRange: range, at: .zero)
            }
            cache.setObject(image, forKey: key)
            return image
        }
    }

    private enum Palette {
        static let background = Color(hex: 0x050508)
        static let text = Color(hex: 0xF4F2FF)
        static let dim = Color(hex: 0x7D7A8F)
        static let mouth = Color(hex: 0x14060F)
        static let mic = Color(hex: 0x16141F)

        // RN rounds each interpolated sRGB channel before drawing.
        static func mix(_ a: UInt32, _ b: UInt32, _ u: Double) -> Color {
            func channel(_ shift: UInt32) -> Double {
                let start = Double((a >> shift) & 255)
                let end = Double((b >> shift) & 255)
                return (start * (1 - u) + end * u).rounded() / 255
            }
            return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
        }
    }

    // The same grayscale + alpha PNG as RN, decoded once.
    private static let mask: Image = {
        guard let url = Bundle.main.url(forResource: "MoodMaskVoice-mask", withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else { return Image(uiImage: UIImage()) }
        return Image(uiImage: image.preparingForDisplay() ?? image)
    }()

    private struct Voice {
        var mood = 0.0
        var listen = 0.0
        var speak = 0.0
        var osc = 0.0
    }

    private struct Hold {
        var start: Double
        var from: Voice
    }

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var gestureTouching = false
    @State private var accumulated = 0.0
    @State private var anchor = Date()
    @State private var running = false
    @State private var loopOrigin = 0.0
    @State private var hold: Hold?
    @State private var frozen = Voice()
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double {
        clamp((t - a) / (b - a))
    }
    private static func smooth(_ x: Double) -> Double {
        let u = clamp(x)
        return u * u * (3 - 2 * u)
    }
    private static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    private static func voice(at t: Double) -> Voice {
        Voice(
            mood: cubic(segment(t, 3.4, 4)) * (1 - cubic(segment(t, 6.4, 7.4))),
            listen: smooth(segment(t, 1, 1.3)) * (1 - smooth(segment(t, 3.1, 3.4))),
            speak: smooth(segment(t, 3.8, 4.1)) * (1 - smooth(segment(t, 6.1, 6.4))),
            osc: t
        )
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(anchor)) : 0)
    }

    private func voice(at now: Double) -> Voice {
        guard !reduced else { return Voice() }
        if let hold {
            // Analytic equivalents of RN's per-frame +/- dt ramps.
            let dt = max(0, now - hold.start)
            return Voice(mood: max(0, hold.from.mood - dt * 2),
                         listen: min(1, hold.from.listen + dt * 4),
                         speak: max(0, hold.from.speak - dt * 4), osc: now)
        }
        guard autoPlay else { return frozen }
        let t = max(0, now - loopOrigin).truncatingRemainder(dividingBy: Self.loop)
        return Self.voice(at: t)
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let faceY = safe.top + 64
            let waveY = faceY + Self.faceHeight + 40
            let micY = size.height - safe.bottom - 70

            TimelineView(.animation(paused: !running || reduced || (!autoPlay && hold == nil))) { frame in
                let voice = voice(at: elapsed(at: frame.date))
                ZStack(alignment: .topLeading) {
                    Palette.background
                    Canvas { context, _ in
                        draw(context: context, size: size, faceY: faceY,
                             waveY: waveY, micY: micY, voice: voice)
                    }
                    .frame(width: size.width, height: size.height)
                    .accessibilityLabel("\(name), voice assistant. Hold to talk.")

                    HStack(spacing: 8) {
                        Circle().fill(Color(hex: 0x7CF0B0)).frame(width: 8, height: 8)
                        Image(uiImage: Typography.image(name, size: 28, lineHeight: 32,
                                                        height: 32, color: Palette.text))
                            .accessibilityLabel(name)
                    }
                    .frame(width: size.width, height: 34)
                    .offset(y: safe.top + 12)
                    .allowsHitTesting(false)

                    captions(voice: voice, width: size.width - 56)
                        .offset(x: 28, y: waveY + 36)
                        .allowsHitTesting(false)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(holdGesture)
            .ignoresSafeArea()
        }
        .background(Palette.background)
        .preferredColorScheme(.dark)
        .onAppear {
            updateClock()
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, _ in updateClock() }
        .onChange(of: reduced) { _, _ in
            if reduced {
                hold = nil
                frozen = Voice()
            }
            updateClock()
        }
        .onChange(of: gestureTouching) { _, touching in
            if !touching { finishHold() }
        }
    }

    private func captions(voice: Voice, width: Double) -> some View {
        let replyOpacity = Self.smooth((voice.mood - 0.3) / 0.4)
        return ZStack(alignment: .top) {
            caption(idle, color: Palette.dim, width: width)
                .opacity(Self.clamp(1 - max(voice.listen, replyOpacity)))
            caption(listening, color: Palette.text, width: width)
                .opacity(voice.listen)
            caption(reply, color: Palette.text, width: width)
                .opacity(replyOpacity)
        }
        .frame(width: width, height: 76, alignment: .top)
    }

    private func caption(_ text: String, color: Color, width: Double) -> some View {
        Image(uiImage: Typography.image(text, size: 30, lineHeight: 36,
                                        width: width, height: 76, color: color))
            .frame(width: width, height: 76, alignment: .top)
            .accessibilityLabel(text)
    }

    private func draw(context: GraphicsContext, size: CGSize, faceY: Double,
                      waveY: Double, micY: Double, voice: Voice) {
        let cx = size.width / 2
        let faceX = cx - Self.faceWidth / 2
        let centre = CGPoint(x: cx, y: faceY + Self.faceHeight / 2)
        let rect = CGRect(x: faceX, y: faceY, width: Self.faceWidth, height: Self.faceHeight)
        let level = max(voice.listen, voice.speak)

        context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
            Gradient(colors: [Palette.mix(0x0B0920, 0x1E0A0C, voice.mood), Palette.background]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))

        context.drawLayer { glow in
            glow.opacity = 0.32 + 0.18 * voice.mood + 0.1 * voice.speak
            glow.addFilter(.blur(radius: 70))
            glow.fill(Path(ellipseIn: CGRect(x: centre.x - 150, y: centre.y - 150,
                                             width: 300, height: 300)),
                      with: .color(Palette.mix(0xB07BFF, 0xFF3B2E, voice.mood)))
        }

        var face = context
        let scale = 1 + 0.02 * voice.listen + 0.015 * voice.speak * sin(voice.osc * 5)
        face.translateBy(x: centre.x, y: centre.y)
        face.scaleBy(x: scale, y: scale)
        face.translateBy(x: -centre.x, y: -centre.y)
        face.drawLayer { sculpture in
            sculpture.draw(Self.mask, in: rect)
            sculpture.blendMode = .color
            sculpture.fill(Path(rect), with: .linearGradient(
                Gradient(colors: [Palette.mix(0x4F5BFF, 0xFF3B2E, voice.mood),
                                  Palette.mix(0xB07BFF, 0xFFB25C, voice.mood)]),
                startPoint: CGPoint(x: cx, y: faceY),
                endPoint: CGPoint(x: cx, y: faceY + Self.faceHeight)))
            sculpture.blendMode = .destinationIn
            sculpture.draw(Self.mask, in: rect)
        }

        var eyes = face
        eyes.opacity = 0.75 + 0.25 * level
        eyes.drawLayer { eyes in
            for x in [0.31, 0.69] {
                let eye = Path(roundedRect: CGRect(x: faceX + Self.faceWidth * x - 17,
                                                   y: faceY + Self.faceHeight * 0.488 - 2.5,
                                                   width: 34, height: 5), cornerRadius: 2.5)
                // Skia BlurMask "solid": combine the halo and core before opacity.
                eyes.drawLayer { halo in
                    halo.addFilter(.blur(radius: 4))
                    halo.fill(eye, with: .color(Color(hex: 0xE9E4FF)))
                }
                eyes.fill(eye, with: .color(Color(hex: 0xE9E4FF)))
            }
        }

        let mouthOpen = voice.speak * (0.25 + 0.75 * abs(sin(voice.osc * 9))
                                      * (0.6 + 0.4 * sin(voice.osc * 3.1)))
        let mouthH = 1 + 16 * mouthOpen
        let mouthW = 30 + 8 * mouthOpen
        face.drawLayer { mouth in
            mouth.opacity = Self.smooth(mouthOpen * 6)
            mouth.addFilter(.blur(radius: 2))
            mouth.fill(Path(roundedRect: CGRect(x: faceX + Self.faceWidth * 0.49 - mouthW / 2,
                                                y: faceY + Self.faceHeight * 0.712 - mouthH / 2,
                                                width: mouthW, height: mouthH), cornerRadius: 8),
                       with: .color(Palette.mouth))
        }

        var wave = context
        wave.opacity = 0.35 + 0.55 * level
        let waveColor = Palette.mix(0xC9C3FF, 0xFFB25C, voice.mood)
        let left = cx - Double(Self.bars - 1) * 8 / 2
        for k in 0..<Self.bars {
            let edge = sin(.pi * (Double(k) + 0.5) / Double(Self.bars))
            let amplitude = abs(sin(voice.osc * 6.2 + Double(k) * 0.7))
                * (0.5 + 0.5 * sin(Double(k) * 1.3 + voice.osc * 3.1))
            let h = 3 + 34 * level * edge * amplitude
            wave.fill(Path(roundedRect: CGRect(x: left + Double(k) * 8 - 1.5,
                                               y: waveY - h / 2, width: 3, height: h), cornerRadius: 1.5),
                      with: .color(waveColor))
        }

        let ring = 40 + 16 * voice.listen * (0.5 + 0.5 * sin(voice.osc * 7))
        context.stroke(Path(ellipseIn: CGRect(x: cx - ring, y: micY - ring,
                                              width: ring * 2, height: ring * 2)),
                       with: .color(.white.opacity(0.12 + 0.3 * voice.listen)), lineWidth: 1.5)
        context.fill(Path(ellipseIn: CGRect(x: cx - 34, y: micY - 34, width: 68, height: 68)),
                     with: .color(Palette.mic))
        context.fill(Path(roundedRect: CGRect(x: cx - 6, y: micY - 14, width: 12, height: 20),
                          cornerRadius: 6), with: .color(Palette.text))
        context.fill(Path(CGRect(x: cx - 1, y: micY + 9, width: 2, height: 6)),
                     with: .color(Palette.text))
    }

    private func updateClock() {
        let date = Date()
        accumulated = elapsed(at: date)
        anchor = date
        running = scenePhase == .active && !reduced
    }

    private var holdGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureTouching) { _, touching, _ in touching = true }
            .onChanged { _ in
                guard !reduced, hold == nil else { return }
                let now = elapsed(at: .now)
                hold = Hold(start: now, from: voice(at: now))
                haptic.impactOccurred()
            }
            .onEnded { _ in finishHold() }
    }

    private func finishHold() {
        guard hold != nil else { return }
        let now = elapsed(at: .now)
        frozen = voice(at: now)
        hold = nil
        loopOrigin = now - Self.replyAt
    }
}

#Preview {
    MoodMaskVoice(autoPlay: true)
}
