package app.opia.routes

import io.ktor.server.request.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

const val KeyInstallationID = "Installation-Id"
const val KeyChallengeResponse = "Challenge-Response"
const val KeyRefreshToken = "Refresh-Token"

@Serializable
data class ApiSuccessResponse<out T : Any>(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int? = null, val data: T
)

// TODO make properties part of Actor -> no more Hints
@Serializable
data class HintedApiSuccessResponse<out T : Any, out S : Any>(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int? = null, val data: T, val hints: S
)

@Serializable
data class ApiErrorResponse(
    val scope: ValidationScope = ValidationScope.Request,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val errors: Errors? = null
)

enum class ValidationScope {
    // request headers, request body or request url
    // can usually be resolved by the client
    Request,

    // refers to state in current server db, i.e. a queried/computed property
    // usually refers to state outside this clients control
    Data
}

// TODO consider custom exception type
fun ApplicationRequest.headerOrFail(name: String): String =
    headers[name] ?: throw ValidationException(name, ApiError.Required())

class ValidationException(
    val field: String,
    vararg val errors: ApiError,
    val scope: ValidationScope? = ValidationScope.Request
) : Exception() {
    override val message: String
        get() = "$scope.${this.field}: ${errors.contentToString()}"
}

typealias Errors = Map<String, Array<out ApiError>>
