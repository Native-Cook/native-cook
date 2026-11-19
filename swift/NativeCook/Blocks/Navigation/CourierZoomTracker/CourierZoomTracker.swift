import SwiftUI
import UIKit

/// A 19-second film of a parcel tracker. The map frames the courier's van and the customer's door together, so it zooms
/// in by itself as the van gets closer: the whole city, the district, the street (where it turns north-up and leans
/// into 3D, buildings raised from their footprints), and for the last 100 m right in to the front door, where the van
/// parks and the panel says Arrived. A ghost finger drags the map to look ahead and taps locate to follow the van
/// again, then taps Call ("Calling Marco…") and Message (a quick reply is sent); the camera pulls back out to the city.
///
/// Touch: drag and pinch the map to take over; locate follows the van again; Call and Message open the system
/// confirmation dialogs. 2 s after the last touch the screen eases back to the poster and the film starts again.
/// Reduce Motion (or `autoPlay` off) shows the poster and leaves the map to the finger.
struct CourierZoomTracker: View {
    var autoPlay: Bool = true

    static let loop: Double = CourierZoomTrackerMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var touch = CourierZoomTrackerTouch()
    @State private var awake = false
    @State private var geo: CourierZoomTrackerGeometry?

    private typealias M = CourierZoomTrackerMotion
    private typealias City = CourierZoomTrackerCity
    private var film: Bool { autoPlay && !reduceMotion }

