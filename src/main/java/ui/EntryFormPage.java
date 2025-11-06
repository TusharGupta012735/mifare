// package ui;

// import db.AccessDb;
// import javafx.application.Platform;
// import javafx.beans.binding.Bindings;
// import javafx.beans.binding.DoubleBinding;
// import javafx.concurrent.Service;
// import javafx.concurrent.Task;
// import javafx.geometry.Insets;
// import javafx.geometry.Pos;
// import javafx.scene.Node;
// import javafx.scene.Parent;
// import javafx.scene.control.*;
// import javafx.scene.layout.*;
// import javafx.scene.text.Font;
// import javafx.scene.text.FontWeight;
// import javafx.stage.Window;
// import nfc.SmartMifareReader;
// import nfc.SmartMifareWriter;

// import java.time.LocalDate;
// import java.util.*;
// import java.util.concurrent.*;
// import java.util.concurrent.atomic.AtomicBoolean;
// import java.util.concurrent.atomic.AtomicReference;

// public final class EntryFormPage {

//     private EntryFormPage() {
//     }

//     // ---------------- Global NFC busy flag (shared discipline) ----------------
//     private static final AtomicBoolean NFC_BUSY = new AtomicBoolean(false);

//     private static void setNfcBusy(boolean b) {
//         NFC_BUSY.set(b);
//     }

//     // ---------------- Public factory ----------------
//     public static Parent create() {
//         EntryFormPage page = new EntryFormPage();
//         Parent root = page.buildUI();
//         // Start NFC auto-fill poller and tie lifecycle to this root
//         ScheduledExecutorService svc = page.startNfcAutoFill(root);
//         if (svc != null)
//             root.getProperties().put("nfc-poller", svc);
//         // store self for optional future hooks (if needed)
//         root.getProperties().put("entry-form-page", page);
//         return root;
//     }

//     /**
//      * Optional: call from container when switching away (Dashboard already stops
//      * pollers).
//      */
//     public static void stopNfcPolling(Node root) {
//         if (root == null)
//             return;
//         Object svc = root.getProperties().get("nfc-poller");
//         if (svc instanceof ScheduledExecutorService s) {
//             s.shutdownNow();
//             root.getProperties().remove("nfc-poller");
//         }
//     }

//     // ---------------- UI fields ----------------
//     private Label banner;
//     private TextField fullName, bsguid, bsgDistrict, email, phoneNumber, bsgState, memberTyp, unitNam, age;
//     private ComboBox<String> participationType, rank_or_section;
//     private DatePicker dateOfBirth;
//     private Button saveBtn, clearBtn, eraseBtn;

//     // ---------------- Build UI ----------------
//     private Parent buildUI() {
//         BorderPane root = new BorderPane();
//         root.setPadding(new Insets(20));
//         root.setStyle("-fx-background-color: linear-gradient(to bottom, #ffffff, #f4f6f8);");

//         Label title = new Label("📝 Entry Form");
//         title.setFont(Font.font("System", FontWeight.BOLD, 24));
//         title.setStyle("-fx-text-fill: #1565c0;");
//         HBox header = new HBox(title);
//         header.setAlignment(Pos.CENTER_LEFT);
//         header.setPadding(new Insets(0, 0, 12, 0));

//         banner = new Label();
//         banner.getStyleClass().add("status-banner");
//         banner.setId("statusBanner");
//         banner.setMaxWidth(Double.MAX_VALUE);
//         banner.setWrapText(true);
//         setBannerInfo("Fill details and press Save.");
//         HBox bannerWrap = new HBox(banner);
//         bannerWrap.setAlignment(Pos.CENTER);
//         bannerWrap.setPadding(new Insets(0, 0, 12, 0));

