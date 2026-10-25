package app.aster.aster_keyboard.settings

/** Authoritative bounds for persisted integer preferences and platform-channel updates. */
object SettingsConstraints {
    private val ranges = mapOf(
        "keyHeight" to 42..66,
        "keySpacing" to 0..5,
        "keyCornerRadius" to 0..18,
        "longPressDelay" to 200..800,
    )
    fun integer(key: String, value: Number): Int {
        val range = requireNotNull(ranges[key]) { "Unknown integer setting" }
        val number = value.toDouble()
        require(number.isFinite()) { "Setting must be a finite number" }
        return number.coerceIn(range.first.toDouble(), range.last.toDouble()).toInt()
    }
}
