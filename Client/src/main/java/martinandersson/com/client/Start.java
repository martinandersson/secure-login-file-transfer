package martinandersson.com.client;

import java.io.IOException;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


/**
 * Will load and display the primary stage.<p>
 * 
 * This is the main entry point of the program and the class itself should be
 * executable as long as you have a JDK installed that include the JavaFX
 * runtime (>= 1.7.0_6).
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class Start extends Application
{
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader frameLoader = new FXMLLoader();
        
        frameLoader.setController(new FrameController(stage, getHostServices()));
        frameLoader.setLocation(Start.class.getResource("Frame.fxml"));
        
        Scene scene = new Scene(frameLoader.load());
        scene.getStylesheets().add(Start.class.getResource("Scene.css").toExternalForm());
        
        stage.setScene(scene);
        
        stage.setTitle("Secure authentication and encryption");
        stage.initStyle(StageStyle.UNIFIED);
        
        stage.show();
    }
}
