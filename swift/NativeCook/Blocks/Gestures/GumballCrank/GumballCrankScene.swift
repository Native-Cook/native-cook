import CoreImage
import Foundation
import SwiftUI
import UIKit

/// Rendering port of the active React Native route, `GumballUpgrade`.
/// Coordinates are transformed once from its 402 point-wide Skia stage.
struct GumballCrankScene: View {
    let state: GumballCrankState
    let width: CGFloat
    let height: CGFloat
    let safeTop: CGFloat
    let onTurn: () -> Void
    let onCollect: () -> Void

    private let artwork = GumballCrankArtwork.shared
    @State private var displayedCoins = GumballCrankMotion.coins.from

    private var s: CGFloat { width / CGFloat(GumballCrankTheme.stageWidth) }
    private var phase: Int { state.phase }
    private var phaseT: Double { state.phaseT }
    private var isReady: Bool { phase == GumballCrankPhase.ready }
    private var isDispensing: Bool { phase == GumballCrankPhase.dispensing }
    private var isReward: Bool { phase == GumballCrankPhase.reward }
    private var isCollecting: Bool { phase == GumballCrankPhase.collecting }
    /// The core owns the 350 ms manual-interruption return. It is normally 1,
    /// so the approved RN formulae below are unchanged during ordinary play.
    private var resumeFade: Double { 1 - state.resumeProgress }
    private var visualTubOut: Double { tubOut * resumeFade }
    private var reducedTubVisible: Double { isReady ? 0 : 1 }
    // The batch's explicit accessibility contract uses a 200 ms fade-only
    // outcome; the RN worklet's normal-motion transforms remain intact.
    private var tubReveal: Double {
        guard state.reduced else { return 1 }
        if isDispensing { return clamp((phaseT - GumballCrankMotion.upT.out[0]) / GumballCrankMotion.reduced.fade) }
        if isReward { return 1 }
        if isCollecting { return 1 - clamp(phaseT / GumballCrankMotion.reduced.fade) }
        return 0
    }
    private var cardReveal: Double {
        guard state.reduced else { return morph }
        if isReward { return clamp(phaseT / GumballCrankMotion.reduced.fade) }
        if isCollecting { return 1 - clamp(phaseT / GumballCrankMotion.reduced.fade) }
        return 0
    }
    private var visualMorph: Double { cardReveal * resumeFade }

    var body: some View {
        let scene = machineScene
            .scaleEffect(
                x: 1 + machineSquash * CGFloat(GumballCrankTheme.presentation.machineSquashX),
                y: 1 - machineSquash,
                anchor: UnitPoint(
                    x: 0,
                    y: CGFloat(GumballCrankTheme.presentation.machineAnchorY) * s / max(height, 1)
                )
            )
            .scaleEffect(cameraScale, anchor: .topLeading)
            .offset(cameraOffset)

        ZStack(alignment: .topLeading) {
            scene
            shadowLayer
            potLayer(fullOpacity: false)

            // GumballUpgrade.tsx:264-272: fixed-radius duplicate, then cross-fade.
            ZStack(alignment: .topLeading) {
                scene
                potLayer(fullOpacity: true)
            }
            .blur(radius: CGFloat(GumballCrankTheme.up.blur) * s, opaque: false)
            .opacity(visualMorph)
            .allowsHitTesting(false)

            GumballCrankColor.hex(GumballCrankTheme.up.scrimColor)
                .opacity(GumballCrankTheme.up.scrim * visualMorph)
            confettiLayer
            couponLayer
            hudLayer
            flyingCoinsLayer
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .contain)
        .task(id: state.coins) { await updateDisplayedCoins() }
    }

    // MARK: - GumballUpgrade.tsx:156-219

    private var machineSquash: CGFloat {
        guard isDispensing, phaseT < GumballCrankMotion.upT.squash, !state.reduced else { return 0 }
        return CGFloat(
            sin(.pi * phaseT / GumballCrankMotion.upT.squash)
                * GumballCrankTheme.presentation.machineSquash
        )
    }

