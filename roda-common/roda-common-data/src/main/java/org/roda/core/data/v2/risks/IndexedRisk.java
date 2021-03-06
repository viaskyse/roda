/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.data.v2.risks;

import org.roda.core.data.v2.index.IsIndexed;

public class IndexedRisk extends Risk implements IsIndexed {

  private static final long serialVersionUID = 2864416437668370485L;
  private int objectsSize = 0;

  public IndexedRisk() {
    super();
  }

  public IndexedRisk(IndexedRisk risk) {
    super(risk);
    this.objectsSize = risk.getObjectsSize();
  }

  public int getObjectsSize() {
    return objectsSize;
  }

  public void setObjectsSize(int objectsSize) {
    this.objectsSize = objectsSize;
  }

  @Override
  public String getUUID() {
    return getId();
  }

}
