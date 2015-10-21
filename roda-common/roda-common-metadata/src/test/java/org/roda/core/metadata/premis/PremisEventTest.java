package org.roda.core.metadata.premis;

import java.util.Date;

import org.roda.core.data.v2.EventPreservationObject;

public class PremisEventTest {

  /**
   * @param args
   */
  public static void main(String[] args) {

    EventPreservationObject eventPO = new EventPreservationObject();
    // null, "roda:x", "roda:p:event", new Date(),
    // new Date(), RODAObject.STATE_ACTIVE);

    eventPO.setId("roda:ev");
    eventPO.setEventType(EventPreservationObject.PRESERVATION_EVENT_TYPE_INGESTION);
    eventPO.setAgentID("roda:p:agent:007");
    eventPO.setAgentRole(EventPreservationObject.PRESERVATION_EVENT_AGENT_ROLE_INGEST_TASK);
    eventPO.setDatetime(new Date());
    eventPO.setEventDetail("details");
    eventPO.setOutcome("OK");
    eventPO.setOutcomeDetailNote("detail notes");
    eventPO.setOutcomeDetailExtension("<pá putinha qu& pariu>");

    try {

      new PremisEventHelper(eventPO).saveToByteArray();

    } catch (PremisMetadataException e) {
      e.printStackTrace();
    }

  }

}