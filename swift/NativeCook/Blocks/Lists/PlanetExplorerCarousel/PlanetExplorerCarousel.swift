import CoreText
import SwiftUI
import UIKit

struct PlanetExplorerCarousel: View {
    static let loop: TimeInterval = 10.2

    private let planets = PlanetExplorerCarouselPlanet.all
    private let friends = [
        "PlanetExplorerCarousel-avatar-2.png", "PlanetExplorerCarousel-avatar-3.png",
        "PlanetExplorerCarousel-avatar-4.png", "PlanetExplorerCarousel-avatar-5.png",
    ]
    private let stars: [(CGFloat, CGFloat)] = [
        (48, 152), (201, 245), (345, 242), (358, 272), (120, 370), (48, 280),
        (372, 330), (96, 708), (112, 690), (300, 722), (366, 740), (70, 790),
        (330, 770), (185, 860), (26, 640), (380, 610), (250, 300), (140, 250),
    ]

    var autoPlay = false
    var freezeAt: TimeInterval? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var motion = PlanetExplorerCarouselMotion()
    @State private var dragOrigin: CGFloat?
    @State private var resumeTask: Task<Void, Never>?
    @State private var clockStopTask: Task<Void, Never>?
    @State private var clockActive = false
    @State private var redrawToken = 0

    var body: some View {
        let _ = PlanetExplorerCarouselFonts.registered
        let _ = redrawToken
        GeometryReader { proxy in
            let scale = proxy.size.width / 402
            TimelineView(.animation(minimumInterval: 1 / 120, paused: !clockActive)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let frame = freezeAt.map { motion.frozenFrame(milliseconds: $0, reduced: reduceMotion) }
                    ?? motion.frame(at: now, autoPlay: autoPlay, reduced: reduceMotion)
                ZStack(alignment: .topLeading) {
                    list(frame, scale: scale, size: proxy.size)
                        .opacity(1 - window(frame.detail, 0, 0.78))
                        .offset(y: reduceMotion ? 0 : 12 * window(frame.detail, 0, 0.78) * scale)
                        .allowsHitTesting(frame.detail < 0.5)
                    detailBackdrop(frame, scale: scale, size: proxy.size)
                    detailContent(frame, scale: scale)
                    detailHero(frame, scale: scale)
                    detailRocket(frame, scale: scale)
                    backButton(frame, scale: scale, now: now)
                }
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .clipped()
                .allowsHitTesting(freezeAt == nil)
            }
        }
        .background(Color(hex: 0x185CA3))
        .ignoresSafeArea()
        .onAppear { refreshClockForEnvironment() }
        .onChange(of: autoPlay) { refreshClockForEnvironment() }
        .onChange(of: freezeAt) { refreshClockForEnvironment() }
        .onChange(of: reduceMotion) { refreshClockForEnvironment() }
        .onDisappear { resumeTask?.cancel(); clockStopTask?.cancel() }
    }

