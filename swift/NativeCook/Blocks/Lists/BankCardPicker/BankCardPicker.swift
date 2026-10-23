import CoreText
import SwiftUI
import UIKit

struct BankCardPickerSelection: Identifiable {
    let designId: String
    let designLabel: String
    let planId: String
    let planLabel: String

    var id: String { "\(designId)-\(planId)" }
    var summary: String { "Bloom Card preview\nDesign: \(designLabel)\nPlan: \(planLabel)" }
}

struct BankCardPicker: View {
    static let loop = 12.0

    var autoPlay = false
    var freezeAt: Double?
    var onBack: (() -> Void)?
    var onShare: ((BankCardPickerSelection) -> Void)?
    var onGetCard: ((BankCardPickerSelection) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var autoStartedAt: Date?
    @State private var design = 0
    @State private var plan = 0
    @State private var slotA = 0
    @State private var slotB = 0
    @State private var swap = 0.0
    @State private var ring = 0.0
    @State private var pill = 0.0
    @State private var contactActive = false
    @State private var resumeGeneration = 0
    @State private var ctaPressed = false
    @State private var pressedThumb: Int?
    @State private var confirmation: BankCardPickerSelection?
    @State private var shareSelection: BankCardPickerSelection?
    @State private var modalOpen = false

    init(
        autoPlay: Bool = false,
        freezeAt: Double? = nil,
        onBack: (() -> Void)? = nil,
        onShare: ((BankCardPickerSelection) -> Void)? = nil,
        onGetCard: ((BankCardPickerSelection) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.freezeAt = freezeAt
        self.onBack = onBack
        self.onShare = onShare
        self.onGetCard = onGetCard
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let running = autoStartedAt != nil && freezeAt == nil && !reduceMotion
            TimelineView(.animation(paused: !running)) { timeline in
                let snapshot = renderedSnapshot(at: timeline.date)
                content(size: size, safeTop: insets.top, snapshot: snapshot)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(BankCardPickerPalette.page.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            BankCardPickerFonts.register()
            if autoPlay, !reduceMotion, freezeAt == nil { autoStartedAt = .now }
        }
        .onChange(of: reduceMotion) {
            if reduceMotion {
                captureCurrent(at: .now)
            } else if autoPlay, freezeAt == nil {
                resetToRest()
                autoStartedAt = .now
            }
        }
        .task(id: resumeGeneration) {
            guard autoPlay, freezeAt == nil, !reduceMotion, !contactActive, !modalOpen, autoStartedAt == nil else { return }
            try? await Task.sleep(for: .milliseconds(BankCardPickerMotion.resumeAfterMs))
            guard !Task.isCancelled, !contactActive else { return }
            resetToRest()
            autoStartedAt = .now
        }
        .alert(
            "Card selected",
            isPresented: Binding(
                get: { confirmation != nil },
                set: { if !$0 { confirmation = nil } }
            ),
            presenting: confirmation
        ) { _ in
            Button("Done") {
                confirmation = nil
                endModal()
            }
        } message: { value in
            Text("\(value.designLabel)\n\(value.planLabel)")
        }
        .sheet(item: $shareSelection, onDismiss: endModal) { value in
            BankCardPickerShareSheet(items: [value.summary])
        }
    }

    private func content(size: CGSize, safeTop: CGFloat, snapshot: BankCardPickerSnapshot) -> some View {
        let dy = safeTop - 62
        let segmentWidth = size.width - BankCardPickerTokens.segmentSide * 2
        let halfSegment = (segmentWidth - BankCardPickerTokens.segmentInset * 2) / 2

        return ZStack(alignment: .topLeading) {
            BankCardPickerPalette.page

            hero(size: size, dy: dy, snapshot: snapshot)

            planSelector(
                dy: dy,
                width: segmentWidth,
                halfWidth: halfSegment,
                planValue: snapshot.pill,
                selectedPlan: snapshot.plan
            )

            information(dy: dy, plan: snapshot.plan)
            bottomBar(dy: dy, size: size)
            callToAction(dy: dy, size: size)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(freezeAt == nil)
        .simultaneousGesture(contactGesture)
    }

    private func hero(size: CGSize, dy: CGFloat, snapshot: BankCardPickerSnapshot) -> some View {
        ZStack(alignment: .topLeading) {
            BankCardPickerHeroField()

            ZStack(alignment: .topLeading) {
                ForEach((1...BankCardPickerTokens.stackCount).reversed(), id: \.self) { depth in
                    BankCardPickerPlate(depth: depth)
                }
                BankCardPickerCardSlot(
                    design: BankCardPickerData.designs[snapshot.slotA],
                    frontness: 1 - snapshot.swap
                )
                BankCardPickerCardSlot(
                    design: BankCardPickerData.designs[snapshot.slotB],
                    frontness: snapshot.swap
                )
            }
            .frame(width: BankCardPickerTokens.cardWidth, height: BankCardPickerTokens.cardHeight, alignment: .topLeading)
            .offset(
                x: BankCardPickerTokens.cardCenterX - BankCardPickerTokens.cardWidth / 2,
                y: BankCardPickerTokens.cardCenterY - BankCardPickerTokens.cardHeight / 2 + dy
            )
            .allowsHitTesting(false)

            Button(action: { onBack?() }) { BankCardPickerBackGlyph() }
                .buttonStyle(.plain)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
                .offset(x: 11.3 - (44 - 11.3) / 2, y: 67 + dy - 22)
                .accessibilityLabel("Back")
                .accessibilityHint(onBack == nil ? "Use the system back gesture to leave this preview" : "")
                .disabled(onBack == nil)

            Button(action: share) { BankCardPickerShareGlyph() }
                .buttonStyle(.plain)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
                .offset(x: size.width - 18.3 - 19 - (44 - 19) / 2, y: 67 + dy - 22)
                .accessibilityLabel("Share selected card")

            Text("Bloom Card")
                .font(BankCardPickerFonts.semibold(30))
                .tracking(-0.5)
                .foregroundStyle(.white)
                .frame(width: size.width, height: 36)
                .offset(y: 370 + dy - 18)
                .accessibilityAddTraits(.isHeader)

            designCaption(index: snapshot.design)
                .offset(y: 407 + dy - 12)

            designPicker(snapshot: snapshot)
                .frame(width: size.width, height: 77)
                .offset(y: 471.7 + dy - 65 / 2 - 6)
        }
        .frame(width: size.width, height: 538 + dy, alignment: .topLeading)
        .clipShape(
            UnevenRoundedRectangle(
                cornerRadii: .init(bottomLeading: 33, bottomTrailing: 33),
                style: .continuous
            )
        )
    }

    private func designCaption(index: Int) -> some View {
        let item = BankCardPickerData.designs[index]
        return HStack(spacing: 9.7) {
            Text("Design by \(item.designer)")
                .font(BankCardPickerFonts.regular(17))
                .foregroundStyle(.white)
            Text(item.price)
                .font(BankCardPickerFonts.medium(13))
                .monospacedDigit()
                .foregroundStyle(.black)
                .padding(.horizontal, 10)
                .frame(height: 21)
                .background(BankCardPickerPalette.lime, in: Capsule())
        }
        .frame(maxWidth: .infinity)
        .transition(.asymmetric(insertion: .opacity, removal: .opacity))
        .id(item.id)
    }

    private func designPicker(snapshot: BankCardPickerSnapshot) -> some View {
        ScrollView(.horizontal) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(BankCardPickerPalette.lime, lineWidth: 3)
                    .frame(width: 65, height: 65)
                    .offset(x: snapshot.ring * 81.4)
                    .allowsHitTesting(false)

                HStack(spacing: 16.4) {
                    ForEach(Array(BankCardPickerData.designs.enumerated()), id: \.element.id) { index, item in
                        Button {
                            selectDesign(index, byFinger: true)
                        } label: {
                            ZStack {
                                BankCardPickerAssetImage(name: item.thumbnail)
                                    .scaledToFill()
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(.black.opacity(0.35), lineWidth: 2)
                                BankCardPickerCheckGlyph()
                                    .opacity(max(0, 1 - abs(snapshot.ring - Double(index)) * 2))
                                    .scaleEffect(0.9 + 0.1 * max(0, 1 - abs(snapshot.ring - Double(index)) * 2))
                            }
                            .frame(width: 65, height: 65)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .scaleEffect(
                                (1 - (10.8 / 65) * max(0, 1 - abs(snapshot.ring - Double(index)))) *
                                    (pressedThumb == index ? 0.97 : 1)
                            )
                        }
                        .buttonStyle(.plain)
                        .simultaneousGesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { _ in withAnimation(.easeOut(duration: 0.12)) { pressedThumb = index } }
                                .onEnded { _ in withAnimation(.easeOut(duration: 0.15)) { pressedThumb = nil } }
                        )
                        .accessibilityLabel("\(item.label), design by \(item.designer), \(item.price)")
                        .accessibilityAddTraits(index == snapshot.design ? .isSelected : [])
                    }
                }
            }
            .padding(.leading, 33.3)
            .padding(.trailing, 24)
            .padding(.top, 6)
        }
        .scrollIndicators(.hidden)
    }

    private func planSelector(
        dy: CGFloat,
        width: CGFloat,
        halfWidth: CGFloat,
        planValue: Double,
        selectedPlan: Int
    ) -> some View {
        ZStack(alignment: .topLeading) {
            Color.white
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(BankCardPickerPalette.dark)
                .frame(width: halfWidth, height: 47)
                .offset(x: 2 + CGFloat(planValue) * halfWidth, y: 2)
            HStack(spacing: 0) {
                ForEach(Array(BankCardPickerData.plans.enumerated()), id: \.element.id) { index, item in
                    Button {
                        selectPlan(index, byFinger: true)
                    } label: {
                        HStack(spacing: 8) {
                            Text(item.label)
                                .font(BankCardPickerFonts.semibold(15))
                                .foregroundStyle(index == selectedPlan ? .white : BankCardPickerPalette.grey)
                            if index == 1 { BankCardPickerFlagGlyph().frame(width: 14.3, height: 13) }
                        }
                        .frame(width: halfWidth, height: 51)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(item.label)
                    .accessibilityAddTraits(index == selectedPlan ? .isSelected : [])
                }
            }
            .padding(.leading, 2)
        }
        .frame(width: width, height: 51, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .offset(x: 25, y: 556 + dy)
    }

    private func information(dy: CGFloat, plan: Int) -> some View {
        let selected = BankCardPickerData.plans[plan]
        return ZStack(alignment: .topLeading) {
            Color.white
            BankCardPickerInfoRow(label: "Card\nmaintenance", value: selected.fee, unit: selected.feeUnit)
                .offset(y: 25)
            Divider().background(BankCardPickerPalette.hairline).offset(x: 17, y: 88).frame(width: 368)
            BankCardPickerInfoRow(label: "Cashback\ncategories", value: selected.categories)
                .offset(y: 106)
            Divider().background(BankCardPickerPalette.hairline).offset(x: 17, y: 169).frame(width: 368)
            BankCardPickerInfoRow(label: "Transfers to any bank", value: "Free")
                .offset(y: 189)
        }
        .frame(height: 280)
        .clipShape(
            UnevenRoundedRectangle(
                cornerRadii: .init(topLeading: 24, topTrailing: 24),
                style: .continuous
            )
        )
        .offset(x: 8, y: 621 + dy)
        .padding(.trailing, 16)
    }

    private func bottomBar(dy: CGFloat, size: CGSize) -> some View {
        Rectangle()
            .fill(.ultraThinMaterial)
            .overlay(Color.white.opacity(0.55))
            .clipShape(
                UnevenRoundedRectangle(
                    cornerRadii: .init(topLeading: 30, topTrailing: 30),
                    style: .continuous
                )
            )
            .frame(width: size.width, height: max(0, size.height - (752 + dy)))
            .offset(y: 752 + dy)
            .allowsHitTesting(false)
    }

    private func callToAction(dy: CGFloat, size: CGSize) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            let value = currentSelection(at: .now)
            if let onGetCard {
                onGetCard(value)
            } else {
                beginModal()
                confirmation = value
            }
        } label: {
            Text("Get the card")
                .font(BankCardPickerFonts.medium(18))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(.plain)
        .frame(width: size.width - 34, height: 55)
        .background(BankCardPickerPalette.dark, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .scaleEffect(ctaPressed ? 0.97 : 1)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in withAnimation(.easeOut(duration: 0.12)) { ctaPressed = true } }
                .onEnded { _ in withAnimation(.easeOut(duration: 0.15)) { ctaPressed = false } }
        )
        .offset(x: 17, y: 772.5 + dy)
        .accessibilityLabel("Get the card")
        .accessibilityHint("Confirm the selected design and plan")
    }

    private var contactGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !contactActive else { return }
                contactActive = true
                captureCurrent(at: .now)
                resumeGeneration += 1
            }
            .onEnded { _ in
                contactActive = false
                resumeGeneration += 1
            }
    }

    private func selectDesign(_ index: Int, byFinger: Bool) {
        guard index != design else { return }
        captureCurrent(at: .now)
        let toB = swap < 0.5
        if toB { slotB = index } else { slotA = index }
        design = index
        let target = toB ? 1.0 : 0.0
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .timingCurve(0.77, 0, 0.175, 1, duration: 0.42)) {
            swap = target
        }
        withAnimation(reduceMotion ? .easeOut(duration: 0.16) : .spring(duration: 0.3, bounce: 0)) {
            ring = Double(index)
        }
        if byFinger { UISelectionFeedbackGenerator().selectionChanged() }
    }

    private func selectPlan(_ index: Int, byFinger: Bool) {
        guard index != plan else { return }
        captureCurrent(at: .now)
        plan = index
        withAnimation(reduceMotion ? .easeOut(duration: 0.16) : .spring(duration: 0.3, bounce: 0)) {
            pill = Double(index)
        }
        if byFinger { UISelectionFeedbackGenerator().selectionChanged() }
    }

    private func captureCurrent(at date: Date) {
        guard autoStartedAt != nil else { return }
        let snapshot = renderedSnapshot(at: date)
        autoStartedAt = nil
        design = snapshot.design
        plan = snapshot.plan
        slotA = snapshot.slotA
        slotB = snapshot.slotB
        swap = snapshot.swap
        ring = snapshot.ring
        pill = snapshot.pill
    }

    private func resetToRest() {
        design = 0
        plan = 0
        slotA = 0
        slotB = 0
        swap = 0
        ring = 0
        pill = 0
    }

    private func currentSelection(at date: Date) -> BankCardPickerSelection {
        let snapshot = renderedSnapshot(at: date)
        let selectedDesign = BankCardPickerData.designs[snapshot.design]
        let selectedPlan = BankCardPickerData.plans[snapshot.plan]
        return BankCardPickerSelection(
            designId: selectedDesign.id,
            designLabel: selectedDesign.label,
            planId: selectedPlan.id,
            planLabel: selectedPlan.label
        )
    }

    private func share() {
        let value = currentSelection(at: .now)
        if let onShare {
            onShare(value)
        } else {
            beginModal()
            shareSelection = value
        }
    }

    private func beginModal() {
        modalOpen = true
        captureCurrent(at: .now)
        resumeGeneration += 1
    }

    private func endModal() {
        modalOpen = false
        resumeGeneration += 1
    }

    private func renderedSnapshot(at date: Date) -> BankCardPickerSnapshot {
        if let freezeAt {
            return BankCardPickerSnapshot.at(milliseconds: freezeAt, reduced: false)
        }
        if let autoStartedAt, !reduceMotion {
            let milliseconds = date.timeIntervalSince(autoStartedAt)
                .truncatingRemainder(dividingBy: Self.loop) * 1_000
            return BankCardPickerSnapshot.at(milliseconds: milliseconds, reduced: false)
        }
        return .init(
            design: design,
            plan: plan,
            slotA: slotA,
            slotB: slotB,
            swap: swap,
            ring: ring,
            pill: pill
        )
    }
}

