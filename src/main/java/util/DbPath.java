package util;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;

public final class DbPath {
    private DbPath() {
    }

    public static Path getUserDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path base;
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            base = (localAppData != null)
                    ? Paths.get(localAppData)
                    : Paths.get(System.getProperty("user.home"), "AppData", "Local");
            return base.resolve("AttendanceApp").resolve("data");
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"),
                    "Library", "Application Support", "AttendanceApp", "data");
        } else {
            // linux/other
            return Paths.get(System.getProperty("user.home"),
                    ".local", "share", "AttendanceApp", "data");
        }
    }

    public static Path getWritableDbPath() {
        return getUserDataDir().resolve("bsd.accdb");
    }

    /** Copies the seed DB from classpath to the user data dir if missing. */
    public static void ensureSeedDbPresent() throws IOException {
        Path dir = getUserDataDir();
        Files.createDirectories(dir);
        Path dest = getWritableDbPath();
        if (Files.exists(dest))
            return;

        try (InputStream in = DbPath.class.getResourceAsStream("/db/bsd.accdb")) {
            if (in == null) {
                throw new FileNotFoundException("Seed database resource /db/bsd.accdb not found");
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
