import SwiftUI

/// Every pose the Orb can express. `natural` keeps the ambient website behaviour; the other
/// cases loop through preset pose sequences. Mirrors `grok-orb-expressions.ts`.
enum GrokOrbExpression: String, CaseIterable, Identifiable, Hashable {
    case natural
    case happy
    case surprised
    case focused
    case curious
    case sleepy
    case skeptical
    case angry
    case confused
    case sad
    case playful
    case shy

    static let defaultExpression: GrokOrbExpression = .natural

    var id: String { rawValue }

    var isNatural: Bool { self == .natural }

    var option: GrokOrbExpressionOption {
        GrokOrbExpressionOption.all.first { $0.id == self }!
    }
}

struct GrokOrbExpressionOption: Identifiable, Hashable {
    let id: GrokOrbExpression
    let label: String
    let description: String
    let accent: Color

    static let all: [GrokOrbExpressionOption] = [
        .init(
            id: .natural,
            label: "Tự nhiên",
            description: "Thư thái với chuyển động biểu cảm tự động.",
            accent: Color(hex: 0x8EC5FF)
        ),
        .init(
            id: .happy,
            label: "Vui vẻ",
            description: "Hào hứng, tích cực và đầy năng lượng.",
            accent: Color(hex: 0xF3D36A)
        ),
        .init(
            id: .surprised,
            label: "Ngạc nhiên",
            description: "Bất ngờ và lập tức chú ý.",
            accent: Color(hex: 0xB79CFF)
        ),
        .init(
            id: .focused,
            label: "Tập trung",
            description: "Sẵn sàng xử lý nhiệm vụ trước mắt.",
            accent: Color(hex: 0x72D7C0)
        ),
        .init(
            id: .curious,
            label: "Tò mò",
            description: "Muốn quan sát và khám phá thêm.",
            accent: Color(hex: 0x72B9FF)
        ),
        .init(
            id: .sleepy,
            label: "Buồn ngủ",
            description: "Chậm rãi với đôi mí mắt nặng.",
            accent: Color(hex: 0xAAA9CE)
        ),
        .init(
            id: .skeptical,
            label: "Nghi ngờ",
            description: "Đang cân nhắc lại thông tin vừa nhận.",
            accent: Color(hex: 0xF0A36B)
        ),
        .init(
            id: .angry,
            label: "Giận dữ",
            description: "Quyết liệt và không hài lòng.",
            accent: Color(hex: 0xFF7A6E)
        ),
        .init(
            id: .confused,
            label: "Bối rối",
            description: "Chưa hiểu điều vừa mới xảy ra.",
            accent: Color(hex: 0xC48CFF)
        ),
        .init(
            id: .sad,
            label: "Buồn",
            description: "Trầm xuống và ít năng lượng hơn.",
            accent: Color(hex: 0x7FA4C9)
        ),
        .init(
            id: .playful,
            label: "Tinh nghịch",
            description: "Vui đùa với một chút láu lỉnh.",
            accent: Color(hex: 0xF48EC7)
        ),
        .init(
            id: .shy,
            label: "Ngại ngùng",
            description: "Kín đáo, nhẹ nhàng và dè dặt.",
            accent: Color(hex: 0xD9A1BD)
        ),
    ]
}

/// Pose sequences and cadence ranges copied from `GROK_ORB_EXPRESSION_SEQUENCES` and
/// `GROK_ORB_EXPRESSION_TIMING_RANGES`.
enum GrokOrbExpressionData {
    /// Insertion order of the Expo object literal. It matters: the cadence variation picked for
    /// each pose depends on the preset's index.
    static let presets: [GrokOrbExpression] = [
        .happy, .surprised, .focused, .curious, .sleepy, .skeptical,
        .angry, .confused, .sad, .playful, .shy,
    ]

    /// Indices into `GrokOrbPathData.poses`.
    static let sequences: [GrokOrbExpression: [Int]] = [
        .happy: [2, 11, 17, 19],
        .surprised: [3, 21],
        .focused: [8, 16, 14, 17, 5],
        .curious: [3, 21, 0, 15],
        .sleepy: [4, 22, 13],
        .skeptical: [14, 5, 23],
        .angry: [7, 16],
        .confused: [14, 5, 8],
        .sad: [4, 13, 22],
        .playful: [2, 17, 11, 8],
        .shy: [0, 24, 13],
    ]

    /// Seconds between pose changes (minimum, maximum), matched to the cadence used by x.ai.
    static let timingRanges: [GrokOrbExpression: (minimum: Double, maximum: Double)] = [
        .happy: (2.5, 4.5),
        .surprised: (2.5, 4),
        .focused: (2, 3.6),
        .curious: (1.8, 3.2),
        .sleepy: (4, 8),
        .skeptical: (2.6, 4.5),
        .angry: (2.2, 3.8),
        .confused: (2.2, 3.8),
        .sad: (4, 7),
        .playful: (1.5, 3),
        .shy: (3, 5.5),
    ]
}

enum GrokOrbPalette {
    static let screenBackground = Color(hex: 0x0B0B0B)
    static let cardBackground = Color(hex: 0x181818)
    static let body = Color.white
    static let eye = Color(hex: 0x1A1A1A)
}

extension Color {
    /// Builds an sRGB colour from a `0xRRGGBB` literal.
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