    private var tubOut: Double {
        if isDispensing {
            guard !state.reduced else { return phaseT > GumballCrankMotion.upT.out[0] ? 1 : 0 }
            return easeOutBack(clamp((phaseT - GumballCrankMotion.upT.out[0]) /
                (GumballCrankMotion.upT.out[1] - GumballCrankMotion.upT.out[0])))
        }
        if isReward { return 1 }
        if isCollecting {
            if phaseT < GumballCrankMotion.upT.collect { return 1 }
            return 1 - easeOutCubic(clamp((phaseT - GumballCrankMotion.upT.collect) / GumballCrankMotion.upT.back))
        }
        return 0
    }

    private var morph: Double {
        if isDispensing {
            guard !state.reduced else { return 0 }
            return easeOutCubic(clamp((phaseT - GumballCrankMotion.upT.morph[0]) /
                (GumballCrankMotion.upT.morph[1] - GumballCrankMotion.upT.morph[0])))
        }
        if isReward { return 1 }
        if isCollecting {
            return 1 - easeInOut(clamp((phaseT - GumballCrankMotion.upT.couponOut[0]) /
                (GumballCrankMotion.upT.couponOut[1] - GumballCrankMotion.upT.couponOut[0])))
        }
        return 0
    }

    private var cameraScale: CGFloat {
        guard !state.reduced else { return 1 }
        let u = smooth01(clamp(visualTubOut))
        return 1 + CGFloat(GumballCrankMotion.replicaCamera.b[0] - 1) * u
    }

    private var cameraOffset: CGSize {
        guard !state.reduced else { return .zero }
        let u = smooth01(clamp(visualTubOut))
        let screenPixel = width / CGFloat(GumballCrankTheme.presentation.cameraReferenceWidth)
        return CGSize(
            width: CGFloat(GumballCrankMotion.replicaCamera.b[1]) * screenPixel * u,
            height: CGFloat(GumballCrankMotion.replicaCamera.b[2]) * screenPixel * u
        )
    }

    // MARK: - Replica scene retained by the active upgrade

