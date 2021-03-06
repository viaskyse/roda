/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.common.utils;

import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.wui.client.common.Dialogs;
import org.roda.wui.client.welcome.Welcome;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.ClientLoggerService;
import org.roda.wui.common.client.tools.Tools;
import org.roda.wui.common.client.widgets.Toast;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.StatusCodeException;

import config.i18n.client.ClientMessages;

public class AsyncCallbackUtils {

  private static final ClientMessages messages = GWT.create(ClientMessages.class);
  private static ClientLogger LOGGER = new ClientLogger(AsyncCallbackUtils.class.getName());

  public static final boolean treatCommonFailures(Throwable caught) {
    boolean treatedError = false;
    if (caught instanceof StatusCodeException && ((StatusCodeException) caught).getStatusCode() == 0) {
      // check if browser is offline
      if (!JavascriptUtils.isOnline()) {
        Toast.showError(messages.browserOfflineError());
      } else {
        Toast.showError(messages.cannotReachServerError());
      }
      treatedError = true;
    } else if (caught instanceof AuthorizationDeniedException) {
      Dialogs.showInformationDialog(messages.authorizationDeniedAlert(),
        messages.authorizationDeniedAlertMessage(caught.getMessage()), messages.dialogOk(), new AsyncCallback<Void>() {

          @Override
          public void onSuccess(Void result) {
            Tools.newHistory(Welcome.RESOLVER);
          }

          @Override
          public void onFailure(Throwable caught) {
            // do nothing
          }
        });

      treatedError = true;
    }
    return treatedError;
  }

  public static final void defaultFailureTreatment(Throwable caught) {
    if (!treatCommonFailures(caught)) {
      Toast.showError(caught.getClass().getSimpleName(), caught.getMessage());
      LOGGER.error("Async request error", caught);
    }
  }

}
