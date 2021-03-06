/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.ingest.characterization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.roda.core.data.common.RodaConstants;
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
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.metadata.LinkingIdentifier;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.jobs.Report.PluginState;
import org.roda.core.data.v2.validation.ValidationException;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.plugins.AbstractPlugin;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.orchestrate.SimpleJobPluginInfo;
import org.roda.core.plugins.plugins.PluginHelper;
import org.roda.core.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiegfriedPlugin extends AbstractPlugin<AIP> {
  private static final Logger LOGGER = LoggerFactory.getLogger(SiegfriedPlugin.class);
  public static final String FILE_SUFFIX = ".json";

  private boolean createsPluginEvent = true;

  @Override
  public void init() throws PluginException {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  public static String getStaticName() {
    return "Format identification (Siegfried)";
  }

  @Override
  public String getName() {
    return getStaticName();
  }

  public static String getStaticDescription() {
    return "Identification of format and version of files included inside the information package using Siegfried (a signature-based file format identification tool that supports PRONOM and Mimetypes).";
  }

  @Override
  public String getDescription() {
    return getStaticDescription();
  }

  @Override
  public String getVersionImpl() {
    return SiegfriedPluginUtils.getVersion();
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    super.setParameterValues(parameters);

    // updates the flag responsible to allow plugin event creation
    if (parameters.containsKey(RodaConstants.PLUGIN_PARAMS_CREATES_PLUGIN_EVENT)) {
      createsPluginEvent = Boolean.parseBoolean(parameters.get(RodaConstants.PLUGIN_PARAMS_CREATES_PLUGIN_EVENT));
    }
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<AIP> list)
    throws PluginException {
    Report report = PluginHelper.initPluginReport(this);

    try {
      SimpleJobPluginInfo jobPluginInfo = PluginHelper.getInitialJobInformation(this, list.size());
      PluginHelper.updateJobInformation(this, jobPluginInfo);

      try {
        for (AIP aip : list) {
          Report reportItem = PluginHelper.initPluginReportItem(this, aip.getId(), AIP.class,
            AIPState.INGEST_PROCESSING);
          PluginHelper.updatePartialJobReport(this, model, index, reportItem, false);

          LOGGER.debug("Processing AIP {}", aip.getId());
          List<LinkingIdentifier> sources = new ArrayList<LinkingIdentifier>();
          try {

            for (Representation representation : aip.getRepresentations()) {
              LOGGER.debug("Processing representation {} of AIP {}", representation.getId(), aip.getId());
              SiegfriedPluginUtils.runSiegfriedOnRepresentation(this, index, model, aip, representation);
              sources.add(PluginHelper.getLinkingIdentifier(aip.getId(), representation.getId(),
                RodaConstants.PRESERVATION_LINKING_OBJECT_SOURCE));
              model.notifyRepresentationUpdated(representation);
            }

            jobPluginInfo.incrementObjectsProcessedWithSuccess();
            reportItem.setPluginState(PluginState.SUCCESS);
          } catch (PluginException | NotFoundException | GenericException | RequestNotValidException
            | AuthorizationDeniedException | AlreadyExistsException e) {
            LOGGER.error("Error running Siegfried " + aip.getId() + ": " + e.getMessage(), e);

            jobPluginInfo.incrementObjectsProcessedWithFailure();
            reportItem.setPluginState(PluginState.FAILURE)
              .setPluginDetails("Error running Siegfried " + aip.getId() + ": " + e.getMessage());
          }

          if (createsPluginEvent) {
            try {
              List<LinkingIdentifier> outcomes = null;
              boolean notify = true;
              PluginHelper.createPluginEvent(this, aip.getId(), model, index, sources, outcomes,
                reportItem.getPluginState(), "", notify);
            } catch (ValidationException | RequestNotValidException | NotFoundException | GenericException
              | AuthorizationDeniedException | AlreadyExistsException e) {
              LOGGER.error("Error creating event: " + e.getMessage(), e);
            }
          }

          report.addReport(reportItem);
          PluginHelper.updatePartialJobReport(this, model, index, reportItem, true);
        }
      } catch (ClassCastException e) {
        LOGGER.error("Trying to execute an AIP-only plugin with other objects");
        jobPluginInfo.incrementObjectsProcessedWithFailure(list.size());
      }

      jobPluginInfo.finalizeInfo();
      PluginHelper.updateJobInformation(this, jobPluginInfo);
    } catch (JobException e) {
      throw new PluginException("A job exception has occurred", e);
    }
    return report;
  }

  @Override
  public Plugin<AIP> cloneMe() {
    SiegfriedPlugin siegfriedPlugin = new SiegfriedPlugin();
    try {
      siegfriedPlugin.init();
    } catch (PluginException e) {
      LOGGER.error("Error doing " + SiegfriedPlugin.class.getName() + "init", e);
    }
    return siegfriedPlugin;
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
    return PreservationEventType.FORMAT_IDENTIFICATION;
  }

  @Override
  public String getPreservationEventDescription() {
    return "Identified the object's file formats and versions using Siegfried.";
  }

  @Override
  public String getPreservationEventSuccessMessage() {
    return "File formats were identified and recorded in PREMIS objects.";
  }

  @Override
  public String getPreservationEventFailureMessage() {
    return "Failed to identify file formats in the package.";
  }

  @Override
  public Report beforeAllExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public Report afterAllExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public List<String> getCategories() {
    return Arrays.asList(RodaConstants.PLUGIN_CATEGORY_CHARACTERIZATION);
  }

  @Override
  public List<Class<AIP>> getObjectClasses() {
    return Arrays.asList(AIP.class);
  }

}
