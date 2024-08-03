package app.opia.routes

import kotlinx.serialization.SerialName

enum class Code {
    // only success response
    @SerialName("ok")
    OK,

    @SerialName("schema")
    Schema,

    @SerialName("constraint")
    Constraint,

    @SerialName("conflict")
    Conflict,

    @SerialName("required")
    Required,

    @SerialName("reference")
    Reference,

    @SerialName("unauthenticated")
    Unauthenticated,

    @SerialName("forbidden")
    Forbidden,

    @SerialName("expired")
    Expired,

    @SerialName("signature")
    Signature,

    @SerialName("internal")
    Internal
}
