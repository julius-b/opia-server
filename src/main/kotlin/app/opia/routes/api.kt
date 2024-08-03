@file:OptIn(ExperimentalSerializationApi::class)

package app.opia.routes

import io.ktor.server.request.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

const val KeyInstallationID = "Installation-Id"
const val KeyChallengeResponse = "Challenge-Response"

@Serializable
data class ApiSuccessResponse<out T : Any>(
    val code: Code = Code.OK, @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int? = null, val data: T
)

// TODO make properties part of Actor -> no more Hints
@Serializable
data class HintedApiSuccessResponse<out T : Any, out S : Any>(
    val code: Code, @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int? = null, val data: T, val hints: S
)

@Serializable
data class ApiErrorResponse(
    val code: Code, @EncodeDefault(EncodeDefault.Mode.NEVER) val errors: Errors? = null
)

typealias Errors = Map<String, Array<Status>>

@Serializable
data class Status(
    val code: Code,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val raw: @Contextual Any? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val parsed: @Contextual Any? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val constraints: Map<String, @Contextual Any>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val error: String? = null
)

// TODO consider custom exception type
fun ApplicationRequest.headerOrFail(name: String): String =
    headers[name] ?: throw ValidationException(Code.Required, name)

// main: code, fieldName to fieldValue
// TODO support Any as value, eg. UUID
// TODO support Status??
class ValidationException(val code: Code, vararg val fields: Pair<String, Any?>) : Exception() {
    constructor(code: Code, vararg fieldNames: String) : this(
        code, *fieldNames.map<String, Pair<String, Any?>> { it to null }.toTypedArray()
    )

    // TODO ...
    constructor(code: Code) : this(code, *emptyArray<String>())
}

class ValidationException2(val status: Status, vararg val fields: Pair<String, Any?>) : Exception() {
    constructor(status: Status, vararg fieldNames: String) : this(
        status, *fieldNames.map<String, Pair<String, Any?>> { it to null }.toTypedArray()
    )
}