private struct BankCardPickerSnapshot {
    let design: Int
    let plan: Int
    let slotA: Int
    let slotB: Int
    let swap: Double
    let ring: Double
    let pill: Double

    static func at(milliseconds: Double, reduced: Bool) -> BankCardPickerSnapshot {
        let time = milliseconds.truncatingRemainder(dividingBy: BankCardPicker.loop * 1_000)
        var design = 0
        var plan = 0
        var slotA = 0
        var slotB = 0
        var swap = 0.0
        var ring = 0.0
        var pill = 0.0

        for cue in BankCardPickerMotion.script where time >= cue.at {
            let elapsed = time - cue.at
            switch cue.action {
            case .design:
                let fromSwap = swap.rounded()
                let target = fromSwap < 0.5 ? 1.0 : 0.0
                if target > 0.5 { slotB = cue.target } else { slotA = cue.target }
                let previous = design
                design = cue.target
                if elapsed < (reduced ? 200 : 420) {
                    let unit = max(0, min(1, elapsed / Double(reduced ? 200 : 420)))
                    let eased = reduced ? BankCardPickerMotion.easeOut(unit) : BankCardPickerMotion.easeInOut(unit)
                    swap = fromSwap + (target - fromSwap) * eased
                } else {
                    swap = target
                }
                if elapsed < (reduced ? 160 : 300) {
                    let unit = max(0, min(1, elapsed / Double(reduced ? 160 : 300)))
                    ring = Double(previous) + Double(cue.target - previous) * BankCardPickerMotion.spring(unit)
                } else {
                    ring = Double(cue.target)
                }
            case .plan:
                let previous = plan
                plan = cue.target
                if elapsed < (reduced ? 160 : 300) {
                    let unit = max(0, min(1, elapsed / Double(reduced ? 160 : 300)))
                    pill = Double(previous) + Double(cue.target - previous) * BankCardPickerMotion.spring(unit)
                } else {
                    pill = Double(cue.target)
                }
            }
        }
        return .init(design: design, plan: plan, slotA: slotA, slotB: slotB, swap: swap, ring: ring, pill: pill)
    }
}

