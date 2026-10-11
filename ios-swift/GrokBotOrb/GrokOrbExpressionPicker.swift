import SwiftUI

/// Port of `grok-orb-expression-picker.tsx`: the animated Orb, a responsive grid of expression
/// thumbnails and the colour-trail replay triggered by the "Tự nhiên" card.
///
/// Uncontrolled by default; pass `expression:` to control the selection from a parent.
struct GrokOrbExpressionPicker: View {
    private static let contentMaxWidth: CGFloat = 820
    private static let gridGap: CGFloat = 10
    static let cornerRadius: CGFloat = 22

    private let accessibilityLabel: String?
    private let externalExpression: Binding<GrokOrbExpression>?
    private let onExpressionChange: ((GrokOrbExpression) -> Void)?
    private let paused: Bool

    @State private var internalExpression: GrokOrbExpression
    @State private var measuredWidth: CGFloat = 0
    @State private var trailReplayKey = 0

    init(
        defaultExpression: GrokOrbExpression = .natural,
        expression: Binding<GrokOrbExpression>? = nil,
        onExpressionChange: ((GrokOrbExpression) -> Void)? = nil,
        paused: Bool = false,
        accessibilityLabel: String? = nil
    ) {
        self.accessibilityLabel = accessibilityLabel
        self.externalExpression = expression
        self.onExpressionChange = onExpressionChange
        self.paused = paused
        _internalExpression = State(initialValue: defaultExpression)
    }

    private var selectedExpression: GrokOrbExpression {
        externalExpression?.wrappedValue ?? internalExpression
    }

    var body: some View {
        let contentWidth = max(1, measuredWidth)
        let columnCount = Self.columnCount(for: contentWidth)
        let itemSize = max(
            1, (contentWidth - Self.gridGap * CGFloat(columnCount - 1)) / CGFloat(columnCount)
        )
        let orbHeight = min(430, max(320, contentWidth))
        let rows = Self.chunk(GrokOrbExpressionOption.all, size: columnCount)

        VStack(spacing: 8) {
            GrokOrb(
                accessibilityLabel: accessibilityLabel
                    ?? "Orb đang thể hiện trạng thái \(selectedExpression.option.label)",
                expression: selectedExpression,
                paused: paused,
                trailReplayKey: trailReplayKey
            )
            .frame(maxWidth: .infinity)
            .frame(height: orbHeight)
            .clipped()

            VStack(alignment: .leading, spacing: Self.gridGap) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: Self.gridGap) {
                        ForEach(row) { option in
                            GrokExpressionItem(
                                option: option,
                                selected: option.id == selectedExpression,
                                size: itemSize,
                                onSelect: select
                            )
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .contain)
        }
        .frame(maxWidth: Self.contentMaxWidth)
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.width
        } action: { width in
            measuredWidth = width
        }
    }

    private func select(_ next: GrokOrbExpression) {
        if let externalExpression {
            externalExpression.wrappedValue = next
        } else {
            internalExpression = next
        }

        onExpressionChange?(next)

        if next.isNatural {
            trailReplayKey += 1
        }
    }

    private static func columnCount(for width: CGFloat) -> Int {
        if width >= 728 { return 6 }
        if width >= 448 { return 5 }
        if width < 328 { return 3 }
        return 4
    }

    private static func chunk<T>(_ items: [T], size: Int) -> [[T]] {
        stride(from: 0, to: items.count, by: size).map {
            Array(items[$0..<min($0 + size, items.count)])
        }
    }
}

/// One radio-style card. The press feedback mirrors the Expo `Pressable`: 0.98 scale over
/// 100 ms on press-in, back to 1 over 130 ms on release, cubic-bezier(0.23, 1, 0.32, 1).
private struct GrokExpressionItem: View {
    let option: GrokOrbExpressionOption
    let selected: Bool
    let size: CGFloat
    let onSelect: (GrokOrbExpression) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let shape = RoundedRectangle(
            cornerRadius: GrokOrbExpressionPicker.cornerRadius, style: .continuous
        )

        Button {
            onSelect(option.id)
        } label: {
            ZStack {
                shape.fill(GrokOrbPalette.cardBackground)
                GrokExpressionPreview(expression: option.id, size: max(1, size - 20))
            }
            .overlay(
                shape.strokeBorder(selected ? option.accent : Color.clear, lineWidth: 2)
            )
            .frame(width: size, height: size)
            .clipShape(shape)
            .contentShape(shape)
        }
        .buttonStyle(GrokExpressionCardButtonStyle(reduceMotion: reduceMotion))
        .accessibilityLabel("\(option.label). \(option.description)")
        .accessibilityHint("Chuyển Orb sang biểu cảm \(option.label)")
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

private struct GrokExpressionCardButtonStyle: ButtonStyle {
    let reduceMotion: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
            .animation(
                .timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? 0.1 : 0.13),
                value: configuration.isPressed
            )
    }
}

#Preview {
    ScrollView {
        GrokOrbExpressionPicker()
            .padding(16)
    }
    .background(GrokOrbPalette.screenBackground)
}
