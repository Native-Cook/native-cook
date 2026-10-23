import SwiftUI
import UIKit

/// The source's compact feeling slider, faint activity chooser and line-drawn desk reveal.
struct MoodShapeMixer: View {
    var autoPlay = false
    var renderTime: Double? = nil
    static let loop = MoodShapeMixerModel.loop
    private typealias M = MoodShapeMixerModel
    private typealias A = MoodShapeMixerArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var origin = Date()
    @State private var autoStart: Date? = nil
    @State private var chooser = Anim.still(1)
    @State private var panel = Anim.still(0)
    @State private var panelClosing = false
    @State private var panelOrigin = 0.0
    @State private var mood = Anim.still(5)
    @State private var dragStart: Double? = nil
    @State private var lastMood = 5
    @State private var asleep = true
    @State private var resume: Task<Void, Never>? = nil
    @State private var tick = UISelectionFeedbackGenerator()

    private struct Anim {
        var from: Double, to: Double, start: Date, duration: Double, curve: Int = 0
        func at(_ date: Date) -> Double {
            guard duration > 0 else { return to }
            let p = M.segment(date.timeIntervalSince(start), 0, duration)
            let e = curve == 1 ? 1 - pow(1 - p, 3) : curve == 2 ? pow(p, 3) : M.ease(p)
            return M.mix(from, to, e)
        }
        static func still(_ value: Double) -> Anim { Anim(from: value, to: value, start: .distantPast, duration: 0) }
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                // The room is static and sits outside the frame-driven subtree.
                Canvas { context, _ in A.room(&context) }
                    .frame(width: 393, height: 852)
                    .accessibilityHidden(true)
                TimelineView(.animation(paused: renderTime != nil || reduceMotion || asleep)) { timeline in
                    let s = shown(at: timeline.date)
                    ZStack(alignment: .topLeading) {
                        Canvas { context, _ in A.stage(&context, s, still: reduceMotion) }
                            .frame(width: 393, height: 852)
                            .accessibilityHidden(true)
                        Button { openPanel() } label: { Color.clear.contentShape(Rectangle()) }
                            .frame(width: M.itemWidth(s.m), height: 68)
                            .position(x: 196.5, y: 686)
                            .buttonStyle(.plain)
                            .allowsHitTesting(s.chooser > 0.5 && s.panel < 0.1)
                            .accessibilityHidden(s.chooser < 0.5 || s.panel > 0.1)
                            .accessibilityLabel("Feeling, \(M.moods[M.nearest(s.m)].name.capitalized)")
                        Color.clear
                            .frame(width: M.mix(M.itemWidth(s.m), 372.6, s.closing ? 1 : s.panel), height: 68)
                            .contentShape(Rectangle())
                            .position(x: 196.5, y: M.mix(686, 690, s.closing ? 1 : s.panel))
                            .gesture(slider)
                            .allowsHitTesting(s.panel > 0.8)
                            .accessibilityElement()
                            .accessibilityLabel("Feeling slider")
                            .accessibilityValue(M.moods[M.nearest(s.m)].name.capitalized)
                            .accessibilityHidden(s.panel <= 0.8)
                            .accessibilityAdjustableAction { direction in selectMood(s.m.rounded() + (direction == .increment ? 1 : -1)) }
                        Button { close() } label: { Color.clear.contentShape(Rectangle()) }
                            .frame(width: 48, height: 48)
                            .position(x: 196.5, y: 784)
                            .buttonStyle(.plain)
                            .allowsHitTesting(s.chooser > 0.1)
                            .accessibilityHidden(s.chooser <= 0.1)
                            .accessibilityLabel(s.panel > 0.1 ? "Close feeling picker" : "Close activities")
                        Button { openPanel() } label: { Color.clear.contentShape(Rectangle()) }
                            .frame(width: 138, height: 50)
                            .position(x: 196.5, y: 790)
                            .buttonStyle(.plain)
                            .allowsHitTesting(s.chooser < 0.1)
                            .accessibilityHidden(s.chooser >= 0.1)
                            .accessibilityLabel("Feeling, \(M.moods[M.nearest(s.m)].name.capitalized)")
                    }
                    .frame(width: 393, height: 852, alignment: .topLeading)
                }
            }
            .frame(width: 393, height: 852, alignment: .topLeading)
            .scaleEffect(x: geometry.size.width / 393, y: geometry.size.height / 852, anchor: .topLeading)
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            .clipped()
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear { reset(); tick.prepare() }
        .onChange(of: reduceMotion) { _, _ in reset() }
        .onDisappear { resume?.cancel() }
    }

    private func reset() {
        origin = .now
        chooser = .still(1)
        panel = .still(reduceMotion ? 1 : 0)
        panelClosing = false; panelOrigin = 0
        mood = .still(5)
        autoStart = autoPlay && !reduceMotion ? origin : nil
        asleep = !autoPlay || reduceMotion
    }
    private func shown(at date: Date) -> M.Shown {
        if let renderTime { return M.scripted(renderTime) }
        if let autoStart, date >= autoStart, !reduceMotion { return M.scripted(date.timeIntervalSince(autoStart)) }
        return M.Shown(chooser: chooser.at(date), panel: panel.at(date), m: mood.at(date), clock: date.timeIntervalSince(origin), closing: panelClosing, origin: panelOrigin)
    }
    private func takeOver() -> M.Shown {
        resume?.cancel()
        let s = shown(at: .now)
        autoStart = nil
        chooser = .still(s.chooser); panel = .still(s.panel); mood = .still(s.m)
        panelClosing = s.closing; panelOrigin = s.origin
        asleep = false
        return s
    }
    private func animate(_ from: Double, _ to: Double, _ duration: Double, curve: Int = 0) -> Anim {
        Anim(from: from, to: to, start: .now, duration: reduceMotion ? 0 : duration, curve: curve)
    }
    private func openPanel() {
        let s = takeOver()
        panelClosing = false
        panelOrigin = s.panel < 0.1 && s.chooser < 0.5 ? 1 : s.origin
        chooser = animate(s.chooser, 1, 0.3)
        panel = animate(s.panel, 1, 0.4, curve: 1)
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
        scheduleResume()
    }
    private func close() {
        let s = takeOver()
        if s.panel > 0.1 {
            panelClosing = true; panelOrigin = 0
            panel = animate(s.panel, 0, 0.37)
        }
        else { chooser = animate(s.chooser, 0, 0.4) }
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
        scheduleResume()
    }
    private func selectMood(_ value: Double) {
        let s = takeOver(), target = M.clamp(value)
        mood = animate(s.m, target, 0.26, curve: 1)
        tick.selectionChanged()
        scheduleResume()
    }
    private var slider: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if dragStart == nil {
                    let s = takeOver(); dragStart = s.m; lastMood = M.nearest(s.m)
                }
                let m = M.clamp((dragStart ?? 5) + value.translation.width / 47.8)
                mood = .still(m)
                let selected = M.nearest(m)
                if selected != lastMood { tick.selectionChanged(); lastMood = selected }
            }
            .onEnded { _ in
                dragStart = nil
                mood = animate(mood.to, mood.to.rounded(), 0.26, curve: 1)
                scheduleResume()
            }
    }
    private func scheduleResume() {
        resume?.cancel()
        guard !reduceMotion else { asleep = true; return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let s = shown(at: .now)
            panelClosing = true; panelOrigin = 0
            chooser = animate(s.chooser, 1, 0.5)
            panel = animate(s.panel, 0, 0.5)
            mood = animate(s.m, 5, 0.5)
            try? await Task.sleep(for: .seconds(0.55))
            guard !Task.isCancelled else { return }
            panelClosing = false; panelOrigin = 0
            autoStart = autoPlay ? .now : nil
            asleep = !autoPlay
        }
    }
}
