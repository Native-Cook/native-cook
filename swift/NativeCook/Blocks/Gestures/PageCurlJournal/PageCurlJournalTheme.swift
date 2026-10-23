import SwiftUI

/// Page Curl Journal: every colour, size and position. Positions are points on the 402 × 874 reference screen,
/// measured from the source recording and scaled with the screen width (theme.ts).
enum PageCurlJournalTheme {
    static let referenceWidth: CGFloat = 402
    static let referenceHeight: CGFloat = 874

    /// The edges of the pages still to turn, seen at the right edge of the book.
    static let stackInk = Color(hex: 0xD9D9DD)

    enum Ink {
        static let paper = Color(hex: 0xFFFFFF)
        static let text = Color(hex: 0x000000)
        static let eyebrow = Color(hex: 0x6E6E73)
        static let mute = Color(hex: 0x636367)
        static let footnote = Color(hex: 0x727276)
        static let author = Color(hex: 0x8E8E92)
        static let caption = Color(hex: 0x7A7A7E)
        static let iconLine = Color(hex: 0xE3E3E6)
    }

    /// One entry per text line or block: top of the line box, font size, weight, line height.
    struct Line {
        let top: CGFloat
        let size: CGFloat
        let weight: Font.Weight
        let line: CGFloat
    }

    enum Typography {
        static let eyebrow = Line(top: 104.3, size: 13.6, weight: .medium, line: 18)
        static let brand = Line(top: 124.6, size: 30.0, weight: .heavy, line: 37)
        static let title = Line(top: 129.1, size: 25.7, weight: .bold, line: 31)
        static let intro = Line(top: 586.2, size: 14.1, weight: .regular, line: 20)
        static let hint = Line(top: 816.2, size: 14.2, weight: .medium, line: 18)
        static let folio = Line(top: 816.2, size: 14, weight: .regular, line: 18)
        static let rowTitle = Line(top: 0, size: 14.7, weight: .semibold, line: 18)
        static let rowBody = Line(top: 0, size: 13.6, weight: .regular, line: 17)
        static let footnote = Line(top: 712.7, size: 11.2, weight: .regular, line: 13.7)
        static let body = Line(top: 273, size: 14.1, weight: .regular, line: 19.7)
        static let cover = Line(top: 214.2, size: 32.3, weight: .bold, line: 37.3)
        static let caption = Line(top: 416.4, size: 9.9, weight: .semibold, line: 12)
        static let author = Line(top: 444.9, size: 26.1, weight: .bold, line: 31)
    }

    /// Spacing scale from the measured source gaps; added elements use these tokens.
    enum Space {
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let md: CGFloat = 12
        static let lg: CGFloat = 16
        static let xl: CGFloat = 24
        static let xxl: CGFloat = 32
        static let body: CGFloat = 48
    }

    enum Layout {
        static let markLeft: CGFloat = 145
        static let markTop: CGFloat = 314.7
        static let markSize: CGFloat = 112.6
        static let markRadius: CGFloat = 25.5
        static let bodyLeft: CGFloat = 47.8
        static let rowIconX: CGFloat = 62
        static let rowTextLeft: CGFloat = 101.5
        static let rowTitleTop: [CGFloat] = [313.1, 402.8]
        static let rowIcon: CGFloat = 30.5
        static let rowIconY: [CGFloat] = [341.7, 431]
        static let rowGap: CGFloat = 3.4
        static let shieldTop: CGFloat = 689
        static let shieldSize: CGFloat = 15.5
        static let hintArrowSize: CGFloat = 13.5
        static let hintArrowGap: CGFloat = 8
        static let sparkleCX: CGFloat = 201.5
        static let sparkleCY: CGFloat = 154
        static let sparkleSize: CGFloat = 47
    }

    /// The invented brand. The source's wordmark dots its i's with sparkles; ours does the same.
    enum Brand {
        static let name = "Ritualbook"
        static let mark = "Ri"
    }

    enum Copy {
        static let meet = "Meet"
        static let intro = ["Your personal ‘Great Book of Habits’, written", "and completed by you, one day at a time."]
        static let hint = "Turn the page"
        static let preface = "Preface"
        static let prefaceTitle = "Your Data Is Yours"
        static let rows: [(title: String, body: [String])] = [
            ("Track Habits", ["Feel proud of every small step you take", "toward your goals."]),
            ("Review Records", ["Learn from your setbacks and your wins", "as the weeks go by."]),
        ]
        static let footnote = ["Everything you write stays private on this iPhone.", "No servers, no tracking, no account, no ads."]
        static let foreword = "Foreword"
        static let forewordTitle = "How It Works"
        static let body = [
            "Every day, your book automatically adds",
            "one fresh page. It’s up to you to make the",
            "most of it.",
            "",
            "Missed a day? Don’t worry about it. You can",
            "always turn back a page and fill in",
            "any blanks as you go.",
            "",
            "Every time you check in on your daily habits,",
            "you move a step closer to finishing all the",
            "chapters of your own unfolding story.",
            "",
            "Now, before your book finds its rightful",
            "place on your home screen, all that’s left is",
            "to make the commitment to the journey",
            "ahead...",
        ]
        static let cover = ["The Great Book", "of Habits"]
        static let caption = "TO BE WRITTEN BY"
        static let author = "Untitled Author"
        static let pages = ["Title page", "Preface", "Foreword", "Cover"]
    }

    /// Icon colours, read from the source frames.
    enum Icon {
        static let check = [Color(hex: 0x6FB6F5), Color(hex: 0x8C8DF0), Color(hex: 0xB08BEF)]
        static let bars = [Color(hex: 0x79A6F2), Color(hex: 0x9B8CEE)]
        static let barsBase = Color(hex: 0xB9A5F2)
        static let sparkle = [Color(hex: 0xFFC83D), Color(hex: 0x3FA0FF), Color(hex: 0x8A63F0), Color(hex: 0xFF7A8A), Color(hex: 0xFFC83D)]
        static let markInk = Color(hex: 0x141414)
        static let curlDark = Color(hex: 0x8F8F93)
        static let curlMid = Color(hex: 0xC9C9CC)
        static let curlLight = Color(hex: 0xF2F2F4)
    }

    /// Page-curl shading, fitted against the source's mid-turn frame (theme.ts CURL).
    enum Curl {
        static let radius = 22.77 // roll radius where the fold meets the grabbed corner, pt
        static let radiusSlope = 0.01 // the roll widens along the fold (a cone, as on iOS)
        static let radiusMin = 1.0
        static let shadow = 0.413 // cast shadow on the page beneath, darkest at the roll
        static let shadowWidth = 1.6
        static let edgeShadow = 0.334 // contact shadow under the lying flap's edge
        static let edgeShadowWidth = 3.62
        static let frontLight = 0.793 // ambient on the rising front of the roll
        static let backLight = 0.414 // ambient on the back of the roll, the fold's dark line
        static let backGamma = 0.455
        static let backPaper = 0.962 // the back side of the sheet
        static let showThrough = 0.218 // how much of the printed front reads through the back
        static let grain = 0.018 // paper grain on the back, a seeded hash per device pixel
        static let edgeInk = 0.08 // the sheet's cut edge reads as thickness
        static let sheen = 0.06 // soft specular band on the roll where it faces the key light
    }
}
