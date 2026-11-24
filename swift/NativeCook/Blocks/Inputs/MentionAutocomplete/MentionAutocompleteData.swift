import SwiftUI

/*
 Mention Autocomplete — people in the channel and the text model of the composer.

 The composer's text is plain text plus "chips": ranges of it that are a mention of a person.
 Everything here is plain Swift shared by the loop's script and by a person's typing.
*/

struct MentionAutocompletePerson: Hashable {
    let id: String
    let first: String
    let last: String
    let role: String
    /// Three-stop gradient of the avatar disc.
    let colors: [UInt32]
    let online: Bool

    var fullName: String { "\(first) \(last)" }
    var mention: String { "@\(fullName)" }
    var initials: String { "\(first.prefix(1))\(last.prefix(1))" }
}

enum MentionAutocompleteData {
    typealias Person = MentionAutocompletePerson

    /// Ordered by who spoke last in the channel: an empty query lists them in this order.
    static let people: [Person] = [
        Person(id: "theo", first: "Theo", last: "Lindqvist", role: "Design", colors: [0xC3CB86, 0x97A356, 0x727E3A], online: true),
        Person(id: "priya", first: "Priya", last: "Raman", role: "Product", colors: [0xD1A6C9, 0xA9739F, 0x83507D], online: true),
        Person(id: "maya", first: "Maya", last: "Okafor", role: "Copy lead", colors: [0xF3A882, 0xE07A5C, 0xC4573F], online: true),
        Person(id: "jonah", first: "Jonah", last: "Weiss", role: "Marketing", colors: [0xF2C978, 0xDBA545, 0xB88228], online: false),
        Person(id: "marcus", first: "Marcus", last: "Bell", role: "Engineering", colors: [0x8CC0D1, 0x5797AD, 0x3A768C], online: true),
        Person(id: "mayra", first: "Mayra", last: "Diaz", role: "Support", colors: [0xA6CDAF, 0x76AA84, 0x548A63], online: false),
    ]

    static let you = Person(id: "you", first: "Sam", last: "Ito", role: "You", colors: [0x8E95A3, 0x666D7C, 0x4A505E], online: true)

    static let maxRows = 4

    /// First names that start with the query, then last names, then names containing it.
    static func rank(_ query: String) -> [Int] {
        let q = query.lowercased()
        if q.isEmpty { return Array(0 ..< min(maxRows, people.count)) }
        var first: [Int] = [], last: [Int] = [], rest: [Int] = []
        for (i, p) in people.enumerated() {
            if p.first.lowercased().hasPrefix(q) { first.append(i) }
            else if p.last.lowercased().hasPrefix(q) { last.append(i) }
            else if p.fullName.lowercased().contains(q) { rest.append(i) }
        }
        return Array((first + last + rest).prefix(maxRows))
    }

    /// The stretch of the person's full name that the query matched (character offsets).
    static func matchRange(_ p: Person, _ query: String) -> Range<Int>? {
        let q = query.lowercased()
        if q.isEmpty { return nil }
        let name = p.fullName.lowercased()
        if name.hasPrefix(q) { return 0 ..< q.count }
        let lastAt = p.first.count + 1
        if String(name.dropFirst(lastAt)).hasPrefix(q) { return lastAt ..< lastAt + q.count }
        guard let r = name.range(of: q) else { return nil }
        let a = name.distance(from: name.startIndex, to: r.lowerBound)
        return a ..< a + q.count
    }
}

// MARK: - Composer text

struct MentionAutocompleteChip: Equatable {
    var start: Int
    var len: Int
    var person: Int
}

struct MentionAutocompleteDoc: Equatable {
    var text: String
    var chips: [MentionAutocompleteChip]
    /// Index into chips of the chip a first Backspace selected, or -1.
    var selected: Int

    static let empty = MentionAutocompleteDoc(text: "", chips: [], selected: -1)

    /// Text by character offsets (the composer's text is plain ASCII plus what a person types).
    func slice(_ a: Int, _ b: Int) -> String {
        let chars = Array(text)
        let lo = max(0, min(a, chars.count)), hi = max(lo, min(b, chars.count))
        return String(chars[lo ..< hi])
    }

