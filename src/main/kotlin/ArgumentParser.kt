import com.github.ajalt.mordant.rendering.AnsiLevel
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.system.exitProcess

class ArgumentParser(private val args: Array<String>) {
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

    private fun printHelp() {
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

    fun parseArguments(): ParsedArguments {
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

}