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
package org.roda.wui.client.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.index.IsIndexed;
import org.roda.core.data.v2.index.SelectedItems;
import org.roda.core.data.v2.index.SelectedItemsList;
import org.roda.core.data.v2.index.SelectedItemsNone;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.ip.TransferredResource;
import org.roda.core.data.v2.jobs.PluginInfo;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.wui.client.browse.Browse;
import org.roda.wui.client.browse.BrowserService;
import org.roda.wui.client.common.utils.PluginUtils;
import org.roda.wui.client.ingest.process.PluginOptionsPanel;
import org.roda.wui.client.ingest.transfer.IngestTransfer;
import org.roda.wui.client.process.CreateActionJob;
import org.roda.wui.client.process.CreateIngestJob;
import org.roda.wui.client.process.Process;
import org.roda.wui.client.search.Search;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.Tools;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import config.i18n.client.ClientMessages;

/**
 * @author Luis Faria
 * 
 */
public abstract class CreateJob<T extends IsIndexed> extends Composite {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, final AsyncCallback<Widget> callback) {
      if (historyTokens.size() == 1) {
        if (historyTokens.get(0).equals("ingest")) {
          CreateIngestJob createIngestJob = new CreateIngestJob();
          callback.onSuccess(createIngestJob);
        } else if (historyTokens.get(0).equals("action")) {
          CreateActionJob createActionJob = new CreateActionJob();
          callback.onSuccess(createActionJob);
        } else {
          Tools.newHistory(CreateJob.RESOLVER);
          callback.onSuccess(null);
        }
      } else if (historyTokens.size() == 2) {
        if (historyTokens.get(0).equals("action")) {
          SelectedItems items = Browse.getInstance().getSelected();
          SelectedItemsList list = SelectedItemsList.create(IndexedAIP.class, Arrays.asList(historyTokens.get(1)));

          if (items instanceof SelectedItemsNone) {
            items = list;
          } else if (items instanceof SelectedItemsList) {
            SelectedItemsList itemsList = (SelectedItemsList) items;
            if (itemsList.getIds().isEmpty()) {
              items = list;
            }
          }

          CreateActionJob createActionJob = new CreateActionJob(items);
          callback.onSuccess(createActionJob);
        } else {
          Tools.newHistory(CreateJob.RESOLVER);
          callback.onSuccess(null);
        }
      } else {
        Tools.newHistory(CreateJob.RESOLVER);
        callback.onSuccess(null);
      }
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      UserLogin.getInstance().checkRoles(new HistoryResolver[] {Process.RESOLVER}, false, callback);
    }

    public List<String> getHistoryPath() {
      return Tools.concat(Process.RESOLVER.getHistoryPath(), getHistoryToken());
    }

    public String getHistoryToken() {
      return "create";
    }
  };

  @SuppressWarnings("rawtypes")
  public interface MyUiBinder extends UiBinder<Widget, CreateJob> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);
  private static final ClientMessages messages = GWT.create(ClientMessages.class);

  private SelectedItems<?> selected = new SelectedItemsNone<>();
  private List<PluginInfo> plugins = null;
  private PluginInfo selectedPlugin = null;
  private String listSelectedClass = TransferredResource.class.getName();
  private boolean isIngest = false;

  @UiField
  TextBox name;

  @UiField
  FlowPanel targetPanel;

  @UiField
  Label selectedObject;

  @UiField
  Label workflowCategoryLabel;

  @UiField
  FlowPanel workflowCategoryList;

  @UiField
  ListBox workflowList;

  @UiField
  Label workflowListDescription;

  @UiField
  FlowPanel workflowPanel;

  @UiField
  PluginOptionsPanel workflowOptions;

  @UiField
  Button buttonCreate;

  @UiField
  Button buttonCancel;

  public CreateJob(Class<T> classToReceive, final List<PluginType> pluginType, SelectedItems items) {
    this.selected = items;
    getInformation(classToReceive, pluginType);
  }

  public CreateJob(Class<T> classToReceive, final List<PluginType> pluginType) {
    getInformation(classToReceive, pluginType);
  }

  private void getInformation(Class<T> classToReceive, final List<PluginType> pluginType) {
    if (classToReceive.getName().equals(TransferredResource.class.getName())) {
      this.selected = IngestTransfer.getInstance().getSelected();
      isIngest = true;
    } else {
      if (selected instanceof SelectedItemsNone) {
        this.selected = Search.getInstance().getSelected();
      }
      isIngest = false;
    }

    initWidget(uiBinder.createAndBindUi(this));

    boolean isEmpty = updateObjectList();

    if (isEmpty && isIngest) {
      Tools.newHistory(IngestTransfer.RESOLVER);
    }

    BrowserService.Util.getInstance().retrievePluginsInfo(pluginType, new AsyncCallback<List<PluginInfo>>() {

      @Override
      public void onFailure(Throwable caught) {
        // do nothing
      }

      @Override
      public void onSuccess(List<PluginInfo> pluginsInfo) {
        init(pluginsInfo);
      }
    });
  }

  public void init(List<PluginInfo> plugins) {

    this.plugins = plugins;

    name.setText(messages.processNewDefaultName(new Date()));
    workflowOptions.setPlugins(plugins);
    configurePlugins(selected.getSelectedClass());

    workflowCategoryList.addStyleName("form-listbox-job");
    workflowList.addChangeHandler(new ChangeHandler() {

      @Override
      public void onChange(ChangeEvent event) {
        String selectedPluginId = workflowList.getSelectedValue();
        if (selectedPluginId != null) {
          CreateJob.this.selectedPlugin = lookupPlugin(selectedPluginId);
        }
        updateWorkflowOptions();
      }
    });
  }

  public abstract boolean updateObjectList();

  public void configurePlugins(final String selectedClass) {
    List<String> categoriesOnListBox = new ArrayList<String>();

    if (plugins != null) {
      PluginUtils.sortByName(plugins);

      for (PluginInfo pluginInfo : plugins) {
        if (pluginInfo != null) {

          List<String> pluginCategories = pluginInfo.getCategories();

          if (pluginCategories != null) {
            for (String category : pluginCategories) {
              if (!categoriesOnListBox.contains(category)
                && !category.equals(RodaConstants.PLUGIN_CATEGORY_NOT_LISTABLE)
                && ((!isSelectedEmpty() && pluginInfo.hasObjectClass(selectedClass))
                  || (isSelectedEmpty() && pluginInfo.hasObjectClass(listSelectedClass)))) {

                CheckBox box = new CheckBox();
                box.setText(messages.showPluginCategories(category));
                box.setName(category);
                box.addStyleName("form-checkbox-job");

                box.addValueChangeHandler(new ValueChangeHandler<Boolean>() {

                  @Override
                  public void onValueChange(ValueChangeEvent<Boolean> event) {
                    workflowList.clear();
                    boolean noChecks = true;

                    if (plugins != null) {
                      PluginUtils.sortByName(plugins);
                      for (PluginInfo pluginInfo : plugins) {
                        if (pluginInfo != null) {
                          List<String> categories = pluginInfo.getCategories();

                          if (categories != null) {
                            for (int i = 0; i < workflowCategoryList.getWidgetCount(); i++) {
                              CheckBox checkbox = (CheckBox) workflowCategoryList.getWidget(i);

                              if (checkbox.getValue()) {
                                noChecks = false;

                                if (categories.contains(checkbox.getName())
                                  && !categories.contains(RodaConstants.PLUGIN_CATEGORY_NOT_LISTABLE)
                                  && ((!isSelectedEmpty() && pluginInfo.hasObjectClass(selectedClass))
                                    || (isSelectedEmpty() && pluginInfo.hasObjectClass(listSelectedClass)))) {
                                  workflowList.addItem(
                                    messages.pluginLabel(pluginInfo.getName(), pluginInfo.getVersion()),
                                    pluginInfo.getId());
                                }

                              }
                            }

                            if (noChecks) {
                              if (!pluginInfo.getCategories().contains(RodaConstants.PLUGIN_CATEGORY_NOT_LISTABLE)
                                && ((!isSelectedEmpty() && pluginInfo.hasObjectClass(selectedClass))
                                  || (isSelectedEmpty() && pluginInfo.hasObjectClass(listSelectedClass)))) {
                                workflowList.addItem(
                                  messages.pluginLabel(pluginInfo.getName(), pluginInfo.getVersion()),
                                  pluginInfo.getId());
                              }
                            }
                          }
                        }
                      }
                    }

                    String selectedPluginId = workflowList.getSelectedValue();
                    if (selectedPluginId != null) {
                      CreateJob.this.selectedPlugin = lookupPlugin(selectedPluginId);
                    }
                    updateWorkflowOptions();
                  }

                });

                workflowCategoryList.add(box);
                categoriesOnListBox.add(category);
              }
            }

            if (!pluginCategories.contains(RodaConstants.PLUGIN_CATEGORY_NOT_LISTABLE)
              && ((!isSelectedEmpty() && pluginInfo.hasObjectClass(selectedClass))
                || (isSelectedEmpty() && pluginInfo.hasObjectClass(listSelectedClass)))) {
              workflowList.addItem(messages.pluginLabel(pluginInfo.getName(), pluginInfo.getVersion()),
                pluginInfo.getId());
            }
          }

        } else {
          GWT.log("Got a null plugin");
        }
      }

      String selectedPluginId = workflowList.getSelectedValue();
      if (selectedPluginId != null) {
        CreateJob.this.selectedPlugin = lookupPlugin(selectedPluginId);
      }
      updateWorkflowOptions();
    }
  }

  protected void updateWorkflowOptions() {
    if (selectedPlugin == null) {
      workflowListDescription.setText("");
      workflowListDescription.setVisible(false);
      workflowOptions.setPluginInfo(null);
    } else {
      name.setText(selectedPlugin.getName());
      String description = selectedPlugin.getDescription();
      if (description != null && description.length() > 0) {
        workflowListDescription.setText(description);
        workflowListDescription.setVisible(true);
      } else {
        workflowListDescription.setVisible(false);
      }

      if (selectedPlugin.getParameters().size() == 0) {
        workflowPanel.setVisible(false);
      } else {
        workflowPanel.setVisible(true);
        workflowOptions.setPluginInfo(selectedPlugin);
      }

    }
  }

  private PluginInfo lookupPlugin(String selectedPluginId) {
    PluginInfo p = null;
    if (plugins != null && selectedPluginId != null) {
      for (PluginInfo pluginInfo : plugins) {
        if (pluginInfo != null && pluginInfo.getId().equals(selectedPluginId)) {
          p = pluginInfo;
          break;
        }
      }
    }
    return p;
  }

  @UiHandler("buttonCreate")
  public abstract void buttonCreateHandler(ClickEvent e);

  @UiHandler("buttonCancel")
  void buttonCancelHandler(ClickEvent e) {
    cancel();
  }

  public abstract void cancel();

  public SelectedItems<?> getSelected() {
    return selected;
  }

  public void setSelected(SelectedItems<?> selected) {
    this.selected = selected;
  }

  public PluginInfo getSelectedPlugin() {
    return selectedPlugin;
  }

  public void setSelectedPlugin(PluginInfo selectedPlugin) {
    this.selectedPlugin = selectedPlugin;
  }

  public FlowPanel getTargetPanel() {
    return this.targetPanel;
  }

  public Button getButtonCreate() {
    return this.buttonCreate;
  }

  public TextBox getName() {
    return this.name;
  }

  public ListBox getWorkflowList() {
    return workflowList;
  }

  public PluginOptionsPanel getWorkflowOptions() {
    return this.workflowOptions;
  }

  public void setJobSelectedDescription(String text) {
    selectedObject.setText(text);
  }

  public void setCategoryListBoxVisible(boolean visible) {
    workflowCategoryLabel.setVisible(visible);
    workflowCategoryList.setVisible(visible);
  }

  public String getSelectedClass() {
    return listSelectedClass;
  }

  public void setSelectedClass(String selectedClass) {
    this.listSelectedClass = selectedClass;
  }

  public FlowPanel getCategoryList() {
    return workflowCategoryList;
  }

  public boolean isSelectedEmpty() {
    if (selected instanceof SelectedItemsList) {
      return (((SelectedItemsList) selected).getIds().isEmpty());
    }
    return false;
  }

}
