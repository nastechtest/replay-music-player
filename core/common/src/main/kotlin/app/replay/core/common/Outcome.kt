package app.replay.core.common

/** Result of an operation that can fail with a classified [ReplayError]. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: ReplayError) : Outcome<Nothing>

    companion object {
        fun <T> success(value: T): Outcome<T> = Success(value)
        fun failure(error: ReplayError): Outcome<Nothing> = Failure(error)
    }
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

/**
 * Classified failures (architecture.md §9). Raw exception text never reaches the UI; each case maps
 * to user copy in one place.
 */
sealed interface ReplayError {
    data object Offline : ReplayError
    data class Network(val detail: String? = null) : ReplayError
    data class Server(val httpStatus: Int? = null) : ReplayError
    data class AuthRequired(val detail: String? = null) : ReplayError

    /** A sign-in that worked before and doesn't any more: the listener signs in again. */
    data class AuthExpired(val detail: String? = null) : ReplayError
    data class RateLimited(val retryAfterMillis: Long? = null) : ReplayError
    data class NotFound(val what: String) : ReplayError
    data class InvalidMedia(val detail: String? = null) : ReplayError
    data class UnsupportedFormat(val codec: String? = null) : ReplayError
    data class PolicyDisabled(val detail: String? = null) : ReplayError
    data class PermissionRequired(val permission: String) : ReplayError
    data class Unexpected(val detail: String? = null) : ReplayError

    /** The source doesn't offer this at all (not a failure, never retried). */
    data class Unsupported(val what: String) : ReplayError

    /** It exists, but can't be played here (region, rights, needs another app). */
    data class NotPlayable(val reason: String) : ReplayError

    /** Something only the listener can do: install an app, grant access, accept terms. */
    data class UserActionRequired(val action: String) : ReplayError
}

/**
 * Worth trying again later without the listener doing anything: the network, a busy or failing
 * server, a rate limit. Misses, refusals and things the source doesn't offer are permanent.
 */
val ReplayError.isTransient: Boolean
    get() = this is ReplayError.Offline || this is ReplayError.Network || this is ReplayError.Server ||
        this is ReplayError.RateLimited || this is ReplayError.Unexpected
