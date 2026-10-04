package dev.team1.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OrderChannel {
    SALA,
    DOMICILIO;

    @JsonCreator
    public static OrderChannel fromValue(String value) {
        if (value == null) {
            return null;
        }

        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "SALA", "ONSITE" -> SALA;
            case "DOMICILIO", "ONLINE" -> DOMICILIO;
            default -> throw new IllegalArgumentException("Unknown order channel: " + value);
        };
    }
}
