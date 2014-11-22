package martinandersson.com.client.pages;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ForkJoinPool;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javax.websocket.DeploymentException;
import martinandersson.com.client.FrameController;
import martinandersson.com.client.ServerConnection;
import org.controlsfx.dialog.Dialogs;

/**
 * Controller of page 1: connect to server.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class Page1Controller implements PageController
{
    private FrameController frameController;
    
    private final BooleanProperty loading = new SimpleBooleanProperty();
    
    
    
    @FXML
    private ToggleGroup radioButtons;
    
    @FXML
    private RadioButton rbCustom;
    
    @FXML
    private RadioButton rbWildFly;
    
    @FXML
    private RadioButton rbGlassFish;
    
    @FXML
    private TextField tfCustom;
    
    @FXML
    private Button btnConnect;
    
    
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        rbCustom.setOnAction(actionEvent -> tfCustom.requestFocus());
        
        tfCustom.focusedProperty().addListener(observable -> {
            if (tfCustom.isFocused()) {
                rbCustom.setSelected(true);
            }
        });
        
        BooleanBinding radioSelected
                = rbCustom.selectedProperty().and(tfCustom.textProperty().isNotEmpty()).or(
                  rbWildFly.selectedProperty()).or(
                  rbGlassFish.selectedProperty());
        
        btnConnect.disableProperty().bind(
                radioSelected.not().or(loading));
        
        btnConnect.setOnAction(this::connect);
    }

    @Override
    public void setFrameController(FrameController frameController) {
        this.frameController = frameController;
    }
    
    private void connect(ActionEvent ignored) {
        loading.set(true);
        
        String url = getURL();
        
        ServerConnection conn = ServerConnection.getInstance();
        
        // Connect at once:
        ForkJoinPool.commonPool().execute(() -> {
            try {
                conn.connectToServer(url);
                frameController.gotoNextPage();
            }
            catch (DeploymentException | IOException e) {
                Platform.runLater(() -> Dialogs.create().showException(e));
            }
            finally {
                Platform.runLater(() -> loading.set(false));
            }
        });
    }
    
    private String getURL() {
        Toggle t = radioButtons.getSelectedToggle();
        
        final String url;
        
        if (t == rbCustom) {
            url = tfCustom.getText().trim();
        }
        else if (t == rbWildFly) {
            url = "ws://localhost:8080/Server-1.0.0-SNAPSHOT/mywebsocket";
        }
        else if (t == rbGlassFish) {
            url = "ws://localhost:8080/Server/mywebsocket";
        }
        else {
            throw new AssertionError("Know shit about this toggle: " + t);
        }
        
        return url;
    }
}