private enum BankCardPickerMotion {
    static let resumeAfterMs = 3_500
    static let script = [
        BankCardPickerCue(at: 1_200, action: .design, target: 1),
        BankCardPickerCue(at: 3_000, action: .design, target: 2),
        BankCardPickerCue(at: 4_800, action: .design, target: 3),
        BankCardPickerCue(at: 6_800, action: .plan, target: 1),
        BankCardPickerCue(at: 8_600, action: .plan, target: 0),
        BankCardPickerCue(at: 10_200, action: .design, target: 0),
    ]

    static func easeOut(_ value: Double) -> Double { bezier(value, 0.23, 1, 0.32, 1) }
    static func easeInOut(_ value: Double) -> Double { bezier(value, 0.77, 0, 0.175, 1) }
    static func spring(_ value: Double) -> Double {
        guard value > 0 else { return 0 }
        guard value < 1 else { return 1 }
        let omega = 2 * Double.pi
        return 1 - exp(-omega * value) * (1 + omega * value)
    }

    private static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        func sample(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let inverse = 1 - t
            return 3 * inverse * inverse * t * a + 3 * inverse * t * t * b + t * t * t
        }
        var low = 0.0
        var high = 1.0
        for _ in 0..<12 {
            let middle = (low + high) / 2
            if sample(middle, x1, x2) < x { low = middle } else { high = middle }
        }
        return sample((low + high) / 2, y1, y2)
    }
}

