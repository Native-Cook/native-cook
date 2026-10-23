import SwiftUI
import UIKit

/// Timings of the autoPlay loop and of the sheet, in seconds unless noted.
enum BlurBackdropComposerMotion {
    // One loop: the page at rest, then three openings, each with its own photo behind the composer.
    static let rest = 0.8
    static let opening = 2.7
    static let openings = 3
    static let loop = 9.0
    // Inside an opening: the sheet rises, the name is typed, the sheet drops.
    static let typeAt = 0.6
    static let typeEvery = 0.09
    static let closeAt = 1.75
    static let names = ["Tokyo", "Maldives", "Swiss Alps"]
    // After a touch the loop waits this long, then starts again from the page.
    static let userHold = 3.0

    static let openDuration = 0.45
    static let openBounce = 0.1 // damping ratio 0.9
    static let closeDuration = 0.35
    static let fadeDuration = 0.2
    // The cards fade in and rise once the sheet is up: when they start, how long all of them take,
    // the gap between two, and how far each rises.
    static let cardsAfter = 0.3
    static let cardsFor = 0.6
    static let cardStagger = 0.1
    static let cardRise = 14.0
    // The photo drifts from this scale to the next while the composer is up.
    static let driftFrom = 1.05
    static let driftTo = 1.1
    static let driftSeconds = 6.0

    /// One card's share of the reveal, eased out (cubic).
    static func stage(_ progress: Double, _ index: Int) -> Double {
        let p = min(1, max(0, (progress - Double(index) * cardStagger) / (1 - 2 * cardStagger)))
        return 1 - (1 - p) * (1 - p) * (1 - p)
    }
}

private typealias M = BlurBackdropComposerMotion

private enum Palette {
    static let accent = Color(hex: 0xe8643a)
    static let ink = Color(hex: 0x111114)
    static let mute = Color(hex: 0x7c7c83)
    static let field = Color(hex: 0xf1f1f4)
    static let button = Color(hex: 0x1f1f22)
    static let tab = Color(hex: 0x9a9aa1)
    static let sheet = Color(hex: 0x20323a)
    static let glass = Color.white.opacity(0.2)
    static let glassEdge = Color.white.opacity(0.32)
    static let glassTop = Color.white.opacity(0.55)
    static let glassIcon = Color.white.opacity(0.24)
    static let keyboard = Color(hex: 0xd2d4da)
    static let key = Color.white
    static let keyLit = Color(hex: 0xb9bcc4)
    static let keyDark = Color(hex: 0xabafb9)
    static let keyDone = Color(hex: 0x0a7aff)
    static let keyboardIcon = Color(hex: 0x4b4b52)
}

private enum Metrics {
    static let margin = 20.0
    static let searchHeight = 38.0
    static let heroHeight = 176.0
    static let heroRadius = 24.0
    static let glassRadius = 22.0
    static let keyGap = 6.0
    static let keyHeight = 44.0
    static let keyRows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
    static let suggestions = ["I", "The", "I’m"]
    static let maxName = 22
    static let collageHeight = 158.0
    /// Where the collage's tilted cards sit: centre as a fraction of the width, top, tilt. Drawn in
    /// this order, so the middle card lies on top.
    static let collage: [(photo: Int, x: Double, y: Double, angle: Double)] = [
        (0, 0.2, 34, -14),
        (1, 0.8, 30, 12),
        (2, 0.5, 8, -3),
    ]
    static let tabs: [(label: String, symbol: String)] = [
        ("Trips", "airplane"),
        ("Explore", "safari"),
        ("Saved", "bookmark"),
        ("Profile", "person.crop.circle"),
    ]
}

