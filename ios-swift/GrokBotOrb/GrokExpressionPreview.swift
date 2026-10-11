import SwiftUI

/// Static thumbnail of an expression (port of `grok-expression-preview.tsx`). It paints the
/// body plus the first pose of the expression without the horizontal mirror or any motion,
/// matching the Expo thumbnails pixel for pixel.
struct GrokExpressionPreview: View {
    let expression: GrokOrbExpression
    let size: CGFloat

    private static let eyePaths: [GrokOrbExpression: (left: Path, right: Path)] = Dictionary(
        uniqueKeysWithValues: GrokOrbExpression.allCases.map { expression in
            let eyes = GrokOrbGeometry.previewEyes(for: expression)
            return (
                expression,
                (GrokOrbMath.polygonPath(eyes.left), GrokOrbMath.polygonPath(eyes.right))
            )
        }
    )

    var body: some View {
        Canvas { context, canvasSize in
            let scale = canvasSize.width / GrokOrbGeometry.orbSize
            context.scaleBy(x: scale, y: scale)
            context.fill(GrokOrbGeometry.bodyPath, with: .color(GrokOrbPalette.body))

            if let eyes = Self.eyePaths[expression] {
                context.fill(eyes.left, with: .color(GrokOrbPalette.eye))
                context.fill(eyes.right, with: .color(GrokOrbPalette.eye))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

#Preview {
    HStack {
        ForEach(GrokOrbExpression.allCases) { expression in
            GrokExpressionPreview(expression: expression, size: 48)
        }
    }
    .padding()
    .background(GrokOrbPalette.screenBackground)
}
