package repository;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;

import db.AccessDb;

public class EventFormRepository {

    private static final String TABLE_NAME = "events";

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE Events (
                id AUTOINCREMENT PRIMARY KEY,
                name TEXT(255) NOT NULL,
                venue TEXT(255),
                event_date TEXT(20),
                participant_type TEXT(50),
                custom_participant_type TEXT(100),
                entry_from TEXT(10),
                entry_till TEXT(10),
                created_at TEXT(30)
            )
            """;

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO Events (
                name,
                venue,
                event_date,
                participant_type,
                custom_participant_type,
                entry_from,
                entry_till
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    public void insertEvent(
            String name,
            String venue,
            LocalDate date,
            String participantType,
            String customParticipantType,
            LocalTime entryFrom,
            LocalTime entryTill) throws Exception {

        try (Connection conn = AccessDb.getConnection()) {
            ensureTableExists(conn);

            try (PreparedStatement ps = conn.prepareStatement(INSERT_EVENT_SQL)) {

                ps.setString(1, name);
                ps.setString(2, venue);
                ps.setString(3, date != null ? date.toString() : null);
                ps.setString(4, participantType);
                ps.setString(5, customParticipantType);
                ps.setString(6, entryFrom != null ? entryFrom.toString() : null);
                ps.setString(7, entryTill != null ? entryTill.toString() : null);

                ps.executeUpdate();
            }

        }
    }

    private void ensureTableExists(Connection conn) throws Exception {

        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet rs = meta.getTables(null, null, TABLE_NAME, null)) {
            if (rs.next()) {
                return;
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(CREATE_TABLE_SQL)) {
            ps.execute();
        }
    }
}
