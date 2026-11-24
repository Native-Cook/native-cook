import SwiftUI
import UIKit

/// A before / after photo under a divider with mass. Drag the glass handle: it squashes under the
/// finger, stretches with speed, rubber-bands at the photo's edges and, let go, is carried on by a
/// spring that overshoots and settles. Day / Evening crossfades both photos under the divider.
struct CompareRevealSlider: View {
    static let loop: Double = CompareRevealSliderMotion.loop

    private typealias M = CompareRevealSliderMotion

    private static let page = color(0xf2eee6)
    private static let inkColor = color(0x211d18)
    private static let mute = color(0x7d7468)
    private static let card = color(0xfbf9f4)
    private static let hair = color(0x211d18, 0.1)
    private static let side: CGFloat = 16
    private static let radius: CGFloat = 26
    private static let dot: CGFloat = 46
    private static let facts: [(String, String)] = [("6 weeks", "Start to finish"), ("$8,420", "Total spend"), ("14 m²", "Floor area")]

    private static let beforeDay = photo("before-day")
    private static let afterDay = photo("after-day")
    private static let beforeEvening = photo("before-evening")
    private static let afterEvening = photo("after-evening")

    private static func photo(_ name: String) -> UIImage {
        guard let url = Bundle.main.url(forResource: "CompareRevealSlider-\(name)", withExtension: "webp"),
              let image = UIImage(contentsOfFile: url.path) else {
            assertionFailure("Missing Compare Reveal Slider render \(name)")
            return UIImage()
        }
        return image.preparingForDisplay() ?? image
    }

