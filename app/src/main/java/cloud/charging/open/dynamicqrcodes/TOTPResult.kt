package cloud.charging.open.dynamicqrcodes

data class TOTPResult(
    val previous:       String,
    val current:        String,
    val next:           String,
    val remainingTime:  ULong
)
