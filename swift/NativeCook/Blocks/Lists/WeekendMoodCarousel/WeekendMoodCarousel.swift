import SwiftUI
import UIKit

struct WeekendMoodSelection {
    let index: Int
    let label: String
    let characterIndex: Int
}

struct WeekendMoodCarousel: View {
    static let loop: Double = WeekendMoodCarouselMetrics.loopMilliseconds / 1_000

    let autoPlay: Bool
    let onBack: (() -> Void)?
    let onSave: ((WeekendMoodSelection) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var autoplayStartedAt: Date?
    @State private var manual = false
    @State private var manualVisual = WeekendMoodCarouselManualVisual(frame: .initial)
    @State private var manualTracks = WeekendMoodCarouselManualTracks()
    @State private var manualSelectedChip = WeekendMoodCarouselMetrics.startChip
    @State private var manualSelectedCharacter = WeekendMoodCarouselMetrics.startCharacter
    @State private var manualPreviousCharacter = WeekendMoodCarouselFaceKind.social.rawValue
    @State private var manualAgeOrigin = 1_000.0
    @State private var manualAgeBegan = Date()
    @State private var manualPhaseOrigin = 0.0
    @State private var manualPhaseBegan = Date()
    @State private var dragTarget: WeekendMoodCarouselDragTarget?
    @State private var dragStartChip = 0.0
    @State private var dragStartCharacter = 0.0
    @State private var dragActivated = false
    @State private var resumeGeneration = 0
    @State private var selectionGeneration = 0
    @State private var manualFrameGeneration = 0
    @State private var manualNeedsFrames = false
    @State private var selectionStartedDuringInteraction = false
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private let centers: [Double]
    private let widths: [Double]

    init(
        autoPlay: Bool = false,
        onBack: (() -> Void)? = nil,
        onSave: ((WeekendMoodSelection) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.onBack = onBack
        self.onSave = onSave
        WeekendMoodCarouselAssets.registerFonts()
        self.widths = WeekendMoodCarouselMetrics.chipWidths()
        self.centers = WeekendMoodCarouselMetrics.chipCenters()
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            TimelineView(.animation(paused: !(autoPlay && !reduceMotion) && !manualNeedsFrames)) { timeline in
                content(size: size, date: timeline.date)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: WeekendMoodCarouselMetrics.ink).ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear {
            let now = Date.now
            autoplayStartedAt = now
            resetManualState()
            haptic.prepare()
        }
        .onChange(of: reduceMotion) { _, reduced in
            autoplayStartedAt = .now
            resumeGeneration += 1
            if reduced { settleForReducedMotion() }
        }
        .task(id: resumeGeneration) {
            guard autoPlay, !reduceMotion, resumeGeneration > 0 else { return }
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            guard !Task.isCancelled, !reduceMotion, dragTarget == nil else { return }
            returnToRest()
        }
    }

    private func content(size: CGSize, date: Date) -> some View {
        let frame = frame(at: date)
        let extra = Double(size.height) - WeekendMoodCarouselMetrics.pinHeight
        let panelHeight = WeekendMoodCarouselMetrics.panelHeight + extra
        let fromBottom: (Double) -> CGFloat = { CGFloat($0 + extra) }
        let panel = ZStack(alignment: .topLeading) {
            Canvas(rendersAsynchronously: false) { context, canvasSize in
                WeekendMoodCarouselScene.panel(&context, size: canvasSize, frame: frame, reduced: reduceMotion)
            }
            .frame(width: size.width, height: CGFloat(panelHeight), alignment: .topLeading)
            .opacity(1 - frame.veil)

            Button(action: { onBack?() }) {
                WeekendMoodCarouselBackArrow()
                    .stroke(Color(hex: WeekendMoodCarouselMetrics.buttonInk), style: StrokeStyle(lineWidth: CGFloat(WeekendMoodCarouselMetrics.arrowStroke), lineCap: .round, lineJoin: .round))
                    .frame(width: 18, height: 18)
                    .frame(width: CGFloat(WeekendMoodCarouselMetrics.buttonSize), height: CGFloat(WeekendMoodCarouselMetrics.buttonSize))
                    .overlay(Circle().stroke(Color.black.opacity(0.4), lineWidth: CGFloat(WeekendMoodCarouselMetrics.buttonRing)))
            }
            .buttonStyle(.plain)
            .contentShape(Circle())
            .position(x: CGFloat(WeekendMoodCarouselMetrics.backLeft + WeekendMoodCarouselMetrics.buttonSize / 2), y: CGFloat(WeekendMoodCarouselMetrics.buttonTop + WeekendMoodCarouselMetrics.buttonSize / 2))
            .accessibilityLabel("Back")
            .disabled(onBack == nil)

            Button(action: { onSave?(selection(at: .now)) }) {
                Text("Save")
                    .font(.custom("HelveticaNeue", fixedSize: CGFloat(WeekendMoodCarouselMetrics.saveFont)))
                    .foregroundStyle(Color(hex: WeekendMoodCarouselMetrics.buttonInk))
                    .frame(width: CGFloat(WeekendMoodCarouselMetrics.saveWidth), height: CGFloat(WeekendMoodCarouselMetrics.buttonSize))
                    .overlay(Capsule().stroke(Color.black.opacity(0.4), lineWidth: CGFloat(WeekendMoodCarouselMetrics.buttonRing)))
            }
            .buttonStyle(.plain)
            .contentShape(Capsule())
            .position(x: size.width - CGFloat(WeekendMoodCarouselMetrics.saveRight + WeekendMoodCarouselMetrics.saveWidth / 2), y: CGFloat(WeekendMoodCarouselMetrics.buttonTop + WeekendMoodCarouselMetrics.buttonSize / 2))
            .accessibilityLabel("Save mood")
            .disabled(onSave == nil)
        }
        .frame(width: size.width, height: CGFloat(panelHeight), alignment: .topLeading)
        .clipShape(
            UnevenRoundedRectangle(
                cornerRadii: .init(
                    topLeading: 0,
                    bottomLeading: CGFloat(WeekendMoodCarouselMetrics.panelRadius),
                    bottomTrailing: CGFloat(WeekendMoodCarouselMetrics.panelRadius),
                    topTrailing: 0
                ),
                style: .continuous
            )
        )
        .contentShape(Rectangle())

        return ZStack(alignment: .topLeading) {
            Color(hex: WeekendMoodCarouselMetrics.ink)
            panel
            Text("Select your\nweekend mood")
                .font(.custom(WeekendMoodCarouselAssets.titleFontName, fixedSize: CGFloat(WeekendMoodCarouselMetrics.titleFontSize)))
                .lineSpacing(titleLineSpacing)
                .multilineTextAlignment(.center)
                .foregroundStyle(Color(hex: WeekendMoodCarouselMetrics.white))
                .frame(width: size.width, height: 94, alignment: .top)
                .offset(x: CGFloat(WeekendMoodCarouselMetrics.titleShiftX), y: fromBottom(WeekendMoodCarouselMetrics.titleTop))
                .accessibilityAddTraits(.isHeader)

            Canvas(rendersAsynchronously: false) { context, canvasSize in
                WeekendMoodCarouselScene.ruler(
                    &context,
                    size: canvasSize,
                    chipX: frame.chipX,
                    anchor: centers[weekendSafe: WeekendMoodCarouselMetrics.startChip] ?? 0,
                    characterPosition: frame.characterPosition
                )
            }
            .frame(width: size.width, height: CGFloat(WeekendMoodCarouselMetrics.rulerHeight), alignment: .topLeading)
            .offset(y: fromBottom(WeekendMoodCarouselMetrics.rulerTop))
            .allowsHitTesting(false)

            HStack(spacing: CGFloat(WeekendMoodCarouselMetrics.chipGap)) {
                ForEach(Array(WeekendMoodCarouselMetrics.chips.enumerated()), id: \.element.id) { index, chip in
                    chipView(chip, index: index, width: widths[index], frame: frame)
                }
            }
            .offset(x: CGFloat(WeekendMoodCarouselMetrics.chipsCenterX + (Double(size.width) - WeekendMoodCarouselMetrics.screenWidth) / 2 - frame.chipX))
            .frame(height: CGFloat(WeekendMoodCarouselMetrics.chipsHeight), alignment: .topLeading)
            .opacity(1 - frame.veil)
            .fixedSize(horizontal: true, vertical: false)
            .frame(width: size.width, height: CGFloat(WeekendMoodCarouselMetrics.chipsHeight + 16), alignment: .topLeading)
            .offset(y: fromBottom(WeekendMoodCarouselMetrics.chipsTop) - 8)
            .clipped()
            .contentShape(Rectangle())

            WeekendMoodCarouselTouchView(
                panelHeight: CGFloat(panelHeight),
                chipsRect: CGRect(
                    x: 0,
                    y: fromBottom(WeekendMoodCarouselMetrics.chipsTop) - 8,
                    width: size.width,
                    height: CGFloat(WeekendMoodCarouselMetrics.chipsHeight + 16)
                ),
                backRect: CGRect(
                    x: CGFloat(WeekendMoodCarouselMetrics.backLeft),
                    y: CGFloat(WeekendMoodCarouselMetrics.buttonTop),
                    width: CGFloat(WeekendMoodCarouselMetrics.buttonSize),
                    height: CGFloat(WeekendMoodCarouselMetrics.buttonSize)
                ),
                saveRect: CGRect(
                    x: size.width - CGFloat(WeekendMoodCarouselMetrics.saveRight + WeekendMoodCarouselMetrics.saveWidth),
                    y: CGFloat(WeekendMoodCarouselMetrics.buttonTop),
                    width: CGFloat(WeekendMoodCarouselMetrics.saveWidth),
                    height: CGFloat(WeekendMoodCarouselMetrics.buttonSize)
                ),
                onBegan: { point, inChips in touchBegan(at: point, inChips: inChips, width: size.width) },
                onChanged: { translation, location in touchChanged(translation: translation, location: location, width: size.width) },
                onEnded: { translation, velocity, location, completed, tapWithinMaxDistance in
                    touchEnded(
                        translation: translation,
                        velocity: velocity,
                        location: location,
                        completed: completed,
                        tapWithinMaxDistance: tapWithinMaxDistance,
                        width: size.width
                    )
                }
            )
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private var titleLineSpacing: CGFloat {
        let font = UIFont(name: WeekendMoodCarouselAssets.titleFontName, size: CGFloat(WeekendMoodCarouselMetrics.titleFontSize))
        return CGFloat(WeekendMoodCarouselMetrics.titleLineHeight) - (font?.lineHeight ?? CGFloat(WeekendMoodCarouselMetrics.titleFontSize * 1.15))
    }

    private func chipView(_ chip: WeekendMoodCarouselChip, index: Int, width: Double, frame: WeekendMoodCarouselFrame) -> some View {
        let mood = WeekendMoodCarouselMetrics.moods[chip.character]
        let selected = frame.selectedChip == index
        let amount = selected ? frame.fill : 0
        let foreground = weekendBlendColor(0xF2F4F4, 0x24201F, selected ? frame.ink : 0)
        return Text(chip.label)
            .font(.custom(WeekendMoodCarouselAssets.chipFontName, fixedSize: CGFloat(WeekendMoodCarouselMetrics.chipFontSize)))
            .tracking(CGFloat(WeekendMoodCarouselMetrics.chipLetterSpacing))
            .foregroundStyle(foreground)
            .lineLimit(1)
            .padding(.horizontal, CGFloat(WeekendMoodCarouselMetrics.chipPadding))
            .frame(width: CGFloat(width), height: CGFloat(WeekendMoodCarouselMetrics.chipsHeight))
            .background(
                LinearGradient(
                    colors: [Color(hex: mood.chip.0), Color(hex: mood.chip.1), Color(hex: mood.chip.2)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .opacity(amount)
            )
            .clipShape(Capsule())
            .overlay(
                Capsule().stroke(
                    Color.rgba(255, 255, 255, 0.3).opacity(selected ? 1 - frame.fill : 1),
                    lineWidth: CGFloat(WeekendMoodCarouselMetrics.chipBorder)
                )
            )
            .accessibilityLabel(chip.label)
            .accessibilityAddTraits(selected && frame.fill > 0.5 ? [.isButton, .isSelected] : .isButton)
            .accessibilityAction { activateChip(index) }
    }

    private func selection(at date: Date) -> WeekendMoodSelection {
        let index = min(max(frame(at: date).selectedChip, 0), WeekendMoodCarouselMetrics.chips.count - 1)
        let chip = WeekendMoodCarouselMetrics.chips[index]
        return WeekendMoodSelection(index: index, label: chip.label, characterIndex: chip.character)
    }

    private func activateChip(_ index: Int) {
        beginInteraction(.chips, date: .now)
        guard index != manualSelectedChip else {
            finishInteraction()
            return
        }
        animateManual(at: .now, fill: 0, ink: 0, duration: WeekendMoodCarouselMetrics.fillOut, curve: .timing)
        let character = WeekendMoodCarouselMetrics.chips[index].character
        if reduceMotion {
            selectReduced(chip: index, character: character)
        } else {
            select(chip: index, character: character, fromDrag: false, velocity: 0)
        }
        finishInteraction()
    }

    private func frame(at date: Date) -> WeekendMoodCarouselFrame {
        if manual {
            let sampled = manualTracks.sample(manualVisual, at: date)
            return WeekendMoodCarouselFrame(
                chipX: sampled.chipX,
                characterPosition: sampled.characterPosition,
                fill: sampled.fill,
                selectedChip: manualSelectedChip,
                selectedCharacter: manualSelectedCharacter,
                previousCharacter: manualPreviousCharacter,
                age: manualAgeOrigin + date.timeIntervalSince(manualAgeBegan) * 1_000,
                veil: sampled.veil,
                ink: sampled.ink,
                phase: manualPhaseOrigin + date.timeIntervalSince(manualPhaseBegan) * 1_000
            )
        }
        let start = autoplayStartedAt ?? date
        let elapsed = autoPlay && !reduceMotion ? date.timeIntervalSince(start) * 1_000 : 0
        return weekendSampleFrame(at: elapsed, centers: centers, reduced: reduceMotion)
    }

    private func resetManualState() {
        let metrics = WeekendMoodCarouselMetrics.self
        manual = false
        manualVisual = WeekendMoodCarouselManualVisual(
            chipX: (centers[weekendSafe: metrics.startChip] ?? 0) - metrics.startDx,
            characterPosition: Double(metrics.startCharacter),
            fill: 1,
            ink: 1,
            veil: 0
        )
        manualTracks = WeekendMoodCarouselManualTracks()
        manualSelectedChip = metrics.startChip
        manualSelectedCharacter = metrics.startCharacter
        manualPreviousCharacter = WeekendMoodCarouselFaceKind.social.rawValue
        manualAgeOrigin = 1_000
        manualPhaseOrigin = 0
        manualAgeBegan = .now
        manualPhaseBegan = .now
        dragTarget = nil
        dragActivated = false
        selectionStartedDuringInteraction = false
        manualNeedsFrames = false
        manualFrameGeneration += 1
    }

    private func settleForReducedMotion() {
        guard manual else { return }
        selectionGeneration += 1
        setManualValues(
            chipX: centers[weekendSafe: manualSelectedChip] ?? manualVisual.chipX,
            characterPosition: Double(manualSelectedCharacter),
            fill: 1,
            ink: 1,
            veil: 0
        )
        manualAgeOrigin = 9_999
        manualAgeBegan = .now
        manualNeedsFrames = false
        manualFrameGeneration += 1
    }

    private func beginInteraction(_ target: WeekendMoodCarouselDragTarget, date: Date) {
        guard dragTarget == nil else { return }
        let current = frame(at: date)
        manual = true
        manualVisual = WeekendMoodCarouselManualVisual(frame: current)
        manualTracks = WeekendMoodCarouselManualTracks()
        manualSelectedChip = current.selectedChip
        manualSelectedCharacter = current.selectedCharacter
        manualPreviousCharacter = current.previousCharacter
        manualAgeOrigin = current.age
        manualPhaseOrigin = current.phase
        manualAgeBegan = date
        manualPhaseBegan = date
        dragTarget = target
        dragActivated = false
        selectionStartedDuringInteraction = false
        manualNeedsFrames = true
        manualFrameGeneration += 1
        // Every new touch invalidates a pending delayed fill, including a tap
        // that subsequently turns into a cancelled gesture.
        selectionGeneration += 1
        resumeGeneration += 1
    }

    private func touchBegan(at _: CGPoint, inChips: Bool, width _: CGFloat) {
        let target: WeekendMoodCarouselDragTarget = inChips ? .chips : .panel
        beginInteraction(target, date: .now)
        if target == .panel {
            dragStartCharacter = manualVisual.characterPosition
        } else {
            dragStartChip = manualVisual.chipX
        }
    }

    private func touchChanged(translation: CGFloat, location _: CGPoint, width _: CGFloat) {
        guard let target = dragTarget else { return }
        let horizontal = Double(translation)
        if !dragActivated, abs(horizontal) >= 8 {
            dragActivated = true
            animateManual(
                at: .now,
                fill: 0,
                ink: 0,
                duration: WeekendMoodCarouselMetrics.fillOut,
                curve: .timing
            )
        }
        guard dragActivated else { return }
        switch target {
        case .panel:
            let maximum = Double(WeekendMoodCarouselMetrics.moods.count - 1)
            var position = dragStartCharacter - horizontal / WeekendMoodCarouselMetrics.characterSpacing
            if position < 0 { position *= 0.35 }
            if position > maximum { position = maximum + (position - maximum) * 0.35 }
            setManualCharacterPosition(position, at: .now)
        case .chips:
            let minimum = centers.first ?? 0
            let maximum = centers.last ?? 0
            var position = dragStartChip - horizontal
            if position < minimum { position = minimum - (minimum - position) * 0.35 }
            if position > maximum { position = maximum + (position - maximum) * 0.35 }
            setManualChipX(position, at: .now)
        }
    }

    private func manualVisual(at date: Date) -> WeekendMoodCarouselManualVisual {
        manualTracks.sample(manualVisual, at: date)
    }

    private func animateManual(
        at date: Date,
        chipX: Double? = nil,
        characterPosition: Double? = nil,
        chipVelocity: Double = 0,
        characterVelocity: Double = 0,
        fill: Double? = nil,
        ink: Double? = nil,
        veil: Double? = nil,
        duration: Double,
        curve: WeekendMoodCarouselTrackCurve
    ) {
        let current = manualVisual(at: date)
        if let chipX {
            let distance = chipX - current.chipX
            let normalizedVelocity = abs(distance) > 0.001 ? chipVelocity / distance : 0
            manualTracks.chipX = WeekendMoodCarouselScalarTrack(
                from: current.chipX,
                to: chipX,
                startedAt: date,
                duration: duration,
                curve: curve,
                initialVelocity: normalizedVelocity
            )
        }
        if let characterPosition {
            let distance = characterPosition - current.characterPosition
            let normalizedVelocity = abs(distance) > 0.001 ? characterVelocity / distance : 0
            manualTracks.characterPosition = WeekendMoodCarouselScalarTrack(
                from: current.characterPosition,
                to: characterPosition,
                startedAt: date,
                duration: duration,
                curve: curve,
                initialVelocity: normalizedVelocity
            )
        }
        if let fill {
            manualTracks.fill = WeekendMoodCarouselScalarTrack(from: current.fill, to: fill, startedAt: date, duration: duration, curve: curve, initialVelocity: 0)
        }
        if let ink {
            manualTracks.ink = WeekendMoodCarouselScalarTrack(from: current.ink, to: ink, startedAt: date, duration: duration, curve: curve, initialVelocity: 0)
        }
        if let veil {
            manualTracks.veil = WeekendMoodCarouselScalarTrack(from: current.veil, to: veil, startedAt: date, duration: duration, curve: curve, initialVelocity: 0)
        }
    }

    private func setManualCharacterPosition(_ position: Double, at _: Date) {
        manualVisual.characterPosition = position
        manualTracks.characterPosition = nil
    }

    private func setManualChipX(_ position: Double, at _: Date) {
        manualVisual.chipX = position
        manualTracks.chipX = nil
    }

    private func setManualValues(
        chipX: Double? = nil,
        characterPosition: Double? = nil,
        fill: Double? = nil,
        ink: Double? = nil,
        veil: Double? = nil
    ) {
        if let chipX {
            manualVisual.chipX = chipX
            manualTracks.chipX = nil
        }
        if let characterPosition {
            manualVisual.characterPosition = characterPosition
            manualTracks.characterPosition = nil
        }
        if let fill {
            manualVisual.fill = fill
            manualTracks.fill = nil
        }
        if let ink {
            manualVisual.ink = ink
            manualTracks.ink = nil
        }
        if let veil {
            manualVisual.veil = veil
            manualTracks.veil = nil
        }
    }

    private func touchEnded(
        translation _: CGFloat,
        velocity: CGFloat,
        location: CGPoint,
        completed: Bool,
        tapWithinMaxDistance: Bool,
        width: CGFloat
    ) {
        guard let target = dragTarget else { return }
        defer { finishInteraction() }
        guard completed else { return }
        let horizontalVelocity = Double(velocity)
        guard dragActivated else {
            guard target == .chips else { return }
            guard tapWithinMaxDistance else { return }
            let x = manualVisual(at: .now).chipX + Double(location.x) - WeekendMoodCarouselMetrics.chipsCenterX - (Double(width) - WeekendMoodCarouselMetrics.screenWidth) / 2
            let chip = nearestChip(x)
            if abs((centers[weekendSafe: chip] ?? 0) - x) < 80, chip != manualSelectedChip {
                animateManual(
                    at: .now,
                    fill: 0,
                    ink: 0,
                    duration: WeekendMoodCarouselMetrics.fillOut,
                    curve: .timing
                )
                let character = WeekendMoodCarouselMetrics.chips[chip].character
                if reduceMotion {
                    selectReduced(chip: chip, character: character)
                } else {
                    select(chip: chip, character: character, fromDrag: false, velocity: 0)
                }
            }
            return
        }
        switch target {
        case .panel:
            let maximum = WeekendMoodCarouselMetrics.moods.count - 1
            let projected = manualVisual(at: .now).characterPosition - (horizontalVelocity * 0.2) / WeekendMoodCarouselMetrics.characterSpacing
            let character = min(max(Int(projected.rounded()), 0), maximum)
            let chip = WeekendMoodCarouselMetrics.chipOfCharacter[character]
            if reduceMotion {
                selectReduced(chip: chip, character: character)
            } else {
                select(chip: chip, character: character, fromDrag: true, velocity: -horizontalVelocity / WeekendMoodCarouselMetrics.characterSpacing, characterSpring: true)
            }
        case .chips:
            let target = nearestChip(manualVisual(at: .now).chipX - horizontalVelocity * 0.18)
            if reduceMotion {
                selectReduced(chip: target, character: WeekendMoodCarouselMetrics.chips[target].character)
            } else {
                select(chip: target, character: WeekendMoodCarouselMetrics.chips[target].character, fromDrag: true, velocity: horizontalVelocity, characterSpring: false)
            }
        }
    }

    private func nearestChip(_ x: Double) -> Int {
        centers.indices.min(by: { abs(centers[$0] - x) < abs(centers[$1] - x) }) ?? 0
    }

    private func select(chip: Int, character: Int, fromDrag: Bool, velocity: Double, characterSpring: Bool = false) {
        selectionStartedDuringInteraction = true
        if manualSelectedCharacter != character { manualPreviousCharacter = manualSelectedCharacter }
        manualSelectedChip = chip
        manualSelectedCharacter = character
        let lightAt = characterSpring ? 420.0 : fromDrag ? 380.0 : WeekendMoodCarouselMetrics.userScroll * 0.9
        manualAgeOrigin = -lightAt
        manualAgeBegan = .now
        let targetChip = centers[weekendSafe: chip] ?? 0
        let now = Date.now
        animateManual(
            at: now,
            chipX: targetChip,
            chipVelocity: fromDrag && !characterSpring ? -velocity : 0,
            duration: fromDrag && !characterSpring ? WeekendMoodCarouselMetrics.snapSpringDuration : WeekendMoodCarouselMetrics.userScroll,
            curve: fromDrag && !characterSpring ? .spring : .scroll
        )
        animateManual(
            at: now,
            characterPosition: Double(character),
            characterVelocity: fromDrag && characterSpring ? velocity : 0,
            duration: fromDrag && characterSpring ? WeekendMoodCarouselMetrics.snapSpringDuration : WeekendMoodCarouselMetrics.userScroll,
            curve: fromDrag && characterSpring ? .spring : .scroll
        )
        let chipDuration = fromDrag && !characterSpring
            ? WeekendMoodCarouselMetrics.snapSpringDuration
            : WeekendMoodCarouselMetrics.userScroll
        let characterDuration = fromDrag && characterSpring
            ? WeekendMoodCarouselMetrics.snapSpringDuration
            : WeekendMoodCarouselMetrics.userScroll
        let movementDuration = max(chipDuration, characterDuration)
        let expressionDuration: Double
        if character == WeekendMoodCarouselFaceKind.sleepy.rawValue {
            expressionDuration = max(
                WeekendMoodCarouselMetrics.fillIn,
                WeekendMoodCarouselMetrics.zzzStagger * 2 + WeekendMoodCarouselMetrics.zzzPop
            )
        } else if character == WeekendMoodCarouselFaceKind.foodie.rawValue {
            expressionDuration = max(
                WeekendMoodCarouselMetrics.fillIn,
                WeekendMoodCarouselMetrics.droolDelay + WeekendMoodCarouselMetrics.drool
            )
        } else {
            expressionDuration = WeekendMoodCarouselMetrics.fillIn
        }
        let continuous = character == WeekendMoodCarouselFaceKind.binge.rawValue ||
            character == WeekendMoodCarouselFaceKind.shopping.rawValue ||
            character == WeekendMoodCarouselFaceKind.social.rawValue
        keepManualFrames(
            for: max(movementDuration, lightAt + expressionDuration),
            continuously: continuous
        )
        let generation = selectionGeneration + 1
        selectionGeneration = generation
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(lightAt * 1_000_000))
            guard !Task.isCancelled, generation == selectionGeneration else { return }
            animateManual(
                at: .now,
                fill: 1,
                ink: 1,
                duration: WeekendMoodCarouselMetrics.fillIn,
                curve: .timing
            )
        }
        haptic.impactOccurred()
        haptic.prepare()
    }

    private func selectReduced(chip: Int, character: Int) {
        selectionStartedDuringInteraction = true
        if manualSelectedCharacter != character { manualPreviousCharacter = manualSelectedCharacter }
        manualSelectedChip = chip
        manualSelectedCharacter = character
        let generation = selectionGeneration + 1
        selectionGeneration = generation
        animateManual(
            at: .now,
            veil: 1,
            duration: WeekendMoodCarouselMetrics.reducedFade / 2,
            curve: .timing
        )
        keepManualFrames(for: WeekendMoodCarouselMetrics.reducedFade, continuously: false)
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(WeekendMoodCarouselMetrics.reducedFade / 2 * 1_000_000))
            guard !Task.isCancelled, generation == selectionGeneration else { return }
            setManualValues(
                chipX: centers[weekendSafe: chip] ?? 0,
                characterPosition: Double(character),
                fill: 1,
                ink: 1
            )
            manualAgeOrigin = 9_999
            manualAgeBegan = .now
            animateManual(
                at: .now,
                veil: 0,
                duration: WeekendMoodCarouselMetrics.reducedFade / 2,
                curve: .timing
            )
        }
        haptic.impactOccurred()
        haptic.prepare()
    }

    private func finishInteraction() {
        if !selectionStartedDuringInteraction, !autoPlay {
            let continuous = !reduceMotion && (
                manualSelectedCharacter == WeekendMoodCarouselFaceKind.binge.rawValue ||
                    manualSelectedCharacter == WeekendMoodCarouselFaceKind.shopping.rawValue ||
                    manualSelectedCharacter == WeekendMoodCarouselFaceKind.social.rawValue
            )
            keepManualFrames(for: WeekendMoodCarouselMetrics.fillOut + 80, continuously: continuous)
        }
        dragTarget = nil
        dragActivated = false
        resumeGeneration += 1
    }

    private func keepManualFrames(for milliseconds: Double, continuously: Bool) {
        manualNeedsFrames = true
        manualFrameGeneration += 1
        let generation = manualFrameGeneration
        guard !continuously else { return }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(milliseconds * 1_000_000))
            guard !Task.isCancelled, generation == manualFrameGeneration else { return }
            manualNeedsFrames = false
        }
    }

