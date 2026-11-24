import SwiftUI

/// Cadence's landing hero: "Plan your **week** calmly." with the bold word rolling through week, launch, wedding,
/// sprint letter by letter, the line reflowing by offset, a coloured underline stretching to match and the plan card
/// below cross-fading with the word. One word position drives it all (`RotatingWordHeadlineDriver`).
struct RotatingWordHeadline: View {
    static let loop: Double = RotatingWordHeadlineMotion.loop

    private typealias C = RotatingWordHeadlineColor
    fileprivate static let ink = C.hex(0x1f1c18)
    fileprivate static let mute = C.hex(0x6d665c)
    fileprivate static let words = ["week", "launch", "wedding", "sprint"]
    fileprivate static let accents: [Color] = [C.hex(0xe0663a), C.hex(0x3a6fd8), C.hex(0xc64b7c), C.hex(0x22936a)]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver: RotatingWordHeadlineDriver

    init(autoPlay: Bool = true) {
        _driver = State(initialValue: RotatingWordHeadlineDriver(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { geo in
            let W = geo.size.width
            let H = geo.size.height + geo.safeAreaInsets.top + geo.safeAreaInsets.bottom
            let side: CGFloat = 24
            let fontSize = min(46, (W * 0.112).rounded())
            let compact = H < 720

            VStack(alignment: .leading, spacing: 0) {
                nav
                chip.padding(.top, compact ? 18 : 30)
                RotatingWordHeadlineText(
                    words: Self.words, accents: Self.accents, lead: "Plan your", tail: "calmly.",
                    fontSize: fontSize, maxWidth: W - side * 2, ink: Self.ink, driver: driver)
                    .padding(.top, compact ? 14 : 22)
                Text("Calendars, checklists and timelines in one shared plan, so nobody has to ask what happens next.")
                    .font(.system(size: 17))
                    .lineSpacing(24 - 20.29)
                    .foregroundStyle(Self.mute)
                    .frame(maxWidth: 360, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    // RN's 24 pt line box puts half its extra leading above the first line and below the last.
                    .padding(.vertical, (24 - 20.29) / 2)
                    .padding(.top, compact ? 8 : 14)
                buttons.padding(.top, compact ? 16 : 24)
                RotatingWordHeadlinePlanCard(driver: driver)
                    .frame(maxWidth: .infinity, minHeight: 196, maxHeight: 268)
                    .padding(.top, compact ? 18 : 28)
                Text("Free for up to 5 people · No card needed")
                    .font(.system(size: 13))
                    .foregroundStyle(Self.mute)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, side)
            .padding(.top, 8)
            .padding(.bottom, 12)
            .frame(width: W, height: geo.size.height, alignment: .top)
        }
        .background(RotatingWordHeadlinePaper().ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { driver.reducedMotion = reduceMotion }
        .onChange(of: reduceMotion) { _, v in driver.reducedMotion = v }
    }

    private var nav: some View {
        HStack(spacing: 0) {
            RotatingWordHeadlineLogo()
            Text("Cadence")
                .font(.system(size: 18, weight: .bold))
                .tracking(-0.3)
                .foregroundStyle(Self.ink)
                .padding(.leading, 9)
            Spacer(minLength: 0)
            Text("Sign in")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Self.ink)
        }
        .frame(height: 44)
    }

    private var chip: some View {
        HStack(spacing: 0) {
            Text("New")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(C.hex(0xf8f5ef))
                .padding(.horizontal, 8)
                .frame(height: 22)
                .background(Capsule().fill(C.hex(0x1f1c18)))
                .padding(.trailing, 8)
            Text("Shared timelines for small teams")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Self.ink)
        }
        .padding(.leading, 4)
        .padding(.trailing, 12)
        .frame(height: 30)
        .background(
            Capsule().fill(Color(.sRGB, red: 1, green: 253 / 255, blue: 249 / 255, opacity: 0.85))
                .overlay(Capsule().strokeBorder(Color(.sRGB, red: 60 / 255, green: 45 / 255, blue: 30 / 255, opacity: 0.16), lineWidth: 1 / 3))
        )
    }

    private var buttons: some View {
        HStack(spacing: 12) {
            Button {} label: {
                Text("Start free")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(C.hex(0xfaf7f1))
                    .padding(.horizontal, 26)
                    .frame(height: 52)
                    .background(
                        ZStack(alignment: .top) {
                            LinearGradient(stops: [.init(color: C.hex(0x2e2a24), location: 0), .init(color: C.hex(0x1f1c18), location: 0.5), .init(color: C.hex(0x151310), location: 1)], startPoint: .top, endPoint: .bottom)
                            Rectangle().fill(Color.white.opacity(0.18)).frame(height: 1).padding(.horizontal, 1)
                        }
                        .clipShape(Capsule())
                    )
            }
            .buttonStyle(.plain)
            Button {} label: {
                Text("Take the tour")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Self.ink)
                    .padding(.horizontal, 20)
                    .frame(height: 52)
                    .background(Capsule().strokeBorder(C.hex(0x1f1c18, 0.16), lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }
}

/// Warm paper: a three-stop wash, a warm glow top right, a cool one bottom left and 2600 seeded grain dots,
/// drawn once (the canvas has no changing inputs).
private struct RotatingWordHeadlinePaper: View {
    var body: some View {
        Canvas(rendersAsynchronously: false) { ctx, size in
            let W = size.width
            let H = size.height
            let r = CGRect(origin: .zero, size: size)
            let c = RotatingWordHeadlineColor.self
            ctx.fill(Path(r), with: .linearGradient(
                Gradient(stops: [.init(color: c.hex(0xf8f5ef), location: 0), .init(color: c.hex(0xf3eee6), location: 0.55), .init(color: c.hex(0xece5da), location: 1)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: H)))
            ctx.fill(Path(r), with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(.sRGB, red: 240 / 255, green: 170 / 255, blue: 120 / 255, opacity: 0.22), location: 0),
                    .init(color: Color(.sRGB, red: 236 / 255, green: 196 / 255, blue: 160 / 255, opacity: 0.08), location: 0.55),
                    .init(color: Color(.sRGB, red: 1, green: 1, blue: 1, opacity: 0), location: 1),
                ]),
                center: CGPoint(x: W * 0.92, y: H * 0.08), startRadius: 0, endRadius: W * 0.95))
            ctx.fill(Path(r), with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(.sRGB, red: 130 / 255, green: 160 / 255, blue: 220 / 255, opacity: 0.12), location: 0),
                    .init(color: Color(.sRGB, red: 150 / 255, green: 170 / 255, blue: 210 / 255, opacity: 0.04), location: 0.5),
                    .init(color: Color(.sRGB, red: 1, green: 1, blue: 1, opacity: 0), location: 1),
                ]),
                center: CGPoint(x: W * 0.05, y: H * 0.78), startRadius: 0, endRadius: W * 0.9))
            var dots = Path()
            for i in 0..<2600 {
                let x = abs(fmod(sin(Double(i) * 12.9898) * 43758.5453, 1))
                let y = abs(fmod(sin(Double(i) * 78.233 + 3.1) * 12543.871, 1))
                dots.addEllipse(in: CGRect(x: x * W - 0.55, y: y * H - 0.55, width: 1.1, height: 1.1))
            }
            ctx.fill(dots, with: .color(Color(.sRGB, red: 60 / 255, green: 45 / 255, blue: 30 / 255, opacity: 0.05)))
        }
        .background(RotatingWordHeadlineColor.hex(0xf3eee6))
    }
}

