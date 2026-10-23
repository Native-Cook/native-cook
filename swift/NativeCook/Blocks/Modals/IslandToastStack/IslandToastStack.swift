import CoreImage
import SwiftUI
import UIKit

/// Live notifications that grow out of the Dynamic Island. Each toast morphs out of the island's
/// own frame on a spring (width a little ahead of height, corners continuous), its content fades in
/// once the shape has settled, then it drops out of the island as a card, joined to it by a gooey
/// neck that thins and snaps, and lands at the front of a stack under it. The stack opens into a
/// list, cards swipe away, pulling the island down opens a big timer, and clearing draws every card
/// back up into the island, which gulps each one. Without an island it plays from a pill under the
/// status bar.
struct IslandToastStack: View {
    /// Plays a loop of four toasts, the stack opening and trimmed, the island pulled open and
    /// everything drawn back in, then starts over. A touch pauses it for a few seconds.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = IslandToastStackEngine()
    /// The wallpaper, blurred once for its size: the cards' material shows it clipped to their shape.
    @State private var frosted: Image?

    private typealias M = IslandToastStackMetrics
    private typealias S = IslandToastStackShapes
    private typealias P = IslandToastStackPeople
    private static let space = "IslandToastStack"

    var body: some View {
        // The block draws under the system bars, edge to edge, and places the pill on the island
        // itself, so the touch area over the island lies inside its own frame.
        GeometryReader { proxy in
            let window = Self.windowInsets()
            let top = max(proxy.safeAreaInsets.top, window.top)
            let bottom = max(proxy.safeAreaInsets.bottom, window.bottom)
            stage(size: proxy.size, top: top, bottom: bottom)
        }
        .ignoresSafeArea()
        .task { await loadAvatars() }
    }

    /// The window's safe area, for when the reader ignoring it reports none.
    private static func windowInsets() -> UIEdgeInsets {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.compactMap(\.keyWindow).first?.safeAreaInsets ?? .zero
    }

