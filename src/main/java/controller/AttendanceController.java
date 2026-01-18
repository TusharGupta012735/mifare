package controller;

import service.AttendanceService;

import java.util.Collections;
import java.util.List;

import dto.AttendanceEvent;
import dto.AttendanceRequest;
import dto.AttendanceResult;
import dto.ParticipantRow;

public class AttendanceController {

    private final AttendanceService service = new AttendanceService();

    /* ================= EVENTS ================= */

    public List<AttendanceEvent> getAllEvents() {
        try {
            return service.loadEvents();
        } catch (Exception ex) {
            System.out.println("[AttendanceController] Failed to load events");
            ex.printStackTrace();
            return Collections.emptyList();
        }
    }

    /* ================= READ-ONLY LOOKUP (OPTIONAL UI USE) ================= */

    /**
     * Lookup participant details using CardUID.
     * This is a helper for UI display ONLY.
     * Attendance marking does NOT depend on this.
     */
    public ParticipantRow lookupParticipantByCardUid(String cardUid) {

        if (cardUid == null || cardUid.isBlank()) {
            System.out.println("[AttendanceController] lookupParticipantByCardUid called with empty cardUid");
            return null;
        }

        try {
            return service.lookupParticipantByCardUid(cardUid);
        } catch (Exception ex) {
            System.out.println("[AttendanceController] Error during participant lookup for cardUid=" + cardUid);
            ex.printStackTrace();
            return null;
        }
    }

    /* ================= ATTENDANCE ================= */

    public AttendanceResult markAttendance(AttendanceRequest req) {

        AttendanceResult result = new AttendanceResult();;

        if (req == null) {
            System.out.println("[AttendanceController] markAttendance called with null request");
            return AttendanceResult.denied("Invalid request");
        }
        if (req.mode == null || req.mode.isBlank()) {
            req.mode = "ENTRY"; // safe default
        }

        System.out.println("[AttendanceController] markAttendance called"
                + " | cardUid=" + req.cardUid
                + " | eventId=" + req.eventId
                + " | event=" + req.eventName
                + " | location=" + req.location
                + " | mode=" + req.mode);

        if(req.mode.equals("EXIT")){
            System.out.println("[AttendanceController] markAttendance in EXIT mode");
            result = service.markExit(req);
        }
        else{
            System.out.println("[AttendanceController] markAttendance in ENTRY mode");
            result = service.markAttendance(req);
        }

        System.out.println("[AttendanceController] markAttendance result"
                + " | success=" + result.success
                + " | message=" + result.message);

        return result;
    }
}