//         // controls
//         fullName = tf("Full name");
//         bsguid = tf("BS GUID");
//         participationType = cb("Select", "guide", "scout", "ranger");
//         bsgDistrict = tf("District");
//         email = tf("example@domain.com");
//         phoneNumber = tf("Phone number");
//         bsgState = tf("State");
//         memberTyp = tf("Member type");
//         unitNam = tf("Unit name");
//         rank_or_section = cb("Select", "guide", "scout", "ranger");
//         dateOfBirth = new DatePicker();
//         dateOfBirth.setPromptText("Date of birth");
//         age = tf("Age");

//         GridPane left = grid();
//         GridPane right = grid();

//         addField(left, 0, "FullName", fullName);
//         addField(left, 1, "BSGUID", bsguid);
//         addField(left, 2, "ParticipationType", participationType);
//         addField(left, 3, "bsgDistrict", bsgDistrict);
//         addField(left, 4, "unitNam", unitNam);
//         addField(left, 5, "dateOfBirth", dateOfBirth);

//         addField(right, 0, "Email", email);
//         addField(right, 1, "phoneNumber", phoneNumber);
//         addField(right, 2, "bsgState", bsgState);
//         addField(right, 3, "memberTyp", memberTyp);
//         addField(right, 4, "rank_or_section", rank_or_section);
//         addField(right, 5, "age", age);

//         HBox columns = new HBox(60, left, right);
//         columns.setAlignment(Pos.CENTER);
//         columns.setPadding(new Insets(10));

//         // responsive fonts
//         DoubleBinding fieldWidthBinding = columns.widthProperty().divide(2.3);
//         for (Control c : new Control[] { fullName, bsguid, participationType, bsgDistrict, email,
//                 phoneNumber, bsgState, memberTyp, unitNam, rank_or_section, dateOfBirth, age }) {
//             ((Region) c).prefWidthProperty().bind(fieldWidthBinding);
//         }
//         DoubleBinding fontSizeBinding = Bindings.createDoubleBinding(() -> {
//             double w = columns.getWidth();
//             double fs = Math.max(12, Math.min(18, w / 70.0));
//             return fs;
//         }, columns.widthProperty());
//         fontSizeBinding.addListener((o, ov, nv) -> {
//             double fs = nv.doubleValue();
//             styleFieldFonts(left, right, fs, title);
//         });

//         // buttons
//         saveBtn = new Button("Save");
//         clearBtn = new Button("Clear");
//         eraseBtn = new Button("Erase Card");

//         saveBtn.setStyle(btnPrimary());
//         clearBtn.setStyle(btnGhost());
//         eraseBtn.setStyle(btnDanger());

//         saveBtn.setOnAction(e -> onSave());
//         clearBtn.setOnAction(e -> clearForm());
//         eraseBtn.setOnAction(e -> onErase());

//         HBox buttons = new HBox(10, saveBtn, clearBtn, eraseBtn);
//         buttons.setAlignment(Pos.CENTER_RIGHT);
//         buttons.setPadding(new Insets(10, 0, 0, 0));

//         VBox center = new VBox(header, bannerWrap, columns, buttons);
//         center.setSpacing(10);
//         center.setPadding(new Insets(10));
//         root.setCenter(center);

//         // init font sizing
//         fontSizeBinding.getValue();
//         return root;
//     }

//     // ---------------- Save flow (Service) ----------------
//     private void onSave() {
//         if (isBlank(fullName) || isBlank(phoneNumber)) {
//             setBannerWarn("Please fill required fields: FullName and phoneNumber.");
//             return;
//         }

//         Map<String, String> data = buildDataMap();
//         String csv = buildCsvForNfc(data);
//         data.put("__CSV__", csv);

//         disableActions(true);
//         setBannerInfo("Submitting… present card (if writing).");

