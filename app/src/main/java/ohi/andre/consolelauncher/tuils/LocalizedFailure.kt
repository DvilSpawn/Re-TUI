package ohi.andre.consolelauncher.tuils

import android.content.Context
import androidx.annotation.StringRes

/** A validation failure carries a resource until it reaches a UI context. */
interface LocalizedFailure {
    @get:StringRes val messageResource: Int
    val messageArguments: Array<out Any?>
}

class LocalizedArgumentException(
    @StringRes override val messageResource: Int,
    vararg arguments: Any?
) : IllegalArgumentException(), LocalizedFailure {
    override val messageArguments = arguments
}

class LocalizedStateException(
    @StringRes override val messageResource: Int,
    vararg arguments: Any?
) : IllegalStateException(), LocalizedFailure {
    override val messageArguments = arguments
}

fun Throwable.displayMessage(context: Context): String? =
    if (this is LocalizedFailure) context.getString(messageResource, *messageArguments.map {
        if (it is Throwable && it is LocalizedFailure) it.displayMessage(context) else it
    }.toTypedArray())
    else message
