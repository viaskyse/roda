/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.browse;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONException;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.datepicker.client.DateBox;

import config.i18n.client.ClientMessages;

/**
 * Created by adrapereira on 13-06-2016.
 */
public class FormUtilities {
  private static final ClientMessages messages = GWT.create(ClientMessages.class);

  public static void create(FlowPanel panel, Set<MetadataValue> bundle, boolean addStyle) {
    for (MetadataValue mv : bundle) {
      boolean mandatory = (mv.get("mandatory") != null && mv.get("mandatory").equalsIgnoreCase("true")) ? true : false;

      if (mv.get("hidden") != null && mv.get("hidden").equals("true"))
        continue;

      FlowPanel layout = new FlowPanel();

      if (addStyle) {
        layout.addStyleName("metadata-form-field");
      }
      String controlType = mv.get("type");
      if (controlType == null) {
        addTextField(panel, layout, mv, mandatory);
      } else {
        switch (controlType) {
          case "text":
            addTextField(panel, layout, mv, mandatory);
            break;
          case "textarea":
          case "big-text":
          case "text-area":
            addTextArea(panel, layout, mv, mandatory);
            break;
          case "list":
            addList(panel, layout, mv, mandatory);
            break;
          case "date":
            addDatePicker(panel, layout, mv, mandatory);
            break;
          case "separator":
            layout.addStyleName("form-separator");
            addSeparator(panel, layout, mv);
            break;
          default:
            addTextField(panel, layout, mv, mandatory);
            break;
        }
      }
    }
  }

  private static String getFieldLabel(MetadataValue mv) {
    String result = mv.getId();
    String rawLabel = mv.get("label");
    if (rawLabel != null && rawLabel.length() > 0) {
      String loc = LocaleInfo.getCurrentLocale().getLocaleName();
      try {
        JSONObject jsonObject = JSONParser.parseLenient(rawLabel).isObject();
        JSONValue jsonValue = jsonObject.get(loc);
        if (jsonValue != null) {
          JSONString jsonString = jsonObject.get(loc).isString();
          if (jsonString != null) {
            result = jsonString.stringValue();
          }
        } else {
          // label for the desired language doesn't exist
          // do nothing
        }
      } catch (JSONException e) {
        // The JSON was malformed
        // do nothing
      }
    }
    mv.set("l", result);
    return result;
  }

