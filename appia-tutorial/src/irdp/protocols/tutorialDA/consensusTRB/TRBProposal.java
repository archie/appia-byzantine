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

package irdp.protocols.tutorialDA.consensusTRB;

import irdp.protocols.tutorialDA.consensusUtils.Proposal;

/**
 * Used to encapsulate a message for consensus.
 * 
 * @author alexp
 */
public class TRBProposal extends Proposal {
  private static final long serialVersionUID = 7315948574282563060L;

  /**
   * If true, it proposes a failure.
   */
  public boolean failed = false;
  /**
   * Message to propose for consensus.
   */
  public Object m = null;

  public TRBProposal(Object m) {
    this.m = m;
  }

  public TRBProposal(boolean f) {
    failed = f;
  }

  /**
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(Proposal o) {
    if (!(o instanceof TRBProposal))
      throw new ClassCastException("Required TRBProposal");
    TRBProposal p = (TRBProposal) o;
    if (p.failed == failed)
      return 0;
    if (p.failed)
      return -1;
    else
      return 1;
  }

  /**
   * @see java.lang.Object#equals(java.lang.Object)
   */
  public boolean equals(Object obj) {
    if (!(obj instanceof TRBProposal))
      return false;
    TRBProposal p = (TRBProposal) obj;

    return compareTo(p) == 0;
  }

  /**
   * @see java.lang.Object#hashCode()
   */
  public int hashCode() {
    if (failed)
      return 0;
    else
      return 1;
    // return m.hashCode();
  }

  /**
   * @see java.lang.Object#toString()
   */
  public String toString() {
    return "[TRBProposal:" + failed + "]";
  }
}
