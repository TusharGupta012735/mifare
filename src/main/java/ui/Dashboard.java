package ui;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import db.AccessDb;
import nfc.SmartMifareReader;
import nfc.SmartMifareWriter;
import javafx.application.Platform;
import javafx.scene.Parent;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.scene.Node;

public class Dashboard extends BorderPane {

    // >>> Attendance poller state (runs ONLY on Attendance tab)
    private volatile Thread attendancePollerThread = null;
    private final AtomicBoolean attendancePollerRunning = new AtomicBoolean(false);
    private final AtomicBoolean onAttendanceTab = new AtomicBoolean(false);
    private volatile String lastSeenUid = null;

    // Make inputs/headings larger & cleaner without touching EntryForm code
    private void prettifyForm(Parent root) {
        // TextFields
        for (Node n : root.lookupAll(".text-field")) {
            n.setStyle("""
                        -fx-font-size: 15px;
                        -fx-padding: 10 12;
                        -fx-background-color: white;
                        -fx-background-radius: 8;
                        -fx-border-color: #BDBDBD;
                        -fx-border-radius: 8;
                    """);
            if (n instanceof Region r) {
                r.setMinHeight(36);
            }
        }

        // ComboBoxes
        for (Node n : root.lookupAll(".combo-box")) {
            n.setStyle("""
                        -fx-font-size: 15px;
                        -fx-padding: 8 10;
                        -fx-background-color: white;
                        -fx-background-radius: 8;
                        -fx-border-color: #BDBDBD;
                        -fx-border-radius: 8;
                    """);
            if (n instanceof ComboBox<?> cb) {
                cb.setVisibleRowCount(12);
            }
            if (n instanceof Region r) {
                r.setMinHeight(36);
            }
        }

        // DatePickers
        for (Node n : root.lookupAll(".date-picker")) {
            n.setStyle("""
                        -fx-font-size: 15px;
                        -fx-padding: 8 10;
                        -fx-background-color: white;
                        -fx-background-radius: 8;
                        -fx-border-color: #BDBDBD;
                        -fx-border-radius: 8;
                    """);
            if (n instanceof Region r) {
                r.setMinHeight(36);
            }
        }

        // TextAreas
        for (Node n : root.lookupAll(".text-area")) {
            n.setStyle("""
                        -fx-font-size: 15px;
                        -fx-padding: 10 12;
                        -fx-background-color: white;
                        -fx-background-radius: 8;
                        -fx-border-color: #BDBDBD;
                        -fx-border-radius: 8;
                    """);
        }

        // Labels (only inside form – skip the big status banner)
        for (Node n : root.lookupAll(".label")) {
            if (n.getStyleClass().contains("status-banner") || "statusBanner".equals(n.getId())) {
                continue; // don't override the banner styling
            }
            n.setStyle("-fx-font-size: 14px; -fx-text-fill: #263238;");
        }

        // Buttons (only inside the batch form container)
        for (Node n : root.lookupAll(".button")) {
            n.setStyle("""
                        -fx-background-color: #1976D2;
                        -fx-text-fill: white;
                        -fx-font-weight: bold;
                        -fx-font-size: 14px;
                        -fx-background-radius: 8;
                        -fx-padding: 8 14;
                        -fx-cursor: hand;
                    """);
            n.setOnMouseEntered(e -> n.setStyle("""
                        -fx-background-color: #2196F3;
                        -fx-text-fill: white;
                        -fx-font-weight: bold;
                        -fx-font-size: 14px;
                        -fx-background-radius: 8;
                        -fx-padding: 8 14;
                        -fx-cursor: hand;
                    """));
            n.setOnMouseExited(e -> n.setStyle("""
                        -fx-background-color: #1976D2;
                        -fx-text-fill: white;
                        -fx-font-weight: bold;
                        -fx-font-size: 14px;
                        -fx-background-radius: 8;
                        -fx-padding: 8 14;
                        -fx-cursor: hand;
                    """));
        }

        if (root instanceof Region r) {
            r.setStyle("-fx-background-color: transparent;");
        }
    }

    private final StackPane contentArea = new StackPane();

