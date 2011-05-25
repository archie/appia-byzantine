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

package irdp.protocols.tutorialDA.consensusNBAC;

import irdp.protocols.tutorialDA.consensusUtils.IntProposal;
import irdp.protocols.tutorialDA.events.ConsensusDecide;
import irdp.protocols.tutorialDA.events.ConsensusPropose;
import irdp.protocols.tutorialDA.events.Crash;
import irdp.protocols.tutorialDA.events.NBACDecide;
import irdp.protocols.tutorialDA.events.NBACPropose;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.Debug;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;

import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Iterator;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;


/**
 * Consensus-based Non-Blocking Atomic Commit implementation.
 * 
 * @author alexp
 */
public class ConsensusNBACSession extends Session {

  public ConsensusNBACSession(Layer layer) {
    super(layer);
  }

  /**
   * @see appia.Session#handle(appia.Event)
   */
  public void handle(Event event) {
    if (event instanceof ProcessInitEvent)
      handleProcessInit((ProcessInitEvent) event);
    else if (event instanceof Crash)
      handleCrash((Crash) event);
    else if (event instanceof NBACPropose)
      handleNBACPropose((NBACPropose) event);
    else if (event instanceof ConsensusDecide)
      handleConsensusDecide((ConsensusDecide) event);
    else {
      Debug.print("Unwanted event received, ignoring.");
      try {
        event.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    }
  }

  private HashSet<SampleProcess> delivered = null;
  private ProcessSet correct = null;
  private int proposal;

  private void init() {
    delivered = new HashSet<SampleProcess>();
    proposal = 1;
  }

  private void handleProcessInit(ProcessInitEvent event) {
    correct = event.getProcessSet();
    init();
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleCrash(Crash crash) {
    correct.setCorrect(crash.getCrashedProcess(), false);
    try {
      crash.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    all_delivered(crash.getChannel());
  }

  private void handleNBACPropose(NBACPropose event) {
    if (event.getDir() == Direction.DOWN) {
      event.getMessage().pushInt(event.value);

      try {
        event.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    } else {
      SampleProcess p_i = correct.getProcess((SocketAddress) event.source);
      int v = event.getMessage().popInt();

      delivered.add(p_i);
      proposal *= v;

      all_delivered(event.getChannel());
    }
  }

  private void all_delivered(Channel channel) {
    debugAll("all_delivered");

    boolean all_correct = true;
    int i;
    for (i = 0; i < correct.getSize(); i++) {
      SampleProcess p = correct.getProcess(i);
      if (p != null) {
        if (p.isCorrect()) {
          if (!delivered.contains(p))
            return;
        } else {
          all_correct = false;
        }
      }
    }

    if (!all_correct)
      proposal = 0;

    try {
      ConsensusPropose ev = new ConsensusPropose(channel, Direction.DOWN, this);
      ev.value = new IntProposal(proposal);
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    init();
  }

  private void handleConsensusDecide(ConsensusDecide event) {
    try {
      NBACDecide ev = new NBACDecide(event.getChannel(), Direction.UP, this);
      ev.decision = ((IntProposal) event.decision).i;
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  // DEBUG
  private static final boolean debugFull = false;

  private void debugAll(String s) {
    if (!debugFull)
      return;

    int i;
    System.err.println("DEBUG ALL -" + s);
    System.err.println("\tproposal=" + proposal);

    System.err.print("\tdelivered=");
    Iterator<SampleProcess> iter = delivered.iterator();
    while (iter.hasNext()) {
      SampleProcess p = (SampleProcess) iter.next();
      System.err.print(p.getProcessNumber() + ",");
    }
    System.err.println();

    System.err.print("\tprocesses=");
    for (i = 0; i < correct.getSize(); i++) {
      SampleProcess p = correct.getProcess(i);
      System.err.print("[" + p.getProcessNumber() + ";" + p.getSocketAddress()
          + ";" + p.isCorrect() + "],");
    }
    System.err.println();
  }
}
