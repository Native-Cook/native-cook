import CoreText
import SwiftUI
import UIKit

struct PolaroidDevelopWelcomeShot {
    var photo: String
    /// Written on the border by hand.
    var caption: String
}

/// A welcome screen built around an instant camera. It opens with the camera taking a photo: the
/// flash fires and a print slides out of the slot and, still moving, settles onto the table below,
/// where it develops from a dark blank through a washed-out ghost to full colour and a caption
/// writes itself on its border. Tap the shutter for more; hold the top print and shake it to
/// develop it faster, or throw it aside to send it under the pile. A headline, a sub line, a
/// primary and a secondary action sit below.
struct PolaroidDevelopWelcome: View {
    /// Snaps two prints, shakes one, throws it under the pile, clears the table and starts over.
    var autoPlay = false
    /// Called by the primary button.
    var onStart: (() -> Void)? = nil
    /// Called by the secondary button.
    var onSignIn: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The table's motion lives in a plain object stepped once a frame, like the RN frame callbacks.
    @State private var engine = PolaroidDevelopWelcomeEngine(shots: PolaroidDevelopWelcomeShots.all.count)
    // False again once the finger is gone, also when the system takes the touch away.
    @GestureState private var touching = false

    private typealias S = PolaroidDevelopWelcomeShots
    private typealias M = PolaroidDevelopWelcomeMetrics

