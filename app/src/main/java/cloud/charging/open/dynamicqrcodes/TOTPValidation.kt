/*
 * Copyright (c) 2024-2026 GraphDefined GmbH <achim.friedland@graphdefined.com>
 * This file is part of DynamicQRCodes.Android <https://github.com/OpenChargingCloud/DynamicQRCodes.Android>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
