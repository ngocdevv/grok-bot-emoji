import SwiftUI

struct GrokOrbEyePose {
    let left: [CGPoint]
    let right: [CGPoint]
}

/// One preset expression's pose loop. `poseIndices` repeats the first pose at the end so the
/// loop closes seamlessly, exactly like `[...sequence, sequence[0]]` in the Expo scene.
struct GrokOrbExpressionMotion {
    let poseIndices: [Int]
    let durations: [Double]
    let totalDuration: Double
}

/// Parsed geometry shared by the animated Orb and the static previews.
enum GrokOrbGeometry {
    static let orbSize: CGFloat = 228.541
    static let bodyCenter: CGFloat = 114.2705

    static let bodyPath = GrokOrbSVGPath.path(GrokOrbPathData.bodyPath)

    static let lowerLeftEye = GrokOrbSVGPath.polygon(GrokOrbPathData.eyeLowerLeftPath)
    static let upperLeftEye = GrokOrbSVGPath.polygon(GrokOrbPathData.eyeUpperLeftPath)
    static let lowerRightEye = GrokOrbSVGPath.polygon(GrokOrbPathData.eyeLowerRightPath)
    static let upperRightEye = GrokOrbSVGPath.polygon(GrokOrbPathData.eyeUpperRightPath)

    static let poses: [GrokOrbEyePose] = GrokOrbPathData.poses.map { pose in
        GrokOrbEyePose(
            left: GrokOrbSVGPath.polygon(pose.left),
            right: GrokOrbSVGPath.polygon(pose.right)
        )
    }

    /// Stable cadence samples so every pose lasts a reproducible time inside its range.
    static let cadenceVariations: [Double] = [0.18, 0.72, 0.42, 0.9, 0.58]

    static let motions: [GrokOrbExpression: GrokOrbExpressionMotion] = {
        var result: [GrokOrbExpression: GrokOrbExpressionMotion] = [:]

        for (expressionIndex, expression) in GrokOrbExpressionData.presets.enumerated() {
            guard
                let sequence = GrokOrbExpressionData.sequences[expression],
                let range = GrokOrbExpressionData.timingRanges[expression]
            else { continue }

            let durations = sequence.indices.map { poseIndex -> Double in
                let variation = cadenceVariations[
                    (expressionIndex + poseIndex) % cadenceVariations.count
                ]
                return range.minimum + (range.maximum - range.minimum) * variation
            }

            result[expression] = GrokOrbExpressionMotion(
                poseIndices: sequence + [sequence[0]],
                durations: durations,
                totalDuration: durations.reduce(0, +)
            )
        }

        return result
    }()

    /// `natural` has no preset loop; the Expo scene falls back to `happy` for the hidden side
    /// of the cross-fade, so we do the same.
    static func motion(for expression: GrokOrbExpression) -> GrokOrbExpressionMotion {
        motions[expression] ?? motions[.happy]!
    }

    /// Eye pose shown by the static thumbnails: the lower natural eyes, or the first pose of
    /// the preset sequence.
    static func previewEyes(for expression: GrokOrbExpression) -> GrokOrbEyePose {
        if expression.isNatural {
            return GrokOrbEyePose(left: lowerLeftEye, right: lowerRightEye)
        }

        let poseIndex = GrokOrbExpressionData.sequences[expression]?.first ?? 0
        return poses[poseIndex]
    }
}