    public Dashboard() {
        // --- Buttons ---
        Button attendanceBtn = new Button("Attendance");
        Button entryFormBtn = new Button("Entry Form");
        Button infoBtn = new Button("Information");
        Button batchBtn = new Button("Batch (Filter)");
        Button reportBtn = new Button("Report");
        Button importBtn = new Button("Import Excel"); // NEW

        // --- Common Button Style ---
        String btnStyle = """
                    -fx-background-color: #1976d2;
                    -fx-text-fill: white;
                    -fx-font-weight: bold;
                    -fx-font-size: 15px;
                    -fx-background-radius: 8;
                    -fx-padding: 12 20;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 1);
                """;

        String hoverStyle = """
                    -fx-background-color: #2196f3;
                    -fx-text-fill: white;
                    -fx-font-weight: bold;
                    -fx-font-size: 15px;
                    -fx-background-radius: 8;
                    -fx-padding: 12 20;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 2);
                """;

        for (Button btn : new Button[] { attendanceBtn, entryFormBtn, batchBtn, reportBtn, infoBtn, importBtn }) {
            btn.setStyle(btnStyle);
            btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
            btn.setOnMouseExited(e -> btn.setStyle(btnStyle));
        }

        // --- Navbar Layout (added Import Excel at the end) ---
        HBox navBar = new HBox(20, attendanceBtn, entryFormBtn, batchBtn, reportBtn, infoBtn, importBtn);
        navBar.setPadding(new Insets(15, 20, 15, 20));
        navBar.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #1565c0, #0d47a1); -fx-alignment: center; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);");

        for (Button btn : new Button[] { attendanceBtn, entryFormBtn, batchBtn, reportBtn, infoBtn, importBtn }) {
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.setMaxWidth(Double.MAX_VALUE);
        }

        // --- Default Content ---
        contentArea.setPadding(new Insets(20));
        setContent(AttendancePage.create());
        // >>> Mark as on Attendance initially and start poller
        onAttendanceTab.set(true);
        startAttendancePoller();

        // --- Add Components to Layout ---
        setTop(navBar);
        setCenter(contentArea);

        // --- Actions ---
        attendanceBtn.setOnAction(e -> {
            // >>> Switch to Attendance: ensure ONLY this tab runs the poller
            onAttendanceTab.set(true);
            setContent(AttendancePage.create()); // safe to refresh view
            startAttendancePoller();
        });

        entryFormBtn.setOnAction(e -> {
            // >>> Leaving Attendance: stop poller
            leaveAttendance();
            Parent form = EntryForm.create((formData, done) -> {
                new Thread(() -> {
                    long dbId = -1;
                    try {
                        String textToWrite = formData.get("__CSV__");
                        if (textToWrite == null) {
                            textToWrite = formData.values().stream()
                                    .map(v -> v == null ? "" : v.trim())
                                    .collect(Collectors.joining(","));
                        }

                        EntryForm.setNfcBusy(true);
                        String cardUid = null;
                        try {
                            SmartMifareWriter.WriteResult result = SmartMifareWriter.writeText(textToWrite);
                            if (result != null)
                                cardUid = result.uid;
                        } catch (Exception nfcEx) {
                            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(
                                    1);
                            final boolean[] proceed = new boolean[1];
                            Platform.runLater(() -> {
                                Alert warn = new Alert(Alert.AlertType.CONFIRMATION,
                                        "Writing to NFC card failed: " + nfcEx.getMessage() + "\n\n" +
                                                "Continue and save to DB without assigning a card?",
                                        ButtonType.YES, ButtonType.NO);
                                warn.setHeaderText(null);
                                proceed[0] = warn.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
                                latch.countDown();
                            });
                            try {
                                latch.await();
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            if (!proceed[0])
                                return;
                        } finally {
                            EntryForm.setNfcBusy(false);
                        }

                        try {
                            dbId = AccessDb.insertAttendee(formData, cardUid);
                        } catch (Exception dbEx) {
                            final String msg = "DB insert failed: " + dbEx.getMessage();
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                            return;
                        }

                        final long idFinal = dbId;
                        Platform.runLater(() -> {
                            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                                    "Saved successfully to database. (id=" + idFinal + ")",
                                    ButtonType.OK);
                            ok.setHeaderText(null);
                            ok.showAndWait();
                        });

                    } catch (Exception ex2) {
                        final String err = ex2.getMessage() == null ? ex2.toString() : ex2.getMessage();
                        Platform.runLater(() -> {
                            Alert a = new Alert(Alert.AlertType.ERROR, "Operation failed: " + err, ButtonType.OK);
                            a.setHeaderText(null);
                            a.showAndWait();
                        });
                    } finally {
                        if (done != null)
                            done.run();
                    }
                }, "writer-db-thread").start();
            });

            setContent(form);
        });

        infoBtn.setOnAction(e -> {
            // >>> Leaving Attendance: stop poller
            leaveAttendance();

            Label waitLbl = new Label("📡 Present card to read info...");
            waitLbl.setStyle("""
                        -fx-font-size: 24px;
                        -fx-font-weight: bold;
                        -fx-text-fill: #1565C0;
                        -fx-background-color: #E3F2FD;
                        -fx-padding: 20 30;
                        -fx-background-radius: 10;
                        -fx-border-color: #1565C0;
                        -fx-border-radius: 10;
                        -fx-border-width: 2;
                        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
                    """);
            setContent(new StackPane(waitLbl));

            new Thread(() -> {
                EntryForm.setNfcBusy(true);
                SmartMifareReader.ReadResult rr = null;
                try {
                    rr = SmartMifareReader.readUIDWithData(10_000);
                } catch (Exception ex) {
                    /* ignore */ } finally {
                    EntryForm.setNfcBusy(false);
                }

                SmartMifareReader.ReadResult finalRR = rr;
                Platform.runLater(() -> {
                    if (finalRR == null || finalRR.uid == null) {
                        Label err = new Label("❌ No card read (timed out or error).");
                        err.setStyle("""
                                    -fx-font-size: 24px;
                                    -fx-font-weight: bold;
                                    -fx-text-fill: #C62828;
                                    -fx-background-color: #FFEBEE;
                                    -fx-padding: 20 30;
                                    -fx-background-radius: 10;
                                    -fx-border-color: #C62828;
                                    -fx-border-radius: 10;
                                    -fx-border-width: 2;
                                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
                                """);
                        setContent(new StackPane(err));
                        return;
                    }
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put("UID", finalRR.uid);
                    map.put("Readable data",
                            (finalRR.data == null || finalRR.data.trim().isEmpty()) ? "(none)" : finalRR.data.trim());

                    GridPane grid = new GridPane();
                    grid.setVgap(12);
                    grid.setHgap(20);
                    grid.setPadding(new Insets(20));
                    grid.setStyle("""
                                -fx-background-color: white;
                                -fx-background-radius: 12;
                                -fx-border-radius: 12;
                                -fx-border-color: #E0E0E0;
                                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 2);
                            """);
                    int row = 0;
                    for (Map.Entry<String, String> en : map.entrySet()) {
                        grid.add(new Label(en.getKey() + ":"), 0, row);
                        grid.add(new Label(en.getValue()), 1, row);
                        row++;
                    }
                    VBox container = new VBox(16, new Label("Card Information"), grid);
                    container.setPadding(new Insets(20));
                    setContent(container);
                });
            }, "info-read-thread").start();
        });

        // Batch (Filter)
        batchBtn.setOnAction(e -> {
            // >>> Leaving Attendance: stop poller
            leaveAttendance();

            List<Map<String, String>> rows = BatchFilterDialog.showAndFetch(
                    this.getScene() == null ? null : this.getScene().getWindow());
            if (rows == null || rows.isEmpty())
                return;

            // Top banner (centered)
            int total = rows.size();
            Label banner = new Label("Ready for records — " + total + " selected");
            banner.setStyle("""
                        -fx-font-size: 22px;
                        -fx-font-weight: 900;
                        -fx-text-fill: #0D47A1;
                    """);
            HBox bannerWrap = new HBox(banner);
            bannerWrap.setAlignment(Pos.CENTER);
            bannerWrap.setPadding(new Insets(8, 0, 12, 0));

            java.util.concurrent.atomic.AtomicInteger processed = new java.util.concurrent.atomic.AtomicInteger(0);

            // SAME editable form UI
            Parent batch = EntryForm.createBatch((formData, done) -> {
                new Thread(() -> {
                    try {
                        String textToWrite = formData.get("__CSV__");
                        if (textToWrite == null) {
                            textToWrite = formData.values().stream()
                                    .map(v -> v == null ? "" : v.trim())
                                    .collect(Collectors.joining(","));
                        }

                        String cardUid = null;
                        EntryForm.setNfcBusy(true);
                        try {
                            SmartMifareWriter.WriteResult wr = SmartMifareWriter.writeText(textToWrite);
                            if (wr != null)
                                cardUid = wr.uid;
                        } catch (Exception nfcEx) {
                            System.err.println("[WARN] NFC write failed: " + nfcEx.getMessage());
                        } finally {
                            EntryForm.setNfcBusy(false);
                        }

                        try {
                            AccessDb.insertAttendee(formData, cardUid);
                            int doneCount = processed.incrementAndGet();
                            Platform.runLater(() -> {
                                banner.setText("Processed " + doneCount + " / " + total);
                                Alert ok = new Alert(Alert.AlertType.INFORMATION,
                                        "Saved: " + formData.getOrDefault("FullName", "(no name)"),
                                        ButtonType.OK);
                                ok.setHeaderText(null);
                                ok.showAndWait();
                            });
                        } catch (Exception dbEx) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR,
                                        "DB insert failed: " + dbEx.getMessage(), ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                        }
                    } finally {
                        if (done != null)
                            done.run();
                    }
                }, "batch-filter-thread").start();
            }, rows);

            // Make the existing form look cleaner/bigger WITHOUT changing its structure
            prettifyForm(batch);

            VBox page = new VBox(0, bannerWrap, batch);
            page.setPadding(new Insets(12, 20, 12, 20));
            setContent(page);
        });

