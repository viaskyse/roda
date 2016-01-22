/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.data.v2.ip;

import java.util.Date;

/**
 * This is a agent preservation object.
 * 
 * @author Rui Castro
 */
public class AgentPreservationObject extends PreservationObject {

  public static final String PRESERVATION_AGENT_TYPE_INGEST_TASK = "software:ingest_task";
  public static final String PRESERVATION_AGENT_TYPE_MIGRATOR = "software:migrator";
  public static final String PRESERVATION_AGENT_TYPE_DIGITALIZATION = "software:digitalization";
  public static final String PRESERVATION_AGENT_TYPE_FIXITY_CHECK_PLUGIN = "software:plugin:fixity_check";
  public static final String PRESERVATION_AGENT_TYPE_CHARACTERIZATION_PLUGIN = "software:plugin:characterization";
  public static final String PRESERVATION_AGENT_TYPE_VIRUS_CHECK_PLUGIN = "software:plugin:virus_check";
  public static final String PRESERVATION_AGENT_TYPE_VERAPDF_CHECK_PLUGIN = "software:plugin:verapdf_check";
  public static final String PRESERVATION_AGENT_TYPE_PDFTOPDFA_CONVERSION_PLUGIN = "software:plugin:pdftopdfa_conversion";
  public static final String PRESERVATION_AGENT_TYPE_UNKNOWN_PREFIX = "unknown:";

  public static final String[] PRESERVATION_AGENT_TYPES = new String[] {PRESERVATION_AGENT_TYPE_INGEST_TASK,
    PRESERVATION_AGENT_TYPE_MIGRATOR, PRESERVATION_AGENT_TYPE_DIGITALIZATION,
    PRESERVATION_AGENT_TYPE_FIXITY_CHECK_PLUGIN, PRESERVATION_AGENT_TYPE_VERAPDF_CHECK_PLUGIN,
    PRESERVATION_AGENT_TYPE_PDFTOPDFA_CONVERSION_PLUGIN, PRESERVATION_AGENT_TYPE_VIRUS_CHECK_PLUGIN};

  /**
   * Preservation Object type - Agent
   */
  public static final String TYPE = "agent";

  private String agentType = null;

  /**
   * Constructs an empty {@link AgentPreservationObject}.
   */
  public AgentPreservationObject() {
    setType(TYPE);
  }

  /**
   * @param agent
   */
  public AgentPreservationObject(AgentPreservationObject agent) {
    this(agent.getId(), agent.getLabel(), agent.getLastModifiedDate(), agent.getCreatedDate(), agent.getState());

    setType(agent.getType());
    setId(agent.getId());
    setAgentType(agent.getAgentType());
  }

  /**
   * @param id
   * @param label
   * @param model
   * @param lastModifiedDate
   * @param createdDate
   * @param state
   */
  public AgentPreservationObject(String id, String label, Date lastModifiedDate, Date createdDate, String state) {
    super(id, label, lastModifiedDate, createdDate, state);
  }

  /**
   * @see PreservationObject#toString()
   */
  @Override
  public String toString() {
    return "AgentPreservationObject(" + super.toString() + ", agentType=" //$NON-NLS-1$ //$NON-NLS-2$
      + getAgentType() + ", agentName=" + getAgentName() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
  }

  /**
   * @return the agentName
   */
  public String getAgentName() {
    return getLabel();
  }

  /**
   * @param agentName
   *          the agentName to set
   */
  public void setAgentName(String agentName) {
    setLabel(agentName);
  }

  /**
   * @return the agentType
   */
  public String getAgentType() {
    return agentType;
  }

  /**
   * @param agentType
   *          the agentType to set
   */
  public void setAgentType(String agentType) {
    this.agentType = agentType;
  }

}