    private static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xff) / 255, green: Double((hex >> 8) & 0xff) / 255,
              blue: Double(hex & 0xff) / 255, opacity: alpha)
    }

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var clock = CompareRevealSliderClock()
    @State private var live = false
    @State private var liveToken = 0
    @State private var touching = false

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private struct Layout {
        let screen: CGSize
        let contentW: CGFloat
        let w: CGFloat
        let top: CGFloat
        let headerH: CGFloat
        let gap: CGFloat
        let factsH: CGFloat
        let photoH: CGFloat
        let handleY: CGFloat
        let titleSize: CGFloat
        let compact: Bool

        init(size: CGSize, insets: EdgeInsets) {
            let height = size.height + insets.top + insets.bottom
            screen = CGSize(width: size.width + insets.leading + insets.trailing, height: height)
            compact = height < 740
            contentW = min(screen.width, 480)
            w = contentW - CompareRevealSlider.side * 2
            top = insets.top + (compact ? 4 : 8)
            headerH = compact ? 50 : 58
            gap = compact ? 10 : 16
            factsH = compact ? 54 : 64
            let bottom = max(insets.bottom, 12) + (compact ? 6 : 10)
            let free = height - top - headerH - gap * 3 - factsH - 32 - bottom
            photoH = (min(w * 1.5, max(w * 0.9, free))).rounded()
            handleY = (photoH * CGFloat(CompareRevealSliderMotion.handleY)).rounded()
            titleSize = compact ? 26 : 30
        }
    }

    private var film: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { proxy in
            let layout = Layout(size: proxy.size, insets: proxy.safeAreaInsets)
            ZStack(alignment: .top) {
                Self.page
                VStack(spacing: 0) {
                    header(layout)
                    TimelineView(.animation(minimumInterval: nil, paused: !film && !live)) { context in
                        let frame = advance(context.date, layout)
                        photoCard(frame, layout)
                    }
                    .frame(width: layout.w, height: layout.photoH)
                    .overlay { touchLayer(layout) }
                    .padding(.top, layout.gap)
                    factsCard(layout)
                        .padding(.top, layout.gap)
                    TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !film && !live)) { _ in
                        CompareRevealSliderControls(selected: clock.selected) { index in
                            clock.choose(index, Date().timeIntervalSinceReferenceDate)
                            wake()
                        }
                        .equatable()
                    }
                    .frame(width: layout.w, height: 32)
                    .padding(.top, layout.gap)
                }
                .padding(.top, layout.top)
                .frame(width: layout.contentW)
            }
            .frame(width: layout.screen.width, height: layout.screen.height, alignment: .top)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .task(id: liveToken) {
            guard liveToken > 0 else { return }
            while !Task.isCancelled {
                let now = Date().timeIntervalSinceReferenceDate
                if !clock.holding && now - clock.lastTouch > 1.6 { break }
                try? await Task.sleep(for: .milliseconds(200))
            }
            if !Task.isCancelled { live = false }
        }
    }

    private func advance(_ date: Date, _ layout: Layout) -> CompareRevealSliderClock.Frame {
        clock.film = film
        clock.reduced = reduceMotion
        clock.width = Double(layout.w)
        return clock.frame(date.timeIntervalSinceReferenceDate)
    }

    private func wake() {
        live = true
        liveToken += 1
    }

    // MARK: Touch

    private func touchLayer(_ layout: Layout) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let now = Date().timeIntervalSinceReferenceDate
                        if !touching {
                            touching = true
                            clock.width = Double(layout.w)
                            clock.touchBegan(x: Double(value.startLocation.x), now)
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            wake()
                        }
                        if clock.touchMoved(x: Double(value.location.x), now) {
                            UISelectionFeedbackGenerator().selectionChanged()
                        }
                    }
                    .onEnded { value in
                        touching = false
                        clock.touchEnded(velocityX: Double(value.velocity.width), Date().timeIntervalSinceReferenceDate)
                        wake()
                    }
            )
            .accessibilityElement()
            .accessibilityLabel("Before and after, living room")
            .accessibilityHint("Swipe up or down to move the divider")
            .accessibilityAdjustableAction { direction in
                let now = Date().timeIntervalSinceReferenceDate
                switch direction {
                case .increment: clock.step(1, now)
                case .decrement: clock.step(-1, now)
                @unknown default: break
                }
                wake()
            }
    }

    // MARK: Drawing

    private func header(_ layout: Layout) -> some View {
        HStack(alignment: .bottom, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Roomwork · Project 214")
                    .font(.system(size: 14, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.mute)
                    .lineLimit(1)
                    .frame(height: 18)
                Text("Linden Street")
                    .font(.system(size: layout.titleSize, weight: .bold))
                    .tracking(-0.4)
                    .foregroundStyle(Self.inkColor)
                    .lineLimit(1)
                    .frame(height: layout.titleSize + 5)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.trailing, 12)
            Image(systemName: "square.and.arrow.up")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Self.inkColor)
                .frame(width: 38, height: 38)
                .background {
                    Circle()
                        .fill(Self.card)
                        .shadow(color: Self.color(0x3c2814, 0.14), radius: 1, x: 0, y: 1)
                        .shadow(color: Self.color(0x3c2814, 0.08), radius: 7, x: 0, y: 6)
                }
                .padding(.bottom, 2)
                .accessibilityLabel("Share")
                .accessibilityAddTraits(.isButton)
        }
        .frame(width: layout.w, height: layout.headerH, alignment: .bottom)
    }

    private func photoCard(_ frame: CompareRevealSliderClock.Frame, _ layout: Layout) -> some View {
        let shape = RoundedRectangle(cornerRadius: Self.radius, style: .continuous)
        let x = CGFloat(frame.pos) * layout.w
        let a = abs(frame.stretch)
        let scaleX = 1 + M.pressSquashX * frame.press + M.stretchX * a
        let scaleY = 1 - M.pressSquashY * frame.press - M.stretchY * a
        let handleX = x - CGFloat(M.lean * frame.stretch)
        return ZStack(alignment: .topLeading) {
            scene(frame, layout)
            handle(frame, layout, handleX: handleX, scaleX: scaleX, scaleY: scaleY)
            label("Before")
                .opacity(M.labelBefore(frame.pos))
                .offset(x: 14 - 6 * (1 - M.labelBefore(frame.pos)), y: 14)
            label("After")
                .opacity(M.labelAfter(frame.pos))
                .offset(x: 6 * (1 - M.labelAfter(frame.pos)) - 14, y: 14)
                .frame(width: layout.w, alignment: .topTrailing)
            shape.strokeBorder(.white.opacity(0.22), lineWidth: 2.0 / 3)
                .frame(width: layout.w, height: layout.photoH)
            Circle()
                .fill(.white.opacity(0.3))
                .overlay(Circle().strokeBorder(.white.opacity(0.75), lineWidth: 1.5))
                .frame(width: Self.dot, height: Self.dot)
                .scaleEffect(1.12 - 0.2 * frame.press)
                .opacity(frame.ghost * 0.9)
                .position(x: x, y: layout.handleY)
        }
        .frame(width: layout.w, height: layout.photoH, alignment: .topLeading)
        .clipShape(shape)
        .background {
            shape
                .fill(Self.color(0xd9d2c4))
                .shadow(color: Self.color(0x3c2814, 0.2), radius: 2, x: 0, y: 2)
                .shadow(color: Self.color(0x3c2814, 0.2), radius: 18, x: 0, y: 18)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func image(_ image: UIImage, _ layout: Layout) -> some View {
        Image(uiImage: image)
            .resizable()
            .interpolation(.high)
            .scaledToFill()
            .frame(width: layout.w, height: layout.photoH)
            .clipped()
    }

    /// Everything the glass handle sits on. Also drawn, blurred, inside the handle.
    private func scene(_ frame: CompareRevealSliderClock.Frame, _ layout: Layout) -> some View {
        let x = CGFloat(M.clamp01(frame.pos)) * layout.w
        return ZStack(alignment: .topLeading) {
            image(Self.beforeDay, layout)
            image(Self.beforeEvening, layout).opacity(frame.evening)
            ZStack {
                image(Self.afterDay, layout)
                image(Self.afterEvening, layout).opacity(frame.evening)
            }
            .clipShape(CompareRevealSliderAfterClip(x: x))
            LinearGradient(stops: [
                .init(color: Self.color(0x18120c, 0.34), location: 0),
                .init(color: Self.color(0x18120c, 0.12), location: 0.5),
                .init(color: Self.color(0x18120c, 0), location: 1),
            ], startPoint: .top, endPoint: .bottom)
            .frame(width: layout.w, height: 96)
            divider(frame, layout)
                .offset(x: CGFloat(frame.pos) * layout.w)
        }
        .frame(width: layout.w, height: layout.photoH, alignment: .topLeading)
    }

    private func divider(_ frame: CompareRevealSliderClock.Frame, _ layout: Layout) -> some View {
        let glow = Self.color(0xfffaf0, 0.2)
        let glowClear = Self.color(0xfffaf0, 0)
        let len = CGFloat(M.glintLen)
        let glintY = -len / 2 + CGFloat(frame.glint) * (layout.photoH + len)
        return ZStack(alignment: .topLeading) {
            LinearGradient(colors: [glowClear, glow, glowClear], startPoint: .leading, endPoint: .trailing)
                .frame(width: M.glowW, height: layout.photoH)
                .offset(x: -M.glowW / 2)
            Rectangle().fill(Self.color(0x281e14, 0.18))
                .frame(width: M.lineW + 1, height: layout.photoH)
                .offset(x: -M.lineW / 2)
            Rectangle().fill(Self.color(0xfffcf6, 0.88))
                .frame(width: M.lineW, height: layout.photoH)
                .offset(x: -M.lineW / 2)
            ZStack {
                LinearGradient(colors: [.white.opacity(0), .white.opacity(0.95), .white.opacity(0)], startPoint: .top, endPoint: .bottom)
                    .frame(width: 8, height: len)
                    .blur(radius: 3.5)
                LinearGradient(colors: [.white.opacity(0), .white, .white.opacity(0)], startPoint: .top, endPoint: .bottom)
                    .frame(width: M.lineW, height: len * 0.6)
            }
            .frame(width: 24, height: len)
            .opacity(M.glintGain(frame.stretch))
            .offset(x: -12, y: glintY - len / 2)
        }
        .frame(width: 1, height: layout.photoH, alignment: .topLeading)
    }

    private func handle(_ frame: CompareRevealSliderClock.Frame, _ layout: Layout, handleX: CGFloat, scaleX: Double, scaleY: Double) -> some View {
        let w = CGFloat(M.handleW)
        let h = CGFloat(M.handleH)
        let capsule = RoundedRectangle(cornerRadius: w / 2)
        let cropW: CGFloat = 112
        let cropH: CGFloat = 112
        let cropX = min(max(0, handleX - cropW / 2), max(0, layout.w - cropW))
        let cropY = min(max(0, layout.handleY - cropH / 2), max(0, layout.photoH - cropH))
        return ZStack(alignment: .topLeading) {
            // shadows
            capsule.fill(Self.color(0x1c1208))
                .frame(width: w, height: h)
                .blur(radius: 12)
                .opacity(0.3 - 0.14 * frame.press)
                .scaleEffect(x: scaleX, y: scaleY)
                .position(x: handleX, y: layout.handleY + 9 * scaleY)
            capsule.fill(Self.color(0x1c1208))
                .frame(width: w, height: h)
                .blur(radius: 2.5)
                .opacity(0.22 + 0.16 * frame.press)
                .scaleEffect(x: scaleX, y: scaleY)
                .position(x: handleX, y: layout.handleY + 1.5 * scaleY)

            // the room, frosted, through the glass
            scene(frame, layout)
                .offset(x: -cropX, y: -cropY)
                .frame(width: cropW, height: cropH, alignment: .topLeading)
                .clipped()
                .blur(radius: 9, opaque: true)
                .mask {
                    capsule
                        .frame(width: w, height: h)
                        .scaleEffect(x: scaleX, y: scaleY)
                        .position(x: handleX - cropX, y: layout.handleY - cropY)
                }
                .offset(x: cropX, y: cropY)

            // glass, rim and grabber
            ZStack {
                capsule.fill(Self.color(0xfffcf6, 0.3))
                capsule.fill(LinearGradient(stops: [
                    .init(color: .white.opacity(0.5), location: 0),
                    .init(color: .white.opacity(0.06), location: 0.55),
                    .init(color: .white.opacity(0.18), location: 1),
                ], startPoint: .top, endPoint: .bottom))
                capsule.stroke(LinearGradient(stops: [
                    .init(color: .white.opacity(0.95), location: 0),
                    .init(color: .white.opacity(0.35), location: 0.6),
                    .init(color: .white.opacity(0.6), location: 1),
                ], startPoint: .top, endPoint: .bottom), lineWidth: 1.2)
                CompareRevealSliderGrabber()
                    .stroke(Self.color(0x211d18, 0.82), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            }
            .frame(width: w, height: h)
            .scaleEffect(x: scaleX, y: scaleY)
            .position(x: handleX, y: layout.handleY)
        }
        .frame(width: layout.w, height: layout.photoH, alignment: .topLeading)
    }

    private func label(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .tracking(0.1)
            .foregroundStyle(Self.color(0xfffaf1))
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(Capsule().fill(Self.color(0x1c1610, 0.46)))
            .overlay(Capsule().strokeBorder(.white.opacity(0.22), lineWidth: 1.0 / 3))
    }

    private func factsCard(_ layout: Layout) -> some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        return HStack(spacing: 0) {
            ForEach(0..<Self.facts.count, id: \.self) { i in
                VStack(alignment: .leading, spacing: 2) {
                    Text(Self.facts[i].0)
                        .font(.system(size: layout.compact ? 16 : 18, weight: .semibold).monospacedDigit())
                        .tracking(-0.2)
                        .foregroundStyle(Self.inkColor)
                        .lineLimit(1)
                    Text(Self.facts[i].1)
                        .font(.system(size: 12.5))
                        .foregroundStyle(Self.mute)
                        .lineLimit(1)
                }
                .padding(.horizontal, 14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .overlay(alignment: .leading) {
                    if i > 0 { Rectangle().fill(Self.hair).frame(width: 1.0 / 3) }
                }
            }
        }
        .frame(width: layout.w, height: layout.factsH)
        .background {
            shape
                .fill(Self.card)
                .shadow(color: Self.color(0x3c2814, 0.1), radius: 1, x: 0, y: 1)
                .shadow(color: Self.color(0x3c2814, 0.06), radius: 10, x: 0, y: 8)
        }
    }
}

private struct CompareRevealSliderAfterClip: Shape {
    let x: CGFloat

    func path(in rect: CGRect) -> Path {
        Path(CGRect(x: x, y: rect.minY, width: max(0, rect.maxX - x), height: rect.height))
    }
}

private struct CompareRevealSliderGrabber: Shape {
    func path(in rect: CGRect) -> Path {
        let c = CGPoint(x: rect.midX, y: rect.midY)
        var path = Path()
        path.move(to: CGPoint(x: c.x - 7, y: c.y - 6))
        path.addLine(to: CGPoint(x: c.x - 12.5, y: c.y))
        path.addLine(to: CGPoint(x: c.x - 7, y: c.y + 6))
        path.move(to: CGPoint(x: c.x + 7, y: c.y - 6))
        path.addLine(to: CGPoint(x: c.x + 12.5, y: c.y))
        path.addLine(to: CGPoint(x: c.x + 7, y: c.y + 6))
        return path
    }
}

/// Day / Evening: the system segmented control. The film drives it through `selected`.
private struct CompareRevealSliderControls: View, Equatable {
    let selected: Int
    let onSelect: (Int) -> Void

    static func == (a: Self, b: Self) -> Bool { a.selected == b.selected }

    var body: some View {
        Picker("Light", selection: Binding(get: { selected }, set: onSelect)) {
            Text("Day").tag(0)
            Text("Evening").tag(1)
        }
        .pickerStyle(.segmented)
        .animation(.default, value: selected)
    }
}
