package ui;

import db.AccessDb;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Window;

import java.util.*;

public final class BatchFilterDialog {

    private BatchFilterDialog() {
    }

    public static final class Result {
        public final String state;
        public final String category;
        public final boolean onlyStatusF;

        public Result(String state, String category, boolean onlyStatusF) {
            this.state = state;
            this.category = category;
            this.onlyStatusF = onlyStatusF;
        }
    }

    private static final String ALL_MARKER = "— All —";

    /** Show the dialog and fetch rows. Returns null if cancelled or none found. */
    public static List<Map<String, String>> showAndFetch(Window owner) {
        // --- Load distinct lists ---
        List<String> states = safe(() -> AccessDb.fetchDistinctStates(), List.of());
        List<String> cats = safe(() -> AccessDb.fetchDistinctExcelCategories(), List.of());

        // Add "All" at top
        states = withAll(states);
        cats = withAll(cats);

        Dialog<Result> dlg = new Dialog<>();
        dlg.setTitle("Batch Filter");
        if (owner != null)
            dlg.initOwner(owner);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // --- Root container with padding ---
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18));

        // --- Top banner: “Ready for records — …” (starts neutral, gets updated after
        // fetch) ---
        Label banner = new Label("Ready for records — set filters and press OK");
        banner.setStyle("""
                    -fx-font-weight: 800;
                    -fx-text-fill: #0D47A1;
                """);
        // Responsive font size: scales with dialog width but capped
        banner.fontProperty().bind(Bindings.createObjectBinding(
                () -> Font.font(Math.max(16, Math.min(26, dlg.getDialogPane().getWidth() / 20))),
                dlg.getDialogPane().widthProperty()));
        StackPane bannerWrap = new StackPane(banner);
        StackPane.setAlignment(banner, Pos.CENTER);
        bannerWrap.setPadding(new Insets(6, 0, 16, 0));
        root.setTop(bannerWrap);

        // --- “Card” with inputs ---
        VBox card = new VBox(14);
        card.setPadding(new Insets(16));
        card.setStyle("""
                    -fx-background-color: white;
                    -fx-background-radius: 12;
                    -fx-border-radius: 12;
                    -fx-border-color: #E0E0E0;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 3);
                """);

        Label title = new Label("📂 Prepare Batch");
        title.setStyle("-fx-text-fill: #1565C0; -fx-font-weight: 800;");
        title.fontProperty().bind(Bindings.createObjectBinding(
                () -> Font.font(Math.max(16, Math.min(22, dlg.getDialogPane().getWidth() / 28))),
                dlg.getDialogPane().widthProperty()));

        // Row: State
        ComboBox<String> stateBox = new ComboBox<>();
        stateBox.getItems().setAll(states);
        stateBox.getSelectionModel().selectFirst();
        stateBox.setEditable(true);
        stylizeCombo(stateBox);

        Button stateClear = new Button("Clear");
        styleGhostBtn(stateClear);
        stateClear.setOnAction(e -> {
            stateBox.getEditor().clear();
            stateBox.getSelectionModel().selectFirst();
        });

        HBox stateRow = labeledRow("State", stateBox, stateClear);

        // Row: Excel Category
        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().setAll(cats);
        categoryBox.getSelectionModel().selectFirst();
        categoryBox.setEditable(true);
        stylizeCombo(categoryBox);

        Button categoryClear = new Button("Clear");
        styleGhostBtn(categoryClear);
        categoryClear.setOnAction(e -> {
            categoryBox.getEditor().clear();
            categoryBox.getSelectionModel().selectFirst();
        });

        HBox catRow = labeledRow("Excel Category", categoryBox, categoryClear);

        // Row: Only F
        CheckBox onlyF = new CheckBox("Only status = 'f' (unprocessed)");
        onlyF.setSelected(true);
        onlyF.setStyle("-fx-font-size: 13px; -fx-text-fill: #37474F;");

        card.getChildren().addAll(title, stateRow, catRow, onlyF);

        // Center the card, keep layout comfy
        root.setCenter(card);

        // Footer hint
        Label hint = new Label("Tip: Start typing to filter the drop-down lists.");
        hint.setStyle("-fx-text-fill: #607D8B; -fx-font-size: 12px;");
        BorderPane.setMargin(hint, new Insets(10, 0, 0, 4));
        root.setBottom(hint);

        dlg.getDialogPane().setContent(root);

        // Enable/disable OK like before
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.disableProperty().bind(
                stateBox.valueProperty().isEqualTo(ALL_MARKER)
                        .or(stateBox.valueProperty().isNull())
                        .and(categoryBox.valueProperty().isEqualTo(ALL_MARKER)
                                .or(categoryBox.valueProperty().isNull()))
                        .and(onlyF.selectedProperty().not()));

        // Enter -> OK (if enabled)
        dlg.getDialogPane().setOnKeyPressed(ke -> {
            if (ke.getCode() == KeyCode.ENTER && !okBtn.isDisabled()) {
                okBtn.fire();
                ke.consume();
            }
        });

        // Convert result
        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                String state = normalizeBoxValue(stateBox.getEditor().getText());
                String cat = normalizeBoxValue(categoryBox.getEditor().getText());
                return new Result(state, cat, onlyF.isSelected());
            }
            return null;
        });

        Optional<Result> res = dlg.showAndWait();
        if (res.isEmpty())
            return null;

        Result r = res.get();
        try {
            List<Map<String, String>> rows = AccessDb.fetchParticipantsByStateAndCategory(
                    r.state, r.category, r.onlyStatusF);

            if (rows == null || rows.isEmpty()) {
                banner.setText("Ready for records — 0 found");
                info("No matching records.", Alert.AlertType.INFORMATION);
                return null;
            }

            // Success UX: update banner & show confirmation
            banner.setText("Ready for records — " + rows.size() + " found");
            info(rows.size() + " record(s) ready.", Alert.AlertType.INFORMATION);

            return rows;
        } catch (Exception ex) {
            error("DB fetch failed: " + ex.getMessage());
            return null;
        }
    }

    // ---------------- helpers ----------------

    private static void stylizeCombo(ComboBox<String> cb) {
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setVisibleRowCount(12);
        cb.setPromptText("Select or type…");
        cb.setStyle("""
                    -fx-background-color: white;
                    -fx-border-color: #BDBDBD;
                    -fx-border-radius: 8;
                    -fx-background-radius: 8;
                    -fx-padding: 6 10;
                    -fx-font-size: 13px;
                    -fx-text-fill: #212121;
                """);
        HBox.setHgrow(cb, Priority.ALWAYS);
    }

    private static void styleGhostBtn(Button b) {
        b.setStyle("""
                    -fx-background-color: transparent;
                    -fx-text-fill: #1565C0;
                    -fx-font-size: 12px;
                    -fx-underline: true;
                    -fx-padding: 2 6;
                """);
        b.setOnMouseEntered(e -> b.setStyle("""
                    -fx-background-color: rgba(21,101,192,0.06);
                    -fx-text-fill: #0D47A1;
                    -fx-font-size: 12px;
                    -fx-underline: true;
                    -fx-padding: 2 6;
                """));
        b.setOnMouseExited(e -> b.setStyle("""
                    -fx-background-color: transparent;
                    -fx-text-fill: #1565C0;
                    -fx-font-size: 12px;
                    -fx-underline: true;
                    -fx-padding: 2 6;
                """));
    }

    /** Label + control + optional trailing button, aligned nicely */
    private static HBox labeledRow(String label, Node field, Button trailing) {
        Label l = new Label(label + ":");
        l.setStyle("-fx-font-weight: 700; -fx-text-fill: #263238; -fx-font-size: 13px;");

        HBox row = new HBox(10, l, field);
        if (trailing != null)
            row.getChildren().add(trailing);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);

        return row;
    }

    private static void info(String msg, Alert.AlertType type) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private static void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private static <T> T safe(ThrowingSupplier<T> s, T fallback) {
        try {
            return s.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static List<String> withAll(List<String> list) {
        List<String> out = new ArrayList<>(list.size() + 1);
        out.add(ALL_MARKER);
        out.addAll(list);
        return out;
    }

    private static String normalizeBoxValue(String v) {
        if (v == null)
            return null;
        v = v.trim();
        if (v.isEmpty() || v.equals(ALL_MARKER))
            return null;
        return v;
    }
}