/// A trips page with a "Create a Trip" button. The button raises a full-screen composer over a
/// blurred photo that changes on every opening: the name field, three frosted cards and a keyboard
/// drawn here, so nothing ever opens the system keyboard. The keys work by touch.
struct BlurBackdropComposer: View {
    /// Opens and closes the composer by itself, three times a loop, and leaves the page as it was.
    var autoPlay = false
    var title = "My Trips"
    var createLabel = "Create a Trip"
    var placeholder = "Trip name"
    /// Called with the name when Create (or the keyboard's done key) is pressed.
    var onCreate: ((String) -> Void)? = nil
    var onCancel: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var scene = 0
    @State private var name = ""
    @State private var lit: String? = nil
    @State private var isOpen = false
    /// 0 is the page alone, 1 the composer fully up.
    @State private var sheet = 0.0
    /// When the composer opened; drives the drift, the cards and the caret.
    @State private var openedAt = Date.now
    @State private var opened = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var touching = false
    @State private var keyHaptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            ZStack(alignment: .topLeading) {
                page(width: screen.width, top: insets.top)
                tabBar(bottom: insets.bottom)
                    .frame(width: screen.width, height: screen.height, alignment: .bottom)
                composer(screen: screen, insets: insets)
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color.white.ignoresSafeArea())
        .preferredColorScheme(isOpen ? .dark : .light)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard autoPlay, !touching else { return }
                    touching = true
                    touches += 1
                }
                .onEnded { _ in touching = false }
        )
        .onAppear { keyHaptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The page

    private func page(width: Double, top: Double) -> some View {
        let contentWidth = width - Metrics.margin * 2
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                BlurBackdropComposerPhoto(url: BlurBackdropComposerPhotos.avatar)
                    .clipShape(Circle())
                    .padding(4)
                    .overlay(Circle().strokeBorder(Palette.accent, lineWidth: 2))
                    .frame(width: 34, height: 34)
                Spacer(minLength: 0)
                HStack(spacing: 16) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 19, weight: .semibold))
                    Image(systemName: "plus")
                        .font(.system(size: 23, weight: .regular))
                }
                .foregroundStyle(Palette.accent)
            }
            .frame(height: 44)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Palette.mute)
                line("Search trips, places", 17, 22, .regular, Palette.mute, tracking: -0.3)
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: Metrics.searchHeight, maxHeight: Metrics.searchHeight, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.field))
            .padding(.top, 8)

            line(title, 34, 40, .heavy, Palette.ink, tracking: -1)
                .padding(.top, 20)
                .padding(.bottom, 16)

            hero(width: contentWidth)

            collage(width: contentWidth)
                .padding(.top, 20)

            line("Welcome, Traveler!", 22, 28, .heavy, Palette.ink, tracking: -0.5)
                .frame(maxWidth: .infinity)
                .padding(.top, 4)
            paragraph("Create your first trip and start planning your next adventure.", 15, 21, .regular, Palette.mute)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 32)
                .padding(.top, 6)

            Button {
                openByHand()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "plus")
                        .font(.system(size: 15, weight: .semibold))
                    line(createLabel, 17, 22, .semibold, .white, tracking: -0.2)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 28)
                .frame(height: 52)
                .background(Capsule().fill(Palette.button))
                .shadow(color: .black.opacity(0.22), radius: 12, y: 6)
            }
            .buttonStyle(BlurBackdropComposerPress())
            .frame(maxWidth: .infinity)
            .padding(.top, 20)
        }
        .padding(.horizontal, Metrics.margin)
        .padding(.top, top + 2)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.white)
        .accessibilityHidden(isOpen)
    }

    private func hero(width: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Color(hex: 0x6d7fa8)
            BlurBackdropComposerPhoto(url: BlurBackdropComposerPhotos.hero)
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x0a1028, opacity: 0.55), location: 0),
                    .init(color: Color(hex: 0x0a1028, opacity: 0.15), location: 0.55),
                    .init(color: Color(hex: 0x0a1028, opacity: 0), location: 1),
                ],
                startPoint: .topLeading, endPoint: UnitPoint(x: 0.7, y: 1)
            )
            VStack(alignment: .leading, spacing: 0) {
                line("Welcome to Wander!", 21, 26, .heavy, .white, tracking: -0.5)
                paragraph("Learn how to plan every detail of your trip in one place.", 13, 18, .regular, .white.opacity(0.88))
                    .frame(width: (width - 36) * 0.62, alignment: .leading)
                    .padding(.top, 4)
            }
            .padding(18)
        }
        .overlay(alignment: .topTrailing) {
            Image(systemName: "xmark")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(Circle().fill(.black.opacity(0.28)))
                .padding(16)
        }
        .overlay(alignment: .bottomLeading) {
            line("See How", 14, 18, .bold, Palette.accent)
                .padding(.horizontal, 16)
                .frame(height: 34)
                .background(Capsule().fill(.white))
                .padding(18)
        }
        .frame(width: width, height: Metrics.heroHeight)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.heroRadius, style: .continuous))
        .shadow(color: Color(hex: 0x0b1a2a, opacity: 0.18), radius: 20, y: 10)
    }

    private func collage(width: Double) -> some View {
        let card = (width * 0.36).rounded()
        return ZStack(alignment: .topLeading) {
            ForEach(Metrics.collage, id: \.photo) { item in
                BlurBackdropComposerPhoto(url: BlurBackdropComposerPhotos.collage[item.photo])
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .padding(4)
                    .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.white))
                    .shadow(color: Color(hex: 0x1a2230, opacity: 0.2), radius: 14, y: 8)
                    .frame(width: card, height: card * 0.76)
                    .rotationEffect(.degrees(item.angle))
                    .offset(x: item.x * width - card / 2, y: item.y)
            }
        }
        .frame(width: width, height: Metrics.collageHeight, alignment: .topLeading)
    }

    private func tabBar(bottom: Double) -> some View {
        HStack(spacing: 0) {
            ForEach(Metrics.tabs.indices, id: \.self) { i in
                let tint = i == 0 ? Palette.accent : Palette.tab
                VStack(spacing: 2) {
                    Image(systemName: Metrics.tabs[i].symbol)
                        .font(.system(size: 20, weight: i == 0 ? .semibold : .regular))
                        .frame(height: 24)
                    line(Metrics.tabs[i].label, 11, 14, .semibold, tint)
                }
                .foregroundStyle(tint)
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.top, 8)
        .padding(.bottom, bottom)
        .background(Color.white.opacity(0.96))
        .overlay(alignment: .top) {
            Rectangle().fill(Color.rgba(60, 60, 67, 0.2)).frame(height: 1 / displayScale)
        }
        .accessibilityHidden(isOpen)
    }

    // MARK: The composer

    private func composer(screen: CGSize, insets: EdgeInsets) -> some View {
        let keyWidth = (screen.width - Metrics.keyGap * 11) / 10
        return TimelineView(.animation(paused: !isOpen)) { timeline in
            let t = max(0, timeline.date.timeIntervalSince(openedAt))
            let cards = min(1, max(0, (t - M.cardsAfter) / M.cardsFor))
            let drift = reduceMotion ? 1 : M.driftFrom + (M.driftTo - M.driftFrom) * min(t / M.driftSeconds, 1)
            let caret = Int(t / 0.5) % 2 == 0

            ZStack(alignment: .top) {
                Palette.sheet
                BlurBackdropComposerBackdrop(scene: scene, size: screen)
                    .scaleEffect(drift)
                LinearGradient(
                    stops: [
                        .init(color: .black.opacity(0.34), location: 0),
                        .init(color: .black.opacity(0.08), location: 0.45),
                        .init(color: .black.opacity(0.12), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                Color.white.opacity(0.06)

                nav.padding(.top, insets.top + 4)

                nameRow(caret: caret)
                    .modifier(BlurBackdropComposerFieldRise(progress: sheet, reduced: reduceMotion))
                    .padding(.top, insets.top + 64)

                reveal(cards: cards, index: 0) {
                    VStack(alignment: .leading, spacing: 0) {
                        cardHead("calendar", label: "Itinerary")
                        HStack(alignment: .bottom) {
                            VStack(alignment: .leading, spacing: 0) {
                                small("Starts")
                                big("Set Dates")
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "arrow.right")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(.white.opacity(0.9))
                                .frame(height: 22)
                            Spacer(minLength: 0)
                            VStack(alignment: .trailing, spacing: 0) {
                                small("Ends")
                                big("Set Dates")
                            }
                        }
                        .padding(.top, 10)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, minHeight: 104, maxHeight: 104, alignment: .topLeading)
                    .modifier(BlurBackdropComposerGlass())
                }
                .padding(.horizontal, Metrics.margin)
                .padding(.top, insets.top + 134)

                HStack(spacing: 12) {
                    reveal(cards: cards, index: 1) {
                        halfCard("globe", "Timezone", "Bengaluru, India")
                    }
                    reveal(cards: cards, index: 2) {
                        halfCard("photo.fill", "Choose Image", "From your photos")
                    }
                }
                .frame(height: 112)
                .padding(.horizontal, Metrics.margin)
                .padding(.top, insets.top + 250)

                keyboard(keyWidth: keyWidth, bottom: insets.bottom)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
            .frame(width: screen.width, height: screen.height)
            .clipped()
            .modifier(BlurBackdropComposerSlide(progress: sheet, height: screen.height, reduced: reduceMotion))
            .allowsHitTesting(isOpen)
            .accessibilityHidden(!isOpen)
        }
    }

    private var nav: some View {
        HStack {
            Button {
                onCancel?()
                close()
            } label: {
                line("Cancel", 17, 22, .medium, .white)
                    .contentShape(Rectangle().inset(by: -12))
            }
            .buttonStyle(.plain)
            Spacer(minLength: 0)
            Button {
                create()
            } label: {
                line("Create", 16, 20, .bold, Palette.accent)
                    .padding(.horizontal, 18)
                    .frame(height: 36)
                    .background(Capsule().fill(.white))
                    .shadow(color: .black.opacity(0.16), radius: 8, y: 3)
            }
            .buttonStyle(BlurBackdropComposerPress())
        }
        .frame(height: 40)
        .padding(.horizontal, Metrics.margin)
    }

    private func nameRow(caret: Bool) -> some View {
        let empty = name.isEmpty
        let bar = RoundedRectangle(cornerRadius: 1.5).fill(Palette.accent)
            .frame(width: 3, height: 38)
            .padding(.horizontal, 2)
            .opacity(caret ? 1 : 0)
        return HStack(spacing: 0) {
            if empty { bar }
            line(empty ? placeholder : name, 36, 44, .bold, empty ? .white.opacity(0.55) : .white, tracking: -0.9)
                .lineLimit(1)
            if !empty { bar }
            Spacer(minLength: 0)
        }
        .frame(height: 52)
        .padding(.horizontal, Metrics.margin)
    }

    private func halfCard(_ symbol: String, _ title: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            cardHead(symbol)
            Spacer(minLength: 0)
            big(title)
            small(detail)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .modifier(BlurBackdropComposerGlass())
    }

    private func cardHead(_ symbol: String, label: String? = nil) -> some View {
        HStack(spacing: 8) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Palette.glassIcon))
            if let label {
                line(label, 15, 20, .semibold, .white.opacity(0.92), tracking: -0.2)
            }
        }
    }

    private func keyboard(keyWidth: Double, bottom: Double) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(Metrics.suggestions.indices, id: \.self) { i in
                    line(Metrics.suggestions[i], 17, 22, .regular, Palette.ink)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .overlay(alignment: .leading) {
                            if i > 0 {
                                Rectangle().fill(.black.opacity(0.14)).frame(width: 1).padding(.vertical, 12)
                            }
                        }
                }
            }
            .frame(height: 44)

            VStack(spacing: 12) {
                ForEach(Array(Metrics.keyRows.enumerated()), id: \.offset) { r, row in
                    let holdsLit = row.uppercased().contains(lit ?? "-")
                    HStack(spacing: Metrics.keyGap) {
                        if r == 2 {
                            key(width: keyWidth * 1.3, fill: Palette.keyDark, accessibility: nil, action: nil) {
                                Image(systemName: "arrow.up").font(.system(size: 18, weight: .medium))
                            }
                            .padding(.trailing, keyWidth * 0.2)
                        }
                        ForEach(Array(row), id: \.self) { char in
                            let label = String(char).uppercased()
                            key(width: keyWidth, accessibility: String(char), action: { type(String(char)) }) {
                                Text(label).font(.system(size: 23))
                            }
                            .overlay(alignment: .bottom) {
                                if lit == label { callout(label, width: keyWidth + 18) }
                            }
                            .zIndex(lit == label ? 1 : 0)
                        }
                        if r == 2 {
                            key(width: keyWidth * 1.3, fill: Palette.keyDark, accessibility: "Delete", action: { type("back") }) {
                                Image(systemName: "delete.left").font(.system(size: 18, weight: .regular))
                            }
                            .padding(.leading, keyWidth * 0.2)
                        }
                    }
                    .padding(.horizontal, r == 1 ? keyWidth / 2 : 0)
                    .frame(maxWidth: .infinity)
                    .zIndex(holdsLit ? 1 : 0)
                }
                HStack(spacing: Metrics.keyGap) {
                    key(width: keyWidth * 2.3, fill: Palette.keyDark, accessibility: nil, action: nil) {
                        Text("123").font(.system(size: 16))
                    }
                    key(width: nil, fill: lit == " " ? Palette.keyLit : Palette.key, accessibility: "Space", action: { type(" ") }) {
                        Text("space").font(.system(size: 16))
                    }
                    key(width: keyWidth * 2.3, fill: Palette.keyDone, accessibility: "Done", action: { create() }) {
                        Text("done").font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
                    }
                }
            }
            .padding(.horizontal, Metrics.keyGap / 2)

            HStack {
                Image(systemName: "face.smiling")
                Spacer(minLength: 0)
                Image(systemName: "mic")
            }
            .font(.system(size: 22, weight: .regular))
            .foregroundStyle(Palette.keyboardIcon)
            .padding(.horizontal, 26)
            .frame(height: 40)
            .padding(.top, 4)
        }
        .padding(.bottom, max(bottom - 18, 6))
        .background(Palette.keyboard)
    }

    private func key<Label: View>(
        width: Double?, fill: Color = Palette.key, accessibility: String?, action: (() -> Void)?,
        @ViewBuilder label: () -> Label
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 8, style: .continuous)
        let face = label()
            .foregroundStyle(.black)
            .frame(width: width.map { CGFloat($0) }, height: Metrics.keyHeight)
            .frame(maxWidth: width == nil ? .infinity : nil)
            .background {
                // A hard one-point shadow under the key, as on the system keyboard.
                shape.fill(.black.opacity(0.3)).offset(y: 1)
                shape.fill(fill)
            }
        return Group {
            if let action {
                Button(action: action) { face }
                    .buttonStyle(.plain)
                    .accessibilityLabel(accessibility ?? "")
            } else {
                face.accessibilityHidden(true)
            }
        }
    }

    /// The enlarged letter over a key while it is typed.
    private func callout(_ label: String, width: Double) -> some View {
        Text(label)
            .font(.system(size: 32))
            .foregroundStyle(.black)
            .frame(width: width, height: 58)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(.white)
                    .shadow(color: .black.opacity(0.28), radius: 4, y: 1)
            )
            .offset(y: -(Metrics.keyHeight - 2))
            .allowsHitTesting(false)
    }

    private func reveal<Content: View>(cards: Double, index: Int, @ViewBuilder content: () -> Content) -> some View {
        let p = reduceMotion ? (cards > 0 ? 1 : 0) : M.stage(cards, index)
        return content()
            .opacity(p)
            .scaleEffect(reduceMotion ? 1 : 0.97 + 0.03 * p)
            .offset(y: reduceMotion ? 0 : (1 - p) * M.cardRise)
    }

    private func small(_ text: String) -> some View {
        line(text, 13, 17, .regular, .white.opacity(0.72))
    }

    private func big(_ text: String) -> some View {
        line(text, 17, 22, .bold, .white, tracking: -0.3)
    }

    /// One line of text in a box as tall as the React Native line height.
    private func line(_ text: String, _ size: Double, _ height: Double, _ weight: Font.Weight, _ color: Color, tracking: Double = 0) -> some View {
        Text(text)
            .font(.system(size: size, weight: weight))
            .tracking(tracking)
            .foregroundStyle(color)
            .frame(height: height)
    }

    /// Wrapped text whose lines are `height` apart, like a React Native line height.
    private func paragraph(_ text: String, _ size: Double, _ height: Double, _ weight: Font.Weight, _ color: Color) -> some View {
        let gap = height - UIFont.systemFont(ofSize: size, weight: weight.blurBackdropComposerUIKit).lineHeight
        return Text(text)
            .font(.system(size: size, weight: weight))
            .foregroundStyle(color)
            .lineSpacing(gap)
            .padding(.vertical, gap / 2)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: Behaviour

    private func open(_ index: Int) {
        scene = index % BlurBackdropComposerPhotos.backdrops.count
        name = ""
        isOpen = true
        openedAt = .now
        withAnimation(reduceMotion ? .easeInOut(duration: M.fadeDuration) : .spring(duration: M.openDuration, bounce: M.openBounce)) {
            sheet = 1
        }
    }

    private func close() {
        // Ease-in cubic, also for the reduced-motion fade.
        let curve = Animation.timingCurve(0.32, 0, 0.67, 0, duration: reduceMotion ? M.fadeDuration : M.closeDuration)
        withAnimation(curve, completionCriteria: .logicallyComplete) {
            sheet = 0
        } completion: {
            if sheet == 0 { isOpen = false }
        }
    }

    private func openByHand() {
        open(opened)
        opened += 1
    }

    private func type(_ char: String) {
        keyHaptic.selectionChanged()
        if char == "back" {
            name = String(name.dropLast())
        } else if name.count >= Metrics.maxName {
            return
        } else if char == " " {
            if !name.isEmpty, !name.hasSuffix(" ") { name += " " }
        } else {
            name += name.isEmpty ? char.uppercased() : char
        }
    }

    private func create() {
        onCreate?(name)
        close()
    }

    // MARK: The autoPlay loop

    private struct Step {
        let at: Double
        let run: @MainActor (BlurBackdropComposer) -> Void
    }

    /// Every step of one loop, at its own time from the start.
    private static let steps: [Step] = {
        var steps: [Step] = []
        for k in 0..<M.openings {
            let start = M.rest + Double(k) * M.opening
            steps.append(Step(at: start) { $0.open(k) })
            let word = Array(M.names[k])
            for i in word.indices {
                let at = start + M.typeAt + Double(i) * M.typeEvery
                steps.append(Step(at: at) {
                    $0.name = String(word[...i])
                    $0.lit = String(word[i]).uppercased()
                })
                steps.append(Step(at: at + 0.07) { $0.lit = nil })
            }
            steps.append(Step(at: start + M.closeAt) { $0.close() })
        }
        return steps.enumerated().sorted { ($0.element.at, $0.offset) < ($1.element.at, $1.offset) }.map(\.element)
    }()

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            // Back to the page, then the loop from its first second.
            close()
            lit = nil
            try? await Task.sleep(for: .seconds(0.4), tolerance: .zero)
        }
        var origin = clock.now
        while !Task.isCancelled {
            for step in Self.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step.run(self)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

private extension Font.Weight {
    var blurBackdropComposerUIKit: UIFont.Weight {
        switch self {
        case .heavy: .heavy
        case .bold: .bold
        case .semibold: .semibold
        case .medium: .medium
        default: .regular
        }
    }
}

/// Dims a button while it is held.
private struct BlurBackdropComposerPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.8 : 1)
    }
}

