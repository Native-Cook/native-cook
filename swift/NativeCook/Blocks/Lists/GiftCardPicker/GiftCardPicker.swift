import SwiftUI
import UIKit

struct GiftCardPicker: View {
    static let loop = 19.87
    let autoPlay: Bool
    // As in RN, freezeAt is milliseconds into the first autoplay loop.
    let freezeAt: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var origin = Date.now
    @State private var wakeDate = Date.now
    @State private var manual = GiftCardPickerMotion()
    @State private var interrupted = false
    @State private var touching = false
    @State private var resumeDate: Date?
    @State private var resumeClosing: GiftCardPickerMotion?
    @State private var dragStart = 0.0
    @State private var pressGift = false
    @State private var pressLight = false
    @State private var pressDark = false

    init(autoPlay: Bool = false, freezeAt: Double = -1) {
        self.autoPlay = autoPlay
        self.freezeAt = freezeAt
    }

    private var playing: Bool { autoPlay && !reduceMotion && !interrupted }

    var body: some View {
        GeometryReader { safe in
            // Read insets before the full-bleed child ignores them.
            GeometryReader { full in
                let width = full.size.width
                let height = full.size.height
                let top = safe.safeAreaInsets.top
                let sheetHeight = height - top
                let time = clock(at: wakeDate)
                let state = motion(at: time)
                let frozen = playing && freezeAt >= 0
                let deadline = nextWake(at: wakeDate)
                ZStack(alignment: .topLeading) {
                    TimelineView(.animation(minimumInterval: 1.0 / 60, paused: frozen || !state.needsFrames(at: time, reduced: reduceMotion))) { context in
                        let t = clock(at: max(context.date, wakeDate))
                        let frame = motion(at: t).frame(at: t)
                        ZStack(alignment: .topLeading) {
                            GiftCardPickerPortfolio(width: width, height: height, topInset: top, bottomInset: safe.safeAreaInsets.bottom, scale: 1, press: pressGift, landing: frame.commit, onGiftPress: { pressed in
                                withAnimation(Animation(GiftCardPickerPressAnimation(duration: pressed ? 0.12 : 0.15))) { pressGift = pressed }
                            }, onGift: {
                                perform(haptic: true) { $0.present(at: $1, reduced: reduceMotion) }
                            })
                            .allowsHitTesting(frame.layer == 0)
                            .accessibilityHidden(frame.layer != 0)
                            Color.black.opacity(0.19 * frame.sheet).allowsHitTesting(false)
                            GiftCardPickerSheet(
                                width: width, height: sheetHeight, scale: width / 402, ky: sheetHeight / 812,
                                frame: frame, time: t,
                                pressLight: pressLight, pressDark: pressDark, reduceMotion: reduceMotion,
                                onDiscover: { perform(haptic: true) { $0.discover(at: $1) } },
                                onChooseStock: { perform(haptic: true) { $0.choose(at: $1, reduced: reduceMotion) } },
                                onClose: { perform(haptic: true) { $0.dismiss(at: $1, reduced: reduceMotion) } },
                                onCard: { index in select(index, velocity: 0) },
                                onChoose: { perform(haptic: true) { $0.commitSelection(at: $1, reduced: reduceMotion) } },
                                onLightPress: { pressed in withAnimation(Animation(GiftCardPickerPressAnimation(duration: pressed ? 0.12 : 0.15))) { pressLight = pressed } },
                                onDarkPress: { pressed in withAnimation(Animation(GiftCardPickerPressAnimation(duration: pressed ? 0.12 : 0.15))) { pressDark = pressed } },
                                onDragStart: {
                                    touchBegan()
                                    dragStart = manual.scroll.value(clock(at: .now))
                                    manual.scroll.set(dragStart)
                                    wakeDate = .now
                                },
                                onDrag: drag,
                                onDragEnd: { _, velocity, x, tapped in
                                    if tapped {
                                        let selected = Int((manual.scroll.value(clock(at: .now)) + Double(x - width / 2) / 227).rounded())
                                        select(selected, velocity: 0)
                                    } else {
                                        let v = -Double(velocity) / 227
                                        let base = Int(dragStart.rounded())
                                        let proposal = Int((manual.scroll.value(clock(at: .now)) + v * 0.18).rounded())
                                        select(min(base + 1, max(base - 1, proposal)), velocity: v)
                                    }
                                    touchEnded()
                                }
                            )
                            .frame(width: width, height: sheetHeight, alignment: .topLeading)
                            .clipShape(.rect(topLeadingRadius: 30, topTrailingRadius: 30))
                            .offset(y: top + (reduceMotion ? 0 : (1 - frame.sheet) * (sheetHeight + 20)))
                            .opacity(reduceMotion ? frame.sheet : 1)
                            .allowsHitTesting((frame.layer == 1 || frame.layer == 2) && frame.sheet > 0.001)
                            .accessibilityHidden(frame.layer != 1 && frame.layer != 2)
                            if frame.layer == 3, frame.commit > 0.001 {
                                GiftCardPickerSelectionFlight(
                                    width: width,
                                    sheetTop: top,
                                    sheetHeight: sheetHeight,
                                    frame: frame,
                                    reducedMotion: reduceMotion
                                )
                                .allowsHitTesting(false)
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel("\(GiftCardPickerData.stocks[frame.commitIndex].name) share certificate moving to portfolio")
                            }
                        }
                        .frame(width: width, height: height, alignment: .topLeading)
                    }
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .background(GiftCardPickerTheme.page)
                .clipped()
                .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in touchBegan() }.onEnded { _ in touchEnded() })
                // Wake only at animation/cue boundaries; no per-frame @State mutation.
                .task(id: deadline) {
                    guard let deadline else { return }
                    do { try await Task.sleep(for: .seconds(max(0, deadline.timeIntervalSinceNow))) } catch { return }
                    guard !Task.isCancelled else { return }
                    if let resumeDate, Date.now >= resumeDate { resume() }
                    if Date.now.timeIntervalSince(origin) >= Self.loop { resumeClosing = nil }
                    wakeDate = .now
                }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .allowsHitTesting(freezeAt < 0)
        .onAppear { origin = .now; wakeDate = origin }
        .onChange(of: reduceMotion) { _, _ in
            manual = GiftCardPickerMotion()
            interrupted = false
            resumeClosing = nil
            resumeDate = nil
            origin = .now
            wakeDate = origin
        }
    }

    private func clock(at date: Date) -> Double {
        let elapsed = max(0, date.timeIntervalSince(origin))
        if playing {
            let stopped = freezeAt >= 0 ? freezeAt / 1000 : elapsed
            return stopped.truncatingRemainder(dividingBy: Self.loop)
        }
        return elapsed
    }

    private func motion(at time: Double) -> GiftCardPickerMotion {
        guard playing else { return manual }
        if time >= Self.loop - 0.6, var closing = resumeClosing {
            // The resumed RN script still executes its normal dismiss cue at 19.5 s.
            if time >= 19.5 { closing.dismiss(at: 19.5, reduced: false) }
            return closing
        }
        return .script(at: time)
    }

    private func nextWake(at date: Date) -> Date? {
        let t = clock(at: date)
        let state = motion(at: t)
        var delays: [Double] = []
        if let boundary = state.nextBoundary(after: t, reduced: reduceMotion) { delays.append(boundary - t) }
        if playing {
            if freezeAt >= 0 { return nil }
            delays += GiftCardPickerMotion.cueTimes.filter { $0 > t + 0.00001 }.map { $0 - t }
            delays.append(Self.loop - t)
        }
        if let resumeDate { delays.append(resumeDate.timeIntervalSince(date)) }
        guard let delay = delays.filter({ $0 > 0.00001 }).min() else { return nil }
        return date.addingTimeInterval(delay)
    }

    private func touchBegan() {
        guard !touching else { return }
        let now = Date.now
        let t = clock(at: now)
        if playing {
            manual = motion(at: t)
            origin = now.addingTimeInterval(-t)
            interrupted = true
        }
        touching = true
        resumeDate = nil
        wakeDate = now
    }

    private func touchEnded() {
        touching = false
        if autoPlay && !reduceMotion { resumeDate = Date.now.addingTimeInterval(3.5) }
        wakeDate = .now
    }

    private func perform(haptic: Bool = false, _ action: (inout GiftCardPickerMotion, Double) -> Void) {
        touchBegan()
        if haptic { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        let t = clock(at: .now)
        action(&manual, t)
        wakeDate = .now
        touchEnded()
    }

    private func select(_ index: Int, velocity: Double) {
        perform { state, time in
            let before = state.active
            state.goTo(index, at: time, velocity: velocity, reduced: reduceMotion)
            if before != state.active { UISelectionFeedbackGenerator().selectionChanged() }
        }
    }

    private func drag(_ translation: CGFloat) {
        let raw = dragStart - Double(translation) / 227
        let value = raw < 0 ? -(-raw * 0.35) / (1 + -raw * 0.35) : raw > 4 ? 4 + ((raw - 4) * 0.35) / (1 + (raw - 4) * 0.35) : raw
        manual.scroll.set(value)
        let target = max(0, min(4, Int(value.rounded())))
        if target != manual.active {
            manual.pick(target, at: clock(at: .now))
            UISelectionFeedbackGenerator().selectionChanged()
        }
        wakeDate = .now
    }

    private func resume() {
        let now = Date.now
        let manualTime = clock(at: now)
        let frame = manual.frame(at: manualTime)
        resumeDate = nil
        resumeClosing = nil
        if frame.sheet > 0.01 {
            var closing = manual
            closing.shiftTime(by: Self.loop - 0.6 - manualTime)
            closing.dismiss(at: Self.loop - 0.6, reduced: false)
            resumeClosing = closing
            origin = now.addingTimeInterval(-(Self.loop - 0.6))
        } else { origin = now }
        interrupted = false
    }
}

private struct GiftCardPickerSelectionFlight: View {
    let width: CGFloat
    let sheetTop: CGFloat
    let sheetHeight: CGFloat
    let frame: GiftCardPickerMotion.Frame
    let reducedMotion: Bool

    private func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    private func easeOut(_ value: Double) -> Double { 1 - pow(1 - clamp(value), 3) }

    var body: some View {
        let raw = frame.commit
        let milliseconds = raw * (reducedMotion ? 420 : 2_540)
        let lift = reducedMotion ? 0 : easeOut(milliseconds / 240)
        let flip = reducedMotion ? 0 : clamp((milliseconds - 240) / 560)
        let certificateMix = reducedMotion ? clamp(milliseconds / 200) : (flip >= 0.5 ? 1 : 0)
        let flight = clamp((milliseconds - 1_500) / 620)
        let landing = clamp((milliseconds - 2_120) / 420)
        let startY = sheetTop + (328 - 62) * (sheetHeight / 812)
        let targetY = sheetTop - 62 + 557 + (87 - 260 * 0.3) / 2
        let y = reducedMotion ? startY : startY - 10 * lift + (targetY - (startY - 10)) * (flight * flight)
        let arc = reducedMotion ? 0 : sin(flight * .pi) * 34
        let flightScale = 1.05 + (0.3 - 1.05) * easeOut(flight)
        let scale = reducedMotion ? 1 : flightScale * (1 - 0.42 * landing)
        let opacity = raw <= 0 ? 0 : (1 - landing)
        let stock = GiftCardPickerData.stocks[min(max(frame.commitIndex, 0), GiftCardPickerData.stocks.count - 1)]

        ZStack {
            GiftCardPickerStockCard(stock: stock, reveal: 2).opacity(1 - certificateMix)
            GiftCardPickerCertificate(stock: stock, reversed: !reducedMotion).opacity(certificateMix)
        }
        .frame(width: 196, height: 260)
        .rotation3DEffect(.degrees(flip * 180), axis: (x: 0, y: 1, z: 0), perspective: 0.7)
        .rotationEffect(.degrees(reducedMotion ? 0 : -10 * flight))
        .scaleEffect(scale)
        .position(x: width / 2 + arc, y: y + 130)
        .opacity(opacity)
        .shadow(color: .black.opacity(0.22 * (1 - landing)), radius: 18, y: 10)
    }
}

private struct GiftCardPickerCertificate: View {
    let stock: GiftCardPickerStock
    let reversed: Bool
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color(hex: 0xF7F1E6))
            RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(stock.markBackground.opacity(0.65), lineWidth: 2).padding(8)
            VStack(spacing: 12) {
                Text("SHARE CERTIFICATE").font(.system(size: 10, weight: .semibold)).tracking(1.6)
                GiftCardPickerBrandMark(mark: stock.mark).frame(width: 58, height: 58)
                    .background(stock.markBackground, in: Circle()).clipShape(Circle())
                Text(stock.name).font(.system(size: 25, weight: .bold))
                Text("USD 3 · ONE GIFT SHARE").font(.system(size: 10).monospaced()).tracking(0.8)
                Text("NO. 0003–2026").font(.custom("JetBrainsMono-Medium", size: 9))
            }
            .foregroundStyle(Color(hex: 0x282326))
        }
        .frame(width: 196, height: 260)
        .rotation3DEffect(.degrees(reversed ? 180 : 0), axis: (x: 0, y: 1, z: 0))
    }
}
