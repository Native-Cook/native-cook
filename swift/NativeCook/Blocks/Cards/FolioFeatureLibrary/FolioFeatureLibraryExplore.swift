import SwiftUI
import UIKit

/// One card of the Featured carousel (`featureCard`, line 589, and its children, lines 495): the artwork, the title and
/// three lines of copy on a white card. The same view is the card that grows into the loading screen.
struct FolioFeatureLibraryFeatureCard: View {
    let item: FolioFeatureLibraryFeature
    let width: Double
    let height: Double
    let type: FolioFeatureLibraryType
    /// `overflow: 'hidden'` of the copy that grows into the loading screen.
    var clipsContent = false

    var body: some View {
        let content = VStack(spacing: 0) {
            Image(uiImage: FolioFeatureLibraryAssets.image(item.art))
                .resizable()
                .scaledToFit()
                .frame(width: 172, height: 172)
            FolioFeatureLibraryLabel(text: item.title, style: FolioFeatureLibraryStyles.featureTitle(ink: item.ink), type: type, alignment: .center)
                .padding(.top, 6)
            FolioFeatureLibraryLabel(text: item.body, style: FolioFeatureLibraryStyles.featureBody, type: type, alignment: .center, lines: 3)
                .padding(.top, 10)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 18)
        .padding(.top, 20)
        .frame(width: CGFloat(width), height: CGFloat(height), alignment: .top)
        if clipsContent {
            content
                .clipShape(RoundedRectangle(cornerRadius: 28))
                .background(FolioFeatureLibrarySurface(radius: 28, shadows: FolioFeatureLibraryShadows.card))
        } else {
            content
                .background(FolioFeatureLibrarySurface(radius: 28, shadows: FolioFeatureLibraryShadows.card))
        }
    }
}

/// `AppRow` (lines 135-140): an icon and three lines.
struct FolioFeatureLibraryAppRow: View {
    let app: FolioFeatureLibraryApp
    let type: FolioFeatureLibraryType
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(uiImage: FolioFeatureLibraryAssets.icon(app.icon))
                    .resizable()
                    .scaledToFill()
                    .frame(width: 64, height: 64)
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 0) {
                    FolioFeatureLibraryLabel(text: app.name, style: FolioFeatureLibraryStyles.appName, type: type, lines: 1)
                    FolioFeatureLibraryLabel(text: app.handle, style: FolioFeatureLibraryStyles.appHandle, type: type)
                        .padding(.top, 1)
                    FolioFeatureLibraryLabel(text: app.body, style: FolioFeatureLibraryStyles.appDescription, type: type, lines: 2)
                        .padding(.top, 3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(minHeight: 79)
            .contentShape(Rectangle())
        }
        .buttonStyle(FolioFeatureLibraryRowStyle())
    }
}

