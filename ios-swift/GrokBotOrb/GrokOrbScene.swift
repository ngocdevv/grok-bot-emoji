import SwiftUI

/// Port of `grok-orb-scene.tsx` (exported as `GrokOrb` in the Expo project).
///
/// The Expo version drives Skia from Reanimated shared values. Here a `TimelineView` feeds a
/// `Canvas` every frame and all the per-frame values are recomputed from the elapsed time, which
/// keeps the maths identical: same orbit, same eye morph cycle, same cadence, same trails.
struct GrokOrb: View {
    private enum Constants {
        static let bodyCenter = Double(GrokOrbGeometry.bodyCenter)
        static let eyeOriginY = 92.0
        static let orbitCenterX = 114.25
        static let orbitCenterY = 106.25
        static let orbitRadiusX = 148.45
        static let orbitRadiusY = 56.45
        static let orbitSpeed = 1.55096587
        static let particleMinRadius = 4.13
        static let particleRadiusRange = 3.37
        static let particleMinOpacity = 0.685
        static let particleOpacityRange = 0.315

        static let eyeCycleDuration = 13.6
        static let morphUpStart = 3.35
        static let morphUpEnd = 4.15
        static let morphDownStart = 9.25
        static let morphDownEnd = 10.05
        /// Seconds; the Expo scene uses 320 ms.
        static let expressionMorphDuration = 0.32
        static let expressionEasing = CubicBezierEasing(0.77, 0, 0.175, 1)
        static let expressionPoseMorphDuration = 0.72
    }

    var accessibilityLabel: String
    var expression: GrokOrbExpression
    /// Stops the animation on its reference pose.
    var paused: Bool
    /// Restarts the one-shot colour trails whenever this value changes.
    var trailReplayKey: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var startDate = Date()
    @State private var fromExpression: GrokOrbExpression
    @State private var toExpression: GrokOrbExpression
    @State private var transitionStartTime = 0.0
    @State private var trailStartTime = 0.0

    init(
        accessibilityLabel: String = "Animated Grok Bot Orb",
        expression: GrokOrbExpression = .natural,
        paused: Bool = false,
        trailReplayKey: Int = 0
    ) {
        self.accessibilityLabel = accessibilityLabel
        self.expression = expression
        self.paused = paused
        self.trailReplayKey = trailReplayKey
        _fromExpression = State(initialValue: expression)
        _toExpression = State(initialValue: expression)
    }

