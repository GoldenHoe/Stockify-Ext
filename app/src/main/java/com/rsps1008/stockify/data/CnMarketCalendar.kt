package com.rsps1008.stockify.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Mainland China trading calendar.
 *
 * The Shanghai and Shenzhen exchanges close on weekends and on the public holiday
 * weekdays announced by the State Council. Weekends that are moved into service
 * (調休上班) never reopen the exchanges, so only holiday weekdays are listed here.
 * Years without a verified list fall back to weekdays only: a missing holiday can
 * only make the app refresh a quote that did not move, which is harmless, while an
 * invented date would suppress a real session.
 */
object CnMarketCalendar {

    // 國辦發明電〔2025〕7 號：2026 年部分節假日安排，只取落在週一至週五的休市日。
    private val holidays2026: Set<LocalDate> = buildSet {
        // 元旦：1/1（四）～1/3（六）
        add(LocalDate.of(2026, 1, 1))
        add(LocalDate.of(2026, 1, 2))
        // 春節：2/15（日）～2/23（一）
        add(LocalDate.of(2026, 2, 16))
        add(LocalDate.of(2026, 2, 17))
        add(LocalDate.of(2026, 2, 18))
        add(LocalDate.of(2026, 2, 19))
        add(LocalDate.of(2026, 2, 20))
        add(LocalDate.of(2026, 2, 23))
        // 清明節：4/4（六）～4/6（一）
        add(LocalDate.of(2026, 4, 6))
        // 勞動節：5/1（五）～5/5（二）
        add(LocalDate.of(2026, 5, 1))
        add(LocalDate.of(2026, 5, 4))
        add(LocalDate.of(2026, 5, 5))
        // 端午節：6/19（五）～6/21（日）
        add(LocalDate.of(2026, 6, 19))
        // 中秋節：9/25（五）～9/27（日）
        add(LocalDate.of(2026, 9, 25))
        // 國慶節：10/1（四）～10/7（三）
        add(LocalDate.of(2026, 10, 1))
        add(LocalDate.of(2026, 10, 2))
        add(LocalDate.of(2026, 10, 5))
        add(LocalDate.of(2026, 10, 6))
        add(LocalDate.of(2026, 10, 7))
    }

    private val holidaysByYear: Map<Int, Set<LocalDate>> = mapOf(2026 to holidays2026)

    /** True for a date that is a public holiday in a year with a verified list. */
    fun isHoliday(date: LocalDate): Boolean = holidaysByYear[date.year]?.contains(date) ?: false

    /** Weekends and public holidays: days the Shanghai / Shenzhen exchanges never open. */
    fun isNonTradingDay(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY ||
            date.dayOfWeek == DayOfWeek.SUNDAY ||
            isHoliday(date)

    fun isTradingDay(date: LocalDate): Boolean = !isNonTradingDay(date)
}
