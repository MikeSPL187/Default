package com.metrolist.music.ui.screens.wrapped

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Periods worth a recap with their listening time in milliseconds, newest first, and the first listen day. */
data class WrappedRecapOptions(
    val choices: List<Pair<WrappedPeriod, Long>>,
    val firstListen: LocalDate?,
)

/** Opens the period picker; shown on the stats screen. */
@Composable
fun WrappedRecapCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.crown), contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wrapped_recap), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.wrapped_recap_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.navigate_next), contentDescription = null)
        }
    }
}

/** Bottom sheet content listing the periods to recap, each with how long was listened in it. */
@Composable
fun WrappedPeriodMenu(
    loadOptions: suspend () -> WrappedRecapOptions,
    onPeriodClick: (WrappedPeriod) -> Unit,
    onPickDatesClick: (firstListen: LocalDate?) -> Unit,
) {
    val context = LocalContext.current
    val options by produceState<WrappedRecapOptions?>(initialValue = null) { value = loadOptions() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.wrapped_recap),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
        )
        val loaded = options
        if (loaded == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (loaded.choices.isEmpty()) {
                Text(
                    text = stringResource(R.string.wrapped_recap_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
                )
            } else {
                Material3MenuGroup(
                    items =
                        loaded.choices.map { (period, playTimeMs) ->
                            val minutes = (playTimeMs / 60_000).toInt()
                            Material3MenuItemData(
                                title = { Text(period.title(context)) },
                                description = { Text(pluralStringResource(R.plurals.minute, minutes, minutes)) },
                                onClick = { onPeriodClick(period) },
                            )
                        },
                )
                Spacer(Modifier.height(12.dp))
            }
            Material3MenuGroup(
                items =
                    listOf(
                        Material3MenuItemData(
                            icon = { Icon(painterResource(R.drawable.history), contentDescription = null) },
                            title = { Text(stringResource(R.string.wrapped_pick_dates)) },
                            onClick = { onPickDatesClick(loaded.firstListen) },
                        ),
                    ),
            )
        }
    }
}

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Picks a custom recap range between the first listen and today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrappedDateRangeDialog(
    firstListen: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (WrappedPeriod.Custom) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val firstDay = remember(firstListen) { minOf(firstListen ?: today, today) }
    // The picker reports days as UTC midnights.
    val selectableDates =
        remember(firstDay, today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = utcTimeMillis.toUtcDate()
                    return !date.isBefore(firstDay) && !date.isAfter(today)
                }

                override fun isSelectableYear(year: Int): Boolean = year in firstDay.year..today.year
            }
        }
    val state =
        rememberDateRangePickerState(
            yearRange = firstDay.year..today.year,
            selectableDates = selectableDates,
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.toUtcDate() ?: return@TextButton
                    val end = state.selectedEndDateMillis?.toUtcDate() ?: start
                    onConfirm(WrappedPeriod.Custom(start, end))
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}
