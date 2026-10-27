import SwiftUI
import UIKit

/// The list of books inside the library's scroll view (lines 515-519).
struct FolioFeatureLibraryLibraryContent: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            filterRow
            if e.generated {
                generatedBook
            }
            noticingBook
        }
        .padding(.top, CGFloat(e.introHeight + 18))
        .padding(.horizontal, 18)
        .padding(.bottom, CGFloat(130 + f.insetBottom))
        .frame(maxWidth: .infinity, minHeight: CGFloat(f.height + e.introHeight), alignment: .topLeading)
    }

    /// `filterRow` (line 516).
    private var filterRow: some View {
        HStack(spacing: 0) {
            FolioFeatureLibraryLabel(text: "FILTER LIBRARY", style: FolioFeatureLibraryStyles.filterLabel, type: f.type)
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                FolioFeatureLibraryLabel(text: "All Books", style: FolioFeatureLibraryStyles.allBooksText, type: f.type)
                FolioFeatureLibraryIcon(name: "chevron-down", size: 15, color: Color(folio: 0x2A2A2A), type: f.type)
            }
            .padding(.horizontal, 14)
            .frame(height: 32)
            .background(FolioFeatureLibrarySurface(radius: 16, shadows: FolioFeatureLibraryShadows.single(y: 1, blur: 4, opacity: 0.06)))
        }
        .padding(.horizontal, 2)
        .padding(.bottom, 18)
    }

    /// A book's card: its cover and three lines (`bookCard`, line 639).
    private func bookCard<Cover: View>(cover: Cover, book: FolioFeatureLibraryBook, nameLines: Int?) -> some View {
        HStack(alignment: .top, spacing: 12) {
            cover
            VStack(alignment: .leading, spacing: 0) {
                FolioFeatureLibraryLabel(text: book.title, style: FolioFeatureLibraryStyles.bookName, type: f.type, lines: nameLines)
                FolioFeatureLibraryLabel(text: book.author, style: FolioFeatureLibraryStyles.bookMeta, type: f.type)
                    .padding(.top, 3)
                FolioFeatureLibraryLabel(text: book.blurb, style: FolioFeatureLibraryStyles.bookNote, type: f.type, lines: 3)
                    .padding(.top, 10)
            }
            .padding(.top, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .background(FolioFeatureLibrarySurface(radius: 20, shadows: FolioFeatureLibraryShadows.card))
    }

    /// The book the topic sheet made: it drops in from 14 pt above (`FadeInDown.duration(420).easing(OUT)`), with a
    /// grey placeholder that a highlight sweeps until the cover fades in (`FadeIn.duration(360)`).
    private var generatedBook: some View {
        let time = f.time
        let enter = e.entering("book", duration: 0.42, curve: .out, at: time)
        let coverFade = e.entering("cover", duration: 0.36, at: time)
        let cover = Group {
            if e.coverReady {
                FolioFeatureLibraryCover(book: .rituals, large: false)
                    .opacity(coverFade)
            } else {
                placeholder
            }
        }
        return Button {
            e.openDetail(.rituals)
        } label: {
            bookCard(cover: cover, book: FolioFeatureLibraryData.rituals, nameLines: nil)
        }
        .buttonStyle(FolioFeatureLibraryPressStyle(scale: 0.98, reduced: f.reduced))
        .accessibilityLabel("Read Small Rituals")
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { measured in
            e.bookCardHeight = Double(measured) + 12
        }
        .padding(.bottom, 12)
        .opacity(enter)
        .offset(y: CGFloat(-14 * (1 - enter)))
    }

    /// `coverPlaceholder` (line 644) and `shimmer`: 74 by 105 of grey with a white highlight sliding across it.
    private var placeholder: some View {
        ZStack(alignment: .leading) {
            Color(folio: 0xD6D6D6)
            LinearGradient(
                colors: [Color(folio: 0xFFFFFF, alpha: 0), Color(folio: 0xFFFFFF, alpha: Double(0xCC) / 255), Color(folio: 0xFFFFFF, alpha: 0)],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: 60)
            .offset(x: CGFloat(f.shimmerX))
            .opacity(f.shimmerOpacity)
        }
        .frame(width: 74, height: 105)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    /// The tour's card slides down when the new one is inserted above it (`LinearTransition.duration(320).easing(OUT)`).
    private var noticingBook: some View {
        let time = f.time
        let progress = e.entering("bookShift", duration: 0.32, curve: .out, at: time)
        let slide = e.since["bookShift"] != nil ? -e.bookCardHeight * (1 - progress) : 0
        return Button {
            e.openDetail(.noticing)
        } label: {
            bookCard(cover: FolioFeatureLibraryCover(book: .noticing, large: false), book: FolioFeatureLibraryData.noticing, nameLines: 2)
        }
        .buttonStyle(FolioFeatureLibraryPressStyle(scale: 0.98, reduced: f.reduced))
        .accessibilityLabel("Read A Brief Tour of Almost Everything")
        .padding(.bottom, 12)
        .offset(y: CGFloat(slide))
    }
}

/// The library screen's layers above the list: the header card, the app bar and the Generate New button (lines
/// 513-537), all of which let a touch through where they have no control (`pointerEvents="box-none"`).
struct FolioFeatureLibraryLibraryLayer: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        ZStack(alignment: .topLeading) {
            list
            headerCard
            appBar
            footer
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height), alignment: .topLeading)
        .offset(x: CGFloat(f.libraryTX))
        .allowsHitTesting(e.libraryVisible)
        .accessibilityHidden(!e.libraryVisible)
    }

    private var list: some View {
        FolioFeatureLibraryBlurViewport(engine: e, scroller: .library, enabled: e.libraryVisible, reduceTransparency: f.reduceTransparency) {
            FolioFeatureLibraryScroller(engine: e, id: .library, interactive: e.libraryVisible, revision: e.layoutRevision) {
                FolioFeatureLibraryLibraryContent(e: e, f: f)
            }
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height))
        .offset(y: CGFloat(f.listShift))
    }

    // MARK: Header card

    /// `headerCard` (line 625): the app's card, which contracts into a small tile around its orb.
    private var headerCard: some View {
        let radius = f.headerRadius
        return ZStack(alignment: .topLeading) {
            Color.white
                .allowsHitTesting(false)
            Image(uiImage: FolioFeatureLibraryAssets.image("glass-book-blue"))
                .resizable()
                .scaledToFit()
                .frame(width: 60, height: 60)
                .allowsHitTesting(false)
                .offset(x: CGFloat(f.orbLeft), y: CGFloat(f.orbTop))
            headerContent
        }
        .frame(width: CGFloat(f.headerWidth), height: CGFloat(f.headerHeight), alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: CGFloat(radius)))
        .background(FolioFeatureLibrarySurface(radius: radius, shadows: FolioFeatureLibraryShadows.card).allowsHitTesting(false))
        .offset(x: CGFloat(f.headerLeft), y: CGFloat(f.headerTop))
    }

    private var feature: FolioFeatureLibraryFeature {
        FolioFeatureLibraryData.features[e.feature]
    }

    /// `headerContent` (line 523): who made the app, its name and copy, and Remix and Get.
    private var headerContent: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                chip {
                    Image(uiImage: FolioFeatureLibraryAssets.avatar(0))
                        .resizable()
                        .scaledToFill()
                        .frame(width: 18, height: 18)
                        .clipShape(Circle())
                    FolioFeatureLibraryLabel(text: "mirawells", style: FolioFeatureLibraryStyles.chipText, type: f.type)
                }
                chip {
                    FolioFeatureLibraryIcon(name: "globe-outline", size: 15, color: Color(folio: 0x2A2A2A), type: f.type)
                    FolioFeatureLibraryLabel(text: "Public", style: FolioFeatureLibraryStyles.chipText, type: f.type)
                }
            }
            .allowsHitTesting(false)
            FolioFeatureLibraryLabel(text: feature.name, style: FolioFeatureLibraryStyles.libraryTitle, type: f.type, alignment: .center)
                .padding(.top, 18)
                .allowsHitTesting(false)
            FolioFeatureLibraryLabel(text: feature.body, style: FolioFeatureLibraryStyles.libraryBody, type: f.type, alignment: .center)
                .padding(.top, 8)
                .allowsHitTesting(false)
            FolioFeatureLibraryGrowRow(spacing: 6) {
                FolioFeatureLibraryButton(title: "Remix", look: .dark, icon: "shuffle", engine: e, frame: f) {
                    e.openTopics()
                }
                FolioFeatureLibraryButton(title: e.saved ? "Added" : "Get", look: .dark, icon: "arrow-down", engine: e, frame: f) {
                    e.toggleSaved()
                }
            }
            .padding(.top, 60)
            RoundedRectangle(cornerRadius: 2)
                .fill(Color(folio: 0xE4E4E4))
                .frame(width: 56, height: 4)
                .padding(.top, 8)
                .allowsHitTesting(false)
        }
        .padding(.top, CGFloat(f.insetTop + 76))
        .padding(.horizontal, 18)
        .padding(.bottom, 8)
        .frame(width: CGFloat(f.width - 20), alignment: .top)
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { measured in
            e.setIntroHeight(Double(measured).rounded(.up))
        }
        .opacity(f.headerContentOpacity)
        .offset(y: CGFloat(f.headerContentTY))
        .allowsHitTesting(f.collapse < 0.5 && e.libraryVisible)
        .accessibilityHidden(f.collapse >= 0.5)
    }

    /// `chip` (line 629): a small grey pill of icon or picture and text.
    private func chip<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 6) {
            content()
        }
        .padding(.horizontal, 9)
        .frame(height: 30)
        .background(Capsule().fill(Color(folio: 0xF5F5F5)))
    }

    // MARK: App bar

    /// `appBar` (line 531): back, the status of what is being made, share.
    private var appBar: some View {
        let status = e.statusText
        let fade = e.entering("status", duration: 0.2, at: f.time)
        return HStack(spacing: 0) {
            Button {
                e.closeLibrary()
            } label: {
                FolioFeatureLibraryCircle(size: 46, shadow: FolioFeatureLibraryShadows.single(y: 2, blur: 10, opacity: 0.08), icon: "chevron-back", iconSize: 22, iconColor: Color(folio: 0x1F1F1F), type: f.type)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back to Explore")
            Spacer(minLength: 0)
            if let status = status {
                FolioFeatureLibraryStatusPill(text: status, type: f.type)
                    .opacity(fade)
            }
            Spacer(minLength: 0)
            Button {
                e.openNotice(FolioFeatureLibraryData.shareNotice)
            } label: {
                FolioFeatureLibraryCircle(size: 46, shadow: FolioFeatureLibraryShadows.single(y: 2, blur: 10, opacity: 0.08), icon: "share-outline", iconSize: 20, iconColor: Color(folio: 0x1F1F1F), type: f.type)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Share collection")
        }
        .padding(.top, CGFloat(f.insetTop + 8))
        .padding(.horizontal, 18)
        .frame(width: CGFloat(f.width), alignment: .top)
        .frame(maxHeight: .infinity, alignment: .top)
    }

    // MARK: Footer

    /// `libraryFooter` (line 536).
    private var footer: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            FolioFeatureLibraryButton(title: "Generate New", look: .primary, pill: true, icon: "add", engine: e, frame: f) {
                e.openTopics()
            }
        }
        .padding(.bottom, CGFloat(f.insetBottom + 14))
        .frame(width: CGFloat(f.width), height: CGFloat(f.height), alignment: .bottom)
    }
}

