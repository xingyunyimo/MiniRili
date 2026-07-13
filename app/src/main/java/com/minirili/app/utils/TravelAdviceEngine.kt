package com.minirili.app.utils

import com.minirili.app.data.weather.CurrentWeather
import com.minirili.app.data.weather.DailyWeather

/**
 * 出行建议规则引擎（v1.3 WTH-06）。
 *
 * 基于当前天气 + 今日预报，生成一组出行建议文案。
 * 规则均为本地计算，无需联网。
 */
object TravelAdviceEngine {

    /**
     * 生成今日出行建议列表。
     * @param current 当前实况天气
     * @param todayDaily 今日预报（daily[0]）
     * @return 建议文案列表，无建议时返回空列表
     */
    fun getAdvice(current: CurrentWeather, todayDaily: DailyWeather?): List<String> {
        val advice = mutableListOf<String>()

        // 1. 高温防晒
        val maxTemp = todayDaily?.tempMax ?: current.temperature
        if (maxTemp >= 35.0) {
            advice.add("高温天气（≥35°C），注意防晒防暑，尽量避免午后外出")
        } else if (maxTemp >= 30.0) {
            advice.add("气温较高（≥30°C），外出注意防晒")
        }

        // 2. 降温穿衣
        val minTemp = todayDaily?.tempMin ?: current.temperature
        if (minTemp <= 0.0) {
            advice.add("气温低至零下，注意防寒保暖")
        } else if (maxTemp <= 10.0) {
            advice.add("气温偏低（≤10°C），注意添衣保暖")
        }

        // 3. 昼夜温差大
        if (todayDaily != null) {
            val diff = todayDaily.tempMax - todayDaily.tempMin
            if (diff >= 15.0) {
                advice.add("昼夜温差较大（≥15°C），注意适时增减衣物")
            }
        }

        // 4. 下雨带伞
        val weatherCode = todayDaily?.weatherCode ?: current.weatherCode
        if (isRainy(weatherCode)) {
            advice.add("有降水，出门记得带伞")
        } else {
            val precProb = todayDaily?.precipitationProbabilityMax ?: 0
            if (precProb >= 60) {
                advice.add("降水概率较高（${precProb}%），建议带伞以防万一")
            } else if (precProb >= 30) {
                advice.add("可能有降水（${precProb}%），出门可备伞")
            }
        }

        // 5. 大风提醒
        val windSpeed = current.windSpeed
        if (windSpeed >= 60.0) {
            advice.add("风力较大（≥60 km/h），注意防风，避免户外高空作业")
        } else if (windSpeed >= 40.0) {
            advice.add("风力较强（≥40 km/h），外出注意防风")
        } else if (windSpeed >= 25.0) {
            advice.add("风力适中（≥25 km/h），户外活动注意")
        }

        // 6. 雷暴提醒
        if (weatherCode == 95 || weatherCode == 96 || weatherCode == 99) {
            advice.add("有雷暴天气，注意安全，避免户外活动")
        }

        // 7. 雾霾/低能见度
        if (weatherCode == 45 || weatherCode == 48) {
            advice.add("有雾，能见度较低，驾车出行注意安全")
        }

        // 8. 冰雪提醒
        if (weatherCode in 71..77 || weatherCode == 85 || weatherCode == 86) {
            advice.add("有降雪，路面湿滑，出行注意安全")
        }

        // 9. 冻雨提醒
        if (weatherCode == 56 || weatherCode == 57 || weatherCode == 66 || weatherCode == 67) {
            advice.add("有冻雨，路面可能结冰，出行注意防滑")
        }

        return advice
    }

    private fun isRainy(code: Int): Boolean {
        return code in 51..57 || code in 61..67 || code in 80..82
    }
}