//         SavePipeline svc = new SavePipeline(data); // runs write → DB
//         svc.setOnSucceeded(e -> {
//             disableActions(false);
//             Long id = svc.getValue();
//             if (id != null && id > 0) {
//                 setBannerSuccess("Saved successfully. (id=" + id + ")");
//                 Alert ok = new Alert(Alert.AlertType.INFORMATION,
//                         "Saved successfully to database. (id=" + id + ")", ButtonType.OK);
//                 ok.setHeaderText(null);
//                 ok.showAndWait();
//             } else {
//                 setBannerSuccess("Saved successfully.");
//             }
//         });
//         svc.setOnFailed(e -> {
//             disableActions(false);
//             Throwable ex = svc.getException();
//             String msg = ex == null ? "Unknown error" : (ex.getMessage() == null ? ex.toString() : ex.getMessage());
//             setBannerError("❌ Save failed: " + msg);
//             Alert a = new Alert(Alert.AlertType.ERROR, "Operation failed: " + msg, ButtonType.OK);
//             a.setHeaderText(null);
//             a.showAndWait();
//         });
//         svc.start();
//     }

//     /** Pipeline service: NFC write (optional) → DB insert (always). */
//     private final class SavePipeline extends Service<Long> {
//         private final Map<String, String> data;

//         SavePipeline(Map<String, String> data) {
//             this.data = data;
//         }

//         @Override
//         protected Task<Long> createTask() {
//             return new Task<>() {
//                 @Override
//                 protected Long call() throws Exception {
//                     String textToWrite = data.get("__CSV__");
//                     String cardUid = null;

//                     // Write to NFC (optional): if data is non-empty we try; if fails we ask user.
//                     try {
//                         setNfcBusy(true);
//                         SmartMifareWriter.WriteResult wr = SmartMifareWriter.writeText(textToWrite);
//                         if (wr != null)
//                             cardUid = wr.uid;
//                     } catch (Exception nfcEx) {
//                         // ask user: continue without writing?
//                         final CountDownLatch latch = new CountDownLatch(1);
//                         final boolean[] proceed = new boolean[1];
//                         Platform.runLater(() -> {
//                             Alert warn = new Alert(Alert.AlertType.CONFIRMATION,
//                                     "Writing to NFC card failed: " + nfcEx.getMessage() + "\n\n" +
//                                             "Continue and save to DB without assigning a card?",
//                                     ButtonType.YES, ButtonType.NO);
//                             warn.setHeaderText(null);
//                             proceed[0] = warn.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
//                             latch.countDown();
//                         });
//                         latch.await();
//                         if (!proceed[0])
//                             throw nfcEx;
//                     } finally {
//                         setNfcBusy(false);
//                     }

//                     // DB insert (always attempts)
//                     try {
//                         return AccessDb.insertAttendee(data, cardUid);
//                     } catch (Exception dbEx) {
//                         throw dbEx;
//                     }
//                 }
//             };
//         }
//     }

//     // ---------------- Erase flow ----------------
//     private void onErase() {
//         Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
//                 "This will overwrite (erase) writable data on the presented MIFARE Classic 1K card for sectors\n" +
//                         "the reader can authenticate. Make sure you own the card and want to proceed.\n\nContinue?",
//                 ButtonType.CANCEL, ButtonType.OK);
//         confirm.setHeaderText(null);
//         confirm.showAndWait().ifPresent(btn -> {
//             if (btn != ButtonType.OK)
//                 return;

//             disableActions(true);
//             setBannerInfo("Waiting for card and erasing…");

//             Thread th = new Thread(() -> {
//                 try {
//                     setNfcBusy(true);
//                     nfc.SmartMifareEraser.eraseMemory();
//                     Platform.runLater(() -> setBannerSuccess("✅ Erase complete."));
//                 } catch (Exception ex) {
//                     final String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
//                     Platform.runLater(() -> setBannerError("❌ Erase failed: " + msg));
//                 } finally {
//                     setNfcBusy(false);
//                     Platform.runLater(() -> disableActions(false));
//                 }
//             }, "nfc-eraser-thread");
//             th.setDaemon(true);
//             th.start();
//         });
//     }

