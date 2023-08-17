@file:Suppress("SpellCheckingInspection")

import Operator.Companion.Information
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.table.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import traewelling.*
import java.io.IOException
import java.nio.file.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.inputStream
import kotlin.math.roundToInt
import kotlin.system.*

private const val TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssxxxxx"
private const val TOP_LIMIT = Int.MAX_VALUE
private const val ON_TIME_RANGE_SECONDS = 60

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

interface Operator {
    fun matches(name: String, origin: String, destination: String): Boolean
    fun name(): String

    companion object {
        fun matchesTypes(name: String, vararg types: String): Boolean {
            val split = name.split(' ')
            if (split.isEmpty()) return false
            return split.first() in types
        }

        data class Information(val name: String, val origin: String?, val destination: String?)

        fun matchesAny(info: Information, valid: Set<Information>): Boolean {
            val otherWay = Information(info.name, info.destination, info.origin)
            return valid.any {
                it.name == info.name && (it.origin == info.origin || it.destination == info.destination)
            } || valid.any {
                it.name == otherWay.name && (it.origin == otherWay.origin || it.destination == otherWay.destination)
            }
        }
    }
}

class TLX : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "TLX", "TL")

    override fun name() = "trilex - Die Länderbahn"
}

class ALX : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "ALX")

    override fun name() = "alex - Die Länderbahn"
}

class OPX : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "OPX")

    override fun name() = "Oberpfalzbahn - Die Länderbahn"
}

class ABRM : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (Operator.matchesTypes(name, "HBX")) return true

        if (!Operator.matchesTypes(name, "RE", "RB", "S")) return false

        // https://www.abellio.de/verkehr-aktuell
        val valid = setOf(
            Information("S 7", "Halle(Saale)Hbf", "Lutherstadt Eisleben"),
            Information("RB 20", null, "Leipzig Hbf"),
            Information("RB 20", "Eisenach", "Halle(Saale)Hbf"),
            Information("RB 25", "Halle(Saale)Hbf", "Saalfeld(Saale)"),
            Information("RB 35", "Stendal Hbf", "Wolfsburg Hbf"),
            Information("RB 36", "Magdeburg Hbf", "Wolfsburg Hbf"),
            Information("RB 59", "Erfurt Hbf", "Sangerhausen"),
            Information("RE 4", "Halle(Saale)Hbf", "Goslar"),
            Information("RE 6", "Magdeburg Hbf", "Wolfsburg Hbf"),
            Information("RE 9", "Halle(Saale)Hbf", "Kassel-Wilhelmshöhe"),
            Information("RE 10", "Magdeburg Hbf", "Erfurt Hbf"),
            Information("RE 11", "Magdeburg Hbf", "Thale Hbf"),
            Information("RE 11", "Halberstadt", "Thale Hbf"),
            Information("RE 16", "Halle(Saale)Hbf", null),
            Information("RE 17", "Erfurt Hbf", "Naumburg(Saale)Hbf"),
            Information("RE 21", null, "Goslar"),
            Information("RE 31", null, "Blankenburg(Harz)"),
        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "Abellio Rail Mitteldeutschland"
}

