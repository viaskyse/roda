/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.model.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.roda.core.RodaCoreFactory;
import org.roda.core.common.iterables.CloseableIterable;
import org.roda.core.common.tools.ZipEntryInfo;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.IsRODAObject;
import org.roda.core.data.v2.agents.Agent;
import org.roda.core.data.v2.common.OptionalWithCause;
import org.roda.core.data.v2.formats.Format;
import org.roda.core.data.v2.index.SelectedItems;
import org.roda.core.data.v2.index.SelectedItemsFilter;
import org.roda.core.data.v2.index.SelectedItemsList;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.File;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.ip.IndexedFile;
import org.roda.core.data.v2.ip.IndexedRepresentation;
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.data.v2.ip.metadata.DescriptiveMetadata;
import org.roda.core.data.v2.ip.metadata.OtherMetadata;
import org.roda.core.data.v2.ip.metadata.PreservationMetadata;
import org.roda.core.data.v2.ip.metadata.PreservationMetadata.PreservationMetadataType;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.log.LogEntry;
import org.roda.core.data.v2.notifications.Notification;
import org.roda.core.data.v2.risks.IndexedRisk;
import org.roda.core.data.v2.risks.Risk;
import org.roda.core.data.v2.risks.RiskIncidence;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.plugins.orchestrate.SimpleJobPluginInfo;
import org.roda.core.storage.Binary;
import org.roda.core.storage.DefaultStoragePath;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.fs.FSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Model related utility class
 * 
 * @author Hélder Silva <hsilva@keep.pt>
 * @author Luis Faria <lfaria@keep.pt>
 */
