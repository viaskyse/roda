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
package org.roda.wui.client.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.roda.core.data.adapter.facet.FacetParameter;
import org.roda.core.data.adapter.facet.SimpleFacetParameter;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.filter.NotSimpleFilterParameter;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.v2.index.IsIndexed;
import org.roda.core.data.v2.index.SelectedItems;
import org.roda.core.data.v2.index.SelectedItemsList;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.ip.IndexedFile;
import org.roda.core.data.v2.ip.IndexedRepresentation;
import org.roda.wui.client.browse.Browse;
import org.roda.wui.client.browse.BrowserService;
import org.roda.wui.client.common.CreateJob;
import org.roda.wui.client.common.Dialogs;
import org.roda.wui.client.common.LoadingAsyncCallback;
import org.roda.wui.client.common.UserLogin;
import org.roda.wui.client.common.dialogs.SelectAipDialog;
import org.roda.wui.client.common.lists.SelectedItemsUtils;
import org.roda.wui.client.common.search.MainSearch;
import org.roda.wui.client.common.utils.AsyncCallbackUtils;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.Tools;
import org.roda.wui.common.client.widgets.HTMLWidgetWrapper;
import org.roda.wui.common.client.widgets.Toast;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import config.i18n.client.ClientMessages;

/**
 * @author Luis Faria
 * 
 */