    var body: some View {
        // The block draws under the system bars and keeps the camera and the copy clear of them.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(layout: PolaroidDevelopWelcomeLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom))
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
                .task(id: size) { await makeGrain(size) }
        }
        .background(Color(hex: S.tableDark))
        .task { await loadPhotos() }
    }

    private func stage(layout: PolaroidDevelopWelcomeLayout) -> some View {
        TimelineView(.animation) { timeline in
            let _ = engine.step(to: timeline.date, layout: layout, autoPlay: autoPlay, reduced: reduceMotion)
            ZStack(alignment: .topLeading) {
                Canvas(rendersAsynchronously: false) { context, _ in
                    PolaroidDevelopWelcomePicture(engine: engine, layout: layout, reduced: reduceMotion).draw(context)
                }
                .frame(width: layout.width, height: layout.height)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .updating($touching) { _, state, _ in state = true }
                        .onChanged { engine.touchMoved($0) }
                        .onEnded { engine.touchEnded($0) }
                )
                .accessibilityElement()
                .accessibilityAddTraits([.isButton, .allowsDirectInteraction])
                .accessibilityLabel("Instant camera. Tap the shutter to take a photo.")
                .accessibilityAction { engine.snapFromAccessibility() }
                copy(layout)
                actions(layout)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
        .onChange(of: touching) { _, now in
            if !now { engine.touchLost() }
        }
    }

    /// An element arriving `delay` s after the screen opened: it rises and fades in.
    private func arriving(_ delay: Double) -> (opacity: Double, rise: Double) {
        let t = engine.entered(after: delay)
        return (t, reduceMotion ? 0 : (1 - t) * M.enterRise)
    }

    /// The header, and the headline on 38 pt lines with the sub line under it.
    private func copy(_ l: PolaroidDevelopWelcomeLayout) -> some View {
        let header = arriving(0)
        let title = arriving(M.enterStagger * 2)
        let sub = arriving(M.enterStagger * 3)
        let titleFont = UIFont(name: "AvenirNext-Bold", size: 34)
        let ascender = titleFont?.ascender ?? 34
        let descender = titleFont?.descender ?? -12
        // Lines shorter than the font's own are cut from the top: each baseline sits a descent
        // above the bottom of its 38 pt line.
        let lines = ["Every mile,", "one frame."]
        return ZStack(alignment: .topLeading) {
            HStack(spacing: 8) {
                PolaroidDevelopWelcomeMark()
                    .frame(width: 22, height: 22)
                Text("Roadbook")
                    .font(.custom("AvenirNext-Bold", fixedSize: 17))
                    .kerning(-0.2)
                    .foregroundStyle(Color(hex: S.cream))
            }
            .fixedSize()
            .opacity(header.opacity)
            .offset(x: 28, y: l.headerTop + header.rise)
            ForEach(0..<2, id: \.self) { k in
                Text(lines[k])
                    .font(.custom("AvenirNext-Bold", fixedSize: 34))
                    .kerning(-0.6)
                    .foregroundStyle(Color(hex: S.cream))
                    .fixedSize()
                    .opacity(title.opacity)
                    .offset(x: 28, y: l.copyTop + 38 * CGFloat(k + 1) + descender - ascender + title.rise)
            }
            Text("A travel journal that develops itself, one instant photo for every stop.")
                .font(.custom("AvenirNext-Medium", fixedSize: 16))
                .foregroundStyle(Color(hex: S.cream).opacity(0.62))
                .lineSpacing(0)
                .frame(width: l.width - 56, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .opacity(sub.opacity)
                .offset(x: 28, y: l.copyTop + 76 + 10 + sub.rise)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    /// The primary and secondary actions.
    private func actions(_ l: PolaroidDevelopWelcomeLayout) -> some View {
        let enter = arriving(M.enterStagger * 4)
        return VStack(spacing: 8) {
            Button {
                engine.touched()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                onStart?()
            } label: {
                Text("Start your roadbook")
                    .font(.custom("AvenirNext-Bold", fixedSize: 17))
                    .foregroundStyle(Color(hex: S.tableDark))
                    .frame(width: l.width - 56, height: 56)
                    .background(Capsule().fill(Color(hex: S.cream)))
                    .contentShape(Capsule())
            }
            .buttonStyle(PolaroidDevelopWelcomePressStyle(scale: 0.97))
            Button {
                engine.touched()
                onSignIn?()
            } label: {
                Text("I already have an account")
                    .font(.custom("AvenirNext-DemiBold", fixedSize: 15))
                    .foregroundStyle(Color(hex: S.cream).opacity(0.82))
                    .frame(width: l.width - 56, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PolaroidDevelopWelcomeFadeStyle())
        }
        .opacity(enter.opacity)
        .offset(x: 28, y: l.primaryTop + enter.rise)
    }

    private func loadPhotos() async {
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, shot) in S.all.enumerated() {
                group.addTask { (index, await PolaroidDevelopWelcomePhotos.load(shot.photo)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, at: index) }
            }
        }
    }

    private func makeGrain(_ size: CGSize) async {
        // Three samples a point across, where the grain is fine, and one down, where it is long.
        let width = Int(size.width.rounded()) * 3
        let height = Int(size.height.rounded())
        guard width > 0, height > 0 else { return }
        let image = await Task.detached(priority: .utility) {
            PolaroidDevelopWelcomeGrain.make(width: width, height: height)
        }.value
        if let image { engine.grainArrived(image) }
    }
}

/// Presses to `scale` on press-in (110 ms) and comes back in 220 ms.
private struct PolaroidDevelopWelcomePressStyle: ButtonStyle {
    let scale: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.easeOut(duration: configuration.isPressed ? 0.11 : 0.22), value: configuration.isPressed)
    }
}

/// Dims to 55% while pressed.
private struct PolaroidDevelopWelcomeFadeStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.55 : 1)
    }
}

