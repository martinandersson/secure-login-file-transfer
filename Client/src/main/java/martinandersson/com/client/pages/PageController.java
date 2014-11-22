package martinandersson.com.client.pages;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;
import martinandersson.com.client.FrameController;

/**
 * A page controller controls a page.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public interface PageController extends Initializable
{    
    /**
     * Invoked by page navigator for all those page controllers that need to use
     * {@code FrameController.gotoNextPage()}.
     * 
     * @param frameController the frame controller instance
     */
    default void setFrameController(FrameController frameController) {};
    
    /**
     * Invoked by page navigator when previous page controller has a payload to
     * forward.
     * 
     * @param payload any object provided by previous page controller
     */
    default void setPayload(Object payload) {
        throw new UnsupportedOperationException("Not supported.");
    };
    
    @Override
    default void initialize(URL location, ResourceBundle resources) {}
}