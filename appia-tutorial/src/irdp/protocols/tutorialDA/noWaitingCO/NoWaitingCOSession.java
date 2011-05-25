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

package irdp.protocols.tutorialDA.noWaitingCO;


import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.Debug;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import net.sf.appia.core.*;
import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.core.message.Message;
import net.sf.appia.core.message.MessageFactory;

import java.net.SocketAddress;
import java.util.LinkedList;

/**
 * Reliable causal order broadcast no-waiting algorithm.
 * 
 * Note: no-waiting because each msg carries the ones that causally precede it.
 * So, no msg has to wait for others to arrive in order to be delivered.
 * 
 * July 2003
 * 
 * @author MJo�oMonteiro
 * 
 */
public class NoWaitingCOSession extends Session {
  private Channel channel;
  private ProcessSet processes = null;
  private int seqNumber = 0;
  /*
   * Set of delivered messages.
   * 
   */
  private LinkedList<ListElement> delivered;
  /*
   * Set of messages processed by this element. These messages will causally
   * precede the ones to be sent by this element.
   * 
   */
  private LinkedList<ListElement> myPast;

  private MessageFactory msgFactory = null;
  /**
   * Standard constructor
   * 
   * @param l
   *          the CONoWaintingLayer
   */
  public NoWaitingCOSession(Layer l) {
    super(l);
  }

  /**
   * The event handler function. Dispatches the new event to the appropriate
   * function.
   * 
   * @param e
   *          the event
   */
  public void handle(Event e) {

    if (e instanceof ChannelInit)
      handleChannelInit((ChannelInit) e);
    else if (e instanceof ProcessInitEvent)
      handleProcessInit((ProcessInitEvent) e);
    else if (e instanceof SendableEvent) {
      if (e.getDir() == Direction.DOWN)
        handleSendableEventDOWN((SendableEvent) e);
      else
        handleSendableEventUP((SendableEvent) e);
    } else {
      try {
        e.go();
      } catch (AppiaEventException ex) {
        System.out.println("[CONoWaitingSession:handle]" + ex.getMessage());
      }
    }
  }

