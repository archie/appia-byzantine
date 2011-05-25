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

package irdp.protocols.tutorialDA.majorityAckURB;

import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.*;
import net.sf.appia.core.*;
import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.events.channel.ChannelInit;

import java.net.SocketAddress;
import java.util.Hashtable;
import java.util.LinkedList;


/**
 * Session implementing the Majority-Ack Uniform Reliable Broadcast protocol.
 * 
 * @author nuno
 */
public class MajorityAckURBSession extends Session {

  // List of processes.
  private ProcessSet processes;
  // This sequence number represents the delivered set.
  private int seqNumber;
  // List of MessageID objects
  private LinkedList<MessageID> forward, delivered;
  // hashtable mapping: < MessageID --> MessageEntry >
  private Hashtable<MessageID,MessageEntry> ack;

  /**
   * @param layer
   */
  public MajorityAckURBSession(Layer layer) {
    super(layer);
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
        iurbBroadcast((SendableEvent) event);
      else
        // UPON event from the bottom protocol (or perfect point2point links)
        bebDeliver((SendableEvent) event);
    }
    /*
     * Unexpected event arrived. Forwarding it.
     */
    else
      try {
        event.go();
      } catch (AppiaEventException e) {
        e.printStackTrace();
      }

    // Every time something happens, the protocol verify if more messages can be
    // delivered.
    urbTryDeliver();
  }

  /**
   * verifies if there are messages to be delivered.
   */
  private void urbTryDeliver() {
	  for(MessageEntry entry : ack.values()){
		  if (canDeliver(entry)) {
			  // add the message to the delivered set and
			  delivered.add(entry.messageID);
			  // delivers the message.
			  iurbDeliver(entry.event, entry.messageID.process);
		  }    	
	  }
  }

  /**
   * Verifies if the message can be delivered.
   * 
   * @param entry
   * @return
   */
  private boolean canDeliver(MessageEntry entry) {
    int N = processes.getSize(), numAcks = 0;
    for (int i = 0; i < N; i++)
      if (entry.acks[i])
        numAcks++;
    return (numAcks > (N / 2)) && (!delivered.contains(entry.messageID));
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
    forward = new LinkedList<MessageID>();
    delivered = new LinkedList<MessageID>();
    ack = new Hashtable<MessageID,MessageEntry>();
  }

  /**
   * @param event
   */
  private void handleProcessInitEvent(ProcessInitEvent event) {
    processes = event.getProcessSet();
    try {
      event.go();
    } catch (AppiaEventException e) {
      e.printStackTrace();
    }
  }

  /**
   * Called when the above protocol sends a message.
   * 
   * @param event
   */
  private void iurbBroadcast(SendableEvent event) {
    /*
     * header is composed by a sequence number and the number of the process
     * that is sending the message
     */
    SampleProcess self = processes.getSelfProcess();
    MessageID msgID = new MessageID(self.getProcessNumber(), seqNumber);
    Debug.print("URB: broadcasting message from " + msgID.process
        + "with seqNumber = " + msgID.seqNumber);
    seqNumber++;
    // adds the message to the forward set.
    forward.add(msgID);
    // push the message header
    event.getMessage().pushObject(msgID);
    // broadcasts the message.
    bebBroadcast(event);
  }

  /**
   * Called when the lower protocol delivers a message.
   * 
   * @param event
   */
  private void bebDeliver(SendableEvent event) {
    Debug.print("URB: Received message from beb.");
    SendableEvent clone = null;
    try {
      clone = (SendableEvent) event.cloneEvent();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
      return;
    }
    // pops the message header
    MessageID msgID = (MessageID) clone.getMessage().popObject();
    // register the information about the ack
    addAck(clone, msgID);
    if (!forward.contains(msgID)) {
      Debug.print("URB: Message is not on the forward set.");
      // forwards the message
      forward.add(msgID);
      bebBroadcast(event);
    }
  }

  /**
   * Called by this protocol to send a message to the lower protocol.
   * 
   * @param event
   */
  private void bebBroadcast(SendableEvent event) {
    Debug.print("URB: sending message to beb.");
    try {
      if (event.getDir() != Direction.DOWN)
        event.setDir(Direction.invert(event.getDir()));
      event.setSourceSession(this);
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
   * @param sender
   */
  private void iurbDeliver(SendableEvent event, int sender) {
    Debug.print("URB: delivering message to above protocol.");
    try {
      if (event.getDir() != Direction.UP)
        event.setDir(Direction.invert(event.getDir()));
      event.setSourceSession(this);
      event.source = processes.getProcess(sender).getSocketAddress();
      event.init();
      event.go();
    } catch (AppiaEventException e) {
      e.printStackTrace();
    }
  }

  /*
   * ################################### Methods to help the protocol
   * ###################################
   */


  private void addAck(SendableEvent event, MessageID msgID) {
    Debug.print("URB: adding ack.");
    int pi = processes.getProcess((SocketAddress) event.source)
        .getProcessNumber();
    MessageEntry entry = (MessageEntry) ack.get(msgID);
    if (entry == null) {
      Debug.print("URB: first time that the message is seen.");
      entry = new MessageEntry(event, msgID, processes.getSize());
      ack.put(msgID, entry);
    }
    entry.acks[pi] = true;
  }

}
