package dto;

import java.util.ArrayList;
import java.util.List;

public class EventRow {
    public final int id;
    public final String name;
    public final List<String> locations = new ArrayList<>();

    public EventRow(int id, String name) {
        this.id = id;
        this.name = name;
    }
}
