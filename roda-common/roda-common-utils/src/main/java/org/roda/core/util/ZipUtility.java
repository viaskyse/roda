/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Rui Castro
 * @author Vladislav Korecký <vladislav_korecky@gordic.cz>
 * 
 * @since 20160909 hsilva: RODA is not using any of these methods, but external
 *        plugins are
 */
public final class ZipUtility {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZipUtility.class);
  private static final int BUFFER_SIZE = 1024;

  /**
   * Extract files in zipFilename to outputDir.
   * 
   * @param zipFilename
   *          the zip file to extract files from.
   * @param outputDir
   *          the output directory to extract files to.
   * @throws IOException
   *           if a input/output operation fails, like opening a file or
   *           reading/writing from/to a stream.
   */
  public static void extractZIPFiles(File zipFilename, File outputDir) throws IOException {
    extractFilesFromZIP(zipFilename, outputDir);
  }

  /**
   * Extract files in zipFilename to outputDir.
   * 
   * @param zipFilename
   *          the zip file to extract files from.
   * @param outputDir
   *          the output directory to extract files to.
   * 
   * @return a {@link List} of with all the extracted {@link File}s.
   * 
   * @throws IOException
   *           if a input/output operation fails, like opening a file or
   *           reading/writing from/to a stream.
   * 
   * @deprecated Use {@link ZipUtility#extractFilesFromZIP(File, File, boolean)}
   *             instead of this method because in this method is not clear if
   *             you are going to get a list of files with absolute path or not
   */
  @Deprecated
  public static List<File> extractFilesFromZIP(File zipFilename, File outputDir) throws IOException {
    return extractFilesFromZIP(zipFilename, outputDir, false);
  }

  /**
   * Extract files in zipFilename to outputDir.
   * 
   * @param zipFilename
   *          the zip file to extract files from.
   * @param outputDir
   *          the output directory to extract files to.
   * @param filesWithAbsolutePath
   *          determines if the output list of files will contain the absolute
   *          or relative path to the files
   * 
   * @return a {@link List} of with all the extracted {@link File}s.
   * 
   * @throws IOException
   *           if a input/output operation fails, like opening a file or
   *           reading/writing from/to a stream.
   */
  public static List<File> extractFilesFromZIP(File zipFilename, File outputDir, boolean filesWithAbsolutePath)
    throws IOException {
    return extractFilesFromInputStream(new FileInputStream(zipFilename), outputDir, filesWithAbsolutePath);

  }

  private static List<File> extractFilesFromInputStream(InputStream inputStream, File outputDir,
    boolean filesWithAbsolutePath) throws IOException {
    ZipInputStream zipInputStream = new ZipInputStream(inputStream);
    // JarInputStream jarInputStream = new JarInputStream(new
    // FileInputStream(
    // zipFilename));

    ZipEntry zipEntry = zipInputStream.getNextEntry();
    // JarEntry jarEntry = jarInputStream.getNextJarEntry();

    if (zipEntry == null) {
      // if (jarEntry == null) {
      // No entries in ZIP

      zipInputStream.close();
      // jarInputStream.close();

      throw new IOException("No files inside ZIP");

    } else {

      List<File> extractedFiles = new ArrayList<File>();

      while (zipEntry != null) {
        // while (jarEntry != null) {

        // for each entry to be extracted
        String entryName = zipEntry.getName();
        // String entryName = jarEntry.getName();

        LOGGER.debug("Extracting {}", entryName);

        File newFile = new File(outputDir, entryName);

        if (filesWithAbsolutePath) {
          extractedFiles.add(newFile);
        } else {
          extractedFiles.add(new File(entryName));
        }

        if (zipEntry.isDirectory()) {
          // if (jarEntry.isDirectory()) {

          newFile.mkdirs();

        } else {

          if (newFile.getParentFile() != null && (!newFile.getParentFile().exists())) {
            newFile.getParentFile().mkdirs();
          }

          FileOutputStream newFileOutputStream = new FileOutputStream(newFile);

          // IOUtils.copy(zipInputStream, newFileOutputStream);
          // copyLarge returns a long instead of int
          IOUtils.copyLarge(zipInputStream, newFileOutputStream);
          // IOUtils.copyLarge(jarInputStream, newFileOutputStream);

          // int n;
          // while ((n = zipInputStream.read(buf, 0, BUFFER_SIZE)) >
          // -1) {
          // newFileOutputStream.write(buf, 0, n);
          // }

          newFileOutputStream.close();
          zipInputStream.closeEntry();
          // jarInputStream.closeEntry();

        }

        zipEntry = zipInputStream.getNextEntry();
        // jarEntry = jarInputStream.getNextJarEntry();

      } // end while

      zipInputStream.close();
      // jarInputStream.close();

      return extractedFiles;
    }
  }

  /**
   * Creates ZIP file with the files inside directory <code>contentsDir</code> .
   * 
   * @param newZipFile
   *          the ZIP file to create
   * @param contentsDir
   *          the directory containing the files to compress.
   * @return the created ZIP file.
   * @throws IOException
   *           if something goes wrong with creation of the ZIP file or the
   *           reading of the files to compress.
   */
  public static File createZIPFile(File newZipFile, File contentsDir) throws IOException {

    List<File> contentAbsoluteFiles = FileUtility.listFilesRecursively(contentsDir);

    JarOutputStream jarOutputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(newZipFile)));
    // ZipOutputStream zipOutputStream = new ZipOutputStream(
    // new BufferedOutputStream(new FileOutputStream(newZipFile)));

    // Create a buffer for reading the files
    byte[] buffer = new byte[BUFFER_SIZE];

    Iterator<File> iterator = contentAbsoluteFiles.iterator();
    while (iterator.hasNext()) {
      File absoluteFile = iterator.next();
      String relativeFile = getFilePathRelativeTo(absoluteFile, contentsDir);

      BufferedInputStream in = new BufferedInputStream(new FileInputStream(absoluteFile));

      // Add ZIP entry to output stream.
      // zipOutputStream.putNextEntry(new
      // ZipEntry(relativeFile.toString()));
      jarOutputStream.putNextEntry(new JarEntry(relativeFile));

      LOGGER.trace("Adding {}", relativeFile);

      int length;
      while ((length = in.read(buffer)) > 0) {
        // zipOutputStream.write(buffer, 0, length);
        jarOutputStream.write(buffer, 0, length);
      }

      // Complete the entry
      // zipOutputStream.closeEntry();
      jarOutputStream.closeEntry();
      in.close();
    }

    // Complete the ZIP file
    // zipOutputStream.close();
    jarOutputStream.close();

    return newZipFile;
  }

  /**
   * @param file
   *          the {@link File} to make relative
   * @param relativeTo
   *          the {@link File} (or directory) that file should be made relative
   *          to.
   * @return a {@link String} with the relative file path.
   */
  public static String getFilePathRelativeTo(File file, File relativeTo) {
    return relativeTo.getAbsoluteFile().toURI().relativize(file.getAbsoluteFile().toURI()).toString();
  }

  /**
   * Adds file / folder to zip output stream. Method works recursively.
   * 
   * @param zos
   * @param srcFile
   */
  public static void addDirToArchive(ZipOutputStream zos, File srcFile) {
    File[] files = srcFile.listFiles();
    for (int i = 0; i < files.length; i++) {
      // if the file is directory, use recursion
      if (files[i].isDirectory()) {
        addDirToArchive(zos, files[i]);
        continue;
      }
      try {
        // create byte buffer
        byte[] buffer = new byte[1024];
        FileInputStream fis = new FileInputStream(files[i]);
        zos.putNextEntry(new ZipEntry(files[i].getName()));
        int length;
        while ((length = fis.read(buffer)) > 0) {
          zos.write(buffer, 0, length);
        }
        zos.closeEntry();
        // close the InputStream
        fis.close();
      } catch (IOException e) {
        LOGGER.error("Error adding file/folder to zip", e);
      }
    }
  }

  public static void zip(File directory, ZipOutputStream zout) throws IOException {
    URI base = directory.toURI();
    Deque<File> queue = new LinkedList<File>();
    queue.push(directory);
    try {
      while (!queue.isEmpty()) {
        directory = queue.pop();
        for (File kid : directory.listFiles()) {
          String name = base.relativize(kid.toURI()).getPath();
          if (kid.isDirectory()) {
            queue.push(kid);
            name = name.endsWith("/") ? name : name + "/";
            zout.putNextEntry(new ZipEntry(name));
          } else {
            zout.putNextEntry(new ZipEntry(name));
            copy(kid, zout);
            zout.closeEntry();
          }
        }
      }
    } finally {
      zout.close();
    }
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[1024];
    while (true) {
      int readCount = in.read(buffer);
      if (readCount < 0) {
        break;
      }
      out.write(buffer, 0, readCount);
    }
  }

  private static void copy(File file, OutputStream out) throws IOException {
    InputStream in = new FileInputStream(file);
    try {
      copy(in, out);
    } finally {
      in.close();
    }
  }

  private static void copy(InputStream in, File file) throws IOException {
    OutputStream out = new FileOutputStream(file);
    try {
      copy(in, out);
    } finally {
      out.close();
    }
  }

  public static List<File> extractFilesFromInputStream(InputStream inputStream, Path outputDir) throws IOException {
    return extractFilesFromInputStream(inputStream, outputDir.toFile(), false);

  }
}
