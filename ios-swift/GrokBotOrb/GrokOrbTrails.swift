import SwiftUI

struct GrokOrbTrailConfig {
    let arc: Double
    let baseHue: Double
    let baseWidth: Double
    let follow: Double
    let hueSpan: Double
    let hueVelocity: Double
    let lambdaOffset: Double
    let lambdaVelocity: Double
    let radius: Double
    let radiusVelocity: Double
    let roll: Double
    let spawnDelay: Double
    let tilt: Double
}

enum GrokOrbTrailLayer {
    case back
    case front
}

/// Port of `grok-orb-trails.tsx`: nine one-shot colour ribbons orbiting the head on three
/// tilted planes. Each ribbon is split into "behind" and "in front" segments so the body can
/// be painted between the two layers.
enum GrokOrbTrails {
    static let headCenter = 114.2705
    static let hummingStart = 0.8
    static let maxLife = 9.0
    static let retractionStart = maxLife * 0.55
    static let retractionDuration = 0.5
    static let maxSampleAngle = 0.09
    static let maxPointCount = 48
    static let gradientPositions: [CGFloat] = [0, 0.25, 0.5, 0.75, 1]

    // The website creates three orbital planes and emits three ribbons on each plane. Its
    // values are randomized per visit; these are stable samples from the same ranges so the
    // native version remains reproducible (identical to the Expo port).
    static let configs: [GrokOrbTrailConfig] = [
        .init(arc: 2.72, baseHue: 79, baseWidth: 6.3, follow: 0.84, hueSpan: -74, hueVelocity: 24,
              lambdaOffset: 0.08, lambdaVelocity: 0.78, radius: 115.2, radiusVelocity: 1.4,
              roll: -0.31, spawnDelay: 0, tilt: 0.23),
        .init(arc: 3.18, baseHue: 122, baseWidth: 6.8, follow: 0.76, hueSpan: 61, hueVelocity: -31,
              lambdaOffset: 0.14, lambdaVelocity: 1.02, radius: 116.6, radiusVelocity: 0.5,
              roll: 0.78, spawnDelay: 0.07, tilt: 0.4),
        .init(arc: 2.38, baseHue: 160, baseWidth: 5.9, follow: 0.92, hueSpan: 89, hueVelocity: 38,
              lambdaOffset: 0.04, lambdaVelocity: 0.57, radius: 114.9, radiusVelocity: 2.1,
              roll: 1.86, spawnDelay: 0.15, tilt: 0.47),
        .init(arc: 3.34, baseHue: 204, baseWidth: 7.1, follow: 0.8, hueSpan: -48, hueVelocity: -20,
              lambdaOffset: 0.16, lambdaVelocity: 0.91, radius: 134.2, radiusVelocity: 1.8,
              roll: -0.25, spawnDelay: 0.235, tilt: 0.2),
        .init(arc: 2.9, baseHue: 244, baseWidth: 6.5, follow: 0.88, hueSpan: 82, hueVelocity: 29,
              lambdaOffset: 0.1, lambdaVelocity: 0.64, radius: 135.8, radiusVelocity: 0.9,
              roll: 0.83, spawnDelay: 0.315, tilt: 0.37),
        .init(arc: 2.46, baseHue: 286, baseWidth: 5.7, follow: 0.74, hueSpan: -92, hueVelocity: -40,
              lambdaOffset: 0.06, lambdaVelocity: 1.08, radius: 135.1, radiusVelocity: 2.3,
              roll: 1.93, spawnDelay: 0.385, tilt: 0.44),
        .init(arc: 3.06, baseHue: 322, baseWidth: 6.9, follow: 0.94, hueSpan: 58, hueVelocity: 21,
              lambdaOffset: 0.13, lambdaVelocity: 0.52, radius: 154.6, radiusVelocity: 0.3,
              roll: -0.36, spawnDelay: 0.475, tilt: 0.27),
        .init(arc: 2.58, baseHue: 7, baseWidth: 6, follow: 0.78, hueSpan: -69, hueVelocity: -27,
              lambdaOffset: 0.02, lambdaVelocity: 0.87, radius: 153.4, radiusVelocity: 1.6,
              roll: 0.72, spawnDelay: 0.555, tilt: 0.34),
        .init(arc: 3.26, baseHue: 45, baseWidth: 7.3, follow: 0.86, hueSpan: 94, hueVelocity: 35,
              lambdaOffset: 0.11, lambdaVelocity: 0.69, radius: 155.1, radiusVelocity: 2.4,
              roll: 1.8, spawnDelay: 0.64, tilt: 0.49),
    ]

