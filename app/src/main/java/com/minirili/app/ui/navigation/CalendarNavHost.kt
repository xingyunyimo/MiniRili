package com.minirili.app.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.minirili.app.ui.screens.PrivacyPolicyScreen
import com.minirili.app.ui.screens.calendar.CalendarScreen
import com.minirili.app.ui.screens.calendar.CalendarViewType
import com.minirili.app.ui.screens.event.EventDetailScreen
import com.minirili.app.ui.screens.events.AllEventsScreen
import com.minirili.app.ui.screens.weather.WeatherScreen
import com.minirili.app.ui.viewmodel.EventViewModel

fun buildStartDestination(appContext: Context): String =
    if (com.minirili.app.ui.screens.shouldShowPrivacyPolicy(appContext)) {
        Screen.PrivacyPolicy.route
    } else {
        Screen.Calendar.createRoute("month")
    }

@Composable
fun CalendarNavHost(
    navController: NavHostController,
    viewModel: EventViewModel,
    onStartPrivacyPolicy: () -> Unit
) {
    val startDestination = buildStartDestination(navController.context)
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onAccept = {
                    onStartPrivacyPolicy()
                    navController.navigate(Screen.Calendar.createRoute("month")) {
                        popUpTo(Screen.PrivacyPolicy.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Calendar.route,
            arguments = listOf(navArgument("viewMode") {
                type = NavType.StringType
                defaultValue = "month"
            })
        ) { backStackEntry ->
            val viewModeStr = backStackEntry.arguments?.getString("viewMode") ?: "month"
            val initialViewMode = when (viewModeStr) {
                "day" -> CalendarViewType.DAY
                "week" -> CalendarViewType.WEEK
                "year" -> CalendarViewType.YEAR
                else -> CalendarViewType.MONTH
            }
            CalendarScreen(
                viewModel = viewModel,
                navController = navController,
                initialViewMode = initialViewMode
            )
        }

        composable(Screen.Weather.route) {
            WeatherScreen(navController = navController)
        }

        composable(Screen.AllEvents.route) {
            AllEventsScreen(
                viewModel = viewModel,
                navController = navController
            )
        }

        composable(
            route = Screen.EventDetail.route,
            arguments = listOf(
                navArgument("eventId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("date") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId") ?: 0L
            val initDate = backStackEntry.arguments?.getString("date") ?: ""
            EventDetailScreen(
                eventId = eventId,
                viewModel = viewModel,
                navController = navController,
                initDate = initDate
            )
        }
    }
}