/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.browse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.filter.NotSimpleFilterParameter;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.index.SelectedItems;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.ip.Permissions;
import org.roda.core.data.v2.ip.Permissions.PermissionType;
import org.roda.core.data.v2.user.RODAMember;
import org.roda.wui.client.common.LoadingAsyncCallback;
import org.roda.wui.client.common.UserLogin;
import org.roda.wui.client.common.dialogs.MemberSelectDialog;
import org.roda.wui.client.common.lists.SelectedItemsUtils;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.Tools;
import org.roda.wui.common.client.widgets.HTMLWidgetWrapper;
import org.roda.wui.common.client.widgets.Toast;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import config.i18n.client.ClientMessages;

public class EditPermissions extends Composite {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, final AsyncCallback<Widget> callback) {
      if (historyTokens.size() == 1) {
        final String aipId = historyTokens.get(0);
        BrowserService.Util.getInstance().retrieve(IndexedAIP.class.getName(), aipId, new AsyncCallback<IndexedAIP>() {

          @Override
          public void onFailure(Throwable caught) {
            Tools.newHistory(Browse.RESOLVER);
            callback.onSuccess(null);
          }

          @Override
          public void onSuccess(IndexedAIP aip) {
            EditPermissions edit = new EditPermissions(aip);
            callback.onSuccess(edit);
          }
        });

      } else if (historyTokens.size() == 0 && !SelectedItemsUtils.isEmpty(Browse.getInstance().getSelected())) {
        final SelectedItems<IndexedAIP> selected = Browse.getInstance().getSelected();
        BrowserService.Util.getInstance().retrieve(IndexedAIP.class.getName(), selected,
          new AsyncCallback<List<IndexedAIP>>() {

            @Override
            public void onFailure(Throwable caught) {
              Tools.newHistory(Browse.RESOLVER);
              callback.onSuccess(null);
            }

            @Override
            public void onSuccess(List<IndexedAIP> aips) {
              EditPermissions edit = new EditPermissions(aips);
              callback.onSuccess(edit);
            }
          });
      } else {
        Tools.newHistory(Browse.RESOLVER);
        callback.onSuccess(null);
      }
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      UserLogin.getInstance().checkRoles(new HistoryResolver[] {EditPermissions.RESOLVER}, false, callback);
    }

    public List<String> getHistoryPath() {
      return Tools.concat(Browse.RESOLVER.getHistoryPath(), getHistoryToken());
    }

    public String getHistoryToken() {
      return "edit_permissions";
    }
  };

  interface MyUiBinder extends UiBinder<Widget, EditPermissions> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  private static ClientMessages messages = GWT.create(ClientMessages.class);

  @UiField
  FlowPanel editPermissionsDescription;

  @UiField
  Label userPermissionsHeader, groupPermissionsHeader;

  @UiField
  FlowPanel userPermissionsPanel, groupPermissionsPanel;

  private List<IndexedAIP> aips = new ArrayList<IndexedAIP>();

  public EditPermissions(IndexedAIP aip) {
    this.aips.add(aip);
    initWidget(uiBinder.createAndBindUi(this));
    createPermissionPanel();
  }

  public EditPermissions(List<IndexedAIP> aips) {
    this.aips.addAll(aips);
    initWidget(uiBinder.createAndBindUi(this));
    editPermissionsDescription.add(new HTMLWidgetWrapper("EditPermissionsDescription.html"));
    createPermissionPanelList();
  }

  private void createPermissionPanelList() {
    Map<String, Set<PermissionType>> userPermissionsToShow = new HashMap<String, Set<PermissionType>>();
    Map<String, Set<PermissionType>> groupPermissionsToShow = new HashMap<String, Set<PermissionType>>();

    if (!aips.isEmpty()) {
      Permissions firstAIPPermissions = aips.get(0).getPermissions();

      for (String userName : firstAIPPermissions.getUsernames()) {
        userPermissionsToShow.put(userName, firstAIPPermissions.getUserPermissions(userName));
      }

      for (String groupName : firstAIPPermissions.getGroupnames()) {
        groupPermissionsToShow.put(groupName, firstAIPPermissions.getGroupPermissions(groupName));
      }

      for (int i = 1; i < aips.size(); i++) {
        Permissions permissions = aips.get(i).getPermissions();

        for (Iterator<Entry<String, Set<PermissionType>>> userIterator = userPermissionsToShow.entrySet()
          .iterator(); userIterator.hasNext();) {
          Entry<String, Set<PermissionType>> entry = userIterator.next();
          if (permissions.getUsernames().contains(entry.getKey())) {
            Set<PermissionType> userPermissionType = entry.getValue();

            for (Iterator<PermissionType> permissionTypeIterator = userPermissionType.iterator(); permissionTypeIterator
              .hasNext();) {
              PermissionType permissionType = permissionTypeIterator.next();

              if (!permissions.getUserPermissions(entry.getKey()).contains(permissionType)) {
                permissionTypeIterator.remove();
              }
            }
          } else {
            userIterator.remove();
          }
        }

        for (Iterator<Entry<String, Set<PermissionType>>> groupIterator = groupPermissionsToShow.entrySet()
          .iterator(); groupIterator.hasNext();) {
          Entry<String, Set<PermissionType>> entry = groupIterator.next();
          if (permissions.getGroupnames().contains(entry.getKey())) {
            Set<PermissionType> groupPermissionType = entry.getValue();

            for (Iterator<PermissionType> permissionTypeIterator = groupPermissionType
              .iterator(); permissionTypeIterator.hasNext();) {
              PermissionType permissionType = permissionTypeIterator.next();

              if (!permissions.getGroupPermissions(entry.getKey()).contains(permissionType)) {
                permissionTypeIterator.remove();
              }
            }
          } else {
            groupIterator.remove();
          }
        }

      }
    }

    if (userPermissionsToShow.size() == 0) {
      userPermissionsHeader.setVisible(false);
    }

    if (groupPermissionsToShow.size() == 0) {
      groupPermissionsHeader.setVisible(false);
    }

    for (String username : userPermissionsToShow.keySet()) {
      userPermissionsPanel.add(new PermissionPanel(username, true, userPermissionsToShow.get(username)));
    }

    for (String groupname : groupPermissionsToShow.keySet()) {
      groupPermissionsPanel.add(new PermissionPanel(groupname, false, groupPermissionsToShow.get(groupname)));
    }
  }

  private void createPermissionPanel() {
    Permissions permissions = aips.get(0).getPermissions();
    GWT.log("Permissions are: " + permissions);

    if (permissions.getUsernames().size() == 0) {
      userPermissionsHeader.setVisible(false);
    }

    if (permissions.getGroupnames().size() == 0) {
      groupPermissionsHeader.setVisible(false);
    }

    for (String username : permissions.getUsernames()) {
      userPermissionsPanel.add(new PermissionPanel(username, true, permissions.getUserPermissions(username)));
    }

    for (String groupname : permissions.getGroupnames()) {
      groupPermissionsPanel.add(new PermissionPanel(groupname, false, permissions.getGroupPermissions(groupname)));
    }
  }

  public void addPermissionPanel(RODAMember member) {
    if (member.isUser()) {
      userPermissionsPanel.insert(new PermissionPanel(member), 0);
      userPermissionsHeader.setVisible(true);
    } else {
      groupPermissionsPanel.insert(new PermissionPanel(member), 0);
      groupPermissionsHeader.setVisible(true);
    }
  }

  @UiHandler("buttonAdd")
  void buttonAddHandler(ClickEvent e) {

    Filter filter = new Filter();

    for (String username : getAssignedUserNames()) {
      filter.add(new NotSimpleFilterParameter(RodaConstants.MEMBERS_ID, username));
    }

    for (String groupname : getAssignedGroupNames()) {
      filter.add(new NotSimpleFilterParameter(RodaConstants.MEMBERS_ID, groupname));
    }

    MemberSelectDialog selectDialog = new MemberSelectDialog(messages.selectUserOrGroupToAdd(), filter);
    selectDialog.showAndCenter();
    selectDialog.addValueChangeHandler(new ValueChangeHandler<RODAMember>() {

      @Override
      public void onValueChange(ValueChangeEvent<RODAMember> event) {
        RODAMember selected = event.getValue();
        if (selected != null) {
          addPermissionPanel(selected);
        }
      }
    });
  }

  public List<String> getAssignedUserNames() {
    List<String> ret = new ArrayList<String>();
    for (int i = 0; i < userPermissionsPanel.getWidgetCount(); i++) {
      PermissionPanel permissionPanel = (PermissionPanel) userPermissionsPanel.getWidget(i);

      if (permissionPanel.isUser()) {
        ret.add(permissionPanel.getName());
      }
    }
    return ret;
  }

  public List<String> getAssignedGroupNames() {
    List<String> ret = new ArrayList<String>();
    for (int i = 0; i < groupPermissionsPanel.getWidgetCount(); i++) {
      PermissionPanel permissionPanel = (PermissionPanel) groupPermissionsPanel.getWidget(i);

      if (!permissionPanel.isUser()) {
        ret.add(permissionPanel.getName());
      }
    }
    return ret;
  }

  public Permissions getPermissions() {
    Permissions permissions = new Permissions();

    for (int i = 0; i < userPermissionsPanel.getWidgetCount(); i++) {
      PermissionPanel permissionPanel = (PermissionPanel) userPermissionsPanel.getWidget(i);

      if (permissionPanel.isUser()) {
        permissions.setUserPermissions(permissionPanel.getName(), permissionPanel.getPermissions());
      } else {
        permissions.setGroupPermissions(permissionPanel.getName(), permissionPanel.getPermissions());
      }
    }

    for (int i = 0; i < groupPermissionsPanel.getWidgetCount(); i++) {
      PermissionPanel permissionPanel = (PermissionPanel) groupPermissionsPanel.getWidget(i);

      if (permissionPanel.isUser()) {
        permissions.setUserPermissions(permissionPanel.getName(), permissionPanel.getPermissions());
      } else {
        permissions.setGroupPermissions(permissionPanel.getName(), permissionPanel.getPermissions());
      }
    }

    return permissions;
  }

  private void apply(boolean recursive) {
    Permissions permissions = getPermissions();

    BrowserService.Util.getInstance().updateAIPPermissions(aips, permissions, recursive,
      new LoadingAsyncCallback<Void>() {

        @Override
        public void onSuccessImpl(Void result) {
          Toast.showInfo(messages.dialogSuccess(), messages.permissionsChanged());
        }

      });
  }

  @UiHandler("buttonApply")
  void buttonApplyHandler(ClickEvent e) {
    apply(false);
  }

  @UiHandler("buttonApplyToAll")
  void buttonApplyToAllHandler(ClickEvent e) {
    apply(true);
  }

  @UiHandler("buttonClose")
  void buttonCancelHandler(ClickEvent e) {
    close();
  }

  public void close() {
    if (aips.size() == 1) {
      Tools.newHistory(Browse.RESOLVER, aips.get(0).getId());
    } else {
      Tools.newHistory(Browse.RESOLVER);
    }
  }

  public class PermissionPanel extends Composite {
    private FlowPanel panel;
    private FlowPanel panelBody;
    private FlowPanel rightPanel;
    private HTML type;
    private Label nameLabel;
    private FlowPanel editPermissionsPanel;
    private Button removePanel;

    private String name;
    private boolean isUser;

    public PermissionPanel(RODAMember member) {
      this(member.getName(), member.isUser(), new HashSet<PermissionType>());
    }

    public PermissionPanel(String name, boolean isUser, Set<PermissionType> permissions) {
      this.name = name;
      this.isUser = isUser;

      panel = new FlowPanel();
      panelBody = new FlowPanel();

      type = new HTML(
        SafeHtmlUtils.fromSafeConstant(isUser ? "<i class='fa fa-user'></i>" : "<i class='fa fa-users'></i>"));
      nameLabel = new Label(name);

      rightPanel = new FlowPanel();

      editPermissionsPanel = new FlowPanel();

      for (PermissionType permissionType : Permissions.PermissionType.values()) {
        ValueCheckBox valueCheckBox = new ValueCheckBox(permissionType);
        if (permissions.contains(permissionType)) {
          valueCheckBox.setValue(true);
        }
        editPermissionsPanel.add(valueCheckBox);
        valueCheckBox.addStyleName("permission-edit-checkbox");
      }

      removePanel = new Button(messages.removeButton());

      panelBody.add(type);
      panelBody.add(nameLabel);
      panelBody.add(rightPanel);

      rightPanel.add(editPermissionsPanel);
      rightPanel.add(removePanel);

      panel.add(panelBody);

      initWidget(panel);

      panel.addStyleName("panel permission");
      panel.addStyleName(isUser ? "permission-user" : "permission-group");
      panelBody.addStyleName("panel-body");
      type.addStyleName("permission-type");
      nameLabel.addStyleName("permission-name");
      rightPanel.addStyleName("pull-right");
      editPermissionsPanel.addStyleName("permission-edit");
      removePanel.addStyleName("permission-remove btn btn-danger btn-ban");

      removePanel.addClickHandler(new ClickHandler() {

        @Override
        public void onClick(ClickEvent event) {
          PermissionPanel.this.removeFromParent();
        }
      });

    }

    public Set<PermissionType> getPermissions() {
      HashSet<PermissionType> permissions = new HashSet<>();
      for (int i = 0; i < editPermissionsPanel.getWidgetCount(); i++) {
        ValueCheckBox valueCheckBox = (ValueCheckBox) editPermissionsPanel.getWidget(i);
        if (valueCheckBox.getValue()) {
          permissions.add(valueCheckBox.getPermissionType());
        }
      }
      return permissions;
    }

    public String getName() {
      return name;
    }

    public boolean isUser() {
      return isUser;
    }

    public class ValueCheckBox extends CheckBox {
      private PermissionType permissionType;

      public ValueCheckBox(PermissionType permissionType) {
        super(messages.objectPermission(permissionType));
        setTitle(messages.objectPermissionDescription(permissionType));
        this.permissionType = permissionType;
      }

      public PermissionType getPermissionType() {
        return permissionType;
      }
    }
  }
}
