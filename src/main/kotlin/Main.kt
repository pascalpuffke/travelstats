@file:Suppress("SpellCheckingInspection") @file:OptIn(ExperimentalSerializationApi::class)

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import steps.*
import traewelling.DataEntry
import traewelling.Meta
import traewelling.TraewellingJson
import traewelling.TrainOriginOrDestination
import java.io.IOException
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.system.exitProcess

const val TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssxxxxx"
const val ON_TIME_RANGE_SECONDS = 60

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

fun <K, V : Comparable<V>> Map<K, V>.toDescendingSortedList() = this.toList().sortedByDescending { it.second }

fun <K> MutableMap<K, Int>.incrementValue(key: K, default: Int = 0) = this.set(key, this.getOrDefault(key, default) + 1)

fun toZonedDateTime(date: String, pattern: String = TIME_FORMAT): ZonedDateTime =
    ZonedDateTime.parse(date, DateTimeFormatter.ofPattern(pattern))

fun toUnixTime(date: String) = toZonedDateTime(date).toEpochSecond()

fun delay(originOrDestination: TrainOriginOrDestination): Pair<TextColors, String> {
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

fun eprintln(message: Any?) = System.err.println(message)

fun exitError(code: Int, message: String): Nothing {
    eprintln(message)
    exitProcess(code)
}

// For some reason Kotlin style format strings do not support float precision, so we have to do it the Java (or C) way.
fun percentageString(n: Int, total: Int, precision: Int = 2) =
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

fun main(args: Array<String>) {
    val arguments = ArgumentParser(args).parseArguments()

    val term = Terminal(tabWidth = 4, width = Int.MAX_VALUE, interactive = false, ansiLevel = arguments.ansiLevel)
    if (Path.of("operators.json").notExists()) {
        term.println(brightYellow("warning: 'operators.json' file not found. Make sure it is located next to the launching script/binary. Steps using operator data may shit themselves."))
        term.println(brightYellow("Continuing anyway."))
    }
    val data = loadTraewellingData(arguments.paths) ?: exitError(1, "error: no input")

    val steps = mutableSetOf<StatisticStep>()
    if (arguments.useMetadataStep) steps.add(Metadata(term))
    if (arguments.useEventsStep) steps.add(Events(term))
    if (arguments.useModesStep) steps.add(Modes(term, atMost = arguments.topLimit))
    if (arguments.useLinesStep) steps.add(Lines(term, atMost = arguments.topLimit))
    if (arguments.useOperatorsStep) steps.add(Operators(term, atMost = arguments.topLimit))
    if (arguments.useModeStatsStep) steps.add(ModeStats(term))
    if (arguments.useSeenStationsStep) steps.add(SeenStations(term))
    if (arguments.useAllLinesStep) steps.add(AllLines(term, operatorJsonPath = Path.of("operators.json")))
    if (arguments.useAllCheckinsStep) steps.add(CheckIns(term))

    if (steps.isEmpty()) {
        exitError(2, "error: empty step set. refer to the help page using the `--help` argument and add some!")
    }

    steps.forEach { step ->
        step.exec(data)
    }
}