    static let ink = City.c(0x16181D)
    static let mute = City.c(0x6E727B)
    static let blue = City.c(0x2F6BFF)
    static let green = City.c(0x1F9D55)
    static let shadow = City.c(0x0A1A40)
    static let action = 48.0
    static let actionGap = 12.0
    static let panelH = 204.0
    static let panelPad = 20.0
    static let rowCY = 132.0 + 24
    static let quickReplies = ["Coming down now", "Leave it at the door"]
    static let phone = "+44 20 7946 0958"

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            let width = outer.size.width + insets.leading + insets.trailing
            let height = outer.size.height + insets.top + insets.bottom
            let g = CourierZoomTrackerGeometry(width: width, height: height, top: insets.top, bottom: insets.bottom)
            TimelineView(.animation(minimumInterval: nil, paused: !film && !awake)) { tl in
                let v = touch.frame(at: tl.date, film: film, g: g)
                screen(v, g)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
            .onAppear { geo = g }
            .onChange(of: g) { _, new in geo = new }
        }
        .background(City.Palette.land.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            _ = City.map // build the city's paths before the clock starts
            touch.origin = Date()
        }
    }

    // MARK: the screen at one frame

    private func screen(_ v: CourierZoomTrackerFrame, _ g: CourierZoomTrackerGeometry) -> some View {
        let cam = M.screenCam(v.cam, g.lay)
        return ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                CourierZoomTrackerDraw.map(&ctx, cam: cam, lay: g.lay, vanD: v.vanD, vanA: v.vanA, travEnd: v.travEnd, travA: v.travA,
                                           doorPulse: v.doorPulse)
            }
            .frame(width: g.width, height: g.height)
            .allowsHitTesting(false)
            pins(cam)
            Color.clear
                .contentShape(Rectangle())
                .frame(width: g.width, height: g.panelTop)
                .gesture(mapGesture(g))
            toast(icon: "checkmark.circle.fill", text: "Sent · “\(v.sent)”", tint: Self.green, k: v.toast, g: g)
            toast(icon: "phone.fill", text: "Calling Marco…", tint: Self.blue, k: v.calling, g: g)
            locate(following: v.follow)
                .position(x: g.locate.x, y: g.locate.y)
            panel(v, g)
                .offset(x: g.panelLeft, y: g.panelTop)
            Canvas { ctx, _ in
                let f = M.filmFinger(v.t, g.lay, g.buttons)
                let vis = f.vis * M.clamp01(v.live)
                if f.kind != 0 && vis >= 0.004 { CourierZoomTrackerDraw.finger(&ctx, f.x, f.y, vis, f.press) }
            }
            .frame(width: g.width, height: g.height)
            .allowsHitTesting(false)
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .clipped()
    }

    // MARK: pins

    private func pins(_ cam: CourierZoomTrackerCam) -> some View {
        let h = cam.toScreen(City.doorX, (City.home.y0 + City.home.y1) / 2)
        let homeFade = 1 - M.smooth01((cam.s - 1.2) / 1.1)
        let hub = cam.toScreen(City.hub.x, City.hub.y)
        let hubFade = 1 - M.smooth01((cam.s - 0.5) / 0.6)
        return ZStack(alignment: .topLeading) {
            // the hub: a dark rounded square with a box, "Hub" to its left
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(Self.ink)
                    .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(.white, lineWidth: 2))
                    .overlay(Image(systemName: "shippingbox.fill").font(.system(size: 11, weight: .semibold)).foregroundStyle(.white))
                    .frame(width: 26, height: 26)
                Text("Hub")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Self.ink)
                    .shadow(color: .white.opacity(0.95), radius: 1.5)
                    .frame(width: 28, alignment: .trailing)
                    .fixedSize()
                    .offset(x: -32, y: 5)
            }
            .frame(width: 26, height: 26, alignment: .topLeading)
            .opacity(hubFade)
            .offset(x: hub.x - 43, y: hub.y + 1)
            // Home: a blue disc with a house on a short white stem, "Home" to its right
            ZStack(alignment: .topLeading) {
                VStack(spacing: 0) {
                    Circle()
                        .fill(Self.blue)
                        .overlay(Circle().strokeBorder(.white, lineWidth: 3))
                        .overlay(Image(systemName: "house.fill").font(.system(size: 13, weight: .semibold)).foregroundStyle(.white))
                        .frame(width: 36, height: 36)
                        .shadow(color: Self.shadow.opacity(0.25), radius: 4, x: 0, y: 3)
                    UnevenRoundedRectangle(bottomLeadingRadius: 2, bottomTrailingRadius: 2)
                        .fill(.white)
                        .frame(width: 3, height: 7)
                }
                Text("Home")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Self.ink)
                    .shadow(color: .white.opacity(0.95), radius: 1.5)
                    .fixedSize()
                    .offset(x: 42, y: 9)
            }
            .frame(width: 36, height: 43, alignment: .topLeading)
            .scaleEffect(0.85 + 0.15 * homeFade)
            .opacity(homeFade)
            .offset(x: h.x - 18, y: h.y - 44)
        }
        .allowsHitTesting(false)
    }

    // MARK: overlays

    private func toast(icon: String, text: String, tint: Color, k raw: Double, g: CourierZoomTrackerGeometry) -> some View {
        let k: Double = M.clamp01(raw)
        let scale: Double = 0.96 + 0.04 * k
        let lift: Double = 10 * (1 - k)
        let pill = HStack(spacing: 8) {
            Image(systemName: icon).font(.system(size: 16, weight: .semibold)).foregroundStyle(tint)
            Text(text)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Self.ink)
                .lineLimit(1)
        }
        .padding(.horizontal, 16)
        .frame(height: 40)
        .frame(maxWidth: 260)
        .background(Capsule().fill(Color.white).shadow(color: Self.shadow.opacity(0.16), radius: 6, x: 0, y: 4))
        .fixedSize()
        return pill
            .scaleEffect(scale)
            .offset(y: lift)
            .opacity(k)
            .position(x: g.width / 2, y: g.panelTop - 36)
            .allowsHitTesting(false)
            .accessibilityHidden(k < 0.5)
    }

    @ViewBuilder
    private func locate(following: Bool) -> some View {
        let icon = Image(systemName: following ? "location.fill" : "location")
            .font(.system(size: 19))
            .foregroundStyle(Self.blue)
            .frame(width: 24, height: 24)
        if #available(iOS 26, *) {
            Button(action: onLocate) { icon }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.large)
                .accessibilityLabel("Follow the van")
        } else {
            Button(action: onLocate) { icon.frame(width: Self.action, height: Self.action) }
                .buttonStyle(.plain)
                .background(Circle().fill(.white).shadow(color: Self.shadow.opacity(0.16), radius: 5, x: 0, y: 3))
                .accessibilityLabel("Follow the van")
        }
    }

    // MARK: the panel

    private func panel(_ v: CourierZoomTrackerFrame, _ g: CourierZoomTrackerGeometry) -> some View {
        let a = v.arrived
        let left = City.routeLen - v.vanD
        return VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Arriving in").font(.system(size: 13, weight: .medium)).foregroundStyle(Self.mute).frame(height: 18)
                    Text(M.etaText(left))
                        .font(.system(size: 34, weight: .bold).monospacedDigit())
                        .tracking(-0.4)
                        .foregroundStyle(Self.ink)
                        .frame(height: 42)
                    Text(M.distanceText(left))
                        .font(.system(size: 15).monospacedDigit())
                        .foregroundStyle(City.c(0x3C4049))
                        .frame(height: 20)
                }
                .opacity((1 - a) * (1 - v.dip))
                .offset(y: -8 * a)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Order 4821 · 2 parcels").font(.system(size: 13, weight: .medium)).foregroundStyle(Self.mute).frame(height: 18)
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill").font(.system(size: 26, weight: .semibold)).foregroundStyle(Self.green)
                        Text("Arrived")
                            .font(.system(size: 34, weight: .bold))
                            .tracking(-0.4)
                            .foregroundStyle(Self.ink)
                    }
                    .frame(height: 42)
                    Text("Marco is at your door").font(.system(size: 15)).foregroundStyle(City.c(0x3C4049)).frame(height: 20)
                }
                .opacity(a * (1 - v.dip))
                .offset(y: 8 * (1 - a))
            }
            .frame(height: 84, alignment: .topLeading)
            .padding(.top, 18)
            Rectangle()
                .fill(City.c(0x3C4049).opacity(0.18))
                .frame(height: 1 / UIScreen.main.scale)
                .padding(.top, 14)
            HStack(spacing: 0) {
                Circle()
                    .fill(City.c(0xE8EEFC))
                    .overlay(Text("MR").font(.system(size: 17, weight: .bold)).foregroundStyle(Self.blue))
                    .frame(width: 48, height: 48)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Marco Ruiz").font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.ink).lineLimit(1).frame(height: 22)
                    Text("Blue van · KX24 TRL").font(.system(size: 13)).foregroundStyle(Self.mute).lineLimit(1).frame(height: 18)
                }
                .padding(.leading, 12)
                .padding(.trailing, 8)
                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                actions
            }
            .frame(height: 48)
            .padding(.top, 15)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Self.panelPad)
        .frame(width: g.panelW, height: Self.panelH + g.bottomInset, alignment: .topLeading)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 30, topTrailingRadius: 30)
                .fill(.white)
                .shadow(color: Self.shadow.opacity(0.14), radius: 9, x: 0, y: -4)
        )
    }

    /// Call and Message as the system's large bordered circles (about 54 pt), centred 76 pt apart on the 108 pt slot
    /// the layout gives them, as the React Native build shows them.
    private var actions: some View {
        ZStack {
            roundButton("phone.fill", label: "Call Marco", action: onCall)
                .confirmationDialog("Call Marco?", isPresented: dialogBinding(1), titleVisibility: .visible) {
                    Button("Call \(Self.phone)", action: onConfirmCall)
                    Button("Cancel", role: .cancel, action: onDismiss)
                }
                .position(x: 16, y: Self.action / 2)
            roundButton("message.fill", label: "Message Marco", action: onMessage)
                .confirmationDialog("Quick message to Marco", isPresented: dialogBinding(2), titleVisibility: .visible) {
                    ForEach(Self.quickReplies, id: \.self) { r in Button(r) { onSend(r) } }
                    Button("Cancel", role: .cancel, action: onDismiss)
                }
                .position(x: 92, y: Self.action / 2)
        }
        .frame(width: Self.action * 2 + Self.actionGap, height: Self.action)
    }

    private func roundButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 19))
                .foregroundStyle(Self.blue)
                .frame(width: 24, height: 24)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.circle)
        .controlSize(.large)
        .tint(Self.blue)
        .accessibilityLabel(label)
    }

    private func dialogBinding(_ d: Int) -> Binding<Bool> {
        Binding(get: { touch.dialog == d }, set: { open in
            if !open && touch.dialog == d { onDismiss() }
        })
    }

    // MARK: touch

    private func onCall() {
        touch.pause(at: Date(), film: film, g: currentGeometry)
        UISelectionFeedbackGenerator().selectionChanged()
        touch.dialog = 1
        wake()
    }

    private func onMessage() {
        touch.pause(at: Date(), film: film, g: currentGeometry)
        UISelectionFeedbackGenerator().selectionChanged()
        touch.dialog = 2
        wake()
    }

    private func onDismiss() {
        guard touch.dialog != 0 else { return }
        touch.pause(at: Date(), film: film, g: currentGeometry)
        touch.dialog = 0
        wake()
    }

    private func onConfirmCall() {
        let now = Date()
        touch.pause(at: now, film: film, g: currentGeometry)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        touch.dialog = 0
        touch.calling = .pulse(from: touch.calling.at(now), start: now, hold: 1.4, down: 0.3)
        wake()
    }

    private func onSend(_ text: String) {
        let now = Date()
        touch.pause(at: now, film: film, g: currentGeometry)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        touch.dialog = 0
        touch.sent = text
        touch.toast = .pulse(from: touch.toast.at(now), start: now, hold: 1.6, down: 0.4)
        wake()
    }

    /// Locate: the camera glides back onto the van and follows it again.
    private func onLocate() {
        let now = Date()
        let g = currentGeometry
        touch.pause(at: now, film: film, g: g)
        UISelectionFeedbackGenerator().selectionChanged()
        let target = M.framing(touch.vanD, g.lay)
        touch.camAnim = .init(from: touch.camera(at: now), to: target, start: now, duration: reduceMotion ? 0.2 : 0.8)
        touch.decay = nil
        touch.follow = true
        touch.lastActivity = now
        wake()
    }

    /// The screen's geometry, for the controls (the frames get it from the GeometryReader).
    private var currentGeometry: CourierZoomTrackerGeometry { geo ?? CourierZoomTrackerGeometry(width: 402, height: 874, top: 62, bottom: 34) }

    private static let lzMin = log2(M.sMin)
    private static let lzMax = log2(M.sMax * 1.2)

    private static func rubber(_ v: Double, _ lo: Double, _ hi: Double, _ band: Double) -> Double {
        if v < lo { let o = lo - v; return lo - o / (1 + o / band) }
        if v > hi { let o = v - hi; return hi + o / (1 + o / band) }
        return v
    }

    private func startTouch(_ now: Date, _ g: CourierZoomTrackerGeometry) {
        touch.pause(at: now, film: film, g: g)
        touch.follow = false
        if touch.dialog != 0 { touch.dialog = 0 }
    }

    private func mapGesture(_ g: CourierZoomTrackerGeometry) -> some Gesture {
        let pan = DragGesture(minimumDistance: 2)
            .onChanged { e in
                let now = Date()
                if !touch.panning {
                    startTouch(now, g)
                    touch.panning = true
                    touch.lastTranslation = .zero
                }
                let d = CGSize(width: e.translation.width - touch.lastTranslation.width, height: e.translation.height - touch.lastTranslation.height)
                touch.lastTranslation = e.translation
                var c = touch.camera(at: now)
                let cam = M.screenCam(c, g.lay)
                let a = cam.toWorld(g.lay.fx, g.lay.fy), b = cam.toWorld(g.lay.fx + d.width, g.lay.fy + d.height)
                c.x = Self.rubber(c.x - (b.0 - a.0), City.cityX0, City.cityX1, 300)
                c.y = Self.rubber(c.y - (b.1 - a.1), City.cityY0, City.cityY1, 300)
                touch.setCamera(c)
                touch.lastActivity = now
                wake()
            }
            .onEnded { e in
                let now = Date()
                touch.panning = false
                let c = touch.camera(at: now)
                let cam = M.screenCam(c, g.lay)
                let a = cam.toWorld(g.lay.fx, g.lay.fy)
                let b = cam.toWorld(g.lay.fx + e.velocity.width, g.lay.fy + e.velocity.height)
                touch.decay = .init(x: c.x, y: c.y, vx: -(b.0 - a.0), vy: -(b.1 - a.1), start: now)
                touch.lastActivity = now
                wake()
            }
        let pinch = MagnifyGesture()
            .onChanged { e in
                let now = Date()
                if !touch.pinching {
                    startTouch(now, g)
                    touch.pinching = true
                    touch.lastMagnification = 1
                }
                let change = e.magnification / max(touch.lastMagnification, 0.01)
                touch.lastMagnification = e.magnification
                var c = touch.camera(at: now)
                let cam = M.screenCam(c, g.lay)
                let f = cam.toWorld(e.startLocation.x, e.startLocation.y)
                let z1 = Self.rubber(c.z + log2(max(change, 0.01)), Self.lzMin, Self.lzMax, 0.4)
                let k = pow(2, c.z - z1)
                c.x = f.0 - (f.0 - c.x) * k
                c.y = f.1 - (f.1 - c.y) * k
                c.z = z1
                touch.setCamera(c)
                touch.lastActivity = now
                wake()
            }
            .onEnded { _ in
                let now = Date()
                touch.pinching = false
                let c = touch.camera(at: now)
                if c.z < Self.lzMin || c.z > Self.lzMax {
                    var to = c
                    to.z = min(max(c.z, Self.lzMin), Self.lzMax)
                    touch.camAnim = .init(from: c, to: to, start: now, duration: 0.3)
                }
                touch.lastActivity = now
                wake()
            }
        return pan.simultaneously(with: pinch)
    }

    /// Keeps the timeline running while a touch animates something with the film off.
    private func wake() {
        guard !film else { return }
        awake = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
            let quiet = Date().timeIntervalSince(touch.lastActivity) > 2.5
            if quiet && !touch.panning && !touch.pinching { awake = false } else { wake() }
        }
    }
}

