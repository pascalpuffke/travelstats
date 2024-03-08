@file:Suppress("SpellCheckingInspection") @file:OptIn(ExperimentalSerializationApi::class)

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.underline
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import traewelling.DataEntry
import traewelling.Meta
import traewelling.TraewellingJson
import traewelling.TrainOriginOrDestination
import java.io.IOException
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.math.roundToInt
import kotlin.system.exitProcess

private const val TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssxxxxx"
private const val ON_TIME_RANGE_SECONDS = 60

val LONG_DISTANCE_PREFIXES = setOf(
    "IC", "ICE", "RJ", "RJX", "NJ", "EC", "ECE", "EN", "FLX", "TLK"
)

val REGIONAL_PREFIXES = setOf(
    "AKN",
    "BRB",
    "DWE",
    "FEX",
    "IRE",
    "ME",
    "MEX",
    "NBE",
    "R",
    "RB",
    "RE",
    "REX",
    "TER",
    "TL",
    "TLX",
    "VBG",
    "WFB",
    "erx",
)

private fun <K, V : Comparable<V>> Map<K, V>.toDescendingSortedList() = this.toList().sortedByDescending { it.second }

private fun <K> MutableMap<K, Int>.incrementValue(key: K, default: Int = 0) =
    this.set(key, this.getOrDefault(key, default) + 1)

private fun toZonedDateTime(date: String, pattern: String = TIME_FORMAT) =
    ZonedDateTime.parse(date, DateTimeFormatter.ofPattern(pattern))

private fun toUnixTime(date: String) = toZonedDateTime(date).toEpochSecond()

private fun delay(originOrDestination: TrainOriginOrDestination): Pair<TextColors, String> {
    val departure = toUnixTime(
        originOrDestination.departureReal ?: originOrDestination.departure
    ) - toUnixTime(originOrDestination.departurePlanned ?: originOrDestination.departure)
    val arrival = toUnixTime(
        originOrDestination.arrivalReal ?: originOrDestination.arrival
    ) - toUnixTime(originOrDestination.arrivalPlanned ?: originOrDestination.arrival)
    val accumulated = departure + arrival

    return when {
        accumulated > ON_TIME_RANGE_SECONDS -> red to "+$accumulated sec"
        accumulated < 0 -> yellow to "$accumulated sec"
        else -> green to "on time"
    }
}

private fun eprintln(message: Any?) = System.err.println(message)

private fun exitError(code: Int, message: String): Nothing {
    eprintln(message)
    exitProcess(code)
}

// For some reason Kotlin style format strings do not support float precision, so we have to do it the Java (or C) way.
private fun percentageString(n: Int, total: Int, precision: Int = 2) =
    String.format("%.${precision}f", (n / total.toFloat()) * 100)

@Serializable
data class JsonDefinedLine(val line: String, val from: String?, val to: String?)

@Serializable
data class JsonDefinedOperator(
    val name: String,
    val types: List<String>,
    val lines: List<JsonDefinedLine> = emptyList(),
    @SerialName("match-all-stations-containing") val stationMatches: List<String> = emptyList(),
    val regex: String? = null,
)

fun getOperatorJsonDefined(
    operators: List<JsonDefinedOperator>,
    trainLine: String,
    origin: String,
    destination: String,
): String? {
    operators.filter { operator ->
        operator.types.any {
            return@any when {
                operator.regex != null -> Regex(operator.regex).matches(trainLine)
                trainLine.contains(' ') -> trainLine.split(' ').first() == it
                else -> trainLine.startsWith(it)
            }
        }
    }.forEach { operator ->
        when {
            operator.stationMatches.isNotEmpty() -> {
                if (operator.stationMatches.any { origin.contains(it) || destination.contains(it) }) {
                    return operator.name
                }
            }

            operator.lines.isNotEmpty() -> {
                val target = JsonDefinedLine(trainLine, origin, destination)

                for (line in operator.lines) {
                    when {
                        line.line != trainLine -> continue
                        line.from == null && line.to == null -> continue
                        line == target || JsonDefinedLine(
                            line.line, from = line.to, to = line.from
                        ) == target -> return operator.name

                        line.from == null && (line.to == origin || line.to == destination) -> return operator.name
                        line.to == null && (line.from == origin || line.from == destination) -> return operator.name
                    }
                }
            }

            else -> return operator.name
        }
    }

    return null
}

