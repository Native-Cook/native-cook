import CoreText
import SwiftUI
import UIKit

/// Page Curl Journal's print system, measured on the React Native 402 x 874 reference screen.
enum PageCurlJournalTheme {
    static let referenceWidth: CGFloat = 402
    static let referenceHeight: CGFloat = 874

    static let stackInk = color(0xD8CDB8)

    enum Paper {
        static let base = PageCurlJournalTheme.color(0xF6F0E3)
        static let ink = PageCurlJournalTheme.color(0x2B241C)
        static let mute = PageCurlJournalTheme.color(0x7C6E5B)
        static let rule = PageCurlJournalTheme.color(0xCDBFA8)
        static let accent = PageCurlJournalTheme.color(0x8A3B2E)
        static let foil = PageCurlJournalTheme.color(0x6B4E1F)
    }

    enum Book {
        static let margin: CGFloat = 48
        static let head: CGFloat = 92
        static let folio: CGFloat = 806
        static let gutter: CGFloat = 30
    }

    enum Typography {
        static let hintTop: CGFloat = 806
    }

    enum Space {
        static let md: CGFloat = 12
    }

    enum Copy {
        static let presents = "Ritualbook presents"
        static let title = "The Great Book\nof Habits"
        static let subtitle = "a journal written one day at a time"
        static let sealTop = "VOL. I"
        static let sealMid = "MMXXVI"
        static let sealBottom = "RITUALBOOK"
        static let imprint = "RITUALBOOK  ·  FIRST EDITION"
        static let hint = "turn the page"
        static let headPreface = "THE GREAT BOOK OF HABITS"
        static let headForeword = "HOW IT WORKS"
        static let preface = "PREFACE"
        static let prefaceTitle = "Your Data Is Yours"
        static let rows: [(title: String, body: String)] = [
            (
                "TRACK HABITS",
                "Mark the small things you do each day and watch them add up. A tick is enough; a line of notes is better. Nothing is graded, and nothing is lost when you miss."
            ),
            (
                "REVIEW RECORDS",
                "Every Sunday the book turns back to the week behind you: what held, what slipped, and the one habit worth carrying into the next."
            ),
        ]
        static let footnote = "* Everything you write stays on this iPhone. No servers, no tracking, no account, no ads."
        static let foreword = "FOREWORD"
        static let forewordTitle = "How It Works"
        static let body = [
            " your book adds one fresh page. It is yours to fill, a line or a whole page, and it waits patiently for you if you are late.",
            "Missed a day? Turn back a page and fill in the blank; nobody is keeping score but you.",
            "Each check-in moves you a step closer to the last chapter of a story that only you can write.",
        ]
        static let bodyLead = "EVERY MORNING"
        static let signoff = "with care, the makers of Ritualbook"
        static let belongs = "THIS BOOK BELONGS TO"
        static let author = "Untitled Author"
        static let begun = "begun on the 23rd of October"
        static let pages = ["Title page", "Preface", "Foreword", "Ownership page"]
    }

    enum Curl {
        static let radius = 22.77
        static let radiusSlope = 0.01
        static let radiusMin = 1.0
        static let shadow = 0.413
        static let shadowWidth = 1.6
        static let edgeShadow = 0.334
        static let edgeShadowWidth = 3.62
        static let frontLight = 0.793
        static let backLight = 0.414
        static let backGamma = 0.455
        static let backPaper = 0.962
        static let showThrough = 0.218
        static let grain = 0.018
        static let edgeInk = 0.08
        static let sheen = 0.06
    }

    private static func color(_ rgb: UInt32) -> Color {
        Color(
            .sRGB,
            red: Double((rgb >> 16) & 255) / 255,
            green: Double((rgb >> 8) & 255) / 255,
            blue: Double(rgb & 255) / 255
        )
    }
}

enum PageCurlJournalAssets {
    private static let romanResource = "PageCurlJournal-Newsreader"
    private static let italicResource = "PageCurlJournal-NewsreaderItalic"

    static let paper: UIImage = {
        guard let url = Bundle.main.url(forResource: "PageCurlJournal-paper", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else {
            preconditionFailure("PageCurlJournal-paper.jpg is missing from the app bundle")
        }
        return image
    }()

    private static let romanName = postScriptName(for: romanResource)
    private static let italicName = postScriptName(for: italicResource)

    private static let registered: Void = {
        for resource in [romanResource, italicResource] {
            guard let url = Bundle.main.url(forResource: resource, withExtension: "ttf") else {
                preconditionFailure("\(resource).ttf is missing from the app bundle")
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        precondition(Bundle.main.url(forResource: "PageCurlJournal-Newsreader-OFL", withExtension: "txt") != nil)
        _ = paper
    }()

    static func register() { _ = registered }

    static func font(size: CGFloat, italic: Bool = false) -> UIFont {
        register()
        let name = italic ? italicName : romanName
        guard let font = UIFont(name: name, size: size) else {
            preconditionFailure("Could not create bundled font \(name)")
        }
        return font
    }

    private static func postScriptName(for resource: String) -> String {
        guard let url = Bundle.main.url(forResource: resource, withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL),
              let font = CGFont(provider),
              let name = font.postScriptName else {
            preconditionFailure("Could not read \(resource).ttf")
        }
        return name as String
    }
}
