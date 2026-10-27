import SwiftUI
import UIKit

/*
 Folio Feature Library: a light app-library home for invented apps. Explore holds a Featured carousel of glass-orb cards,
 an Invites card, Popular apps with category chips and a Recently added list, under a frosted Home and Explore pill with
 a plus button. Opening a feature fills the screen through a loading lens into a library (a header card with Remix and
 Get, a filtered book list and Generate New); Generate New opens a topic sheet whose first pick suggests more topics,
 then a new book is created and opens into a readable detail with sections and its author.

 autoPlay (loop = 39 s), the guided loop of the React Native block: the carousel turns at 0.5, 1.5 and 2.5 s, the page
 scrolls down 3.5 to 6.6 s and back at 8 s, the first card opens at 9.75 s from its own frame (280 ms) through a neutral
 lens that turns into the green book orb, the library appears at 12.5 s and its header card contracts into the toolbar
 orb 13.5 to 14.5 s. Topics open at 16 s, Productivity is picked at 17.25 s, creation runs 18.25 to 22.5 s with a status
 pill, the cover arrives at 24 s and the detail pushes in at 26.5 s (260 ms); its sections fill from 28.5 s, the author
 portrait at 32.9 s, and a reading pass scrolls down to the author and back 29.8 to 34.6 s before returning to the
 library at 35.5 s and home at 36.75 s.

 Touch: any touch takes over; two quiet seconds later (while still on Explore) the loop starts again. Once a person has
 opened a library, the loop stays stopped so created books and favourites are kept. Reduced motion: no loop, the screens
 change with fades.

 How it is built: the component's state, effects and timers (FolioFeatureLibraryEngine) run on a clock the engine steps
 itself, so every frame is a function of the loop time and `previewTime` can freeze any frame. The screens are SwiftUI
 views of the engine's state and of the frame's animated numbers; the scroll views are UIScrollViews that the engine takes
 to exact offsets.
*/
struct FolioFeatureLibrary: View {
    /// The length of the guided loop, in seconds (`LOOP`).
    static let loop: Double = 39

    /// Plays the guided loop by itself.
    var autoPlay = false
    /// Holds the loop at this time, in seconds, and ignores touches.
    var previewTime: Double?
    /// Called when a person has generated a book, with the topics they picked (`onBookCreated`).
    var onBookCreated: (([String]) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.displayScale) private var displayScale
    @State private var engine = FolioFeatureLibraryEngine()

    init(autoPlay: Bool = false, previewTime: Double? = nil, onBookCreated: (([String]) -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onBookCreated = onBookCreated
        _ = FolioFeatureLibraryAssets.fontRegistered
        _ = FolioFeatureLibraryAssets.images
    }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(size: size, insets: insets)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(Color(folio: 0xEFEFEF).ignoresSafeArea())
        // `<StatusBar style="dark" />`: dark text on the light screen.
        .preferredColorScheme(.light)
        .onChange(of: scenePhase) { _, phase in
            engine.setForeground(phase == .active)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let environment = FolioFeatureLibraryEnvironment(
            width: Double(size.width),
            height: Double(size.height),
            insetTop: Double(insets.top),
            insetBottom: Double(insets.bottom),
            pixelScale: Double(displayScale),
            type: FolioFeatureLibraryType(dynamicTypeSize),
            reduced: reduceMotion,
            reduceTransparency: reduceTransparency,
            autoPlay: autoPlay,
            previewTime: previewTime,
            onBookCreated: onBookCreated
        )
        return TimelineView(.animation(paused: scenePhase != .active)) { timeline in
            let frame = engine.step(date: timeline.date, environment: environment)
            scene(frame)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
            if let end = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
                engine.keyboardHeight = max(0, Double(size.height) - Double(end.minY))
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            engine.keyboardHeight = 0
        }
    }

    // MARK: - Layers (lines 490-577)

    private func scene(_ f: FolioFeatureLibraryFrame) -> some View {
        let e = engine
        return ZStack(alignment: .topLeading) {
            Color(folio: 0xEFEFEF)
            FolioFeatureLibraryTouchLayer(engine: e)
            explore(f)
            FolioFeatureLibraryFloatingNav(e: e, f: f)
            appSurface(f)
            cardBridge(f)
            if e.topicsMounted(at: f.time) {
                FolioFeatureLibraryTopicsSheet(e: e, f: f)
            }
            if e.noticeMounted {
                FolioFeatureLibraryNoticeModal(e: e, f: f)
            }
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height), alignment: .topLeading)
        .clipped()
    }

    /// Explore: the page in its scroll view, with blurred edges while it overflows.
    private func explore(_ f: FolioFeatureLibraryFrame) -> some View {
        let e = engine
        return FolioFeatureLibraryBlurViewport(engine: e, scroller: .outer, enabled: e.exploreVisible, reduceTransparency: f.reduceTransparency) {
            FolioFeatureLibraryScroller(engine: e, id: .outer, reportsDrags: true, interactive: e.exploreVisible, revision: e.layoutRevision) {
                FolioFeatureLibraryExploreContent(e: e, f: f)
            }
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height))
        .accessibilityHidden(!e.exploreVisible)
    }

    /// `appSurface` (line 510): the loading screen, the library and the detail, grown from the card that was opened.
    private func appSurface(_ f: FolioFeatureLibraryFrame) -> some View {
        let e = engine
        let inactive = e.screen == .explore || e.topicsOpen || e.notice != nil
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                if e.screen == .loading {
                    FolioFeatureLibraryLoader(e: e, f: f)
                } else {
                    FolioFeatureLibraryLibraryLayer(e: e, f: f)
                }
                FolioFeatureLibraryDetailLayer(e: e, f: f)
            }
            .frame(width: CGFloat(f.width), height: CGFloat(f.height), alignment: .topLeading)
            .opacity(f.appContentOpacity)
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height), alignment: .topLeading)
        .background(Color(folio: 0xF6F6F6))
        .clipped()
        .scaleEffect(x: CGFloat(f.appSX), y: CGFloat(f.appSY))
        .offset(x: CGFloat(f.appTX), y: CGFloat(f.appTY))
        .opacity(f.appOpacity)
        .allowsHitTesting(!inactive)
        .accessibilityHidden(inactive)
    }

    /// The copy of the card that rides out ahead of the surface (line 556).
    private func cardBridge(_ f: FolioFeatureLibraryFrame) -> some View {
        let e = engine
        let origin = e.origin
        return FolioFeatureLibraryFeatureCard(
            item: FolioFeatureLibraryData.features[e.feature],
            width: origin.width,
            height: origin.height,
            type: f.type,
            clipsContent: true
        )
        .scaleEffect(CGFloat(f.bridgeScale))
        .offset(x: CGFloat(f.bridgeTX), y: CGFloat(f.bridgeTY))
        .offset(x: CGFloat(origin.x), y: CGFloat(origin.y))
        .opacity(f.bridgeOpacity)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
