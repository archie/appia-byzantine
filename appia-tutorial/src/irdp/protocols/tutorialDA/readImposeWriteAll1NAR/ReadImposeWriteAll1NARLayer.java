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

package irdp.protocols.tutorialDA.readImposeWriteAll1NAR;

import irdp.protocols.tutorialDA.events.Crash;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.events.SharedRead;
import irdp.protocols.tutorialDA.events.SharedReadReturn;
import irdp.protocols.tutorialDA.events.SharedWrite;
import irdp.protocols.tutorialDA.events.SharedWriteReturn;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;

/**
 * Layer of the Read-Impose Write-All (1,N) Atomic Registers protocol.
 * 
 * @author alexp
 */
public class ReadImposeWriteAll1NARLayer extends Layer {

  public ReadImposeWriteAll1NARLayer() {

    evProvide = new Class[6];
    evProvide[0] = SharedReadReturn.class;
    evProvide[1] = SharedWriteReturn.class;
    evProvide[2] = WriteEvent.class;
    evProvide[3] = AckEvent.class;
    // To validate PerfectPointToPoint channel is required a layer that provides
    // these events.
    // The events are sent in the BeB channel by the SampleAppl layer.
    evProvide[4] = RegisterSocketEvent.class;
    evProvide[5] = ProcessInitEvent.class;

    evRequire = new Class[0];

    evAccept = new Class[7];
    evAccept[0] = ProcessInitEvent.class;
    evAccept[1] = SharedRead.class;
    evAccept[2] = SharedWrite.class;
    evAccept[3] = WriteEvent.class;
    evAccept[4] = AckEvent.class;
    evAccept[5] = ChannelInit.class;
    evAccept[6] = Crash.class;
  }

  /**
   * @see appia.Layer#createSession()
   */
  public Session createSession() {
    return new ReadImposeWriteAll1NARSession(this);
  }
}
