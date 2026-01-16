package controller;

import service.AttendanceService;

import java.util.Collections;
import java.util.List;

import dto.AttendanceEvent;
import dto.AttendanceRequest;
import dto.AttendanceResult;

public class AttendanceController {

    private final AttendanceService service = new AttendanceService();

    public List<AttendanceEvent> getAllEvents() {
        try {
            return service.loadEvents();
        } catch (Exception ex) {
            ex.printStackTrace();
            return Collections.emptyList();
        }
    }

    public AttendanceResult markAttendance(AttendanceRequest req) {
        return service.markAttendance(req);
    }

}
