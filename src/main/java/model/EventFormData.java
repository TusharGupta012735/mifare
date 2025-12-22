package model;

import java.time.LocalDate;
import java.time.LocalTime;

public class EventFormData {

    public String name;
    public String venue;
    public LocalDate date;

    public String participantType; // participant / staff / volunteer / vip / other
    public String customParticipantType; // only if "other"

    public LocalTime entryAllowedFrom;
    public LocalTime entryAllowedTill;

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
                '}';
    }
}