class DBRegioAGSuedost : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "RE", "RB", "S")) return false

        if (name.startsWith("S")) {
            fun anyContains(s: String) = origin.contains(s) || destination.contains(s)
            // S1, S2, S6, S10
            if (anyContains("Leipzig")) return true
            // S3, S5, S5X, S8, S9, S47
            if (anyContains("Halle") && name != "S 7" /* ABRM */) return true
            // S1, S2, S8
            if (anyContains("Dresden") || anyContains("Bad Schandau")) return true

            if (anyContains("Geithain")) return true
        }

        val valid = setOf(
            Information("RE 1", "Göttingen", "Glauchau(Sachs)"),
            Information("RE 2", "Kassel-Wilhelmshöhe", "Erfurt Hbf"),
            Information("RE 3", "Erfurt Hbf", null),
            Information("RE 7", "Erfurt Hbf", "Würzburg Hbf"),
            Information("RE 13", "Magdeburg Hbf", "Leipzig Hbf"),
            Information("RE 14", "Magdeburg Hbf", "Falkenberg(Elster)"),
            Information("RE 18", "Halle(Saale)Hbf", "Jena-Göschwitz"),
            Information("RE 19", "Dresden Hbf", "Kurort Altenberg(Erzgebirge)"),
            Information("RE 20", "Dresden Hbf", "Schöna(Gr)"),
            Information("RE 20", null, "Uelzen"),
            Information("RE 30", "Magdeburg Hbf", "Halle(Saale)Hbf"),
            Information("RE 50", "Leipzig Hbf", "Dresden Hbf"),
            Information("RE 55", "Nordhausen", "Erfurt Hbf"),
            Information("RE 56", "Nordhausen", "Erfurt Hbf"),
            Information("RE 57", "Bad Kissingen", "Würzburg Hbf"),
            Information("RB U 28", "Schöna", "Sebnitz(Sachs)"),
            Information("RB 32", "Stendal Hbf", "Salzwedel"),
            Information("RB 33", "Dresden Hbf", "Königsbrück"),
            Information("RB 34", "Dresden Hbf", "Senftenberg"),
            Information("RB 40", "Braunschweig Hbf", null),
            Information("RB 51", "Dessau Hbf", "Falkenberg(Elster)"),
            Information("RB 51", "Lutherstadt Wittenberg Hbf", "Falkenberg(Elster)"),
            Information("RB 52", "Leinefelde", "Erfurt Hbf"),
            Information("RB 53", "Bad Langensalza", "Gotha"),
            Information("RB 71", "Sebnitz(Sachs)", "Pirna"),
            Information("RB 72", "Kurort Altenberg(Erzgebirge)", "Heidenau"),
            Information("RB 76", "Weißenfels", "Zeitz"),
            Information("RB 78", "Querfurt", "Merseburg Hbf"),
            Information("RB 113", "Leipzig Hbf", "Geithain"),
            Information("S 1", "Meißen Triebischtal", "Schöna"),
            Information("S 1", "Schönebeck-Bad Salzelmen", "Wittenberge"),
            Information("S 4", "Markkleeberg-Gaschwitz", null),
        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "DB Regio AG Südost"
}

class DBRegioAGNord : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "RE", "RB", "S")) return false

        val valid = setOf(
            Information("RE 6", "Westerland(Sylt)", null),
            Information("RE 7", "Kiel Hbf", null),
            Information("RE 7", "Flensburg", null),
            Information("RE 8", "Lübeck Hbf", null),
            Information("RE 70", "Kiel Hbf", null),
            Information("RE 80", "Lübeck Hbf", null),
            Information("RB 85", "Lübeck Hbf", null),
            Information("RE 86", "Lübeck-Travemünde Strand", null),
            Information("RB 86", "Lübeck-Travemünde Strand", null),
        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "DB Regio AG Nord"
}

// ODEG
// Operator.Companion.Information("RE 2", "Berlin-Charlottenburg", "Wismar"),

class DBRegioAGNRW : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "RE", "RB", "S")) return false

        val valid = setOf(
            Information("RE 9", "Aachen Hbf", null),
            Information("RB 20", "Stolberg (Rheinl) Hbf", null),
            Information("RB 20", "Stolberg(Rheinl)Hbf", null),
            Information("RB 24", "Kall", null),
            Information("RB 25", "Overath", null),
            Information("RB 27", "Koblenz Hbf", null),
            Information("RB 33", "Aachen Hbf", null),
        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "DB Regio AG NRW"
}