/// The Explore page inside its vertical scroll view (lines 492-504).
struct FolioFeatureLibraryExploreContent: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionTitle("Featured")
            carousel
            sectionTitle("Invites")
            invite
            sectionRow("Popular", top: 26)
            categories
            popularRows
            sectionRow("Recently added", top: 14)
            recentRows
        }
        .padding(.top, CGFloat(f.insetTop - 14))
        .padding(.bottom, CGFloat(f.insetBottom + 110))
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }

    // MARK: Sections

    /// `sectionTitle`: `paddingHorizontal: 19, marginBottom: 14, marginTop: 22`.
    private func sectionTitle(_ text: String) -> some View {
        FolioFeatureLibraryLabel(text: text, style: FolioFeatureLibraryStyles.sectionTitle, type: f.type)
            .padding(.horizontal, 19)
            .padding(.top, 22)
            .padding(.bottom, 14)
    }

    /// `sectionRow` (line 586): a title and a round chevron.
    private func sectionRow(_ text: String, top: Double) -> some View {
        HStack(spacing: 0) {
            FolioFeatureLibraryLabel(text: text, style: FolioFeatureLibraryStyles.sectionTitle, type: f.type)
                .padding(.horizontal, 19)
            Spacer(minLength: 0)
            FolioFeatureLibraryCircle(
                size: 34,
                shadow: FolioFeatureLibraryShadows.single(y: 2, blur: 8, opacity: 0.08),
                icon: "chevron-forward",
                iconSize: 16,
                iconColor: Color(folio: 0x3A3A3A),
                type: f.type
            )
        }
        .padding(.trailing, 19)
        .padding(.top, CGFloat(top))
        .padding(.bottom, 14)
    }

    // MARK: Featured

    private var carousel: some View {
        FolioFeatureLibraryScroller(
            engine: e,
            id: .carousel,
            horizontal: true,
            snapInterval: CGFloat(e.cardWidth + 14),
            fastDeceleration: true,
            reportsDrags: true,
            revision: e.layoutRevision
        ) {
            HStack(spacing: 14) {
                ForEach(FolioFeatureLibraryData.features.indices, id: \.self) { index in
                    featureButton(index)
                }
            }
            .padding(.horizontal, 19)
            .padding(.bottom, 14)
        }
        .frame(height: 344)
    }

    private func featureButton(_ index: Int) -> some View {
        let item = FolioFeatureLibraryData.features[index]
        return Button {
            e.openFeature(index)
        } label: {
            FolioFeatureLibraryFeatureCard(item: item, width: e.cardWidth, height: 330, type: f.type)
        }
        .buttonStyle(FolioFeatureLibraryPressStyle(scale: 0.98, reduced: f.reduced))
        .accessibilityLabel("Open \(item.name)")
    }

    // MARK: Invites

    private var invite: some View {
        Button {
            e.openNotice(FolioFeatureLibraryData.inviteNotice)
        } label: {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 0) {
                    FolioFeatureLibraryLabel(text: "You’ve got 1 Invite!", style: FolioFeatureLibraryStyles.inviteTitle, type: f.type)
                    FolioFeatureLibraryLabel(
                        text: "Great apps start with great people. Invite friends with taste who can help improve Folio.",
                        style: FolioFeatureLibraryStyles.inviteBody,
                        type: f.type
                    )
                    .padding(.top, 10)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                avatars
            }
            .padding(.vertical, 26)
            .padding(.leading, 22)
            .padding(.trailing, 14)
            .background(FolioFeatureLibrarySurface(radius: 26, shadows: FolioFeatureLibraryShadows.card))
        }
        .buttonStyle(FolioFeatureLibraryPressStyle(scale: 0.98, reduced: f.reduced))
        .padding(.horizontal, 19)
    }

    /// `avatarCluster` (lines 596-599): three friends and a plus, 92 by 76.
    private var avatars: some View {
        ZStack(alignment: .topLeading) {
            avatar(0, size: 40, x: 0, y: 6)
            avatar(1, size: 46, x: 34, y: 0)
            avatar(2, size: 44, x: 16, y: 32)
            ZStack {
                Circle().fill(Color(folio: 0x8A8794))
                Circle().strokeBorder(Color.white, lineWidth: 2)
                FolioFeatureLibraryIcon(name: "add", size: 12, color: Color.white, type: f.type)
            }
            .frame(width: 20, height: 20)
            .offset(x: 50, y: 54)
        }
        .frame(width: 92, height: 76, alignment: .topLeading)
    }

    private func avatar(_ index: Int, size: Double, x: Double, y: Double) -> some View {
        ZStack {
            Circle().fill(Color(folio: 0xE4E4E4))
            Image(uiImage: FolioFeatureLibraryAssets.avatar(index))
                .resizable()
                .scaledToFill()
                .frame(width: CGFloat(size), height: CGFloat(size))
                .clipShape(Circle())
            Circle().strokeBorder(Color.white, lineWidth: 2)
        }
        .frame(width: CGFloat(size), height: CGFloat(size))
        .offset(x: CGFloat(x), y: CGFloat(y))
        .accessibilityHidden(true)
    }

    // MARK: Popular

    private var categories: some View {
        FolioFeatureLibraryScroller(
            engine: e,
            id: .categories,
            horizontal: true,
            reportsDrags: true,
            revision: e.layoutRevision
        ) {
            HStack(spacing: 8) {
                ForEach(FolioFeatureLibraryData.categories, id: \.self) { category in
                    categoryChip(category)
                }
            }
            .padding(.horizontal, 19)
            .padding(.bottom, 14)
        }
        .frame(height: 50)
    }

    private func categoryChip(_ category: String) -> some View {
        let active = e.activeCategory == category
        return Button {
            e.selectCategory(category)
        } label: {
            FolioFeatureLibraryLabel(text: category, style: FolioFeatureLibraryStyles.categoryLabel(active: active), type: f.type)
                .padding(.horizontal, 12)
                .frame(minHeight: 36)
                .background(
                    FolioFeatureLibrarySurface(
                        radius: 18,
                        fill: active ? Color(folio: 0x050505) : Color(folio: 0xFAFAFA),
                        shadows: FolioFeatureLibraryShadows.single(y: 1, blur: 3, opacity: 0.06)
                    )
                )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(active ? [.isButton, .isSelected] : .isButton)
    }

    /// `APPS.filter(...)` (line 501).
    private var popularApps: [FolioFeatureLibraryApp] {
        let offset = FolioFeatureLibraryData.categories.firstIndex(of: e.activeCategory) ?? -1
        var shown: [FolioFeatureLibraryApp] = []
        for (index, app) in FolioFeatureLibraryData.apps.enumerated() {
            if e.activeCategory == "Health & Fitness" || (index + offset) % 2 == 0 { shown.append(app) }
        }
        return shown
    }

    private var popularRows: some View {
        VStack(spacing: 0) {
            ForEach(popularApps, id: \.name) { app in
                FolioFeatureLibraryAppRow(app: app, type: f.type) {
                    e.openNotice("\(app.name): \(app.body)")
                }
            }
        }
        .padding(.horizontal, 19)
    }

    // MARK: Recently added

    private var recentRows: some View {
        VStack(spacing: 0) {
            ForEach(FolioFeatureLibraryData.recent, id: \.name) { app in
                FolioFeatureLibraryAppRow(app: app, type: f.type) {
                    e.openFeature(0, from: .recent)
                }
            }
        }
        .padding(.horizontal, 19)
        .background(FolioFeatureLibraryAnchor(engine: e, key: "recent"))
    }
}

