import SwiftUI

enum EmojiRatingPopupMotion {
    // The timeline of one loop, in seconds: the dialog pops in, the stars appear, a finger runs along
    // them, the comment is typed, Submit is pressed and the dialog leaves in a burst of hearts.
    static let loop = 10.0
    static let openAt = 0.8
    static let rateAt = [2.4, 2.9, 3.4, 3.9, 4.4]
    static let focusAt = 4.8
    static let typeAt = 4.9
    static let typeEvery = 0.08
    static let pressAt = 6.2
    static let submitAt = 6.3
    // After a touch the loop waits this long, then starts again from the page.
    static let userHold = 3.0

    static let openSpring = Spring(duration: 0.45, bounce: 1 - 0.75)
    static let closeFor = 0.35
    static let fade = 0.2
    static let contentAfter = 0.7
    static let contentFor = 0.7
    static let faceFor = 0.15
    static let moodFor = 0.25
    static let squashFor = 0.07
    static let burstFor = 0.8
    static let submitColorFor = 0.25
    static let shrink = 0.9
    static let popFrom = 0.85
    static let popRise = 24.0
    static let scrimOpacity = 0.6

    static func inOutQuad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t) }
    static func outQuad(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) }
    static func inCubic(_ t: Double) -> Double { t * t * t }
    static func linear(_ t: Double) -> Double { t }

    static func stage(_ value: Double, _ start: Double, _ length: Double) -> Double {
        min(1, max(0, (value - start) / length))
    }

    static func backOut(_ p: Double) -> Double {
        1 + 2.70158 * pow(p - 1, 3) + 1.70158 * pow(p - 1, 2)
    }
}

private typealias M = EmojiRatingPopupMotion
private typealias Channel = EmojiRatingPopupChannel

private enum Palette {
    static let page = Color(hex: 0xf3f4f7)
    static let scrim = Color(hex: 0x3c4350)
    static let blue = Color(hex: 0x4aa3df)
    static let submitOn = Color(hex: 0x2f7de1)
    static let submitOff = Color(hex: 0x8a8a8a)
    static let ink = Color(hex: 0x111318)
    static let mute = Color(hex: 0x6e7380)
    static let star = Color(hex: 0xffbe2e)
    static let like = Color(hex: 0xff4d6a)
    static let storyRing = Color(hex: 0xff7a59)
    static let storySeen = Color(hex: 0xd5d8de)
    static let box = Color(hex: 0xf7f9fc)
    static let shadow = Color.rgba(17, 19, 24, 1)
}

private enum Metrics {
    static let dialogWidth = 336.0
    static let dialogHeight = 490.0
    static let dialogRadius = 32.0
    static let padding = 20.0
    static let boxHeight = 84.0
    static let buttonHeight = 48.0
    static let faceTop = 52.0
    static let copyTop = 146.0
    static let starsTop = 228.0
    static let starsHeight = 48.0
    static let lowerTop = 290.0
    static let maxComment = 140

    static func photo(_ id: String, _ w: Int, _ h: Int? = nil) -> URL? {
        URL(string: "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(w)&h=\(h ?? w)&q=80")
    }

    // The page behind the dialog: a row of stories and a feed of travel posts.
    static let stories: [(name: String, photo: String)] = [
        ("You", "photo-1544005313-94ddf0286df2"),
        ("Lena", "photo-1517841905240-472988babdf9"),
        ("Marco", "photo-1531891437562-4301cf35b7e4"),
        ("Aiko", "photo-1487412720507-e7ab37603c6f"),
        ("Sam", "photo-1535713875002-d1d0cf377fde"),
    ]

    struct Post {
        let name: String
        let meta: String
        let text: String
        let avatar: String
        let photo: String
        let likes: String
        let comments: String
    }

    static let posts = [
        Post(
            name: "Lena Fischer", meta: "Oia, Santorini · 2h",
            text: "Golden hour from the cliffs. Worth every one of those steps.",
            avatar: "photo-1517841905240-472988babdf9", photo: "photo-1570077188670-e3a8d69ac5ff",
            likes: "1.2k", comments: "84"
        ),
        Post(
            name: "Marco Bellini", meta: "Manarola, Italy · 5h",
            text: "Five villages, one train pass and far too much focaccia.",
            avatar: "photo-1531891437562-4301cf35b7e4", photo: "photo-1516483638261-f4dbaf036963",
            likes: "962", comments: "41"
        ),
    ]
    /// The face for each number of stars (0 means none yet): the grin is the face at rest.
    static let faceOf = [3.0, 0, 1, 2, 3, 4]
    /// The header's field and the wave over it, for none yet and for one to five stars.
    static let field: [UInt32] = [0xf7d774, 0xf29c9c, 0xf5be8c, 0xf7d774, 0xc9e49a, 0x9ae3b5]
    static let wave: [UInt32] = [0xfbe59a, 0xf7b9b9, 0xf8d3ae, 0xfbe59a, 0xdcf0b8, 0xbdf0cf]
    /// Six hearts leave the emoji: sideways reach, rise and size.
    static let hearts: [(dx: Double, rise: Double, size: Double)] = [
        (-64, 96, 16), (-38, 130, 20), (-12, 104, 14), (14, 138, 18), (40, 100, 22), (66, 120, 15),
    ]
}

