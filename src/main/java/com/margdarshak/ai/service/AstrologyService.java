package com.margdarshak.ai.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Rule-based astrology data provider. Supplies day-quality information, basic
 * tithi/nakshatra context, and planetary data that handlers embed into their
 * AI prompts for grounded responses.
 */
@Service
public class AstrologyService {

    private static final Map<DayOfWeek, DayInfo> DAY_QUALITIES = Map.of(
            DayOfWeek.MONDAY, new DayInfo("Moderate", "Ruled by Moon. Good for starting spiritual activities, meditation, and water-related ventures."),
            DayOfWeek.TUESDAY, new DayInfo("Challenging", "Ruled by Mars. Avoid new beginnings. Good for courage-related tasks, property, and machinery."),
            DayOfWeek.WEDNESDAY, new DayInfo("Good", "Ruled by Mercury. Excellent for business, communication, learning, and financial decisions."),
            DayOfWeek.THURSDAY, new DayInfo("Excellent", "Ruled by Jupiter. Most auspicious for new beginnings, education, marriage, and religious activities."),
            DayOfWeek.FRIDAY, new DayInfo("Good", "Ruled by Venus. Favorable for purchases, travel, relationships, and creative pursuits."),
            DayOfWeek.SATURDAY, new DayInfo("Challenging", "Ruled by Saturn. Avoid new ventures. Good for discipline, hard work, and serving others."),
            DayOfWeek.SUNDAY, new DayInfo("Good", "Ruled by Sun. Good for government work, leadership initiatives, and health-related activities.")
    );

    public DayInfo getDayQuality(LocalDate date) {
        return DAY_QUALITIES.getOrDefault(date.getDayOfWeek(),
                new DayInfo("Moderate", "General day with no strong planetary influence."));
    }

    public String getContextForDate(LocalDate date) {
        DayInfo info = getDayQuality(date);
        return String.format(
                "Date: %s (%s). Day quality: %s. Planetary note: %s",
                date, date.getDayOfWeek(), info.quality(), info.description()
        );
    }

    public record DayInfo(String quality, String description) {}
}