fun loadTraewellingData(paths: Set<Path>): TraewellingJson? {
    if (paths.isEmpty()) return null

    // caller ensures all provided paths resolve to existing files

    var oldestMeta: Meta? = null
    var newestMeta: Meta? = null
    val entries = mutableSetOf<DataEntry>()
    val json = Json { ignoreUnknownKeys = true }

    for (path in paths) {
        val data = try {
            json.decodeFromStream<TraewellingJson>(path.inputStream())
        } catch (e: SerializationException) {
            eprintln("error deserializing data, skipping: ${e.message}")
            continue
        } catch (e: IOException) {
            eprintln("error reading file, skipping: ${e.message}")
            continue
        } catch (e: Throwable) {
            eprintln("unknown error, skipping: ${e.message}")
            continue
        }

        val meta = data.meta
        if (oldestMeta == null) oldestMeta = meta
        if (newestMeta == null) newestMeta = meta

        val from = toUnixTime(meta.from)
        if (from > toUnixTime(newestMeta.from)) newestMeta = meta
        if (from < toUnixTime(oldestMeta.from)) oldestMeta = meta

        data.entries.forEach { entries += it }
    }

    requireNotNull(oldestMeta)
    requireNotNull(newestMeta)

    return TraewellingJson(
        meta = Meta(
            user = newestMeta.user, from = oldestMeta.from, to = newestMeta.to, exportedAt = newestMeta.exportedAt
        ), entries = entries.toTypedArray()
    )
}

abstract class StatisticStep(internal val term: Terminal) {
    abstract fun exec(data: TraewellingJson)
}

class PrintMetadata(term: Terminal) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        with(data.meta) {
            term.println("\n${from.dropLast(6).replace('T', ' ')} - ${to.dropLast(6).replace('T', ' ')}\n")
            term.println("${bold("Distance travelled: ")}${user.trainDistance / 1000} km")
            term.println("${bold("Duration: ")}${user.trainDuration / 60} h")
            term.println("${bold("Points: ")}${user.points} (total: ${data.entries.sumOf { it.status.train.points }})")
        }
    }
}

class PrintCheckIns(term: Terminal) : StatisticStep(term) {
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

            term.println("${train.lineName} ${gray("${train.origin.name} -> ${train.destination.name}")}")

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

class PrintEvents(term: Terminal) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        term.println(bold(underline("Events")))

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

class PrintModes(term: Terminal, private val atMost: Int) : StatisticStep(term) {
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
            term.println(bold(underline("Modes ($title)")))
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

class PrintLines(term: Terminal, private val atMost: Int) : StatisticStep(term) {
    override fun exec(data: TraewellingJson) {
        (if (atMost == Int.MAX_VALUE) "all time" else "top $atMost").let { title ->
            term.println(bold(underline("Lines ($title)")))
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

class PrintOperators(term: Terminal, private val atMost: Int) : StatisticStep(term) {
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

class PrintModeStats(term: Terminal) : StatisticStep(term) {
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
                "${red(name)}: ${bold(it.checkins.toString())} check-ins, ${bold(it.distance.toString())} km, ${
                    bold(
                        it.duration.toString()
                    )
                } hours, ${bold((it.distance / it.duration).toString())} km/h"
            )
        }

        printModeStats("Fernverkehr") {
            it.split(' ').firstOrNull() in LONG_DISTANCE_PREFIXES
        }
        printModeStats("Regional   ") {
            if (it.contains(' '))
                if (it.split(' ').firstOrNull() in REGIONAL_PREFIXES) return@printModeStats true
            else
                REGIONAL_PREFIXES.forEach { prefix ->
                    if (it.startsWith(prefix))
                        return@printModeStats true
                }
            false
        }
        printModeStats("S-Bahn     ") { it.startsWith("S") && !it.startsWith("ST") }
        printModeStats("U-Bahn     ") { it.startsWith("U") }
        printModeStats("Tram       ") { it.startsWith("ST") }
        printModeStats("Bus        ") { it.startsWith("Bus") }
    }
}

class PrintSeenStations(term: Terminal) : StatisticStep(term) {
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

class PrintAllLines(term: Terminal, private val operatorJsonPath: Path) : StatisticStep(term) {
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

