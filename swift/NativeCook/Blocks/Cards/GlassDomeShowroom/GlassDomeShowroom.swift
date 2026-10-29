import SwiftUI
import UIKit

/// The line-up of Nuvra, an invented electric car maker. Each car stands under a tall glass bell
/// jar; the jars ride a carousel with the featured car in the middle and its neighbours peeking in
/// at the edges. The glass reads as glass: a bright rim, vertical specular streaks that slide
/// across it as the jar moves, a lighter refracted copy of the wash inside, the far and near rims
/// of its base around the car, a contact shadow and a mirror image on the glossy floor. Each car
/// brings its own full-screen colour wash, the giant MORE stack rolls up one row per car, and the
/// carousel smears with its speed.
///
/// Swipe to change car (it carries the finger's speed and snaps to a jar); tap a side jar to bring
/// it to the middle; tap the heart to like the car. 2 s after the last touch the carousel springs
/// back to the first car and the loop restarts. Reduced motion: the first car, still.
struct GlassDomeShowroom: View {
    static let loop = GlassDomeShowroomMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = GlassDomeShowroomTouch()
    @State private var awake = false
    /// Bumped by a heart tap so a still screen (reduced motion) redraws.
    @State private var likeTick = 0

    private typealias M = GlassDomeShowroomMotion
    private typealias Art = GlassDomeShowroomArt

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            let safeBottom = outer.safeAreaInsets.bottom
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let height = geometry.size.height / k
                let floorY = M.floorY(height: height, safeBottom: safeBottom / k)
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active || !(autoPlay || awake))) { timeline in
                    let clock = max(0, timeline.date.timeIntervalSince(epoch))
                    scene(clock: clock, k: k, height: height, floorY: floorY, safeTop: safeTop / k, safeBottom: safeBottom / k)
                }
                .contentShape(Rectangle())
                .gesture(drag(k: k, floorY: floorY))
            }
            .ignoresSafeArea()
        }
        .background(Art.color(Art.cars[0].mid).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: awake) {
            guard awake, !autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching, touch.release != nil, clock > touch.restsAfter {
                    awake = false
                    return
                }
            }
        }
    }

    private func phi(_ clock: Double) -> Double {
        reducedMotion ? 0 : touch.phi(clock, autoPlay: autoPlay)
    }

    /// Carousel speed in cars per second (wrap-aware, so the loop's seam is not a jump).
    private func speed(_ clock: Double) -> Double {
        if reducedMotion { return 0 }
        if touch.touching { return touch.dragVelocity }
        let dt = 1.0 / 240
        return M.wrap(phi(clock) - phi(clock - dt)) / dt
    }

    // MARK: Scene

    @ViewBuilder
    private func scene(clock: Double, k: CGFloat, height: Double, floorY: Double, safeTop: Double, safeBottom: Double) -> some View {
        let p = phi(clock)
        let wash = Art.wash(p)
        let jarSmear = min(18, 0.018 * abs(speed(clock)) * M.spacing)
        let headPhi = phi(clock - 0.06)
        let headSmear = min(9, 0.03 * abs(speed(clock - 0.06)) * M.headlinePitch)
        let selected = M.index(p)
        let nearness = 1 - 2 * abs(p - p.rounded())
        ZStack(alignment: .topLeading) {
            Canvas { context, size in
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
                    Gradient(stops: [.init(color: wash.top, location: 0), .init(color: wash.mid, location: 0.55), .init(color: wash.bottom, location: 1)]),
                    startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
                context.scaleBy(x: k, y: k)
                // A soft spotlight on the floor under the middle jar.
                var spot = context
                spot.translateBy(x: M.centreX, y: floorY + 6)
                spot.scaleBy(x: 1, y: 0.22)
                spot.fill(Path(ellipseIn: CGRect(x: -210, y: -210, width: 420, height: 420)), with: .radialGradient(
                    Gradient(stops: [.init(color: .white.opacity(0.16), location: 0), .init(color: .white.opacity(0), location: 1)]),
                    center: .zero, startRadius: 0, endRadius: 210))
            }
            .allowsHitTesting(false)

            headline(phi: headPhi, smear: headSmear, k: k)
                .offset(y: safeTop * k)

            VStack(spacing: 0) {
                Text("Nuvra Electric")
                Text("more miles for your money")
            }
            .font(.system(size: 18 * k, weight: .semibold))
            .foregroundStyle(.white)
            .lineSpacing(0)
            .frame(width: 393 * k)
            .offset(y: (safeTop + 238) * k)
            .allowsHitTesting(false)

            Canvas { context, _ in
                _ = likeTick
                context.scaleBy(x: k, y: k)
                for jar in M.jars(phi: p) {
                    drawJar(context, jar: jar, floorY: floorY, clock: clock, wash: wash)
                }
            }
            .layerEffect(ShaderLibrary.glassDomeShowroomSmear(.float2(jarSmear * k, 0)),
                         maxSampleOffset: CGSize(width: 20 * k, height: 0))
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel("The new \(Art.cars[selected].model) \(Art.cars[selected].trim)\(touch.liked(selected, clock: clock, autoPlay: autoPlay) ? ", liked" : "")")
            .accessibilityHint("Swipe left or right to see another car")

            Text(Art.cars[selected].info)
                .font(.system(size: 14 * k, weight: .semibold).monospacedDigit())
                .foregroundStyle(.white.opacity(0.78))
                .opacity(nearness)
                .frame(width: 393 * k)
                .offset(y: (floorY + 56 - 9) * k)
                .allowsHitTesting(false)

            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                Text("Book a test drive")
                    .font(.system(size: 17 * k, weight: .semibold))
                    .foregroundStyle(wash.bottom)
                    .frame(width: 220 * k, height: 52 * k)
                    .background(Capsule().fill(Color.white.opacity(0.96)))
                    .shadow(color: .black.opacity(0.18), radius: 10 * k, y: 4 * k)
            }
            .buttonStyle(.plain)
            .offset(x: (M.centreX - 110) * k, y: (floorY + 76) * k)
        }
    }

    /// The stacked MORE headline: rows roll up one row per car, with a vertical smear and fading
    /// top and bottom edges.
    private func headline(phi: Double, smear: Double, k: CGFloat) -> some View {
        let pitch = M.headlinePitch
        let shift = (phi - floor(phi)) * pitch
        return Canvas { context, _ in
            context.scaleBy(x: k, y: k)
            let row = context.resolve(Text("MORE").font(.system(size: 76, weight: .black)).tracking(-1).foregroundStyle(.white))
            for n in -1...4 {
                context.draw(row, at: CGPoint(x: M.centreX, y: 40 + Double(n) * pitch - shift), anchor: .center)
            }
        }
        .frame(width: 393 * k, height: 236 * k)
        .layerEffect(ShaderLibrary.glassDomeShowroomSmear(.float2(0, smear * k)), maxSampleOffset: CGSize(width: 0, height: 10 * k))
        .mask(LinearGradient(stops: [
            .init(color: .clear, location: 0),
            .init(color: .white, location: 14.0 / 236),
            .init(color: .white, location: 182.0 / 236),
            .init(color: .white.opacity(0.25), location: 212.0 / 236),
            .init(color: .clear, location: 1),
        ], startPoint: .top, endPoint: .bottom))
        .allowsHitTesting(false)
        .accessibilityElement()
        .accessibilityLabel("More, more, more")
        .accessibilityAddTraits(.isHeader)
    }

    // MARK: Jar

    private func jarPath() -> Path {
        let w = M.jarWidth, h = M.jarHeight, r = M.jarRadius
        return Path(roundedRect: CGRect(x: -w / 2, y: 4 - h, width: w, height: h),
                    cornerRadii: RectangleCornerRadii(topLeading: r, bottomLeading: 0, bottomTrailing: 0, topTrailing: r),
                    style: .continuous)
    }

    /// Half of the base ellipse (centre at the jar's bottom): the far half when `far`, else the near one.
    private func baseArc(far: Bool) -> Path {
        var path = Path()
        let rx = M.jarWidth / 2, ry = 12.0
        for s in 0...40 {
            let a = Double.pi * Double(s) / 40 + (far ? Double.pi : 0)
            let point = CGPoint(x: rx * cos(a), y: 4 + ry * sin(a))
            if s == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        return path
    }

    private func softEllipse(_ context: GraphicsContext, x: Double, y: Double, rx: Double, ry: Double, alpha: Double) {
        var e = context
        e.translateBy(x: x, y: y)
        e.scaleBy(x: 1, y: ry / rx)
        e.fill(Path(ellipseIn: CGRect(x: -rx, y: -rx, width: 2 * rx, height: 2 * rx)), with: .radialGradient(
            Gradient(stops: [
                .init(color: .black.opacity(alpha), location: 0),
                .init(color: .black.opacity(alpha * 0.55), location: 0.5),
                .init(color: .black.opacity(0), location: 1),
            ]), center: .zero, startRadius: 0, endRadius: rx))
    }

    private func drawJar(_ context: GraphicsContext, jar: M.Jar, floorY: Double, clock: Double,
                         wash: (top: Color, mid: Color, bottom: Color)) {
        let car = Art.cars[jar.index]
        let uiImage = Art.images[jar.index]
        let aspect = uiImage.size.height > 0 ? uiImage.size.width / uiImage.size.height : 2.55
        let carW = M.carWidth, carH = M.carWidth / aspect
        let w = M.jarWidth, h = M.jarHeight
        let top = 4 - h
        let shift = -22 * jar.d
        let path = jarPath()

        var c = context
        c.translateBy(x: jar.x, y: floorY)
        c.scaleBy(x: jar.scale, y: jar.scale)
        let image = c.resolve(Image(uiImage: uiImage))

        // Mirror image on the glossy floor.
        c.drawLayer { layer in
            layer.scaleBy(x: 1, y: -1)
            layer.draw(image, in: CGRect(x: -carW / 2, y: -carH + 1, width: carW, height: carH))
            layer.blendMode = .destinationIn
            layer.fill(Path(CGRect(x: -carW / 2 - 4, y: -carH, width: carW + 8, height: carH + 2)), with: .linearGradient(
                Gradient(stops: [.init(color: .black.opacity(0.30), location: 0), .init(color: .black.opacity(0), location: 1)]),
                startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: -48)))
        }

        // Back of the glass: the wash refracted lighter, frosted, brighter at the edges (Fresnel).
        var back = c
        back.clip(to: path)
        back.fill(path, with: .linearGradient(
            Gradient(stops: [.init(color: wash.top.opacity(0.45), location: 0), .init(color: wash.top.opacity(0.1), location: 1)]),
            startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: 4)))
        back.fill(path, with: .linearGradient(
            Gradient(stops: [.init(color: .white.opacity(0.13), location: 0), .init(color: .white.opacity(0.05), location: 0.55), .init(color: .white.opacity(0.09), location: 1)]),
            startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: 4)))
        back.fill(path, with: .linearGradient(
            Gradient(stops: [
                .init(color: .white.opacity(0.22), location: 0),
                .init(color: .white.opacity(0.05), location: 0.09),
                .init(color: .white.opacity(0), location: 0.24),
                .init(color: .white.opacity(0), location: 0.78),
                .init(color: .white.opacity(0.05), location: 0.91),
                .init(color: .white.opacity(0.2), location: 1),
            ]), startPoint: CGPoint(x: -w / 2, y: 0), endPoint: CGPoint(x: w / 2, y: 0)))
        // The floor inside the jar and the far rim of its base.
        back.fill(Path(ellipseIn: CGRect(x: -w / 2, y: 4 - 12, width: w, height: 24)), with: .color(.white.opacity(0.06)))
        c.stroke(baseArc(far: true), with: .color(.white.opacity(0.22)), lineWidth: 1)

        // The label behind the glass.
        let tx = -w / 2 + 24, ty = top + 30
        var label = c
        label.addFilter(.shadow(color: .black.opacity(0.22), radius: 5, x: 0, y: 1))
        label.draw(Text("The new").font(.system(size: 20, weight: .bold)).foregroundStyle(.white), at: CGPoint(x: tx, y: ty), anchor: .topLeading)
        label.draw(Text(car.model).font(.system(size: 20, weight: .bold)).foregroundStyle(.white), at: CGPoint(x: tx, y: ty + 24), anchor: .topLeading)
        label.draw(Text(car.trim).font(.system(size: 20, weight: .regular)).foregroundStyle(.white.opacity(0.92)), at: CGPoint(x: tx, y: ty + 48), anchor: .topLeading)
        drawHeart(c, index: jar.index, clock: clock)

        // The car on its contact shadow.
        softEllipse(c, x: 0, y: 0, rx: 130, ry: 15, alpha: 0.32)
        softEllipse(c, x: 0, y: -1, rx: 104, ry: 5, alpha: 0.6)
        c.draw(image, in: CGRect(x: -carW / 2, y: -carH + 1, width: carW, height: carH))

        // Front of the glass: specular streaks that slide as the jar moves, the dome's highlight.
        var front = c
        front.clip(to: path)
        front.fill(path, with: .linearGradient(
            Gradient(stops: [.init(color: .white.opacity(0.07), location: 0), .init(color: .white.opacity(0), location: 0.6)]),
            startPoint: CGPoint(x: -w / 2, y: top), endPoint: CGPoint(x: w / 2, y: 4)))
        let broadX = -w / 2 + 30 + shift
        front.fill(Path(roundedRect: CGRect(x: broadX, y: top + 34, width: 34, height: h - 70), cornerRadius: 17), with: .linearGradient(
            Gradient(stops: [.init(color: .white.opacity(0), location: 0), .init(color: .white.opacity(0.2), location: 0.45), .init(color: .white.opacity(0), location: 1)]),
            startPoint: CGPoint(x: broadX, y: 0), endPoint: CGPoint(x: broadX + 34, y: 0)))
        front.fill(Path(roundedRect: CGRect(x: broadX + 42, y: top + 52, width: 3.5, height: h - 120), cornerRadius: 1.75),
                   with: .linearGradient(Gradient(stops: [.init(color: .white.opacity(0.42), location: 0), .init(color: .white.opacity(0.06), location: 1)]),
                                         startPoint: CGPoint(x: 0, y: top + 52), endPoint: CGPoint(x: 0, y: top + h - 68)))
        let rightX = w / 2 - 26 + 0.6 * shift
        front.fill(Path(roundedRect: CGRect(x: rightX, y: top + 46, width: 10, height: h - 96), cornerRadius: 5), with: .linearGradient(
            Gradient(stops: [.init(color: .white.opacity(0), location: 0), .init(color: .white.opacity(0.16), location: 0.5), .init(color: .white.opacity(0), location: 1)]),
            startPoint: CGPoint(x: rightX, y: 0), endPoint: CGPoint(x: rightX + 10, y: 0)))
        // The dome's highlight: a bright arc inside the top-left corner, with a soft glow.
        var arc = Path()
        let r = M.jarRadius
        for s in 0...24 {
            let a = (188 + 72 * Double(s) / 24) * Double.pi / 180
            let point = CGPoint(x: -w / 2 + r + (r - 6) * cos(a), y: top + r + (r - 6) * sin(a))
            if s == 0 { arc.move(to: point) } else { arc.addLine(to: point) }
        }
        front.stroke(arc, with: .color(.white.opacity(0.14)), style: StrokeStyle(lineWidth: 10, lineCap: .round))
        front.stroke(arc, with: .color(.white.opacity(0.6)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))

        // The rim: a faint chromatic fringe, the bright edge, and the glass's inner edge.
        var rim = c
        rim.clip(to: Path(CGRect(x: -w / 2 - 6, y: top - 6, width: w + 12, height: h + 3)))
        var cyan = rim
        cyan.translateBy(x: -0.9, y: 0)
        cyan.stroke(path, with: .color(Color(.sRGB, red: 0.55, green: 0.95, blue: 1, opacity: 0.28)), lineWidth: 1)
        var red = rim
        red.translateBy(x: 0.9, y: 0)
        red.stroke(path, with: .color(Color(.sRGB, red: 1, green: 0.6, blue: 0.75, opacity: 0.22)), lineWidth: 1)
        rim.stroke(path, with: .linearGradient(
            Gradient(stops: [.init(color: .white.opacity(0.9), location: 0), .init(color: .white.opacity(0.45), location: 0.5), .init(color: .white.opacity(0.3), location: 1)]),
            startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: 4)), lineWidth: 1.6)
        let inner = Path(roundedRect: CGRect(x: -w / 2 + 4, y: top + 4, width: w - 8, height: h + 4),
                         cornerRadii: RectangleCornerRadii(topLeading: r - 4, bottomLeading: 0, bottomTrailing: 0, topTrailing: r - 4),
                         style: .continuous)
        rim.stroke(inner, with: .color(.white.opacity(0.14)), lineWidth: 1)
        // The near rim of the base, in front of the car's wheels.
        c.stroke(baseArc(far: false), with: .color(.white.opacity(0.42)), lineWidth: 1.4)
    }

    private func drawHeart(_ context: GraphicsContext, index: Int, clock: Double) {
        let centre = M.heartCentre
        let liked = touch.liked(index, clock: clock, autoPlay: autoPlay)
        let age = reducedMotion ? 10 : touch.popAge(index, clock: clock, autoPlay: autoPlay)
        var c = context
        c.translateBy(x: centre.x, y: centre.y)
        c.stroke(Path(ellipseIn: CGRect(x: -19, y: -19, width: 38, height: 38)), with: .color(.white.opacity(0.42)), lineWidth: 1.5)

        if age >= 0, age < 0.8 {
            // Ring and particles of the pop.
            let p = age / 0.8
            let e = 1 - pow(1 - min(1, age / 0.45), 3)
            if age < 0.45 {
                let rr = 19 + 18 * e
                c.stroke(Path(ellipseIn: CGRect(x: -rr, y: -rr, width: 2 * rr, height: 2 * rr)),
                         with: .color(Art.color(Art.heartRed, 0.7 * (1 - e))), lineWidth: 2.2 * (1 - e) + 0.4)
            }
            let ease = 1 - pow(1 - p, 3)
            for i in 0..<10 {
                let fi = Double(i)
                let angle = -Double.pi / 2 + (fi - 4.5) / 4.5 * 1.25 + (Art.hash(fi + 1) - 0.5) * 0.3
                let dist = 18 + (34 + 16 * Art.hash(fi + 7)) * ease
                let size = (7 + 5 * Art.hash(fi + 3)) * (1 - 0.45 * p)
                var dot = c
                dot.translateBy(x: cos(angle) * dist, y: sin(angle) * dist - 10 * p)
                dot.fill(Art.heart(size: size), with: .color(Art.color(i % 2 == 0 ? Art.heartRed : Art.heartPink, 1 - p)))
            }
        }

        let scale = M.heartScale(age)
        var glyph = c
        glyph.scaleBy(x: scale, y: scale)
        let heart = Art.heart(size: 18)
        if liked {
            glyph.fill(heart, with: .color(Art.color(Art.heartRed)))
        } else {
            glyph.stroke(heart, with: .color(.white.opacity(0.9)), lineWidth: 1.6)
        }
    }

    // MARK: Touch

    private func drag(k: CGFloat, floorY: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching {
                    touch.begin(clock: clock, autoPlay: autoPlay && !reducedMotion)
                    if !awake && !reducedMotion { awake = true }
                }
                if reducedMotion { return }
                if touch.move(dx: value.translation.width / k, dy: value.translation.height / k, clock: clock) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.7)
                }
            }
            .onEnded { value in
                guard touch.touching else { return }
                let clock = Date().timeIntervalSince(epoch)
                let point = CGPoint(x: value.location.x / k, y: value.location.y / k)
                let feedback = touch.end(clock: clock, velocityX: reducedMotion ? 0 : value.velocity.width / k, point: point, floorY: floorY)
                likeTick += 1
                switch feedback {
                case .none: break
                case .like: UIImpactFeedbackGenerator(style: .medium).impactOccurred(intensity: 0.7)
                case .unlike, .snap: UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
    }
}
