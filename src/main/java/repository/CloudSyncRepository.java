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

public class CloudSyncRepository {
    public static final String name = "trans";

    public static final String alter_table_sql = "ALTER TABLE [trans] ADD COLUMN [uploadstatus] INTEGER DEFAULT 0";

    public static final String fetch_table_sql = """
            SELECT *
            FROM [trans]
            WHERE uploadstatus = 0
            ORDER BY date_time ASC
            """;

    public static List<Map<String, Object>> fetchPendingTransUploads() throws SQLException {

        List<Map<String, Object>> out = new ArrayList<>();

        try (Connection c = AccessDb.getConnection()) {

            boolean uploadstatusexists = ensureUploadStatusColumn(c);
            if (!uploadstatusexists) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate(
                            "ALTER TABLE [trans] ADD COLUMN [uploadstatus] INTEGER DEFAULT 0");
                }
                DebugLog.d("uploadstatus column created with default=0");
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

    private static boolean ensureUploadStatusColumn(Connection c) throws SQLException {

        DatabaseMetaData md = c.getMetaData();
        boolean exists = false;

        try (ResultSet rs = md.getColumns(null, null, "trans", "uploadstatus")) {
            if (rs.next()) {
                exists = true;
            }
        }
        if (!exists) {
            try (ResultSet rs = md.getColumns(null, null, "TRANS", "UPLOADSTATUS")) {
                if (rs.next()) {
                    exists = true;
                }
            }
        }
        return exists;
    }

    public static void markUploadedExceptFailed(
            List<Map<String, Object>> sentRows,
            Set<String> failedCardUids) throws SQLException {

        if (sentRows == null || sentRows.isEmpty())
            return;

        try (Connection c = AccessDb.getConnection()) {
            c.setAutoCommit(false);

            String sql = """
                    UPDATE [trans]
                    SET uploadstatus = 1
                    WHERE carduid = ? AND date_time = ?
                    """;

            try (PreparedStatement ps = c.prepareStatement(sql)) {

                for (Map<String, Object> row : sentRows) {

                    String carduid = Objects.toString(row.get("carduid"), null);
                    String dateTime = Objects.toString(row.get("date_time"), null);

                    if (carduid == null || dateTime == null)
                        continue;

                    if (failedCardUids.contains(carduid))
                        continue;

                    ps.setString(1, carduid);
                    ps.setString(2, dateTime);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        }
    }
}
