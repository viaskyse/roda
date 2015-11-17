/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.common;

import java.util.Date;

import org.roda.core.data.adapter.facet.Facets;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.sort.SortParameter;
import org.roda.core.data.adapter.sort.Sorter;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.IndexResult;
import org.roda.core.data.v2.SimpleDescriptionObject;
import org.roda.wui.client.browse.BrowserService;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.tools.DescriptionLevelUtils;
import org.roda.wui.common.client.widgets.AsyncTableCell;

import com.google.gwt.cell.client.DateCell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.view.client.ProvidesKey;

public class AIPList extends AsyncTableCell<SimpleDescriptionObject> {

  private static final int PAGE_SIZE = 20;

  private final ClientLogger logger = new ClientLogger(getClass().getName());

  private Column<SimpleDescriptionObject, SafeHtml> levelColumn;
  // private TextColumn<SimpleDescriptionObject> idColumn;
  private TextColumn<SimpleDescriptionObject> titleColumn;
  private Column<SimpleDescriptionObject, Date> dateInitialColumn;
  private Column<SimpleDescriptionObject, Date> dateFinalColumn;

  public AIPList() {
    this(null, null, null);
  }

  public AIPList(Filter filter, Facets facets, String summary) {
    super(filter, facets, summary);
  }
  
  @Override
  protected void configureDisplay(CellTable<SimpleDescriptionObject> display) {
    levelColumn = new Column<SimpleDescriptionObject, SafeHtml>(new SafeHtmlCell()) {
      @Override
      public SafeHtml getValue(SimpleDescriptionObject sdo) {
        SafeHtml ret;
        if (sdo == null) {
          logger.error("Trying to display a NULL item");
          ret = null;
        } else {
          ret = DescriptionLevelUtils.getElementLevelIconSafeHtml(sdo.getLevel());
        }
        return ret;
      }
    };

    // idColumn = new TextColumn<SimpleDescriptionObject>() {
    //
    // @Override
    // public String getValue(SimpleDescriptionObject sdo) {
    // return sdo != null ? sdo.getId() : null;
    // }
    // };

    titleColumn = new TextColumn<SimpleDescriptionObject>() {

      @Override
      public String getValue(SimpleDescriptionObject sdo) {
        return sdo != null ? sdo.getTitle() : null;
      }
    };

    dateInitialColumn = new Column<SimpleDescriptionObject, Date>(
      new DateCell(DateTimeFormat.getFormat("yyyy-MM-dd"))) {
      @Override
      public Date getValue(SimpleDescriptionObject sdo) {
        return sdo != null ? sdo.getDateInitial() : null;
      }
    };

    dateFinalColumn = new Column<SimpleDescriptionObject, Date>(new DateCell(DateTimeFormat.getFormat("yyyy-MM-dd"))) {
      @Override
      public Date getValue(SimpleDescriptionObject sdo) {
        return sdo != null ? sdo.getDateFinal() : null;
      }
    };

    levelColumn.setSortable(true);
    // idColumn.setSortable(true);
    titleColumn.setSortable(true);
    dateFinalColumn.setSortable(true);
    dateInitialColumn.setSortable(true);

    // TODO externalize strings into constants
    display.addColumn(levelColumn, SafeHtmlUtils.fromSafeConstant("<i class='fa fa-tag'></i>"));
    // display.addColumn(idColumn, "Id");
    display.addColumn(titleColumn, "Title");
    display.addColumn(dateInitialColumn, "Date initial");
    display.addColumn(dateFinalColumn, "Date final");
    display.setColumnWidth(levelColumn, "35px");
    // display.setAutoHeaderRefreshDisabled(true);
    Label emptyInfo = new Label("No items to display");
    display.setEmptyTableWidget(emptyInfo);
    display.setColumnWidth(titleColumn, "100%");

    // define default sorting
    GWT.log("Defining default sorting");
    display.getColumnSortList().push(new ColumnSortInfo(dateInitialColumn, false));

    dateInitialColumn.setCellStyleNames("nowrap");
    dateFinalColumn.setCellStyleNames("nowrap");

    addStyleName("my-collections-table");
    emptyInfo.addStyleName("my-collections-empty-info");
  }

  @Override
  protected void getData(int start, int length, ColumnSortList columnSortList,
    AsyncCallback<IndexResult<SimpleDescriptionObject>> callback) {

    GWT.log("Getting data");
    Filter filter = getFilter();
    if (filter == null) {
      // search not yet ready, deliver empty result
      callback.onSuccess(null);
    } else {
      // calculate sorter
      Sorter sorter = new Sorter();
      for (int i = 0; i < columnSortList.size(); i++) {
        ColumnSortInfo columnSortInfo = columnSortList.get(i);
        String sortParameterKey;
        if (columnSortInfo.getColumn().equals(levelColumn)) {
          sortParameterKey = RodaConstants.SDO_LEVEL;
          // } else if (columnSortInfo.getColumn().equals(idColumn)) {
          // sortParameterKey = RodaConstants.AIP_ID;
        } else if (columnSortInfo.getColumn().equals(titleColumn)) {
          sortParameterKey = RodaConstants.SDO_TITLE;
        } else if (columnSortInfo.getColumn().equals(dateInitialColumn)) {
          sortParameterKey = RodaConstants.SDO_DATE_INITIAL;
        } else if (columnSortInfo.getColumn().equals(dateFinalColumn)) {
          sortParameterKey = RodaConstants.SDO_DATE_FINAL;
        } else {
          sortParameterKey = null;
        }

        if (sortParameterKey != null) {
          sorter.add(new SortParameter(sortParameterKey, !columnSortInfo.isAscending()));
        } else {
          logger.warn("Selecting a sorter that is not mapped");
        }
      }

      // define sublist
      Sublist sublist = new Sublist(start, length);

      BrowserService.Util.getInstance().findDescriptiveMetadata(filter, sorter, sublist, getFacets(),
        LocaleInfo.getCurrentLocale().getLocaleName(), callback);
    }

  }

  @Override
  protected ProvidesKey<SimpleDescriptionObject> getKeyProvider() {
    return new ProvidesKey<SimpleDescriptionObject>() {

      @Override
      public Object getKey(SimpleDescriptionObject item) {
        return item.getId();
      }
    };
  }

  @Override
  protected int getInitialPageSize() {
    return PAGE_SIZE;
  }


}