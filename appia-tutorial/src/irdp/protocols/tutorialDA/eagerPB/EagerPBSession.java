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

/*
 * PBSession.java
 * Created on 22-Sep-2003, 17:09:41
 */
package irdp.protocols.tutorialDA.eagerPB;

import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.Debug;
import irdp.protocols.tutorialDA.utils.MessageID;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import net.sf.appia.core.*;
import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.events.channel.ChannelInit;

import java.util.LinkedList;
import java.util.Random;


/**
 * Session implementing the Eager Probabilistic Broadcast protocol.
 * 
 * @author nuno
 */
public class EagerPBSession extends Session {

  // list of delivered messages
  private LinkedList<MessageID> delivered;
  // set of processes
  private ProcessSet processes;
  private int fanout, maxRounds, seqNumber;

  /**
   * @param layer
   */
  public EagerPBSession(Layer layer) {
    super(layer);
    EagerPBLayer pbLayer = (EagerPBLayer) layer;
    fanout = pbLayer.getFanout();
    maxRounds = pbLayer.getMaxRounds();
    seqNumber = 0;
  }

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
        pbBroadcast((SendableEvent) event);
      else
        // UPON event from the bottom protocol (or perfect point2point links)
        up2pDeliver((SendableEvent) event);
    }
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
  private void handleProcessInitEvent(ProcessInitEvent event) {
    processes = event.getProcessSet();
    fanout = Math.min(fanout, processes.getSize());
    try {
      event.go();
    } catch (AppiaEventException e) {
      e.printStackTrace();
    }
  }

  /**
   * @param event
   */
  private void pbBroadcast(SendableEvent event) {
    MessageID msgID = new MessageID(processes.getSelfRank(), seqNumber);
    seqNumber++;
    // gossips a new message.
    gossip(event, msgID, maxRounds - 1);
  }

  /**
   * @param event
   */
  private void up2pDeliver(SendableEvent event) {
    // pops the round of the message
    int round = event.getMessage().popInt();
    // and the remaining header
    MessageID msgID = (MessageID) event.getMessage().popObject();
    // if the message was not delivered yet
    Debug.print("PB: received message " + msgID + " with round " + round);
    if (!delivered.contains(msgID)) {
      delivered.add(msgID);

      SendableEvent clone = null;
      try {
        clone = (SendableEvent) event.cloneEvent();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }
      // delivers the message
      pbDeliver(clone, msgID);
    }

    // gossips the message again, if there are more rounds.
    if (round > 0)
      gossip(event, msgID, round - 1);
  }

  private void gossip(SendableEvent event, MessageID msgID, int round) {
    Debug.print("PB: gossip of message" + msgID + " in round " + round);
    int[] targets = pickTargets();
    for (int i = 0; i < fanout; i++) {
      SendableEvent clone = null;
      try {
        clone = (SendableEvent) event.cloneEvent();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }
      clone.getMessage().pushObject(msgID);
      clone.getMessage().pushInt(round);
      up2pSend(clone, targets[i]);
    }
  }

  /**
   * This method returns n different process ranks, where n is the fanout.
   */
  private int[] pickTargets() {
    Random random = new Random(System.currentTimeMillis());
    LinkedList<Integer> targets = new LinkedList<Integer>();
    int candidate = -1;
    while (targets.size() < fanout) {
      candidate = random.nextInt(processes.getSize());
      if ((!targets.contains(candidate)) && (candidate != processes.getSelfRank()))
        targets.add(candidate);
    }
    int[] targetArray = new int[fanout];
    for(int i=0; i<targets.size() && i<targetArray.length; i++){
        targetArray[i] = targets.get(i);
    }
    return targetArray;
  }

  /**
   * Called by this protocol to send a message to the lower protocol.
   * 
   * @param event
   */
  private void up2pSend(SendableEvent event, int dest) {
    Debug.print("PB: sending message to up2p, destination " + dest + ".");
    try {
      event.setDir(Direction.DOWN);
      event.setSourceSession(this);
      event.dest = processes.getProcess(dest).getSocketAddress();
      event.init();
      event.go();
    } catch (AppiaEventException e) {
      e.printStackTrace();
    }
  }

  /**
   * Delivers the message to above protocol or application
   * 
   * @param event
   * @param msgID
   */
  private void pbDeliver(SendableEvent event, MessageID msgID) {
    Debug.print("PB: delivering message to above protocol.");
    try {
      event.setDir(Direction.UP);
      event.setSourceSession(this);
      event.source = processes.getProcess(msgID.process).getSocketAddress();
      event.init();
      event.go();
    } catch (AppiaEventException e) {
      e.printStackTrace();
    }
  }

}
