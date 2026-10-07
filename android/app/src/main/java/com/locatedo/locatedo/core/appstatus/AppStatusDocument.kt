package com.locatedo.locatedo.core.appstatus

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

// The static JSON at BuildConfig.APP_STATUS_URL; keys the app does not know are ignored so more can be added later.
@Serializable
data class AppStatusDocument(
    @SerialName("minimum_version") val minimumVersion: MinimumVersion? = null,
    val maintenance: Maintenance? = null,
    val notice: Notice? = null,
) {
    @Serializable
    data class Localized(val ja: String? = null, val en: String? = null) {
        fun text(language: String?): String? {
            if (language == "ja") {
                return ja ?: en
            }
            return en ?: ja
        }
    }

    @Serializable
    data class MinimumVersion(val android: String? = null)

    @Serializable
    data class Maintenance(
        @SerialName("starts_at") @Serializable(with = InstantSerializer::class) val startsAt: Instant,
        @SerialName("ends_at") @Serializable(with = InstantSerializer::class) val endsAt: Instant,
        val message: Localized? = null,
    ) {
        // Dismissing the upcoming banner is remembered per window, so a moved window shows it again.
        val key: String
            get() = "${startsAt.toEpochMilli()}-${endsAt.toEpochMilli()}"
    }

    @Serializable
    data class Notice(
        val id: String,
        @Serializable(with = InstantSerializer::class) val until: Instant,
        val message: Localized,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): AppStatusDocument = json.decodeFromString(serializer(), text)
    }
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

sealed interface MaintenancePhase {
    data object None : MaintenancePhase

    data class Upcoming(val maintenance: AppStatusDocument.Maintenance) : MaintenancePhase

    data class Active(val maintenance: AppStatusDocument.Maintenance) : MaintenancePhase

    companion object {
        fun of(maintenance: AppStatusDocument.Maintenance?, now: Instant): MaintenancePhase {
            if (maintenance == null || !now.isBefore(maintenance.endsAt)) {
                return None
            }
            if (now.isBefore(maintenance.startsAt)) {
                return Upcoming(maintenance)
            }
            return Active(maintenance)
        }
    }
}

object AppVersion {
    // Dot-separated numbers compared as numbers, so "1.10.0" is newer than "1.9.0"; missing parts count as 0.
    fun isOlder(version: String, minimum: String): Boolean {
        val lhs = components(version)
        val rhs = components(minimum)
        for (index in 0 until maxOf(lhs.size, rhs.size)) {
            val left = lhs.getOrElse(index) { 0 }
            val right = rhs.getOrElse(index) { 0 }
            if (left != right) {
                return left < right
            }
        }
        return false
    }

    private fun components(version: String): List<Int> = version.split(".").map { it.toIntOrNull() ?: 0 }
}
