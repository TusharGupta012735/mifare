package constants;

public final class DenialReason {

    private DenialReason() {
        // prevent instantiation
    }

    /* ================= TIME / EVENT ================= */

    public static final String TIME_WINDOW_VIOLATION = "TIME_WINDOW_VIOLATION";

    public static final String EVENT_NOT_SELECTED = "EVENT_NOT_SELECTED";

    public static final String LOCATION_NOT_SELECTED = "LOCATION_NOT_SELECTED";

    /* ================= PARTICIPANT ================= */

    public static final String PARTICIPANT_NOT_FOUND = "PARTICIPANT_NOT_FOUND";

    public static final String PARTICIPANT_TYPE_MISMATCH = "PARTICIPANT_TYPE_MISMATCH";

    public static final String PARTICIPANT_INACTIVE = "PARTICIPANT_INACTIVE";

    public static final String CARD_NOT_REGISTERED = "CARD_NOT_REGISTERED";

    /* ================= ATTENDANCE ================= */

    public static final String DUPLICATE_ATTENDANCE = "DUPLICATE_ATTENDANCE";

    /* ================= SYSTEM ================= */

    public static final String INVALID_CARD_DATA = "INVALID_CARD_DATA";

    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    public static final String EXIT_WITHOUT_ENTRY = "EXIT_WITHOUT_ENTRY";

}
