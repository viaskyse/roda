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
package org.roda.wui.client.browse;

import java.util.ArrayList;
import java.util.List;

import org.roda.core.data.adapter.facet.Facets;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.filter.SimpleFilterParameter;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.ip.metadata.IndexedPreservationEvent;
import org.roda.wui.client.common.UserLogin;
import org.roda.wui.client.common.lists.PreservationEventList;
import org.roda.wui.client.common.utils.AsyncCallbackUtils;
import org.roda.wui.client.main.BreadcrumbItem;
import org.roda.wui.client.main.BreadcrumbPanel;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.DescriptionLevelUtils;
import org.roda.wui.common.client.tools.RestUtils;
import org.roda.wui.common.client.tools.Tools;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;

import config.i18n.client.ClientMessages;

/**
 * @author Luis Faria
 * 
 */
public class PreservationEvents extends Composite {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, final AsyncCallback<Widget> callback) {
      if (historyTokens.size() == 1) {
        final String aipId = historyTokens.get(0);
        PreservationEvents preservationEvents = new PreservationEvents(aipId);
        callback.onSuccess(preservationEvents);
      } else if (historyTokens.size() > 1
        && historyTokens.get(0).equals(ShowPreservationEvent.RESOLVER.getHistoryToken())) {
        ShowPreservationEvent.RESOLVER.resolve(Tools.tail(historyTokens), callback);
      } else {
        Tools.newHistory(Browse.RESOLVER);
        callback.onSuccess(null);
      }
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      UserLogin.getInstance().checkRoles(new HistoryResolver[] {Browse.RESOLVER}, false, callback);
    }

    public List<String> getHistoryPath() {
      return Tools.concat(Browse.RESOLVER.getHistoryPath(), getHistoryToken());
    }

    public String getHistoryToken() {
      return "events";
    }
  };

  public static final List<String> getViewItemHistoryToken(String id) {
    return Tools.concat(RESOLVER.getHistoryPath(), id);
  }

  interface MyUiBinder extends UiBinder<Widget, PreservationEvents> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  private static ClientMessages messages = (ClientMessages) GWT.create(ClientMessages.class);

  @UiField
  BreadcrumbPanel breadcrumb;

  @UiField
  SimplePanel itemIcon;

  @UiField
  Label itemTitle;

  @UiField(provided = true)
  PreservationEventList eventList;

  @UiField
  Button downloadButton;

  @UiField
  Button backButton;

  private String aipId;

  private BrowseItemBundle itemBundle;

  /**
   * Create a new panel to edit a user
   * 
   * @param itemBundle
   * 
   */
  public PreservationEvents(final String aipId) {
    this.aipId = aipId;

    Filter filter = new Filter(new SimpleFilterParameter(RodaConstants.PRESERVATION_EVENT_AIP_ID, aipId));
    Facets facets = null;

    eventList = new PreservationEventList(filter, facets, messages.preservationEventsTitle(), false);

    initWidget(uiBinder.createAndBindUi(this));

    eventList.getSelectionModel().addSelectionChangeHandler(new Handler() {

      @Override
      public void onSelectionChange(SelectionChangeEvent event) {
        IndexedPreservationEvent selected = eventList.getSelectionModel().getSelectedObject();
        if (selected != null) {
          Tools.newHistory(ShowPreservationEvent.RESOLVER, aipId, selected.getId());
        }
      }
    });

    BrowserService.Util.getInstance().retrieveItemBundle(aipId, LocaleInfo.getCurrentLocale().getLocaleName(),
      new AsyncCallback<BrowseItemBundle>() {

        @Override
        public void onFailure(Throwable caught) {
          AsyncCallbackUtils.defaultFailureTreatment(caught);
          Tools.newHistory(Browse.RESOLVER);
        }

        @Override
        public void onSuccess(BrowseItemBundle itemBundle) {
          PreservationEvents.this.itemBundle = itemBundle;
          viewAction();
        }
      });
  }

  public void viewAction() {
    IndexedAIP aip = itemBundle.getAip();

    breadcrumb.updatePath(getBreadcrumbsFromAncestors(itemBundle.getAIPAncestors(), aip));
    breadcrumb.setVisible(true);
  }

  private List<BreadcrumbItem> getBreadcrumbsFromAncestors(List<IndexedAIP> aipAncestors, IndexedAIP aip) {
    List<BreadcrumbItem> ret = new ArrayList<>();
    ret.add(new BreadcrumbItem(DescriptionLevelUtils.getTopIconSafeHtml(), Browse.RESOLVER.getHistoryPath()));
    for (IndexedAIP ancestor : aipAncestors) {
      SafeHtml breadcrumbLabel = getBreadcrumbLabel(ancestor);
      BreadcrumbItem ancestorBreadcrumb = new BreadcrumbItem(breadcrumbLabel,
        Tools.concat(Browse.RESOLVER.getHistoryPath(), ancestor.getId()));
      ret.add(1, ancestorBreadcrumb);
    }

    ret.add(new BreadcrumbItem(getBreadcrumbLabel(aip), Tools.concat(Browse.RESOLVER.getHistoryPath(), aip.getId())));
    return ret;
  }

  private SafeHtml getBreadcrumbLabel(IndexedAIP ancestor) {
    SafeHtml elementLevelIconSafeHtml = DescriptionLevelUtils.getElementLevelIconSafeHtml(ancestor.getLevel(), false);
    SafeHtmlBuilder builder = new SafeHtmlBuilder();
    String label = ancestor.getTitle() != null ? ancestor.getTitle() : ancestor.getId();
    builder.append(elementLevelIconSafeHtml).append(SafeHtmlUtils.fromString(label));
    SafeHtml breadcrumbLabel = builder.toSafeHtml();
    return breadcrumbLabel;
  }

  @UiHandler("downloadButton")
  void buttonDownloadHandler(ClickEvent e) {
    SafeUri downloadUri = RestUtils.createPreservationMetadataDownloadUri(aipId);
    if (downloadUri != null) {
      Window.Location.assign(downloadUri.asString());
    }
  }

  @UiHandler("backButton")
  void buttonBackHandler(ClickEvent e) {
    Tools.newHistory(Tools.concat(Browse.RESOLVER.getHistoryPath(), aipId));
  }
}