    struct Ribbon {
        let path: Path
        let opacity: Double
        let gradientStart: CGPoint
        let gradientEnd: CGPoint
        let colors: [Color]
    }

    /// Draws every ribbon segment that belongs to `layer` into `context`, whose transform must
    /// already map Orb units (0…228.541) to the canvas.
    static func draw(layer: GrokOrbTrailLayer, time: Double, in context: inout GraphicsContext) {
        for config in configs {
            guard let ribbon = ribbon(for: config, layer: layer, time: time) else { continue }

            let stops = zip(ribbon.colors, gradientPositions).map { color, position in
                Gradient.Stop(color: color, location: position)
            }
            var ribbonContext = context
            ribbonContext.opacity = ribbon.opacity
            ribbonContext.fill(
                ribbon.path,
                with: .linearGradient(
                    Gradient(stops: stops),
                    startPoint: ribbon.gradientStart,
                    endPoint: ribbon.gradientEnd
                )
            )
        }
    }

    // MARK: - Motion helpers (one-to-one with the Expo worklets)

    private static func integralEaseInOutCubic(_ value: Double) -> Double {
        value <= 0.5 ? pow(value, 4) : value - 0.5 + pow(1 - value, 4)
    }

    private static func hummingSpinAngle(_ time: Double) -> Double {
        if time <= 0 { return 0 }

        if time < 0.5 {
            return 3.5 * integralEaseInOutCubic(time / 0.5)
        }

        let firstStageAngle = 1.75

        if time < 1.3 {
            let progress = (time - 0.5) / 0.8
            return firstStageAngle + 5.6 * progress - 4.32 * integralEaseInOutCubic(progress)
        }

        let secondStageAngle = firstStageAngle + 3.44

        return secondStageAngle + 1.6 * (time - 1.3) + 0.6 * (cos(0.65) - cos(0.5 * time))
    }

    private static func projectOrbit(
        lambda: Double, radius: Double, tilt: Double, roll: Double
    ) -> (x: Double, y: Double, z: Double) {
        let horizontal = radius * sin(lambda)
        let vertical = -radius * cos(lambda) * sin(tilt)
        let cosRoll = cos(roll)
        let sinRoll = sin(roll)

        return (
            x: headCenter + horizontal * cosRoll - vertical * sinRoll,
            y: headCenter + horizontal * sinRoll + vertical * cosRoll,
            z: cos(lambda) * cos(tilt)
        )
    }

    private static func trailLife(time: Double, spawnDelay: Double) -> Double {
        time - hummingStart - 0.3 - spawnDelay
    }

    private static func trailRetraction(life: Double) -> Double {
        GrokOrbMath.clamp((life - retractionStart) / retractionDuration, 0, 1)
    }

    private static func trailLambda(life: Double, config: GrokOrbTrailConfig) -> Double {
        let spawnTime = 0.3 + config.spawnDelay
        let currentTime = spawnTime + max(life, 0)
        let spinAtSpawn = hummingSpinAngle(spawnTime)

        return spinAtSpawn
            - config.lambdaOffset
            + (hummingSpinAngle(currentTime) - spinAtSpawn) * config.follow
            + config.lambdaVelocity * max(life, 0)
    }

