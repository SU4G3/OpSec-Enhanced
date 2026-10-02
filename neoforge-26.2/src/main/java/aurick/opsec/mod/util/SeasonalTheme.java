package aurick.opsec.mod.util;

import java.time.LocalDate;
import java.time.MonthDay;

/**
 * Purely cosmetic seasonal accent-color override — no effect on any protection behavior.
 * Halloween window: Oct 24 - Oct 31 (one week before, through the day itself).
 * Christmas window: Dec 18 - Dec 25 (one week before, through the day itself).
 */
public final class SeasonalTheme {

    public enum Season { NONE, HALLOWEEN, CHRISTMAS }

    private static final MonthDay HALLOWEEN_START = MonthDay.of(10, 24);
    private static final MonthDay HALLOWEEN_END = MonthDay.of(10, 31);
    private static final MonthDay CHRISTMAS_START = MonthDay.of(12, 18);
    private static final MonthDay CHRISTMAS_END = MonthDay.of(12, 25);

    private SeasonalTheme() {}

    public static Season currentSeason(boolean halloweenEnabled, boolean christmasEnabled) {
        MonthDay today = MonthDay.from(LocalDate.now());
        if (halloweenEnabled && isWithin(today, HALLOWEEN_START, HALLOWEEN_END)) {
            return Season.HALLOWEEN;
        }
        if (christmasEnabled && isWithin(today, CHRISTMAS_START, CHRISTMAS_END)) {
            return Season.CHRISTMAS;
        }
        return Season.NONE;
    }

    private static boolean isWithin(MonthDay today, MonthDay start, MonthDay end) {
        return !today.isBefore(start) && !today.isAfter(end);
    }
}
