import SwiftUI
import UIKit
import CoreText

// Lumen's smoked-glass hub wakes inside a wide plasma squircle. The bundled artwork,
// Archivo faces, point coordinates and compositing follow the React Native reference.
//
// autoPlay timeline (loop = 6 s):
//   0.0–0.8  rest, faint outline, "Hold to wake", rooms off
//   0.8–2.0  level 0 → 0.6, ease-in-out cubic: sparks crawl towards the corners
//   2.0–2.4  level 0.6 → 1, ease-out cubic: ignition, glass and status light brighten
//   2.45–3.06 rooms light in order, 0.18 s apart, 0.25 s each
//   2.4–4.4  hold, crackling; status crossfades as level passes 0.75
//   4.4–5.4  level 1 → 0, ease-in cubic; rooms turn off 0.1 s apart
//   5.4–6.0  rest, identical to t = 0
// Holding adds 1 / 1.4 level per second. Release holds for 2 s, then falls 1 per second.
struct PlasmaSquircleCharge: View {
    static let loop: Double = 6

    var brand = "lumen"
    var idle = "Hold to wake"
    var online = "Online"
    var detail = "Lights, music and climate wake together."
    var autoPlay = false

    private static let hubWidth: CGFloat = 232
    private static let hubHeight: CGFloat = hubWidth * 340 / 540
    private static let rise = 1.4
    private static let hold = 2.0
    private static let fall = 1.0
    private static let background = Color(hex: 0x05040B)
    private static let ink = Color(hex: 0xF4F2FF)
    private static let dim = Color(hex: 0x8A86A3)
    private static let card = Color(hex: 0x100E1A)
    private static let border = Color(hex: 0x25213A)
    private static let glow = Color(hex: 0xA898FF)
    private static let rooms = ["Living room", "Kitchen", "Studio"]

