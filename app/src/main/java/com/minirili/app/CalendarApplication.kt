package com.minirili.app

import android.app.Application
import com.minirili.app.data.HolidayService
import com.minirili.app.utils.TravelAdvicePrefs
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CalendarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HolidayService.initFromJson(this)
        TravelAdvicePrefs.reschedule(this)
    }
}
