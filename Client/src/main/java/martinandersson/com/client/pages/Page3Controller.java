package martinandersson.com.client.pages;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.OptionalInt;
import java.util.ResourceBundle;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import martinandersson.com.client.Dialogs;
import martinandersson.com.client.FileSender;
import martinandersson.com.client.ServerConnection;
import martinandersson.com.library.AesGcmCipher;
import martinandersson.com.library.ServerStrategy;
import org.controlsfx.dialog.ProgressDialog;

/**
 * Controller of page 3: send files.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class Page3Controller implements PageController
{
    private static final Logger LOGGER = Logger.getLogger(Page3Controller.class.getName());
    
    @FXML
    private Label lblFile;
    
    private long fileSize = -1L;
    
    @FXML
    private Button btnBrowse;
    
    @FXML
    private Slider slider;
    
    @FXML
    private CheckBox cbChunks;
    
    @FXML
    private TextField tfChunkVal;
    
    @FXML
    private CheckBox cbEncrypt,
                     cbTellServer,
                     cbManipulate;
    
    @FXML
    private ComboBox<ServerStrategy> cbStrategy;
    
    @FXML
    private Label lblDescription;
    
    @FXML
    private Button btnSend;
    
    private AesGcmCipher cipher;
    
    private final ObjectProperty<Path> file = new SimpleObjectProperty<>();
    private final BooleanProperty sending = new SimpleBooleanProperty();
    
    
    
    @Override
    public void setPayload(Object sessionKey) {
        try {
            cipher = new AesGcmCipher((BigInteger) sessionKey);
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            logAndDisplay("Cipher failure!", "Failed to setup a new Cipher.", e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        /*
         * NOT proud of the code in this class =) TODO: Use own properties or
         * even sub controllers. Alternatively, change name and move to unknown
         * location.
         */
        
        file.addListener(this::onFileChange);
        file.addListener(this::updateSlider);
        
        disableSlider();
        
        tfChunkVal.textProperty().addListener(this::onChunkSizeChange);
        
        String noFileMessage = lblFile.getText();
        
        lblFile.textProperty().bind(new When(file.isNotNull())
                .then(file.asString()
                        .concat(Bindings.createStringBinding(this::getSelectedFileSuffix, tfChunkVal.textProperty())))
                .otherwise(noFileMessage));
        
        btnBrowse.setOnAction(this::browse);
        
        slider.disableProperty().bind(cbChunks.selectedProperty().not());
        tfChunkVal.disableProperty().bind(slider.disableProperty());
        
        cbChunks.selectedProperty().addListener(observable -> {
            tfChunkVal.setText(cbChunks.isSelected() ?
                    tfChunkVal.getText() : // <-- night hacked invalidation of the file label binding..
                    getSliderValue());     // <-- restore bad inputs in the text field
        });
        
        slider.valueProperty().addListener(observable ->
                tfChunkVal.setText(getSliderValue()));
        
        cbTellServer.disableProperty().bind(cbEncrypt.selectedProperty().not());
        cbManipulate.disableProperty().bind(cbEncrypt.selectedProperty().not().or(cbTellServer.selectedProperty().not()));
        
        cbStrategy.getItems().addAll(ServerStrategy.values());
        lblDescription.disableProperty().bind(cbStrategy.getSelectionModel().selectedItemProperty().isNull());
        
        String noStrategy = lblDescription.getText();
        
        cbStrategy.valueProperty().addListener(observable -> {
            lblDescription.setText(cbStrategy.getValue() != null ?
                    cbStrategy.getValue().getDescription() :
                    noStrategy);
        });
        
        btnSend.disableProperty().bind(
                file.isNull().or(
                cbStrategy.getSelectionModel().selectedItemProperty().isNull()).or(
                sending).or(
                Bindings.createBooleanBinding(() -> !isChunkSizeValid(), tfChunkVal.styleProperty())));
        
        btnSend.setOnAction(this::sendFile);
    }
    
    private void onFileChange(Observable ignored) {
        boolean isNull = file.get() == null;
        
        lblFile.setDisable(isNull);
        cbChunks.setDisable(isNull);

        if (isNull) {
            fileSize = -1;
        }
        else {
            try {
                fileSize = Files.size(file.get());
            } catch (IOException e) {
                logAndDisplay("IO Failure", "Failed to query size of file.", e);
                fileSize = -1;
            }
        }
    }
    
    private void browse(ActionEvent ignored) {
        FileChooser chooser = new FileChooser();
        File picked = chooser.showOpenDialog(btnBrowse.getScene().getWindow());
        if (picked != null) {
            file.set(picked.toPath());
        }
    }
    
    private void updateSlider(Observable ignored) {
        if (file.get() == null) {
            disableSlider();
            return;
        }
        
        if (fileSize == -1) {
            disableSlider();
            return;
        }
        
        final int MB = (int) (fileSize / 1_000_000L);
        
        if (MB == 0) {
            cbChunks.setSelected(false);
            disableSlider();
        }
        else {
            cbChunks.setDisable(false);
            
            final int quarter = MB / 4, min = 1, /* TODO: */ minor, major;
            
            if (quarter == 0) {
                major = 1;
                minor = 0;
            }
            else {
                major = quarter;
                minor = quarter == 1 ? 0 : quarter - 1;
            }
            
            slider.setMin(min);
            slider.setMinorTickCount(minor);
            slider.setMajorTickUnit(major);
            slider.setMax(MB);
            
            slider.setValue(Math.min(slider.getValue(), MB));
            
            // BuggyFX didn't call invalidation listener of the slider property:
            tfChunkVal.setText(getSliderValue());
        }
    }
    
    private void onChunkSizeChange(Observable ignored) {
        String val = tfChunkVal.getText();

        if (val.isEmpty() && tfChunkVal.isDisabled()) {
            tfChunkVal.setStyle("");
        }
        else {
            String bad = "-fx-background-color: -fx-control-inner-background; -fx-border-color: red;";
            
            try {
                int size = Integer.parseInt(val);
                if (size > 0 && size <= slider.getMax()) {
                    slider.setValue(size);
                    tfChunkVal.setStyle("");
                }
                else {
                    tfChunkVal.setStyle(bad);
                }
            }
            catch (NumberFormatException e) {
                tfChunkVal.setStyle(bad);
            }
        }
    }
    
    private boolean isChunkSizeValid() {
        // Valid chunk size only if TextField does not have a bad style applied, see onChunkSizeChange()
        return tfChunkVal.getStyle().isEmpty();
    }
    
    private void disableSlider() {
        cbChunks.setDisable(true);
        slider.setMin(1);
        slider.setMax(1);
        slider.setMinorTickCount(0);
        slider.setMajorTickUnit(1);
        slider.setValue(1);
        tfChunkVal.setText("");
    }
    
    private void sendFile(ActionEvent ignored) {
        sending.set(true);
        
        try {
            Path source = file.get();
            ServerStrategy strategy = cbStrategy.getValue();
            ServerConnection conn = ServerConnection.getInstance();
            
            Cipher encrypt = null;

            FileSender sender = new FileSender(source, strategy);
            
            if (cbChunks.isSelected()) {
                sender.useChunkSize(Long.valueOf(tfChunkVal.getText()) * 1_000_000);
            }
            
            if (cbEncrypt.isSelected()) {
                sender.useCipher(cipher);
            }
            
            if (cbEncrypt.isSelected()) {
                sender.tellServerAboutEncryption(cbTellServer.isSelected());
            }
            
            sender.manipulateBitInMiddle(cbManipulate.isSelected());
            
            ProgressDialog pd = new ProgressDialog(sender);
            pd.setTitle("Sending file..");
            pd.initOwner(btnSend.getScene().getWindow());
            pd.show();

            sender.setOnSucceeded(workerStateEvent -> {
                final String problem = sender.getProblem(),
                             header  = "Successfully sent: " + sender.getBytesSent() + " byte(s)!";
                
                final StringBuilder msg = new StringBuilder();
                
                if (problem.isEmpty()) {
                    msg.append("Server had nothing to complain about.");
                }
                else {
                    msg.append("Server had something to complain about:\n")
                       .append(problem);
                }
                
                msg.append("\n\n")
                   .append("Transfer time: ").append(sender.getTransferDurationTotal()).append("\n")
                   .append("Server's time to respond (decryption if enabled): ").append(sender.getConfirmationDurationTotal()).append("\n\n")
                   .append("Total working time: ").append(sender.getTaskDuration());
                
                final Window owner = btnSend.getScene().getWindow();
                
                if (problem.isEmpty()) {
                    Dialogs.showInformation(owner, "File sent", header, msg.toString());
                }
                else {
                    Dialogs.showWarning(owner, "File sent", header, msg.toString());
                }
            });
            
            sender.setOnFailed(workerStateEvent ->
                    Dialogs.showThrowable(sender.getException(), btnSend.getScene().getWindow()));
            
            sender.runningProperty().addListener(observable -> {
                if (!sender.isRunning()) {
                    sending.set(false);
                }
            });
            
            ForkJoinPool.commonPool().execute(sender);
        }
        finally {
            sending.set(false);
        }
    }
    
    private String getSelectedFileSuffix() {
        OptionalInt chunks = getSelectedFileChunkCount();
        
        if (chunks.isPresent()) {
            int val = chunks.getAsInt();
            return new StringBuilder()
                    .append(" (")
                      .append("sent in ").append(val)
                      .append(val == 1 ? " piece" : " pieces")
                    .append(")")
                    .toString();
        }
        
        return "";
    }
    
    private OptionalInt getSelectedFileChunkCount() {
        if (!cbChunks.isSelected()) {
            return OptionalInt.of(1);
        }
        
        if (fileSize == -1 || !isChunkSizeValid()) {
            return OptionalInt.empty();
        }
        
        long MB = fileSize / 1_000_000;
        
        long chunks = MB / Long.parseLong(tfChunkVal.getText());
        boolean rest = fileSize % 1_000_000 > 0;
        
        return OptionalInt.of((int) (chunks + (rest ? 1 : 0)));
    }

    private String getSliderValue() {
        return String.valueOf(Math.round(slider.getValue()));
    }
    
    private void logAndDisplay(String title, String header, Exception e) {
        LOGGER.log(Level.SEVERE, header, e);
        
        Platform.runLater(() ->
                Dialogs.showThrowable(e, btnSend.getScene().getWindow(), title, header));
    }
}