/// The app's mark: a print with a road running into it.
private struct PolaroidDevelopWelcomeMark: View {
    var body: some View {
        Canvas { context, size in
            let k = size.width / 22
            var frame = Path()
            frame.move(to: CGPoint(x: 4 * k, y: 4 * k))
            frame.addLine(to: CGPoint(x: 18 * k, y: 4 * k))
            frame.addQuadCurve(to: CGPoint(x: 19.5 * k, y: 5.5 * k), control: CGPoint(x: 19.5 * k, y: 4 * k))
            frame.addLine(to: CGPoint(x: 19.5 * k, y: 18 * k))
            frame.addLine(to: CGPoint(x: 2.5 * k, y: 18 * k))
            frame.addLine(to: CGPoint(x: 2.5 * k, y: 5.5 * k))
            frame.addQuadCurve(to: CGPoint(x: 4 * k, y: 4 * k), control: CGPoint(x: 2.5 * k, y: 4 * k))
            frame.closeSubpath()
            context.stroke(frame, with: .color(Color(hex: 0xf4ebdd)), style: StrokeStyle(lineWidth: 1.9 * k, lineJoin: .round))
            var road = Path()
            road.move(to: CGPoint(x: 8.6 * k, y: 18 * k))
            road.addLine(to: CGPoint(x: 10.4 * k, y: 9.6 * k))
            road.move(to: CGPoint(x: 13.4 * k, y: 18 * k))
            road.addLine(to: CGPoint(x: 11.6 * k, y: 9.6 * k))
            context.stroke(road, with: .color(Color(hex: 0xe0a46a)), style: StrokeStyle(lineWidth: 1.9 * k, lineCap: .round))
        }
    }
}

/// One frame of the table: the wood, the hero (prints bottom of the pile first, then the camera)
/// and the flash's bloom over all.
@MainActor
private struct PolaroidDevelopWelcomePicture {
    let engine: PolaroidDevelopWelcomeEngine
    let layout: PolaroidDevelopWelcomeLayout
    let reduced: Bool

    private typealias M = PolaroidDevelopWelcomeMetrics
    private typealias X = PolaroidDevelopWelcomeMath
    private typealias S = PolaroidDevelopWelcomeShots

