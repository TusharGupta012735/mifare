package repository;

import db.AccessDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class AttendanceRepository {

    private static final String FETCH_EVENTS_WITH_LOCATIONS_SQL = """
                SELECT
                    e.id   AS event_id,
                    e.name AS event_name,
                    l.location_name
                FROM Events e
                LEFT JOIN Event_Locations l ON l.event_id = e.id
                ORDER BY e.id DESC
            """;

    public List<EventRow> fetchAllEvents() throws Exception {

        Map<Integer, EventRow> map = new LinkedHashMap<>();

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(FETCH_EVENTS_WITH_LOCATIONS_SQL);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                int eventId = rs.getInt("event_id");
                String eventName = rs.getString("event_name");
                String location = rs.getString("location_name");

                EventRow row = map.computeIfAbsent(
                        eventId,
                        id -> new EventRow(id, eventName));

                if (location != null && !location.isBlank()) {
                    row.locations.add(location.trim());
                }
            }
        }

        return new ArrayList<>(map.values());
    }

    /* ================= DTO ================= */

    public static class EventRow {
        public final int id;
        public final String name;
        public final List<String> locations = new ArrayList<>();

        public EventRow(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
