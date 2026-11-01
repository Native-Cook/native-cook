import SwiftUI

struct SproutStats: View {
    static let loop = SproutStatsMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var driver = SproutStatsDriver()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "sproutStatsTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
            GeometryReader { full in
                let size = full.size, art = SproutStatsArt(scale: displayScale)
                TimelineView(.animation(paused: pinned != nil || phase != .active || !driver.ticking)) { timeline in
                    let frame = driver.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
                    let day = frame.scrub >= 0 ? Int(floor(frame.scrub+0.5)) : -1
                    Canvas { context, _ in
                        context.withCGContext { c in
                            if let image = art.r.chrome(size:size, top:top, bottom:bottom, scale:displayScale) {
                                c.saveGState()
                                c.translateBy(x:0,y:size.height); c.scaleBy(x:1,y:-1)
                                c.draw(image,in:CGRect(origin:.zero,size:size)); c.restoreGState()
                            }
                            art.header(c, size:size, top:top, bottom:bottom, frame:frame)
                            art.garden(c, size:size, frame:frame, reduced:reduced)
                        }
                    }
                    .accessibilityChildren {
                        Text("THIS WEEK")
                        Text("Mindful minutes")
                        Text(day >= 0 ? "\(SproutStatsMotion.days[day]) · \(SproutStatsMotion.minutes[day]) min" : "128 min · 18% more than last week")
                        Text("5 day streak")
                        Text("30 best day · Sat")
                        Text("18 daily average")
                    }
                    .onChange(of: day) { _, value in
                        if UIAccessibility.isVoiceOverRunning {
                            let summary = value >= 0 ? "\(SproutStatsMotion.days[value]) · \(SproutStatsMotion.minutes[value]) min" : "128 min · 18% more than last week"
                            UIAccessibility.post(notification: .announcement, argument: summary)
                        }
                    }
                    .overlay(alignment: .topLeading) {
                        if pinned == nil {
                            HStack(spacing: 0) {
                                modeButton("Garden", 0, frame.m <= 0.5)
                                modeButton("Chart", 1, frame.m > 0.5)
                            }
                            .frame(width:204,height:32).offset(x:28,y:top+art.segmentY+4)
                            SproutStatsTouchSurface { state, translation, point, velocity in
                                driver.pan(state, translation:translation, point:point, velocity:velocity, step:(size.width-92)/6)
                            }
                            .frame(width:size.width,height:(size.height*0.34).rounded()+100)
                            .offset(y:(size.height*0.7).rounded()-(size.height*0.34).rounded()-60)
                            .accessibilityElement(children:.ignore)
                            .accessibilityLabel("Mindful minutes this week: Monday 12, Tuesday 20, Wednesday 8, Thursday 25, Friday 18, Saturday 30, Sunday 15")
                            .accessibilityHint("Drag up for the chart, sideways to read a day")
                        }
                    }
                    .accessibilityElement(children:.contain)
                }
                .frame(width:size.width,height:size.height,alignment:.topLeading)
            }.ignoresSafeArea()
        }
        .background(Color(red:252/255,green:248/255,blue:241/255).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onChange(of:phase) { _, _ in driver.resume() }
        .onAppear { driver.resume() }
        .onChange(of:reduced) { _, _ in driver.resume(); driver.wakeUp() }
    }
    private func modeButton(_ label: String, _ mode: Double, _ selected: Bool) -> some View {
        Button { driver.mode(mode) } label: { Color.clear.contentShape(Rectangle()) }
            .buttonStyle(.plain).accessibilityLabel(label)
            .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
