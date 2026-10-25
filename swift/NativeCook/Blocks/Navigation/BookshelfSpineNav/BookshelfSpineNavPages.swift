import SwiftUI

/// The two pages above the shelf: the library's home (at rest) and a section, which the open book
/// becomes. Both are static views; only their containers move.
enum BookshelfSpineNavStyle {
    static let cream = Color(hex: 0xF4EADB)
    static let muted = Color.rgba(244, 234, 219, 0.6)
    static let ink = Color(hex: 0x2B231D)
    static let inkSoft = Color.rgba(43, 35, 29, 0.62)
    static func serif(_ size: Double) -> Font { .custom("Georgia-Bold", fixedSize: size) }
}

/// A small drawn cover: two tones, a foil rule and the title.
struct BookshelfSpineNavMiniCover: View {
    let tone: (UInt32, UInt32)
    let label: String
    let width: Double
    let height: Double

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(stops: [
                .init(color: BookshelfSpineNavMotion.shade(tone.0, 0.12), location: 0), .init(color: Color(hex: tone.0), location: 0.45),
                .init(color: Color(hex: tone.1), location: 1),
            ], startPoint: .topLeading, endPoint: .bottomTrailing)
            Rectangle().fill(Color.black.opacity(0.25)).frame(width: 4).frame(maxHeight: .infinity, alignment: .leading)
            Rectangle().fill(Color.rgba(255, 236, 200, 0.6)).frame(width: width - 14, height: 1)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .offset(x: 8, y: 10)
            Text(label)
                .font(BookshelfSpineNavStyle.serif(max(9, width * 0.16)))
                .foregroundStyle(Color.rgba(255, 240, 215, 0.92))
                .lineLimit(3)
                .padding(.leading, 3)
                .padding(6)
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: 3))
        .background(RoundedRectangle(cornerRadius: 3).fill(Color(hex: tone.1))
            .shadow(color: .black.opacity(0.4), radius: 1, y: 1).shadow(color: .black.opacity(0.25), radius: 7, y: 6))
    }
}

struct BookshelfSpineNavHome: View {
    var body: some View {
        let S = BookshelfSpineNavStyle.self
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 16) {
                BookshelfSpineNavMiniCover(tone: (0xC96F4A, 0x4A2226), label: "The Salt Orchard", width: 104, height: 152)
                VStack(alignment: .leading, spacing: 3) {
                    Text("CURRENTLY READING")
                        .font(.system(size: 11, weight: .bold)).kerning(1.6)
                        .foregroundStyle(Color(hex: 0xE0B36C))
                    Text("The Salt Orchard")
                        .font(S.serif(21))
                        .foregroundStyle(S.cream)
                        .padding(.top, 2)
                    Text("Ines Calloway")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(S.muted)
                    Capsule().fill(Color.rgba(255, 240, 220, 0.12)).frame(height: 6)
                        .overlay(alignment: .leading) {
                            GeometryReader { g in
                                Capsule().fill(LinearGradient(colors: [Color(hex: 0xF0C27E), Color(hex: 0xD9954B)], startPoint: .leading, endPoint: .trailing))
                                    .frame(width: g.size.width * 0.64)
                            }
                        }
                        .padding(.top, 12)
                    Text("64% · Chapter 11 · 38 min left")
                        .font(.system(size: 12, weight: .medium).monospacedDigit())
                        .foregroundStyle(S.muted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.9)
                        .padding(.top, 6)
                    Text("Continue")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color(hex: 0x2A1808))
                        .padding(.horizontal, 16)
                        .frame(height: 32)
                        .background(Capsule().fill(Color(hex: 0xE8B36A)))
                        .padding(.top, 10)
                }
                .padding(.top, 4)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Color.rgba(255, 240, 220, 0.06))
                    .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Color.rgba(255, 240, 220, 0.14), lineWidth: 0.5))
                    .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.3), radius: 16, y: 14)
            )
            HStack(spacing: 8) {
                ForEach([("12", "day streak"), ("23", "books this year"), ("4 h", "this week")], id: \.1) { n, label in
                    VStack(alignment: .leading, spacing: 0) {
                        Text(n).font(.system(size: 22, weight: .bold).monospacedDigit()).foregroundStyle(S.cream)
                        Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(S.muted).lineLimit(1).minimumScaleFactor(0.85)
                    }
                    .padding(.horizontal, 12)
                    .frame(maxWidth: .infinity, minHeight: 72, maxHeight: 72, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Color.rgba(255, 240, 220, 0.05))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.rgba(255, 240, 220, 0.12), lineWidth: 0.5))
                    )
                }
            }
            Spacer(minLength: 0)
            HStack(alignment: .lastTextBaseline) {
                Text("Sections").font(.system(size: 17, weight: .bold)).foregroundStyle(S.cream)
                Spacer()
                Text("Sorted by recent").font(.system(size: 12, weight: .medium)).foregroundStyle(S.muted)
            }
            .padding(.horizontal, 4)
        }
        .padding(.top, 6)
        .frame(width: BookshelfSpineNavMotion.page.width, height: BookshelfSpineNavMotion.page.height, alignment: .topLeading)
    }
}

