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

import irdp.protocols.tutorialDA.events.ConsensusDecide;
import irdp.protocols.tutorialDA.events.ConsensusPropose;
import irdp.protocols.tutorialDA.events.Crash;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.Debug;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;
import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;

/**
 * Consensus-based Terminating Reliable Broadcast implementation.
 * 
 * @author alexp
 */
public class ConsensusTRBSession extends Session {

  public ConsensusTRBSession(Layer layer) {
    super(layer);
  }

  /**
   * @see appia.Session#handle(appia.Event)
   */
  public void handle(Event event) {
    if (event instanceof ProcessInitEvent) {
      handleProcessInit((ProcessInitEvent) event);
      return;
    }
    if (event instanceof Crash) {
      handleCrash((Crash) event);
      return;
    }
    if (event instanceof ConsensusDecide) {
      handleConsensusDecide((ConsensusDecide) event);
      return;
    }
    if (event instanceof TRBSendableEvent) {
      handleTRBSendableEvent((TRBSendableEvent) event);
      return;
    }
    if (event instanceof TRBEvent) {
      handleTRBEvent((TRBEvent) event);
      return;
    }

    Debug.print("Unwanted event received, ignoring.");
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private TRBProposal proposal;
  private ProcessSet correct = null;
  private SampleProcess src;

  private void handleProcessInit(ProcessInitEvent event) {
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    correct = event.getProcessSet();
    init();
  }

  private void init() {
    proposal = null;
    src = null;
    // correct filled at handleProcessInit
  }

  private void handleCrash(Crash crash) {
    correct.setCorrect(crash.getCrashedProcess(), false);
    try {
      crash.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    failed(crash.getChannel());
  }

  private void handleTRBEvent(TRBEvent event) {
    src = correct.getProcess(event.p);

    debugAll("handleTRB");

    if (src.isSelf()) {
      try {
        TRBSendableEvent ev = new TRBSendableEvent(event.getChannel(),
            Direction.DOWN, this);
        ev.getMessage().pushObject(event.m);
        ev.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    }

    failed(event.getChannel());
  }

  private void handleTRBSendableEvent(TRBSendableEvent event) {
    if (proposal != null)
      return;

    proposal = new TRBProposal(event.getMessage().popObject());

    try {
      ConsensusPropose ev = new ConsensusPropose(event.getChannel(),
          Direction.DOWN, this);
      ev.value = proposal;
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void failed(Channel channel) {
    if (proposal != null)
      return;
    if ((src == null) || src.isCorrect())
      return;

    proposal = new TRBProposal(true);

    try {
      ConsensusPropose ev = new ConsensusPropose(channel, Direction.DOWN, this);
      ev.value = proposal;
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleConsensusDecide(ConsensusDecide event) {

    if (event.decision instanceof TRBProposal) {

      debugAll("handleConsensusDecide: OK");

      try {
        TRBEvent ev = new TRBEvent(event.getChannel(), Direction.UP, this);
        ev.p = src.getSocketAddress();
        if (((TRBProposal) event.decision).failed)
          ev.m = null;
        else
          ev.m = ((TRBProposal) event.decision).m;
        ev.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }

      init();
    } else {
      debugAll("handleConsensusDecide: not TBRProposal");

      try {
        event.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    }
  }

  // DEBUG
  private static final boolean debugFull = false;

  private void debugAll(String s) {
    if (!debugFull)
      return;

    System.out.println(s);
  }
}