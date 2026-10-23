import SwiftUI
import UIKit

/// One show in the list.
struct RubberChainListEvent: Hashable {
    let id: String
    let title: String
    let venue: String
    let city: String
    let month: String
    let day: String
    let times: String
    let price: String
    let blurb: String
}

/// A list of upcoming shows whose rows hang together like a chain of springs. Flung, the rows
/// drift apart, each lagging a little more than the one before it, and catch up with a springy
/// settle when the list slows; pulled past either end, the gaps open like a stretched spring.
/// Tapping a row highlights it and opens it on a spring to show the times, a line about the show
/// and its price.
struct RubberChainList: View {
    var events: [RubberChainListEvent] = RubberChainListEvent.samples
    /// Flings the list down and back up, then opens a row and closes it again.
    var autoPlay = false
    /// Called with a show when its row is opened by hand.
    var onOpen: ((RubberChainListEvent) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The list's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = RubberChainListEngine()
    /// True while the drag is alive; it resets without `onEnded` when the system cancels the touch.
    @GestureState private var touching = false

    private typealias M = RubberChainListMetrics

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: M.background).ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let listTop = insets.top + M.headerH
        let background = Color(hex: M.background)
        let stage = RubberChainListStage(
            top: listTop, bottom: size.height - insets.bottom, count: events.count,
            autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion,
            onOpen: { [events, onOpen] index in onOpen?(events[index]) }
        )
        let width = max(0, size.width - M.side * 2)
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, stage: stage)
                ZStack(alignment: .topLeading) {
                    // The rows settle in once: they fade up a few points when the screen opens.
                    ZStack(alignment: .topLeading) {
                        ForEach(0..<min(frame.built, events.count), id: \.self) { index in
                            row(index: index, frame: frame, width: width)
                        }
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .opacity(frame.shown)
                    .offset(y: reduceMotion ? 0 : (1 - frame.shown) * M.enterRise)

                    // The header covers the rows that scroll under it; only its words fade in.
                    header(count: events.count, top: insets.top)
                        .opacity(frame.shown)
                        .frame(width: size.width, height: listTop, alignment: .topLeading)
                        .background(background)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }

            LinearGradient(colors: [background, background.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: size.width, height: M.fade)
                .offset(y: listTop - 1)
            LinearGradient(colors: [background.opacity(0), background], startPoint: .top, endPoint: .bottom)
                .frame(width: size.width, height: insets.bottom + M.fade * 2)
                .offset(y: size.height - insets.bottom - M.fade * 2)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(true)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { engine.touchMoved($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onChange(of: touching) { _, isTouching in
            if !isTouching { engine.touchCancelled() }
        }
        .accessibilityElement()
        .accessibilityLabel("\(events.count) upcoming shows. Tap a show to open it.")
    }

    /// One show: the card grows on a spring when opened, and the details fade in inside it.
    private func row(index: Int, frame: RubberChainListFrame, width: CGFloat) -> some View {
        let open = index < frame.opens.count ? frame.opens[index] : 0
        let glow = index < frame.glows.count ? frame.glows[index] : 0
        let y = index < frame.ys.count ? frame.ys[index] : 0
        let e = M.clamp(open, 0, 1)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: M.radius, style: .continuous)
                .fill(M.mix(M.row, M.rowPressed, glow))
                .frame(width: width, height: M.rowH + M.extra * max(0, open))
            RubberChainListSummary(event: events[index])
                .equatable()
                .frame(width: width, height: M.rowH)
            RubberChainListDetails(event: events[index])
                .equatable()
                .frame(width: max(0, width - 28), alignment: .leading)
                .opacity(e * e)
                .accessibilityHidden(e < 0.5)
                .offset(x: 14, y: M.rowH - 2 + (reduceMotion ? 0 : (1 - e) * -10))
        }
        .frame(width: width, height: M.rowH + M.extra, alignment: .topLeading)
        .offset(x: M.side, y: y)
        .allowsHitTesting(false)
    }

    private func header(count: Int, top: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("LIVE NEAR YOU")
                .font(.system(size: 11, weight: .bold))
                .tracking(2)
                .foregroundStyle(Color(hex: M.accent))
            Text("Tonight & After")
                .font(.system(size: 34, weight: .bold))
                .tracking(-0.6)
                .foregroundStyle(Color(hex: M.text))
            Text("\(count) shows · October to December")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Color(hex: M.muted))
        }
        .padding(.top, top + 18)
        .padding(.horizontal, M.side + 4)
    }
}

/// The row's summary line: the photo, the act and where it plays, and the date chip.
struct RubberChainListSummary: View, Equatable {
    let event: RubberChainListEvent

    private typealias M = RubberChainListMetrics