// MARK: - geometry

struct CourierZoomTrackerGeometry: Equatable {
    let width: Double
    let height: Double
    let bottomInset: Double
    let panelW: Double
    let panelLeft: Double
    let panelTop: Double
    let lay: CourierZoomTrackerLay
    let locate: CGPoint
    /// Centres of locate, Call and Message (what the ghost finger taps).
    let buttons: [CGPoint]

    init(width: Double, height: Double, top: Double, bottom: Double) {
        typealias Z = CourierZoomTracker
        self.width = width
        self.height = height
        bottomInset = max(bottom, 16)
        panelW = width >= 600 ? min(480, width - 32) : width
        panelLeft = (width - panelW) / 2
        panelTop = height - Z.panelH - bottomInset
        lay = CourierZoomTrackerLay(w: width, h: height, fx: width / 2, fy: top + (panelTop - top) * 0.55, aw: min(width, 520) - 40,
                                    ah: panelTop - top - 70, panel: panelTop)
        locate = CGPoint(x: width - 16 - Z.action / 2, y: panelTop - 16 - Z.action / 2)
        let msgX = panelLeft + panelW - Z.panelPad - Z.action / 2
        buttons = [locate, CGPoint(x: msgX - Z.action - Z.actionGap, y: panelTop + Z.rowCY), CGPoint(x: msgX, y: panelTop + Z.rowCY)]
    }

