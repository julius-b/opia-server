package app.opia.routes

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Reference(
    val entity: String? = null,
    val property: String? = null
)

@Serializable
sealed class ApiError {
    // the value that is actually wrong
    // useful to see a sanitized version
    abstract val value: String?

    @Serializable
    @SerialName("size")
    data class Size(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val min: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val max: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val size: Int? = null
    ) : ApiError()

    @Serializable
    @SerialName("required")
    data class Required(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val category: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("schema")
    data class Schema(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val schema: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("forbidden")
    data class Forbidden(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val property: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val auth: String? = null
    ) : ApiError()

    // Specifically not found, usually client referenced an id that does not exist
    // Does not refer to missing data
    @Serializable
    @SerialName("reference")
    data class Reference(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("conflict")
    data class Conflict(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("state")
    data class State(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("internal")
    data class Internal(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val entity: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val property: String? = null
    ) : ApiError()
}