class DBRegioAGNordost : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (name.startsWith("FEX")) return true

        if (!Operator.matchesTypes(name, "RE", "RB", "S")) return false

        if (name.startsWith("S") && origin == "Warnemünde" || destination == "Warnemünde") return true

        val valid = setOf(
            Information("RE 1", "Hamburg Hbf", "Rostock Hbf"),
            Information("RE 2", "Nauen", "Cottbus Hbf"),
            Information("RE 2", "Berlin Ostbahnhof", "Cottbus Hbf"),
            Information("RE 3", "Schwedt(Oder)", "Lutherstadt Wittenberg Hbf"),
            Information("RE 3", "Stralsund Hbf", null),
            Information("RE 3", "Eberswalde Hbf", "Halle(Saale)Hbf"),
            Information("RE 4", "Lübeck Hbf", "Grambow"),
            Information("RE 4", "Pasewalk", "Ueckermünde Stadthafen"),
            Information("RE 4", "Neubrandenburg", "Lübeck Hbf"),
            Information("RE 4", "Elstal", "Jüterbog"),
            Information("RE 4", "Stendal Hbf", "Falkenberg(Elster)"),
            Information("RE 5", null, "Berlin Südkreuz"),
            Information("RE 6", "Wittenberge", "Berlin-Charlottenburg"),
            Information("RE 7", "Dessau Hbf", null),
            Information("RE 7", "Senftenberg", "Königs Wusterhausen"),
            Information("RE 7", "Stralsund Hbf", "Greifswald"),
            Information("RE 10", "Leipzig Hbf", null),
            Information("RB 10", "Nauen", "Berlin Südkreuz"),
            Information("RB 11", "Wismar", "Tessin"),
            Information("RE 11", "Leipzig Hbf", "Hoyerswerda"),
            Information("RB 12", "Bad Doberan", "Ostseeheilbad Graal-Müritz"),
            Information("RB 12", "Rostock Hbf", "Ribnitz-Damgarten West"),
            Information("RE 13", "Cottbus Hbf", "Elsterwerda"),
            Information("RB 14", "Nauen", "Berlin Südkreuz"),
            Information("RE 15", "Hoyerswerda", "Dresden Hbf"),
            Information("RB 17", null, "Ludwigslust"),
            Information("RB 18", "Bad Kleinen", "Schwerin Hbf"),
            Information("RE 18", "Cottbus Hbf", "Dresden-Neustadt"),
            Information("RB 20", "Oranienburg", "Potsdam Griebnitzsee"),
            Information("RB 21", "Potsdam Hbf", "Berlin Gesundbrunnen"),
            Information("RB 22", "Potsdam Griebnitzsee", "Königs Wusterhausen"),
            Information("RB 23", "Golm", "Flughafen BER - Terminal 1-2"),
            Information("RB 23", "Züssow", "Swinoujscie Centrum"),
            Information("RB 24 Nord", "Eberswalde Hbf", "Flughafen BER - Terminal 5 (Schönefeld)"),
            Information("RB 24 Süd", "Flughafen BER - Terminal 1-2", "Wünsdorf-Waldstadt"),
            Information("RB 24", "Zinnowitz", "Peenemünde"),
            Information("RB 25", "Barth", "Velgast"),
            Information("RB 31", "Elsterwerda-Biehla", "Dresden Hbf"),
            Information("RB 32 Süd", "Elsterwerda-Biehla", "Dresden Hbf"),
            Information("RB 32 Nord", "Oranienburg", "Flughafen BER - Terminal 5 (Schönefeld)"),
            Information("RB 43", "Falkenberg(Elster)", "Frankfurt(Oder)"),
            Information("RB 49", "Cottbus Hbf", "Falkenberg(Elster)"),
            Information("RB 55", "Kremmen", "Henningsdorf(Berlin)"),
            Information("RB 66", "Angermünde", "Tantow"),
            Information("RE 66", "Berlin Gesundbrunnen", "Tantow"),
            Information("RB 92", "Cottbus Hbf", "Guben"),
        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "DB Regio AG Nordost"
}

class DBRegioMitte : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "RE", "RB")) return false

        val valid = setOf(
            Information("RE 1", "Koblenz Hbf", "Mannheim Hbf"),
            Information("RE 2", "Koblenz Hbf", "Frankfurt(Main)Hbf"),
            Information("RE 4", "Karlsruhe Hbf", "Frankfurt(Main)Hbf"),
            Information("RE 6", "Karlsruhe Hbf", "Kaiserslautern Hbf"),
            Information("RE 9", "Karlsruhe Hbf", "Mannheim Hbf"),
            Information("RE 14", "Frankfurt(Main)Hbf", "Mannheim Hbf"),
            Information("RE 20", "Frankfurt(Main)Hbf", "Limburg(Lahn)"),
            Information("RB 22", "Frankfurt(Main)Hbf", "Limburg(Lahn)"),
            Information("RB 23", "Mayen Ost", "Limburg(Lahn)"),
            Information("RE 25", "Gießen", "Koblenz Hbf"),
            Information("RE 30", "Kassel Hbf", "Frankfurt(Main)Hbf"),
            Information("RE 34", "Glauburg-Stockheim", "Frankfurt(Main)Hbf"),
            Information("RB 35", "Bingen(Rhein) Stadt", "Worms Hbf"),
            Information("RB 38", "Andernach", "Kaisersesch"),
            Information("RB 40", "Dillenburg", "Frankfurt(Main)Hbf"),
            Information("RE 40", "Mannheim Hbf", "Freudenstadt"),
        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "DB Regio Mitte"
}

