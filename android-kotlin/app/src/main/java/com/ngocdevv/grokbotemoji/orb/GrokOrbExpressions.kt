package com.ngocdevv.grokbotemoji.orb

import androidx.compose.ui.graphics.Color

/**
 * Every pose the Orb can express. [Natural] keeps the ambient website behaviour; the other
 * entries loop through preset pose sequences. Mirrors `grok-orb-expressions.ts`.
 */
enum class GrokOrbExpression(
    val id: String,
    val label: String,
    val description: String,
    val accent: Color,
) {
    Natural("natural", "Tự nhiên", "Thư thái với chuyển động biểu cảm tự động.", Color(0xFF8EC5FF)),
    Happy("happy", "Vui vẻ", "Hào hứng, tích cực và đầy năng lượng.", Color(0xFFF3D36A)),
    Surprised("surprised", "Ngạc nhiên", "Bất ngờ và lập tức chú ý.", Color(0xFFB79CFF)),
    Focused("focused", "Tập trung", "Sẵn sàng xử lý nhiệm vụ trước mắt.", Color(0xFF72D7C0)),
    Curious("curious", "Tò mò", "Muốn quan sát và khám phá thêm.", Color(0xFF72B9FF)),
    Sleepy("sleepy", "Buồn ngủ", "Chậm rãi với đôi mí mắt nặng.", Color(0xFFAAA9CE)),
    Skeptical("skeptical", "Nghi ngờ", "Đang cân nhắc lại thông tin vừa nhận.", Color(0xFFF0A36B)),
    Angry("angry", "Giận dữ", "Quyết liệt và không hài lòng.", Color(0xFFFF7A6E)),
    Confused("confused", "Bối rối", "Chưa hiểu điều vừa mới xảy ra.", Color(0xFFC48CFF)),
    Sad("sad", "Buồn", "Trầm xuống và ít năng lượng hơn.", Color(0xFF7FA4C9)),
    Playful("playful", "Tinh nghịch", "Vui đùa với một chút láu lỉnh.", Color(0xFFF48EC7)),
    Shy("shy", "Ngại ngùng", "Kín đáo, nhẹ nhàng và dè dặt.", Color(0xFFD9A1BD));

    val isNatural: Boolean
        get() = this == Natural

    companion object {
        val Default: GrokOrbExpression = Natural
    }
}

/**
 * Pose sequences and cadence ranges copied from `GROK_ORB_EXPRESSION_SEQUENCES` and
 * `GROK_ORB_EXPRESSION_TIMING_RANGES`.
 */
object GrokOrbExpressionData {
    /**
     * Insertion order of the Expo object literal. It matters: the cadence variation picked for
     * each pose depends on the preset's index.
     */
    val presets: List<GrokOrbExpression> = listOf(
        GrokOrbExpression.Happy,
        GrokOrbExpression.Surprised,
        GrokOrbExpression.Focused,
        GrokOrbExpression.Curious,
        GrokOrbExpression.Sleepy,
        GrokOrbExpression.Skeptical,
        GrokOrbExpression.Angry,
        GrokOrbExpression.Confused,
        GrokOrbExpression.Sad,
        GrokOrbExpression.Playful,
        GrokOrbExpression.Shy,
    )

    /** Indices into [GrokOrbPathData.POSES]. */
    val sequences: Map<GrokOrbExpression, List<Int>> = mapOf(
        GrokOrbExpression.Happy to listOf(2, 11, 17, 19),
        GrokOrbExpression.Surprised to listOf(3, 21),
        GrokOrbExpression.Focused to listOf(8, 16, 14, 17, 5),
        GrokOrbExpression.Curious to listOf(3, 21, 0, 15),
        GrokOrbExpression.Sleepy to listOf(4, 22, 13),
        GrokOrbExpression.Skeptical to listOf(14, 5, 23),
        GrokOrbExpression.Angry to listOf(7, 16),
        GrokOrbExpression.Confused to listOf(14, 5, 8),
        GrokOrbExpression.Sad to listOf(4, 13, 22),
        GrokOrbExpression.Playful to listOf(2, 17, 11, 8),
        GrokOrbExpression.Shy to listOf(0, 24, 13),
    )

    /** Seconds between pose changes (minimum to maximum), matched to the cadence used by x.ai. */
    val timingRanges: Map<GrokOrbExpression, Pair<Double, Double>> = mapOf(
        GrokOrbExpression.Happy to (2.5 to 4.5),
        GrokOrbExpression.Surprised to (2.5 to 4.0),
        GrokOrbExpression.Focused to (2.0 to 3.6),
        GrokOrbExpression.Curious to (1.8 to 3.2),
        GrokOrbExpression.Sleepy to (4.0 to 8.0),
        GrokOrbExpression.Skeptical to (2.6 to 4.5),
        GrokOrbExpression.Angry to (2.2 to 3.8),
        GrokOrbExpression.Confused to (2.2 to 3.8),
        GrokOrbExpression.Sad to (4.0 to 7.0),
        GrokOrbExpression.Playful to (1.5 to 3.0),
        GrokOrbExpression.Shy to (3.0 to 5.5),
    )
}

object GrokOrbPalette {
    val ScreenBackground = Color(0xFF0B0B0B)
    val CardBackground = Color(0xFF181818)
    val Body = Color.White
    val Eye = Color(0xFF1A1A1A)
}