    private func list(_ frame: PlanetExplorerCarouselFrame, scale s: CGFloat, size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(
                gradient: Gradient(stops: [
                    .init(color: Color(hex: 0x1F6DB3), location: 0.12),
                    .init(color: Color(hex: 0x185CA3), location: 0.35),
                    .init(color: Color(hex: 0x272D79), location: 0.8),
                    .init(color: Color(hex: 0x2D1E6B), location: 1),
                ]), startPoint: .top, endPoint: .bottom
            )
            .frame(width: size.width, height: size.height)

            Circle()
                .stroke(Color.white.opacity(0.22), style: StrokeStyle(lineWidth: s, dash: [3 * s, 6 * s]))
                .frame(width: 600 * s, height: 600 * s)
                .position(x: 201 * s, y: 560 * s)
                .rotationEffect(.degrees(reduceMotion ? 0 : Double((frame.orbit - 1) * -9)))

            Canvas { context, _ in
                let starShift = reduceMotion ? 0 : -(frame.position - 1) * 22
                for (x, y) in stars {
                    context.fill(
                        Path(ellipseIn: CGRect(x: (x + starShift - 1.4) * s, y: (y - 1.4) * s, width: 2.8 * s, height: 2.8 * s)),
                        with: .color(.white.opacity(0.75))
                    )
                }
                let dots: [(CGFloat, CGFloat, CGFloat, Color)] = [
                    (285, 86, 11, Color(hex: 0x2FD07A)), (307, 123, 9, Color(hex: 0xFFB12B)),
                    (85, 214, 9, Color(hex: 0x2F9BEA)), (0, 270, 9, Color(hex: 0xFFC21A)),
                    (335, 872, 12, Color(hex: 0x2F9BEA)),
                ]
                let dotShift = reduceMotion ? 0 : -(frame.position - 1) * 40
                for (x, y, radius, color) in dots {
                    context.fill(
                        Path(ellipseIn: CGRect(x: (x + dotShift - radius) * s, y: (y - radius) * s, width: radius * 2 * s, height: radius * 2 * s)),
                        with: .color(color)
                    )
                }
            }

            VStack(spacing: 6 * s) {
                Capsule().fill(.white).frame(width: 30 * s, height: 2.6 * s)
                Capsule().fill(.white).frame(width: 22 * s, height: 2.6 * s)
                Capsule().fill(.white).frame(width: 30 * s, height: 2.6 * s)
            }
            .position(x: 37 * s, y: 49.2 * s)
            .accessibilityLabel("Menu")

            Image(uiImage: PlanetExplorerCarouselAssets.image(named: "PlanetExplorerCarousel-avatar-1.png"))
                .resizable().scaledToFill()
                .frame(width: 40 * s, height: 40 * s).clipShape(Circle())
                .position(x: 356 * s, y: 54 * s)
                .accessibilityLabel("Your profile")

            ZStack(alignment: .topLeading) {
                Text("Space")
                    .font(.custom("Poppins-SemiBold", size: 60 * s)).tracking(-0.5 * s)
                    .position(x: 85 * s, y: 41 * s)
                Text("Explorer")
                    .font(.custom("Poppins-SemiBold", size: 50 * s)).tracking(-0.5 * s)
                    .scaleEffect(x: 1.067, anchor: .leading)
                    .position(x: 111 * s, y: 94 * s)
            }
            .foregroundStyle(.white)
            .frame(width: 260 * s, height: 140 * s, alignment: .topLeading)
            .offset(x: 96 * s, y: 90 * s)
            .accessibilityElement(children: .ignore).accessibilityLabel("Space Explorer")

            ForEach(planets.indices, id: \.self) { index in
                card(index, frame: frame, scale: s, width: size.width)
            }

            Rectangle()
                .fill(.clear)
                .contentShape(Rectangle())
                .frame(width: size.width, height: 396 * s)
                .offset(y: 288 * s)
                .gesture(carouselDrag(scale: s))
                .zIndex(9)

            exploreButton(frame, scale: s).position(x: 201 * s, y: 689 * s).zIndex(10)

            HStack(spacing: -11 * s) {
                ForEach(friends.indices, id: \.self) { index in
                    Image(uiImage: PlanetExplorerCarouselAssets.image(named: friends[index])).resizable().scaledToFill()
                        .frame(width: 37.5 * s, height: 37.5 * s).clipShape(Circle())
                        .zIndex(Double(4 - index))
                }
            }
            .position(x: 201 * s, y: 789 * s)

            Text("Joey, Linn and 8 friends are online")
                .font(.custom("Poppins-Regular", size: 12 * s))
                .foregroundStyle(Color(hex: 0xA9B0E0))
                .position(x: 201 * s, y: 822 * s)
        }
    }

    private func card(_ index: Int, frame: PlanetExplorerCarouselFrame, scale s: CGFloat, width: CGFloat) -> some View {
        let planet = planets[index]
        let positionAmount = amount(index, frame.position)
        let orbitAmount = amount(index, frame.orbit)
        let travel = clamp((frame.position - frame.orbit) * 8, -1, 1)
        let relative = CGFloat(index) - frame.position
        let ahead = relative == 0 ? 0 : (relative > 0 ? 1 : -1) * travel
        let bell = 4 * positionAmount * (1 - positionAmount)
        let outgoing = max(0, -ahead) * bell
        let incoming = max(0, ahead) * bell
        let frameScale = 1 - 0.12 * outgoing + 0.03 * incoming
        let cover = 1 - clamp((outgoing - 0.15) / 0.25, 0, 1)
        let side: CGFloat = CGFloat(index) - frame.orbit > 0 ? -1 : 1
        let lag = (frame.position - frame.orbit) * 234
        let planetLead = ahead > 0 ? -0.8 * ahead : 1 + 0.6 * max(0, -ahead)
        let planetSize = 120 + 80 * orbitAmount
        let planetX = 117 + side * 46 * (1 - orbitAmount) + lag * planetLead
        let planetY = lerp(434, 388, orbitAmount) - 395
        let swell = 1 + min(0.16, abs(frame.position - frame.orbit) * 3 * 0.16)
        let backgroundTop = lerp(25, 0, positionAmount)
        let backgroundHeight = lerp(235, 289, positionAmount)

        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 30 * s, style: .continuous)
                .fill(Color(hex: 0xB9B8F4).mixed(with: .white, amount: positionAmount))
                .frame(width: 234 * s, height: backgroundHeight * s).offset(y: backgroundTop * s)
            RoundedRectangle(cornerRadius: 30 * s, style: .continuous)
                .fill(Color(hex: 0x272D79))
                .frame(width: 234 * s, height: backgroundHeight * s).offset(y: backgroundTop * s)
                .opacity(0.28 * outgoing)
            Text(planet.order)
                .font(.custom("Poppins-SemiBold", size: 150 * s)).foregroundStyle(Color(hex: 0xE0E0E7))
                .frame(width: 190 * s, alignment: .trailing).offset(x: 26 * s, y: 87 * s)
                .opacity(positionAmount * cover)
            Text(planet.name)
                .font(.custom("Poppins-SemiBold", size: 34 * s))
                .foregroundStyle(Color(hex: 0x5D66B8).mixed(with: Color(hex: 0x11406E), amount: positionAmount))
                .lineLimit(1).frame(width: 186 * s, alignment: .leading)
                .offset(x: 26 * s, y: (100 + 20 * (1 - positionAmount)) * s).opacity(cover)
            Text(planet.short)
                .font(.custom("Poppins-Regular", size: 15 * s)).lineSpacing(9 * s)
                .foregroundStyle(Color(hex: 0x8D90CF).mixed(with: Color(hex: 0x747684), amount: positionAmount))
                .lineLimit(3).frame(width: 187 * s, alignment: .leading)
                .offset(x: 25 * s, y: (152 + 20 * (1 - positionAmount)) * s).opacity(cover)
            if !reduceTransparency {
                Circle()
                    .fill(RadialGradient(colors: [planet.glow.opacity(0.25), planet.glow.opacity(0)], center: .center, startRadius: 0, endRadius: 125 * planetSize / 200 * s))
                    .frame(width: 250 * planetSize / 200 * s, height: 250 * planetSize / 200 * s)
                    .scaleEffect(swell).position(x: planetX * s, y: planetY * s)
                    .opacity(frame.detail > 0 && frame.selected == index ? 0 : 1)
            }
            Image(uiImage: PlanetExplorerCarouselAssets.image(named: planet.image)).resizable().scaledToFit()
                .frame(width: planetSize * s, height: planetSize * s)
                .rotationEffect(.degrees(Double(24 * (CGFloat(index) - frame.orbit))))
                .position(x: planetX * s, y: planetY * s)
                .opacity(frame.detail > 0 && frame.selected == index ? 0 : 1)
        }
        .frame(width: 234 * s, height: 289 * s, alignment: .topLeading)
        .scaleEffect(frameScale)
        .position(x: width / 2 + (CGFloat(index) - frame.position) * 234 * s, y: (395 + 289 / 2) * s)
        .zIndex(Double((positionAmount * 10 + ahead * 6).rounded()))
        .accessibilityLabel("\(planet.name), planet \(planet.order)")
    }

    private func exploreButton(_ frame: PlanetExplorerCarouselFrame, scale s: CGFloat) -> some View {
        Button {
            let now = Date.timeIntervalSinceReferenceDate
            interrupt(at: now)
            motion.openDetail(at: now, reduced: reduceMotion)
            if reduceMotion { runClock(for: 0.21) }
            else { clockStopTask?.cancel(); clockActive = true }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            scheduleResume()
        } label: {
            ZStack {
                Circle().fill(.white).frame(width: 86 * s, height: 86 * s)
                Circle().fill(LinearGradient(colors: [Color(hex: 0xFFC21A), Color(hex: 0xF7A600)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 72 * s, height: 72 * s)
                Canvas { context, _ in
                    var path = Path()
                    path.move(to: CGPoint(x: 4 * s, y: 12 * s)); path.addLine(to: CGPoint(x: 19 * s, y: 12 * s))
                    path.move(to: CGPoint(x: 13 * s, y: 5.5 * s)); path.addLine(to: CGPoint(x: 19.5 * s, y: 12 * s)); path.addLine(to: CGPoint(x: 13 * s, y: 18.5 * s))
                    context.stroke(path, with: .color(.white), style: StrokeStyle(lineWidth: 2.6 * s, lineCap: .round, lineJoin: .round))
                }.frame(width: 24 * s, height: 24 * s)
            }
            .scaleEffect(frame.arrowScale)
        }
        .buttonStyle(PlanetExplorerCarouselPressStyle(scale: 0.97))
        .accessibilityLabel("Explore this planet")
    }

    private func detailBackdrop(_ frame: PlanetExplorerCarouselFrame, scale s: CGFloat, size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(gradient: Gradient(stops: [
                .init(color: Color(hex: 0xA29FB3), location: 0), .init(color: Color(hex: 0xC1BECE), location: 0.1),
                .init(color: Color(hex: 0xE3E2E8), location: 0.2), .init(color: .white, location: 0.3), .init(color: .white, location: 1),
            ]), startPoint: .leading, endPoint: .trailing)
            Circle().stroke(Color(hex: 0x11406E).opacity(0.14), style: StrokeStyle(lineWidth: s, dash: [3 * s, 6 * s]))
                .frame(width: 500 * s, height: 500 * s).position(x: 267 * s, y: 327 * s)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .opacity(window(frame.detail, 0.08, 0.82)).allowsHitTesting(false)
    }

    private func detailContent(_ frame: PlanetExplorerCarouselFrame, scale s: CGFloat) -> some View {
        let planet = planets[frame.selected]
        let numeral = stagger(frame.detail, 0.36, 0.7)
        let name = stagger(frame.detail, 0.44, 0.78)
        let desc = stagger(frame.detail, 0.52, 0.86)
        let video = stagger(frame.detail, 0.6, 0.94)
        return ZStack(alignment: .topLeading) {
            Rectangle().fill(Color(hex: 0x00A86B)).frame(width: 145 * s).offset(y: 745 * s)
                .frame(maxHeight: .infinity, alignment: .top).opacity(video).offset(y: reduceMotion ? 0 : 12 * (1 - video) * s)
            Text(planet.order).font(.custom("Poppins-SemiBold", size: 210 * s)).foregroundStyle(Color(hex: 0xE0E0E7))
                .offset(x: 36 * s, y: 222 * s).opacity(numeral).offset(y: reduceMotion ? 0 : 12 * (1 - numeral) * s)
            Group {
                Circle().fill(Color(hex: 0x2FB66A)).frame(width: 18 * s, height: 18 * s).position(x: 40 * s, y: 145 * s)
                Circle().fill(Color(hex: 0xFFC21A)).frame(width: 26 * s, height: 26 * s).position(x: 330 * s, y: 436 * s)
                Circle().fill(Color(hex: 0x2A6FE0)).frame(width: 18 * s, height: 18 * s).position(x: 220 * s, y: 589 * s)
            }.opacity(desc).offset(y: reduceMotion ? 0 : 12 * (1 - desc) * s)
            Text(planet.name).font(.custom("Poppins-SemiBold", size: 48 * s)).foregroundStyle(Color(hex: 0x11406E))
                .offset(x: 40 * s, y: 414 * s).opacity(name).offset(y: reduceMotion ? 0 : 12 * (1 - name) * s)
            accentedDescription(planet, scale: s).lineSpacing(11.5 * s)
                .frame(width: 326 * s, alignment: .leading).offset(x: 40 * s, y: 479 * s)
                .opacity(desc).offset(y: reduceMotion ? 0 : 12 * (1 - desc) * s)
            ZStack {
                Image(uiImage: PlanetExplorerCarouselAssets.image(named: "PlanetExplorerCarousel-earth-video.jpg"))
                    .resizable().scaledToFill().frame(width: 332 * s, height: 204 * s).clipped()
                Circle().fill(Color.black.opacity(0.55)).frame(width: 66 * s, height: 66 * s)
                Canvas { context, _ in
                    var path = Path(); path.move(to: CGPoint(x: 6.5 * s, y: 4.5 * s)); path.addLine(to: CGPoint(x: 15.5 * s, y: 10 * s)); path.addLine(to: CGPoint(x: 6.5 * s, y: 15.5 * s)); path.closeSubpath(); context.fill(path, with: .color(.white))
                }.frame(width: 20 * s, height: 20 * s)
            }
            .frame(width: 332 * s, height: 204 * s).clipShape(RoundedRectangle(cornerRadius: 30 * s, style: .continuous))
            .offset(x: 35 * s, y: 631 * s).opacity(video).offset(y: reduceMotion ? 0 : 12 * (1 - video) * s)
        }
        .allowsHitTesting(false)
    }

    private func accentedDescription(_ planet: PlanetExplorerCarouselPlanet, scale s: CGFloat) -> Text {
        let parts = planet.long.components(separatedBy: planet.accent)
        let before = parts.first ?? planet.long
        let after = parts.count > 1 ? parts[1] : ""
        return Text(before).font(.custom("Poppins-Regular", size: 14.5 * s)).foregroundStyle(Color(hex: 0x747684))
        + Text(planet.accent).font(.custom("Poppins-SemiBold", size: 14.5 * s)).foregroundStyle(Color(hex: 0x22A866))
        + Text(after).font(.custom("Poppins-Regular", size: 14.5 * s)).foregroundStyle(Color(hex: 0x747684))
    }

    private func detailHero(_ frame: PlanetExplorerCarouselFrame, scale s: CGFloat) -> some View {
        let progress = reduceMotion ? 1 : easeInOut(window(frame.detail, 0, 0.62))
        let opacity = reduceMotion ? window(frame.detail, 0.3, 0.7) : (frame.detail > 0 ? 1 : 0)
        let float = reduceMotion ? 0 : sin(frame.clock / 4 * .pi * 2) * 3 * window(frame.detail, 0.9, 1)
        let size = lerp(200, 366, progress)
        return ZStack {
            if !reduceTransparency {
                Circle().fill(RadialGradient(colors: [planets[frame.selected].glow.opacity(0.25), planets[frame.selected].glow.opacity(0)], center: .center, startRadius: 0, endRadius: 125 * size / 200 * s))
                    .frame(width: 250 * size / 200 * s, height: 250 * size / 200 * s)
            }
                Image(uiImage: PlanetExplorerCarouselAssets.image(named: planets[frame.selected].image))
                    .resizable().scaledToFit().frame(width: size * s, height: size * s)
        }
        .position(x: lerp(201, 257, progress) * s, y: (lerp(388, 147, progress) + float) * s)
        .opacity(opacity).allowsHitTesting(false)
    }

    private func detailRocket(_ frame: PlanetExplorerCarouselFrame, scale s: CGFloat) -> some View {
        let progress = stagger(frame.detail, 0.55, 1)
        let bob = reduceMotion ? 0 : sin(frame.clock / 4 * .pi * 2 + 1) * 5 * window(frame.detail, 0.95, 1)
        return Image(uiImage: PlanetExplorerCarouselAssets.image(named: "PlanetExplorerCarousel-rocket.png"))
            .resizable().scaledToFit().frame(width: 138 * s, height: 138 * s)
            .offset(x: -70 * (1 - progress) * s, y: (90 * (1 - progress) + bob) * s)
            .rotationEffect(.degrees(Double(-18 * (1 - progress)))).position(x: 243 * s, y: 321 * s)
            .opacity(progress).allowsHitTesting(false)
    }

    private func backButton(_ frame: PlanetExplorerCarouselFrame, scale s: CGFloat, now: TimeInterval) -> some View {
        let visible = stagger(frame.detail, 0.3, 0.6)
        return Button {
            interrupt(at: now); motion.closeDetail(at: now, reduced: reduceMotion)
            runClock(for: reduceMotion ? 0.21 : 0.65)
            scheduleResume()
        } label: {
            Canvas { context, _ in
                var path = Path(); path.move(to: CGPoint(x: 22 * s, y: 13 * s)); path.addLine(to: CGPoint(x: 4.5 * s, y: 13 * s)); path.move(to: CGPoint(x: 11 * s, y: 6 * s)); path.addLine(to: CGPoint(x: 4 * s, y: 13 * s)); path.addLine(to: CGPoint(x: 11 * s, y: 20 * s))
                context.stroke(path, with: .color(Color(hex: 0x11406E)), style: StrokeStyle(lineWidth: 2.4 * s, lineCap: .round, lineJoin: .round))
            }
            .frame(width: 26 * s, height: 26 * s).frame(width: 44 * s, height: 44 * s).scaleEffect(frame.backScale)
        }
        .buttonStyle(PlanetExplorerCarouselPressStyle(scale: 0.97)).position(x: 48 * s, y: 59 * s)
        .opacity(visible).offset(y: reduceMotion ? 0 : 12 * (1 - visible) * s)
        .allowsHitTesting(frame.detail > 0.5).accessibilityLabel("Back")
    }

    private func carouselDrag(scale: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 10)
            .onChanged { value in
                let now = Date.timeIntervalSinceReferenceDate
                if dragOrigin == nil {
                    interrupt(at: now)
                    dragOrigin = motion.frame(at: now, autoPlay: autoPlay, reduced: reduceMotion).position
                    if !reduceMotion { clockStopTask?.cancel(); clockActive = true }
                }
                guard let origin = dragOrigin else { return }
                let raw = origin - value.translation.width / (234 * scale)
                motion.drag(to: raw < 0 ? raw * 0.3 : raw > 2 ? 2 + (raw - 2) * 0.3 : raw, at: now, reduced: reduceMotion)
                redrawToken += 1
            }
            .onEnded { value in
                let now = Date.timeIntervalSinceReferenceDate
                let current = motion.frame(at: now, autoPlay: autoPlay, reduced: reduceMotion).position
                let velocity = value.velocity.width / (234 * scale)
                let target = clamp((current - velocity * 0.2).rounded(), 0, 2)
                motion.snap(to: target, velocity: -velocity, at: now, reduced: reduceMotion)
                if let origin = dragOrigin, target != origin.rounded() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                dragOrigin = nil
                if !reduceMotion { runClock(for: 0.7) }
                scheduleResume(); redrawToken += 1
            }
    }

    private func interrupt(at now: TimeInterval) {
        resumeTask?.cancel(); motion.interrupt(at: now, autoPlay: autoPlay, reduced: reduceMotion); redrawToken += 1
    }

    private func scheduleResume() {
        guard autoPlay, freezeAt == nil else { return }
        resumeTask?.cancel()
        resumeTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(3500))
            guard !Task.isCancelled else { return }
            motion.returnToRest(at: Date.timeIntervalSinceReferenceDate, reduced: reduceMotion)
            runClock(for: reduceMotion ? 0.21 : 0.65)
            redrawToken += 1
        }
    }

    private func refreshClockForEnvironment() {
        let now = Date.timeIntervalSinceReferenceDate
        if let freezeAt { _ = motion.frozenFrame(milliseconds: freezeAt, reduced: reduceMotion) }
        else { _ = motion.frame(at: now, autoPlay: autoPlay, reduced: reduceMotion) }
        clockStopTask?.cancel()
        clockActive = freezeAt == nil && ((autoPlay && !reduceMotion) || (!reduceMotion && motion.detailVisible(at: now)))
        redrawToken += 1
    }

    private func runClock(for seconds: TimeInterval) {
        clockStopTask?.cancel()
        guard freezeAt == nil else { clockActive = false; return }
        clockActive = true
        guard !(autoPlay && !reduceMotion) else { return }
        clockStopTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            clockActive = !reduceMotion && motion.detailVisible(at: Date.timeIntervalSinceReferenceDate)
            redrawToken += 1
        }
    }
}

