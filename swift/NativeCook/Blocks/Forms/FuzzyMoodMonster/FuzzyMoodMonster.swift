import CoreText
import SwiftUI
import UIKit

// Port of the React Native reference. Mood m crossfades the same three bundled furballs,
// words and palette. All transforms are sampled from one pausable clock.
// 8 s: Meh 0–0.8; cubic slide to Grumpy 0.8–2; shake 2–3; cubic slide to Sunny 3–4.6;
// two 26 pt hops 4.7–5.2 and 5.3–5.8, with 0.2 s landing squashes; return 6–7.2; rest.
// Four 2 s breaths per loop. Drag anywhere sideways; hold the chosen mood for 2 s after
// release, return to Meh over 0.8 s, then restart. Reduced motion freezes the frame, initially Meh.
struct FuzzyMoodMonster: View {
    static let loop: Double = 8

    var brand = "tuft"
    var date = "Thu, 31 Oct"
    var question = "How’s your vibe\ntoday?"
    var action = "Log my vibe"
    var autoPlay = false

    private static let ball = 290.0
    private static let track = 300.0
    private static let thumb = 34.0
    private static let hold = 2.0
    private static let returnDuration = 0.8

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var fingerDown = false
    @State private var accumulated = 0.0
    @State private var anchor = Date()
    @State private var running = false
    @State private var origin = 0.0
    @State private var touching = false
    @State private var manual = false
    @State private var released = -1.0
    @State private var userMood = 1.0
    @State private var lastStep = 1
    @State private var touchID = 0
    @State private var reducedFrame = Frame()
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private struct SettleKey: Equatable {
        var touchID: Int
        var running: Bool
    }
    private struct Mood {
        var word: String
        var note: String
        var top: UInt32
        var bottom: UInt32
        var deep: UInt32
        var image: String
    }
    private static let moods = [
        Mood(word: "Grumpy", note: "Everything is a little loud today.", top: 0xFFE3DC, bottom: 0xF4A493, deep: 0xB7311C, image: "monster-grumpy"),
        Mood(word: "Meh", note: "Not bad, not great. Just here.", top: 0xFFF2DC, bottom: 0xFFC57E, deep: 0xB76A00, image: "monster-meh"),
        Mood(word: "Sunny", note: "Light, easy and ready to go.", top: 0xEDF8DD, bottom: 0xAEDD84, deep: 0x2F7F1A, image: "monster-sunny")
    ]
    private static let ink = color(0x2B1B17)
    private static func color(_ value: UInt32) -> Color {
        Color(.sRGB, red: Double((value >> 16) & 255) / 255,
              green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255, opacity: 1)
    }
    private static func moodColor(_ mood: Double, _ key: KeyPath<Mood, UInt32>) -> Color {
        let m = min(2, max(0, mood))
        let a = moods[m <= 1 ? 0 : 1][keyPath: key]
        let b = moods[m <= 1 ? 1 : 2][keyPath: key]
        let u = m <= 1 ? m : m - 1
        func channel(_ shift: UInt32) -> Double {
            (Double((a >> shift) & 255) * (1 - u) + Double((b >> shift) & 255) * u).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }
    private enum Assets {
        static let registered: Void = {
            if let url = Bundle.main.url(forResource: "FuzzyMoodMonster-BricolageHeadline", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }()
        static func font(_ size: CGFloat) -> UIFont {
            _ = registered
            return UIFont(name: "BricolageHeadline-Regular", size: size) ?? .systemFont(ofSize: size)
        }
        // Decode each PNG once, before the TimelineView starts transforming it.
        static let images: [UIImage] = FuzzyMoodMonster.moods.map { mood in
            guard let url = Bundle.main.url(forResource: "FuzzyMoodMonster-\(mood.image)", withExtension: "png"),
                  let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
            return image.preparingForDisplay() ?? image
        }
    }
    private struct Frame {
        var mood = 1.0
        var breath = 0.0
        var shake = 0.0
        var hop = 0.0
        var squash = 0.0
        var finger = 0.0
    }
    private static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    private static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    private static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    private static func state(at t: Double) -> Frame {
        var f = Frame()
        if t < 2 { f.mood = 1 - cubic(seg(t, 0.8, 2)) }
        else if t < 3 { f.mood = 0 }
        else if t < 4.6 { f.mood = 2 * cubic(seg(t, 3, 4.6)) }
        else if t < 6 { f.mood = 2 }
        else { f.mood = 2 - cubic(seg(t, 6, 7.2)) }
        f.shake = sin(.pi * seg(t, 2, 3)) * 7 * sin(t * .pi * 12)
        for k in 0..<2 {
            let offset = 0.6 * Double(k)
            let u = seg(t, 4.7 + offset, 5.2 + offset)
            if u > 0 && u < 1 { f.hop = sin(.pi * u) * 26 }
            let land = seg(t, 5.2 + offset, 5.4 + offset)
            if land > 0 && land < 1 { f.squash = sin(.pi * land) }
        }
        f.finger = seg(t, 0.6, 0.8) * (1 - seg(t, 2, 2.2))
            + seg(t, 2.8, 3) * (1 - seg(t, 4.6, 4.8))
            + seg(t, 5.8, 6) * (1 - seg(t, 7.2, 7.4))
        return f
    }
    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(anchor)) : 0)
    }
    private func frame(at date: Date, honorReducedMotion: Bool = true) -> Frame {
        if reduced && honorReducedMotion { return reducedFrame }
        let now = elapsed(at: date)
        let t = max(0, now - origin).truncatingRemainder(dividingBy: Self.loop)
        var f = Frame()
        if manual {
            f.mood = userMood
            if !touching {
                let k = Self.cubic(Self.clamp((now - released - Self.hold) / Self.returnDuration))
                f.mood += (1 - f.mood) * k
            }
            let angry = Self.clamp(1 - f.mood / 0.5)
            f.shake = angry * 6 * sin(now * .pi * 12)
            f.hop = Self.clamp((f.mood - 1.6) / 0.4) * abs(sin(now * .pi * 1.6)) * 18
        } else if autoPlay {
            f = Self.state(at: t)
        }
        f.breath = 0.025 * sin(t / Self.loop * .pi * 8)
        return f
    }

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: !running)) { context in
                let f = frame(at: context.date)
                ZStack(alignment: .topLeading) {
                    Self.moodColor(f.mood, \.top)
                    contents(f, size: size, safe: safe)
                        .contentShape(Rectangle())
                        .gesture(drag(width: size.width))
                    footer(f, size: size, safe: safe)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            _ = Assets.images
            updateClock()
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, _ in updateClock() }
        .onChange(of: reduced) { _, enabled in
            if enabled { reducedFrame = frame(at: .now, honorReducedMotion: false) }
            updateClock()
        }
        .onChange(of: fingerDown) { _, down in
            if !down && touching { finishTouch() }
        }
        .task(id: SettleKey(touchID: touchID, running: running)) { await settle() }
    }

    private func contents(_ f: Frame, size: CGSize, safe: EdgeInsets) -> some View {
        let ballTop = size.height * 0.45 - Self.ball / 2
        let wordTop = ballTop + Self.ball + 6
        let trackLeft = (size.width - Self.track) / 2
        return ZStack(alignment: .topLeading) {
            ZStack {
                Self.moodColor(f.mood, \.bottom)
                    .clipShape(UnevenRoundedRectangle(topLeadingRadius: 200, topTrailingRadius: 200, style: .circular))
                LinearGradient(colors: [.clear, .white.opacity(0.2)], startPoint: .top, endPoint: .bottom)
            }
            .frame(width: size.width, height: size.height * 0.65)
            .compositingGroup().opacity(0.55)
            .offset(y: size.height * 0.35)

            LinearGradient(colors: [.white.opacity(0.9), .white.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: size.width, height: size.height * 0.5)
            NativeText(text: brand, font: Assets.font(26), color: UIColor(Self.ink), tracking: -0.5, centerVertically: true)
                .frame(width: 140, height: 34)
                .offset(x: 24, y: safe.top + 8)
            NativeText(text: date, font: .systemFont(ofSize: 14, weight: .semibold), color: UIColor(Self.ink.opacity(0.55)), alignment: .right, centerVertically: true)
                .frame(width: 160, height: 34)
                .offset(x: size.width - 24 - 160, y: safe.top + 8)
            NativeText(text: question, font: Assets.font(38), color: UIColor(Self.ink), lineHeight: 42, tracking: -1, alignment: .center)
                .frame(width: size.width - 48, height: 84)
                .offset(x: 24, y: safe.top + 64)

            NativeShadow(opacity: 0.1 - f.hop / 600)
                .frame(width: 180, height: 18)
                .scaleEffect(x: 1 - f.hop / 80 + 0.1 * f.squash, y: 1)
                .offset(x: (size.width - 180) / 2, y: ballTop + Self.ball - 24)
            ForEach(0..<3) { k in
                Image(uiImage: Assets.images[k])
                    .resizable().scaledToFit()
                    .frame(width: Self.ball, height: Self.ball)
                    .scaleEffect(x: 1 + f.breath + 0.08 * f.squash, y: 1 - f.breath - 0.1 * f.squash)
                    .offset(x: f.shake, y: -f.hop + 0.05 * Self.ball * f.squash)
                    .opacity(max(0, 1 - abs(f.mood - Double(k))))
                    .offset(x: (size.width - Self.ball) / 2, y: ballTop)
                    .accessibilityHidden(true)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Mood furball")
            ForEach(0..<3) { k in
                let mood = Self.moods[k]
                ZStack(alignment: .topLeading) {
                    NativeText(text: mood.word, font: Assets.font(46), color: UIColor(Self.color(mood.deep)), lineHeight: 54, tracking: -1.2, alignment: .center)
                        .frame(width: size.width - 48, height: 54)
                    NativeText(text: mood.note, font: .systemFont(ofSize: 15, weight: .medium), color: UIColor(Self.ink.opacity(0.6)), lineHeight: 20, alignment: .center)
                        .frame(width: size.width - 48, height: 20)
                        .offset(y: 56)
                }
                .frame(width: size.width - 48, height: 90, alignment: .topLeading)
                .opacity(max(0, 1 - abs(f.mood - Double(k)) * 1.6))
                .offset(x: 24, y: wordTop - (f.mood - Double(k)) * 18)
                .accessibilityHidden(abs(f.mood - Double(k)) >= 0.5)
            }
            slider(f)
                .offset(x: trackLeft, y: wordTop + 104)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func slider(_ f: Frame) -> some View {
        ZStack(alignment: .topLeading) {
            Capsule().fill(LinearGradient(colors: [Self.color(0xE8472E), Self.color(0xFFA329), Self.color(0x7CC63A)], startPoint: .leading, endPoint: .trailing))
                .frame(width: Self.track, height: 14).offset(y: 10)
            ZStack {
                Circle().fill(Self.ink.opacity(0.12))
                    .overlay(Circle().strokeBorder(Self.ink.opacity(0.35), lineWidth: 1.5))
                    .frame(width: Self.thumb + 18, height: Self.thumb + 18)
                    .scaleEffect(0.8 + 0.2 * f.finger).opacity(f.finger)
                Circle().fill(.white)
                    .overlay(Circle().strokeBorder(Self.ink.opacity(0.08), lineWidth: 3))
                    .shadow(color: Self.ink.opacity(0.25), radius: 8, y: 4)
                    .frame(width: Self.thumb, height: Self.thumb)
            }
            .frame(width: Self.thumb, height: Self.thumb)
            .offset(x: min(2, max(0, f.mood)) / 2 * (Self.track - Self.thumb))
        }
        .frame(width: Self.track, height: Self.thumb, alignment: .topLeading)
        .accessibilityLabel("Mood: \(Self.moods[Int(floor(min(2, max(0, f.mood)) + 0.5))].word). Drag sideways to change your vibe.")
    }

    private func footer(_ f: Frame, size: CGSize, safe: EdgeInsets) -> some View {
        NativeText(text: action, font: Assets.font(19), color: .white, alignment: .center, centerVertically: true)
            .frame(width: size.width - 48, height: 58)
            .background(Capsule().fill(Self.moodColor(f.mood, \.deep)))
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { _ in })
            .accessibilityAddTraits(.isButton)
            .offset(x: 24, y: size.height - safe.bottom - 14 - 58)
    }

    private func drag(width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, state, _ in state = true }
            .onChanged { value in
                guard !reduced else { return }
                if !touching {
                    touching = true
                    manual = true
                    touchID += 1
                }
                let trackLeft = (width - Self.track) / 2
                userMood = min(2, max(0, (value.location.x - trackLeft - Self.thumb / 2) / (Self.track - Self.thumb) * 2))
                let step = Int(floor(userMood + 0.5))
                if step != lastStep {
                    lastStep = step
                    haptic.impactOccurred()
                    haptic.prepare()
                }
            }
            .onEnded { _ in finishTouch() }
    }
    private func finishTouch() {
        guard touching else { return }
        touching = false
        released = elapsed(at: .now)
        touchID += 1
    }
    private func updateClock() {
        let now = Date()
        accumulated = elapsed(at: now)
        anchor = now
        running = scenePhase == .active && !reduced
    }
    private func settle() async {
        guard manual, !touching, running else { return }
        let remaining = max(0, Self.hold + Self.returnDuration - (elapsed(at: .now) - released))
        do { try await Task.sleep(for: .seconds(remaining)) } catch { return }
        guard !Task.isCancelled, !touching, running else { return }
        origin = elapsed(at: .now)
        manual = false
    }

    // Use the same CALayer shadow and view alpha as RN, without flattening them in SwiftUI.
    private struct NativeShadow: UIViewRepresentable {
        var opacity: Double

        final class ShadowView: UIView {
            override init(frame: CGRect) {
                super.init(frame: frame)
                isUserInteractionEnabled = false
                isAccessibilityElement = false
                backgroundColor = UIColor(FuzzyMoodMonster.ink)
                layer.shadowColor = UIColor(FuzzyMoodMonster.ink).cgColor
                layer.shadowOpacity = 0.6
                layer.shadowRadius = 10
                layer.shadowOffset = .zero
                layer.masksToBounds = false
            }
            required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
            override func layoutSubviews() {
                super.layoutSubviews()
                // RN resolves oversized border radii against the view's bounds before drawing.
                let radius = min(90, min(bounds.width, bounds.height) / 2)
                layer.cornerRadius = radius
                layer.shadowPath = UIBezierPath(roundedRect: bounds, cornerRadius: radius).cgPath
            }
        }
        func makeUIView(context: Context) -> ShadowView { ShadowView(frame: .zero) }
        func updateUIView(_ view: ShadowView, context: Context) { view.alpha = CGFloat(opacity) }
    }

    // TextKit matches RN's fixed line heights and baseline placement for the bundled face.
    private struct NativeText: UIViewRepresentable {
        var text: String
        var font: UIFont
        var color: UIColor
        var lineHeight: CGFloat? = nil
        var tracking: CGFloat = 0
        var alignment: NSTextAlignment = .left
        var centerVertically = false

        final class TextView: UIView {
            let storage = NSTextStorage()
            let layout = NSLayoutManager()
            let container = NSTextContainer(size: .zero)
            var centerVertically = false
            override init(frame: CGRect) {
                super.init(frame: frame)
                isOpaque = false
                backgroundColor = .clear
                isUserInteractionEnabled = false
                isAccessibilityElement = true
                container.lineFragmentPadding = 0
                container.lineBreakMode = .byClipping
                layout.usesFontLeading = false
                layout.addTextContainer(container)
                storage.addLayoutManager(layout)
            }
            required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
            override func layoutSubviews() {
                super.layoutSubviews()
                container.size = bounds.size
                setNeedsDisplay()
            }
            override func draw(_ rect: CGRect) {
                layout.ensureLayout(for: container)
                let range = layout.glyphRange(for: container)
                let y = centerVertically ? (bounds.height - layout.usedRect(for: container).height) / 2 : 0
                layout.drawGlyphs(forGlyphRange: range, at: CGPoint(x: 0, y: y))
            }
        }
        func makeUIView(context: Context) -> TextView { TextView(frame: .zero) }
        func updateUIView(_ view: TextView, context: Context) {
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = alignment
            var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .kern: tracking, .paragraphStyle: paragraph]
            if let lineHeight {
                paragraph.minimumLineHeight = lineHeight
                paragraph.maximumLineHeight = lineHeight
                if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
            }
            let value = NSAttributedString(string: text, attributes: attributes)
            if !view.storage.isEqual(to: value) {
                view.storage.setAttributedString(value)
                view.setNeedsDisplay()
            }
            view.centerVertically = centerVertically
            view.accessibilityLabel = text
        }
    }
}

#Preview {
    FuzzyMoodMonster(autoPlay: true)
}
