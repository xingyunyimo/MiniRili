package com.minirili.app.di

import android.content.Context
import com.minirili.app.data.weather.OpenMeteoApi
import com.minirili.app.data.weather.WeatherDataSource
import com.minirili.app.data.weather.WeatherRepository
import com.minirili.app.database.CalendarDatabase
import com.minirili.app.database.dao.CityDao
import com.minirili.app.database.dao.WeatherCacheDao
import com.minirili.app.repository.EventRepository
import com.minirili.app.scheduler.RecurringReminderScheduler
import com.minirili.app.scheduler.ReminderScheduler
import com.minirili.app.utils.LocationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCalendarDatabase(@ApplicationContext context: Context): CalendarDatabase {
        return CalendarDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideEventDao(db: CalendarDatabase) = db.eventDao()

    @Provides
    @Singleton
    fun provideWeatherCacheDao(db: CalendarDatabase): WeatherCacheDao = db.weatherCacheDao()

    @Provides
    @Singleton
    fun provideCityDao(db: CalendarDatabase): CityDao = db.cityDao()

    @Provides
    @Singleton
    fun provideReminderScheduler(@ApplicationContext context: Context): ReminderScheduler {
        return ReminderScheduler(context)
    }

    @Provides
    @Singleton
    fun provideRecurringReminderScheduler(
        @ApplicationContext context: Context,
        eventDao: com.minirili.app.database.dao.EventDao,
        reminderScheduler: ReminderScheduler
    ): RecurringReminderScheduler {
        return RecurringReminderScheduler(context, eventDao, reminderScheduler)
    }

    @Provides
    @Singleton
    fun provideEventRepository(
        eventDao: com.minirili.app.database.dao.EventDao,
        reminderScheduler: ReminderScheduler,
        recurringReminderScheduler: RecurringReminderScheduler,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): EventRepository {
        return EventRepository(eventDao, reminderScheduler, recurringReminderScheduler, context)
    }

    @Provides
    @Singleton
    fun provideWeatherDataSource(): WeatherDataSource = OpenMeteoApi()

    @Provides
    @Singleton
    fun provideWeatherRepository(
        dataSource: WeatherDataSource,
        weatherCacheDao: WeatherCacheDao,
        cityDao: CityDao
    ): WeatherRepository {
        return WeatherRepository(dataSource, weatherCacheDao, cityDao)
    }

    @Provides
    @Singleton
    fun provideLocationHelper(@ApplicationContext context: Context): LocationHelper {
        return LocationHelper(context)
    }
}
