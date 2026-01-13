package service;

import model.EventFormData;
import model.SubEventData;
import repository.EventFormRepository;

public final class EventFormService {

    private static final EventFormRepository eventFormRepository = new EventFormRepository();

    public static void save(EventFormData ev) throws Exception {

        /* ========= EVENT LEVEL VALIDATION ========= */

        if (ev == null) {
            throw new IllegalArgumentException("Event data is missing");
        }

        if (ev.name == null || ev.name.isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }

        if (ev.subEvents == null || ev.subEvents.isEmpty()) {
            throw new IllegalArgumentException("At least one location is required");
        }

        /* ========= SUB-EVENT LEVEL VALIDATION ========= */

        for (SubEventData se : ev.subEvents) {

            if (se.locationName == null || se.locationName.isBlank()) {
                throw new IllegalArgumentException(
                        "Location name is required for all entries");
            }

            if (se.allowedParticipantTypes == null ||
                    se.allowedParticipantTypes.isEmpty()) {

                throw new IllegalArgumentException(
                        "Select participant type for location: " + se.locationName);
            }

            if (se.entryFrom != null && se.entryTill != null &&
                    se.entryFrom.isAfter(se.entryTill)) {

                throw new IllegalArgumentException(
                        "Invalid entry time for location: " + se.locationName);
            }
        }

        /* ========= DB INSERT ========= */

        eventFormRepository.insertFullEvent(ev);
    }
}
