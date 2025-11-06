package ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;
import nfc.SmartMifareReader;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EntryForm {

    // --- Global NFC busy flag so only one NFC operation runs at a time
    // (read/write) ---
    private static final AtomicBoolean NFC_BUSY = new AtomicBoolean(false);

    public static void setNfcBusy(boolean b) {
        NFC_BUSY.set(b);
    }

    /**
     * Note: onSave is a BiConsumer where the second parameter is a Runnable `done`
     * that the caller MUST run (on any thread) when the save/write operation
     * finishes.
     */
    public static Parent create(BiConsumer<Map<String, String>, Runnable> onSave) {

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #ffffff, #f4f6f8);");

        Label title = new Label("📝 Entry Form");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #1565c0;");
        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));

        // --- Top Center Banner (big, colorful) ---
        Label banner = new Label();
        banner.getStyleClass().add("status-banner");
        banner.setId("statusBanner");
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setWrapText(true);
        setBannerInfo(banner, ""); // start blank, styled
        HBox bannerWrap = new HBox(banner);
        bannerWrap.setAlignment(Pos.CENTER);
        bannerWrap.setPadding(new Insets(0, 0, 12, 0));

        String baseStyleCore = "-fx-background-color: white; -fx-border-color: #bdbdbd; -fx-border-radius: 6; " +
                "-fx-background-radius: 6; -fx-padding: 8 10; -fx-text-fill: #212121;";
        String labelBaseStyleCore = "-fx-font-weight:600; -fx-text-fill:#212121;";

        // Create input controls
        TextField fullName = new TextField();
        fullName.setPromptText("Full name");
        TextField bsguid = new TextField();
        bsguid.setPromptText("BS GUID");
        ComboBox<String> participationType = new ComboBox<>();
        participationType.getItems().addAll("guide", "scout", "ranger");
        participationType.setPromptText("Select");
        TextField bsgDistrict = new TextField();
        bsgDistrict.setPromptText("District");
        TextField email = new TextField();
        email.setPromptText("example@domain.com");
        TextField phoneNumber = new TextField();
        phoneNumber.setPromptText("Phone number");
        TextField bsgState = new TextField();
        bsgState.setPromptText("State");
        TextField memberTyp = new TextField();
        memberTyp.setPromptText("Member type");
        TextField unitNam = new TextField();
        unitNam.setPromptText("Unit name");
        ComboBox<String> rank_or_section = new ComboBox<>();
        rank_or_section.getItems().addAll("guide", "scout", "ranger");
        rank_or_section.setPromptText("Select");
        DatePicker dateOfBirth = new DatePicker();
        dateOfBirth.setPromptText("Date of birth");
        TextField age = new TextField();
        age.setPromptText("Age");

        Control[] controls = new Control[] {
                fullName, bsguid, participationType, bsgDistrict,
                email, phoneNumber, bsgState, memberTyp,
                unitNam, rank_or_section, dateOfBirth, age
        };

        for (Control c : controls) {
            c.setStyle(baseStyleCore);
        }

        // Left and right grids
        GridPane left = new GridPane();
        left.setHgap(10);
        left.setVgap(14);
        GridPane right = new GridPane();
        right.setHgap(10);
        right.setVgap(14);

        addField(left, 0, "FullName", fullName);
        addField(left, 1, "BSGUID", bsguid);
        addField(left, 2, "ParticipationType", participationType);
        addField(left, 3, "bsgDistrict", bsgDistrict);
        addField(left, 4, "unitNam", unitNam);
        addField(left, 5, "dateOfBirth", dateOfBirth);

        addField(right, 0, "Email", email);
        addField(right, 1, "phoneNumber", phoneNumber);
        addField(right, 2, "bsgState", bsgState);
        addField(right, 3, "memberTyp", memberTyp);
        addField(right, 4, "rank_or_section", rank_or_section);
        addField(right, 5, "age", age);

        HBox columns = new HBox(60, left, right);
        columns.setAlignment(Pos.CENTER);
        columns.setPadding(new Insets(10));

        // Bind input widths dynamically
        DoubleBinding fieldWidthBinding = columns.widthProperty().divide(2.3);
        for (Control c : controls) {
            ((Region) c).prefWidthProperty().bind(fieldWidthBinding);
        }

        // Dynamic font size (safe version)
        DoubleBinding fontSizeBinding = Bindings.createDoubleBinding(() -> {
            double w = columns.getWidth();
            double fs = w / 70.0;
            if (fs < 12)
                fs = 12;
            if (fs > 18)
                fs = 18;
            return fs;
        }, columns.widthProperty());

        fontSizeBinding.addListener((obs, oldV, newV) -> {
            double fs = newV.doubleValue();
            left.getChildren().stream()
                    .filter(n -> n instanceof Label)
                    .forEach(n -> n.setStyle(labelBaseStyleCore + "; -fx-font-size: " + (fs - 1) + "px;"));
            right.getChildren().stream()
                    .filter(n -> n instanceof Label)
                    .forEach(n -> n.setStyle(labelBaseStyleCore + "; -fx-font-size: " + (fs - 1) + "px;"));
            for (Control c : controls) {
                c.setStyle(baseStyleCore + " -fx-font-size: " + fs + "px;");
                if (c instanceof DatePicker dp && dp.getEditor() != null) {
                    dp.getEditor().setStyle("-fx-font-size:" + fs + "px;");
                }
            }
            title.setFont(Font.font("System", FontWeight.BOLD, Math.max(16, fs + 4)));
            // scale banner a bit with width
            banner.setStyle(banner.getStyle().replaceAll("-fx-font-size:\\s*\\d+px;",
                    "-fx-font-size: " + Math.max(28, fs + 14) + "px;"));
        });

        // Buttons
        Button saveBtn = new Button("Save");
        Button clearBtn = new Button("Clear");
        Button eraseBtn = new Button("Erase Card");
        saveBtn.setStyle(
                "-fx-background-color:#2196f3;-fx-text-fill:white;-fx-font-weight:600;-fx-padding:8 20;-fx-background-radius:6;");
        clearBtn.setStyle(
                "-fx-background-color:#f5f5f5;-fx-text-fill:#424242;-fx-font-weight:600;-fx-padding:8 20;-fx-background-radius:6;-fx-border-color:#e0e0e0;");
        eraseBtn.setStyle(
                "-fx-background-color:#e53935;-fx-text-fill:white;-fx-font-weight:600;-fx-padding:8 14;-fx-background-radius:6;");

        HBox buttons = new HBox(10, saveBtn, clearBtn, eraseBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        // --- Center content (banner at the top) ---
        VBox center = new VBox(header, bannerWrap, columns, buttons);
        center.setSpacing(10);
        center.setPadding(new Insets(10));
        root.setCenter(center);

        // Save action
        saveBtn.setOnAction(e -> {
            // Validation in banner
            if (fullName.getText().trim().isEmpty() || phoneNumber.getText().trim().isEmpty()) {
                setBannerWarn(banner, "Please fill required fields: FullName and phoneNumber.");
                return;
            }

            // --- BUILD MAP + CSV (keys aligned to AccessDb expectations) ---
            String fullNameVal = txt(fullName);
            String bsguidVal = txt(bsguid);
            String participationVal = val(participationType);
            String bsgDistrictVal = txt(bsgDistrict);
            String emailVal = txt(email);
            String phoneVal = txt(phoneNumber);
            String bsgStateVal = txt(bsgState);
            String memberTypVal = txt(memberTyp); // memberTyp
            String unitNamVal = txt(unitNam); // unitNam
            String rankVal = val(rank_or_section);
            LocalDate dobVal = dateOfBirth.getValue();
            String dobStr = dobVal == null ? "" : dobVal.toString(); // yyyy-MM-dd
            String ageVal = txt(age);

            Map<String, String> data = new LinkedHashMap<>();
            data.put("FullName", fullNameVal);
            data.put("BSGUID", bsguidVal);
            data.put("ParticipationType", participationVal);
            data.put("bsgDistrict", bsgDistrictVal);
            data.put("Email", emailVal);
            data.put("phoneNumber", phoneVal);
            data.put("bsgState", bsgStateVal);
            data.put("memberTyp", memberTypVal);
            data.put("unitNam", unitNamVal);
            data.put("rank_or_section", rankVal);
            data.put("dataOfBirth", dobStr); // AccessDb expects dataOfBirth
            data.put("age", ageVal);

            String csvForNfc = String.join(",",
                    Arrays.asList(
                            fullNameVal, bsguidVal, participationVal, bsgDistrictVal,
                            emailVal, phoneVal, bsgStateVal, memberTypVal, unitNamVal,
                            rankVal, dobStr, ageVal));

            data.put("__CSV__", csvForNfc);

            saveBtn.setDisable(true);
            clearBtn.setDisable(true);
            eraseBtn.setDisable(true);
            setBannerInfo(banner, "Submitting... hold card to update (if writing to card).");

            if (onSave != null) {
                Runnable done = () -> Platform.runLater(() -> {
                    saveBtn.setDisable(false);
                    clearBtn.setDisable(false);
                    eraseBtn.setDisable(false);
                    // Leave banner as whatever Dashboard shows via Alerts; we provide a soft
                    // success:
                    setBannerSuccess(banner, "Submitted. (If NFC write was requested, it has finished.)");
                });

                try {
                    onSave.accept(data, done);
                } catch (Exception ex) {
                    saveBtn.setDisable(false);
                    clearBtn.setDisable(false);
                    eraseBtn.setDisable(false);
                    setBannerError(banner, "❌ Submit failed: " + ex.getMessage());
                }
            } else {
                saveBtn.setDisable(false);
                clearBtn.setDisable(false);
                eraseBtn.setDisable(false);
                setBannerWarn(banner, "No save handler configured.");
            }
        });

        // Clear action
        clearBtn.setOnAction(evt -> {
            fullName.clear();
            bsguid.clear();
            participationType.setValue(null);
            bsgDistrict.clear();
            email.clear();
            phoneNumber.clear();
            bsgState.clear();
            memberTyp.clear();
            unitNam.clear();
            rank_or_section.setValue(null);
            dateOfBirth.setValue(null);
            age.clear();
            setBannerInfo(banner, "Form cleared.");
        });

        // Erase Card action
        eraseBtn.setOnAction(evt -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "This will overwrite (erase) writable data on the presented MIFARE Classic 1K card for sectors\n" +
                            "the reader can authenticate. Make sure you own the card and want to proceed.\n\nContinue?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn != ButtonType.OK)
                    return;

                saveBtn.setDisable(true);
                clearBtn.setDisable(true);
                eraseBtn.setDisable(true);
                setBannerInfo(banner, "Hold the card steady: reading UID…");

                Thread th = new Thread(() -> {
                    try {
                        setNfcBusy(true);

                        // 1) Read UID (block until card present)
                        nfc.SmartMifareReader.ReadResult present = null;
                        try {
                            present = nfc.SmartMifareReader.readUIDWithData(0); // infinite wait
                        } catch (Exception ignore) {
                        }

                        if (present == null || present.uid == null || present.uid.isBlank()) {
                            Platform.runLater(() -> setBannerError(banner,
                                    "❌ No card detected. Please present a card and try again."));
                            return;
                        }
                        final String uid = present.uid;

                        Platform.runLater(
                                () -> setBannerInfo(banner, "Erasing card data… please keep the card on the reader."));

                        // 2) Erase
                        try {
                            nfc.SmartMifareEraser.eraseMemory();
                        } catch (Exception ex) {
                            final String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                            Platform.runLater(() -> setBannerError(banner, "❌ Erase failed: " + msg));
                            return;
                        }

                        // 3) Clear DB assignment: status='F', CardUID=NULL
                        int rows = 0;
                        try {
                            rows = db.AccessDb.clearCardAssignment(uid);
                        } catch (Exception ex) {
                            final String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                            Platform.runLater(() -> setBannerWarn(banner,
                                    "Card erased, but DB update failed: " + msg
                                            + "\nYou may need to clear the assignment manually."));
                            return;
                        }

                        // 4) Success banner
                        final int rowsFinal = rows;
                        Platform.runLater(() -> {
                            if (rowsFinal > 0) {
                                setBannerSuccess(banner, "✅ Erase complete. Card unassigned and status set to F.");
                            } else {
                                setBannerSuccess(banner,
                                        "✅ Erase complete. (No matching participant had this CardUID.)");
                            }
                        });

                    } finally {
                        setNfcBusy(false);
                        Platform.runLater(() -> {
                            saveBtn.setDisable(false);
                            clearBtn.setDisable(false);
                            eraseBtn.setDisable(false);
                        });
                    }
                }, "nfc-eraser-thread");
                th.setDaemon(true);
                th.start();
            });
        });

        fontSizeBinding.getValue();

        // Start NFC auto-fill (false = don't overwrite existing fields; 1000ms poll)
        ScheduledExecutorService svc = startNfcAutoFill(root,
                fullName, bsguid, participationType,
                bsgDistrict, email, phoneNumber,
                bsgState, memberTyp, unitNam,
                rank_or_section, dateOfBirth, age,
                false, // overwriteAlways
                1000);
        // expose poller on the root so Dashboard can stop it when switching screens
        if (svc != null) {
            root.getProperties().put("nfc-poller", svc);
        }

        return root;
    }

    private static String txt(TextField tf) {
        return tf.getText() == null ? "" : tf.getText().trim();
    }

    private static String val(ComboBox<String> cb) {
        return cb.getValue() == null ? "" : cb.getValue().trim();
    }

    private static void addField(GridPane grid, int row, String labelText, Control field) {
        Label lbl = new Label(labelText + ":");
        lbl.setStyle("-fx-font-weight:600;-fx-text-fill:#212121;");
        GridPane.setConstraints(lbl, 0, row);
        GridPane.setConstraints(field, 1, row);
        grid.getChildren().addAll(lbl, field);
    }

    // ---------------- Banner styling helpers ----------------
    private static void setBannerBase(Label banner, String text, String bg, String border, String fg) {
        banner.setText(text);
        banner.setStyle(
                "-fx-font-size: 32px;" +
                        "-fx-font-weight: 900;" +
                        "-fx-text-fill: " + fg + ";" +
                        "-fx-background-color: " + bg + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: " + border + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 2;" +
                        "-fx-padding: 10 18;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 2);");
    }

    private static void setBannerInfo(Label banner, String text) {
        setBannerBase(banner, text, "#E3F2FD", "#90CAF9", "#0D47A1");
    }

    private static void setBannerSuccess(Label banner, String text) {
        setBannerBase(banner, text, "#E8F5E9", "#A5D6A7", "#2E7D32");
    }

    private static void setBannerWarn(Label banner, String text) {
        setBannerBase(banner, text, "#FFF8E1", "#FFE082", "#E65100");
    }

    private static void setBannerError(Label banner, String text) {
        setBannerBase(banner, text, "#FFEBEE", "#EF9A9A", "#C62828");
    }

    // ---------------- NFC auto-fill helpers ----------------
    private static ScheduledExecutorService startNfcAutoFill(
            Parent root,
            TextField fullName, TextField bsguid, ComboBox<String> participationType,
            TextField bsgDistrict, TextField email, TextField phoneNumber,
            TextField bsgState, TextField memberTyp, TextField unitNam,
            ComboBox<String> rank_or_section, DatePicker dateOfBirth, TextField age,
            boolean overwriteAlways, long pollIntervalMs) {

        ScheduledExecutorService svc = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nfc-poller");
            t.setDaemon(true);
            return t;
        });

        AtomicReference<String> lastUid = new AtomicReference<>("");

        Runnable task = () -> {
            try {
                // if someone else is using NFC (write/erase/info), don't poll now
                if (NFC_BUSY.get())
                    return;

                SmartMifareReader.ReadResult rr = SmartMifareReader.readUIDWithData(1500);
                if (rr == null || rr.uid == null || rr.uid.isEmpty())
                    return;

                String uid = rr.uid;
                String data = rr.data == null ? "" : rr.data.trim();

                // debounce: ignore if same UID processed recently
                if (uid.equals(lastUid.get()))
                    return;
                lastUid.set(uid);

                if (data.isEmpty())
                    return;

                String[] parts = Arrays.stream(data.split(",", -1))
                        .map(String::trim)
                        .toArray(String[]::new);

                Platform.runLater(() -> {
                    try {
                        int i = 0;
                        setFieldFromCsv(fullName, parts, i++, overwriteAlways);
                        setFieldFromCsv(bsguid, parts, i++, overwriteAlways);
                        setComboFromCsv(participationType, parts, i++, overwriteAlways);
                        setFieldFromCsv(bsgDistrict, parts, i++, overwriteAlways);
                        setFieldFromCsv(email, parts, i++, overwriteAlways);
                        setFieldFromCsv(phoneNumber, parts, i++, overwriteAlways);
                        setFieldFromCsv(bsgState, parts, i++, overwriteAlways);
                        setFieldFromCsv(memberTyp, parts, i++, overwriteAlways);
                        setFieldFromCsv(unitNam, parts, i++, overwriteAlways);
                        setComboFromCsv(rank_or_section, parts, i++, overwriteAlways);

                        if (parts.length > i) {
                            String dobStr = parts[i++].trim();
                            if (!dobStr.isEmpty()) {
                                try {
                                    LocalDate d = LocalDate.parse(dobStr);
                                    if (overwriteAlways || dateOfBirth.getValue() == null)
                                        dateOfBirth.setValue(d);
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        if (parts.length > i) {
                            setFieldFromCsv(age, parts, i++, overwriteAlways);
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };

        svc.scheduleWithFixedDelay(task, 0, Math.max(200, pollIntervalMs), TimeUnit.MILLISECONDS);

        // shutdown when window closes (safety)
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Window w = newScene.getWindow();
                if (w != null) {
                    w.setOnHidden(evt -> svc.shutdownNow());
                } else {
                    newScene.windowProperty().addListener((o, oldW, newW) -> {
                        if (newW != null)
                            newW.setOnHidden(e -> svc.shutdownNow());
                    });
                }
            }
        });

        // also stop when this node is removed from scene graph
        root.parentProperty().addListener((o, oldP, newP) -> {
            if (newP == null) {
                svc.shutdownNow();
            }
        });

        return svc;
    }

    public static void stopNfcPolling(Node root) {
        if (root == null)
            return;
        Object svc = root.getProperties().get("nfc-poller");
        if (svc instanceof ScheduledExecutorService s) {
            s.shutdownNow();
            root.getProperties().remove("nfc-poller");
        }
    }

    private static void setFieldFromCsv(TextField tf, String[] parts, int idx, boolean overwriteAlways) {
        if (idx < 0 || idx >= parts.length)
            return;
        String v = parts[idx];
        if (v == null)
            return;
        if (overwriteAlways || tf.getText() == null || tf.getText().isEmpty()) {
            tf.setText(v);
        }
    }

    private static void setComboFromCsv(ComboBox<String> cb, String[] parts, int idx, boolean overwriteAlways) {
        if (idx < 0 || idx >= parts.length)
            return;
        String v = parts[idx];
        if (v == null || v.isEmpty())
            return;
        if (cb.getItems().contains(v)) {
            if (overwriteAlways || cb.getValue() == null)
                cb.setValue(v);
        } else {
            // optionally accept arbitrary values:
            // cb.setValue(v);
        }
    }

    // ---------- Batch UI ----------
    public static Parent createBatch(
            BiConsumer<Map<String, String>, Consumer<Boolean>> onSave,
            List<Map<String, String>> batchRows) {

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #ffffff, #f7f9fb);");

        // --- Top Status (centered, big & colorful) ---
        Label status = new Label(); // we'll update this everywhere
        status.getStyleClass().add("status-banner");
        status.setId("statusBanner");
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setStyle("""
                    -fx-font-size: 40px;
                    -fx-font-weight: 900;
                    -fx-text-fill: #0D47A1;
                    -fx-background-color: #E3F2FD;
                    -fx-background-radius: 8;
                    -fx-padding: 10 18;
                """);

        HBox statusWrap = new HBox(status);
        statusWrap.setAlignment(Pos.CENTER);
        statusWrap.setPadding(new Insets(6, 0, 12, 0));
        HBox.setHgrow(status, Priority.ALWAYS);

        // --- Inputs ---
        TextField fullName = new TextField();
        TextField bsguid = new TextField();
        ComboBox<String> participationType = new ComboBox<>();
        participationType.getItems().addAll("guide", "scout", "ranger");
        TextField bsgDistrict = new TextField();
        TextField email = new TextField();
        TextField phoneNumber = new TextField();
        TextField bsgState = new TextField();
        TextField memberTyp = new TextField();
        TextField unitNam = new TextField();
        ComboBox<String> rank_or_section = new ComboBox<>();
        rank_or_section.getItems().addAll("guide", "scout", "ranger");
        DatePicker dateOfBirth = new DatePicker();
        TextField age = new TextField();

        GridPane left = new GridPane();
        left.setHgap(10);
        left.setVgap(10);
        GridPane right = new GridPane();
        right.setHgap(10);
        right.setVgap(10);

        addField(left, 0, "FullName", fullName);
        addField(left, 1, "BSGUID", bsguid);
        addField(left, 2, "ParticipationType", participationType);
        addField(left, 3, "bsgDistrict", bsgDistrict);
        addField(left, 4, "unitNam", unitNam);
        addField(left, 5, "dateOfBirth", dateOfBirth);

        addField(right, 0, "Email", email);
        addField(right, 1, "phoneNumber", phoneNumber);
        addField(right, 2, "bsgState", bsgState);
        addField(right, 3, "memberTyp", memberTyp);
        addField(right, 4, "rank_or_section", rank_or_section);
        addField(right, 5, "age", age);

        HBox columns = new HBox(40, left, right);
        columns.setPadding(new Insets(8));

        Button writeNextBtn = new Button("Write & Next");
        Button skipBtn = new Button("Skip / Next");
        Button stopBtn = new Button("Stop Batch");
        HBox controls = new HBox(10, writeNextBtn, skipBtn, stopBtn);
        controls.setAlignment(Pos.CENTER_RIGHT);

        // --- Center layout (status moved to TOP) ---
        VBox center = new VBox(8, statusWrap, columns, controls);
        center.setPadding(new Insets(10));
        root.setCenter(center);

        final int total = batchRows == null ? 0 : batchRows.size();
        final int[] index = new int[] { 0 };
        final boolean[] running = new boolean[] { true };

        // helper to pick a value from map by likely keys (case-insensitive)
        java.util.function.BiFunction<Map<String, String>, String, String> pick = (map, key) -> {
            if (map == null)
                return "";
            if (map.containsKey(key))
                return map.get(key);
            for (String k : map.keySet())
                if (k.equalsIgnoreCase(key))
                    return map.get(k);
            return "";
        };

        Runnable fillCurrent = () -> {
            if (index[0] < 0 || index[0] >= total) {
                fullName.clear();
                bsguid.clear();
                participationType.setValue(null);
                bsgDistrict.clear();
                email.clear();
                phoneNumber.clear();
                bsgState.clear();
                memberTyp.clear();
                unitNam.clear();
                rank_or_section.setValue(null);
                dateOfBirth.setValue(null);
                age.clear();
                return;
            }
            Map<String, String> cur = batchRows.get(index[0]);
            fullName.setText(pick.apply(cur, "FullName"));
            bsguid.setText(pick.apply(cur, "BSGUID"));
            String p = pick.apply(cur, "ParticipationType");
            if (!p.isEmpty())
                participationType.setValue(p);
            bsgDistrict.setText(pick.apply(cur, "bsgDistrict"));
            email.setText(pick.apply(cur, "Email"));
            phoneNumber.setText(pick.apply(cur, "phoneNumber"));
            bsgState.setText(pick.apply(cur, "bsgState"));

            String mt = pick.apply(cur, "memberTyp");
            if (mt.isEmpty())
                mt = pick.apply(cur, "memberType");
            memberTyp.setText(mt);

            String un = pick.apply(cur, "unitNam");
            if (un.isEmpty())
                un = pick.apply(cur, "unitName");
            unitNam.setText(un);

            String r = pick.apply(cur, "rank_or_section");
            if (!r.isEmpty())
                rank_or_section.setValue(r);

            String dob = pick.apply(cur, "dataOfBirth");
            if (dob.isEmpty())
                dob = pick.apply(cur, "dateOfBirth");
            if (!dob.isEmpty()) {
                try {
                    dateOfBirth.setValue(LocalDate.parse(dob));
                } catch (Exception ex) {
                    dateOfBirth.setValue(null);
                }
            } else {
                dateOfBirth.setValue(null);
            }
            age.setText(pick.apply(cur, "age"));

            status.setText("Record " + (index[0] + 1) + " / " + total);
        };

        if (total == 0) {
            status.setText("No rows found.");
            writeNextBtn.setDisable(true);
            skipBtn.setDisable(true);
            stopBtn.setDisable(true);
        } else {
            fillCurrent.run();
            status.setText("Ready for record " + (index[0] + 1) + " / " + total +
                    " — Present card and click Write & Next.");
        }

        writeNextBtn.setOnAction(evt -> {
            if (!running[0])
                return;
            if (index[0] < 0 || index[0] >= total) {
                status.setText("No more rows.");
                return;
            }
            Map<String, String> cur = batchRows.get(index[0]);

            // Build map for AccessDb.insertAttendee (canonical keys!)
            Map<String, String> data = new LinkedHashMap<>();
            String fn = pick.apply(cur, "FullName");
            String bs = pick.apply(cur, "BSGUID");
            String pt = pick.apply(cur, "ParticipationType");
            String bd = pick.apply(cur, "bsgDistrict");
            String em = pick.apply(cur, "Email");
            String ph = pick.apply(cur, "phoneNumber");
            String st = pick.apply(cur, "bsgState");

            String mt = pick.apply(cur, "memberTyp");
            if (mt.isEmpty())
                mt = pick.apply(cur, "memberType");
            String un = pick.apply(cur, "unitNam");
            if (un.isEmpty())
                un = pick.apply(cur, "unitName");

            String rk = pick.apply(cur, "rank_or_section");
            String dob = pick.apply(cur, "dataOfBirth");
            if (dob.isEmpty())
                dob = pick.apply(cur, "dateOfBirth");
            String ag = pick.apply(cur, "age");

            data.put("FullName", fn);
            data.put("BSGUID", bs);
            data.put("ParticipationType", pt);
            data.put("bsgDistrict", bd);
            data.put("Email", em);
            data.put("phoneNumber", ph);
            data.put("bsgState", st);
            data.put("memberTyp", mt);
            data.put("unitNam", un);
            data.put("rank_or_section", rk);
            data.put("dataOfBirth", dob);
            data.put("age", ag);

            String csv = String.join(",", Arrays.asList(fn, bs, pt, bd, em, ph, st, mt, un, rk, dob, ag));
            data.put("__CSV__", csv);

            // Disable actions during write
            writeNextBtn.setDisable(true);
            skipBtn.setDisable(true);
            stopBtn.setDisable(true);
            status.setText("Writing record " + (index[0] + 1) + " / " + total + " — present card now...");

            // NEW: success/failure finisher
            Consumer<Boolean> finish = success -> Platform.runLater(() -> {
                if (success) {
                    index[0]++;
                    if (!running[0] || index[0] >= total) {
                        status.setText("Batch finished. Processed " + Math.min(total, index[0]) + " rows.");
                        writeNextBtn.setDisable(true);
                        skipBtn.setDisable(true);
                        stopBtn.setDisable(true);
                        return;
                    }
                    fillCurrent.run();
                    status.setText("Ready for record " + (index[0] + 1) + " / " + total
                            + " — Present card and click Write & Next.");
                } else {
                    // stay on same record
                    status.setText("Write failed. Present the card again and click Write & Next.");
                }
                // Re-enable controls for next action (both cases)
                writeNextBtn.setDisable(false);
                skipBtn.setDisable(false);
                stopBtn.setDisable(false);
            });

            try {
                if (onSave != null) {
                    onSave.accept(data, finish);
                } else {
                    // No handler → treat as success to allow stepping through
                    finish.accept(true);
                }
            } catch (Exception ex) {
                status.setText("Write failed: " + ex.getMessage());
                writeNextBtn.setDisable(false);
                skipBtn.setDisable(false);
                stopBtn.setDisable(false);
            }
        });

        skipBtn.setOnAction(evt -> {
            if (!running[0])
                return;
            index[0]++;
            if (index[0] >= total) {
                status.setText("Reached end of batch.");
                writeNextBtn.setDisable(true);
                skipBtn.setDisable(true);
            } else {
                fillCurrent.run();
                status.setText("Skipped. Now at " + (index[0] + 1) + " / " + total);
            }
        });

        stopBtn.setOnAction(evt -> {
            running[0] = false;
            status.setText("Batch stopped by user. Processed " + index[0] + " rows.");
            writeNextBtn.setDisable(true);
            skipBtn.setDisable(true);
            stopBtn.setDisable(true);
        });

        // optional NFC auto-fill during batch (guarded by NFC_BUSY)
        ScheduledExecutorService svc = startNfcAutoFill(root,
                fullName, bsguid, participationType,
                bsgDistrict, email, phoneNumber,
                bsgState, memberTyp, unitNam,
                rank_or_section, dateOfBirth, age,
                false,
                1200);
        if (svc != null) {
            root.getProperties().put("nfc-poller", svc);
        }
        return root;
    }

}
