/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import androidx.annotation.StringRes
import com.metrolist.music.R
import kotlin.random.Random

data class MessagePair(val tease: String, val reveal: String)

/** A tease shown before the listening time and the line revealing it; the reveal takes the minutes as `%s`. */
data class WrappedMessage(@StringRes val tease: Int, @StringRes val reveal: Int)

object WrappedRepository {
    /**
     * Average minutes a day at which each tier after the first starts: the original yearly marks of
     * 1,000, 5,000, 15,000 and 40,000 minutes spread over 365 days, so any period length compares alike.
     */
    private val tierStarts = listOf(1_000, 5_000, 15_000, 40_000).map { it / 365.0 }

    private val tiers =
        listOf(
            listOf(
                WrappedMessage(R.string.wrapped_message_0_0_tease, R.string.wrapped_message_0_0_reveal),
                WrappedMessage(R.string.wrapped_message_0_1_tease, R.string.wrapped_message_0_1_reveal),
                WrappedMessage(R.string.wrapped_message_0_2_tease, R.string.wrapped_message_0_2_reveal),
                WrappedMessage(R.string.wrapped_message_0_3_tease, R.string.wrapped_message_0_3_reveal),
            ),
            listOf(
                WrappedMessage(R.string.wrapped_message_1_0_tease, R.string.wrapped_message_1_0_reveal),
                WrappedMessage(R.string.wrapped_message_1_1_tease, R.string.wrapped_message_1_1_reveal),
                WrappedMessage(R.string.wrapped_message_1_2_tease, R.string.wrapped_message_1_2_reveal),
                WrappedMessage(R.string.wrapped_message_1_3_tease, R.string.wrapped_message_1_3_reveal),
            ),
            listOf(
                WrappedMessage(R.string.wrapped_message_2_0_tease, R.string.wrapped_message_2_0_reveal),
                WrappedMessage(R.string.wrapped_message_2_1_tease, R.string.wrapped_message_2_1_reveal),
                WrappedMessage(R.string.wrapped_message_2_2_tease, R.string.wrapped_message_2_2_reveal),
                WrappedMessage(R.string.wrapped_message_2_3_tease, R.string.wrapped_message_2_3_reveal),
            ),
            listOf(
                WrappedMessage(R.string.wrapped_message_3_0_tease, R.string.wrapped_message_3_0_reveal),
                WrappedMessage(R.string.wrapped_message_3_1_tease, R.string.wrapped_message_3_1_reveal),
                WrappedMessage(R.string.wrapped_message_3_2_tease, R.string.wrapped_message_3_2_reveal),
                WrappedMessage(R.string.wrapped_message_3_3_tease, R.string.wrapped_message_3_3_reveal),
            ),
            listOf(
                WrappedMessage(R.string.wrapped_message_4_0_tease, R.string.wrapped_message_4_0_reveal),
                WrappedMessage(R.string.wrapped_message_4_1_tease, R.string.wrapped_message_4_1_reveal),
                WrappedMessage(R.string.wrapped_message_4_2_tease, R.string.wrapped_message_4_2_reveal),
                WrappedMessage(R.string.wrapped_message_4_3_tease, R.string.wrapped_message_4_3_reveal),
            ),
        )

    private val fallback = WrappedMessage(R.string.wrapped_message_fallback_tease, R.string.wrapped_message_fallback_reveal)

    /** 0 for the lightest listeners up to 4 for the heaviest. */
    fun tier(totalMinutes: Long, days: Long): Int {
        val perDay = totalMinutes.toDouble() / days.coerceAtLeast(1)
        return tierStarts.count { perDay >= it }
    }

    /** A random message index within [tier], stable across recompositions once remembered. */
    fun randomIndex(tier: Int, random: Random = Random): Int = random.nextInt(tiers.getOrNull(tier)?.size ?: 1)

    fun message(tier: Int, index: Int): WrappedMessage = tiers.getOrNull(tier)?.getOrNull(index) ?: fallback
}