    func draw(_ context: GraphicsContext) {
        let l = layout
        let full = CGRect(x: 0, y: 0, width: l.width, height: l.height)
        context.fill(
            Path(full),
            with: .radialGradient(
                Gradient(colors: [Color(hex: S.tableLight), Color(hex: S.tableDark)]),
                center: CGPoint(x: l.width / 2, y: l.pileY - 60),
                startRadius: 0,
                endRadius: l.height * 0.7
            )
        )
        // Grain, so the table reads as a surface rather than a gradient.
        if let grain = engine.grain {
            var c = context
            c.opacity = 0.07
            c.draw(Image(decorative: grain, scale: 1), in: full)
        }
        // The lamp's pool of light falls off towards the copy.
        let fallTop = l.copyTop - 120
        context.fill(
            Path(CGRect(x: 0, y: fallTop, width: l.width, height: l.height - fallTop)),
            with: .linearGradient(
                Gradient(colors: [Color(hex: S.tableDark).opacity(0), Color(hex: S.tableDark).opacity(0.85)]),
                startPoint: CGPoint(x: 0, y: fallTop),
                endPoint: CGPoint(x: 0, y: l.copyTop + 40)
            )
        )

        // The hero scales about the camera's top centre, rises into place as the screen opens and
        // gives a little when the camera is pressed.
        let enter = engine.entered(after: 0)
        var hero = context
        hero.opacity = enter
        hero.translateBy(x: l.width / 2, y: l.cameraY + (reduced ? 0 : (1 - enter) * M.enterRise))
        let s = l.scale * (1 - engine.cameraPress.value * 0.015)
        hero.scaleBy(x: s, y: s)
        hero.translateBy(x: -l.width / 2, y: -l.cameraY)
        // Prints are hidden above the camera's middle, so one still inside the camera never shows.
        var pile = hero
        pile.clip(to: Path(CGRect(x: -l.width, y: l.cameraY + 30, width: l.width * 3, height: l.height * 2)))
        for print in engine.prints {
            self.print(pile, print)
        }
        camera(hero)

        let bloom = engine.flash.value * (reduced ? 0.35 : M.bloomPeak)
        if bloom > 0 {
            var c = context
            c.opacity = bloom
            c.fill(
                Path(full),
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0xfff8ea), location: 0),
                        .init(color: Color(hex: 0xfff1d8, opacity: 0.67), location: 0.35),
                        .init(color: Color(hex: 0xfff1d8, opacity: 0), location: 1),
                    ]),
                    center: CGPoint(x: l.cameraX + M.cameraWidth - 53, y: l.cameraY + 31),
                    startRadius: 0,
                    endRadius: l.height * 0.6
                )
            )
        }
    }

    /// One print: its shadow, the paper, the developing photo with its sheen, the caption and the
    /// shade of the prints above it.
    private func print(_ context: GraphicsContext, _ print: PolaroidDevelopWelcomePrint) {
        let pose = engine.pose(of: print)
        guard pose.alpha > 0.001, pose.x.isFinite, pose.y.isFinite else { return }
        var c = context
        c.opacity = pose.alpha
        c.translateBy(x: pose.x, y: pose.y)
        c.rotate(by: .degrees(pose.tilt))
        c.scaleBy(x: 1 + pose.lift, y: 1 + pose.lift)

        let left = -M.printWidth / 2
        let top = -M.printHeight / 2
        let lifted = max(0, pose.lift)
        let shadowAlpha = X.clamp01(0.55 - lifted * 3)
        if shadowAlpha > 0 {
            var shadow = c
            shadow.translateBy(x: 0, y: 3 + lifted * 160)
            shadow.opacity *= shadowAlpha
            let radius = M.blurRadius(4 + lifted * 140)
            shadow.drawLayer { layer in
                layer.addFilter(.blur(radius: radius))
                layer.fill(
                    Path(roundedRect: CGRect(x: left + 3, y: top + 3, width: M.printWidth - 6, height: M.printHeight - 6), cornerRadius: 3, style: .circular),
                    with: .color(.black)
                )
            }
        }
        let card = Path(roundedRect: CGRect(x: left, y: top, width: M.printWidth, height: M.printHeight), cornerRadius: 2.5, style: .circular)
        c.fill(
            card,
            with: .linearGradient(
                Gradient(colors: [Color(hex: S.paper), Color(hex: S.paperShade)]),
                startPoint: CGPoint(x: left, y: top),
                endPoint: CGPoint(x: -left, y: -top)
            )
        )

        let photoRect = CGRect(x: left + M.photoInset, y: top + M.photoInset, width: M.photoSize, height: M.photoSize)
        var face = c
        face.clip(to: Path(photoRect))
        face.fill(Path(photoRect), with: .color(Color(hex: 0x2a2420)))
        if engine.photos.indices.contains(print.shot), let image = engine.photos[print.shot], image.size.width > 0, image.size.height > 0 {
            let d = print.develop
            let blur = M.developBlur * pow(1 - d, 2)
            let values = X.developMatrix(d).map(Float.init)
            face.drawLayer { layer in
                var matrix = ColorMatrix()
                matrix.r1 = values[0]; matrix.r2 = values[1]; matrix.r3 = values[2]; matrix.r4 = values[3]; matrix.r5 = values[4]
                matrix.g1 = values[5]; matrix.g2 = values[6]; matrix.g3 = values[7]; matrix.g4 = values[8]; matrix.g5 = values[9]
                matrix.b1 = values[10]; matrix.b2 = values[11]; matrix.b3 = values[12]; matrix.b4 = values[13]; matrix.b5 = values[14]
                matrix.a1 = values[15]; matrix.a2 = values[16]; matrix.a3 = values[17]; matrix.a4 = values[18]; matrix.a5 = values[19]
                layer.addFilter(.colorMatrix(matrix))
                if blur > 0.01 { layer.addFilter(.blur(radius: M.blurRadius(blur), options: .opaque)) }
                let fit = max(photoRect.width / image.size.width, photoRect.height / image.size.height)
                let w = image.size.width * fit
                let h = image.size.height * fit
                layer.draw(Image(uiImage: image), in: CGRect(x: photoRect.midX - w / 2, y: photoRect.midY - h / 2, width: w, height: h))
            }
        }
        // A faint sheen across the glossy face of the film, and its edge.
        face.fill(
            Path(photoRect),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.15), location: 0),
                    .init(color: .white.opacity(0), location: 0.55),
                    .init(color: .white.opacity(0.07), location: 1),
                ]),
                startPoint: CGPoint(x: photoRect.minX, y: photoRect.minY),
                endPoint: CGPoint(x: photoRect.maxX, y: photoRect.maxY)
            )
        )
        face.stroke(Path(photoRect), with: .color(.black.opacity(0.2)), lineWidth: 1)

        // The caption is written left to right once the picture has come through.
        let reveal = X.smooth((print.develop - 0.62) / 0.3)
        if reveal > 0, S.all.indices.contains(print.shot) {
            let shot = S.all[print.shot]
            var caption = c
            caption.clip(to: Path(CGRect(x: left + M.photoInset, y: M.printHeight / 2 - 44, width: (S.captionWidths[print.shot] + 12) * reveal, height: 44)))
            caption.translateBy(x: left + M.photoInset + 4, y: M.printHeight / 2 - 15)
            caption.rotate(by: .radians(-0.025))
            let text = caption.resolve(Text(shot.caption).font(S.captionFont).foregroundColor(Color(hex: S.ink)))
            let ascent = text.firstBaseline(in: CGSize(width: 1000, height: 1000))
            caption.draw(text, at: CGPoint(x: 0, y: -ascent), anchor: .topLeading)
        }
        // Prints lower in the pile sit in the shade of the ones above.
        if pose.dim > 0 {
            c.fill(card, with: .color(Color(hex: 0x1a1009).opacity(pose.dim)))
        }
    }

    /// The instant camera, front on: a cream body with a darker lower band, a big lens in a ringed
    /// barrel, a ribbed flash window, a viewfinder, a terracotta shutter button and the film slot.
    private func camera(_ context: GraphicsContext) {
        let l = layout
        let x = l.cameraX
        let y = l.cameraY
        let w = M.cameraWidth
        let h = M.cameraHeight
        let r = M.cameraRadius
        let lens = M.lens
        let lensX = x + w / 2
        let lensY = y + h * 0.5
        let buttonX = x + w - 38
        let buttonY = y + h - 44
        let shutter = engine.shutter.value
        let flash = engine.flash.value
        let seam = y + h * 0.66

        func rounded(_ rx: CGFloat, _ ry: CGFloat, _ rw: CGFloat, _ rh: CGFloat, _ cr: CGFloat) -> Path {
            Path(roundedRect: CGRect(x: rx, y: ry, width: rw, height: rh), cornerRadius: cr, style: .circular)
        }
        func circle(_ cx: CGFloat, _ cy: CGFloat, _ cr: CGFloat) -> Path {
            Path(ellipseIn: CGRect(x: cx - cr, y: cy - cr, width: cr * 2, height: cr * 2))
        }
        func vertical(_ top: CGFloat, _ bottom: CGFloat, _ stops: [(UInt32, Double, CGFloat)]) -> GraphicsContext.Shading {
            .linearGradient(
                Gradient(stops: stops.map { .init(color: Color(hex: $0.0, opacity: $0.1), location: $0.2) }),
                startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: bottom)
            )
        }

        // The shutter sinks into the body; the whole camera gives a tiny jolt.
        var c = context
        c.translateBy(x: 0, y: shutter * 1.5)

        // Contact shadow on the table.
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: M.blurRadius(18)))
            layer.fill(rounded(x + 10, y + 18, w - 20, h - 6, r), with: .color(.black.opacity(0.6)))
        }
        c.fill(rounded(x, y, w, h, r), with: vertical(y, y + h, [(0xf6eee1, 1, 0), (0xe7dcc9, 1, 0.6), (0xd9ccb6, 1, 1)]))
        // The lower band, a shade darker, split from the top by a fine seam.
        var band = c
        band.clip(to: Path(CGRect(x: x, y: seam, width: w, height: h)))
        band.fill(rounded(x, y, w, h, r), with: vertical(seam, y + h, [(0xd8c9b0, 1, 0), (0xc6b498, 1, 1)]))
        c.fill(Path(CGRect(x: x + 4, y: seam, width: w - 8, height: 1)), with: .color(Color(hex: 0xa8977c).opacity(0.7)))
        c.fill(Path(CGRect(x: x + 4, y: seam + 1, width: w - 8, height: 1)), with: .color(.white.opacity(0.35)))
        // Light along the top edge, shade along the bottom.
        c.stroke(
            rounded(x + 0.75, y + 0.75, w - 1.5, h - 1.5, r - 0.75),
            with: vertical(y, y + h, [(0xffffff, 0.8, 0), (0xffffff, 0, 0.4), (0x000000, 0.25, 1)]),
            lineWidth: 1.5
        )

        // Viewfinder, top left, and the maker's plate on the lower band.
        c.fill(rounded(x + 24, y + 18, 36, 26, 7), with: .color(Color(hex: 0x2b2622)))
        c.fill(
            rounded(x + 27, y + 21, 30, 20, 5),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x4a5a72), Color(hex: 0x0e1219)]),
                startPoint: CGPoint(x: x + 27, y: y + 21),
                endPoint: CGPoint(x: x + 57, y: y + 41)
            )
        )
        c.fill(rounded(x + 30, y + 23, 9, 3.5, 1.75), with: .color(.white.opacity(0.4)))
        let plate = c.resolve(Text("ROADBOOK").font(.custom("AvenirNext-Bold", fixedSize: 8)).kerning(1.6).foregroundColor(Color(hex: 0x8f7d62)))
        let plateAscent = plate.firstBaseline(in: CGSize(width: 1000, height: 1000))
        c.draw(plate, at: CGPoint(x: x + 24, y: y + h - 28 - plateAscent), anchor: .topLeading)

        // Flash window, top right, with its ribbed lens; it lights up when it fires.
        c.fill(rounded(x + w - 84, y + 16, 62, 30, 7), with: .color(Color(hex: 0xb7ad9d)))
        c.fill(rounded(x + w - 82, y + 18, 58, 26, 6), with: vertical(y + 18, y + 44, [(0xfdfcf8, 1, 0), (0xd8d2c6, 1, 1)]))
        for k in 0..<7 {
            c.fill(Path(CGRect(x: x + w - 76.5 + CGFloat(k) * 7.5, y: y + 19, width: 1, height: 24)), with: .color(Color(hex: 0xb5ad9f).opacity(0.75)))
        }
        if flash > 0 {
            var glow = c
            glow.opacity = flash
            glow.drawLayer { layer in
                layer.addFilter(.blur(radius: M.blurRadius(14)))
                layer.fill(rounded(x + w - 94, y + 8, 82, 46, 14), with: .color(.white))
            }
            glow.fill(rounded(x + w - 82, y + 18, 58, 26, 6), with: .color(.white))
        }

        // Lens: a cream outer ring, a dark barrel with fine rings, and the glass.
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: M.blurRadius(8)))
            layer.fill(circle(lensX, lensY + 4, lens + 8), with: .color(.black.opacity(0.28)))
        }
        c.fill(circle(lensX, lensY, lens + 6), with: vertical(lensY - lens, lensY + lens, [(0xfbf6ee, 1, 0), (0xcbbda6, 1, 1)]))
        c.fill(circle(lensX, lensY, lens), with: vertical(lensY - lens, lensY + lens, [(0x45403b, 1, 0), (0x121110, 1, 1)]))
        c.fill(circle(lensX, lensY, lens - 8), with: .color(Color(hex: 0x0e0d0c)))
        c.stroke(circle(lensX, lensY, lens - 11), with: .color(Color(hex: 0x5a554f)), lineWidth: 1.2)
        c.fill(
            circle(lensX, lensY, lens - 17),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x46608a), location: 0),
                    .init(color: Color(hex: 0x16213a), location: 0.55),
                    .init(color: Color(hex: 0x04060a), location: 1),
                ]),
                center: CGPoint(x: lensX - 7, y: lensY - 9),
                startRadius: 0,
                endRadius: lens - 12
            )
        )
        c.fill(circle(lensX, lensY, 10), with: .color(Color(hex: 0x020306).opacity(0.85)))
        c.fill(circle(lensX - 12, lensY - 13, 6.5), with: .color(.white.opacity(0.4)))
        c.fill(circle(lensX + 11, lensY + 12, 2.2), with: .color(.white.opacity(0.28)))

        // Shutter button, with its ready pulse breathing out every 2.4 s.
        let pulse = engine.time.truncatingRemainder(dividingBy: 2.4) / 2.4
        let pulseAlpha = (1 - pulse) * 0.5 * engine.readyGlow
        if pulseAlpha > 0.001 {
            c.stroke(circle(buttonX, buttonY, 15 + pulse * 14), with: .color(Color(hex: S.shutter).opacity(pulseAlpha)), lineWidth: 2)
        }
        c.fill(circle(buttonX, buttonY, 15), with: .color(Color(hex: 0xb9a78b)))
        c.fill(circle(buttonX, buttonY, 13.5), with: .color(Color(hex: 0x8b7a61).opacity(0.6)))
        c.fill(
            circle(buttonX, buttonY, 11 - shutter * 2),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xe57259), location: 0),
                    .init(color: Color(hex: S.shutter), location: 0.6),
                    .init(color: Color(hex: 0x9d3a25), location: 1),
                ]),
                center: CGPoint(x: buttonX - 4, y: buttonY - 5),
                startRadius: 0,
                endRadius: 14
            )
        )

        // The film slot along the bottom edge.
        c.fill(rounded(x + w / 2 - M.printWidth / 2 - 9, y + h - 13, M.printWidth + 18, 7, 3.5), with: .color(Color(hex: 0x120f0c)))
        c.fill(Path(CGRect(x: x + w / 2 - M.printWidth / 2 - 6, y: y + h - 6, width: M.printWidth + 12, height: 1)), with: .color(.white.opacity(0.3)))
    }
}

