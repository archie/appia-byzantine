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

import irdp.protocols.tutorialDA.consensusTRB.TRBEvent;
import irdp.protocols.tutorialDA.events.BlockEvent;
import irdp.protocols.tutorialDA.events.BlockOkEvent;
import irdp.protocols.tutorialDA.events.ViewEvent;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.SendableEvent;

/**
 * The TerminatingReliableBroadcast-based View Synchrony algorithm.
 * 
 * @author alexp
 */
public class TRBViewSyncLayer extends Layer {

  public TRBViewSyncLayer() {

    evProvide = new Class[2];
    evProvide[0] = BlockEvent.class;
    evProvide[1] = TRBEvent.class;

    evRequire = new Class[2];
    evRequire[0] = ViewEvent.class;
    evRequire[1] = BlockOkEvent.class;

    evAccept = new Class[4];
    evAccept[0] = ViewEvent.class;
    evAccept[1] = BlockOkEvent.class;
    evAccept[2] = TRBEvent.class;
    evAccept[3] = SendableEvent.class;
  }

  /**
   * @see appia.Layer#createSession()
   */
  public Session createSession() {
    return new TRBViewSyncSession(this);
  }
}
