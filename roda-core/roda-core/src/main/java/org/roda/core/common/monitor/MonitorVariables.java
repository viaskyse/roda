/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.common.monitor;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorVariables {
  private static final Logger LOGGER = LoggerFactory.getLogger(MonitorVariables.class);
  private WatchService watcher;
  private Map<WatchKey, Path> keys;
  private TaskBlocker taskBlocker;

  private static MonitorVariables instance = null;

  public MonitorVariables() {
    try {
      this.watcher = FileSystems.getDefault().newWatchService();
      this.keys = new HashMap<WatchKey, Path>();
      this.taskBlocker = new TaskBlocker() {
        final Lock lock = new ReentrantLock();

        @Override
        public void acquire() {
          lock.lock();
        }

        @Override
        public void release() {
          lock.unlock();
        }
      };
    } catch (IOException e) {
      LOGGER.error("Error initializing watcher: " + watcher);
    }
  }

  public TaskBlocker getTaskBlocker() {
    return taskBlocker;
  }

  public static synchronized MonitorVariables getInstance() {
    if (instance == null) {
      instance = new MonitorVariables();
    }
    return instance;
  }

  public WatchService getWatcher() {
    return watcher;
  }

  public Map<WatchKey, Path> getKeys() {
    return keys;
  }

  public void register(Path directoryPath) throws IOException {
    WatchKey key = directoryPath.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
    keys.put(key, directoryPath);
  }

  public void registerAll(final Path start) throws IOException {
    EnumSet<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
    Files.walkFileTree(start, opts, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        register(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}