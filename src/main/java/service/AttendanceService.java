package service;

import constants.DenialReason;
import dto.*;
import repository.AttendanceRepository;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AttendanceService {

    private final AttendanceRepository repo = new AttendanceRepository();

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // load events

    public List<AttendanceEvent> loadEvents() throws Exception {

        List<EventRow> rows = repo.fetchAllEvents();
        List<AttendanceEvent> result = new ArrayList<>();

        for (EventRow r : rows) {
            result.add(new AttendanceEvent(r.id, r.name, r.locations));
        }
        return result;
    }

    /* ================= NEW ATTENDANCE FLOW ================= */

    public AttendanceResult markAttendance(AttendanceRequest req) {

        LocalDateTime now = LocalDateTime.now();
        String nowTs = now.format(DT_FMT);

        try {

            // check for event and location

            if (req.eventId <= 0) {
                deny(req, DenialReason.EVENT_NOT_SELECTED, null, null);
                return AttendanceResult.denied("Event not selected");
            }

            if (req.location == null || req.location.isBlank()) {
                deny(req, DenialReason.LOCATION_NOT_SELECTED, null, null);
                return AttendanceResult.denied("Location not selected");
            }

            String cleanBsguid = req.bsguid == null ? null : req.bsguid.trim();
            if (cleanBsguid == null || cleanBsguid.isBlank()) {
                deny(req, DenialReason.PARTICIPANT_NOT_FOUND, null, null);
                return AttendanceResult.denied("Participant not found");
            }
            System.out.println("DEBUG BSGUID=[" + cleanBsguid + "]");

            // check for participant

            ParticipantRow p = repo.findParticipantByBSGUID(cleanBsguid);
            if (p == null) {
                deny(req, DenialReason.PARTICIPANT_NOT_FOUND, null, null);
                return AttendanceResult.denied("Participant not found");
            }

            // check for event location rule

            EventLocationRule rule = repo.findEventLocationRule(req.eventId, req.location);

            if (rule == null) {
                deny(req, DenialReason.LOCATION_NOT_SELECTED, p, null);
                return AttendanceResult.denied("Invalid location");
            }

            // check entry exit time

            LocalTime from = LocalTime.parse(rule.entryFrom);
            LocalTime till = LocalTime.parse(rule.entryTill);
            LocalTime nowTime = now.toLocalTime();

            if (nowTime.isBefore(from) || nowTime.isAfter(till)) {
                deny(req, DenialReason.TIME_WINDOW_VIOLATION, p, rule);
                return AttendanceResult.denied(
                        "Allowed between " + from + " and " + till);
            }

            // check participant type

            Set<String> allowedTypes = new HashSet<>(
                    Arrays.asList(rule.allowedParticipantTypes.split(",")));

            if (!allowedTypes.contains(p.participationType)) {
                deny(req, DenialReason.PARTICIPANT_TYPE_MISMATCH, p, rule);
                return AttendanceResult.denied("Type not allowed");
            }

            // check for duplicate attendance

            String lastTs = repo.fetchLastAttendanceTime(req.bsguid, req.eventName);

            if (lastTs != null) {

                LocalDateTime lastTime = LocalDateTime.parse(lastTs, DT_FMT);

                Duration diff = Duration.between(lastTime, now);

                if (diff.toMinutes() < 2) {
                    deny(req, DenialReason.DUPLICATE_ATTENDANCE, p, rule);
                    return AttendanceResult.denied(
                            "Attendance already marked recently. Please wait 2 minutes.");
                }
            }

            /* ---------- SUCCESS ---------- */

            AttendanceInsertRow row = new AttendanceInsertRow();
            row.cardUid = req.cardUid;
            row.bsguid = req.bsguid;
            row.fullName = p.fullName;
            row.dateTime = nowTs;
            row.location = req.location;
            row.eventName = req.eventName;
            row.uploadStatus = 0;

            repo.insertAttendance(row);

            return AttendanceResult.success();

        } catch (Exception ex) {
            ex.printStackTrace();
            deny(req, DenialReason.INTERNAL_ERROR, null, null);
            return AttendanceResult.denied("Internal error");
        }
    }

    // function to insert denied attendance

    private void deny(
            AttendanceRequest req,
            String reason,
            ParticipantRow p,
            EventLocationRule rule) {

        try {
            DeniedAttendanceRow d = new DeniedAttendanceRow();
            d.cardUid = req.cardUid;
            d.bsguid = req.bsguid;
            d.fullName = (p == null) ? null : p.fullName;
            d.eventId = req.eventId;
            d.eventName = req.eventName;
            d.location = req.location;
            d.attemptedDateTime = LocalDateTime.now().format(DT_FMT);
            d.denialReason = reason;
            d.participantType = (p == null) ? null : p.participationType;
            d.entryFrom = (rule == null) ? null : rule.entryFrom;
            d.entryTill = (rule == null) ? null : rule.entryTill;
            d.createdAt = d.attemptedDateTime;

            repo.insertDeniedAttendance(d);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