/// A frosted card's fill and its edge, lighter along the top.
private struct BlurBackdropComposerGlass: ViewModifier {
    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: Metrics.glassRadius, style: .continuous)
        content
            .background(shape.fill(Palette.glass))
            .overlay {
                GeometryReader { proxy in
                    let top = min(1, Metrics.glassRadius / max(proxy.size.height, 1))
                    shape.strokeBorder(
                        LinearGradient(
                            stops: [.init(color: Palette.glassTop, location: 0), .init(color: Palette.glassEdge, location: top)],
                            startPoint: .top, endPoint: .bottom
                        ),
                        lineWidth: 1
                    )
                }
            }
    }
}

/// The name field settles a beat before the cards, as the sheet comes to rest.
private struct BlurBackdropComposerFieldRise: ViewModifier, Animatable {
    var progress: Double
    let reduced: Bool

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let p = reduced ? 1 : min(1, max(0, (progress - 0.4) / 0.6))
        content
            .opacity(p)
            .offset(y: (1 - p) * 10)
    }
}

/// Slides the composer up from below the screen. The spring may overshoot, but the sheet stops at the top.
private struct BlurBackdropComposerSlide: ViewModifier, Animatable {
    var progress: Double
    let height: Double
    /// Reduced motion fades the sheet in place.
    let reduced: Bool

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        content
            .opacity(reduced ? progress : 1)
            .offset(y: reduced ? 0 : (1 - min(progress, 1)) * height)
    }
}
