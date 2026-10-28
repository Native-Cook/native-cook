import SwiftUI
import UIKit

private enum AuricRedeemPointsReferenceFit {
    static let coinScale: CGFloat = 0.8021340693
    static let coinOffset = CGSize(width: -0.192986, height: 0.831597)
    static let headphonesScale: CGFloat = 0.9448726501
    static let headphonesOffset = CGSize(width: 0.2, height: 1.8963235294)
    static let detailHeadphonesOffset = CGSize(width: headphonesOffset.width * 1.4, height: headphonesOffset.height * 1.4)
    static let sneakersScale: CGFloat = 1.0373679775
    static let sneakersOffset = CGSize(width: -3.802741573, height: -4.887213483)
}

/// Reference-led rewards layout with a bounded, interruptible material-light preview.
struct AuricRedeemPoints: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    var previewDetailProgress: Double? = nil
    var previewReducedMotion = false
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    private var reduceMotion: Bool { systemReduceMotion || previewReducedMotion }
    @State private var detailsVisible = false
    @State private var detailsReady = false
    @State private var detailsRequestedOpen = false
    @State private var detailProgress = 0.0
    @State private var detailGeneration = 0
    @AccessibilityFocusState private var detailCloseFocused: Bool
    @AccessibilityFocusState private var redeemFocused: Bool
    @State private var motionReset = 0
    @State private var message: String?
    private let gold = Color(hex: 0xFFC43B)
    private let muted = Color(hex: 0xC28DBB)

    private static let art: [String: UIImage] = {
        var result: [String: UIImage] = [:]
        for name in ["coin", "headphones", "sneakers"] {
            if let path = Bundle.main.path(forResource: "AuricRedeemPoints-\(name)", ofType: "png"),
               let image = UIImage(contentsOfFile: path) {
                result[name] = image
            }
        }
        return result
    }()

    private static let grain: UIImage? = {
        let side = 96
        var bytes = [UInt8](repeating: 255, count: side * side * 4)
        for i in 0..<(side * side) {
            var hash = UInt32(i) &* 0x45D9F3B
            hash = (hash ^ (hash >> 16)) &* 0x45D9F3B
            let value = UInt8(truncatingIfNeeded: hash ^ (hash >> 16))
            bytes[i * 4] = value; bytes[i * 4 + 1] = value; bytes[i * 4 + 2] = value
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData),
              let image = CGImage(width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: side * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                                  provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent) else { return nil }
        return UIImage(cgImage: image)
    }()

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 393, geometry.size.height / 852)
            composition
                .frame(width: 393, height: 852, alignment: .topLeading)
                .scaleEffect(scale, anchor: .topLeading)
                .frame(width: 393 * scale, height: 852 * scale, alignment: .topLeading)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(background.ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .background {
            if detailsVisible {
                Button(action: closeDetailsImmediately) {
                    Color.clear.frame(width: 1, height: 1)
                }
                .keyboardShortcut(.cancelAction)
                .accessibilityHidden(true)
            }
        }
        .alert("Rewards", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
        .task(id: detailGeneration) {
            guard detailsVisible else { return }
            let generation = detailGeneration
            if detailsRequestedOpen {
                try? await Task.sleep(for: .milliseconds(16))
                guard !Task.isCancelled, detailsRequestedOpen, generation == detailGeneration else { return }
                withAnimation(reduceMotion ? .easeOut(duration: 0.15) : .spring(response: 0.3, dampingFraction: 0.85), completionCriteria: .logicallyComplete) {
                    detailProgress = 1
                } completion: {
                    if detailsRequestedOpen && generation == detailGeneration {
                        detailsReady = true
                        detailCloseFocused = true
                    }
                }
            } else {
                withAnimation(reduceMotion ? .easeOut(duration: 0.12) : .timingCurve(0.23, 1, 0.32, 1, duration: 0.20), completionCriteria: .logicallyComplete) {
                    detailProgress = 0
                } completion: {
                    guard !detailsRequestedOpen, generation == detailGeneration else { return }
                    detailsVisible = false
                    detailCloseFocused = false
                    redeemFocused = true
                }
            }
        }
        .onChange(of: reduceMotion) { _, reduced in
            guard reduced, detailsVisible else { return }
            var transaction = Transaction(); transaction.disablesAnimations = true
            withTransaction(transaction) {
                detailProgress = detailsRequestedOpen ? 1 : 0
                if !detailsRequestedOpen { detailsVisible = false }
            }
        }
    }

    private var background: some View {
        ZStack {
            Color(hex: 0x20032F)
            RadialGradient(stops: [
                .init(color: Color(hex: 0xAA0C62), location: 0),
                .init(color: Color(hex: 0x57054C), location: 0.46),
                .init(color: Color(hex: 0x20032F), location: 1)
            ], center: UnitPoint(x: 0.49, y: 0.51), startRadius: 0, endRadius: 410)
            RadialGradient(colors: [Color(hex: 0x3D38B9).opacity(0.35), .clear],
                           center: UnitPoint(x: 1.03, y: 0.59), startRadius: 0, endRadius: 360)
            LinearGradient(colors: [.black.opacity(0.18), .clear, .black.opacity(0.26)], startPoint: .top, endPoint: .bottom)
            if let image = Self.grain {
                Image(uiImage: image).resizable(resizingMode: .tile).opacity(0.035).blendMode(.softLight)
            }
        }.accessibilityHidden(true)
    }

    private var showingDetails: Bool { detailsVisible || previewDetailProgress != nil }

    private var composition: some View {
        ZStack(alignment: .topLeading) {
            baseComposition
                .accessibilityElement(children: showingDetails ? .ignore : .contain)
                .accessibilityHidden(showingDetails).allowsHitTesting(!showingDetails)
            if showingDetails {
                AuricRedeemPointsDetailMorph(
                    progress: previewDetailProgress ?? detailProgress,
                    reduceMotion: reduceMotion,
                    image: Self.art["headphones"],
                    backdrop: background,
                    closeFocus: $detailCloseFocused,
                    onClose: closeDetails,
                    focusEnabled: previewDetailProgress == nil,
                    interactive: previewDetailProgress.map { $0 >= 1 } ?? detailsReady)
                    .accessibilityAddTraits(.isModal)
                    .accessibilityAction(.escape) { closeDetailsImmediately() }

                    .frame(width: 393, height: 852, alignment: .topLeading)
                    .transition(.identity)
            }
        }
    }

    private func openDetails() {
        guard !detailsVisible else { return }
        motionReset += 1
        detailProgress = 0
        detailsVisible = true
        detailsReady = false
        detailsRequestedOpen = true
        detailGeneration += 1
    }

    private func closeDetails() {
        guard detailsVisible, detailsRequestedOpen else { return }
        motionReset += 1
        detailsReady = false
        detailsRequestedOpen = false
        detailGeneration += 1
    }

    private func closeDetailsImmediately() {
        guard detailsVisible else { return }
        detailGeneration += 1
        motionReset += 1
        var transaction = Transaction(); transaction.disablesAnimations = true
        withTransaction(transaction) {
            detailsRequestedOpen = false
            detailsReady = false
            detailProgress = 0
            detailsVisible = false
            detailCloseFocused = false
            redeemFocused = true
        }
    }

    private var baseComposition: some View {
        ZStack(alignment: .topLeading) {
            background
            AuricRedeemPointsMint(image: Self.art["coin"], autoPlay: autoPlay && !showingDetails && message == nil, previewTime: previewTime, interruption: motionReset, isObscured: showingDetails || message != nil, reduceMotionOverride: previewReducedMotion)
                .position(x: 196, y: 151)
            HStack(alignment: .firstTextBaseline, spacing: 5) {
                Text("120").font(.system(size: 53, weight: .bold)).tracking(-1.5)
                Text("pts").font(.system(size: 31, weight: .semibold)).baselineOffset(16)
            }.foregroundStyle(.white).position(x: 196.5, y: 267)
                .accessibilityElement(children: .ignore).accessibilityLabel("120 points")
            Text("Available to Redeem").font(.system(size: 16)).foregroundStyle(muted)
                .position(x: 196.5, y: 314)
            tierRail.position(x: 196.5, y: 371)
            HStack {
                Text("Redeem Rewards").font(.system(size: 18, weight: .semibold))
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 18, weight: .medium)).accessibilityHidden(true)
            }.foregroundStyle(.white).frame(width: 359).position(x: 196.5, y: 418)
            rewardCard(expired: false).position(x: 106, y: 544)
                .opacity(showingDetails && !reduceMotion ? 0 : 1)
            rewardCard(expired: true).position(x: 289, y: 544)
            Text("Please note that offers are updated weekly")
                .font(.system(size: 11.5)).foregroundStyle(muted.opacity(0.8))
                .position(x: 196.5, y: 681)
            Button {
                motionReset += 1
                message = "Keep earning points to unlock your next offer."
            } label: {
                Text("Claim Offer").font(.system(size: 17, weight: .semibold))
                    .frame(width: 359, height: 50)
                    .background(goldGradient, in: Capsule())
                    .overlay(Capsule().stroke(.white.opacity(0.14), lineWidth: 1))
            }.buttonStyle(AuricRedeemPointsPillStyle()).foregroundStyle(.black).position(x: 196.5, y: 719)
            Button { motionReset += 1; message = "Your offers will be here when you return." } label: {
                Text("Not Now").font(.system(size: 17, weight: .semibold))
                    .frame(width: 359, height: 50)
                    .background(Color(hex: 0xBAAFC2).opacity(0.6), in: Capsule())
            }.buttonStyle(AuricRedeemPointsPillStyle()).foregroundStyle(.black).position(x: 196.5, y: 780)
        }
    }

    private var tierRail: some View {
        ZStack(alignment: .topLeading) {
            Capsule().fill(.white.opacity(0.14)).frame(width: 359, height: 6).offset(y: 3)
            Capsule().fill(Color(hex: 0xF29724)).frame(width: 127, height: 6).offset(y: 3)
            Circle().fill(Color(hex: 0xF29724)).overlay(Circle().stroke(.white, lineWidth: 1.5))
                .frame(width: 14, height: 14).offset(x: 120, y: -1)
            Text("675pts").font(.system(size: 8)).foregroundStyle(.white).offset(y: 19)
            ForEach(Array(["Bronze", "Silver", "Gold"].enumerated()), id: \.offset) { index, name in
                let color: Color = [Color(hex: 0xD7811E), Color(hex: 0xADB7D2), Color(hex: 0xFFCE09)][index]
                VStack(spacing: 5) {
                    Circle().fill(LinearGradient(colors: [color, color.opacity(0.65)], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .overlay(Circle().stroke(.white.opacity(0.45), lineWidth: 1))
                        .overlay(Image(systemName: index == 2 ? "trophy.fill" : "star.fill").font(.system(size: 8)).foregroundStyle(.white.opacity(0.65)).accessibilityHidden(true))
                        .frame(width: 18, height: 18)
                    Text(name).font(.system(size: 8)).foregroundStyle(muted)
                }.frame(width: 36).offset(x: [187.0, 260.0, 330.0][index], y: -3)
            }
        }.frame(width: 359, height: 38)
    }

    private func rewardCard(expired: Bool) -> some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 26)
                .fill(expired ? Color(hex: 0x3B2B73).opacity(0.45) : Color(hex: 0x893C58).opacity(0.4))
                .overlay {
                    if !expired {
                        RadialGradient(colors: [Color(hex: 0xC88629).opacity(0.7), .clear], center: UnitPoint(x: 0.49, y: 0.28), startRadius: 3, endRadius: 125)
                            .clipShape(RoundedRectangle(cornerRadius: 26))
                    }
                }
                .overlay(RoundedRectangle(cornerRadius: 26).stroke(expired ? .white.opacity(0.07) : gold.opacity(0.65), lineWidth: 0.75))
            let artOffset = expired ? AuricRedeemPointsReferenceFit.sneakersOffset : AuricRedeemPointsReferenceFit.headphonesOffset
            artwork(expired ? "sneakers" : "headphones", width: expired ? 124 : 97, height: expired ? 104 : 120)
                .scaleEffect(expired ? AuricRedeemPointsReferenceFit.sneakersScale : AuricRedeemPointsReferenceFit.headphonesScale)
                .offset(x: artOffset.width, y: artOffset.height)
                .offset(y: expired ? 0 : -10)
                .opacity(expired ? 0.85 : 1)
            Text(expired ? "Aster Sneakers" : "Aural Headphones")
                .font(.system(size: 12.5)).foregroundStyle(expired ? muted.opacity(0.35) : .white.opacity(0.85))
                .offset(y: 95)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(expired ? "450" : "600").font(.system(size: 23, weight: .bold))
                Text("pts").font(.system(size: 11, weight: .semibold)).baselineOffset(4)
            }.foregroundStyle(expired ? muted.opacity(0.35) : .white).offset(y: 115)
            Button {
                motionReset += 1
                openDetails()
            } label: {
                Text(expired ? "Expired" : "Redeem").font(.system(size: 12, weight: .semibold))
                    .frame(width: 132, height: 29)
                    .background {
                        if expired { Capsule().fill(Color(hex: 0xBD884B).opacity(0.25)) }
                        else { Capsule().fill(goldGradient) }
                    }.padding(.vertical, 7.5).contentShape(Rectangle())
            }.buttonStyle(AuricRedeemPointsPillStyle()).foregroundStyle(expired ? Color.black.opacity(0.4) : .black)
                .disabled(expired).offset(y: 141.5)
                .accessibilityLabel(expired ? "Sneaker reward expired" : "Redeem headphones for 600 points")
                .modifier(AuricRedeemPointsFocus(enabled: !expired && previewTime == nil, focus: $redeemFocused))
        }.frame(width: 177, height: 193)
    }

    private var goldGradient: LinearGradient {
        LinearGradient(stops: [.init(color: Color(hex: 0xF3A82F), location: 0), .init(color: Color(hex: 0xFAC672), location: 0.6), .init(color: Color(hex: 0xFED99B), location: 1)], startPoint: .top, endPoint: .bottom)
    }

    @ViewBuilder private func artwork(_ name: String, width: CGFloat, height: CGFloat) -> some View {
        if let image = Self.art[name] {
            Image(uiImage: image).resizable().scaledToFit().frame(width: width, height: height).accessibilityHidden(true)
        } else {
            Color.clear.frame(width: width, height: height)
        }
    }


}

private struct AuricRedeemPointsPillStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.88 : 1)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.975 : 1)
            .animation(.easeOut(duration: configuration.isPressed ? 0.12 : 0.1), value: configuration.isPressed)
    }
}

private struct AuricRedeemPointsSparkle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: rect.midY), control: CGPoint(x: rect.midX, y: rect.midY))
        path.addQuadCurve(to: CGPoint(x: rect.midX, y: rect.maxY), control: CGPoint(x: rect.midX, y: rect.midY))
        path.addQuadCurve(to: CGPoint(x: rect.minX, y: rect.midY), control: CGPoint(x: rect.midX, y: rect.midY))
        path.addQuadCurve(to: CGPoint(x: rect.midX, y: rect.minY), control: CGPoint(x: rect.midX, y: rect.midY))
        path.closeSubpath()
        return path
    }
}


private struct AuricRedeemPointsMintVelocity {
    var offsetX = 0.0
    var lift = 0.0
    var yaw = 0.0
    var pitch = 0.0
    var roll = 0.0
    var scaleX = 0.0
    var scaleY = 0.0
    static let zero = Self()
}

/// All values describe the same material response under either a finger or the demo clock.
private struct AuricRedeemPointsMintPose {
    var offsetX = 0.0
    var lift = 0.0
    var yaw = 0.0
    var pitch = 0.0
    var roll = 0.0
    var scaleX = 1.0
    var scaleY = 1.0
    var sweep = -1.0
    var light = 0.0
    var morph = 0.0
    var streak = 0.0
    var eventAge = -1.0

    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(b, max(a, x)) }
    static func smooth(_ value: Double) -> Double {
        let x = clamp(value)
        return x * x * (3 - 2 * x)
    }
    static func pulse(_ t: Double, _ start: Double, _ duration: Double) -> Double {
        let x = (t - start) / duration
        return x > 0 && x < 1 ? pow(sin(.pi * x), 2) : 0
    }
    static func rubber(_ value: Double, limit: Double) -> Double {
        let excess = max(0, abs(value) - limit)
        return copysign(min(abs(value), limit) + 4 * excess / (excess + 20), value)
    }
    static func rubberDerivative(_ value: Double, limit: Double) -> Double {
        let excess = abs(value) - limit
        return excess <= 0 ? 1 : 80 / pow(excess + 20, 2)
    }
    static func dragAngle(origin: Double, displacement: Double, limit: Double, hardLimit: Double) -> Double {
        clamp(origin + rubber(origin + displacement, limit: limit) - rubber(origin, limit: limit), -hardLimit, hardLimit)
    }
    static func dragVelocity(origin: Double, displacement: Double, velocity: Double, limit: Double, hardLimit: Double) -> Double {
        let raw = origin + rubber(origin + displacement, limit: limit) - rubber(origin, limit: limit)
        return abs(raw) >= hardLimit ? 0 : velocity * rubberDerivative(origin + displacement, limit: limit)
    }
    static func spring(_ t: Double, from: Double, velocity: Double = 0) -> Double {
        guard t < 1.2 else { return 0 }
        // Analytic underdamped spring. Position and velocity both survive a re-grab.
        let omega = 16.0, damping = 0.8
        let decay = omega * damping, frequency = omega * sqrt(1 - damping * damping)
        return exp(-decay * t) * (from * cos(frequency * t) + (velocity + decay * from) / frequency * sin(frequency * t))
    }
    static func release(_ t: Double, from origin: Self, velocity: AuricRedeemPointsMintVelocity, celebrate: Bool) -> Self {
        let time = max(0, t)
        let flash = celebrate ? pulse(time, 0.04, 0.38) : 0
        var p = Self()
        let liftPulse = celebrate ? pulse(time, 0, 0.66) : 0
        let expansion = celebrate ? 0.055 * pulse(time, 0.04, 0.48) : 0
        p.offsetX = spring(time, from: origin.offsetX, velocity: clamp(velocity.offsetX, -240, 240))
        p.lift = spring(time, from: origin.lift, velocity: clamp(velocity.lift, -160, 160)) - 16 * liftPulse
        p.yaw = clamp(spring(time, from: origin.yaw, velocity: clamp(velocity.yaw, -280, 280)), -24, 24)
        p.pitch = clamp(spring(time, from: origin.pitch, velocity: clamp(velocity.pitch, -160, 160)), -14, 14)
        p.roll = spring(time, from: origin.roll, velocity: clamp(velocity.roll, -160, 160))
        p.scaleX = 1 + spring(time, from: origin.scaleX - 1, velocity: velocity.scaleX) + expansion
        p.scaleY = 1 + spring(time, from: origin.scaleY - 1, velocity: velocity.scaleY) + expansion
        p.sweep = origin.sweep + (1 - origin.sweep) * smooth(time / 0.38)
        p.light = max(origin.light * (1 - smooth(time / 0.42)), 0.45 * flash)
        p.morph = origin.morph * (1 - smooth(time / 0.42)) + 0.65 * flash
        p.streak = origin.streak * (1 - smooth(time / 0.48)) + 0.8 * flash
        p.eventAge = celebrate ? time : -1
        return p
    }
    static func preview(_ time: Double) -> Self {
        let t = max(0, time).truncatingRemainder(dividingBy: AuricRedeemPoints.loop)
        if t < 0.8 { return Self() }
        var compressed = Self(offsetX: -4, lift: 2, yaw: -10, roll: -8, scaleX: 1.045, scaleY: 0.9, morph: 0.7, streak: 0.4)
        if t < 0.96 {
            let p = smooth((t - 0.8) / 0.16)
            compressed.offsetX *= p; compressed.lift *= p; compressed.yaw *= p; compressed.roll *= p
            compressed.scaleX = 1 + 0.045 * p; compressed.scaleY = 1 - 0.1 * p
            compressed.morph *= p; compressed.streak *= p
            return compressed
        }
        if t < 2.16 { return release(t - 0.96, from: compressed, velocity: AuricRedeemPointsMintVelocity(offsetX: 120, yaw: 240, roll: 120), celebrate: true) }
        if t < 6.8 {
            let u = (t - 2.16) / 4.64
            let envelope = pow(sin(.pi * u), 2)
            let turn = sin(2 * .pi * u) * envelope
            return Self(offsetX: 18 * turn, lift: -10 * envelope, yaw: 24 * turn, pitch: 5 * envelope,
                        roll: 12 * turn, scaleX: 1 + 0.025 * envelope, scaleY: 1 + 0.025 * envelope,
                        sweep: turn, light: 0.34 * envelope, morph: 0.65 * envelope, streak: 0.35 * envelope)
        }
        return Self()
    }
}

