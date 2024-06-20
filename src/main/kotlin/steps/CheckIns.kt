package steps

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import delay
import toZonedDateTime
import traewelling.TraewellingJson
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt

class CheckIns(term: Terminal) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        data.entries.forEach { travel ->
            val status = travel.status
            val train = status.train
            val distanceString = String.format("%.02f", train.distance / 1000.0)
            val departureDateTime = toZonedDateTime(train.origin.departure)
            val arrivalDateTime = toZonedDateTime(train.destination.arrival)
            val delayAtOrigin = delay(train.origin)
            val delayAtDestination = delay(train.destination)
            val duration = train.duration
            val speed = ((train.distance / 1000.0) / (duration / 60.0)).roundToInt()

            val formatter = DateTimeFormatter.ofPattern("EEEE, dd. LLLL yyyy HH:mm", Locale.GERMANY)
            val formattedDepartureTime = departureDateTime.format(formatter)
            val formattedArrivalTime = arrivalDateTime.format(formatter)

            term.println("${train.lineName} ${TextColors.gray("${train.origin.name} -> ${train.destination.name}")}")

            status.event?.let { term.println("\tEvent: ${it.name}") }

            if (status.body.isNotEmpty()) term.println("\tNote: \"${status.body}\"")

            term.println(
                "\t${formattedDepartureTime} (${delayAtOrigin.first(delayAtOrigin.second)}) -> $formattedArrivalTime (${
                    delayAtDestination.first(
                        delayAtDestination.second
                    )
                })"
            )
            term.println("\t${distanceString} km, $duration min ($speed km/h)")
        }
    }
}