// MARK: - Grain

/// Fractal noise like Skia's (an SVG `feTurbulence` of type fractalNoise), at 0.9 cycles per pt
/// across and 0.07 down with three octaves, one channel each for red, green, blue and alpha. The
/// image is three columns per point wide and one row per point tall.
enum PolaroidDevelopWelcomeGrain {
    private static let permutation: [Int] = {
        var table = Array(0..<256)
        var seed: UInt32 = 2_463_534_242
        for i in stride(from: 255, to: 0, by: -1) {
            seed ^= seed << 13
            seed ^= seed >> 17
            seed ^= seed << 5
            table.swapAt(i, Int(seed % UInt32(i + 1)))
        }
        return table + table
    }()

    private static func fade(_ t: Double) -> Double { t * t * t * (t * (t * 6 - 15) + 10) }

    private static func gradient(_ hash: Int, _ x: Double, _ y: Double) -> Double {
        switch hash & 7 {
        case 0: return x + y
        case 1: return x - y
        case 2: return -x + y
        case 3: return -x - y
        case 4: return x
        case 5: return -x
        case 6: return y
        default: return -y
        }
    }

    /// Gradient noise in about -1…1.
    private static func noise(_ x: Double, _ y: Double) -> Double {
        let p = permutation
        let xf = floor(x)
        let yf = floor(y)
        let xi = Int(xf) & 255
        let yi = Int(yf) & 255
        let dx = x - xf
        let dy = y - yf
        let u = fade(dx)
        let v = fade(dy)
        let aa = p[p[xi] + yi]
        let ab = p[p[xi] + yi + 1]
        let ba = p[p[xi + 1] + yi]
        let bb = p[p[xi + 1] + yi + 1]
        let x1 = gradient(aa, dx, dy) + (gradient(ba, dx - 1, dy) - gradient(aa, dx, dy)) * u
        let x2 = gradient(ab, dx, dy - 1) + (gradient(bb, dx - 1, dy - 1) - gradient(ab, dx, dy - 1)) * u
        return x1 + (x2 - x1) * v
    }

