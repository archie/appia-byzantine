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

import irdp.protocols.tutorialDA.consensusUtils.Proposal;
import irdp.protocols.tutorialDA.events.ConsensusDecide;
import irdp.protocols.tutorialDA.events.ConsensusPropose;
import irdp.protocols.tutorialDA.events.Crash;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;

import java.io.PrintStream;
import java.net.SocketAddress;
import java.util.HashSet;

import net.sf.appia.core.AppiaDuplicatedSessionsException;
import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelInit;


/**
 * The Uniform Hierarchical Consensus implementation.
 * 
 * @author alexp
 */
public class UniformHierarchicalConsensusSession extends Session {

  public UniformHierarchicalConsensusSession(Layer layer) {
    super(layer);
  }

  private Proposal proposal = null;
  private Proposal decided = null;
  private int round = -1;
  private HashSet<Integer> suspected = new HashSet<Integer>();
  private HashSet<Integer> ack_set = new HashSet<Integer>();
  private int prop_round = -1;
  private ProcessSet processes = null;

  private Channel mainchannel = null;
  private Channel rbchannel = null;
  private Channel rbinit = null;

  /**
   * @see appia.Session#handle(appia.Event)
   */
  public void handle(Event event) {
    if (event instanceof ChannelInit)
      handleChannelInit((ChannelInit) event);
    else if (event instanceof ProcessInitEvent)
      handleProcessInit((ProcessInitEvent) event);
    else if (event instanceof Crash)
      handleCrash((Crash) event);
    else if (event instanceof ConsensusPropose)
      handleConsensusPropose((ConsensusPropose) event);
    else if (event instanceof ProposeEvent)
      handleProposeEvent((ProposeEvent) event);
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

  /**
   * Sets the Reliable Broadcast Channel
   */
  public void rbchannel(Channel c) {
    rbinit = c;
  }

  private void handleChannelInit(ChannelInit init) {
    if (mainchannel == null) {
      mainchannel = init.getChannel();
      debug("mainchannel initiated");
      try {
        rbinit.start();
      } catch (AppiaDuplicatedSessionsException ex) {
        ex.printStackTrace();
      }
    } else {
      if (init.getChannel() == rbinit) {
        rbchannel = init.getChannel();

        if (processes != null) {
          try {
            ProcessInitEvent ev = new ProcessInitEvent(rbchannel,
                Direction.DOWN, this);
            ev.setProcessSet(processes);
            ev.go();
            debug("rbchannel initiated (ci)");
          } catch (AppiaEventException ex) {
            ex.printStackTrace();
          }
        }
      }
    }

    try {
      init.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleProcessInit(ProcessInitEvent event) {
    processes = event.getProcessSet();
    init();
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    if (rbchannel != null) {
      try {
        ProcessInitEvent ev = new ProcessInitEvent(rbchannel, Direction.DOWN,
            this);
        ev.setProcessSet(processes);
        ev.go();
        debug("rbchannel initiated (pi)");
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    }
  }

  private void init() {
    proposal = null;
    decided = null;
    round = 0;
    // suspected
    ack_set = new HashSet<Integer>();
    prop_round = -1;

    count_decided = 0;
  }

  private void handleCrash(Crash crash) {
    debug("Received Crash");
    processes.setCorrect(crash.getCrashedProcess(), false);

    suspected.add(crash.getCrashedProcess());
    try {
      crash.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    suspected_or_acked();
    propose();
    decide();
  }

  private void handleConsensusPropose(ConsensusPropose propose) {
    if (proposal != null)
      return;

    proposal = propose.value;

    propose();
  }

  private void propose() {

    debugAll("propose");

    if (decided != null)
      return;
    if (proposal == null)
      return;
    if (round != processes.getSelfRank())
      return;

    try {
      ProposeEvent ev = new ProposeEvent(mainchannel, Direction.DOWN, this);
      ev.getMessage().pushObject(proposal);
      ev.getMessage().pushInt(round);
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleProposeEvent(ProposeEvent event) {
    int p_i_rank = processes.getRank((SocketAddress) event.source);
    int r = event.getMessage().popInt();
    Proposal v = (Proposal) event.getMessage().popObject();

    ack_set.add(p_i_rank);
    if ((r < processes.getSelfRank()) && (r > prop_round)) {
      proposal = v;
      prop_round = r;
    }

    suspected_or_acked();
    propose();
    decide();
  }

  private void suspected_or_acked() {
    if (suspected.contains(round)
        || ack_set.contains(round))
      round++;
  }

  private void decide() {
    int i;
    for (i = 0; i < processes.getSize(); i++) {
      int p_i_rank = processes.getProcess(i).getProcessNumber();
      if (!suspected.contains(p_i_rank)
          && !ack_set.contains(p_i_rank)) {
        debug("decide: " + p_i_rank + "not suspected or acked");
        return;
      }
    }

    try {
      DecidedEvent ev = new DecidedEvent(rbchannel, Direction.DOWN, this);
      ev.getMessage().pushObject(proposal);
      ev.go();

      debug("DecidedEvent sent");
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleDecided(DecidedEvent event) {
    // Counts the number os Decided messages received and reinitiates the
    // algorithm
    if ((++count_decided >= correctSize()) && (decided != null)) {
      debug("reinitiated algorithm (count_decided=" + count_decided + ")");
      init();
      return;
    }

    if (decided != null)
      return;

    decided = (Proposal) event.getMessage().popObject();

    try {
      ConsensusDecide ev = new ConsensusDecide(mainchannel, Direction.UP, this);
      ev.decision = (Proposal) decided;
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  // Used to count the number of Decided messages received, therefore
  // determining when
  // all processes have decided and therefore allow a new decision process.
  private int count_decided;
  private int correctSize() {
    int size = 0, i;
    for (i = 0; i < processes.getSize(); i++) {
      if ((processes.getProcess(i) != null)
          && processes.getProcess(i).isCorrect())
        ++size;
    }
    return size;
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
    debug.println("\tproposal=" + proposal);
    debug.println("\tdecided=" + decided);
    debug.println("\tround=" + round);
    debug.println("\tprop_round=" + round);

    debug.print("\tsuspected=");
    for(Integer p : suspected)
        debug.print(p.intValue() + ",");
    debug.println();

    debug.print("\tack_set=");
    for(Integer p : ack_set)
        debug.print(p.intValue() + ",");
    debug.println();

    debug.print("\tprocesses=");
    for (i = 0; i < processes.getSize(); i++) {
      SampleProcess p = processes.getProcess(i);
      debug.print("[" + p.getProcessNumber() + ";" + p.getSocketAddress() + ";"
          + p.isCorrect() + "],");
    }
    debug.println();

    debug.println("\tmainchannel=" + mainchannel.getChannelID());
    debug.println("\trbchannel=" + rbchannel.getChannelID());

    debug.println();
  }
}
