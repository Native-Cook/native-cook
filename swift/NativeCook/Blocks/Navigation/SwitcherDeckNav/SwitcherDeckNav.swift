import SwiftUI
import UIKit

/// A photo journal with four pages and an app switcher of its own. Pulling up the handle at the
/// bottom shrinks the page under the finger into a card while the other pages slide in beside
/// it, fanned out as a deck in perspective with a title chip over each. The deck scrolls
/// sideways with momentum; tapping a page zooms it back to the full screen, and flicking one up
/// closes it while the rest close the gap.
struct SwitcherDeckNav: View {
    /// Opens the deck, scrolls it, opens another page and goes back, on a loop.
    var autoPlay = false
    /// Called with a page's title when it is opened from the deck.
    var onOpenPage: ((String) -> Void)? = nil

    private let pages = SwitcherDeckNavPageInfo.samples

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The deck's motion lives in a plain object stepped once a frame, like the RN frame callback:
    // nothing in it is observed, the timeline redraws every frame anyway.
    @State private var engine = SwitcherDeckNavEngine()

    var body: some View {
        // The block draws under the system bars and keeps its pages' content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private var settings: SwitcherDeckNavSettings {
        let titles = pages.map(\.title)
        let callback = onOpenPage
        return SwitcherDeckNavSettings(
            count: pages.count,
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            onOpenPage: callback.map { open in { open(titles[$0]) } }
        )
    }

    private func stage(size: CGSize, top: CGFloat, bottom: CGFloat) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, width: size.width, height: size.height, bottom: bottom, settings: settings)
            ZStack(alignment: .topLeading) {
                SwitcherDeckNavBackdrop(frame: frame, width: size.width, top: top, bottom: bottom)
                    .opacity(min(max(frame.open, 0), 1))
                ForEach(0..<pages.count, id: \.self) { id in
                    SwitcherDeckNavCard(id: id, page: pages[id], state: frame.cards[id], width: size.width, height: size.height, top: top, bottom: bottom)
                        .zIndex(frame.cards[id].order)
                }
                Circle()
                    .fill(Color.white.opacity(0.35))
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1.5))
                    .frame(width: 46, height: 46)
                    .scaleEffect(1 - 0.18 * frame.fingerPress)
                    .opacity(frame.fingerAlpha * 0.9)
                    .offset(x: frame.fingerX - 23, y: frame.fingerY - 23)
                    .zIndex(100)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: 0x0d0c0b))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .accessibilityElement()
        .accessibilityLabel("Photo journal with \(pages.count) pages. Swipe up from the handle at the bottom to show every page as a deck; tap a page to open it, flick it up to close it.")
    }
}

/// The warm dark backdrop of the deck, its title and the line under it.
private struct SwitcherDeckNavBackdrop: View {
    let frame: SwitcherDeckNavFrame
    let width: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    private typealias T = SwitcherDeckNavType

    var body: some View {
        let cream = Color(hex: 0xf4efe6)
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [Color(hex: 0x23201c), Color(hex: 0x0d0c0b)], startPoint: .top, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 2) {
                Text("SLOW DEPARTURES")
                    .font(T.sans(11, .bold))
                    .kerning(1.8)
                    .foregroundStyle(Color(hex: 0xc98a64))
                Text("\(frame.openCount) open \(frame.openCount == 1 ? "page" : "pages")")
                    .font(T.serif(26, bold: true))
                    .kerning(-0.3)
                    .foregroundStyle(cream)
            }
            .padding(.horizontal, 24)
            .padding(.top, top + 14)

            Group {
                if frame.closedCount > 0 {
                    Text("Reopen \(frame.closedCount) closed \(frame.closedCount == 1 ? "page" : "pages")")
                        .font(T.sans(13, .semibold))
                        .foregroundStyle(cream)
                        .padding(.horizontal, 16)
                        .frame(height: 36)
                        .background(
                            Capsule()
                                .fill(cream.opacity(0.12))
                                .overlay(Capsule().strokeBorder(cream.opacity(0.25), lineWidth: 1 / UIScreen.main.scale))
                        )
                } else {
                    Text("Tap a page to open it · Flick it up to close")
                        .font(T.sans(13, .medium))
                        .foregroundStyle(cream.opacity(0.5))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            .padding(.bottom, bottom + 18)
        }
    }
}

/// One page of the app: full screen while it is the open one, a turned card in the deck otherwise.
private struct SwitcherDeckNavCard: View {
    let id: Int
    let page: SwitcherDeckNavPageInfo
    let state: SwitcherDeckNavCardState
    let width: CGFloat
    let height: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    private typealias M = SwitcherDeckNavMetrics

    var body: some View {
        let unit = 1 / M.deckScale
        let shape = RoundedRectangle(cornerRadius: state.radius, style: .circular)
        ZStack(alignment: .topLeading) {
            shape
                .fill(page.tint)
                .shadow(color: .black.opacity(state.radius > 0 ? 0.55 : 0), radius: 30, y: 24)
            SwitcherDeckNavPage(index: id, width: width, height: height, top: top, bottom: bottom)
                .equatable()
                .overlay(Color(hex: 0x0d0c0b).opacity(state.dim))
                .clipShape(shape)
            chip(unit: unit)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        // The page fades as one piece, so its shadow never shows through it.
        .compositingGroup()
        .opacity(state.alpha)
        .projectionEffect(state.projection)
    }

    private func chip(unit: Double) -> some View {
        let height = M.chipHeight * unit
        return HStack(spacing: 7 * unit) {
            RoundedRectangle(cornerRadius: 4 * unit, style: .circular)
                .fill(page.tint)
                .overlay(RoundedRectangle(cornerRadius: 4 * unit, style: .circular).strokeBorder(Color.white.opacity(0.35), lineWidth: 1.2 * unit))
                .frame(width: 14 * unit, height: 14 * unit)
            Text(page.title)
                .font(SwitcherDeckNavType.sans(13 * unit, .semibold))
                .kerning(0.2)
                .foregroundStyle(Color(hex: 0xf4efe6))
                .fixedSize()
        }
        .padding(.horizontal, 11 * unit)
        .frame(height: height)
        .background(
            Capsule()
                .fill(Color(hex: 0xfffaf0, opacity: 0.12))
                .overlay(Capsule().strokeBorder(Color(hex: 0xfffaf0, opacity: 0.18), lineWidth: 1.5))
        )
        .offset(y: -(M.chipHeight + M.chipGap) * unit + (1 - state.chip) * 30)
        .opacity(state.chip)
    }
}
