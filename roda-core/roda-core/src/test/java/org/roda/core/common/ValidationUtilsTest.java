/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.common;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.roda.core.CorporaConstants;
import org.roda.core.common.validation.ValidationUtils;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.metadata.DescriptiveMetadata;
import org.roda.core.data.v2.validation.ValidationException;
import org.roda.core.index.IndexServiceTest;
import org.roda.core.model.ModelService;
import org.roda.core.model.ModelServiceTest;
import org.roda.core.storage.DefaultStoragePath;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.fs.FSUtils;
import org.roda.core.storage.fs.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationUtilsTest {
  private static Path basePath;
  private static Path indexPath;
  private static StorageService storage;
  private static ModelService model;

  private static Path corporaPath;
  private static StorageService corporaService;

  private static final Logger logger = LoggerFactory.getLogger(ModelServiceTest.class);

  @BeforeClass
  public static void setUp() throws IOException, URISyntaxException, GenericException {

    basePath = Files.createTempDirectory("modelTests");
    indexPath = Files.createTempDirectory("indexTests");
    storage = new FileStorageService(basePath);
    model = new ModelService(storage);

    // Configure Solr
    // URL solrConfigURL =
    // IndexServiceTest.class.getResource("/index/solr.xml");
    // Path solrConfigPath = Paths.get(solrConfigURL.toURI());
    // Files.copy(solrConfigPath, indexPath.resolve("solr.xml"));
    // Path aipSchema = indexPath.resolve("aip");
    // Files.createDirectories(aipSchema);
    // Files.createFile(aipSchema.resolve("core.properties"));

    // Path solrHome =
    // Paths.get(IndexServiceTest.class.getResource("/index/").toURI());
    // System.setProperty("solr.data.dir", indexPath.toString());
    // System.setProperty("solr.data.dir.aip",
    // indexPath.resolve("aip").toString());
    // System.setProperty("solr.data.dir.sdo",
    // indexPath.resolve("sdo").toString());
    // System.setProperty("solr.data.dir.representation",
    // indexPath.resolve("representation").toString());
    // System.setProperty("solr.data.dir.preservationobject",
    // indexPath.resolve("preservationobject").toString());
    // System.setProperty("solr.data.dir.preservationevent",
    // indexPath.resolve("preservationevent").toString());
    // start embedded solr
    // final EmbeddedSolrServer solr = new EmbeddedSolrServer(solrHome, "test");

    URL corporaURL = IndexServiceTest.class.getResource("/corpora");
    corporaPath = Paths.get(corporaURL.toURI());
    corporaService = new FileStorageService(corporaPath);

    logger.debug("Running model test under storage: " + basePath);
  }

  @AfterClass
  public static void tearDown() throws NotFoundException, GenericException {
    FSUtils.deletePath(basePath);
    FSUtils.deletePath(indexPath);
  }

  @Test
  public void testValidateDescriptiveMetadata() throws ValidationException, RequestNotValidException, GenericException,
    AuthorizationDeniedException, AlreadyExistsException, NotFoundException {
    final String aipId = UUID.randomUUID().toString();
    model.createAIP(aipId, corporaService,
      DefaultStoragePath.parse(CorporaConstants.SOURCE_AIP_CONTAINER, CorporaConstants.SOURCE_AIP_ID));
    final DescriptiveMetadata descMetadata = model.retrieveDescriptiveMetadata(aipId,
      CorporaConstants.DESCRIPTIVE_METADATA_ID);
    assertEquals(ValidationUtils.isDescriptiveMetadataValid(model, descMetadata, true), true);
  }

  @Test
  public void testValidateDescriptiveMetadataBuggy() throws ValidationException, RequestNotValidException,
    GenericException, AuthorizationDeniedException, AlreadyExistsException, NotFoundException {
    // buggy aip have acqinfo2 instead of acqinfo in ead-c.xml
    final String aipId = UUID.randomUUID().toString();
    model.createAIP(aipId, corporaService,
      DefaultStoragePath.parse(CorporaConstants.SOURCE_AIP_CONTAINER, CorporaConstants.SOURCE_AIP_BUGGY_ID));
    final DescriptiveMetadata descMetadata = model.retrieveDescriptiveMetadata(aipId,
      CorporaConstants.DESCRIPTIVE_METADATA_ID);
    assertEquals(ValidationUtils.isDescriptiveMetadataValid(model, descMetadata, true), false);
  }

  @Test
  public void testValidateAIP() throws ValidationException, RequestNotValidException, GenericException,
    AuthorizationDeniedException, AlreadyExistsException, NotFoundException {
    final String aipId = UUID.randomUUID().toString();
    final AIP aip = model.createAIP(aipId, corporaService,
      DefaultStoragePath.parse(CorporaConstants.SOURCE_AIP_CONTAINER, CorporaConstants.SOURCE_AIP_ID));
    assertEquals(ValidationUtils.isAIPDescriptiveMetadataValid(model, aip.getId(), true), true);
  }

  @Ignore
  @Test
  public void testValidateAIPBuggy() throws ValidationException, RequestNotValidException, GenericException,
    AuthorizationDeniedException, AlreadyExistsException, NotFoundException {
    // TODO AIP changed, so the corpora also needs to be changed
    // buggy aip have acqinfo2 instead of acqinfo in ead-c.xml
    final String aipId = UUID.randomUUID().toString();
    final AIP aip = model.createAIP(aipId, corporaService,
      DefaultStoragePath.parse(CorporaConstants.SOURCE_AIP_CONTAINER, CorporaConstants.SOURCE_AIP_BUGGY_ID));
    assertEquals(ValidationUtils.isAIPDescriptiveMetadataValid(model, aip.getId(), true), false);
  }
}
