/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
/**
 * 
 */
package org.roda.wui.client.main;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.roda.wui.client.browse.Browse;
import org.roda.wui.client.common.Dialogs;
import org.roda.wui.client.common.UserLogin;
import org.roda.wui.client.common.utils.AsyncCallbackUtils;
import org.roda.wui.client.ingest.Ingest;
import org.roda.wui.client.management.Management;
import org.roda.wui.client.management.Profile;
import org.roda.wui.client.management.RecoverLogin;
import org.roda.wui.client.management.Register;
import org.roda.wui.client.management.ResetPassword;
import org.roda.wui.client.management.VerifyEmail;
import org.roda.wui.client.planning.Planning;
import org.roda.wui.client.process.Process;
import org.roda.wui.client.search.Relation;
import org.roda.wui.client.search.Search;
import org.roda.wui.client.welcome.Help;
import org.roda.wui.client.welcome.Welcome;
import org.roda.wui.common.client.BadHistoryTokenException;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.Tools;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import config.i18n.client.ClientMessages;

/**
 * @author Luis Faria
 * 
 */
public class ContentPanel extends SimplePanel {

  private static ContentPanel instance = null;

  @SuppressWarnings("unused")
  private static ClientLogger logger = new ClientLogger(ContentPanel.class.getName());

  /**
   * Get the singleton instance
   * 
   * @return the singleton instance
   */
  public static ContentPanel getInstance() {
    if (instance == null) {
      instance = new ContentPanel();
    }
    return instance;
  }

  private static final Set<HistoryResolver> resolvers = new HashSet<HistoryResolver>();
  private static ClientMessages messages = (ClientMessages) GWT.create(ClientMessages.class);

  private Widget currWidget;

  private List<String> currHistoryPath;

  private ContentPanel() {
    super();
    this.addStyleName("contentPanel");
    this.currWidget = null;

  }

  public void init() {
    // Login
    resolvers.add(Login.RESOLVER);
    // Home
    resolvers.add(Welcome.RESOLVER);
    // Theme static pages
    resolvers.add(Theme.RESOLVER);
    // Browse
    resolvers.add(Browse.RESOLVER);
    // Search
    resolvers.add(Search.RESOLVER);
    resolvers.add(Relation.RESOLVER);
    // Ingest
    resolvers.add(Ingest.RESOLVER);
    // Management
    resolvers.add(Management.RESOLVER);
    // Planning
    resolvers.add(Planning.RESOLVER);
    // User Management
    resolvers.add(Profile.RESOLVER);
    resolvers.add(Register.RESOLVER);
    resolvers.add(RecoverLogin.RESOLVER);
    resolvers.add(ResetPassword.RESOLVER);
    resolvers.add(VerifyEmail.RESOLVER);
    resolvers.add(Process.RESOLVER);

    // Help
    resolvers.add(Help.RESOLVER);
  }

  /**
   * Update the content panel with the new history
   * 
   * @param historyTokens
   *          the history tokens
   */
  public void update(final List<String> historyTokens) {
    boolean foundit = false;
    for (final HistoryResolver resolver : resolvers) {
      if (historyTokens.get(0).equals(resolver.getHistoryToken())) {
        foundit = true;
        currHistoryPath = historyTokens;
        resolver.isCurrentUserPermitted(new AsyncCallback<Boolean>() {

          public void onFailure(Throwable caught) {
            AsyncCallbackUtils.defaultFailureTreatment(caught);
          }

          public void onSuccess(Boolean permitted) {
            if (!permitted.booleanValue()) {
              UserLogin.getInstance().showSuggestLoginDialog();
            } else {
              resolver.resolve(Tools.tail(historyTokens), new AsyncCallback<Widget>() {

                public void onFailure(Throwable caught) {
                  if (caught instanceof BadHistoryTokenException) {
                    Dialogs.showInformationDialog(messages.notFoundError(), messages.pageNotFound(caught.getMessage()),
                      messages.dialogOk());
                    if (currWidget == null) {
                      Tools.newHistory(Welcome.RESOLVER);
                    }
                  } else {
                    AsyncCallbackUtils.defaultFailureTreatment(caught);
                  }
                }

                public void onSuccess(Widget widget) {
                  if (widget != null) {
                    if (widget != currWidget) {
                      currWidget = widget;
                      setWidget(widget);
                    }
                    setWindowTitle(historyTokens);
                  }
                }

              });
            }
          }

        });
      }
    }
    if (!foundit) {
      Dialogs.showInformationDialog(messages.notFoundError(), messages.pageNotFound(historyTokens.get(0)),
        messages.dialogOk());
      if (currWidget == null) {
        Tools.newHistory(Welcome.RESOLVER);
      } else {
        Tools.newHistory(currHistoryPath);
      }
    }

  }

  private void setWindowTitle(List<String> historyTokens) {
    String tokenI18N = "";
    boolean resolved = false;
    List<String> tokens = historyTokens;

    while (!resolved && tokens.size() > 0) {
      String token = Tools.join(tokens, "_");
      tokenI18N = messages.title(token).toUpperCase();

      if (tokenI18N.isEmpty()) {
        tokens = Tools.removeLast(tokens);
      } else {
        resolved = true;
      }

    }

    if (!resolved) {
      String lastToken = historyTokens.get(historyTokens.size() - 1);
      
      // TODO generalize suffix approach
      if (lastToken.endsWith(".html")) {
        lastToken = lastToken.substring(0, lastToken.length() - ".html".length());
      }

      // transform camel case to spaces
      lastToken = lastToken.replaceAll("([A-Z])", " $1");
      
      // upper-case
      lastToken = lastToken.toUpperCase();
            
      tokenI18N = lastToken;
    }

    // title.setText(tokenI18N);
    Window.setTitle(messages.windowTitle(tokenI18N));
  }


}
