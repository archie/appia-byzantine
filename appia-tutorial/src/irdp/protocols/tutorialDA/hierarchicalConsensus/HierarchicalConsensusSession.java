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

package irdp.protocols.tutorialDA.hierarchicalConsensus;

import irdp.protocols.tutorialDA.consensusUtils.Proposal;
import irdp.protocols.tutorialDA.events.ConsensusDecide;
import irdp.protocols.tutorialDA.events.ConsensusPropose;
import irdp.protocols.tutorialDA.events.Crash;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;
import net.sf.appia.core.*;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;


/**
 * The regular Hierarchical Consensus implementation.
 * 
 * @author alexp
 */
public class HierarchicalConsensusSession extends Session {

  public HierarchicalConsensusSession(Layer layer) {
    super(layer);
  }

  private int round = -1;
  private int prop_round = -1;
  private ProcessSet processes = null;
  private HashSet<Integer> suspected = new HashSet<Integer>();
  private boolean[] broadcast = null;
  private boolean[] delivered = null;
  private Proposal proposal = null;

  public void handle(Event event) {
    if (event instanceof ProcessInitEvent)
      handleProcessInit((ProcessInitEvent) event);
    else if (event instanceof Crash)
      handleCrash((Crash) event);
    else if (event instanceof ConsensusPropose)
      handleConsensusPropose((ConsensusPropose) event);
    else if (event instanceof DecidedEvent)
      handleDecided((DecidedEvent) event);
    else {
      debug("Unwanted event received, ignoring.");
      try {
        event.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    }
  }

  private void init() {
    int max_rounds = processes.getSize();

    // suspected
    round = 0;
    proposal = null;
    prop_round = -1;

    delivered = new boolean[max_rounds];
    Arrays.fill(delivered, false);
    broadcast = new boolean[max_rounds];
    Arrays.fill(broadcast, false);
  }

  private void handleProcessInit(ProcessInitEvent event) {
    processes = event.getProcessSet();
    init();
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleCrash(Crash crash) {
    processes.setCorrect(crash.getCrashedProcess(), false);

    suspected.add(new Integer(crash.getCrashedProcess()));
    try {
      crash.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    suspected_or_delivered();
    decide(crash.getChannel());
  }

  private void handleConsensusPropose(ConsensusPropose propose) {
    if (proposal != null)
      return;

    proposal = propose.value;

    decide(propose.getChannel());
  }

  private void decide(Channel channel) {

    debugAll("decide");

    if (broadcast[round])
      return;
    if (proposal == null)
      return;
    if (round != processes.getSelfRank())
      return;

    broadcast[round] = true;
    try {
      ConsensusDecide ev = new ConsensusDecide(channel, Direction.UP, this);
      ev.decision = (Proposal) proposal;
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
    try {
      DecidedEvent ev = new DecidedEvent(channel, Direction.DOWN, this);
      ev.getMessage().pushObject(proposal);
      ev.getMessage().pushInt(round);
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void suspected_or_delivered() {
    if (suspected.contains(new Integer(round)) || delivered[round])
      round++;

    if (round >= delivered.length) {
      debug("Reinitiated algorithm");
      init();
    }
  }

  private void handleDecided(DecidedEvent event) {
//    SampleProcess p_i = processes.getProcess((SocketAddress) event.source);
    int r = event.getMessage().popInt();
    Proposal v = (Proposal) event.getMessage().popObject();

    if ((r < processes.getSelfRank()) && (r > prop_round)) {
      proposal = v;
      prop_round = r;
    }
    delivered[r] = true;

    suspected_or_delivered();
    decide(event.getChannel());
  }

  // DEBUG
  public static final boolean debugFull = false;
  private PrintStream debug = System.out;

  private void debug(String s) {
    if ((debug != null) && debugFull)
      debug.println(this.getClass().getName() + ": " + s);
  }

  private void debugAll(String s) {
    if ((debug == null) || !debugFull)
      return;
    int i;
    debug.println("DEBUG ALL - " + s);
    debug.println("\tround=" + round);
    debug.println("\tprop_round=" + round);
    debug.println("\tproposal=" + proposal);

    debug.print("\tprocesses=");
    for (i = 0; i < processes.getSize(); i++) {
      SampleProcess p = processes.getProcess(i);
      debug.print("[" + p.getProcessNumber() + ";" + p.getSocketAddress() + ";"
          + p.isCorrect() + "],");
    }
    debug.println();

    debug.print("\tsuspected=");
    for( int p : suspected)
        debug.print(p + ",");
    debug.println();

    debug.print("\tbroadcast=");
    for (i = 0; i < broadcast.length; i++) {
      debug.print(broadcast[i] + ",");
    }
    debug.println();

    debug.print("\tdelivered=");
    for (i = 0; i < delivered.length; i++) {
      debug.print(delivered[i] + ",");
    }
    debug.println();
  }
}