    static func make(width: Int, height: Int) -> CGImage? {
        var channels = [[Double]](repeating: [], count: 4)
        let offsets: [(Double, Double)] = [(0, 0), (37.1, 11.3), (71.9, 53.7), (19.4, 97.2)]
        for k in 0..<4 {
            var values = [Double](repeating: 0, count: width * height)
            let (ox, oy) = offsets[k]
            for row in 0..<height {
                let y = Double(row) * 0.07 + oy
                for column in 0..<width {
                    let x = Double(column) * 0.9 / 3 + ox
                    let sum = noise(x, y) + noise(x * 2, y * 2) / 2 + noise(x * 4, y * 4) / 4
                    values[row * width + column] = min(1, max(0, (sum + 1) / 2))
                }
            }
            channels[k] = values
        }
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        for i in 0..<(width * height) {
            let a = channels[3][i]
            pixels[i * 4] = UInt8(channels[0][i] * a * 255)
            pixels[i * 4 + 1] = UInt8(channels[1][i] * a * 255)
            pixels[i * 4 + 2] = UInt8(channels[2][i] * a * 255)
            pixels[i * 4 + 3] = UInt8(a * 255)
        }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData) else { return nil }
        return CGImage(
            width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum PolaroidDevelopWelcomePhotos {
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

// MARK: - Shots

enum PolaroidDevelopWelcomeShots {
    // The photos are drawn about 130 pt square; 520 px covers that at 3x with the lift.
    static func photo(_ id: String) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=520&h=520&q=80"
    }

    // The film in the camera, shot in this order.
    static let all: [PolaroidDevelopWelcomeShot] = [
        .init(photo: photo("photo-1526478512290-5397e7d2ca6a"), caption: "Route 9, first light"),
        .init(photo: photo("photo-1597649260564-997729f07c5d"), caption: "Red rock pass"),
        .init(photo: photo("photo-1578158335529-27b8d78cfea5"), caption: "Van, mile 212"),
        .init(photo: photo("photo-1557159557-7a93eaadf72a"), caption: "Straight on, 90 mi"),
        .init(photo: photo("photo-1534540378968-85a7b8fde19f"), caption: "Last of the sun"),
        .init(photo: photo("photo-1494822493217-c9840aba840c"), caption: "Snowline in July"),
        .init(photo: photo("photo-1484687742385-1249620c2687"), caption: "Parked for pie"),
        .init(photo: photo("photo-1573097637683-58e6462d2902"), caption: "Pines all the way"),
    ]

    // Palette: a walnut table under a warm lamp, a cream camera, cream paper and blue ink.
    static let tableLight: UInt32 = 0x57412f
    static let tableDark: UInt32 = 0x140e0a
    static let paper: UInt32 = 0xf5f1e8
    static let paperShade: UInt32 = 0xe4dccd
    static let ink: UInt32 = 0x26365a
    static let cream: UInt32 = 0xf4ebdd
    static let shutter: UInt32 = 0xc9553b

    static let captionName = "BradleyHandITCTT-Bold"
    static let captionFont = Font.custom(captionName, fixedSize: PolaroidDevelopWelcomeMetrics.captionSize)

    /// Each glyph's own advance added up, the way the RN version measures a line.
    static func advance(_ text: String, font name: String, size: CGFloat) -> CGFloat {
        let font = CTFontCreateWithName(name as CFString, size, nil)
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var sizes = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &sizes, chars.count)
        return sizes.reduce(0) { $0 + $1.width }
    }

    static let captionWidths: [CGFloat] = all.map { advance($0.caption, font: captionName, size: PolaroidDevelopWelcomeMetrics.captionSize) }
}
