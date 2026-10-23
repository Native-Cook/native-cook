import SwiftUI
import UIKit

/// A clothbound dream journal that opens at its spine and becomes a writing page.
struct DreambookHingeJournal: View {
    static let loop: Double = 8
    var autoPlay = false
    /// Freezes one deterministic phase for screenshots and port comparison.
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date()
    @State private var lastTouch = Date.distantPast
    @State private var transitionStarted = Date.distantPast
    @State private var transitionFrom = DreambookHingeJournalPose.rest
    @State private var transitionTo = DreambookHingeJournalPose.rest
    @State private var heldPose = DreambookHingeJournalPose.rest
    @State private var dragStart = DreambookHingeJournalPose.rest
    @State private var dragging = false
    @State private var manualLive = false
    @State private var releaseVelocity = 0.0
    @State private var lastHaptic = Date.distantPast
    @State private var crossedOpen = false
    @State private var reflected = false

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 393, geometry.size.height / 852)
            ZStack {
                DreambookHingeJournalStyle.screen.ignoresSafeArea()
                canonicalScreen
                    .frame(width: 393, height: 852, alignment: .topLeading)
                    .scaleEffect(scale)
                    .frame(width: geometry.size.width, height: geometry.size.height)
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .task(id: lastTouch) {
            guard manualLive else { return }
            try? await Task.sleep(for: .seconds(2.65))
            guard !Task.isCancelled, !dragging else { return }
            manualLive = false
        }
    }

    private var canonicalScreen: some View {
        ZStack(alignment: .topLeading) {
            background
            TimelineView(.animation(minimumInterval: nil, paused: timelinePaused)) { timeline in
                let pose = currentPose(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    header(pose)
                    projectedBook(pose)
                    calendar(pose)
                    footer(pose)
                    interactionLayer(pose)
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
            }
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .coordinateSpace(name: "DreambookHingeJournalStage")
        .clipped()
    }

    private var timelinePaused: Bool {
        reduceMotion || previewTime != nil || (!autoPlay && !manualLive)
    }

    private var background: some View {
        ZStack {
            DreambookHingeJournalStyle.screen
            LinearGradient(colors: [.clear, .clear, DreambookHingeJournalStyle.color(0x242225).opacity(0.65)],
                           startPoint: .top, endPoint: .bottom)
        }
        .frame(width: 393, height: 852)
        .accessibilityHidden(true)
    }

    private func header(_ pose: DreambookHingeJournalPose) -> some View {
        ZStack(alignment: .topLeading) {
            Text("Thursday, 15 October").font(.system(size: 16, weight: .regular, design: .serif))
                .foregroundStyle(DreambookHingeJournalStyle.color(0xA5A1A5)).position(x: 196.5, y: 72)
            Image(systemName: "slider.horizontal.3").font(.system(size: 17, weight: .ultraLight))
                .foregroundStyle(DreambookHingeJournalStyle.color(0xA5A1A5)).position(x: 365, y: 72)
            Text("Give your dreams\na place to stay.")
                .font(.system(size: 31, weight: .regular, design: .serif)).lineSpacing(-1)
                .multilineTextAlignment(.center).foregroundStyle(DreambookHingeJournalStyle.color(0xDEDCDE))
                .frame(width: 345, height: 80).position(x: 196.5, y: 142)
                .opacity(Self.ease((pose.header - 0.45) / 0.55))
            Text("Pages from the night").font(.system(size: 26, weight: .regular, design: .serif))
                .foregroundStyle(DreambookHingeJournalStyle.color(0xDEDCDE))
                .frame(width: 345, height: 40).position(x: 196.5, y: 121)
                .opacity(1 - Self.ease(pose.header / 0.55))
        }
        .frame(width: 393, height: 205, alignment: .topLeading)
        .opacity(1 - pose.fullscreen)
        .accessibilityHidden(pose.fullscreen > 0.5)
    }

    private func projectedBook(_ pose: DreambookHingeJournalPose) -> some View {
        let mesh = DreambookHingeJournalMesh(pose)
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                if mesh.paperAlpha > 0.001 {
                    let outline = DreambookHingeJournalMesh.path(mesh.paper)
                    var ambient = context
                    ambient.addFilter(.blur(radius: 26))
                    ambient.translateBy(x: 0, y: 16)
                    ambient.fill(outline, with: .color(.black.opacity(0.20 * mesh.paperAlpha)))
                    var contact = context
                    contact.addFilter(.blur(radius: 3))
                    contact.translateBy(x: 0, y: 2)
                    contact.fill(outline, with: .color(.black.opacity(0.22 * mesh.paperAlpha)))
                    if pose.fullscreen < 1 {
                        for index in (1...5).reversed() {
                            var layer = context
                            layer.translateBy(x: Double(index) * 0.38, y: Double(index) * 0.45)
                            layer.fill(outline, with: .color(DreambookHingeJournalStyle.color(index.isMultiple(of: 2) ? 0x68656A : 0x79767A)
                                .opacity((1 - pose.fullscreen) * mesh.paperAlpha)))
                        }
                    }
                }
                if mesh.coverAlpha > 0.001 {
                    let outline = DreambookHingeJournalMesh.path(mesh.cover)
                    var shadow = context
                    shadow.addFilter(.blur(radius: 20))
                    shadow.translateBy(x: 0, y: 12)
                    shadow.fill(outline, with: .color(.black.opacity(0.25 * mesh.coverAlpha)))
                }
            }
            .frame(width: 393, height: 852)
            if mesh.reverseAlpha > 0.001 {
                Image(uiImage: DreambookHingeJournalCoverCache.back)
                    .resizable().interpolation(.high)
                    .frame(width: 253, height: 352)
                    .projectionEffect(DreambookHingeJournalMesh.projection(width: 253, height: 352, quad: mesh.reverse))
                    .frame(width: 393, height: 852, alignment: .topLeading)
                    .opacity(mesh.reverseAlpha)
            }
            paperPanel(pose, mesh: mesh, lower: false)
            paperPanel(pose, mesh: mesh, lower: true)
            Canvas { context, _ in
                var crease = Path()
                crease.move(to: mesh.paper[5]); crease.addLine(to: mesh.paper[2])
                context.stroke(crease, with: .color(DreambookHingeJournalStyle.color(0x8E8990)
                    .opacity((0.16 + 0.22 * sin(.pi * min(1, max(0, pose.openness)))) * mesh.paperAlpha * (1 - pose.fullscreen))), lineWidth: 0.6 + 1.8 * sin(.pi * min(1, max(0, pose.openness))))
            }
            .frame(width: 393, height: 852)
            if mesh.coverAlpha > 0.001 {
                coverFace(pose, mesh: mesh)
            }
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private func journalFace(_ pose: DreambookHingeJournalPose, ink: Double) -> some View {
        let rect = pose.pageFrame
        let corner = 17 + 7 * pose.fullscreen
        return DreambookHingeJournalPage(pose: pose, reflected: reflected, ink: ink)
            .frame(width: rect.width, height: rect.height, alignment: .topLeading)
            .background {
                LinearGradient(colors: [DreambookHingeJournalStyle.color(0xC7C4C7),
                                        DreambookHingeJournalStyle.color(0xBDBABD),
                                        DreambookHingeJournalStyle.color(0xC6C3C6)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }
            .overlay {
                RoundedRectangle(cornerRadius: corner)
                    .stroke(Color.white.opacity(0.35), lineWidth: 0.65)
            }
            .clipShape(RoundedRectangle(cornerRadius: corner))
    }

    private func paperPanel(_ pose: DreambookHingeJournalPose, mesh: DreambookHingeJournalMesh, lower: Bool) -> some View {
        let rect = pose.pageFrame
        let half = rect.height * 0.5
        let quad = lower ? [mesh.paper[5], mesh.paper[2], mesh.paper[3], mesh.paper[4]]
                         : [mesh.paper[0], mesh.paper[1], mesh.paper[2], mesh.paper[5]]
        return journalFace(pose, ink: mesh.ink)
            .offset(y: lower ? -half : 0)
            .frame(width: rect.width, height: half, alignment: .topLeading)
            .clipped()
            .projectionEffect(DreambookHingeJournalMesh.projection(width: rect.width, height: half, quad: quad))
            .frame(width: 393, height: 852, alignment: .topLeading)
            .opacity(mesh.paperAlpha)
    }

    private func coverFace(_ pose: DreambookHingeJournalPose, mesh: DreambookHingeJournalMesh) -> some View {
        Image(uiImage: DreambookHingeJournalCoverCache.front)
            .resizable().interpolation(.high)
            .frame(width: 253, height: 352)
            .overlay {
                LinearGradient(colors: [.clear, .white.opacity(0.10), .clear], startPoint: .leading, endPoint: .trailing)
                    .frame(width: 105, height: 352)
                    .offset(x: 180 * pose.openness - 68)
                    .opacity(max(0, sin(pose.openness * .pi)))
            }
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 2, bottomLeadingRadius: 2,
                                             bottomTrailingRadius: 17, topTrailingRadius: 17))
            .projectionEffect(DreambookHingeJournalMesh.projection(width: 253, height: 352, quad: mesh.cover))
            .frame(width: 393, height: 852, alignment: .topLeading)
            .opacity(mesh.coverAlpha)
    }

    private func calendar(_ pose: DreambookHingeJournalPose) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 27).fill(DreambookHingeJournalStyle.color(0x454246))
                .overlay(RoundedRectangle(cornerRadius: 27).stroke(Color.white.opacity(0.045), lineWidth: 1))
            Capsule().fill(Color.white.opacity(0.25)).frame(width: 42, height: 3).offset(x: 165.5, y: 9)
            Text("October 2026 ›").font(.system(size: 15, weight: .medium, design: .serif))
                .foregroundStyle(DreambookHingeJournalStyle.color(0xCDC9CD)).offset(x: 21, y: 24)
            Image(systemName: "circle.inset.filled").font(.system(size: 16, weight: .ultraLight))
                .foregroundStyle(DreambookHingeJournalStyle.color(0xCDC9CD)).position(x: 338, y: 34)
            HStack(spacing: 0) {
                ForEach(["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"], id: \.self) { day in
                    Text(day).font(.system(size: 10, weight: .regular, design: .serif))
                        .foregroundStyle(DreambookHingeJournalStyle.color(0xABA7AB)).frame(width: 49, height: 17)
                }
            }.offset(x: 15, y: 63)
            HStack(spacing: 0) {
                ForEach(12..<19) { day in
                    Text("\(day)").font(.system(size: 14, weight: day == 15 ? .medium : .regular, design: .serif))
                        .foregroundStyle(day == 15 ? DreambookHingeJournalStyle.color(0xD8D4D8) : DreambookHingeJournalStyle.color(0x8C888D))
                        .frame(width: 49, height: 28)
                        .background {
                            if day == 15 { Circle().fill(Color.white.opacity(0.08)).frame(width: 26, height: 26) }
                        }
                }
            }
            .offset(x: 15, y: 82)
        }
        .frame(width: 373, height: 132, alignment: .topLeading)
        .position(x: 196.5, y: 777)
        .opacity(pose.calendar * (1 - pose.fullscreen))
        .accessibilityHidden(true)
    }

    private func footer(_ pose: DreambookHingeJournalPose) -> some View {
        ZStack(alignment: .topLeading) {
            Text("Save a dream while it is still near.\nReturn when the day grows quiet.")
                .font(.system(size: 18, weight: .regular, design: .serif)).lineSpacing(1).multilineTextAlignment(.center)
                .foregroundStyle(DreambookHingeJournalStyle.color(0xB2AEB3))
                .frame(width: 345, height: 48).position(x: 196.5, y: 683)
                .opacity(reduceMotion ? 0 : 1)
            Capsule().fill(LinearGradient(colors: [DreambookHingeJournalStyle.color(0x858286),
                                                  DreambookHingeJournalStyle.color(0x626064),
                                                  DreambookHingeJournalStyle.color(0x4B484D)],
                                        startPoint: .top, endPoint: .bottom))
                .overlay(Capsule().stroke(LinearGradient(colors: [.white.opacity(0.6), .white.opacity(0.04)],
                                                        startPoint: .top, endPoint: .bottom), lineWidth: 1.5))
                .shadow(color: .black.opacity(0.16), radius: 3, y: 2)
                .frame(width: 333, height: 94).position(x: 196.5, y: 767)
            Rectangle().fill(Color.black.opacity(0.22)).frame(width: 1, height: 90).position(x: 196.5, y: 767)
            Rectangle().fill(Color.white.opacity(0.08)).frame(width: 0.7, height: 88).position(x: 197.3, y: 767)
            footerLegend("Write", symbol: "pencil.line").position(x: 113.25, y: 767)
            footerLegend("Reflect", symbol: "sparkle").position(x: 279.75, y: 767)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .opacity(pose.header)
        .offset(y: 120 * (1 - pose.header))
        .accessibilityHidden(true)
    }

    private func footerLegend(_ text: String, symbol: String) -> some View {
        VStack(spacing: 3) {
            Image(systemName: symbol).font(.system(size: 24, weight: .ultraLight)).frame(height: 30)
            Text(text).font(.system(size: 18, weight: .regular, design: .serif))
        }
        .foregroundStyle(DreambookHingeJournalStyle.color(0xDBD7DC))
    }

    private func interactionLayer(_ pose: DreambookHingeJournalPose) -> some View {
        ZStack(alignment: .topLeading) {
            pageInteraction(pose)
            if pose.header > 0.45 {
                VStack(spacing: 0) {
                    Spacer().frame(height: 720)
                    HStack(spacing: 0) {
                        Button("Write") {
                            if reduceMotion { reflected.toggle() }
                            animate(to: .full)
                        }
                        Button("Reflect") { reflected.toggle(); animate(to: .full) }
                    }
                    .font(.system(size: 12))
                    .buttonStyle(DreambookHingeJournalInvisibleButtonStyle())
                    .frame(width: 333, height: 94)
                    .offset(y: 120 * (1 - pose.header))
                    Spacer(minLength: 0)
                }
                .frame(width: 393, height: 852)
            }
            if pose.fullscreen > 0.4 {
                VStack(spacing: 0) {
                    Spacer().frame(height: 62)
                    HStack(spacing: 0) {
                        Spacer().frame(width: 333)
                        Button { animate(to: .reading) } label: {
                            Image(systemName: "xmark").font(.system(size: 11, weight: .regular))
                                .foregroundStyle(DreambookHingeJournalStyle.color(0x777378))
                                .frame(width: 44, height: 44)
                                .background(Circle().fill(Color.black.opacity(0.04)).frame(width: 28, height: 28))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Close writing page")
                        Spacer().frame(width: 16)
                    }
                    Spacer(minLength: 0)
                }
                .frame(width: 393, height: 852)
                .opacity(pose.fullscreen)
            }
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func pageInteraction(_ pose: DreambookHingeJournalPose) -> some View {
        let mesh = DreambookHingeJournalMesh(pose)
        let rect = mesh.hitFrame
        let polygons = mesh.hitPolygons.map { points in
            points.map { CGPoint(x: $0.x - rect.minX, y: $0.y - rect.minY) }
        }
        let shape = DreambookHingeJournalHitShape(polygons: polygons)
        return shape.fill(Color.clear)
            .frame(width: rect.width, height: rect.height)
            .contentShape(shape)
            .gesture(bookDrag)
            .position(x: rect.midX, y: rect.midY)
            .accessibilityElement()
            .accessibilityLabel(pose.openness < 0.5 ? "Night Atlas journal" : "Dream journal page")
            .accessibilityValue(pose.openness < 0.5 ? "Closed" : "Open")
            .accessibilityHint("Drag left to unfold the journal, or tap to look closer")
            .accessibilityAction { togglePage() }
    }

    private var bookDrag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("DreambookHingeJournalStage"))
            .onChanged { value in
                let now = Date()
                if !dragging {
                    dragStart = currentPose(at: now)
                    heldPose = dragStart
                    crossedOpen = dragStart.openness > 0.86
                    dragging = true
                }
                guard !reduceMotion else { lastTouch = now; manualLive = true; return }
                let raw = dragStart.openness - value.translation.width / 180
                let openness = Self.rubberBand(raw)
                let activation = Self.ease(abs(value.translation.width) / 24)
                let expanded = Self.ease((openness - 0.66) / 0.34)
                heldPose = .manual(openness: openness,
                                   expansion: dragStart.expansion + (expanded - dragStart.expansion) * activation,
                                   fullscreen: dragStart.fullscreen * (1 - activation),
                                   extraAngle: min(4, max(-4, value.translation.height * 0.035)))
                heldPose.closing = dragStart.closing
                heldPose.header = dragStart.header + (heldPose.header - dragStart.header) * activation
                heldPose.calendar = dragStart.calendar + (heldPose.calendar - dragStart.calendar) * activation
                heldPose.angle = dragStart.angle + (heldPose.angle - dragStart.angle) * activation
                heldPose.lift = dragStart.lift + (heldPose.lift - dragStart.lift) * activation
                heldPose.scale = dragStart.scale + (1 - dragStart.scale) * activation
                lastTouch = now
                manualLive = true
                if openness > 0.86 && !crossedOpen { crossedOpen = true; haptic() }
                if openness < 0.08 && crossedOpen { crossedOpen = false; haptic() }
            }
            .onEnded { value in
                dragging = false
                let distance = hypot(value.translation.width, value.translation.height)
                if distance < 6 { togglePage(from: heldPose); return }
                let projected = heldPose.openness - value.velocity.width * 0.0008
                releaseVelocity = min(2, max(-2, -value.velocity.width / 180))
                animate(to: projected > 0.5 ? .reading : .rest, from: heldPose, preserveVelocity: true)
            }
    }

    private func togglePage(from supplied: DreambookHingeJournalPose? = nil) {
        let shown = supplied ?? currentPose(at: Date())
        if reduceMotion {
            reflected.toggle()
            animate(to: .reading)
        } else if shown.openness < 0.5 {
            animate(to: .reading, from: shown)
        } else {
            animate(to: shown.fullscreen > 0.5 ? .reading : .full, from: shown)
        }
    }

    private func animate(to target: DreambookHingeJournalPose,
                         from supplied: DreambookHingeJournalPose? = nil, preserveVelocity: Bool = false) {
        let now = Date()
        transitionFrom = supplied ?? currentPose(at: now)
        transitionTo = target
        transitionStarted = now
        lastTouch = now
        manualLive = true
        if !preserveVelocity { releaseVelocity = 0 }
        haptic()
    }

    private func currentPose(at now: Date) -> DreambookHingeJournalPose {
        if let previewTime { return reduceMotion ? .reduced : Self.loopPose(previewTime) }
        if reduceMotion { return .reduced }
        if dragging { return heldPose }
        let touchAge = now.timeIntervalSince(lastTouch)
        if touchAge < 2.6 {
            let age = max(0, now.timeIntervalSince(transitionStarted))
            let pose = DreambookHingeJournalPose.transition(from: transitionFrom, to: transitionTo,
                                                           age: age, velocity: releaseVelocity)
            var rest = DreambookHingeJournalPose.rest
            rest.closing = 1
            return pose.blend(to: rest, amount: Self.ease((touchAge - 2) / 0.6))
        }
        guard autoPlay else { return .rest }
        let time = lastTouch == .distantPast ? now.timeIntervalSince(epoch) : touchAge - 2.6
        return Self.loopPose(time)
    }

    private func haptic() {
        let now = Date()
        guard now.timeIntervalSince(lastHaptic) > 0.16 else { return }
        lastHaptic = now
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
    }

    private static func loopPose(_ rawTime: Double) -> DreambookHingeJournalPose {
        let t = (rawTime.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        let o = min(1, max(0, (t - 1.2) / (2.0 / 3))) * (1 - min(1, max(0, (t - 6.05) / (2.0 / 3))))
        let e = ease((o - 0.45) / 0.55)
        let q = ease((t - 3.45) / 0.55) * (1 - ease((t - 4.60) / 0.55))
        var pose = DreambookHingeJournalPose.manual(openness: o, expansion: e, fullscreen: q)
        pose.closing = t >= 6.05 && t < 6.05 + 2.0 / 3 ? 1 : 0
        pose.header = min(1, 1 - ease((t - 1.20) / 0.35) + ease((t - 6.35) / 0.40))
        pose.calendar = ease((t - 1.45) / 0.40) * (1 - ease((t - 5.90) / 0.30))
        return pose
    }

    private static func rubberBand(_ raw: Double) -> Double {
        if raw < 0 { return raw / (1 + abs(raw) / 0.2) * 0.25 }
        if raw > 1 { let over = raw - 1; return 1 + over / (1 + over / 0.2) * 0.25 }
        return raw
    }
    private static func ease(_ value: Double) -> Double {
        let u = min(1, max(0, value))
        return u * u * (3 - 2 * u)
    }
}

private struct DreambookHingeJournalPose {
    var openness: Double
    var expansion: Double
    var fullscreen: Double
    var angle: Double
    var lift: Double
    var scale: Double
    var header: Double
    var calendar: Double
    var closing: Double = 0

    static let rest = Self(openness: 0, expansion: 0, fullscreen: 0, angle: 0, lift: 0, scale: 1, header: 1, calendar: 0)
    static let reading = manual(openness: 1, expansion: 1, fullscreen: 0)
    static let full = manual(openness: 1, expansion: 1, fullscreen: 1)
    static var reduced: Self {
        var pose = reading
        pose.header = 1
        pose.calendar = 0
        return pose
    }

    var pageFrame: CGRect {
        let e = min(1, max(0, expansion)), q = min(1, max(0, fullscreen))
        let x = mix(68.41, 10.19, e), y = mix(250.36, 189.22, e)
        let width = mix(254.72, 372.62, e), height = mix(356.61, 481.79, e)
        return CGRect(x: mix(x, 0, q), y: mix(y, 52, q),
                      width: mix(width, 393, q), height: mix(height, 800, q))
    }

    static func manual(openness: Double, expansion: Double, fullscreen: Double, extraAngle: Double = 0) -> Self {
        let e = min(1, max(0, expansion))
        let q = min(1, max(0, fullscreen))
        return .init(openness: openness, expansion: e, fullscreen: q,
                     angle: extraAngle, lift: 0, scale: 1,
                     header: 1 - smooth((e - 0.05) / 0.6), calendar: smooth((e - 0.05) / 0.6))
    }

    static func transition(from: Self, to: Self, age: Double, velocity: Double) -> Self {
        let opening = to.openness > from.openness + 0.1
        let closing = to.openness < from.openness - 0.1
        let oAge = closing ? max(0, age - 0.14) : age
        let spring = oAge >= 0.65 ? 1 : 1 - exp(-9 * oAge) * (cos(15 * oAge) + 0.6 * sin(15 * oAge))
        let momentum = velocity * oAge * exp(-9 * oAge)
        let o = mix(from.openness, to.openness, spring) + momentum
        let eDelay = opening ? 0.30 : 0
        let e = mix(from.expansion, to.expansion, smooth((age - eDelay) / 0.55))
        let qDelay = opening ? 0.72 : 0
        let q = mix(from.fullscreen, to.fullscreen, smooth((age - qDelay) / 0.50))
        var pose = manual(openness: o, expansion: e, fullscreen: q)
        pose.closing = mix(from.closing, closing ? 1 : (opening ? 0 : to.closing), smooth(age / 0.18))
        let settle = smooth(age / 0.22)
        pose.angle = mix(from.angle, pose.angle, settle)
        pose.lift = mix(from.lift, pose.lift, settle)
        pose.scale = mix(from.scale, pose.scale, settle)
        pose.header = mix(from.header, pose.header, smooth(age / 0.18))
        pose.calendar = mix(from.calendar, pose.calendar, smooth(age / 0.18))
        if !opening && !closing {
            pose = from.blend(to: to, amount: smooth(age / 0.5))
        }
        return pose
    }

    func blend(to other: Self, amount: Double) -> Self {
        .init(openness: mix(openness, other.openness, amount), expansion: mix(expansion, other.expansion, amount),
              fullscreen: mix(fullscreen, other.fullscreen, amount), angle: mix(angle, other.angle, amount),
              lift: mix(lift, other.lift, amount), scale: mix(scale, other.scale, amount),
              header: mix(header, other.header, amount), calendar: mix(calendar, other.calendar, amount),
              closing: mix(closing, other.closing, amount))
    }

    private func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    private static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    private static func smooth(_ raw: Double) -> Double {
        let u = min(1, max(0, raw))
        return u * u * (3 - 2 * u)
    }
}

private struct DreambookHingeJournalPage: View {
    var pose: DreambookHingeJournalPose
    var reflected: Bool
    var ink: Double = 1

    var body: some View {
        let e = pose.expansion, q = pose.fullscreen
        let width = pose.pageFrame.width, height = pose.pageFrame.height
        let inset = mix(18, 24, e)
        let dateY = mix(mix(23, 27, e), 25, q)
        let bodyY = mix(mix(54, 59, e), 62, q)
        let dateSize = mix(mix(9, 12, e), 13, q)
        let bodySize = mix(mix(11, 15, e), 17, q)
        let lineHeight = mix(mix(15, 21, e), 25, q)
        let fold = 1 + 0.12 * sin(.pi * min(1, max(0, pose.openness)))
        return ZStack(alignment: .topLeading) {
            paperRules(width: width, height: height, inset: inset, lineHeight: lineHeight)
            Text("Thursday, 15 October").font(.system(size: dateSize, weight: .medium, design: .serif))
                .foregroundStyle(DreambookHingeJournalStyle.color(0x666267))
                .frame(width: width - 2 * inset, height: dateSize * 1.5, alignment: .topLeading)
                .offset(x: inset, y: dateY).opacity(ink)
            Text(reflected ? "The door was already open. I stepped\ninto a room of little stars, and\nevery one of them felt familiar." : "I found a quiet stairway above\nthe clouds. At the top, a door\nwas already open.")
                .font(.system(size: bodySize, weight: .regular, design: .serif))
                .lineSpacing(max(0, lineHeight - UIFont.systemFont(ofSize: bodySize).lineHeight))
                .foregroundStyle(DreambookHingeJournalStyle.color(0x514D52))
                .frame(width: width - 2 * inset, height: lineHeight * 4, alignment: .topLeading)
                .offset(x: inset, y: bodyY).opacity(ink)
            LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black.opacity(0.025 * fold), location: 0.30),
                                   .init(color: .black.opacity(0.13 * fold), location: 0.49), .init(color: .white.opacity(0.025 * fold), location: 0.56),
                                   .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: 120).offset(y: height * 0.5 - 60)
                .opacity(1 - q)
            Text("Tap to write").font(.system(size: 9, weight: .regular, design: .serif))
                .foregroundStyle(DreambookHingeJournalStyle.color(0x969197)).offset(x: inset, y: height - 34)
                .opacity((1 - q) * ink)
            Text("1 / 1").font(.system(size: 9, weight: .regular, design: .serif))
                .foregroundStyle(DreambookHingeJournalStyle.color(0x969197)).position(x: width - 32, y: height - 27)
                .opacity((1 - q) * ink)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private func paperRules(width: Double, height: Double, inset: Double, lineHeight: Double) -> some View {
        let e = pose.expansion, q = pose.fullscreen
        let start = mix(140, 190, e)
        let count = 8
        return Canvas { context, _ in
            for index in 0..<count {
                let y = start + Double(index) * 104
                if y < height - 30 {
                    var path = Path()
                    path.move(to: CGPoint(x: inset, y: y))
                    path.addLine(to: CGPoint(x: width - inset, y: y))
                    context.stroke(path, with: .color(DreambookHingeJournalStyle.color(0xA8A4A9).opacity(0.18 * q)), lineWidth: 0.5)
                }
            }
            for index in 0..<90 {
                let x = DreambookHingeJournalStyle.hash(Double(index) + 8) * width
                let y = DreambookHingeJournalStyle.hash(Double(index) + 63) * height
                let fleck = Path(ellipseIn: CGRect(x: x, y: y, width: 0.6, height: 0.6))
                context.fill(fleck, with: .color(DreambookHingeJournalStyle.color(0x756F77).opacity(0.025)))
            }
        }
        .frame(width: width, height: height)
    }

    private func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
}

private struct DreambookHingeJournalCoverFront: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [DreambookHingeJournalStyle.color(0x4A484B),
                                    DreambookHingeJournalStyle.color(0x484649),
                                    DreambookHingeJournalStyle.color(0x454346)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            DreambookHingeJournalClothGrain()
            Rectangle().fill(DreambookHingeJournalStyle.color(0x373538).opacity(0.35)).frame(width: 12, height: 352)
            Rectangle().fill(Color.white.opacity(0.035)).frame(width: 1.2, height: 350).offset(x: 10, y: 1)
            Text("Night Atlas").font(.system(size: 24, weight: .regular, design: .serif))
                .multilineTextAlignment(.center).foregroundStyle(DreambookHingeJournalStyle.color(0xB8B4B9))
                .shadow(color: .black.opacity(0.5), radius: 0.3, y: 1)
                .frame(width: 221, height: 40).position(x: 126.5, y: 73)
            Text("Notes from the night.").font(.system(size: 12, weight: .regular, design: .serif))
                .foregroundStyle(DreambookHingeJournalStyle.color(0x99959B)).position(x: 126.5, y: 99)
            Image(systemName: "moon.stars.fill").font(.system(size: 23, weight: .regular))
                .foregroundStyle(DreambookHingeJournalStyle.color(0x8F8B91))
                .shadow(color: .black.opacity(0.4), radius: 0.5, y: 1).position(x: 126.5, y: 315)
        }
        .frame(width: 253, height: 352, alignment: .topLeading)
    }
}

