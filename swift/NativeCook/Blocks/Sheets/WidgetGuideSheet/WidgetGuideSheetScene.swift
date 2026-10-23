import SwiftUI

struct WidgetGuideSheetLabels {
    var search = "Search"
    var countdown = "Daybreak"
    var until = "days until"
    var event = "Mia's birthday"
    var trip = "Road trip"
    var tomorrow = "tomorrow"
    var firstName = "Graduation day"
    var firstDate = "in 7 days"
    var secondName = "Lena's birthday"
    var secondDate = "in 9 days"
    var done = "Done"
    var edit = "Edit Widget"
}

enum WidgetGuideSheetSceneMetrics {
    static let phoneHeight = 520.0
}

private enum S {
    static let phoneColors = [Color(hex: 0x3446b4), Color(hex: 0x4a5cc4), Color(hex: 0x6a7fdc)]
    static let notch = CGSize(width: 96, height: 24)
    static let icon = 58.0
    static let iconLeft = 22.0
    static let iconTop = 54.0
    static let rowPitch = 92.0
    static let slotTop = 246.0
    static let slotHeight = 138.0
    static let slotInset = 14.0
    static let dockHeight = 72.0
    static let dockIcon = 50.0
    static let phoneHeight = WidgetGuideSheetSceneMetrics.phoneHeight

    static let icons: [(color: Color, glyph: String, label: String)] = [
        (Color(hex: 0xe84c6a), "heart.fill", "Health"),
        (Color(hex: 0xf2634f), "cloud.fill", "Weather"),
        (Color(hex: 0xf4b23e), "leaf.fill", "Garden"),
        (Color(hex: 0x4bd964), "flag.fill", "Goals"),
        (Color(hex: 0x3ccfa8), "triangle.fill", "Trails"),
        (Color(hex: 0x3aa9e0), "star.fill", "Stars"),
        (Color(hex: 0x5d6cc8), "sun.max.fill", "Sun"),
        (Color(hex: 0x7a55d9), "moon.fill", "Sleep"),
    ]
    static let dock: [(color: Color, glyph: String)] = [
        (Color(hex: 0x34c759), "phone.fill"),
        (Color(hex: 0x0a84ff), "safari.fill"),
        (Color(hex: 0x30d158), "message.fill"),
        (Color(hex: 0xff9f0a), "music.note"),
    ]
    /// The widget covers the two icons at the left of each row.
    static let covered = [0, 1, 4, 5]

    static let tiles = [Color(hex: 0x3fd6a8), Color(hex: 0xf0476b), Color(hex: 0xf4b23e), Color(hex: 0x3cc4e0)]
    static let tileDays = ["21", "7", "1", "9"]
    static let green = Color(hex: 0x2fcf8a)
    static let grey = Color(hex: 0xe9e9ec)
    static let ink = Color(hex: 0x14141f)
    static let mute = Color(hex: 0x8a8a96)
    /// React Native's hairline width on a 3x screen.
    static let hairline = 1.0 / 3
    static let pink = Color(hex: 0xf0476b)
    static let amber = Color(hex: 0xf4a000)
    static let cyan = Color(hex: 0x1fb3d6)
    /// Points the dashes round the empty slot march in a second.
    static let dashPeriod = 8.0

    static func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }

    /// Smooth 0 → 1 between `from` and `to`.
    static func ramp(_ value: Double, _ from: Double, _ to: Double) -> Double {
        let t = min(1, max(0, (value - from) / (to - from)))
        return t * t * (3 - 2 * t)
    }

    /// 0 → 1 with a small overshoot, for a pop.
    static func pop(_ t: Double) -> Double {
        let c = 1.9
        return 1 + (c + 1) * pow(t - 1, 3) + c * pow(t - 1, 2)
    }
}

/// The miniature home screen inside the sheet. Everything here is a function of `step`, so the
/// slides cross-fade into each other, run backwards, and stop wherever a swipe leaves them.
struct WidgetGuideSheetScene: View {
    let width: Double
    /// Which slide is on show, as a number that moves between slides: 0 to 4.
    let step: Double
    /// When the dashes round the empty slot started to march; nil while they stand still.
    let dashStart: Date?
    /// 0 → 1: a light that crosses the picker's tiles once.
    let shimmer: Double
    let labels: WidgetGuideSheetLabels