private struct RotatingWordHeadlineLogo: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [
                .init(color: RotatingWordHeadlineColor.hex(0x2c2822), location: 0),
                .init(color: RotatingWordHeadlineColor.hex(0x1a1713), location: 0.6),
                .init(color: RotatingWordHeadlineColor.hex(0x0f0d0b), location: 1),
            ], startPoint: .top, endPoint: .bottom)
            // An open ring (its right quarter missing), turned −30°.
            Circle()
                .inset(by: 1.25)
                .trim(from: 0.125, to: 0.875)
                .stroke(RotatingWordHeadlineColor.hex(0xf3eee6), lineWidth: 2.5)
                .frame(width: 14, height: 14)
                .rotationEffect(.degrees(-30))
                .offset(x: 7, y: 7)
            Circle()
                .fill(RotatingWordHeadline.accents[0])
                .frame(width: 4, height: 4)
                .offset(x: 18, y: 7)
        }
        .frame(width: 28, height: 28)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

// MARK: plan card

private struct RotatingWordHeadlinePlan {
    struct Task { let title: String; let meta: String; let who: String; let done: Bool }
    let title: String
    let sub: String
    let tasks: [Task]

    static let all: [RotatingWordHeadlinePlan] = [
        .init(title: "This week", sub: "Nov 24 – 30 · 9 items", tasks: [
            .init(title: "Groceries and meal prep", meta: "Mon · 6:30 pm", who: "AL", done: true),
            .init(title: "Dentist for Theo", meta: "Wed · 9:15 am", who: "MK", done: false),
            .init(title: "Send the rent transfer", meta: "Fri · due", who: "AL", done: false),
        ]),
        .init(title: "App launch", sub: "Ships Dec 12 · 18 days left", tasks: [
            .init(title: "Freeze the release build", meta: "Dec 3 · Engineering", who: "JR", done: true),
            .init(title: "Store screenshots, 6 sizes", meta: "Dec 5 · Design", who: "SP", done: false),
            .init(title: "Press note to 40 writers", meta: "Dec 10 · Marketing", who: "NO", done: false),
        ]),
        .init(title: "Our wedding", sub: "June 7 · 64 guests", tasks: [
            .init(title: "Book the florist", meta: "Deposit $480", who: "EM", done: true),
            .init(title: "Final menu tasting", meta: "Jan 18 · 2 pm", who: "DR", done: false),
            .init(title: "Seating plan, draft 2", meta: "12 tables", who: "EM", done: false),
        ]),
        .init(title: "Sprint 23", sub: "2 weeks · 31 points", tasks: [
            .init(title: "Offline sync for notes", meta: "8 pts · In review", who: "KT", done: true),
            .init(title: "Search across calendars", meta: "5 pts · In progress", who: "BW", done: false),
            .init(title: "Fix the reminder drift", meta: "3 pts · To do", who: "KT", done: false),
        ]),
    ]
}

