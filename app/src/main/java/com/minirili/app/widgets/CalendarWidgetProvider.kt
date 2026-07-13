package com.minirili.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.minirili.app.MainActivity
import com.minirili.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 4×4 月历桌面小部件（UI-01）。
 *
 * 显示当月日历网格 + 今日标记 + 事件提示。
 * 翻页通过 PendingIntent 广播回自身更新（ACTION_VIEW_PREV / ACTION_VIEW_NEXT）。
 */
class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_VIEW_PREV || action == ACTION_VIEW_NEXT) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val currentYear = intent.getIntExtra(EXTRA_YEAR, Calendar.getInstance().get(Calendar.YEAR))
            val currentMonth = intent.getIntExtra(EXTRA_MONTH, Calendar.getInstance().get(Calendar.MONTH) + 1)

            val cal = Calendar.getInstance()
            cal.set(currentYear, currentMonth - 1, 1)
            cal.add(Calendar.MONTH, if (action == ACTION_VIEW_PREV) -1 else 1)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val views = buildCalendarViews(context, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_VIEW_PREV = "com.minirili.app.widget.VIEW_PREV"
        const val ACTION_VIEW_NEXT = "com.minirili.app.widget.VIEW_NEXT"
        const val EXTRA_YEAR = "widget_year"
        const val EXTRA_MONTH = "widget_month"

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val cal = Calendar.getInstance()
            val views = buildCalendarViews(context, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

            // 点击小部件打开 APP
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingOpen = PendingIntent.getActivity(
                context, appWidgetId, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_month_title, pendingOpen)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildCalendarViews(context: Context, year: Int, month: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.calendar_widget)
            views.setTextViewText(R.id.widget_month_title, "${year}年${month}月")

            // 计算当月信息
            val cal = Calendar.getInstance()
            cal.set(year, month - 1, 1)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOffset = (cal.get(Calendar.DAY_OF_WEEK) + 6) % 7
            val today = Calendar.getInstance()
            val todayYear = today.get(Calendar.YEAR)
            val todayMonth = today.get(Calendar.MONTH) + 1
            val todayDay = today.get(Calendar.DAY_OF_MONTH)

            // 用 RemoteViews 构建日期行
            val rowHeight = 36 // dp equivalent
            var dayCounter = 1
            val totalRows = (firstDayOffset + daysInMonth + 6) / 7

            // 先清除网格容器中可能残留的子视图
            views.removeAllViews(R.id.widget_calendar_grid)

            for (row in 0 until totalRows) {
                val rowViews = RemoteViews(context.packageName, R.layout.widget_calendar_row)
                for (col in 0..6) {
                    val cellId = context.resources.getIdentifier("cell_$col", "id", context.packageName)
                    if (cellId == 0) continue

                    if (row == 0 && col < firstDayOffset) {
                        rowViews.setTextViewText(cellId, "")
                        rowViews.setViewVisibility(cellId, View.VISIBLE)
                    } else if (dayCounter <= daysInMonth) {
                        rowViews.setTextViewText(cellId, dayCounter.toString())
                        rowViews.setViewVisibility(cellId, View.VISIBLE)

                        // 高亮今天
                        if (year == todayYear && month == todayMonth && dayCounter == todayDay) {
                            rowViews.setInt(cellId, "setBackgroundColor", Color.parseColor("#FF6F00"))
                            rowViews.setTextColor(cellId, Color.WHITE)
                        } else {
                            rowViews.setInt(cellId, "setBackgroundColor", Color.TRANSPARENT)
                            rowViews.setTextColor(cellId, Color.parseColor("#333333"))
                        }
                        dayCounter++
                    } else {
                        rowViews.setViewVisibility(cellId, View.GONE)
                    }
                }
                views.addView(R.id.widget_calendar_grid, rowViews)
            }

            // 翻页 PendingIntent
            val prevIntent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = ACTION_VIEW_PREV
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                putExtra(EXTRA_YEAR, year)
                putExtra(EXTRA_MONTH, month)
            }
            val prevPending = PendingIntent.getBroadcast(context, year * 12 + month, prevIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_prev, prevPending)

            val nextIntent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = ACTION_VIEW_NEXT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                putExtra(EXTRA_YEAR, year)
                putExtra(EXTRA_MONTH, month)
            }
            val nextPending = PendingIntent.getBroadcast(context, year * 12 + month + 10000, nextIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_next, nextPending)

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            views.setTextViewText(R.id.widget_event_summary, "今日 ${sdf.format(today.time)} · 点击打开日历")

            return views
        }
    }
}