private struct AuricRedeemPointsMintContact {
    let origin: AuricRedeemPointsMintPose
    let start: Date
    var translation = CGSize.zero
    var velocity = CGSize.zero
}

private struct AuricRedeemPointsMintRelease {
    let origin: AuricRedeemPointsMintPose
    let start: Date
    let velocity: AuricRedeemPointsMintVelocity
    let celebrate: Bool
}

/// The glint's four quadratic controls move from a concave star to a convex optical lens.
/// This is an actual path morph; the ellipse-like streak is not a crossfade between icons.
private struct AuricRedeemPointsOpticalMorph: Shape {
    var amount: Double
    var animatableData: Double { get { amount } set { amount = newValue } }
    func path(in r: CGRect) -> Path {
        let m = AuricRedeemPointsMintPose.clamp(amount)
        let dx = r.width * 0.5 * m, dy = r.height * 0.5 * m
        var path = Path()
        path.move(to: CGPoint(x: r.midX, y: r.minY))
        path.addQuadCurve(to: CGPoint(x: r.maxX, y: r.midY), control: CGPoint(x: r.midX + dx, y: r.midY - dy))
        path.addQuadCurve(to: CGPoint(x: r.midX, y: r.maxY), control: CGPoint(x: r.midX + dx, y: r.midY + dy))
        path.addQuadCurve(to: CGPoint(x: r.minX, y: r.midY), control: CGPoint(x: r.midX - dx, y: r.midY + dy))
        path.addQuadCurve(to: CGPoint(x: r.midX, y: r.minY), control: CGPoint(x: r.midX - dx, y: r.midY - dy))
        path.closeSubpath()
        return path
    }
}

