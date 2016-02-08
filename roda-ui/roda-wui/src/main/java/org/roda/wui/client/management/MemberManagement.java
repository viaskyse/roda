/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.management;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.roda.core.data.adapter.facet.Facets;
import org.roda.core.data.adapter.facet.SimpleFacetParameter;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.user.RODAMember;
import org.roda.wui.client.common.UserLogin;
import org.roda.wui.client.common.lists.RodaMemberList;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.FacetUtils;
import org.roda.wui.common.client.tools.Tools;
import org.roda.wui.management.client.Management;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionChangeEvent;

public class MemberManagement extends Composite {
  private static final String EDIT_GROUP_HISTORY_TOKEN = "edit_group";

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
      getInstance().resolve(historyTokens, callback);
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      UserLogin.getInstance().checkRoles(new HistoryResolver[] {MemberManagement.RESOLVER}, false, callback);
    }

    public List<String> getHistoryPath() {
      return Tools.concat(Management.RESOLVER.getHistoryPath(), getHistoryToken());
    }

    public String getHistoryToken() {
      return "user";
    }
  };

  private static MemberManagement instance = null;

  /**
   * Get the singleton instance
   *
   * @return the instance
   */
  public static MemberManagement getInstance() {
    if (instance == null) {
      instance = new MemberManagement();
    }
    return instance;
  }

  interface MyUiBinder extends UiBinder<Widget, MemberManagement> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  // private UserManagementConstants constants = (UserManagementConstants)
  // GWT.create(UserManagementConstants.class);

  // private UserManagementMessages messages = (UserManagementMessages)
  // GWT.create(UserManagementMessages.class);

  @UiField(provided = true)
  RodaMemberList list;

  @UiField(provided = true)
  FlowPanel facetIsActive;

  @UiField(provided = true)
  FlowPanel facetIsUser;

  @UiField(provided = true)
  FlowPanel facetGroups;

  @UiField
  Button buttonAddUser;

  @UiField
  Button buttonAddGroup;

  public MemberManagement() {
    Filter filter = null;
    Facets facets = new Facets(new SimpleFacetParameter(RodaConstants.MEMBERS_IS_ACTIVE),
      new SimpleFacetParameter(RodaConstants.MEMBERS_IS_USER),
      new SimpleFacetParameter(RodaConstants.MEMBERS_GROUPS_ALL));
    list = new RodaMemberList(filter, facets, "Users and groups");
    facetIsActive = new FlowPanel();
    facetIsUser = new FlowPanel();
    facetGroups = new FlowPanel();

    Map<String, FlowPanel> facetPanels = new HashMap<String, FlowPanel>();
    facetPanels.put(RodaConstants.MEMBERS_IS_ACTIVE, facetIsActive);
    facetPanels.put(RodaConstants.MEMBERS_IS_USER, facetIsUser);
    facetPanels.put(RodaConstants.MEMBERS_GROUPS_ALL, facetGroups);

    FacetUtils.bindFacets(list, facetPanels);

    initWidget(uiBinder.createAndBindUi(this));

    list.getSelectionModel().addSelectionChangeHandler(new SelectionChangeEvent.Handler() {

      @Override
      public void onSelectionChange(SelectionChangeEvent event) {
        RODAMember selected = list.getSelectionModel().getSelectedObject();
        if (selected != null) {
          Tools.newHistory(RESOLVER,
            (selected.isUser() ? EditUser.RESOLVER.getHistoryToken() : EDIT_GROUP_HISTORY_TOKEN), selected.getId());
        }
      }
    });
  }

  public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
    if (historyTokens.size() == 0) {
      list.refresh();
      callback.onSuccess(this);
    } else if (historyTokens.size() == 1) {
      if (historyTokens.get(0).equals(CreateUser.RESOLVER.getHistoryToken())) {
        CreateUser.RESOLVER.resolve(Tools.tail(historyTokens), callback);
      } else if (historyTokens.get(0).equals(CreateGroup.RESOLVER.getHistoryToken())) {
        CreateGroup.RESOLVER.resolve(Tools.tail(historyTokens), callback);
      } else {
        Tools.newHistory(RESOLVER);
        callback.onSuccess(null);
      }
    } else if (historyTokens.size() == 2) {
      if (historyTokens.get(0).equals(EditUser.RESOLVER.getHistoryToken())) {
        EditUser.RESOLVER.resolve(Tools.tail(historyTokens), callback);
      } else if (historyTokens.get(0).equals(EDIT_GROUP_HISTORY_TOKEN)) {
        EditGroup.RESOLVER.resolve(Tools.tail(historyTokens), callback);
      } else {
        Tools.newHistory(RESOLVER);
        callback.onSuccess(null);
      }
    } else {
      Tools.newHistory(RESOLVER);
      callback.onSuccess(null);
    }
  }

  @UiHandler("buttonAddUser")
  void buttonAddUserHandler(ClickEvent e) {
    Tools.newHistory(RESOLVER, "create_user");
  }

  @UiHandler("buttonAddGroup")
  void buttonAddGroupHandler(ClickEvent e) {
    Tools.newHistory(RESOLVER, "create_group");
  }
}