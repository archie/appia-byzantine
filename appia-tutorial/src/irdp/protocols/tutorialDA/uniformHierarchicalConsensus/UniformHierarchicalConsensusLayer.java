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

package irdp.protocols.tutorialDA.uniformHierarchicalConsensus;

import irdp.protocols.tutorialDA.events.ConsensusDecide;
import irdp.protocols.tutorialDA.events.ConsensusPropose;
import irdp.protocols.tutorialDA.events.Crash;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.tcpBasedPFD.PFDStartEvent;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;

/**
 * The Uniform Hierarchical Consensus algorithm.
 * 
 * @author alexp
 */
public class UniformHierarchicalConsensusLayer extends Layer {

  public UniformHierarchicalConsensusLayer() {

    evProvide = new Class[7];
    evProvide[0] = ConsensusDecide.class;
    evProvide[1] = ProposeEvent.class;
    evProvide[2] = DecidedEvent.class;
    // To validate Reliable Broadcast channel is required a layer that provides
    // these events.
    // The events are sent in the BeB channel by the SampleAppl layer.
    evProvide[3] = RegisterSocketEvent.class;
    evProvide[4] = ProcessInitEvent.class;
    evProvide[5] = PFDStartEvent.class;
    evProvide[6] = ConsensusPropose.class;

    evRequire = new Class[3];
    evRequire[0] = ProcessInitEvent.class;
    evRequire[1] = Crash.class;
    evRequire[2] = ConsensusPropose.class;

    evAccept = new Class[6];
    evAccept[0] = ProcessInitEvent.class;
    evAccept[1] = Crash.class;
    evAccept[2] = ConsensusPropose.class;
    evAccept[3] = ProposeEvent.class;
    evAccept[4] = DecidedEvent.class;
    evAccept[5] = ChannelInit.class;
  }

  /**
   * @see appia.Layer#createSession()
   */
  public Session createSession() {
    return new UniformHierarchicalConsensusSession(this);
  }
}
