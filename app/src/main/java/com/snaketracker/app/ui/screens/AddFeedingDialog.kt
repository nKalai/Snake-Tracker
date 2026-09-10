package com.snaketracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem

private const val CUSTOM_ENTRY_LABEL = "Custom (not from stock)"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedingDialog(
    snakeId: Long,
    stockItems: List<FoodStockItem>,
    onDismiss: () -> Unit,
    onSave: (FeedingEvent) -> Unit
) {
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedStockItem by remember { mutableStateOf<FoodStockItem?>(null) }
    var foodType by remember { mutableStateOf("Mouse") }
    var foodSize by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(true) }
    var assist by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val dropdownLabel = selectedStockItem?.let { "${it.name} (${it.quantity} left)" } ?: CUSTOM_ENTRY_LABEL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Feeding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(label = "Date", dateMillis = date, onDateSelected = { date = it })

                if (stockItems.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = dropdownLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Food source") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(CUSTOM_ENTRY_LABEL) },
                                onClick = {
                                    selectedStockItem = null
                                    dropdownExpanded = false
                                }
                            )
                            stockItems.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text("${item.name} (${item.quantity} left)") },
                                    enabled = item.quantity > 0,
                                    onClick = {
                                        selectedStockItem = item
                                        foodType = item.foodType
                                        foodSize = item.size
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (selectedStockItem != null) {
                        Text(
                            "Selecting Save will subtract 1 from this item's stock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = foodType, onValueChange = { foodType = it }, label = { Text("Food type") },
                    enabled = selectedStockItem == null, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = foodSize, onValueChange = { foodSize = it }, label = { Text("Size (e.g. Small Adult, 20g)") },
                    enabled = selectedStockItem == null, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accepted", modifier = Modifier.weight(1f))
                    Switch(checked = accepted, onCheckedChange = { accepted = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Assist-fed", modifier = Modifier.weight(1f))
                    Switch(checked = assist, onCheckedChange = { assist = it })
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    FeedingEvent(
                        snakeId = snakeId,
                        date = date,
                        foodType = foodType.trim(),
                        foodSize = foodSize.trim(),
                        accepted = accepted,
                        assist = assist,
                        notes = notes.trim(),
                        foodStockItemId = selectedStockItem?.id
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
