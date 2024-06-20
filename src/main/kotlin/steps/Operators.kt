package steps

import JsonDefinedOperator
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import getOperatorJsonDefined
import incrementValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import percentageString
import toDescendingSortedList
import traewelling.TraewellingJson
import java.nio.file.Path
import kotlin.io.path.inputStream

class Operators(term: Terminal, private val atMost: Int) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        val json = Json { ignoreUnknownKeys = true }
        val jsonDefinedOperators =
            json.decodeFromStream<List<JsonDefinedOperator>>(Path.of("operators.json").inputStream())
        val operators = mutableMapOf<String, Int>()

        data.entries.forEach {
            val traewellingName = it.status.train.operator?.name
            val originStation = it.trip.origin.name
            val destinationStation = it.trip.destination.name

            // Give priority to whatever operator is provided by Träwelling.
            // They commonly return null for local transport operators, in which case we will try to fall back to our
            // own matching logic and see if we can guess the correct operator. If that still doesn't work, too bad.
            val operator = when {
                traewellingName != null -> traewellingName
                else -> getOperatorJsonDefined(
                    jsonDefinedOperators, it.trip.lineName, originStation, destinationStation
                ) ?: ""
            }
            operators.incrementValue(operator)
        }

        val totalOperatorCount = operators.map { it.value }.sum()
        term.println(table {
            tableBorders = Borders.NONE
            borderType = BorderType.BLANK

            header {
                style = TextStyle(bold = true)
                cellBorders = Borders.NONE

                row("Operator", "Count", "%")
            }
            body {
                cellBorders = Borders.NONE

                operators.toDescendingSortedList().take(atMost).forEach { (operator, count) ->
                    row(operator, count, percentageString(count, totalOperatorCount))
                }
            }
            if (operators.size > atMost) {
                footer {
                    cellBorders = Borders.NONE
                    row("... and ${operators.size - atMost} more")
                }
            }
        })
    }
}