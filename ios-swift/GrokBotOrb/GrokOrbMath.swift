import SwiftUI

enum GrokOrbMath {
    static func clamp(_ value: Double, _ minimum: Double, _ maximum: Double) -> Double {
        min(maximum, max(minimum, value))
    }

    static func smoothstep(_ value: Double) -> Double {
        let clamped = clamp(value, 0, 1)
        return clamped * clamped * (3 - 2 * clamped)
    }

    /// Weighted average of two equal-length vertex lists, the same operation Skia performs in
    /// `SkPath::interpolate`. `progress == 0` yields `from`, `progress == 1` yields `to`.
    static func lerp(_ from: [CGPoint], _ to: [CGPoint], _ progress: CGFloat) -> [CGPoint] {
        let count = min(from.count, to.count)
        var result = [CGPoint](repeating: .zero, count: count)

        for index in 0..<count {
            result[index] = CGPoint(
                x: from[index].x + (to[index].x - from[index].x) * progress,
                y: from[index].y + (to[index].y - from[index].y) * progress
            )
        }

        return result
    }

    static func polygonPath(_ points: [CGPoint]) -> Path {
        var path = Path()
        guard let first = points.first else { return path }

        path.move(to: first)
        for point in points.dropFirst() {
            path.addLine(to: point)
        }
        path.closeSubpath()

        return path
    }

    /// HSL → sRGB, matching the worklet used for the trail gradients.
    static func hslColor(hue: Double, saturation: Double, lightness: Double) -> Color {
        let normalizedHue = ((hue.truncatingRemainder(dividingBy: 360)) + 360)
            .truncatingRemainder(dividingBy: 360)
        let chroma = (1 - abs(2 * lightness - 1)) * saturation
        let sector = normalizedHue / 60
        let secondary = chroma * (1 - abs(sector.truncatingRemainder(dividingBy: 2) - 1))
        var red = 0.0
        var green = 0.0
        var blue = 0.0

        if sector < 1 {
            red = chroma
            green = secondary
        } else if sector < 2 {
            red = secondary
            green = chroma
        } else if sector < 3 {
            green = chroma
            blue = secondary
        } else if sector < 4 {
            green = secondary
            blue = chroma
        } else if sector < 5 {
            red = secondary
            blue = chroma
        } else {
            red = chroma
            blue = secondary
        }

        let lightnessOffset = lightness - chroma / 2

        return Color(
            .sRGB,
            red: red + lightnessOffset,
            green: green + lightnessOffset,
            blue: blue + lightnessOffset,
            opacity: 1
        )
    }
}

/// Cubic Bézier timing function equivalent to CSS `cubic-bezier(x1, y1, x2, y2)` and
/// Reanimated's `Easing.bezier`.
struct CubicBezierEasing {
    let x1: Double
    let y1: Double
    let x2: Double
    let y2: Double

    init(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
        self.x1 = x1
        self.y1 = y1
        self.x2 = x2
        self.y2 = y2
    }

    func callAsFunction(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }

        var t = x

        for _ in 0..<8 {
            let error = sample(t, x1, x2) - x
            if abs(error) < 1e-6 { break }
            let slope = derivative(t, x1, x2)
            if abs(slope) < 1e-6 { break }
            t -= error / slope
        }

        if t < 0 || t > 1 || abs(sample(t, x1, x2) - x) > 1e-4 {
            var lower = 0.0
            var upper = 1.0
            t = x

            for _ in 0..<32 {
                let sampled = sample(t, x1, x2)
                if abs(sampled - x) < 1e-6 { break }
                if sampled < x { lower = t } else { upper = t }
                t = (lower + upper) / 2
            }
        }

        return sample(t, y1, y2)
    }

    private func sample(_ t: Double, _ a: Double, _ b: Double) -> Double {
        let u = 1 - t
        return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t
    }

    private func derivative(_ t: Double, _ a: Double, _ b: Double) -> Double {
        let u = 1 - t
        return 3 * u * u * a + 6 * u * t * (b - a) + 3 * t * t * (1 - b)
    }
}
