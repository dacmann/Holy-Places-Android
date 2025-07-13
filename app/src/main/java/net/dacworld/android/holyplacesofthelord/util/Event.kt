package net.dacworld.android.holyplacesofthelord.util // Or your preferred package

import androidx.lifecycle.Observer // Make sure this import is correct

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T = content
}

/**
 * An [Observer] for [Event]s, simplifying the boilerplate of checking if the Event's content has been handled.
 *
 * [onEventUnhandledContent] is *only* called if the [Event]'s contents has not been handled.
 */
class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {

    // Corrected onChanged signature to match what your IDE generated: NON-NULLABLE Event<T>
    override fun onChanged(value: Event<T>) { // Parameter 'value' is Event<T> (non-null)
        // Since 'value' is non-null, we don't need the ?. safe call on it directly
        value.getContentIfNotHandled()?.let { unhandledContent ->
            // unhandledContent is of type T (non-null, due to .let on a non-null T?)
            onEventUnhandledContent(unhandledContent)
        }
    }
}
