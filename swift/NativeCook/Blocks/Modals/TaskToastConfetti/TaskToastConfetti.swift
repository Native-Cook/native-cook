import SwiftUI

/// A task list for today. Ticking the first task strikes the text through, drops a springy toast
/// from the top while confetti bursts out of it, and a speech bubble pops with the next hint beside
/// a mascot. Everything on screen is a function of one clock, so a touch and the autoPlay loop run
/// the same motion.
struct TaskToastConfetti: View {
    /// The task that gets ticked.
    var task = "Begin my first habit"
    /// The tasks under it, which stay as they are.
    var otherTasks = ["Drink a glass of water", "Read ten pages"]
    var toastLabel = "Task completed!"
    /// The small chip at the left of the toast.
    var reward = "+5 ✦"
    var hintLabel = "Tap to complete"
    var nextTitle = "Have something in mind?"
    var nextLabel = "NEXT"
    /// Ticks the task by itself every `TaskToastConfettiMotion.loop` seconds and unticks it again.
    var autoPlay = false
    /// Called when the task is ticked by a touch.
    var onComplete: (() -> Void)? = nil

    private typealias M = TaskToastConfettiMotion
    private typealias Art = TaskToastConfettiArt

    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var clock = TaskToastConfettiClock()
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UINotificationFeedbackGenerator()

    private static let blue = Art.blue
    private static let blueDeep = Color(hex: 0x0b45e0)
    private static let page = Color(hex: 0xfdfdff)
    private static let ink = Color(hex: 0x14161f)
    private static let mute = Color(hex: 0xa3a7b5)
    private static let orange = Color(hex: 0xf39a10)
    private static let toast = Color(hex: 0xf04e1d)
    private static let mic = Color(hex: 0xffe9cf)

    private static let margin = 16.0
    /// Below the status bar: the gift icon's row, then the title's row.
    private static let headerBar = 112.0
    private static let banner = 56.0
    private static let rowHeight = 60.0
    private static let rowGap = 12.0
    private static let toastHeight = 52.0
    private static let hintHeight = 62.0
    private static let nextWidth = 262.0
    private static let nextHeight = 128.0
    private static let mascotSize = 64.0
    private static let tabBar = 66.0
    /// A tall, condensed heavy face for the title and the bubbles, as the design has.
    private static let display = "AvenirNextCondensed-Heavy"

