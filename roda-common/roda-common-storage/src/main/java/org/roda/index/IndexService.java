package org.roda.index;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.roda.index.utils.SolrUtils;
import org.roda.model.AIP;
import org.roda.model.ModelService;
import org.roda.model.ModelServiceException;
import org.roda.model.utils.ModelUtils;
import org.roda.storage.Binary;
import org.roda.storage.ClosableIterable;
import org.roda.storage.DefaultStoragePath;
import org.roda.storage.Resource;
import org.roda.storage.StorageActionException;

import pt.gov.dgarq.roda.core.common.RodaConstants;
import pt.gov.dgarq.roda.core.data.adapter.filter.Filter;
import pt.gov.dgarq.roda.core.data.adapter.sort.Sorter;
import pt.gov.dgarq.roda.core.data.adapter.sublist.Sublist;
import pt.gov.dgarq.roda.core.data.v2.IndexResult;
import pt.gov.dgarq.roda.core.data.v2.LogEntry;
import pt.gov.dgarq.roda.core.data.v2.Representation;
import pt.gov.dgarq.roda.core.data.v2.SimpleDescriptionObject;
import pt.gov.dgarq.roda.core.data.v2.SimpleEventPreservationMetadata;
import pt.gov.dgarq.roda.core.data.v2.SimpleRepresentationFilePreservationMetadata;
import pt.gov.dgarq.roda.core.data.v2.SimpleRepresentationPreservationMetadata;

public class IndexService {

	private final Logger logger = Logger.getLogger(getClass());

	private final SolrClient index;
	private final ModelService model;
	private final IndexModelObserver observer;

	public IndexService(SolrClient index, ModelService model) {
		super();
		this.index = index;
		this.model = model;

		observer = new IndexModelObserver(index, model);
		model.addModelObserver(observer);
	}

	public AIP retrieveAIP(String aipId) throws IndexActionException {
		return SolrUtils.retrieve(index, AIP.class, aipId);
	}

	public IndexResult<AIP> findAIP(Filter filter, Sorter sorter, Sublist sublist) throws IndexActionException {
		return SolrUtils.find(index, AIP.class, filter, sorter, sublist);
	}

	public Long countAIP(Filter filter) throws IndexActionException {
		return SolrUtils.count(index, AIP.class, filter);
	}

	public IndexResult<SimpleDescriptionObject> findDescriptiveMetadata(Filter filter, Sorter sorter, Sublist sublist)
			throws IndexActionException {
		return SolrUtils.find(index, SimpleDescriptionObject.class, filter, sorter, sublist);
	}

	public Long countDescriptiveMetadata(Filter filter) throws IndexActionException {
		return SolrUtils.count(index, SimpleDescriptionObject.class, filter);
	}

	public SimpleDescriptionObject retrieveDescriptiveMetadata(String aipId) throws IndexActionException {
		// TODO check if return type shouldn't be updated to
		// SimpleDescriptiveMetadata
		return SolrUtils.retrieve(index, SimpleDescriptionObject.class, aipId);
	}

	public Representation retrieveRepresentation(String aipId, String repId) throws IndexActionException {
		return SolrUtils.retrieve(index, Representation.class, aipId, repId);
	}

	public IndexResult<Representation> findRepresentation(Filter filter, Sorter sorter, Sublist sublist)
			throws IndexActionException {
		return SolrUtils.find(index, Representation.class, filter, sorter, sublist);
	}

	public Long countRepresentations(Filter filter) throws IndexActionException {
		return SolrUtils.count(index, Representation.class, filter);
	}

	public String getParentId(String id) throws IndexActionException {
		return retrieveAIP(id).getParentId();
	}

	public SimpleDescriptionObject getParent(String id) throws IndexActionException {
		return SolrUtils.retrieve(index, SimpleDescriptionObject.class, getParentId(id));
	}

	public SimpleDescriptionObject getParent(SimpleDescriptionObject sdo) throws IndexActionException {
		return SolrUtils.retrieve(index, SimpleDescriptionObject.class, sdo.getParentID());
	}

	public List<SimpleDescriptionObject> getAncestors(SimpleDescriptionObject sdo) throws IndexActionException {
		List<SimpleDescriptionObject> ancestors = new ArrayList<SimpleDescriptionObject>();
		SimpleDescriptionObject parent = null, actual = sdo;

		while (actual != null && actual.getParentID() != null) {
			parent = getParent(actual);
			if (parent != null) {
				ancestors.add(parent);
				actual = parent;
			}
		}

		return ancestors;
	}

	public List<SimpleDescriptionObject> getAncestors(String id) throws IndexActionException {
		List<SimpleDescriptionObject> ancestors = new ArrayList<SimpleDescriptionObject>();
		SimpleDescriptionObject parent = null;
		String currId = id;

		do {
			parent = getParent(currId);
			if (parent != null) {
				ancestors.add(parent);
				currId = parent.getId();
			}
		} while (parent != null && parent.getParentID() != null);

		return ancestors;
	}

	// TODO:
	public SimpleRepresentationPreservationMetadata retrieveSimpleRepresentationPreservationMetadata(String aipId,
			String representationId, String fileId) throws IndexActionException {
		return SolrUtils.retrieve(index, SimpleRepresentationPreservationMetadata.class, aipId, representationId,
				fileId);
	}

