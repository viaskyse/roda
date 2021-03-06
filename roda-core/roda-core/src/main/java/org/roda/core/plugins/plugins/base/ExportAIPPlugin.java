/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.base;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.roda.core.common.ConsumesOutputStream;
import org.roda.core.common.DownloadUtils;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.common.RodaConstants.ExportType;
import org.roda.core.data.common.RodaConstants.PreservationEventType;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.InvalidParameterException;
import org.roda.core.data.exceptions.JobException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.AIPState;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.data.v2.jobs.PluginParameter;
import org.roda.core.data.v2.jobs.PluginParameter.PluginParameterType;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.jobs.Report.PluginState;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.model.utils.ModelUtils;
import org.roda.core.plugins.AbstractPlugin;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.orchestrate.SimpleJobPluginInfo;
import org.roda.core.plugins.plugins.PluginHelper;
import org.roda.core.storage.DefaultStoragePath;
import org.roda.core.storage.Directory;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.fs.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportAIPPlugin extends AbstractPlugin<AIP> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExportAIPPlugin.class);

  public static final String PLUGIN_PARAM_EXPORT_FOLDER_PARAMETER = "outputFolder";
  public static final String PLUGIN_PARAM_EXPORT_TYPE = "exportType";
  public static final String PLUGIN_PARAM_EXPORT_REMOVE_IF_ALREADY_EXISTS = "removeIfAlreadyExists";

  private String outputFolder;
  private ExportType exportType;
  private boolean removeIfAlreadyExists;

  private static Map<String, PluginParameter> pluginParameters = new HashMap<>();
  static {
    pluginParameters.put(PLUGIN_PARAM_EXPORT_FOLDER_PARAMETER, new PluginParameter(PLUGIN_PARAM_EXPORT_FOLDER_PARAMETER,
      "Output folder", PluginParameterType.STRING, "/tmp", true, false, "Folder where the exported AIP will be sent."));
    pluginParameters.put(PLUGIN_PARAM_EXPORT_TYPE, new PluginParameter(PLUGIN_PARAM_EXPORT_TYPE, "Type of export",
      PluginParameterType.STRING, "FOLDER", true, false, "Type of exportation (MULTI_ZIP, FOLDER)"));
    pluginParameters.put(PLUGIN_PARAM_EXPORT_REMOVE_IF_ALREADY_EXISTS,
      new PluginParameter(PLUGIN_PARAM_EXPORT_REMOVE_IF_ALREADY_EXISTS, "Remove if already exists",
        PluginParameterType.BOOLEAN, "true", true, false, "Remove if already exists"));
  }

  @Override
  public void init() throws PluginException {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "Export AIP(s)";
  }

  @Override
  public String getDescription() {
    return "Exports selected AIP(s) to a ZIP file on the server file system.";
  }

  @Override
  public String getVersionImpl() {
    return "1.0";
  }

  @Override
  public List<PluginParameter> getParameters() {
    ArrayList<PluginParameter> parameters = new ArrayList<PluginParameter>();
    parameters.add(pluginParameters.get(PLUGIN_PARAM_EXPORT_FOLDER_PARAMETER));
    parameters.add(pluginParameters.get(PLUGIN_PARAM_EXPORT_TYPE));
    parameters.add(pluginParameters.get(PLUGIN_PARAM_EXPORT_REMOVE_IF_ALREADY_EXISTS));
    return parameters;
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    super.setParameterValues(parameters);
    if (parameters.containsKey(PLUGIN_PARAM_EXPORT_FOLDER_PARAMETER)) {
      outputFolder = parameters.get(PLUGIN_PARAM_EXPORT_FOLDER_PARAMETER);
    }
    if (parameters.containsKey(PLUGIN_PARAM_EXPORT_REMOVE_IF_ALREADY_EXISTS)) {
      removeIfAlreadyExists = Boolean
        .parseBoolean(getParameterValues().get(PLUGIN_PARAM_EXPORT_REMOVE_IF_ALREADY_EXISTS));
    }
    if (parameters.containsKey(PLUGIN_PARAM_EXPORT_TYPE)) {
      try {
        exportType = ExportType.valueOf(parameters.get(PLUGIN_PARAM_EXPORT_TYPE));
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        exportType = ExportType.FOLDER;
      }
    }
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<AIP> aips)
    throws PluginException {
    Report report = PluginHelper.initPluginReport(this);
    try {
      SimpleJobPluginInfo jobPluginInfo = PluginHelper.getInitialJobInformation(this, aips.size());
      PluginHelper.updateJobInformation(this, jobPluginInfo);

      Path outputPath = Paths.get(outputFolder);
      String error = null;
      try {
        if (!Files.exists(outputPath)) {
          Files.createDirectories(outputPath);
        }
        if (!Files.isWritable(outputPath)) {
          error = "No permissions to write to " + outputPath.toString();
        }
      } catch (IOException e) {
        LOGGER.error("Error creating base folder: " + e.getMessage());
        error = e.getMessage();
      }
      LOGGER.error("Error: " + error);
      if (error == null && exportType == ExportType.MULTI_ZIP) {
        report = exportMultiZip(aips, outputPath, report, model, index, storage, jobPluginInfo);
      } else if (error == null && exportType == ExportType.FOLDER) {
        report = exportFolders(aips, outputPath, storage, model, index, report, jobPluginInfo);
      } else if (error != null) {
        jobPluginInfo.incrementObjectsProcessedWithFailure(aips.size());
        report.setCompletionPercentage(100);
        report.setPluginState(PluginState.FAILURE);
        report.setPluginDetails("Error exporting AIPs: " + error);
      }
      jobPluginInfo.finalizeInfo();
      PluginHelper.updateJobInformation(this, jobPluginInfo);
    } catch (JobException e) {
      LOGGER.error("Could not update Job information");
    }
    return report;
  }

  private Report exportFolders(List<AIP> aips, Path outputPath, StorageService storage, ModelService model,
    IndexService index, Report report, SimpleJobPluginInfo jobPluginInfo) {
    try {
      FileStorageService localStorage = new FileStorageService(Paths.get(outputFolder));
      for (AIP aip : aips) {
        LOGGER.debug("Exporting AIP {} to folder", aip.getId());
        String error = null;
        StoragePath aipPath = ModelUtils.getAIPStoragePath(aip.getId());
        try {
          localStorage.copy(storage, aipPath, DefaultStoragePath.parse(aip.getId()));
        } catch (AlreadyExistsException e) {
          if (removeIfAlreadyExists) {
            try {
              localStorage.deleteResource(DefaultStoragePath.parse(aip.getId()));
              localStorage.copy(storage, aipPath, DefaultStoragePath.parse(aip.getId()));
            } catch (AlreadyExistsException e2) {
              error = "Error removing/creating folder " + aipPath.toString();
            }
          } else {
            error = "Folder " + aipPath.toString() + " already exists.";
          }
        }

        Report reportItem = PluginHelper.initPluginReportItem(this, aip.getId(), AIP.class, AIPState.ACTIVE);
        if (error != null) {
          reportItem.setPluginState(PluginState.FAILURE)
            .setPluginDetails("Export AIP did not end successfully: " + error);
          jobPluginInfo.incrementObjectsProcessedWithFailure();
        } else {
          reportItem.setPluginState(PluginState.SUCCESS).setPluginDetails("Export AIP ended successfully");
          jobPluginInfo.incrementObjectsProcessedWithSuccess();
        }
        report.addReport(reportItem);
        PluginHelper.updatePartialJobReport(this, model, index, reportItem, true);

      }
    } catch (GenericException | RequestNotValidException | AuthorizationDeniedException | NotFoundException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return report;
  }

  private Report exportMultiZip(List<AIP> aips, Path outputPath, Report report, ModelService model, IndexService index,
    StorageService storage, SimpleJobPluginInfo jobPluginInfo) {
    for (AIP aip : aips) {
      LOGGER.debug("Exporting AIP {} to ZIP", aip.getId());
      OutputStream os = null;
      String error = null;
      try {
        Path zip = outputPath.resolve(aip.getId() + ".zip");
        if (Files.exists(zip) && removeIfAlreadyExists) {
          Files.delete(zip);
        } else if (Files.exists(zip) && !removeIfAlreadyExists) {
          error = "File " + zip.toString() + " already exists";
        }
        if (error == null) {
          os = Files.newOutputStream(zip, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

          Directory directory = storage.getDirectory(ModelUtils.getAIPStoragePath(aip.getId()));
          ConsumesOutputStream cos = DownloadUtils.download(storage, directory);
          cos.consumeOutputStream(os);
        }
      } catch (Exception e) {
        LOGGER.error("Error exporting AIP " + aip.getId() + ": " + e.getMessage());
        error = e.getMessage();
      } finally {
        if (os != null) {
          IOUtils.closeQuietly(os);
        }
      }

      Report reportItem = PluginHelper.initPluginReportItem(this, aip.getId(), AIP.class, AIPState.ACTIVE);
      if (error != null) {
        reportItem.setPluginState(PluginState.FAILURE)
          .setPluginDetails("Export AIP did not end successfully: " + error);
        jobPluginInfo.incrementObjectsProcessedWithFailure();
      } else {
        reportItem.setPluginState(PluginState.SUCCESS).setPluginDetails("Export AIP ended successfully");
        jobPluginInfo.incrementObjectsProcessedWithSuccess();
      }
      report.addReport(reportItem);
      PluginHelper.updatePartialJobReport(this, model, index, reportItem, true);
    }
    return report;
  }

  @Override
  public Report beforeAllExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    return new Report();
  }

  @Override
  public Report afterAllExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {
    return new Report();
  }

  @Override
  public Plugin<AIP> cloneMe() {
    return new ExportAIPPlugin();
  }

  @Override
  public PluginType getType() {
    return PluginType.AIP_TO_AIP;
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }

  @Override
  public PreservationEventType getPreservationEventType() {
    return PreservationEventType.MIGRATION;
  }

  @Override
  public String getPreservationEventDescription() {
    return "Exports AIPS to a local folder";
  }

  @Override
  public String getPreservationEventSuccessMessage() {
    return "The AIPs were successfully exported";
  }

  @Override
  public String getPreservationEventFailureMessage() {
    return "The AIPs were not exported";
  }

  @Override
  public List<String> getCategories() {
    return Arrays.asList(RodaConstants.PLUGIN_CATEGORY_MANAGEMENT);
  }

  @Override
  public List<Class<AIP>> getObjectClasses() {
    return Arrays.asList(AIP.class);
  }
}