private struct DreambookHingeJournalCoverBack: View {
    var body: some View {
        ZStack(alignment: .leading) {
            LinearGradient(colors: [DreambookHingeJournalStyle.color(0x514C52),
                                    DreambookHingeJournalStyle.color(0x413C43)], startPoint: .leading, endPoint: .trailing)
            DreambookHingeJournalClothGrain()
            Rectangle().fill(.black.opacity(0.15)).frame(width: 12, height: 352)
            RoundedRectangle(cornerRadius: 4).stroke(Color.white.opacity(0.035), lineWidth: 1).padding(13)
        }
        .frame(width: 253, height: 352)
    }
}

private struct DreambookHingeJournalClothGrain: View {
    var body: some View {
        Canvas { context, _ in
            for index in 0..<240 {
                let x = DreambookHingeJournalStyle.hash(Double(index) + 12) * 253
                let y = DreambookHingeJournalStyle.hash(Double(index) + 39) * 352
                var dash = Path()
                dash.move(to: CGPoint(x: x, y: y))
                dash.addLine(to: CGPoint(x: x + 1 + DreambookHingeJournalStyle.hash(Double(index)) * 4, y: y + 0.5))
                context.stroke(dash, with: .color(Color.white.opacity(0.03)), lineWidth: 0.45)
            }
        }
        .frame(width: 253, height: 352)
    }
}

private struct DreambookHingeJournalInvisibleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .foregroundStyle(Color.clear)
            .contentShape(Rectangle())
    }
}

private enum DreambookHingeJournalStyle {
    static let screen = color(0x2A2629)
    static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255)
    }
    static func hash(_ value: Double) -> Double {
        let n = sin(value * 12.9898) * 43758.5453
        return n - floor(n)
    }
}

/// The screen-space positions come from the two visible paper panels, not an invented camera spin.
private struct DreambookHingeJournalMesh {
    var paper: [CGPoint]
    var cover: [CGPoint]
    var reverse: [CGPoint]
    var reverseAlpha: Double
    var paperAlpha: Double
    var coverAlpha: Double
    var ink: Double

    init(_ pose: DreambookHingeJournalPose) {
        let o = Self.clamp(pose.openness), q = Self.clamp(pose.fullscreen), closing = Self.clamp(pose.closing)
        let source = 0.4 + o * (2.0 / 3)
        let returnSource = 5.3 + (1 - o) * (2.0 / 3)
        let openPaper = Self.sample(Self.opening, at: source)
        let closePaper = Self.sample(Self.closing, at: returnSource)
        var points = zip(openPaper, closePaper).map { $0 + ($1 - $0) * closing }
        let frame = pose.pageFrame
        let flat = [frame.minX, frame.minY, frame.maxX, frame.minY, frame.maxX, frame.midY,
                    frame.maxX, frame.maxY, frame.minX, frame.maxY, frame.minX, frame.midY]
        let flatten = Self.ease(q / 0.12)
        points = zip(points, flat).map { $0 + ($1 - $0) * flatten }
        paper = stride(from: 0, to: 12, by: 2).map { CGPoint(x: points[$0], y: points[$0 + 1]) }
        let openCover = Self.sample(Self.frontCover, at: source)
        let closeCover = Self.sample(Self.returnCover, at: returnSource)
        let coverPoints = zip(openCover, closeCover).map { $0 + ($1 - $0) * closing }
        cover = stride(from: 0, to: 8, by: 2).map { CGPoint(x: coverPoints[$0], y: coverPoints[$0 + 1]) }
        paperAlpha = (Self.ease((source - 0.42) / 0.06) * (1 - closing)
                      + (1 - Self.ease((returnSource - 5.73333333) / (1.0 / 30))) * closing)
        coverAlpha = ((1 - Self.ease((source - 0.63333333) / 0.02666667)) * (1 - closing)
                      + Self.ease((returnSource - 5.55) / (1.0 / 60)) * closing) * (1 - q)
        ink = Self.ease((source - 0.93333333) / (2.0 / 15)) * (1 - closing)
            + (1 - Self.ease((returnSource - 5.3) / 0.10)) * closing
        let edge = CGPoint(x: paper[1].x - paper[0].x, y: paper[1].y - paper[0].y)
        let length = max(0.001, hypot(edge.x, edge.y))
        let depth = Self.sample(Self.reverseDepth, at: source)[0] * (1 - closing) + 3 * closing
        let normal = CGPoint(x: edge.y / length * depth, y: -edge.x / length * depth)
        reverse = [CGPoint(x: paper[0].x + normal.x, y: paper[0].y + normal.y),
                   CGPoint(x: paper[1].x + normal.x, y: paper[1].y + normal.y), paper[1], paper[0]]
        reverseAlpha = paperAlpha * (1 - coverAlpha) * (1 - q)
        let center = CGPoint(x: 196.5, y: 429)
        paper = paper.map { Self.apply($0, pose: pose, center: center) }
        cover = cover.map { Self.apply($0, pose: pose, center: center) }
        reverse = reverse.map { Self.apply($0, pose: pose, center: center) }
    }