private struct BankCardPickerCue {
    enum Action { case design, plan }
    let at: Double
    let action: Action
    let target: Int
}

private enum BankCardPickerTokens {
    static let cardCenterX: CGFloat = 197.5
    static let cardCenterY: CGFloat = 204
    static let cardWidth: CGFloat = 260
    static let cardHeight: CGFloat = 167
    static let stackCount = 14
    static let stackDX: CGFloat = -16.5
    static let stackDY: CGFloat = 15
    static let segmentSide: CGFloat = 25
    static let segmentInset: CGFloat = 2
}

private enum BankCardPickerPalette {
    static let page = Color(red: 0xF1 / 255, green: 0xF1 / 255, blue: 0xF1 / 255)
    static let dark = Color(red: 0x31 / 255, green: 0x37 / 255, blue: 0x3B / 255)
    static let grey = Color(red: 0x98 / 255, green: 0x98 / 255, blue: 0x98 / 255)
    static let hairline = Color(red: 0xDA / 255, green: 0xDA / 255, blue: 0xDA / 255)
    static let lime = Color(red: 0x64 / 255, green: 1, blue: 0x52 / 255)
}

private struct BankCardPickerDesign: Identifiable {
    let id: String
    let art: String?
    let thumbnail: String
    let designer: String
    let price: String
    let label: String
}