        // Report placeholder
        reportBtn.setOnAction(e -> {
            // >>> Leaving Attendance: stop poller
            leaveAttendance();
            setContent("📊 Report Page");
        });

        // NEW: Import Excel wiring
        importBtn.setOnAction(e -> {
            // >>> Leaving Attendance: stop poller
            leaveAttendance();

            int imported = ExcelImportDialog.show(
                    this.getScene() == null ? null : (javafx.stage.Stage) this.getScene().getWindow());
            if (imported >= 0) {
                Alert a = new Alert(Alert.AlertType.INFORMATION,
                        imported + " row(s) imported with status='F'. You can now use Batch (Filter).",
                        ButtonType.OK);
                a.setHeaderText(null);
                a.showAndWait();
            }
        });
    }

    // --- Helper Method for Page Switching with Animation ---
    private void setContent(Node node) {
        // stop any NFC poller from previous view (EntryForm-owned pollers)
        if (!contentArea.getChildren().isEmpty()) {
            Node prev = contentArea.getChildren().get(0);
            EntryForm.stopNfcPolling(prev);
        }

        contentArea.getChildren().setAll(node);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), node);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void setContent(String text) {
        Text newText = new Text(text);
        newText.setStyle("-fx-font-size: 20px; -fx-fill: #212121; -fx-font-weight: 600;");
        setContent(newText);
    }

    // >>> Attendance poller control

    /**
     * Start (or restart) the attendance poller. Safe to call repeatedly.
     * Polls the reader with a short timeout and watches for a transition:
     * CARD PRESENT -> CARD ABSENT. On removal, refresh Attendance UI so it asks to
     * tap again.
     */
    private void startAttendancePoller() {
        // If already running, keep it; otherwise start fresh
        if (attendancePollerRunning.get())
            return;

        attendancePollerRunning.set(true);
        attendancePollerThread = new Thread(() -> {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            final int READ_TIMEOUT_MS = 350; // short, non-blocking-ish
            final int IDLE_SLEEP_MS = 150; // be gentle on CPU

            while (attendancePollerRunning.get()) {
                // Run only while we're actually on the Attendance tab
                if (!onAttendanceTab.get())
                    break;

                boolean hadCard = (lastSeenUid != null);
                SmartMifareReader.ReadResult rr = null;
                try {
                    // Non-intrusive short read; exceptions are swallowed
                    rr = SmartMifareReader.readUIDWithData(READ_TIMEOUT_MS);
                } catch (Throwable t) {
                    // ignore to keep the loop resilient
                }

                if (rr != null && rr.uid != null && !rr.uid.isBlank()) {
                    // Card currently present
                    lastSeenUid = rr.uid;
                } else {
                    // No card detected in this slice
                    if (hadCard) {
                        // Transition: card was present and now absent -> refresh view & ask to tap
                        // again
                        lastSeenUid = null;
                        Platform.runLater(() -> {
                            if (onAttendanceTab.get()) {
                                // Recreate the Attendance page (lightweight) to show the prompt again
                                setContent(AttendancePage.create());
                            }
                        });
                    }
                }

                try {
                    Thread.sleep(IDLE_SLEEP_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            attendancePollerRunning.set(false);
        }, "attendance-poller");
        attendancePollerThread.setDaemon(true);
        attendancePollerThread.start();
    }

    /**
     * Called whenever we navigate away from the Attendance tab.
     * Ensures the poller is fully stopped and state cleared (so other tabs aren't
     * affected).
     */
    private void leaveAttendance() {
        onAttendanceTab.set(false);
        stopAttendancePoller();
    }

    private void stopAttendancePoller() {
        if (!attendancePollerRunning.get()) {
            lastSeenUid = null;
            return;
        }
        attendancePollerRunning.set(false);
        lastSeenUid = null;
        Thread t = attendancePollerThread;
        attendancePollerThread = null;
        if (t != null) {
            t.interrupt();
        }
    }
}
