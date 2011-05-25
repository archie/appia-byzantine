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

package irdp.protocols.tutorialDA.lazyRB;

import irdp.protocols.tutorialDA.events.Crash;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.Debug;
import irdp.protocols.tutorialDA.utils.MessageID;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;
import net.sf.appia.core.*;
import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.events.channel.ChannelInit;

import java.net.SocketAddress;
import java.util.LinkedList;


/**
 * Session implementing the Lazy Reliable Broadcast protocol.
 * 
 * @author nuno
 * 
 */
public class LazyRBSession extends Session {

  private ProcessSet processes;
  private int seqNumber;
  // array of lists
  private LinkedList<SendableEvent>[] from;
  // List of MessageID objects
  private LinkedList<MessageID> delivered;

  /**
   * @param layer
   */
  public LazyRBSession(Layer layer) {
    super(layer);
    seqNumber = 0;
  }

  /**
   * Main event handler
   */
  public void handle(Event event) {
    // Init events. Channel Init is from Appia and ProcessInitEvent is to know
    // the elements of the group
    if (event instanceof ChannelInit)
      handleChannelInit((ChannelInit) event);
    else if (event instanceof ProcessInitEvent)
      handleProcessInitEvent((ProcessInitEvent) event);
    else if (event instanceof SendableEvent) {
      if (event.getDir() == Direction.DOWN)
        // UPON event from the above protocol (or application)
        rbBroadcast((SendableEvent) event);
      else
        // UPON event from the bottom protocol (or perfect point2point links)
        bebDeliver((SendableEvent) event);
    } else if (event instanceof Crash)
      handleCrash((Crash) event);
  }

  /**
   * @param init
   */
  private void handleChannelInit(ChannelInit init) {
    try {
      init.go();
    } catch (AppiaEventException e) {
      e.printStackTrace();
    }
    delivered = new LinkedList<MessageID>();
  }

  /**
   * @param event
   */
  @SuppressWarnings("unchecked")
private void handleProcessInitEvent(ProcessInitEvent event) {
    processes = event.getProcessSet();
    try {
      event.go();
    } catch (AppiaEventException e) {
      e.printStackTrace();
    }
    from = new LinkedList[processes.getSize()];
    for (int i = 0; i < from.length; i++)
      from[i] = new LinkedList<SendableEvent>();
  }

  /**
   * Called when the above protocol sends a message.
   * 
   * @param event
   */
  private void rbBroadcast(SendableEvent event) {
    // first we take care of the header of the message
    SampleProcess self = processes.getSelfProcess();
    MessageID msgID = new MessageID(self.getProcessNumber(), seqNumber);
    seqNumber++;
    Debug.print("RB: broadcasting message.");
    event.getMessage().pushObject(msgID);
    // broadcast the message
    bebBroadcast(event);
  }

  /**
   * Called when the lower protocol delivers a message.
   * 
   * @param event
   */
  private void bebDeliver(SendableEvent event) {
    Debug.print("RB: Received message from beb.");
    MessageID msgID = (MessageID) event.getMessage().peekObject();
    if (!delivered.contains(msgID)) {
      Debug.print("RB: message is new.");
      delivered.add(msgID);
      // removes the header from the message (sender and seqNumber) and delivers
      // it
      SendableEvent cloned = null;
      try {
        cloned = (SendableEvent) event.cloneEvent();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
        return;
      }
      event.getMessage().popObject();
      try {
        event.go();
      } catch (AppiaEventException e) {
        e.printStackTrace();
      }
      // adds message to the "from" array
      SampleProcess pi = processes.getProcess((SocketAddress) event.source);
      int piNumber = pi.getProcessNumber();
      from[piNumber].add(cloned);
      /*
       * resends the message if the source is no longer correct
       */
      if (!pi.isCorrect()) {
        SendableEvent retransmission = null;

        try {
          retransmission = (SendableEvent) cloned.cloneEvent();
        } catch (CloneNotSupportedException e1) {
          e1.printStackTrace();
        }
        bebBroadcast(retransmission);
      }
    }
  }

  /**
   * Called by this protocol to send a message to the lower protocol.
   * 
   * @param event
   */
  private void bebBroadcast(SendableEvent event) {
    Debug.print("RB: sending message to beb.");
    try {
      event.setDir(Direction.DOWN);
      event.setSourceSession(this);
      event.init();
      event.go();
    } catch (AppiaEventException e) {
      e.printStackTrace();
    }
  }

  /**
   * Called when some process crashed.
   * 
   * @param crash
   */
  private void handleCrash(Crash crash) {
    int pi = crash.getCrashedProcess();
    System.out.println("Process " + pi + " failed.");

    try {
      crash.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    // changes the state of the process to "failed"
    processes.getProcess(pi).setCorrect(false);

    // resends the messages of the failed process
    for(SendableEvent event : from[pi])
    	bebBroadcast(event);
    from[pi].clear();
  }
}
