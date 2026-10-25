import SwiftUI
import UIKit

private enum FruitSmoothieCarouselMath {
    static func clamp(_ x: Double, _ lower: Double = 0, _ upper: Double = 1) -> Double { min(upper, max(lower, x)) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u*u*(3-2*u) }
    static func wrap(_ index: Int) -> Int { (index % 5 + 5) % 5 }
    static func automatic(_ seconds: Double) -> Double {
        let t = max(0, seconds).truncatingRemainder(dividingBy: FruitSmoothieCarousel.loop)
        let slot = floor(t / 1.6)
        return slot + smooth((t-slot*1.6-1)/0.6)
    }
    static func spring(_ progress: Double) -> Double {
        if progress >= 1 { return 1 }
        let u = max(0, progress), a = 5.15, b = 3.59
        let end = 1-exp(-a)*(cos(b)+a/b*sin(b))
        return (1-exp(-a*u)*(cos(b*u)+a/b*sin(b*u)))/end
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255)
    }
    static func mixedColor(_ from: UInt32, _ to: UInt32, _ value: Double) -> Color {
        let t = clamp(value)
        func channel(_ shift: UInt32) -> Double {
            let a = Double((from>>shift)&255), b = Double((to>>shift)&255)
            return (a+(b-a)*t)/255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }
    static func tabX(_ index: Int) -> Double {
        let cycle = floor(Double(index)/5)
        return [63.0,196,315,420,505][wrap(index)] + cycle*575
    }
    static func tabShift(_ selection: Double) -> Double {
        let index = Int(floor(selection)), fraction = selection-floor(selection)
        return tabX(index) + (tabX(index+1)-tabX(index))*fraction - 63
    }
    static func tabWidth(_ index: Int) -> Double { [106.0,92,78,58,70][wrap(index)] }
}

