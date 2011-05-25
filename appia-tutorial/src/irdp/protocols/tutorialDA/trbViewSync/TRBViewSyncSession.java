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
import irdp.protocols.tutorialDA.events.ReleaseEvent;
import irdp.protocols.tutorialDA.events.ViewEvent;
import irdp.protocols.tutorialDA.membershipUtils.View;
import irdp.protocols.tutorialDA.utils.Debug;
import irdp.protocols.tutorialDA.utils.SampleProcess;
import net.sf.appia.core.*;
import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.core.message.Message;
import net.sf.appia.core.message.MessageFactory;

import java.net.SocketAddress;
import java.util.HashSet;
import java.util.LinkedList;


/**
 * The TerminatingReliableBroadcast-based View Synchrony implementation.
 * 
 * @author alexp
 */
public class TRBViewSyncSession extends Session {

	private MessageFactory msgFactory = null;
	private LinkedList<View> pending_views = new LinkedList<View>();
	private HashSet<Msg> delivered = new HashSet<Msg>();
	private View current_view = null;
	private View next_view = null;
	private boolean flushing = false;
	private boolean blocked = true;
	/**
	 * trb_done counts the number of processes for wich TRB was used. This is
	 * possible because the TRB is used one at a time.
	 */
	private int trb_done;

	private int msg_id = 0;


  public TRBViewSyncSession(Layer layer) {
    super(layer);
  }