    var body: some View {
        let slotWidth = width - S.slotInset * 2
        let pitch = (width - S.iconLeft * 2 - S.icon) / 3
        let widgetWidth = pitch + S.icon
        let widgetHeight = S.rowPitch + S.icon
        let pickerTop = 70.0
        let tileWidth = (width - 12 - 28 - 12) / 2
        let s = step

        ZStack(alignment: .topLeading) {
            // Under the blur: the home screen and its icons, the empty slot and the list widget.
            ZStack(alignment: .topLeading) {
                wallpaper(width: width)

                ForEach(Array(S.icons.enumerated()), id: \.offset) { index, icon in
                    let dim = S.lerp(1, 0.75, S.ramp(s, 0.4, 1))
                    VStack(spacing: 5) {
                        appIcon(icon.glyph, color: icon.color, size: S.icon, radius: 15, glyph: 23, light: 0.3, lightTo: 0.6)
                        Text(icon.label)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .frame(height: 13)
                    }
                    .frame(width: S.icon + 12)
                    .opacity(S.covered.contains(index) ? dim * (1 - S.ramp(s, 2.55, 2.95)) : dim)
                    .offset(x: S.iconLeft - 6 + Double(index % 4) * pitch, y: S.iconTop + Double(index / 4) * S.rowPitch)
                }

                TimelineView(.animation(paused: dashStart == nil)) { timeline in
                    let phase = dashStart.map { timeline.date.timeIntervalSince($0).truncatingRemainder(dividingBy: 1) * S.dashPeriod } ?? 0
                    RoundedRectangle(cornerRadius: 18)
                        .inset(by: 1)
                        .stroke(.white.opacity(0.85), style: StrokeStyle(lineWidth: 1.5, dash: [6, 4], dashPhase: -phase))
                        .frame(width: slotWidth, height: S.slotHeight)
                }
                .opacity(1 - S.ramp(s, 2.3, 2.7))
                .offset(x: S.slotInset, y: S.slotTop)

                list(width: slotWidth, step: s)
                    .offset(x: S.slotInset, y: S.slotTop)
            }
            .frame(width: width, height: S.phoneHeight, alignment: .topLeading)
            .blur(radius: 6 * S.ramp(s, 3.1, 3.9))

            let greenT = S.ramp(s, 2.55, 2.95)
            VStack(alignment: .leading, spacing: 0) {
                Text("21").font(.system(size: 40, weight: .heavy)).tracking(-1).frame(height: 44, alignment: .leading)
                Text(labels.until).font(.system(size: 12, weight: .semibold)).foregroundStyle(.white.opacity(0.82)).frame(height: 15)
                Text(labels.event).font(.system(size: 14, weight: .heavy)).lineLimit(1).frame(height: 17).padding(.top, 2)
                Spacer(minLength: 0)
            }
            .foregroundStyle(.white)
            .padding(14)
            .frame(width: widgetWidth, height: widgetHeight, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 26)
                    .fill(S.green)
                    .shadow(color: Color(hex: 0x0b3b26).opacity(0.35), radius: 14, y: 8)
            )
            .overlay(alignment: .topTrailing) {
                Image(systemName: "gift.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(S.green)
                    .frame(width: 26, height: 26)
                    .background(.white, in: RoundedRectangle(cornerRadius: 8))
                    .padding(14)
            }
            .scaleEffect(S.lerp(1.15, 1, greenT))
            .opacity(greenT)
            .offset(x: S.iconLeft, y: S.iconTop)

            let menuT = S.ramp(s, 3.4, 3.95)
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(labels.edit).font(.system(size: 14, weight: .heavy)).foregroundStyle(S.ink)
                    Spacer(minLength: 0)
                    Image(systemName: "info.circle").font(.system(size: 14)).foregroundStyle(S.mute)
                }
                .frame(height: 18)
                Rectangle().fill(Color(hex: 0xe2e2e8)).frame(height: S.hairline).padding(.horizontal, -12)
                HStack {
                    Text("Event").font(.system(size: 13, weight: .medium)).foregroundStyle(S.mute)
                    Spacer(minLength: 0)
                    Text(labels.event).font(.system(size: 13, weight: .bold)).foregroundStyle(S.pink)
                }
                HStack {
                    Text("Show days").font(.system(size: 13, weight: .medium)).foregroundStyle(S.mute)
                    Spacer(minLength: 0)
                    Circle().fill(.white).frame(width: 18, height: 18)
                        .padding(2)
                        .frame(width: 36, height: 22, alignment: .trailing)
                        .background(S.pink, in: Capsule())
                }
            }
            .padding(12)
            .frame(width: 196)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(.white)
                    .shadow(color: .black.opacity(0.25), radius: 18, y: 8)
            )
            .scaleEffect(S.pop(menuT), anchor: .topLeading)
            .opacity(min(1, menuT * 3))
            .offset(x: S.iconLeft, y: S.iconTop + widgetHeight + 8)

            let plusFade = 1 - S.ramp(s, 2.6, 2.9)
            let press = 1 - 0.1 * max(0, 1 - abs(s - 1.45) * 6)
            Image(systemName: "plus")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(S.ink)
                .frame(width: 42, height: 26)
                .background(Capsule().fill(.white).shadow(color: .black.opacity(0.15), radius: 4, y: 2))
                .scaleEffect(max(0, S.pop(S.ramp(s, 0.35, 1)) * plusFade * press))
                .opacity(S.ramp(s, 0.35, 0.6) * plusFade)
                .offset(x: 14, y: 10)

            Text(labels.done)
                .font(.system(size: 13, weight: .heavy))
                .foregroundStyle(S.ink)
                .padding(.horizontal, 12)
                .frame(height: 26)
                .background(Capsule().fill(.white).shadow(color: .black.opacity(0.15), radius: 4, y: 2))
                .opacity(S.ramp(s, 2.7, 3) * (1 - S.ramp(s, 3.2, 3.5)))
                .frame(width: width - 14, alignment: .trailing)
                .offset(y: 10)

            let up = 1 - pow(1 - S.ramp(s, 1.45, 2.05), 3)
            let down = S.ramp(s, 2.55, 3)
            picker(width: width, tileWidth: tileWidth)
                .offset(x: 6, y: pickerTop + S.lerp(S.phoneHeight - pickerTop, 0, up) + down * (S.phoneHeight - pickerTop))

            UnevenRoundedRectangle(bottomLeadingRadius: S.notch.height / 2, bottomTrailingRadius: S.notch.height / 2)
                .fill(.white)
                .frame(width: S.notch.width, height: S.notch.height)
                .offset(x: (width - S.notch.width) / 2)
        }
        .frame(width: width, height: S.phoneHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 30))
    }

    /// The home screen's wallpaper, with soft light and shade so it reads as a picture and not a
    /// fill, and its search pill and dock.
    private func wallpaper(width: Double) -> some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: S.phoneColors, startPoint: .topLeading, endPoint: .bottomTrailing)
            Circle().fill(Color(hex: 0x8fa2ff).opacity(0.28)).frame(width: 260, height: 260)
                .offset(x: -80, y: -60)
            Circle().fill(Color(hex: 0x25319a).opacity(0.35)).frame(width: 280, height: 280)
                .offset(x: width + 90 - 280, y: 150)
            Circle().fill(Color(hex: 0xa7b4ff).opacity(0.2)).frame(width: 240, height: 240)
                .offset(x: 60, y: S.phoneHeight + 120 - 240)

            HStack(spacing: 5) {
                Image(systemName: "magnifyingglass").font(.system(size: 10, weight: .semibold))
                Text(labels.search).font(.system(size: 12, weight: .semibold))
            }
            .foregroundStyle(.white)
            .frame(width: 92, height: 28)
            .background(.white.opacity(0.22), in: Capsule())
            .offset(x: (width - 92) / 2, y: S.phoneHeight - 12 - S.dockHeight - 12 - 28)

            HStack(spacing: 0) {
                ForEach(Array(S.dock.enumerated()), id: \.offset) { _, item in
                    appIcon(item.glyph, color: item.color, size: S.dockIcon, radius: 13, glyph: 21, light: 0.28, lightTo: 1)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 10)
            .frame(width: width - S.slotInset * 2, height: S.dockHeight)
            .background(.white.opacity(0.22), in: RoundedRectangle(cornerRadius: 26))
            .offset(x: S.slotInset, y: S.phoneHeight - 12 - S.dockHeight)
        }
        .frame(width: width, height: S.phoneHeight, alignment: .topLeading)
    }

    /// A rounded square app icon with a white glyph and a light across its top.
    private func appIcon(_ symbol: String, color: Color, size: Double, radius: Double, glyph: Double, light: Double, lightTo: Double) -> some View {
        Image(systemName: symbol)
            .font(.system(size: glyph))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background {
                LinearGradient(
                    stops: [.init(color: .white.opacity(light), location: 0), .init(color: .white.opacity(0), location: lightTo)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .background(color)
            }
            .clipShape(RoundedRectangle(cornerRadius: radius))
    }

    private func list(width: Double, step s: Double) -> some View {
        let t = S.ramp(s, 2.65, 3)
        return VStack(spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(labels.trip).font(.system(size: 16, weight: .heavy)).foregroundStyle(S.amber).frame(height: 19)
                    Text(labels.tomorrow).font(.system(size: 13, weight: .bold)).foregroundStyle(S.ink).frame(height: 16)
                }
                Spacer(minLength: 0)
                Image(systemName: "star.fill")
                    .font(.system(size: 11))
                    .foregroundStyle(S.amber)
                    .frame(width: 28, height: 28)
                    .background(Color(hex: 0xfff2d6), in: RoundedRectangle(cornerRadius: 9))
            }
            Spacer(minLength: 0)
            listRow(labels.firstName, labels.firstDate, S.pink)
            Spacer(minLength: 0)
            listRow(labels.secondName, labels.secondDate, S.cyan)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(width: width, height: S.slotHeight)
        .background(RoundedRectangle(cornerRadius: 24).fill(.white).shadow(color: .black.opacity(0.18), radius: 12, y: 6))
        .opacity(t)
        .offset(y: S.lerp(24, 0, t))
    }

    private func listRow(_ name: String, _ date: String, _ color: Color) -> some View {
        HStack {
            Text(name).font(.system(size: 13, weight: .heavy)).foregroundStyle(color).lineLimit(1)
            Spacer(minLength: 0)
            Text(date).font(.system(size: 12, weight: .bold)).foregroundStyle(S.ink)
        }
    }

    private func picker(width: Double, tileWidth: Double) -> some View {
        VStack(spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass").font(.system(size: 13, weight: .medium)).foregroundStyle(S.mute)
                Text(labels.countdown).font(.system(size: 15)).foregroundStyle(S.mute)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .frame(height: 38)
            .background(S.grey, in: RoundedRectangle(cornerRadius: 12))

            LazyVGrid(columns: [GridItem(.fixed(tileWidth), spacing: 12), GridItem(.fixed(tileWidth), spacing: 12)], spacing: 12) {
                ForEach(S.tiles.indices, id: \.self) { index in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(S.tileDays[index])
                            .font(.system(size: 30, weight: .heavy))
                            .foregroundStyle(.white)
                            .frame(height: 34)
                            .padding(.bottom, 4)
                        ForEach([0.7, 0.5], id: \.self) { fraction in
                            RoundedRectangle(cornerRadius: 3.5).fill(.white.opacity(0.6))
                                .frame(width: (tileWidth - 24) * fraction, height: 7)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(12)
                    .frame(width: tileWidth, height: 132, alignment: .topLeading)
                    .background(S.tiles[index], in: RoundedRectangle(cornerRadius: 20))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(width: width - 12, height: S.phoneHeight, alignment: .top)
        .background(Color(hex: 0xf6f6f8), in: RoundedRectangle(cornerRadius: 24))
        .overlay(alignment: .leading) {
            LinearGradient(colors: [.white.opacity(0), .white.opacity(0.55), .white.opacity(0)], startPoint: .leading, endPoint: .trailing)
                .frame(width: 140)
                .offset(x: S.lerp(-width, width, shimmer))
                .allowsHitTesting(false)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }
}
