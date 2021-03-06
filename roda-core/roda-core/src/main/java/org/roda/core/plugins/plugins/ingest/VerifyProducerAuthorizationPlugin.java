/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.roda.core.RodaCoreFactory;
import org.roda.core.common.XMLUtility;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.common.RodaConstants.PreservationEventType;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.AIPState;
import org.roda.core.data.v2.ip.Permissions;
import org.roda.core.data.v2.ip.Permissions.PermissionType;
import org.roda.core.data.v2.ip.metadata.DescriptiveMetadata;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.jobs.Report.PluginState;
import org.roda.core.data.v2.user.RODAMember;
import org.roda.core.data.v2.validation.ValidationException;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.plugins.AbstractPlugin;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.plugins.PluginHelper;
import org.roda.core.storage.Binary;
import org.roda.core.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyProducerAuthorizationPlugin extends AbstractPlugin<AIP> {
  private static final Logger LOGGER = LoggerFactory.getLogger(VerifyProducerAuthorizationPlugin.class);

  private static final String CREATE_TOP_LEVEL_AIP_PERMISSION = "create.top.level.aip";
  private static final String NO_PERMISSION_TO_CREATE_UNDER_AIP = "The user doesn't have permission to create under AIP";
  private static final String PARENT_AIP_NOT_FOUND = "The parent of the AIP was not found";
  private static final String NO_AIP_PERMISSION = "The user doesn't have access to the parent AIP";
  private static final String AIP_PERMISSIONS_SUCCESSFULLY_VERIFIED = "The user permissions are valid and the AIP permissions were updated";
  private static final String NO_CREATE_TOP_LEVEL_AIP_PERMISSION = "The user doesn't have CREATE_TOP_LEVEL_AIP_PERMISSION permission";

  private boolean hasFreeAccess = false;

  @Override
  public void init() throws PluginException {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  public static String getStaticName() {
    return "Verify producer authorization";
  }

  @Override
  public String getName() {
    return getStaticName();
  }

  public static String getStaticDescription() {
    return "Checks if the producer has enough permissions to place the AIP under the desired node in the classification scheme";
  }

  @Override
  public String getDescription() {
    return getStaticDescription();
  }

  @Override
  public String getVersionImpl() {
    return "1.0";
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<AIP> list)
    throws PluginException {
    Report report = PluginHelper.initPluginReport(this);
    Job currentJob = getJob(index);

    for (AIP aip : list) {
      LOGGER.debug("Checking producer authorization for AIPingest.submitP {}", aip.getId());

      Report reportItem = PluginHelper.initPluginReportItem(this, aip.getId(), AIP.class, AIPState.INGEST_PROCESSING);
      PluginHelper.updatePartialJobReport(this, model, index, reportItem, false);

      reportItem.setPluginState(PluginState.SUCCESS)
        .setPluginDetails(String.format("Done with checking producer authorization for AIP %s", aip.getId()));

      if (currentJob != null) {
        processAIPPermissions(index, model, currentJob, aip, reportItem);
      } else {
        reportItem.setPluginState(PluginState.FAILURE).setPluginDetails("Unable to determine Job");
      }

      try {
        boolean notify = true;
        PluginHelper.createPluginEvent(this, aip.getId(), model, index, reportItem.getPluginState(),
          reportItem.getPluginDetails(), notify);
      } catch (RequestNotValidException | NotFoundException | GenericException | AuthorizationDeniedException
        | ValidationException | AlreadyExistsException e) {
        throw new PluginException("Error while creating the event", e);
      }

      LOGGER.debug("Done with checking producer authorization for AIP {}", aip.getId());

      report.addReport(reportItem);
      PluginHelper.updatePartialJobReport(this, model, index, reportItem, true);

    }

    return report;
  }

  private void processAIPPermissions(IndexService index, ModelService model, Job currentJob, AIP aip,
    Report reportItem) {
    try {
      AIP parentAIP = null;
      String jobCreatorUsername = currentJob.getUsername();
      if (aip.getParentId() != null) {
        try {
          parentAIP = model.retrieveAIP(aip.getParentId());
          Set<PermissionType> userPermissions = parentAIP.getPermissions().getUserPermissions(jobCreatorUsername);
          LOGGER.trace("Checking if user '{}' has permissions on parent AIP '{}' ({})", jobCreatorUsername,
            parentAIP.getId(), userPermissions);
          if (userPermissions.contains(PermissionType.CREATE)) {
            LOGGER.debug("User '{}' has CREATE permission on parent AIP.", jobCreatorUsername);
          } else {
            LOGGER.debug("User '{}' doesn't have CREATE permission on parent... Error...", jobCreatorUsername);
            reportItem.setPluginState(PluginState.FAILURE).setPluginDetails(NO_PERMISSION_TO_CREATE_UNDER_AIP);
          }
        } catch (NotFoundException nfe) {
          reportItem.setPluginState(PluginState.FAILURE).setPluginDetails(PARENT_AIP_NOT_FOUND);
        } catch (AuthorizationDeniedException e) {
          LOGGER.debug("User doesn't have access to parent... Error...");
          reportItem.setPluginState(PluginState.FAILURE).setPluginDetails(NO_AIP_PERMISSION);
        }
      } else {
        RODAMember member = index.retrieve(RODAMember.class, jobCreatorUsername);
        if (member.getAllRoles().contains(CREATE_TOP_LEVEL_AIP_PERMISSION) || member.getName().equals("admin")) {
          LOGGER
            .debug("User have CREATE_TOP_LEVEL_AIP_PERMISSION permission.");
        } else {
          reportItem.setPluginState(PluginState.FAILURE).setPluginDetails(NO_CREATE_TOP_LEVEL_AIP_PERMISSION);
          LOGGER.debug("User doesn't have CREATE_TOP_LEVEL_AIP_PERMISSION permission...");
        }
      }
    } catch (GenericException | RequestNotValidException | NotFoundException e) {
      reportItem.setPluginState(PluginState.FAILURE).setPluginDetails(e.getMessage());
    }
  }

  private Job getJob(IndexService index) {
    Job currentJob = null;
    try {
      currentJob = PluginHelper.getJob(this, index);
    } catch (NotFoundException | GenericException e) {
      LOGGER.error("Error retrieving Job from index", e);
    }
    return currentJob;
  }

  @Override
  public Plugin<AIP> cloneMe() {
    return new VerifyProducerAuthorizationPlugin();
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
    return PreservationEventType.AUTHORIZATION_CHECK;
  }

  @Override
  public String getPreservationEventDescription() {
    String description = "Producer permissions have been checked to ensure that he has sufficient authorization to store the AIP under the desired node of the classification scheme.";

    if (hasFreeAccess) {
      description += " It was given READ permission to the users group as indicated on the descriptive metadata";
    }

    return description;
  }

  @Override
  public String getPreservationEventSuccessMessage() {
    return "The producer has enough permissions to deposit the AIP under the designated node of the classification scheme";
  }

  @Override
  public String getPreservationEventFailureMessage() {
    return "The producer does not have enough permissions to deposit the AIP under the designated node of the classification scheme";
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
    return Arrays.asList(RodaConstants.PLUGIN_CATEGORY_NOT_LISTABLE);
  }

  @Override
  public List<Class<AIP>> getObjectClasses() {
    return Arrays.asList(AIP.class);
  }
}
