package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EventFormData {

    public Integer id; // populated after DB insert

    public String name;
    public String venue;
    public LocalDate date;

    public List<SubEventData> subEvents = new ArrayList<>();

    @Override
    public String toString() {
        return "EventFormData{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", venue='" + venue + '\'' +
                ", date=" + date +
                ", subEvents=" + subEvents +
                '}';
    }
}
