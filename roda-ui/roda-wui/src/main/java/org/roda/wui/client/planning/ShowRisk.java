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
package org.roda.wui.client.planning;

import java.util.List;

import org.roda.core.data.v2.risks.IndexedRisk;
import org.roda.core.data.v2.risks.Risk;
import org.roda.wui.client.browse.BrowserService;
import org.roda.wui.client.common.UserLogin;
import org.roda.wui.client.management.MemberManagement;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.Tools;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

/**
 * @author Luis Faria
 *
 */
public class ShowRisk extends Composite {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, final AsyncCallback<Widget> callback) {
      getInstance().resolve(historyTokens, callback);
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      UserLogin.getInstance().checkRoles(new HistoryResolver[] {MemberManagement.RESOLVER}, false, callback);
    }

    public List<String> getHistoryPath() {
      return Tools.concat(RiskRegister.RESOLVER.getHistoryPath(), getHistoryToken());
    }

    public String getHistoryToken() {
      return "risk";
    }
  };

  private static ShowRisk instance = null;

  public static ShowRisk getInstance() {
    if (instance == null) {
      instance = new ShowRisk();
    }
    return instance;
  }

  interface MyUiBinder extends UiBinder<Widget, ShowRisk> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  @UiField(provided = true)
  RiskShowPanel riskShowPanel;

  @UiField
  Button buttonHistory;

  @UiField
  Button buttonEdit;

  @UiField
  Button buttonCancel;

  private Risk risk;

  /**
   * Create a new panel to view a risk
   *
   *
   */

  public ShowRisk() {
    this.risk = new Risk();
    this.riskShowPanel = new RiskShowPanel();
    initWidget(uiBinder.createAndBindUi(this));
  }

  public ShowRisk(Risk risk) {
    this.risk = risk;
    this.riskShowPanel = new RiskShowPanel(risk, true);
    initWidget(uiBinder.createAndBindUi(this));

    BrowserService.Util.getInstance().hasRiskVersions(risk.getId(), new AsyncCallback<Boolean>() {

      @Override
      public void onFailure(Throwable caught) {
        buttonHistory.setVisible(false);
      }

      @Override
      public void onSuccess(Boolean bundle) {
        buttonHistory.setVisible(bundle.booleanValue());
      }
    });
  }

  void resolve(List<String> historyTokens, final AsyncCallback<Widget> callback) {

    if (historyTokens.size() == 1) {
      String riskId = historyTokens.get(0);
      BrowserService.Util.getInstance().retrieve(IndexedRisk.class.getName(), riskId, new AsyncCallback<IndexedRisk>() {

        @Override
        public void onFailure(Throwable caught) {
          callback.onFailure(caught);
        }

        @Override
        public void onSuccess(IndexedRisk result) {
          ShowRisk riskPanel = new ShowRisk(result);
          callback.onSuccess(riskPanel);
        }
      });
    } else {
      Tools.newHistory(RiskRegister.RESOLVER);
      callback.onSuccess(null);
    }
  }

  @UiHandler("buttonHistory")
  void handleButtonHistory(ClickEvent e) {
    Tools.newHistory(RiskRegister.RESOLVER, RiskHistory.RESOLVER.getHistoryToken(), risk.getId());
  }

  @UiHandler("buttonEdit")
  void handleButtonEdit(ClickEvent e) {
    Tools.newHistory(RiskRegister.RESOLVER, EditRisk.RESOLVER.getHistoryToken(), risk.getId());
  }

  @UiHandler("buttonCancel")
  void handleButtonCancel(ClickEvent e) {
    cancel();
  }

  private void cancel() {
    Tools.newHistory(RiskRegister.RESOLVER);
  }

}
