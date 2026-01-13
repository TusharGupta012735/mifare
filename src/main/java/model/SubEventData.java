package model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class SubEventData {

    public String subEventName;
    public String locationName;

    public LocalTime entryFrom;
    public LocalTime entryTill;

    public List<String> allowedParticipantTypes = new ArrayList<>();

    @Override
    public String toString() {
        return "SubEventData{" +
                "subEventName='" + subEventName + '\'' +
                ", locationName='" + locationName + '\'' +
                ", entryFrom=" + entryFrom +
                ", entryTill=" + entryTill +
                ", allowedParticipantTypes=" + allowedParticipantTypes +
                '}';
    }
}
