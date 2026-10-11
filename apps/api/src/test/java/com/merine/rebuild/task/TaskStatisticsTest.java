package com.merine.rebuild.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.task.TaskStatisticsLookup.Period;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskStatisticsTest {
    @Test
    void monthStartsAtBeijingMidnightInsteadOfUtcMidnight() {
        var september = TaskStatisticsLookup.timeRange(Period.THIS_MONTH, Instant.parse("2026-09-30T15:59:59Z"));
        var october = TaskStatisticsLookup.timeRange(Period.THIS_MONTH, Instant.parse("2026-09-30T16:00:00Z"));
        assertThat(september.startAt()).isEqualTo(Instant.parse("2026-08-31T16:00:00Z"));
        assertThat(september.endAtExclusive()).isEqualTo(Instant.parse("2026-09-30T15:59:59Z"));
        assertThat(october.startAt()).isEqualTo(Instant.parse("2026-09-30T16:00:00Z"));
        assertThat(october.endAtExclusive()).isEqualTo(october.startAt());
    }

    @Test
    void thisMonthStopsAtServerTimeInsteadOfTheFutureMonthEnd() {
        var now = Instant.parse("2026-10-10T04:34:56Z");
        var range = TaskStatisticsLookup.timeRange(Period.THIS_MONTH, now);
        assertThat(range.startAt()).isEqualTo(Instant.parse("2026-09-30T16:00:00Z"));
        assertThat(range.endAtExclusive()).isEqualTo(now);
    }

    @Test
    void previousMonthHandlesYearBoundaryAndLeapYear() {
        var december = TaskStatisticsLookup.timeRange(Period.LAST_MONTH, Instant.parse("2027-01-01T00:00:00Z"));
        var february = TaskStatisticsLookup.timeRange(Period.LAST_MONTH, Instant.parse("2028-03-01T00:00:00Z"));
        assertThat(december.startAt()).isEqualTo(Instant.parse("2026-11-30T16:00:00Z"));
        assertThat(december.endAtExclusive()).isEqualTo(Instant.parse("2026-12-31T16:00:00Z"));
        assertThat(february.startAt()).isEqualTo(Instant.parse("2028-01-31T16:00:00Z"));
        assertThat(february.endAtExclusive()).isEqualTo(Instant.parse("2028-02-29T16:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-10T04:34:56Z,2026-09-10T04:34:56Z",
            "2026-02-10T04:34:56Z,2026-01-10T04:34:56Z",
            "2026-03-31T04:34:56Z,2026-02-28T04:34:56Z",
            "2028-03-31T04:34:56Z,2028-02-29T04:34:56Z",
            "2026-05-31T04:34:56Z,2026-04-30T04:34:56Z",
            "2027-01-31T04:34:56Z,2026-12-31T04:34:56Z",
            "2026-09-30T16:00:00Z,2026-08-31T16:00:00Z"
    })
    void rollingMonthPreservesBeijingTimeAndHandlesMissingDay(String nowText, String startText) {
        var now = Instant.parse(nowText);
        var range = TaskStatisticsLookup.timeRange(Period.ROLLING_MONTH, now);
        assertThat(range.startAt()).isEqualTo(Instant.parse(startText));
        assertThat(range.endAtExclusive()).isEqualTo(now);
    }
}