/// The preview card under the hero follows the word: one small plan per word, cross-fading with the position.
private struct RotatingWordHeadlinePlanCard: View {
    let driver: RotatingWordHeadlineDriver
    private typealias M = RotatingWordHeadlineMotion
    private static let border = Color(.sRGB, red: 60 / 255, green: 45 / 255, blue: 30 / 255, opacity: 0.14)

    var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(RotatingWordHeadlineColor.hex(0xfffdf9))
                .shadow(color: RotatingWordHeadlineColor.hex(0x3a2a1a, 0.12), radius: 28, y: 14)
            TimelineView(.animation(paused: !driver.isMoving(at: .now))) { tl in
                let pos = driver.frame(at: tl.date).pos
                // The plan VoiceOver reads: the nearest word's, a discrete index (never a per-frame opacity).
                let current = M.wrapIndex(Int(pos.rounded()), M.words)
                ZStack(alignment: .top) {
                    ForEach(0..<4, id: \.self) { k in
                        let d = M.wordDistance(pos, k)
                        let o = M.clamp01(1 - abs(d) * 1.6)
                        if o > 0 {
                            RotatingWordHeadlinePlanView(plan: RotatingWordHeadlinePlan.all[k], accent: RotatingWordHeadline.accents[k])
                                .accessibilityElement(children: .combine)
                                .accessibilityHidden(k != current)
                                .opacity(o)
                                .offset(y: d * -10)
                        }
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Self.border, lineWidth: 1 / 3)
            Rectangle()
                .fill(Color.white.opacity(0.9))
                .frame(height: 1)
                .padding(.horizontal, 14)
        }
        .allowsHitTesting(false)
    }
}

private struct RotatingWordHeadlinePlanView: View {
    let plan: RotatingWordHeadlinePlan
    let accent: Color
    private var ink: Color { RotatingWordHeadline.ink }
    private var mute: Color { RotatingWordHeadline.mute }
    private static let hair = Color(.sRGB, red: 60 / 255, green: 45 / 255, blue: 30 / 255, opacity: 0.12)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 5).fill(accent).frame(width: 10, height: 30).padding(.trailing, 12)
                VStack(alignment: .leading, spacing: 1) {
                    Text(plan.title).font(.system(size: 17, weight: .bold)).tracking(-0.2).foregroundStyle(ink)
                    Text(plan.sub).font(.system(size: 13)).monospacedDigit().foregroundStyle(mute)
                }
                Spacer(minLength: 0)
                Text("Shared").font(.system(size: 13, weight: .semibold)).foregroundStyle(mute)
            }
            .padding(.bottom, 8)
            ForEach(plan.tasks, id: \.title) { task in
                HStack(spacing: 0) {
                    ZStack {
                        if task.done {
                            Circle().fill(accent)
                            RotatingWordHeadlineCheck().stroke(Color.white, style: StrokeStyle(lineWidth: 2, lineCap: .butt, lineJoin: .miter))
                                .frame(width: 9, height: 5)
                                .rotationEffect(.degrees(-45))
                                .offset(y: -1)
                        } else {
                            Circle().strokeBorder(Color(.sRGB, red: 60 / 255, green: 45 / 255, blue: 30 / 255, opacity: 0.3), lineWidth: 1.5)
                        }
                    }
                    .frame(width: 22, height: 22)
                    .padding(.trailing, 12)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(task.title)
                            .font(.system(size: 15, weight: .semibold))
                            .strikethrough(task.done, color: mute)
                            .foregroundStyle(task.done ? mute : ink)
                            .lineLimit(1)
                        Text(task.meta).font(.system(size: 12.5)).monospacedDigit().foregroundStyle(mute)
                    }
                    Spacer(minLength: 0)
                    Text(task.who)
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(accent)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(accent.opacity(0x22 / 255)))
                        .padding(.leading, 10)
                }
                .frame(height: 50)
                .overlay(alignment: .top) { Rectangle().fill(Self.hair).frame(height: 1 / 3) }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

/// The tick: the left and bottom edges of a 9 × 5 box (turned −45° by the caller).
private struct RotatingWordHeadlineCheck: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX + 1, y: r.minY))
        p.addLine(to: CGPoint(x: r.minX + 1, y: r.maxY - 1))
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY - 1))
        return p
    }
}