class DBFernverkehrAG : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        // TODO: How do we correctly determine which IC services are operated by DB Fernverkehr?
        //       Hard-coding a set of train numbers seems incredibly dumb and becomes out of date immediately.
        //       Also note that some German IC services may use the "RE" prefix, too.
        //       If during the check-in you have the choice of either taking the "IC" or "RE", obviously use IC.
        //       This is immensely stupid, go thank Deutsche Bahn and federal states for this mess.
        return Operator.matchesTypes(name, "ICE", "IC")
        // TODO: THIS ASSIGNS DB FERNVERKEHR FOR EVERY IC SERVICE, WHICH IS WRONG.
    }

    override fun name() = "DB Fernverkehr AG"
}

class NBE : Operator {
    override fun matches(name: String, origin: String, destination: String) = name.startsWith("NBE")

    override fun name() = "Nordbahn Eisenbahngesellschaft"
}

class DWE : Operator {
    override fun matches(name: String, origin: String, destination: String) = name.length == 8 && name.startsWith("DWE")

    override fun name() = "Dessau-Wörlitzer Eisenbahn"
}

class FlixTrain : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "FLX")

    override fun name() = "FlixTrain"
}

class Erixx : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "erx")

    override fun name() = "erixx"
}

class Metronom : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "ME")

    override fun name() = "metronom"
}

class KD : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "KD")

    override fun name() = "Koleje Dolnośląskie"
}

class ODEG : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "RE", "RB")) return false

        val valid = setOf(
            Information("RE 1", "Magdeburg Hbf", null),
            Information("RE 1", "Frankfurt(Oder)", null),
            Information("RE 2", "Berlin-Charlottenburg", "Wismar"),
            Information("RB 33", "Jüterbog", "Potsdam Hbf"),
            Information("RB 64", "Görlitz", "Hoyerswerda"),
            Information("RE 8", "Wismar", null),
            Information("RB 65", "Cottbus Hbf", "Zittau")

        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "Ostdeutsche Eisenbahn GmbH"
}

class EB : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "RE", "RB")) return false

        val valid = setOf(
            Information("RE 12", "Leipzig Hbf", null),
            Information("RE 50", "Erfurt Hbf", null),
            Information("RB 22", "Leipzig Hbf", null),
            Information("RB 23", "Erfurt Hbf", null),
            Information("RB 13", "Leipzig Hbf", null),
            Information("RB 13", "Gera Hbf", null),
        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "Erfurter Bahn"
}

class SWEG : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        // TODO !!!!!!!!!!
        return name.startsWith("MEX")
    }

    override fun name() = "SWEG Bahn Stuttgart GmbH"
}

class BRB : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "BRB")

    override fun name() = "Bayrische Regiobahn"
}

class MRB : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "RE", "RB")) return false

        val valid = setOf(
            Information("RB 30", "Dresden Hbf", "Zwickau(Sachs)Hbf"),
            Information("RB 45", "Chemnitz Hbf", "Elsterwerda"),
            Information("RB 110", "Leipzig Hbf", "Döbeln"),
            Information("RE 3", "Dresden Hbf", "Hof Hbf"),
            Information("RE 6", "Leipzig Hbf", "Chemnitz Hbf"),
        )

        val info = Information(name, origin, destination)
        return Operator.matchesAny(info, valid)
    }

    override fun name() = "Mitteldeutsche Regiobahn"
}

// I only wanted to focus on German rail companies, but I have some TGVs in my logs.
class SNCF : Operator {
    // There's surely more to consider here. (TER?)
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "TGV")

    override fun name() = "SNCF"
}

class OEBB : Operator {
    // There's surely more to consider here. (CityJets? RegionalEXpress?)
    override fun matches(name: String, origin: String, destination: String) =
        Operator.matchesTypes(name, "RJ", "RJX", "NJ")

    override fun name() = "ÖBB"
}