            val s = "${it.trip.lineName} ${gray("from $originStation to $destinationStation")} $evu"
            if (s !in seen) {
                term.println(s)
                seen += s
            }
        }
    }
}

data class ParsedArguments(
    var paths: Set<Path>,
    var topLimit: Int,
    var ansiLevel: AnsiLevel,
    var useMetadataStep: Boolean,
    var useEventsStep: Boolean,
    var useModesStep: Boolean,
    var useLinesStep: Boolean,
    var useOperatorsStep: Boolean,
    var useModeStatsStep: Boolean,
    var useSeenStationsStep: Boolean,
    var useAllLinesStep: Boolean,
    var useAllCheckinsStep: Boolean,
)

fun printHelp() {
    val help = """
        Usage: travelstats [options] [files...]
        
        Input files are JSON-formatted exports from Träwelling. At least one is required,
        but merging multiple files into one dataset is supported. Make sure their date
        ranges do not overlap.
        Get yours here: https://traewelling.de/export

        Enable options by passing them as separate arguments. Options can only be toggled
        on; the argument parser is extremely bare-bones, key-value syntax is not implemented.
        By default, all options are off. Everything that is not identified as a supported
        option will be treated as an input file.
        Refer to the list below to find ones you might be interested in.

        Options:
            --top-limit [int]: how many rows of values to show. 
                               Only affects "modes", "lines" and "operators".
                               Default: MAX_INT (${Int.MAX_VALUE})

            --ansi-level [string]: the level of color support to use.
                                   Valid options: ansi16, ansi256, truecolor
                                   Default: truecolor   

            --metadata: print basic statistics from the `meta` field.
                        time range, total distance and duration, current and total points.

            --checkins: print all checkins.
                        line name, origin and destination stations, (optional) event,
                        (optional) body text, departure and arrival times, delays,
                        distance in km, duration in minutes, speed in km/h.

            --events: print general event data as a table.
            
            --modes: print general mode data as a table.

            --mode-stats: more detailed statistics about general transit modes.
                          categories: Fernverkehr, Regional, S-Bahn, U-Bahn, Tram, Bus
                          
                          Fernverkehr: $LONG_DISTANCE_PREFIXES
                          Regional: $REGIONAL_PREFIXES
                          
                          number of check-ins, total distance (km), total duration (h),
                          average speed (km/h)

            --lines: print general line data as a table.

            --all-lines: print all unique lines and their respective operators.
                         Träwelling usually provides operator data in the export,
                         but often fails at local operators; in which case, this program
                         *guesses* which one it might be based on rules defined in
                         the `operators.json` file. The ruleset is far from complete,
                         but does a decent job of getting closer to 100% coverage.

            --operators: print all operators as a table.
                         Uses same operator guessing algorithm as described above.

            --seen-stations: prints all the stations you have seen (visited) as a table.
                             counts both origin and destination stations.

            --help, -h: prints this message and exits with code 0.
            
        This program works best on a terminal with truecolor support.
    """.trimIndent()

    println(help)
}

fun parseArguments(args: Array<String>): ParsedArguments {
    val parsedArguments = ParsedArguments(
        paths = emptySet(),
        topLimit = Int.MAX_VALUE,
        ansiLevel = AnsiLevel.TRUECOLOR,
        useMetadataStep = false,
        useEventsStep = false,
        useModesStep = false,
        useLinesStep = false,
        useOperatorsStep = false,
        useModeStatsStep = false,
        useSeenStationsStep = false,
        useAllLinesStep = false,
        useAllCheckinsStep = false,
    )

    val paths = mutableSetOf<Path>()
    // Set when we need to read 1 token ahead and prevent the default branch from complaing
    var skipNext = false

    args.forEachIndexed { index, arg ->
        when (arg) {
            "--top-limit" -> {
                val default = Int.MAX_VALUE
                skipNext = true

                val limit = args.getOrElse(index + 1) {
                    eprintln("error: '--top-limit' passed without specifying a limit, expected an integer")

                    skipNext = false
                    default.toString()
                }

                parsedArguments.topLimit = limit.toIntOrNull() ?: default
            }

            "--ansi-level" -> {
                skipNext = true
                val level = args.getOrElse(index + 1) {
                    eprintln("error: '--ansi-level' passed without specifying a level, must be one of 'ansi16', 'ansi256' or 'truecolor'")

                    skipNext = false
                    "truecolor"
                }

                parsedArguments.ansiLevel = when (level) {
                    "ansi16" -> AnsiLevel.ANSI16
                    "ansi256" -> AnsiLevel.ANSI256
                    "truecolor" -> AnsiLevel.TRUECOLOR
                    else -> AnsiLevel.NONE
                }
            }

            "--metadata" -> parsedArguments.useMetadataStep = true
            "--events" -> parsedArguments.useEventsStep = true
            "--modes" -> parsedArguments.useModesStep = true
            "--lines" -> parsedArguments.useLinesStep = true
            "--operators" -> parsedArguments.useOperatorsStep = true
            "--mode-stats" -> parsedArguments.useModeStatsStep = true
            "--seen-stations" -> parsedArguments.useSeenStationsStep = true
            "--all-lines" -> parsedArguments.useAllLinesStep = true
            "--checkins" -> parsedArguments.useAllCheckinsStep = true
            "--help", "-h" -> {
                printHelp()
                exitProcess(0)
            }

            else -> {
                if (skipNext) {
                    skipNext = false
                    return@forEachIndexed
                }

                val path = Path.of(arg)
                if (path.exists()) {
                    paths.add(path)
                } else {
                    eprintln("error: file does not exist: '$path'")
                }
            }
        }
    }

    parsedArguments.paths = paths
    return parsedArguments
}

fun main(args: Array<String>) {
    val arguments = parseArguments(args)

    val term = Terminal(tabWidth = 4, width = Int.MAX_VALUE, interactive = false, ansiLevel = arguments.ansiLevel)
    if (Path.of("operators.json").notExists()) {
        term.println(brightYellow("warning: 'operators.json' file not found. Make sure it is located next to the launching script/binary. Steps using operator data may shit themselves."))
        term.println(brightYellow("Continuing anyway."))
    }
    val data = loadTraewellingData(arguments.paths) ?: exitError(1, "error: no input")

    val steps = mutableSetOf<StatisticStep>()
    if (arguments.useMetadataStep) steps.add(PrintMetadata(term))
    if (arguments.useEventsStep) steps.add(PrintEvents(term))
    if (arguments.useModesStep) steps.add(PrintModes(term, atMost = arguments.topLimit))
    if (arguments.useLinesStep) steps.add(PrintLines(term, atMost = arguments.topLimit))
    if (arguments.useOperatorsStep) steps.add(PrintOperators(term, atMost = arguments.topLimit))
    if (arguments.useModeStatsStep) steps.add(PrintModeStats(term))
    if (arguments.useSeenStationsStep) steps.add(PrintSeenStations(term))
    if (arguments.useAllLinesStep) steps.add(PrintAllLines(term, operatorJsonPath = Path.of("operators.json")))
    if (arguments.useAllCheckinsStep) steps.add(PrintCheckIns(term))

    if (steps.isEmpty()) {
        eprintln("error: empty step set. refer to the help page using the `--help` argument and add some!")
    }

    steps.forEach { step ->
        step.exec(data)
    }
}