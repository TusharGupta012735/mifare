package controller;

import model.EventFormData;
import service.EventFormService;
import util.DebugLog;

public class EventFormController {

    public void handleCreate(EventFormData event) {
        try {
            DebugLog.d("Creating event: %s", event.name);
            EventFormService.save(event);
            DebugLog.d("Event created successfully: %s", event.name);
        } catch (Exception e) {
            DebugLog.ex(e, "Failed to create event: %s", event.name);
            throw new RuntimeException("Failed to save event", e);
        }
    }
}
