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

    /* ================= LOAD EVENTS ================= */

    public List<AttendanceEvent> loadEvents() throws Exception {
        List<EventRow> rows = repo.fetchAllEvents();
        List<AttendanceEvent> result = new ArrayList<>();
        for (EventRow r : rows) {
            result.add(new AttendanceEvent(r.id, r.name, r.locations));
        }
        return result;
    }

    /* ================= MAIN ATTENDANCE FLOW ================= */

    public AttendanceResult markAttendance(AttendanceRequest req) {

        LocalDateTime now = LocalDateTime.now();
        String nowTs = now.format(DT_FMT);

        System.out.println("[AttendanceService] markAttendance started at " + nowTs
                + " for cardUid=" + req.cardUid);

        try {
            /* ---------- BASIC VALIDATION ---------- */

            if (req.cardUid == null || req.cardUid.isBlank()) {
                return AttendanceResult.denied("Invalid card");
            }

            if (req.eventId <= 0) {
                deny(req, DenialReason.EVENT_NOT_SELECTED, null, null);
                return AttendanceResult.denied("Event not selected");
            }

            if (req.location == null || req.location.isBlank()) {
                deny(req, DenialReason.LOCATION_NOT_SELECTED, null, null);
                return AttendanceResult.denied("Location not selected");
            }

            /* ---------- PARTICIPANT LOOKUP (ONLY BY CARDUID) ---------- */

            System.out.println("[AttendanceService] Fetching participant by cardUid=" + req.cardUid);
            ParticipantRow p = repo.findParticipantByCardUid(req.cardUid);

            if (p == null) {
                System.out.println("[AttendanceService] No participant mapped to cardUid");
                deny(req, DenialReason.PARTICIPANT_NOT_FOUND, null, null);
                return AttendanceResult.denied("Participant not found");
            }

            System.out.println("[AttendanceService] Participant found: "
                    + p.fullName + " | " + p.bsguid + " | " + p.participationType);

            /* ---------- EVENT LOCATION RULE ---------- */

            EventLocationRule rule = repo.findEventLocationRule(req.eventId, req.location);

            if (rule == null) {
                deny(req, DenialReason.LOCATION_NOT_SELECTED, p, null);
                return AttendanceResult.denied("Invalid location");
            }

            /* ---------- TIME WINDOW CHECK ---------- */

            LocalTime nowTime = now.toLocalTime();
            LocalTime from = LocalTime.parse(rule.entryFrom);
            LocalTime till = LocalTime.parse(rule.entryTill);

            if (nowTime.isBefore(from) || nowTime.isAfter(till)) {
                deny(req, DenialReason.TIME_WINDOW_VIOLATION, p, rule);
                return AttendanceResult.denied(
                        "Allowed between " + from + " and " + till);
            }

            /* ---------- PARTICIPANT TYPE CHECK ---------- */

            Set<String> allowedTypes = new HashSet<>();
            for (String t : rule.allowedParticipantTypes.split(",")) {
                allowedTypes.add(t.trim().toLowerCase());
            }

            String participantType = p.participationType == null ? "" : p.participationType.trim().toLowerCase();

            if (!allowedTypes.contains(participantType)) {
                deny(req, DenialReason.PARTICIPANT_TYPE_MISMATCH, p, rule);
                return AttendanceResult.denied("Type not allowed");
            }

            /* ---------- DUPLICATE CHECK ---------- */

            String lastTs = repo.fetchLastAttendanceTime(p.bsguid, req.eventName, req.location);

            if (lastTs != null) {
                LocalDateTime last = LocalDateTime.parse(lastTs, DT_FMT);
                if (Duration.between(last, now).toMinutes() < 2) {
                    deny(req, DenialReason.DUPLICATE_ATTENDANCE, p, rule);
                    return AttendanceResult.denied(
                            "Attendance already marked recently");
                }
            }

            /* ---------- SUCCESS INSERT ---------- */

            AttendanceInsertRow row = new AttendanceInsertRow();
            row.cardUid = req.cardUid;
            row.bsguid = p.bsguid;
            row.fullName = p.fullName;
            row.dateTime = nowTs;
            row.location = req.location;
            row.eventName = req.eventName;
            row.uploadStatus = 0;

            repo.insertAttendance(row);

            System.out.println("[AttendanceService] Attendance marked successfully");
            return AttendanceResult.success();

        } catch (Exception ex) {
            ex.printStackTrace();
            deny(req, DenialReason.INTERNAL_ERROR, null, null);
            return AttendanceResult.denied("Internal error");
        }
    }

    /* ================= DENIAL HANDLER ================= */

    private void deny(
            AttendanceRequest req,
            String reason,
            ParticipantRow p,
            EventLocationRule rule) {

        try {
            DeniedAttendanceRow d = new DeniedAttendanceRow();
            d.cardUid = req.cardUid;
            d.bsguid = (p == null) ? null : p.bsguid;
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

    public ParticipantRow lookupParticipantByCardUid(String cardUid) throws Exception {

        if (cardUid == null || cardUid.isBlank()) {
            return null;
        }

        System.out.println("[AttendanceService] Looking up participant by CardUID=" + cardUid);

        ParticipantRow p = repo.findParticipantByCardUid(cardUid);

        if (p == null) {
            System.out.println("[AttendanceService] No participant found for CardUID=" + cardUid);
        } else {
            System.out.println(
                    "[AttendanceService] Participant found"
                            + " | fullName=" + p.fullName
                            + " | bsguid=" + p.bsguid
                            + " | type=" + p.participationType
                            + " | status=" + p.status);
        }

        return p;
    }

    public AttendanceResult markExit(AttendanceRequest req) {

        LocalDateTime now = LocalDateTime.now();
        String nowTs = now.format(DT_FMT);

        try {
            if (req.cardUid == null || req.cardUid.isBlank()) {
                return AttendanceResult.denied("Invalid card");
            }

            if (req.eventId <= 0) {
                deny(req, DenialReason.EVENT_NOT_SELECTED, null, null);
                return AttendanceResult.denied("Event not selected");
            }

            if (req.location == null || req.location.isBlank()) {
                deny(req, DenialReason.LOCATION_NOT_SELECTED, null, null);
                return AttendanceResult.denied("Location not selected");
            }

            ParticipantRow p = repo.findParticipantByCardUid(req.cardUid);
            if (p == null) {
                deny(req, DenialReason.PARTICIPANT_NOT_FOUND, null, null);
                return AttendanceResult.denied("Participant not found");
            }

            EventLocationRule rule = repo.findEventLocationRule(req.eventId, req.location);
            if (rule == null) {
                deny(req, DenialReason.LOCATION_NOT_SELECTED, p, null);
                return AttendanceResult.denied("Invalid location");
            }

            // find today's latest entry row where exit is not done
            Integer openId = repo.findOpenEntryId(
                    p.bsguid,
                    req.eventName,
                    req.location,
                    LocalDate.now().toString());

            if (openId == null) {
                deny(req, DenialReason.EXIT_WITHOUT_ENTRY, p, rule);
                return AttendanceResult.denied("No open entry found / already exited");
            }

            repo.updateExitTime(openId, nowTs);
            return AttendanceResult.success();

        } catch (Exception ex) {
            ex.printStackTrace();
            deny(req, DenialReason.INTERNAL_ERROR, null, null);
            return AttendanceResult.denied("Internal error");
        }
    }

}
