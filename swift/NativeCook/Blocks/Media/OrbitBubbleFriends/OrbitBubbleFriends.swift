import SwiftUI
import UIKit

// Pinboard's photo badges roll along a column of eighteen slots, repeating six friends.
// RN's current constants: 214 / 150 pt faces, 136 pt first gap, then 23 pt; 80° tip.
// Each 1.2 s stop rests for 0.75 s, then rolls with ease-in-out cubic for 0.45 s.
// Six stops make one 7.2 s loop. Drag 90 pt per badge; snap for 0.35 s, hold 2 s,
// then continue the loop from the selected friend. The clock keeps the central face breathing.
struct OrbitBubbleFriends: View {
    static let loop: Double = 7.2
    var brand = "Pinboard"
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var engine = OrbitBubbleFriendsEngine()
    @State private var touchRevision = 0
    @GestureState private var gestureActive = false

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            let footerTop = size.height - safe.bottom - 158
            TimelineView(.animation(paused: reduced || scene != .active)) { timeline in
                let frame = engine.frame(at: timeline.date, running: !reduced && scene == .active,
                                         autoPlay: autoPlay)
                Canvas { context, _ in
                    OrbitBubbleFriendsArt.drawColumn(context, index: frame.index, clock: frame.clock, size: size)
                    OrbitBubbleFriendsArt.drawCopy(context, index: frame.index, size: size, safe: safe, brand: brand)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .background(OrbitBubbleFriendsArt.background)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(brand). Friends' photo badges. Drag up or down to roll them.")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            // RN's footer is outside the pan detector.
            .contentShape(Rectangle().path(in: CGRect(x: 0, y: 0, width: size.width, height: footerTop)))
            .gesture(pan, including: reduced ? .none : .all)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onChange(of: scene) { _, _ in engine.pauseClock() }
        .onChange(of: reduced) { _, _ in engine.pauseClock() }
        .onChange(of: gestureActive) { _, active in
            if !active && engine.touching {
                Task { @MainActor in
                    await Task.yield()
                    if !gestureActive && engine.touching { engine.end(); touchRevision += 1 }
                }
            }
        }
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduced else { return }
                if !engine.touching { engine.begin(autoPlay: autoPlay) }
                if hypot(value.translation.width, value.translation.height) >= 4 { engine.activated = true }
                if engine.activated { engine.move(translationY: value.translation.height) }
                touchRevision += 1
            }
            .onEnded { _ in engine.end(); touchRevision += 1 }
    }
}

// Only touch/lifecycle transitions change published state. Frames use wall time, without
// accumulating frame deltas or writing SwiftUI state from a TimelineView.
private final class OrbitBubbleFriendsEngine {
    struct Frame { var index: Double; var clock: Double }
    private var epoch: Date?
    private var elapsedOffset = 0.0
    private var origin = 0.0
    private var latest = Frame(index: 0, clock: 0)
    private var manual = false
    private var released = -1.0
    private var grabbed = 0.0
    private var from = 0.0
    private var snapTo = 0.0
    private var lastStep = 0.0
    private let feedback = UISelectionFeedbackGenerator()
    var touching = false
    var activated = false

    private func elapsed(at date: Date) -> Double {
        elapsedOffset + (epoch.map { max(0, date.timeIntervalSince($0)) } ?? 0)
    }

    func pauseClock() {
        elapsedOffset = elapsed(at: .now)
        epoch = nil
    }

    func frame(at date: Date, running: Bool, autoPlay: Bool) -> Frame {
        guard running else { return latest }
        if epoch == nil { epoch = date }
        let now = elapsed(at: date)
        let t = OrbitBubbleFriendsMotion.mod(now - origin, OrbitBubbleFriends.loop)
        latest.clock = t
        if touching { return latest }
        if manual {
            let since = now - released
            latest.index = since < 0.35
                ? from + (snapTo - from) * OrbitBubbleFriendsMotion.ease(max(0, since / 0.35))
                : snapTo
            if since > 2.35 {
                manual = false
                origin = now - OrbitBubbleFriendsMotion.mod(snapTo, 6) * 1.2
            }
            return latest
        }
        if autoPlay { latest.index = OrbitBubbleFriendsMotion.index(at: t) }
        return latest
    }

