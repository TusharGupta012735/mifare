package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class MainUI extends Application {
    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();

        Dashboard dashboard = new Dashboard();
        root.setCenter(dashboard);

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("NFC Attendance System");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