	// public Long countSimpleRepresentationPreservationMetadata(Filter filter)
	// throws IndexActionException {
	// return SolrUtils.count(index,
	// SimpleRepresentationPreservationMetadata.class, filter);
	// }
	// public IndexResult<SimpleRepresentationPreservationMetadata>
	// findRepresentationPreservationMetadata(Filter filter, Sorter sorter,
	// Sublist sublist)
	// throws IndexActionException {
	// return SolrUtils.find(index,
	// SimpleRepresentationPreservationMetadata.class, filter, sorter, sublist);
	// }
	// /*
	// public IndexResult<SimpleRepresentationPreservationObject>
	// findRepresentationPreservationObject(ContentAdapter contentAdapter){
	// return null;
	// }
	// */
	//
	public SimpleEventPreservationMetadata retrieveSimpleEventPreservationMetadata(String aipId,
			String representationId, String fileId) throws IndexActionException {
		return SolrUtils.retrieve(index, SimpleEventPreservationMetadata.class, aipId, representationId, fileId);
	}

	public Long countSimpleEventPreservationMetadata(Filter filter) throws IndexActionException {
		return SolrUtils.count(index, SimpleEventPreservationMetadata.class, filter);
	}

	public IndexResult<SimpleEventPreservationMetadata> findSimpleEventPreservationMetadata(Filter filter,
			Sorter sorter, Sublist sublist) throws IndexActionException {
		return SolrUtils.find(index, SimpleEventPreservationMetadata.class, filter, sorter, sublist);
	}

	public SimpleRepresentationFilePreservationMetadata retrieveSimpleRepresentationFilePreservationMetadata(
			String aipId, String representationId, String fileId) throws IndexActionException {
		return SolrUtils.retrieve(index, SimpleRepresentationFilePreservationMetadata.class, aipId, representationId,
				fileId);
	}

	public Long countSimpleRepresentationFilePreservationMetadata(Filter filter) throws IndexActionException {
		return SolrUtils.count(index, SimpleRepresentationFilePreservationMetadata.class, filter);
	}

	public IndexResult<SimpleRepresentationFilePreservationMetadata> findSimpleRepresentationFilePreservationMetadata(
			Filter filter, Sorter sorter, Sublist sublist) throws IndexActionException {
		return SolrUtils.find(index, SimpleRepresentationFilePreservationMetadata.class, filter, sorter, sublist);
	}

	/*
	 * public IndexResult<SimpleRepresentationPreservationObject>
	 * findRepresentationPreservationObject(ContentAdapter contentAdapter){
	 * return null; }
	 */

	// LOG
	public Long getLogEntriesCount(Filter filter) throws IndexActionException {
		return SolrUtils.count(index, LogEntry.class, filter);
	}

	public IndexResult<LogEntry> findLogEntry(Filter filter, Sorter sorter, Sublist sublist)
			throws IndexActionException {
		return SolrUtils.find(index, LogEntry.class, filter, sorter, sublist);
	}

	// FIXME perhaps transform sysout into logger logging
	public void reindexAIPs() throws IndexActionException {
		ClosableIterable<AIP> aips = null;
		try {
			System.out.println(new Date().getTime() + " > Listing AIPs");
			aips = model.listAIPs();
			for (AIP aip : aips) {
				if (aip != null) {
					System.out.println(new Date().getTime() + " > Reindexing AIP " + aip.getId());
					reindexAIP(aip);
				} else {
					System.err.println(new Date().getTime() + " > An error occurred. See log for more details.");
				}
			}
			System.out.println(new Date().getTime() + " > Optimizing indexes");
			optimizeAIPs();
			System.out.println(new Date().getTime() + " > Done");
		} catch (ModelServiceException e) {
			throw new IndexActionException("Error while reindexing AIPs", IndexActionException.INTERNAL_SERVER_ERROR,
					e);
		} finally {
			try {
				if (aips != null) {
					aips.close();
				}
			} catch (IOException e) {
				logger.error("Error while while freeing up resources", e);
			}
		}
	}

	public void optimizeAIPs() throws IndexActionException {
		try {
			index.optimize(RodaConstants.INDEX_AIP);
			index.optimize(RodaConstants.INDEX_SDO);
		} catch (SolrServerException | IOException e) {
			throw new IndexActionException("Error while optimizing indexes", IndexActionException.INTERNAL_SERVER_ERROR,
					e);
		}
	}

	private void reindexAIP(AIP aip) {
		observer.aipCreated(aip);
	}

	// FIXME analyze method & verify if it is correct/the best way to do it
	public void reindexActionLogs() throws StorageActionException, ModelServiceException {
		ClosableIterable<Resource> actionLogs = model.getStorage()
				.listResourcesUnderContainer(DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_ACTIONLOG));
		try {
			Iterator<Resource> it = actionLogs.iterator();
			while (it.hasNext()) {
				Resource r = it.next();
				try {
					Binary b = model.getStorage().getBinary(r.getStoragePath());
					java.io.File f = new java.io.File(b.getContent().getURI().getPath());
					try (BufferedReader br = new BufferedReader(new FileReader(f))) {
						String line;
						while ((line = br.readLine()) != null) {
							LogEntry entry = ModelUtils.getLogEntry(line);
							reindexActionLog(entry);
						}
					}
				} catch (IOException e) {
					throw new ModelServiceException("Error parsing log file",
							ModelServiceException.INTERNAL_SERVER_ERROR, e);
				}
			}
		} finally {
			if (actionLogs != null) {
				try {
					actionLogs.close();
				} catch (IOException e) {
					logger.error("Error while while freeing up resources", e);
				}
			}
		}
	}

	private void reindexActionLog(LogEntry entry) {
		observer.logEntryCreated(entry);
	}
}