private struct PlanetExplorerCarouselPlanet {
    let name: String; let order: String; let short: String; let long: String; let accent: String; let image: String; let glow: Color
    static let all = [
        Self(name: "Mars", order: "4", short: "The fourth planet from the Sun, a dusty, cold, desert world..", long: "The fourth planet from the Sun is a dusty, cold desert world with a very thin atmosphere and the tallest volcano we know.", accent: "the Sun", image: "PlanetExplorerCarousel-mars.png", glow: Color(hex: 0xFF8A4C)),
        Self(name: "Earth", order: "3", short: "The third planet from the Sun and the only astronomical..", long: "The third planet from the Sun and the only astronomical object known to harbor and support life. 70.8% covered with water.", accent: "the Sun", image: "PlanetExplorerCarousel-earth.png", glow: Color(hex: 0x5FC6FF)),
        Self(name: "Venus", order: "2", short: "The second planet from the Sun and our closest planetary neighbour..", long: "The second planet from the Sun and our closest planetary neighbour, wrapped in thick clouds that trap its heat.", accent: "the Sun", image: "PlanetExplorerCarousel-venus.png", glow: Color(hex: 0xCFEAFF)),
    ]
}

private struct PlanetExplorerCarouselFrame {
    var position: CGFloat = 1; var orbit: CGFloat = 1; var detail: CGFloat = 0; var selected = 1
    var arrowScale: CGFloat = 1; var backScale: CGFloat = 1; var clock: TimeInterval = 0
}

