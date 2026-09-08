package com.snaketracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.snaketracker.app.data.entities.WeightEntry

@Composable
fun AddWeightDialog(
    snakeId: Long,
    onDismiss: () -> Unit,
    onSave: (WeightEntry) -> Unit
) {
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var grams by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(label = "Date", dateMillis = date, onDateSelected = { date = it })
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Weight (grams)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val g = grams.toFloatOrNull() ?: return@TextButton
                    onSave(WeightEntry(snakeId = snakeId, date = date, grams = g, notes = notes.trim()))
                },
                enabled = grams.toFloatOrNull() != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