    func begin(autoPlay: Bool) {
        _ = frame(at: .now, running: true, autoPlay: autoPlay)
        touching = true
        manual = true
        activated = false
        grabbed = latest.index
        lastStep = floor(latest.index + 0.5)
        feedback.prepare()
    }

    func move(translationY: Double) {
        latest.index = grabbed - translationY / 90
        let step = floor(latest.index + 0.5) // JavaScript Math.round also rounds negative ties up.
        if step != lastStep {
            lastStep = step
            feedback.selectionChanged()
            feedback.prepare()
        }
    }

    func end() {
        guard touching else { return }
        touching = false
        from = latest.index
        snapTo = floor(latest.index + 0.5)
        released = elapsed(at: .now)
    }
}

private enum OrbitBubbleFriendsMotion {
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mod(_ x: Double, _ period: Double) -> Double {
        (x.truncatingRemainder(dividingBy: period) + period).truncatingRemainder(dividingBy: period)
    }
    static func ease(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func index(at t: Double) -> Double {
        let k = floor(t / 1.2)
        let u = t - k * 1.2
        return k + ease(clamp((u - 0.75) / 0.45))
    }
    static func distance(slot: Int, index: Double) -> Double {
        var d = (Double(slot) - index).truncatingRemainder(dividingBy: 18)
        if d < -9 { d += 18 }
        if d >= 9 { d -= 18 }
        return d
    }
    static func friendDistance(_ friend: Int, index: Double) -> Double {
        var d = mod(Double(friend) - index, 6)
        if d > 3 { d -= 6 }
        return abs(d)
    }
}

private enum OrbitBubbleFriendsArt {
    struct Friend { var name: String; var caption: String; var when: String }
    static let friends = [
        Friend(name: "Mika", caption: "Biscuit learned to sit. Took a week.", when: "2 min"),
        Friend(name: "Theo", caption: "Saturday tax: one sunbeam, all of it.", when: "18 min"),
        Friend(name: "Ana", caption: "Found the noodle bar under the bridge.", when: "1 h"),
        Friend(name: "Jules", caption: "New leaf. I am unreasonably proud.", when: "3 h"),
        Friend(name: "Rui", caption: "Out of office until further notice.", when: "5 h"),
        Friend(name: "Noor", caption: "Painted the bike. Then the wall. Oops.", when: "8 h")
    ]
    static func color(_ hex: UInt32, _ opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: opacity)
    }
    static let background = color(0x141216)
    static let ink = color(0xF5F2EC)
    // Load and decode the six bundled photos once, before drawing any frames.
    static let photos: [Image?] = ["pet-dog", "pet-cat", "food-ramen", "plant", "beach", "bike"].map { name in
        guard let url = Bundle.main.url(forResource: "OrbitBubbleFriends-\(name)", withExtension: "jpg"),
              let source = UIImage(contentsOfFile: url.path) else { return nil }
        return Image(uiImage: source.preparingForDisplay() ?? source)
    }

    static func drawColumn(_ context: GraphicsContext, index: Double, clock: Double, size: CGSize) {
        let cx = size.width / 2
        let cy = size.height * 0.44
        let order = (0..<18).sorted {
            let a = abs(OrbitBubbleFriendsMotion.distance(slot: $0, index: index))
            let b = abs(OrbitBubbleFriendsMotion.distance(slot: $1, index: index))
            return a == b ? $0 < $1 : a > b
        }
        for j in order {
            let d = OrbitBubbleFriendsMotion.distance(slot: j, index: index)
            let a = abs(d)
            if a > 8.5 { continue }
            let near = min(a, 1)
            let sign = d == 0 ? 0.0 : (d < 0 ? -1.0 : 1.0)
            let y = cy + sign * (136 * near + 23 * max(0, a - 1))
            let breathe = a < 0.5 ? 1 + 0.015 * sin(clock / OrbitBubbleFriends.loop * .pi * 12) : 1
            let width = (214 - 64 * near) * breathe
            let tip = 80 * near * .pi / 180
            let height = width * max(0.12, cos(tip))
            let rect = CGRect(x: cx - width / 2, y: y - height / 2, width: width, height: height)
            let rim = Path(ellipseIn: rect.offsetBy(dx: 0, dy: -sign * 9 * sin(tip)))
            context.fill(rim, with: .color(color(0xD9D4CC)))
            context.fill(rim, with: .color(.black.opacity(0.35)))
            if a < 0.6 {
                var shadow = context
                shadow.addFilter(.blur(radius: 18))
                let shadowRect = CGRect(x: rect.minX + 6, y: rect.minY + 16, width: width - 12, height: height)
                shadow.fill(Path(ellipseIn: shadowRect), with: .color(.black.opacity(0.5)))
            }
            let face = Path(ellipseIn: rect)
            context.fill(face, with: .color(ink))
            if let image = photos[j % 6] {
                let photoRect = rect.insetBy(dx: width * 0.06, dy: height * 0.06)
                var photo = context
                photo.clip(to: Path(ellipseIn: photoRect))
                // RN stretches the square image into the tipped ellipse, without a perspective warp.
                photo.draw(image, in: photoRect)
            }
            let gloss = Gradient(stops: [.init(color: .white.opacity(0.28), location: 0),
                                         .init(color: .white.opacity(0), location: 0.45),
                                         .init(color: .black.opacity(0.12), location: 1)])
            context.fill(face, with: .linearGradient(gloss, startPoint: CGPoint(x: 0, y: rect.minY),
                                                     endPoint: CGPoint(x: 0, y: rect.maxY)))
            context.fill(face, with: .color(color(0x0E0D10, OrbitBubbleFriendsMotion.clamp((a - 0.3) / 7) * 0.8)))
        }
    }