    static func == (a: Self, b: Self) -> Bool { a.width == b.width && a.height == b.height && a.panelTop == b.panelTop }
}


// MARK: - state

/// Everything one frame shows.
struct CourierZoomTrackerFrame {
    var t: Double
    var cam: CourierZoomTrackerPose
    var follow: Bool
    var vanD: Double, vanA: Double
    var travEnd: Double, travA: Double
    var arrived: Double
    var toast: Double, calling: Double
    var doorPulse: Double
    var dip: Double
    var live: Double
    var sent: String
}

/// A value that rises to 1, holds, and falls (the toasts), or holds still.
struct CourierZoomTrackerPulse {
    var from: Double
    var start: Date
    var hold: Double
    var down: Double

    static func constant(_ v: Double) -> Self { .init(from: v, start: .distantPast, hold: -1, down: 0) }
    static func pulse(from: Double, start: Date, hold: Double, down: Double) -> Self { .init(from: from, start: start, hold: hold, down: down) }

    func at(_ now: Date) -> Double {
        typealias M = CourierZoomTrackerMotion
        guard hold >= 0 else { return from }
        let e = now.timeIntervalSince(start)
        if e < 0.3 { return M.lerp(from, 1, M.easeOutCubic(M.clamp01(e / 0.3))) }
        if e < 0.3 + hold { return 1 }
        return 1 - M.clamp01((e - 0.3 - hold) / down)
    }
}

