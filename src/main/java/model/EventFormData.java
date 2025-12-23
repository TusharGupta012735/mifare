package model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class EventFormData {

    public String name;
    public String venue;
    public LocalDate date;

    public String participantType; // participant / staff / volunteer / vip / other
    public String customParticipantType; // only if "other"

    public LocalTime entryAllowedFrom;
    public LocalTime entryAllowedTill;

    public List<String> locations = new ArrayList<>();


    @Override
    public String toString() {
        return "EventFormData{" +
                "name='" + name + '\'' +
                ", venue='" + venue + '\'' +
                ", date=" + date +
                ", participantType='" + participantType + '\'' +
                ", customParticipantType='" + customParticipantType + '\'' +
                ", entryAllowedFrom=" + entryAllowedFrom +
                ", entryAllowedTill=" + entryAllowedTill +
                ", locations=" + locations +
                '}';
    }
}