private struct BankCardPickerPlan: Identifiable {
    let id: String
    let label: String
    let fee: String
    let feeUnit: String
    let categories: String
}

private enum BankCardPickerData {
    static let designs = [
        BankCardPickerDesign(id: "blossom", art: nil, thumbnail: "BankCardPicker-thumb-blossom.jpg", designer: "Olga Paperova", price: "$4.99", label: "Pink blossoms on green"),
        BankCardPickerDesign(id: "meadow", art: "BankCardPicker-card-meadow.jpg", thumbnail: "BankCardPicker-thumb-meadow.jpg", designer: "Kenta Hayashi", price: "$4.99", label: "A cat watching the sea"),
        BankCardPickerDesign(id: "ridge", art: "BankCardPicker-card-ridge.jpg", thumbnail: "BankCardPicker-thumb-ridge.jpg", designer: "Inês Duarte", price: "$6.99", label: "Mountains under a setting planet"),
        BankCardPickerDesign(id: "valley", art: "BankCardPicker-card-valley.jpg", thumbnail: "BankCardPicker-thumb-valley.jpg", designer: "Tomasz Wilk", price: "$6.99", label: "A green valley with a striped moon"),
        BankCardPickerDesign(id: "silk", art: "BankCardPicker-card-silk.jpg", thumbnail: "BankCardPicker-thumb-silk.jpg", designer: "Ana Lucía Ríos", price: "Free", label: "Silver silk"),
    ]
    static let plans = [
        BankCardPickerPlan(id: "basic", label: "Basic", fee: "$0", feeUnit: "per month", categories: "3"),
        BankCardPickerPlan(id: "prime", label: "BloomPrime", fee: "$0", feeUnit: "with Prime", categories: "5"),
    ]
}

