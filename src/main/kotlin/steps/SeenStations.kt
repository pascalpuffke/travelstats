package steps

import com.github.ajalt.mordant.terminal.Terminal
import incrementValue
import toDescendingSortedList
import traewelling.TraewellingJson

class SeenStations(term: Terminal) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        val seenStations = mutableMapOf<String, Int>()
        data.entries.map { it.status.train }.forEach {
            seenStations.incrementValue(it.origin.name)
            seenStations.incrementValue(it.destination.name)
        }
        seenStations.toDescendingSortedList().forEach { (station, count) ->
            println("$count $station")
        }
    }
}