/// What a finger set: written only by touches, read every frame.
struct CourierZoomTrackerTouch {
    private typealias M = CourierZoomTrackerMotion
    typealias Pose = CourierZoomTrackerPose

    struct CamAnim { var from: Pose; var to: Pose; var start: Date; var duration: Double }
    struct Decay { var x: Double; var y: Double; var vx: Double; var vy: Double; var start: Date }

    var origin = Date()
    var manual = false
    var camBase = Pose(x: 0, y: 0, z: 0, b: 0)
    var camAnim: CamAnim?
    var decay: Decay?
    var follow = true
    var vanD = 0.0
    var travEnd = 0.0
    var arrived = 0.0
    var toast = CourierZoomTrackerPulse.constant(0)
    var calling = CourierZoomTrackerPulse.constant(0)
    var pauseT = 0.0
    var liveFrom = 0.0
    var liveStart = Date.distantPast
    var dialog = 0
    var sent = CourierZoomTracker.quickReplies[0]
    var panning = false
    var pinching = false
    var lastTranslation = CGSize.zero
    var lastMagnification = 1.0
    var lastActivity = Date.distantPast

    /// The finger's camera now: the base, a glide, and a fling's decay on top.
    func camera(at now: Date) -> Pose {
        var c = camBase
        if let a = camAnim {
            let k = a.duration <= 0 ? 1 : M.easeBezier(now.timeIntervalSince(a.start) / a.duration)
            c = Pose.lerp(a.from, a.to, k)
        }
        if let d = decay {
            let k = -log(0.994) * 1000
            let e = (1 - exp(-k * max(0, now.timeIntervalSince(d.start)))) / k
            c.x = min(max(d.x + d.vx * e, CourierZoomTrackerCity.cityX0), CourierZoomTrackerCity.cityX1)
            c.y = min(max(d.y + d.vy * e, CourierZoomTrackerCity.cityY0), CourierZoomTrackerCity.cityY1)
        }
        return c
    }

