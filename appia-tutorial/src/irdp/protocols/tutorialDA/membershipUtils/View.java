/*
 *
 * Hands-On code of the book Introduction to Reliable Distributed Programming
 * by Christian Cachin, Rachid Guerraoui and Luis Rodrigues
 * Copyright (C) 2005-2011 Luis Rodrigues
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 *
 * Contact
 * 	Address:
 *		Rua Alves Redol 9, Office 605
 *		1000-029 Lisboa
 *		PORTUGAL
 * 	Email:
 * 		ler@ist.utl.pt
 * 	Web:
 *		http://homepages.gsd.inesc-id.pt/~ler/
 * 
 */

package irdp.protocols.tutorialDA.membershipUtils;

import irdp.protocols.tutorialDA.consensusUtils.Proposal;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;

/**
 * Defines a view used in membership. Extends Proposal so it can be used in
 * consensus.
 * 
 * @author alexp
 */
public class View extends Proposal {
  private static final long serialVersionUID = 357435326121352256L;

  /**
   * The view identifier.
   */
  public int id;
  /**
   * The view members.
   */
  public ProcessSet memb;

  /**
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(Proposal o) {
    if (!(o instanceof View))
      throw new ClassCastException("Required View");
    View v = (View) o;
    if (id > v.id)
      return -1;
    if (id < v.id)
      return 1;
    if (memb.getSize() < v.memb.getSize())
      return -1;
    if (memb.getSize() > v.memb.getSize())
      return 1;
    return 0;
  }

  /**
   * @see java.lang.Object#equals(java.lang.Object)
   */
  public boolean equals(Object obj) {
    if (!(obj instanceof View))
      return false;
    View v = (View) obj;
    if (id != v.id)
      return false;
    if (memb.getSize() != v.memb.getSize())
      return false;
    int i;
    for (i = 0; i < memb.getSize(); i++) {
      if (v.memb.getProcess(memb.getProcess(i).getSocketAddress()) == null)
        return false;
    }
    return true;
  }

  /**
   * @see java.lang.Object#hashCode()
   */
  public int hashCode() {
    int c = id;
    int i;
    for (i = 0; i < memb.getSize(); i++)
      c += memb.getProcess(i).getSocketAddress().hashCode();
    return c;
  }

  /**
   * @see java.lang.Object#toString()
   */
  public String toString() {
    String s = "[View:" + id + ";{";
    int i;
    for (i = 0; i < memb.getSize(); i++) {
      SampleProcess p = memb.getProcess(i);
      s += "(" + p.getProcessNumber() + ";" + p.getSocketAddress() + ";"
          + p.isCorrect() + "),";
    }
    s += "}]";
    return s;
  }
}
