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

class Lines(term: Terminal, private val atMost: Int) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        (if (atMost == Int.MAX_VALUE) "all time" else "top $atMost").let { title ->
            term.println(TextStyles.bold(TextStyles.underline("Lines ($title)")))
        }

        val lines = mutableMapOf<String, Int>()
        data.entries.map { it.trip.lineName }.forEach { lines.incrementValue(it) }

        val totalLineCount = lines.map { it.value }.sum()
        term.println(table {
            tableBorders = Borders.NONE
            borderType = BorderType.BLANK

            header {
                style = TextStyle(bold = true)
                cellBorders = Borders.NONE

                row("Line", "Count", "%")
            }
            body {
                cellBorders = Borders.NONE

                lines.toDescendingSortedList().take(atMost).forEach { (line, count) ->
                    row(line, count, percentageString(count, totalLineCount))
                }
            }
            if (lines.size > atMost) {
                footer {
                    cellBorders = Borders.NONE
                    row("... and ${lines.size - atMost} more")
                }
            }
        })
    }
}