    var count: Int { text.count }

    struct Run: Equatable {
        let text: String
        let chip: Int
    }

    /// Plain and chip runs, in order, for drawing.
    var runs: [Run] {
        var out: [Run] = []
        var at = 0
        for (i, c) in chips.enumerated() {
            if c.start > at { out.append(Run(text: slice(at, c.start), chip: -1)) }
            out.append(Run(text: slice(c.start, c.start + c.len), chip: i))
            at = c.start + c.len
        }
        if at < count { out.append(Run(text: slice(at, count), chip: -1)) }
        return out
    }

    /// The doc up to offset n (chips that end before it).
    func prefix(_ n: Int) -> MentionAutocompleteDoc {
        MentionAutocompleteDoc(text: slice(0, n), chips: chips.filter { $0.start + $0.len <= n }, selected: -1)
    }

    /// The mention being typed before the caret: where its "@" is and what follows it, or nil.
    func activeQuery(caret: Int) -> (at: Int, query: String)? {
        let chars = Array(slice(0, caret))
        var i = chars.count
        while i > 0, chars[i - 1].isASCII, chars[i - 1].isLetter { i -= 1 }
        guard i > 0, chars[i - 1] == "@" else { return nil }
        let at = i - 1
        if at > 0, !chars[at - 1].isWhitespace { return nil }
        if chips.contains(where: { at >= $0.start && at < $0.start + $0.len }) { return nil }
        return (at, String(chars[i...]))
    }

    enum Edit {
        /// A normal edit: the new doc and where the caret ended up.
        case text(MentionAutocompleteDoc, caret: Int)
        /// Backspace reached a chip: select it (first press) or break it into plain text (second).
        case select(chip: Int)
        case breakChip(chip: Int, doc: MentionAutocompleteDoc)
    }

    /// Interprets a change of the input's plain text against the doc.
    func apply(_ next: String) -> Edit {
        let prev = Array(text), nx = Array(next)
        var p = 0
        while p < prev.count && p < nx.count && prev[p] == nx[p] { p += 1 }
        var ep = prev.count, en = nx.count
        while ep > p && en > p && prev[ep - 1] == nx[en - 1] {
            ep -= 1
            en -= 1
        }
        let delta = en - ep
        let pureDelete = en == p && ep > p
        if let hit = chips.firstIndex(where: { $0.start < ep && $0.start + $0.len > p }), pureDelete {
            if selected != hit { return .select(chip: hit) }
            var d = self
            d.chips.remove(at: hit)
            d.selected = -1
            return .breakChip(chip: hit, doc: d)
        }
        var out: [MentionAutocompleteChip] = []
        for c in chips {
            if c.start + c.len <= p { out.append(c) }
            else if c.start >= ep { out.append(MentionAutocompleteChip(start: c.start + delta, len: c.len, person: c.person)) }
            // A chip the edit overlapped is no longer a mention.
        }
        return .text(MentionAutocompleteDoc(text: next, chips: out, selected: -1), caret: en)
    }

    /// Replaces the typed "@query" (from `at` to the caret) with a chip for the person, and a space.
    func insertMention(at: Int, caret: Int, person: Int) -> (doc: MentionAutocompleteDoc, chip: Int, caret: Int) {
        let label = MentionAutocompleteData.people[person].mention
        let newText = slice(0, at) + label + " " + slice(caret, count)
        let delta = label.count + 1 - (caret - at)
        var out: [MentionAutocompleteChip] = []
        var index = 0
        for c in chips {
            if c.start < at {
                out.append(c)
                index += 1
            } else {
                out.append(MentionAutocompleteChip(start: c.start + delta, len: c.len, person: c.person))
            }
        }
        out.insert(MentionAutocompleteChip(start: at, len: label.count, person: person), at: index)
        return (MentionAutocompleteDoc(text: newText, chips: out, selected: -1), index, at + label.count + 1)
    }
}
