package com.snaketracker.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.snaketracker.app.ui.screens.*
import com.snaketracker.app.ui.viewmodel.SnakeViewModel

object Routes {
    const val CALENDAR = "calendar"
    const val LIST = "list"
    const val ADD_SNAKE = "add_snake"
    const val EDIT_SNAKE = "edit_snake/{snakeId}"
    const val DETAIL = "detail/{snakeId}"
    const val FOOD_STOCK = "food_stock"
    const val SETTINGS = "settings"

    fun editSnake(id: Long) = "edit_snake/$id"
    fun detail(id: Long) = "detail/$id"

    // The destinations reachable from the bottom navigation bar, derived from
    // the tab descriptors so the bar and this set can never disagree.
    val topLevel: Set<String> = TopLevelDestinations.all.map { it.route }.toSet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnakeTrackerNavGraph(viewModel: SnakeViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in Routes.topLevel) {
                NavigationBar {
                    TopLevelDestinations.all.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            modifier = Modifier.testTag(destination.testTag),
                            icon = { Icon(destination.icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALENDAR,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    viewModel = viewModel,
                    onSnakeClick = { id -> navController.navigate(Routes.detail(id)) }
                )
            }
            composable(Routes.LIST) {
                SnakeListScreen(
                    viewModel = viewModel,
                    onSnakeClick = { id -> navController.navigate(Routes.detail(id)) },
                    onAddSnakeClick = { navController.navigate(Routes.ADD_SNAKE) }
                )
            }
            composable(Routes.ADD_SNAKE) {
                AddEditSnakeScreen(viewModel = viewModel, snakeId = null, onDone = { navController.popBackStack() })
            }
            composable(
                Routes.EDIT_SNAKE,
                arguments = listOf(navArgument("snakeId") { type = NavType.LongType })
            ) { backStackEntry ->
                val snakeId = backStackEntry.arguments?.getLong("snakeId") ?: return@composable
                AddEditSnakeScreen(viewModel = viewModel, snakeId = snakeId, onDone = { navController.popBackStack() })
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("snakeId") { type = NavType.LongType })
            ) { backStackEntry ->
                val snakeId = backStackEntry.arguments?.getLong("snakeId") ?: return@composable
                SnakeDetailScreen(
                    viewModel = viewModel,
                    snakeId = snakeId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editSnake(snakeId)) }
                )
            }
            composable(Routes.FOOD_STOCK) {
                FoodStockScreen(viewModel = viewModel)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

/** Switches between the bottom-nav tabs while keeping Calendar as the base of the stack. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.CALENDAR) { inclusive = false }
        launchSingleTop = true
    }
}
