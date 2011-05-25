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

package irdp.protocols.tutorialDA.consensusMembership;

import irdp.protocols.tutorialDA.events.ConsensusDecide;
import irdp.protocols.tutorialDA.events.ConsensusPropose;
import irdp.protocols.tutorialDA.events.Crash;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.events.ViewEvent;
import irdp.protocols.tutorialDA.membershipUtils.View;
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
 * Session implementing the Consensus-Based Membership protocol.
 * 
 * @author alexp
 */
public class ConsensusMembershipSession extends Session {

  public ConsensusMembershipSession(Layer layer) {
    super(layer);
  }

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

    Debug.print("Unwanted event received, ignoring.");
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private View view = null;
  private ProcessSet correct = null;
  private boolean wait;

  private void handleProcessInit(ProcessInitEvent event) {
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    view = new View();
    view.id = 0;
    view.memb = event.getProcessSet();
    correct = event.getProcessSet();
    wait = false;

    try {
      ViewEvent ev = new ViewEvent(event.getChannel(), Direction.UP, this);
      ev.view = view;
      ev.go();
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

    newMembership(crash.getChannel());
  }

  private void newMembership(Channel channel) {
    if (wait)
      return;

    boolean crashed = false;
    int i;
    for (i = 0; i < correct.getSize(); i++) {
      SampleProcess p = correct.getProcess(i);
      SampleProcess m = view.memb.getProcess(p.getSocketAddress());
      if (!p.isCorrect() && (m != null)) {
        crashed = true;
      }
    }
    if (!crashed)
      return;

    wait = true;

    int j;
    ProcessSet trimmed_memb = new ProcessSet();
    for (i = 0, j = 0; i < correct.getSize(); i++) {
      SampleProcess p = correct.getProcess(i);
      if (p.isCorrect())
        trimmed_memb.addProcess(p, j++);
    }

    View v = new View();
    v.id = view.id + 1;
    v.memb = trimmed_memb;

    try {
      ConsensusPropose ev = new ConsensusPropose(channel, Direction.DOWN, this);
      ev.value = v;
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleConsensusDecide(ConsensusDecide event) {
    view = (View) event.decision;

    wait = false;

    try {
      ViewEvent ev = new ViewEvent(event.getChannel(), Direction.UP, this);
      ev.view = view;
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }
}
