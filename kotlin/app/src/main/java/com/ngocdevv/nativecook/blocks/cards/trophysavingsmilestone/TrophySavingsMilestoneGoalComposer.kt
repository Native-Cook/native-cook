package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

/** A local draft never mutates the School Fees milestone behind this sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrophySavingsMilestoneGoalComposer(onDismiss: () -> Unit) {
    var goalName by rememberSaveable { mutableStateOf("") }
    var goalAmount by rememberSaveable { mutableStateOf("") }
    var goalReady by rememberSaveable { mutableStateOf(false) }
    val amount = goalAmount.replace(",", "").trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val validGoal = goalName.trim().isNotEmpty() && amount != null
    val amountFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val formatter = remember {
        NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 0; maximumFractionDigits = 2 }
    }

    fun dismiss() {
        focus.clearFocus()
        keyboard?.hide()
        onDismiss()
    }

    fun createGoal() {
        if (!validGoal) return
        goalName = goalName.trim()
        focus.clearFocus()
        keyboard?.hide()
        goalReady = true
    }

    ModalBottomSheet(onDismissRequest = ::dismiss, containerColor = Color(0xFFF6F6F6)) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!goalReady) TextButton(onClick = ::dismiss) { Text("Cancel") }
                Text(
                    if (goalReady) "Goal Ready" else "Set New Goal",
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                )
                if (goalReady) {
                    TextButton(onClick = ::dismiss) { Text("Done") }
                } else {
                    TextButton(onClick = ::createGoal, enabled = validGoal) { Text("Create Goal") }
                }
            }
            if (goalReady) {
                Text(goalName, style = MaterialTheme.typography.titleLarge)
                Text("Target: ₦${formatter.format(amount ?: 0.0)}")
                Text("Your new goal is ready on this device.", color = Color(0xFF666666))
            } else {
                Text("New savings goal", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = goalName,
                    onValueChange = { goalName = it },
                    label = { Text("Goal name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { amountFocus.requestFocus() }),
                )
                OutlinedTextField(
                    value = goalAmount,
                    onValueChange = { goalAmount = it },
                    label = { Text("Target amount") },
                    prefix = { Text("₦") },
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus), singleLine = true,
                    isError = goalAmount.isNotBlank() && amount == null,
                    supportingText = {
                        if (goalAmount.isNotBlank() && amount == null) Text("Enter an amount greater than zero.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                )
                Text(
                    "This creates a local goal. Your completed School Fees milestone stays unchanged.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF666666),
                )
            }
        }
    }
}
