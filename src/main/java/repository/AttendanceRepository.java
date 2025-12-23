package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import db.AccessDb;

public class AttendanceRepository {

    private static final String FETCH_LATEST_EVENT_SQL = """
                SELECT TOP 1 name, locations
                FROM Events
                ORDER BY id DESC
            """;

    public Optional<EventLocationsRow> fetchLatestEventAndLocations() throws Exception {

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(FETCH_LATEST_EVENT_SQL);
                ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                return Optional.empty();
            }

            String eventName = rs.getString("name");
            String locationsCsv = rs.getString("locations");

            List<String> locations = new ArrayList<>();
            if (locationsCsv != null && !locationsCsv.isBlank()) {
                for (String s : locationsCsv.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty())
                        locations.add(t);
                }
            }

            return Optional.of(new EventLocationsRow(eventName, locations));
        }
    }

    // simple DTO
    public static class EventLocationsRow {
        public final String eventName;
        public final List<String> locations;

        public EventLocationsRow(String eventName, List<String> locations) {
            this.eventName = eventName;
            this.locations = locations;
        }
    }
}
