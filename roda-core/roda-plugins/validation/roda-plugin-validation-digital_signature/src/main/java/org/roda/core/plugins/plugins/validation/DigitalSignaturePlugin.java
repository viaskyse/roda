/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.roda.core.RodaCoreFactory;
import org.roda.core.common.IdUtils;
import org.roda.core.common.iterables.CloseableIterable;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.common.RodaConstants.PreservationEventType;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.InvalidParameterException;
import org.roda.core.data.exceptions.JobException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.IsRODAObject;
import org.roda.core.data.v2.common.OptionalWithCause;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.AIPState;
import org.roda.core.data.v2.ip.File;
import org.roda.core.data.v2.ip.IndexedFile;
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.data.v2.ip.metadata.LinkingIdentifier;
import org.roda.core.data.v2.jobs.PluginParameter;
import org.roda.core.data.v2.jobs.PluginParameter.PluginParameterType;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.jobs.Report.PluginState;
import org.roda.core.data.v2.validation.ValidationException;
import org.roda.core.data.v2.validation.ValidationIssue;
import org.roda.core.data.v2.validation.ValidationReport;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.model.utils.ModelUtils;
import org.roda.core.plugins.AbstractPlugin;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.orchestrate.SimpleJobPluginInfo;
import org.roda.core.plugins.plugins.PluginHelper;
import org.roda.core.plugins.plugins.common.FileFormatUtils;
import org.roda.core.storage.Binary;
import org.roda.core.storage.ContentPayload;
import org.roda.core.storage.DirectResourceAccess;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.fs.FSPathContentPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DigitalSignaturePlugin<T extends IsRODAObject> extends AbstractPlugin<T> {
  private static Logger LOGGER = LoggerFactory.getLogger(DigitalSignaturePlugin.class);

  private boolean doVerify;
  private boolean doExtract;
  private boolean doStrip;
  private boolean verificationAffectsOnOutcome;
  private List<String> applicableTo;
  private Map<String, List<String>> pronomToExtension;
  private Map<String, List<String>> mimetypeToExtension;
  private boolean ignoreFiles = true;

  private static Map<String, PluginParameter> pluginParameters = new HashMap<>();
  static {
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_SIGNATURE_VERIFY,
      new PluginParameter(RodaConstants.PLUGIN_PARAMS_SIGNATURE_VERIFY, "Verify digital signature",
        PluginParameterType.BOOLEAN, "true", true, false, "Verifies the digital signature of files"));

    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_SIGNATURE_EXTRACT,
      new PluginParameter(RodaConstants.PLUGIN_PARAMS_SIGNATURE_EXTRACT, "Extract digital signature",
        PluginParameterType.BOOLEAN, "false", true, false, "Extracts the information of the digital signature"));

    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_SIGNATURE_STRIP,
      new PluginParameter(RodaConstants.PLUGIN_PARAMS_SIGNATURE_STRIP, "Strip digital signature",
        PluginParameterType.BOOLEAN, "true", true, false, "Strips the digital signature of the file"));

    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_IGNORE_OTHER_FILES,
      new PluginParameter(RodaConstants.PLUGIN_PARAMS_IGNORE_OTHER_FILES, "Ignore non PDF files",
        PluginParameterType.BOOLEAN, "true", false, false, "Ignore files that are not recognised as PDF"));
  }

  public DigitalSignaturePlugin() {
    super();
    doVerify = true;
    doExtract = false;
    doStrip = false;
    verificationAffectsOnOutcome = Boolean.parseBoolean(RodaCoreFactory.getRodaConfigurationAsString("core", "tools",
      "digitalsignature", "verificationAffectsOnOutcome"));

    applicableTo = FileFormatUtils.getInputExtensions("digitalsignature");
    pronomToExtension = FileFormatUtils.getPronomToExtension("digitalsignature");
    mimetypeToExtension = FileFormatUtils.getMimetypeToExtension("digitalsignature");
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
    return "Validation of digital signature";
  }

  @Override
  public String getDescription() {
    return "Check if a digital signatures are valid.";
  }

  @Override
  public String getVersionImpl() {
    return "1.0";
  }

  @Override
  public List<PluginParameter> getParameters() {
    ArrayList<PluginParameter> parameters = new ArrayList<PluginParameter>();
    parameters.add(pluginParameters.get(RodaConstants.PLUGIN_PARAMS_SIGNATURE_VERIFY));
    parameters.add(pluginParameters.get(RodaConstants.PLUGIN_PARAMS_SIGNATURE_EXTRACT));
    parameters.add(pluginParameters.get(RodaConstants.PLUGIN_PARAMS_SIGNATURE_STRIP));
    parameters.add(pluginParameters.get(RodaConstants.PLUGIN_PARAMS_IGNORE_OTHER_FILES));
    return parameters;
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    super.setParameterValues(parameters);

    // do the digital signature verification
    if (parameters.containsKey(RodaConstants.PLUGIN_PARAMS_SIGNATURE_VERIFY)) {
      doVerify = Boolean.parseBoolean(parameters.get(RodaConstants.PLUGIN_PARAMS_SIGNATURE_VERIFY));
    }

    // do the digital signature information extraction
    if (parameters.containsKey(RodaConstants.PLUGIN_PARAMS_SIGNATURE_EXTRACT)) {
      doExtract = Boolean.parseBoolean(parameters.get(RodaConstants.PLUGIN_PARAMS_SIGNATURE_EXTRACT));
    }

    // do the digital signature strip
    if (parameters.containsKey(RodaConstants.PLUGIN_PARAMS_SIGNATURE_STRIP)) {
      doStrip = Boolean.parseBoolean(parameters.get(RodaConstants.PLUGIN_PARAMS_SIGNATURE_STRIP));
    }

    if (parameters.containsKey(RodaConstants.PLUGIN_PARAMS_IGNORE_OTHER_FILES)) {
      ignoreFiles = Boolean.parseBoolean(parameters.get(RodaConstants.PLUGIN_PARAMS_IGNORE_OTHER_FILES));
    }

  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<T> list)
    throws PluginException {

    if (!list.isEmpty()) {
      if (list.get(0) instanceof AIP) {
        return executeOnAIP(index, model, storage, (List<AIP>) list);
      } else if (list.get(0) instanceof Representation) {
        return executeOnRepresentation(index, model, storage, (List<Representation>) list);
      } else if (list.get(0) instanceof File) {
        return executeOnFile(index, model, storage, (List<File>) list);
      }
    }

    return PluginHelper.initPluginReport(this);
  }

  public Report executeOnAIP(IndexService index, ModelService model, StorageService storage, List<AIP> list)
    throws PluginException {
    List<String> newRepresentations = new ArrayList<String>();
    Report report = PluginHelper.initPluginReport(this);

    try {
      SimpleJobPluginInfo jobPluginInfo = PluginHelper.getInitialJobInformation(this, list.size());
      PluginHelper.updateJobInformation(this, jobPluginInfo);

      for (AIP aip : list) {
        Report reportItem = PluginHelper.initPluginReportItem(this, aip.getId(), AIP.class, AIPState.INGEST_PROCESSING);
        PluginHelper.updatePartialJobReport(this, model, index, reportItem, false);
        PluginState reportState = PluginState.SUCCESS;
        ValidationReport validationReport = new ValidationReport();
        boolean hasNonPdfFiles = false;

        try {
          for (Representation representation : aip.getRepresentations()) {
            String newRepresentationID = UUID.randomUUID().toString();
            List<File> unchangedFiles = new ArrayList<File>();
            List<File> alteredFiles = new ArrayList<File>();
            List<File> extractedFiles = new ArrayList<File>();
            List<File> newFiles = new ArrayList<File>();
            Map<String, String> verifiedFiles = new HashMap<String, String>();
            String verification = null;
            boolean notify = true;
            // FIXME 20160516 hsilva: see how to set initial
            // initialOutcomeObjectState

            LOGGER.debug("Processing representation {}", representation);
            boolean recursive = true;
            CloseableIterable<OptionalWithCause<File>> allFiles = model.listFilesUnder(representation.getAipId(),
              representation.getId(), recursive);

            for (OptionalWithCause<File> oFile : allFiles) {
              if (oFile.isPresent()) {
                File file = oFile.get();
                LOGGER.debug("Processing file {}", file);

                if (!file.isDirectory()) {
                  IndexedFile ifile = index.retrieve(IndexedFile.class, IdUtils.getFileId(file));
                  String fileMimetype = ifile.getFileFormat().getMimeType();
                  String filePronom = ifile.getFileFormat().getPronom();
                  String fileFormat = ifile.getId().substring(ifile.getId().lastIndexOf('.') + 1);
                  String fileInfoPath = StringUtils.join(Arrays.asList(aip.getId(), representation.getId(),
                    StringUtils.join(file.getPath(), '/'), file.getId()), '/');

                  if (((filePronom != null && pronomToExtension.containsKey(filePronom))
                    || (fileMimetype != null && getMimetypeToExtension().containsKey(fileMimetype))
                    || (applicableTo.contains(fileFormat)))) {

                    fileFormat = getNewFileFormat(fileFormat, filePronom, fileMimetype);
                    StoragePath fileStoragePath = ModelUtils.getFileStoragePath(file);
                    DirectResourceAccess directAccess = storage.getDirectAccess(fileStoragePath);
                    LOGGER.debug("Running DigitalSignaturePlugin on {}", file.getId());

                    if (doVerify) {
                      LOGGER.debug("Verifying digital signatures on {}", file.getId());

                      verification = DigitalSignaturePluginUtils.runDigitalSignatureVerify(directAccess.getPath(),
                        fileFormat, fileMimetype);
                      verifiedFiles.put(file.getId(), verification);

                      if (!verification.equals("Passed") && verificationAffectsOnOutcome) {
                        reportState = PluginState.FAILURE;
                        reportItem.addPluginDetails(" Signature validation failed on " + fileInfoPath + ".");
                      }
                    }

                    if (doExtract) {
                      LOGGER.debug("Extracting digital signatures information of {}", file.getId());
                      int extractResultSize = DigitalSignaturePluginUtils.runDigitalSignatureExtraction(model, file,
                        directAccess.getPath(), fileFormat, fileMimetype);

                      if (extractResultSize > 0) {
                        extractedFiles.add(file);
                      }
                    }

                    if (doStrip) {
                      LOGGER.debug("Stripping digital signatures from {}", file.getId());
                      Path pluginResult = DigitalSignaturePluginUtils.runDigitalSignatureStrip(directAccess.getPath(),
                        fileFormat, fileMimetype);

                      if (pluginResult != null) {
                        ContentPayload payload = new FSPathContentPayload(pluginResult);

                        if (!newRepresentations.contains(newRepresentationID)) {
                          LOGGER.debug("Creating a new representation {} on AIP {}", newRepresentationID, aip.getId());
                          boolean original = false;
                          newRepresentations.add(newRepresentationID);
                          model.createRepresentation(aip.getId(), newRepresentationID, original,
                            representation.getType(), notify);
                        }

                        // update file on new representation
                        String newFileId = file.getId().replaceFirst("[.][^.]+$", "." + fileFormat);
                        File f = model.createFile(aip.getId(), newRepresentationID, file.getPath(), newFileId, payload,
                          notify);
                        alteredFiles.add(file);
                        newFiles.add(f);

                      } else {
                        LOGGER.debug("Process failed on file {} of representation {} from AIP {}", file.getId(),
                          representation.getId(), aip.getId());
                        reportState = PluginState.FAILURE;
                        reportItem.addPluginDetails(" Signature validation stripping on " + fileInfoPath + ".");
                      }
                    }
                    IOUtils.closeQuietly(directAccess);

                  } else {
                    unchangedFiles.add(file);

                    if (ignoreFiles) {
                      validationReport.addIssue(new ValidationIssue(fileInfoPath));
                    } else {
                      reportState = PluginState.FAILURE;
                      hasNonPdfFiles = true;
                    }

                  }
                }
              } else {
                LOGGER.error("Cannot process representation file", oFile.getCause());
              }
            }
            IOUtils.closeQuietly(allFiles);

            // add unchanged files to the new representation
            if (!alteredFiles.isEmpty()) {
              for (File f : unchangedFiles) {
                StoragePath fileStoragePath = ModelUtils.getFileStoragePath(f);
                Binary binary = storage.getBinary(fileStoragePath);
                Path uriPath = Paths.get(binary.getContent().getURI());
                ContentPayload payload = new FSPathContentPayload(uriPath);
                model.createFile(f.getAipId(), newRepresentationID, f.getPath(), f.getId(), payload, notify);
              }
            }

            LOGGER.debug("Creating digital signature plugin event for the representation {}", representation.getId());
            boolean notifyEvent = true;
            createEvent(alteredFiles, extractedFiles, newFiles, verifiedFiles, aip, newRepresentationID, model, index,
              reportState, notifyEvent);
          }

          jobPluginInfo.incrementObjectsProcessed(reportState);
          reportItem.setPluginState(reportState);

          if (!reportState.equals(PluginState.FAILURE)) {
            if (ignoreFiles && validationReport.getIssues().size() > 0) {
              reportItem.setHtmlPluginDetails(true)
                .setPluginDetails(validationReport.toHtml(false, false, false, "Ignored files"));
            }
          }

          if (hasNonPdfFiles) {
            reportItem.setPluginDetails("Non PDF files were not ignored");
          }

        } catch (RODAException | IOException | RuntimeException e) {
          LOGGER.error("Error processing AIP " + aip.getId() + ": " + e.getMessage(), e);
          reportItem.setPluginState(PluginState.FAILURE).setPluginDetails(e.getMessage());
          jobPluginInfo.incrementObjectsProcessedWithFailure();
        } finally {
          report.addReport(reportItem);
          PluginHelper.updatePartialJobReport(this, model, index, reportItem, true);
        }
      }

      jobPluginInfo.finalizeInfo();
      PluginHelper.updateJobInformation(this, jobPluginInfo);
    } catch (JobException e) {
      throw new PluginException("A job exception has occurred", e);
    }

    return report;
  }

  public Report executeOnRepresentation(IndexService index, ModelService model, StorageService storage,
    List<Representation> list) throws PluginException {
    List<String> newRepresentations = new ArrayList<String>();
    Report report = PluginHelper.initPluginReport(this);

    try {
      SimpleJobPluginInfo jobPluginInfo = PluginHelper.getInitialJobInformation(this, list.size());
      PluginHelper.updateJobInformation(this, jobPluginInfo);

      for (Representation representation : list) {
        String newRepresentationID = UUID.randomUUID().toString();
        List<File> unchangedFiles = new ArrayList<File>();
        List<File> alteredFiles = new ArrayList<File>();
        List<File> extractedFiles = new ArrayList<File>();
        List<File> newFiles = new ArrayList<File>();
        Map<String, String> verifiedFiles = new HashMap<String, String>();
        String aipId = representation.getAipId();
        String verification = null;
        boolean notify = true;
        // FIXME 20160329 hsilva: the report item should be at AIP level (and
        // not representation level)
        // FIXME 20160516 hsilva: see how to set initial
        // initialOutcomeObjectState
        Report reportItem = PluginHelper.initPluginReportItem(this, IdUtils.getRepresentationId(representation),
          Representation.class, AIPState.INGEST_PROCESSING);
        PluginHelper.updatePartialJobReport(this, model, index, reportItem, false);
        PluginState reportState = PluginState.SUCCESS;
        ValidationReport validationReport = new ValidationReport();
        boolean hasNonPdfFiles = false;

        try {
          LOGGER.debug("Processing representation {}", representation);
          boolean recursive = true;
          CloseableIterable<OptionalWithCause<File>> allFiles = model.listFilesUnder(representation.getAipId(),
            representation.getId(), recursive);

          for (OptionalWithCause<File> oFile : allFiles) {
            if (oFile.isPresent()) {
              File file = oFile.get();
              LOGGER.debug("Processing file {}", file);

              if (!file.isDirectory()) {
                IndexedFile ifile = index.retrieve(IndexedFile.class, IdUtils.getFileId(file));
                String fileMimetype = ifile.getFileFormat().getMimeType();
                String filePronom = ifile.getFileFormat().getPronom();
                String fileFormat = ifile.getId().substring(ifile.getId().lastIndexOf('.') + 1);
                String fileInfoPath = StringUtils.join(
                  Arrays.asList(representation.getId(), StringUtils.join(file.getPath(), '/'), file.getId()), '/');

                if (((filePronom != null && pronomToExtension.containsKey(filePronom))
                  || (fileMimetype != null && getMimetypeToExtension().containsKey(fileMimetype))
                  || (applicableTo.contains(fileFormat)))) {

                  fileFormat = getNewFileFormat(fileFormat, filePronom, fileMimetype);
                  StoragePath fileStoragePath = ModelUtils.getFileStoragePath(file);
                  DirectResourceAccess directAccess = storage.getDirectAccess(fileStoragePath);
                  LOGGER.debug("Running DigitalSignaturePlugin on {}", file.getId());

                  if (doVerify) {
                    LOGGER.debug("Verifying digital signatures on {}", file.getId());

                    verification = DigitalSignaturePluginUtils.runDigitalSignatureVerify(directAccess.getPath(),
                      fileFormat, fileMimetype);
                    verifiedFiles.put(file.getId(), verification);

                    if (!verification.equals("Passed") && verificationAffectsOnOutcome) {
                      reportState = PluginState.FAILURE;
                      reportItem.addPluginDetails(" Signature validation failed on " + fileInfoPath + ".");
                    }
                  }

                  if (doExtract) {
                    LOGGER.debug("Extracting digital signatures information of {}", file.getId());
                    int extractResultSize = DigitalSignaturePluginUtils.runDigitalSignatureExtraction(model, file,
                      directAccess.getPath(), fileFormat, fileMimetype);

                    if (extractResultSize > 0) {
                      extractedFiles.add(file);
                    }
                  }

                  if (doStrip) {
                    LOGGER.debug("Stripping digital signatures from {}", file.getId());
                    Path pluginResult = DigitalSignaturePluginUtils.runDigitalSignatureStrip(directAccess.getPath(),
                      fileFormat, fileMimetype);

                    if (pluginResult != null) {
                      ContentPayload payload = new FSPathContentPayload(pluginResult);

                      if (!newRepresentations.contains(newRepresentationID)) {
                        LOGGER.debug("Creating a new representation {} on AIP {}", newRepresentationID, aipId);
                        boolean original = false;
                        newRepresentations.add(newRepresentationID);
                        model.createRepresentation(aipId, newRepresentationID, original, representation.getType(),
                          notify);
                        reportItem.setOutcomeObjectId(
                          IdUtils.getRepresentationId(representation.getAipId(), newRepresentationID));
                      }

                      // update file on new representation
                      String newFileId = file.getId().replaceFirst("[.][^.]+$", "." + fileFormat);
                      File f = model.createFile(aipId, newRepresentationID, file.getPath(), newFileId, payload, notify);
                      alteredFiles.add(file);
                      newFiles.add(f);
                      reportItem.setPluginState(reportState);

                    } else {
                      LOGGER.debug("Process failed on file {} of representation {} from AIP {}", file.getId(),
                        representation.getId(), aipId);
                      reportState = PluginState.FAILURE;
                      reportItem.addPluginDetails(" Signature validation stripping on " + fileInfoPath + ".");
                    }
                  }
                  IOUtils.closeQuietly(directAccess);
                } else {
                  unchangedFiles.add(file);

                  if (ignoreFiles) {
                    validationReport.addIssue(new ValidationIssue(fileInfoPath));
                  } else {
                    reportState = PluginState.FAILURE;
                    hasNonPdfFiles = true;
                  }
                }
              }
            } else {
              LOGGER.error("Cannot process representation file", oFile.getCause());
            }
          }
          IOUtils.closeQuietly(allFiles);

          // add unchanged files to the new representation
          if (!alteredFiles.isEmpty()) {
            for (File f : unchangedFiles) {
              StoragePath fileStoragePath = ModelUtils.getFileStoragePath(f);
              Binary binary = storage.getBinary(fileStoragePath);
              Path uriPath = Paths.get(binary.getContent().getURI());
              ContentPayload payload = new FSPathContentPayload(uriPath);
              model.createFile(f.getAipId(), newRepresentationID, f.getPath(), f.getId(), payload, notify);
            }
          }

          LOGGER.debug("Creating digital signature plugin event for the representation {}", representation.getId());
          boolean notifyEvent = true;
          createEvent(alteredFiles, extractedFiles, newFiles, verifiedFiles, model.retrieveAIP(aipId),
            newRepresentationID, model, index, reportState, notifyEvent);
          reportItem.setPluginState(reportState);
          jobPluginInfo.incrementObjectsProcessed(reportState);

          if (!reportState.equals(PluginState.FAILURE)) {
            if (ignoreFiles) {
              reportItem.setHtmlPluginDetails(true)
                .setPluginDetails(validationReport.toHtml(false, false, false, "Ignored files"));
            }
          }

          if (hasNonPdfFiles) {
            reportItem.setPluginDetails("Non PDF files were not ignored");
          }

        } catch (RODAException | IOException | RuntimeException e) {
          LOGGER.error("Error processing Representation " + representation.getId() + ": " + e.getMessage(), e);
          reportState = PluginState.FAILURE;
          reportItem.setPluginState(reportState).setPluginDetails(e.getMessage());
          jobPluginInfo.incrementObjectsProcessedWithFailure();
        } finally {
          report.addReport(reportItem);
          PluginHelper.updatePartialJobReport(this, model, index, reportItem, true);
        }
      }

      jobPluginInfo.finalizeInfo();
      PluginHelper.updateJobInformation(this, jobPluginInfo);
    } catch (JobException e) {
      throw new PluginException("A job exception has occurred", e);
    }

    return report;
  }

  public Report executeOnFile(IndexService index, ModelService model, StorageService storage, List<File> list)
    throws PluginException {
    List<String> newRepresentations = new ArrayList<String>();
    Report report = PluginHelper.initPluginReport(this);

    try {
      SimpleJobPluginInfo jobPluginInfo = PluginHelper.getInitialJobInformation(this, list.size());
      PluginHelper.updateJobInformation(this, jobPluginInfo);

      String newRepresentationID = UUID.randomUUID().toString();
      List<File> unchangedFiles = new ArrayList<File>();
      List<File> alteredFiles = new ArrayList<File>();
      List<File> extractedFiles = new ArrayList<File>();
      List<File> newFiles = new ArrayList<File>();
      Map<String, String> verifiedFiles = new HashMap<String, String>();

      String verification = null;
      boolean notify = true;
      // FIXME 20160329 hsilva: the report item should be at AIP level (and
      // not representation level)
      // FIXME 20160516 hsilva: see how to set initial
      // initialOutcomeObjectState

      for (File file : list) {
        LOGGER.debug("Processing file {}", file);
        Report reportItem = PluginHelper.initPluginReportItem(this, IdUtils.getFileId(file), File.class,
          AIPState.INGEST_PROCESSING);
        PluginHelper.updatePartialJobReport(this, model, index, reportItem, false);
        PluginState reportState = PluginState.SUCCESS;

        try {

          if (!file.isDirectory()) {
            IndexedFile ifile = index.retrieve(IndexedFile.class, IdUtils.getFileId(file));
            String fileMimetype = ifile.getFileFormat().getMimeType();
            String filePronom = ifile.getFileFormat().getPronom();
            String fileFormat = ifile.getId().substring(ifile.getId().lastIndexOf('.') + 1);

            if (((filePronom != null && pronomToExtension.containsKey(filePronom))
              || (fileMimetype != null && getMimetypeToExtension().containsKey(fileMimetype))
              || (applicableTo.contains(fileFormat)))) {

              fileFormat = getNewFileFormat(fileFormat, filePronom, fileMimetype);
              StoragePath fileStoragePath = ModelUtils.getFileStoragePath(file);
              DirectResourceAccess directAccess = storage.getDirectAccess(fileStoragePath);
              LOGGER.debug("Running DigitalSignaturePlugin on {}", file.getId());

              if (doVerify) {
                LOGGER.debug("Verifying digital signatures on {}", file.getId());

                verification = DigitalSignaturePluginUtils.runDigitalSignatureVerify(directAccess.getPath(), fileFormat,
                  fileMimetype);
                verifiedFiles.put(file.getId(), verification);

                if (!verification.equals("Passed") && verificationAffectsOnOutcome) {
                  reportState = PluginState.FAILURE;
                  reportItem.addPluginDetails("Signature validation failed on " + file.getId() + ".");
                }
              }

              if (doExtract) {
                LOGGER.debug("Extracting digital signatures information of {}", file.getId());
                int extractResultSize = DigitalSignaturePluginUtils.runDigitalSignatureExtraction(model, file,
                  directAccess.getPath(), fileFormat, fileMimetype);

                if (extractResultSize > 0) {
                  extractedFiles.add(file);
                }
              }

              if (doStrip) {
                LOGGER.debug("Stripping digital signatures from {}", file.getId());
                Path pluginResult = DigitalSignaturePluginUtils.runDigitalSignatureStrip(directAccess.getPath(),
                  fileFormat, fileMimetype);

                if (pluginResult != null) {
                  ContentPayload payload = new FSPathContentPayload(pluginResult);

                  if (!newRepresentations.contains(newRepresentationID)) {
                    LOGGER.debug("Creating a new representation {} on AIP {}", newRepresentationID, file.getAipId());
                    boolean original = false;
                    newRepresentations.add(newRepresentationID);
                    Representation representation = model.retrieveRepresentation(file.getAipId(),
                      file.getRepresentationId());
                    model.createRepresentation(file.getAipId(), newRepresentationID, original, representation.getType(),
                      notify);
                  }

                  // update file on new representation
                  String newFileId = file.getId().replaceFirst("[.][^.]+$", "." + fileFormat);
                  File f = model.createFile(file.getAipId(), newRepresentationID, file.getPath(), newFileId, payload,
                    notify);
                  alteredFiles.add(file);
                  newFiles.add(f);

                  reportItem.setOutcomeObjectId(IdUtils.getFileId(f));
                  reportItem.setPluginState(reportState);

                } else {
                  LOGGER.debug("Process failed on file {} of representation {} from AIP {}", file.getId(),
                    file.getRepresentationId(), file.getAipId());

                  reportState = PluginState.FAILURE;
                  reportItem.setPluginState(reportState)
                    .addPluginDetails(" Signature validation stripping on " + file.getId() + ".");
                }
              }
              IOUtils.closeQuietly(directAccess);
            } else {
              unchangedFiles.add(file);

              if (!reportState.equals(PluginState.FAILURE)) {
                if (ignoreFiles) {
                  reportItem.setPluginDetails("This file was ignored.");
                } else {
                  reportState = PluginState.FAILURE;
                  reportItem.setPluginDetails("This file was not ignored.");
                }
              }
            }
          }

          // add unchanged files to the new representation
          if (!alteredFiles.isEmpty()) {
            for (File f : unchangedFiles) {
              StoragePath fileStoragePath = ModelUtils.getFileStoragePath(f);
              Binary binary = storage.getBinary(fileStoragePath);
              Path uriPath = Paths.get(binary.getContent().getURI());
              ContentPayload payload = new FSPathContentPayload(uriPath);
              model.createFile(f.getAipId(), newRepresentationID, f.getPath(), f.getId(), payload, notify);
            }
          }

          reportItem.setPluginState(reportState);

        } catch (RODAException | IOException | RuntimeException e) {
          LOGGER.error("Error processing Representation " + file.getRepresentationId() + ": " + e.getMessage(), e);
          reportState = PluginState.FAILURE;
          reportItem.setPluginState(reportState).setPluginDetails(e.getMessage());
        } finally {
          report.addReport(reportItem);
          PluginHelper.updatePartialJobReport(this, model, index, reportItem, true);
        }

        LOGGER.debug("Creating digital signature plugin event for the representation {}", file.getRepresentationId());
        boolean notifyEvent = true;
        createEvent(alteredFiles, extractedFiles, newFiles, verifiedFiles, model.retrieveAIP(file.getAipId()),
          newRepresentationID, model, index, reportState, notifyEvent);
        jobPluginInfo.incrementObjectsProcessed(reportState);
      }

      jobPluginInfo.finalizeInfo();
      PluginHelper.updateJobInformation(this, jobPluginInfo);
    } catch (RequestNotValidException | NotFoundException | GenericException | AuthorizationDeniedException e) {
      LOGGER.error("Error retrieving aip from file");
    } catch (JobException e) {
      throw new PluginException("A job exception has occurred", e);
    }

    return report;
  }

  private void createEvent(List<File> alteredFiles, List<File> extractedFiles, List<File> newFiles,
    Map<String, String> verifiedFiles, AIP aip, String newRepresentationID, ModelService model, IndexService index,
    PluginState pluginResultState, boolean notify) throws PluginException {

    List<LinkingIdentifier> premisSourceFilesIdentifiers = new ArrayList<LinkingIdentifier>();
    List<LinkingIdentifier> premisTargetFilesIdentifiers = new ArrayList<LinkingIdentifier>();

    // building the detail for the plugin event
    StringBuilder stringBuilder = new StringBuilder();

    if (doVerify) {
      if (!verifiedFiles.isEmpty()) {
        stringBuilder.append("The DS verification ran on: ");
        String verifies = "";
        for (String fileId : verifiedFiles.keySet()) {
          verifies += fileId + " (" + verifiedFiles.get(fileId) + "), ";
        }
        stringBuilder.append(verifies.substring(0, verifies.lastIndexOf(',')) + ". ");
      }
    }

    if (doExtract) {
      if (!extractedFiles.isEmpty()) {
        stringBuilder.append("The following files DS information were extracted: ");
        String extracts = "";

        for (File file : extractedFiles) {
          extracts += file.getId() + ", ";
        }

        if (extracts.length() > 0) {
          stringBuilder.append(extracts.substring(0, extracts.lastIndexOf(',')) + ". ");
        }
      }
    }

    if (alteredFiles.isEmpty()) {
      stringBuilder.append("No file was stripped on this representation.");
    } else {
      stringBuilder.append("The digital signature (DS) operation stripped some files. ");
      for (File file : alteredFiles) {
        premisSourceFilesIdentifiers.add(PluginHelper.getLinkingIdentifier(aip.getId(), file.getRepresentationId(),
          file.getPath(), file.getId(), RodaConstants.PRESERVATION_LINKING_OBJECT_SOURCE));
      }
      for (File file : newFiles) {
        premisTargetFilesIdentifiers.add(PluginHelper.getLinkingIdentifier(aip.getId(), file.getRepresentationId(),
          file.getPath(), file.getId(), RodaConstants.PRESERVATION_LINKING_OBJECT_OUTCOME));
      }
    }

    // FIXME revise PREMIS generation
    try {
      PluginHelper.createPluginEvent(this, aip.getId(), model, index, premisSourceFilesIdentifiers,
        premisTargetFilesIdentifiers, pluginResultState, stringBuilder.toString(), notify);
    } catch (RequestNotValidException | NotFoundException | GenericException | AuthorizationDeniedException
      | ValidationException | AlreadyExistsException e) {
      throw new PluginException(e.getMessage(), e);
    }
  }

  private String getNewFileFormat(String fileFormat, String filePronom, String fileMimetype) {
    if (!applicableTo.isEmpty()) {
      if (filePronom != null && !filePronom.isEmpty() && pronomToExtension.get(filePronom) != null
        && !pronomToExtension.get(filePronom).contains(fileFormat)) {
        fileFormat = pronomToExtension.get(filePronom).get(0);
      } else if (fileMimetype != null && !fileMimetype.isEmpty() && mimetypeToExtension.get(fileMimetype) != null
        && !mimetypeToExtension.get(fileMimetype).contains(fileFormat)) {
        fileFormat = mimetypeToExtension.get(fileMimetype).get(0);
      }
    }
    return fileFormat;
  }

  @Override
  public PluginType getType() {
    return PluginType.AIP_TO_AIP;
  }

  @Override
  public Plugin<T> cloneMe() {
    return new DigitalSignaturePlugin<T>();
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }

  @Override
  public PreservationEventType getPreservationEventType() {
    return PreservationEventType.DIGITAL_SIGNATURE_VALIDATION;
  }

  @Override
  public String getPreservationEventDescription() {
    return "Checked if digital signatures were valid and/or stripped them.";
  }

  @Override
  public String getPreservationEventSuccessMessage() {
    return "Digital signatures were valid and/or they were stripped with success.";
  }

  @Override
  public String getPreservationEventFailureMessage() {
    return "Failed to validate and/or strip digital signatures.";
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
    return Arrays.asList(RodaConstants.PLUGIN_CATEGORY_VALIDATION);
  }

  public List<String> getApplicableTo() {
    return applicableTo;
  }

  public Map<String, List<String>> getPronomToExtension() {
    return pronomToExtension;
  }

  public Map<String, List<String>> getMimetypeToExtension() {
    return mimetypeToExtension;
  }

  @Override
  public List<Class<T>> getObjectClasses() {
    List<Class<? extends IsRODAObject>> list = new ArrayList<>();
    list.add(AIP.class);
    list.add(Representation.class);
    list.add(File.class);
    return (List) list;
  }

}