/// A rating dialog over a page: an emoji that changes with the stars (a frown to heart eyes) under
/// a wave that goes from red to green, a comment box and Submit. Stars light up under a finger run
/// along them. Submit sends the dialog away in a burst of hearts, and the "Rate the app" button on
/// the page brings it back.
struct EmojiRatingPopup: View {
    /// Runs the whole dialog by itself, in a loop, and leaves the page as it was.
    var autoPlay = false
    var title = "How would you rate the overall user experience of our app?"
    var subtitle = "Do you find the app easy to use?"
    var commentLabel = "Can you tell us more?"
    var placeholder = "We'd love to hear more!"
    var cancelLabel = "Cancel"
    var submitLabel = "Submit"
    /// What the autoPlay run types in the box.
    var sampleComment = "Great app!"
    /// Called with the stars (1 to 5) and the comment when Submit is pressed.
    var onSubmit: ((Int, String) -> Void)? = nil
    var onCancel: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var stars = 0
    @State private var comment = ""
    @FocusState private var focused: Bool
    @State private var typing = false
    @State private var pressed = false
    @State private var isOpen = false
    /// True while something moves, so the clock only runs then.
    @State private var live = false
    @State private var closing = 0
    // 0 is the page alone, 1 the dialog in place; the spring may pass 1.
    @State private var open = Channel(0)
    @State private var out = Channel(0)
    @State private var content = Channel(0)
    @State private var burst = Channel(0)
    @State private var face = Channel(Metrics.faceOf[0])
    @State private var mood = Channel(0)
    @State private var squash = Channel(1)
    @State private var bounce = Channel(1)
    @State private var active = Channel(0)
    @State private var ready = Channel(0)
    @State private var lift = Channel(0)
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var touching = false
    @State private var selection = UISelectionFeedbackGenerator()

