import SwiftUI

@MainActor
enum TrophySavingsMilestonePaths {
    static func parse(_ source: String) -> Path {
        let regex = try! NSRegularExpression(pattern:"[MLHVQCZmlhvqcz]|[-+]?(?:[0-9]*\\.)?[0-9]+(?:[eE][-+]?[0-9]+)?")
        let range = NSRange(source.startIndex...,in:source)
        let tokens = regex.matches(in:source,range:range).compactMap { Range($0.range,in:source).map { String(source[$0]) } }
        var i = 0, command = "M", path = Path(), point = CGPoint.zero, start = CGPoint.zero
        func number() -> CGFloat { defer { i += 1 }; return CGFloat(Double(tokens[i])!) }
        func nextPoint() -> CGPoint { CGPoint(x:number(),y:number()) }
        while i < tokens.count {
            if tokens[i].first?.isLetter == true { command = tokens[i]; i += 1 }
            switch command {
            case "M": point = nextPoint(); path.move(to:point); start = point; command = "L"
            case "L": point = nextPoint(); path.addLine(to:point)
            case "H": point.x = number(); path.addLine(to:point)
            case "V": point.y = number(); path.addLine(to:point)
            case "Q": let c = nextPoint(); point = nextPoint(); path.addQuadCurve(to:point,control:c)
            case "C": let a = nextPoint(), b = nextPoint(); point = nextPoint(); path.addCurve(to:point,control1:a,control2:b)
            case "Z", "z": path.closeSubpath(); point = start; command = "M"
            default: preconditionFailure("Unsupported trophy path command")
            }
        }; return path
    }
    static let bowl = parse("M139 120C166 105 226 105 252 120C252 157 240 186 214 208C208 215 202 218 195.5 219C189 218 183 216 178 211C150 190 140 156 139 120Z")
    static let facet = parse("M159 111C157 151 165 178 172 192C176 201 181 209 185 214C176 195 172 154 174 110Z")
    static let glint = parse("M174 122L176.5 127.5L183.5 130L176.5 132.5L174 138L171.5 132.5L164.5 130L171.5 127.5Z")
    static let gold = parse("M139 120C166 105 226 105 252 120C252 157 240 186 214 208C208 215 202 218 195.5 219C189 218 183 216 178 211C150 190 140 156 139 120Z M137.5 108.5C137.5 101.7 163.5 96.2 195.5 96.2C228.1 96.2 254.5 101.7 254.5 108.5L253 120C226 105 166 105 138.5 120Z M182.5 214.5H208.5Q211.5 214.5 211.5 217.5V221Q211.5 224 208.5 224H182.5Q179.5 224 179.5 221V217.5Q179.5 214.5 182.5 214.5Z M189 222C192 223 200 223 203.5 222V237C204.5 250 216 255 226.5 257V261C211 256 180 256 165 261V257C176 255 189 249 189 237Z M158.5 262C160.5 259 162.5 257.5 166.5 256.5C183 254.5 208 254.5 225 256.5C229 257.5 231.5 259 232.5 262L232 266H158.5Z")
}
