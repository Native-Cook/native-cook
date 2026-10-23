import CoreText
import SwiftUI
import UIKit

struct WaveformScrubberTrack {
    var title: String
    var artist: String
    var album: String
    /// Square cover art, also blurred behind the player.
    var cover: String
    var backdrop: String
    /// Length of the track, in seconds.
    var duration: Double

    static let sample = WaveformScrubberTrack(
        title: "Afterglow Avenue",
        artist: "Juno Vale",
        album: "Night Transit",
        cover: waveformScrubberPhoto("photo-1642019341061-f81d61659c62", 960, 960),
        // Drawn under a heavy blur, so a small file is enough.
        backdrop: waveformScrubberPhoto("photo-1642019341061-f81d61659c62", 480, 960),
        duration: 240
    )
}

private func waveformScrubberPhoto(_ id: String, _ w: Int, _ h: Int) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(w)&h=\(h)&q=80"
}

/// A night-drive player over a blurred neon street: the cover art, the title, and a waveform of
/// sixty rounded bars. Dragging across the waveform scrubs it: the bars under the finger swell like
/// a loupe passing over them, played bars fill with the accent, and a bubble rides above the finger
/// with the time under it. Letting go snaps the playhead to the start of a bar with a spring. The
/// play button morphs between play and pause, and while it plays the bars sway to the beat.
struct WaveformScrubber: View {
    var track = WaveformScrubberTrack.sample
    /// Plays, scrubs forward and back across the waveform, plays on and pauses, on a loop.
    var autoPlay = false
    /// Colour of the played bars, the time bubble's text and the play button's ring.
    var accent: (UInt32, UInt32) = (0xff9a5c, 0xff3d7f)
    /// Called with the new position, in seconds, when a scrub is let go.
    var onSeek: ((Double) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The player's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = WaveformScrubberEngine()
    @State private var cover: UIImage?
    @State private var backdrop: UIImage?

    private typealias M = WaveformScrubberMetrics

    static let ink = Color(hex: 0xf4f6fb)

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            ZStack(alignment: .topLeading) {
                backdropLayer(size: size)
                player(size: size, insets: insets)
            }
            .frame(width: size.width, height: size.height)
            .clipped()
            .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: track.cover) {
            async let art = WaveformScrubberPhotos.load(track.cover)
            async let back = WaveformScrubberPhotos.load(track.backdrop)
            if let image = await art {
                cover = image
                engine.coverArrived()
            }
            if let image = await back {
                withAnimation(.easeInOut(duration: M.imageFade)) { backdrop = image }
            }
        }
    }

    /// The photo, blurred and darkened like a dark blur view over it, then a fade that keeps the top
    /// and the controls readable. It never changes per frame, so it stays out of the timeline.
    private func backdropLayer(size: CGSize) -> some View {
        ZStack {
            Color(hex: 0x05070c)
            if let backdrop {
                Image(uiImage: backdrop)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size.width + 80, height: size.height + 80)
                    .blur(radius: 30, opaque: true)
                    .frame(width: size.width, height: size.height)
                    .clipped()
                    .transition(.opacity)
            }
            Color(hex: 0x101114, opacity: 0.45)
            LinearGradient(
                stops: [
                    .init(color: .rgba(6, 10, 18, 0.35), location: 0),
                    .init(color: .rgba(6, 10, 18, 0.1), location: 0.4),
                    .init(color: .rgba(6, 8, 14, 0.78), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
    }

    /// Every position is a share of the duration: a track without a length must not divide by zero.
    private var duration: Double {
        track.duration.isFinite && track.duration > 0 ? track.duration : 1
    }

    private func settings(width: Double) -> WaveformScrubberSettings {
        WaveformScrubberSettings(
            layout: WaveformScrubberLayout(width: width, duration: duration),
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            onSeek: onSeek
        )
    }

    private func player(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings(width: size.width)
        let coverSize = max(0, min(size.width - 80, size.height * 0.36))
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, settings: settings)
            VStack(spacing: 0) {
                topBar
                    .padding(.top, insets.top + 6)

                coverArt(frame: frame, side: coverSize)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                VStack(alignment: .leading, spacing: 2) {
                    Text(track.title)
                        .font(.custom("AvenirNext-Bold", fixedSize: 25))
                        .kerning(-0.3)
                        .foregroundStyle(Self.ink)
                    Text(track.artist)
                        .font(.custom("AvenirNext-Medium", fixedSize: 16))
                        .foregroundStyle(Color.rgba(236, 242, 255, 0.62))
                }
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, M.side)
                .padding(.top, 4)

                waveform(frame: frame, settings: settings)
                    .padding(.top, -14)

                controls(frame: frame)

                Text("Drag across the waveform to scrub")
                    .font(.custom("AvenirNext-Medium", fixedSize: 12))
                    .kerning(0.2)
                    .foregroundStyle(Color.rgba(236, 242, 255, 0.4))
                    .frame(maxWidth: .infinity)
                    .padding(.top, 6)
                    .padding(.bottom, insets.bottom + 14)
            }
            .frame(width: size.width, height: size.height)
        }
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            Path { p in
                p.move(to: CGPoint(x: 7, y: 11))
                p.addLine(to: CGPoint(x: 14, y: 18))
                p.addLine(to: CGPoint(x: 21, y: 11))
            }
            .stroke(Self.ink, style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
            .frame(width: 28, height: 28)

            VStack(spacing: 1) {
                Text("PLAYING FROM ALBUM")
                    .font(.custom("AvenirNext-DemiBold", fixedSize: 10))
                    .kerning(1.6)
                    .foregroundStyle(Color.rgba(236, 242, 255, 0.55))
                Text(track.album)
                    .font(.custom("AvenirNext-DemiBold", fixedSize: 14))
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)

            Canvas { context, _ in
                for x in [7.0, 14, 21] {
                    context.fill(Path(ellipseIn: CGRect(x: x - 2.1, y: 14 - 2.1, width: 4.2, height: 4.2)), with: .color(Self.ink))
                }
            }
            .frame(width: 28, height: 28)
        }
        .frame(height: 48)
        .padding(.horizontal, 20)
    }

    private func coverArt(frame: WaveformScrubberFrame, side: CGFloat) -> some View {
        let scale = reduceMotion ? 1 : M.coverPaused + (1 - M.coverPaused) * frame.cover
        return ZStack {
            // A CSS box shadow: cast by the box as if it were opaque, and only outside it.
            ZStack {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color(hex: 0x000000))
                    .shadow(color: Color(hex: 0x000000, opacity: 0.6), radius: 25, y: 26)
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .blendMode(.destinationOut)
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color(hex: 0x000000, opacity: 0.2))
            }
            .compositingGroup()
            .padding(EdgeInsets(top: 18, leading: 14, bottom: -6, trailing: 14))
            .opacity(0.35 + 0.65 * frame.cover)
            // Shows if the photo cannot load, so the card never turns into a bare shadow.
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color(hex: 0x1b1f2c))
                .frame(width: side, height: side)
            if let cover {
                Image(uiImage: cover)
                    .resizable()
                    .scaledToFill()
                    .frame(width: side, height: side)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .opacity(frame.coverAlpha)
                    .accessibilityLabel("\(track.album) cover art")
            }
        }
        .frame(width: side, height: side)
        .scaleEffect(scale)
    }

    // MARK: The waveform

    private func waveform(frame: WaveformScrubberFrame, settings: WaveformScrubberSettings) -> some View {
        let layout = settings.layout
        let accent = accent
        let reduced = reduceMotion
        return Canvas { context, _ in
            WaveformScrubberPainter.draw(in: context, frame: frame, layout: layout, accent: accent, reducedMotion: reduced)
        }
        .frame(height: M.canvasHeight)
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle().inset(by: -12))
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .local)
                .onChanged { engine.scrubChanged($0) }
                .onEnded { engine.scrubEnded($0) }
        )
        .accessibilityElement()
        .accessibilityLabel("Track position")
        .accessibilityValue(M.clock(min(duration, max(0, frame.progress))))
        .accessibilityAdjustableAction { direction in
            engine.seek(by: direction == .increment ? M.seekStep : -M.seekStep)
        }
    }

    // MARK: The controls

    private func controls(frame: WaveformScrubberFrame) -> some View {
        HStack(spacing: 44) {
            WaveformScrubberSeekIcon(direction: -1)
                .frame(width: 52, height: 52)
                .offset(x: min(0, frame.nudge))
                .contentShape(Rectangle())
                .gesture(tap(-1) { engine.seek(by: -M.seekStep) })
                .accessibilityElement()
                .accessibilityLabel("Back 10 seconds")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { engine.seek(by: -M.seekStep) }

            Canvas { context, _ in
                context.fill(WaveformScrubberPainter.icon(frame.morph), with: .color(Color(hex: 0x0b0d14)))
            }
            .frame(width: M.button, height: M.button)
            .background(Circle().fill(Self.ink).shadow(color: Color(hex: 0x000000, opacity: 0.45), radius: 15, y: 10))
            .scaleEffect(1 - 0.07 * frame.press)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in engine.buttonChanged() }
                    .onEnded { engine.buttonEnded($0) }
            )
            .accessibilityElement()
            .accessibilityLabel("Play or pause")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { engine.togglePlaying() }

            WaveformScrubberSeekIcon(direction: 1)
                .frame(width: 52, height: 52)
                .offset(x: max(0, frame.nudge))
                .contentShape(Rectangle())
                .gesture(tap(1) { engine.seek(by: M.seekStep) })
                .accessibilityElement()
                .accessibilityLabel("Forward 10 seconds")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { engine.seek(by: M.seekStep) }
        }
        .frame(height: 92)
    }

    /// A tap as Gesture Handler counts one: lifted within 500 ms without leaving the button.
    private func tap(_ id: Int, _ action: @escaping () -> Void) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in engine.tapBegan(id) }
            .onEnded { value in
                let held = engine.tapEnded(id)
                let moved = hypot(value.translation.width, value.translation.height)
                if moved < 26 && held <= 0.5 { action() }
            }
    }
}