    private var canSubmit: Bool {
        stars > 0 && !comment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let dialogWidth = min(Metrics.dialogWidth, screen.width - 48)
            let top = max(insets.top + 12, (screen.height - Metrics.dialogHeight) / 2)

            ZStack(alignment: .topLeading) {
                page(top: insets.top + 4)
                TimelineView(.animation(paused: !live)) { timeline in
                    overlay(at: timeline.date, screen: screen, dialogWidth: dialogWidth, top: top)
                }
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Palette.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard autoPlay, !touching else { return }
                    touching = true
                    touches += 1
                }
                .onEnded { _ in touching = false }
        )
        .onChange(of: canSubmit) { _, now in
            ready.go(to: now ? 1 : 0, .timing(M.submitColorFor, M.inOutQuad))
        }
        .onChange(of: focused || typing) { _, now in
            active.go(to: now ? 1 : 0, .timing(M.fade, M.inOutQuad))
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
            guard let end = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
            let seconds = note.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? 0.25
            lift.go(to: max(0, UIScreen.main.bounds.height - end.minY), .timing(seconds, M.outQuad))
        }
        .onAppear { selection.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches, sample: sampleComment)) { await loop() }
    }

    // MARK: The page

    private func page(top: Double) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text("Discover")
                    .font(.system(size: 34, weight: .heavy))
                    .tracking(-1)
                    .foregroundStyle(Palette.ink)
                    .frame(height: 40)
                Spacer(minLength: 0)
                Button {
                    openDialog()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(Palette.star)
                        Text("Rate the app")
                            .font(.system(size: 15, weight: .semibold))
                            .tracking(-0.2)
                            .foregroundStyle(Palette.ink)
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .background(Capsule().fill(.white).shadow(color: Palette.shadow.opacity(0.08), radius: 4, y: 2))
                }
                .buttonStyle(.plain)
            }
            .frame(height: 48)

            HStack(spacing: 0) {
                ForEach(Array(Metrics.stories.enumerated()), id: \.offset) { i, story in
                    if i > 0 { Spacer(minLength: 0) }
                    VStack(spacing: 6) {
                        EmojiRatingPopupPhoto(url: Metrics.photo(story.photo, 120))
                            .clipShape(Circle())
                            .padding(3 + 2.5)
                            .overlay(Circle().strokeBorder(i == 0 ? Palette.storySeen : Palette.storyRing, lineWidth: 2.5))
                            .frame(width: 64, height: 64)
                        Text(story.name)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Palette.ink)
                            .lineLimit(1)
                            .frame(height: 16)
                    }
                    .frame(width: 64)
                }
            }
            .padding(.vertical, 16)

            ForEach(Metrics.posts, id: \.name) { post in
                self.post(post)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.top, top)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Palette.page)
        .accessibilityHidden(isOpen)
    }

    private func post(_ post: Metrics.Post) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                EmojiRatingPopupPhoto(url: Metrics.photo(post.avatar, 96))
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 0) {
                    Text(post.name)
                        .font(.system(size: 15, weight: .bold))
                        .tracking(-0.2)
                        .frame(height: 20)
                    Text(post.meta)
                        .font(.system(size: 13))
                        .foregroundStyle(Palette.mute)
                        .frame(height: 17)
                }
                Spacer(minLength: 0)
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Palette.mute)
            }
            Text(post.text)
                .font(.system(size: 15))
                // 21 pt lines, as in the React Native version.
                .lineSpacing(21 - UIFont.systemFont(ofSize: 15).lineHeight)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 12)
            EmojiRatingPopupPhoto(url: Metrics.photo(post.photo, 720, 440))
                .frame(height: 188)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .padding(.top, 12)
            HStack(spacing: 6) {
                Image(systemName: "heart.fill")
                    .font(.system(size: 17))
                    .foregroundStyle(Palette.like)
                Text(post.likes)
                Image(systemName: "bubble.left")
                    .font(.system(size: 16))
                    .padding(.leading, 12)
                Text(post.comments)
                Spacer(minLength: 0)
                Image(systemName: "bookmark")
                    .font(.system(size: 16))
            }
            .font(.system(size: 14, weight: .semibold))
            .frame(height: 20)
            .padding(.top, 12)
        }
        .foregroundStyle(Palette.ink)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(.white)
                .shadow(color: Palette.shadow.opacity(0.04), radius: 1, y: 1)
                .shadow(color: Palette.shadow.opacity(0.06), radius: 12, y: 8)
        )
        .padding(.bottom, 12)
    }

    // MARK: The dialog

    private func overlay(at date: Date, screen: CGSize, dialogWidth: Double, top: Double) -> some View {
        let o = open.value(at: date)
        let gone = out.value(at: date)
        let scrim = M.scrimOpacity * min(1, max(0, o)) * (1 - gone)
        return ZStack(alignment: .topLeading) {
            Palette.scrim.opacity(scrim).allowsHitTesting(false)

            if isOpen {
                dialog(at: date, width: dialogWidth)
                    .frame(width: dialogWidth, height: Metrics.dialogHeight)
                    .background(
                        RoundedRectangle(cornerRadius: Metrics.dialogRadius, style: .continuous)
                            .fill(.white)
                            .shadow(color: Color(hex: 0x0e121c, opacity: 0.12), radius: 5, y: 4)
                            .shadow(color: Color(hex: 0x0e121c, opacity: 0.32), radius: 28, y: 24)
                    )
                    .scaleEffect(reduceMotion ? 1 : (M.popFrom + (1 - M.popFrom) * o) * (1 - (1 - M.shrink) * gone))
                    .opacity(reduceMotion ? min(1, max(0, o)) * (1 - gone) : min(1, o * 3) * (1 - gone))
                    .offset(
                        x: (screen.width - dialogWidth) / 2,
                        y: top + (reduceMotion ? 0 : (1 - o) * M.popRise) - room(top: top, screen: screen, date: date)
                    )
            }

            hearts(at: date, x: screen.width / 2, y: top + Metrics.faceTop + EmojiRatingPopupMood.size / 2)
        }
        .frame(width: screen.width, height: screen.height, alignment: .topLeading)
    }

    /// How far the keyboard pushes the dialog up so the box stays in view.
    private func room(top: Double, screen: CGSize, date: Date) -> Double {
        max(0, top + Metrics.dialogHeight - (screen.height - lift.value(at: date)) + 16)
    }

    private func dialog(at date: Date, width: Double) -> some View {
        let content = content.value(at: date)
        let intro = M.stage(content, 0, 0.5)
        let squash = squash.value(at: date)
        let faceScale = bounce.value(at: date)
        let moodNow = mood.value(at: date)
        let introOffset = reduceMotion ? 0 : (1 - intro) * 8

        return ZStack(alignment: .topLeading) {
            EmojiRatingPopupWave(width: width, mood: moodNow, field: Metrics.field, wave: Metrics.wave)

            EmojiRatingPopupMood(face: face.value(at: date))
                .scaleEffect(x: faceScale * (2 - squash), y: faceScale * squash)
                .offset(x: width / 2 - EmojiRatingPopupMood.size / 2, y: Metrics.faceTop)

            VStack(spacing: 0) {
                Text(title)
                    .font(.system(size: 19, weight: .bold))
                    .tracking(-0.4)
                    .lineSpacing(25 - UIFont.systemFont(ofSize: 19, weight: .bold).lineHeight)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text(subtitle)
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.mute)
                    .frame(height: 20)
                    .padding(.top, 6)
            }
            .frame(width: width - 48)
            .opacity(intro)
            .offset(x: 24, y: Metrics.copyTop + introOffset)

            HStack(spacing: EmojiRatingPopupStar.gap) {
                ForEach(0..<5, id: \.self) { index in
                    let appear = reduceMotion
                        ? (content > 0 ? 1 : 0)
                        : M.backOut(M.stage(content * M.contentFor, 0.1 + Double(index) * 0.06, 0.35))
                    EmojiRatingPopupStar(date: date, lit: stars >= index + 1, appear: appear, reduced: reduceMotion)
                }
            }
            .frame(height: Metrics.starsHeight)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0).onChanged { value in
                    let place = Int(floor(value.location.x / (EmojiRatingPopupStar.size + EmojiRatingPopupStar.gap))) + 1
                    setRating(min(5, max(1, place)), byHand: true)
                }
            )
            .offset(x: (width - EmojiRatingPopupStar.width(5)) / 2, y: Metrics.starsTop)

            lower(at: date, width: width - Metrics.padding * 2)
                .opacity(intro)
                .offset(x: Metrics.padding, y: Metrics.lowerTop + introOffset)
        }
        .frame(width: width, height: Metrics.dialogHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.dialogRadius, style: .continuous))
    }

    private func lower(at date: Date, width: Double) -> some View {
        let boxColor = Self.mix(0xc9d6e4, 1, 0x4aa3df, 1, active.value(at: date))
        let submitColor = Self.mix(0x8a8a8a, 1, 0x2f7de1, 1, ready.value(at: date))
        let button = RoundedRectangle(cornerRadius: 14, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            Text(commentLabel)
                .font(.system(size: 15, weight: .semibold))
                .tracking(-0.2)
                .foregroundStyle(Palette.ink)
                .frame(height: 20)
                .padding(.bottom, 8)
            TextField(
                "", text: $comment,
                prompt: Text(placeholder).foregroundColor(Color(hex: 0x999999)),
                axis: .vertical
            )
            .focused($focused)
            .font(.system(size: 15))
            .foregroundStyle(Palette.ink)
            .lineLimit(1...3)
            .onChange(of: comment) { _, text in
                if text.count > Metrics.maxComment { comment = String(text.prefix(Metrics.maxComment)) }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(width: width, height: Metrics.boxHeight, alignment: .topLeading)
            .background(button.fill(Palette.box))
            .overlay(button.strokeBorder(boxColor, lineWidth: 1.5))
            .accessibilityLabel(commentLabel)

            HStack(spacing: 12) {
                Button {
                    onCancel?()
                    dismiss()
                } label: {
                    Text(cancelLabel)
                        .font(.system(size: 16, weight: .semibold))
                        .tracking(-0.2)
                        .foregroundStyle(Palette.ink)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .overlay(button.strokeBorder(Palette.blue, lineWidth: 1.5))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Button {
                    submit()
                } label: {
                    Text(submitLabel)
                        .font(.system(size: 16, weight: .semibold))
                        .tracking(-0.2)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(button.fill(submitColor))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .allowsHitTesting(canSubmit)
                .scaleEffect(pressed ? 0.96 : 1)
            }
            .frame(width: width, height: Metrics.buttonHeight)
            .padding(.top, 16)
        }
        .frame(width: width, alignment: .topLeading)
    }

    private func hearts(at date: Date, x: Double, y: Double) -> some View {
        let p = burst.value(at: date)
        return ZStack(alignment: .topLeading) {
            ForEach(0..<Metrics.hearts.count, id: \.self) { i in
                let heart = Metrics.hearts[i]
                Image(systemName: "heart.fill")
                    .font(.system(size: heart.size * 0.85))
                    .foregroundStyle(Color(hex: 0xff4d6a))
                    .frame(width: heart.size, height: heart.size)
                    .scaleEffect(0.5 + 0.5 * M.stage(p, 0, 0.3))
                    .opacity(p <= 0 ? 0 : 1 - M.stage(p, 0.55, 0.45))
                    .offset(x: x - heart.size / 2 + heart.dx * p, y: y - heart.size / 2 - heart.rise * p)
            }
        }
        .allowsHitTesting(false)
    }

    /// A straight mix in RGB, so a fading border keeps its colour.
    private static func mix(_ a: UInt32, _ alphaA: Double, _ b: UInt32, _ alphaB: Double, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let from = Double((a >> shift) & 0xff)
            let to = Double((b >> shift) & 0xff)
            return (from + (to - from) * t) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: alphaA + (alphaB - alphaA) * t)
    }

    // MARK: Behaviour

    private func setRating(_ value: Int, byHand: Bool) {
        guard value != stars else { return }
        if byHand { selection.selectionChanged() }
        stars = value
        face.go(to: Metrics.faceOf[value], .timing(M.faceFor, M.inOutQuad))
        mood.go(to: Double(value), .timing(M.moodFor, M.outQuad))
        if !reduceMotion {
            squash.go([
                .init(to: 0.88, curve: .timing(M.squashFor, M.inOutQuad)),
                .init(to: 1, curve: .spring(Spring(duration: 0.22, bounce: 0.5))),
            ])
        }
    }

    private func openDialog() {
        isOpen = true
        live = true
        closing += 1
        burst.set(0)
        content.set(0)
        out.set(0)
        if reduceMotion {
            open.go(to: 1, .timing(M.fade, M.inOutQuad))
            content.go(to: 1, .timing(M.fade, M.inOutQuad))
            return
        }
        open.go(to: 1, .spring(M.openSpring))
        content.go(to: 1, .timing(M.contentFor, M.linear), delay: M.contentAfter)
        bounce.go([
            .init(to: 1.2, curve: .timing(0.16, M.inOutQuad), delay: 0.1),
            .init(to: 1, curve: .spring(Spring(duration: 0.3, bounce: 0.5))),
        ])
    }

    /// The page is back: the next opening starts from a blank dialog.
    private func reset() {
        open.set(0)
        out.set(0)
        content.set(0)
        face.set(Metrics.faceOf[0])
        mood.set(0)
        stars = 0
        comment = ""
        focused = false
        typing = false
        isOpen = false
        // The hearts are still flying for a moment: the clock stops once they are gone.
        let token = closing
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.burstFor), tolerance: .zero)
            if token == closing, !isOpen { live = false }
        }
    }

    private func dismiss() {
        closing += 1
        let token = closing
        let seconds = reduceMotion ? M.fade : M.closeFor
        out.go(to: 1, .timing(seconds, M.inCubic))
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds), tolerance: .zero)
            if token == closing { reset() }
        }
    }

    private func submit() {
        guard canSubmit else { return }
        onSubmit?(stars, comment)
        if !reduceMotion { burst.go(to: 1, .timing(M.burstFor, M.outQuad)) }
        dismiss()
    }

    // MARK: The autoPlay loop

    private struct Step {
        let at: Double
        let run: @MainActor (EmojiRatingPopup) -> Void
    }

    private func steps() -> [Step] {
        var steps = [Step(at: M.openAt) { $0.openDialog() }]
        for (i, at) in M.rateAt.enumerated() {
            steps.append(Step(at: at) { $0.setRating(i + 1, byHand: false) })
        }
        steps.append(Step(at: M.focusAt) { $0.typing = true })
        let sample = Array(sampleComment)
        for i in sample.indices {
            steps.append(Step(at: M.typeAt + Double(i) * M.typeEvery) { $0.comment = String(sample[...i]) })
        }
        steps.append(Step(at: M.pressAt) { $0.pressed = true })
        steps.append(Step(at: M.submitAt) {
            $0.pressed = false
            $0.submit()
        })
        return steps.enumerated().sorted { ($0.element.at, $0.offset) < ($1.element.at, $1.offset) }.map(\.element)
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        let steps = steps()
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            pressed = false
            typing = false
            if isOpen { dismiss() }
            try? await Task.sleep(for: .seconds(0.7), tolerance: .zero)
        }
        var origin = clock.now
        while !Task.isCancelled {
            for step in steps {
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
        let sample: String
    }
}

/// A photo that fills its frame, cropped, with nothing drawn until it has loaded.
private struct EmojiRatingPopupPhoto: View {
    let url: URL?

    var body: some View {
        Color.clear
            .overlay {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    }
                }
            }
            .clipped()
    }
}
