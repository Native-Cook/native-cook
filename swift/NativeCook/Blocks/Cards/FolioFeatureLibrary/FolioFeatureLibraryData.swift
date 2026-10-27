import CoreText
import SwiftUI
import UIKit

// The block's content and its small value types. Everything here is copied from the constants of
// FolioFeatureLibrary.tsx (lines 16-66) and its StyleSheet (lines 581-692).

/// `type Screen` (FolioFeatureLibrary.tsx:56).
enum FolioFeatureLibraryScreen: Equatable {
    case explore
    case loading
    case library
    case detail
}

/// `type BookId` (FolioFeatureLibrary.tsx:57).
enum FolioFeatureLibraryBookID: Equatable {
    case rituals
    case noticing
}

/// `type CardFrame` (FolioFeatureLibrary.tsx:58): a frame in the root's coordinates.
struct FolioFeatureLibraryCardFrame: Equatable {
    var x: Double
    var y: Double
    var width: Double
    var height: Double
}

/// `type ReturnOrigin` (FolioFeatureLibrary.tsx:59): which control a library opened from, so closing returns to it.
enum FolioFeatureLibraryReturnOrigin: Equatable {
    case feature(Int)
    case recent
    case plus
}

/// `OpeningOperation` (FolioFeatureLibrary.tsx:61): the loading lens, with its start and length.
struct FolioFeatureLibraryOpening {
    let id: Int
    let startedAt: Double
    let duration: Double
    var deadline: Double { startedAt + duration }
}

/// `GenerationOperation` (FolioFeatureLibrary.tsx:62).
struct FolioFeatureLibraryGeneration {
    let id: Int
    let deadline: Double
    let topics: [String]
    let notifyHost: Bool
    let coverDelay: Double
}

/// `PendingOperation` (FolioFeatureLibrary.tsx:60): a cover or a detail that arrives at a deadline.
struct FolioFeatureLibraryPending {
    let id: Int
    let deadline: Double
}

struct FolioFeatureLibraryFeature {
    let title: String
    let name: String
    let body: String
    /// Asset name (without the block prefix and extension).
    let art: String
    let ink: UInt32
}

struct FolioFeatureLibraryApp {
    let icon: Int
    let name: String
    let handle: String
    let body: String
}

struct FolioFeatureLibraryBook {
    let title: String
    let author: String
    let blurb: String
    let summary: String
    let lessons: [String]
}

enum FolioFeatureLibraryData {
    /// `FEATURES` (FolioFeatureLibrary.tsx:32).
    static let features: [FolioFeatureLibraryFeature] = [
        FolioFeatureLibraryFeature(title: "Non-fiction\nsummaries", name: "Non-fiction summaries", body: "This app gives you detailed outlines of popular non-fiction books to help you learn various topics easily.", art: "orb-book-green", ink: 0x0B4A2E),
        FolioFeatureLibraryFeature(title: "Places to\nremember", name: "Places to remember", body: "Keep track of places you have been and dream of visiting with beautiful cards.", art: "glass-globe", ink: 0x0D3A73),
        FolioFeatureLibraryFeature(title: "Portrait\nstudio", name: "Portrait studio", body: "Turn your photos into hand-drawn illustrated portraits in a few seconds.", art: "glass-portrait", ink: 0x0B5A5A),
        FolioFeatureLibraryFeature(title: "Pantry\nrecipes", name: "Pantry recipes", body: "Snap your fridge and get recipes you can make tonight.", art: "orb-recipe", ink: 0x7A3B12),
    ]

    /// `CATEGORIES` (FolioFeatureLibrary.tsx:38).
    static let categories: [String] = ["Lifestyle", "Health & Fitness", "Mindfulness", "Cooking", "Learning"]

    /// `APPS` (FolioFeatureLibrary.tsx:39).
    static let apps: [FolioFeatureLibraryApp] = [
        FolioFeatureLibraryApp(icon: 0, name: "Weight room tracker", handle: "@kaito", body: "Track your workouts, log sets, and make every session count."),
        FolioFeatureLibraryApp(icon: 1, name: "Clear air today", handle: "@priya", body: "Get real-time air quality updates for the streets you walk."),
        FolioFeatureLibraryApp(icon: 2, name: "Stretch break coach", handle: "@dani", body: "Get daily stretch routines tailored to your day."),
        FolioFeatureLibraryApp(icon: 3, name: "Interval timer", handle: "@unai", body: "Accurate intervals for runs, rows and rests."),
        FolioFeatureLibraryApp(icon: 4, name: "Rep counter", handle: "@noor", body: "Log your lifts, sets, and weights effortlessly."),
    ]

    /// `RECENT` (FolioFeatureLibrary.tsx:46).
    static let recent: [FolioFeatureLibraryApp] = [
        FolioFeatureLibraryApp(icon: 5, name: "Daily spend tracker", handle: "@moonlit", body: "Quickly log and manage your daily expenses with ease."),
        FolioFeatureLibraryApp(icon: 6, name: "Fridge planner", handle: "@awa", body: "Plan meals around what is already waiting in your fridge."),
        FolioFeatureLibraryApp(icon: 7, name: "Liner notes", handle: "@kasra", body: "Keep a note on every album you fall in love with."),
        FolioFeatureLibraryApp(icon: 3, name: "Night sky log", handle: "@ines", body: "Note the stars you found and the clouds that hid them."),
        FolioFeatureLibraryApp(icon: 0, name: "Plant days", handle: "@theo", body: "Gentle reminders for every pot on the windowsill."),
    ]