// MARK: - Drawing

enum WaveformScrubberPainter {
    private typealias M = WaveformScrubberMetrics

    /// Every bar, its loudness swayed by the music and swollen by the loupe, in one path; the loupe's
    /// tallest bar and the bar under the playhead.
    static func bars(frame: WaveformScrubberFrame, layout: WaveformScrubberLayout, reducedMotion: Bool) -> (Path, Double, Double) {
        var path = Path()
        let e = frame.energy
        let now = frame.time
        let loupe = reducedMotion ? 0 : frame.lens
        let lx = frame.lensX
        let headBar = frame.progress / layout.duration * Double(M.bars)
        let beat = now * M.bpm / 60
        let pulse = exp(-(beat - floor(beat)) * 5) * e
        var peak = 0.0
        var headHalf = M.barHalf * 0.4
        for i in 0..<M.bars {
            let d = Double(i)
            let cx = layout.left + layout.slot * (d + 0.5)
            let near = exp(-pow((cx - lx) / M.lensRadius, 2)) * loupe
            let sway = M.dance * e * (
                0.55 * sin(now * (5.1 + Double(i % 7) * 0.53) + d * 1.7)
                    + 0.45 * sin(now * (3.3 + Double(i % 5) * 0.71) + d * 0.9)
            )
            let throb = M.beat * pulse * exp(-pow((d - headBar) / 5, 2))
            let half = max(1.6, M.barHalf * M.levels[i] * (1 + sway + throb) * (1 + M.magnify * near))
            let w = layout.slot * M.barFill * (1 + M.widen * near)
            path.addRoundedRect(
                in: CGRect(x: cx - w / 2, y: M.axisY - half, width: w, height: half * 2),
                cornerSize: CGSize(width: min(w / 2, half), height: min(w / 2, half))
            )
            if near > 0.02 { peak = max(peak, half) }
            if abs(d + 0.5 - headBar) < 0.5 { headHalf = half }
        }
        return (path, peak, headHalf)
    }

