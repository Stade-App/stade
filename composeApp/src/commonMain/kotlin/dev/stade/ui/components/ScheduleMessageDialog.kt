package dev.stade.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stade.ui.i18n.LocalStrings
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMessageDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val strings = LocalStrings.current
    val zone = remember { TimeZone.currentSystemDefault() }
    val nowLocal = remember { Clock.System.now().toLocalDateTime(zone) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = LocalDateTime(nowLocal.year, nowLocal.monthNumber, nowLocal.dayOfMonth, 0, 0)
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds()
    )
    val timeState = rememberTimePickerState(
        initialHour = nowLocal.hour,
        initialMinute = nowLocal.minute,
        is24Hour = true
    )

    val date = pickedDate
    if (date == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        val millis = dateState.selectedDateMillis ?: return@TextButton
                        pickedDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    }
                ) { Text(strings.scheduleNextAction) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            }
        ) {
            DatePicker(
                state = dateState,
                title = {
                    Text(
                        strings.scheduleMessagePickDate,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    } else {
        val target = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, timeState.hour, timeState.minute)
            .toInstant(zone)
            .toEpochMilliseconds()
        val inFuture = target > Clock.System.now().toEpochMilliseconds()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(strings.scheduleMessagePickTime) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimePicker(state = timeState)
                    Text(
                        if (inFuture) strings.scheduleDeliveryAt(formatScheduledTime(target)) else strings.scheduleTimeInPast,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (inFuture) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = inFuture, onClick = { onConfirm(target) }) {
                    Text(strings.scheduleConfirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { pickedDate = null }) { Text(strings.back) }
            }
        )
    }
}