    static func drawCopy(_ context: GraphicsContext, index: Double, size: CGSize, safe: EdgeInsets, brand: String) {
        func label(_ text: String, size fontSize: Double, weight: Font.Weight = .regular,
                   tracking: Double = 0, opacity: Double = 1) -> GraphicsContext.ResolvedText {
            context.resolve(Text(text).font(.system(size: fontSize, weight: weight))
                .tracking(tracking).foregroundStyle(ink.opacity(opacity)))
        }
        let brandText = label(brand, size: 20, weight: .heavy, tracking: -0.4)
        let meta = label("6 new", size: 14, weight: .semibold, opacity: 0.55)
        let rowCenter = safe.top + 8 + 17
        context.draw(brandText, at: CGPoint(x: 24, y: rowCenter), anchor: .leading)
        context.draw(meta, at: CGPoint(x: size.width - 24, y: rowCenter), anchor: .trailing)
        let infoTop = size.height - safe.bottom - 158
        for (k, friend) in friends.enumerated() {
            let opacity = 1 - OrbitBubbleFriendsMotion.clamp(OrbitBubbleFriendsMotion.friendDistance(k, index: index) / 0.45)
            if opacity <= 0 { continue }
            var copy = context
            copy.opacity = opacity
            let y = infoTop + 10 * (1 - opacity)
            let name = label(friend.name, size: 30, weight: .heavy, tracking: -0.8)
            let when = label(friend.when, size: 14, weight: .semibold, opacity: 0.5)
            copy.draw(name, at: CGPoint(x: 24, y: y), anchor: .topLeading)
            copy.draw(when, at: CGPoint(x: size.width - 24, y: y + name.firstBaseline(in: CGSize(width: 1000, height: 100)) - when.firstBaseline(in: CGSize(width: 1000, height: 100))), anchor: .topTrailing)
            let caption = label(friend.caption, size: 16, opacity: 0.7)
            // The native 16 pt font has a 19.1 pt line box; RN centres it in a 22 pt line.
            copy.draw(caption, at: CGPoint(x: 24, y: y + 36 + 4 + (22 - 19.1) / 2), anchor: .topLeading)
        }
        let reply = CGRect(x: 24, y: size.height - safe.bottom - 70, width: size.width - 48, height: 56)
        context.fill(Path(roundedRect: reply, cornerRadius: 28), with: .color(ink.opacity(0.08)))
        context.draw(label("Send a reaction…", size: 16, opacity: 0.5), at: CGPoint(x: 44, y: reply.midY), anchor: .leading)
        let send = CGRect(x: reply.maxX - 50, y: reply.minY + 6, width: 44, height: 44)
        context.fill(Path(ellipseIn: send), with: .color(ink))
        context.draw(Text("♥").font(.system(size: 18)).foregroundStyle(background), at: CGPoint(x: send.midX, y: send.midY))
    }
}

#Preview { OrbitBubbleFriends(autoPlay: true) }
