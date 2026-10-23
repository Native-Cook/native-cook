import SwiftUI
import UIKit

/// A hinged silver sleeve reveals an everyday card inside a quiet delivery sheet.
struct PrismUnsealSheet: View {
    static let loop = 8.0

    var autoPlay = false
    /// An optional fixed loop time for previews and matching captures across platforms.
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date.timeIntervalSinceReferenceDate
    @State private var releasedAt: Double? = nil
    @State private var releaseFrame = M.scrub(0)
    @State private var releaseTo = 0.0
    @State private var dragStartFrame: M.Frame? = nil
    @State private var dragFrame: M.Frame? = nil
    @State private var dragTilt = 0.0
    @State private var hapticCrossed = false
    @State private var handSettled = true
    @State private var touchSerial = 0

    private typealias M = PrismUnsealSheetMotion

    var body: some View {
        GeometryReader { geometry in
            let insets = geometry.safeAreaInsets
            let full = CGSize(width: geometry.size.width + insets.leading + insets.trailing,
                              height: geometry.size.height + insets.top + insets.bottom)
            let scale = min(full.width / 393, full.height / 852)
            TimelineView(.animation(minimumInterval: 1 / 120, paused: clockPaused)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let frame = frame(at: now)
                scene(frame: frame, topInset: insets.top / scale)
                    .frame(width: 393, height: 852, alignment: .topLeading)
                    .scaleEffect(scale, anchor: .topLeading)
                    .frame(width: 393 * scale, height: 852 * scale, alignment: .topLeading)
                    .position(x: full.width / 2, y: full.height / 2)
            }
            .frame(width: full.width, height: full.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(M.color(0x111216).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { epoch = Date.timeIntervalSinceReferenceDate }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { epoch = Date.timeIntervalSinceReferenceDate }
        }
        .task(id: touchSerial) {
            guard releasedAt != nil, dragFrame == nil else { return }
            try? await Task.sleep(for: .seconds(2.6))
            guard !Task.isCancelled else { return }
            handSettled = true
        }
    }

    private var clockPaused: Bool {
        reduceMotion || previewTime != nil || scenePhase != .active || (!autoPlay && handSettled && dragFrame == nil)
    }

    private func frame(at now: Double) -> M.Frame {
        if reduceMotion { return M.scrub(releasedAt == nil ? 1 : releaseTo) }
        if let previewTime { return M.timeline(previewTime) }
        if let dragFrame { return dragFrame }
        if let releasedAt {
            let elapsed = max(0, now - releasedAt)
            if elapsed < 2 {
                return M.blend(releaseFrame, M.scrub(releaseTo), M.spring(elapsed))
            }
            if elapsed < 2.6 {
                return M.blend(M.scrub(releaseTo), M.scrub(0), M.smooth(M.segment(elapsed, 2, 2.6)))
            }
            return autoPlay ? M.timeline(elapsed - 2.6) : M.scrub(0)
        }
        return autoPlay ? M.timeline(now - epoch) : M.scrub(0)
    }

    private func scene(frame: M.Frame, topInset: Double) -> some View {
        ZStack(alignment: .topLeading) {
            wallet(topShift: max(0, topInset + 12 - 70))
            Rectangle().fill(Color.black.opacity(0.26))
                .frame(width: 393, height: 364)
                .allowsHitTesting(false)

            UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32)
                .fill(M.color(0xFAF9FC))
                .frame(width: 393, height: 488)
                .shadow(color: .black.opacity(0.18), radius: 32, x: 0, y: 16)
                .shadow(color: .black.opacity(0.16), radius: 4, x: 0, y: 2)
                .offset(y: 364)
            Capsule().fill(M.color(0xD9D8DF))
                .frame(width: 39, height: 4).offset(x: 177, y: 375)

            Button { setManual(0) } label: {
                Image(systemName: "xmark").font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(M.color(0x77747F))
                    .frame(width: 30, height: 30)
                    .background(M.color(0xEEEAF0), in: Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain).accessibilityLabel("Close package")
            .offset(x: 336, y: 378)

            label("PRISM / CARD DELIVERY", size: 10, weight: .medium, color: 0x93909A, tracking: 2)
                .frame(width: 310, height: 16).position(x: 196.5, y: 409)

            copy(frame: frame)
            package(frame: frame)

            ZStack {
                label("Drag up to unseal", size: 11, color: 0x77717F).opacity(max(0, 1 - frame.ready * 2))
                    .accessibilityHidden(frame.ready >= 0.5)
                label("A fresh start, in your hands", size: 11, color: 0x77717F).opacity(max(0, frame.ready * 2 - 1))
                    .accessibilityHidden(frame.ready < 0.5)
            }
            .frame(width: 330, height: 18).position(x: 196.5, y: 715)

            Button { setManual(frame.progress > 0.5 ? 0 : 1) } label: {
                ZStack {
                    label("Unseal card", size: 13, weight: .semibold, color: 0x3A3542).opacity(max(0, 1 - frame.ready * 2))
                    label("All set", size: 13, weight: .semibold, color: 0x3A3542).opacity(max(0, frame.ready * 2 - 1))
                }
                .frame(width: 341, height: 50)
                .background(M.color(0xEEEAF2), in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.white.opacity(0.85), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(frame.progress > 0.5 ? "Close card sleeve" : "Unseal card")
            .offset(x: 26, y: 748)

            label("A small reveal. A new beginning.", size: 10, color: 0xAAA6B0)
                .frame(width: 341, height: 16).position(x: 196.5, y: 805)

            Color.clear.frame(width: 253, height: 196)
                .contentShape(Rectangle())
                .gesture(packageDrag)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Unseal package")
                .accessibilityValue(frame.ready > 0.9 ? "Open" : (frame.progress > 0.01 ? "Partly open" : "Closed"))
                .accessibilityAdjustableAction { direction in
                    setManual(direction == .increment ? 1 : 0)
                }
                .position(x: 196.5, y: 607)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func wallet(topShift: Double) -> some View {
        ZStack(alignment: .topLeading) {
            M.color(0x111216)
            label("Prism", size: 28, weight: .semibold, color: 0xFAF9FC)
                .offset(x: 26, y: 80 + topShift)
            label("P", size: 14, weight: .medium, color: 0xE6E3EC)
                .frame(width: 36, height: 36)
                .background(M.color(0x2C2A34), in: Circle())
                .offset(x: 331, y: 80 + topShift)
            label("EVERYDAY WALLET", size: 11, weight: .medium, color: 0x9B9DA6, tracking: 2)
                .offset(x: 26, y: 144 + topShift)
            label("$2,840.50", size: 42, weight: .medium, color: 0xF4F2F6)
                .offset(x: 26, y: 171 + topShift)
            HStack(spacing: 14) {
                ZStack {
                    Circle().fill(AngularGradient(colors: M.rainbow + [M.rainbow[0]], center: .center))
                        .frame(width: 35, height: 35)
                    Circle().fill(M.color(0x22242B)).frame(width: 15, height: 15)
                }
                VStack(alignment: .leading, spacing: 6) {
                    label("Your next everyday card", size: 13, weight: .medium, color: 0xE8E6ED)
                    label("Made for the way you move", size: 11, color: 0x9797A1)
                }
                Spacer(minLength: 0)
                Image(systemName: "arrow.up.right").font(.system(size: 12, weight: .medium))
                    .foregroundStyle(M.color(0x86848F))
            }
            .padding(.horizontal, 18).frame(width: 341, height: 90)
            .background(M.color(0x22242B), in: RoundedRectangle(cornerRadius: 20))
            .offset(x: 26, y: 239 + topShift)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func copy(frame: M.Frame) -> some View {
        // The departing copy gives the long lid room to fold through the sheet's upper air.
        let clearance = 1 - M.smooth(M.segment(frame.lid, 0.25, 0.65))
        return ZStack(alignment: .topLeading) {
            label("A little more possibility.", size: 25, weight: .semibold, color: 0x25242A)
                .frame(width: 365, height: 34)
                .position(x: 196.5, y: 446 - 6 * frame.ready)
                .opacity((1 - frame.ready) * clearance)
                .accessibilityHidden(frame.ready >= 0.5 || clearance < 0.5)
            label("Your card is ready", size: 25, weight: .semibold, color: 0x25242A)
                .frame(width: 365, height: 34)
                .position(x: 196.5, y: 452 - 6 * frame.ready)
                .opacity(frame.ready)
                .accessibilityHidden(frame.ready < 0.5)
            label("A new everyday card.\nUnseal something good.", size: 13, color: 0x77717F)
                .multilineTextAlignment(.center).lineSpacing(3)
                .frame(width: 330, height: 40)
                .position(x: 196.5, y: 488 - 6 * frame.ready)
                .opacity((1 - frame.ready) * clearance)
                .accessibilityHidden(frame.ready >= 0.5 || clearance < 0.5)
            label("Made for wherever you go.\nReady when you are.", size: 13, color: 0x77717F)
                .multilineTextAlignment(.center).lineSpacing(3)
                .frame(width: 330, height: 40)
                .position(x: 196.5, y: 494 - 6 * frame.ready)
                .opacity(frame.ready)
                .accessibilityHidden(frame.ready < 0.5)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func package(frame: M.Frame) -> some View {
        let sleeveOpacity = 1 - M.smooth(M.segment(frame.extraction, 0.72, 1))
        return ZStack(alignment: .topLeading) {
            Ellipse().fill(M.color(0x54475E).opacity((0.13 - 0.04 * frame.spread) * sleeveOpacity))
                .frame(width: 176 + 26 * frame.spread, height: 17)
                .blur(radius: 11 + 5 * frame.spread)
                .position(x: 196.5, y: 671 + 18 * frame.spread)

            rainbowSurface
                .frame(width: 174, height: 132)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.white.opacity(0.5), lineWidth: 0.8))
                .shadow(color: M.color(0x554065).opacity(0.12), radius: 10, x: 0, y: 6)
                .opacity(frame.spread * sleeveOpacity)
                .position(x: 196.5, y: 604 - 22 * frame.spread)
                .zIndex(0)

            lid(frame: frame)
                .rotation3DEffect(.degrees(-155 * frame.lid), axis: (x: 1, y: 0, z: 0), anchor: .top, perspective: 0.55)
                .opacity(1 - M.smooth(M.segment(frame.lid, 0.72, 1)))
                .position(x: 196.5, y: 604)
                .zIndex(frame.lid < 0.55 ? 4 : 1)

            card
                .opacity(M.smooth(M.segment(frame.lid, 0.1, 0.4)))
                .scaleEffect(1 + 0.12 * frame.extraction)
                .rotationEffect(.degrees(-4 * (1 - frame.extraction) + frame.tilt))
                .shadow(color: M.color(0x4A3F5A).opacity(0.17 * frame.extraction), radius: 8, x: 0, y: 6)
                .position(x: 196.5, y: 616 - 44 * frame.extraction)
                .zIndex(2)

            pocket(frame: frame)
                .opacity(sleeveOpacity)
                .rotationEffect(.degrees(3 * frame.spread), anchor: .top)
                .position(x: 196.5, y: 639 + 18 * frame.spread)
                .zIndex(5)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var rainbowSurface: some View {
        ZStack {
            LinearGradient(stops: [
                .init(color: M.color(0xCADC76), location: 0),
                .init(color: M.color(0xF3CB84), location: 0.32),
                .init(color: M.color(0xE25277), location: 0.66),
                .init(color: M.color(0x7860AB), location: 1),
            ], startPoint: .topLeading, endPoint: .bottomTrailing)
            RadialGradient(colors: [M.color(0xB5CFED).opacity(0.9), .clear], center: .topTrailing, startRadius: 1, endRadius: 145)
            LinearGradient(colors: [Color.white.opacity(0.32), .clear, M.color(0xB04C9D).opacity(0.18)], startPoint: .top, endPoint: .bottom)
            Image(uiImage: M.grain).resizable().opacity(0.035).blendMode(.multiply)
        }
    }

    private func lid(frame: M.Frame) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8).fill(LinearGradient(stops: [
                .init(color: M.color(0xF1F0F3), location: 0),
                .init(color: M.color(0xE5E4E9), location: 0.28),
                .init(color: M.color(0xF3F2F5), location: 0.54),
                .init(color: M.color(0xDAD9DF), location: 1),
            ], startPoint: .topLeading, endPoint: .bottomTrailing))
            LinearGradient(colors: [.clear, Color.white.opacity(0.85), .clear], startPoint: .leading, endPoint: .trailing)
                .frame(width: 30, height: 180)
                .rotationEffect(.degrees(22))
                .offset(x: -65 + 135 * frame.lid)
                .blur(radius: 7)
            Image(uiImage: M.grain).resizable().opacity(0.04).blendMode(.multiply)
            RoundedRectangle(cornerRadius: 8).strokeBorder(LinearGradient(colors: [Color.white.opacity(0.95), M.color(0xB8B6C0).opacity(0.55)], startPoint: .top, endPoint: .bottom), lineWidth: 0.8)
            Path { path in
                path.move(to: CGPoint(x: 2, y: 2))
                path.addLine(to: CGPoint(x: 87, y: 65))
                path.addLine(to: CGPoint(x: 172, y: 2))
            }
            .stroke(M.color(0xC3C0CC).opacity(0.35), lineWidth: 0.65)
            label("prism", size: 11, weight: .medium, color: 0x92909A, tracking: 0.3)
                .offset(y: -14)
                .opacity(1 - M.smooth(M.segment(frame.lid, 0.35, 0.55)))
        }
        .frame(width: 174, height: 118)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .shadow(color: M.color(0x5D5367).opacity(0.12), radius: 3, x: 0, y: 2)
    }

    private var card: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 9).fill(LinearGradient(stops: [
                .init(color: M.color(0xFEFDFE), location: 0),
                .init(color: M.color(0xE3E1E8), location: 0.48),
                .init(color: M.color(0xF5F4F7), location: 0.7),
                .init(color: M.color(0xD7D5DE), location: 1),
            ], startPoint: .topLeading, endPoint: .bottomTrailing))
            Image(uiImage: M.grain).resizable().opacity(0.025).blendMode(.multiply)
            label("prism", size: 12, weight: .semibold, color: 0x78777D).offset(x: 13, y: 12)
            label("EVERYDAY", size: 7, weight: .medium, color: 0x78777D, tracking: 1.6).offset(x: 13, y: 31)
            label("NOVA REED", size: 8, weight: .medium, color: 0x78777D, tracking: 0.5).offset(x: 13, y: 72)
            Canvas { context, _ in
                let chip = CGRect(x: 112, y: 65, width: 18, height: 13)
                context.stroke(Path(roundedRect: chip, cornerRadius: 3), with: .color(M.color(0xA6A0AD)), lineWidth: 0.7)
                var lines = Path()
                lines.move(to: CGPoint(x: 118, y: 65)); lines.addLine(to: CGPoint(x: 118, y: 78))
                lines.move(to: CGPoint(x: 124, y: 65)); lines.addLine(to: CGPoint(x: 124, y: 78))
                lines.move(to: CGPoint(x: 112, y: 71.5)); lines.addLine(to: CGPoint(x: 130, y: 71.5))
                context.stroke(lines, with: .color(M.color(0xB6B0BF)), lineWidth: 0.55)
                for i in 0..<3 {
                    var arc = Path()
                    arc.addArc(center: CGPoint(x: 131, y: 40), radius: CGFloat(3 + i * 3), startAngle: .degrees(-48), endAngle: .degrees(48), clockwise: false)
                    context.stroke(arc, with: .color(M.color(0x92909A)), style: StrokeStyle(lineWidth: 0.75, lineCap: .round))
                }
            }
            RoundedRectangle(cornerRadius: 9).strokeBorder(Color.white.opacity(0.85), lineWidth: 0.8)
        }
        .frame(width: 150, height: 94)
        .clipShape(RoundedRectangle(cornerRadius: 9))
    }

    private func pocket(frame: M.Frame) -> some View {
        ZStack {
            rainbowSurface
            LinearGradient(colors: [M.color(0xF0EFF3), M.color(0xDEDBE3)], startPoint: .topLeading, endPoint: .bottomTrailing)
                .opacity(1 - frame.spread)
            Path { path in
                path.move(to: CGPoint(x: 0, y: 1))
                path.addLine(to: CGPoint(x: 87, y: 29))
                path.addLine(to: CGPoint(x: 174, y: 1))
            }
            .stroke(Color.white.opacity(0.6), lineWidth: 0.8)
            Rectangle().fill(M.color(0xD75D99))
                .frame(height: 5).offset(y: 21.5)
            LinearGradient(colors: [Color.white.opacity(0.35), .clear, M.color(0xA54585).opacity(0.2)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        .frame(width: 174, height: 48)
        .clipShape(PrismUnsealSheetPocket())
        .overlay(PrismUnsealSheetPocket().stroke(Color.white.opacity(0.5), lineWidth: 0.8))
        .shadow(color: M.color(0x66445E).opacity(0.1), radius: 2, x: 0, y: 2)
    }

    private var packageDrag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                if dragStartFrame == nil {
                    // Capture every channel: touching during extraction or refolding never jumps.
                    dragStartFrame = frame(at: Date.timeIntervalSinceReferenceDate)
                    hapticCrossed = false
                    handSettled = false
                    touchSerial += 1
                }
                guard let origin = dragStartFrame else { return }
                let delta = -value.translation.height / 180
                let target = delta >= 0 ? M.scrub(1) : M.scrub(0)
                let distance = delta >= 0 ? max(0.001, 1 - origin.progress) : max(0.001, origin.progress)
                var pose = M.blend(origin, target, M.clamp(abs(delta) / distance))
                dragTilt = M.clamp((value.predictedEndTranslation.height - value.translation.height) / 140, -1, 1) * 4
                pose.tilt = dragTilt
                dragFrame = pose
                if pose.progress >= 0.7, !hapticCrossed {
                    hapticCrossed = true
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
            .onEnded { _ in
                guard !reduceMotion else { setManual(1); return }
                let current = dragFrame ?? M.scrub(0)
                releaseFrame = current
                releaseTo = current.progress >= 0.5 ? 1 : 0
                releasedAt = Date.timeIntervalSinceReferenceDate
                dragStartFrame = nil
                dragFrame = nil
                dragTilt = 0
                handSettled = false
                touchSerial += 1
            }
    }

    private func setManual(_ target: Double) {
        let now = Date.timeIntervalSinceReferenceDate
        releaseFrame = frame(at: now)
        releaseTo = target
        releasedAt = now
        dragStartFrame = nil
        dragFrame = nil
        handSettled = false
        touchSerial += 1
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func label(_ text: String, size: Double, weight: Font.Weight = .regular, color: UInt, tracking: Double = 0) -> some View {
        Text(text).font(.system(size: size, weight: weight)).tracking(tracking)
            .foregroundStyle(M.color(color)).fixedSize(horizontal: false, vertical: true)
    }
}

private struct PrismUnsealSheetPocket: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: 0))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.height * 0.58))
        path.addLine(to: CGPoint(x: rect.width, y: 0))
        path.addLine(to: CGPoint(x: rect.width, y: rect.height - 7))
        path.addQuadCurve(to: CGPoint(x: rect.width - 7, y: rect.height), control: CGPoint(x: rect.width, y: rect.height))
        path.addLine(to: CGPoint(x: 7, y: rect.height))
        path.addQuadCurve(to: CGPoint(x: 0, y: rect.height - 7), control: CGPoint(x: 0, y: rect.height))
        path.closeSubpath()
        return path
    }
}

private enum PrismUnsealSheetMotion {
    struct Frame {
        var lid: Double
        var spread: Double
        var extraction: Double
        var ready: Double
        var tilt = 0.0
        var progress = 0.0
    }

    static let rainbow = [color(0xCADC76), color(0xF3CB84), color(0xE25277), color(0x7860AB)]

    static func timeline(_ time: Double) -> Frame {
        let t = max(0, time).truncatingRemainder(dividingBy: PrismUnsealSheet.loop)
        let refold = 1 - smooth(segment(t, 5.9, 6.9))
        let returning = 1 - smooth(segment(t, 5, 5.9))
        return Frame(lid: easeOut(segment(t, 1.4, 2.6)) * refold,
                     spread: smooth(segment(t, 1.55, 2.8)) * refold,
                     extraction: easeOut(segment(t, 2.5, 3.55)) * returning,
                     ready: smooth(segment(t, 3.1, 3.65)) * returning,
                     progress: clamp((t - 1.4) / 2.25) * (1 - smooth(segment(t, 5, 6.9))))
    }

    static func scrub(_ progress: Double, tilt: Double = 0) -> Frame {
        let p = clamp(progress)
        return Frame(lid: p, spread: smooth(p), extraction: clamp((p - 0.45) / 0.55),
                     ready: smooth(segment(p, 0.7, 0.98)), tilt: tilt, progress: p)
    }

    static func blend(_ from: Frame, _ to: Frame, _ q: Double) -> Frame {
        func mix(_ a: Double, _ b: Double) -> Double { a + (b - a) * q }
        return Frame(lid: mix(from.lid, to.lid), spread: mix(from.spread, to.spread),
                     extraction: mix(from.extraction, to.extraction), ready: clamp(mix(from.ready, to.ready)),
                     tilt: mix(from.tilt, to.tilt), progress: clamp(mix(from.progress, to.progress)))
    }

    static func clamp(_ value: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, value)) }
    static func segment(_ t: Double, _ start: Double, _ end: Double) -> Double { clamp((t - start) / (end - start)) }
    static func smooth(_ q: Double) -> Double { q * q * (3 - 2 * q) }
    static func easeOut(_ q: Double) -> Double { 1 - pow(1 - q, 3) }