  private static void addTextField(FlowPanel panel, final FlowPanel layout, final MetadataValue mv,
    final boolean mandatory) {
    // Top label
    Label mvLabel = new Label(getFieldLabel(mv));
    mvLabel.addStyleName("form-label");
    if (mandatory) {
      mvLabel.addStyleName("form-label-mandatory");
    }
    // Field
    final TextBox mvText = new TextBox();
    mvText.addStyleName("form-textbox");
    if (mv.get("value") != null) {
      mvText.setText(mv.get("value"));
    }

    mvText.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent changeEvent) {
        mv.set("value", mvText.getValue());
        if (mandatory && (mvText.getValue() != null && !mvText.getValue().trim().equalsIgnoreCase(""))) {
          mvText.removeStyleName("isWrong");
        } else if (mandatory && (mvText.getValue() == null || mvText.getValue().trim().equalsIgnoreCase(""))) {
          mvText.addStyleName("isWrong");
        }
      }
    });

    layout.add(mvLabel);
    layout.add(mvText);

    // Description
    String description = mv.get("description");
    if (description != null && description.length() > 0) {
      Label mvDescription = new Label(description);
      mvDescription.addStyleName("form-help");
      layout.add(mvDescription);
    }

    if (mv.get("error") != null && !mv.get("error").trim().equalsIgnoreCase("")) {
      Label errorLabel = new Label(mv.get("error"));
      errorLabel.addStyleName("form-label-error");
      layout.add(errorLabel);
      mvText.addStyleName("isWrong");
    }
    panel.add(layout);
  }

  private static void addTextArea(FlowPanel panel, final FlowPanel layout, final MetadataValue mv,
    final boolean mandatory) {
    // Top label
    Label mvLabel = new Label(getFieldLabel(mv));
    mvLabel.addStyleName("form-label");
    if (mandatory) {
      mvLabel.addStyleName("form-label-mandatory");
    }
    // Field
    final TextArea mvText = new TextArea();
    mvText.addStyleName("form-textbox metadata-form-text-area");
    if (mv.get("value") != null) {
      mvText.setText(mv.get("value"));
    }

    mvText.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent changeEvent) {
        mv.set("value", mvText.getValue());
        if (mandatory && (mvText.getValue() != null && !mvText.getValue().trim().equalsIgnoreCase(""))) {
          mvText.removeStyleName("isWrong");
        } else if (mandatory && (mvText.getValue() == null || mvText.getValue().trim().equalsIgnoreCase(""))) {
          mvText.addStyleName("isWrong");
        }
      }
    });

    layout.add(mvLabel);
    layout.add(mvText);

    // Description
    String description = mv.get("description");
    if (description != null && description.length() > 0) {
      Label mvDescription = new Label(description);
      mvDescription.addStyleName("form-help");
      layout.add(mvDescription);
    }
    if (mv.get("error") != null && !mv.get("error").trim().equalsIgnoreCase("")) {
      Label errorLabel = new Label(mv.get("error"));
      errorLabel.addStyleName("form-label-error");
      layout.add(errorLabel);
      mvText.addStyleName("isWrong");
    }
    panel.add(layout);
  }

  private static void addList(FlowPanel panel, final FlowPanel layout, final MetadataValue mv,
    final boolean mandatory) {
    // Top Label
    Label mvLabel = new Label(getFieldLabel(mv));
    mvLabel.addStyleName("form-label");
    if (mandatory) {
      mvLabel.addStyleName("form-label-mandatory");
    }
    // Field
    final ListBox mvList = new ListBox();
    mvList.addStyleName("form-textbox");

    String list = mv.get("options");
    mvList.addItem("");
    if (list != null) {
      JSONArray jsonArray = JSONParser.parseLenient(list).isArray();
      if (jsonArray != null) {
        for (int i = 0; i < jsonArray.size(); i++) {
          String value = jsonArray.get(i).isString().stringValue();
          mvList.addItem(value);

          if (value.equals(mv.get("value"))) {
            mvList.setSelectedIndex(i + 1);
          }
        }
      } else {
        JSONObject jsonObject = JSONParser.parseLenient(list).isObject();
        if (jsonObject != null) {
          String loc = LocaleInfo.getCurrentLocale().getLocaleName();
          int i = 0;
          for (String key : jsonObject.keySet()) {
            JSONValue entry = jsonObject.get(key);
            if (entry.isObject() != null) {
              JSONValue jsonValue = entry.isObject().get(loc);
              String value = null;
              if (jsonValue != null) {
                value = jsonValue.isString().stringValue();
              } else {
                value = entry.isObject().get(entry.isObject().keySet().iterator().next()).isString().stringValue();
              }
              if (value != null) {
                mvList.addItem(value, key);
                if (key.equals(mv.get("value"))) {
                  mvList.setSelectedIndex(i + 1);
                }
              }
            }
            i++;
          }
        }
      }
    }

    mvList.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent changeEvent) {
        mv.set("value", mvList.getSelectedValue());
        if (mandatory
          && (mvList.getSelectedValue() != null && !mvList.getSelectedValue().trim().equalsIgnoreCase(""))) {
          mvList.removeStyleName("isWrong");
        } else if (mandatory
          && (mvList.getSelectedValue() == null || mvList.getSelectedValue().trim().equalsIgnoreCase(""))) {
          mvList.removeStyleName("isWrong");
        }
      }
    });

    if (mv.get("value") == null || mv.get("value").isEmpty()) {
      mvList.setSelectedIndex(0);
      mv.set("value", mvList.getSelectedValue());
    }

    layout.add(mvLabel);
    layout.add(mvList);

    // Description
    String description = mv.get("description");
    if (description != null && description.length() > 0) {
      Label mvDescription = new Label(description);
      mvDescription.addStyleName("form-help");
      layout.add(mvDescription);
    }

    if (mv.get("error") != null && !mv.get("error").trim().equalsIgnoreCase("")) {
      Label errorLabel = new Label(mv.get("error"));
      errorLabel.addStyleName("form-label-error");
      layout.add(errorLabel);
      mvList.addStyleName("isWrong");
    }
    panel.add(layout);
  }

  private static void addDatePicker(FlowPanel panel, final FlowPanel layout, final MetadataValue mv,
    final boolean mandatory) {
    // Top label
    final DateTimeFormat dateTimeFormat = DateTimeFormat.getFormat("yyyy-MM-dd");
    Label mvLabel = new Label(getFieldLabel(mv));
    mvLabel.addStyleName("form-label");
    if (mandatory) {
      mvLabel.addStyleName("form-label-mandatory");
    }
    // Field
    final DateBox mvDate = new DateBox();
    mvDate.getDatePicker().setYearAndMonthDropdownVisible(true);
    mvDate.getDatePicker().setYearArrowsVisible(true);
    mvDate.addStyleName("form-textbox");
    mvDate.setFormat(new DateBox.DefaultFormat() {
      @Override
      public String format(DateBox dateBox, Date date) {
        if (date == null)
          return null;
        return dateTimeFormat.format(date);
      }
    });
    String value = mv.get("value");
    if (value != null && value.length() > 0) {
      try {
        Date date = dateTimeFormat.parse(value.trim());
        mvDate.setValue(date);
      } catch (IllegalArgumentException iae) {
        mvDate.getTextBox().setValue(value);
      }
    }
    mvDate.addValueChangeHandler(new ValueChangeHandler<Date>() {
      @Override
      public void onValueChange(ValueChangeEvent<Date> valueChangeEvent) {
        String newValue = dateTimeFormat.format(mvDate.getValue());
        mv.set("value", newValue);
        if (mandatory && (newValue != null && !newValue.trim().equalsIgnoreCase(""))) {
          mvDate.removeStyleName("isWrong");
        } else if (mandatory && (newValue == null || newValue.trim().equalsIgnoreCase(""))) {
          mvDate.addStyleName("isWrong");
        }
      }
    });
    mvDate.getTextBox().addValueChangeHandler(new ValueChangeHandler<String>() {

      @Override
      public void onValueChange(ValueChangeEvent<String> event) {
        String value = event.getValue();
        try {
          Date date = dateTimeFormat.parse(value.trim());
          mvDate.setValue(date);
          mv.set("value", value);
        } catch (IllegalArgumentException iae) {
          if (event.getValue() == null || event.getValue().trim().equalsIgnoreCase("")) {
            mv.set("value", null);
          }
          mvDate.getTextBox().setValue(value);
        }
      }
    });

    layout.add(mvLabel);
    layout.add(mvDate);

    // Description
    String description = mv.get("description");
    if (description != null && description.length() > 0) {
      Label mvDescription = new Label(description);
      mvDescription.addStyleName("form-help");
      layout.add(mvDescription);
    }
    if (mv.get("error") != null && !mv.get("error").trim().equalsIgnoreCase("")) {
      Label errorLabel = new Label(mv.get("error"));
      errorLabel.addStyleName("form-label-error");
      layout.add(errorLabel);
      mvDate.addStyleName("isWrong");
    }
    panel.add(layout);
  }

  private static void addSeparator(FlowPanel panel, final FlowPanel layout, final MetadataValue mv) {
    Label mvLabel = new Label(getFieldLabel(mv));
    layout.add(mvLabel);

    // Description
    String description = mv.get("description");
    if (description != null && description.length() > 0) {
      Label mvDescription = new Label(description);
      mvDescription.addStyleName("form-help");
      layout.add(mvDescription);
    }

    panel.add(layout);
  }

  public static List<String> validate(Set<MetadataValue> values, FlowPanel extra) {
    List<String> errors = new ArrayList<String>();
    if (values != null) {
      for (MetadataValue mv : values) {
        String value = mv.get("value");
        boolean mandatory = (mv.get("mandatory") != null && mv.get("mandatory").equalsIgnoreCase("true")) ? true
          : false;
        if (mandatory && (value == null || value.trim().equalsIgnoreCase(""))) {
          String labels = mv.get("l");
          errors.add(messages.isAMandatoryField(labels));
        }
      }
    }
    if (errors.size() > 0) {
      extra.clear();
      create(extra, values, true);
    }
    return errors;
  }
}