@MainActor
private final class PlanetExplorerCarouselMotion {
    private enum Mode { case initial, autoplay, manual, returning }
    private var mode = Mode.initial
    private var loopStart: TimeInterval = 0; private var returnStart: TimeInterval = 0
    private var position = PlanetExplorerCarouselDriven(1); private var detail = PlanetExplorerCarouselDriven(0)
    private var orbit: CGFloat = 1; private var orbitVelocity: CGFloat = 0; private var orbitTime: TimeInterval = 0
    private var selected = 1
    private var pausedForReducedMotion = false

    func frame(at now: TimeInterval, autoPlay: Bool, reduced: Bool) -> PlanetExplorerCarouselFrame {
        if mode == .initial { mode = autoPlay && !reduced ? .autoplay : .manual; loopStart = now; orbitTime = now }
        if reduced, mode == .autoplay {
            mode = .manual; pausedForReducedMotion = true
            position.set(1, at: now); detail.set(0, at: now); orbit = 1; orbitVelocity = 0
        } else if !reduced, autoPlay, pausedForReducedMotion {
            pausedForReducedMotion = false; mode = .autoplay; loopStart = now; orbit = 1; orbitVelocity = 0; orbitTime = now
        }
        switch mode {
        case .autoplay:
            let clock = max(0, now - loopStart).truncatingRemainder(dividingBy: PlanetExplorerCarousel.loop)
            let sample = Self.sample(clock); orbit = Self.autoplayOrbit(clock); selected = 1
            return .init(position: sample.position, orbit: orbit, detail: sample.detail, selected: 1, arrowScale: sample.arrowPressed ? 0.97 : 1, backScale: sample.backPressed ? 0.97 : 1, clock: clock)
        case .returning:
            let p = position.value(at: now); let d = detail.value(at: now); stepOrbit(target: p, now: now, reduced: reduced)
            if now - returnStart >= 0.5 {
                mode = autoPlay && !reduced ? .autoplay : .manual; loopStart = now; orbit = 1; orbitVelocity = 0; orbitTime = now
                if mode == .autoplay { return frame(at: now, autoPlay: autoPlay, reduced: reduced) }
            }
            return .init(position: p, orbit: orbit, detail: d, selected: selected, clock: now)
        case .initial, .manual:
            let p = position.value(at: now); let d = detail.value(at: now); stepOrbit(target: p, now: now, reduced: reduced)
            return .init(position: p, orbit: orbit, detail: d, selected: selected, clock: now)
        }
    }