    private static let registeredFonts: Void = {
        for face in ["ArchivoExpanded-Black", "Archivo-Medium", "Archivo-SemiBold"] {
            if let url = Bundle.main.url(forResource: "PlasmaSquircleCharge-" + face, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    private static func font(_ face: String, size: CGFloat) -> Font {
        _ = registeredFonts
        return .custom(face, fixedSize: size)
    }

    // Decode the artwork once, rather than loading or decoding during a frame.
    private static let artwork: CGImage? = {
        guard let url = Bundle.main.url(forResource: "PlasmaSquircleCharge-hub", withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return image.preparingForDisplay()?.cgImage ?? image.cgImage
    }()

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var gestureTouching = false

    // A clock that freezes while inactive, just like RN's paused frame callback.
    // Only events write state; a TimelineView date derives every animated frame.
    @State private var accumulated = 0.0
    @State private var anchor = Date()
    @State private var running = false
    @State private var origin = 0.0
    @State private var touching = false
    @State private var manual = false
    @State private var touchStart = 0.0
    @State private var touchLevel = 0.0
    @State private var released = 0.0
    @State private var releaseLevel = 0.0
    @State private var touchID = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private struct SettleKey: Equatable {
        var touchID: Int
        var running: Bool
    }

    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }

    private static func smooth(_ x: Double) -> Double {
        let u = min(1, max(0, x))
        return u * u * (3 - 2 * u)
    }

    private static func levelAt(_ t: Double) -> Double {
        let u = segment(t, 0.8, 2.0)
        let crawl = u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
        let build = 0.6 * crawl + 0.4 * (1 - pow(1 - segment(t, 2.0, 2.4), 3))
        return build * (1 - pow(segment(t, 4.4, 5.4), 3))
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(anchor)) : 0)
    }

    private func loopTime(at now: Double) -> Double {
        max(0, now - origin).truncatingRemainder(dividingBy: Self.loop)
    }

    private func level(at now: Double) -> Double {
        if reduced { return 1 }
        if touching { return min(1, touchLevel + max(0, now - touchStart) / Self.rise) }
        if manual {
            return max(0, releaseLevel - max(0, now - released - Self.hold) / Self.fall)
        }
        return autoPlay ? Self.levelAt(loopTime(at: now)) : 0
    }

    private static func roomAt(_ t: Double, _ k: Int) -> Double {
        smooth(segment(t, 2.45 + 0.18 * Double(k), 2.7 + 0.18 * Double(k)))
            * (1 - smooth(segment(t, 4.4 + 0.1 * Double(k), 4.9 + 0.1 * Double(k))))
    }

    private func roomLight(_ k: Int, at now: Double, level: Double) -> Double {
        if reduced { return 1 }
        if manual {
            let thresholds = [0.9, 0.94, 0.98]
            let widths = [0.04, 0.04, 0.02]
            return Self.smooth((level - thresholds[k]) / widths[k])
        }
        return autoPlay ? Self.roomAt(loopTime(at: now), k) : 0
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + safe.leading + safe.trailing,
                height: geo.size.height + safe.top + safe.bottom
            )
            let centre = CGPoint(x: size.width / 2, y: size.height * 0.52)
            TimelineView(.animation(paused: !running || (!autoPlay && !manual))) { frame in
                let now = elapsed(at: frame.date)
                let value = level(at: now)
                let cover = Self.smooth(value / 0.6)
                let ignition = Self.smooth((value - 0.6) / 0.4)
                let status = Self.smooth((value - 0.75) / 0.25)
                let phase = reduced ? 0 : loopTime(at: now) / Self.loop
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .fill(.black)
                        .colorEffect(ShaderLibrary.plasmaSquircleChargeField(
                            .float2(Float(centre.x), Float(centre.y)),
                            .float(cover), .float(0.55 * cover + 0.45 * ignition), .float(phase)
                        ))
                        .accessibilityLabel("\(brand) hub. Hold to wake the home.")

                    hub(at: centre, ignition: ignition)
                        .frame(width: size.width, height: size.height, alignment: .topLeading)
                        .accessibilityHidden(true)

                    header(safe: safe, width: size.width)

                    ZStack {
                        Text(idle).opacity(1 - status)
                        Text(online).opacity(status)
                    }
                    .font(Self.font("ArchivoExpanded-Black", size: 22))
                    .tracking(-0.2)
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
                    .frame(width: size.width, height: 30)
                    .offset(y: centre.y + 150)

                    Text(detail)
                        .font(Self.font("Archivo-Medium", size: 14))
                        .foregroundStyle(Self.dim)
                        .multilineTextAlignment(.center)
                        .frame(width: size.width - 64, height: 20)
                        .offset(x: 32, y: centre.y + 186)

                    HStack(spacing: 10) {
                        ForEach(0..<Self.rooms.count, id: \.self) { k in
                            roomCard(Self.rooms[k], light: roomLight(k, at: now, level: value))
                        }
                    }
                    .frame(width: size.width - 32, height: 88)
                    .offset(x: 16, y: size.height - safe.bottom - 108)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(holdGesture)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            accumulated = 0
            origin = 0
            anchor = .now
            running = scenePhase == .active && !reduced
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            setClockRunning(phase == .active && !reduced)
        }
        .onChange(of: reduced) { _, value in
            setClockRunning(scenePhase == .active && !value)
        }
        .onChange(of: gestureTouching) { _, value in
            if !value { endHold() }
        }
        .task(id: SettleKey(touchID: touchID, running: running)) {
            await settle()
        }
    }

    private func hub(at centre: CGPoint, ignition: Double) -> some View {
        Canvas { context, _ in
            guard let artwork = Self.artwork else { return }
            let image = Image(decorative: artwork, scale: 1)
            let w = Self.hubWidth
            let h = Self.hubHeight
            let x = centre.x - w / 2
            let y = centre.y - h / 2
            let rect = CGRect(x: x, y: y, width: w, height: h)

            // RN multiplies each paint's alpha: image and gradient are separate draws.
            var reflection = context
            reflection.opacity = 0.12 + 0.16 * ignition
            var flipped = reflection
            flipped.translateBy(x: 0, y: 2 * y + 2 * h + 6)
            flipped.scaleBy(x: 1, y: -1)
            flipped.draw(image, in: rect)
            reflection.fill(
                Path(CGRect(x: x - 20, y: y + h + 6, width: w + 40, height: h)),
                with: .linearGradient(
                    Gradient(colors: [Self.background.opacity(0), Self.background]),
                    startPoint: CGPoint(x: 0, y: y + h),
                    endPoint: CGPoint(x: 0, y: y + h * 1.8)
                )
            )

            var glow = context
            glow.opacity = 0.15 + 0.85 * ignition
            glow.addFilter(.blur(radius: 28))
            glow.fill(
                Path(roundedRect: CGRect(x: x + 10, y: y + 12, width: w - 20, height: h - 16), cornerRadius: 40),
                with: .color(Self.glow)
            )
            context.draw(image, in: rect)

            var sheen = context
            sheen.opacity = 0.45 * ignition
            sheen.blendMode = .screen
            sheen.fill(
                Path(roundedRect: CGRect(x: x + 18, y: y + 8, width: w - 36, height: h * 0.55), cornerRadius: 36),
                with: .linearGradient(
                    Gradient(colors: [Self.glow.opacity(0.9), Color(hex: 0x5B5BFF).opacity(0)]),
                    startPoint: CGPoint(x: 0, y: y),
                    endPoint: CGPoint(x: 0, y: y + h * 0.6)
                )
            )

            var light = context
            light.opacity = 0.25 + 0.75 * ignition
            let lightY = y + h * 0.865
            var halo = light
            halo.addFilter(.blur(radius: 5))
            halo.fill(Path(ellipseIn: CGRect(x: centre.x - 6, y: lightY - 6, width: 12, height: 12)), with: .color(.white))
            light.fill(Path(ellipseIn: CGRect(x: centre.x - 2.4, y: lightY - 2.4, width: 4.8, height: 4.8)), with: .color(.white))
        }
    }