  /**
   * Handles channelInit event. Initializes both lists.
   * 
   * @param e
   *          the channelinit event just arrived.
   */
  public void handleChannelInit(ChannelInit e) {
    Debug.print("CO: handle: " + e.getClass().getName());
    msgFactory = e.getChannel().getMessageFactory();
    try {
      e.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
    this.channel = e.getChannel();

    delivered = new LinkedList<ListElement>();
    myPast = new LinkedList<ListElement>();

  }

  /**
   * Handles processinit event and keeps the information about the set of
   * processes in the group.
   * 
   * 
   * @param e
   *          the processinit event just arrived
   */
  public void handleProcessInit(ProcessInitEvent e) {
    Debug.print("CO: handle: " + e.getClass().getName());

    processes = e.getProcessSet();
    try {
      e.go();
    } catch (AppiaEventException ex) {
      System.out.println("[CONoWaitingGCSession:handlePI]" + ex.getMessage());
    }
  }

  /**
   * Handles sendable event to be sent to the network.
   * 
   * Sends the msg and the myPast list.
   * 
   * @param e
   *          the sendable event.
   */
  public void handleSendableEventDOWN(SendableEvent e) {
    Debug.print("CO: handle: " + e.getClass().getName() + " DOWN");

    // cloning the event to be sent in oder to keep it in the mypast list...
    // (with no headers...)
    SendableEvent e_aux = null;
    try {
      e_aux = (SendableEvent) e.cloneEvent();
    } catch (CloneNotSupportedException ex) {
      System.out.println("CONoW:handleSEdown:" + ex.getMessage());
    }

    Message om = e.getMessage();

    // inserting myPast list in the msg:
    for (int k = myPast.size(); k > 0; k--) {
      Message om_k = myPast.get(k - 1). getSE().getMessage();

      // inserting the message
      om.pushObject(om_k.toByteArray());
      // inserting the size of the message
      om.pushInt(om_k.toByteArray().length);
      // inserting the sequence number of this msg
      om.pushInt(myPast.get(k - 1).getSeq());
      // inserting the source of the msg
      om.pushObject(myPast.get(k - 1).getSE().source);
      // inserting the class of the event to be created by the protocol
      // when it receives this msg
      om.pushString(myPast.get(k - 1).getSE().getClass().getName());
    }

    // inserting the number of entries of myPast list...
    om.pushInt(myPast.size());
    // inserting the global seq number of this msg
    om.pushInt(seqNumber);
    // restoring the message to the sendable event, now updated with myPast
    // list!
    System.out.println("CO: Sending msg with size: " + om.toByteArray().length);

    // sending the event!
    try {
      e.go();
    } catch (AppiaEventException ex) {
      System.out.println("[CONoWaitingSession:handleSendableEventDOWN]"
          + ex.getMessage());
    }

    // add this message to the myPast list:
    // but only with the original msg from the application
    // in order to keep this msg we must fill the source field in the
    // event...
    // (this source is filled only in the lower protocols)
    e_aux.source = processes.getSelfProcess().getSocketAddress();
    ListElement le = new ListElement(e_aux, seqNumber);
    myPast.add(le);

    // increments the global seq number
    seqNumber++;

  }

  /**
   * Handles sendable event just arrived from the network.
   * 
   * @param e
   *          the sendable event.
   */
  public void handleSendableEventUP(SendableEvent e) {

    Debug.print("CO: handle: " + e.getClass().getName() + " UP");

    Message om = e.getMessage();
    int seq = om.popInt();

    // checks to see if this msg has been already delivered...
    if (!isDelivered((SocketAddress) e.source, seq)) {

      // size of the past list of this msg
      int pastSize = om.popInt();
      for (int k = 0; k < pastSize; k++) {

        String className = om.popString();
        SocketAddress msgSource = (SocketAddress) om.popObject();
        int msgSeq = om.popInt();
        int msgSize = om.popInt();
        byte[] msg = (byte[]) om.popObject();

        // if this msg hasn't been already delivered, we must deliver it
        // prior to the one that just arrived!
        if (!isDelivered(msgSource, msgSeq)) {

          Debug.print("CO: old msg, not received yet. Source: "
              + msgSource.toString() + " Seq: " + msgSeq);
          // composing and sending the msg!
          SendableEvent se = null;

          try {
            se = (SendableEvent) Class.forName(className).newInstance();
            se.setChannel(channel);
            se.setDir(Direction.UP);
            se.setSourceSession(this);
            Message aux_om = msgFactory.newMessage();
            aux_om.setByteArray(msg, 0, msgSize);
            se.setMessage(aux_om);
            se.source = msgSource;

            se.init();
            se.go();
          } catch (AppiaEventException ex) {
            System.out.println("[CONoWaitingSession:handleSendableEventUP]:1:"
                + ex.getMessage());
          } catch (InstantiationException ex) {
            System.out.println("[CONoWaitingSession:handleSendableEventUP]:2:"
                + ex.getMessage());
          } catch (IllegalAccessException ex) {
            System.out.println("[CONoWaitingSession:handleSendableEventUP]:3:"
                + ex.getMessage());
          } catch (ClassNotFoundException ex) {
            System.out.println("[CONoWaitingSession:handleSendableEventUP]:4:"
                + ex.getMessage());
          }

          // this msg has been delivered!
          ListElement le = new ListElement(se, msgSeq);
          delivered.add(le);
          myPast.add(le);
        }

      } // fim do for

      // cloning the event just received to keep it in the mypast list
      // (with no headers)
      SendableEvent e_aux = null;
      try {
        e_aux = (SendableEvent) e.cloneEvent();
      } catch (CloneNotSupportedException ex) {
        System.out.println("[CONoWaitingSession:handleSendableEventUP]:5:"
            + ex.getMessage());
      }

      // it's now proper to deliver the sendable event e,
      // as we are sure that all msg that causally precede it are
      // delivered.
      try {
        e.setMessage(om);
        e.go();
      } catch (AppiaEventException ex) {
        System.out.println("[CONoWaitingSession:handleSendableEventUP]:6:"
            + ex.getMessage());
      }

      // this msg has been delivered!
      ListElement le = new ListElement(e_aux, seq);
      delivered.add(le);

      // this msg is already in the past list. It was added on the
      // sending!!!!
      if (!e_aux.source.equals(processes.getSelfProcess().getSocketAddress()))
        myPast.add(le);

    } else {
      Debug.print("CO: Msg already delivered, let's discard it!");
    }
  }

  /*
   * Checks if the msg with seq number 'seq' and coming from 'source' has been
   * already delivered i.e, is already in the delivered list
   */
  boolean isDelivered(SocketAddress source, int seq) {

    for (int k = 0; k < delivered.size(); k++) {

      SocketAddress iwp_aux = (SocketAddress) delivered.get(k).getSE().source;
      int seq_aux = ((ListElement) delivered.get(k)).getSeq();
      if (iwp_aux.equals(source) && seq_aux == seq)
        return true;
    }

    return false;
  }
}// end CONoWaitingSession

class ListElement {
  /* the message */
  SendableEvent se;

  /* sequence number */
  int seq;

  public ListElement(SendableEvent se, int seq) {
    this.se = se;
    this.seq = seq;
  }

  SendableEvent getSE() {
    return se;
  }

  int getSeq() {
    return seq;
  }
}