class GVB : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Amsterdam") || !destination.contains("Amsterdam")) return false

        // NOTE: Trams, buses and ferries are not considered here.
        //       These are not contained in the API Träwelling uses, and thus don't show up in exports.
        return Operator.matchesTypes(name, "U")
    }

    override fun name() = "Gemeente Vervoerbedrijf Amsterdam"
}

class BVG : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Berlin") || !destination.contains("Berlin")) return false

        return Operator.matchesTypes(name, "STR", "U", "Bus")
    }

    override fun name() = "Berliner Verkehrsbetriebe"
}

class BSAG : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Bremen") || !destination.contains("Bremen")) return false

        if (!Operator.matchesTypes(name, "STR", "Bus")) return false

        // Bus 1
        val parts = name.split(" ") // [Bus, 1]
        if (parts.size != 2) return false
        val line = parts.last() // [1]
        if (!line.all { Character.isDigit(it) }) return false
        val lineNumber = line.toInt()

        return lineNumber <= 100
    }

    override fun name() = "Bremer Straßenbahn AG"
}


class LVB : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "STR", "Bus")) return false

        fun anyContains(s: String) = origin.contains(s) || destination.contains(s)
        if (anyContains("Leipzig")) return true
        if (anyContains("Schkeuditz")) return true

        return false
    }

    override fun name() = "Leipziger Verkehrsbetriebe"
}

class WFB : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "WFB")

    override fun name() = "Westfalenbahn"
}

class NWB : Operator {
    override fun matches(name: String, origin: String, destination: String) = Operator.matchesTypes(name, "NWB")

    override fun name() = "Nordwestbahn"
}

class DVB : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (Operator.matchesTypes(name, "BusEV")) return true
        if (!Operator.matchesTypes(name, "STR", "Bus")) return false

        fun anyContains(s: String) = origin.contains(s) || destination.contains(s)

        if (Operator.matchesTypes(name, "STR")) {
            if (anyContains("Radebeul")) return true
            if (anyContains("Dresden")) return true
        }

        if (!anyContains("Dresden")) return false

        val line = name.split(' ').last().toIntOrNull()
        return (line ?: 100) < 100
    }

    override fun name() = "Dresdner Verkehrsbetriebe"
}

class VGH : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {

        fun anyContains(s: String) = origin.contains(s) || destination.contains(s)

        if (!Operator.matchesTypes(name, "Bus")) return false

        if (!anyContains("Hoyerswerda")) return false

        // Bus 1
        val parts = name.split(" ") // [Bus, 1]
        if (parts.size != 2) return false
        val line = parts.last() // [1]
        if (!line.all { Character.isDigit(it) }) return false
        val lineNumber = line.toInt()

        return lineNumber <= 5
    }

    override fun name() = "Verkehrsgesellschaft Hoyerswerda mbH"
}

class SBahnBerlin : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "S")) return false
        if (origin.contains("Berlin") || destination.contains("Berlin")) return true
        if (origin.contains("Bernau") || destination.contains("Bernau")) return true
        if (origin.contains("Potsdam") || destination.contains("Potsdam")) return true

        return false
    }

    override fun name() = "S-Bahn Berlin"
}

class SBahnHamburg : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "S")) return false
        if (origin.contains("Hamburg") || destination.contains("Hamburg")) return true
        if (origin.contains("Stade") || destination.contains("Stade")) return true

        return false
    }

    override fun name() = "S-Bahn Hamburg"
}

class SBahnMuenchen : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "S")) return false
        if (origin.contains("München") || destination.contains("München")) return true
        if (origin.contains("Tutzing") || destination.contains("Tutzing")) return true

        return false
    }

    override fun name() = "S-Bahn München"
}

class SBahnStuttgart : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!Operator.matchesTypes(name, "S")) return false

        return origin.contains("Stuttgart") || destination.contains("Stuttgart")
    }

    override fun name() = "S-Bahn Stuttgart"
}

class RVSOE : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        // TODO: RVSOE buses don't just start/end in Dresden.
        val scuffed = origin + destination
        if (!scuffed.contains("Dresden")) return false

        if (!Operator.matchesTypes(name, "Bus")) return false
        val line = name.split(' ').last().toIntOrNull()
        // RVSOE buses usually have numbers >100 and are not operated by DVB, except for the line 166, interestingly enough...
        return (line ?: 0) >= 100
    }

    override fun name() = "Regionalverkehr Sächsische Schweiz-Osterzgebirge GmbH"
}

