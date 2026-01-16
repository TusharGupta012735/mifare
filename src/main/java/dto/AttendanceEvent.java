package dto;

import java.util.List;

public class AttendanceEvent {
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