    static func draw(
        in context: GraphicsContext,
        frame: WaveformScrubberFrame,
        layout: WaveformScrubberLayout,
        accent: (UInt32, UInt32),
        reducedMotion: Bool
    ) {
        let duration = layout.duration
        let (bars, peak, headHalf) = bars(frame: frame, layout: layout, reducedMotion: reducedMotion)
        let at = min(duration, max(0, frame.progress))
        let lens = frame.lens

        context.fill(bars, with: .color(.rgba(236, 242, 255, 0.30)))
        // The loupe lights the bars it passes over.
        if lens > 0.001 {
            var glow = context
            glow.opacity = lens
            glow.fill(bars, with: .radialGradient(
                Gradient(colors: [Color(hex: 0xffffff, opacity: 0.75), Color(hex: 0xffffff, opacity: 0)]),
                center: CGPoint(x: frame.lensX, y: M.axisY),
                startRadius: 0,
                endRadius: M.lensRadius * 2.2
            ))
        }
        var played = context
        played.clip(to: Path(CGRect(x: 0, y: 0, width: max(0, layout.xOf(frame.progress)), height: Double(M.canvasHeight))))
        played.fill(bars, with: .linearGradient(
            Gradient(colors: [Color(hex: accent.0), Color(hex: accent.1)]),
            startPoint: CGPoint(x: layout.left, y: 0),
            endPoint: CGPoint(x: layout.left + layout.span, y: 0)
        ))

        let x = layout.xOf(at)
        let reach = headHalf + 9
        var head = context
        head.opacity = 0.55 + 0.45 * lens
        head.fill(
            Path(roundedRect: CGRect(x: x - 1, y: M.axisY - reach, width: 2, height: reach * 2), cornerRadius: 1),
            with: .color(WaveformScrubber.ink)
        )

        let timeFont = WaveformScrubberInk.font("AvenirNext-DemiBold", 12)
        text(context, M.clock(at), font: "AvenirNext-DemiBold", size: 12, color: .rgba(236, 242, 255, 0.7), x: M.side, baseline: M.timesY)
        let remaining = "-\(M.clock(duration - at))"
        text(
            context, remaining, font: "AvenirNext-DemiBold", size: 12, color: .rgba(236, 242, 255, 0.5),
            x: layout.width - M.side - WaveformScrubberInk.width(remaining, timeFont), baseline: M.timesY
        )

        // The bubble: it rides above the tallest bar under the finger and shows the time under it,
        // only over the middle of the loupe's opening, so it pops in and out quickly.
        let k = min(1, max(0, (lens - 0.35) / 0.5))
        let opacity = k * k * (3 - 2 * k)
        guard opacity > 0.001 else { return }
        let label = M.clock(at)
        let width = WaveformScrubberInk.width(label, WaveformScrubberInk.font("AvenirNext-Bold", 13)) + 22
        let bx = min(layout.width - 8 - width, max(8, x - width / 2))
        let bottom = M.axisY - max(M.barHalf, peak) - M.bubbleGap
        var bubble = context
        bubble.opacity = opacity
        bubble.translateBy(x: x, y: bottom)
        bubble.translateBy(x: 0, y: reducedMotion ? 0 : (1 - lens) * 10)
        let scale = reducedMotion ? 1 : 0.7 + 0.3 * lens
        bubble.scaleBy(x: scale, y: scale)
        bubble.translateBy(x: -x, y: -bottom)
        bubble.fill(
            Path(roundedRect: CGRect(x: bx, y: bottom - M.tail - M.bubbleHeight, width: width, height: M.bubbleHeight), cornerRadius: M.bubbleHeight / 2),
            with: .color(WaveformScrubber.ink)
        )
        let ax = min(bx + width - 14, max(bx + 14, x))
        var tail = Path()
        tail.move(to: CGPoint(x: ax - M.tail - 1, y: bottom - M.tail - 0.5))
        tail.addLine(to: CGPoint(x: ax, y: bottom))
        tail.addLine(to: CGPoint(x: ax + M.tail + 1, y: bottom - M.tail - 0.5))
        tail.closeSubpath()
        bubble.fill(tail, with: .color(WaveformScrubber.ink))
        text(bubble, label, font: "AvenirNext-Bold", size: 13, color: Color(hex: accent.1), x: bx + 11, baseline: bottom - M.tail - M.bubbleHeight / 2 + 4.6)
    }