    private static func trailVisibleArc(life: Double, config: GrokOrbTrailConfig) -> Double {
        if life <= 0 { return 0 }

        let initialLambda = trailLambda(life: 0, config: config)
        let currentLambda = trailLambda(life: life, config: config)
        let travelled = abs(currentLambda - initialLambda)
        let retraction = trailRetraction(life: life)
        let retractionEase = retraction * retraction * (3 - 2 * retraction)

        return min(travelled, config.arc * (1 - retractionEase))
    }

    private static func addRibbonContour(
        to path: inout Path,
        start: Int,
        end: Int,
        pointCount: Int,
        xs: [Double],
        ys: [Double],
        leftXs: [Double],
        leftYs: [Double],
        rightXs: [Double],
        rightYs: [Double]
    ) {
        path.move(to: CGPoint(x: leftXs[start], y: leftYs[start]))

        if start + 1 <= end {
            for index in (start + 1)...end {
                path.addLine(to: CGPoint(x: leftXs[index], y: leftYs[index]))
            }
        }

        if end == pointCount - 1 {
            let previous = max(end - 1, 0)
            let dx = xs[end] - xs[previous]
            let dy = ys[end] - ys[previous]
            var length = hypot(dx, dy)
            if length == 0 { length = 1 }
            let tangentX = dx / length
            let tangentY = dy / length
            var capRadius = hypot(leftXs[end] - xs[end], leftYs[end] - ys[end])
            if capRadius == 0 { capRadius = 0.2 }

            path.addQuadCurve(
                to: CGPoint(x: rightXs[end], y: rightYs[end]),
                control: CGPoint(
                    x: xs[end] + tangentX * capRadius * 2,
                    y: ys[end] + tangentY * capRadius * 2
                )
            )
        } else {
            path.addLine(to: CGPoint(x: rightXs[end], y: rightYs[end]))
        }

        var index = end - 1
        while index >= start {
            path.addLine(to: CGPoint(x: rightXs[index], y: rightYs[index]))
            index -= 1
        }

        if start == 0 {
            let next = min(start + 1, pointCount - 1)
            let dx = xs[next] - xs[start]
            let dy = ys[next] - ys[start]
            var length = hypot(dx, dy)
            if length == 0 { length = 1 }
            let tangentX = dx / length
            let tangentY = dy / length
            var capRadius = hypot(leftXs[start] - xs[start], leftYs[start] - ys[start])
            if capRadius == 0 { capRadius = 0.2 }

            path.addQuadCurve(
                to: CGPoint(x: leftXs[start], y: leftYs[start]),
                control: CGPoint(
                    x: xs[start] - tangentX * capRadius * 2,
                    y: ys[start] - tangentY * capRadius * 2
                )
            )
        } else {
            path.addLine(to: CGPoint(x: leftXs[start], y: leftYs[start]))
        }

        path.closeSubpath()
    }

