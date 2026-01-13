package repository;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import db.AccessDb;
import model.EventFormData;
import model.SubEventData;

public class EventFormRepository {

    /* ================== TABLE NAMES ================== */

    private static final String EVENTS_TABLE = "Events";
    private static final String LOCATIONS_TABLE = "Event_Locations";

    /* ================== CREATE TABLES ================== */

    private static final String CREATE_EVENTS_TABLE_SQL = """
                CREATE TABLE Events (
                    id AUTOINCREMENT PRIMARY KEY,
                    name TEXT(255) NOT NULL UNIQUE,
                    venue TEXT(255),
                    event_date TEXT(20),
                    created_at TEXT(30)
                )
            """;

    private static final String CREATE_LOCATIONS_TABLE_SQL = """
                CREATE TABLE Event_Locations (
                    id AUTOINCREMENT PRIMARY KEY,
                    event_id INTEGER NOT NULL,
                    sub_event_name TEXT(255),
                    location_name TEXT(255),
                    allowed_participant_types TEXT(255),
                    entry_from TEXT(10),
                    entry_till TEXT(10),
                    FOREIGN KEY (event_id) REFERENCES Events(id)
                )
            """;

    /* ================== INSERT SQL ================== */

    private static final String INSERT_EVENT_SQL = """
                INSERT INTO Events (name, venue, event_date, created_at)
                VALUES (?, ?, ?, ?)
            """;

    private static final String INSERT_LOCATION_SQL = """
                INSERT INTO Event_Locations (
                    event_id,
                    sub_event_name,
                    location_name,
                    allowed_participant_types,
                    entry_from,
                    entry_till
                )
                VALUES (?, ?, ?, ?, ?, ?)
            """;

    /* ================== PUBLIC API ================== */

    public void insertFullEvent(EventFormData ev) throws Exception {

        try (Connection conn = AccessDb.getConnection()) {
            conn.setAutoCommit(false);

            ensureTablesExist(conn);

            int eventId = insertEvent(conn, ev);
            insertSubEvents(conn, eventId, ev.subEvents);

            conn.commit();
        }
    }

    /* ================== PRIVATE HELPERS ================== */

    private int insertEvent(Connection conn, EventFormData ev) throws Exception {

        try (PreparedStatement ps = conn.prepareStatement(
                INSERT_EVENT_SQL,
                Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, ev.name);
            ps.setString(2, ev.venue);
            ps.setString(3, ev.date != null ? ev.date.toString() : null);
            ps.setString(4, LocalDate.now().toString());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        throw new IllegalStateException("Failed to generate Event ID");
    }

    private void insertSubEvents(
            Connection conn,
            int eventId,
            List<SubEventData> subEvents) throws Exception {

        try (PreparedStatement ps = conn.prepareStatement(INSERT_LOCATION_SQL)) {

            for (SubEventData se : subEvents) {

                String typesCsv = String.join(",", se.allowedParticipantTypes);

                ps.setInt(1, eventId);
                ps.setString(2, se.subEventName);
                ps.setString(3, se.locationName);
                ps.setString(4, typesCsv);
                ps.setString(5, se.entryFrom != null ? se.entryFrom.toString() : null);
                ps.setString(6, se.entryTill != null ? se.entryTill.toString() : null);

                ps.addBatch();
            }

            ps.executeBatch();
        }
    }

    private void ensureTablesExist(Connection conn) throws Exception {

        DatabaseMetaData meta = conn.getMetaData();

        if (!tableExists(meta, EVENTS_TABLE)) {
            try (PreparedStatement ps = conn.prepareStatement(CREATE_EVENTS_TABLE_SQL)) {
                ps.execute();
            }
        }

        if (!tableExists(meta, LOCATIONS_TABLE)) {
            try (PreparedStatement ps = conn.prepareStatement(CREATE_LOCATIONS_TABLE_SQL)) {
                ps.execute();
            }
        }
    }

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws Exception {
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }
}