/// Unequal-size drink cards exchange their matching artwork layers beyond the panel edges.
struct FruitSmoothieCarousel: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var epoch = Date()
    @State private var visible = false
    @State private var manualFrom: Double? = nil
    @State private var manualTarget = 0.0
    @State private var eventDate = Date()
    @State private var eventDuration = 0.0
    @State private var eventSpring = false
    @State private var eventMoving = false
    @State private var lastTouch = Date()
    @State private var held = false
    @State private var panOrigin = 0.0
    @State private var serial = 0
    @State private var reducedSelection = 0
    @State private var chooser = false
    @GestureState private var panActive = false

    private static let flavors = ["Strawberry", "Blueberry", "Orange", "Kiwi", "Apricot"]
    private static let tops: [UInt32] = [0xEE91B0,0xA8B8F1,0xFFE690,0xDAF78B,0xF5D5BE]
    private static let bottoms: [UInt32] = [0xD26388,0x6F83D1,0xF2B751,0xB7DF60,0xE2A18F]
    private static let art = ["strawberry", "blueberry", "orange", "kiwi", "apricot"].map { flavor in
        UIImage(named: "FruitSmoothieCarousel-\(flavor).png").flatMap { $0.preparingForDisplay() ?? $0 }
    }

    private func selection(_ now: Date) -> Double {
        if reduced { return Double(reducedSelection) }
        if let from = manualFrom {
            if held { return manualTarget }
            let raw = eventDuration == 0 ? 1 : now.timeIntervalSince(eventDate)/eventDuration
            let progress = eventSpring ? FruitSmoothieCarouselMath.spring(raw) : FruitSmoothieCarouselMath.smooth(raw)
            let current = from + (manualTarget-from)*progress
            let recovery = autoPlay ? FruitSmoothieCarouselMath.smooth((now.timeIntervalSince(lastTouch)-2)/0.6) : 0
            // A nearby complete cycle is visually the same Strawberry rest, without a long rewind.
            let rest = (manualTarget/5).rounded()*5
            return current + (rest-current)*recovery
        }
        return FruitSmoothieCarouselMath.automatic(previewTime ?? (autoPlay ? now.timeIntervalSince(epoch) : 0))
    }

    var body: some View {
        GeometryReader { geometry in
            let scale = geometry.size.width/393
            TimelineView(.animation(paused: !visible || scene != .active || reduced || held || previewTime != nil || (!autoPlay && !eventMoving))) { timeline in
                let q = selection(timeline.date)
                ZStack(alignment: .topLeading) {
                    FruitSmoothieCarouselMath.color(0xFCFAFC)
                    heading
                    cards(q)
                    tabs(q)
                    navigation
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
                .clipped()
                .allowsHitTesting(previewTime == nil)
                .contentShape(Rectangle())
                .gesture(pan(scale: scale), including: reduced || previewTime != nil || chooser ? .none : .all)
                .scaleEffect(scale, anchor: .topLeading)
                .offset(y: max(0, (geometry.size.height-852*scale)/2))
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            }
        }
        .background(FruitSmoothieCarouselMath.color(0xFCFAFC))
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear { visible = true; reset() }
        .onDisappear { visible = false; reset() }
        .onChange(of: scene) { _, _ in reset() }
        .onChange(of: reduced) { _, _ in reset() }
        .onChange(of: autoPlay) { _, _ in reset() }
        .onChange(of: previewTime) { _, _ in reset() }
        .onChange(of: panActive) { _, active in
            guard !active else { return }
            Task { @MainActor in
                await Task.yield()
                guard !panActive, held, !chooser else { return }
                finishPan(velocity: 0, haptic: false)
            }
        }
        .onChange(of: chooser) { _, presented in
            if !presented && held { interact(selection(Date()), duration: 0) }
        }
        .task(id: serial) {
            guard manualFrom != nil && !held && !reduced else { return }
            let delay = autoPlay ? 2.6 : max(0.02, eventDuration+0.02)
            do { try await Task.sleep(for: .seconds(delay)) } catch { return }
            guard !held, manualFrom != nil else { return }
            if autoPlay { reset() } else { eventMoving = false }
        }
        .confirmationDialog("Choose a flavor", isPresented: $chooser, titleVisibility: .visible) {
            ForEach(0..<5, id: \.self) { index in
                Button(Self.flavors[index]) { choose(index) }
            }
            Button("Cancel", role: .cancel) { }
        }
    }

    private var heading: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Seasonal").font(.system(size: 22, weight: .semibold))
            Text("Smoothie").font(.system(size: 22, weight: .bold))
        }
        .foregroundStyle(FruitSmoothieCarouselMath.color(0x242426))
        .frame(width: 260, height: 54, alignment: .topLeading)
        .offset(x: 63, y: 161)
        .allowsHitTesting(false)
    }

    private var navigation: some View {
        ZStack(alignment: .topLeading) {
            Button(action: presentChooser) {
                VStack(spacing: 9.5) {
                    ForEach(0..<3, id: \.self) { _ in Capsule().fill(FruitSmoothieCarouselMath.color(0xD5A6B4)).frame(width: 26, height: 2) }
                }
                .frame(width: 44, height: 44)
            }
            .position(x: 76, y: 84.5)
            .accessibilityLabel("Choose a flavor")

            Button(action: presentChooser) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 20, weight: .regular))
                    .foregroundStyle(FruitSmoothieCarouselMath.color(0xAEAAAF))
                    .frame(width: 44, height: 43)
                    .background(FruitSmoothieCarouselMath.color(0xF0EDF0), in: RoundedRectangle(cornerRadius: 11))
                    .frame(width: 44, height: 44)
            }
            .position(x: 312, y: 86.5)
            .accessibilityLabel("Find a flavor")
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .buttonStyle(.plain)
    }

    private func cards(_ q: Double) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(-1...6, id: \.self) { copy in
                let index = FruitSmoothieCarouselMath.wrap(copy)
                let distance = Double(copy)-q
                let a = min(1, abs(distance))
                let x = 51 + (distance >= 0 ? 304 : 120)*distance
                let width = 287-197*a
                let height = 405-63*a
                let y = 294+32*a
                let radius = 40-6*a
                let artScale = 1-0.69*a
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: radius)
                        .fill(LinearGradient(colors: [FruitSmoothieCarouselMath.color(Self.tops[index]), FruitSmoothieCarouselMath.color(Self.bottoms[index])], startPoint: .top, endPoint: .bottom))
                        .overlay(RoundedRectangle(cornerRadius: radius).stroke(.white.opacity(0.1), lineWidth: 0.7))
                        .frame(width: width, height: height)
                        .offset(x: x, y: y)
                    if let image = Self.art[index] {
                        Image(uiImage: image).resizable().interpolation(.high).scaledToFit()
                            .frame(width: 336*artScale, height: 504*artScale)
                            .position(x: x+width/2, y: 438+50*a)
                    }
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
                .zIndex(1-a)
                .opacity(copy == -1 ? 0 : copy == 4 ? 1-FruitSmoothieCarouselMath.smooth((q-4.82)/0.18) : 1)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(Self.flavors[FruitSmoothieCarouselMath.wrap(Int(q.rounded()))]) smoothie")
        .accessibilityHint("Swipe horizontally to choose a flavor")
        .accessibilityAdjustableAction { direction in
            chooseAdjacent(direction == .decrement ? -1 : 1)
        }
    }

    private func tabs(_ q: Double) -> some View {
        let shift = FruitSmoothieCarouselMath.tabShift(q)
        let current = Int(floor(q)), fraction = q-floor(q)
        let underlineWidth = FruitSmoothieCarouselMath.tabWidth(current)*(1-fraction)+FruitSmoothieCarouselMath.tabWidth(current+1)*fraction
        return ZStack(alignment: .topLeading) {
            // Extra copies preserve the visible Orange label at the final Strawberry wrap.
            ForEach(-1...7, id: \.self) { copy in
                let index = FruitSmoothieCarouselMath.wrap(copy)
                let selected = max(0, 1-abs(Double(copy)-q))
                Button { choose(index) } label: {
                    Text(Self.flavors[index]).font(.system(size: 18, weight: .regular))
                        .foregroundStyle(FruitSmoothieCarouselMath.mixedColor(0xB5B2B5, 0x2D2D30, selected))
                        .frame(width: FruitSmoothieCarouselMath.tabWidth(copy), height: 44, alignment: .leading)
                }
                .buttonStyle(.plain)
                .position(x: FruitSmoothieCarouselMath.tabX(copy)-shift+FruitSmoothieCarouselMath.tabWidth(copy)/2, y: 754)
                .accessibilityLabel(Self.flavors[index])
                .accessibilityAddTraits(abs(Double(copy)-q)<0.5 ? .isSelected : [])
                .accessibilityHidden(FruitSmoothieCarouselMath.tabX(copy)-shift < -110 || FruitSmoothieCarouselMath.tabX(copy)-shift > 393)
            }
            Capsule().fill(FruitSmoothieCarouselMath.color(0xC66A8F))
                .frame(width: 50, height: 3)
                .offset(x: 63+underlineWidth/2-25, y: 776)
                .allowsHitTesting(false)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func reset() {
        held = false; manualFrom = nil; eventMoving = false; reducedSelection = 0; chooser = false
        epoch = Date(); serial += 1
    }
    private func interact(_ target: Double, duration: Double = 0.6, spring: Bool = false) {
        let now = Date(), current = selection(now)
        let wrapped = current >= 5 - 0.00000001
        let from = wrapped ? current - 5 : current
        let destination = wrapped && target >= 5 ? target - 5 : target
        if reduced { reducedSelection = FruitSmoothieCarouselMath.wrap(Int(destination.rounded())) }
        held = false; manualFrom = from; manualTarget = destination; eventDate = now; eventDuration = duration
        eventSpring = spring; eventMoving = duration > 0; lastTouch = now; serial += 1
    }
    private func choose(_ index: Int) {
        let q = selection(Date()), item = FruitSmoothieCarouselMath.wrap(index)
        let target = item == 0 && q > 2.5 ? 5 : Double(item)
        let changed = FruitSmoothieCarouselMath.wrap(Int(q.rounded())) != item
        chooser = false
        interact(target)
        if changed && !reduced { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }
    private func chooseAdjacent(_ direction: Int) { choose(FruitSmoothieCarouselMath.wrap(Int(selection(Date()).rounded())+direction)) }
    private func presentChooser() {
        let q = selection(Date())
        manualFrom = q; manualTarget = q; held = true; eventMoving = false; serial += 1; chooser = true
    }
    private func pan(scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 6)
            .updating($panActive) { _, active, _ in active = true }
            .onChanged { value in
                if !held {
                    let current = selection(Date()), q = current >= 5 - 0.00000001 ? current - 5 : current
                    panOrigin = q; manualFrom = q; manualTarget = q
                    held = true; eventMoving = false; serial += 1
                }
                let proposed = panOrigin-Double(value.translation.width)/scale/304
                let edge = FruitSmoothieCarouselMath.clamp(proposed, 0, 5)
                let over = (proposed-edge)*304
                manualTarget = edge + over/(1+abs(over)/120)/304
                eventDuration = 0; lastTouch = Date()
            }
            .onEnded { value in finishPan(velocity: Double(value.velocity.width)/scale, haptic: true) }
    }
    private func finishPan(velocity: Double, haptic: Bool) {
        guard held else { return }
        let projected = manualTarget-FruitSmoothieCarouselMath.clamp(velocity, -1200, 1200)*0.12/304
        let target = FruitSmoothieCarouselMath.clamp(projected.rounded(), 0, 5)
        let changed = FruitSmoothieCarouselMath.wrap(Int(target)) != FruitSmoothieCarouselMath.wrap(Int(panOrigin.rounded()))
        interact(target, duration: 0.42, spring: true)
        if haptic && changed && !reduced { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }
}