private struct AuricRedeemPointsMint: View {
    let image: UIImage?
    let autoPlay: Bool
    let previewTime: Double?
    let interruption: Int
    let isObscured: Bool
    var reduceMotionOverride = false
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    private var reduceMotion: Bool { systemReduceMotion || reduceMotionOverride }
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var gestureActive = false
    @State private var epoch = Date()
    @State private var contact: AuricRedeemPointsMintContact?
    @State private var release: AuricRedeemPointsMintRelease?
    @State private var resumeDate: Date?
    @State private var reducedFeedback = false
    @State private var pausedPose: AuricRedeemPointsMintPose?
    @State private var impactFeedback: UIImpactFeedbackGenerator?

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 120,
                                paused: reduceMotion || isObscured || scenePhase != .active || previewTime != nil || (!autoPlay && contact == nil && release == nil))) { context in
            let p = pose(at: context.date)
            ZStack {
                reflectedLight(p).offset(y: -4)
                coinBody(p).offset(y: -4)
                sourceStars(p)
                transientGlints(p).offset(y: -4)
            }
            .frame(width: 220, height: 240)
        }
        .frame(width: 180, height: 190)
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, state, _ in state = true }
            .onChanged { value in
                guard !isObscured else { return }
                let now = Date()
                if contact == nil && !reduceMotion {
                    if impactFeedback == nil { impactFeedback = UIImpactFeedbackGenerator(style: .light) }
                    impactFeedback?.prepare()
                }
                var next = contact ?? AuricRedeemPointsMintContact(origin: pose(at: now), start: now)
                next.translation = value.translation; next.velocity = value.velocity
                contact = next; release = nil; resumeDate = nil
                if reduceMotion { withAnimation(.easeOut(duration: 0.12)) { reducedFeedback = true } }
            }
            .onEnded { value in
                finish(translation: value.translation, velocity: value.velocity, celebrate: true)
            })
        .onChange(of: gestureActive) { _, active in
            if !active && contact != nil { finish(translation: contact?.translation ?? .zero, velocity: .zero, celebrate: false) }
        }
        .onChange(of: interruption) { _, _ in
            guard !isObscured else { return }
            release = AuricRedeemPointsMintRelease(origin: pose(at: Date()), start: Date(), velocity: .zero, celebrate: false)
            contact = nil; resumeDate = Date()
            withAnimation(.easeOut(duration: 0.15)) { reducedFeedback = false }
        }
        .onChange(of: isObscured) { _, obscured in
            let now = Date()
            if obscured {
                pausedPose = pose(at: now)
                contact = nil; release = nil; resumeDate = nil
                reducedFeedback = false
            } else {
                let origin = pausedPose ?? AuricRedeemPointsMintPose()
                pausedPose = nil
                release = AuricRedeemPointsMintRelease(origin: origin, start: now, velocity: .zero, celebrate: false)
                resumeDate = now
            }
        }
        .task(id: resumeDate) {
            guard resumeDate != nil else { return }
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled, contact == nil, !isObscured else { return }
            release = nil; resumeDate = nil; epoch = Date()
        }
        .task(id: reducedFeedback) {
            guard reducedFeedback, contact == nil else { return }
            try? await Task.sleep(for: .milliseconds(150))
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.15)) { reducedFeedback = false }
        }
        .onChange(of: scenePhase) { _, _ in
            contact = nil; release = nil; resumeDate = nil; pausedPose = nil; reducedFeedback = false; epoch = Date()
        }
        .onChange(of: reduceMotion) { _, _ in
            contact = nil; release = nil; resumeDate = nil; pausedPose = nil; reducedFeedback = false; epoch = Date()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Gold rewards coin")
        .accessibilityHint("Activate to preview the gold finish. Your points do not change.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            guard !isObscured else { return }
            if reduceMotion { reducedFeedback = true }
            else {
                let origin = AuricRedeemPointsMintPose(scaleX: 1.045, scaleY: 0.9, morph: 0.7)
                release = AuricRedeemPointsMintRelease(origin: origin, start: Date(), velocity: .zero, celebrate: true)
            }
            resumeDate = Date()
        }
    }

    private var coin: some View {
        Group {
            if let image { Image(uiImage: image).resizable().scaledToFit() }
            else { Color.clear }
        }
        .frame(width: 180, height: 190)
        .scaleEffect(AuricRedeemPointsReferenceFit.coinScale)
        .offset(x: AuricRedeemPointsReferenceFit.coinOffset.width, y: AuricRedeemPointsReferenceFit.coinOffset.height)
    }

    private func coinBody(_ p: AuricRedeemPointsMintPose) -> some View {
        coin
            .opacity(reducedFeedback ? 0.84 : 1)
            .overlay {
                // A narrow warm band preserves the relief instead of washing the whole face white.
                LinearGradient(stops: [.init(color: .clear, location: 0.455),
                                       .init(color: Color(red: 1, green: 0.94, blue: 0.72).opacity(p.light), location: 0.5),
                                       .init(color: .clear, location: 0.545)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
                    .frame(width: 180, height: 190)
                    .offset(x: p.sweep * 105)
                    .mask(coin)
                    .blendMode(.screen)
                    .allowsHitTesting(false)
            }
            .overlay {
                AuricRedeemPointsOpticalMorph(amount: p.morph).fill(.white)
                    .frame(width: 9 + 3 * p.streak, height: 16 + 12 * p.streak)
                    .rotationEffect(.degrees(22 + 10 * p.streak))
                    .shadow(color: Color.yellow.opacity(0.7), radius: 3)
                    .offset(x: 39 + 4 * p.streak, y: -51 + 6 * p.streak)
                    .allowsHitTesting(false)
            }
            .scaleEffect(x: p.scaleX, y: p.scaleY)
            .rotationEffect(.degrees(p.roll))
            .rotation3DEffect(.degrees(p.yaw), axis: (x: 0, y: 1, z: 0), perspective: 0.25)
            .rotation3DEffect(.degrees(p.pitch), axis: (x: 1, y: 0, z: 0), perspective: 0.25)
            .offset(x: p.offsetX, y: p.lift)
    }

    private func reflectedLight(_ p: AuricRedeemPointsMintPose) -> some View {
        let charge = AuricRedeemPointsMintPose.smooth(p.light / 0.45)
        return Ellipse().fill(RadialGradient(stops: [
            .init(color: Color(hex: 0xFFD35A).opacity(0), location: 0),
            .init(color: Color(hex: 0xFFD35A).opacity(0.24), location: 0.55),
            .init(color: Color(hex: 0xF29724).opacity(0.12), location: 0.80),
            .init(color: .clear, location: 1)
        ], center: UnitPoint(x: 0.5 + 0.08 * p.yaw / 24, y: 0.5), startRadius: 0, endRadius: 86))
            .frame(width: 210, height: 210)
            .scaleEffect(1 + 0.04 * charge).opacity(charge)
            .offset(x: p.offsetX * 0.15, y: 7 + p.lift * 0.2)
            .allowsHitTesting(false)
    }

    private func sourceStars(_ p: AuricRedeemPointsMintPose) -> some View {
        ZStack {
            sourceStar(size: 26, pulse: AuricRedeemPointsMintPose.pulse(p.eventAge, 0.15, 0.3), angle: -1).offset(x: -78, y: -27)
            sourceStar(size: 30, pulse: AuricRedeemPointsMintPose.pulse(p.eventAge, 0.19, 0.3), angle: 1).offset(x: 79, y: 4)
            sourceStar(size: 7).offset(x: -78, y: 26)
            sourceStar(size: 7).offset(x: -37, y: 79)
            sourceStar(size: 7).offset(x: 57, y: 66)
        }.allowsHitTesting(false)
    }

    private func sourceStar(size: Double, pulse: Double = 0, angle: Double = 0) -> some View {
        AuricRedeemPointsOpticalMorph(amount: 0.08 + pulse * 0.14)
            .fill(LinearGradient(colors: [Color(hex: 0xFBE078), Color(hex: 0xFBA534)], startPoint: .topLeading, endPoint: .bottomTrailing))
            .frame(width: size, height: size * 1.4)
            .scaleEffect(1 + pulse * 0.22)
            .rotationEffect(.degrees(angle * pulse * 10))
            .accessibilityHidden(true)
    }

    private func transientGlints(_ p: AuricRedeemPointsMintPose) -> some View {
        Canvas { context, size in
            guard p.eventAge >= 0 else { return }
            for i in 0..<6 {
                let delay = 0.17 + Double(i) * 0.018
                let life = (p.eventAge - delay) / 0.28
                guard life > 0 && life < 1 else { continue }
                let angle = (-140 + Double(i) * 29) * .pi / 180
                let distance = 54 + 17 * AuricRedeemPointsMintPose.smooth(life)
                let radius = (0.7 + Double(i % 3) * 0.3) * sin(.pi * life)
                let point = CGPoint(x: size.width / 2 + cos(angle) * distance, y: size.height / 2 - 10 + sin(angle) * distance)
                let rect = CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)
                context.fill(Path(ellipseIn: rect), with: .color(Color(hex: 0xFFE6A0).opacity(0.55 * sin(.pi * life))))
            }
        }.frame(width: 220, height: 240).allowsHitTesting(false)
    }

    private func pressedPose(_ touch: AuricRedeemPointsMintContact, at date: Date, translation: CGSize) -> AuricRedeemPointsMintPose {
        let progress = 1 - pow(1 - AuricRedeemPointsMintPose.clamp(date.timeIntervalSince(touch.start) / 0.1), 3)
        var p = touch.origin
        p.scaleX += (1.045 - p.scaleX) * progress
        p.scaleY += (0.9 - p.scaleY) * progress
        p.offsetX = AuricRedeemPointsMintPose.dragAngle(origin: touch.origin.offsetX, displacement: Double(translation.width) * 0.28, limit: 20, hardLimit: 24)
        p.lift = AuricRedeemPointsMintPose.dragAngle(origin: touch.origin.lift, displacement: Double(translation.height) * 0.18, limit: 14, hardLimit: 18)
        p.yaw = AuricRedeemPointsMintPose.dragAngle(origin: touch.origin.yaw, displacement: Double(translation.width) * 0.32, limit: 20, hardLimit: 24)
        p.pitch = AuricRedeemPointsMintPose.dragAngle(origin: touch.origin.pitch, displacement: -Double(translation.height) * 0.13, limit: 10, hardLimit: 14)
        p.roll = AuricRedeemPointsMintPose.dragAngle(origin: touch.origin.roll, displacement: Double(translation.width) * 0.08, limit: 8, hardLimit: 12)
        p.sweep += (p.yaw / 24 - p.sweep) * progress
        p.light += (0.3 - p.light) * progress
        p.morph += (0.65 - p.morph) * progress
        p.streak += (0.4 - p.streak) * progress
        // Complete any prior glint instead of restarting it when the user grabs mid-flight.
        p.eventAge = touch.origin.eventAge >= 0 ? touch.origin.eventAge + date.timeIntervalSince(touch.start) : -1
        return p
    }

    private func pressedVelocity(_ touch: AuricRedeemPointsMintContact, at date: Date, translation: CGSize, velocity: CGSize) -> AuricRedeemPointsMintVelocity {
        let u = AuricRedeemPointsMintPose.clamp(date.timeIntervalSince(touch.start) / 0.1)
        let compressionSpeed = u < 1 ? 30 * pow(1 - u, 2) : 0
        return AuricRedeemPointsMintVelocity(
            offsetX: AuricRedeemPointsMintPose.dragVelocity(origin: touch.origin.offsetX, displacement: Double(translation.width) * 0.28, velocity: Double(velocity.width) * 0.28, limit: 20, hardLimit: 24),
            lift: AuricRedeemPointsMintPose.dragVelocity(origin: touch.origin.lift, displacement: Double(translation.height) * 0.18, velocity: Double(velocity.height) * 0.18, limit: 14, hardLimit: 18),
            yaw: AuricRedeemPointsMintPose.dragVelocity(origin: touch.origin.yaw, displacement: Double(translation.width) * 0.32, velocity: Double(velocity.width) * 0.32, limit: 20, hardLimit: 24),
            pitch: AuricRedeemPointsMintPose.dragVelocity(origin: touch.origin.pitch, displacement: -Double(translation.height) * 0.13, velocity: -Double(velocity.height) * 0.13, limit: 10, hardLimit: 14),
            roll: AuricRedeemPointsMintPose.dragVelocity(origin: touch.origin.roll, displacement: Double(translation.width) * 0.08, velocity: Double(velocity.width) * 0.08, limit: 8, hardLimit: 12),
            scaleX: (1.045 - touch.origin.scaleX) * compressionSpeed,
            scaleY: (0.9 - touch.origin.scaleY) * compressionSpeed)
    }

    private func pose(at date: Date) -> AuricRedeemPointsMintPose {
        if reduceMotion { return AuricRedeemPointsMintPose() }
        if let previewTime { return .preview(previewTime) }
        if let pausedPose { return pausedPose }
        if let contact { return pressedPose(contact, at: date, translation: contact.translation) }
        if let release {
            return .release(date.timeIntervalSince(release.start), from: release.origin, velocity: release.velocity, celebrate: release.celebrate)
        }
        return autoPlay && resumeDate == nil ? .preview(date.timeIntervalSince(epoch)) : AuricRedeemPointsMintPose()
    }

    private func finish(translation: CGSize, velocity: CGSize, celebrate: Bool) {
        guard let contact, !isObscured else { return }
        let now = Date()
        let origin = pressedPose(contact, at: now, translation: translation)
        let mapped = pressedVelocity(contact, at: now, translation: translation, velocity: velocity)
        release = AuricRedeemPointsMintRelease(origin: origin, start: now, velocity: mapped, celebrate: celebrate)
        self.contact = nil; resumeDate = now
        withAnimation(.easeOut(duration: 0.15)) { reducedFeedback = false }
        if celebrate && !reduceMotion { impactFeedback?.impactOccurred(intensity: 0.6) }
    }
}

