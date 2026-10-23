import SwiftUI
import UIKit

/// A moving metal slider separates the teeth before the denim cover leaves the page.
struct DenimPocketWelcome: View {
    var autoPlay = false
    var previewTime: Double? = nil
    static let loop = 8.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var epoch = Date.now
    @State private var dragging = false
    @State private var captured = 0.0
    @State private var capturedTab = 0.0
    @State private var finger = 0.0
    @State private var tabDeflection = 0.0
    @State private var releasedAt: Date?
    @State private var releaseFrom = 0.0
    @State private var releaseTarget = 0.0
    @State private var releaseVelocity = 0.0
    @State private var ready = false

    static func smooth(_ x: Double) -> Double {
        let v = min(1, max(0, x)); return v * v * (3 - 2 * v)
    }
    static func progress(_ time: Double) -> Double {
        let t = time.truncatingRemainder(dividingBy: loop)
        if t < 1.3 { return 0 }
        if t < 1.63 { return 0.10 * smooth((t - 1.3) / 0.33) }
        if t < 1.88 { return 0.10 * (1 - smooth((t - 1.63) / 0.25)) }
        if t < 3.75 { return 0.86 * smooth((t - 1.88) / 1.87) }
        if t < 4.22 { return 0.86 + 0.14 * (1 - pow(1 - (t - 3.75) / 0.47, 3)) }
        if t < 5.18 { return 1 }
        return 1 - smooth((t - 5.18) / 1.92)
    }
    private func drive(_ date: Date) -> (p: Double, tab: Double) {
        if reduced { return (1, 0) }
        var t = previewTime ?? max(0, date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop)
        if previewTime == nil {
            if dragging { return (finger, tabDeflection) }
            if let releasedAt {
                let age = max(0, date.timeIntervalSince(releasedAt))
                if age < 2 {
                    let delta = releaseFrom - releaseTarget
                    let spring = releaseTarget + delta * exp(-12 * age) * cos(12 * age) + releaseVelocity * age * exp(-12 * age)
                    return (min(1, max(0, spring)), tabDeflection * exp(-9 * age) * cos(13 * age))
                }
                if age < 2.6 { return (releaseTarget * (1 - Self.smooth((age - 2) / 0.6)), 0) }
                t = max(0, age - 2.6).truncatingRemainder(dividingBy: Self.loop)
            }
        }
        let p = autoPlay || previewTime != nil ? Self.progress(t) : 0
        let angle = -14 * Self.smooth((t - 1.15) / 0.15) * (1 - Self.smooth((t - 1.88) / 1.97))
            + 9 * sin((t - 1.88) * 9) * Self.smooth((t - 1.88) / 0.2) * (1 - Self.smooth((t - 2.4) / 1.45))
        return (p, p > 0.98 || (!autoPlay && previewTime == nil) ? 0 : angle)
    }
    var body: some View {
        GeometryReader { geo in
            let s = geo.size.width / 393
            TimelineView(.animation(paused: reduced || previewTime != nil || (!autoPlay && releasedAt == nil && !dragging))) { clock in
                let state = drive(clock.date)
                let p = state.p
                ZStack(alignment: .topLeading) {
                    LinearGradient(colors: [Self.color(0x087CFA), Self.color(0x279BF0), Self.color(0x72D3F3)], startPoint: .top, endPoint: .bottom)
                    if p > 0.72 || reduced {
                        setup(p: p, isReady: ready && (reduced || releasedAt.map { clock.date.timeIntervalSince($0) < 2.6 } == true), bottom: max(0, geo.safeAreaInsets.bottom / s - 34))
                    }
                    Canvas { ctx, _ in Self.drawCover(ctx, p: p, angle: state.tab) }
                        .accessibilityHidden(true)
                        .allowsHitTesting(false)
                    if !reduced && (p < 0.985 || dragging) {
                        Color.clear.frame(width: 92, height: 124)
                            .contentShape(Rectangle())
                            .position(x: 196.5, y: Self.join(p) + 26)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("Open journal cover")
                            .accessibilityValue("\(Int(p * 100)) percent open")
                            .accessibilityHint("Drag the zipper downward to reveal your journal.")
                            .accessibilityAddTraits(.isButton)
                            .accessibilityAction { release(from: p, target: 1, velocity: 0) }
                            .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                                guard previewTime == nil else { return }
                                if !dragging {
                                    let current = drive(.now)
                                    captured = current.p; capturedTab = current.tab; finger = captured
                                    dragging = true; releasedAt = nil; ready = false
                                }
                                finger = min(1, max(0, captured + value.translation.height / (650 * s)))
                                tabDeflection = min(18, max(-18, capturedTab + value.translation.width / (3 * s)))
                            }.onEnded { value in
                                let vy = (value.predictedEndTranslation.height - value.translation.height) * 5 / s
                                release(from: finger, target: finger >= 0.62 || vy >= 720 ? 1 : 0, velocity: min(1000, max(-1000, vy)) / 650)
                            })
                    }
                }
                .frame(width: 393, height: max(852, geo.size.height / s), alignment: .topLeading)
                .scaleEffect(s, anchor: .topLeading)
                .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
            }
        }
        .background(Self.color(0x72D3F3))
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .onAppear { epoch = .now }
        .onChange(of: reduced) { _, _ in
            dragging = false; releasedAt = nil; finger = 0; tabDeflection = 0; epoch = .now
        }
        .task(id: releasedAt) {
            guard let stamp = releasedAt else { return }
            try? await Task.sleep(for: .seconds(2.6))
            guard !Task.isCancelled, !dragging, releasedAt == stamp else { return }
            epoch = stamp.addingTimeInterval(2.6)
            releasedAt = nil; finger = 0; tabDeflection = 0
        }
    }
    private func release(from: Double, target: Double, velocity: Double) {
        dragging = false; releaseFrom = from; releaseTarget = target; releaseVelocity = velocity
        releasedAt = .now
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.55)
    }
    private func setup(p: Double, isReady: Bool, bottom: Double) -> some View {
        let reveal = reduced ? 1 : Self.smooth((p - 0.72) / 0.28)
        return ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 18).fill(Self.color(0xE8E5C9))
                        .shadow(color: .black.opacity(0.12), radius: 1.5, y: 2)
                        .shadow(color: Self.color(0x315F92).opacity(0.20), radius: 8, y: 7)
                    RoundedRectangle(cornerRadius: 8).fill(Self.color(0x158CFA)).frame(width: 78, height: 73).offset(x: 9, y: 22)
                    ForEach([20.0, 66.0], id: \.self) { x in
                        RoundedRectangle(cornerRadius: 2).fill(Self.color(0x65716E)).frame(width: 10, height: 13).offset(x: x, y: 6)
                            .overlay(alignment: .topLeading) { Rectangle().fill(.white.opacity(0.3)).frame(width: 2, height: 8).offset(x: x + 1, y: 7) }
                    }
                    Capsule().fill(Self.color(0xC7BF9E)).frame(width: 30, height: 2).offset(x: 33, y: 101)
                }.frame(width: 96, height: 108)
                Text("Dayloom").font(.system(size: 52, weight: .regular, design: .rounded)).tracking(-1.6)
                    .frame(width: 337, height: 65).padding(.top, 28)
            }.position(x: 196.5, y: 219.5 + 12 * (1 - reveal))
                .opacity(reveal)
            ForEach(0..<3) { i in
                let y = 437.0 + Double(i) * 75 - bottom
                let titles = ["Keep a small moment", "Find the thread", "Make room for more"]
                let captions = ["One photo, one line, one ordinary day.", "See the days that belong together.", "Your memories stay close, at your pace."]
                let icons = ["camera", "square.grid.2x2", "hand.thumbsup"]
                ZStack {
                    Circle().fill(Self.color(0x095D84).opacity(0.65))
                    Text("\(i + 1)").font(.system(size: 12, weight: .semibold))
                }.frame(width: 24, height: 24).position(x: 41, y: y + 17)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 5) {
                        Image(systemName: icons[i]).font(.system(size: 14, weight: .regular)).accessibilityHidden(true)
                        Text(titles[i]).font(.system(size: 14.5, weight: .semibold))
                    }
                    Text(captions[i]).font(.system(size: 12.5)).foregroundStyle(Self.color(0xF0F1DD).opacity(0.64))
                        .lineSpacing(2).fixedSize(horizontal: false, vertical: true)
                }.frame(width: 284, height: 60, alignment: .topLeading).position(x: 215, y: y + 30)
            }.opacity(reveal)
                .offset(y: 8 * (1 - reveal))
            Button {
                ready = true
                if !reduced { release(from: 1, target: 1, velocity: 0) }
                else { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5) }
            } label: {
                HStack(spacing: 8) {
                    if isReady { Image(systemName: "checkmark").font(.system(size: 14, weight: .semibold)) }
                    Text(isReady ? "Journal ready" : "Start a journal").font(.system(size: 16, weight: .semibold))
                }.frame(width: 353, height: 60)
                    .background(Self.color(0x087FFA), in: RoundedRectangle(cornerRadius: 17))
                    .overlay { RoundedRectangle(cornerRadius: 17).stroke(.white.opacity(0.12), lineWidth: 0.8) }
            }.buttonStyle(.plain).position(x: 196.5, y: 777 - bottom)
                .opacity(reveal).allowsHitTesting(p > 0.985 || reduced)
                .accessibilityHidden(p <= 0.985 && !reduced)
        }.foregroundStyle(Self.color(0xF0F1DD))
            .accessibilityHidden(p <= 0.985 && !reduced)
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    static func join(_ p: Double) -> Double {
        116 + 650 * min(p / 0.86, 1) + 128 * smooth((p - 0.86) / 0.14)
    }
    static func edge(_ y: Double, _ p: Double, _ side: Double) -> Double {
        let j = join(p)
        let gap = 10 + 175 * smooth(p / 0.86)
        return 196.5 + side * (2 + (y < j ? gap * pow(max(0, (j - y) / j), 0.78) : 0))
    }
    private static let denim: Image? = {
        guard let url = Bundle.main.url(forResource: "DenimPocketWelcome-denim", withExtension: "jpg"),
              let data = try? Data(contentsOf: url), let image = UIImage(data: data) else { return nil }
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let decoded = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in image.draw(at: .zero) }
        return Image(uiImage: decoded)
    }()
    static func drawCover(_ context: GraphicsContext, p: Double, angle: Double) {
        if p >= 1 { return }
        let depart = smooth((p - 0.86) / 0.14)
        for side in [-1.0, 1.0] {
            var c = context
            c.translateBy(x: side * 238 * depart, y: 64 * depart)
            c.rotate(by: .degrees(side * 2.5 * depart))
            var outline = Path()
            let outer = side < 0 ? -240.0 : 633.0
            outline.move(to: CGPoint(x: outer, y: -70))
            outline.addLine(to: CGPoint(x: edge(-70, p, side), y: -70))
            for row in 0...60 { let y = Double(row) * 16; outline.addLine(to: CGPoint(x: edge(y, p, side), y: y)) }
            outline.addLine(to: CGPoint(x: outer, y: 960)); outline.closeSubpath()
            var shadow = c
            shadow.addFilter(.shadow(color: .black.opacity(0.42), radius: 6, x: -side * 4, y: 2))
            shadow.fill(outline, with: .color(color(0x123342)))
            var cloth = c; cloth.clip(to: outline)
            if let denim {
                // Horizontal strips preserve material UVs while their inner edge bows around the slider.
                for row in 0..<28 {
                    let y = Double(row) * 36 - 72
                    var strip = cloth
                    strip.clip(to: Path(CGRect(x: -300, y: y, width: 1000, height: 36.2)))
                    let shift = edge(y + 18, p, side) - (196.5 + side * 2)
                    strip.draw(denim, in: CGRect(x: -100 + shift * 0.4, y: -70, width: 593, height: 1024))
                }
            }
            var hem = Path()
            for i in 0...125 {
                let y = Double(i) * 7.2 - 36
                let point = CGPoint(x: edge(y, p, side) + side * 5, y: y)
                if i == 0 { hem.move(to: point) } else { hem.addLine(to: point) }
            }
            let fold = hem.applying(CGAffineTransform(translationX: side * 10, y: 0))
            var foldedShade = c
            foldedShade.addFilter(.shadow(color: .black.opacity(0.26), radius: 3, x: side * 2, y: 1))
            foldedShade.stroke(fold, with: .color(color(0x081B29).opacity(0.45)), lineWidth: 5)
            c.stroke(fold, with: .color(color(0x7396A3).opacity(0.22)), lineWidth: 1.1)
            c.stroke(hem, with: .color(color(0x132127)), style: StrokeStyle(lineWidth: 21))
            c.stroke(hem, with: .color(color(0x3A4A4F).opacity(0.8)), style: StrokeStyle(lineWidth: 1.0))
            var stitches = Path()
            var teeth = Path(), bevel = Path(), recess = Path()
            for i in 0..<126 {
                let y = Double(i) * 7.2 + (side > 0 ? 3.6 : 0) - 28
                let x = edge(y, p, side)
                let slope = (edge(y + 1, p, side) - edge(y - 1, p, side)) / 2
                let transform = CGAffineTransform(translationX: x, y: y).rotated(by: -atan(slope))
                let bar = Path(roundedRect: CGRect(x: side < 0 ? -6 : -1.5, y: -1.7, width: 7.5, height: 3.4), cornerRadius: 0.7)
                teeth.addPath(bar, transform: transform)
                bevel.addPath(Path(CGRect(x: side < 0 ? -6 : -1.5, y: -1.7, width: 7.5, height: 0.75)), transform: transform)
                recess.addPath(Path(CGRect(x: side < 0 ? -6 : -1.5, y: 1.1, width: 7.5, height: 0.6)), transform: transform)
                let stitchX = x + side * 15
                stitches.move(to: CGPoint(x: stitchX, y: y - 1.5)); stitches.addLine(to: CGPoint(x: stitchX + slope * 3, y: y + 1.5))
            }
            c.stroke(stitches, with: .color(color(0x738386).opacity(0.58)), style: StrokeStyle(lineWidth: 0.6, lineCap: .round))
            c.fill(teeth, with: .color(color(0x8B9698)))
            c.fill(bevel, with: .color(color(0xD5DAD6).opacity(0.75)))
            c.fill(recess, with: .color(color(0x34434A)))
        }
        var c = context
        c.translateBy(x: 196.5, y: join(p) + 64 * depart)
        c.addFilter(.shadow(color: .black.opacity(0.32), radius: 2.5, x: 0, y: 1.5))
        c.addFilter(.shadow(color: .black.opacity(0.18), radius: 7, x: 0, y: 5))
        let body = Path(roundedRect: CGRect(x: -15, y: -20, width: 30, height: 43), cornerRadius: 6)
        c.fill(body, with: .linearGradient(Gradient(colors: [color(0xAAB3B3), color(0x717C7F), color(0x455255)]), startPoint: CGPoint(x: -15, y: -15), endPoint: CGPoint(x: 15, y: 20)))
        c.stroke(body, with: .color(color(0x394A4E)), lineWidth: 1)
        c.fill(Path(roundedRect: CGRect(x: -10, y: -17, width: 20, height: 27), cornerRadius: 4), with: .color(color(0x596568)))
        c.fill(Path(roundedRect: CGRect(x: -5.5, y: -19, width: 11, height: 31), cornerRadius: 4), with: .linearGradient(Gradient(colors: [color(0xAFB7B7), color(0x6B7679), color(0x859092)]), startPoint: CGPoint(x: -5, y: 0), endPoint: CGPoint(x: 5, y: 0)))
        c.translateBy(x: 0, y: 11); c.rotate(by: .degrees(angle))
        var pull = Path(roundedRect: CGRect(x: -12, y: -4, width: 24, height: 64), cornerRadius: 9)
        pull.addRoundedRect(in: CGRect(x: -4, y: 43, width: 8, height: 10), cornerSize: CGSize(width: 3.5, height: 3.5))
        c.fill(pull, with: .linearGradient(Gradient(colors: [color(0xAAB2B2), color(0x727D7F), color(0x536267)]), startPoint: CGPoint(x: -12, y: 0), endPoint: CGPoint(x: 12, y: 55)), style: FillStyle(eoFill: true))
        c.stroke(pull, with: .color(color(0x3B4A4D)), lineWidth: 0.8)
        c.fill(Path(roundedRect: CGRect(x: -7, y: 3, width: 14, height: 28), cornerRadius: 4), with: .color(color(0x5B686C)))
        c.stroke(Path(roundedRect: CGRect(x: -10.5, y: -2, width: 21, height: 59), cornerRadius: 8), with: .color(.white.opacity(0.14)), lineWidth: 0.7)
    }
}
