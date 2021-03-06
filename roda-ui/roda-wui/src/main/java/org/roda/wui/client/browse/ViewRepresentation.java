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
import java.util.HashMap;
import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.roda.core.data.adapter.filter.EmptyKeyFilterParameter;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.filter.SimpleFilterParameter;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.ip.IndexedFile;
import org.roda.core.data.v2.ip.IndexedRepresentation;
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.metadata.FileFormat;
import org.roda.wui.client.common.Dialogs;
import org.roda.wui.client.common.UserLogin;
import org.roda.wui.client.common.lists.SimpleFileList;
import org.roda.wui.client.common.search.SearchPanel;
import org.roda.wui.client.common.utils.AsyncCallbackUtils;
import org.roda.wui.client.common.utils.JavascriptUtils;
import org.roda.wui.client.common.utils.StringUtils;
import org.roda.wui.client.main.BreadcrumbItem;
import org.roda.wui.client.main.BreadcrumbPanel;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.DescriptionLevelUtils;
import org.roda.wui.common.client.tools.Humanize;
import org.roda.wui.common.client.tools.RestUtils;
import org.roda.wui.common.client.tools.Tools;
import org.roda.wui.common.client.widgets.Toast;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ErrorEvent;
import com.google.gwt.event.dom.client.ErrorHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.media.client.Audio;
import com.google.gwt.media.client.Video;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;

import config.i18n.client.ClientMessages;

/**
 * @author Luis Faria
 * 
 */
public class ViewRepresentation extends Composite {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(final List<String> historyTokens, final AsyncCallback<Widget> callback) {
      BrowserService.Util.getInstance().retrieveViewersProperties(new AsyncCallback<Viewers>() {

        @Override
        public void onSuccess(Viewers viewers) {
          load(viewers, historyTokens, callback);
        }

        @Override
        public void onFailure(Throwable caught) {
          errorRedirect(callback);
        }
      });
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      UserLogin.getInstance().checkRoles(new HistoryResolver[] {Browse.RESOLVER}, false, callback);
    }

    public List<String> getHistoryPath() {
      return Tools.concat(Browse.RESOLVER.getHistoryPath(), getHistoryToken());
    }

    public String getHistoryToken() {
      return "view";
    }

    private void load(final Viewers viewers, final List<String> historyTokens, final AsyncCallback<Widget> callback) {
      if (historyTokens.size() > 1) {
        final String aipId = historyTokens.get(0);
        final String representationUUID = historyTokens.get(1);

        BrowserService.Util.getInstance().retrieveItemBundle(aipId, LocaleInfo.getCurrentLocale().getLocaleName(),
          new AsyncCallback<BrowseItemBundle>() {

            @Override
            public void onFailure(Throwable caught) {
              errorRedirect(callback);
            }

            @Override
            public void onSuccess(final BrowseItemBundle itemBundle) {
              if (itemBundle != null && verifyRepresentation(itemBundle.getRepresentations(), representationUUID)) {
                if (historyTokens.size() > 2) {
                  final String fileUUID = historyTokens.get(2);

                  BrowserService.Util.getInstance().retrieve(IndexedFile.class.getName(), fileUUID,
                    new AsyncCallback<IndexedFile>() {

                      @Override
                      public void onSuccess(IndexedFile simpleFile) {
                        ViewRepresentation view = new ViewRepresentation(viewers, aipId, itemBundle, representationUUID,
                          fileUUID, simpleFile);
                        callback.onSuccess(view);
                      }

                      @Override
                      public void onFailure(Throwable caught) {
                        Toast.showError(caught.getClass().getSimpleName(), caught.getMessage());
                        errorRedirect(callback);
                      }
                    });

                } else {
                  ViewRepresentation view = new ViewRepresentation(viewers, aipId, itemBundle, representationUUID);
                  callback.onSuccess(view);
                }
              } else {
                errorRedirect(callback);
              }
            }
          });
      } else {
        errorRedirect(callback);
      }
    }

