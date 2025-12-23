package service;

import repository.AttendanceRepository;
import repository.AttendanceRepository.EventRow;

import java.util.*;

public class AttendanceService {

    private final AttendanceRepository repo = new AttendanceRepository();

    public List<AttendanceEvent> loadEvents() throws Exception {

        List<EventRow> rows = repo.fetchAllEvents();
        List<AttendanceEvent> result = new ArrayList<>();

        for (EventRow r : rows) {
            result.add(new AttendanceEvent(r.id, r.name, r.locations));
        }
        return result;
    }

    // DTO for UI
    public static class AttendanceEvent {
        public final int id;
        public final String name;
        public final List<String> locations;

        public AttendanceEvent(int id, String name, List<String> locations) {
            this.id = id;
            this.name = name;
            this.locations = locations;
        }

        @Override
        public String toString() {
            return name; 
        }
    }
}
