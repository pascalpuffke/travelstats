package traewelling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias Metres = Int
typealias Minutes = Int

@Serializable
data class User(
    val id: Int,
    val displayName: String,
    val username: String,
    val profilePicture: String, // URL
    val trainDistance: Metres,
    val trainDuration: Minutes,
    val trainSpeed: Double, // ((trainDistance / 1000) / (trainDuration / 60) / 1000) km/h
    val points: Int,
    val twitterUrl: String?,
    val mastodonUrl: String?,
    val privateProfile: Boolean,
    //val privacyHideDays: Int?,
    val userInvisibleToMe: Boolean,
    val muted: Boolean,
    val following: Boolean,
    val followPending: Boolean,
    val preventIndex: Boolean,
)

@Serializable
data class Meta(
    val user: User,
    // YYYY-MM-DD'T'HH:mm:ss(+TZ)
    val from: String,
    val to: String,
    val exportedAt: String,
)

@Serializable
data class TrainOriginOrDestination(
    val id: Int,
    val name: String,
    val rilIdentifier: String?, // Unknown type
    val evaIdentifier: Int,
    val arrival: String,
    val arrivalPlanned: String?,
    val arrivalReal: String?,
    val arrivalPlatformPlanned: String?,
    val arrivalPlatformReal: String?,
    val departure: String,
    val departurePlanned: String?,
    val departureReal: String?,
    val departurePlatformPlanned: String?,
    val departurePlatformReal: String?,
    val platform: String?,
    val isArrivalDelayed: Boolean,
    val isDepartureDelayed: Boolean,
    val cancelled: Boolean,
)

// 'Train' isn't entirely accurate, as e.g. bus journeys are recorded too
@Serializable
data class Train(
    val trip: Int,
    val hafasId: String,
    val category: String,
    val number: String,
    val lineName: String,
    val distance: Metres,
    val points: Int,
    val duration: Minutes,
    val speed: Double,
    val origin: TrainOriginOrDestination,
    val destination: TrainOriginOrDestination,
)

// Thank you Traewelling for breaking my deserialization code and causing headaches because I had no
// fucking idea why this suddenly broke. It appears they now changed the 'station' field at 'status.event'
// from a simple (nullable) string to an entire struct with a lot more data, for whatever reason.
@Serializable
data class EventStation(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val ibnr: Int,
    val rilIdentifier: String?,
)

@Serializable
data class Event(
    val id: Int,
    val name: String,
    val slug: String,
    val hashtag: String,
    val host: String,
    val url: String,
    val begin: String,
    val end: String,
    val station: EventStation?
)

@Serializable
data class Status(
    val id: Int,
    val body: String,
    val type: String,
    val user: Int,
    val username: String,
    val profilePicture: String,
    val preventIndex: Boolean,
    val business: Int, // Why isn't this a bool?
    val visibility: Int, // Private, follower only, unlisted, public?
    val likes: Int,
    val liked: Boolean,
    val createdAt: String,
    val train: Train,
    val event: Event?,
)

@Serializable
data class Stopover(
    val id: Int,
    val name: String,
    val rilIdentifier: String?,
    val evaIdentifier: Int,
    val arrival: String,
    val arrivalPlanned: String?, // Null for first stop
    val arrivalReal: String?,
    val arrivalPlatformPlanned: String?,
    val arrivalPlatformReal: String?,
    val departure: String,
    val departurePlanned: String?, // Null for last stop
    val departureReal: String?,
    val departurePlatformPlanned: String?,
    val departurePlatformReal: String?,
    val platform: String?,
    val isArrivalDelayed: Boolean,
    val isDepartureDelayed: Boolean,
    val cancelled: Boolean,
)

@Serializable
data class Trip(
    val id: Int,
    val category: String,
    val number: String,
    val lineName: String,
    val origin: TripOriginOrDestination,
    val destination: TripOriginOrDestination,
    val stopovers: Array<Stopover>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Trip

        if (id != other.id) return false
        if (category != other.category) return false
        if (number != other.number) return false
        if (lineName != other.lineName) return false
        if (origin != other.origin) return false
        if (destination != other.destination) return false
        if (!stopovers.contentEquals(other.stopovers)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + category.hashCode()
        result = 31 * result + number.hashCode()
        result = 31 * result + lineName.hashCode()
        result = 31 * result + origin.hashCode()
        result = 31 * result + destination.hashCode()
        result = 31 * result + stopovers.contentHashCode()
        return result
    }
}

@Serializable
data class TripOriginOrDestination(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val ibnr: Int,
    val rilIdentifier: String?,
)

@Serializable
data class DataEntry(
    val status: Status,
    val trip: Trip,
)

@Serializable
data class TraewellingJson(
    val meta: Meta,
    @SerialName("data")
    val entries: Array<DataEntry>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TraewellingJson

        if (meta != other.meta) return false
        if (!entries.contentEquals(other.entries)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = meta.hashCode()
        result = 31 * result + entries.contentHashCode()
        return result
    }
}
