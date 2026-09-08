package com.snaketracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.ui.viewmodel.SnakeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodStockScreen(
    viewModel: SnakeViewModel
) {
    val items by viewModel.foodStock.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Food Stock") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Add item") }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No food stock items yet. Tap + to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item -> FoodStockRow(item = item, viewModel = viewModel) }
            }
        }
    }

    if (showAddDialog) {
        AddFoodStockDialog(
            onDismiss = { showAddDialog = false },
            onSave = { viewModel.addFoodStock(it); showAddDialog = false }
        )
    }
}

@Composable
private fun FoodStockRow(item: FoodStockItem, viewModel: SnakeViewModel) {
    val isLow = item.quantity <= item.lowStockThreshold
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text("${item.foodType} ${item.size}".trim())
                Text(
                    "Qty: ${item.quantity}" + if (isLow) "  ⚠ Low stock" else "",
                    color = if (isLow) Color(0xFFC62828) else Color.Unspecified
                )
            }
            IconButton(onClick = { if (item.quantity > 0) viewModel.updateFoodStock(item.copy(quantity = item.quantity - 1)) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
            }
            IconButton(onClick = { viewModel.updateFoodStock(item.copy(quantity = item.quantity + 1)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
            IconButton(onClick = { viewModel.deleteFoodStock(item) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun AddFoodStockDialog(onDismiss: () -> Unit, onSave: (FoodStockItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var foodType by remember { mutableStateOf("Mouse") }
    var size by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("0") }
    var threshold by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Food Stock Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Frozen mice - small)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = foodType, onValueChange = { foodType = it }, label = { Text("Food type") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("Size") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantity") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = threshold, onValueChange = { threshold = it.filter { c -> c.isDigit() } },
                    label = { Text("Low stock alert threshold") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        FoodStockItem(
                            name = name.trim(),
                            foodType = foodType.trim(),
                            size = size.trim(),
                            quantity = quantity.toIntOrNull() ?: 0,
                            lowStockThreshold = threshold.toIntOrNull() ?: 5
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