    /// Text with its first letter's baseline origin at (x, baseline), as Skia draws it.
    private static func text(_ context: GraphicsContext, _ string: String, font: String, size: CGFloat, color: Color, x: Double, baseline: Double) {
        let resolved = context.resolve(Text(string).font(.custom(font, fixedSize: size)).foregroundStyle(color))
        let measured = resolved.measure(in: CGSize(width: 1000, height: 1000))
        let base = resolved.firstBaseline(in: measured)
        context.draw(resolved, in: CGRect(x: x, y: baseline - base, width: measured.width, height: measured.height))
    }

    /// The play/pause icon, `morph` of the way from play to pause, its corners rounded like Skia's
    /// corner path effect: each corner cut back along both edges and joined by a curve through it.
    static func icon(_ morph: Double) -> Path {
        let c = Double(M.button) / 2
        // The icon dips a little mid-morph, so the halves read as turning over rather than sliding.
        let k = 1 - 0.14 * sin(.pi * min(1, max(0, morph)))
        var path = Path()
        for half in 0..<2 {
            let from = half == 0 ? M.playLeft : M.playRight
            let to = half == 0 ? M.pauseLeft : M.pauseRight
            let points = (0..<4).map { p in
                CGPoint(
                    x: c + M.lerp(from[p * 2], to[p * 2], morph) * k,
                    y: c + M.lerp(from[p * 2 + 1], to[p * 2 + 1], morph) * k
                )
            }
            func cut(_ a: CGPoint, toward b: CGPoint) -> CGPoint {
                let dx = b.x - a.x
                let dy = b.y - a.y
                let length = hypot(dx, dy)
                guard length > 0 else { return a }
                let share = min(M.iconCorner / length, 0.5)
                return CGPoint(x: a.x + dx * share, y: a.y + dy * share)
            }
            for i in 0..<4 {
                let corner = points[i]
                let enter = cut(corner, toward: points[(i + 3) % 4])
                let leave = cut(corner, toward: points[(i + 1) % 4])
                if i == 0 { path.move(to: enter) } else { path.addLine(to: enter) }
                path.addQuadCurve(to: leave, control: corner)
            }
            path.closeSubpath()
        }
        return path
    }
}

