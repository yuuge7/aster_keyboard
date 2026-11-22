package app.aster.aster_keyboard.ime

/** Main-thread input ordering while a word-boundary correction runs off-thread. */
internal class InputDispatchQueue {
    private val events = ArrayDeque<() -> Unit>()
    private var paused = false
    private var draining = false
    private var revision = 0L

    fun submit(event: () -> Unit) { events.addLast(event); drain() }
    fun pause(): Long { check(!paused); paused = true; return ++revision }
    fun resume(token: Long, boundary: () -> Unit) {
        if (!paused || token != revision) return
        paused = false
        events.addFirst(boundary)
        drain()
    }
    /** Editor/lifecycle changes invalidate both queued keys and worker replies. */
    fun clear() { revision++; paused = false; events.clear() }
    private fun drain() {
        if (draining) return
        draining = true
        try { while (!paused && events.isNotEmpty()) events.removeFirst().invoke() }
        finally { draining = false }
    }
}
