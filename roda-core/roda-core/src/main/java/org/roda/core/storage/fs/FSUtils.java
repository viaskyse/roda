/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.storage.fs;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.roda.core.common.iterables.CloseableIterable;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.storage.Container;
import org.roda.core.storage.ContentPayload;
import org.roda.core.storage.DefaultBinary;
import org.roda.core.storage.DefaultContainer;
import org.roda.core.storage.DefaultDirectory;
import org.roda.core.storage.DefaultStoragePath;
import org.roda.core.storage.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File System related utility class
 * 
 * @author Luis Faria <lfaria@keep.pt>
 * @author Hélder Silva <hsilva@keep.pt>
 */
public final class FSUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(FSUtils.class);

  /**
   * Private empty constructor
   */
  private FSUtils() {

  }

  /**
   * Moves a directory/file from one path to another
   * 
   * @param sourcePath
   *          source path
   * @param targetPath
   *          target path
   * @param replaceExisting
   *          true if the target directory/file should be replaced if it already
   *          exists; false otherwise
   * @throws AlreadyExistsException
   * @throws GenericException
   * 
   */
  public static void move(final Path sourcePath, final Path targetPath, boolean replaceExisting)
    throws AlreadyExistsException, GenericException {

    // check if we can replace existing
    if (!replaceExisting && Files.exists(targetPath)) {
      throw new AlreadyExistsException("Cannot copy because target path already exists: " + targetPath);
    }

    // ensure parent directory exists or can be created
    try {
      Files.createDirectories(targetPath.getParent());
    } catch (IOException e) {
      throw new GenericException("Error while creating target directory parent folder", e);
    }

    CopyOption[] copyOptions = replaceExisting ? new CopyOption[] {StandardCopyOption.REPLACE_EXISTING}
      : new CopyOption[] {};

    if (Files.isDirectory(sourcePath)) {
      try {
        Files.move(sourcePath, targetPath, copyOptions);
      } catch (IOException e) {
        throw new GenericException("Error while moving directory from one path to another", e);
      }
    } else {
      try {
        Files.move(sourcePath, targetPath, copyOptions);
      } catch (IOException e) {
        throw new GenericException("Error while copying one file into another", e);
      }
    }

  }

  /**
   * Copies a directory/file from one path to another
   * 
   * @param sourcePath
   *          source path
   * @param targetPath
   *          target path
   * @param replaceExisting
   *          true if the target directory/file should be replaced if it already
   *          exists; false otherwise
   * @throws AlreadyExistsException
   * @throws GenericException
   */
  public static void copy(final Path sourcePath, final Path targetPath, boolean replaceExisting)
    throws AlreadyExistsException, GenericException {

    // check if we can replace existing
    if (!replaceExisting && Files.exists(targetPath)) {
      throw new AlreadyExistsException("Cannot copy because target path already exists: " + targetPath);
    }

    // ensure parent directory exists or can be created
    try {
      Files.createDirectories(targetPath.getParent());
    } catch (IOException e) {
      throw new GenericException("Error while creating target directory parent folder", e);
    }

    if (Files.isDirectory(sourcePath)) {
      try {
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
            Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            Files.copy(file, targetPath.resolve(sourcePath.relativize(file)));
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException e) {
        throw new GenericException("Error while copying one directory into another", e);
      }
    } else {
      try {

        CopyOption[] copyOptions = replaceExisting ? new CopyOption[] {StandardCopyOption.REPLACE_EXISTING}
          : new CopyOption[] {};
        Files.copy(sourcePath, targetPath, copyOptions);
      } catch (IOException e) {
        throw new GenericException("Error while copying one file into another", e);
      }

    }

  }

  /**
   * Deletes a directory/file
   * 
   * @param path
   *          path to the directory/file that will be deleted. in case of a
   *          directory, if not empty, everything in it will be deleted as well.
   *          in case of a file, if metadata associated to it exists, it will be
   *          deleted as well.
   * @throws NotFoundException
   * @throws GenericException
   */
  public static void deletePath(Path path) throws NotFoundException, GenericException {
    if (path == null) {
      return;
    }

    try {
      Files.delete(path);

    } catch (NoSuchFileException e) {
      throw new NotFoundException("Could not delete path", e);
    } catch (DirectoryNotEmptyException e) {
      try {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }

        });
      } catch (IOException e1) {
        throw new GenericException("Could not delete entity", e1);
      }
    } catch (IOException e) {
      throw new GenericException("Could not delete entity", e);
    }
  }

  /**
   * Get path
   * 
   * @param basePath
   *          base path
   * @param storagePath
   *          storage path, related to base path, that one wants to resolve
   */
  public static Path getEntityPath(Path basePath, StoragePath storagePath) {
    Path resourcePath = basePath.resolve(storagePath.asString());
    return resourcePath;
  }

  /**
   * List content of the certain folder
   * 
   * @param basePath
   *          base path
   * @param path
   *          relative path to base path
   * @throws NotFoundException
   * @throws GenericException
   */
  public static CloseableIterable<Resource> listPath(final Path basePath, final Path path)
    throws NotFoundException, GenericException {
    CloseableIterable<Resource> resourceIterable;
    try {
      final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path);
      final Iterator<Path> pathIterator = directoryStream.iterator();
      resourceIterable = new CloseableIterable<Resource>() {

        @Override
        public Iterator<Resource> iterator() {
          return new Iterator<Resource>() {

            @Override
            public boolean hasNext() {
              return pathIterator.hasNext();
            }

            @Override
            public Resource next() {
              Path next = pathIterator.next();
              Resource ret;
              try {
                ret = convertPathToResource(basePath, next);
              } catch (GenericException | NotFoundException | RequestNotValidException e) {
                LOGGER.error("Error while list path " + basePath + " while parsing resource " + next, e);
                ret = null;
              }

              return ret;
            }

          };
        }

        @Override
        public void close() throws IOException {
          directoryStream.close();
        }
      };

    } catch (NoSuchFileException e) {
      throw new NotFoundException("Could not list contents of entity because it doesn't exist: " + path, e);
    } catch (IOException e) {
      throw new GenericException("Could not list contents of entity at: " + path, e);
    }

    return resourceIterable;
  }

  public static Long countPath(Path basePath, Path directoryPath) throws NotFoundException, GenericException {
    Long count = 0L;
    DirectoryStream<Path> directoryStream = null;
    try {
      directoryStream = Files.newDirectoryStream(directoryPath);

      final Iterator<Path> pathIterator = directoryStream.iterator();
      while (pathIterator.hasNext()) {
        count++;
        pathIterator.next();
      }

    } catch (NoSuchFileException e) {
      throw new NotFoundException("Could not list contents of entity because it doesn't exist: " + directoryPath);
    } catch (IOException e) {
      throw new GenericException("Could not list contents of entity at: " + directoryPath, e);
    } finally {
      IOUtils.closeQuietly(directoryStream);
    }

    return count;
  }

  public static Long recursivelyCountPath(Path basePath, Path directoryPath)
    throws NotFoundException, GenericException {
    Long count = 0L;
    Stream<Path> walk = null;
    try {
      walk = Files.walk(directoryPath);

      final Iterator<Path> pathIterator = walk.iterator();
      while (pathIterator.hasNext()) {
        count++;
        pathIterator.next();
      }
    } catch (NoSuchFileException e) {
      throw new NotFoundException("Could not list contents of entity because it doesn't exist: " + directoryPath);
    } catch (IOException e) {
      throw new GenericException("Could not list contents of entity at: " + directoryPath, e);
    } finally {
      if (walk != null) {
        walk.close();
      }
    }

    return count;
  }

  public static CloseableIterable<Resource> recursivelyListPath(final Path basePath, final Path path)
    throws NotFoundException, GenericException {
    CloseableIterable<Resource> resourceIterable;
    try {
      final Stream<Path> walk = Files.walk(path, FileVisitOption.FOLLOW_LINKS);
      final Iterator<Path> pathIterator = walk.iterator();

      // skip root
      if (pathIterator.hasNext()) {
        pathIterator.next();
      }

      resourceIterable = new CloseableIterable<Resource>() {

        @Override
        public Iterator<Resource> iterator() {
          return new Iterator<Resource>() {

            @Override
            public boolean hasNext() {
              return pathIterator.hasNext();
            }

            @Override
            public Resource next() {
              Path next = pathIterator.next();
              Resource ret;
              try {
                ret = convertPathToResource(basePath, next);
              } catch (GenericException | NotFoundException | RequestNotValidException e) {
                LOGGER.error("Error while list path " + basePath + " while parsing resource " + next, e);
                ret = null;
              }

              return ret;
            }

          };
        }

        @Override
        public void close() throws IOException {
          walk.close();
        }
      };

    } catch (NoSuchFileException e) {
      throw new NotFoundException("Could not list contents of entity because it doesn't exist: " + path, e);
    } catch (IOException e) {
      throw new GenericException("Could not list contents of entity at: " + path, e);
    }

    return resourceIterable;
  }

  /**
   * List containers
   * 
   * @param basePath
   *          base path
   * @throws GenericException
   */
  public static CloseableIterable<Container> listContainers(final Path basePath) throws GenericException {
    CloseableIterable<Container> containerIterable;
    try {
      final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(basePath);
      final Iterator<Path> pathIterator = directoryStream.iterator();
      containerIterable = new CloseableIterable<Container>() {

        @Override
        public Iterator<Container> iterator() {
          return new Iterator<Container>() {

            @Override
            public boolean hasNext() {
              return pathIterator.hasNext();
            }

            @Override
            public Container next() {
              Path next = pathIterator.next();
              Container ret;
              try {
                ret = convertPathToContainer(basePath, next);
              } catch (NoSuchElementException | GenericException | RequestNotValidException e) {
                LOGGER.error("Error while listing containers, while parsing resource " + next, e);
                ret = null;
              }

              return ret;
            }

          };
        }

        @Override
        public void close() throws IOException {
          directoryStream.close();
        }
      };

    } catch (IOException e) {
      throw new GenericException("Could not list contents of entity at: " + basePath, e);
    }

    return containerIterable;
  }

  /**
   * Converts a path into a resource
   * 
   * @param basePath
   *          base path
   * @param path
   *          relative path to base path
   * @throws RequestNotValidException
   * @throws NotFoundException
   * @throws GenericException
   */
  public static Resource convertPathToResource(Path basePath, Path path)
    throws RequestNotValidException, NotFoundException, GenericException {
    Resource resource;

    // TODO support binary reference

    if (!Files.exists(path)) {
      throw new NotFoundException("Cannot find file or directory at " + path);
    }

    // storage path
    Path relativePath = basePath.relativize(path);
    StoragePath storagePath = DefaultStoragePath.parse(relativePath.toString());

    // construct
    if (Files.isDirectory(path)) {
      resource = new DefaultDirectory(storagePath);
    } else {
      ContentPayload content = new FSPathContentPayload(path);
      long sizeInBytes;
      try {
        sizeInBytes = Files.size(path);
        Map<String, String> contentDigest = null;
        resource = new DefaultBinary(storagePath, content, sizeInBytes, false, contentDigest);
      } catch (IOException e) {
        throw new GenericException("Could not get file size", e);
      }
    }
    return resource;
  }

  /**
   * Converts a path into a container
   * 
   * @param basePath
   *          base path
   * @param path
   *          relative path to base path
   * @throws GenericException
   * @throws RequestNotValidException
   */
  public static Container convertPathToContainer(Path basePath, Path path)
    throws GenericException, RequestNotValidException {
    Container resource;

    // storage path
    Path relativePath = basePath.relativize(path);
    StoragePath storagePath = DefaultStoragePath.parse(relativePath.toString());

    // construct
    if (Files.isDirectory(path)) {
      resource = new DefaultContainer(storagePath);
    } else {
      throw new GenericException("A file is not a container!");
    }
    return resource;
  }

  public static String computeContentDigest(Path path, String algorithm) throws GenericException {
    FileChannel fc = null;
    try {
      final int bufferSize = 1073741824;
      fc = FileChannel.open(path);
      final long size = fc.size();
      final MessageDigest hash = MessageDigest.getInstance(algorithm);
      long position = 0;
      while (position < size) {
        final MappedByteBuffer data = fc.map(FileChannel.MapMode.READ_ONLY, 0, Math.min(size, bufferSize));
        if (!data.isLoaded()) {
          data.load();
        }
        hash.update(data);
        position += data.limit();
        if (position >= size) {
          break;
        }
      }

      byte[] mdbytes = hash.digest();
      StringBuilder hexString = new StringBuilder();

      for (int i = 0; i < mdbytes.length; i++) {
        String hexInt = Integer.toHexString((0xFF & mdbytes[i]));
        if (hexInt.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hexInt);
      }

      return hexString.toString();

    } catch (NoSuchAlgorithmException | IOException e) {
      throw new GenericException("Cannot compute content digest for " + path + " using algorithm " + algorithm);
    } finally {
      if (fc != null) {
        try {
          fc.close();
        } catch (IOException e) {
          LOGGER.warn("Cannot close file channel", e);
        }
      }
    }
  }

  /**
   * Method for computing one or more file content digests (a.k.a. hash's)
   * 
   * @param path
   *          file which digests will be computed
   * @throws GenericException
   */
  public static Map<String, String> generateContentDigest(Path path, String... algorithms) throws GenericException {
    Map<String, String> digests = new HashMap<String, String>();

    for (String algorithm : algorithms) {
      String digest = computeContentDigest(path, algorithm);
      digests.put(algorithm, digest);
    }

    return digests;
  }

  public static Path createRandomDirectory(Path parent) throws IOException {
    Path directory;
    do {
      try {
        directory = Files.createDirectory(parent.resolve(UUID.randomUUID().toString()));
      } catch (FileAlreadyExistsException e) {
        LOGGER.warn("Got colision when creating random directory", e);
        directory = null;
      }
    } while (directory == null);

    return directory;
  }

  public static Path createRandomFile(Path parent) throws IOException {
    Path file;
    do {
      try {
        file = Files.createFile(parent.resolve(UUID.randomUUID().toString()));
      } catch (FileAlreadyExistsException e) {
        LOGGER.warn("Got colision when creating random directory", e);
        file = null;
      }
    } while (file == null);

    return file;
  }

}
