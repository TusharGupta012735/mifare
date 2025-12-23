package repository;

import db.AccessDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class AttendanceRepository {

    private static final String FETCH_ALL_EVENTS_SQL = """
                SELECT id, name, locations
                FROM Events
                ORDER BY id DESC
            """;

    public List<EventRow> fetchAllEvents() throws Exception {

        List<EventRow> list = new ArrayList<>();

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(FETCH_ALL_EVENTS_SQL);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String locationsCsv = rs.getString("locations");

                List<String> locations = new ArrayList<>();
                if (locationsCsv != null && !locationsCsv.isBlank()) {
                    for (String s : locationsCsv.split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty())
                            locations.add(t);
                    }
                }

                list.add(new EventRow(id, name, locations));
            }
        }

        return list;
    }

    // DTO
    public static class EventRow {
        public final int id;
        public final String name;
        public final List<String> locations;

        public EventRow(int id, String name, List<String> locations) {
            this.id = id;
            this.name = name;
            this.locations = locations;
        }
    }
}
