package steps

import JsonDefinedOperator
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import getOperatorJsonDefined
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import traewelling.TraewellingJson
import java.nio.file.Path
import kotlin.io.path.inputStream

class AllLines(term: Terminal, private val operatorJsonPath: Path) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        val json = Json { ignoreUnknownKeys = true }
        val jsonDefinedOperators = json.decodeFromStream<List<JsonDefinedOperator>>(operatorJsonPath.inputStream())
        val seen = mutableSetOf<String>()
        data.entries.sortedBy { it.trip.lineName }.forEach {
            val traewellingName = it.status.train.operator?.name
            val originStation = it.trip.origin.name
            val destinationStation = it.trip.destination.name

            // Prioritize whatever operator provided by Träwelling.
            // They commonly return null for local transport operators, in which case we will try to fall back to our
            // own matching logic and see if we can guess the correct operator. If that still doesn't work, too bad.
            val evu = when {
                traewellingName != null -> traewellingName
                else -> getOperatorJsonDefined(
                    jsonDefinedOperators, it.trip.lineName, originStation, destinationStation
                ) ?: "<unknown>"
            }

            val s = "${it.trip.lineName} ${TextColors.gray("from $originStation to $destinationStation")} $evu"
            if (s !in seen) {
                term.println(s)
                seen += s
            }
        }
    }
}