class HAVAG : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Halle") && !destination.contains("Halle")) return false

        if (!Operator.matchesTypes(name, "STR", "Bus")) return false
        val parts = name.split(' ')
        val last = parts.last()
        if (last == "E") return true
        require(last.chars().allMatch { Character.isDigit(it) })
        val line = last.toInt()
        // OBS buses usually have numbers >300 and are not operated by HAVAG
        return line < 300
    }

    override fun name() = "Hallesche Verkehrs-AG"
}

class ViP : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Potsdam") && !destination.contains("Potsdam")) return false

        return Operator.matchesTypes(name, "STR", "Bus")
    }

    override fun name() = "ViP Verkehrsbetrieb Potsdam GmbH"
}

class DVV : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Dessau") && !destination.contains("Dessau")) return false

        return Operator.matchesTypes(name, "STR", "Bus")
    }

    override fun name() = "Dessauer Verkehrs-GmbH"
}

class VVS : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        val scuffed = origin + destination
        if (!scuffed.contains("Stuttgart")) return false

        // STB, not STR... thanks.
        return Operator.matchesTypes(name, "STB", "Bus")
    }

    override fun name() = "Verkehrs- und Tarifverbund Stuttgart GmbH"
}

class VBBR : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Brandenburg an der Havel") && !destination.contains("Brandenburg an der Havel")) return false

        return Operator.matchesTypes(name, "STR", "Bus")
    }

    override fun name() = "Verkehrsbetriebe Brandenburg an der Havel GmbH"
}

class NaumburgerStrassenbahn : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Naumburg") && !destination.contains("Naumburg")) return false

        return Operator.matchesTypes(name, "STR")
    }

    override fun name() = "Naumburger Straßenbahn GmbH"
}

class GVBGera : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Gera") && !destination.contains("Gera")) return false

        return Operator.matchesTypes(name, "STR", "Bus")
    }

    override fun name() = "Verkehrs- und Betriebsgesellschaft Gera"
}

class WVV : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Würzburg") && !destination.contains("Würzburg")) return false

        return Operator.matchesTypes(name, "STR", "Bus")
    }

    override fun name() = "Würzburger Versorgungs- und Verkehrs-GmbH"
}

class EVAG : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        if (!origin.contains("Erfurt") && !destination.contains("Erfurt")) return false

        return Operator.matchesTypes(name, "STR", "Bus")
    }

    override fun name() = "Erfurter Verkehrsbetriebe"
}

class CD : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        // TODO lol
        //      Not just naive, but also plain incorrect. But Prague ECs - at least the ones I've seen, - use CD rolling stock. Perhaps not entirely dumb.
        return name.startsWith("EC") && (origin == "Praha hl.n." || destination == "Praha hl.n.")
    }

    override fun name() = "České dráhy"
}

class OBS : Operator {
    override fun matches(name: String, origin: String, destination: String): Boolean {
        // TODO: OBS buses don't have to start/end in Halle. I think.
        if (!origin.contains("Halle") || !destination.contains("Halle")) return false

        if (!Operator.matchesTypes(name, "Bus")) return false
        val line = name.split(' ').last().toIntOrNull()
        // OBS buses usually have numbers >300 and are not operated by HAVAG
        return (line ?: 0) >= 300
    }

    override fun name() = "Omnibusbetrieb Saalekreis"
}

val operators = setOf(
    ABRM(),
    ALX(),
    BRB(),
    BSAG(),
    BVG(),
    CD(),
    DBFernverkehrAG(),
    DBRegioAGNRW(),
    DBRegioAGNord(),
    DBRegioAGNordost(),
    DBRegioAGSuedost(),
    DBRegioMitte(),
    DVB(),
    DVV(),
    DWE(),
    EB(),
    EVAG(),
    Erixx(),
    FlixTrain(),
    GVB(),
    GVBGera(),
    HAVAG(),
    KD(),
    LVB(),
    MRB(),
    Metronom(),
    NBE(),
    NWB(),
    NaumburgerStrassenbahn(),
    OBS(),
    ODEG(),
    OEBB(),
    OPX(),
    RVSOE(),
    SBahnBerlin(),
    SBahnHamburg(),
    SBahnMuenchen(),
    SBahnStuttgart(),
    SNCF(),
    SWEG(),
    TLX(),
    VBBR(),
    VGH(),
    VVS(),
    ViP(),
    WFB(),
    WVV(),
)

