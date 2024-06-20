package steps

import LONG_DISTANCE_PREFIXES
import REGIONAL_PREFIXES
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import traewelling.TraewellingJson

class ModeStats(term: Terminal) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        data class ModeStats(val checkins: Int, val duration: Long, val distance: Long)

        fun getModeStats(filter: (String) -> Boolean): ModeStats {
            val filtered = data.entries.map { it.status.train }.filter { filter(it.lineName) }

            return ModeStats(checkins = filtered.count(),
                duration = filtered.sumOf { it.duration } / 60,
                distance = filtered.sumOf { it.distance } / 1000)
        }

        fun printModeStats(name: String, filter: (String) -> Boolean) = getModeStats(filter).let {
            term.println(
                "${TextColors.red(name)}: ${TextStyles.bold(it.checkins.toString())} check-ins, ${TextStyles.bold(it.distance.toString())} km, ${
                    TextStyles.bold(
                        it.duration.toString()
                    )
                } hours, ${TextStyles.bold((it.distance / it.duration).toString())} km/h"
            )
        }

        printModeStats("Fernverkehr") {
            it.split(' ').firstOrNull() in LONG_DISTANCE_PREFIXES
        }
        printModeStats("Regional   ") {
            if (it.contains(' ')) if (it.split(' ').firstOrNull() in REGIONAL_PREFIXES) return@printModeStats true
            else REGIONAL_PREFIXES.forEach { prefix ->
                if (it.startsWith(prefix)) return@printModeStats true
            }
            false
        }
        printModeStats("S-Bahn     ") { it.startsWith("S") && !it.startsWith("ST") }
        printModeStats("U-Bahn     ") { it.startsWith("U") }
        printModeStats("Tram       ") { it.startsWith("ST") }
        printModeStats("Bus        ") { it.startsWith("Bus") }
    }
}