import SwiftUI
import UIKit

/// Walking dashboard with a bounded, reduced-motion-aware orbit field.
struct LunarWalkingPoints: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    var previewOrbit: Double? = nil
    var previewReducedMotion = false
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var message: String?
    @State private var searchText = ""
    @State private var selectedFriendID = 0
    @State private var displayedFriendID = 0
    @State private var calloutOpacity = 1.0
    @State private var calloutGeneration = 0
    @State private var selectedTab = 1
    @State private var selectedNav = 0
    @State private var orbitEpoch = Date()
    @State private var orbitContact: LunarWalkingPointsOrbitContact?
    @State private var orbitSettle: LunarWalkingPointsOrbitSettle?
    @State private var pausedOrbitQ: Double?
    @State private var pausedOrbitEnvelope = 0.0
    @State private var resumeGeneration = 0
    @GestureState private var fieldGestureActive = false
    @State private var dragDidMove = false
    @State private var releaseImpact: UIImpactFeedbackGenerator?
    @State private var ignoreFriendTapUntil: Date?
    @FocusState private var searchFocused: Bool
    private var reduceMotion: Bool { systemReduceMotion || previewReducedMotion }
    private var motionPaused: Bool { reduceMotion || scenePhase != .active || searchFocused || message != nil || pausedOrbitQ != nil }
    private var shouldTick: Bool {
        guard !motionPaused, previewTime == nil, previewOrbit == nil else { return false }
        return autoPlay || orbitContact != nil || orbitSettle != nil
    }
    private func c(_ hex: UInt32) -> Color { Color(hex: hex) }
    private func label(_ value: String, _ size: CGFloat, _ weight: Font.Weight = .regular, _ color: Color = .primary) -> some View {
        Text(value).font(.system(size: size, weight: weight)).foregroundStyle(color)
    }
    private static let artwork: [String:UIImage] = {
        var images: [String:UIImage] = [:]
        let prefix = "LunarWalkingPoints-"
        for url in Bundle.main.urls(forResourcesWithExtension:"png",subdirectory:nil) ?? [] {
            let key = url.deletingPathExtension().lastPathComponent
            if key.hasPrefix(prefix), let image = UIImage(contentsOfFile:url.path) { images[String(key.dropFirst(prefix.count))] = image }
        }
        if let atlas = images["avatars"]?.cgImage {
            let w = atlas.width / 2, h = atlas.height / 2
            for i in 0..<4 {
                if let crop = atlas.cropping(to: CGRect(x:(i % 2) * w,y:(i / 2) * h,width:w,height:h)) { images["avatar-\(i)"] = UIImage(cgImage:crop) }
            }
        }
        return images
    }()
    private func art(_ name: String, _ width: CGFloat, _ height: CGFloat) -> some View {
        Group {
            if let image = Self.artwork[name] { Image(uiImage:image).resizable().scaledToFit() }
            else { Color.clear }
        }.frame(width:width,height:height).accessibilityHidden(true)
    }
    private let friends: [LunarWalkingPointsFriend] = [
        LunarWalkingPointsFriend(id: 0, name: "Ari", level: "L3", color: 0x22A5E9, diameter: 61, center: CGPoint(x: 54, y: 293), avatar: 0),
        LunarWalkingPointsFriend(id: 1, name: "Mika", level: "L6", color: 0xEE45AC, diameter: 49, center: CGPoint(x: 344, y: 405), avatar: 1),
        LunarWalkingPointsFriend(id: 2, name: "Jules", level: "L4", color: 0xF7DC21, diameter: 33, center: CGPoint(x: 42, y: 453), avatar: 2),
    ]
    private var calloutFriend: LunarWalkingPointsFriend { friends.first(where: { $0.id == displayedFriendID }) ?? friends[0] }
    private var matchingFriends: [LunarWalkingPointsFriend] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return friends }
        return friends.filter { $0.name.localizedCaseInsensitiveContains(query) || $0.level.localizedCaseInsensitiveContains(query) }
    }
    private func orbitValue(at date: Date) -> LunarWalkingPointsOrbitValue {
        guard !reduceMotion else { return .rest }
        if let previewOrbit {
            let q = min(0.065, max(-0.065, previewOrbit))
            return LunarWalkingPointsOrbitValue(q: q, envelope: min(1, abs(q) / 0.06495))
        }
        if let previewTime { return Self.loopOrbitValue(previewTime) }
        if let orbitContact {
            let raw = orbitContact.originRaw + orbitContact.translationX / 420
            let q = min(0.065, max(-0.065, orbitContact.originQ + Self.rubberOrbit(raw) - orbitContact.originRubber))
            let envelope = min(1, max(0, orbitContact.originEnvelope + (abs(q) - abs(orbitContact.originQ)) / 0.06495))
            return LunarWalkingPointsOrbitValue(q: q, envelope: envelope)
        }
        if let orbitSettle {
            let age = max(0, date.timeIntervalSince(orbitSettle.startedAt))
            let q = Self.orbitSpring(age, origin: orbitSettle.originQ, velocity: orbitSettle.velocity)
            let envelope = min(1, max(0, Self.orbitSpring(age, origin: orbitSettle.originEnvelope, velocity: orbitSettle.envelopeVelocity)))
            return LunarWalkingPointsOrbitValue(q: q, envelope: envelope)
        }
        if let pausedOrbitQ { return LunarWalkingPointsOrbitValue(q: pausedOrbitQ, envelope: pausedOrbitEnvelope) }
        if autoPlay { return Self.loopOrbitValue(date.timeIntervalSince(orbitEpoch)) }
        return .rest
    }
    private static func loopOrbitValue(_ time: Double) -> LunarWalkingPointsOrbitValue {
        let t = ((time.truncatingRemainder(dividingBy: 8)) + 8).truncatingRemainder(dividingBy: 8)
        guard t >= 1.2 && t < 5.8 else { return .rest }
        let u = (t - 1.2) / 4.6
        let envelope = pow(sin(.pi * u), 2)
        return LunarWalkingPointsOrbitValue(q: 0.10 * sin(2 * .pi * u) * envelope, envelope: envelope)
    }
    private static func rubberOrbit(_ value: Double) -> Double {
        let magnitude = abs(value), soft = 0.045, range = 0.020, hard = 0.065
        if magnitude <= soft { return value }
        let compressed = soft + range * (1 - exp(-(magnitude - soft) / range))
        let result = min(hard, compressed)
        return value < 0 ? -result : result
    }
    private static func rubberOrbitDerivative(_ value: Double) -> Double {
        let magnitude = abs(value), soft = 0.045, range = 0.020, hard = 0.065
        if magnitude <= soft { return 1 }
        let compressed = soft + range * (1 - exp(-(magnitude - soft) / range))
        if compressed >= hard - 1e-12 { return 0 }
        return exp(-(magnitude - soft) / range)
    }
    private static func inverseRubberOrbit(_ value: Double) -> Double {
        let magnitude = min(0.065 - 1e-10, abs(value)), soft = 0.045, range = 0.020
        if magnitude <= soft { return value }
        let raw = soft - range * log(1 - (magnitude - soft) / range)
        return value < 0 ? -raw : raw
    }
    private static func orbitSpring(_ time: Double, origin: Double, velocity: Double) -> Double {
        if time >= 1.2 { return 0 }
        let omega = 12.0, damping = 0.85
        let decay = damping * omega
        let frequency = omega * sqrt(1 - damping * damping)
        return exp(-decay * time) * (origin * cos(frequency * time) + (velocity + decay * origin) / frequency * sin(frequency * time))
    }
    private func orbitPoint(_ point: CGPoint, q: Double) -> CGPoint {
        let dx = Double(point.x) - 196.5, dy = Double(point.y) - 496
        return CGPoint(
            x: CGFloat(196.5 + dx * cos(q) - dy / 0.72 * sin(q)),
            y: CGFloat(496 + 0.72 * dx * sin(q) + dy * cos(q))
        )
    }
    private func resetOrbit() {
        resumeGeneration += 1
        orbitContact = nil
        orbitSettle = nil
        pausedOrbitQ = nil
        pausedOrbitEnvelope = 0
        dragDidMove = false
        orbitEpoch = Date()
    }
    private func springVelocityIsBounded(_ velocity: Double, origin: Double, limit: Double = 0.065, ceilingOnly: Bool = false) -> Bool {
        let omega = 12.0, damping = 0.85
        let decay = damping * omega
        let frequency = omega * sqrt(1 - damping * damping)
        let sineCoefficient = (velocity + decay * origin) / frequency
        let derivativeCos = velocity
        let derivativeSin = -decay * sineCoefficient - origin * frequency
        let phase = atan2(derivativeSin, derivativeCos) + .pi / 2
        var times = [0.0, 1.2]
        for turn in -8...8 {
            let time = (phase + Double(turn) * .pi) / frequency
            if time > 0 && time < 1.2 { times.append(time) }
        }
        return times.allSatisfy {
            let value = Self.orbitSpring($0, origin: origin, velocity: velocity)
            return (ceilingOnly ? value : abs(value)) <= limit + 1e-10
        }
    }
    private func boundedSpringVelocity(_ velocity: Double, origin: Double, limit: Double = 0.065, ceilingOnly: Bool = false) -> Double {
        guard !springVelocityIsBounded(velocity, origin: origin, limit: limit, ceilingOnly: ceilingOnly) else { return velocity }
        let sign = velocity < 0 ? -1.0 : 1.0
        var low = 0.0, high = abs(velocity)
        for _ in 0..<40 {
            let middle = (low + high) / 2
            if springVelocityIsBounded(sign * middle, origin: origin, limit: limit, ceilingOnly: ceilingOnly) { low = middle } else { high = middle }
        }
        return sign * low
    }
    private func envelopeReleaseVelocity(q: Double, envelope: Double, velocity: Double) -> Double {
        // Carry the contact mapping's derivative into lift/material response.
        // The floor is contact with the ground; upper extrema must stay below 1.
        let direction = q == 0 ? (velocity < 0 ? -1.0 : 1.0) : (q < 0 ? -1.0 : 1.0)
        let requested = direction * velocity / 0.06495
        if (envelope <= 0 && requested < 0) || (envelope >= 1 && requested > 0) { return 0 }
        return boundedSpringVelocity(requested, origin: envelope, limit: 1, ceilingOnly: true)
    }
    private func beginSettle(from q: Double, envelope: Double = 0, velocity: Double = 0, carryEnvelopeVelocity: Bool = false) {
        guard !reduceMotion, previewTime == nil, previewOrbit == nil else { return }
        resumeGeneration += 1
        pausedOrbitQ = nil
        orbitContact = nil
        let boundedVelocity = boundedSpringVelocity(min(1, max(-1, velocity)), origin: q)
        orbitSettle = LunarWalkingPointsOrbitSettle(
            generation: resumeGeneration,
            originQ: q,
            originEnvelope: envelope,
            velocity: boundedVelocity,
            envelopeVelocity: carryEnvelopeVelocity ? envelopeReleaseVelocity(q: q, envelope: envelope, velocity: boundedVelocity) : 0,
            startedAt: Date()
        )
    }
    private func interruptOrbit() {
        guard !reduceMotion, previewTime == nil, previewOrbit == nil, scenePhase == .active, !searchFocused, message == nil, pausedOrbitQ == nil else { return }
        let value = orbitValue(at: Date())
        beginSettle(from: value.q, envelope: value.envelope)
    }
    private func pauseOrbitForOverlay() {
        if pausedOrbitQ == nil {
            let value = orbitValue(at: Date())
            pausedOrbitQ = value.q
            pausedOrbitEnvelope = value.envelope
        }
        resumeGeneration += 1
        orbitContact = nil
        orbitSettle = nil
        dragDidMove = false
    }
    private func resumeOrbitAfterOverlay() {
        guard message == nil, !searchFocused, scenePhase == .active, !reduceMotion, let q = pausedOrbitQ else { return }
        beginSettle(from: q, envelope: pausedOrbitEnvelope)
    }
    private func showMessage(_ text: String) {
        interruptOrbit()
        message = text
    }
    private func updateOrbitDrag(_ value: DragGesture.Value) {
        guard !reduceMotion, previewTime == nil, previewOrbit == nil, !searchFocused, message == nil else { return }
        let now = Date()
        if orbitContact == nil {
            let presented = orbitValue(at: now)
            let rawOrigin = Self.inverseRubberOrbit(presented.q)
            resumeGeneration += 1
            orbitSettle = nil
            dragDidMove = false
            if releaseImpact == nil { releaseImpact = UIImpactFeedbackGenerator(style: .light) }
            releaseImpact?.prepare()
            orbitContact = LunarWalkingPointsOrbitContact(
                originQ: presented.q,
                originEnvelope: presented.envelope,
                originRaw: rawOrigin,
                originRubber: Self.rubberOrbit(rawOrigin),
                translationX: 0,
                velocityPointsPerSecond: 0,
                lastTranslationX: 0,
                lastUpdate: now
            )
        }
        guard var contact = orbitContact else { return }
        let translation = Double(value.translation.width)
        let dt = now.timeIntervalSince(contact.lastUpdate)
        if dt > 0.001 { contact.velocityPointsPerSecond = (translation - contact.lastTranslationX) / dt }
        contact.translationX = translation
        contact.lastTranslationX = translation
        contact.lastUpdate = now
        orbitContact = contact
        if abs(translation) > 5 { dragDidMove = true }
    }
    private func finishOrbitDrag(cancelled: Bool) {
        guard let contact = orbitContact else { dragDidMove = false; return }
        orbitContact = nil
        let raw = contact.originRaw + contact.translationX / 420
        let q = min(0.065, max(-0.065, contact.originQ + Self.rubberOrbit(raw) - contact.originRubber))
        let rawEnvelope = contact.originEnvelope + (abs(q) - abs(contact.originQ)) / 0.06495
        let envelope = min(1, max(0, rawEnvelope))
        let moved = dragDidMove && !cancelled && !reduceMotion
        let mappedVelocity = moved ? Self.rubberOrbitDerivative(raw) * contact.velocityPointsPerSecond / 420 : 0
        beginSettle(from: q, envelope: envelope, velocity: mappedVelocity, carryEnvelopeVelocity: moved && (0...1).contains(rawEnvelope))
        if moved { releaseImpact?.impactOccurred(intensity: 0.45) }
        if dragDidMove { ignoreFriendTapUntil = Date().addingTimeInterval(0.25) }
        dragDidMove = false
    }
    private var fieldDragGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($fieldGestureActive) { _, active, _ in active = true }
            .onChanged(updateOrbitDrag)
            .onEnded { _ in finishOrbitDrag(cancelled: false) }
    }
    private func chooseFriend(_ id: Int) {
        if dragDidMove || (ignoreFriendTapUntil.map { Date() < $0 } ?? false) { return }
        interruptOrbit()
        guard selectedFriendID != id else { return }
        withAnimation(.easeOut(duration: reduceMotion ? 0.12 : 0.18)) { selectedFriendID = id }
        calloutGeneration += 1
    }
    private func actionPill(_ title: String, symbol: String? = nil, width: CGFloat, message: String) -> some View {
        Button { showMessage(message) } label: {
            Group {
                if let symbol { Image(systemName: symbol).font(.system(size: 14, weight: .bold)).foregroundStyle(.black) }
                else { label(title, 12, .bold, .black) }
            }
            .frame(width: width, height: 36)
            .background(c(0xFFE000), in: RoundedRectangle(cornerRadius: 6))
            .frame(width: max(width, 44), height: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(LunarWalkingPointsButtonStyle())
        .accessibilityLabel(title)
    }
    var body: some View {
        GeometryReader { geo in
            let scale = min(geo.size.width / 393, geo.size.height / 852)
            composition.frame(width: 393, height: 852, alignment: .topLeading)
                .scaleEffect(scale, anchor: .topLeading)
                .frame(width: 393 * scale, height: 852 * scale, alignment: .topLeading)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }.background(c(0x02013A).ignoresSafeArea()).ignoresSafeArea().preferredColorScheme(.dark)
        .alert("Walking Activity", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
        .onAppear { orbitEpoch = Date() }
        .onChange(of: autoPlay) { _, _ in resetOrbit() }
        .onChange(of: previewTime) { _, _ in resetOrbit() }
        .onChange(of: previewOrbit) { _, _ in resetOrbit() }
        .onChange(of: reduceMotion) { _, _ in
            resetOrbit()
            displayedFriendID = selectedFriendID
            calloutOpacity = 1
        }
        .onChange(of: searchFocused) { _, focused in
            if focused { pauseOrbitForOverlay() } else { resumeOrbitAfterOverlay() }
        }
        .onChange(of: message) { oldValue, newValue in
            if newValue != nil { pauseOrbitForOverlay() }
            else if oldValue != nil { resumeOrbitAfterOverlay() }
        }
        .onChange(of: scenePhase) { _, _ in resetOrbit() }
        .onChange(of: fieldGestureActive) { wasActive, isActive in
            if wasActive && !isActive && orbitContact != nil { finishOrbitDrag(cancelled: true) }
        }
        .task(id: resumeGeneration) {
            guard orbitSettle?.generation == resumeGeneration else { return }
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            guard !Task.isCancelled, orbitSettle?.generation == resumeGeneration else { return }
            orbitSettle = nil
            orbitEpoch = Date()
        }
        .task(id: "\(calloutGeneration)-\(reduceMotion)") {
            guard calloutGeneration > 0 else { return }
            guard displayedFriendID != selectedFriendID else {
                if reduceMotion {
                    calloutOpacity = 1
                } else {
                    withAnimation(.easeOut(duration: 0.10)) { calloutOpacity = 1 }
                }
                return
            }
            let generation = calloutGeneration
            let targetFriendID = selectedFriendID
            if reduceMotion {
                displayedFriendID = targetFriendID
                withAnimation(.easeOut(duration: 0.12)) { calloutOpacity = 0.84 }
                try? await Task.sleep(nanoseconds: 120_000_000)
            } else {
                withAnimation(.easeOut(duration: 0.07)) { calloutOpacity = 0 }
                try? await Task.sleep(nanoseconds: 70_000_000)
                guard !Task.isCancelled, generation == calloutGeneration else { return }
                displayedFriendID = targetFriendID
            }
            guard !Task.isCancelled, generation == calloutGeneration else { return }
            withAnimation(.easeOut(duration: 0.10)) { calloutOpacity = 1 }
        }
        .sensoryFeedback(.selection, trigger: selectedFriendID)
    }
    private var composition: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [c(0x02013A),c(0x02013A),c(0x110248)],startPoint: .top,endPoint: .bottom)
            RadialGradient(colors:[c(0x3F0070).opacity(0.18),.clear],center:UnitPoint(x:196.5 / 393,y:800 / 852),startRadius:0,endRadius:230)
                .accessibilityHidden(true).allowsHitTesting(false)
            Canvas { context,size in
                for i in 0..<38 {
                    let x=CGFloat((i*97+31)%390),y=CGFloat((i*61+120)%680)
                    context.fill(Path(ellipseIn: CGRect(x:x,y:y,width:i%4==0 ? 2:1,height:i%4==0 ? 2:1)),with: .color(.white.opacity(0.22)))
                }
                var orbit=Path();orbit.move(to:CGPoint(x:-30,y:292));orbit.addCurve(to:CGPoint(x:440,y:520),control1:CGPoint(x:165,y:258),control2:CGPoint(x:330,y:355));context.stroke(orbit,with: .color(.white.opacity(0.045)),lineWidth:1)
            }
            HStack(spacing: 12) {
                Button { showMessage("Maya’s walking profile.") } label: {
                    art("avatar-3", 43, 43).background(c(0x8D8E85),in: Circle()).clipShape(Circle())
                        .frame(width:44,height:44).contentShape(Rectangle())
                }
                .buttonStyle(LunarWalkingPointsButtonStyle())
                .accessibilityLabel("Maya’s profile")
                ZStack { Circle().trim(from:0.1,to:0.93).stroke(c(0xD3BC00),lineWidth:2);label("L1",10,.semibold,c(0xEED934)) }.frame(width:34,height:34)
                Spacer()
                Button { showMessage("Your point balance is 3745.") } label: {
                    HStack(spacing: 5) { Image(systemName:"dollarsign.circle.fill").foregroundStyle(c(0xF7D80B));label("3745",13,.medium,.white);Image(systemName:"plus.circle.fill").foregroundStyle(c(0x49405E)) }
                        .frame(minHeight:44).contentShape(Rectangle())
                }
                .buttonStyle(LunarWalkingPointsButtonStyle())
                .accessibilityLabel("Point balance, 3745 points")
                Button { showMessage("You’re all caught up.") } label: {
                    Image(systemName:"bell.fill").font(.system(size:16)).foregroundStyle(.white).overlay(alignment:.topTrailing) { Circle().fill(.red).frame(width:4,height:4) }
                        .frame(width:44,height:44).contentShape(Rectangle())
                }
                .buttonStyle(LunarWalkingPointsButtonStyle())
                .accessibilityLabel("Notifications")
            }.frame(width:359).position(x:196.5,y:72)
            ZStack {
                ForEach(Array(["We Walk", "Aster", "Nimbly", "Arc"].enumerated()), id: \.offset) { i, text in
                    Button {
                        interruptOrbit()
                        withAnimation(.easeOut(duration: reduceMotion ? 0.12 : 0.18)) { selectedTab = i }
                    } label: {
                        VStack(spacing: 6) {
                            label(text, 14, selectedTab == i ? .semibold : .regular, selectedTab == i ? .white : c(0xA49CB5))
                            Rectangle().fill(selectedTab == i ? c(0xF1D633) : .clear).frame(width: 40, height: 2)
                        }
                        .frame(width: 75, height: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(LunarWalkingPointsButtonStyle())
                    .accessibilityLabel(text)
                    .accessibilityAddTraits(selectedTab == i ? .isSelected : [])
                    .position(x: [51.0,134.0,215.0,281.0][i], y: 132)
                }
            }
            HStack(spacing: 9) {
                Image(systemName: "magnifyingglass").font(.system(size: 12)).foregroundStyle(c(0x837A99))
                TextField("Search Friends", text: $searchText)
                    .font(.system(size: 12)).foregroundStyle(.white).tint(c(0xFFE000))
                    .textInputAutocapitalization(.never).autocorrectionDisabled().submitLabel(.search)
                    .focused($searchFocused)
                    .accessibilityLabel("Search friends")
                    .onSubmit {
                        if let first = matchingFriends.first { chooseFriend(first.id) }
                        searchFocused = false
                    }
                if !searchText.isEmpty {
                    Button { interruptOrbit(); searchText = "" } label: { Image(systemName: "xmark.circle.fill").font(.system(size: 13)).foregroundStyle(c(0x9189A5)).frame(width:44,height:44).contentShape(Rectangle()) }
                        .buttonStyle(LunarWalkingPointsButtonStyle()).accessibilityLabel("Clear search")
                }
            }
            .padding(.horizontal,16).frame(width:359,height:44)
            .background(c(0x030020),in:RoundedRectangle(cornerRadius:4))
            .position(x:196.5,y:188)
            orbitTimeline
            stat("233 km", "Distance").position(x:111,y:626)
            stat("127", "Points").position(x:201,y:626)
            stat("34", "My Rank").position(x:286,y:626)
            label("50% Completed",7,.regular,.white).frame(width:88,height:10)
                .background(c(0x342B5A),in:RoundedRectangle(cornerRadius:4)).position(x:111,y:678)
            actionPill("Activity",width:113,message:"Walking summary: 233 km and 127 points.").position(x:111,y:701)
            actionPill("Next milestone",symbol:"bolt.fill",width:37,message:"Keep walking to reach your next activity milestone.").position(x:196.5,y:701)
            actionPill("Leaderboard",width:113,message:"Your current rank is 34.").position(x:282,y:701)
            LunarWalkingPointsStar().fill(LinearGradient(colors:[.white.opacity(0.55),.white.opacity(0.02)],startPoint:.topLeading,endPoint:.bottomTrailing))
                .frame(width:22,height:28).position(x:20,y:678).accessibilityHidden(true)
            LunarWalkingPointsStar().fill(.white.opacity(0.85)).frame(width:7,height:9).position(x:299,y:359).accessibilityHidden(true)
            HStack {
                ForEach(Array([("shoeprints.fill","Home"),("figure.walk","Walkathons"),("newspaper.fill","Feed"),("bag.fill","Shop")].enumerated()),id:\.offset) { i,item in
                    Button { interruptOrbit(); withAnimation(.easeOut(duration:reduceMotion ? 0.12 : 0.18)) { selectedNav = i } } label: {
                        VStack(spacing:4) {
                            ZStack {
                                if selectedNav == i {
                                    Circle().fill(RadialGradient(colors:[c(0xFFE000).opacity(0.28),.clear],center:.center,startRadius:0,endRadius:16)).frame(width:36,height:36)
                                }
                                Image(systemName:item.0).font(.system(size:18))
                            }.frame(width:18,height:18)
                            label(item.1,10,.regular,selectedNav == i ? c(0xEBDC3D):c(0xA29AAE))
                        }
                        .foregroundStyle(selectedNav == i ? c(0xEBDC3D):c(0xA29AAE))
                        .frame(maxWidth:.infinity,maxHeight:.infinity)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(LunarWalkingPointsButtonStyle())
                    .accessibilityLabel(item.1)
                    .accessibilityAddTraits(selectedNav == i ? .isSelected : [])
                }
            }.padding(.horizontal,10).frame(width:365,height:74).background(c(0x32264E),in:RoundedRectangle(cornerRadius:16)).position(x:196.5,y:794)
        }
    }
    private var orbitTimeline: some View {
        TimelineView(.animation(minimumInterval:1.0 / 60.0,paused:!shouldTick)) { context in
            let motion = orbitValue(at: context.date)
            let heightProgress = reduceMotion ? 0 : min(1, max(0, motion.envelope))
            let runnerLift = 16 * heightProgress
            let runnerRoll = -2 * min(1, max(-1, motion.q / 0.065)) * heightProgress
            let auraProgress = heightProgress * heightProgress * (3 - 2 * heightProgress)
            // Fade in at takeoff, then shrink/fade with height; the reference rest has no shadow.
            let shadowOpacity = 0.16 * min(1, heightProgress / 0.12) * (1 - heightProgress)
            ZStack(alignment:.topLeading) {
                Color.clear.frame(width:393,height:390).contentShape(Rectangle())
                    .position(x:196.5,y:411).gesture(fieldDragGesture)
                RadialGradient(colors:[c(0xFF4400).opacity(0.18),.clear],center:.center,startRadius:60,endRadius:120)
                    .frame(width:240,height:240).position(x:199,y:496).accessibilityHidden(true).allowsHitTesting(false)
                RadialGradient(stops:[
                    .init(color:c(0xFFD24A).opacity(0),location:0),
                    .init(color:c(0xFFD24A).opacity(0.30),location:0.58),
                    .init(color:c(0xFF6A00).opacity(0.16),location:0.78),
                    .init(color:.clear,location:1)
                ],center:.center,startRadius:0,endRadius:120)
                    .frame(width:240,height:240)
                    .scaleEffect(CGFloat(1 + 0.04 * auraProgress))
                    .opacity(auraProgress)
                    .position(x:CGFloat(199 - 45 * motion.q),y:CGFloat(496 - 3 * heightProgress))
                    .accessibilityHidden(true).allowsHitTesting(false)
                art("moon",180,180)
                    .position(x:199,y:496).allowsHitTesting(false)
                if heightProgress > 0 {
                    Ellipse().fill(c(0x02013A).opacity(shadowOpacity))
                        .frame(width:30,height:4)
                        .scaleEffect(x:CGFloat(1 - 0.4 * heightProgress),y:CGFloat(1 - 0.375 * heightProgress))
                        .blur(radius:1.25)
                        .position(x:CGFloat(199 - 70 * motion.q),y:435)
                        .accessibilityHidden(true).allowsHitTesting(false)
                }
                art("runner",129,142)
                    .offset(x:CGFloat(-70 * motion.q),y:CGFloat(-runnerLift))
                    .rotationEffect(.degrees(runnerRoll))
                    .position(x:199,y:379).allowsHitTesting(false)
                ForEach(matchingFriends) { friend in
                    friendButton(friend,selected:selectedFriendID == friend.id,q:motion.q)
                        .position(orbitPoint(friend.center,q:motion.q))
                }
                friendCallout(q:motion.q)
            }
            .frame(width:393,height:852,alignment:.topLeading)
        }
    }
    private func friendCallout(q:Double)->some View {
        let ari = orbitPoint(friends[0].center,q:q)
        let dx = ari.x - friends[0].center.x
        let dy = ari.y - friends[0].center.y
        return Group {
            if !searchText.isEmpty && matchingFriends.isEmpty {
                label("No friends found",12,.regular,c(0xA49CB5)).frame(width:154,height:59)
                    .background(c(0x1D1B43),in:RoundedRectangle(cornerRadius:8))
                    .overlay(RoundedRectangle(cornerRadius:8).stroke(.white.opacity(0.12),lineWidth:1))
                    .position(x:184+dx,y:272.5+dy).accessibilityLabel("No friends found")
                    .allowsHitTesting(false)
            } else {
                VStack(alignment:.leading,spacing:3) {
                    if calloutFriend.id == 0 {
                        (Text("Ari just scored ") + Text("345 pts").fontWeight(.bold).foregroundColor(.white))
                            .font(.system(size:12)).lineLimit(1).minimumScaleFactor(0.9)
                            .foregroundColor(c(0xE0DFEB))
                        label("by 5 km walk",12,.regular,c(0xBFB5D3))
                    } else {
                        label("\(calloutFriend.name) · \(calloutFriend.level)",12,.semibold,.white)
                        label("Walking friend",12,.regular,c(0xBFB5D3))
                    }
                }
                .font(.system(size:12)).lineSpacing(2).padding(.horizontal,12)
                .frame(width:154,height:59,alignment:.center)
                .background {
                    LunarWalkingPointsCalloutTail().fill(c(0x1D1B43))
                        .frame(width:10,height:14).offset(x:-77)
                    RoundedRectangle(cornerRadius:8).fill(c(0x1D1B43))
                }
                .overlay {
                    RoundedRectangle(cornerRadius:8).stroke(.white.opacity(0.12),lineWidth:1)
                    LunarWalkingPointsCalloutTail().stroke(.white.opacity(0.12),lineWidth:1)
                        .frame(width:10,height:14).offset(x:-77)
                }
                .position(x:184+dx,y:272.5+dy)
                .accessibilityElement(children:.combine)
                .accessibilityLabel(calloutFriend.id == 0 ? "Ari just scored 345 points by 5 kilometre walk" : "\(calloutFriend.name), level \(calloutFriend.level), walking friend")
                .allowsHitTesting(false)
            }
        }.opacity(calloutOpacity).accessibilityHidden(calloutOpacity < 0.8)
    }
    private func stat(_ value:String,_ title:String)->some View {
        VStack(spacing:6) { label(value,17,.bold,.white);label(title,11.5,.regular,c(0xBFB5D3)) }
    }
    private func friendButton(_ friend:LunarWalkingPointsFriend,selected:Bool,q:Double)->some View {
        let emphasized = selected && friend.id != 0
        let badgeRoundness = selected ? CGFloat(min(1,abs(q) / 0.065)) : 0
        let hitSize: CGSize = friend.id == 0 ? CGSize(width:86,height:88) : friend.id == 1 ? CGSize(width:60,height:82) : CGSize(width:48,height:54)
        return Button { chooseFriend(friend.id) } label: {
            ZStack {
                art("avatar-\(friend.avatar)",friend.diameter,friend.diameter).background(c(0x8D8E85),in:Circle())
                    .clipShape(Circle())
                    .overlay(Circle().stroke(emphasized ? c(0xFFE000) : .white,lineWidth:emphasized ? 1.5 : 1))
                label(friend.level,friend.diameter < 40 ? 5 : 6,.bold,.black)
                    .frame(width:friend.diameter < 40 ? 12 : 17,height:friend.diameter < 40 ? 12 : 17)
                    .background(.white,in:Circle()).offset(x:friend.diameter * 0.24,y:friend.diameter * 0.48)
                LunarWalkingPointsHexagonMorph(progress:badgeRoundness).fill(c(friend.color))
                    .overlay(LunarWalkingPointsHexagonMorph(progress:badgeRoundness).stroke(emphasized ? c(0xFFE000) : .white.opacity(0.9),lineWidth:emphasized ? 2 : 1.5))
                    .frame(width:friend.id == 0 ? 28 : friend.id == 1 ? 22 : 18,height:friend.id == 0 ? 32 : friend.id == 1 ? 25 : 21)
                    .offset(x:friend.id == 0 ? 29 : friend.id == 1 ? 19 : 14,y:friend.id == 2 ? -16 : -28)
                    .scaleEffect(emphasized && !reduceMotion ? 1.06 : 1)
                    .animation(.easeOut(duration:reduceMotion ? 0.12 : 0.18),value:selected)
            }
            .frame(width:friend.diameter,height:friend.diameter)
            .frame(width:hitSize.width,height:hitSize.height)
            .contentShape(Rectangle())
        }
        .buttonStyle(LunarWalkingPointsButtonStyle())
        .simultaneousGesture(fieldDragGesture)
        .accessibilityLabel("\(friend.name), level \(friend.level)")
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct LunarWalkingPointsOrbitValue {
    static let rest = LunarWalkingPointsOrbitValue(q: 0, envelope: 0)
    let q: Double
    let envelope: Double
}

private struct LunarWalkingPointsOrbitContact {
    let originQ: Double
    let originEnvelope: Double
    let originRaw: Double
    let originRubber: Double
    var translationX: Double
    var velocityPointsPerSecond: Double
    var lastTranslationX: Double
    var lastUpdate: Date
}

private struct LunarWalkingPointsOrbitSettle {
    let generation: Int
    let originQ: Double
    let originEnvelope: Double
    let velocity: Double
    let envelopeVelocity: Double
    let startedAt: Date
}

private struct LunarWalkingPointsFriend: Identifiable {
    let id: Int
    let name: String
    let level: String
    let color: UInt32
    let diameter: CGFloat
    let center: CGPoint
    let avatar: Int
}

private struct LunarWalkingPointsButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.88 : 1)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.975 : 1)
            .animation(.easeOut(duration:configuration.isPressed ? 0.12 : 0.1),value:configuration.isPressed)
    }
}