fun getOperator(name: String, origin: String, destination: String): String? {
    var result: String? = null

    for (it in operators) {
        if (it.matches(name, origin, destination)) {
            result = it.name()
            break
        }
    }

    return result
}

private fun printEvents(term: Terminal, data: TraewellingJson) {
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
            padding = Padding.of(right = 3)

            events.toDescendingSortedList().forEach { (event, count) ->
                row(event, count, percentageString(count, totalEventCount))
            }
        }
    })
}

private fun printModes(term: Terminal, data: TraewellingJson) {
    fun toFancyModeString(mode: String) = when (mode) {
        "bus" -> "Bus"
        "tram" -> "Tram"
        "suburban" -> "S-Bahn"
        "subway" -> "U-Bahn"
        "regional" -> "Regional (RB, RE, ...)"
        "regionalExp" -> "Fernverkehr (FLX)" // ???????
        "national" -> "Fernverkehr (IC, EC, ...)"
        "nationalExpress" -> "Fernverkehr (ICE, ...)"
        else -> mode.replaceFirstChar { it.uppercaseChar() }
    }

    (if (TOP_LIMIT == Int.MAX_VALUE) "all time" else "top $TOP_LIMIT").let { title ->
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
            padding = Padding.of(right = 3)

            modes.toDescendingSortedList().take(TOP_LIMIT).forEach { (mode, count) ->
                row(mode, count, percentageString(count, totalModesCount))
            }
        }
        if (modes.size > TOP_LIMIT) {
            footer {
                cellBorders = Borders.NONE
                row("... and ${modes.size - TOP_LIMIT} more")
            }
        }
    })
}

private fun printLines(term: Terminal, data: TraewellingJson) {
    data class LineInfo(val line: String, val origin: String, val destination: String)

    (if (TOP_LIMIT == Int.MAX_VALUE) "all time" else "top $TOP_LIMIT").let { title ->
        term.println(bold(underline("Lines ($title)")))
    }

    // val lines = mutableMapOf<LineInfo, Int>()
    val lines = mutableMapOf<String, Int>()
    data.entries.map { it.trip.lineName }.forEach { lines.incrementValue(it) }/*
data.entries.map { it.trip }.map {
LineInfo(
    line = it.lineName.truncate(MAX_STRING_LENGTH),
    origin = it.origin.name.truncate(MAX_STRING_LENGTH),
    destination = it.destination.name.truncate(MAX_STRING_LENGTH),
)
}.forEach { lines.incrementValue(it) }
     */

    val totalLineCount = lines.map { it.value }.sum()
    term.println(table {
        tableBorders = Borders.NONE
        borderType = BorderType.BLANK

        header {
            style = TextStyle(bold = true)
            cellBorders = Borders.NONE

            //row("Line", "Destination", "Count", "%")
            row("Line", "Count", "%")
        }
        body {
            cellBorders = Borders.NONE
            padding = Padding.of(right = 3)

            lines.toDescendingSortedList().take(TOP_LIMIT).forEach { (line, count) ->
                //row(lineInfo.line, lineInfo.destination, count, percentageString(count, totalLineCount))
                row(line, count, percentageString(count, totalLineCount))
            }
        }
        if (lines.size > TOP_LIMIT) {
            footer {
                cellBorders = Borders.NONE
                row("... and ${lines.size - TOP_LIMIT} more")
            }
        }
    })
}

fun printOperators(term: Terminal, data: TraewellingJson) {
    val operators = mutableMapOf<String, Int>()
    data.entries.map { it.trip }.forEach {
        val operator = getOperator(it.lineName, it.origin.name, it.destination.name)
        operators.incrementValue(operator ?: "<unknown operator>")
    }

    val totalLineCount = operators.map { it.value }.sum()
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
            padding = Padding.of(right = 3)

            operators.toDescendingSortedList().take(TOP_LIMIT).forEach { (operator, count) ->
                row(operator, count, percentageString(count, totalLineCount))
            }
        }
        if (operators.size > TOP_LIMIT) {
            footer {
                cellBorders = Borders.NONE
                row("... and ${operators.size - TOP_LIMIT} more")
            }
        }
    })
}

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

