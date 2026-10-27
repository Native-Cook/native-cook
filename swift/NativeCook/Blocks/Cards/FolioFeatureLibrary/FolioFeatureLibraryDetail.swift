import SwiftUI
import UIKit

/// The book's page inside its scroll view (lines 540-547).
struct FolioFeatureLibraryDetailContent: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    private var book: FolioFeatureLibraryBook {
        FolioFeatureLibraryData.book(e.selectedBookID)
    }

    /// `(detailReady || reduced)`: the sections' text exists.
    private var filled: Bool {
        e.detailReady || f.reduced
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            FolioFeatureLibraryCover(book: e.selectedBookID, large: true)
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
            FolioFeatureLibraryLabel(text: book.title, style: FolioFeatureLibraryStyles.detailTitle, type: f.type, alignment: .center)
                .frame(maxWidth: .infinity)
                .padding(.top, 22)
            FolioFeatureLibraryLabel(text: book.author, style: FolioFeatureLibraryStyles.detailAuthor, type: f.type, alignment: .center)
                .frame(maxWidth: .infinity)
                .padding(.top, 4)
            actions
            summarySection
            lessonsSection
            authorSection
        }
        .padding(.top, CGFloat(f.insetTop + 64))
        .padding(.horizontal, 18)
        .padding(.bottom, CGFloat(f.insetBottom + 40))
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }

    // MARK: Actions

    /// `detailActions` (line 666): the favourites toggle and Read, one under the other.
    private var actions: some View {
        let bookmarked = e.bookmarked
        return VStack(spacing: 14) {
            FolioFeatureLibraryButton(
                title: bookmarked ? "Saved to Favorites" : "Save to Favorites",
                look: .secondary,
                icon: bookmarked ? "heart" : "heart-outline",
                engine: e,
                frame: f
            ) {
                e.toggleBookmark()
            }
            FolioFeatureLibraryButton(title: "Read", look: .primary, engine: e, frame: f) {
                e.read()
            }
        }
        .padding(.top, 22)
        .padding(.bottom, 30)
    }

    // MARK: Sections

    /// `section` (line 667): a white card with a title and, once there is text, its body.
    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            FolioFeatureLibraryLabel(text: title, style: FolioFeatureLibraryStyles.contentTitle, type: f.type)
                .padding(.bottom, 4)
            content()
        }
        .padding(18)
        .frame(maxWidth: .infinity, minHeight: 96, alignment: .topLeading)
        .background(FolioFeatureLibrarySurface(radius: 22, shadows: FolioFeatureLibraryShadows.single(y: 1, blur: 3, opacity: 0.04)))
        .padding(.bottom, 14)
    }

    private func paragraph(_ text: String, boldPrefix: String? = nil) -> some View {
        FolioFeatureLibraryLabel(text: text, style: FolioFeatureLibraryStyles.paragraph, type: f.type, boldPrefix: boldPrefix)
    }

    private var summarySection: some View {
        section("What’s it about?") {
            if filled {
                paragraph(book.summary)
                    .padding(.top, 12)
                    .opacity(e.entering("detailText", duration: 0.32, at: f.time))
            }
        }
    }

    private var lessonsSection: some View {
        section("What you’ll learn") {
            if filled {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(book.lessons, id: \.self) { line in
                        HStack(alignment: .top, spacing: 12) {
                            Circle()
                                .fill(Color(folio: 0x1E6FEF))
                                .frame(width: 6, height: 6)
                                .padding(.top, 8)
                            paragraph(line)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(.top, 12)
                    }
                }
                .opacity(e.entering("detailText", duration: 0.32, delay: 1.0, at: f.time))
            }
        }
    }

    private var authorSection: some View {
        let author = book.author
        let initials = author.split(separator: " ").compactMap { $0.first }.map { String($0) }.joined()
        return section("Meet the author") {
            if filled {
                HStack(alignment: .top, spacing: 14) {
                    portrait
                    VStack(alignment: .leading, spacing: 0) {
                        paragraph(" is a writer and designer who studies attention, routines and the small things that shape a day.", boldPrefix: author)
                            .padding(.top, 12)
                        paragraph("Her essays on everyday habits are read in classrooms, studios and kitchens, and are known for turning research into steps anyone can try tonight.")
                            .padding(.top, 12)
                        paragraph("Her work draws on behavioral science and years of designing calm, useful products, but is written in a warm, plain style.")
                            .padding(.top, 12)
                        paragraph("This original sample is included locally, so your reading stays available offline.")
                            .padding(.top, 12)
                        if e.authorReady {
                            sourcePill(initials)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .opacity(e.entering("detailText", duration: 0.32, delay: 2.4, at: f.time))
            }
        }
    }

    /// `authorAvatar` (line 672): a grey disc until the portrait fades in.
    private var portrait: some View {
        ZStack {
            Circle().fill(Color(folio: 0xD4D4D4))
            if e.authorReady {
                Image(uiImage: FolioFeatureLibraryAssets.image("author"))
                    .resizable()
                    .scaledToFill()
                    .frame(width: 60, height: 60)
                    .opacity(e.entering("author", duration: 0.4, at: f.time))
            }
        }
        .frame(width: 60, height: 60)
        .clipShape(Circle())
        .padding(.top, 12)
    }

    /// `sourcePill` (line 673).
    private func sourcePill(_ initials: String) -> some View {
        HStack(spacing: 8) {
            Text(initials)
                .font(Font(FolioFeatureLibraryStyles.sourceDotText.uiFont(size: 8) as CTFont))
                .foregroundColor(Color.white)
                .frame(width: 20, height: 20)
                .background(Circle().fill(Color(folio: 0x111111)))
            FolioFeatureLibraryLabel(text: "1 SOURCE", style: FolioFeatureLibraryStyles.sourceText, type: f.type)
        }
        .padding(.leading, 8)
        .padding(.trailing, 14)
        .frame(height: 34)
        .background(Capsule().strokeBorder(Color(folio: 0xE2E2E2), lineWidth: 1))
        .padding(.top, 14)
    }
}

/// `detailSurface` (line 539): the book's page over the library, with its own app bar.
struct FolioFeatureLibraryDetailLayer: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        ZStack(alignment: .topLeading) {
            FolioFeatureLibraryBlurViewport(engine: e, scroller: .detail, enabled: e.detailVisible, reduceTransparency: f.reduceTransparency) {
                FolioFeatureLibraryScroller(engine: e, id: .detail, interactive: e.detailVisible, revision: e.layoutRevision) {
                    FolioFeatureLibraryDetailContent(e: e, f: f)
                }
            }
            .frame(width: CGFloat(f.width), height: CGFloat(f.height))
            appBar
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height), alignment: .topLeading)
        .background(Color(folio: 0xF6F6F6))
        .opacity(f.detailOpacity)
        .offset(x: CGFloat(f.detailTX))
        .allowsHitTesting(e.detailVisible)
        .accessibilityHidden(!e.detailVisible)
    }

    /// The detail's `appBar` (line 548): back, the status, and the favourites control.
    private var appBar: some View {
        HStack(spacing: 0) {
            Button {
                e.backToLibrary()
            } label: {
                FolioFeatureLibraryCircle(size: 46, shadow: FolioFeatureLibraryShadows.single(y: 2, blur: 10, opacity: 0.08), icon: "chevron-back", iconSize: 22, iconColor: Color(folio: 0x1F1F1F), type: f.type)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back to library")
            Spacer(minLength: 0)
            if let status = e.statusText {
                FolioFeatureLibraryStatusPill(text: status, type: f.type)
            }
            Spacer(minLength: 0)
            Button {
                e.toggleBookmark()
            } label: {
                FolioFeatureLibraryCircle(size: 46, shadow: FolioFeatureLibraryShadows.single(y: 2, blur: 10, opacity: 0.08), icon: "chatbubble-outline", iconSize: 19, iconColor: Color(folio: 0x1F1F1F), type: f.type)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(e.bookmarked ? "Remove from favorites" : "Save to favorites")
        }
        .padding(.top, CGFloat(f.insetTop + 8))
        .padding(.horizontal, 18)
        .frame(width: CGFloat(f.width), alignment: .top)
        .frame(maxHeight: .infinity, alignment: .top)
    }
}