    var hitPolygons: [[CGPoint]] {
        (paperAlpha > 0.01 ? [paper] : []) + (coverAlpha > 0.01 ? [cover] : [])
    }
    var hitPoints: [CGPoint] { hitPolygons.flatMap { $0 } }
    var hitFrame: CGRect {
        let points = hitPoints
        let xs = points.map(\.x), ys = points.map(\.y)
        return CGRect(x: xs.min() ?? 0, y: ys.min() ?? 0,
                      width: max(1, (xs.max() ?? 393) - (xs.min() ?? 0)),
                      height: max(1, (ys.max() ?? 852) - (ys.min() ?? 0)))
    }

    static func path(_ points: [CGPoint]) -> Path {
        Path { path in
            guard let first = points.first else { return }
            path.move(to: first)
            for point in points.dropFirst() { path.addLine(to: point) }
            path.closeSubpath()
        }
    }

    /// Unit-square projective solve. Signed determinants preserve orientation through the fold.
    static func projection(width: Double, height: Double, quad: [CGPoint]) -> ProjectionTransform {
        let p = quad
        let dx1 = p[1].x - p[2].x, dx2 = p[3].x - p[2].x
        let dy1 = p[1].y - p[2].y, dy2 = p[3].y - p[2].y
        let dx3 = p[0].x - p[1].x + p[2].x - p[3].x
        let dy3 = p[0].y - p[1].y + p[2].y - p[3].y
        let determinant = dx1 * dy2 - dx2 * dy1
        var g = 0.0, h = 0.0
        if abs(determinant) > 0.000001 {
            g = (dx3 * dy2 - dx2 * dy3) / determinant
            h = (dx1 * dy3 - dx3 * dy1) / determinant
        }
        var matrix = ProjectionTransform()
        matrix.m11 = (p[1].x - p[0].x + g * p[1].x) / width
        matrix.m12 = (p[1].y - p[0].y + g * p[1].y) / width
        matrix.m13 = g / width
        matrix.m21 = (p[3].x - p[0].x + h * p[3].x) / height
        matrix.m22 = (p[3].y - p[0].y + h * p[3].y) / height
        matrix.m23 = h / height
        matrix.m31 = p[0].x; matrix.m32 = p[0].y; matrix.m33 = 1
        return matrix
    }

