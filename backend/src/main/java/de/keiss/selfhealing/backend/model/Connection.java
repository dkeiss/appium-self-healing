package de.keiss.selfhealing.backend.model;

import java.time.LocalTime;
import java.util.List;

public record Connection(String id, String from, String to, LocalTime departure, LocalTime arrival, int durationMinutes,
        int transfers, double priceEuro, List<String> trainTypes, List<Leg> legs) {

    public record Leg(String from, String to, LocalTime departure, LocalTime arrival, String trainType,
            String trainNumber, String platform) {
    }
}
