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

package irdp.protocols.tutorialDA.gcPastCO;

import irdp.protocols.tutorialDA.events.Crash;
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
 * Reliable causal order broadcast no-waiting algorithm with garbage collection.
 * 
 * Note: no-waiting because each msg carries the ones that causally precede it.
 * So, no msg has to wait for others to arrive in order to be delivered.
 * 
 * Note: garbage collection, to fight back the continously growing of the past
 * set
 * 
 * 
 * July 2003
 * 
 * @author MJo�oMonteiro
 * 
 */
public class GCPastCOSession extends Session {
  private Channel channel;
  private int seqNumber = 0;
  /*
   * Set of delivered messages.
   * 
   * unit: sendable event+seq number (ListElement)
   */
  private LinkedList<ListElement> delivered;
  /*
   * Set of messages processed by this element. These messages will causally
   * precede the ones to be sent by this element.
   * 
   * unit: sendable event+seq number (ListElement)
   */
  private LinkedList<ListElement> myPast;
  /*
   * Set of the correct processes.
   * 
   */
  private ProcessSet correct;
  /*
   * Set of the msgs not yet acked by all correct processes.
   * 
   * unit: seq number+source+list of processes acked (AckElement)
   */
  private LinkedList<AckElement> acks;

  private MessageFactory msgFactory = null;
  /**
   * Standard constructor
   * 
   * @param l
   *          the CONoWaintingLayer
   */
  public GCPastCOSession(Layer l) {
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
    else if (e instanceof AckEvent)
      handleAck((AckEvent) e);
    else if (e instanceof SendableEvent) {
      if (e.getDir() == Direction.DOWN)
        handleSendableEventDOWN((SendableEvent) e);
      else
        handleSendableEventUP((SendableEvent) e);
    } else if (e instanceof Crash)
      handleCrash((Crash) e);
    else {
      try {
        e.go();
      } catch (AppiaEventException ex) {
        System.out.println("[CONoWaitingGCSession:handle]" + ex.getMessage());
      }
    }
  }

  /**
   * Handles channelInit event. Initializes the three lists.
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
      System.out.println("[CONoWaitingGCSession:handleCI]" + ex.getMessage());
    }

    this.channel = e.getChannel();

    delivered = new LinkedList<ListElement>();
    myPast = new LinkedList<ListElement>();
    acks = new LinkedList<AckElement>();
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

    correct = e.getProcessSet();
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

    // cloning the event to be sent in order to keep it in the mypast
    // list...
    // (with no headers...)
    SendableEvent e_aux = null;
    try {
      e_aux = (SendableEvent) e.cloneEvent();
    } catch (CloneNotSupportedException ex) {
      System.out.println("CONoWaitingGC:handleSEdown:" + ex.getMessage());
    }

    Message om = e.getMessage();

    // inserting myPast list in the msg:
    for (int k = myPast.size(); k > 0; k--) {
      Message om_k = myPast.get(k - 1).getSE().getMessage();

      // inserting the message
      om.pushObject(om_k.toByteArray());
      // inserting the size of the message
      om.pushInt(om_k.toByteArray().length);
      // inserting the sequence number of this msg
      om.pushInt(((ListElement) myPast.get(k - 1)).getSeq());
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
    e.setMessage(om);

    // sending the event!
    try {
      e.go();
    } catch (AppiaEventException ex) {
      System.out.println("[CONoWaitingGCSession:handleSendableEventDOWN]"
          + ex.getMessage());
    }

    // add this message to the myPast list:
    // but only with the original msg from the application
    // in order to keep this msg we must fill the source field in the
    // event...
    // (this source is filled only in the lower protocols)
    e_aux.source = correct.getSelfProcess().getSocketAddress();
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

          Debug.print("CO: msg antiga AINDA n�o recebida: Source: "
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
            System.out
                .println("[CONoWaitingGCSession:handleSendableEventUP]:1:"
                    + ex.getMessage());
          } catch (InstantiationException ex) {
            System.out
                .println("[CONoWaitingGCSession:handleSendableEventUP]:2:"
                    + ex.getMessage());
          } catch (IllegalAccessException ex) {
            System.out
                .println("[CONoWaitingGCSession:handleSendableEventUP]:3:"
                    + ex.getMessage());
          } catch (ClassNotFoundException ex) {
            System.out
                .println("[CONoWaitingGCSession:handleSendableEventUP]:4:"
                    + ex.getMessage());
          }

          // this msg has been delivered!
          ListElement le = new ListElement(se, msgSeq);
          delivered.add(le);
          myPast.add(le);

          // let's send the ACK for this msg
          sendAck(le);
        }

      }

      // cloning the event just received to keep it in the mypast list
      // (with no headers)
      SendableEvent e_aux = null;
      try {
        e_aux = (SendableEvent) e.cloneEvent();
      } catch (CloneNotSupportedException ex) {
        System.out.println("[CONoWaitingGCSession:handleSendableEventUP]:5:"
            + ex.getMessage());
      }

      // it's now proper to deliver the sendable event e,
      // as we are sure that all msg that causally precede it are
      // delivered.
      try {
        e.setMessage(om);
        e.go();
      } catch (AppiaEventException ex) {
        System.out.println("[CONoWaitingGCSession:handleSendableEventUP]:6:"
            + ex.getMessage());
      }

      // this msg has been delivered!
      ListElement le = new ListElement(e_aux, seq);
      delivered.add(le);

      // this msg is already in the past list. It was added on the
      // sending!!!!
      if (!e_aux.source.equals(correct.getSelfProcess().getSocketAddress()))
        myPast.add(le);

      Debug.print("Past list content:");
      if (myPast.size() == 0)
        Debug.print("EMPTY");

      for (int k = 0; k < myPast.size(); k++) {
        Debug.print("index:" + k);
        Debug.print("source: "
            + ((ListElement) myPast.get(k)).getSE().source.toString());
        Debug.print("seq: " + ((ListElement) myPast.get(k)).getSeq());
      }

      // let's send the ACK for this msg
      sendAck(le);

      return;
    } else {
      Debug.print("CO: Msg already delivered, let's discard it!");
    }

  }

  /*
   * Auxiliar function, that sends the ackevent for all correct processes
   * 
   */
  private void sendAck(ListElement le) {

    /* let's check if the entry for this msg already exists in the acks list */
    int index = -1;
    // search for it in the acks list:
    
    for (int i = 0; i < acks.size(); i++) {
      if (acks.get(i).seq == le.seq
          && acks.get(i).source
              .equals(le.se.source)) {
        index = i;
        i = acks.size(); // exit
      }
    }

    if (index == -1) {
      // the entry for this msg doesn't exist yet in the acks list
      // let's create it!
      AckElement ael = new AckElement(le.seq, (SocketAddress) le.se.source);
      acks.add(ael);
      index = acks.size() - 1;
    }

    /* let's register our ack for this msg in the proper list */
    ((AckElement) acks.get(index)).regAck(correct.getSelfProcess().getSocketAddress());

    /* now let's send the ack event */
    AckEvent ae = null;
    try {
      ae = new AckEvent(channel, Direction.DOWN, this);

      Message om = msgFactory.newMessage();
      om.pushObject(le.se.source);
      om.pushInt(le.seq);
      ae.setMessage(om);
      ae.init();
      ae.go();
    } catch (AppiaEventException ex) {
      System.out
          .println("[CONoWaitingGCSession:handleSendableEventUP]:sendAck:1:"
              + ex.getMessage());
    }
  }