    var body: some View {
        HStack(spacing: 14) {
            RubberChainListPhoto(address: rubberChainListThumb(event.id))
                .frame(width: M.thumb, height: M.thumb)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text(event.title)
                    .font(.system(size: 17, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(Color(hex: M.text))
                Text("\(event.venue) · \(event.city)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color(hex: M.muted))
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(spacing: 0) {
                Text(event.month)
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.2)
                    .foregroundStyle(Color(hex: M.accent))
                Text(event.day)
                    .font(.system(size: 22, weight: .bold).monospacedDigit())
                    .foregroundStyle(Color(hex: M.text))
            }
            .frame(width: 52, height: 60)
            .background(Color(hex: M.chip), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .padding(.horizontal, 12)
    }
}

/// What an open row shows: the times, a line about the show, the price and a tickets button.
struct RubberChainListDetails: View, Equatable {
    let event: RubberChainListEvent

    private typealias M = RubberChainListMetrics
    // The blurb is set 20 pt a line, as in the RN version.
    private static let blurbSpacing = 20 - (UIFont.systemFont(ofSize: 14).lineHeight)

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(event.times)
                .font(.system(size: 13, weight: .semibold).monospacedDigit())
                .foregroundStyle(Color(hex: M.accent))
            Text(event.blurb)
                .font(.system(size: 14))
                .lineSpacing(Self.blurbSpacing)
                .padding(.vertical, Self.blurbSpacing / 2)
                .foregroundStyle(Color(hex: M.text).opacity(0.78))
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Text(event.price)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color(hex: M.text))
                Spacer()
                Text("Tickets")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(hex: 0x1a1408))
                    .padding(.horizontal, 18)
                    .frame(height: 36)
                    .background(Color(hex: M.accent), in: RoundedRectangle(cornerRadius: 18, style: .circular))
            }
            .padding(.top, 6)
        }
    }
}

/// A photo from the shared cache, loaded on first sight and faded in over 0.25 s.
private struct RubberChainListPhoto: View {
    let address: String
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color(hex: RubberChainListMetrics.chip)
            if let image = image ?? RubberChainListPhotos.cached(address) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .transition(.opacity)
            }
        }
        .task(id: address) {
            guard RubberChainListPhotos.cached(address) == nil else { return }
            let loaded = await RubberChainListPhotos.load(address)
            withAnimation(.linear(duration: 0.25)) { image = loaded }
        }
    }
}

// MARK: - Samples

func rubberChainListThumb(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=220&h=220&q=80"
}

extension RubberChainListEvent {
    static let samples: [RubberChainListEvent] = [
        .init(id: "photo-1459749411175-04bf5292ceea", title: "Saltwater Choir", venue: "Harbor Hall", city: "Lisbon", month: "OCT", day: "18", times: "Doors 19:30 · Show 20:30", price: "from €28", blurb: "Twelve voices and a brass quartet, playing the new record from start to finish."),
        .init(id: "photo-1563841930606-67e2bce48b78", title: "Neon Orchard", venue: "The Lantern", city: "Porto", month: "OCT", day: "21", times: "Doors 20:00 · Show 21:00", price: "from €22", blurb: "A synth-pop four-piece on their first tour since the debut sold out twice."),
        .init(id: "photo-1533174072545-7a4b6ad7a6c3", title: "Marigold Static", venue: "Canal Room", city: "Amsterdam", month: "OCT", day: "24", times: "Doors 19:00 · Show 20:15", price: "from €30", blurb: "Fuzz guitars, a string section and a light show built from old slide projectors."),
        .init(id: "photo-1493225457124-a3eb161ffa5f", title: "The Quiet Engines", venue: "Vault 9", city: "Berlin", month: "OCT", day: "29", times: "Doors 22:00 · Set 23:30", price: "from €26", blurb: "An all-night live techno set from the duo who never play the same show twice."),
        .init(id: "photo-1470229722913-7c0e2dbbafd3", title: "Ivy Lane Ensemble", venue: "Glasshouse", city: "Copenhagen", month: "NOV", day: "02", times: "Doors 18:30 · Show 19:30", price: "from €35", blurb: "Chamber jazz in a greenhouse, with the audience seated among the palms."),
        .init(id: "photo-1429962714451-bb934ecdc4ec", title: "Hollow Moon", venue: "Riverside Shed", city: "Ghent", month: "NOV", day: "06", times: "Doors 19:30 · Show 20:30", price: "from €24", blurb: "Slow, heavy and very loud. Earplugs at the door, free of charge."),
        .init(id: "photo-1514525253161-7a46d19cd819", title: "Copper & Kin", venue: "Old Tram Depot", city: "Vienna", month: "NOV", day: "09", times: "Doors 19:00 · Show 20:00", price: "from €32", blurb: "A family folk band of three generations, back with fiddles and a full choir."),
        .init(id: "photo-1565035010268-a3816f98589a", title: "Velvet Signal", venue: "Sala Norte", city: "Madrid", month: "NOV", day: "14", times: "Doors 21:00 · Show 22:00", price: "from €27", blurb: "Dream pop under a ceiling of mirror balls, with support from Lake Vesper."),
        .init(id: "photo-1524368535928-5b5e00ddc76b", title: "North Atlas", venue: "Pier 4", city: "Hamburg", month: "NOV", day: "20", times: "Doors 19:30 · Show 20:45", price: "from €29", blurb: "Post-rock on the water: a three-hour set as the ferries go by."),
        .init(id: "photo-1540039155733-5bb30b53aa14", title: "Paper Tigers Club", venue: "The Annex", city: "Brussels", month: "NOV", day: "27", times: "Doors 20:00 · Show 21:00", price: "from €18", blurb: "Garage punk in a room for two hundred. Expect to leave with less voice."),
        .init(id: "photo-1506157786151-b8491531f063", title: "Lumen Field Day", venue: "Cinder Park", city: "Milan", month: "DEC", day: "04", times: "Gates 14:00 · Last set 23:00", price: "from €40", blurb: "Nine acts, two stages and a winter market between them."),
        .init(id: "photo-1522158637959-30385a09e0da", title: "Saint Ember", venue: "Chapel Works", city: "Dublin", month: "DEC", day: "12", times: "Doors 19:00 · Show 20:00", price: "from €25", blurb: "Candlelit piano and voice, closing the year where the songs were written."),
    ]
}