    private static func apply(_ point: CGPoint, pose: DreambookHingeJournalPose, center: CGPoint) -> CGPoint {
        let angle = pose.angle * .pi / 180, c = cos(angle) * pose.scale, s = sin(angle) * pose.scale
        let x = point.x - center.x, y = point.y - center.y
        return CGPoint(x: center.x + c * x - s * y, y: center.y + s * x + c * y + pose.lift)
    }
    private static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
    private static func ease(_ v: Double) -> Double { let u = clamp(v); return u * u * (3 - 2 * u) }

    private static func sample(_ rows: [[Double]], at t: Double) -> [Double] {
        if t <= rows[0][0] { return Array(rows[0].dropFirst()) }
        if t >= rows[rows.count - 1][0] { return Array(rows[rows.count - 1].dropFirst()) }
        var index = 0
        while index + 1 < rows.count && rows[index + 1][0] < t { index += 1 }
        let a = rows[index], b = rows[index + 1], dt = b[0] - a[0]
        let u = (t - a[0]) / dt, u2 = u * u, u3 = u2 * u
        return (1..<a.count).map { column in
            func tangent(_ i: Int) -> Double {
                if i == 0 { return (rows[1][column] - rows[0][column]) / (rows[1][0] - rows[0][0]) }
                if i == rows.count - 1 {
                    return (rows[i][column] - rows[i - 1][column]) / (rows[i][0] - rows[i - 1][0])
                }
                let h0 = rows[i][0] - rows[i - 1][0], h1 = rows[i + 1][0] - rows[i][0]
                let d0 = (rows[i][column] - rows[i - 1][column]) / h0
                let d1 = (rows[i + 1][column] - rows[i][column]) / h1
                if d0 * d1 <= 0 { return 0 }
                let w0 = 2 * h1 + h0, w1 = h1 + 2 * h0
                return (w0 + w1) / (w0 / d0 + w1 / d1)
            }
            let value = (2 * u3 - 3 * u2 + 1) * a[column] + (u3 - 2 * u2 + u) * dt * tangent(index)
                + (-2 * u3 + 3 * u2) * b[column] + (u3 - u2) * dt * tangent(index + 1)
            return min(max(a[column], b[column]), max(min(a[column], b[column]), value))
        }
    }

