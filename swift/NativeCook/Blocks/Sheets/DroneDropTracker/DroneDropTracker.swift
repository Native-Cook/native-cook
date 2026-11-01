import SwiftUI

struct DroneDropTracker: View {
    static let loop = DroneDropTrackerMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var clock = DroneDropTrackerClock()
    @State private var sheetDragging = false
    @State private var mapDragging = false
    @GestureState private var sheetGestureActive = false
    @GestureState private var mapGestureActive = false
    private let resources = DroneDropTrackerResources.shared
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "droneDropTrackerTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let g = DroneDropTrackerGeometry(width: full.size.width, height: full.size.height, safeTop: safe.safeAreaInsets.top, safeBottom: safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
                    let _ = clock.revision
                    let o = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
                    ZStack(alignment: .topLeading) {
                        DroneDropTrackerArt.color(0xBFE6C9)
                        Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                            DroneDropTrackerArt.draw(c, g, o, reduced)
                        }.allowsHitTesting(false)
                        Color.clear.contentShape(Rectangle())
                            .frame(width: g.width, height: g.mapH - 40).offset(y: g.top)
                            .gesture(mapGesture(g))
                            .onChange(of: mapGestureActive) { _, active in
                                if !active && mapDragging { mapDragging = false; clock.mapEnd() }
                            }
                            .accessibilityElement().accessibilityLabel("Delivery map. Drag sideways to replay the flight")
                            .accessibilityAdjustableAction { _ in
                                // RN declares the adjustable role without an accessibility action.
                            }
                        header(g)
                        sheet(g, o)
                            .offset(y: g.sheetY(o))
                    }
                    .frame(width: g.width, height: g.height, alignment: .topLeading)
                    .clipped()
                    .allowsHitTesting(pinned == nil)
                }
            }.ignoresSafeArea()
        }
        .background(DroneDropTrackerArt.color(0xBFE6C9).ignoresSafeArea())
        .preferredColorScheme(.light)
        .persistentSystemOverlays(.hidden)
        .onChange(of: scenePhase) { _, _ in clock.resume() }
    }
    private func text(_ copy: String, _ weight: String, _ size: Double, _ color: Int) -> some View {
        Text(copy).font(resources.font(weight, size)).foregroundStyle(DroneDropTrackerArt.color(color))
    }
    private func icon(_ scalar: UInt32, _ size: Double, _ color: Int) -> some View {
        Text(String(UnicodeScalar(scalar)!)).font(.custom("Ionicons", fixedSize: size)).foregroundStyle(DroneDropTrackerArt.color(color)).offset(y: size == 20 ? 1 / displayScale : 0)
    }
    private func header(_ g: DroneDropTrackerGeometry) -> some View {
        HStack {
            Button {} label: { icon(0xf229, 22, 0x1F2A37).frame(width: 44, height: 44).background(.white, in: Circle()) }.accessibilityLabel("Back")
            Spacer(minLength: 0)
            text("Order #4821", "SemiBold", 16, 0x1F2A37).padding(.horizontal, 14).frame(height: 36).background(.white, in: Capsule(style: .circular)).offset(x: -1 / displayScale)
            Spacer(minLength: 0)
            Button {} label: { icon(0xf379, 20, 0x1F2A37).frame(width: 44, height: 44).background(.white, in: Circle()) }.accessibilityLabel("Help")
        }
        .buttonStyle(.plain).padding(.horizontal, 16).padding(.top, g.safeTop + 4)
    }
    private func sheet(_ g: DroneDropTrackerGeometry, _ o: DroneDropTrackerFrame) -> some View {
        let details = DroneDropTrackerMotion.clamp((o.sheet - 0.35) / 0.5)
        return VStack(spacing: 0) {
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 3, style: .circular).fill(DroneDropTrackerArt.color(0xE1E6EE)).frame(width: 40, height: 5).padding(.top, 10).padding(.bottom, 14)
                HStack(spacing: 12) {
                    icon(o.delivered ? 0xf21d : 0xf48d, 20, 0xFFFFFF)
                        .frame(width: 44, height: 44).background(DroneDropTrackerArt.color(0x2F9E62), in: RoundedRectangle(cornerRadius: 14, style: .circular))
                    VStack(alignment: .leading, spacing: 2) {
                        text(o.eta, "Bold", 20, 0x1F2A37).frame(maxWidth: .infinity, alignment: .leading)
                        text("Drone K-7 · 2 bags from Greenleaf Market", "Regular", 13, 0x6B7686)
                            .fixedSize(horizontal: false, vertical: true)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
                ZStack(alignment: .leading) {
                    Capsule(style: .circular).fill(DroneDropTrackerArt.color(0xEDF1F6))
                    Capsule(style: .circular).fill(DroneDropTrackerArt.color(0x2F9E62))
                        .scaleEffect(x: o.back > 0 ? 1 - 0.85 * { let k = min(1, max(0, (o.back - 0.55) / 0.45)); return k * k * (3 - 2 * k) }() : 0.15 + 0.75 * o.f + 0.1 * o.drop, y: 1, anchor: .leading)
                }.frame(height: 6).clipShape(Capsule(style: .circular)).padding(.top, 14)
            }
            .padding(.horizontal, 20).padding(.bottom, 12)
            .contentShape(Rectangle()).gesture(sheetGesture(g))
            .onChange(of: sheetGestureActive) { _, active in
                if !active && sheetDragging { sheetDragging = false; clock.sheetEnd(clock.lastSheetVelocity, g.travel) }
            }
            .accessibilityElement(children: .combine).accessibilityAddTraits(.isButton)
            .accessibilityLabel("Order details. Drag up to open")
            VStack(spacing: 0) {
                step(0xf15c, "Order packed", "6:42 PM", true)
                step(0xf48e, "In the air", "6:44 PM", true)
                step(0xf2ba, "Lowered to your doorstep", o.delivered ? "6:51 PM" : "about 6:51 PM", o.delivered)
                HStack(spacing: 12) {
                    action(0xf215, "Message", "Message the hub", false)
                    action(0xf3c5, "Drop spot", "Drop instructions", true)
                }.padding(.top, 14)
            }
            .padding(.horizontal, 20).padding(.top, 6)
            .compositingGroup().opacity(details).offset(y: (1 - details) * 14)
            Spacer(minLength: 0)
        }
        .frame(width: g.width, height: g.maxSheet, alignment: .top)
        .background {
            UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .circular)
                .fill(.white).shadow(color: DroneDropTrackerArt.color(0x1F2A37, 0.12), radius: 20, x: 0, y: -4)
        }
    }
    private func step(_ glyph: UInt32, _ title: String, _ time: String, _ on: Bool) -> some View {
        HStack(spacing: 12) {
            icon(glyph, 18, on ? 0xFFFFFF : 0x8A94A6).frame(width: 34, height: 34)
                .background(DroneDropTrackerArt.color(on ? 0x2F9E62 : 0xEDF1F6), in: Circle())
            text(title, "SemiBold", 15, 0x1F2A37).offset(y: -1 / displayScale).frame(maxWidth: .infinity, alignment: .leading)
            text(time, "Regular", 13, 0x8A94A6).fixedSize()
        }.frame(height: 50)
    }
    private func action(_ glyph: UInt32, _ title: String, _ label: String, _ dark: Bool) -> some View {
        Button {} label: {
            HStack(spacing: 8) {
                icon(glyph, 18, dark ? 0xFFFFFF : 0x1F2A37)
                text(title, "SemiBold", 15, dark ? 0xFFFFFF : 0x1F2A37).offset(y: -1 / displayScale)
            }.frame(maxWidth: .infinity).frame(height: 50)
                .background(DroneDropTrackerArt.color(dark ? 0x1F2A37 : 0xEDF1F6), in: Capsule(style: .circular))
        }.buttonStyle(DroneDropTrackerPressStyle()).accessibilityLabel(label)
    }
    private func sheetGesture(_ g: DroneDropTrackerGeometry) -> some Gesture {
        DragGesture(minimumDistance: 10).updating($sheetGestureActive) { _, active, _ in active = true }.onChanged { value in
            if !sheetDragging { sheetDragging = true; clock.sheetStart() }
            clock.sheetUpdate(value.translation.height, g.travel, value.velocity.height)
        }.onEnded { value in
            sheetDragging = false; clock.sheetEnd(value.velocity.height, g.travel)
        }
    }
    private func mapGesture(_ g: DroneDropTrackerGeometry) -> some Gesture {
        DragGesture(minimumDistance: 0).updating($mapGestureActive) { _, active, _ in active = true }.onChanged { value in
            if !mapDragging && abs(value.translation.width) <= 8 { return }
            if !mapDragging { mapDragging = true; clock.mapStart() }
            clock.mapUpdate(value.translation.width, g.width)
        }.onEnded { _ in if mapDragging { mapDragging = false; clock.mapEnd() } }
    }
}

struct DroneDropTrackerPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.scaleEffect(configuration.isPressed ? 0.96 : 1) }
}