    private var isStatic: Bool { paused || reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: isStatic)) { timeline in
            Canvas { context, size in
                drawScene(in: &context, size: size, now: timeline.date)
            }
        }
        .clipped()
        .accessibilityElement()
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(.isImage)
        .onChange(of: expression) { _, next in
            guard toExpression != next else { return }
            fromExpression = toExpression
            toExpression = next
            transitionStartTime = currentTime()
        }
        .onChange(of: isStatic) {
            if fromExpression != toExpression {
                transitionStartTime = currentTime()
            }
        }
        .onChange(of: trailReplayKey, initial: true) {
            trailStartTime = currentTime()
        }
    }

    private func currentTime() -> Double {
        Date().timeIntervalSince(startDate)
    }

    // MARK: - Per-frame maths

    private static func animatedExpressionValue(
        time: Double, motion: GrokOrbExpressionMotion
    ) -> Double {
        let total = motion.totalDuration
        var segmentTime = ((time.truncatingRemainder(dividingBy: total)) + total)
            .truncatingRemainder(dividingBy: total)
        var poseIndex = 0

        for index in 0..<motion.durations.count {
            if segmentTime < motion.durations[index] {
                poseIndex = index
                break
            }
            segmentTime -= motion.durations[index]
        }

        let segmentDuration = motion.durations[poseIndex]
        let morphDuration = min(Constants.expressionPoseMorphDuration, segmentDuration * 0.42)
        let holdDuration = segmentDuration - morphDuration
        let morphProgress = GrokOrbMath.smoothstep((segmentTime - holdDuration) / morphDuration)

        return Double(poseIndex) + morphProgress
    }

    private static func eyeMorph(at time: Double) -> Double {
        let localTime = time.truncatingRemainder(dividingBy: Constants.eyeCycleDuration)

        if localTime < Constants.morphUpStart { return 0 }

        if localTime < Constants.morphUpEnd {
            return GrokOrbMath.smoothstep(
                (localTime - Constants.morphUpStart) / (Constants.morphUpEnd - Constants.morphUpStart)
            )
        }

        if localTime < Constants.morphDownStart { return 1 }

        if localTime < Constants.morphDownEnd {
            return 1 - GrokOrbMath.smoothstep(
                (localTime - Constants.morphDownStart)
                    / (Constants.morphDownEnd - Constants.morphDownStart)
            )
        }

        return 0
    }

    private static func transition(at time: Double) -> (isMorphingUp: Bool, progress: Double) {
        let localTime = time.truncatingRemainder(dividingBy: Constants.eyeCycleDuration)

        if localTime >= Constants.morphUpStart, localTime < Constants.morphUpEnd {
            return (
                true,
                (localTime - Constants.morphUpStart) / (Constants.morphUpEnd - Constants.morphUpStart)
            )
        }

        if localTime >= Constants.morphDownStart, localTime < Constants.morphDownEnd {
            return (
                false,
                (localTime - Constants.morphDownStart)
                    / (Constants.morphDownEnd - Constants.morphDownStart)
            )
        }

        return (false, -1)
    }

    private static func particleDepth(_ angle: Double) -> Double {
        (1 - sin(angle)) * 0.5
    }

    /// Interpolated eye polygon for a preset expression at `value` (pose index + morph).
    private static func presetEye(
        _ motion: GrokOrbExpressionMotion, value: Double, left: Bool
    ) -> [CGPoint] {
        let lastIndex = motion.poseIndices.count - 1
        let clamped = GrokOrbMath.clamp(value, 0, Double(lastIndex))
        let lower = min(Int(clamped.rounded(.down)), lastIndex - 1)
        let fraction = CGFloat(clamped - Double(lower))
        let fromPose = GrokOrbGeometry.poses[motion.poseIndices[lower]]
        let toPose = GrokOrbGeometry.poses[motion.poseIndices[lower + 1]]

        return GrokOrbMath.lerp(
            left ? fromPose.left : fromPose.right,
            left ? toPose.left : toPose.right,
            fraction
        )
    }

    private func drawScene(in context: inout GraphicsContext, size: CGSize, now: Date) {
        let time = isStatic ? 0 : now.timeIntervalSince(startDate)
        let trailTime = isStatic ? 0 : max(0, time - trailStartTime)
        let previousIsNatural = fromExpression.isNatural
        let expressionIsNatural = toExpression.isNatural

        let expressionProgress: Double
        if isStatic || fromExpression == toExpression {
            expressionProgress = 1
        } else {
            expressionProgress = Constants.expressionEasing(
                GrokOrbMath.clamp(
                    (time - transitionStartTime) / Constants.expressionMorphDuration, 0, 1
                )
            )
        }

        // Eye geometry -------------------------------------------------------------------

        let morph = Self.eyeMorph(at: time)
        let naturalLeft = GrokOrbMath.lerp(
            GrokOrbGeometry.lowerLeftEye, GrokOrbGeometry.upperLeftEye, CGFloat(morph)
        )
        let naturalRight = GrokOrbMath.lerp(
            GrokOrbGeometry.lowerRightEye, GrokOrbGeometry.upperRightEye, CGFloat(morph)
        )

        let previousMotion = GrokOrbGeometry.motion(for: fromExpression)
        let selectedMotion = GrokOrbGeometry.motion(for: toExpression)
        let previousValue = Self.animatedExpressionValue(time: time, motion: previousMotion)
        let selectedValue = Self.animatedExpressionValue(time: time, motion: selectedMotion)

        let fromLeft = previousIsNatural
            ? naturalLeft : Self.presetEye(previousMotion, value: previousValue, left: true)
        let fromRight = previousIsNatural
            ? naturalRight : Self.presetEye(previousMotion, value: previousValue, left: false)
        let toLeft = expressionIsNatural
            ? naturalLeft : Self.presetEye(selectedMotion, value: selectedValue, left: true)
        let toRight = expressionIsNatural
            ? naturalRight : Self.presetEye(selectedMotion, value: selectedValue, left: false)

        let leftEyePath = GrokOrbMath.polygonPath(
            GrokOrbMath.lerp(fromLeft, toLeft, CGFloat(expressionProgress))
        )
        let rightEyePath = GrokOrbMath.polygonPath(
            GrokOrbMath.lerp(fromRight, toRight, CGFloat(expressionProgress))
        )

        let naturalWeight: Double
        if previousIsNatural, expressionIsNatural {
            naturalWeight = 1
        } else if previousIsNatural {
            naturalWeight = 1 - expressionProgress
        } else if expressionIsNatural {
            naturalWeight = expressionProgress
        } else {
            naturalWeight = 0
        }
        let presetWeight = 1 - naturalWeight

        // Transforms -----------------------------------------------------------------------

        let bodyTranslateX = 1.4878 * sin((Double.pi * 2 * time) / 18.29 - 3.0698)
        let bodyTranslateY = 1.4539 * sin((Double.pi * 2 * time) / 7.835 - 0.828)
        let bodyRotation = (1.9847 * sin((Double.pi * 2 * time) / 13.71 + 2.2731)) * Double.pi / 180

        let cycle = Self.transition(at: time)
        let selectionPulse = sin(Double.pi * expressionProgress)
        let eyeScaleX: Double
        let eyeScaleY: Double
        if cycle.progress < 0 {
            let selectionScale = 1 + 0.045 * selectionPulse
            eyeScaleX = selectionScale
            eyeScaleY = selectionScale
        } else {
            let naturalPulse = sin(Double.pi * cycle.progress)
            let overshoot = 1 + 0.052 * naturalPulse + 0.045 * selectionPulse
            let blink = cycle.isMorphingUp ? pow(naturalPulse, 10) * 0.58 : 0
            eyeScaleX = overshoot
            eyeScaleY = overshoot * (1 - blink)
        }

        let leftEyeTranslateX =
            naturalWeight * (-4.05 + 0.22 * morph + 0.85 * sin(time * 0.71))
            + presetWeight * (0.68 * sin(time * 0.71) + 0.2 * sin(time * 1.31 + 0.4))
        let leftEyeTranslateY =
            naturalWeight * (-2.9 + 4.25 * morph + 0.7 * sin(time * 0.93 + 1.1))
            + presetWeight * 0.5 * sin(time * 0.93 + 1.1)
        let rightEyeTranslateX =
            naturalWeight * (-5.6 + 1.1 * morph + 0.8 * sin(time * 0.66 + 2.1))
            + presetWeight * (0.64 * sin(time * 0.66 + 2.1) + 0.18 * sin(time * 1.23 + 1.7))
        let rightEyeTranslateY =
            naturalWeight * (-2.1 + 2.6 * morph + 0.65 * sin(time * 0.87 + 2.4))
            + presetWeight * 0.46 * sin(time * 0.87 + 2.4)

        // Layout: centre the Orb, scale it to 72% of the shorter side and mirror it horizontally
        // exactly like the Expo scene (`scaleX: -orbScale`).
        let center = Constants.bodyCenter
        let orbScale = Double(min(size.width, size.height)) * 0.36 / center

        context.translateBy(x: size.width / 2, y: size.height / 2)
        context.scaleBy(x: -orbScale, y: orbScale)
        context.translateBy(x: -center, y: -center)

        GrokOrbTrails.draw(layer: .back, time: trailTime, in: &context)

        // Orbiting particles ---------------------------------------------------------------

        for phase in [0.42, 0.42 + Double.pi] {
            let angle = time * Constants.orbitSpeed + phase
            let depth = Self.particleDepth(angle)
            let radius = Constants.particleMinRadius + Constants.particleRadiusRange * depth
            let opacity = Constants.particleMinOpacity + Constants.particleOpacityRange * depth
            let centerX = Constants.orbitCenterX + Constants.orbitRadiusX * cos(angle)
            let centerY = Constants.orbitCenterY + Constants.orbitRadiusY * sin(angle)
            let circle = Path(
                ellipseIn: CGRect(
                    x: centerX - radius, y: centerY - radius, width: radius * 2, height: radius * 2
                )
            )
            context.fill(circle, with: .color(GrokOrbPalette.body.opacity(opacity)))
        }

        // Body + eyes ----------------------------------------------------------------------

        var bodyContext = context
        bodyContext.translateBy(x: center, y: center)
        bodyContext.translateBy(x: bodyTranslateX, y: bodyTranslateY)
        bodyContext.rotate(by: .radians(bodyRotation))
        bodyContext.translateBy(x: -center, y: -center)
        bodyContext.fill(GrokOrbGeometry.bodyPath, with: .color(GrokOrbPalette.body))

        bodyContext.clip(to: GrokOrbGeometry.bodyPath)
        bodyContext.translateBy(x: center, y: Constants.eyeOriginY)
        bodyContext.scaleBy(x: eyeScaleX, y: eyeScaleY)
        bodyContext.translateBy(x: -center, y: -Constants.eyeOriginY)

        var leftEyeContext = bodyContext
        leftEyeContext.translateBy(x: leftEyeTranslateX, y: leftEyeTranslateY)
        leftEyeContext.fill(leftEyePath, with: .color(GrokOrbPalette.eye))

        var rightEyeContext = bodyContext
        rightEyeContext.translateBy(x: rightEyeTranslateX, y: rightEyeTranslateY)
        rightEyeContext.fill(rightEyePath, with: .color(GrokOrbPalette.eye))

        GrokOrbTrails.draw(layer: .front, time: trailTime, in: &context)
    }
}

#Preview {
    GrokOrb(expression: .happy)
        .frame(height: 420)
        .background(GrokOrbPalette.screenBackground)
}
