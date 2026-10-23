import Foundation

/// Invented to-dos. `note` shows the small note glyph after the title.
struct DragMultiSelectTask: Identifiable {
    let id: String
    let title: String
    let note: Bool
}

enum DragMultiSelectTasks {
    // The source's own list; only names of real companies and products are changed.
    private static let titles: [(String, Bool)] = [
        ("Return Halo band", false),
        ("Set up Simplify PWA", false),
        ("Product Manager (North America)", true),
        ("Interactive SVG Animations is ready", true),
        ("Try voice cloning", true),
        ("Restaurant List", true),
        ("Pata sala", true),
        ("Kerala", true),
        ("Train", true),
        ("Tetto", true),
        ("Mockups", true),
        ("Glossy Icon Tutorial", true),
        ("Zeno Hoodie", true),
        ("Harbor Tribune Article", true),
        ("China City", true),
        ("Check out Cmd + G", false),
        ("Steddy select time", false),
        ("Renew passport", false),
        ("Plant the basil", true),
        ("Call the landlord", false),
        ("Sort the receipts", true),
        ("Book the dentist", false),
        ("Fix the bike light", false),
        ("Gift for Ines", true),
    ]

    static let all: [DragMultiSelectTask] = titles.enumerated().map { index, entry in
        DragMultiSelectTask(id: "t\(index)", title: entry.0, note: entry.1)
    }
}
