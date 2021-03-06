/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.validation;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.operator.OperatorCreationException;
import org.roda.common.certification.ODFSignatureUtils;
import org.roda.common.certification.OOXMLSignatureUtils;
import org.roda.common.certification.PDFSignatureUtils;
import org.roda.common.certification.SignatureUtility;
import org.roda.common.certification.SignatureUtils;
import org.roda.core.RodaCoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DigitalSignatureDIPPluginUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(DigitalSignatureDIPPluginUtils.class);

  private static String KEYSTORE_PATH = RodaCoreFactory.getRodaHomePath()
    .resolve(RodaCoreFactory.getRodaConfigurationAsString("core", "signature", "keystore", "path")).toString();
  private static final String KEYSTORE_PASSWORD = RodaCoreFactory.getRodaConfigurationAsString("core", "signature",
    "keystore", "password");
  private static final String KEYSTORE_ALIAS = RodaCoreFactory.getRodaConfigurationAsString("core", "signature",
    "keystore", "alias");

  public static void setKeystorePath(String path) {
    KEYSTORE_PATH = path;
  }

  public static void addElementToRepresentationZip(ZipOutputStream zout, Path file, String name) {
    try {
      ZipEntry entry = new ZipEntry(name);
      InputStream in = new FileInputStream(file.toString());
      zout.putNextEntry(entry);
      byte[] data = IOUtils.toByteArray(in);
      zout.write(data);
      zout.closeEntry();
      IOUtils.closeQuietly(in);

    } catch (IOException e) {
      LOGGER.debug("Problems adding element to the representation zip");
    }
  }

  public static Path runZipDigitalSigner(Path input) {

    try {
      SignatureUtility signatureUtility = new SignatureUtility();
      if (KEYSTORE_PATH != null) {
        InputStream is = new FileInputStream(KEYSTORE_PATH);
        signatureUtility.loadKeyStore(is, KEYSTORE_PASSWORD.toCharArray());
      }
      signatureUtility.initSign(KEYSTORE_ALIAS, KEYSTORE_PASSWORD.toCharArray());

      Path zipResult = Files.createTempFile("signed_", ".zip");
      Path signatureTempFile = Files.createTempFile("signature_", ".p7s");
      signatureUtility.sign(input.toFile(), signatureTempFile.toFile());

      OutputStream os = new FileOutputStream(zipResult.toString());
      ZipOutputStream zout = new ZipOutputStream(os);

      // add representation zip
      ZipEntry zipEntry = new ZipEntry(input.toFile().getName());
      InputStream in = new FileInputStream(input.toString());
      zout.putNextEntry(zipEntry);
      byte[] data = IOUtils.toByteArray(in);
      zout.write(data);
      zout.closeEntry();
      IOUtils.closeQuietly(in);

      // add signature
      ZipEntry zipEntry2 = new ZipEntry(signatureTempFile.toFile().getName());
      InputStream in2 = new FileInputStream(signatureTempFile.toString());
      zout.putNextEntry(zipEntry2);
      byte[] data2 = IOUtils.toByteArray(in2);
      zout.write(data2);
      zout.closeEntry();
      IOUtils.closeQuietly(in2);

      zout.finish();
      IOUtils.closeQuietly(zout);
      IOUtils.closeQuietly(os);
      input.toFile().delete();
      signatureTempFile.toFile().delete();

      return zipResult;

    } catch (CertificateException | IOException e) {
      LOGGER.error("Cannot load keystore " + KEYSTORE_PATH, e);
    } catch (KeyStoreException | NoSuchAlgorithmException | NoSuchProviderException e) {
      LOGGER.error("Error initializing SignatureUtility", e);
    } catch (UnrecoverableKeyException | CMSException | OperatorCreationException e) {
      LOGGER.error("Error running initSign of SignatureUtility", e);
    }

    return null;
  }

  public static Path addEmbeddedSignature(Path input, String fileFormat, String mimetype) {
    try {
      String generalFileFormat = SignatureUtils.canHaveEmbeddedSignature(fileFormat, mimetype);
      if (generalFileFormat.equals("pdf")) {
        String reason = RodaCoreFactory.getRodaConfigurationAsString("core", "signature", "reason");
        String location = RodaCoreFactory.getRodaConfigurationAsString("core", "signature", "location");
        String contact = RodaCoreFactory.getRodaConfigurationAsString("core", "signature", "contact");

        return PDFSignatureUtils.runDigitalSignatureSign(input, KEYSTORE_PATH, KEYSTORE_ALIAS, KEYSTORE_PASSWORD,
          reason, location, contact);
      } else if (generalFileFormat.equals("ooxml")) {
        return OOXMLSignatureUtils.runDigitalSignatureSign(input, KEYSTORE_PATH, KEYSTORE_ALIAS, KEYSTORE_PASSWORD,
          fileFormat);
      } else if (generalFileFormat.equals("odf")) {
        return ODFSignatureUtils.runDigitalSignatureSign(input, KEYSTORE_PATH, KEYSTORE_ALIAS, KEYSTORE_PASSWORD,
          fileFormat);
      }
    } catch (Exception e) {
      LOGGER.warn("Problems running digital signature embedded signature");
      e.printStackTrace();
    }

    return null;
  }
}