    private func stage(size: CGSize, top: Double, bottom: Double) -> some View {
        ZStack(alignment: .topLeading) {
            IslandToastStackWallpaper()
            footer(bottom: bottom)

            TimelineView(.animation) { timeline in
                let _ = engine.layout(width: size.width, top: top, reduced: reduceMotion)
                let _ = engine.step(to: timeline.date, autoPlay: autoPlay)
                let order = engine.stack.reversed() + IslandToastStackKind.allCases.filter { !engine.stack.contains($0) }
                ZStack(alignment: .topLeading) {
                    Canvas { context, canvasSize in draw(context, size: canvasSize, order: order) }
                    header
                    ForEach(order, id: \.self) { kind in card(kind) }
                    island
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            // One recogniser over the whole block: the engine works out what a touch started on.
            // (Views the timeline rebuilds every frame can't keep a gesture of their own alive.)
            Color.clear
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
                        .onChanged { engine.touchChanged($0) }
                        .onEnded { engine.touchEnded($0) }
                )
                .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .coordinateSpace(.named(Self.space))
        .task(id: size) { renderFrosted(size) }
    }

    /// Blurs the wallpaper once for this size. The wallpaper never moves, so a card's material is
    /// this image seen through the card's shape.
    private func renderFrosted(_ size: CGSize) {
        guard size.width > 0, size.height > 0, let image = IslandToastStackWallpaper.frosted(size: size) else { return }
        frosted = Image(decorative: image, scale: 1)
    }

    // MARK: Shapes

    /// A card's body. Leaving the island it is still the island's black, so the neck between them
    /// reads as one shape; as it settles into the stack it clears into the notification material, and
    /// rising back into the island it darkens to black again before it is swallowed. The material is
    /// the wallpaper itself drawn through the card's shape under a grey tint, which keeps the cards
    /// behind out of sight.
    private func draw(_ context: GraphicsContext, size: CGSize, order: [IslandToastStackKind]) {
        for kind in order {
            guard let m = engine.motion[kind], m.vis > 0, m.absorb.value <= 0.995 else { continue }
            let r = engine.cardRect(kind)
            // 0 the island's black … 1 the material; how far back the card sits (0 front … 1 the
            // third card); how much of it is left past that.
            let clear = S.clamp01((m.arrive.value - 0.5) / 0.4) * (1 - S.clamp01(m.absorb.value * 2.5))
            let depth = S.clamp01(r.fade / 0.42)
            let present = 1 - S.smoothstep((r.fade - 0.42) / 0.58)
            guard present > 0.002 else { continue }
            let tx = m.tx.value
            let cx = r.x + r.w / 2
            let cy = r.y + r.h / 2
            var c = context
            c.translateBy(x: cx + tx, y: cy)
            c.rotate(by: .degrees(tx * 0.035))
            c.translateBy(x: -cx, y: -cy)
            let shape = S.squircle(r.x, r.y, r.w, r.h, r.r)
            // The island's black lies over the material and thins away as the card clears (mixing
            // the two colours instead would flash a light grey half way).
            let dark = (1 - clear) * present
            if dark < 0.998 {
                // The wallpaper seen through the card, turned back as the card turns so it stays put
                // on screen. Until it is ready, the wallpaper's own ink stands in for it.
                var g = c
                g.clip(to: shape)
                if let frosted {
                    g.translateBy(x: cx, y: cy)
                    g.rotate(by: .degrees(-tx * 0.035))
                    g.translateBy(x: -cx - tx, y: -cy)
                    g.draw(frosted, in: CGRect(origin: .zero, size: size))
                } else {
                    g.fill(shape, with: .color(M.wallTop))
                }
                c.fill(shape, with: .color(M.material.opacity(present)))
                let shade = M.backShade * depth * present
                if shade > 0.002 { c.fill(shape, with: .color(.black.opacity(shade))) }
            }
            if dark > 0.002 { c.fill(shape, with: .color(.black.opacity(dark))) }
        }
        for kind in IslandToastStackKind.allCases {
            if let neck = engine.bridge(kind) { context.fill(neck, with: .color(.black)) }
        }
        let i = engine.islandRect()
        context.fill(S.squircle(i.x, i.y, i.w, i.h, i.r), with: .color(.black))
    }

    // MARK: Island

    private var island: some View {
        let i = engine.islandRect()
        return ZStack(alignment: .topLeading) {
            ForEach(IslandToastStackKind.allCases, id: \.self) { kind in
                let c = engine.motion[kind]?.content.value ?? 0
                if c > 0.001 {
                    toastBody(kind)
                        .frame(width: engine.toastW, height: M.toastH, alignment: .top)
                        .scaleEffect(reduceMotion ? 1 : S.mix(0.96, 1, c))
                        .offset(y: reduceMotion ? 0 : S.mix(-6, 0, c))
                        .opacity(c)
                }
            }
            if engine.bigMounted {
                let b = engine.bigOpacity.value
                bigTimer
                    .frame(width: engine.toastW, height: M.bigH, alignment: .top)
                    .scaleEffect(reduceMotion ? 1 : S.mix(0.96, 1, b), anchor: .top)
                    .opacity(b)
            }
        }
        .frame(width: engine.toastW, height: max(M.bigH, M.toastH), alignment: .topLeading)
        .offset(x: (i.w - engine.toastW) / 2)
        .frame(width: i.w, height: i.h, alignment: .topLeading)
        .clipShape(IslandToastStackSquircle(radius: i.r))
        .offset(x: i.x, y: i.y)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(islandLabel)
        .accessibilityHint("Tap for the next toast, pull down to open the timer")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { engine.tapIsland() }
    }

    private var islandLabel: String {
        if engine.bigMounted { return "Timer, open" }
        if let kind = engine.presenting { return P.label(kind) }
        return "Island"
    }

    // MARK: Stack

    private var header: some View {
        let e = engine.expanded.value
        let off = max(0, engine.islandH.value - M.islandH) + (reduceMotion ? 0 : S.mix(-10, 0, e))
        return HStack {
            Text("Notifications")
                .font(.system(size: 22, weight: .bold))
                .tracking(-0.4)
                .foregroundStyle(M.ink)
            Spacer()
            HStack(spacing: 8) {
                Text("Show less")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(M.ink)
                    .padding(.horizontal, 13)
                    .frame(height: 30)
                    .background(Capsule().fill(M.control))
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { engine.toggleStack() }
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(M.ink)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(M.control))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Clear all")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { engine.clearStack() }
            }
        }
        .frame(width: engine.cardW - 12, height: 34)
        .offset(x: (engine.width - engine.cardW) / 2 + 6, y: engine.deckTop + off)
        .opacity(S.clamp01(e * 1.6 - 0.6))
        .accessibilityHidden(!engine.expandedOpen)
    }

    @ViewBuilder
    private func card(_ kind: IslandToastStackKind) -> some View {
        if let m = engine.motion[kind], m.vis > 0 {
            let r = engine.cardRect(kind)
            let tx = m.tx.value
            let e = engine.expanded.value
            // A card behind keeps its words hidden until the one in front has slid clear of them,
            // about two thirds of the way open: the cards are solid, so they would show through it.
            let front = S.mix(1 - S.clamp01(m.slot.value), 1, S.smoothstep((e - 0.62) / 0.36))
            let landed = S.clamp01((m.arrive.value - 0.72) / 0.28)
            let leaving = 1 - S.clamp01(m.absorb.value * 3)
            let swiped = 1 - min(0.8, abs(tx) / max(1, engine.width) * 0.9)
            let opacity = front * landed * leaving * swiped
            let interactive = engine.stack.contains(kind) && (engine.expandedOpen || engine.stack.first == kind)
            deckBody(kind)
                .frame(width: engine.cardW, height: M.cardH)
                .scaleEffect(r.w / max(1, engine.cardW))
                .rotationEffect(.degrees(tx * 0.035))
                .position(x: r.x + r.w / 2 + tx, y: r.y + r.h / 2)
                .opacity(opacity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(P.label(kind))
                .accessibilityHint("Tap to open or close the stack, swipe sideways to dismiss")
                .accessibilityHidden(!interactive)
                .accessibilityAction { engine.toggleStack() }
        }
    }

    // MARK: Footer

    private func footer(bottom: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer(minLength: 0)
            Text("Tuesday, 14 October")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(M.ink.opacity(0.7))
            Text("9:41")
                .font(.system(size: 96, weight: .bold, design: .rounded).monospacedDigit())
                .tracking(-3)
                .foregroundStyle(M.ink)
                .frame(height: 104)
            Text("Tap or pull the island · Tap the stack · Swipe a card away")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(M.ink.opacity(0.46))
                .padding(.top, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 26)
        .padding(.bottom, bottom + 28)
        .allowsHitTesting(false)
        .accessibilityElement(children: .combine)
    }

    // MARK: Content

    private var bandColor: Color { Color(.sRGB, red: 235 / 255, green: 235 / 255, blue: 245 / 255, opacity: 0.62) }

    private func band(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold).monospacedDigit())
            .foregroundStyle(bandColor)
            .lineLimit(1)
            .fixedSize()
    }

