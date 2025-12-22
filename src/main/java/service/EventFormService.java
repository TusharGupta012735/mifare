package service;

import model.EventFormData;
import repository.EventFormRepository;

public final class EventFormService {

    private static final EventFormRepository eventFormRepository = new EventFormRepository();

    public static void save(EventFormData ev) throws Exception {

        if (ev.name == null || ev.name.isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }

        if (ev.entryAllowedFrom != null && ev.entryAllowedTill != null &&
                ev.entryAllowedFrom.isAfter(ev.entryAllowedTill)) {
            throw new IllegalArgumentException("Entry start time must be before end time");
        }

        // Example DB insert (replace with your AccessDb logic)
        eventFormRepository.insertEvent(ev.name,
                ev.venue,
                ev.date,
                ev.participantType,
                ev.customParticipantType,
                ev.entryAllowedFrom,
                ev.entryAllowedTill);
    }
}
