import SwiftUI

/// Minimal parser for the SVG path subset used by the Orb assets: absolute `M`, `L`, `C` and `Z`.
/// Skia parsed these strings at runtime in the Expo version; here we do the same once at launch.
enum GrokOrbSVGPath {
    private enum Token {
        case command(Character)
        case number(CGFloat)
    }

    /// Builds a SwiftUI `Path` from the path data.
    static func path(_ data: String) -> Path {
        let tokens = tokenize(data)
        var path = Path()
        var command: Character = "M"
        var index = 0

        func nextNumber() -> CGFloat {
            defer { index += 1 }
            if index < tokens.count, case .number(let value) = tokens[index] {
                return value
            }
            return 0
        }

        while index < tokens.count {
            if case .command(let letter) = tokens[index] {
                command = letter
                index += 1
                if letter == "Z" || letter == "z" {
                    path.closeSubpath()
                }
                continue
            }

            switch command {
            case "M":
                path.move(to: CGPoint(x: nextNumber(), y: nextNumber()))
                command = "L"
            case "L":
                path.addLine(to: CGPoint(x: nextNumber(), y: nextNumber()))
            case "C":
                let control1 = CGPoint(x: nextNumber(), y: nextNumber())
                let control2 = CGPoint(x: nextNumber(), y: nextNumber())
                let end = CGPoint(x: nextNumber(), y: nextNumber())
                path.addCurve(to: end, control1: control1, control2: control2)
            default:
                index += 1
            }
        }

        return path
    }

    /// Extracts the vertices of a closed polyline path (`M x y L x y … Z`).
    static func polygon(_ data: String) -> [CGPoint] {
        var numbers: [CGFloat] = []
        for token in tokenize(data) {
            if case .number(let value) = token {
                numbers.append(value)
            }
        }

        precondition(numbers.count % 2 == 0, "Odd coordinate count in Orb eye path.")

        return stride(from: 0, to: numbers.count, by: 2).map {
            CGPoint(x: numbers[$0], y: numbers[$0 + 1])
        }
    }

    private static func tokenize(_ data: String) -> [Token] {
        var tokens: [Token] = []
        var buffer = ""

        func flush() {
            if let value = Double(buffer) {
                tokens.append(.number(CGFloat(value)))
            }
            buffer.removeAll(keepingCapacity: true)
        }

        for character in data {
            if character.isLetter {
                flush()
                tokens.append(.command(character))
            } else if character == "-" {
                flush()
                buffer.append(character)
            } else if character.isWhitespace || character == "," {
                flush()
            } else {
                buffer.append(character)
            }
        }
        flush()

        return tokens
    }
}