    func interrupt(at now: TimeInterval, autoPlay: Bool, reduced: Bool) {
        let current = frame(at: now, autoPlay: autoPlay, reduced: reduced); mode = .manual
        position.set(current.position, at: now); detail.set(current.detail, at: now); orbit = current.orbit; orbitTime = now
    }
    func drag(to value: CGFloat, at now: TimeInterval, reduced: Bool) {
        position.set(value, at: now); if reduced { orbit = value; orbitVelocity = 0; orbitTime = now }
    }
    func snap(to target: CGFloat, velocity: CGFloat, at now: TimeInterval, reduced: Bool) {
        if reduced { position.set(target, at: now); orbit = target; orbitVelocity = 0; orbitTime = now }
        else { position.spring(to: target, duration: 0.4, dampingRatio: 0.8, velocity: velocity, at: now) }
    }
    func openDetail(at now: TimeInterval, reduced: Bool) {
        let current = position.value(at: now); selected = Int(clamp(current.rounded(), 0, 2))
        if reduced { position.set(CGFloat(selected), at: now) } else { position.spring(to: CGFloat(selected), duration: 0.4, dampingRatio: 0.8, velocity: 0, at: now) }
        detail.time(to: 1, duration: reduced ? 0.2 : 0.9 * Double(1 - detail.value(at: now)), curve: .quadratic, at: now)
    }
    func closeDetail(at now: TimeInterval, reduced: Bool) {
        let current = detail.value(at: now); detail.time(to: 0, duration: reduced ? 0.2 : 0.6 * Double(current), curve: .quadratic, at: now)
    }
    func returnToRest(at now: TimeInterval, reduced: Bool) {
        mode = .returning; returnStart = now; position.time(to: 1, duration: reduced ? 0 : 0.5, curve: .easeOut, at: now)
        let current = detail.value(at: now); detail.time(to: 0, duration: reduced ? 0.2 : 0.6 * Double(current), curve: .quadratic, at: now)
    }
    func frozenFrame(milliseconds: TimeInterval, reduced: Bool) -> PlanetExplorerCarouselFrame {
        let period = PlanetExplorerCarousel.loop * 1_000
        let wrapped = ((milliseconds.truncatingRemainder(dividingBy: period)) + period).truncatingRemainder(dividingBy: period)
        let clock = wrapped / 1_000
        let frozen = Self.sample(clock)
        return PlanetExplorerCarouselFrame(
            position: frozen.position,
            orbit: Self.frozenOrbit(milliseconds: milliseconds, reduced: reduced),
            detail: frozen.detail,
            selected: 1,
            arrowScale: frozen.arrowPressed ? 0.97 : 1,
            backScale: frozen.backPressed ? 0.97 : 1,
            clock: clock
        )
    }
    func detailVisible(at now: TimeInterval) -> Bool { detail.value(at: now) > 0.001 }
    private func stepOrbit(target: CGFloat, now: TimeInterval, reduced: Bool) {
        if reduced { orbit = target; orbitVelocity = 0; orbitTime = now; return }
        if orbitTime <= 0 || now < orbitTime || now - orbitTime > 1 { orbit = target; orbitVelocity = 0; orbitTime = now; return }
        while orbitTime < now {
            let dt = min(0.008, now - orbitTime); let acceleration = 220 * (target - orbit) - 22 * orbitVelocity
            orbitVelocity += acceleration * dt; orbit += orbitVelocity * dt; orbitTime += dt
        }
    }
    private static func sample(_ time: TimeInterval) -> (position: CGFloat, detail: CGFloat, arrowPressed: Bool, backPressed: Bool) {
        let t = time.truncatingRemainder(dividingBy: PlanetExplorerCarousel.loop)
        let position = 1 + easeInOut(window(t, 1.2, 1.8)) - easeInOut(window(t, 2.6, 3.2))
        let detail: CGFloat = t >= 3.72 && t < 8.42 ? window(t, 3.72, 4.62) : (t >= 8.42 ? 1 - window(t, 8.42, 9.02) : 0)
        return (position, detail, t >= 3.6 && t < 3.74, t >= 8.3 && t < 8.44)
    }
    private static func autoplayOrbit(_ time: TimeInterval) -> CGFloat {
        var x: CGFloat = 1; var velocity: CGFloat = 0; var elapsed: TimeInterval = 0
        while elapsed < time {
            let dt = min(0.008, time - elapsed); let target = sample(elapsed + dt).position
            let acceleration = 220 * (target - x) - 22 * velocity; velocity += acceleration * dt; x += velocity * dt; elapsed += dt
        }
        return x
    }
    private static func frozenOrbit(milliseconds: TimeInterval, reduced: Bool) -> CGFloat {
        var x: CGFloat = 1
        var velocity: CGFloat = 0
        var last: TimeInterval = -1
        var sampleTime: TimeInterval = 0
        let outerStep = 1_000 / 120.0
        while sampleTime <= milliseconds {
            let target = sample(sampleTime / 1_000).position
            if reduced {
                x = target; velocity = 0; last = sampleTime
            } else if last < 0 || sampleTime < last || sampleTime - last > 1_000 {
                x = target; velocity = 0; last = sampleTime
            } else {
                while last < sampleTime {
                    let stepMilliseconds = min(8, sampleTime - last)
                    let dt = stepMilliseconds / 1_000
                    let acceleration = 220 * (target - x) - 22 * velocity
                    velocity += acceleration * dt
                    x += velocity * dt
                    last += stepMilliseconds
                }
            }
            sampleTime += outerStep
        }
        return x
    }
}