    private static let tabs: [(label: String, icon: String)] = [
        ("Home", "house.fill"), ("Teams", "person.2"), ("Progress", "chart.bar"), ("Discover", "safari"),
    ]

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: !clock.isPlaying)) { timeline in
                screen(t: clock.time(at: timeline.date), insets: insets, size: size)
            }
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: touches) { await resume() }
    }

    // MARK: Clock

    private func complete() {
        guard !clock.busy(at: .now) else { return }
        clock.auto = nil
        clock.touch = .now
        touches += 1
        haptic.notificationOccurred(.success)
        onComplete?()
    }

    /// Frees the touch once its run is over and, with autoPlay, starts the loop again from rest.
    private func resume() async {
        if touches > 0 {
            try? await Task.sleep(for: .seconds(TaskToastConfettiClock.remaining), tolerance: .zero)
            guard !Task.isCancelled else { return }
            clock.touch = nil
            try? await Task.sleep(for: .seconds(M.resumeAfter), tolerance: .zero)
            guard !Task.isCancelled else { return }
        }
        if autoPlay { clock.auto = .now }
    }

    // MARK: Screen

    /// A straight mix of two colours, with no gamma, like React Native's `interpolateColor`.
    private static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let from = Double((a >> shift) & 0xff), to = Double((b >> shift) & 0xff)
            return (from + (to - from) * t) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }

    /// The display face at a size, with the gap between lines that makes them `lineHeight` apart.
    private static func display(_ size: Double, lineHeight: Double) -> (font: Font, spacing: Double) {
        let natural = UIFont(name: display, size: size)?.lineHeight ?? size * 1.37
        return (.custom(display, size: size), lineHeight - natural)
    }

    /// 1 while the task is ticked: in with the tick, out as it is unticked.
    private func ticked(_ t: Double) -> Double {
        M.seg(t, M.tickAt, M.tickAt + M.tickIn) * (1 - M.seg(t, M.untickAt, M.untickAt + M.untickIn))
    }

    private func screen(t: Double, insets: EdgeInsets, size: CGSize) -> some View {
        let header = insets.top + Self.headerBar
        let firstRow = header + Self.banner
        let toastTop = max(insets.top - 6, 14)
        let toastWidth = min(size.width - 2 * 28, 340)
        let tabs = Self.tabBar + insets.bottom
        let nextBottom = tabs + 22
        return ZStack(alignment: .topLeading) {
            Self.page
            VStack(spacing: 0) {
                headerBar(top: insets.top, height: header)
                banner
                taskRow(t: t)
                ForEach(otherTasks, id: \.self) { label in
                    HStack(spacing: 14) {
                        RoundedRectangle(cornerRadius: 9, style: .continuous).fill(.white)
                            .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous).strokeBorder(Color(hex: 0xc3c7d2), lineWidth: 2))
                            .frame(width: 28, height: 28)
                        taskText(label)
                    }
                    .modifier(TaskToastConfettiRow())
                    .padding(.top, Self.rowGap)
                }
            }
            hint(t: t).offset(x: Self.margin + 2, y: firstRow + Self.rowHeight + 12)
            footer.frame(width: size.width - Self.margin * 2, height: size.height - tabs - 14, alignment: .bottom)
                .offset(x: Self.margin)
            tabBar(height: tabs, bottom: insets.bottom).offset(y: size.height - tabs)
            next(t: t).offset(x: size.width - Self.margin - Self.nextWidth, y: size.height - nextBottom - Self.nextHeight)
            mascot(t: t).offset(
                x: max(6, size.width - Self.margin - Self.nextWidth - Self.mascotSize - 2),
                y: size.height - (nextBottom - 10) - Self.mascotSize
            )
            if !reduced {
                TaskToastConfettiConfetti(
                    time: t, at: M.confettiAt,
                    origin: CGPoint(x: size.width / 2, y: toastTop + Self.toastHeight / 2), size: size
                )
            }
            toast(t: t, top: toastTop, width: toastWidth)
                .offset(x: (size.width - toastWidth) / 2, y: toastTop)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func headerBar(top: Double, height: Double) -> some View {
        let title = Self.display(38, lineHeight: 44)
        return VStack(spacing: 0) {
            Image(systemName: "gift").font(.system(size: 22, weight: .regular)).foregroundStyle(.white)
                .frame(width: 26, height: 26)
                .padding(.trailing, 4)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .frame(height: 48)
            HStack(spacing: 0) {
                Art.logo
                Spacer(minLength: 0)
                HStack(spacing: 2) {
                    Text("Today").font(title.font).kerning(-0.6).foregroundStyle(.white).frame(height: 44)
                    Image(systemName: "chevron.down").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: 22, height: 22)
                        .padding(.top, 6)
                }
                Spacer(minLength: 0)
                Art.avatar
            }
            .padding(.bottom, 8)
            .frame(maxHeight: .infinity)
        }
        .padding(.horizontal, Self.margin)
        .padding(.top, top)
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .background(LinearGradient(stops: [
            .init(color: Self.blue, location: 0), .init(color: Self.blue, location: 0.7), .init(color: Self.blueDeep, location: 1),
        ], startPoint: .top, endPoint: .bottom))
    }

    private var banner: some View {
        HStack(spacing: 12) {
            Image(systemName: "bell").font(.system(size: 18, weight: .regular)).foregroundStyle(Self.blue)
                .frame(width: 21, height: 21)
            Text("Get notified about tasks")
                .font(.system(size: 17, weight: .heavy)).kerning(-0.2).foregroundStyle(Self.blue)
                .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "xmark").font(.system(size: 15, weight: .medium)).foregroundStyle(Self.mute)
                .frame(width: 20, height: 20)
        }
        .padding(.horizontal, 20)
        .frame(height: Self.banner)
        .frame(maxWidth: .infinity)
    }

    private func taskText(_ label: String) -> some View {
        Text(label)
            .font(.system(size: 17, weight: .semibold)).kerning(-0.2).foregroundStyle(Self.ink)
            .lineLimit(1)
            .frame(height: 22)
    }

    private func taskRow(t: Double) -> some View {
        let on = ticked(t)
        let into = M.seg(t, M.tickAt, M.tickAt + M.tickIn)
        let out = M.seg(t, M.untickAt, M.untickAt + M.untickIn)
        // The tick overshoots on its way in: 0.6 -> 1.2 -> 1.
        let tickScale = into < 0.6 ? 0.6 + 0.6 * M.easeOut(into / 0.6) : 1.2 - 0.2 * M.easeOut((into - 0.6) / 0.4)
        let draw = M.seg(t, M.tickAt + 0.1, M.tickAt + 0.1 + M.strikeIn) * (1 - out)
        return Button(action: complete) {
            HStack(spacing: 14) {
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(Self.mix(0xffffff, 0xa9aebb, on))
                    .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous).strokeBorder(Self.mix(0xc3c7d2, 0xa9aebb, on), lineWidth: 2))
                    .overlay(
                        Image(systemName: "checkmark").font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
                            .opacity(into * (1 - out))
                            .scaleEffect(reduced ? 1 : tickScale)
                    )
                    .frame(width: 28, height: 28)
                taskText(task)
                    .opacity(1 - 0.5 * on)
                    .overlay(alignment: .topLeading) {
                        GeometryReader { text in
                            Capsule().fill(Self.ink).frame(width: text.size.width * draw, height: 2).offset(y: 10.5)
                        }
                    }
            }
            .modifier(TaskToastConfettiRow())
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(task)
        .accessibilityAddTraits(on > 0.5 ? [.isButton, .isSelected] : .isButton)
    }

    private func hint(t: Double) -> some View {
        let gone = M.seg(t, M.hintOutAt, M.hintOutAt + M.hintOutIn)
        let back = M.seg(t, M.hintBackAt, M.hintBackAt + M.hintBackIn)
        let present = t < M.hintBackAt ? 1 - gone : back
        let scale = reduced ? 1 : t < M.hintBackAt ? 1 - M.easeIn(gone) : M.spring(t - M.hintBackAt, stiffness: M.popStiffness, damping: M.popDamping)
        let bob = reduced || present < 1 ? 0 : M.bob * sin(t / M.bobPeriod * tau)
        let s = max(scale, 0)
        return Text(hintLabel)
            .font(Self.display(28, lineHeight: 34).font).kerning(-0.3).foregroundStyle(.white)
            .lineLimit(1)
            .frame(height: Self.hintHeight)
            .padding(.horizontal, 28)
            .background(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 4, style: .continuous).fill(Self.orange)
                    .frame(width: 22, height: 22).rotationEffect(.degrees(45)).offset(x: 26, y: -7)
            }
            .background(
                Capsule().fill(Self.orange)
                    .shadow(color: .rgba(150, 80, 0, 0.18), radius: 2, y: 2)
                    .shadow(color: .rgba(214, 120, 0, 0.30), radius: 12, y: 10)
            )
            .opacity(reduced ? present : min(1, present * 4))
            // Scaled about a point 36 pt in from the left of its top edge, under the tail.
            .scaleEffect(s, anchor: .topLeading)
            .offset(x: 36 * (1 - s), y: bob)
            .allowsHitTesting(false)
    }

    private var footer: some View {
        let scribble = Self.display(25, lineHeight: 27)
        return HStack(alignment: .bottom, spacing: 0) {
            VStack(spacing: 0) {
                Text("Looking 4\ninspiration?")
                    .font(scribble.font).lineSpacing(scribble.spacing).foregroundStyle(Art.scribble)
                    .multilineTextAlignment(.center)
                    .rotationEffect(.degrees(-3))
                Art.scribbleArrow
            }
            .padding(.leading, 56)
            Spacer(minLength: 0)
            HStack(spacing: 12) {
                Circle().fill(Self.mic).frame(width: 62, height: 62)
                    .overlay(Image(systemName: "mic.fill").font(.system(size: 22)).foregroundStyle(Self.blue))
                Circle().fill(Self.blue)
                    .shadow(color: .rgba(10, 40, 140, 0.25), radius: 2.5, y: 2)
                    .shadow(color: .rgba(26, 92, 255, 0.35), radius: 11, y: 10)
                    .frame(width: 74, height: 74)
                    .overlay(Image(systemName: "plus").font(.system(size: 31, weight: .regular)).foregroundStyle(.white))
            }
            .padding(.bottom, 14)
        }
        .allowsHitTesting(false)
    }

    private func tabBar(height: Double, bottom: Double) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(Self.tabs.enumerated()), id: \.offset) { i, tab in
                VStack(spacing: 4) {
                    Image(systemName: tab.icon).font(.system(size: 22)).frame(height: 27)
                    Text(tab.label).font(.system(size: 13, weight: .semibold)).frame(height: 16)
                }
                .foregroundStyle(i == 0 ? Self.blue : Self.mute)
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.top, 8)
        .frame(height: height - bottom, alignment: .top)
        .padding(.bottom, bottom)
        .frame(maxWidth: .infinity)
        .background(Self.page)
    }

    private func mascot(t: Double) -> some View {
        let away = M.seg(t, M.nextAwayAt, M.nextAwayAt + M.nextAwayIn)
        let pop = t < M.nextAwayAt ? M.spring(t - M.nextAt, stiffness: 220, damping: 9) : 1 - M.easeIn(away)
        let visible = t >= M.nextAt && t < M.nextAwayAt + M.nextAwayIn
        let p = max(pop, 0)
        // It hops once more as the confetti lands, then sits still.
        let hop = t < M.nextAwayAt ? -10 * max(0, sin(min(1, M.seg(t, M.confettiAt, M.confettiAt + 0.45)) * .pi)) : 0
        return Art.mascot
            .rotationEffect(.degrees(reduced ? 0 : (1 - min(pop, 1)) * -18))
            .scaleEffect(reduced ? 1 : p)
            .offset(y: reduced ? 0 : (1 - p) * 28 + hop)
            .opacity(reduced ? (visible ? M.seg(t, M.nextAt, M.nextAt + M.reducedFade) * (1 - away) : 0) : visible ? 1 : 0)
            .allowsHitTesting(false)
    }

    private func next(t: Double) -> some View {
        let away = M.seg(t, M.nextAwayAt, M.nextAwayAt + M.nextAwayIn)
        let pop = t < M.nextAwayAt ? M.spring(t - M.nextAt - 0.08, stiffness: M.popStiffness, damping: M.popDamping) : 1 - M.easeIn(away)
        let visible = t >= M.nextAt && t < M.nextAwayAt + M.nextAwayIn
        let title = Self.display(30, lineHeight: 32)
        return VStack(alignment: .leading, spacing: 0) {
            Text(nextTitle).font(title.font).lineSpacing(title.spacing).kerning(-0.3).foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
            Spacer(minLength: 0)
            Text("\(nextLabel) ›").font(.system(size: 15, weight: .black)).kerning(0.4).foregroundStyle(.white)
                .frame(height: 18)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.horizontal, 24)
        .padding(.top, 20)
        .padding(.bottom, 18)
        .frame(width: Self.nextWidth, height: Self.nextHeight)
        .background(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: 5, style: .continuous).fill(Self.orange)
                .frame(width: 26, height: 26).rotationEffect(.degrees(45)).offset(x: -7, y: -20)
        }
        .background(
            RoundedRectangle(cornerRadius: 40, style: .continuous).fill(Self.orange)
                .shadow(color: .rgba(150, 80, 0, 0.2), radius: 4, y: 3)
                .shadow(color: .rgba(214, 120, 0, 0.32), radius: 18, y: 18)
        )
        .scaleEffect(reduced ? 1 : max(pop, 0), anchor: .bottomLeading)
        .opacity(reduced ? (visible ? M.seg(t, M.nextAt, M.nextAt + M.reducedFade) * (1 - away) : 0) : visible ? 1 : 0)
        .allowsHitTesting(false)
    }

    private func toast(t: Double, top: Double, width: Double) -> some View {
        let away = M.seg(t, M.toastAwayAt, M.toastAwayAt + M.toastAwayIn)
        let visible = t >= M.toastAt && t < M.toastAwayAt + M.toastAwayIn
        // From above the screen to its place, past it and back; away, it goes up and out again.
        let drop = M.spring(t - M.toastAt, stiffness: M.toastStiffness, damping: M.toastDamping)
        let rise = M.easeIn(away)
        let offscreen = top + Self.toastHeight + 16
        let opacity = reduced
            ? (visible ? M.seg(t, M.toastAt, M.toastAt + M.reducedFade) * (1 - M.seg(t, M.toastAwayAt, M.toastAwayAt + M.reducedFade)) : 0)
            : (visible ? 1 : 0)
        return HStack(spacing: 12) {
            Text(reward).font(.system(size: 14, weight: .black)).foregroundStyle(Self.toast)
                .frame(height: 18)
                .padding(.horizontal, 10).frame(height: 28)
                .background(.white, in: Capsule())
            Text(toastLabel).font(.system(size: 18, weight: .heavy)).kerning(-0.2).foregroundStyle(.white)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .frame(width: width, height: Self.toastHeight)
        .background(
            Capsule().fill(LinearGradient(colors: [Color(hex: 0xff6a35), Self.toast], startPoint: .top, endPoint: .bottom))
                .shadow(color: .rgba(120, 30, 0, 0.25), radius: 3, y: 3)
                .shadow(color: .rgba(190, 50, 10, 0.35), radius: 14, y: 14)
        )
        .scaleEffect(reduced ? 1 : (0.8 + 0.2 * min(drop, 1)) * (1 - 0.2 * rise))
        .offset(y: reduced ? 0 : -offscreen * (1 - drop) - offscreen * rise)
        .opacity(opacity)
        .allowsHitTesting(false)
    }
}

/// A task row's grey pill.
private struct TaskToastConfettiRow: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 60)
            .background(Color(hex: 0xf2f3f7), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .padding(.horizontal, 16)
    }
}

#Preview {
    TaskToastConfetti(autoPlay: true)
}
