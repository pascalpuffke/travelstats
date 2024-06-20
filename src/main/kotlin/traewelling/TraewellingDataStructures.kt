package traewelling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// NOTE: ALL numbers are stored as Long. This is excessive and does not seem correct. BUT:
//       Some fields got huge and I ran into issues where the deserializer could not parse
//       huge numbers as 32-bit integers anymore. As I didn't feel like finding the exact ones
//       which could potentially overflow, I simply changed all ints to longs. Feel free to optimize
//       later, for now I don't care.

// Their exports are a fucking mess. This is hateful.
// Some newer fields are purposefully commented out to keep compatibility with existing exports.
// Most of these are useless anyway.

typealias Metres = Long
typealias Minutes = Long

@Serializable
data class User(
    val id: Long,
    val displayName: String,
    val username: String,
    val profilePicture: String, // URL
    val trainDistance: Metres,
    // val totalDistance: Metres?, // What's the difference train<>total?
    val trainDuration: Minutes,
    // val totalDuration: Minutes?,
    // trainSpeed is deprecated since Nov 2022
    // val trainSpeed: Double, // ((trainDistance / 1000) / (trainDuration / 60) / 1000) km/h
    val points: Long,
    val mastodonUrl: String?,
    val privateProfile: Boolean,
    val preventIndex: Boolean,
    @SerialName("likes_enabled") // Why is this snake_case?
    val likesEnabled: Boolean,
    //val privacyHideDays: Long?,
    val userInvisibleToMe: Boolean,
    val muted: Boolean,
    val following: Boolean,
    val followPending: Boolean,
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
    val id: Long,
    val name: String,
    val rilIdentifier: String?, // Unknown type
    val evaIdentifier: Long,
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

@Serializable
data class TrainOperator(
    val identifier: String,
    val name: String,
)

// 'Train' isn't entirely accurate, as e.g. bus journeys are recorded too
@Serializable
data class Train(
    val trip: Long,
    val hafasId: String,
    val category: String,
    val number: String,
    val lineName: String,
    val journeyNumber: Long?,
    val distance: Metres,
    val points: Long,
    val duration: Minutes,
    val manualDeparture: String?,
    val manualArrival: String?,
    val origin: TrainOriginOrDestination,
    val destination: TrainOriginOrDestination,
    val operator: TrainOperator?,
)

@Serializable
data class EventStation(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val ibnr: Long,
    val rilIdentifier: String?,
)

@Serializable
data class Event(
    val id: Long,
    val name: String,
    val slug: String,
    val hashtag: String?,
    val host: String?,
    val url: String?,
    val begin: String,
    val end: String,
    val station: EventStation?,
)

@Serializable
data class Client(
    val id: Long,
    val name: String,
    val privacyPolicyUrl: String?,
)

@Serializable
data class UserDetails(
    val id: Long,
    val displayName: String,
    val username: String,
    val profilePicture: String,
    val mastodonUrl: String?,
    val preventIndex: Boolean
)

@Serializable
data class Status(
    val id: Long,
    val body: String,
    // bodyMentions: []
    val user: Long,
    val username: String,
    val profilePicture: String,
    val preventIndex: Boolean,
    val business: Long, // Why isn't this a bool?
    val visibility: Long, // Private, follower only, unlisted, public?
    val likes: Long,
    val liked: Boolean,
    val isLikable: Boolean,
    // val client: Client?,
    val createdAt: String,
    val train: Train,
    val event: Event?,
    // val userDetails: UserDetails,
    // tags: []
)

@Serializable
data class Stopover(
    val id: Long,
    val name: String,
    val rilIdentifier: String?,
    val evaIdentifier: Long,
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
    val id: Long,
    val category: String,
    val number: String,
    val lineName: String,
    val journeyNumber: Long?,
    val origin: TripOriginOrDestination,
    val destination: TripOriginOrDestination,
    val stopovers: Array<Stopover>,
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
        var result = id.toInt()
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
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val ibnr: Long,
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