/// A single GPU-drawn surface and fixed native content preserve the selected card's identity.
/// Geometry is derived from one normalized progress; no sibling layout is animated.
private struct AuricRedeemPointsDetailMorph<Backdrop: View>: View, Animatable {
    var progress: Double
    let reduceMotion: Bool
    let image: UIImage?
    let backdrop: Backdrop
    let closeFocus: AccessibilityFocusState<Bool>.Binding
    let onClose: () -> Void
    let focusEnabled: Bool
    let interactive: Bool
    var animatableData: Double { get { progress } set { progress = newValue } }

    private var p: Double { AuricRedeemPointsMintPose.clamp(progress) }
    private var g: Double { reduceMotion ? 1 : p }
    private func mix(_ a: Double, _ b: Double) -> CGFloat { CGFloat(a + (b - a) * g) }
    private var surface: CGRect { CGRect(x: mix(17.5, 17), y: mix(447.5, 442), width: mix(177, 359), height: mix(193, 348)) }
    private var centerX: CGFloat { mix(106, 196.5) }
    private var buttonWidth: CGFloat { mix(132, 315) }
    private var buttonHeight: CGFloat { mix(29, 44) }
    private var buttonCenterY: CGFloat { mix(611, 738) }
    private var targetAlpha: Double { AuricRedeemPointsMintPose.smooth((g - 0.55) / 0.35) }
    private var actionAlpha: Double { AuricRedeemPointsMintPose.smooth((g - 0.15) / 0.30) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.opacity(reduceMotion ? 0.36 : 0.36 * p)
                .frame(width: 393, height: 852).contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { value in
                    if !surface.contains(value.location) { onClose() }
                }).accessibilityHidden(true)
            backdrop.mask {
                Canvas { context, _ in
                    context.fill(Path(roundedRect: surface, cornerRadius: mix(26, 32)), with: .color(.white))
                }.frame(width: 393, height: 852)
            }.allowsHitTesting(false).accessibilityHidden(true)
            surfaceDrawing.allowsHitTesting(false)
            // Keep a tap on the surface from falling through to the backdrop.
            Color.clear.frame(width: 359, height: 348)
                .scaleEffect(x: surface.width / 359, y: surface.height / 348, anchor: .topLeading)
                .offset(x: surface.minX, y: surface.minY)
                .contentShape(Rectangle()).onTapGesture {}.accessibilityHidden(true)
            if let image {
                Image(uiImage: image).resizable().scaledToFit().frame(width: 135.8, height: 168)
                    .scaleEffect(AuricRedeemPointsReferenceFit.headphonesScale)
                    .offset(x: AuricRedeemPointsReferenceFit.detailHeadphonesOffset.width, y: AuricRedeemPointsReferenceFit.detailHeadphonesOffset.height)
                    .scaleEffect(mix(1 / 1.4, 1))
                    .offset(x: centerX - 67.9, y: mix(497.5, 538) - 84)
                    .accessibilityHidden(true).allowsHitTesting(false)
            }
            Text("Aural Headphones").font(.system(size: 12.5)).foregroundStyle(.white.opacity(0.85))
                .frame(width: 177, height: 22, alignment: .top)
                .scaleEffect(1 + 0.6 * g, anchor: .top)
                .offset(x: centerX - 88.5, y: mix(542.5, 596))
                .accessibilityHidden(!interactive).allowsHitTesting(false)
            price.frame(width: 177, height: 40, alignment: .top)
                .offset(x: centerX - 88.5, y: mix(562.5, 632))
            Text("You need 480 more points.").font(.system(size: 13)).foregroundStyle(.white.opacity(0.8))
                .frame(width: 315, height: 24).offset(x: 39, y: 670)
                .opacity(targetAlpha).accessibilityHidden(targetAlpha < 0.8).allowsHitTesting(false)
            action
            closeButton
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .opacity(reduceMotion ? p : 1)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Headphones reward details")
    }

    private var surfaceDrawing: some View {
        Canvas { context, _ in
            let rect = surface
            let path = Path(roundedRect: rect, cornerRadius: mix(26, 32))
            let color = Color(red: Double(mix(137, 55)) / 255, green: Double(mix(60, 16)) / 255,
                              blue: Double(mix(88, 68)) / 255, opacity: 1)
            context.fill(path, with: .color(color.opacity(Double(mix(0.4, 1)))))
            var glow = context
            glow.clip(to: path)
            glow.fill(Path(rect), with: .radialGradient(Gradient(colors: [Color(hex: 0xC88629).opacity(Double(mix(0.7, 0.45))), .clear]),
                      center: CGPoint(x: rect.minX + rect.width * 0.49, y: rect.minY + rect.height * 0.28),
                      startRadius: 3, endRadius: mix(125, 220)))
            context.stroke(path, with: .color(Color(hex: 0xFFC43B).opacity(0.65)), lineWidth: 0.75)
        }.frame(width: 393, height: 852)
    }

    private var price: some View {
        HStack(alignment: .firstTextBaseline, spacing: 3) {
            Text("600").font(.system(size: 23, weight: .bold))
            Text("pts").font(.system(size: 11, weight: .semibold)).baselineOffset(4)
        }.foregroundStyle(.white).accessibilityElement(children: .ignore)
            .accessibilityLabel("600 points required")
    }

    private var action: some View {
        Button(action: onClose) {
            ZStack {
                AuricRedeemPointsMorphPill(width: buttonWidth, height: buttonHeight)
                    .fill(LinearGradient(stops: [.init(color: Color(hex: 0xF3A82F), location: 0),
                                                .init(color: Color(hex: 0xFAC672), location: 0.6),
                                                .init(color: Color(hex: 0xFED99B), location: 1)], startPoint: .top, endPoint: .bottom))
                    .frame(width: 315, height: 44)
                    .scaleEffect(x: buttonWidth / 315, y: buttonHeight / 44)
                Text("Redeem").font(.system(size: 12, weight: .semibold)).opacity(1 - actionAlpha)
                Text("Keep earning").font(.system(size: 14, weight: .semibold)).opacity(actionAlpha)
            }.foregroundStyle(.black).frame(width: 315, height: 48)
                .contentShape(AuricRedeemPointsMorphHitbox(width: buttonWidth))
        }.buttonStyle(AuricRedeemPointsPillStyle())
            .allowsHitTesting(interactive).accessibilityHidden(!interactive)
            .offset(x: centerX - 157.5, y: buttonCenterY - 24)
            .accessibilityElement(children: .ignore).accessibilityLabel("Keep earning points")
            .accessibilityHint("Close reward details and return to your available rewards.")
    }

    private var closeButton: some View {
        Button(action: onClose) {
            Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(.white.opacity(0.9))
                .frame(width: 28, height: 28).background(.white.opacity(0.1), in: Circle())
                .frame(width: 44, height: 44).contentShape(Rectangle())
        }.buttonStyle(AuricRedeemPointsPillStyle()).offset(x: 323, y: 446)
            .opacity(targetAlpha).allowsHitTesting(interactive)
            .accessibilityLabel("Close reward details").accessibilityHidden(!interactive)
            .modifier(AuricRedeemPointsFocus(enabled: focusEnabled, focus: closeFocus))
    }
}

private struct AuricRedeemPointsMorphPill: Shape {
    var width: CGFloat
    var height: CGFloat
    func path(in r: CGRect) -> Path {
        let rx = height / 2 / (width / 315)
        return Path(CGPath(roundedRect: r, cornerWidth: rx, cornerHeight: 22, transform: nil))
    }
}

private struct AuricRedeemPointsMorphHitbox: Shape {
    var width: CGFloat
    func path(in r: CGRect) -> Path { Path(CGRect(x: r.midX - width / 2, y: r.minY, width: width, height: r.height)) }
}

/// ImageRenderer has no accessibility focus host; snapshots keep the same visible content.
private struct AuricRedeemPointsFocus: ViewModifier {
    let enabled: Bool
    let focus: AccessibilityFocusState<Bool>.Binding
    @ViewBuilder func body(content: Content) -> some View {
        if enabled { content.accessibilityFocused(focus) } else { content }
    }
}

