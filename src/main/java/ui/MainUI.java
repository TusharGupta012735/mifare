package ui;

import cloudSync.CloudSync;
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

        CloudSync.startBackgroundSync();
        
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("NFC Attendance System");
        stage.show();

        scene.getStylesheets().add(
                getClass().getResource("/ui/styles/app.css").toExternalForm());
    }

    public static void main(String[] args) {
        launch();
    }
}
