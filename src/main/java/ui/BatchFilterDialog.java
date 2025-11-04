package ui;

import db.AccessDb;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Styled dialog that asks for State and Excel Category, then fetches matching
 * rows.
 * OK is enabled when:
 * - state is non-empty OR
 * - category is non-empty OR
 * - "Only status = 'f'" is checked (so you can fetch all 'f' without filters)
 */
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

    /** Show the dialog and fetch rows. Returns null if cancelled or none found. */
    public static List<Map<String, String>> showAndFetch(Window owner) {
        Dialog<Result> dlg = new Dialog<>();
        dlg.setTitle("Batch Filter");
        if (owner != null)
            dlg.initOwner(owner);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        String baseStyleCore = "-fx-background-color: white; -fx-border-color: #bdbdbd; -fx-border-radius: 6; "
                + "-fx-background-radius: 6; -fx-padding: 8 10; -fx-text-fill: #212121;";
        String labelStyle = "-fx-font-weight:600; -fx-text-fill:#212121;";
        String titleStyle = "-fx-text-fill: #1565c0;";

        Label title = new Label("📂 Prepare Batch");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));
        title.setStyle(titleStyle);

        TextField stateField = new TextField();
        stateField.setPromptText("BSGState (e.g., Assam)");
        stateField.setStyle(baseStyleCore);

        TextField categoryField = new TextField();
        categoryField.setPromptText("Excel Category (optional)");
        categoryField.setStyle(baseStyleCore);

        CheckBox onlyF = new CheckBox("Only status = 'f' (unprocessed)");
        onlyF.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(rowLabel("State:", labelStyle), 0, 0);
        grid.add(stateField, 1, 0);
        grid.add(rowLabel("Excel category:", labelStyle), 0, 1);
        grid.add(categoryField, 1, 1);
        grid.add(onlyF, 1, 2);

        VBox box = new VBox(12, title, grid);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12));
        dlg.getDialogPane().setContent(box);

        // OK button enabled if (state not empty) OR (category not empty) OR (onlyF
        // selected)
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.disableProperty().bind(
                stateField.textProperty().isEmpty()
                        .and(categoryField.textProperty().isEmpty())
                        .and(onlyF.selectedProperty().not()));

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                return new Result(
                        opt(stateField.getText()),
                        opt(categoryField.getText()),
                        onlyF.isSelected());
            }
            return null;
        });

        Optional<Result> res = dlg.showAndWait();
        if (res.isEmpty())
            return null;

        Result r = res.get();
        try {
            // AccessDb should implement LIKE & case-insensitivity internally
            List<Map<String, String>> rows = AccessDb.fetchParticipantsByStateAndCategory(
                    r.state, r.category, r.onlyStatusF);

            if (rows == null || rows.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.INFORMATION, "No matching records.", ButtonType.OK);
                a.setHeaderText(null);
                a.showAndWait();
                return null;
            }
            return rows;
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "DB fetch failed: " + ex.getMessage(), ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
            return null;
        }
    }

    private static String opt(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Label rowLabel(String text, String style) {
        Label l = new Label(text);
        l.setStyle(style);
        return l;
    }
}