    /// `TOPICS` (FolioFeatureLibrary.tsx:53).
    static let topics: [String] = ["Family", "Productivity", "Mindfulness", "Career", "Leadership", "Communication", "Psychology", "Parenting", "Marketing", "Design", "Art"]

    /// `MORE_TOPICS` (FolioFeatureLibrary.tsx:55): a first pick widens the list.
    static let moreTopics: [String] = ["Personal development", "Entrepreneurship", "Financial literacy", "Emotional intelligence", "Innovation"]

    /// `BOOKS` (FolioFeatureLibrary.tsx:63).
    static let rituals = FolioFeatureLibraryBook(
        title: "Small Rituals",
        author: "Mira Wells",
        blurb: "A practical guide to building good habits, breaking bad ones, and mastering the small, consistent actions that lead to remarkable results.",
        summary: "“Small Rituals” is a gentle, practical guide to changing a day by changing its smallest moments. Mira Wells shows how a walk without your phone, a page before breakfast or one promise you can keep quietly adds up, why the repeatable step matters more than the impressive one, and how to shape your rooms, your evenings and your calendar so the good choice becomes the easy one. It draws on habit research, design and plain everyday experience to make lasting change feel lighter, kinder and entirely within reach.",
        lessons: [
            "Start with a moment you already have: tie each new ritual to something you do every day, so it never needs a reminder.",
            "Make the next step small enough to repeat, even on a tired evening; consistency carries further than intensity.",
            "Shape the room, not just the will: put the book on the pillow and the phone in another room.",
            "Notice what feels better, not just what gets done, and let that feeling choose what you keep.",
        ]
    )

    static let noticing = FolioFeatureLibraryBook(
        title: "A Brief Tour of Almost Everything",
        author: "Theo Marsh",
        blurb: "A curious walk through science, from the first stars to the dinosaurs and the people who worked it all out.",
        summary: "A familiar street still has something to show you. Look for a new color, listen for a quiet sound, or ask a better question. This short read explores how paying attention can turn an ordinary day into a small discovery.",
        lessons: [
            "Look at a familiar place with fresh eyes.",
            "Leave a little space for the unexpected.",
            "Keep one small observation from each day.",
        ]
    )

    static func book(_ id: FolioFeatureLibraryBookID) -> FolioFeatureLibraryBook {
        id == .rituals ? rituals : noticing
    }

    /// The two copy lines of the notice for features 1 and 2 (FolioFeatureLibrary.tsx:296).
    static let placesNotice = "Keep a little list of places that spark your curiosity. Start with the coast, a forest path, or a city you have never seen."
    static let portraitNotice = "A small portrait can become a different way to remember a moment. This original illustration is a preview from the collection."
    static let inviteNotice = "Your invitation is ready. Explore any collection to make it yours."
    static let shareNotice = "A personal shelf of original sample stories. Generated books stay in this session when you go back."
}

extension Color {
    /// `#RRGGBB` as an sRGB colour with an optional alpha.
    init(folio hex: UInt32, alpha: Double = 1) {
        let red = Double((hex >> 16) & 0xFF) / 255
        let green = Double((hex >> 8) & 0xFF) / 255
        let blue = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}

/// The bundled artwork and icon font. Files sit flat in the app bundle as `FolioFeatureLibrary-<name>.<ext>`.
enum FolioFeatureLibraryAssets {
    /// The family of the bundled icon font (`@expo/vector-icons` Ionicons).
    static let ionicons = "Ionicons"

    /// Registers the bundled icon font once for the process. Touch it before the first icon is drawn.
    static let fontRegistered: Bool = {
        if let url = Bundle.main.url(forResource: "FolioFeatureLibrary-Ionicons", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        return true
    }()

    private static let pngNames: Set<String> = ["glass-globe", "glass-portrait"]

    private static let names: [String] = [
        "glass-book-blue", "orb-book-green", "glass-lens-neutral", "glass-lens-green", "glass-globe", "glass-portrait",
        "orb-recipe", "cover-everything", "author",
        "avatar-1", "avatar-2", "avatar-3",
        "icon-1", "icon-2", "icon-3", "icon-4", "icon-5", "icon-6", "icon-7", "icon-8",
    ]

    /// Every picture decoded once, before the first frame that shows it.
    static let images: [String: UIImage] = {
        var loaded: [String: UIImage] = [:]
        for name in names {
            let ext = pngNames.contains(name) ? "png" : "webp"
            guard let url = Bundle.main.url(forResource: "FolioFeatureLibrary-\(name)", withExtension: ext),
                  let image = UIImage(contentsOfFile: url.path) else { continue }
            loaded[name] = image.preparingForDisplay() ?? image
        }
        return loaded
    }()

    static func image(_ name: String) -> UIImage {
        images[name] ?? UIImage()
    }

    /// `ICONS[index]`: icon-1 ... icon-8.
    static func icon(_ index: Int) -> UIImage {
        image("icon-\(index + 1)")
    }

    /// `AVATARS[index]`: avatar-1 ... avatar-3.
    static func avatar(_ index: Int) -> UIImage {
        image("avatar-\(index + 1)")
    }
}
