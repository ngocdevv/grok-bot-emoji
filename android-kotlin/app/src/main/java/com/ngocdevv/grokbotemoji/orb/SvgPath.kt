package com.ngocdevv.grokbotemoji.orb

import androidx.compose.ui.graphics.Path

/**
 * Minimal parser for the SVG path subset used by the Orb assets: absolute `M`, `L`, `C` and `Z`.
 * Skia parsed these strings at runtime in the Expo version; here we do the same once at launch.
 */
object SvgPath {
    private sealed interface Token {
        data class Command(val letter: Char) : Token
        data class Number(val value: Float) : Token
    }

    /** Builds a Compose [Path] from the path data. */
    fun path(data: String, target: Path = Path()): Path {
        val tokens = tokenize(data)
        var command = 'M'
        var index = 0

        fun nextNumber(): Float {
            val token = tokens.getOrNull(index)
            index += 1
            return (token as? Token.Number)?.value ?: 0f
        }

        while (index < tokens.size) {
            val token = tokens[index]
            if (token is Token.Command) {
                command = token.letter
                index += 1
                if (command == 'Z' || command == 'z') target.close()
                continue
            }

            when (command) {
                'M' -> {
                    val x = nextNumber()
                    val y = nextNumber()
                    target.moveTo(x, y)
                    command = 'L'
                }
                'L' -> {
                    val x = nextNumber()
                    val y = nextNumber()
                    target.lineTo(x, y)
                }
                'C' -> {
                    val x1 = nextNumber()
                    val y1 = nextNumber()
                    val x2 = nextNumber()
                    val y2 = nextNumber()
                    val x3 = nextNumber()
                    val y3 = nextNumber()
                    target.cubicTo(x1, y1, x2, y2, x3, y3)
                }
                else -> index += 1
            }
        }

        return target
    }

    /** Extracts the vertices of a closed polyline path (`M x y L x y … Z`) as `[x0, y0, x1, y1, …]`. */
    fun polygon(data: String): FloatArray {
        val numbers = tokenize(data).filterIsInstance<Token.Number>().map { it.value }
        require(numbers.size % 2 == 0) { "Odd coordinate count in Orb eye path." }
        return numbers.toFloatArray()
    }

    private fun tokenize(data: String): List<Token> {
        val tokens = ArrayList<Token>(data.length / 6)
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                buffer.toString().toFloatOrNull()?.let { tokens.add(Token.Number(it)) }
                buffer.setLength(0)
            }
        }

        for (character in data) {
            when {
                character.isLetter() -> {
                    flush()
                    tokens.add(Token.Command(character))
                }
                character == '-' -> {
                    flush()
                    buffer.append(character)
                }
                character.isWhitespace() || character == ',' -> flush()
                else -> buffer.append(character)
            }
        }
        flush()

        return tokens
    }
}