  /*
   * Checks if the msg with seq number 'seq' and coming from 'source' has been
   * already delivered i.e, is already in the delivered list
   */
  boolean isDelivered(SocketAddress source, int seq) {
	  for(ListElement element : delivered){
		  if(element.getSE().source.equals(source) && element.getSeq() == seq)
			  return true;
	  }
	  return false;
  }

  /**
   * Handles the ack event and registers its arrival. If all correct processes
   * have already ACK'ed this msg, remove it from the past set.
   * 
   * @param e
   *          the AckEvent just arrived
   */
  public void handleAck(AckEvent e) {
    Debug.print("CO: handle: " + e.getClass().getName());

    // my ACK was already registered when the AckEvent was sent
    if (e.source.equals(correct.getSelfProcess().getSocketAddress()))
      return;

    Message om = e.getMessage();
    int seq = om.popInt();
    SocketAddress iwp = (SocketAddress) om.popObject();

    /* let's check if the entry for this msg already exists in the acks list */
    int index = -1;
    // search for it in the acks list:
    for (int i = 0; i < acks.size(); i++) {
      if (acks.get(i).seq == seq && acks.get(i).source.equals(iwp)) {
        index = i;
        i = acks.size();
      }
    }

    if (index == -1) {
      // the entry for this msg doesn't exist yet in the acks list
      // let's create it!
      AckElement ael = new AckElement(seq, iwp);
      acks.add(ael);
      index = acks.size() - 1;
    }

    /* let's register this ack for this msg in the proper list */
    acks.get(index).regAck((SocketAddress) e.source);

    Debug.print("CO: Number of acked processes: "
        + ((AckElement) acks.get(index)).processes.size());

    /* if all correct processes have already acked this msg */
    if (getCorrectSize() == ((AckElement) acks.get(index)).processes.size()) {

      Debug.print("CO: All correct processes have confirmed.");

      /* removes the entry for this msg from the myPast list */
      for (int k = 0; k < myPast.size(); k++) {
        if (myPast.get(k).se.source.equals(iwp)
            && myPast.get(k).seq == seq) {
          myPast.remove(k);
          k = myPast.size();
        }
      }
      /* removes the entry for this msg from the acks list */
      acks.remove(index);
    }

    Debug.print("past contents:");
    if (myPast.size() == 0)
      Debug.print("EMPTY!");

    for (int k = 0; k < myPast.size(); k++) {
      Debug.print("index:" + k);
      Debug.print("source: "
          + myPast.get(k).getSE().source.toString());
      Debug.print("seq: " + myPast.get(k).getSeq());
    }
  }

  /**
   * Handles a crash notice coming from the perfect failure detector below!
   * 
   * @param e
   *          the crash notice just arrived.
   */
  public void handleCrash(Crash e) {
    Debug.print("CO: handle: " + e.getClass().getName());

    // marking the process that crashed as not correct!
    correct.setCorrect(e.getCrashedProcess(), false);

    try {
      e.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private int getCorrectSize() {
    int i;
    int count = 0;
    for (i = 0; i < correct.getSize(); i++)
      if (correct.getProcess(i).isCorrect())
        count++;
    return count;
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

class AckElement {

  /* sequence number */
  int seq;

  /* source of the message */
  SocketAddress source;

  /* the set of processes that already acked this msg */
  LinkedList<SocketAddress> processes;

  public AckElement(int seq, SocketAddress source) {
    this.seq = seq;
    this.source = source;
    processes = new LinkedList<SocketAddress>();
  }

  void regAck(SocketAddress p) {
    processes.add(p);
  }
}