    private static let reverseDepth: [[Double]] = [
        [0.4, 0], [0.65, 0], [0.66666667, 12], [0.7, 36], [0.73333333, 38],
        [0.8, 32], [0.86666667, 14], [0.93333333, 3], [1.06666667, 3]
    ]

    private static let opening: [[Double]] = [
        [0.40000000, 318.77000000, 606.97000000, 318.77000000, 250.36000000, 320.22000000, 250.36000000, 323.13000000, 250.36000000, 323.13000000, 606.97000000, 320.22000000, 606.97000000],
        [0.46666667, 314.40000000, 620.07000000, 320.22000000, 260.54000000, 327.50000000, 264.91000000, 331.87000000, 269.28000000, 324.59000000, 615.70000000, 320.22000000, 618.61000000],
        [0.53333333, 253.27000000, 621.52000000, 295.48000000, 267.82000000, 308.58000000, 282.38000000, 353.70000000, 314.40000000, 311.49000000, 620.07000000, 279.47000000, 636.08000000],
        [0.60000000, 142.64000000, 644.81000000, 251.81000000, 276.56000000, 279.47000000, 301.30000000, 382.81000000, 339.14000000, 273.64000000, 669.56000000, 171.76000000, 637.53000000],
        [0.66666667, 24.74000000, 582.22000000, 196.50000000, 278.01000000, 235.80000000, 298.39000000, 423.57000000, 400.28000000, 234.34000000, 703.03000000, 66.96000000, 605.51000000],
        [0.73333333, -32.02000000, 490.52000000, 221.24000000, 243.08000000, 251.81000000, 302.76000000, 435.21000000, 480.33000000, 181.94000000, 727.78000000, -1.46000000, 550.20000000],
        [0.80000000, -69.87000000, 375.53000000, 231.43000000, 197.96000000, 301.30000000, 339.14000000, 423.57000000, 558.93000000, 122.27000000, 736.51000000, 0.00000000, 516.72000000],
        [0.86666667, -65.50000000, 275.10000000, 276.56000000, 158.66000000, 347.88000000, 372.62000000, 413.38000000, 598.23000000, 71.32000000, 714.68000000, 5.82000000, 489.07000000],
        [0.93333333, -16.01000000, 216.88000000, 333.32000000, 163.02000000, 358.07000000, 397.37000000, 398.82000000, 636.08000000, 36.39000000, 694.30000000, 10.19000000, 455.59000000],
        [1.00000000, -4.37000000, 197.96000000, 366.80000000, 179.03000000, 369.71000000, 420.66000000, 391.54000000, 665.19000000, 18.92000000, 684.11000000, 16.01000000, 439.58000000],
        [1.06666667, 10.19000000, 189.22000000, 382.81000000, 189.22000000, 372.62000000, 427.93000000, 382.81000000, 671.01000000, 10.19000000, 671.01000000, 18.92000000, 427.93000000],
    ]
    private static let closing: [[Double]] = [
        [5.30000000, 10.19000000, 189.22000000, 382.81000000, 189.22000000, 372.62000000, 427.93000000, 382.81000000, 671.01000000, 10.19000000, 671.01000000, 18.92000000, 427.93000000],
        [5.40000000, -17.47000000, 267.82000000, 368.26000000, 237.26000000, 368.26000000, 420.66000000, 391.54000000, 660.82000000, 20.38000000, 682.66000000, 16.01000000, 443.94000000],
        [5.46666667, -29.11000000, 369.71000000, 355.16000000, 289.66000000, 340.60000000, 385.72000000, 401.73000000, 618.61000000, 49.49000000, 694.30000000, 5.82000000, 471.60000000],
        [5.53333333, -13.10000000, 477.42000000, 330.41000000, 342.06000000, 317.31000000, 362.43000000, 410.47000000, 576.40000000, 82.97000000, 707.40000000, 0.00000000, 499.26000000],
        [5.60000000, 23.29000000, 592.41000000, 328.96000000, 359.52000000, 334.78000000, 393.00000000, 416.29000000, 483.24000000, 135.37000000, 707.40000000, 55.31000000, 611.33000000],
        [5.66666667, 123.72000000, 665.19000000, 353.70000000, 365.34000000, 360.98000000, 387.18000000, 397.37000000, 411.92000000, 183.40000000, 700.12000000, 142.64000000, 666.64000000],
        [5.80000000, 315.86000000, 606.97000000, 317.31000000, 250.36000000, 318.77000000, 250.36000000, 323.13000000, 250.36000000, 323.13000000, 606.97000000, 318.77000000, 606.97000000],
        [5.96666667, 318.77000000, 606.97000000, 318.77000000, 250.36000000, 320.22000000, 250.36000000, 323.13000000, 250.36000000, 323.13000000, 606.97000000, 320.22000000, 606.97000000],
    ]
    private static let returnCover: [[Double]] = [
        [5.56666670, 283.80000000, 315.90000000, 324.60000000, 334.80000000, 0.00000000, 524.00000000, -42.20000000, 505.10000000],
        [5.60000000, 257.60000000, 296.90000000, 331.90000000, 353.70000000, 16.00000000, 595.30000000, -40.80000000, 522.50000000],
        [5.66666670, 206.70000000, 266.40000000, 362.40000000, 362.40000000, 120.80000000, 673.90000000, -16.00000000, 542.90000000],
        [5.73333330, 154.30000000, 247.40000000, 377.00000000, 337.70000000, 221.20000000, 679.70000000, 5.80000000, 573.50000000],
        [5.80000000, 112.10000000, 246.00000000, 362.40000000, 302.80000000, 279.50000000, 650.60000000, 32.00000000, 592.40000000],
        [5.90000000, 77.10000000, 250.40000000, 329.00000000, 260.50000000, 312.90000000, 615.70000000, 61.10000000, 604.10000000],
        [5.96666670, 68.40000000, 251.80000000, 323.10000000, 251.80000000, 323.10000000, 607.00000000, 68.40000000, 607.00000000],
    ]

