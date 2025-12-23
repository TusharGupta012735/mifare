package controller;

import service.AttendanceService;
import service.AttendanceService.AttendanceEvent;

import java.util.Collections;
import java.util.List;

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
}
