/*
 * Copyright (c) 2015-2016 Fraunhofer FOKUS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.project.openbaton.nubomedia.paas.model.persistence.openbaton;

import org.openbaton.catalogue.util.IdGenerator;

import javax.persistence.*;
import java.util.List;

/**
 * Created by maa on 21.10.15.
 */
@Entity
public class MediaServerGroup {

  @Id private String id;
  private String nsdID;
  private String nsrID;

  @ElementCollection(fetch = FetchType.EAGER)
  private List<String> floatingIPs;

  @ElementCollection(fetch = FetchType.EAGER)
  private List<String> hostnames;

  public MediaServerGroup() {}

  @PrePersist
  public void ensureId() {
    id = IdGenerator.createUUID();
  }

  public String getId() {
    return id;
  }

  public String getNsdID() {
    return nsdID;
  }

  public void setNsdID(String nsdID) {
    this.nsdID = nsdID;
  }

  public String getNsrID() {
    return nsrID;
  }

  public void setNsrID(String nsrID) {
    this.nsrID = nsrID;
  }

  public List<String> getFloatingIPs() {
    return floatingIPs;
  }

  public void setFloatingIPs(List<String> floatingIPs) {
    this.floatingIPs = floatingIPs;
  }

  public List<String> getHostnames() {
    return hostnames;
  }

  public void setHostnames(List<String> hostnames) {
    this.hostnames = hostnames;
  }

  @Override
  public String toString() {
    return "MediaServerGroup{"
        + "id='"
        + id
        + '\''
        + ", nsdID='"
        + nsdID
        + '\''
        + ", nsrID='"
        + nsrID
        + '\''
        + ", floatingIPs="
        + floatingIPs
        + ", hostnames="
        + hostnames
        + '}';
  }
}
