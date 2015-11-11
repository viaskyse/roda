/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.action.ingest.characterization.Droid.utils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.roda.common.RodaCoreFactory;
import org.roda.util.CommandException;
import org.roda.util.CommandUtility;

import edu.harvard.hul.ois.fits.exceptions.FitsException;

public class DroidUtils {
  static final private Logger logger = Logger.getLogger(DroidUtils.class);

  private static List<String> getBatchCommand(Path sourceDirectory) {
    Path rodaHome = RodaCoreFactory.getRodaHomePath();
    Path droidHome = rodaHome.resolve(RodaCoreFactory.getRodaConfigurationAsString("tools", "droid", "home"));
    Path signature = rodaHome.resolve(RodaCoreFactory.getRodaConfigurationAsString("tools", "droid", "signatureFile"));
    Path containerSignature = rodaHome
      .resolve(RodaCoreFactory.getRodaConfigurationAsString("tools", "droid", "containerSignatureFile"));

    File DROID_DIRECTORY = droidHome.toFile();

    String osName = System.getProperty("os.name");
    List<String> command;
    if (osName.startsWith("Windows")) {
      command = new ArrayList<String>(Arrays.asList(DROID_DIRECTORY.getAbsolutePath() + File.separator + "droid.bat",
        "-Ns", signature.toFile().getAbsolutePath(), "-Nc", containerSignature.toFile().getAbsolutePath(), "-q", "-Nr",
        sourceDirectory.toFile().getAbsolutePath()));
    } else {
      command = new ArrayList<String>(Arrays.asList(DROID_DIRECTORY.getAbsolutePath() + File.separator + "droid.sh",
        "-Ns", signature.toFile().getAbsolutePath(), "-Nc", containerSignature.toFile().getAbsolutePath(), "-q", "-Nr",
        sourceDirectory.toFile().getAbsolutePath()));
    }
    return command;
  }

  public static String runDROIDOnPath(Path sourceDirectory) throws FitsException {
    try {
      List<String> command = getBatchCommand(sourceDirectory);
      String droidOutput = CommandUtility.execute(command);
      return droidOutput;
    } catch (CommandException e) {
      throw new FitsException("Error while executing DROID command");
    }
  }
}