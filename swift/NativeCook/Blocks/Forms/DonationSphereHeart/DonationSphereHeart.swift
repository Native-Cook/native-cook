import SwiftUI
import UIKit

/// The same sphere heart changes density while retaining its glass, ceramic and chrome materials.
struct DonationSphereHeart: View {
    var autoPlay = false
    var renderTime: Double? = nil
    static let loop = 10.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date.now
    @State private var dragging = false
    @State private var capturedTime = 0.0
    @State private var interactionTime = 0.0
    @State private var interactionYaw = 0.0
    @State private var releaseVelocity = 0.0
    @State private var releasedAt: Date?

    private func drive(_ date: Date) -> (time: Double, yaw: Double) {
        if reduceMotion { return (0, 0) }
        if let renderTime { return (renderTime, 0) }
        if dragging { return (interactionTime, interactionYaw) }
        if let releasedAt {
            let age = max(0, date.timeIntervalSince(releasedAt))
            let carried = interactionTime + releaseVelocity * 0.14 * (1 - exp(-age / 0.14))
            if age < 2 { return (carried, interactionYaw * exp(-5 * age)) }
            if age < 2.65 { return (carried * (1 - DonationSphereHeartArt.smooth((age - 2) / 0.65)), 0) }
            return (autoPlay ? age - 2.65 : 0, 0)
        }
        return (autoPlay ? max(0, date.timeIntervalSince(epoch)) : 0, 0)
    }
    var body: some View {
        GeometryReader { geometry in
            let scale = geometry.size.width / 393
            let height = geometry.size.height / scale
            let lowerShift = max(0, height - 852)
            TimelineView(.animation(paused: reduceMotion || renderTime != nil || (!autoPlay && releasedAt == nil && !dragging))) { timeline in
                let state = drive(timeline.date)
                ZStack(alignment: .topLeading) {
                    Color(red: 248 / 255, green: 246 / 255, blue: 250 / 255)
                    RoundedRectangle(cornerRadius: 16).fill(.white)
                        .frame(width: 365, height: 510 + lowerShift * 0.34).offset(x: 14, y: 94)
                    RoundedRectangle(cornerRadius: 16).fill(.white)
                        .frame(width: 365, height: 204).offset(x: 14, y: 620 + lowerShift)
                    Button { reset() } label: {
                        Image(systemName: "xmark").font(.system(size: 13, weight: .medium))
                            .frame(width: 40, height: 40).contentShape(Rectangle())
                    }.buttonStyle(.plain).position(x: 28, y: 73).accessibilityLabel("Reset donation heart")
                    Text("DONATE WITH A\nTAP").font(.system(size: 35, weight: .black)).tracking(-0.8)
                        .lineSpacing(-2).fixedSize(horizontal: false, vertical: true)
                        .frame(width: 341, height: 84, alignment: .topLeading).offset(x: 26, y: 116)
                    Canvas { context, _ in
                        DonationSphereHeartArt.draw(context, time: state.time, yaw: state.yaw,
                                                    center: CGPoint(x: 196.5, y: 440 + lowerShift * 0.34))
                    }.allowsHitTesting(false).accessibilityHidden(true)
                    Color.clear.frame(width: 363, height: 362).contentShape(Rectangle())
                        .position(x: 196.5, y: 441 + lowerShift * 0.34)
                        .accessibilityElement(children: .ignore).accessibilityLabel("Glass sphere heart")
                        .accessibilityHint("Drag to gather the spheres or spread them apart.")
                        .accessibilityAddTraits(.isButton).accessibilityAction { demonstrate() }
                        .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                            guard renderTime == nil, !reduceMotion else { return }
                            if !dragging {
                                capturedTime = drive(.now).time.truncatingRemainder(dividingBy: Self.loop)
                                dragging = true; releasedAt = nil
                            }
                            let distance = hypot(value.translation.width / scale, value.translation.height / scale)
                            interactionTime = capturedTime + min(3.3, distance / 43)
                            interactionYaw = max(-0.55, min(0.55, value.translation.width / (300 * scale)))
                        }.onEnded { value in
                            guard renderTime == nil, !reduceMotion else { return }
                            let extra = hypot((value.predictedEndTranslation.width - value.translation.width) / scale,
                                              (value.predictedEndTranslation.height - value.translation.height) / scale)
                            releaseVelocity = min(3, extra / 43)
                            if hypot(value.translation.width, value.translation.height) < 8 { interactionTime = 1.55 }
                            dragging = false; releasedAt = .now
                            UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
                        })
                    option(0, "Feeling generous? Send a one-time donation", y: 645 + lowerShift)
                    option(1, "Prefer a bit more planning? Set it up monthly", y: 696 + lowerShift)
                    option(2, "Give a little, but often? Chip in your change", y: 747 + lowerShift)
                    Button { demonstrate() } label: {
                        Text("Donate now").font(.system(size: 13.5, weight: .medium)).foregroundStyle(.white)
                            .frame(width: 361, height: 48)
                            .background(Color(red: 0.095, green: 0.105, blue: 0.105), in: Capsule())
                            .shadow(color: .black.opacity(0.15), radius: 3, y: 2)
                            .shadow(color: .black.opacity(0.10), radius: 13, y: 9)
                    }.buttonStyle(.plain).position(x: 196.5, y: 783 + lowerShift)
                        .accessibilityHint("Play the sphere heart animation.")
                }.foregroundStyle(Color(red: 0.055, green: 0.055, blue: 0.06))
                    .frame(width: 393, height: height, alignment: .topLeading)
                    .scaleEffect(scale, anchor: .topLeading)
                    .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            }
        }.background(.white).ignoresSafeArea().preferredColorScheme(.light)
            .onAppear { epoch = .now }.onChange(of: reduceMotion) { _, _ in reset() }
            .task(id: releasedAt) {
                guard let stamp = releasedAt else { return }
                try? await Task.sleep(for: .seconds(2.65))
                guard !Task.isCancelled, !dragging, releasedAt == stamp else { return }
                epoch = stamp.addingTimeInterval(2.65)
                releasedAt = nil; interactionTime = 0; interactionYaw = 0
            }
    }
    private func option(_ index: Int, _ title: String, y: Double) -> some View {
        HStack(spacing: 16) {
            Canvas { context, _ in
                var c = context; c.scaleBy(x: 17.0 / 18, y: 17.0 / 18)
                if index == 0 {
                    var p = Path(); p.move(to: CGPoint(x: 2, y: 2.6)); p.addLine(to: CGPoint(x: 16.1, y: 8))
                    p.addLine(to: CGPoint(x: 2, y: 15.4)); p.addLine(to: CGPoint(x: 4.4, y: 9.8))
                    p.addLine(to: CGPoint(x: 11.3, y: 8)); p.addLine(to: CGPoint(x: 4.4, y: 6.3)); p.closeSubpath()
                    c.fill(p, with: .color(.black))
                } else if index == 1 {
                    c.stroke(Path(roundedRect: CGRect(x: 2.2, y: 3.5, width: 13.6, height: 12.3), cornerRadius: 1.5), with: .color(.black), lineWidth: 1.4)
                    var p = Path(); p.move(to: CGPoint(x: 2.3, y: 7)); p.addLine(to: CGPoint(x: 15.5, y: 7))
                    for x in [5.3, 12.7] { p.move(to: CGPoint(x: x, y: 1.8)); p.addLine(to: CGPoint(x: x, y: 5.2)) }
                    c.stroke(p, with: .color(.black), lineWidth: 1.4)
                } else {
                    for column in 0..<2 { for row in 0..<3 {
                        c.fill(Path(ellipseIn: CGRect(x: Double(2 + column * 8), y: Double(2 + row * 4 + column * 4), width: 6, height: 3)), with: .color(.black))
                    } }
                }
            }.frame(width: 17, height: 17).accessibilityHidden(true)
            Text(title).font(.system(size: 13.2)).lineLimit(1).minimumScaleFactor(0.9)
        }.frame(width: 341, height: 28, alignment: .leading).position(x: 196.5, y: y)
    }
    private func reset() {
        dragging = false; releasedAt = nil; interactionTime = 0; interactionYaw = 0; epoch = .now
    }
    private func demonstrate() {
        guard renderTime == nil else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
        guard !reduceMotion else { return }
        interactionTime = 1.55; interactionYaw = 0; releaseVelocity = 0
        dragging = false; releasedAt = .now
    }
}

