import SwiftUI
import UIKit

struct SkyScreenTime: View {
    static let loop = 8.0
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var motion = SkyScreenTimeMotion()
    @State private var cadence = SkyScreenTimeMotion.Cadence.stopped
    @State private var revision = 0
    @State private var touchingChart = false
    @State private var expanded = false
    @State private var fewerEffects = false
    @State private var haptic = 0

    private var quiet: Bool { reducedMotion || voiceOver || fewerEffects }
    private var now: Double { Date.timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { proxy in
            let s = proxy.size.width / 402
            TimelineView(.animation(minimumInterval: cadence.interval, paused: cadence == .stopped)) { timeline in
                let _ = revision
                let time = timeline.date.timeIntervalSinceReferenceDate
                let frame = motion.frame(at: time)
                let nextCadence = motion.cadence(at: time)
                ZStack(alignment: .topLeading) {
                    ScrollView(.vertical, showsIndicators: false) {
                        content(frame: frame, scale: s)
                            .frame(width: proxy.size.width,
                                   height: max(proxy.size.height, (812 + (expanded ? 94 : 0)) * s),
                                   alignment: .topLeading)
                    }
                    .scrollClipDisabled()
                    navigation(scale: s)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                        .offset(y: proxy.safeAreaInsets.bottom - 20 * s)
                }
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
                .background(alignment: .topLeading) { sky(frame: frame, proxy: proxy, scale: s) }
                .onChange(of: nextCadence, initial: true) { _, value in cadence = value }
            }
        }
        .background(Color(hex: 0x2E70E9).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            motion.appear(autoPlay: autoPlay, quiet: quiet, at: now)
            motion.setActive(scenePhase == .active, at: now)
            wake()
        }
        .onChange(of: autoPlay) { _, value in motion.setAutoPlay(value, at: now); wake() }
        .onChange(of: quiet) { _, value in motion.setQuiet(value, at: now); wake() }
        .onChange(of: scenePhase) { _, value in
            touchingChart = false
            motion.setActive(value == .active, at: now)
            wake()
        }
        .sensoryFeedback(.selection, trigger: haptic)
    }

    private func sky(frame: SkyScreenTimeFrame, proxy: GeometryProxy, scale: Double) -> some View {
        let height = proxy.size.height + proxy.safeAreaInsets.top + proxy.safeAreaInsets.bottom
        return ZStack(alignment: .topLeading) {
            Image(uiImage: SkyScreenTimeArtwork.sky)
                .resizable().scaledToFill()
                .frame(width: proxy.size.width, height: height)
                .saturation(1 - frame.storm * 0.65)
            RadialGradient(colors: [Color(hex: 0x2E70E9).opacity(0.55), .clear],
                           center: UnitPoint(x: 0.5, y: 0.26), startRadius: 80 * scale, endRadius: 250 * scale)
                .opacity(1 - frame.storm * 0.3)
            Image(uiImage: SkyScreenTimeArtwork.clouds)
                .resizable().scaledToFit()
                .frame(width: 430 * scale, height: 500 * scale)
                .scaleEffect(1 + frame.storm * 0.04)
                .offset(x: (-25 + frame.storm * 30) * scale, y: (30 + frame.storm * 30) * scale)
                .opacity(frame.storm * 0.85)
            Color(hex: 0x1E325B).opacity(frame.storm * 0.30)
        }
        .frame(width: proxy.size.width, height: height, alignment: .topLeading)
        .clipped()
        .offset(y: -proxy.safeAreaInsets.top)
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }

    private func content(frame: SkyScreenTimeFrame, scale s: Double) -> some View {
        ZStack(alignment: .topLeading) {
            header(scale: s)
            Text(frame.selectedIndex.map { SkyScreenTimeData.timeLabel($0) } ?? "Quality")
                .font(.system(size: 18 * s, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
                .position(x: 201 * s, y: (192 - 62) * s)
            ZStack {
                ForEach(Array(["Productive", "Balanced", "Distracted"].enumerated()), id: \.offset) { index, title in
                    Text(title).opacity(frame.titleOpacities[index]).accessibilityHidden(true)
                }
            }
            .font(.system(size: 58 * s, weight: .bold, design: .rounded))
            .tracking(-2 * s)
            .lineLimit(1)
            .minimumScaleFactor(0.72)
            .frame(width: 362 * s, height: 71 * s)
            .foregroundStyle(.white)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(frame.title)
            .position(x: 201 * s, y: (238 - 62) * s)

            qualityFaces(frame: frame, scale: s)
                .position(x: 201 * s, y: (304 - 62) * s)
            HStack(spacing: 5 * s) {
                Circle().fill(.white.opacity(0.25))
                Circle().fill(.white)
            }
            .frame(width: 16 * s, height: 6 * s)
            .position(x: 201 * s, y: (353 - 62) * s)
            .accessibilityHidden(true)

            statistics(frame: frame, scale: s)
                .offset(x: 20 * s, y: (419 - 62) * s)
            focusButton(frame: frame, scale: s)
                .offset(x: 20 * s, y: (708 - 62 + (expanded ? 94 : 0)) * s)
        }
    }

    private func header(scale s: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Image(uiImage: SkyScreenTimeArtwork.wordmark)
                .resizable().scaledToFit()
                .frame(width: 90 * s, height: 35 * s)
                .position(x: 65 * s, y: 32 * s)
                .accessibilityLabel("Moru")
            Menu {
                Button("Today") { motion.chooseDay(0, at: now); wake(); haptic += 1 }
                Button("Yesterday") { motion.chooseDay(1, at: now); wake(); haptic += 1 }
            } label: {
                HStack(spacing: 7 * s) {
                    Text(motion.day == 0 ? "Today" : "Yesterday")
                    Image(systemName: "chevron.down").font(.system(size: 11 * s, weight: .bold))
                }
                .font(.system(size: 15 * s, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .frame(width: 106 * s, height: 44)
            }
            .position(x: 201 * s, y: 32 * s)
            Button { motion.reset(at: now); wake(); haptic += 1 } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 22 * s, weight: .semibold))
                    .frame(width: 44 * s, height: 44 * s)
                    .background(.blue.opacity(0.10), in: Circle())
                    .overlay(Circle().strokeBorder(.white.opacity(0.35), lineWidth: 1 * s))
            }
            .foregroundStyle(.white)
            .position(x: 306 * s, y: 32 * s)
            .accessibilityLabel("Reset daily overview")
            Menu {
                Toggle("Reduce effects", isOn: $fewerEffects)
                Button("Clear selected hour") { motion.reset(at: now); wake() }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 20 * s, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 44 * s, height: 44 * s)
                    .background(.blue.opacity(0.10), in: Circle())
                    .overlay(Circle().strokeBorder(.white.opacity(0.35), lineWidth: 1 * s))
            }
            .position(x: 359 * s, y: 32 * s)
            .accessibilityLabel("Screen time options")
        }
        .buttonStyle(.plain)
    }

    private func qualityFaces(frame: SkyScreenTimeFrame, scale s: Double) -> some View {
        let data = frame.selectedIndex.map { SkyScreenTimeData.hours(day: motion.day)[$0] } ?? SkyScreenTimeData.total(day: motion.day)
        let divisor = max(0.01, data.minutes)
        let values = [data.productive, data.neutral, data.distracted]
        let labels = ["Productive", "Neutral", "Distracted"]
        return HStack(spacing: 11 * s) {
            ForEach(0..<3, id: \.self) { index in
                VStack(spacing: 7 * s) {
                    SkyScreenTimeFace(kind: index).frame(width: 28 * s, height: 28 * s)
                    Text("\(Int((values[index] / divisor * 100).rounded()))%")
                        .font(.system(size: 16 * s, weight: .semibold, design: .rounded))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .frame(width: 44 * s)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(labels[index]), \(Int((values[index] / divisor * 100).rounded())) percent")
            }
        }
    }

    private func statistics(frame: SkyScreenTimeFrame, scale s: Double) -> some View {
        let total = SkyScreenTimeData.total(day: motion.day)
        let labels = ["Screen time", "Pickups", "Notifications"]
        let values = [SkyScreenTimeData.duration(total.minutes), String(total.pickups), String(total.notifications)]
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 32 * s, style: .continuous).fill(.white)
            HStack(spacing: 0) {
                ForEach(0..<3, id: \.self) { index in
                    VStack(spacing: 3 * s) {
                        Text(labels[index]).font(.system(size: 12 * s, weight: .bold, design: .rounded)).foregroundStyle(Color(hex: 0x888995))
                        Text(values[index]).font(.system(size: 20 * s, weight: .bold, design: .rounded)).foregroundStyle(.black)
                    }
                    .frame(width: 111 * s)
                    .accessibilityElement(children: .combine)
                    if index != 2 { Rectangle().fill(Color(hex: 0xE4E4E8)).frame(width: 2 * s, height: 16 * s) }
                }
            }
            .offset(x: 13 * s, y: 27 * s)
            SkyScreenTimeChart(hours: SkyScreenTimeData.hours(day: motion.day), frame: frame, scale: s)
                .frame(width: 330 * s, height: 126 * s)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !touchingChart { motion.beginScrub(at: now); touchingChart = true }
                        motion.scrub(hour: (value.location.x / s - 6) / (292 / 24))
                        wake()
                    }
                    .onEnded { _ in touchingChart = false; motion.endScrub(at: now); wake(); haptic += 1 })
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Hourly screen time")
                .accessibilityValue(frame.selectedIndex.map { "\(SkyScreenTimeData.timeLabel($0)), \(SkyScreenTimeData.duration(SkyScreenTimeData.hours(day: motion.day)[$0].minutes))" } ?? "Daily overview")
                .accessibilityAdjustableAction { direction in
                    let selected = frame.selectedIndex ?? 0
                    motion.beginScrub(at: now)
                    motion.scrub(hour: Double(selected + (direction == .increment ? 1 : -1)))
                    motion.endScrub(at: now)
                    wake()
                }
                .offset(x: 17 * s, y: 89 * s)
            Button {
                withAnimation(quiet ? nil : .spring(response: 0.35, dampingFraction: 0.82)) { expanded.toggle() }
            } label: {
                Image(systemName: expanded ? "chevron.up" : "chevron.down")
                    .font(.system(size: 17 * s, weight: .bold))
                    .foregroundStyle(Color(hex: 0xC9CBD0))
                    .frame(width: 80 * s, height: 44)
            }
            .buttonStyle(.plain)
            .position(x: 181 * s, y: 245 * s)
            .accessibilityLabel(expanded ? "Hide hourly details" : "Show hourly details")
            if expanded {
                VStack(alignment: .leading, spacing: 7 * s) {
                    Text(frame.selectedIndex.map { "\(SkyScreenTimeData.timeLabel($0)) activity" } ?? "Your day at a glance")
                        .font(.system(size: 15 * s, weight: .semibold))
                    Text(frame.selectedIndex.map { index in
                        let hour = SkyScreenTimeData.hours(day: motion.day)[index]
                        return "\(SkyScreenTimeData.duration(hour.minutes)) on screen · \(hour.pickups) pickups · \(hour.notifications) notifications"
                    } ?? "Drag across the chart to see how each hour felt.")
                    .font(.system(size: 12 * s))
                    .foregroundStyle(Color(hex: 0x757682))
                }
                .foregroundStyle(.black)
                .frame(width: 318 * s, alignment: .leading)
                .offset(x: 22 * s, y: 280 * s)
            }
        }
        .frame(width: 362 * s, height: (268 + (expanded ? 94 : 0)) * s, alignment: .topLeading)
    }

    private func focusButton(frame: SkyScreenTimeFrame, scale s: Double) -> some View {
        Button { motion.toggleFocus(at: now); wake(); haptic += 1 } label: {
            HStack(spacing: 8 * s) {
                Image(systemName: frame.focusSeconds > 0 ? "stop.fill" : "plus")
                    .font(.system(size: 21 * s, weight: .bold))
                Text(frame.focusSeconds > 0
                     ? "End focus · \(frame.focusSeconds / 60):\(String(format: "%02d", frame.focusSeconds % 60))"
                     : "Start focusing")
                    .font(.system(size: 18 * s, weight: .bold, design: .rounded))
            }
            .foregroundStyle(.white)
            .frame(width: 362 * s, height: 61 * s)
            .background(LinearGradient(stops: [
                .init(color: Color(hex: 0x4388D0), location: 0),
                .init(color: Color(hex: 0x508FD9), location: 0.8),
                .init(color: Color(hex: 0xC7E4FA), location: 1),
            ], startPoint: .leading, endPoint: .trailing), in: Capsule())
            .overlay(Capsule().strokeBorder(.white.opacity(0.45), lineWidth: 1 * s))
        }
        .buttonStyle(.plain)
        .accessibilityHint(frame.focusSeconds > 0 ? "Ends the current focus session" : "Starts a twenty-five minute focus timer")
    }

    private func navigation(scale s: Double) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(zip(["Home", "Rules", "Squads", "Profile"], ["house.fill", "lock", "hands.clap", "person"])), id: \.0) { label, icon in
                VStack(spacing: 4 * s) {
                    Image(systemName: icon).font(.system(size: 23 * s, weight: .medium))
                    Text(label).font(.system(size: 11 * s, weight: .semibold))
                }
                .foregroundStyle(.black)
                .frame(width: 88.5 * s, height: 56 * s)
                .background(label == "Home" ? Color.black.opacity(0.09) : .clear, in: Capsule())
                .accessibilityElement(children: .combine)
            }
        }
        .padding(4 * s)
        .background(.ultraThinMaterial, in: Capsule())
        .environment(\.colorScheme, .light)
        .overlay(Capsule().strokeBorder(.white.opacity(0.9), lineWidth: 1.2 * s))
        .shadow(color: .black.opacity(0.12), radius: 15 * s, y: 10 * s)
        .frame(width: 362 * s, height: 64 * s)
    }

    private func wake() { cadence = motion.cadence(at: now); revision += 1 }
}

private enum SkyScreenTimeArtwork {
    static let sky = load("sky", extension: "jpg")
    static let clouds = load("clouds-near", extension: "png")
    static let wordmark: UIImage = {
        let descriptor = UIFont.systemFont(ofSize: 31, weight: .black).fontDescriptor.withDesign(.rounded)!
        let font = UIFont(descriptor: descriptor, size: 31)
        return UIGraphicsImageRenderer(size: CGSize(width: 110, height: 42)).image { _ in
            ("MORU" as NSString).draw(at: CGPoint(x: 3, y: 1), withAttributes: [
                .font: font, .strokeColor: UIColor.white, .strokeWidth: 6,
            ])
        }
    }()
    private static func load(_ name: String, extension ext: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: "SkyScreenTime-\(name)", ofType: ext),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
}
