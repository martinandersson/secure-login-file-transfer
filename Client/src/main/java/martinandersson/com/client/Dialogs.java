package martinandersson.com.client;

import java.io.PrintWriter;
import java.io.StringWriter;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;

/**
 * Utility class for creating and showing dialogs.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public final class Dialogs
{
    public static final String DEFAULT_EXCEPTION_TITLE  = "Ooops.",
                               DEFAULT_EXCEPTION_HEADER = "Something crashed :'(";
    
    
    
    private Dialogs() {
        // Empty
    }
    
    
    
    /**
     * Show an info dialog.
     * 
     * @param owner      owner
     * @param title      title
     * @param header     header
     * @param content    content
     * 
     * @see Alert
     */
    public static void showInformation(Window owner, String title, String header, String content) {
        showDialog(AlertType.INFORMATION, owner, title, header, content);
    }
    
    /**
     * Show a warning dialog.
     * 
     * @param owner      owner
     * @param title      title
     * @param header     header
     * @param content    content
     * 
     * @see Alert
     */
    public static void showWarning(Window owner, String title, String header, String content) {
        showDialog(AlertType.WARNING, owner, title, header, content);
    }
    
    /**
     * Show an error dialog.
     * 
     * @param owner      owner
     * @param title      title
     * @param header     header
     * @param content    content
     * 
     * @see Alert
     */
    public static void showError(Window owner, String title, String header, String content) {
        showDialog(AlertType.ERROR, owner, title, header, content);
    }
    
    /**
     * Show a dialog.
     * 
     * @param alertType  type
     * @param owner      owner
     * @param title      title
     * @param header     header
     * @param content    content
     * 
     * @see Alert
     */
    public static void showDialog(AlertType alertType, Window owner, String title, String header, String content) {
        Alert alert = new Alert(alertType);
        
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        alert.showAndWait();
    }
    
    /**
     * Show a dialog with a stack trace pulled from the specified throwable.<p>
     * 
     * The title will be {@value #DEFAULT_EXCEPTION_TITLE} and the header will
     * be {@value #DEFAULT_EXCEPTION_HEADER}.
     * 
     * @param t       throwable
     * @param owner   owner
     */
    public static void showThrowable(Throwable t, Window owner) {
        showThrowable(t, owner, DEFAULT_EXCEPTION_TITLE, DEFAULT_EXCEPTION_HEADER);
    }
    
    /**
     * Show a dialog with a stack trace pulled from the specified throwable.
     * 
     * @param t       throwable
     * @param owner   owner
     * @param title   title
     * @param header  header
     */
    public static void showThrowable(Throwable t, Window owner, String title, String header) {
        Alert alert = new Alert(AlertType.ERROR);
        
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        
        TextArea ta = new TextArea(sw.toString());
        ta.setEditable(false);
        ta.setWrapText(true);
        
        alert.getDialogPane().setExpandableContent(new BorderPane(ta));
        
        alert.showAndWait();
    }
}