/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.roda.core.common.ReportAssertUtils;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.filter.SimpleFilterParameter;
import org.roda.core.data.adapter.sort.Sorter;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.IsRODAObject;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.index.SelectedItems;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.jobs.Job.JOB_STATE;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.index.IndexService;
import org.roda.core.plugins.Plugin;
import org.testng.AssertJUnit;

public final class TestsHelper {
  private TestsHelper() {

  }

  public static Path createBaseTempDir(Class<?> testClass, boolean setAsRODAHome, FileAttribute<?>... attributes)
    throws IOException {
    Path baseTempDir = Files.createTempDirectory("_" + testClass.getSimpleName(), attributes);
    if (setAsRODAHome) {
      System.setProperty(RodaConstants.INSTALL_FOLDER_SYSTEM_PROPERTY, baseTempDir.toString());
    }
    return baseTempDir;
  }

  public static Path createBaseTempDir(Class<?> testClass, boolean setAsRODAHome) throws IOException {
    return createBaseTempDir(testClass, setAsRODAHome, new FileAttribute[] {});
  }

  public static <T extends IsRODAObject, T1 extends Plugin<T>> Job executeJob(Class<T1> plugin, PluginType pluginType,
    SelectedItems<T> selectedItems)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    return executeJob(plugin, new HashMap<>(), pluginType, selectedItems, JOB_STATE.COMPLETED);
  }

  /**
   * 20160818 hsilva: this method is only useful for testing Jobs (in particular
   * Job failures)
   */
  public static <T extends IsRODAObject, T1 extends Plugin<T>> Job executeJob(Class<T1> plugin, PluginType pluginType,
    SelectedItems<T> selectedItems, JOB_STATE expectedJobState)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    return executeJob(plugin, new HashMap<>(), pluginType, selectedItems, expectedJobState);
  }

  public static <T extends IsRODAObject, T1 extends Plugin<T>> Job executeJob(Class<T1> plugin,
    Map<String, String> pluginParameters, PluginType pluginType, SelectedItems<T> selectedItems)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    return executeJob(plugin, pluginParameters, pluginType, selectedItems, JOB_STATE.COMPLETED);
  }

  /**
   * 20160818 hsilva: this method is only useful for testing Jobs (in particular
   * Job failures)
   */
  public static <T extends IsRODAObject, T1 extends Plugin<T>> Job executeJob(Class<T1> plugin,
    Map<String, String> pluginParameters, PluginType pluginType, SelectedItems<T> selectedItems,
    JOB_STATE expectedJobState)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {

    Job job = new Job();
    job.setId(UUID.randomUUID().toString());
    job.setName(plugin.getName());
    job.setPlugin(plugin.getName());
    job.setPluginParameters(pluginParameters);
    job.setPluginType(pluginType);
    job.setSourceObjects(selectedItems);
    job.setUsername("admin");
    try {
      RodaCoreFactory.getModelService().createJob(job);
      RodaCoreFactory.getPluginOrchestrator().executeJob(job, false);
    } catch (Exception e) {
      AssertJUnit.fail("Unable to execute job in test mode: [" + e.getClass().getName() + "] " + e.getMessage());
    }

    Job jobUpdated = RodaCoreFactory.getModelService().retrieveJob(job.getId());
    MatcherAssert.assertThat(jobUpdated.getState(), Is.is(expectedJobState));
    return jobUpdated;

  }

  public static List<Report> getJobReports(IndexService index, Job job)
    throws GenericException, RequestNotValidException {
    return getJobReports(index, job, true);
  }

  public static List<Report> getJobReports(IndexService index, Job job, boolean failIfReportNotSucceeded)
    throws GenericException, RequestNotValidException {

    index.commit(Job.class, Report.class);

    Filter filter = new Filter(new SimpleFilterParameter(RodaConstants.JOB_REPORT_JOB_ID, job.getId()));

    Long counter = index.count(Report.class, filter);
    IndexResult<Report> indexReports = index.find(Report.class, filter, Sorter.NONE,
      new Sublist(0, counter.intValue()));

    List<Report> reports = indexReports.getResults();

    if (failIfReportNotSucceeded) {
      ReportAssertUtils.assertReports(reports);
    }

    return reports;
  }

}
