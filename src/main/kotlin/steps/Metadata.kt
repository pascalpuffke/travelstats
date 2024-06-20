package steps

import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import traewelling.TraewellingJson

class Metadata(term: Terminal) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        with(data.meta) {
            term.println("\n${from.dropLast(6).replace('T', ' ')} - ${to.dropLast(6).replace('T', ' ')}\n")
            term.println("${TextStyles.bold("Distance travelled: ")}${user.trainDistance / 1000} km")
            term.println("${TextStyles.bold("Duration: ")}${user.trainDuration / 60} h")
            term.println("${TextStyles.bold("Points: ")}${user.points} (total: ${data.entries.sumOf { it.status.train.points }})")
        }
    }
}
