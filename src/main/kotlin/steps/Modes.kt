package steps

import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import incrementValue
import percentageString
import toDescendingSortedList
import traewelling.TraewellingJson

class Modes(term: Terminal, private val atMost: Int) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        fun toFancyModeString(mode: String) = when (mode) {
            "bus" -> "Bus"
            "tram" -> "Tram"
            "suburban" -> "S-Bahn"
            "subway" -> "U-Bahn"
            "regional" -> "Regional (RB, RE, ...)"
            "regionalExp" -> "Fernverkehr (andere)" // ???????
            "national" -> "Fernverkehr (IC, EC, ...)"
            "nationalExpress" -> "Fernverkehr (ICE, ...)"
            "ferry" -> "Fähre"
            else -> mode.replaceFirstChar { it.uppercaseChar() }
        }

        (if (atMost == Int.MAX_VALUE) "all time" else "top $atMost").let { title ->
            term.println(TextStyles.bold(TextStyles.underline("Modes ($title)")))
        }

        val modes = mutableMapOf<String, Int>()
        data.entries.map { toFancyModeString(it.status.train.category) }.forEach { modes.incrementValue(it) }

        val totalModesCount = modes.map { it.value }.sum()
        term.println(table {
            borderType = BorderType.ROUNDED
            header {
                style = TextStyle(bold = true)
                cellBorders = Borders.NONE

                row("Mode", "Count", "%")
            }
            body {
                cellBorders = Borders.NONE

                modes.toDescendingSortedList().take(atMost).forEach { (mode, count) ->
                    row(mode, count, percentageString(count, totalModesCount))
                }
            }
            if (modes.size > atMost) {
                footer {
                    cellBorders = Borders.NONE
                    row("... and ${modes.size - atMost} more")
                }
            }
        })
    }
}