    private var machineScene: some View {
        ZStack(alignment: .topLeading) {
            asset("room.jpg").frame(width: width, height: height)
            doorLayer
            globeLayer
            knobLayer
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var doorLayer: some View {
        let door = GumballCrankTheme.replicaArt.door
        return RoundedRectangle(cornerRadius: CGFloat(door.r) * s)
            .fill(LinearGradient(
                colors: [GumballCrankColor.hex(door.top), GumballCrankColor.hex(door.bottom)],
                startPoint: .top,
                endPoint: .bottom
            ))
            .frame(width: CGFloat(door.w) * s, height: CGFloat(door.h) * s)
            .overlay {
                RoundedRectangle(cornerRadius: CGFloat(door.r) * s)
                    .stroke(
                        GumballCrankColor.rgba(door.shade),
                        lineWidth: CGFloat(GumballCrankTheme.presentation.doorStroke) * s
                    )
                    .blur(radius: CGFloat(GumballCrankTheme.presentation.doorInnerBlur) * s)
                    .clipShape(RoundedRectangle(cornerRadius: CGFloat(door.r) * s))
            }
            .position(x: CGFloat(door.x + door.w / 2) * s, y: CGFloat(door.y + door.h / 2) * s)
    }

    private var globeLayer: some View {
        let art = GumballCrankTheme.replicaArt
        let jostle = CGSize(
            width: CGFloat(state.jx * GumballCrankTheme.presentation.jostleScale) * s,
            height: CGFloat(state.jy * GumballCrankTheme.presentation.jostleScale) * s
        )
        let sweepU = isDispensing
            ? phaseT / GumballCrankMotion.upT.sweep
            : state.clock.truncatingRemainder(dividingBy: GumballCrankMotion.upT.idleSweep) / GumballCrankMotion.upT.sweep
        let sweepX = sweepU < 1
            ? (art.globe.x - art.globe.r - GumballCrankTheme.presentation.sweepPadding
                + (art.globe.r * 2 + GumballCrankTheme.presentation.sweepTravelPadding) * sweepU) * Double(s)
            : -999.0

        return ZStack(alignment: .topLeading) {
            asset("globe-fill.png")
                .frame(width: width, height: height)
                .offset(jostle)
                .overlay(Color.white.opacity(GumballCrankTheme.presentation.globeHazeOpacity))
                .mask(GumballCrankGlobeMask(scale: s))

            Rectangle()
                .fill(GumballCrankColor.rgba(GumballCrankTheme.up.sweep))
                .frame(width: CGFloat(GumballCrankTheme.up.sweepW) * s, height: CGFloat(art.globe.r * 2) * s)
                .blur(radius: CGFloat(GumballCrankTheme.presentation.sweepBlur) * s)
                .projectionEffect(ProjectionTransform(CGAffineTransform(
                    a: 1,
                    b: 0,
                    c: CGFloat(tan(GumballCrankTheme.presentation.sweepSkew)),
                    d: 1,
                    tx: 0,
                    ty: 0
                )))
                .offset(x: CGFloat(sweepX), y: CGFloat(art.globe.y - art.globe.r) * s)
                .frame(width: width, height: height, alignment: .topLeading)
                .mask(GumballCrankGlobeMask(scale: s))

            asset("globe-glass.png").frame(width: width, height: height)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var knobLayer: some View {
        let knob = GumballCrankTheme.replicaArt.knob
        let center = CGPoint(x: CGFloat(knob.x + knob.size / 2) * s, y: CGFloat(knob.y + knob.size / 2) * s)
        let presentation = GumballCrankTheme.presentation
        let rawRingOpacity = presentation.ringBaseOpacity
            + presentation.ringOpacityAmplitude * sin(state.clock * presentation.ringFrequency)
        // The raw RN sine does not land on its rest value at 12 s. For the
        // recording seam, the final 350 ms reaches the same 0.35 as t = 0.
        let seamStart = GumballCrank.loop - presentation.seamEase
        let seam = state.demoOn ? clamp((state.demoT - seamStart) / presentation.seamEase) : 0
        let ringOpacity = isReady && !state.reduced
            ? rawRingOpacity + (presentation.ringBaseOpacity - rawRingOpacity) * easeOutCubic(seam)
            : 0
        let angle = state.reduced ? 0 : state.angle
            + state.wob * presentation.knobWobbleWeight
            + state.back * presentation.knobBackWeight
        let pressScale = state.reduced ? 1 : 1 + (GumballCrankMotion.knob.press - 1) * state.press

        return ZStack {
            Circle()
                .stroke(
                    GumballCrankColor.rgba(GumballCrankTheme.up.ringColor),
                    lineWidth: CGFloat(presentation.ringLineWidth) * s
                )
                .frame(width: CGFloat(GumballCrankTheme.up.ring * 2) * s, height: CGFloat(GumballCrankTheme.up.ring * 2) * s)
                .blur(radius: CGFloat(presentation.ringBlur))
                .opacity(ringOpacity)

            asset("knob.png", processed: true)
                .frame(width: CGFloat(knob.size) * s, height: CGFloat(knob.size) * s)
                .blur(radius: CGFloat(GumballCrankTheme.replicaArt.knobBlur) * s)
                .rotationEffect(.radians(knob.tilt + angle))
                .scaleEffect(CGFloat(pressScale))

            Button(action: onTurn) { Color.clear }
                .buttonStyle(.plain)
                .frame(
                    width: CGFloat(presentation.knobHitSize) * s,
                    height: CGFloat(presentation.knobHitSize) * s
                )
                .contentShape(Circle())
                .accessibilityLabel(GumballCrankTheme.copy.knob)
                .accessibilityHint(GumballCrankTheme.copy.knobHint)
                .disabled(!isReady)
                .opacity(isReady ? 1 : 0)
        }
        .position(center)
    }

    // MARK: - Hero layers

    private var shadowLayer: some View {
        Ellipse()
            .fill(GumballCrankColor.rgba(GumballCrankTheme.up.shadow))
            .frame(
                width: CGFloat(GumballCrankTheme.presentation.shadowWidth) * s,
                height: CGFloat(GumballCrankTheme.presentation.shadowHeight) * s
            )
            .blur(radius: CGFloat(GumballCrankTheme.presentation.shadowBlur) * s)
            .scaleEffect(state.reduced ? 1 : CGFloat(
                GumballCrankTheme.presentation.initialTubScale
                    + (1 - GumballCrankTheme.presentation.initialTubScale) * clamp(visualTubOut)
            ))
            .opacity(
                GumballCrankTheme.presentation.shadowOpacity
                    * (state.reduced ? reducedTubVisible : clamp(visualTubOut)) * tubReveal
            )
            .position(x: CGFloat(GumballCrankTheme.replicaArt.pot.cx) * s, y: CGFloat(GumballCrankTheme.up.shadowY) * s)
    }

    private func potLayer(fullOpacity: Bool) -> some View {
        let art = GumballCrankTheme.replicaArt
        // Reduced Motion uses only a fade at the final tub pose; neither
        // translate nor scale is animated.
        let u = state.reduced ? reducedTubVisible : visualTubOut
        let x = CGFloat(art.exit.x + (art.pot.cx - art.exit.x) * u) * s
        let y = CGFloat(art.exit.y + (art.pot.cy - art.exit.y) * u) * s
        let back = state.reduced
            ? GumballCrankTheme.up.tubBack
            : 1 - (1 - GumballCrankTheme.up.tubBack) * visualMorph
        let potScale = (
            GumballCrankTheme.presentation.initialTubScale
                + (1 - GumballCrankTheme.presentation.initialTubScale) * u
        ) * back

        return asset("pot.png")
            .frame(width: CGFloat(art.pot.w) * s, height: CGFloat(art.pot.h) * s)
            .scaleEffect(CGFloat(potScale))
            .opacity(fullOpacity ? 1 : clamp(u / GumballCrankTheme.presentation.tubFadeFraction) * tubReveal)
            .position(x: x, y: y)
    }

    private var confettiLayer: some View {
        ZStack {
            ForEach(state.px.indices, id: \.self) { index in
                if index < state.py.count, index < state.prot.count {
                    asset(confettiName(for: index))
                        .scaledToFit()
                        .frame(width: confettiSize(index), height: confettiSize(index))
                        .rotationEffect(.radians(state.prot[index]))
                        .position(x: CGFloat(state.px[index]), y: CGFloat(state.py[index]))
                }
            }
        }
        .opacity(state.reduced ? 0 : clamp(
            GumballCrankTheme.presentation.confettiOpacityBase
                - phaseT * GumballCrankTheme.presentation.confettiOpacityRate
        ) * resumeFade)
        .allowsHitTesting(false)
    }

    private var couponLayer: some View {
        let panel = scaledRect(GumballCrankTheme.up.panel)
        let card = scaledRect(GumballCrankTheme.up.card)
        // Normal motion follows RN couponT: its centre follows morph, while
        // its size holds at card dimensions during collection. Reduce Motion
        // holds final card geometry and only fades it over 200 ms.
        let centerProgress = CGFloat(state.reduced ? 1 : visualMorph)
        let sizeProgress = CGFloat(state.reduced ? 1 : (isCollecting ? 1 : visualMorph))
        let center = CGPoint(
            x: panel.midX + (card.midX - panel.midX) * centerProgress,
            y: panel.midY + (card.midY - panel.midY) * centerProgress + (state.reduced ? 0 : (
                isCollecting
                    ? CGFloat((1 - visualMorph) * GumballCrankTheme.presentation.collectingCouponDrop) * s
                    : 0
            ))
        )
        let size = CGSize(
            width: panel.width + (card.width - panel.width) * sizeProgress,
            height: panel.height + (card.height - panel.height) * sizeProgress
        )
        let presentation = GumballCrankTheme.presentation
        let panelOpacity = state.reduced ? 0 : (
            visualMorph > 0.001 ? 1 - clamp(visualMorph / presentation.panelFadeSpan) : 0
        )
        let couponOpacity = state.reduced ? visualMorph : (
            isCollecting
                ? visualMorph
                : clamp((visualMorph - presentation.couponFadeStart) / presentation.couponFadeSpan)
        )
        let stubOpacity = state.reduced ? 0 : (
            isCollecting
                ? 0
                : clamp((visualMorph - presentation.stubFadeStart) / presentation.stubFadeSpan)
        )
        let textOpacity = state.reduced ? visualMorph : (
            isCollecting
                ? visualMorph
                : clamp((visualMorph - presentation.textFadeStart) / presentation.textFadeSpan)
        )

        return ZStack(alignment: .topLeading) {
            ZStack {
                RoundedRectangle(cornerRadius: CGFloat(GumballCrankTheme.up.card.r) * s)
                    .fill(GumballCrankColor.hex(GumballCrankTheme.up.navy))
                    .opacity(panelOpacity)
                asset("coupon.png").frame(width: size.width, height: size.height).opacity(couponOpacity)
                asset("coin.png")
                    .frame(
                        width: CGFloat(GumballCrankTheme.presentation.stubCoinSize) * s,
                        height: CGFloat(GumballCrankTheme.presentation.stubCoinSize) * s
                    )
                    .position(
                        x: size.width / 2 + CGFloat(GumballCrankTheme.up.card.w * (GumballCrankTheme.up.stub.x - 0.5)) * s,
                        y: size.height / 2
                    )
                    .opacity(couponOpacity * stubOpacity)
            }
            .frame(width: size.width, height: size.height)
            .rotationEffect(.radians(state.reduced ? 0 : GumballCrankTheme.up.peel * sin(.pi * visualMorph)))
            .position(center)

            VStack(alignment: .leading, spacing: 0) {
                Text(GumballCrankTheme.copy.prize.uppercased())
                    .font(.system(size: CGFloat(GumballCrankTheme.presentation.eyebrowSize), weight: .bold))
                    .tracking(CGFloat(GumballCrankTheme.presentation.eyebrowTracking))
                    .foregroundStyle(GumballCrankColor.hex(GumballCrankTheme.presentation.eyebrowColor))
                Text(GumballCrankTheme.copy.amount)
                    .font(.system(size: CGFloat(GumballCrankTheme.presentation.amountSize), weight: .heavy))
                    .monospacedDigit()
                    .tracking(CGFloat(GumballCrankTheme.presentation.amountTracking))
                    .frame(height: CGFloat(GumballCrankTheme.presentation.amountLineHeight))
                    .foregroundStyle(.white)
                Text(GumballCrankTheme.copy.cardMeta)
                    .font(.system(size: CGFloat(GumballCrankTheme.presentation.metaSize)))
                    .monospacedDigit()
                    .foregroundStyle(.white.opacity(GumballCrankTheme.presentation.metaOpacity))
            }
            .frame(width: CGFloat(GumballCrankTheme.up.card.w * GumballCrankTheme.up.couponText.w) * s, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .offset(
                x: CGFloat(GumballCrankTheme.up.card.x + GumballCrankTheme.up.card.w * GumballCrankTheme.up.couponText.x) * s,
                y: CGFloat(GumballCrankTheme.up.card.y + GumballCrankTheme.up.card.h * GumballCrankTheme.up.couponText.y) * s
            )
            .opacity(textOpacity)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var hudLayer: some View {
        let card = GumballCrankTheme.up.card
        let textOpacity = state.reduced ? visualMorph : (
            isCollecting
                ? visualMorph
                : clamp((visualMorph - GumballCrankTheme.presentation.textFadeStart)
                    / GumballCrankTheme.presentation.textFadeSpan)
        )
        let counterOpacity = 1 - GumballCrankTheme.presentation.counterScrimFade
            * (isCollecting ? 0 : clamp(visualMorph))

        return ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                asset("coin.png")
                    .frame(
                        width: CGFloat(GumballCrankTheme.presentation.counterCoinSize),
                        height: CGFloat(GumballCrankTheme.presentation.counterCoinSize)
                    )
                    .padding(.trailing, CGFloat(GumballCrankTheme.presentation.counterCoinGap))
                ZStack {
                    Text(displayedCoins.formatted(.number.locale(Locale(
                        identifier: GumballCrankTheme.presentation.counterLocale
                    )).grouping(.automatic)))
                        .font(.system(size: CGFloat(GumballCrankTheme.presentation.counterNumberSize), weight: .bold))
                        .monospacedDigit()
                        .tracking(CGFloat(GumballCrankTheme.presentation.counterTracking))
                        .foregroundStyle(GumballCrankColor.hex(GumballCrankTheme.colors.ink))
                        .id(displayedCoins)
                        .transition(state.reduced ? .identity : .asymmetric(
                            insertion: .opacity.combined(with: .offset(
                                y: CGFloat(GumballCrankTheme.presentation.odometerOffset)
                            )).animation(.easeInOut(duration: GumballCrankTheme.presentation.odometerIn)),
                            removal: .opacity.combined(with: .offset(
                                y: -CGFloat(GumballCrankTheme.presentation.odometerOffset)
                            )).animation(.easeInOut(duration: GumballCrankTheme.presentation.odometerOut))
                        ))
                }
                .frame(height: CGFloat(GumballCrankTheme.presentation.counterNumberLineHeight))
                .clipped()
                Text(GumballCrankTheme.copy.coinsWord)
                    .font(.system(size: CGFloat(GumballCrankTheme.presentation.counterWordSize)))
                    .foregroundStyle(GumballCrankColor.hex(GumballCrankTheme.colors.ink).opacity(
                        GumballCrankTheme.presentation.counterInkOpacity
                    ))
                    .padding(.leading, CGFloat(GumballCrankTheme.presentation.counterWordGap))
            }
            .padding(.horizontal, CGFloat(GumballCrankTheme.presentation.counterPadding))
            .frame(height: CGFloat(GumballCrankTheme.presentation.counterHeight))
            .background(.white.opacity(GumballCrankTheme.presentation.counterBackgroundOpacity), in: Capsule())
            .scaleEffect(state.reduced ? 1 : CGFloat(
                1 + GumballCrankTheme.presentation.counterBumpScale * state.bump
            ))
            .opacity(counterOpacity)
            .offset(x: CGFloat(GumballCrankTheme.up.counter.left), y: safeTop + CGFloat(GumballCrankTheme.up.counter.top))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(Int(state.coins)) \(GumballCrankTheme.copy.coinsWord)")

            Button(action: onCollect) {
                HStack(spacing: CGFloat(GumballCrankTheme.presentation.collectGap)) {
                    asset("coin.png").frame(
                        width: CGFloat(GumballCrankTheme.presentation.collectCoinSize),
                        height: CGFloat(GumballCrankTheme.presentation.collectCoinSize)
                    )
                    Text(GumballCrankTheme.copy.collect)
                        .font(.system(size: CGFloat(GumballCrankTheme.presentation.collectSize), weight: .bold))
                }
                .foregroundStyle(GumballCrankColor.hex(GumballCrankTheme.presentation.collectInk))
                .padding(.horizontal, CGFloat(GumballCrankTheme.presentation.collectPadding))
                .frame(height: CGFloat(GumballCrankTheme.presentation.collectHeight))
                .background(GumballCrankColor.hex(GumballCrankTheme.presentation.collectBackground), in: Capsule())
            }
            .buttonStyle(GumballCrankCollectStyle()).disabled(!isReward).opacity(textOpacity)
            .position(
                x: width / 2,
                y: CGFloat(card.y + card.h + GumballCrankTheme.presentation.collectTopGap) * s
                    + CGFloat(GumballCrankTheme.presentation.collectHeight / 2)
            )
            .accessibilityLabel(GumballCrankTheme.copy.collect)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var flyingCoinsLayer: some View {
        let card = GumballCrankTheme.up.card
        let from = CGPoint(
            x: CGFloat(card.x + card.w * GumballCrankTheme.up.stub.x) * s,
            y: CGFloat(card.y + card.h * GumballCrankTheme.up.stub.y) * s
        )
        let to = CGPoint(
            x: CGFloat(
                GumballCrankTheme.up.counter.left + GumballCrankTheme.presentation.counterTargetX
            ) * s,
            y: safeTop + CGFloat(
                GumballCrankTheme.up.counter.top + GumballCrankTheme.presentation.counterTargetY
            ) * s
        )
        return ZStack {
            if !state.reduced {
                ForEach(0..<GumballCrankMotion.upT.coins, id: \.self) { index in
                let u = isCollecting
                    ? clamp((phaseT - Double(index) * GumballCrankMotion.upT.coinStagger) / GumballCrankMotion.upT.coinFly)
                    : 0
                if u > 0, u < 1 {
                    let e = easeInOut(u)
                    let control = CGPoint(
                        x: (from.x + to.x) / 2 + (
                            CGFloat(GumballCrankTheme.presentation.flyingCoinControlX)
                                - CGFloat(index) * CGFloat(GumballCrankTheme.presentation.flyingCoinControlXStep)
                        ) * s,
                        y: min(from.y, to.y) - (
                            CGFloat(GumballCrankTheme.presentation.flyingCoinControlY)
                                - CGFloat(index) * CGFloat(GumballCrankTheme.presentation.flyingCoinControlYStep)
                        ) * s
                    )
                    asset("coin.png")
                        .frame(
                            width: CGFloat(GumballCrankTheme.presentation.flyingCoinSize) * s,
                            height: CGFloat(GumballCrankTheme.presentation.flyingCoinSize) * s
                        )
                        .scaleEffect(CGFloat(1 - GumballCrankTheme.presentation.flyingCoinShrink * e))
                        .position(quadratic(from, control, to, e))
                }
                }
            }
        }
        .opacity(resumeFade)
        .allowsHitTesting(false)
    }

    // MARK: - Helpers

    private func updateDisplayedCoins() async {
        let next = state.coins
        guard next != displayedCoins else { return }
        guard next > displayedCoins, !state.reduced else {
            displayedCoins = next
            return
        }
        let increment = ceil(GumballCrankMotion.coins.add / Double(GumballCrankMotion.coins.countSteps))
        while displayedCoins < next, !Task.isCancelled {
            do { try await Task.sleep(for: .milliseconds(Int(GumballCrankMotion.coins.countStep))) }
            catch { return }
            withAnimation(.easeInOut(duration: GumballCrankTheme.presentation.odometerIn)) {
                displayedCoins = min(next, displayedCoins + increment)
            }
        }
    }

    private func asset(_ name: String, processed: Bool = false) -> Image {
        Image(uiImage: processed ? artwork.knob : artwork.image(name))
            .resizable().interpolation(.high)
    }

    private func scaledRect(_ token: GumballCrankTheme.Up.Panel) -> CGRect {
        CGRect(x: token.x * s, y: token.y * s, width: token.w * s, height: token.h * s)
    }

    private func scaledRect(_ token: GumballCrankTheme.Up.Card) -> CGRect {
        CGRect(x: token.x * s, y: token.y * s, width: token.w * s, height: token.h * s)
    }

    private func confettiName(for index: Int) -> String {
        ["slice-peach.png", "slice-passion.png", "peel.png"][index % GumballCrankTheme.up.confettiKinds]
    }

    private func confettiSize(_ index: Int) -> CGFloat {
        let size = GumballCrankMotion.confetti.size
        return CGFloat(size[0] + Double((index * 7) % 9) * (size[1] - size[0]) / 8)
            * s * CGFloat(GumballCrankTheme.presentation.confettiSpriteScale)
    }

    private func quadratic(_ start: CGPoint, _ control: CGPoint, _ end: CGPoint, _ t: Double) -> CGPoint {
        let p = CGFloat(t); let q = 1 - p
        return CGPoint(
            x: q * q * start.x + 2 * q * p * control.x + p * p * end.x,
            y: q * q * start.y + 2 * q * p * control.y + p * p * end.y
        )
    }

    private func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    private func smooth01(_ value: Double) -> CGFloat {
        let u = CGFloat(value)
        return u * u * (3 - 2 * u)
    }
    private func easeOutBack(_ value: Double) -> Double {
        let c = GumballCrankTheme.presentation.easeOutBackOvershoot
        return 1 + (c + 1) * pow(value - 1, 3) + c * pow(value - 1, 2)
    }
    private func easeOutCubic(_ value: Double) -> Double { 1 - pow(1 - value, 3) }
    private func easeInOut(_ value: Double) -> Double {
        value < 0.5 ? 8 * pow(value, 4) : 1 - pow(-2 * value + 2, 4) / 2
    }
}

/// Pre-decodes static textures and applies RN's knob matrix once.
private final class GumballCrankArtwork {
    static let shared = GumballCrankArtwork()

    private let images: [String: UIImage]
    let knob: UIImage

    private init() {
        let names = ["room.jpg", "globe-fill.png", "globe-glass.png", "knob.png", "pot.png", "coupon.png", "coin.png", "slice-peach.png", "slice-passion.png", "peel.png"]
        var decoded: [String: UIImage] = [:]
        for name in names { decoded[name] = Self.load(name) }
        images = decoded
        knob = Self.colorMatrix(decoded["knob.png"] ?? UIImage())
    }

    func image(_ name: String) -> UIImage { images[name] ?? UIImage() }

    private static func load(_ name: String) -> UIImage {
        let pieces = name.split(separator: ".", maxSplits: 1).map(String.init)
        let base = "GumballCrank-" + pieces[0]
        let ext = pieces.count == 2 ? pieces[1] : nil
        let url = Bundle.main.url(forResource: base, withExtension: ext)
            ?? Bundle.main.url(forResource: base, withExtension: ext, subdirectory: "Assets")
        guard let url, let image = UIImage(contentsOfFile: url.path) else {
            assertionFailure("Missing Gumball Crank asset: \(base).\(ext ?? "")")
            return UIImage()
        }
        return image.preparingForDisplay() ?? image
    }

    private static func colorMatrix(_ image: UIImage) -> UIImage {
        guard let cgImage = image.cgImage else { return image }
        let source = CIImage(cgImage: cgImage)
        let filter = CIFilter(name: "CIColorMatrix")!
        let matrix = GumballCrankTheme.replicaArt.knobMatrix
        filter.setValue(source, forKey: kCIInputImageKey)
        filter.setValue(CIVector(
            x: CGFloat(matrix[0]), y: CGFloat(matrix[1]), z: CGFloat(matrix[2]), w: CGFloat(matrix[3])
        ), forKey: "inputRVector")
        filter.setValue(CIVector(
            x: CGFloat(matrix[5]), y: CGFloat(matrix[6]), z: CGFloat(matrix[7]), w: CGFloat(matrix[8])
        ), forKey: "inputGVector")
        filter.setValue(CIVector(
            x: CGFloat(matrix[10]), y: CGFloat(matrix[11]), z: CGFloat(matrix[12]), w: CGFloat(matrix[13])
        ), forKey: "inputBVector")
        filter.setValue(CIVector(
            x: CGFloat(matrix[15]), y: CGFloat(matrix[16]), z: CGFloat(matrix[17]), w: CGFloat(matrix[18])
        ), forKey: "inputAVector")
        filter.setValue(CIVector(
            x: CGFloat(matrix[4]), y: CGFloat(matrix[9]), z: CGFloat(matrix[14]), w: CGFloat(matrix[19])
        ), forKey: "inputBiasVector")
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        let context = CIContext(options: [.cacheIntermediates: false, .workingColorSpace: colorSpace])
        guard let output = filter.outputImage, let result = context.createCGImage(output, from: source.extent) else { return image }
        return UIImage(cgImage: result, scale: image.scale, orientation: image.imageOrientation).preparingForDisplay() ?? image
    }
}

enum GumballCrankColor {
    static func hex(_ literal: String) -> Color {
        let value = UInt64(literal.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        return Color(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }

    static func rgba(_ literal: String) -> Color {
        guard literal.hasPrefix("rgba(") else { return hex(literal) }
        let values = literal.dropFirst(5).dropLast().split(separator: ",").map { Double($0.trimmingCharacters(in: .whitespaces)) ?? 0 }
        guard values.count == 4 else { return .clear }
        return Color(red: values[0] / 255, green: values[1] / 255, blue: values[2] / 255, opacity: values[3])
    }
}

/// RN's globeClip: the radius-156 globe intersected with the area above y=462.
private struct GumballCrankGlobeMask: Shape {
    let scale: CGFloat

    func path(in rect: CGRect) -> Path {
        let globe = GumballCrankTheme.replicaArt.globe
        let radius = CGFloat(globe.r - GumballCrankTheme.presentation.globeClipInset) * scale
        let circle = Path(ellipseIn: CGRect(
            x: CGFloat(globe.x) * scale - radius,
            y: CGFloat(globe.y) * scale - radius,
            width: radius * 2,
            height: radius * 2
        ))
        return circle.intersection(Path(CGRect(x: 0, y: 0, width: rect.width, height: CGFloat(globe.floor) * scale)))
    }
}

private struct GumballCrankCollectStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed
                ? CGFloat(GumballCrankTheme.presentation.collectPressedScale)
                : 1)
            .animation(
                .timingCurve(
                    0.23,
                    1,
                    0.32,
                    1,
                    duration: configuration.isPressed
                        ? GumballCrankTheme.presentation.collectPressDuration
                        : GumballCrankTheme.presentation.collectReleaseDuration
                ),
                value: configuration.isPressed
            )
    }
}