/// A section's page: printed on cream paper in the book's colours. Only its text fades.
struct BookshelfSpineNavSection: View {
    let book: BookshelfSpineNavMotion.Book
    let text: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            BookshelfSpineNavSectionPaper(color: book.color)
            BookshelfSpineNavSectionText(book: book)
                .opacity(text)
        }
        .frame(width: BookshelfSpineNavMotion.page.width, height: BookshelfSpineNavMotion.page.height)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color(hex: 0xF4ECDD))
                .shadow(color: .black.opacity(0.4), radius: 2, y: 2)
                .shadow(color: .black.opacity(0.4), radius: 24, y: 24)
        )
    }
}

struct BookshelfSpineNavSectionPaper: View {
    let color: UInt32

    var body: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [.init(color: Color(hex: 0xFBF6EC), location: 0), .init(color: Color(hex: 0xF4ECDD), location: 0.6),
                                   .init(color: Color(hex: 0xEADFCB), location: 1)], startPoint: .top, endPoint: .bottom)
            LinearGradient(colors: [BookshelfSpineNavMotion.shade(color, 0.15), Color(hex: color), BookshelfSpineNavMotion.shade(color, -0.3)],
                           startPoint: .top, endPoint: .bottom)
                .frame(height: 8)
            Rectangle().fill(Color.rgba(80, 55, 30, 0.06)).frame(width: 14)
        }
    }
}

struct BookshelfSpineNavSectionText: View {
    let book: BookshelfSpineNavMotion.Book

    var body: some View {
        let S = BookshelfSpineNavStyle.self
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("SECTION")
                        .font(.system(size: 11, weight: .heavy)).kerning(1.8)
                        .foregroundStyle(BookshelfSpineNavMotion.shade(book.color, -0.1))
                    Text(book.section).font(S.serif(30)).foregroundStyle(S.ink).padding(.top, 2)
                    Text(book.blurb).font(.system(size: 13, weight: .medium).monospacedDigit()).foregroundStyle(S.inkSoft)
                }
                Spacer()
                Text("\(book.count)")
                    .font(.system(size: 16, weight: .heavy).monospacedDigit())
                    .foregroundStyle(Color(hex: book.foil))
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Color(hex: book.color))
                        .overlay(Circle().stroke(LinearGradient(colors: [.white.opacity(0.2), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
                        .shadow(color: .black.opacity(0.25), radius: 3, y: 2))
            }
            Rectangle().fill(Color.rgba(43, 35, 29, 0.16)).frame(height: 1)
            ForEach(book.rows, id: \.title) { row in
                HStack(spacing: 12) {
                    BookshelfSpineNavMiniCover(tone: row.tone, label: row.title.replacingOccurrences(of: "“", with: "").replacingOccurrences(of: "”", with: ""),
                                               width: 40, height: 56)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.title).font(.system(size: 15, weight: .bold)).foregroundStyle(S.ink).lineLimit(1)
                        Text(row.author).font(.system(size: 12, weight: .medium)).foregroundStyle(S.inkSoft).lineLimit(1)
                        if row.progress > 0 && row.progress < 1 {
                            Capsule().fill(Color.rgba(43, 35, 29, 0.1)).frame(height: 4)
                                .overlay(alignment: .leading) {
                                    GeometryReader { g in
                                        Capsule().fill(Color(hex: book.color)).frame(width: g.size.width * row.progress.rounded2)
                                    }
                                }
                                .padding(.top, 6)
                        } else {
                            Text(row.progress >= 1 ? "Finished" : "Not started")
                                .font(.system(size: 11, weight: .semibold)).kerning(0.4)
                                .foregroundStyle(S.inkSoft)
                                .padding(.top, 4)
                        }
                    }
                }
                .frame(minHeight: 56, maxHeight: 62)
            }
            Spacer(minLength: 0)
            Text("Back to the shelf")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(S.ink)
                .padding(.horizontal, 18)
                .frame(height: 36)
                .background(Capsule().fill(Color.rgba(43, 35, 29, 0.08)))
                .frame(maxWidth: .infinity)
        }
        .padding(.top, 24)
        .padding(.horizontal, 22)
        .padding(.bottom, 16)
        .frame(width: BookshelfSpineNavMotion.page.width, height: BookshelfSpineNavMotion.page.height, alignment: .topLeading)
    }
}

private extension Double {
    /// A share rounded to whole per cents, as the page prints it.
    var rounded2: Double { (self * 100).rounded() / 100 }
}