private enum DonationSphereHeartArt {
    struct Sphere {
        var x: Double
        var y: Double
        var z: Double
        var radius: Double
        var material: Int
    }
    struct Pose {
        var medium: Double
        var dense: Double
        var sparse: Double
        var scale: Double
    }
    static func smooth(_ x: Double) -> Double { let p = min(1, max(0, x)); return p * p * (3 - 2 * p) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func hash(_ i: Int) -> Double {
        let v = sin(Double(i) * 12.9898 + 7.233) * 43758.5453
        return v - floor(v)
    }
    static let poseKeys: [(Double, Pose)] = [
        (0, Pose(medium: 1, dense: 0, sparse: 0, scale: 1)),
        (0.15, Pose(medium: 1, dense: 0, sparse: 0, scale: 1)),
        (1.3, Pose(medium: 1, dense: 0, sparse: 0, scale: 0.82)),
        (1.5, Pose(medium: 0, dense: 1, sparse: 0, scale: 1.1)),
        (3.08, Pose(medium: 0, dense: 1, sparse: 0, scale: 0.82)),
        (3.28, Pose(medium: 0, dense: 0, sparse: 1, scale: 1)),
        (4.56, Pose(medium: 0, dense: 0, sparse: 1, scale: 0.76)),
        (4.8, Pose(medium: 1, dense: 0, sparse: 0, scale: 1.02)),
        (6.26, Pose(medium: 1, dense: 0, sparse: 0, scale: 0.82)),
        (6.48, Pose(medium: 0, dense: 1, sparse: 0, scale: 1.1)),
        (7.95, Pose(medium: 0, dense: 1, sparse: 0, scale: 0.82)),
        (8.18, Pose(medium: 0, dense: 0, sparse: 1, scale: 1)),
        (9.17, Pose(medium: 0, dense: 0, sparse: 1, scale: 0.85)),
        (9.75, Pose(medium: 1, dense: 0, sparse: 0, scale: 1)),
        (10, Pose(medium: 1, dense: 0, sparse: 0, scale: 1)),
    ]
    static func pose(_ time: Double) -> Pose {
        let t = max(0, time).truncatingRemainder(dividingBy: DonationSphereHeart.loop)
        for i in 1..<poseKeys.count where t <= poseKeys[i].0 {
            let a = poseKeys[i - 1], b = poseKeys[i], f = smooth((t - a.0) / (b.0 - a.0))
            return Pose(medium: mix(a.1.medium, b.1.medium, f), dense: mix(a.1.dense, b.1.dense, f),
                        sparse: mix(a.1.sparse, b.1.sparse, f), scale: mix(a.1.scale, b.1.scale, f))
        }
        return poseKeys[0].1
    }
    // Hand-placed large spheres preserve the asymmetry of the source heart.
    static let medium: [Sphere] = {
        let data: [(Double, Double, Double, Int)] = [
            (-65,-104,16,0),(34,-80,22,2),(70,-21,17,1),(-1,-18,29,5),(-42,45,18,7),(100,39,13,4),
            (8,112,8,1),(57,82,5,5),(32,-11,7,5),(-59,-54,10,4),(105,-107,18,2),(-1,-49,9,3),
            (68,-111,21,0),(-78,-112,22,6),(-48,-86,22,6),(-108,-66,23,1),(-109,-37,15,0),(91,-61,19,0),
            (98,-77,24,2),(130,-51,18,6),(-68,-18,27,2),(-112,-6,23,6),(106,-2,22,6),(34,19,25,2),
            (-67,25,21,0),(-20,30,16,6),(20,45,25,6),(-37,71,12,4),(64,61,20,0),(7,92,16,6),
            (136,-13,7,1),(7,32,8,1),(-87,-83,10,4),(1,-75,7,4),(-48,-102,8,2),(-150,-65,5,2),
            (-74,-64,12,3),(-40,-58,16,3),(62,-60,12,3),(-33,-40,10,3),(86,-32,10,6),(43,-42,12,3),
            (29,-17,10,3),(74,26,19,3),(-54,11,12,3),(-98,-78,4,4),(25,68,12,3),(73,1,7,5)
        ]
        return data.indices.map { i in
            let v = data[i]
            return Sphere(x: v.0, y: v.1, z: (hash(i + 61) - 0.5) * 74, radius: v.2, material: v.3)
        }
    }()
    static let sparse: [Sphere] = {
        let data: [(Double, Double, Double, Int)] = [
            (-56,-96,34,0),(96,-72,39,2),(131,-7,15,1),(-93,-12,28,5),(-39,49,37,7),(106,45,27,4),
            (14,112,15,1),(58,98,9,5),(26,-15,9,5),(7,-67,14,4),(-143,-57,9,2),(70,15,8,3)
        ]
        return data.enumerated().map { i, v in Sphere(x: v.0, y: v.1, z: (hash(i + 122) - 0.5) * 56, radius: v.2, material: v.3) }
    }()
    static let dense: [Sphere] = {
        // A parametric contour keeps the top cleft open after the sphere radii are added.
        let outline: [CGPoint] = (0..<160).map { i in
            let a = Double(i) * 2 * .pi / 160
            return CGPoint(x: 16 * pow(sin(a), 3) * 9,
                           y: -(13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)) * 7.5 + 1)
        }
        func inside(_ x: Double, _ y: Double) -> Bool {
            var result = false
            var j = outline.count - 1
            for i in outline.indices {
                let a = outline[i], b = outline[j]
                if (a.y > y) != (b.y > y), x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x {
                    result.toggle()
                }
                j = i
            }
            return result
        }
        var points: [Sphere] = []
        var candidate = 0
        while points.count < 120 {
            let px = hash(candidate * 3 + 1001) * 288 - 144
            let py = hash(candidate * 3 + 1002) * 232 - 103
            candidate += 1
            guard inside(px, py) else { continue }
            guard !points.contains(where: { hypot($0.x - px, ($0.y + 17) / 1.25 - py) < 13 }) else { continue }
            let i = points.count
            let material = i < medium.count ? medium[i].material : ([2,6,3,6,2,5,3,0,1,7][i % 10])
            let radius = (7 + hash(i + 173) * 10) * (material == 4 ? 0.66 : 1)
            points.append(Sphere(x: px, y: py * 1.25 - 17, z: (hash(i + 823) - 0.5) * 92, radius: radius, material: material))
        }
        return points
    }()
    static let atlas: [CGImage] = {
        let image = UIImage(named: "DonationSphereHeart-spheres")!.cgImage!
        return (0..<8).map { image.cropping(to: CGRect(x: $0 * 256, y: 0, width: 256, height: 256))! }
    }()
    static func draw(_ original: GraphicsContext, time: Double, yaw: Double, center: CGPoint) {
        let t = max(0, time).truncatingRemainder(dividingBy: DonationSphereHeart.loop)
        let p = pose(t)
        let envelope = smooth(t / 0.5) * (1 - smooth((t - 9.17) / 0.58))
        let turn = 0.13 * sin(2 * .pi * t / 4.8) * envelope + yaw
        let roll = 0.028 * sin(4 * .pi * t / 4.8) * envelope
        var spheres: [Sphere] = []
        for i in 0..<120 {
            let m = medium[i % medium.count], d = dense[i], s = sparse[i % sparse.count]
            let rm = i < medium.count ? m.radius : 0, rs = i < sparse.count ? s.radius : 0
            let radius = (rm * p.medium + d.radius * p.dense + rs * p.sparse) * p.scale
            guard radius > 0.25 else { continue }
            let x = (m.x * p.medium + d.x * p.dense + s.x * p.sparse) * p.scale
            let y = (m.y * p.medium + d.y * p.dense + s.y * p.sparse) * p.scale
            let z = m.z * p.medium + d.z * p.dense + s.z * p.sparse
            let rx = x * cos(turn) + z * sin(turn), rz = z * cos(turn) - x * sin(turn)
            let depth = 760 / (760 - rz)
            let material = i < medium.count ? m.material : d.material
            spheres.append(Sphere(x: (rx * cos(roll) - y * sin(roll)) * depth,
                                  y: (rx * sin(roll) + y * cos(roll)) * depth, z: rz, radius: radius * depth, material: material))
        }
        for sphere in spheres.sorted(by: { $0.z < $1.z }) {
            let extent = sphere.radius * 256 / 112
            let rect = CGRect(x: center.x + sphere.x - extent / 2, y: center.y + sphere.y - extent / 2, width: extent, height: extent)
            original.draw(Image(decorative: atlas[sphere.material], scale: 1), in: rect)
        }
    }
}
