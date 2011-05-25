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

package irdp.protocols.tutorialDA.trbViewSync;

import irdp.protocols.tutorialDA.utils.SampleProcess;

import java.io.Serializable;


/**
 * Represents a message stored in TRB View Synchrony.
 * 
 * @author alexp
 */
public class Msg implements Serializable {
  private static final long serialVersionUID = 6884334770854014703L;

  /**
   * Sender process.
   */
  public SampleProcess src;
  /**
   * Message identification.
   */
  public int id;
  /**
   * Message data.
   */
  public byte[] data;
  /**
   * Event full name.
   */
  public String eventName;

  public Msg(SampleProcess src, int id, String eventName, byte[] data) {
    this.src = src;
    this.id = id;
    this.data = data;
    this.eventName = eventName;
  }

  /**
   * @see java.lang.Object#equals(java.lang.Object)
   */
  public boolean equals(Object obj) {
    if (!(obj instanceof Msg))
      return false;
    Msg msg = (Msg) obj;
    return src.getSocketAddress().equals(msg.src.getSocketAddress())
        && (id == msg.id);
  }
  /**
   * @see java.lang.Object#hashCode()
   */
  public int hashCode() {
    return src.getSocketAddress().hashCode() + id;
  }
  /**
   * @see java.lang.Object#toString()
   */
  public String toString() {
    return "[Msg:" + src.getSocketAddress() + ";" + id + ";(" 
    	+ data.length + ")]";
  }
}