    private func returnToRest() {
        guard autoPlay, !reduceMotion, dragTarget == nil else { return }
        let now = Date.now
        animateManual(
            at: now,
            fill: 0,
            ink: 0,
            duration: WeekendMoodCarouselMetrics.fillOut,
            curve: .timing
        )
        let generation = selectionGeneration + 1
        selectionGeneration = generation
        animateManual(
            at: now,
            chipX: centers[weekendSafe: WeekendMoodCarouselMetrics.startChip] ?? 0,
            characterPosition: Double(WeekendMoodCarouselMetrics.startCharacter),
            duration: WeekendMoodCarouselMetrics.userScroll,
            curve: .scroll
        )
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(WeekendMoodCarouselMetrics.userScroll * 1_000_000))
            guard !Task.isCancelled, generation == selectionGeneration else { return }
            manual = false
            manualVisual = WeekendMoodCarouselManualVisual(
                chipX: (centers[weekendSafe: WeekendMoodCarouselMetrics.startChip] ?? 0) - WeekendMoodCarouselMetrics.startDx,
                characterPosition: Double(WeekendMoodCarouselMetrics.startCharacter),
                fill: 1,
                ink: 1,
                veil: 0
            )
            manualTracks = WeekendMoodCarouselManualTracks()
            manualSelectedChip = WeekendMoodCarouselMetrics.startChip
            manualSelectedCharacter = WeekendMoodCarouselMetrics.startCharacter
            manualPreviousCharacter = WeekendMoodCarouselFaceKind.social.rawValue
            autoplayStartedAt = .now
        }
    }
}

private enum WeekendMoodCarouselDragTarget {
    case panel
    case chips
}

private struct WeekendMoodCarouselBackArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let midY = rect.midY
        path.move(to: CGPoint(x: rect.maxX - 2, y: midY))
        path.addLine(to: CGPoint(x: rect.minX + 2.5, y: midY))
        path.move(to: CGPoint(x: rect.minX + 8.5, y: rect.minY + 2.8))
        path.addLine(to: CGPoint(x: rect.minX + 2.3, y: midY))
        path.addLine(to: CGPoint(x: rect.minX + 8.5, y: rect.maxY - 2.8))
        return path
    }
}
