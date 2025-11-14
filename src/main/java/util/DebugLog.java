package util;

public final class DebugLog {
    private DebugLog() {
    }

    public static void d(String fmt, Object... args) {
        try {
            String msg = args == null || args.length == 0 ? fmt : String.format(fmt, args);
            String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME);
            String thread = Thread.currentThread().getName();
            System.out.println("[" + ts + "][" + thread + "] " + msg);
            // optionally flush
            System.out.flush();
        } catch (Exception ignore) {
        }
    }

    public static void ex(Throwable t, String fmt, Object... args) {
        d(fmt, args);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }
}
