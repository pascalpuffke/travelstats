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

class Events(term: Terminal) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        term.println(TextStyles.bold(TextStyles.underline("Events")))

        val events = mutableMapOf<String, Int>()
        data.entries.map { it.status.event?.name ?: "[No event]" }.forEach { events.incrementValue(it) }

        val totalEventCount = events.map { it.value }.sum()
        term.println(table {
            borderType = BorderType.ROUNDED
            header {
                style = TextStyle(bold = true)
                cellBorders = Borders.NONE

                row("Event", "Count", "%")
            }
            body {
                cellBorders = Borders.NONE

                events.toDescendingSortedList().forEach { (event, count) ->
                    row(event, count, percentageString(count, totalEventCount))
                }
            }
        })
    }
}