@OptIn(ExperimentalSerializationApi::class)
fun getOperatorJsonDefined(trainLine: String, origin: String, destination: String): String? {
    // TODO Stop re-parsing the JSON file on every invocation.
    val json = Json {}
    val operators = json.decodeFromStream<List<JsonDefinedOperator>>(Path.of("operators.json").inputStream())

    operators.filter { operator ->
        operator.types.any {
            when {
                operator.regex != null -> return@any Regex(operator.regex).matches(trainLine)
                trainLine.contains(' ') -> return@any trainLine.split(' ').first() == it
                else -> return@any trainLine.startsWith(it)
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
                if (operator.lines.any { it.line == trainLine && (it.from == null || it.from == origin) && (it.to == null || it.to == destination) }) {
                    return operator.name
                }
            }

            else -> {
                return operator.name
            }
        }
    }

    return null
}

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    val term = Terminal(tabWidth = 4, width = Int.MAX_VALUE, interactive = false)
    if (args.isEmpty()) exitError(1, "error: no input file")

    val path = Path.of(args.first())
    if (Files.notExists(path)) exitError(2, "error: input file does not exist: $path")

    val json = Json { ignoreUnknownKeys = true }
    val preData = try {
        json.decodeFromStream<TraewellingJson>(path.inputStream())
    } catch (e: SerializationException) {
        exitError(3, "error deserializing data: ${e.message}")
    } catch (e: IOException) {
        exitError(3, "error reading file: ${e.message}")
    } catch (e: Throwable) {
        exitError(3, "error: ${e.message}")
    }
    if (preData.entries.isEmpty()) exitError(3, "error: empty data")

    val anotherOne = json.decodeFromStream<TraewellingJson>(
        Path.of("/home/floppa/Documents/luna-07-2023-bis-08-2023.json").inputStream()
    )
    val entries = mutableListOf<DataEntry>()
    preData.entries.forEach { entries.add(it) }
    anotherOne.entries.forEach { entries.add(it) }
    val data = TraewellingJson(
        Meta(
            user = anotherOne.meta.user,
            from = preData.meta.from,
            to = anotherOne.meta.to,
            exportedAt = anotherOne.meta.exportedAt
        ), entries.toTypedArray()
    )

    with(data.meta) {
        term.println("\n${from.dropLast(6).replace('T', ' ')} - ${to.dropLast(6).replace('T', ' ')}\n")
        term.println("${bold("Distance travelled: ")}${user.trainDistance / 1000} km")
        term.println("${bold("Duration: ")}${user.trainDuration / 60} h")
        term.println("${bold("Average speed: ")}${(user.trainSpeed / 1000).roundToInt()} km/h")
        term.println("${bold("Points: ")}${user.points} (total: ${data.entries.sumOf { it.status.train.points }})")
    }

    //printEvents(term, data)
    //printModes(term, data)
    //printLines(term, data)
    printOperators(term, data)

    data.entries.forEach { travel ->
        return@forEach
        val status = travel.status
        val train = status.train
        val distanceString = String.format("%.02f", train.distance / 1000.0)
        val departureDateTime = toZonedDateTime(train.origin.departure)
        val arrivalDateTime = toZonedDateTime(train.destination.arrival)
        val delayAtOrigin = delay(train.origin)
        val delayAtDestination = delay(train.destination)
        val duration = train.duration
        val speed = train.speed.roundToInt()

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
    data.entries.filter { it.trip.category.lowercase() == "bus" }.forEach {
        println("${it.trip.lineName} ${it.trip.origin} ${it.trip.destination}")
    }

    val seen = mutableSetOf<String>()
    data.entries.sortedBy { it.trip.lineName }.forEach { it ->
        val evu = getOperator(it.trip.lineName, it.trip.origin.name, it.trip.destination.name) ?: ""
        val s = "${it.trip.lineName} ${gray("from ${it.trip.origin.name}")} to ${gray(it.trip.destination.name)} $evu"
        if (s !in seen) {
            term.println(s)
            //term.println("$s ${black("${it.trip.stopovers.joinToString { stop -> stop.name }}}")}")
            seen += s
        }
    }
}
