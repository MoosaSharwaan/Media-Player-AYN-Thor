package com.msharwaan.exoplayer.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A place in the app. A goes one step forward into a new route; B goes one step back (D20). */
sealed interface Route {
    data object Browse : Route
    /** A series or anime: [groupKey] names the show; [openEpisodes] starts on the episode cards. */
    data class Show(val groupKey: String, val openEpisodes: Boolean = false) : Route
    data class Player(val fileId: Long) : Route
    data object Diagnostics : Route
    data object Settings : Route
    data class FixMatch(val groupKey: String) : Route
    data class FindSubtitles(val fileId: Long) : Route
    data object Search : Route
    /** The folder browser at [path] (folders from the library root, joined with '/'). */
    data class Folders(val path: String) : Route
}

/** The back stack. Browse is always at the bottom. */
class Navigator {
    private val _stack = MutableStateFlow<List<Route>>(listOf(Route.Browse))
    val stack: StateFlow<List<Route>> = _stack.asStateFlow()
    val current: Route get() = _stack.value.last()

    fun push(route: Route) { _stack.value = _stack.value + route }

    /** Replaces the top route, so B skips the one being left. */
    fun replace(route: Route) { _stack.value = _stack.value.dropLast(1) + route }

    /** Returns false at the root, where Back is left to the system. */
    fun pop(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.value = _stack.value.dropLast(1)
        return true
    }
}
