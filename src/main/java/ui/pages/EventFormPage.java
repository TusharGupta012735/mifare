package ui.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.EventFormData;
import ui.common.LoaderOverlay;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EventFormPage {

    public static Parent create(Consumer<EventFormData> onSubmit) {

        // ------------to get location of
        // events--------------------------------------------------
        List<TextField> locationFields = new ArrayList<>();

        VBox locationsBox = new VBox(8);
        locationsBox.setPadding(new Insets(6, 0, 6, 0));

        Button addLocationBtn = new Button("+ Add Location");
        addLocationBtn.getStyleClass().add("btn-secondary");

        Runnable addLocationField = () -> {
            TextField tf = new TextField();
            tf.setPromptText("Location name");
            tf.getStyleClass().add("input");

            locationFields.add(tf);
            locationsBox.getChildren().add(tf);
        };

        addLocationField.run(); // add first field by default

        addLocationBtn.setOnAction(e -> addLocationField.run());
        // ------------to get location of
        // events--------------------------------------------------

        TextField name = new TextField();
        name.setPromptText("Event name");

        TextField venue = new TextField();
        venue.setPromptText("Venue");

        DatePicker date = new DatePicker(LocalDate.now());

        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll("participant", "staff", "volunteer", "vip", "other");

        name.getStyleClass().add("input");
        venue.getStyleClass().add("input");
        type.getStyleClass().add("input");

        TextField otherType = new TextField();
        otherType.setPromptText("Custom type");
        otherType.setVisible(false);
        otherType.setManaged(false);

        Spinner<LocalTime> fromTime = timeSpinner();
        Spinner<LocalTime> tillTime = timeSpinner();

        type.valueProperty().addListener((o, a, v) -> {
            boolean show = "other".equalsIgnoreCase(v);
            otherType.setVisible(show);
            otherType.setManaged(show);
            if (!show)
                otherType.clear();
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);

        add(grid, 0, "Name", name);
        add(grid, 1, "Venue", venue);
        add(grid, 2, "Locations", locationsBox);
        // grid.add(addLocationBtn, 1, 3);
        add(grid, 3, "+Add more", addLocationBtn);
        add(grid, 4, "Date", date);
        add(grid, 5, "Participant Type", type);
        add(grid, 6, "Custom Type", otherType);
        add(grid, 7, "Entry From", fromTime);
        add(grid, 8, "Entry Till", tillTime);

        Button save = new Button("Save Event");
        save.getStyleClass().add("btn-primary");

        Label title = new Label("📅 Create Event");
        title.getStyleClass().add("page-title");

        VBox root = new VBox(14, title, grid, save);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_LEFT);

        root.getStyleClass().add("card");

        save.setOnAction(e -> {
            if (name.getText().isBlank()) {
                alert("Event name required");
                return;
            }
            if (type.getValue() == null) {
                alert("Participant type required");
                return;
            }
            if ("other".equals(type.getValue()) && otherType.getText().isBlank()) {
                alert("Enter custom participant type");
                return;
            }
            List<String> locations = locationFields.stream()
                    .map(tf -> tf.getText().trim())
                    .filter(s -> !s.isEmpty())
                    .toList();

            if (locations.isEmpty()) {
                alert("At least one location is required");
                return;
            }

            EventFormData ev = new EventFormData();
            ev.name = name.getText().trim();
            ev.venue = venue.getText().trim();
            ev.date = date.getValue();
            ev.participantType = type.getValue();
            ev.customParticipantType = otherType.getText().trim();
            ev.entryAllowedFrom = fromTime.getValue();
            ev.entryAllowedTill = tillTime.getValue();
            ev.locations = new ArrayList<>(locations);

            LoaderOverlay loader = (LoaderOverlay) root.getProperties().get("loader");

            save.setDisable(true);
            loader.show();

            new Thread(() -> {
                try {
                    onSubmit.accept(ev);
                    javafx.application.Platform.runLater(() -> {
                        new Alert(Alert.AlertType.INFORMATION, "Event Saved Succesfully", ButtonType.OK).showAndWait();
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                            ex.getMessage(), ButtonType.OK).showAndWait());
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
        Label lbl = new Label(l + ":");
        lbl.getStyleClass().add("form-label");

        g.add(lbl, 0, r);
        g.add(n, 1, r);
        GridPane.setHgrow(n, Priority.ALWAYS);
    }

    private static void alert(String msg) {
        new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait();
    }
}