private struct PlanetExplorerCarouselDriven {
    enum Curve { case quadratic, easeOut }; private enum Mode { case still, timing, spring }
    private var mode = Mode.still; private var stored: CGFloat; private var from: CGFloat; private var target: CGFloat
    private var start: TimeInterval = 0; private var duration: TimeInterval = 0; private var curve = Curve.quadratic
    private var initialVelocity: CGFloat = 0; private var dampingRatio: CGFloat = 0.8; private var stiffness: CGFloat = 0
    init(_ value: CGFloat) { stored = value; from = value; target = value }
    mutating func set(_ value: CGFloat, at now: TimeInterval) { stored = value; from = value; target = value; start = now; mode = .still }
    mutating func time(to value: CGFloat, duration: TimeInterval, curve: Curve, at now: TimeInterval) {
        let current = self.value(at: now); guard duration > 0 else { set(value, at: now); return }
        from = current; stored = current; target = value; start = now; self.duration = duration; self.curve = curve; mode = .timing
    }
    mutating func spring(to value: CGFloat, duration: TimeInterval, dampingRatio: CGFloat, velocity: CGFloat, at now: TimeInterval) {
        let current = self.value(at: now); from = current; stored = current; target = value; start = now; self.duration = duration
        initialVelocity = velocity; self.dampingRatio = dampingRatio
        stiffness = Self.stiffnessFor(displacement: current - value, velocity: velocity, duration: duration, dampingRatio: dampingRatio)
        mode = stiffness == 0 ? .still : .spring
    }
    mutating func value(at now: TimeInterval) -> CGFloat {
        switch mode {
        case .still: return stored
        case .timing:
            let progress = clamp(CGFloat((now - start) / max(duration, 0.0001)), 0, 1)
            stored = lerp(from, target, curve == .quadratic ? quadratic(progress) : easeOut(progress)); if progress >= 1 { mode = .still }; return stored
        case .spring:
            let t = CGFloat(max(0, now - start)); let omega0 = sqrt(stiffness); let omega1 = omega0 * sqrt(max(0, 1 - dampingRatio * dampingRatio)); let x0 = from - target
            let envelope = exp(-dampingRatio * omega0 * t)
            let displacement = omega1 > 0 ? envelope * (x0 * cos(omega1 * t) + (initialVelocity + dampingRatio * omega0 * x0) / omega1 * sin(omega1 * t)) : envelope * (x0 + (initialVelocity + omega0 * x0) * t)
            stored = target + displacement; if now - start >= duration * 1.5 { set(target, at: now) }; return stored
        }
    }
    private static func stiffnessFor(displacement: CGFloat, velocity: CGFloat, duration: TimeInterval, dampingRatio: CGFloat) -> CGFloat {
        guard displacement != 0 || velocity != 0 else { return 0 }; let settling = duration * 1.5
        func difference(_ stiffness: Double) -> Double {
            let omega = sqrt(stiffness) * Double(dampingRatio); let x0 = Double(displacement); let v0 = Double(velocity)
            let a = x0 + (v0 + x0 * omega) * settling; let envelope = exp(-omega * settling); let x = a * envelope; let v = a * envelope * -omega + (v0 + x0 * omega) * envelope
            return (0.5 * stiffness * x * x + 0.5 * v * v) / (0.5 * stiffness * x0 * x0 + 0.5 * v0 * v0) - 6e-9
        }
        var low = Double.ulpOfOne; var high = 8_000.0; let direction = difference(high) >= difference(low) ? 1.0 : -1.0; var current = (low + high) / 2
        for _ in 0..<100 { if abs(difference(current)) <= 6e-12 { break }; if difference(current) * direction < 0 { low = current } else { high = current }; current = (low + high) / 2 }
        return CGFloat(current)
    }
}