    /// The same closed-form spring can be sampled by the two ports without accumulating frames.
    static func spring(_ time: Double) -> Double {
        if time >= 0.65 { return 1 }
        let omega = 2 * Double.pi / 0.42
        let damping = 0.82
        let damped = omega * sqrt(1 - damping * damping)
        func response(_ t: Double) -> Double {
            1 - exp(-damping * omega * t) * (cos(damped * t) + damping * omega / damped * sin(damped * t))
        }
        return response(max(0, time)) / response(0.65)
    }

    static func color(_ hex: UInt) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }

    /// Static, seeded, and created once rather than drawn again during every hinge frame.
    static let grain: UIImage = {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 180, height: 140), format: format).image { renderer in
            let context = renderer.cgContext
            context.setFillColor(UIColor.white.cgColor)
            context.fill(CGRect(x: 0, y: 0, width: 180, height: 140))
            for i in 0..<1800 {
                let x = CGFloat((i * 73 + 19) % 180)
                let y = CGFloat((i * 43 + i / 11) % 140)
                let alpha = CGFloat((i * 17) % 70) / 100
                context.setFillColor(UIColor(white: 0.12, alpha: alpha).cgColor)
                context.fill(CGRect(x: x, y: y, width: 1, height: 1))
            }
        }
    }()
}
