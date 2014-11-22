package martinandersson.com.client;

import java.net.URL;
import java.util.EnumMap;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import martinandersson.com.client.pages.PageController;

/**
 * Controller of the frame view.<p>
 * 
 * On the inside, the control of most importance is a {@code Pagination} and
 * page controllers move to the next page using {@linkplain #gotoNextPage()}.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class FrameController implements Initializable
{
    private static final Logger LOGGER = Logger.getLogger(FrameController.class.getName());
    
    private final EnumMap<Page, StackPane> columns = new EnumMap<>(Page.class);
    private final EnumMap<Page, Label> labels = new EnumMap<>(Page.class);
    
    private final Stage stage;
    
    private final HostServices host;
    
    private Page current = null;
    
    @FXML
    private BorderPane root;
    
    @FXML
    private Pagination pagination;
    
    @FXML
    private CheckBox alwaysOnTop;
    
    @FXML
    private Hyperlink homepage;
    
    private SVGPath selected;
    
    
    
    public FrameController(Stage stage, HostServices host) {
        this.stage = stage;
        this.host = host;
    }
    
    
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        alwaysOnTop.setOnAction(e -> stage.setAlwaysOnTop(alwaysOnTop.isSelected()));
        stage.setAlwaysOnTop(alwaysOnTop.isSelected());
        
        homepage.setOnAction(e -> host.showDocument(homepage.getText()));
        
        columns.put(Page.CONNECT,    (StackPane) root.lookup("#topColumn1"));
        columns.put(Page.LOGIN,      (StackPane) root.lookup("#topColumn2"));
        columns.put(Page.SEND_FILES, (StackPane) root.lookup("#topColumn3"));
        
        labels.put(Page.CONNECT,     (Label) root.lookup("#topLabel1"));
        labels.put(Page.LOGIN,       (Label) root.lookup("#topLabel2"));
        labels.put(Page.SEND_FILES,  (Label) root.lookup("#topLabel3"));
        
        // Copy paste from: http://raphaeljs.com/icons/#play
        selected = newBullet("M6.684,25.682L24.316,15.5L6.684,5.318V25.682z");
        
        pagination.setPageFactory(this::loadPage);
        
        gotoNextPage();
    }
    
    /**
     * Is equivalent to {@code gotoNextPage(null)}.
     * 
     * @see #gotoNextPage(Object)
     */
    public void gotoNextPage() {
        gotoNextPage(null);
    }
    
    /**
     * Will switch view to the next page.
     * 
     * @param payload will be forwarded to the next page controller, may be
     *        {@code null} in which case nothing is forwarded
     */
    public void gotoNextPage(Object payload) {
        Runnable impl = () -> {
            final Page next = current == null ? Page.CONNECT : current.next();

            allColumnsBut(next).forEach(stackPane -> stackPane.setStyle(""));
            columns.get(next).setStyle("-fx-background-color: white;");

            labels.get(next).setGraphic(selected);

            // Mark previous page as "done"
            if (current != null) {
                // Copy paste from: http://raphaeljs.com/icons/#check
                labels.get(current).setGraphic(newBullet("M2.379,14.729 5.208,11.899 12.958,19.648 25.877,6.733 28.707,9.561 12.958,25.308z"));
            }

            if (payload != null) {
                /*
                 * Store payload in the pagination node. The page factory
                 * loadPage() will retrieve it later and forward to the page
                 * controller.
                 */
                pagination.setUserData(payload);
            }
            
            pagination.setCurrentPageIndex(next.getIndex());
            current = next;
        };
        
        if (Platform.isFxApplicationThread()) {
            impl.run();
        }
        else {
            Platform.runLater(impl);
        }
    }
    
    private SVGPath newBullet(String svgContents) {
        SVGPath bullet = new SVGPath();
        
        bullet.setScaleX(0.5);
        bullet.setScaleY(0.5);
        bullet.setContent(svgContents);
        
        return bullet;
    }
    
    private Node loadPage(int index) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(Page.ofIndex(index).getFXMLFile());
            Node node = loader.load();
            
            PageController controller = loader.getController();
            
            controller.setFrameController(this);
            
            if (pagination.getUserData() != null) {
                controller.setPayload(pagination.getUserData());
                pagination.setUserData(null);
            }
            
            return node;
        }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load Page" + (index + 1) + ".fxml:", e);
            
            Label warning = new Label("Error loading page, please see log.");
            warning.setMinWidth(Region.USE_PREF_SIZE);
            warning.setTextFill(Color.MAROON);
            ScrollPane pane = new ScrollPane(new StackPane(warning));
            pane.setFitToWidth(true);
            pane.setFitToHeight(true);
            pane.getStyleClass().add("no-background");
            
            return pane;
        }
    }
    
    private Stream<StackPane> allColumnsBut(Page exclude) {
        StackPane sp = columns.get(exclude);
        return columns.values().stream().filter(Predicate.isEqual(sp).negate());
    }
    
    public enum Page {
        CONNECT {
            @Override Page next() { return LOGIN; } },
        LOGIN {
            @Override Page next() { return SEND_FILES; } },
        SEND_FILES {
            @Override Page next() { return null; } };
        
        private static Page ofIndex(int index) {
            return values()[index];
        }
        
        public URL getFXMLFile() {
            return Page.class.getResource("pages/Page" + (getIndex() + 1) + ".fxml");
        }
        
        private int getIndex() {
            return ordinal();
        }
        
        abstract Page next();
    }
}