    private func header(safe: EdgeInsets, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text(brand)
                    .font(Self.font("ArchivoExpanded-Black", size: 20))
                    .tracking(-0.3)
                    .foregroundStyle(Self.ink)
                Spacer()
                Text("Home · 3 rooms")
                    .font(Self.font("Archivo-Medium", size: 13))
                    .foregroundStyle(Self.dim)
            }
            .frame(height: 34)
            VStack(alignment: .leading, spacing: 0) {
                Text("Wake the").frame(height: 44)
                Text("home.").frame(height: 44)
            }
            .font(Self.font("ArchivoExpanded-Black", size: 38))
            .tracking(-0.8)
            .foregroundStyle(Self.ink)
            .lineLimit(1)
            .lineSpacing(0)
        }
        .frame(width: width - 48, alignment: .topLeading)
        .offset(x: 24, y: safe.top + 8)
    }

    private func roomCard(_ name: String, light: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Circle()
                .fill(light > 0.5 ? Self.glow : Color(hex: 0x3A3552))
                .frame(width: 10, height: 10)
                .shadow(color: Self.glow.opacity(light), radius: 8)
                .scaleEffect(1 + 0.25 * sin(.pi * light))
            Spacer(minLength: 0)
            Text(name)
                .font(Self.font("Archivo-SemiBold", size: 13))
                .foregroundStyle(Self.ink)
                .lineLimit(1)
            Spacer(minLength: 0)
            ZStack(alignment: .leading) {
                Text("Off").foregroundStyle(Self.dim).opacity(1 - light)
                Text("On").foregroundStyle(Self.glow).opacity(light)
            }
            .font(Self.font("Archivo-Medium", size: 12))
            .frame(height: 16)
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 88)
        .background(RoundedRectangle(cornerRadius: 20).fill(Self.card))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(light > 0.5 ? Self.glow.opacity(0.55) : Self.border, lineWidth: 1))
    }

    private var holdGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureTouching) { _, pressing, _ in pressing = true }
            .onChanged { _ in
                guard !reduced, !touching else { return }
                let now = elapsed(at: .now)
                touchLevel = level(at: now)
                touchStart = now
                touching = true
                manual = true
                touchID += 1
                haptic.impactOccurred()
            }
            .onEnded { _ in endHold() }
    }

    private func endHold() {
        guard touching else { return }
        let now = elapsed(at: .now)
        releaseLevel = level(at: now)
        released = now
        touching = false
        touchID += 1
    }

    private func setClockRunning(_ active: Bool) {
        guard active != running else { return }
        if active {
            anchor = .now
            running = true
        } else {
            // Finalize cancelled touches as RN's LongPress.onFinalize does.
            endHold()
            accumulated = elapsed(at: .now)
            running = false
        }
    }

    private func settle() async {
        guard manual, !touching, running else { return }
        let remaining = max(0, released + Self.hold + releaseLevel * Self.fall - elapsed(at: .now))
        try? await Task.sleep(for: .seconds(remaining))
        guard !Task.isCancelled, !touching, running else { return }
        origin = elapsed(at: .now)
        manual = false
    }
}

#Preview {
    PlasmaSquircleCharge(autoPlay: true)
}