/// The frosted Home and Explore pill with the plus button (`floatingNav`, lines 505-508 and 610-616).
struct FolioFeatureLibraryFloatingNav: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        HStack(spacing: 0) {
            pill
            Spacer(minLength: 0)
            plus
        }
        .padding(.horizontal, 20)
        .padding(.bottom, CGFloat(f.insetBottom - 14))
        .frame(width: CGFloat(f.width), height: CGFloat(f.height), alignment: .bottom)
        .opacity(e.screen == .explore ? 1 : 0)
        .allowsHitTesting(e.exploreVisible)
        .accessibilityHidden(!e.exploreVisible)
    }

    private var pill: some View {
        HStack(spacing: 0) {
            home
            explore
        }
        .padding(4)
        // The 1 pt border takes room inside the pill, as in React Native.
        .padding(1)
        .background(
            ZStack {
                FolioFeatureLibraryShadowLayers(
                    layers: FolioFeatureLibraryShadows.single(y: 10, blur: 30, spread: -8, opacity: 0.22),
                    cornerRadius: 30,
                    clipInside: true
                )
                FolioFeatureLibraryBlurView(intensity: 40)
                    .clipShape(RoundedRectangle(cornerRadius: 30))
                RoundedRectangle(cornerRadius: 30)
                    .fill(Color(folio: 0xFFFFFF, alpha: Double(0xB8) / 255))
            }
        )
        .overlay(
            RoundedRectangle(cornerRadius: 30)
                .strokeBorder(Color(folio: 0xFFFFFF, alpha: Double(0xE6) / 255), lineWidth: 1)
        )
    }

    private var home: some View {
        Button {
            e.scrollHome()
        } label: {
            FolioFeatureLibraryLabel(text: "Home", style: FolioFeatureLibraryStyles.navInactive, type: f.type)
                .frame(minWidth: 92)
                .frame(height: 52)
                .background(alignment: .leading) {
                    LinearGradient(
                        colors: [Color(folio: 0x9FD8FF), Color(folio: 0xD9F0FF, alpha: 0)],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: 40)
                }
                .clipShape(RoundedRectangle(cornerRadius: 26))
        }
        .buttonStyle(.plain)
    }

    private var explore: some View {
        FolioFeatureLibraryLabel(text: "Explore", style: FolioFeatureLibraryStyles.navLabel, type: f.type)
            .frame(minWidth: 92)
            .frame(height: 52)
            .background(
                FolioFeatureLibrarySurface(radius: 26, shadows: FolioFeatureLibraryShadows.single(y: 2, blur: 8, opacity: 0.10))
            )
    }

    private var plus: some View {
        Button {
            e.openFeature(0, from: .plus)
        } label: {
            FolioFeatureLibraryIcon(name: "add", size: 26, color: Color.white, type: f.type)
                .frame(width: 56, height: 56)
                .background(
                    FolioFeatureLibrarySurface(
                        radius: 28,
                        fill: Color(folio: 0x333333),
                        shadows: FolioFeatureLibraryShadows.single(y: 10, blur: 22, spread: -8, opacity: 0.5)
                    )
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Create a collection")
    }
}