//     // ---------------- NFC Auto-fill ----------------
//     private ScheduledExecutorService startNfcAutoFill(Parent root) {
//         ScheduledExecutorService svc = Executors.newSingleThreadScheduledExecutor(r -> {
//             Thread t = new Thread(r, "nfc-poller");
//             t.setDaemon(true);
//             return t;
//         });

//         AtomicReference<String> lastUid = new AtomicReference<>("");
//         Runnable task = () -> {
//             try {
//                 if (NFC_BUSY.get())
//                     return; // respect exclusive NFC use

//                 SmartMifareReader.ReadResult rr = SmartMifareReader.readUIDWithData(1500);
//                 if (rr == null || rr.uid == null || rr.uid.isEmpty())
//                     return;

//                 String uid = rr.uid;
//                 String data = rr.data == null ? "" : rr.data.trim();
//                 if (uid.equals(lastUid.get()))
//                     return; // debounce
//                 lastUid.set(uid);
//                 if (data.isEmpty())
//                     return;

//                 String[] parts = Arrays.stream(data.split(",", -1))
//                         .map(String::trim)
//                         .toArray(String[]::new);

//                 Platform.runLater(() -> fillFromCsv(parts));
//             } catch (Exception ignore) {
//             }
//         };
//         svc.scheduleWithFixedDelay(task, 0, 1000, TimeUnit.MILLISECONDS);

//         // stop on window close / node detach
//         root.sceneProperty().addListener((obs, oldScene, newScene) -> {
//             if (newScene != null) {
//                 Window w = newScene.getWindow();
//                 if (w != null) {
//                     w.setOnHidden(evt -> svc.shutdownNow());
//                 } else {
//                     newScene.windowProperty().addListener((o, oldW, newW) -> {
//                         if (newW != null)
//                             newW.setOnHidden(e -> svc.shutdownNow());
//                     });
//                 }
//             }
//         });
//         root.parentProperty().addListener((o, oldP, newP) -> {
//             if (newP == null)
//                 svc.shutdownNow();
//         });

//         return svc;
//     }

//     private void fillFromCsv(String[] parts) {
//         int i = 0;
//         setField(fullName, parts, i++);
//         setField(bsguid, parts, i++);
//         setCombo(participationType, parts, i++);
//         setField(bsgDistrict, parts, i++);
//         setField(email, parts, i++);
//         setField(phoneNumber, parts, i++);
//         setField(bsgState, parts, i++);
//         setField(memberTyp, parts, i++);
//         setField(unitNam, parts, i++);
//         setCombo(rank_or_section, parts, i++);

//         if (parts.length > i) {
//             String dobStr = parts[i++].trim();
//             if (!dobStr.isEmpty()) {
//                 try {
//                     dateOfBirth.setValue(LocalDate.parse(dobStr));
//                 } catch (Exception ignored) {
//                 }
//             }
//         }
//         if (parts.length > i)
//             setField(age, parts, i++);
//     }

//     // ---------------- Helpers ----------------
//     private static TextField tf(String prompt) {
//         TextField t = new TextField();
//         t.setPromptText(prompt);
//         t.setStyle(baseFieldStyle());
//         return t;
//     }

//     private static ComboBox<String> cb(String prompt, String... items) {
//         ComboBox<String> c = new ComboBox<>();
//         c.getItems().addAll(items);
//         c.setPromptText(prompt);
//         c.setVisibleRowCount(12);
//         c.setStyle(baseFieldStyle());
//         return c;
//     }

//     private static GridPane grid() {
//         GridPane g = new GridPane();
//         g.setHgap(10);
//         g.setVgap(14);
//         return g;
//     }

//     private static void addField(GridPane grid, int row, String labelText, Control field) {
//         Label lbl = new Label(labelText + ":");
//         lbl.setStyle("-fx-font-weight:600;-fx-text-fill:#212121;");
//         GridPane.setConstraints(lbl, 0, row);
//         GridPane.setConstraints(field, 1, row);
//         grid.getChildren().addAll(lbl, field);
//     }

