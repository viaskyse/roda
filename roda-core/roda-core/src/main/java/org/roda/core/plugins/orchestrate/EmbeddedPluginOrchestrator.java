/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.orchestrate;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.roda.core.RodaCoreFactory;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.sort.Sorter;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.v2.IndexResult;
import org.roda.core.data.v2.Representation;
import org.roda.core.index.IndexService;
import org.roda.core.index.IndexServiceException;
import org.roda.core.model.AIP;
import org.roda.core.model.File;
import org.roda.core.model.ModelService;
import org.roda.core.model.ModelServiceException;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.PluginOrchestrator;
import org.roda.core.storage.ClosableIterable;
import org.roda.core.storage.StorageService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class EmbeddedPluginOrchestrator implements PluginOrchestrator {

  private static final int BLOCK_SIZE = 100;
  private static final Sorter SORTER = null;

  private static final int TIMEOUT = 1;
  private static final TimeUnit TIMEOUT_UNIT = TimeUnit.HOURS;

  private static final Logger LOGGER = Logger.getLogger(EmbeddedPluginOrchestrator.class);

  private final IndexService index;
  private final ModelService model;
  private final StorageService storage;

  private final ExecutorService executorService;

  public EmbeddedPluginOrchestrator() {
    index = RodaCoreFactory.getIndexService();
    model = RodaCoreFactory.getModelService();
    storage = RodaCoreFactory.getStorageService();

    final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("plugin-%d").setDaemon(true).build();
    int threads = Runtime.getRuntime().availableProcessors() + 1;
    executorService = Executors.newFixedThreadPool(threads, threadFactory);
    LOGGER.debug("Running embedded plugin orchestrator on a " + threads + " thread pool");

  }

  @Override
  public void setup() {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public <T extends Serializable> void runPluginFromIndex(Class<T> classToActOn, Filter filter, Plugin<T> plugin) {
    try {
      plugin.beforeExecute(index, model, storage);
      IndexResult<T> find;
      int offset = 0;
      do {
        // XXX block size could be recommended by plugin
        find = RodaCoreFactory.getIndexService().find(classToActOn, filter, SORTER, new Sublist(offset, BLOCK_SIZE));
        offset += find.getLimit();
        submitPlugin(find.getResults(), plugin);

      } while (find.getTotalCount() > find.getOffset() + find.getLimit());

      finishedSubmit();
      plugin.afterExecute(index, model, storage);

    } catch (IndexServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (PluginException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  private <T extends Serializable> void submitPlugin(List<T> list, Plugin<T> plugin) {
    executorService.submit(new Runnable() {

      @Override
      public void run() {
        try {
          plugin.init();
          plugin.execute(index, model, storage, list);
          plugin.shutdown();
        } catch (PluginException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    });
  }

  private boolean finishedSubmit() {
    executorService.shutdown();

    try {
      return executorService.awaitTermination(TIMEOUT, TIMEOUT_UNIT);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public void runPluginOnAIPs(Plugin<AIP> plugin, List<String> ids) {
    try {
      plugin.beforeExecute(index, model, storage);
      Iterator<String> iter = ids.iterator();
      String aipId;

      List<AIP> block = new ArrayList<AIP>();
      while (iter.hasNext()) {
        if (block.size() == BLOCK_SIZE) {
          submitPlugin(block, plugin);
          block = new ArrayList<AIP>();
        }

        aipId = iter.next();
        block.add(model.retrieveAIP(aipId));
      }

      if (!block.isEmpty()) {
        submitPlugin(block, plugin);
      }

      finishedSubmit();
      plugin.afterExecute(index, model, storage);

    } catch (ModelServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (PluginException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  @Override
  public void runPluginOnAllAIPs(Plugin<AIP> plugin) {
    try {
      plugin.beforeExecute(index, model, storage);
      ClosableIterable<AIP> aips = model.listAIPs();
      Iterator<AIP> iter = aips.iterator();

      List<AIP> block = new ArrayList<AIP>();
      while (iter.hasNext()) {
        if (block.size() == BLOCK_SIZE) {
          submitPlugin(block, plugin);
          block = new ArrayList<AIP>();
        }

        block.add(iter.next());
      }

      if (!block.isEmpty()) {
        submitPlugin(block, plugin);
      }

      aips.close();

      finishedSubmit();
      plugin.afterExecute(index, model, storage);

    } catch (ModelServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (PluginException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  @Override
  public void runPluginOnAllRepresentations(Plugin<Representation> plugin) {
    try {
      plugin.beforeExecute(index, model, storage);
      ClosableIterable<AIP> aips = model.listAIPs();
      Iterator<AIP> aipIter = aips.iterator();

      List<Representation> block = new ArrayList<Representation>();
      while (aipIter.hasNext()) {
        AIP aip = aipIter.next();
        ClosableIterable<Representation> reps = model.listRepresentations(aip.getId());
        Iterator<Representation> repIter = reps.iterator();

        while (repIter.hasNext()) {

          if (block.size() == BLOCK_SIZE) {
            submitPlugin(block, plugin);
            block = new ArrayList<Representation>();
          }

          block.add(repIter.next());
        }

        reps.close();
      }

      if (!block.isEmpty()) {
        submitPlugin(block, plugin);
      }

      aips.close();

      finishedSubmit();
      plugin.afterExecute(index, model, storage);

    } catch (ModelServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (PluginException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public void runPluginOnAllFiles(Plugin<File> plugin) {
    try {
      plugin.beforeExecute(index, model, storage);
      ClosableIterable<AIP> aips = model.listAIPs();
      Iterator<AIP> aipIter = aips.iterator();

      List<File> block = new ArrayList<File>();
      while (aipIter.hasNext()) {
        AIP aip = aipIter.next();
        ClosableIterable<Representation> reps = model.listRepresentations(aip.getId());
        Iterator<Representation> repIter = reps.iterator();

        while (repIter.hasNext()) {
          Representation rep = repIter.next();

          Iterable<File> files = model.listFiles(aip.getId(), rep.getId());
          Iterator<File> fileIter = files.iterator();

          while (fileIter.hasNext()) {
            if (block.size() == BLOCK_SIZE) {
              submitPlugin(block, plugin);
              block = new ArrayList<File>();
            }

            block.add(fileIter.next());
          }
        }

        reps.close();
      }

      if (!block.isEmpty()) {
        submitPlugin(block, plugin);
      }

      aips.close();

      finishedSubmit();
      plugin.afterExecute(index, model, storage);

    } catch (ModelServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (PluginException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public void runPluginOnFiles(Plugin<String> plugin, List<Path> paths) {
    try {
      plugin.beforeExecute(index, model, storage);

      List<String> block = new ArrayList<String>();
      for (Path path : paths) {
        if (block.size() == BLOCK_SIZE) {
          submitPlugin(block, plugin);
          block = new ArrayList<String>();
        }
        block.add(path.toString());
      }

      if (!block.isEmpty()) {
        submitPlugin(block, plugin);
      }

      finishedSubmit();
      plugin.afterExecute(index, model, storage);

    } catch (PluginException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

}