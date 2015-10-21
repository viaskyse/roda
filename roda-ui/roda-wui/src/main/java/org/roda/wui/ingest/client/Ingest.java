package org.roda.wui.ingest.client;

import java.util.Arrays;
import java.util.List;

import org.roda.core.data.v2.RodaUser;
import org.roda.core.data.v2.User;
import org.roda.wui.common.client.BadHistoryTokenException;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.UserLogin;
import org.roda.wui.common.client.tools.Tools;
import org.roda.wui.common.client.widgets.HTMLWidgetWrapper;
import org.roda.wui.ingest.list.client.IngestList;
import org.roda.wui.ingest.pre.client.PreIngest;
import org.roda.wui.ingest.submit.client.IngestSubmit;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;

/**
 * 
 * @author Luis Faria
 * 
 */
public class Ingest {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
      getInstance().resolve(historyTokens, callback);
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      UserLogin.getInstance().checkRoles(
        new HistoryResolver[] {PreIngest.RESOLVER, IngestSubmit.RESOLVER, IngestList.RESOLVER}, false, callback);
    }

    public List<String> getHistoryPath() {
      return Arrays.asList(getHistoryToken());
    }

    public String getHistoryToken() {
      return "ingest";
    }
  };

  private static Ingest instance = null;

  /**
   * Get the singleton instance
   * 
   * @return the instance
   */
  public static Ingest getInstance() {
    if (instance == null) {
      instance = new Ingest();
    }
    return instance;
  }

  private static ClientLogger logger = new ClientLogger(Ingest.class.getName());

  private boolean initialized;

  private HTMLWidgetWrapper layout;

  private HTMLWidgetWrapper help = null;

  private Ingest() {
    initialized = false;
  }

  private void init() {
    if (!initialized) {
      initialized = true;
      layout = new HTMLWidgetWrapper("Ingest.html");
    }
  }

  private HTMLWidgetWrapper getHelp() {
    if (help == null) {
      help = new HTMLWidgetWrapper("IngestHelp.html");
    }
    return help;
  }

  public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
    if (historyTokens.size() == 0) {
      init();
      callback.onSuccess(layout);
    } else {
      if (historyTokens.get(0).equals(PreIngest.RESOLVER.getHistoryToken())) {
        PreIngest.getInstance().resolve(Tools.tail(historyTokens), callback);
      } else if (historyTokens.get(0).equals(IngestSubmit.RESOLVER.getHistoryToken())) {
        IngestSubmit.getInstance().resolve(Tools.tail(historyTokens), callback);
      } else if (historyTokens.get(0).equals(IngestList.RESOLVER.getHistoryToken())) {
        IngestList.getInstance().resolve(Tools.tail(historyTokens), callback);
      } else if (historyTokens.get(0).equals("help")) {
        callback.onSuccess(getHelp());
      } else {
        callback.onFailure(new BadHistoryTokenException(historyTokens.get(0)));
      }
    }
  }

  /**
   * Open new window to download RODA-in
   * 
   * @param targetUser
   *          the user for which to download the RODA-in Installer, or null to
   *          use the logged user
   * 
   * @param os
   *          the target operative system, e.g. windows, linux or mac. Use null
   *          to get a cross-platform installer
   */
  public static void downloadRodaIn(final User targetUser, final String os) {
    UserLogin.getRodaProperty("roda.in.installer.url", new AsyncCallback<String>() {

      public void onFailure(Throwable caught) {
        logger.error("Error getting RODA-in", caught);
      }

      public void onSuccess(final String rodaInUrl) {
        UserLogin.getInstance().getAuthenticatedUser(new AsyncCallback<RodaUser>() {

          public void onFailure(Throwable caught) {
            logger.error("Error getting RODA-in", caught);
          }

          public void onSuccess(RodaUser user) {
            RodaUser target = targetUser == null ? user : targetUser;
            String url = rodaInUrl.replaceAll("$USERNAME", user.getName()) + "/" + target.getName();
            if (os != null) {
              url += "?os=" + os;
            }
            Window.open(url, "_blank", "");

          }

        });

      }

    });

  }

}