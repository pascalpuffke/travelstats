package steps

import com.github.ajalt.mordant.terminal.Terminal
import traewelling.TraewellingJson

abstract class StatisticStep(internal val term: Terminal) {
    abstract fun exec(data: TraewellingJson)
}