    /// Builds the visible part of one ribbon for the requested layer, or `nil` while the trail
    /// has not spawned yet or has fully retracted.
    static func ribbon(
        for config: GrokOrbTrailConfig, layer: GrokOrbTrailLayer, time: Double
    ) -> Ribbon? {
        let life = trailLife(time: time, spawnDelay: config.spawnDelay)
        let retraction = trailRetraction(life: life)
        let visibleArc = trailVisibleArc(life: life, config: config)

        guard life > 0, retraction < 1, visibleArc >= 0.025 else { return nil }

        let currentLambda = trailLambda(life: life, config: config)
        let radius = config.radius + config.radiusVelocity * life
        let pointCount = min(maxPointCount, max(2, Int(ceil(visibleArc / maxSampleAngle)) + 1))
        var xs = [Double](repeating: 0, count: pointCount)
        var ys = xs
        var zs = xs
        var leftXs = xs
        var leftYs = xs
        var rightXs = xs
        var rightYs = xs
        var pathLength = 0.0

        for index in 0..<pointCount {
            let progress = Double(index) / Double(pointCount - 1)
            let lambda = currentLambda - visibleArc * (1 - progress)
            let point = projectOrbit(lambda: lambda, radius: radius, tilt: config.tilt, roll: config.roll)

            xs[index] = point.x
            ys[index] = point.y
            zs[index] = point.z

            if index > 0 {
                pathLength += hypot(point.x - xs[index - 1], point.y - ys[index - 1])
            }
        }

        let headDepth = 0.72 + 0.28 * GrokOrbMath.clamp(zs[pointCount - 1], 0, 1)
        let growth = GrokOrbMath.smoothstep(GrokOrbMath.clamp(life / 0.34, 0, 1))
        let rawWidth = max(
            config.baseWidth * headDepth * 1.7 * growth * (1 - 0.72 * retraction * retraction),
            0.5
        )
        let width = min(rawWidth, pathLength * 0.34)

        for index in 0..<pointCount {
            let previous = max(index - 1, 0)
            let next = min(index + 1, pointCount - 1)
            let dx = xs[next] - xs[previous]
            let dy = ys[next] - ys[previous]
            var length = hypot(dx, dy)
            if length == 0 { length = 1 }
            let halfWidth = (width * (0.5 + 0.5 * (Double(index) / Double(pointCount - 1)))) / 2
            let normalX = (-dy / length) * halfWidth
            let normalY = (dx / length) * halfWidth

            leftXs[index] = xs[index] + normalX
            leftYs[index] = ys[index] + normalY
            rightXs[index] = xs[index] - normalX
            rightYs[index] = ys[index] - normalY
        }

        var path = Path()
        var groupStart = 0

        while groupStart < pointCount {
            let groupIsFront = zs[groupStart] >= 0
            var groupEnd = groupStart

            while groupEnd + 1 < pointCount, (zs[groupEnd + 1] >= 0) == groupIsFront {
                groupEnd += 1
            }

            if groupIsFront == (layer == .front) {
                let expandedStart = max(groupStart - 1, 0)
                let expandedEnd = min(groupEnd + 1, pointCount - 1)

                if expandedEnd > expandedStart {
                    addRibbonContour(
                        to: &path,
                        start: expandedStart,
                        end: expandedEnd,
                        pointCount: pointCount,
                        xs: xs, ys: ys,
                        leftXs: leftXs, leftYs: leftYs,
                        rightXs: rightXs, rightYs: rightYs
                    )
                }
            }

            groupStart = groupEnd + 1
        }

        if path.isEmpty { return nil }

        let tail = projectOrbit(
            lambda: currentLambda - visibleArc, radius: radius, tilt: config.tilt, roll: config.roll
        )
        let head = projectOrbit(
            lambda: currentLambda, radius: radius, tilt: config.tilt, roll: config.roll
        )
        let movingHue = config.baseHue + config.hueVelocity * life

        return Ribbon(
            path: path,
            opacity: min(1, life / 0.26),
            gradientStart: CGPoint(x: tail.x, y: tail.y),
            gradientEnd: CGPoint(x: head.x, y: head.y),
            colors: [
                GrokOrbMath.hslColor(hue: movingHue, saturation: 0.56, lightness: 0.56),
                GrokOrbMath.hslColor(hue: movingHue + config.hueSpan * 0.25, saturation: 0.56, lightness: 0.59),
                GrokOrbMath.hslColor(hue: movingHue + config.hueSpan * 0.5, saturation: 0.56, lightness: 0.62),
                GrokOrbMath.hslColor(hue: movingHue + config.hueSpan * 0.75, saturation: 0.56, lightness: 0.64),
                GrokOrbMath.hslColor(hue: movingHue + config.hueSpan, saturation: 0.56, lightness: 0.67),
            ]
        )
    }
}
