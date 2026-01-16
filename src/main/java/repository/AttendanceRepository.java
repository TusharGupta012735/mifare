package repository;

import db.AccessDb;
import dto.*;

import java.sql.*;
import java.util.*;

public class AttendanceRepository {

    /* ================= EVENTS ================= */

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
                        eventId, id -> new EventRow(id, eventName));

                if (location != null && !location.isBlank()) {
                    row.locations.add(location.trim());
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    /* ================= PARTICIPANT ================= */

    private static final String FETCH_PARTICIPANT_BY_CARD_UID_SQL = """
                SELECT
                    FullName,
                    BSGUID,
                    ParticipationType,
                    CardUID,
                    status
                FROM ParticipantsRecord
                WHERE CardUID = ?
            """;

    public ParticipantRow findParticipantByCardUid(String cardUid) throws Exception {

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(FETCH_PARTICIPANT_BY_CARD_UID_SQL)) {

            ps.setString(1, cardUid);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next())
                    return null;

                ParticipantRow p = new ParticipantRow();
                p.fullName = rs.getString("FullName");
                p.bsguid = rs.getString("BSGUID");
                p.participationType = rs.getString("ParticipationType");
                p.cardUid = rs.getString("CardUID");
                p.status = rs.getString("status");
                return p;
            }
        }
    }

    /* ================= EVENT RULE ================= */

    private static final String FETCH_EVENT_LOCATION_RULE_SQL = """
                SELECT
                    location_name,
                    allowed_participant_types,
                    entry_from,
                    entry_till
                FROM Event_Locations
                WHERE event_id = ? AND location_name = ?
            """;

    public EventLocationRule findEventLocationRule(int eventId, String location) throws Exception {

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(FETCH_EVENT_LOCATION_RULE_SQL)) {

            ps.setInt(1, eventId);
            ps.setString(2, location);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next())
                    return null;

                EventLocationRule rule = new EventLocationRule();
                rule.locationName = rs.getString("location_name");
                rule.allowedParticipantTypes = rs.getString("allowed_participant_types");
                rule.entryFrom = rs.getString("entry_from");
                rule.entryTill = rs.getString("entry_till");
                return rule;
            }
        }
    }

    /* ================= ATTENDANCE ================= */

    private static final String CHECK_DUPLICATE_SQL = """
                SELECT 1
                FROM trans
                WHERE bsguid = ?
                  AND event = ?
                  AND date_time LIKE ?
            """;

    public boolean existsAttendance(String bsguid, String eventName, String date) throws Exception {

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(CHECK_DUPLICATE_SQL)) {

            ps.setString(1, bsguid);
            ps.setString(2, eventName);
            ps.setString(3, date + "%");

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static final String FETCH_LAST_ATTENDANCE_SQL = """
                SELECT TOP 1 date_time
                FROM trans
                WHERE bsguid = ?
                  AND event = ?
                  AND location = ?
                ORDER BY date_time DESC
            """;

    public String fetchLastAttendanceTime(String bsguid, String eventName, String location) throws Exception {

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(FETCH_LAST_ATTENDANCE_SQL)) {

            ps.setString(1, bsguid);
            ps.setString(2, eventName);
            ps.setString(3, location);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("date_time") : null;
            }
        }
    }

    private static final String INSERT_ATTENDANCE_SQL = """
                INSERT INTO trans (
                    carduid,
                    bsguid,
                    fullname,
                    date_time,
                    location,
                    event,
                    uploadstatus
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    public void insertAttendance(AttendanceInsertRow row) throws Exception {

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(INSERT_ATTENDANCE_SQL)) {

            ps.setString(1, row.cardUid);
            ps.setString(2, row.bsguid);
            ps.setString(3, row.fullName);
            ps.setString(4, row.dateTime);
            ps.setString(5, row.location);
            ps.setString(6, row.eventName);
            ps.setInt(7, row.uploadStatus);

            ps.executeUpdate();
        }
    }

    /* ================= DENIED ================= */

    private static final String INSERT_DENIED_SQL = """
                INSERT INTO Attendance_Denied (
                    carduid,
                    bsguid,
                    full_name,
                    event_id,
                    event_name,
                    location,
                    attempted_date_time,
                    denial_reason,
                    participant_type,
                    entry_from,
                    entry_till,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public void insertDeniedAttendance(DeniedAttendanceRow row) throws Exception {

        try (Connection conn = AccessDb.getConnection();
                PreparedStatement ps = conn.prepareStatement(INSERT_DENIED_SQL)) {

            ps.setString(1, row.cardUid);
            ps.setString(2, row.bsguid);
            ps.setString(3, row.fullName);
            ps.setInt(4, row.eventId);
            ps.setString(5, row.eventName);
            ps.setString(6, row.location);
            ps.setString(7, row.attemptedDateTime);
            ps.setString(8, row.denialReason);
            ps.setString(9, row.participantType);
            ps.setString(10, row.entryFrom);
            ps.setString(11, row.entryTill);
            ps.setString(12, row.createdAt);

            ps.executeUpdate();
        }
    }
}