private struct LunarWalkingPointsCalloutTail: Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: rect.maxX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
            path.closeSubpath()
        }
    }
}

private struct LunarWalkingPointsHexagonMorph: Shape {
    var progress: CGFloat
    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }
    func path(in rect:CGRect)->Path {
        Path { path in
            let center = CGPoint(x:rect.midX,y:rect.midY)
            let amount = min(1,max(0,progress))
            let vertices = (0..<6).map { index -> CGPoint in
                let angle = Double(index) * .pi / 3 - .pi / 2
                return CGPoint(x:rect.midX + cos(angle) * rect.width / 2,y:rect.midY + sin(angle) * rect.height / 2)
            }
            path.move(to:vertices[0])
            for i in 0..<6 {
                let start = vertices[i], end = vertices[(i + 1) % vertices.count]
                let midpoint = CGPoint(x:(start.x + end.x) / 2,y:(start.y + end.y) / 2)
                let radial = CGPoint(x:midpoint.x - center.x,y:midpoint.y - center.y)
                let control = CGPoint(x:midpoint.x + radial.x * 0.309401 * amount,y:midpoint.y + radial.y * 0.309401 * amount)
                path.addQuadCurve(to:end,control:control)
            }
            path.closeSubpath()
        }
    }
}

private struct LunarWalkingPointsStar: Shape {
    func path(in r:CGRect)->Path {
        Path { p in
            p.move(to:CGPoint(x:r.midX,y:r.minY))
            p.addQuadCurve(to:CGPoint(x:r.maxX,y:r.midY),control:CGPoint(x:r.midX,y:r.midY))
            p.addQuadCurve(to:CGPoint(x:r.midX,y:r.maxY),control:CGPoint(x:r.midX,y:r.midY))
            p.addQuadCurve(to:CGPoint(x:r.minX,y:r.midY),control:CGPoint(x:r.midX,y:r.midY))
            p.addQuadCurve(to:CGPoint(x:r.midX,y:r.minY),control:CGPoint(x:r.midX,y:r.midY))
            p.closeSubpath()
        }
    }
}