public final class ModelUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModelUtils.class);

  /**
   * Private empty constructor
   */
  private ModelUtils() {

  }

  private static List<String> build(List<String> basePath, String... path) {
    List<String> ret = new ArrayList<>(basePath);
    for (String pathItem : path) {
      ret.add(pathItem);
    }
    return ret;
  }

  public static StoragePath getAIPcontainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_AIP);
  }

  private static List<String> getAIPPath(String aipId) {
    return Arrays.asList(RodaConstants.STORAGE_CONTAINER_AIP, aipId);
  }

  public static StoragePath getAIPStoragePath(String aipId) throws RequestNotValidException {
    return DefaultStoragePath.parse(getAIPPath(aipId));
  }

  private static List<String> getAIPMetadataPath(String aipId) {
    return build(getAIPPath(aipId), RodaConstants.STORAGE_DIRECTORY_METADATA);
  }

  private static List<String> getAIPPreservationMetadataPath(String aipId) {
    return build(getAIPMetadataPath(aipId), RodaConstants.STORAGE_DIRECTORY_PRESERVATION);
  }

  public static StoragePath getAIPPreservationMetadataStoragePath(String aipId) throws RequestNotValidException {
    return DefaultStoragePath.parse(getAIPPreservationMetadataPath(aipId));
  }

  private static List<String> getAIPOtherMetadataPath(String aipId, String type) {
    return build(getAIPMetadataPath(aipId), RodaConstants.STORAGE_DIRECTORY_OTHER, type);
  }

  public static StoragePath getAIPOtherMetadataStoragePath(String aipId, String type) throws RequestNotValidException {
    return DefaultStoragePath.parse(getAIPOtherMetadataPath(aipId, type));
  }

  private static List<String> getSubmissionPath(String aipId) {
    return build(getAIPPath(aipId), RodaConstants.STORAGE_DIRECTORY_SUBMISSION);
  }

  public static StoragePath getSubmissionStoragePath(String aipId) throws RequestNotValidException {
    return DefaultStoragePath.parse(getSubmissionPath(aipId));
  }

  private static List<String> getRepresentationPath(String aipId, String representationId) {
    if (representationId == null) {
      return getAIPPath(aipId);
    } else {
      return build(getAIPPath(aipId), RodaConstants.STORAGE_DIRECTORY_REPRESENTATIONS, representationId);
    }
  }

  public static StoragePath getRepresentationsContainerPath(String aipId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_AIP, aipId,
      RodaConstants.STORAGE_DIRECTORY_REPRESENTATIONS);
  }

  public static StoragePath getRepresentationStoragePath(String aipId, String representationId)
    throws RequestNotValidException {
    return DefaultStoragePath.parse(getRepresentationPath(aipId, representationId));
  }

  private static List<String> getRepresentationMetadataPath(String aipId, String representationId) {
    return build(getRepresentationPath(aipId, representationId), RodaConstants.STORAGE_DIRECTORY_METADATA);
  }

  public static StoragePath getRepresentationMetadataStoragePath(String aipId, String representationId)
    throws RequestNotValidException {
    return DefaultStoragePath.parse(getRepresentationMetadataPath(aipId, representationId));
  }

  private static List<String> getRepresentationPreservationMetadataPath(String aipId, String representationId) {
    return build(getRepresentationMetadataPath(aipId, representationId), RodaConstants.STORAGE_DIRECTORY_PRESERVATION);
  }

  public static StoragePath getRepresentationPreservationMetadataStoragePath(String aipId, String representationId)
    throws RequestNotValidException {
    return DefaultStoragePath.parse(getRepresentationPreservationMetadataPath(aipId, representationId));
  }

  private static List<String> getRepresentationOtherMetadataFolderPath(String aipId, String representationId) {
    return build(getRepresentationMetadataPath(aipId, representationId), RodaConstants.STORAGE_DIRECTORY_OTHER);
  }

  private static List<String> getRepresentationOtherMetadataPath(String aipId, String representationId, String type) {
    return build(getRepresentationMetadataPath(aipId, representationId), RodaConstants.STORAGE_DIRECTORY_OTHER, type);
  }

  public static StoragePath getRepresentationOtherMetadataFolderStoragePath(String aipId, String representationId)
    throws RequestNotValidException {
    return DefaultStoragePath.parse(getRepresentationOtherMetadataFolderPath(aipId, representationId));
  }

  public static StoragePath getRepresentationOtherMetadataStoragePath(String aipId, String representationId,
    String type) throws RequestNotValidException {
    return DefaultStoragePath.parse(getRepresentationOtherMetadataPath(aipId, representationId, type));
  }

  private static List<String> getRepresentationDataPath(String aipId, String representationId) {
    return build(getRepresentationPath(aipId, representationId), RodaConstants.STORAGE_DIRECTORY_DATA);
  }

  public static StoragePath getRepresentationDataStoragePath(String aipId, String representationId)
    throws RequestNotValidException {
    return DefaultStoragePath.parse(getRepresentationDataPath(aipId, representationId));
  }

  public static StoragePath getDescriptiveMetadataStoragePath(String aipId, String descriptiveMetadataBinaryId)
    throws RequestNotValidException {
    return getDescriptiveMetadataStoragePath(aipId, null, descriptiveMetadataBinaryId);
  }

  public static StoragePath getDescriptiveMetadataStoragePath(String aipId, String representationId,
    String descriptiveMetadataBinaryId) throws RequestNotValidException {
    List<String> path = build(getRepresentationPath(aipId, representationId), RodaConstants.STORAGE_DIRECTORY_METADATA,
      RodaConstants.STORAGE_DIRECTORY_DESCRIPTIVE, descriptiveMetadataBinaryId);
    return DefaultStoragePath.parse(path);
  }

  public static StoragePath getDescriptiveMetadataStoragePath(DescriptiveMetadata descriptiveMetadata)
    throws RequestNotValidException {
    return getDescriptiveMetadataStoragePath(descriptiveMetadata.getAipId(), descriptiveMetadata.getRepresentationId(),
      descriptiveMetadata.getId());
  }

  private static List<String> getDocumentationPath(String aipId) {
    return build(getAIPPath(aipId), RodaConstants.STORAGE_DIRECTORY_DOCUMENTATION);
  }

  private static List<String> getDocumentationPath(String aipId, String representationId) {
    return build(getRepresentationPath(aipId, representationId), RodaConstants.STORAGE_DIRECTORY_DOCUMENTATION);
  }

  public static StoragePath getDocumentationStoragePath(String aipId) throws RequestNotValidException {
    return DefaultStoragePath.parse(getDocumentationPath(aipId));
  }

  public static StoragePath getDocumentationStoragePath(String aipId, String representationId)
    throws RequestNotValidException {
    return DefaultStoragePath.parse(getDocumentationPath(aipId, representationId));
  }

  public static StoragePath getDocumentationStoragePath(String aipId, String representationId,
    List<String> directoryPath, String fileId) throws RequestNotValidException {
    List<String> path = getDocumentationPath(aipId, representationId);
    if (directoryPath != null) {
      path.addAll(directoryPath);
    }
    path.add(fileId);
    return DefaultStoragePath.parse(path);
  }

  private static List<String> getSchemasPath(String aipId) {
    return build(getAIPPath(aipId), RodaConstants.STORAGE_DIRECTORY_SCHEMAS);
  }

  private static List<String> getSchemasPath(String aipId, String representationId) {
    return build(getRepresentationPath(aipId, representationId), RodaConstants.STORAGE_DIRECTORY_SCHEMAS);
  }

  public static StoragePath getSchemasStoragePath(String aipId) throws RequestNotValidException {
    return DefaultStoragePath.parse(getSchemasPath(aipId));
  }

  public static StoragePath getSchemasStoragePath(String aipId, String representationId)
    throws RequestNotValidException {
    return DefaultStoragePath.parse(getSchemasPath(aipId, representationId));
  }

  public static StoragePath getSchemaStoragePath(String aipId, String representationId, List<String> directoryPath,
    String fileId) throws RequestNotValidException {
    List<String> path = getSchemasPath(aipId, representationId);
    if (directoryPath != null) {
      path.addAll(directoryPath);
    }
    path.add(fileId);
    return DefaultStoragePath.parse(path);
  }

  public static StoragePath getFileStoragePath(String aipId, String representationId, List<String> directoryPath,
    String fileId) throws RequestNotValidException {
    List<String> path = getRepresentationDataPath(aipId, representationId);
    if (directoryPath != null) {
      path.addAll(directoryPath);
    }
    path.add(fileId);
    return DefaultStoragePath.parse(path);
  }

  public static StoragePath getFileStoragePath(File f) throws RequestNotValidException {
    return getFileStoragePath(f.getAipId(), f.getRepresentationId(), f.getPath(), f.getId());
  }

  public static String extractAipId(StoragePath path) {
    // AIP/[aipId]/...

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();

    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && !directoryPath.isEmpty()) {
      return directoryPath.get(0);
    } else {
      return null;
    }
  }

  public static String extractRepresentationId(StoragePath path) {
    // AIP/[aipId]/representations/[representationId]/...

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();

    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && directoryPath.size() > 1
      && directoryPath.get(1).equals(RodaConstants.STORAGE_DIRECTORY_REPRESENTATIONS)) {
      String representationId;
      if (directoryPath.size() > 2) {
        representationId = directoryPath.get(2);
      } else {
        representationId = path.getName();
      }

      return representationId;
    } else {
      return null;
    }
  }

  public static List<String> extractFilePathFromRepresentationData(StoragePath path) {
    // AIP/[aipId]/representations/[representationId]/data/.../file.bin

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();
    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && directoryPath.size() > 3
      && directoryPath.get(1).equals(RodaConstants.STORAGE_DIRECTORY_REPRESENTATIONS)
      && directoryPath.get(3).equals(RodaConstants.STORAGE_DIRECTORY_DATA)) {
      return directoryPath.subList(4, directoryPath.size());
    } else {
      return new ArrayList<>();
    }
  }

  public static List<String> extractFilePathFromAipPreservationMetadata(StoragePath path) {
    // AIP/[aipId]/metadata/preservation/.../file.bin

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();
    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && directoryPath.size() > 2
      && directoryPath.get(1).equals(RodaConstants.STORAGE_DIRECTORY_METADATA)
      && directoryPath.get(2).equals(RodaConstants.STORAGE_DIRECTORY_PRESERVATION)) {
      return directoryPath.subList(3, directoryPath.size());
    } else {
      return new ArrayList<>();
    }
  }

  public static List<String> extractFilePathFromRepresentationPreservationMetadata(StoragePath path) {
    // AIP/[aipId]/representations/[representationId]/metadata/preservation/.../file.bin

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();
    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && directoryPath.size() > 4
      && directoryPath.get(1).equals(RodaConstants.STORAGE_DIRECTORY_REPRESENTATIONS)
      && directoryPath.get(3).equals(RodaConstants.STORAGE_DIRECTORY_METADATA)
      && directoryPath.get(4).equals(RodaConstants.STORAGE_DIRECTORY_PRESERVATION)) {
      return directoryPath.subList(5, directoryPath.size());
    } else {
      return new ArrayList<>();
    }
  }

  public static String extractTypeFromAipOtherMetadata(StoragePath path) {
    // AIP/[aipId]/metadata/other/[type]/...

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();
    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && directoryPath.size() > 2
      && directoryPath.get(1).equals(RodaConstants.STORAGE_DIRECTORY_METADATA)
      && directoryPath.get(2).equals(RodaConstants.STORAGE_DIRECTORY_OTHER)) {
      String type;
      if (directoryPath.size() > 3) {
        type = directoryPath.get(3);
      } else {
        type = path.getName();
      }

      return type;
    } else {
      return null;
    }
  }

  public static List<String> extractFilePathFromAipOtherMetadata(StoragePath path) {
    // AIP/[aipId]/metadata/other/[type]/.../file.bin

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();
    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && directoryPath.size() > 3
      && directoryPath.get(1).equals(RodaConstants.STORAGE_DIRECTORY_METADATA)
      && directoryPath.get(2).equals(RodaConstants.STORAGE_DIRECTORY_OTHER)) {
      return directoryPath.subList(4, directoryPath.size());
    } else {
      return null;
    }
  }

  public static String extractTypeFromRepresentationOtherMetadata(StoragePath path) {
    // AIP/[aipId]/representations/[representationId]/metadata/other/[type]/...

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();
    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && directoryPath.size() > 4
      && directoryPath.get(1).equals(RodaConstants.STORAGE_DIRECTORY_REPRESENTATIONS)
      && directoryPath.get(3).equals(RodaConstants.STORAGE_DIRECTORY_METADATA)
      && directoryPath.get(4).equals(RodaConstants.STORAGE_DIRECTORY_OTHER)) {
      String type;
      if (directoryPath.size() > 5) {
        type = directoryPath.get(5);
      } else {
        type = path.getName();
      }

      return type;
    } else {
      return null;
    }
  }

  public static List<String> extractFilePathFromRepresentationOtherMetadata(StoragePath path) {
    // AIP/[aipId]/representations/[representationId]/metadata/other/[type]/.../file.bin

    String container = path.getContainerName();
    List<String> directoryPath = path.getDirectoryPath();
    if (container.equals(RodaConstants.STORAGE_CONTAINER_AIP) && directoryPath.size() > 5
      && directoryPath.get(1).equals(RodaConstants.STORAGE_DIRECTORY_REPRESENTATIONS)
      && directoryPath.get(3).equals(RodaConstants.STORAGE_DIRECTORY_METADATA)
      && directoryPath.get(4).equals(RodaConstants.STORAGE_DIRECTORY_OTHER)) {
      return directoryPath.subList(6, directoryPath.size());
    } else {
      return new ArrayList<>();
    }
  }

  public static StoragePath getPreservationMetadataStoragePath(PreservationMetadata pm)
    throws RequestNotValidException {
    return getPreservationMetadataStoragePath(pm.getId(), pm.getType(), pm.getAipId(), pm.getRepresentationId(),
      pm.getFileDirectoryPath(), pm.getFileId());
  }

  public static StoragePath getPreservationMetadataStoragePath(String id, PreservationMetadataType type)
    throws RequestNotValidException {
    return getPreservationMetadataStoragePath(id, type, null, null, null, null);
  }

  public static StoragePath getPreservationMetadataStoragePath(String id, PreservationMetadataType type, String aipId)
    throws RequestNotValidException {
    return getPreservationMetadataStoragePath(id, type, aipId, null, null, null);
  }

  public static StoragePath getPreservationMetadataStoragePath(String id, PreservationMetadataType type, String aipId,
    String representationId) throws RequestNotValidException {
    return getPreservationMetadataStoragePath(id, type, aipId, representationId, null, null);
  }

  public static StoragePath getPreservationAgentStoragePath() throws RequestNotValidException {
    List<String> path = Arrays.asList(RodaConstants.STORAGE_CONTAINER_PRESERVATION,
      RodaConstants.STORAGE_DIRECTORY_AGENTS);
    return DefaultStoragePath.parse(path);
  }

  public static StoragePath getPreservationMetadataStoragePath(String id, PreservationMetadataType type, String aipId,
    String representationId, List<String> fileDirectoryPath, String fileId) throws RequestNotValidException {
    List<String> path = null;
    if (type != null) {
      if (type.equals(PreservationMetadataType.AGENT)) {
        path = Arrays.asList(RodaConstants.STORAGE_CONTAINER_PRESERVATION, RodaConstants.STORAGE_DIRECTORY_AGENTS,
          id + RodaConstants.PREMIS_SUFFIX);
      } else if (type.equals(PreservationMetadataType.REPRESENTATION)) {
        if (aipId != null && representationId != null) {
          String pFileId = id + RodaConstants.PREMIS_SUFFIX;
          path = build(getRepresentationPreservationMetadataPath(aipId, representationId), pFileId);
        } else {
          throw new RequestNotValidException("Cannot request a representation object with null AIP or Representation. "
            + "AIP id = " + aipId + " and Representation id = " + representationId);
        }
      } else if (type.equals(PreservationMetadataType.EVENT)) {
        if (aipId != null) {
          if (representationId != null) {
            String pFileId = id + RodaConstants.PREMIS_SUFFIX;
            path = build(getRepresentationPreservationMetadataPath(aipId, representationId), pFileId);
          } else {
            String pFileId = id + RodaConstants.PREMIS_SUFFIX;
            path = build(getAIPPreservationMetadataPath(aipId), pFileId);
          }

        } else {
          throw new RequestNotValidException("Requested an event preservation object with null AIP id");
        }
      } else if (type.equals(PreservationMetadataType.FILE)) {
        path = getRepresentationMetadataPath(aipId, representationId);
        path.add(RodaConstants.STORAGE_DIRECTORY_PRESERVATION);
        if (fileDirectoryPath != null) {
          path.addAll(fileDirectoryPath);
        }
        path.add(id + RodaConstants.PREMIS_SUFFIX);
      } else if (type.equals(PreservationMetadataType.OTHER)) {
        path = getRepresentationMetadataPath(aipId, representationId);
        path.add(RodaConstants.STORAGE_DIRECTORY_PRESERVATION);
        path.add(RodaConstants.STORAGE_DIRECTORY_OTHER_TECH_METADATA);
        if (fileDirectoryPath != null) {
          path.addAll(fileDirectoryPath);
        }
        path.add(fileId + RodaConstants.OTHER_TECH_METADATA_FILE_SUFFIX);
      } else {
        throw new RequestNotValidException("Unsupported preservation metadata type: " + type);
      }
    } else {
      throw new RequestNotValidException("Preservation metadata type is null");
    }
    return DefaultStoragePath.parse(path);
  }

  public static StoragePath getLogContainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_ACTIONLOG);
  }

  public static StoragePath getLogStoragePath(String logFile) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_ACTIONLOG, logFile);
  }

  public static StoragePath getJobContainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_JOB);
  }

  public static StoragePath getJobStoragePath(String jobId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_JOB, jobId + RodaConstants.JOB_FILE_EXTENSION);
  }

  public static StoragePath getJobReportContainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_JOB_REPORT);
  }

  public static StoragePath getJobReportsStoragePath(String jobId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_JOB_REPORT, jobId);
  }

  public static StoragePath getJobReportStoragePath(String jobId, String jobReportId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_JOB_REPORT, jobId,
      jobReportId + RodaConstants.JOB_REPORT_FILE_EXTENSION);
  }

  public static StoragePath getRiskContainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_RISK);
  }

  public static StoragePath getRiskStoragePath(String riskId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_RISK, riskId + RodaConstants.RISK_FILE_EXTENSION);
  }

  public static StoragePath getRiskIncidenceContainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_RISK_INCIDENCE);
  }

  public static StoragePath getRiskIncidenceStoragePath(String riskIncidenceId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_RISK_INCIDENCE,
      riskIncidenceId + RodaConstants.RISK_INCIDENCE_FILE_EXTENSION);
  }

  public static StoragePath getAgentContainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_AGENT);
  }

  public static StoragePath getAgentStoragePath(String agentId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_AGENT,
      agentId + RodaConstants.AGENT_FILE_EXTENSION);
  }

  public static StoragePath getFormatContainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_FORMAT);
  }

  public static StoragePath getFormatStoragePath(String formatId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_FORMAT,
      formatId + RodaConstants.FORMAT_FILE_EXTENSION);
  }

  public static StoragePath getNotificationContainerPath() throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_NOTIFICATION);
  }

  public static StoragePath getNotificationStoragePath(String notificationId) throws RequestNotValidException {
    return DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_NOTIFICATION,
      notificationId + RodaConstants.NOTIFICATION_FILE_EXTENSION);
  }

  public static StoragePath getOtherMetadataFolderStoragePath(String aipId, String representationId)
    throws RequestNotValidException {
    return DefaultStoragePath.parse(getRepresentationOtherMetadataFolderPath(aipId, representationId));
  }

  public static StoragePath getOtherMetadataStoragePath(String aipId, String representationId,
    List<String> directoryPath, String fileName, String fileSuffix, String type) throws RequestNotValidException {

    if (fileSuffix == null) {
      throw new RequestNotValidException("File suffix cannot be null");
    }

    if (StringUtils.isBlank(type)) {
      throw new RequestNotValidException("Type cannot be empty");
    }

    List<String> path;

    if (aipId != null && representationId != null && directoryPath != null && fileName != null) {
      // other metadata pertaining to a file
      path = getRepresentationOtherMetadataPath(aipId, representationId, type);
      if (directoryPath != null) {
        path.addAll(directoryPath);
      }
      path.add(fileName + fileSuffix);
    } else if (aipId != null && representationId != null) {
      // other metadata pertaining to a representation
      path = getRepresentationOtherMetadataPath(aipId, representationId, type);
      path.add(representationId + fileSuffix);
      // XXX What if representation id is equal to a file id? Maybe move
      // this to
      // AIP metadata folder and have id
      // [aipId+"-"+representationId+fileSuffix]
    } else if (aipId != null) {
      path = getRepresentationOtherMetadataPath(aipId, representationId, type);
      path.add(aipId + fileSuffix);
    } else {
      throw new RequestNotValidException("AIP id cannot be null");
    }
    return DefaultStoragePath.parse(path);
  }

  public static <T extends Serializable> StoragePath getContainerPath(Class<T> clazz) throws RequestNotValidException {
    if (clazz.equals(Agent.class)) {
      return getAgentContainerPath();
    } else if (clazz.equals(Format.class)) {
      return getFormatContainerPath();
    } else if (clazz.equals(Notification.class)) {
      return getNotificationContainerPath();
    } else if (clazz.equals(Risk.class)) {
      return getRiskContainerPath();
    } else if (clazz.equals(LogEntry.class)) {
      return getLogContainerPath();
    } else if (clazz.equals(Job.class)) {
      return getJobContainerPath();
    } else if (clazz.equals(AIP.class)) {
      return getAIPcontainerPath();
    } else if (clazz.equals(Report.class)) {
      return getJobReportContainerPath();
    } else if (clazz.equals(RiskIncidence.class)) {
      return getRiskIncidenceContainerPath();
    } else {
      throw new RequestNotValidException("Unknown class for getting container path: " + clazz.getName());
    }
  }

  public static StoragePath getOtherMetadataStoragePath(String aipId, String representationId, String type,
    List<String> fileDirectoryPath, String fileId) throws RequestNotValidException {
    List<String> path = getRepresentationOtherMetadataPath(aipId, representationId, type);
    path.addAll(fileDirectoryPath);
    path.add(fileId);
    return DefaultStoragePath.parse(path);
  }

  /**
   * @deprecated use DownloadUtils instead.
   */
  @Deprecated
  public static void addToZip(List<ZipEntryInfo> zipEntries, org.roda.core.data.v2.ip.File file, boolean flat)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    StorageService storage = RodaCoreFactory.getStorageService();

    if (!file.isDirectory()) {
      StoragePath filePath = ModelUtils.getFileStoragePath(file);
      Binary binary = storage.getBinary(filePath);
      ZipEntryInfo info = new ZipEntryInfo(flat ? filePath.getName() : FSUtils.getStoragePathAsString(filePath, true),
        binary.getContent());
      zipEntries.add(info);
    } else {
      // do nothing
    }
  }

  /**
   * @deprecated use DownloadUtils instead.
   */
  @Deprecated
  public static void addToZip(List<ZipEntryInfo> zipEntries, Binary binary)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    String path = FSUtils.getStoragePathAsString(binary.getStoragePath(), true);
    ZipEntryInfo info = new ZipEntryInfo(path, binary.getContent());
    zipEntries.add(info);
  }

  /**
   * @deprecated use DownloadUtils instead.
   */
  @Deprecated
  public static List<ZipEntryInfo> zipIndexedAIP(List<IndexedAIP> aips)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    List<ZipEntryInfo> zipEntries = new ArrayList<ZipEntryInfo>();
    ModelService model = RodaCoreFactory.getModelService();
    for (IndexedAIP aip : aips) {
      AIP fullAIP = model.retrieveAIP(aip.getId());
      zipEntries.addAll(aipToZipEntry(fullAIP));
    }
    return zipEntries;
  }

  /**
   * @deprecated use DownloadUtils instead.
   */
  @Deprecated
  public static List<ZipEntryInfo> aipToZipEntry(AIP aip)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    List<ZipEntryInfo> zipEntries = new ArrayList<ZipEntryInfo>();
    StorageService storage = RodaCoreFactory.getStorageService();
    ModelService model = RodaCoreFactory.getModelService();

    StoragePath aipJsonPath = DefaultStoragePath.parse(ModelUtils.getAIPStoragePath(aip.getId()),
      RodaConstants.STORAGE_AIP_METADATA_FILENAME);
    addToZip(zipEntries, storage.getBinary(aipJsonPath));
    for (DescriptiveMetadata dm : aip.getDescriptiveMetadata()) {
      Binary dmBinary = model.retrieveDescriptiveMetadataBinary(aip.getId(), dm.getId());
      addToZip(zipEntries, dmBinary);
    }
    CloseableIterable<OptionalWithCause<org.roda.core.data.v2.ip.metadata.PreservationMetadata>> preservations = model
      .listPreservationMetadata(aip.getId(), true);
    for (OptionalWithCause<org.roda.core.data.v2.ip.metadata.PreservationMetadata> preservation : preservations) {
      if (preservation.isPresent()) {
        PreservationMetadata pm = preservation.get();
        StoragePath filePath = ModelUtils.getPreservationMetadataStoragePath(pm);
        addToZip(zipEntries, storage.getBinary(filePath));
      } else {
        LOGGER.error("Cannot get AIP representation file", preservation.getCause());
      }
    }
    IOUtils.closeQuietly(preservations);
    for (Representation rep : aip.getRepresentations()) {
      boolean recursive = true;
      CloseableIterable<OptionalWithCause<org.roda.core.data.v2.ip.File>> allFiles = model.listFilesUnder(aip.getId(),
        rep.getId(), recursive);
      for (OptionalWithCause<org.roda.core.data.v2.ip.File> file : allFiles) {
        if (file.isPresent()) {
          addToZip(zipEntries, file.get(), false);
        } else {
          LOGGER.error("Cannot get AIP representation file", file.getCause());
        }
      }
      IOUtils.closeQuietly(allFiles);

      recursive = false;
      CloseableIterable<OptionalWithCause<org.roda.core.data.v2.ip.metadata.OtherMetadata>> allOtherMetadata = model
        .listOtherMetadata(aip.getId(), rep.getId());
      for (OptionalWithCause<org.roda.core.data.v2.ip.metadata.OtherMetadata> otherMetadata : allOtherMetadata) {
        if (otherMetadata.isPresent()) {
          OtherMetadata o = otherMetadata.get();
          StoragePath otherMetadataStoragePath = ModelUtils.getOtherMetadataStoragePath(aip.getId(), rep.getId(),
            o.getFileDirectoryPath(), o.getFileId(), o.getFileSuffix(), o.getType());
          addToZip(zipEntries, storage.getBinary(otherMetadataStoragePath));
        } else {
          LOGGER.error("Cannot get Representation other metadata file", otherMetadata.getCause());
        }
      }
      IOUtils.closeQuietly(allFiles);
    }

    return zipEntries;
  }

  public static List<IndexedAIP> getIndexedAIPsFromObjectIds(SelectedItems<IndexedAIP> selectedItems)
    throws GenericException, RequestNotValidException {
    List<IndexedAIP> res = new ArrayList<IndexedAIP>();
    if (selectedItems instanceof SelectedItemsList) {
      SelectedItemsList<IndexedAIP> list = (SelectedItemsList<IndexedAIP>) selectedItems;
      for (String objectId : list.getIds()) {
        try {
          res.add(RodaCoreFactory.getIndexService().retrieve(IndexedAIP.class, objectId));
        } catch (GenericException | NotFoundException e) {
          LOGGER.error("Error retrieving TransferredResource", e);
        }
      }
    } else if (selectedItems instanceof SelectedItemsFilter) {
      IndexService index = RodaCoreFactory.getIndexService();
      SelectedItemsFilter<IndexedAIP> selectedItemsFilter = (SelectedItemsFilter<IndexedAIP>) selectedItems;
      long count = index.count(IndexedAIP.class, selectedItemsFilter.getFilter());
      for (int i = 0; i < count; i += RodaConstants.DEFAULT_PAGINATION_VALUE) {
        List<IndexedAIP> aips = index.find(IndexedAIP.class, selectedItemsFilter.getFilter(), null,
          new Sublist(i, RodaConstants.DEFAULT_PAGINATION_VALUE), null).getResults();
        res.addAll(aips);
      }
    }
    return res;
  }

  public static List<ZipEntryInfo> zipAIP(List<AIP> aips, SimpleJobPluginInfo jobPluginInfo)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    List<ZipEntryInfo> zipEntries = new ArrayList<ZipEntryInfo>();
    for (AIP aip : aips) {
      zipEntries.addAll(aipToZipEntry(aip));
      jobPluginInfo.incrementObjectsProcessedWithSuccess();
    }
    return zipEntries;
  }

  public static <T extends IsRODAObject> Class<T> giveRespectiveModelClass(Class<T> inputClass) {
    // function that give the model representation object of a RODA object
    if (IndexedAIP.class.equals(inputClass)) {
      return (Class<T>) AIP.class;
    } else if (IndexedRepresentation.class.equals(inputClass)) {
      return (Class<T>) Representation.class;
    } else if (IndexedFile.class.equals(inputClass)) {
      return (Class<T>) File.class;
    } else if (IndexedRisk.class.equals(inputClass)) {
      return (Class<T>) Risk.class;
    } else {
      return inputClass;
    }
  }
}
