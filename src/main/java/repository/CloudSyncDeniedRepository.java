package repository;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import db.AccessDb;
import util.DebugLog;

public class CloudSyncDeniedRepository {

    public static final String name = "Attendance_Denied";

    public static final String alter_table_sql = "ALTER TABLE [Attendance_Denied] ADD COLUMN [uploadstatus] INTEGER DEFAULT 0";

    public static final String fetch_table_sql = """
            SELECT *
            FROM [Attendance_Denied]
            WHERE uploadstatus = 0
            ORDER BY attempted_date_time ASC
            """;

    /* ================= FETCH PENDING DENIED ================= */

    public static List<Map<String, Object>> fetchPendingDeniedUploads()
            throws SQLException {

        List<Map<String, Object>> out = new ArrayList<>();

        try (Connection c = AccessDb.getConnection()) {

            boolean uploadStatusExists = ensureUploadStatusColumn(c);

            if (!uploadStatusExists) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate(alter_table_sql);
                }
                DebugLog.d("Attendance_Denied.uploadstatus column created with default=0");
            }

            try (PreparedStatement ps = c.prepareStatement(fetch_table_sql);
                    ResultSet rs = ps.executeQuery()) {

                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    out.add(row);
                }
            }
        }
        return out;
    }

    /* ================= ENSURE uploadstatus COLUMN ================= */

    private static boolean ensureUploadStatusColumn(Connection c)
            throws SQLException {

        DatabaseMetaData md = c.getMetaData();
        boolean exists = false;

        try (ResultSet rs = md.getColumns(null, null, "Attendance_Denied", "uploadstatus")) {
            if (rs.next()) {
                exists = true;
            }
        }

        if (!exists) {
            try (ResultSet rs = md.getColumns(null, null, "ATTENDANCE_DENIED", "UPLOADSTATUS")) {
                if (rs.next()) {
                    exists = true;
                }
            }
        }
        return exists;
    }

    /* ================= MARK UPLOADED ================= */

    public static void markUploadedExceptFailed(
            List<Map<String, Object>> sentRows,
            Set<String> failedCardUids)
            throws SQLException {

        if (sentRows == null || sentRows.isEmpty())
            return;

        try (Connection c = AccessDb.getConnection()) {

            c.setAutoCommit(false);

            String sql = """
                    UPDATE [Attendance_Denied]
                    SET uploadstatus = 1
                    WHERE id = ?
                    """;

            try (PreparedStatement ps = c.prepareStatement(sql)) {

                for (Map<String, Object> row : sentRows) {

                    Object idObj = row.get("id");
                    String carduid = Objects.toString(row.get("carduid"), null);

                    if (idObj == null)
                        continue;

                    // if API says failed for this carduid, skip marking uploaded
                    if (carduid != null && failedCardUids.contains(carduid))
                        continue;

                    ps.setInt(1, ((Number) idObj).intValue());
                    ps.addBatch();
                }

                ps.executeBatch();
            }

            c.commit();
        }
    }
}