//     private Map<String, String> buildDataMap() {
//         String fn = s(fullName);
//         String bs = s(bsguid);
//         String pt = v(participationType);
//         String bd = s(bsgDistrict);
//         String em = s(email);
//         String ph = s(phoneNumber);
//         String st = s(bsgState);
//         String mt = s(memberTyp);
//         String un = s(unitNam);
//         String rk = v(rank_or_section);
//         LocalDate dob = dateOfBirth.getValue();
//         String dobStr = dob == null ? "" : dob.toString();
//         String ag = s(age);

//         Map<String, String> m = new LinkedHashMap<>();
//         m.put("FullName", fn);
//         m.put("BSGUID", bs);
//         m.put("ParticipationType", pt);
//         m.put("bsgDistrict", bd);
//         m.put("Email", em);
//         m.put("phoneNumber", ph);
//         m.put("bsgState", st);
//         m.put("memberTyp", mt);
//         m.put("unitNam", un);
//         m.put("rank_or_section", rk);
//         m.put("dataOfBirth", dobStr); // your AccessDb supports dataOfBirth/dateOfBirth flexibly
//         m.put("age", ag);
//         return m;
//     }

//     private static String buildCsvForNfc(Map<String, String> d) {
//         return String.join(",", Arrays.asList(
//                 d.getOrDefault("FullName", ""),
//                 d.getOrDefault("BSGUID", ""),
//                 d.getOrDefault("ParticipationType", ""),
//                 d.getOrDefault("bsgDistrict", ""),
//                 d.getOrDefault("Email", ""),
//                 d.getOrDefault("phoneNumber", ""),
//                 d.getOrDefault("bsgState", ""),
//                 d.getOrDefault("memberTyp", ""),
//                 d.getOrDefault("unitNam", ""),
//                 d.getOrDefault("rank_or_section", ""),
//                 d.getOrDefault("dataOfBirth", ""),
//                 d.getOrDefault("age", "")));
//     }

//     private void clearForm() {
//         fullName.clear();
//         bsguid.clear();
//         participationType.setValue(null);
//         bsgDistrict.clear();
//         email.clear();
//         phoneNumber.clear();
//         bsgState.clear();
//         memberTyp.clear();
//         unitNam.clear();
//         rank_or_section.setValue(null);
//         dateOfBirth.setValue(null);
//         age.clear();
//         setBannerInfo("Form cleared.");
//     }

//     private void disableActions(boolean b) {
//         saveBtn.setDisable(b);
//         clearBtn.setDisable(b);
//         eraseBtn.setDisable(b);
//     }

//     private static String s(TextField tf) {
//         return tf.getText() == null ? "" : tf.getText().trim();
//     }

//     private static String v(ComboBox<String> cb) {
//         return cb.getValue() == null ? "" : cb.getValue().trim();
//     }

//     private static boolean isBlank(TextField tf) {
//         return s(tf).isEmpty();
//     }

//     private static void setField(TextField tf, String[] parts, int idx) {
//         if (idx < parts.length) {
//             String v = parts[idx];
//             if (v != null && (tf.getText() == null || tf.getText().isEmpty()))
//                 tf.setText(v);
//         }
//     }

//     private static void setCombo(ComboBox<String> cb, String[] parts, int idx) {
//         if (idx < parts.length) {
//             String v = parts[idx];
//             if (v != null && !v.isEmpty() && cb.getValue() == null) {
//                 if (cb.getItems().contains(v))
//                     cb.setValue(v);
//                 // else ignore (or accept arbitrary values if you want)
//             }
//         }
//     }

//     // --------- Styles & banner helpers ----------
//     private static String baseFieldStyle() {
//         return "-fx-background-color: white; -fx-border-color: #bdbdbd; -fx-border-radius: 6;" +
//                 "-fx-background-radius: 6; -fx-padding: 8 10; -fx-text-fill: #212121;";
//     }

