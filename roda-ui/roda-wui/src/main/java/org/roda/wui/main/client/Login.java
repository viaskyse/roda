/**
 * 
 */
package org.roda.wui.main.client;

import java.util.Arrays;
import java.util.List;

import org.roda.core.common.AuthenticationDeniedException;
import org.roda.core.data.v2.RodaUser;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.UserLogin;
import org.roda.wui.common.client.tools.Tools;
import org.roda.wui.home.client.Home;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.AttachEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

/**
 * @author Luis Faria
 * 
 */
public class Login extends Composite {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
      getInstance().resolve(historyTokens, callback);
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      callback.onSuccess(true);
    }

    @Override
    public String getHistoryToken() {
      return "login";
    }

    @Override
    public List<String> getHistoryPath() {
      return Arrays.asList(getHistoryToken());
    }
  };

  public static final String getViewItemHistoryToken(String id) {
    return RESOLVER.getHistoryPath() + "." + id;
  }

  interface MyUiBinder extends UiBinder<Widget, Login> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  private static Login instance = null;

  /**
   * Get the singleton instance
   * 
   * @return the instance
   */
  public static Login getInstance() {
    if (instance == null) {
      instance = new Login();
    }
    return instance;
  }

  private ClientLogger logger = new ClientLogger(getClass().getName());

  @UiField
  TextBox username;

  @UiField
  PasswordTextBox password;

  @UiField
  Button login;

  @UiField
  Label error;

  private String service = null;

  private Login() {
    initWidget(uiBinder.createAndBindUi(this));
    addAttachHandler(new AttachEvent.Handler() {

      @Override
      public void onAttachOrDetach(AttachEvent event) {
        if (event.isAttached()) {
          username.setFocus(true);
        }
      }
    });
  }

  public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
    username.setText("");
    password.setText("");
    error.setText("");
    service = Tools.join(historyTokens, ".");
    callback.onSuccess(this);
  }

  @UiHandler("login")
  void handleClick(ClickEvent e) {
    doLogin();
  }

  @UiHandler("username")
  void handleUsernameKeyPress(KeyPressEvent event) {
    tryToLoginWhenEnterIsPressed(event);
  }

  @UiHandler("password")
  void handlePasswordKeyPress(KeyPressEvent event) {
    tryToLoginWhenEnterIsPressed(event);
  }

  private void tryToLoginWhenEnterIsPressed(KeyPressEvent event) {
    if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
      doLogin();
    }
  }

  private void doLogin() {
    String usernameText = username.getText();
    String passwordText = password.getText();
    error.setText("");

    if (usernameText.trim().length() == 0 || passwordText.trim().length() == 0) {
      error.setText("Please fill the username and password");
    } else {

      UserLogin.getInstance().login(usernameText, passwordText, new AsyncCallback<RodaUser>() {

        @Override
        public void onFailure(Throwable caught) {
          if (caught instanceof AuthenticationDeniedException) {
            error.setText("Wrong username or password");
          } else {
            error.setText("System currently unavailable");
          }
        }

        @Override
        public void onSuccess(RodaUser user) {
          if (service != null && service.length() > 0) {
            History.newItem(service);
          } else {
            Tools.newHistory(Home.RESOLVER);
          }

        }
      });
    }
  }
}