    private static let frontCover: [[Double]] = [
        [0.40000000, 68.40000000, 251.80000000, 323.10000000, 251.80000000, 323.10000000, 607.00000000, 68.40000000, 607.00000000],
        [0.46670000, 78.60000000, 251.80000000, 323.10000000, 251.80000000, 308.60000000, 624.40000000, 64.00000000, 608.40000000],
        [0.53330000, 103.30000000, 253.30000000, 299.80000000, 262.00000000, 248.90000000, 647.70000000, 53.90000000, 608.40000000],
        [0.60000000, 144.10000000, 259.10000000, 257.60000000, 267.80000000, 141.20000000, 652.10000000, 40.80000000, 604.10000000],
        [0.63330000, 170.30000000, 264.90000000, 228.50000000, 266.40000000, 75.70000000, 636.10000000, 32.00000000, 596.80000000],
    ]
}

private struct DreambookHingeJournalHitShape: Shape {
    var polygons: [[CGPoint]]
    func path(in rect: CGRect) -> Path {
        var path = Path()
        for points in polygons { path.addPath(DreambookHingeJournalMesh.path(points)) }
        return path
    }
}

@MainActor
private enum DreambookHingeJournalCoverCache {
    static let back: UIImage = {
        let renderer = ImageRenderer(content: DreambookHingeJournalCoverBack())
        renderer.scale = 3
        return renderer.uiImage ?? UIImage()
    }()
    static let front: UIImage = {
        let renderer = ImageRenderer(content: DreambookHingeJournalCoverFront()
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 2, bottomLeadingRadius: 2,
                                             bottomTrailingRadius: 17, topTrailingRadius: 17))
            .overlay(UnevenRoundedRectangle(topLeadingRadius: 2, bottomLeadingRadius: 2,
                                           bottomTrailingRadius: 17, topTrailingRadius: 17)
                .stroke(Color.white.opacity(0.09), lineWidth: 0.7)))
        renderer.scale = 3
        return renderer.uiImage ?? UIImage()
    }()
}