private struct PlanetExplorerCarouselPressStyle: ButtonStyle {
    let scale: CGFloat
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? 0.12 : 0.15), value: configuration.isPressed)
    }
}

private enum PlanetExplorerCarouselAssets {
    private static let images: [String: UIImage] = Dictionary(
        uniqueKeysWithValues: [
            "PlanetExplorerCarousel-avatar-1.png",
            "PlanetExplorerCarousel-avatar-2.png",
            "PlanetExplorerCarousel-avatar-3.png",
            "PlanetExplorerCarousel-avatar-4.png",
            "PlanetExplorerCarousel-avatar-5.png",
            "PlanetExplorerCarousel-earth-video.jpg",
            "PlanetExplorerCarousel-earth.png",
            "PlanetExplorerCarousel-mars.png",
            "PlanetExplorerCarousel-rocket.png",
            "PlanetExplorerCarousel-venus.png",
        ].map { name in
            guard let image = UIImage(named: name) else {
                assertionFailure("Missing Planet Explorer Carousel image: \(name)")
                return (name, UIImage())
            }
            return (name, image)
        }
    )

    static func image(named name: String) -> UIImage {
        guard let image = images[name] else {
            assertionFailure("Unknown Planet Explorer Carousel image: \(name)")
            return UIImage()
        }
        return image
    }
}