/// Two arrowheads, rounded, pointing back or forward.
struct WaveformScrubberSeekIcon: View {
    let direction: Double

    var body: some View {
        Canvas { context, size in
            var ctx = context
            ctx.translateBy(x: (size.width - 30) / 2, y: (size.height - 27) / 2)
            if direction < 0 {
                ctx.translateBy(x: 30, y: 0)
                ctx.scaleBy(x: -1, y: 1)
            }
            var path = Path()
            for dx in [0.0, 11] {
                path.move(to: CGPoint(x: 4 + dx, y: 7.5))
                path.addQuadCurve(to: CGPoint(x: 5.3 + dx, y: 6.8), control: CGPoint(x: 4 + dx, y: 6))
                path.addLine(to: CGPoint(x: 14 + dx, y: 12.6))
                path.addQuadCurve(to: CGPoint(x: 14 + dx, y: 14), control: CGPoint(x: 15 + dx, y: 13.3))
                path.addLine(to: CGPoint(x: 5.3 + dx, y: 19.8))
                path.addQuadCurve(to: CGPoint(x: 4 + dx, y: 19), control: CGPoint(x: 4 + dx, y: 20.6))
                path.closeSubpath()
            }
            ctx.fill(path, with: .color(WaveformScrubber.ink))
        }
    }
}

/// Text widths the way the RN version's Skia fonts measure them: the ink bounds of the glyphs,
/// rounded out to whole points and padded by one on each side, not their advance.
enum WaveformScrubberInk {
    private static let fonts = NSCache<NSString, CTFont>()

    static func font(_ name: String, _ size: CGFloat) -> CTFont {
        let key = "\(name)-\(size)" as NSString
        if let hit = fonts.object(forKey: key) { return hit }
        let font = CTFontCreateWithName(name as CFString, size, nil)
        fonts.setObject(font, forKey: key)
        return font
    }

    static func width(_ text: String, _ font: CTFont) -> Double {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        let ink = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds)
        guard ink.width > 0 else { return 0 }
        return Double(ceil(ink.maxX) - floor(ink.minX)) + 2
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum WaveformScrubberPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}