    private func band(_ text: String, symbol: String, tint: UInt32) -> some View {
        HStack(spacing: 5) {
            Image(systemName: symbol)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color(hex: tint))
            band(text)
        }
    }

    private func bandRow(@ViewBuilder lead: () -> some View, @ViewBuilder trail: () -> some View) -> some View {
        HStack {
            lead()
            Spacer(minLength: 8)
            trail()
        }
        .padding(.horizontal, 24)
        .frame(height: M.islandH)
    }

    private func title(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 17, weight: .semibold))
            .tracking(-0.2)
            .foregroundStyle(.white)
            .lineLimit(1)
    }

    private func subtle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(M.secondary)
            .lineLimit(1)
    }

    private func round(_ symbol: String, _ color: UInt32) -> some View {
        Image(systemName: symbol)
            .font(.system(size: 19, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 46, height: 46)
            .background(Circle().fill(Color(hex: color)))
    }

    private var uploadShare: Double {
        guard let start = engine.uploadStart else { return 0 }
        return min(1, max(0, (engine.now - start) / M.uploadTime))
    }

    private var timerLeft: Double {
        guard let start = engine.timerStart else { return M.timerSeconds }
        return M.timerSeconds - max(0, engine.now - start)
    }

    private var percent: some View {
        let p = uploadShare
        return Text(p >= 1 ? "Done" : "\(Int(p * 100))%")
            .font(.system(size: 17, weight: .semibold).monospacedDigit())
            .foregroundStyle(Color(hex: 0x0a84ff))
    }

    private func digits(_ size: Double) -> some View {
        Text(P.format(timerLeft))
            .font(.system(size: size, weight: .semibold, design: .rounded).monospacedDigit())
            .tracking(size > 30 ? -0.5 : 0)
            .foregroundStyle(Color(hex: 0xff9f0a))
            .lineLimit(1)
            .fixedSize()
    }

    /// A toast as the island shows it: a top band either side of the camera, and a row under it.
    @ViewBuilder
    private func toastBody(_ kind: IslandToastStackKind) -> some View {
        VStack(spacing: 0) {
            switch kind {
            case .call:
                bandRow { band("Incoming call") } trail: { band("mobile") }
                HStack(spacing: 12) {
                    avatar(P.caller.photo, P.caller.initials, 46)
                    title(P.caller.name).frame(maxWidth: .infinity, alignment: .leading)
                    round("phone.down.fill", 0xff3b30)
                    round("phone.fill", 0x30d158)
                }
                .padding(.horizontal, 16)
                .frame(height: M.toastH - M.islandH - 10)
            case .message:
                bandRow { band("Message", symbol: "message.fill", tint: 0x30d158) } trail: { band("now") }
                HStack(spacing: 12) {
                    avatar(P.sender.photo, P.sender.initials, 46)
                    VStack(alignment: .leading, spacing: 1) {
                        title(P.sender.name)
                        Text(P.sender.body)
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(Color(.sRGB, red: 235 / 255, green: 235 / 255, blue: 245 / 255, opacity: 0.82))
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 16)
                .frame(height: M.toastH - M.islandH - 10)
            case .upload:
                bandRow { band("Uploading") } trail: { band("\(Int(uploadShare * Double(P.upload.count))) of \(P.upload.count)") }
                HStack(spacing: 12) {
                    ring(40)
                    VStack(alignment: .leading, spacing: 1) {
                        title(P.upload.album)
                        subtle("\(P.upload.count) photos · Shared album")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    percent
                }
                .padding(.horizontal, 16)
                .frame(height: M.toastH - M.islandH - 10)
            case .timer:
                bandRow { band("Timer", symbol: "timer", tint: 0xff9f0a) } trail: { band(P.timer.place) }
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 1) {
                        title(P.timer.title)
                        subtle("Then shape and bake")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    digits(36)
                }
                .padding(.horizontal, 16)
                .frame(height: M.toastH - M.islandH - 10)
            }
        }
    }

    /// An app's icon as the system draws it: a white symbol on a squircle of the app's colour.
    private func appIcon(_ color: UInt32, _ symbol: String, tint: Color = .white) -> some View {
        Image(systemName: symbol)
            .font(.system(size: M.icon * 0.5, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: M.icon, height: M.icon)
            .background(RoundedRectangle(cornerRadius: M.icon * 0.225, style: .continuous).fill(Color(hex: color)))
    }

    /// A person's picture with the app's icon tucked into its corner, as the system shows a call or
    /// a message.
    private func contactIcon(_ photo: String, _ initials: String, _ color: UInt32, _ symbol: String) -> some View {
        avatar(photo, initials, M.icon)
            .overlay(alignment: .bottomTrailing) {
                Image(systemName: symbol)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 17, height: 17)
                    .background(RoundedRectangle(cornerRadius: 4, style: .continuous).fill(Color(hex: color)))
                    .offset(x: 3, y: 3)
            }
    }

    /// The extra room a line of the system font at `size` needs to be `height` tall.
    private static func leading(_ size: Double, _ height: Double) -> Double {
        max(0, height - UIFont.systemFont(ofSize: size).lineHeight)
    }

    /// A notification's words: the title with the time beside it, and a line or two under it.
    private func note(_ title: String, lines: Int = 1, text: Text, @ViewBuilder icon: () -> some View) -> some View {
        let titleLeading = Self.leading(15, 20)
        let bodyLeading = Self.leading(15, 19)
        return HStack(spacing: 11) {
            icon()
            VStack(alignment: .leading, spacing: 1) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(M.ink)
                        .lineLimit(1)
                        .padding(.vertical, titleLeading / 2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("now")
                        .font(.system(size: 13))
                        .foregroundStyle(M.secondary)
                        .fixedSize()
                }
                text
                    .font(.system(size: 15))
                    .foregroundStyle(M.ink)
                    .lineLimit(lines)
                    .lineSpacing(bodyLeading)
                    .padding(.vertical, bodyLeading / 2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 14)
    }

    private var uploadLine: Text {
        let done = Int(uploadShare * Double(P.upload.count))
        let count = P.upload.count
        return Text(uploadShare >= 1 ? "\(count) photos uploaded" : "Uploading \(done) of \(count) photos")
    }

    private var timerLine: Text {
        let digits = Text(P.format(timerLeft))
            .font(.system(size: 15, weight: .semibold).monospacedDigit())
            .foregroundStyle(Color(hex: 0xff9f0a))
        return Text("\(digits) left · \(P.timer.place)")
    }

    /// A toast as it rests in the stack: one of the system's notifications.
    @ViewBuilder
    private func deckBody(_ kind: IslandToastStackKind) -> some View {
        switch kind {
        case .call:
            note(P.caller.name, text: Text("Missed call · mobile")) {
                contactIcon(P.caller.photo, P.caller.initials, 0x34c759, "phone.fill")
            }
        case .message:
            note(P.sender.name, lines: 2, text: Text(P.sender.body)) {
                contactIcon(P.sender.photo, P.sender.initials, 0x34c759, "message.fill")
            }
        case .upload:
            note(P.upload.album, text: uploadLine) {
                appIcon(0x0a84ff, "icloud.and.arrow.up.fill")
            }
        case .timer:
            note(P.timer.title, text: timerLine) {
                appIcon(0x1c1c1e, "timer", tint: Color(hex: 0xff9f0a))
            }
        }
    }

    /// The island pulled open: the kitchen timer as a big live card.
    private var bigTimer: some View {
        let left = timerLeft
        let share = S.clamp01(left / M.timerSeconds)
        return VStack(alignment: .leading, spacing: 0) {
            // The band beside the camera only has room for a short word either side of it.
            bandRow { band("Timer", symbol: "timer", tint: 0xff9f0a) } trail: { band(P.timer.place) }
            VStack(alignment: .leading, spacing: 0) {
            Text(P.format(left))
                .font(.system(size: 72, weight: .semibold, design: .rounded).monospacedDigit())
                .tracking(-1.5)
                .foregroundStyle(Color(hex: 0xff9f0a))
                .frame(height: 84)
            Capsule()
                .fill(Color(hex: 0xff9f0a, opacity: 0.2))
                .frame(height: 5)
                .overlay(alignment: .leading) {
                    GeometryReader { g in
                        Capsule().fill(Color(hex: 0xff9f0a)).frame(width: g.size.width * share)
                    }
                }
            HStack(spacing: 10) {
                Text("Pause")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Capsule().fill(.white.opacity(0.14)))
                Text("+5 min")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x1a1000))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Capsule().fill(Color(hex: 0xff9f0a)))
            }
            .padding(.top, 18)
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 20)
        }
    }

    /// The upload's progress, a ring that closes clockwise from the top.
    private func ring(_ size: Double) -> some View {
        let stroke = 3.5
        return ZStack {
            Circle()
                .stroke(.white.opacity(0.16), lineWidth: stroke)
            Circle()
                .trim(from: 0, to: S.clamp01(engine.upload.value))
                .stroke(Color(hex: 0x0a84ff), style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Image(systemName: "arrow.up")
                .font(.system(size: size * 0.38, weight: .bold))
                .foregroundStyle(Color(hex: 0x0a84ff))
        }
        .padding(stroke / 2)
        .frame(width: size, height: size)
    }

    private func avatar(_ address: String, _ initials: String, _ size: Double) -> some View {
        ZStack {
            Circle().fill(Color(hex: 0x3a3a3c))
            Text(initials)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white.opacity(0.8))
            if let image = IslandToastStackPhotos.cached(address) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size, height: size)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    /// Fills the photo cache the cards read from as they draw: the timeline redraws them every frame,
    /// and a state write from here never reached the views it builds. A photo that fails to load
    /// leaves the initials on their grey disc.
    private func loadAvatars() async {
        for address in [P.caller.photo, P.sender.photo] {
            _ = await IslandToastStackPhotos.load(address)
        }
    }
}