    mutating func setCamera(_ c: Pose) {
        camBase = c
        camAnim = nil
        decay = nil
    }

    private enum Phase { case manual, returning(Double, Date), auto(Double) }

    private func phase(_ now: Date, film: Bool) -> Phase {
        if !manual {
            guard film else { return .manual }
            return .auto(max(0, now.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop))
        }
        if !film || panning || pinching || dialog != 0 { return .manual }
        let quiet = now.timeIntervalSince(lastActivity) - M.idle
        if quiet <= 0 { return .manual }
        if quiet < M.returnDur { return .returning(quiet / M.returnDur, lastActivity.addingTimeInterval(M.idle)) }
        return .auto((quiet - M.returnDur).truncatingRemainder(dividingBy: M.loop))
    }

    private func filmFrame(_ t: Double, _ g: CourierZoomTrackerGeometry) -> CourierZoomTrackerFrame {
        let (cam, follow) = M.filmCamera(t, g.lay)
        let v = M.filmVan(t), tr = M.filmTravelled(t)
        return .init(t: t, cam: cam, follow: follow, vanD: v.d, vanA: v.a, travEnd: tr.end, travA: tr.a, arrived: M.filmArrived(t),
                     toast: M.filmToast(t), calling: M.filmCall(t), doorPulse: M.filmDoorPulse(t), dip: 0, live: 1,
                     sent: CourierZoomTracker.quickReplies[0])
    }