private enum BankCardPickerFonts {
    private static let registration: Void = {
        ["BankCardPicker-Inter-Regular.ttf", "BankCardPicker-Inter-Medium.ttf", "BankCardPicker-Inter-SemiBold.ttf"]
            .forEach { name in
                guard let url = Bundle.main.url(forResource: name, withExtension: nil) else { return }
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
    }()
    static func register() { _ = registration }
    static func regular(_ size: CGFloat) -> Font {
        register()
        return .custom("Inter24pt-Regular", fixedSize: size)
    }
    static func medium(_ size: CGFloat) -> Font {
        register()
        return .custom("Inter-Medium", fixedSize: size)
    }
    static func semibold(_ size: CGFloat) -> Font {
        register()
        return .custom("Inter-SemiBold", fixedSize: size)
    }
}

private struct BankCardPickerPlate: View {
    let depth: Int
    var body: some View {
        let amount = Double(depth - 1) / Double(BankCardPickerTokens.stackCount - 1)
        let front = SIMD3<Double>(124, 193, 142)
        let back = SIMD3<Double>(27, 94, 95)
        let color = front + (back - front) * amount
        RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(Color(red: color.x / 255, green: color.y / 255, blue: color.z / 255).opacity(0.94))
            .overlay {
                LinearGradient(
                    stops: [
                        .init(color: .white.opacity(0.55), location: 0),
                        .init(color: .white.opacity(0), location: 0.1),
                        .init(color: Color(red: 10 / 255, green: 60 / 255, blue: 48 / 255).opacity(0), location: 0.55),
                        .init(color: Color(red: 10 / 255, green: 60 / 255, blue: 48 / 255).opacity(0.55), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(.white.opacity(0.35), lineWidth: 0.5))
            .modifier(BankCardPickerCardTransform(depth: Double(depth)))
    }
}

private struct BankCardPickerCardSlot: View {
    let design: BankCardPickerDesign
    let frontness: Double
    var body: some View {
        let depth = 1 - frontness
        BankCardPickerCardArt(design: design)
            .opacity(max(0, min(1, (frontness - 0.25) / 0.5)))
            .zIndex(frontness > 0.5 ? 2 : 1)
            .shadow(color: Color(red: 3 / 255, green: 42 / 255, blue: 24 / 255).opacity(0.6), radius: 14, x: -4, y: 18)
            .modifier(BankCardPickerCardTransform(depth: depth))
    }
}

private struct BankCardPickerCardTransform: ViewModifier {
    let depth: Double
    func body(content: Content) -> some View {
        let skew = tan(10 * Double.pi / 180)
        return content
            .scaleEffect(x: 0.952, y: 1, anchor: .center)
            .projectionEffect(
                ProjectionTransform(
                    CGAffineTransform(
                        a: 1,
                        b: skew,
                        c: 0,
                        d: 1,
                        tx: 0,
                        ty: -skew * BankCardPickerTokens.cardWidth / 2
                    )
                )
            )
            .offset(
                x: BankCardPickerTokens.stackDX * depth,
                y: BankCardPickerTokens.stackDY * depth
            )
    }
}

private struct BankCardPickerCardArt: View {
    let design: BankCardPickerDesign
    var body: some View {
        ZStack(alignment: .topLeading) {
            if let art = design.art {
                BankCardPickerAssetImage(name: art).scaledToFill()
            } else {
                BankCardPickerBlossoms()
            }
            LinearGradient(
                colors: [.black.opacity(0), Color(red: 20 / 255, green: 20 / 255, blue: 30 / 255).opacity(0.16)],
                startPoint: UnitPoint(x: 0.85, y: 0),
                endPoint: UnitPoint(x: 0.05, y: 1)
            )
            BankCardPickerFurniture()
        }
        .frame(width: 260, height: 167, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private struct BankCardPickerBlossoms: View {
    private let flowers: [(CGFloat, CGFloat, CGFloat, Color, Color)] = [
        (207, 59, 46, Color(red: 0xEA / 255, green: 0xB6 / 255, blue: 0xC4 / 255), Color(red: 0xD1 / 255, green: 0x32 / 255, blue: 0x3C / 255)),
        (87, 46, 25, Color(red: 0xEA / 255, green: 0xB6 / 255, blue: 0xC4 / 255), Color(red: 0xD1 / 255, green: 0x32 / 255, blue: 0x3C / 255)),
        (23, 135, 44, Color(red: 0xEA / 255, green: 0xB6 / 255, blue: 0xC4 / 255), Color(red: 0xD1 / 255, green: 0x32 / 255, blue: 0x3C / 255)),
        (128, 123, 25, Color(red: 0xEA / 255, green: 0xB6 / 255, blue: 0xC4 / 255), Color(red: 0xD1 / 255, green: 0x32 / 255, blue: 0x3C / 255)),
        (157, 71, 17, Color(red: 0xF1 / 255, green: 0xEC / 255, blue: 0xD6 / 255), Color(red: 0xE2 / 255, green: 0xA8 / 255, blue: 0xB2 / 255)),
    ]
    var body: some View {
        Canvas { context, size in
            context.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .linearGradient(
                    Gradient(colors: [Color(red: 0x2F / 255, green: 0x8F / 255, blue: 0x40 / 255), Color(red: 0x4A / 255, green: 0xAF / 255, blue: 0x55 / 255)]),
                    startPoint: CGPoint(x: 0, y: size.height),
                    endPoint: CGPoint(x: size.width, y: 0)
                )
            )
            for (x, y, radius, petal, heart) in flowers {
                for index in 0..<5 {
                    let angle = Double(index) / 5 * Double.pi * 2 - Double.pi / 2
                    let center = CGPoint(x: x + cos(angle) * radius * 0.5, y: y + sin(angle) * radius * 0.5)
                    let petalRadius = radius * 0.52
                    context.fill(Path(ellipseIn: CGRect(x: center.x - petalRadius, y: center.y - petalRadius, width: petalRadius * 2, height: petalRadius * 2)), with: .color(petal))
                }
                context.fill(Path(ellipseIn: CGRect(x: x - radius * 0.2, y: y - radius * 0.2, width: radius * 0.4, height: radius * 0.4)), with: .color(heart))
            }
        }
    }
}

private struct BankCardPickerFurniture: View {
    var body: some View {
        Canvas { context, _ in
            let white = Color.white.opacity(0.88)
            context.stroke(Path(ellipseIn: CGRect(x: 12.5, y: 12.5, width: 25, height: 25)), with: .color(white), lineWidth: 2.4)
            var leaf = Path()
            leaf.move(to: CGPoint(x: 19.5, y: 30.5))
            leaf.addCurve(to: CGPoint(x: 31, y: 19), control1: CGPoint(x: 19, y: 22), control2: CGPoint(x: 24, y: 18.5))
            leaf.addCurve(to: CGPoint(x: 19.5, y: 30.5), control1: CGPoint(x: 31.5, y: 26), control2: CGPoint(x: 28, y: 31))
            context.fill(leaf, with: .color(white))
            context.fill(Path(roundedRect: CGRect(x: 26, y: 63, width: 32, height: 25), cornerRadius: 5), with: .color(Color(red: 0xEC / 255, green: 0xEB / 255, blue: 0xE6 / 255)))
            var chipLines = Path()
            for y in [71.0, 80.0] {
                chipLines.move(to: CGPoint(x: 26, y: y)); chipLines.addLine(to: CGPoint(x: 35, y: y))
                chipLines.move(to: CGPoint(x: 58, y: y)); chipLines.addLine(to: CGPoint(x: 49, y: y))
            }
            for x in [35.0, 49.0] {
                chipLines.move(to: CGPoint(x: x, y: 63)); chipLines.addLine(to: CGPoint(x: x, y: 88))
            }
            chipLines.move(to: CGPoint(x: 35, y: 75.5)); chipLines.addLine(to: CGPoint(x: 49, y: 75.5))
            context.stroke(chipLines, with: .color(Color(red: 0xB9 / 255, green: 0xB6 / 255, blue: 0xAD / 255)), lineWidth: 0.9)
            var bars = Path()
            bars.move(to: CGPoint(x: 202, y: 153)); bars.addLine(to: CGPoint(x: 207, y: 139)); bars.addLine(to: CGPoint(x: 217, y: 139)); bars.addLine(to: CGPoint(x: 212, y: 153)); bars.closeSubpath()
            bars.move(to: CGPoint(x: 216, y: 153)); bars.addLine(to: CGPoint(x: 224, y: 132)); bars.addLine(to: CGPoint(x: 234, y: 132)); bars.addLine(to: CGPoint(x: 226, y: 153)); bars.closeSubpath()
            bars.move(to: CGPoint(x: 230, y: 153)); bars.addLine(to: CGPoint(x: 239, y: 128)); bars.addLine(to: CGPoint(x: 249, y: 128)); bars.addLine(to: CGPoint(x: 240, y: 153)); bars.closeSubpath()
            context.fill(bars, with: .color(.white))
        }
    }
}

private struct BankCardPickerInfoRow: View {
    let label: String
    let value: String
    var unit: String?
    init(label: String, value: String, unit: String? = nil) {
        self.label = label
        self.value = value
        self.unit = unit
    }
    var body: some View {
        HStack(alignment: .top) {
            Text(label)
                .font(BankCardPickerFonts.regular(17))
                .lineSpacing(5)
                .foregroundStyle(.black)
            Spacer()
            VStack(alignment: .trailing, spacing: 0) {
                Text(value).font(BankCardPickerFonts.semibold(22)).monospacedDigit()
                if let unit { Text(unit).font(BankCardPickerFonts.medium(15)) }
            }
            .foregroundStyle(.black)
        }
        .padding(.horizontal, 17)
    }
}

private struct BankCardPickerAssetImage: View {
    let name: String
    var body: some View {
        if let image = UIImage(named: name) {
            Image(uiImage: image).resizable()
        } else {
            Color.gray
        }
    }
}

private struct BankCardPickerHeroField: View {
    private static let image: UIImage = {
        let width = 8
        let height = 14
        var pixels = BankCardPickerHeroColours.rows.flatMap { row in
            row.map { value -> UInt32 in
                let red = (value >> 16) & 0xFF
                let green = (value >> 8) & 0xFF
                let blue = value & 0xFF
                return (0xFF << 24) | (blue << 16) | (green << 8) | red
            }
        }
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let image = pixels.withUnsafeMutableBytes { bytes -> CGImage? in
            guard let context = CGContext(
                data: bytes.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return nil }
            return context.makeImage()
        }
        return image.map(UIImage.init(cgImage:)) ?? UIImage()
    }()

    var body: some View {
        GeometryReader { proxy in
            Image(uiImage: Self.image)
                .resizable()
                .interpolation(.medium)
                .frame(width: proxy.size.width + 96, height: proxy.size.height + 96)
                .offset(x: -48, y: -48)
                .blur(radius: 14, opaque: true)
        }
        .clipped()
    }
}

private enum BankCardPickerHeroColours {
    static let rows: [[UInt32]] = [
        [0x0B1313,0x0B1213,0x0B1212,0x0A1112,0x0A1112,0x0A1011,0x0A1011,0x0A1011],
        [0x0F1E1D,0x0F1D1C,0x0E1C1C,0x0E1B1B,0x0E1B1A,0x0D1A1A,0x0D1919,0x0D1919],
        [0x162D2C,0x152C2B,0x152B2A,0x142A29,0x142827,0x132726,0x132625,0x132625],
        [0x1F4241,0x1E4140,0x1E403F,0x1D3E3D,0x1C3D3C,0x1C3B3A,0x1B3A39,0x1B3A39],
        [0x245F59,0x245D58,0x255C57,0x255A56,0x255854,0x265653,0x265552,0x265452],
        [0x1F6B5D,0x226C60,0x256D63,0x296F66,0x2C7069,0x30726B,0x32736D,0x33736E],
        [0x1E7666,0x24796B,0x2A7D70,0x318176,0x37857B,0x3E8980,0x428C83,0x448D85],
        [0x1F7D6C,0x288373,0x31897A,0x3B9082,0x45978A,0x4E9E92,0x54A297,0x57A499],
        [0x207C6B,0x2C8575,0x388F7F,0x46998A,0x53A395,0x60ADA0,0x68B4A7,0x6CB7AA],
        [0x217470,0x30807B,0x3F8D87,0x509B94,0x60A9A0,0x70B6AC,0x7ABFB3,0x7FC3B7],
        [0x256466,0x357473,0x468582,0x589792,0x6AA9A1,0x7BBAB0,0x86C5B9,0x8CCBBE],
        [0x2B6063,0x3A7071,0x4A827F,0x5C948F,0x6DA69F,0x7EB8AD,0x89C3B7,0x8EC9BC],
        [0x2B6063,0x3A7070,0x4A817F,0x5A938F,0x6BA59E,0x7BB6AD,0x85C1B6,0x8AC7BB],
        [0x326368,0x407275,0x4E8283,0x5E9391,0x6EA4A0,0x7CB4AE,0x86BFB6,0x8BC4BB],
    ]
}

private struct BankCardPickerBackGlyph: View {
    var body: some View {
        Path { path in
            path.move(to: CGPoint(x: 10.3, y: 1))
            path.addLine(to: CGPoint(x: 1.3, y: 10))
            path.addLine(to: CGPoint(x: 10.3, y: 19))
        }
        .stroke(.white, style: .init(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
        .frame(width: 11.3, height: 19.7)
    }
}

private struct BankCardPickerShareGlyph: View {
    var body: some View {
        Path { path in
            path.move(to: CGPoint(x: 11, y: 0))
            path.addLine(to: CGPoint(x: 19, y: 7.5))
            path.addLine(to: CGPoint(x: 11, y: 15))
            path.addLine(to: CGPoint(x: 11, y: 10.5))
            path.addCurve(to: CGPoint(x: 0, y: 15), control1: CGPoint(x: 6, y: 10.5), control2: CGPoint(x: 2, y: 12))
            path.addCurve(to: CGPoint(x: 11, y: 5), control1: CGPoint(x: 1, y: 8), control2: CGPoint(x: 5, y: 5))
            path.closeSubpath()
        }
        .fill(.white)
        .frame(width: 19, height: 15)
    }
}

private struct BankCardPickerFlagGlyph: View {
    var body: some View {
        Path { path in
            path.move(to: .zero)
            path.addLine(to: CGPoint(x: 14, y: 0))
            path.addLine(to: CGPoint(x: 10, y: 6.5))
            path.addLine(to: CGPoint(x: 14, y: 13))
            path.addLine(to: CGPoint(x: 0, y: 13))
            path.closeSubpath()
        }
        .fill(BankCardPickerPalette.lime)
    }
}

private struct BankCardPickerCheckGlyph: View {
    var body: some View {
        Path { path in
            path.move(to: CGPoint(x: 3, y: 12.5))
            path.addLine(to: CGPoint(x: 11, y: 20))
            path.addLine(to: CGPoint(x: 27, y: 3.5))
        }
        .stroke(BankCardPickerPalette.lime, style: .init(lineWidth: 5, lineCap: .round, lineJoin: .round))
        .frame(width: 30, height: 24)
    }
}

private struct BankCardPickerShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private extension Color {
    static func bankCardPicker(hex: UInt32) -> Color {
        Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
