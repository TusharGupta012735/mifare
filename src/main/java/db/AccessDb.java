package db;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.*;
import java.util.stream.Collectors;

public class AccessDb {

    // -------------------- small helpers (keys & parsing) --------------------

    private static String firstNonBlank(Map<String, String> data, String... keys) {
        for (String k : keys) {
            String v = data.get(k);
            if (v != null && !v.trim().isEmpty())
                return v.trim();
        }
        return null;
    }

    // yyyy-MM-dd (ISO) or null
    private static String normalizeDobOrNull(String raw) {
        if (raw == null)
            return null;
        String s = raw.trim();
        if (s.isEmpty())
            return null;
        String[] patterns = {
                "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy",
                "dd.MM.yyyy", "d/M/yyyy", "d-M-yyyy", "M/d/yyyy"
        };
        for (String p : patterns) {
            try {
                var fmt = java.time.format.DateTimeFormatter.ofPattern(p);
                var d = java.time.LocalDate.parse(s, fmt);
                return d.toString();
            } catch (Exception ignore) {
            }
        }
        try {
            return java.time.LocalDate.parse(s).toString();
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String tryNormalizeDob(String raw) {
        String iso = normalizeDobOrNull(raw);
        return iso == null ? "" : iso;
    }

    private static String normalize(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        while (t.endsWith(","))
            t = t.substring(0, t.length() - 1).trim();
        return t.isEmpty() ? null : t;
    }

    // -------------------- phone normalization: +E.164 (India) --------------------

    // Examples:
    // "9.186E11" -> "+918638281911"
    // "0918638281911" -> "+918638281911"
    // "8638281911" -> "+918638281911"
    // "91918638281911" -> "+918638281911"
    private static String normalizePhoneE164IN(String raw) {
        if (raw == null)
            return "";
        String d = raw.replaceAll("\\D+", ""); // only digits

        // remove 00/0 prefixes
        while (d.startsWith("00"))
            d = d.substring(2);
        if (d.startsWith("0"))
            d = d.substring(1);

        // collapse accidental 9191... once
        if (d.startsWith("9191") && d.length() == 13)
            d = d.substring(2);

        if (d.length() == 12 && d.startsWith("91"))
            return "+" + d; // already with country
        if (d.length() == 10)
            return "+91" + d; // add country

        if (d.startsWith("91") && d.length() > 12)
            d = d.substring(0, 12);
        return d.isEmpty() ? "" : "+" + d;
    }

    private static String readPhoneE164IN(ResultSet rs, String... cols) {
        for (String c : cols) {
            try {
                String s = rs.getString(c);
                if (s != null && !s.isBlank())
                    return normalizePhoneE164IN(s);
            } catch (SQLException ignore) {
            }
            try {
                java.math.BigDecimal bd = rs.getBigDecimal(c);
                if (bd != null)
                    return normalizePhoneE164IN(bd.toPlainString());
            } catch (SQLException ignore) {
            }
        }
        return "";
    }

    // -------------------- writable DB location & provisioning --------------------

    private static Path getUserDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = (localAppData != null)
                    ? Paths.get(localAppData)
                    : Paths.get(System.getProperty("user.home"), "AppData", "Local");
            return base.resolve("AttendanceApp").resolve("data");
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"),
                    "Library", "Application Support", "AttendanceApp", "data");
        } else {
            return Paths.get(System.getProperty("user.home"),
                    ".local", "share", "AttendanceApp", "data");
        }
    }

    private static Path getWritableDbPath() {
        return getUserDataDir().resolve("bsd.accdb");
    }

    private static void ensureSeedDbPresent() throws IOException {
        Path dir = getUserDataDir();
        Files.createDirectories(dir);
        Path dest = getWritableDbPath();
        if (Files.exists(dest))
            return;

        try (InputStream in = AccessDb.class.getResourceAsStream("/db/bsd.accdb")) {
            if (in == null)
                throw new FileNotFoundException("Seed database /db/bsd.accdb not found on classpath");
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String buildUrl(Path dbFile) {
        return "jdbc:ucanaccess://" + dbFile.toAbsolutePath()
                + ";memory=false;immediatelyReleaseResources=true";
    }

    public static Connection getConnection() throws SQLException {
        try {
            ensureSeedDbPresent();
        } catch (IOException io) {
            throw new SQLException("Failed to provision seed DB: " + io.getMessage(), io);
        }
        return DriverManager.getConnection(buildUrl(getWritableDbPath()));
    }

    // Optional: handy to print where we’re writing
    public static Path getActiveDbPath() throws SQLException {
        try {
            ensureSeedDbPresent();
        } catch (IOException e) {
            throw new SQLException(e);
        }
        return getWritableDbPath();
    }

    // -------------------- FETCH (alphabetical) --------------------

    /**
     * Fetch ParticipantsRecord filtered by state + excel_category.
     * If onlyStatusF==true, keeps only rows where status='F' (case-insensitive).
     * Sorted by FullName ASC.
     */
    public static List<Map<String, String>> fetchParticipantsByStateAndCategory(
            String state, String excelCategory, boolean onlyStatusF) throws SQLException {

        try (Connection c = getConnection()) {
            DatabaseMetaData md = c.getMetaData();

            // table exists?
            boolean tableExists = false;
            try (ResultSet rs = md.getTables(null, null, "ParticipantsRecord", new String[] { "TABLE" })) {
                if (rs.next())
                    tableExists = true;
            }
            if (!tableExists) {
                try (ResultSet rs = md.getTables(null, null, "PARTICIPANTSRECORD", new String[] { "TABLE" })) {
                    if (rs.next())
                        tableExists = true;
                }
            }
            if (!tableExists)
                throw new SQLException("ParticipantsRecord table not found.");

            // discover columns
            Set<String> cols = new HashSet<>();
            try (ResultSet rs = md.getColumns(null, null, "ParticipantsRecord", "%")) {
                while (rs.next()) {
                    String cn = rs.getString("COLUMN_NAME");
                    if (cn != null)
                        cols.add(cn.toUpperCase(Locale.ROOT));
                }
            }

            final String EXCEL_COL = cols.contains("EXCEL_CATEGORY") ? "[excel_category]"
                    : cols.contains("EXCELCATEGORY") ? "[ExcelCategory]" : null;

            StringBuilder sql = new StringBuilder("SELECT * FROM [ParticipantsRecord] WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (state != null && !state.trim().isEmpty()) {
                sql.append(" AND UCASE([BSGState]) LIKE UCASE(?)");
                params.add("%" + state.trim() + "%");
            }
            if (excelCategory != null && !excelCategory.trim().isEmpty()) {
                if (EXCEL_COL == null) {
                    sql.append(" AND 1=0");
                } else {
                    sql.append(" AND UCASE(").append(EXCEL_COL).append(") LIKE UCASE(?)");
                    params.add("%" + excelCategory.trim() + "%");
                }
            }
            if (onlyStatusF)
                sql.append(" AND UCASE([status]) = 'F'");

            // always alphabetical
            sql.append(" ORDER BY [FullName] ASC");

            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++)
                    ps.setString(i + 1, params.get(i).toString());

                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, String>> out = new ArrayList<>();

                    java.util.function.Function<String, String> get = col -> {
                        try {
                            return Optional.ofNullable(rs.getString(col)).orElse("").trim();
                        } catch (SQLException e) {
                            return "";
                        }
                    };
                    java.util.function.BiFunction<String, String, String> get2 = (a, b) -> {
                        String v = get.apply(a);
                        return !v.isEmpty() ? v : get.apply(b);
                    };

                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();

                        row.put("FullName", get2.apply("FullName", "FULLNAME"));
                        row.put("BSGUID", get2.apply("BSGUID", "BSGUID"));
                        row.put("ParticipationType", get2.apply("ParticipationType", "PARTICIPATIONTYPE"));
                        row.put("bsgDistrict", get2.apply("bsgDistrict", "BSGDISTRICT"));
                        row.put("Email", get2.apply("Email", "EMAIL"));
                        row.put("phoneNumber", readPhoneE164IN(rs, "phoneNumber", "PHONENUMBER"));
                        row.put("bsgState", get2.apply("bsgState", "BSGSTATE"));

                        String memberType = get2.apply("memberType", "MEMBERTYPE");
                        String unitName = get2.apply("unitName", "UNITNAME");
                        String rank = get2.apply("rank_or_section", "RANK_OR_SECTION");

                        row.put("memberType", memberType);
                        row.put("memberTyp", memberType);
                        row.put("unitName", unitName);
                        row.put("unitNam", unitName);
                        row.put("rank_or_section", rank);

                        // DOB -> yyyy-MM-dd
                        String dobIso = "";
                        try {
                            java.sql.Date d = null;
                            try {
                                d = rs.getDate("dateOfBirth");
                            } catch (SQLException ignore) {
                            }
                            if (d == null) {
                                try {
                                    d = rs.getDate("DATEOFBIRTH");
                                } catch (SQLException ignore) {
                                }
                            }
                            if (d != null) {
                                dobIso = d.toLocalDate().toString();
                            } else {
                                String dobText = get2.apply("dateOfBirth", "DATEOFBIRTH");
                                dobIso = tryNormalizeDob(dobText);
                            }
                        } catch (Exception ignore) {
                        }
                        row.put("dateOfBirth", dobIso);
                        row.put("dataOfBirth", dobIso);

                        row.put("age", get2.apply("age", "AGE"));

                        // CSV for NFC
                        String csv = String.join(",",
                                Arrays.asList(
                                        row.getOrDefault("FullName", ""),
                                        row.getOrDefault("BSGUID", ""),
                                        row.getOrDefault("ParticipationType", ""),
                                        row.getOrDefault("bsgDistrict", ""),
                                        row.getOrDefault("Email", ""),
                                        row.getOrDefault("phoneNumber", ""),
                                        row.getOrDefault("bsgState", ""),
                                        row.getOrDefault("memberType", ""),
                                        row.getOrDefault("unitName", ""),
                                        row.getOrDefault("rank_or_section", ""),
                                        row.getOrDefault("dateOfBirth", ""),
                                        row.getOrDefault("age", "")));
                        row.put("__CSV__", csv);

                        out.add(row);
                    }
                    return out;
                }
            }
        }
    }

    // -------------------- INSERT + robust typing --------------------

    /**
     * Insert into ParticipantsWrite and then update ParticipantsRecord (status='T',
     * CardUID).
     * Returns generated Id or -1.
     */
    public static long insertAttendee(Map<String, String> data, String cardUid) throws SQLException {
        List<String> expected = Arrays.asList(
                "FullName", "BSGUID", "ParticipationType", "BSGDistrict",
                "Email", "PhoneNumber", "BSGState", "MemberType",
                "UnitName", "RankOrSection", "DateOfBirth", "Age",
                "CardUID", "CreatedAt");

        try (Connection c = getConnection()) {
            c.setAutoCommit(false);

            try {
                DatabaseMetaData md = c.getMetaData();

                // discover actual columns + jdbc types
                Set<String> actual = new HashSet<>();
                Map<String, Integer> colType = new HashMap<>();
                try (ResultSet rs = md.getColumns(null, null, "ParticipantsWrite", "%")) {
                    while (rs.next()) {
                        String name = rs.getString("COLUMN_NAME");
                        if (name != null) {
                            String up = name.toUpperCase(Locale.ROOT);
                            actual.add(up);
                            colType.put(up, rs.getInt("DATA_TYPE")); // java.sql.Types
                        }
                    }
                }

                List<String> cols = new ArrayList<>();
                List<Object> vals = new ArrayList<>();

                for (String col : expected) {
                    String up = col.toUpperCase(Locale.ROOT);
                    if (!actual.contains(up))
                        continue;

                    switch (col) {
                        case "DateOfBirth": {
                            // accept both dateOfBirth and dataOfBirth
                            String iso = normalizeDobOrNull(firstNonBlank(data, "dateOfBirth", "dataOfBirth"));
                            int jt = colType.getOrDefault(up, Types.DATE);
                            if (iso == null) {
                                vals.add(null); // will bind NULL with jt
                            } else {
                                if (jt == Types.DATE || jt == Types.TIMESTAMP || jt == Types.TIMESTAMP_WITH_TIMEZONE) {
                                    try {
                                        vals.add(java.sql.Date.valueOf(iso));
                                    } catch (IllegalArgumentException e) {
                                        vals.add(null);
                                    }
                                } else {
                                    vals.add(iso); // TEXT column
                                }
                            }
                            cols.add("[" + col + "]");
                            continue;
                        }
                        case "CreatedAt":
                            vals.add(java.sql.Timestamp.from(Instant.now()));
                            cols.add("[" + col + "]");
                            continue;
                        case "CardUID":
                            vals.add(normalize(cardUid));
                            cols.add("[" + col + "]");
                            continue;
                        case "PhoneNumber": {
                            String raw = firstNonBlank(data, "phoneNumber", "PhoneNumber");
                            vals.add(normalizePhoneE164IN(raw));
                            cols.add("[" + col + "]");
                            continue;
                        }
                        case "RankOrSection": {
                            String rank = firstNonBlank(data, "rank_or_section", "RankOrSection", "rankOrSection");
                            vals.add(rank == null ? null : rank.trim());
                            cols.add("[" + col + "]");
                            continue;
                        }
                        default: {
                            String mapKey;
                            switch (col) {
                                case "FullName":
                                    mapKey = "FullName";
                                    break;
                                case "BSGUID":
                                    mapKey = "BSGUID";
                                    break;
                                case "ParticipationType":
                                    mapKey = "ParticipationType";
                                    break;
                                case "BSGDistrict":
                                    mapKey = "bsgDistrict";
                                    break;
                                case "Email":
                                    mapKey = "Email";
                                    break;
                                case "BSGState":
                                    mapKey = "bsgState";
                                    break;
                                case "MemberType":
                                    mapKey = "memberTyp";
                                    break;
                                case "UnitName":
                                    mapKey = "unitNam";
                                    break;
                                case "Age":
                                    mapKey = "age";
                                    break;
                                default:
                                    mapKey = col;
                            }
                            vals.add(normalize(data.get(mapKey)));
                            cols.add("[" + col + "]");
                        }
                    }
                }

                if (cols.isEmpty())
                    throw new SQLException("No insertable columns found in ParticipantsWrite.");

                String placeholders = String.join(",", Collections.nCopies(cols.size(), "?"));
                String sql = "INSERT INTO [ParticipantsWrite] (" + String.join(",", cols) + ") VALUES (" + placeholders
                        + ")";

                long generatedId = -1;
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    for (int i = 0; i < vals.size(); i++) {
                        Object v = vals.get(i);
                        int idx = i + 1;
                        String colName = cols.get(i).replace("[", "").replace("]", ""); // exact name from table
                        int jt = colType.getOrDefault(colName.toUpperCase(Locale.ROOT), Types.VARCHAR);

                        if (v == null) {
                            if (jt == Types.TIMESTAMP_WITH_TIMEZONE)
                                jt = Types.TIMESTAMP;
                            ps.setNull(idx, jt);
                        } else if (v instanceof java.sql.Date d) {
                            ps.setDate(idx, d);
                        } else if (v instanceof java.sql.Timestamp ts) {
                            ps.setTimestamp(idx, ts);
                        } else {
                            // if TEXT column, always setString; if DATE column but value is String ISO,
                            // coerce
                            if (jt == Types.DATE && v instanceof String s) {
                                try {
                                    ps.setDate(idx, java.sql.Date.valueOf(s));
                                } catch (Exception e) {
                                    ps.setNull(idx, Types.DATE);
                                }
                            } else {
                                ps.setString(idx, v.toString());
                            }
                        }
                    }

                    int affected = ps.executeUpdate();
                    if (affected == 0) {
                        c.rollback();
                        return -1;
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next())
                            generatedId = keys.getLong(1);
                    }
                }

                // then update ParticipantsRecord
                boolean updated = updateParticipantsRecord(c, data, cardUid);
                if (!updated) {
                    System.out.println("WARN: no matching ParticipantsRecord updated for BSGUID/FullName/Phone.");
                } else {
                    System.out.println("DEBUG: ParticipantsRecord updated with status='T' and CardUID.");
                }

                c.commit();
                return generatedId;

            } catch (SQLException ex) {
                try {
                    c.rollback();
                } catch (Exception ignored) {
                }
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // -------------------- POST-INSERT UPDATE --------------------

    private static boolean updateParticipantsRecord(Connection c, Map<String, String> data, String cardUid)
            throws SQLException {

        String bsguid = normalize(data.get("BSGUID"));
        String fullName = normalize(data.get("FullName"));
        // accept both keys for DOB
        String dobStr = normalize(firstNonBlank(data, "dateOfBirth", "dataOfBirth"));
        // normalize phone to the same format we store
        String phone = normalizePhoneE164IN(firstNonBlank(data, "phoneNumber", "PhoneNumber"));

        DatabaseMetaData md = c.getMetaData();
        boolean tableExists = false;
        try (ResultSet rs = md.getTables(null, null, "ParticipantsRecord", new String[] { "TABLE" })) {
            if (rs.next())
                tableExists = true;
        }
        if (!tableExists) {
            try (ResultSet rs2 = md.getTables(null, null, "PARTICIPANTSRECORD", new String[] { "TABLE" })) {
                if (rs2.next())
                    tableExists = true;
            }
        }
        if (!tableExists)
            throw new SQLException("ParticipantsRecord table not found in DB.");

        // 1) BSGUID
        if (bsguid != null) {
            String upd = "UPDATE [ParticipantsRecord] SET [status] = ?, [CardUID] = ? WHERE [BSGUID] = ?";
            try (PreparedStatement ps = c.prepareStatement(upd)) {
                ps.setString(1, "T");
                ps.setString(2, normalize(cardUid));
                ps.setString(3, bsguid);
                if (ps.executeUpdate() > 0)
                    return true;
            }
        }

        // 2) FullName + DateOfBirth
        if (fullName != null && dobStr != null) {
            java.sql.Date dobSql = null;
            try {
                dobSql = java.sql.Date.valueOf(dobStr);
            } catch (IllegalArgumentException ignored) {
            }
            if (dobSql != null) {
                String upd = "UPDATE [ParticipantsRecord] SET [status] = ?, [CardUID] = ? WHERE [FullName] = ? AND [DateOfBirth] = ?";
                try (PreparedStatement ps = c.prepareStatement(upd)) {
                    ps.setString(1, "T");
                    ps.setString(2, normalize(cardUid));
                    ps.setString(3, fullName);
                    ps.setDate(4, dobSql);
                    if (ps.executeUpdate() > 0)
                        return true;
                }
            } else {
                String upd = "UPDATE [ParticipantsRecord] SET [status] = ?, [CardUID] = ? WHERE [FullName] = ?";
                try (PreparedStatement ps = c.prepareStatement(upd)) {
                    ps.setString(1, "T");
                    ps.setString(2, normalize(cardUid));
                    ps.setString(3, fullName);
                    if (ps.executeUpdate() > 0)
                        return true;
                }
            }
        }

        // 3) PhoneNumber
        if (phone != null && !phone.isBlank()) {
            String upd = "UPDATE [ParticipantsRecord] SET [status] = ?, [CardUID] = ? WHERE [PhoneNumber] = ?";
            try (PreparedStatement ps = c.prepareStatement(upd)) {
                ps.setString(1, "T");
                ps.setString(2, normalize(cardUid));
                ps.setString(3, phone);
                if (ps.executeUpdate() > 0)
                    return true;
            }
        }

        return false;
    }

    // -------------------- CLI helpers (optional) --------------------

    public static void main(String[] args) {
        String cmd = args.length > 0 ? args[0].toLowerCase() : "help";
        try {
            switch (cmd) {
                case "list" -> listTables();
                case "describe" -> {
                    if (args.length < 2)
                        System.out.println("Usage: describe <TableName>");
                    else
                        describeTable(args[1]);
                }
                case "create-participants" -> createParticipantsWriteIfMissing();
                case "test" -> testConnection();
                default -> {
                    System.out.println("AccessDb helper");
                    System.out.println("Usage:");
                    System.out.println("  run main() with args: test | list | describe <tbl> | create-participants");
                }
            }
        } catch (Exception ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    public static void listTables() {
        try (Connection c = getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getTables(null, null, "%", new String[] { "TABLE", "VIEW" })) {
                System.out.println("Tables/Views found in: " + getWritableDbPath());
                boolean any = false;
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("TABLE_NAME") + " (" + rs.getString("TABLE_TYPE") + ")");
                    any = true;
                }
                if (!any)
                    System.out.println("  (no tables/views found)");
            }
        } catch (SQLException ex) {
            System.out.println("Failed to list tables: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    public static void describeTable(String tableName) {
        try (Connection c = getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            boolean any = false;
            try (ResultSet rs = md.getColumns(null, null, tableName, "%")) {
                while (rs.next()) {
                    if (!any)
                        System.out.println("Columns for table " + tableName + ":");
                    System.out.printf(" - %s : %s(%d)%n",
                            rs.getString("COLUMN_NAME"),
                            rs.getString("TYPE_NAME"),
                            rs.getInt("COLUMN_SIZE"));
                    any = true;
                }
            }
            if (!any) {
                try (ResultSet rs2 = md.getColumns(null, null, tableName.toUpperCase(), "%")) {
                    while (rs2.next()) {
                        if (!any)
                            System.out.println("Columns for table " + tableName.toUpperCase() + ":");
                        System.out.printf(" - %s : %s(%d)%n",
                                rs2.getString("COLUMN_NAME"),
                                rs2.getString("TYPE_NAME"),
                                rs2.getInt("COLUMN_SIZE"));
                        any = true;
                    }
                }
            }
            if (!any)
                System.out.println("  (no columns found or table does not exist: " + tableName + ")");
        } catch (SQLException ex) {
            System.out.println("Failed to describe table: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    /** Create ParticipantsWrite if missing (without CardUID). */
    public static void createParticipantsWriteIfMissing() {
        String target = "ParticipantsWrite";
        try (Connection c = getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            boolean exists = false;
            try (ResultSet rs = md.getTables(null, null, target, new String[] { "TABLE" })) {
                if (rs.next())
                    exists = true;
            }
            if (!exists) {
                try (ResultSet rs2 = md.getTables(null, null, target.toUpperCase(), new String[] { "TABLE" })) {
                    if (rs2.next())
                        exists = true;
                }
            }
            if (exists) {
                System.out.println("Table '" + target + "' already exists - no action taken.");
                describeTable(target);
                return;
            }

            System.out.println("Creating table '" + target + "'...");
            String createSql = "CREATE TABLE [ParticipantsWrite] ("
                    + "[Id] AUTOINCREMENT PRIMARY KEY, "
                    + "[FullName] TEXT(255), "
                    + "[BSGUID] TEXT(255), "
                    + "[ParticipationType] TEXT(100), "
                    + "[BSGDistrict] TEXT(100), "
                    + "[Email] TEXT(255), "
                    + "[PhoneNumber] TEXT(50), "
                    + "[BSGState] TEXT(100), "
                    + "[MemberType] TEXT(100), "
                    + "[UnitName] TEXT(255), "
                    + "[RankOrSection] TEXT(100), "
                    + "[DateOfBirth] DATETIME, "
                    + "[Age] TEXT(10), "
                    + "[CreatedAt] DATETIME"
                    + ")";
            try (Statement st = c.createStatement()) {
                st.executeUpdate(createSql);
                System.out.println("Table created successfully.");
                describeTable(target);
            } catch (SQLException ex) {
                System.out.println("Failed to create table: " + ex.getMessage());
                ex.printStackTrace(System.out);
            }
        } catch (SQLException ex) {
            System.out.println("DB error: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    private static void testConnection() {
        System.out.println("Attempting to connect to the Access database at: " + getWritableDbPath());
        try (Connection c = getConnection()) {
            System.out.println("Connection successful.");
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM [ParticipantsWrite]")) {
                if (rs.next()) {
                    System.out.println("ParticipantsWrite table row count: " + rs.getLong(1));
                } else {
                    System.out.println("ParticipantsWrite table query returned no rows.");
                }
            } catch (SQLException qex) {
                System.out.println("Could not query ParticipantsWrite table. Maybe the table doesn't exist.");
                System.out.println("SQLException: " + qex.getMessage());
            }
        } catch (SQLException ex) {
            System.out.println("Failed to connect: " + ex.getMessage());
            Throwable root = ex.getCause();
            if (root != null)
                System.out.println("Cause: " + root.getMessage());
            ex.printStackTrace(System.out);
            System.out.println();
            System.out.println("Checklist:");
            System.out.println("- Is the seed DB at src/main/resources/db/bsd.accdb?");
            System.out.println("- Are you running with Maven so UCanAccess is on the classpath?");
            System.out.println("- Try: mvn javafx:run -Pwin (or -Pmac / -Plinux)");
        }
    }

    /** Distinct BSGState values from ParticipantsRecord, alphabetically. */
    public static List<String> fetchDistinctStates() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = getConnection()) {
            // Some rows may have nulls/empties; ignore them. Use UCASE to fold case
            // variants.
            String sql = "SELECT DISTINCT UCASE([BSGState]) AS v " +
                    "FROM [ParticipantsRecord] " +
                    "WHERE [BSGState] IS NOT NULL AND TRIM([BSGState]) <> '' " +
                    "ORDER BY UCASE([BSGState])";
            try (PreparedStatement ps = c.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.isBlank())
                        out.add(v.trim());
                }
            }
        }
        return out;
    }

    /**
     * Distinct excel_category (handles excel_category or ExcelCategory),
     * alphabetically.
     */
    public static List<String> fetchDistinctExcelCategories() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = getConnection()) {
            // detect column name
            DatabaseMetaData md = c.getMetaData();
            boolean hasLower = false, hasCamel = false;
            try (ResultSet rs = md.getColumns(null, null, "ParticipantsRecord", "%")) {
                while (rs.next()) {
                    String cn = rs.getString("COLUMN_NAME");
                    if (cn == null)
                        continue;
                    String up = cn.toUpperCase(Locale.ROOT);
                    if (up.equals("EXCEL_CATEGORY"))
                        hasLower = true;
                    if (up.equals("EXCELCATEGORY"))
                        hasCamel = true;
                }
            }
            String col = hasLower ? "[excel_category]" : (hasCamel ? "[ExcelCategory]" : null);
            if (col == null)
                return out; // no such column in this DB

            String sql = "SELECT DISTINCT UCASE(" + col + ") AS v " +
                    "FROM [ParticipantsRecord] " +
                    "WHERE " + col + " IS NOT NULL AND TRIM(" + col + ") <> '' " +
                    "ORDER BY UCASE(" + col + ")";
            try (PreparedStatement ps = c.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.isBlank())
                        out.add(v.trim());
                }
            }
        }
        return out;
    }
    // === Add into db.AccessDb ===

    public static int bulkImportParticipantsRecord(String excelCategory, List<Map<String, String>> rows)
            throws SQLException {
        if (rows == null || rows.isEmpty())
            return 0;

        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {

                int affected = 0;

                // Prepared SELECT to check if row exists
                String selectSql = "SELECT SlNo FROM ParticipantsRecord WHERE UCASE(FullName)=UCASE(?) AND UCASE(BSGUID)=UCASE(?)";
                try (PreparedStatement select = c.prepareStatement(selectSql)) {

                    for (Map<String, String> r : rows) {

                        String name = safe(r, "FullName");
                        String bsguid = safe(r, "BSGUID");

                        if (name == null || bsguid == null || name.isEmpty() || bsguid.isEmpty())
                            continue; // skip incomplete rows

                        // Check if exists
                        Long existingId = null;
                        select.setString(1, name);
                        select.setString(2, bsguid);
                        try (ResultSet rs = select.executeQuery()) {
                            if (rs.next())
                                existingId = rs.getLong(1);
                        }

                        // Normalize / convert values
                        String phone = normalizePhoneE164IN(safe(r, "phoneNumber"));
                        String dobIso = normalizeDobOrNull(safe(r, "dateOfBirth"));

                        if (existingId != null) {
                            // ---------- UPDATE ----------
                            String update = """
                                    UPDATE ParticipantsRecord SET
                                      ParticipationType = ?, bsgDistrict = ?, Email = ?, phoneNumber = ?,
                                      bsgState = ?, memberType = ?, unitName = ?, rank_or_section = ?,
                                      dateOfBirth = ?, age = ?, excel_category = ?, status = 'F'
                                    WHERE SlNo = ?
                                    """;
                            try (PreparedStatement ps = c.prepareStatement(update)) {
                                int i = 1;
                                ps.setString(i++, safe(r, "ParticipationType"));
                                ps.setString(i++, safe(r, "bsgDistrict"));
                                ps.setString(i++, safe(r, "Email"));
                                ps.setString(i++, phone);
                                ps.setString(i++, safe(r, "bsgState"));
                                ps.setString(i++, safe(r, "memberType"));
                                ps.setString(i++, safe(r, "unitName"));
                                ps.setString(i++, safe(r, "rank_or_section"));
                                if (dobIso != null)
                                    ps.setDate(i++, java.sql.Date.valueOf(dobIso));
                                else
                                    ps.setNull(i++, java.sql.Types.DATE);
                                ps.setString(i++, safe(r, "age"));
                                ps.setString(i++, excelCategory);
                                ps.setLong(i++, existingId);
                                affected += ps.executeUpdate();
                            }

                        } else {
                            // ---------- INSERT ----------
                            String insert = """
                                    INSERT INTO ParticipantsRecord
                                    (FullName, BSGUID, ParticipationType, bsgDistrict, Email, phoneNumber,
                                     bsgState, memberType, unitName, rank_or_section, dateOfBirth,
                                     age, excel_category, status)
                                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?, 'F')
                                    """;
                            try (PreparedStatement ps = c.prepareStatement(insert)) {
                                int i = 1;
                                ps.setString(i++, name);
                                ps.setString(i++, bsguid);
                                ps.setString(i++, safe(r, "ParticipationType"));
                                ps.setString(i++, safe(r, "bsgDistrict"));
                                ps.setString(i++, safe(r, "Email"));
                                ps.setString(i++, phone);
                                ps.setString(i++, safe(r, "bsgState"));
                                ps.setString(i++, safe(r, "memberType"));
                                ps.setString(i++, safe(r, "unitName"));
                                ps.setString(i++, safe(r, "rank_or_section"));
                                if (dobIso != null)
                                    ps.setDate(i++, java.sql.Date.valueOf(dobIso));
                                else
                                    ps.setNull(i++, java.sql.Types.DATE);
                                ps.setString(i++, safe(r, "age"));
                                ps.setString(i++, excelCategory);
                                affected += ps.executeUpdate();
                            }
                        }
                    }
                }

                c.commit();
                return affected;

            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
    // Helpers
    private static String safe(Map<String, String> r, String k) {
        if (r == null)
            return null;
        String v = r.get(k);
        return (v == null ? null : v.trim());
    }
}