private enum PlanetExplorerCarouselFonts {
    static let registered: Bool = {
        for name in ["PlanetExplorerCarousel-Poppins-Regular", "PlanetExplorerCarousel-Poppins-Medium", "PlanetExplorerCarousel-Poppins-SemiBold"] {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else { continue }; CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        return true
    }()
}

private func amount(_ index: Int, _ position: CGFloat) -> CGFloat { clamp(1 - abs(CGFloat(index) - position), 0, 1) }
private func clamp<T: Comparable>(_ value: T, _ low: T, _ high: T) -> T { min(high, max(low, value)) }
private func lerp(_ from: CGFloat, _ to: CGFloat, _ amount: CGFloat) -> CGFloat { from + (to - from) * amount }
private func window<T: BinaryFloatingPoint>(_ value: T, _ start: T, _ end: T) -> T { min(1, max(0, (value - start) / (end - start))) }
private func quadratic(_ value: CGFloat) -> CGFloat { value < 0.5 ? 2 * value * value : 1 - 2 * (1 - value) * (1 - value) }
private func stagger(_ detail: CGFloat, _ start: CGFloat, _ end: CGFloat) -> CGFloat { easeOut(window(detail, start, end)) }
private func easeOut<T: BinaryFloatingPoint>(_ value: T) -> T { cubicBezier(value, 0.23, 1, 0.32, 1) }
private func easeInOut<T: BinaryFloatingPoint>(_ value: T) -> T { cubicBezier(value, 0.77, 0, 0.175, 1) }
private func cubicBezier<T: BinaryFloatingPoint>(_ value: T, _ x1: T, _ y1: T, _ x2: T, _ y2: T) -> T {
    let x = min(1, max(0, value)); var low: T = 0; var high: T = 1
    for _ in 0..<16 { let t = (low + high) / 2; let estimate = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t; if estimate < x { low = t } else { high = t } }
    let t = (low + high) / 2; return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
}

private extension Color {
    init(hex: UInt32) { self.init(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1) }
    func mixed(with other: Color, amount: CGFloat) -> Color {
        let lhs = UIColor(self).resolvedColor(with: UITraitCollection(userInterfaceStyle: .light)); let rhs = UIColor(other).resolvedColor(with: UITraitCollection(userInterfaceStyle: .light))
        var lr: CGFloat = 0; var lg: CGFloat = 0; var lb: CGFloat = 0; var la: CGFloat = 0; var rr: CGFloat = 0; var rg: CGFloat = 0; var rb: CGFloat = 0; var ra: CGFloat = 0
        lhs.getRed(&lr, green: &lg, blue: &lb, alpha: &la); rhs.getRed(&rr, green: &rg, blue: &rb, alpha: &ra); let t = clamp(amount, 0, 1)
        return Color(red: Double(lerp(lr, rr, t)), green: Double(lerp(lg, rg, t)), blue: Double(lerp(lb, rb, t)), opacity: Double(lerp(la, ra, t)))
    }
}
