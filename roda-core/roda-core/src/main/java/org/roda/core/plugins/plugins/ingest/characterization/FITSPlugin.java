/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.ingest.characterization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.roda.core.data.PluginParameter;
import org.roda.core.data.Report;
import org.roda.core.data.common.InvalidParameterException;
import org.roda.core.data.v2.Representation;
import org.roda.core.index.IndexService;
import org.roda.core.model.AIP;
import org.roda.core.model.ModelService;
import org.roda.core.model.ModelServiceException;
import org.roda.core.model.utils.ModelUtils;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.storage.Binary;
import org.roda.core.storage.StoragePath;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.StorageServiceException;
import org.roda.core.storage.fs.FSUtils;
import org.roda.core.storage.fs.FileStorageService;

public class FITSPlugin implements Plugin<AIP> {
  private static final Logger LOGGER = Logger.getLogger(FITSPlugin.class);

  @Override
  public void init() throws PluginException {
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "Deep characterization action";
  }

  @Override
  public String getDescription() {
    return "Update the premis files with the object characterization";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public List<PluginParameter> getParameters() {
    return new ArrayList<>();
  }

  @Override
  public Map<String, String> getParameterValues() {
    return new HashMap<>();
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    // no params
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<AIP> list)
    throws PluginException {
    for (AIP aip : list) {
      LOGGER.debug("Processing AIP " + aip.getId());
      for (String representationID : aip.getRepresentationIds()) {
        LOGGER.debug("Processing representation " + representationID + " of AIP " + aip.getId());
        try {
          /*
           * Representation representation =
           * model.retrieveRepresentation(aip.getId(), representationID); for
           * (String fileID : representation.getFileIds()) { LOGGER.debug(
           * "Processing file " + fileID + " of representation " +
           * representationID + " from AIP " + aip.getId()); File file =
           * model.retrieveFile(aip.getId(), representationID, fileID); Binary
           * binary = storage.getBinary(file.getStoragePath());
           * 
           * Path fitsResult = FITSUtils.runFits(file, binary,
           * getParameterValues()); Binary resource = (Binary)
           * FSUtils.convertPathToResource(fitsResult.getParent(), fitsResult);
           * model.createOtherMetadata(aip.getId(), representationID,
           * file.getStoragePath().getName() + ".xml", "FITS", resource);
           * FSUtils.deletePath(fitsResult);
           * 
           * }
           */
          Path data = Files.createTempDirectory("data");
          Path output = Files.createTempDirectory("output");
          StorageService tempStorage = new FileStorageService(data);
          StoragePath representationPath = ModelUtils.getRepresentationPath(aip.getId(), representationID);
          tempStorage.copy(storage, representationPath, representationPath);
          FITSPluginUtils.runFITSOnPath(data.resolve(representationPath.asString()), output);
          Representation representation = model.retrieveRepresentation(aip.getId(), representationID);
          for (String fileID : representation.getFileIds()) {
            Path p = output.resolve(fileID + ".fits.xml");
            Binary resource = (Binary) FSUtils.convertPathToResource(p.getParent(), p);
            LOGGER.debug("Creating other metadata (AIP: " + aip.getId() + ", REPRESENTATION: " + representationID
              + ", FILE: " + fileID + ")");
            model.createOtherMetadata(aip.getId(), representationID, fileID + ".xml", "FITS", resource);
          }
          FSUtils.deletePath(data);
          FSUtils.deletePath(output);
        } catch (StorageServiceException sse) {
          LOGGER.error("Error processing AIP " + aip.getId() + ": " + sse.getMessage());
        } catch (PluginException fe) {
          LOGGER.error("Error processing AIP " + aip.getId() + ": " + fe.getMessage());
        } catch (ModelServiceException mse) {
          LOGGER.error("Error processing AIP " + aip.getId() + ": " + mse.getMessage());
        } catch (IOException ioe) {
          LOGGER.error("Error processing AIP " + aip.getId() + ": " + ioe.getMessage());
        }
      }

    }

    return null;
  }

  @Override
  public Report beforeExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {

    return null;
  }

  @Override
  public Report afterExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {

    return null;
  }

}