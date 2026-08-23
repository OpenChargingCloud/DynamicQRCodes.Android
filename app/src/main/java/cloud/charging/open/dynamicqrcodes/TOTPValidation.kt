package cloud.charging.open.dynamicqrcodes

/**
 * The outcome of checking a TOTP against the expected ones.
 *
 * [PREVIOUS] and [NEXT] are valid results, not near misses: OCPP 2.1 C25 has a
 * CSMS accept the neighbouring time slots to absorb processing delays and clock
 * differences. Which slot matched is still worth showing, because a token that
 * keeps landing in a neighbour points at a drifting clock.
 */
enum class TOTPValidation {
    CURRENT,
    PREVIOUS,
    NEXT,
    INVALID;

    val isValid: Boolean
        get() = this != INVALID
}
