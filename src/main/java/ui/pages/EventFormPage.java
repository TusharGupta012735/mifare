package ui.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.EventFormData;
import model.SubEventData;
import ui.common.LoaderOverlay;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EventFormPage {

    public static Parent create(Consumer<EventFormData> onSubmit) {

        /* ================= EVENT FIELDS ================= */

        TextField name = new TextField();
        name.setPromptText("Event name");
        name.getStyleClass().add("input");

        TextField venue = new TextField();
        venue.setPromptText("Venue");
        venue.getStyleClass().add("input");

        DatePicker date = new DatePicker(LocalDate.now());

        /* ================= SUB EVENTS ================= */

        VBox subEventsBox = new VBox(12);
        subEventsBox.setPadding(new Insets(6, 0, 6, 0));

        List<SubEventRow> subEventRows = new ArrayList<>();

        Button addLocationBtn = new Button("+ Add Location");
        addLocationBtn.getStyleClass().add("btn-secondary");

        Runnable addSubEventRow = () -> {

            final SubEventRow[] holder = new SubEventRow[1];

            holder[0] = new SubEventRow(() -> {
                subEventsBox.getChildren().remove(holder[0]);
                subEventRows.remove(holder[0]);
            });

            subEventRows.add(holder[0]);
            subEventsBox.getChildren().add(holder[0]);
        };

        addSubEventRow.run(); // first location by default
        addLocationBtn.setOnAction(e -> addSubEventRow.run());

        /* ================= LAYOUT ================= */

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);

        add(grid, 0, "Event Name", name);
        add(grid, 1, "Venue", venue);
        add(grid, 2, "Date", date);
        add(grid, 3, "Locations", subEventsBox);
        add(grid, 4, "", addLocationBtn);

        Label title = new Label("📅 Create Event");
        title.getStyleClass().add("page-title");

        Button save = new Button("Save Event");
        save.getStyleClass().add("btn-primary");

        VBox root = new VBox(16, title, grid, save);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_LEFT);
        root.getStyleClass().add("card");

        /* ================= SAVE HANDLER ================= */

        save.setOnAction(e -> {

            if (name.getText().isBlank()) {
                alert("Event name is required");
                return;
            }

            if (subEventRows.isEmpty()) {
                alert("At least one location is required");
                return;
            }

            EventFormData ev = new EventFormData();
            ev.name = name.getText().trim();
            ev.venue = venue.getText().trim();
            ev.date = date.getValue();

            for (SubEventRow row : subEventRows) {
                SubEventData se = row.toData();
                ev.subEvents.add(se);
            }

            LoaderOverlay loader = (LoaderOverlay) root.getProperties().get("loader");

            save.setDisable(true);
            loader.show();

            new Thread(() -> {
                try {
                    onSubmit.accept(ev);
                    javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION,
                            "Event Saved Successfully",
                            ButtonType.OK).showAndWait());
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                            ex.getMessage(),
                            ButtonType.OK).showAndWait());
                } finally {
                    javafx.application.Platform.runLater(() -> {
                        loader.hide();
                        save.setDisable(false);
                    });
                }
            }, "event-save-thread").start();
        });

        LoaderOverlay loader = LoaderOverlay.wrap(root);
        root.getProperties().put("loader", loader);

        return loader.getRoot();
    }

    /* ================= HELPERS ================= */

    private static Spinner<LocalTime> timeSpinner() {
        SpinnerValueFactory<LocalTime> f = new SpinnerValueFactory<>() {
            {
                setValue(LocalTime.of(9, 0));
            }

            @Override
            public void decrement(int s) {
                setValue(getValue().minusMinutes(15 * s));
            }

            @Override
            public void increment(int s) {
                setValue(getValue().plusMinutes(15 * s));
            }
        };
        Spinner<LocalTime> sp = new Spinner<>(f);
        sp.setEditable(true);
        return sp;
    }

    private static void add(GridPane g, int r, String l, javafx.scene.Node n) {
        if (!l.isBlank()) {
            Label lbl = new Label(l + ":");
            lbl.getStyleClass().add("form-label");
            g.add(lbl, 0, r);
        }
        g.add(n, 1, r);
        GridPane.setHgrow(n, Priority.ALWAYS);
    }

    private static void alert(String msg) {
        new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait();
    }

    /* ================= SUB EVENT ROW ================= */

    private static class SubEventRow extends VBox {

        TextField subEventName = new TextField();
        TextField locationName = new TextField();

        Spinner<LocalTime> from = timeSpinner();
        Spinner<LocalTime> till = timeSpinner();

        CheckBox cbParticipant = new CheckBox("Participant");
        CheckBox cbStaff = new CheckBox("Staff");
        CheckBox cbVolunteer = new CheckBox("Volunteer");
        CheckBox cbVip = new CheckBox("VIP");
        CheckBox cbOther = new CheckBox("Other");

        TextField customType = new TextField();

        SubEventRow(Runnable onRemove) {

            setSpacing(8);
            setPadding(new Insets(10));
            getStyleClass().add("card");

            subEventName.setPromptText("Sub-event name");
            locationName.setPromptText("Location");

            customType.setPromptText("Custom type");
            customType.setDisable(true);

            cbOther.selectedProperty().addListener((o, a, v) -> {
                customType.setDisable(!v);
                if (!v)
                    customType.clear();
            });

            Button remove = new Button("Remove");
            remove.getStyleClass().add("btn-danger");
            remove.setOnAction(e -> onRemove.run());

            getChildren().addAll(
                    new Label("Sub Event"),
                    subEventName,
                    new Label("Location"),
                    locationName,
                    new Label("Entry Time"),
                    new HBox(8, from, till),
                    new Label("Allowed Participants"),
                    new HBox(10,
                            cbParticipant, cbStaff,
                            cbVolunteer, cbVip, cbOther),
                    customType,
                    remove);
        }

        SubEventData toData() {

            SubEventData se = new SubEventData();
            se.subEventName = subEventName.getText().trim();
            se.locationName = locationName.getText().trim();
            se.entryFrom = from.getValue();
            se.entryTill = till.getValue();

            if (cbParticipant.isSelected())
                se.allowedParticipantTypes.add("participant");
            if (cbStaff.isSelected())
                se.allowedParticipantTypes.add("staff");
            if (cbVolunteer.isSelected())
                se.allowedParticipantTypes.add("volunteer");
            if (cbVip.isSelected())
                se.allowedParticipantTypes.add("vip");
            if (cbOther.isSelected())
                se.allowedParticipantTypes.add("other:" + customType.getText().trim());

            return se;
        }
    }
}
