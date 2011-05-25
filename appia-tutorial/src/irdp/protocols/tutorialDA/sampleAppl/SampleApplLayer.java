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

package irdp.protocols.tutorialDA.sampleAppl;

import irdp.protocols.tutorialDA.delay.DelayEvent;
import irdp.protocols.tutorialDA.events.BlockEvent;
import irdp.protocols.tutorialDA.events.BlockOkEvent;
import irdp.protocols.tutorialDA.events.ConsensusDecide;
import irdp.protocols.tutorialDA.events.ConsensusPropose;
import irdp.protocols.tutorialDA.events.NBACDecide;
import irdp.protocols.tutorialDA.events.NBACPropose;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.events.ReleaseEvent;
import irdp.protocols.tutorialDA.events.SampleSendableEvent;
import irdp.protocols.tutorialDA.events.SharedRead;
import irdp.protocols.tutorialDA.events.SharedReadReturn;
import irdp.protocols.tutorialDA.events.SharedWrite;
import irdp.protocols.tutorialDA.events.SharedWriteReturn;
import irdp.protocols.tutorialDA.events.ViewEvent;
import irdp.protocols.tutorialDA.tcpBasedPFD.PFDStartEvent;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelClose;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;

/**
 * Layer of the application protocol.
 * 
 * @author nuno
 */
public class SampleApplLayer extends Layer {

  public SampleApplLayer() {
    /* events that the protocol will create */
    evProvide = new Class[10];
    evProvide[0] = ProcessInitEvent.class;
    evProvide[1] = RegisterSocketEvent.class;
    evProvide[2] = PFDStartEvent.class;
    evProvide[3] = SampleSendableEvent.class;
    evProvide[4] = ConsensusPropose.class;
    evProvide[5] = NBACPropose.class;
    evProvide[6] = BlockOkEvent.class;
    evProvide[7] = DelayEvent.class;
    evProvide[8] = SharedRead.class;
    evProvide[9] = SharedWrite.class;

    /*
     * events that the protocol require to work. This is a subset of the
     * accepted events
     */
    evRequire = new Class[1];
    evRequire[0] = ChannelInit.class;

    /* events that the protocol will accept */
    evAccept = new Class[11];
    evAccept[0] = ChannelInit.class;
    evAccept[1] = ChannelClose.class;
    evAccept[2] = RegisterSocketEvent.class;
    evAccept[3] = SampleSendableEvent.class;
    evAccept[4] = ConsensusDecide.class;
    evAccept[5] = NBACDecide.class;
    evAccept[6] = ViewEvent.class;
    evAccept[7] = BlockEvent.class;
    evAccept[8] = ReleaseEvent.class;
    evAccept[9] = SharedReadReturn.class;
    evAccept[10] = SharedWriteReturn.class;
  }

  /**
   * Creates a new session to this protocol.
   * 
   * @see appia.Layer#createSession()
   */
  public Session createSession() {
    return new SampleApplSession(this);
  }

}
