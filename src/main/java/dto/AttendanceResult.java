package dto;

public class AttendanceResult {
    public final boolean success;
    public final String message;

    private AttendanceResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AttendanceResult success() {
        return new AttendanceResult(true, "Attendance marked");
    }

    public static AttendanceResult denied(String msg) {
        return new AttendanceResult(false, msg);
    }
}