//     private static String btnPrimary() {
//         return "-fx-background-color:#2196f3;-fx-text-fill:white;-fx-font-weight:600;" +
//                 "-fx-padding:8 20;-fx-background-radius:6;";
//     }

//     private static String btnGhost() {
//         return "-fx-background-color:#f5f5f5;-fx-text-fill:#424242;-fx-font-weight:600;" +
//                 "-fx-padding:8 20;-fx-background-radius:6;-fx-border-color:#e0e0e0;";
//     }

//     private static String btnDanger() {
//         return "-fx-background-color:#e53935;-fx-text-fill:white;-fx-font-weight:600;" +
//                 "-fx-padding:8 14;-fx-background-radius:6;";
//     }

//     private void styleFieldFonts(GridPane left, GridPane right, double fs, Label title) {
//         left.getChildren().stream().filter(n -> n instanceof Label)
//                 .forEach(n -> n
//                         .setStyle("-fx-font-weight:600;-fx-text-fill:#212121; -fx-font-size:" + (fs - 1) + "px;"));
//         right.getChildren().stream().filter(n -> n instanceof Label)
//                 .forEach(n -> n
//                         .setStyle("-fx-font-weight:600;-fx-text-fill:#212121; -fx-font-size:" + (fs - 1) + "px;"));

//         for (Node n : new Node[] { fullName, bsguid, participationType, bsgDistrict, email,
//                 phoneNumber, bsgState, memberTyp, unitNam, rank_or_section, dateOfBirth, age }) {
//             ((Region) n).setStyle(baseFieldStyle() + " -fx-font-size: " + fs + "px;");
//             if (n instanceof DatePicker dp && dp.getEditor() != null) {
//                 dp.getEditor().setStyle("-fx-font-size:" + fs + "px;");
//             }
//         }
//         title.setFont(Font.font("System", FontWeight.BOLD, Math.max(16, fs + 4)));
//         banner.setStyle(bannerStyleWithFont(Math.max(28, fs + 14)));
//     }

//     private static String bannerStyleWithFont(double sizePx) {
//         return "-fx-font-size: " + sizePx + "px;" +
//                 "-fx-font-weight: 900;" +
//                 "-fx-text-fill: #0D47A1;" +
//                 "-fx-background-color: #E3F2FD;" +
//                 "-fx-background-radius: 10;" +
//                 "-fx-border-color: #90CAF9;" +
//                 "-fx-border-radius: 10;" +
//                 "-fx-border-width: 2;" +
//                 "-fx-padding: 10 18;" +
//                 "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 2);";
//     }

//     private void setBannerInfo(String text) {
//         banner.setText(text);
//         banner.setStyle(bannerStyleWithFont(32));
//     }

//     private void setBannerWarn(String text) {
//         banner.setText(text);
//         banner.setStyle("-fx-font-size:32px;-fx-font-weight:900;-fx-text-fill:#E65100;" +
//                 "-fx-background-color:#FFF8E1;-fx-background-radius:10;-fx-border-color:#FFE082;" +
//                 "-fx-border-radius:10;-fx-border-width:2;-fx-padding:10 18;-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 2);");
//     }

//     private void setBannerSuccess(String text) {
//         banner.setText(text);
//         banner.setStyle("-fx-font-size:32px;-fx-font-weight:900;-fx-text-fill:#2E7D32;" +
//                 "-fx-background-color:#E8F5E9;-fx-background-radius:10;-fx-border-color:#A5D6A7;" +
//                 "-fx-border-radius:10;-fx-border-width:2;-fx-padding:10 18;-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 2);");
//     }

//     private void setBannerError(String text) {
//         banner.setText(text);
//         banner.setStyle("-fx-font-size:32px;-fx-font-weight:900;-fx-text-fill:#C62828;" +
//                 "-fx-background-color:#FFEBEE;-fx-background-radius:10;-fx-border-color:#EF9A9A;" +
//                 "-fx-border-radius:10;-fx-border-width:2;-fx-padding:10 18;-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 2);");
//     }
// }
