package com.minirili.app.ui.navigation

sealed class Screen(val route: String) {
    object Calendar : Screen("calendar?viewMode={viewMode}") {
        fun createRoute(viewMode: String = "month") = "calendar?viewMode=$viewMode"
    }
    object Weather : Screen("weather")
    object AllEvents : Screen("all_events")
    object EventDetail : Screen("event/{eventId}?date={date}") {
        fun createRoute(eventId: Long = 0, date: String = "") = "event/$eventId?date=$date"
    }
    object PrivacyPolicy : Screen("privacy")
    object Settings : Screen("settings")
}