/// `statusPill` (line 622): three working dots and a line of text.
struct FolioFeatureLibraryStatusPill: View {
    let text: String
    let type: FolioFeatureLibraryType

    var body: some View {
        HStack(spacing: 8) {
            FolioFeatureLibraryWorking()
            FolioFeatureLibraryLabel(text: text, style: FolioFeatureLibraryStyles.statusText, type: type)
        }
        .padding(.horizontal, 10)
        .frame(height: 30)
        .allowsHitTesting(false)
    }
}

/// `OpeningCollection` (lines 90-120): the loading lens between a card and its library. A neutral lens, empty, turns green
/// and the open book resolves inside it.
struct FolioFeatureLibraryLoader: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        let top = f.type.scale > 1.4 ? f.insetTop + 64 : f.height * 0.36
        let feature = FolioFeatureLibraryData.features[e.feature]
        return ZStack(alignment: .topLeading) {
            LinearGradient(colors: [Color(folio: 0xFAFAFA), Color(folio: 0xE9E9E9)], startPoint: .top, endPoint: .bottom)
            Button {
                e.closeLibrary()
            } label: {
                FolioFeatureLibraryIcon(name: "chevron-back", size: 21, color: Color(folio: 0x363C34), type: f.type)
                    .frame(minWidth: 48, minHeight: 48)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Cancel opening and return to Explore")
            .offset(x: 16, y: CGFloat(f.insetTop + 8))
            VStack(spacing: 0) {
                ZStack {
                    lens("glass-lens-neutral", opacity: 0.65 * f.shellOpacity)
                    lens("glass-lens-green", opacity: f.greenOpacity)
                    lens("glass-book-blue", opacity: f.iconOpacity)
                }
                .frame(width: 148, height: 148)
                .accessibilityHidden(true)
                FolioFeatureLibraryLabel(text: feature.name, style: FolioFeatureLibraryStyles.loaderTitle, type: f.type, alignment: .center)
                    .padding(.top, 16)
                    .opacity(f.loaderTitleOpacity)
                FolioFeatureLibraryLabel(text: "Loading…", style: FolioFeatureLibraryStyles.loaderCopy, type: f.type, alignment: .center)
                    .padding(.top, 6)
            }
            .padding(.horizontal, 20)
            .frame(width: CGFloat(f.width), alignment: .top)
            .offset(y: CGFloat(top))
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height), alignment: .topLeading)
    }

    private func lens(_ name: String, opacity: Double) -> some View {
        Image(uiImage: FolioFeatureLibraryAssets.image(name))
            .resizable()
            .scaledToFit()
            .frame(width: 148, height: 148)
            .opacity(opacity)
    }
}