/// The island's outline as a clip: the same continuous corners the canvas draws.
struct IslandToastStackSquircle: Shape {
    var radius: Double

    func path(in rect: CGRect) -> Path {
        IslandToastStackShapes.squircle(rect.minX, rect.minY, rect.width, rect.height, radius)
    }
}

/// The lock screen's wallpaper: deep ink, a little lighter at the foot, with wide pools of colour.
struct IslandToastStackWallpaper: View {
    private typealias M = IslandToastStackMetrics

    /// The wallpaper drawn for `size` and blurred with a Gaussian of `materialBlur` pt, one pixel per
    /// point (a blurred image needs no more).
    static func frosted(size: CGSize) -> CGImage? {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let space = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        func color(_ hex: UInt32, _ alpha: Double) -> CGColor {
            CGColor(
                srgbRed: Double((hex >> 16) & 0xff) / 255,
                green: Double((hex >> 8) & 0xff) / 255,
                blue: Double(hex & 0xff) / 255,
                alpha: alpha
            )
        }
        let drawn = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            let cg = renderer.cgContext
            if let base = CGGradient(colorsSpace: space, colors: [color(M.wallTopHex, 1), color(M.wallBottomHex, 1)] as CFArray, locations: [0, 1]) {
                cg.drawLinearGradient(base, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
            }
            for pool in M.pools {
                let colors = [color(pool.rgb, pool.a), color(pool.rgb, pool.a * 0.45), color(pool.rgb, 0)]
                guard let g = CGGradient(colorsSpace: space, colors: colors as CFArray, locations: [0, 0.45, 1]) else { continue }
                let c = CGPoint(x: pool.x * size.width, y: pool.y * size.height)
                cg.drawRadialGradient(g, startCenter: c, startRadius: 0, endCenter: c, endRadius: max(1, pool.r * size.width), options: [])
            }
        }
        guard let input = CIImage(image: drawn) else { return nil }
        let blurred = input.clampedToExtent().applyingGaussianBlur(sigma: M.materialBlur).cropped(to: input.extent)
        return CIContext().createCGImage(blurred, from: input.extent)
    }

    var body: some View {
        GeometryReader { proxy in
            let w = proxy.size.width
            let h = proxy.size.height
            ZStack {
                LinearGradient(colors: [M.wallTop, M.wallBottom], startPoint: .top, endPoint: .bottom)
                ForEach(M.pools.indices, id: \.self) { i in
                    let pool = M.pools[i]
                    let color = Color(hex: pool.rgb)
                    RadialGradient(
                        stops: [
                            .init(color: color.opacity(pool.a), location: 0),
                            .init(color: color.opacity(pool.a * 0.45), location: 0.45),
                            .init(color: color.opacity(0), location: 1),
                        ],
                        center: UnitPoint(x: pool.x, y: pool.y),
                        startRadius: 0,
                        endRadius: max(1, pool.r * w)
                    )
                }
            }
            .frame(width: w, height: h)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
