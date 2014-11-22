package martinandersson.com.client.pages;

import com.nimbusds.srp6.SRP6Exception;
import java.math.BigInteger;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javax.json.JsonObject;
import martinandersson.com.client.FrameController;
import martinandersson.com.client.ServerConnection;
import martinandersson.com.client.login.Authenticate;
import martinandersson.com.client.login.ClientProcedures;

/**
 * Controller of page 2: register and authenticate user.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class Page2Controller implements PageController
{
    private static final Logger LOGGER = Logger.getLogger(Page2Controller.class.getName());
    
    
    
    @FXML
    private GridPane content;
    
    @FXML
    private TextField tfUsername1, tfUsername2;
    
    @FXML
    private TextField tfPassword1, tfPassword2;
    
    @FXML
    private Button btnRegister, btnAuthenticate;
    
    private FrameController frameController;
    
    private final BooleanProperty loading = new SimpleBooleanProperty();
    
    private final BooleanProperty registered = new SimpleBooleanProperty();
    
    

    @Override
    public void setFrameController(FrameController frameController) {
        this.frameController = frameController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        btnRegister.setOnAction(this::register);
        btnAuthenticate.setOnAction(this::authenticate);
        
        
        
        // Headache start:
        
        content.disableProperty().bind(loading);
        
        btnRegister.disableProperty().bind(
                tfUsername1.textProperty().isNotEmpty().and(
                tfPassword1.textProperty().isNotEmpty()).not()
                        .or(registered));
        
        btnAuthenticate.disableProperty().bind(
                tfUsername2.textProperty().isNotEmpty().and(
                tfPassword2.textProperty().isNotEmpty()).not());
        
        tfUsername1.disableProperty().bind(registered);
        tfPassword1.disableProperty().bind(registered);
        
        tfUsername2.disableProperty().bind(registered.not());
        tfPassword2.disableProperty().bind(registered.not());
        
        // ..headache finished.
        
        
        
        // Equal width of buttons:
        Platform.runLater(() -> btnRegister.setMinWidth(btnAuthenticate.getWidth()));
    }
    
    private void register(ActionEvent ignored) {
        loading.set(true);
        
        String username = tfUsername1.getText();
        String password = tfPassword1.getText();
        
        ForkJoinPool.commonPool().execute(() -> {
            try {
                ClientProcedures.registerUser(ServerConnection.getInstance(), username, password);
                Platform.runLater(() -> {
                    registered.set(true);
                    tfUsername2.setText(username);
                    tfPassword2.setText(password); });
            }
            finally {
                Platform.runLater(() -> loading.set(false));
            }
        });
    }
    
    private void authenticate(ActionEvent event) {
        loading.set(true);
        
        String username = tfUsername2.getText();
        String password = tfPassword2.getText();
        
        ForkJoinPool.commonPool().execute(() -> {
            try {
                final ServerConnection conn = ServerConnection.getInstance();
                
                Authenticate auth = new Authenticate(conn);
                auth.step1(username, password);
                 
                // Get salt and B from server (yes, same salt)
                try {
                    JsonObject saltAndB = conn.receiveNext();
                    auth.step2(saltAndB);
                } catch (InterruptedException | SRP6Exception e) {
                    LOGGER.log(Level.WARNING, "Caught exception going from step 1 to step 2: ", e);
                    btnAuthenticate.setDisable(false);
                    return;
                }
                
                // Get M2 from server
                try {
                    JsonObject m2 = conn.receiveNext();
                    auth.step3(m2);
                } catch (InterruptedException | SRP6Exception e) {
                    LOGGER.log(Level.WARNING, "Caught exception going from step 2 to step 3: ", e);
                    btnAuthenticate.setDisable(false);
                    return;
                }
                
                BigInteger sessionKey = auth.getSessionKey();

                // This key will be random, valid for this session only:
                LOGGER.info(() -> "All parties authenticated. Using session key: " + sessionKey);
                
                frameController.gotoNextPage(sessionKey);
            }
            finally {
                Platform.runLater(() -> loading.set(false));
            }
        });
    }
}