public class Search extends Composite {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
      getInstance().resolve(historyTokens, callback);
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      UserLogin.getInstance().checkRole(this, callback);
    }

    @Override
    public List<String> getHistoryPath() {
      return Arrays.asList(getHistoryToken());
    }

    @Override
    public String getHistoryToken() {
      return "search";
    }
  };

  private static Search instance = null;

  public static Search getInstance() {
    if (instance == null) {
      instance = new Search();
    }
    return instance;
  }

  interface MyUiBinder extends UiBinder<Widget, Search> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  private static final ClientMessages messages = GWT.create(ClientMessages.class);

  @SuppressWarnings("unused")
  private ClientLogger logger = new ClientLogger(getClass().getName());

  @UiField
  FlowPanel searchDescription;

  @UiField(provided = true)
  MainSearch mainSearch;

  // FILTERS
  @UiField(provided = true)
  FlowPanel itemsFacets;
  @UiField(provided = true)
  FlowPanel facetDescriptionLevels;
  @UiField(provided = true)
  FlowPanel facetHasRepresentations;

  @UiField(provided = true)
  FlowPanel filesFacets;
  @UiField(provided = true)
  FlowPanel facetFormats;
  @UiField(provided = true)
  FlowPanel facetPronoms;
  @UiField(provided = true)
  FlowPanel facetMimetypes;

  @UiField(provided = true)
  Button newJobButton;

  @UiField(provided = true)
  Button moveItem, remove;

  boolean justActive = true;
  boolean itemsSelectable = true;
  boolean representationsSelectable = true;
  boolean filesSelectable = true;

  private Search() {
    // Variables
    itemsFacets = new FlowPanel();
    facetDescriptionLevels = new FlowPanel();
    facetHasRepresentations = new FlowPanel();

    filesFacets = new FlowPanel();
    facetFormats = new FlowPanel();
    facetPronoms = new FlowPanel();
    facetMimetypes = new FlowPanel();

    newJobButton = new Button();
    moveItem = new Button();
    remove = new Button();

    // Define facets and facets panels
    Map<FacetParameter, FlowPanel> itemsFacetsMap = new HashMap<FacetParameter, FlowPanel>();
    Map<FacetParameter, FlowPanel> representationsFacetsMap = new HashMap<FacetParameter, FlowPanel>();
    Map<FacetParameter, FlowPanel> filesFacetsMap = new HashMap<FacetParameter, FlowPanel>();

    itemsFacetsMap.put(new SimpleFacetParameter(RodaConstants.AIP_LEVEL), facetDescriptionLevels);
    itemsFacetsMap.put(new SimpleFacetParameter(RodaConstants.AIP_HAS_REPRESENTATIONS), facetHasRepresentations);

    filesFacetsMap.put(new SimpleFacetParameter(RodaConstants.FILE_FILEFORMAT), facetFormats);
    filesFacetsMap.put(new SimpleFacetParameter(RodaConstants.FILE_PRONOM), facetPronoms);
    filesFacetsMap.put(new SimpleFacetParameter(RodaConstants.FILE_FORMAT_MIMETYPE), facetMimetypes);

    // Define hide/visible buttons
    Map<Button, Boolean> itemsButtons = new HashMap<Button, Boolean>();
    Map<Button, Boolean> representationsButtons = new HashMap<Button, Boolean>();
    Map<Button, Boolean> filesButtons = new HashMap<Button, Boolean>();

    itemsButtons.put(moveItem, true);
    representationsButtons.put(moveItem, false);
    filesButtons.put(moveItem, false);

    // Define active buttons
    List<Button> itemsSelectionButtons = new ArrayList<>();
    List<Button> representationsSelectionButtons = new ArrayList<>();
    List<Button> filesSelectionButtons = new ArrayList<>();

    itemsSelectionButtons.add(moveItem);

    itemsSelectionButtons.add(remove);
    representationsSelectionButtons.add(remove);
    filesSelectionButtons.add(remove);

    itemsSelectionButtons.add(newJobButton);
    representationsSelectionButtons.add(newJobButton);
    filesSelectionButtons.add(newJobButton);

    // Create main search
    mainSearch = new MainSearch(justActive, itemsSelectable, representationsSelectable, filesSelectable, itemsFacets,
      itemsFacetsMap, itemsButtons, itemsSelectionButtons, new FlowPanel(), representationsFacetsMap,
      representationsButtons, representationsSelectionButtons, filesFacets, filesFacetsMap, filesButtons,
      filesSelectionButtons);

    initWidget(uiBinder.createAndBindUi(this));

    searchDescription.add(new HTMLWidgetWrapper("SearchDescription.html"));

    newJobButton.setEnabled(false);
    moveItem.setEnabled(false);
  }

  public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
    mainSearch.defaultFilters();
    if (historyTokens.isEmpty()) {
      mainSearch.search();
      callback.onSuccess(this);
    } else {
      // #search/TYPE/key/value/key/value
      boolean successful = mainSearch.setSearch(historyTokens);
      if (successful) {
        mainSearch.search();
        callback.onSuccess(this);
      } else {
        Tools.newHistory(RESOLVER);
        callback.onSuccess(null);
      }
    }
  }

  @UiHandler("newJobButton")
  void buttonStartIngestHandler(ClickEvent e) {
    Tools.newHistory(CreateJob.RESOLVER, "action");
  }

  public void clearSelected() {
    mainSearch.clearSelected();
  }

  public SelectedItems<?> getSelected() {
    return mainSearch.getSelected();
  }

  @SuppressWarnings("unchecked")
  @UiHandler("moveItem")
  void buttonMoveItemHandler(ClickEvent e) {
    final SelectedItems<IndexedAIP> selected = (SelectedItems<IndexedAIP>) getSelected();

    // Move all selected
    Filter filter;
    boolean showEmptyParentButton;

    filter = new Filter();

    if (selected instanceof SelectedItemsList) {
      SelectedItemsList<IndexedAIP> list = (SelectedItemsList<IndexedAIP>) selected;
      for (String id : list.getIds()) {
        filter.add(new NotSimpleFilterParameter(RodaConstants.AIP_ANCESTORS, id));
        filter.add(new NotSimpleFilterParameter(RodaConstants.AIP_ID, id));
      }
    } else {
      filter = Filter.NULL;
    }

    showEmptyParentButton = false;

    SelectAipDialog selectAipDialog = new SelectAipDialog(messages.moveItemTitle(), filter, mainSearch.isJustActive(),
      true);
    selectAipDialog.setEmptyParentButtonVisible(showEmptyParentButton);
    selectAipDialog.showAndCenter();
    selectAipDialog.addValueChangeHandler(new ValueChangeHandler<IndexedAIP>() {

      @Override
      public void onValueChange(ValueChangeEvent<IndexedAIP> event) {
        final IndexedAIP parentAIP = event.getValue();
        final String parentId = (parentAIP != null) ? parentAIP.getId() : null;

        BrowserService.Util.getInstance().moveAIPInHierarchy(selected, parentId,
          new LoadingAsyncCallback<IndexedAIP>() {

            @Override
            public void onSuccessImpl(IndexedAIP result) {
              if (result != null) {
                Tools.newHistory(Browse.RESOLVER, result.getId());
              } else {
                Tools.newHistory(Search.RESOLVER);
              }
            }

            @Override
            public void onFailureImpl(Throwable caught) {
              if (caught instanceof NotFoundException) {
                Toast.showError(messages.moveNoSuchObject(caught.getMessage()));
              } else {
                AsyncCallbackUtils.defaultFailureTreatment(caught);
              }
            }
          });
      }
    });

  }

  @UiHandler("remove")
  <T extends IsIndexed> void buttonRemoveHandler(ClickEvent e) {
    final SelectedItems<T> selected = (SelectedItems<T>) getSelected();
    final String selectedClass = getSelected().getSelectedClass();

    if (!SelectedItemsUtils.isEmpty(selected)) {

      Dialogs.showConfirmDialog(messages.ingestTransferRemoveFolderConfirmDialogTitle(),
        messages.ingestTransferRemoveAllSelectedConfirmDialogMessage(),
        messages.ingestTransferRemoveFolderConfirmDialogCancel(), messages.ingestTransferRemoveFolderConfirmDialogOk(),
        new AsyncCallback<Boolean>() {

          @Override
          public void onSuccess(Boolean confirmed) {
            if (confirmed) {
              if (IndexedAIP.class.getName().equals(selectedClass)) {
                final SelectedItems<IndexedAIP> aips = (SelectedItems<IndexedAIP>) selected;
                BrowserService.Util.getInstance().deleteAIP(aips, new LoadingAsyncCallback<String>() {

                  @Override
                  public void onFailureImpl(Throwable caught) {
                    AsyncCallbackUtils.defaultFailureTreatment(caught);
                    mainSearch.refresh();
                  }

                  @Override
                  public void onSuccessImpl(String parentId) {
                    Toast.showInfo(messages.ingestTransferRemoveSuccessTitle(),
                      messages.ingestTransferRemoveAllSuccessMessage());
                    mainSearch.refresh();
                  }
                });
              } else if (IndexedRepresentation.class.getName().equals(selectedClass)) {
                final SelectedItems<IndexedRepresentation> reps = (SelectedItems<IndexedRepresentation>) selected;
                BrowserService.Util.getInstance().deleteRepresentation(reps, new LoadingAsyncCallback<Void>() {

                  @Override
                  public void onFailureImpl(Throwable caught) {
                    AsyncCallbackUtils.defaultFailureTreatment(caught);
                    mainSearch.refresh();
                  }

                  @Override
                  public void onSuccessImpl(Void returned) {
                    Toast.showInfo(messages.ingestTransferRemoveSuccessTitle(),
                      messages.ingestTransferRemoveAllSuccessMessage());
                    mainSearch.refresh();
                  }
                });
              } else if (IndexedFile.class.getName().equals(selectedClass)) {
                final SelectedItems<IndexedFile> files = (SelectedItems<IndexedFile>) selected;
                BrowserService.Util.getInstance().deleteFile(files, new LoadingAsyncCallback<Void>() {

                  @Override
                  public void onFailureImpl(Throwable caught) {
                    AsyncCallbackUtils.defaultFailureTreatment(caught);
                    mainSearch.refresh();
                  }

                  @Override
                  public void onSuccessImpl(Void returned) {
                    Toast.showInfo(messages.ingestTransferRemoveSuccessTitle(),
                      messages.ingestTransferRemoveAllSuccessMessage());
                    mainSearch.refresh();
                  }
                });
              }
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            AsyncCallbackUtils.defaultFailureTreatment(caught);
          }
        });

    }
  }

}