    private func manualFrame(_ now: Date) -> CourierZoomTrackerFrame {
        let live = liveFrom * (1 - M.clamp01(now.timeIntervalSince(liveStart) / 0.22))
        return .init(t: pauseT, cam: camera(at: now), follow: follow, vanD: vanD, vanA: 1, travEnd: travEnd, travA: 1, arrived: arrived,
                     toast: toast.at(now), calling: calling.at(now), doorPulse: 0, dip: 0, live: live, sent: sent)
    }

    func frame(at now: Date, film: Bool, g: CourierZoomTrackerGeometry) -> CourierZoomTrackerFrame {
        switch phase(now, film: film) {
        case .auto(let t): return filmFrame(t, g)
        case .manual: return manual ? manualFrame(now) : filmFrame(0, g)
        case .returning(let k, let rs):
            // back to the poster: the camera eases home while the van and the copy dip out and come back reset
            let f = manualFrame(rs)
            let rest = filmFrame(0, g)
            let e = M.easeInOutCubic(k)
            let d = sin(.pi * k)
            let dip = d * d
            var out = rest
            out.cam = Pose.lerp(f.cam, rest.cam, e)
            out.dip = dip
            out.vanA = 1 - dip
            out.travA = 1 - dip
            if k >= 0.5 {
                out.vanD = 0
                out.travEnd = 0
                out.arrived = 0
            } else {
                out.vanD = f.vanD
                out.travEnd = f.travEnd
                out.arrived = f.arrived * (1 - M.clamp01(k * 2))
            }
            out.toast = f.toast * (1 - M.clamp01(k * 3))
            out.calling = f.calling * (1 - M.clamp01(k * 3))
            out.doorPulse = 0
            out.live = 0
            out.follow = true
            out.t = f.t
            out.sent = f.sent
            return out
        }
    }

    /// A touch pauses the film where it is (the van stops; the map is the finger's).
    mutating func pause(at now: Date, film: Bool, g: CourierZoomTrackerGeometry) {
        let ph = phase(now, film: film)
        switch ph {
        case .manual where manual:
            setCamera(camera(at: now))
        default:
            let v = frame(at: now, film: film, g: g)
            let wasAuto: Bool = { if case .auto = ph { return true }; return !manual }()
            manual = true
            setCamera(v.cam)
            follow = v.follow
            vanD = v.vanD
            travEnd = v.travEnd
            arrived = v.arrived
            toast = .constant(v.toast)
            calling = .constant(v.calling)
            pauseT = v.t
            if wasAuto && film {
                liveFrom = 1
                liveStart = now
            } else {
                liveFrom = 0
            }
            sent = v.sent
        }
        lastActivity = now
    }
}