  public void handle(Event event) {
    if (event instanceof ViewEvent) {
      handleView((ViewEvent) event);
      return;
    }
    if (event instanceof BlockOkEvent) {
      handleBlockOkEvent((BlockOkEvent) event);
      return;
    }
    if (event instanceof TRBEvent) {
      handleTRBEvent((TRBEvent) event);
      return;
    }
    if (event instanceof SendableEvent) {
      if (event.getDir() == Direction.DOWN)
        handleCSVSBroadcast((SendableEvent) event);
      else
        handleRCODeliver((SendableEvent) event);
      return;
    }
    if (event instanceof ChannelInit) {
        handleInit((ChannelInit) event);
        return;
      }

    Debug.print("Unwanted event received, ignoring.");
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleInit(ChannelInit event) {
	msgFactory = event.getChannel().getMessageFactory();
	try {
		event.go();
	} catch (AppiaEventException e) {
		e.printStackTrace();
	}
  }

  private void handleView(ViewEvent event) {

    if (event.view.id == 0) {
      current_view = event.view;
      blocked = false;

      try {
        event.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }

      try {
        ReleaseEvent ev = new ReleaseEvent(event.getChannel(), Direction.UP,
            this);
        ev.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    } else {
      pending_views.addLast(event.view);
    }

    moreViews(event.getChannel());
  }

  private void handleCSVSBroadcast(SendableEvent event) {
    if (blocked)
      return;
    // Assert we have a view
    if (current_view == null)
      return;

    // Chooses a unique identifier for the message
    ++msg_id;

    Msg m = new Msg(current_view.memb.getSelfProcess(), msg_id, event
        .getClass().getName(), event.getMessage().toByteArray());
    delivered.add(m);

    try {
      SendableEvent ev = (SendableEvent) event.cloneEvent();
      ev.source = current_view.memb.getSelfProcess().getSocketAddress();
      ev.setDir(Direction.UP);
      ev.setSourceSession(this);
      ev.init();
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    } catch (CloneNotSupportedException ex) {
      ex.printStackTrace();
    }

    try {
      event.getMessage().pushInt(msg_id);
      event.getMessage().pushInt(current_view.id);
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleRCODeliver(SendableEvent event) {
    SampleProcess src = current_view.memb
        .getProcess((SocketAddress) event.source);
    Message m = event.getMessage();
    int vid = m.popInt();
    // Message identifier
    int mid = m.popInt();

    if (current_view.id != vid)
      return;

    Msg msg = new Msg(src, mid, event.getClass().getName(), m.toByteArray());

    if (delivered.contains(msg))
      return;

    delivered.add(msg);

    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void moreViews(Channel channel) {
    if (flushing)
      return;
    if (pending_views.size() == 0)
      return;

    next_view = (View) pending_views.removeFirst();
    flushing = true;

    try {
      BlockEvent ev = new BlockEvent(channel, Direction.UP, this);
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleBlockOkEvent(BlockOkEvent event) {
    blocked = true;
    trb_done = 0;

    debugAll("Group Blocked");

    SampleProcess p_i = current_view.memb.getProcess(trb_done);
    try {
      debugAll("Sending TRB for " + p_i.getProcessNumber() + "(self="
          + p_i.isSelf() + ")");

      TRBEvent ev = new TRBEvent(event.getChannel(), Direction.DOWN, this);
      ev.p = p_i.getSocketAddress();
      if (p_i.isSelf()) {
        ev.m = delivered;
      } else {
        // pushes an empty set
        ev.m = new HashSet<Msg>();
      }
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  @SuppressWarnings("unchecked")
  private void handleTRBEvent(TRBEvent event) {
	  HashSet<Msg> del = (HashSet<Msg>) event.m;

    trb_done++;

    debugAll("Received TRB for rank " + (trb_done - 1));

    if (del != null) {
    	for(Msg m : del){
        if (!delivered.contains(m)) {
          delivered.add(m);

          try {
            SendableEvent ev = (SendableEvent) Class.forName(m.eventName)
                .newInstance();
            ev.getMessage().setByteArray(m.data, 0, m.data.length);
            ev.setChannel(event.getChannel());
            ev.setDir(Direction.UP);
            ev.setSourceSession(this);
            ev.init();
            ev.go();
          } catch (AppiaEventException ex) {
            ex.printStackTrace();
          } catch (InstantiationException e) {
            e.printStackTrace();
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          } catch (ClassNotFoundException e) {
            e.printStackTrace();
          }
        }
      }
    }

    if (trb_done < current_view.memb.getSize()) {
      SampleProcess p_i = current_view.memb.getProcess(trb_done);
      try {
        debugAll("Sending TRB for " + p_i.getProcessNumber() + "(self="
            + p_i.isSelf() + ")");

        TRBEvent ev = new TRBEvent(event.getChannel(), Direction.DOWN, this);
        ev.p = p_i.getSocketAddress();
        ev.m = msgFactory.newMessage();
        if (p_i.isSelf()) {
          ev.m = delivered;
        } else {
          // proposes an empty set
          ev.m = new HashSet<Msg>();
        }
        ev.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    } else {
      ready(event.getChannel());
    }
  }

  private void ready(Channel channel) {
    if (!blocked)
      return;
    // because TRB was used one at a time, and this function is only invoqued
    // when all are
    // done, its not required to check the "trb_done" variable.

    current_view = next_view;
    flushing = false;
    blocked = false;

    try {
      ViewEvent ev1 = new ViewEvent(channel, Direction.UP, this);
      ev1.view = current_view;
      ev1.go();

      ReleaseEvent ev2 = new ReleaseEvent(channel, Direction.UP, this);
      ev2.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    moreViews(channel);
  }

  // DEBUG
  private static final boolean debugFull = false;

  private void debugAll(String s) {
    if (!debugFull)
      return;

    int i;

    System.err.println("TrbViewSyncSession DEBUG ALL -" + s);

    System.err.print("\tdelivered=");
    for(Msg m : delivered)
        System.err.print(m + ",");
    System.err.println();

    System.err.println();

    /*
     * System.err.println("\tproposal="+proposal);
     * 
     * 
     * System.err.print("\tprocesses="); for (i=0 ; i < correct.getSize() ; i++) {
     * SampleProcess p=correct.getProcess(i);
     * System.err.print("["+p.getProcessNumber()+";"+p.getInetWithPort()+";"+p.isCorrect()+"],"); }
     * System.err.println();
     * 
     */
  }
}