    private boolean verifyRepresentation(List<IndexedRepresentation> representations, String representationUUID) {
      boolean exist = false;
      for (IndexedRepresentation representation : representations) {
        if (representation.getUUID().equals(representationUUID)) {
          exist = true;
        }
      }
      return exist;
    }

    private void errorRedirect(AsyncCallback<Widget> callback) {
      Tools.newHistory(Browse.RESOLVER);
      callback.onSuccess(null);
    }
  };

  public static void jumpTo(IndexedFile selected) {
    Tools.newHistory(ViewRepresentation.RESOLVER, selected.getAipId(), selected.getRepresentationUUID(),
      selected.getUUID());
  }

  interface MyUiBinder extends UiBinder<Widget, ViewRepresentation> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  @SuppressWarnings("unused")
  private ClientLogger logger = new ClientLogger(getClass().getName());

  private static final ClientMessages messages = GWT.create(ClientMessages.class);

  private Viewers viewers;
  private String aipId;
  private BrowseItemBundle itemBundle;
  private String representationUUID;
  @SuppressWarnings("unused")
  private String fileUUID;
  private IndexedFile file;
  private Filter defaultFilter;

  private boolean singleFileMode = false;
  private boolean firstLoad = true;

  static final int WINDOW_WIDTH = 800;

  @UiField
  BreadcrumbPanel breadcrumb;

  @UiField
  FocusPanel focusPanel;

  @UiField
  HorizontalPanel previewPanel;

  @UiField
  FlowPanel filesPanel;

  @UiField(provided = true)
  SearchPanel searchPanel;

  @UiField(provided = true)
  SimpleFileList filesList;

  @UiField
  FlowPanel filePreviewPanel;

  @UiField
  FlowPanel filePreview;

  @UiField
  FocusPanel downloadFileButton;

  @UiField
  FocusPanel removeFileButton;

  @UiField
  FocusPanel infoFileButton;

  @UiField
  FlowPanel infoFilePanel;

  @UiField
  FocusPanel downloadDocumentationButton;

  @UiField
  FocusPanel downloadSchemasButton;

  /**
   * Create a new panel to view a representation
   * 
   * @param viewers
   * @param aipId
   * @param itemBundle
   * @param representationId
   * 
   */
  public ViewRepresentation(Viewers viewers, String aipId, BrowseItemBundle itemBundle, String representationId) {
    this(viewers, aipId, itemBundle, representationId, null, null);
  }

  /**
   * Create a new panel to view a representation
   * 
   * @param viewers
   * @param aipId
   * @param itemBundle
   * @param representationId
   * @param fileId
   * 
   */
  public ViewRepresentation(Viewers viewers, String aipId, BrowseItemBundle itemBundle, String representationId,
    String fileId) {
    this(viewers, aipId, itemBundle, representationId, fileId, null);
  }

  /**
   * Create a new panel to view a representation
   * 
   * @param viewers
   * @param aipId
   * @param itemBundle
   * @param representationUUID
   * @param fileUUID
   * @param file
   * 
   */
  public ViewRepresentation(Viewers viewers, String aipId, BrowseItemBundle itemBundle, String representationUUID,
    String fileUUID, IndexedFile file) {
    this.viewers = viewers;
    this.aipId = aipId;
    this.itemBundle = itemBundle;
    this.representationUUID = representationUUID;
    this.fileUUID = fileUUID;
    this.file = file;

    IndexedRepresentation rep = null;
    for (IndexedRepresentation irep : itemBundle.getRepresentations()) {
      if (irep.getUUID().equals(representationUUID)) {
        rep = irep;
        break;
      }
    }

    if (file != null && file.isDirectory()) {
      defaultFilter = new Filter(new SimpleFilterParameter(RodaConstants.FILE_PARENT_UUID, file.getUUID()));
    } else if (file != null && !file.isDirectory() && file.getParentUUID() != null) {
      defaultFilter = new Filter(new SimpleFilterParameter(RodaConstants.FILE_PARENT_UUID, file.getParentUUID()));
    } else {
      defaultFilter = new Filter(new EmptyKeyFilterParameter(RodaConstants.FILE_PARENT_UUID));
    }
    defaultFilter.add(new SimpleFilterParameter(RodaConstants.FILE_AIPID, aipId));
    defaultFilter.add(new SimpleFilterParameter(RodaConstants.FILE_REPRESENTATION_UUID, representationUUID));

    boolean selectable = false;
    boolean justActive = false;
    filesList = new SimpleFileList(defaultFilter, justActive, null, null, selectable);

    searchPanel = new SearchPanel(defaultFilter, RodaConstants.FILE_SEARCH,
      messages.viewRepresentationSearchPlaceHolder(), false, false, false);
    searchPanel.setList(filesList);
    searchPanel.setDefaultFilterIncremental(false);

    initWidget(uiBinder.createAndBindUi(this));

    breadcrumb.updatePath(getBreadcrumbs());
    breadcrumb.setVisible(true);

    downloadFileButton.setVisible(false);
    removeFileButton.setVisible(false);
    infoFileButton.setVisible(false);

    downloadDocumentationButton.setVisible(rep.getNumberOfDocumentationFiles() > 0);
    downloadSchemasButton.setVisible(rep.getNumberOfSchemaFiles() > 0);

    downloadFileButton.setTitle(messages.viewRepresentationDownloadFileButton());
    removeFileButton.setTitle(messages.viewRepresentationRemoveFileButton());
    infoFileButton.setTitle(messages.viewRepresentationInfoFileButton());

    filesList.getSelectionModel().addSelectionChangeHandler(new Handler() {

      @Override
      public void onSelectionChange(SelectionChangeEvent event) {
        IndexedFile selected = filesList.getSelectionModel().getSelectedObject();
        if (selected != null && selected.isDirectory()) {
          jumpTo(selected);
        } else {
          filePreview();
          panelsControl();
          changeInfoFile();
          changeURL();
        }
      }
    });

    filesList.addValueChangeHandler(new ValueChangeHandler<IndexResult<IndexedFile>>() {

      @Override
      public void onValueChange(ValueChangeEvent<IndexResult<IndexedFile>> event) {
        if (firstLoad) {
          List<IndexedFile> results = event.getValue().getResults();

          if (results.size() == 1 && !results.get(0).isDirectory()
            && (ViewRepresentation.this.file == null || results.get(0).equals(ViewRepresentation.this.file))) {
            singleFileMode = true;
            filesList.nextItemSelection();
          } else if (results.size() > 1 && !results.get(0).isDirectory()
            && (ViewRepresentation.this.file == null || ViewRepresentation.this.file.isDirectory())
            && Window.getClientWidth() > WINDOW_WIDTH) {
            filesList.nextItemSelection();
          }

          firstLoad = false;
        }
      }

    });

    focusPanel.addStyleName("viewRepresentationFocusPanel");
    previewPanel.addStyleName("viewRepresentationPreviewPanel");
    filesPanel.addStyleName("viewRepresentationFilesPanel");
    filePreviewPanel.addStyleName("viewRepresentationFilePreviewPanel");
    filePreview.addStyleName("viewRepresentationFilePreview");
    previewPanel.setCellWidth(filePreviewPanel, "100%");

    panelsControl();

    Window.addResizeHandler(new ResizeHandler() {

      @Override
      public void onResize(ResizeEvent event) {
        panelsControl();
      }
    });

    filePreview();

    focusPanel.setFocus(true);
    focusPanel.addKeyDownHandler(new KeyDownHandler() {

      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_LEFT || event.getNativeKeyCode() == KeyCodes.KEY_UP) {
          event.preventDefault();
          filesList.previousItemSelection();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_RIGHT || event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
          event.preventDefault();
          filesList.nextItemSelection();
        }
      }
    });
  }

  private void clean() {
    cleanURL();
    file = null;
    hideRightPanel();
    breadcrumb.updatePath(getBreadcrumbs());
    firstLoad = true;
    filesList.refresh();
  }

  private void changeURL() {
    if (file != null) {
      String url = Window.Location.createUrlBuilder().buildString();
      String viewUrl = url.substring(0, url.indexOf('#'));

      // TODO set representation UUID
      String hashLink = Tools.createHistoryHashLink(ViewRepresentation.RESOLVER, file.getAipId(),
        file.getRepresentationUUID(), file.getUUID());
      viewUrl += hashLink;

      JavascriptUtils.updateURLWithoutReloading(viewUrl);
    }
  }

  private void cleanURL() {
    String url = Window.Location.createUrlBuilder().buildString();
    url = url.substring(0, url.lastIndexOf("/"));
    JavascriptUtils.updateURLWithoutReloading(url);
  }

  private List<BreadcrumbItem> getBreadcrumbs() {
    List<BreadcrumbItem> fullBreadcrumb = new ArrayList<>();
    List<BreadcrumbItem> fileBreadcrumb = new ArrayList<>();

    IndexedAIP aip = itemBundle.getAip();
    List<IndexedRepresentation> representations = itemBundle.getRepresentations();
    IndexedRepresentation rep = selectRepresentation(representations, representationUUID);

    // AIP breadcrumb
    fullBreadcrumb.add(
      new BreadcrumbItem(getBreadcrumbLabel((aip.getTitle() != null) ? aip.getTitle() : aip.getId(), aip.getLevel()),
        Tools.concat(Browse.RESOLVER.getHistoryPath(), aipId)));

    if (file != null) {
      List<String> filePath = file.getPath();
      List<String> pathBuilder = new ArrayList<>();
      pathBuilder.add(aipId);
      pathBuilder.add(representationUUID);
      for (String folder : filePath) {
        pathBuilder.add(folder);
        List<String> path = new ArrayList<>(pathBuilder);
        if (filePath.indexOf(folder) != (filePath.size() - 1)) {
          fileBreadcrumb.add(new BreadcrumbItem(getBreadcrumbLabel(folder, RodaConstants.VIEW_REPRESENTATION_FOLDER),
            Tools.concat(ViewRepresentation.RESOLVER.getHistoryPath(), path)));
        } else {
          fileBreadcrumb.add(
            new BreadcrumbItem(getBreadcrumbLabel(folder, RodaConstants.VIEW_REPRESENTATION_FOLDER), new Command() {

              @Override
              public void execute() {
                clean();
              }
            }));
        }
      }

      String fileLabel = file.getOriginalName() != null ? file.getOriginalName() : file.getId();

      fileBreadcrumb.add(new BreadcrumbItem(
        file.isDirectory() ? getBreadcrumbLabel(fileLabel, RodaConstants.VIEW_REPRESENTATION_FOLDER)
          : getBreadcrumbLabel(fileLabel, RodaConstants.VIEW_REPRESENTATION_FILE),
        Tools.concat(ViewRepresentation.RESOLVER.getHistoryPath(), aipId, representationUUID, file.getId())));
    }

    // Representation breadcrumb
    fullBreadcrumb.add(fileBreadcrumb.size() > 1
      ? new BreadcrumbItem(
        getBreadcrumbLabel(representationType(rep), RodaConstants.VIEW_REPRESENTATION_REPRESENTATION),
        Tools.concat(ViewRepresentation.RESOLVER.getHistoryPath(), aipId, representationUUID))
      : new BreadcrumbItem(
        getBreadcrumbLabel(representationType(rep), RodaConstants.VIEW_REPRESENTATION_REPRESENTATION), new Command() {

          @Override
          public void execute() {
            clean();
          }
        }));

    fullBreadcrumb.addAll(fileBreadcrumb);

    return fullBreadcrumb;
  }

  private IndexedRepresentation selectRepresentation(List<IndexedRepresentation> representations,
    String representationUUID) {
    IndexedRepresentation rep = null;
    for (IndexedRepresentation representation : representations) {
      if (representation.getUUID().equals(representationUUID)) {
        rep = representation;
      }
    }
    return rep;
  }

  private String representationType(Representation rep) {
    SafeHtml labelText;
    String repType = rep.getType();
    if (rep.isOriginal()) {
      labelText = messages.downloadTitleOriginal(repType);
    } else {
      labelText = messages.downloadTitleDefault(repType);
    }
    return labelText.asString();
  }

  private SafeHtml getBreadcrumbLabel(String label, String level) {
    SafeHtml elementLevelIconSafeHtml = getElementLevelIconSafeHtml(level);
    SafeHtmlBuilder builder = new SafeHtmlBuilder();
    builder.append(elementLevelIconSafeHtml).append(SafeHtmlUtils.fromString(label));
    SafeHtml breadcrumbLabel = builder.toSafeHtml();
    return breadcrumbLabel;
  }

  private SafeHtml getElementLevelIconSafeHtml(String level) {
    return DescriptionLevelUtils.getElementLevelIconSafeHtml(level, false);
  }

  @UiHandler("downloadFileButton")
  void buttonDownloadFileButtonHandler(ClickEvent e) {
    downloadFile();
  }

  private void downloadFile() {
    SafeUri downloadUri = null;
    if (file != null) {
      downloadUri = RestUtils.createRepresentationFileDownloadUri(file.getUUID());
    } else if (filesList.getSelectionModel().getSelectedObject() != null) {
      downloadUri = RestUtils
        .createRepresentationFileDownloadUri(filesList.getSelectionModel().getSelectedObject().getUUID());
    }
    if (downloadUri != null) {
      Window.Location.assign(downloadUri.asString());
    }
  }

  @UiHandler("downloadDocumentationButton")
  void buttonDownloadDocumentationButtonHandler(ClickEvent e) {
    SafeUri downloadUri = null;
    downloadUri = RestUtils.createRepresentationPartDownloadUri(representationUUID,
      RodaConstants.STORAGE_DIRECTORY_DOCUMENTATION);
    Window.Location.assign(downloadUri.asString());
  }

  @UiHandler("downloadSchemasButton")
  void buttonDownloadSchemasButtonHandler(ClickEvent e) {
    SafeUri downloadUri = null;
    downloadUri = RestUtils.createRepresentationPartDownloadUri(representationUUID,
      RodaConstants.STORAGE_DIRECTORY_SCHEMAS);
    Window.Location.assign(downloadUri.asString());
  }

  @UiHandler("removeFileButton")
  void buttonRemoveFileButtonHandler(ClickEvent e) {
    Dialogs.showConfirmDialog(messages.viewRepresentationRemoveFileTitle(),
      messages.viewRepresentationRemoveFileMessage(), messages.dialogCancel(), messages.dialogYes(),
      new AsyncCallback<Boolean>() {

        @Override
        public void onSuccess(Boolean confirmed) {
          if (confirmed) {
            BrowserService.Util.getInstance().deleteFile(file.getUUID(), new AsyncCallback<Void>() {

              @Override
              public void onSuccess(Void result) {
                clean();
              }

              @Override
              public void onFailure(Throwable caught) {
                AsyncCallbackUtils.defaultFailureTreatment(caught);
              }
            });
          }
        }

        @Override
        public void onFailure(Throwable caught) {
          // nothing to do
        }
      });
  }

  @UiHandler("infoFileButton")
  void buttonInfoFileButtonHandler(ClickEvent e) {
    toggleRightPanel();
  }

  private void toggleRightPanel() {
    infoFileButton.setStyleName(infoFileButton.getStyleName().contains(" active")
      ? infoFileButton.getStyleName().replace(" active", "") : infoFileButton.getStyleName().concat(" active"));

    changeInfoFile();
    JavascriptUtils.toggleRightPanel(".infoFilePanel");
  }

  private void hideRightPanel() {
    infoFileButton.removeStyleName("active");
    JavascriptUtils.hideRightPanel(".infoFilePanel");
  }

  private void panelsControl() {
    if (file == null || file.isDirectory()) {
      showFilesPanel();
      if (Window.getClientWidth() < WINDOW_WIDTH) {
        hideFilePreview();
      } else {
        showFilePreview();
      }
    } else {
      showFilePreview();
      if (!singleFileMode) {
        if (Window.getClientWidth() < WINDOW_WIDTH) {
          hideFilesPanel();
        } else {
          showFilesPanel();
        }
      } else {
        hideFilesPanel();
      }
    }
  }

  private void showFilesPanel() {
    filesPanel.setVisible(true);
    filePreviewPanel.removeStyleName("single");
  }

  private void hideFilesPanel() {
    filesPanel.setVisible(false);
    filePreviewPanel.addStyleName("single");
  }

  private void showFilePreview() {
    filesPanel.removeStyleName("full_width");
    previewPanel.setCellWidth(filePreviewPanel, "100%");
    filePreviewPanel.setVisible(true);
  }

  private void hideFilePreview() {
    filesPanel.addStyleName("full_width");
    previewPanel.setCellWidth(filePreviewPanel, "0px");
    filePreviewPanel.setVisible(false);
  }

  private void filePreview() {
    filePreview.clear();

    file = (filesList.getSelectionModel().getSelectedObject() != null)
      ? filesList.getSelectionModel().getSelectedObject() : file;
    breadcrumb.updatePath(getBreadcrumbs());

    if (file != null && !file.isDirectory()) {
      downloadFileButton.setVisible(true);
      // removeFileButton.setVisible(true);
      infoFileButton.setVisible(true);

      String type = viewerType(file);
      if (type != null) {
        if (type.equals("image")) {
          imagePreview(file);
        } else if (type.equals("pdf")) {
          pdfPreview(file);
        } else if (type.equals("text")) {
          textPreview(file);
        } else if (type.equals("audio")) {
          audioPreview(file);
        } else if (type.equals("video")) {
          videoPreview(file);
        } else {
          notSupportedPreview();
        }
      } else {
        notSupportedPreview();
      }
    } else {
      emptyPreview();
    }
  }

  private String viewerType(IndexedFile file) {
    String type = null;
    if (file.getFileFormat() != null) {
      if (file.getFileFormat().getPronom() != null) {
        type = viewers.getPronoms().get(file.getFileFormat().getPronom());
      }

      if (file.getFileFormat().getMimeType() != null && type == null) {
        type = viewers.getMimetypes().get(file.getFileFormat().getMimeType());
      }
    }

    String fileName = file.getOriginalName() != null ? file.getOriginalName() : file.getId();

    if (type == null && fileName.lastIndexOf(".") != -1) {
      String extension = fileName.substring(fileName.lastIndexOf("."));
      type = viewers.getExtensions().get(extension);
    }

    return type;
  }

  private void emptyPreview() {
    HTML html = new HTML();
    SafeHtmlBuilder b = new SafeHtmlBuilder();

    b.append(SafeHtmlUtils.fromSafeConstant("<i class='fa fa-file fa-5'></i>"));
    b.append(SafeHtmlUtils.fromSafeConstant("<h4 class='emptymessage'>"));
    b.append(SafeHtmlUtils.fromString(messages.viewRepresentationEmptyPreview()));
    b.append(SafeHtmlUtils.fromSafeConstant("</h4>"));

    html.setHTML(b.toSafeHtml());
    filePreview.add(html);
    html.setStyleName("viewRepresentationEmptyPreview");

    downloadFileButton.setVisible(false);
    removeFileButton.setVisible(false);
    infoFileButton.setVisible(false);
  }

  private void errorPreview() {
    HTML html = new HTML();
    SafeHtmlBuilder b = new SafeHtmlBuilder();

    b.append(SafeHtmlUtils.fromSafeConstant("<i class='fa fa-download fa-5'></i>"));
    b.append(SafeHtmlUtils.fromSafeConstant("<h4 class='errormessage'>"));
    b.append(SafeHtmlUtils.fromString(messages.viewRepresentationErrorPreview()));
    b.append(SafeHtmlUtils.fromSafeConstant("</h4>"));

    Button downloadButton = new Button(messages.viewRepresentationDownloadFileButton());
    downloadButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        downloadFile();
      }
    });

    html.setHTML(b.toSafeHtml());
    filePreview.add(html);
    filePreview.add(downloadButton);
    html.setStyleName("viewRepresentationErrorPreview");
    downloadButton.setStyleName("btn btn-donwload viewRepresentationNotSupportedDownloadButton");
  }

  private void notSupportedPreview() {
    HTML html = new HTML();
    SafeHtmlBuilder b = new SafeHtmlBuilder();

    b.append(SafeHtmlUtils.fromSafeConstant("<i class='fa fa-picture-o fa-5'></i>"));
    b.append(SafeHtmlUtils.fromSafeConstant("<h4 class='errormessage'>"));
    b.append(SafeHtmlUtils.fromString(messages.viewRepresentationNotSupportedPreview()));
    b.append(SafeHtmlUtils.fromSafeConstant("</h4>"));

    Button downloadButton = new Button(messages.viewRepresentationDownloadFileButton());
    downloadButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        downloadFile();
      }
    });

    html.setHTML(b.toSafeHtml());
    filePreview.add(html);
    filePreview.add(downloadButton);
    html.setStyleName("viewRepresentationNotSupportedPreview");
    downloadButton.setStyleName("btn btn-download viewRepresentationNotSupportedDownloadButton");
  }

  private void imagePreview(IndexedFile file) {
    Image image = new Image(RestUtils.createRepresentationFileDownloadUri(file.getUUID()));
    image.addErrorHandler(new ErrorHandler() {

      @Override
      public void onError(ErrorEvent event) {
        filePreview.clear();
        errorPreview();
      }
    });
    filePreview.add(image);
    image.setStyleName("viewRepresentationImageFilePreview");
  }

  private void pdfPreview(IndexedFile file) {
    String viewerHtml = GWT.getHostPageBaseURL() + "pdf/viewer.html?file="
      + encode(GWT.getHostPageBaseURL() + RestUtils.createRepresentationFileDownloadUri(file.getUUID()).asString());

    Frame frame = new Frame(viewerHtml);
    filePreview.add(frame);
    frame.setStyleName("viewRepresentationPDFFilePreview");
  }

  private void textPreview(IndexedFile file) {
    RequestBuilder request = new RequestBuilder(RequestBuilder.GET,
      RestUtils.createRepresentationFileDownloadUri(file.getUUID()).asString());
    try {
      request.sendRequest(null, new RequestCallback() {

        @Override
        public void onResponseReceived(Request request, Response response) {
          if (response.getStatusCode() == HttpStatus.SC_OK) {
            HTML html = new HTML("<pre><code>" + SafeHtmlUtils.htmlEscape(response.getText()) + "</code></pre>");
            FlowPanel frame = new FlowPanel();
            frame.add(html);

            filePreview.add(frame);
            frame.setStyleName("viewRepresentationTextFilePreview");
            JavascriptUtils.runHighlighter(html.getElement());
          } else {
            errorPreview();
          }
        }

        @Override
        public void onError(Request request, Throwable exception) {
          errorPreview();
        }
      });
    } catch (RequestException e) {
      errorPreview();
    }
  }

  private void audioPreview(IndexedFile file) {
    Audio audioPlayer = Audio.createIfSupported();
    if (audioPlayer != null) {
      HTML html = new HTML();
      SafeHtmlBuilder b = new SafeHtmlBuilder();
      b.append(SafeHtmlUtils.fromSafeConstant("<i class='fa fa-headphones fa-5'></i>"));
      html.setHTML(b.toSafeHtml());

      audioPlayer.addSource(RestUtils.createRepresentationFileDownloadUri(file.getUUID()).asString(), "audio/mpeg");
      audioPlayer.setControls(true);
      filePreview.add(html);
      filePreview.add(audioPlayer);
      audioPlayer.addStyleName("viewRepresentationAudioFilePreview");
      html.addStyleName("viewRepresentationAudioFilePreviewHTML");
    } else {
      notSupportedPreview();
    }
  }

  private void videoPreview(IndexedFile file) {
    Video videoPlayer = Video.createIfSupported();
    if (videoPlayer != null) {
      videoPlayer.addSource(RestUtils.createRepresentationFileDownloadUri(file.getUUID()).asString(), "video/dvd");
      videoPlayer.setControls(true);
      filePreview.add(videoPlayer);
      videoPlayer.addStyleName("viewRepresentationAudioFilePreview");
    } else {
      notSupportedPreview();
    }
  }

  private String encode(String string) {
    return string.replace("?", "%3F").replace("=", "%3D");
  }

  public void changeInfoFile() {
    HashMap<String, SafeHtml> values = new HashMap<String, SafeHtml>();
    infoFilePanel.clear();

    if (file != null) {
      String fileName = file.getOriginalName() != null ? file.getOriginalName() : file.getId();
      values.put(messages.viewRepresentationInfoFilename(), SafeHtmlUtils.fromString(fileName));

      values.put(messages.viewRepresentationInfoSize(),
        SafeHtmlUtils.fromString(Humanize.readableFileSize(file.getSize())));

      if (file.getFileFormat() != null) {
        FileFormat fileFormat = file.getFileFormat();

        if (StringUtils.isNotBlank(fileFormat.getMimeType())) {
          values.put(messages.viewRepresentationInfoMimetype(), SafeHtmlUtils.fromString(fileFormat.getMimeType()));
        }

        if (StringUtils.isNotBlank(fileFormat.getFormatDesignationName())) {
          values.put(messages.viewRepresentationInfoFormat(),
            SafeHtmlUtils.fromString(fileFormat.getFormatDesignationName()));
        }

        if (StringUtils.isNotBlank(fileFormat.getPronom())) {
          values.put(messages.viewRepresentationInfoPronom(), SafeHtmlUtils.fromString(fileFormat.getPronom()));
        }

      }

      if (StringUtils.isNotBlank(file.getCreatingApplicationName())) {
        values.put(messages.viewRepresentationInfoCreatingApplicationName(),
          SafeHtmlUtils.fromString(file.getCreatingApplicationName()));
      }

      if (StringUtils.isNotBlank(file.getCreatingApplicationVersion())) {
        values.put(messages.viewRepresentationInfoCreatingApplicationVersion(),
          SafeHtmlUtils.fromString(file.getCreatingApplicationVersion()));
      }

      if (StringUtils.isNotBlank(file.getDateCreatedByApplication())) {
        values.put(messages.viewRepresentationInfoDateCreatedByApplication(),
          SafeHtmlUtils.fromString(file.getDateCreatedByApplication()));
      }

      if (file.getHash() != null && file.getHash().size() > 0) {
        SafeHtmlBuilder b = new SafeHtmlBuilder();
        boolean first = true;
        for (String hash : file.getHash()) {
          if (first) {
            first = false;
          } else {
            b.append(SafeHtmlUtils.fromSafeConstant("<br/>"));
          }
          b.append(SafeHtmlUtils.fromSafeConstant("<small>"));
          b.append(SafeHtmlUtils.fromString(hash));
          b.append(SafeHtmlUtils.fromSafeConstant("</small>"));
        }
        values.put(messages.viewRepresentationInfoHash(), b.toSafeHtml());
      }

      if (file.getStoragePath() != null) {
        SafeHtmlBuilder b = new SafeHtmlBuilder();
        b.append(SafeHtmlUtils.fromSafeConstant("<small>"));
        b.append(SafeHtmlUtils.fromString(file.getStoragePath()));
        b.append(SafeHtmlUtils.fromSafeConstant("</small>"));

        values.put(messages.viewRepresentationInfoStoragePath(), b.toSafeHtml());
      }
    }

    for (String key : values.keySet()) {
      FlowPanel entry = new FlowPanel();

      Label keyLabel = new Label(key);
      HTML valueLabel = new HTML(values.get(key));

      entry.add(keyLabel);
      entry.add(valueLabel);

      infoFilePanel.add(entry);

      keyLabel.addStyleName("infoFileEntryKey");
      valueLabel.addStyleName("infoFileEntryValue");
      entry.addStyleName("infoFileEntry");
    }
  }
}
