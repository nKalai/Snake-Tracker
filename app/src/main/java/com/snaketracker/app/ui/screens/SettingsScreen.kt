package com.snaketracker.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.snaketracker.app.R

/**
 * Top-level Settings screen. Currently an empty destination: it is the future
 * home of the JSON import/export actions (issues #26 and #28).
 *
 * INSERTION POINT (import/export): add the import/export actions here, inside
 * the `Scaffold` body below — no other file should need to change when they
 * land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.testTag("settings_title")
                    )
                }
            )
        }
    ) { padding ->
        // INSERTION POINT (import/export): compose the import/export actions
        // in this body, honoring `padding` for the top bar inset.
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}
