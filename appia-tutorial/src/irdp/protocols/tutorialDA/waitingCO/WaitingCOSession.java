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

package irdp.protocols.tutorialDA.waitingCO;

import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.Debug;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import net.sf.appia.core.*;
import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.core.message.Message;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.LinkedList;


/**
 * Reliable causal order broadcast waiting algorithm.
 * 
 * Note: waiting because it might occur that a msg must wait to be delivered
 * because the msg that causally precede haven't arrived yet. Each msg has a
 * vector clock associated in order to determine which msgs precede it.
 * 
 * July 2003
 * 
 * @author MJo�oMonteiro
 */
public class WaitingCOSession extends Session {

  /*
   * Set of the correct processes.
   * 
   */
  private ProcessSet correct;

  /*
   * The list of the messages that are waiting to be delivered
   * 
   * unit: SendableEvent
   */
  private LinkedList<SendableEvent> pendingMsg;

  int[] vectorClock;

  /**
   * Standard constructor
   * 
   * @param l
   *          the CONoWaintingLayer
   */
  public WaitingCOSession(Layer l) {
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
        System.out.println("[COWaitingSession:handle]" + ex.getMessage());
      }
    }
  }

  /**
   * Handles channelInit event.
   * 
   * @param e
   *          the channelinit event just arrived.
   */
  public void handleChannelInit(ChannelInit e) {
    Debug.print("CO: handle: " + e.getClass().getName());

    try {
      e.go();
    } catch (AppiaEventException ex) {
      System.out.println("[COWaitingSession:handleCI]" + ex.getMessage());
    }

    pendingMsg = new LinkedList<SendableEvent>();
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

    vectorClock = new int[correct.getSize()];
    Arrays.fill(vectorClock, 0);

    try {
      e.go();
    } catch (AppiaEventException ex) {
      System.out.println("[COWaitingSession:handlePI]" + ex.getMessage());
    }

  }

  /**
   * Handles sendable event to be sent to the network.
   * 
   * 
   * @param e
   *          the sendable event.
   */
  public void handleSendableEventDOWN(SendableEvent e) {
    Debug.print("CO: handle: " + e.getClass().getName() + " DOWN");

    // i'm sending a msg. increments my position in the vector clock!
    vectorClock[correct.getSelfRank()]++;

    // add the vector clock to the msg from the appl
    Message om = e.getMessage();
    om.pushObject(vectorClock);

    try {
      e.go();
    } catch (AppiaEventException ex) {
      System.out.println("[COWaitingSession:handleDOWN]" + ex.getMessage());
    }

  }

  /**
   * Handles sendable event just arrived from the network.
   * 
   * @param e
   *          the sendable event.
   */
  public void handleSendableEventUP(SendableEvent e) {

    Debug.print("CO: handle: " + e.getClass().getName() + " UP");

    // get the vector clock of this msg!
    Message om = e.getMessage();
    int[] vc_msg = (int[]) om.popObject();

    if (canDeliver(correct.getRank((SocketAddress) e.source), vc_msg)) {

      try {
        e.setMessage(om);
        e.go();
      } catch (AppiaEventException ex) {
        System.out.println("[COWaitingSession:handleUP]" + ex.getMessage());
      }
      // increments vectorclock position of the source of the msg
      if (!e.source.equals(correct.getSelfProcess().getSocketAddress()))
        vectorClock[correct.getRank((SocketAddress) e.source)]++;

      // check if there are msg in the pending list that can NOW be
      // delivered!
      checkPending();
    } else {
      // we must keep the vector clock of each msg to discover when to
      // deliver the msg
      om.pushObject(vc_msg);
      e.setMessage(om);
      pendingMsg.add(e);
      Debug.print("CO:handleUP: the msg is pending!");
    }
  }

  /*
   * Auxiliar function that tests if a particular msg can be delivered or must
   * wait!
   * 
   */
  private boolean canDeliver(int rankSource, int[] vc_msg) {

    Debug.print("CO: canDeliver: ");

    boolean ret = false;

    Debug.print("CO: canDeliver: VectorClock:");
    for (int k = 0; k < vectorClock.length; k++)
      Debug.print("index: " + k + "= " + vectorClock[k]);

    Debug.print("CO: canDeliver: VectorClock of the received msg:");
    for (int k = 0; k < vc_msg.length; k++)
      Debug.print("index: " + k + "= " + vc_msg[k]);

    if (vectorClock[rankSource] >= vc_msg[rankSource] - 1)
      ret = true;

    for (int i = 0; i < vectorClock.length; i++) {

      if (i != rankSource && vc_msg[i] > vectorClock[i])
        ret = false;
    }

    return ret;
  }

  /*
   * Auxiliar function that checks if the msg that are in the pending list can
   * be delivered, and delivers them.
   * 
   */
  private void checkPending() {

    Debug.print("CO: checkPending: ");

    // this list will keep the information about which msg can be removed
    // from the pending list!!!
    boolean[] toRemove = new boolean[pendingMsg.size()];
    Arrays.fill(toRemove, false);
    SendableEvent e_aux;

    // runs through the pending List to search for msgs that can already be
    // delivered
    for (int i = 0; i < pendingMsg.size(); i++) {
      e_aux = pendingMsg.get(i);
      Message om = e_aux.getMessage();
      int[] vc_msg = (int[]) om.popObject();

      int sourceRank = correct.getRank((SocketAddress) e_aux.source);

      if (canDeliver(sourceRank, vc_msg)) {
        try {
          e_aux.go();
        } catch (AppiaEventException ex) {
          System.out.println("[COWaitingSession:check]" + ex.getMessage());
        }
        // increments vectorclock position of the source of the msg
        if (!e_aux.source.equals(correct.getSelfProcess().getSocketAddress()))
          vectorClock[correct.getRank((SocketAddress) e_aux.source)]++;

        toRemove[i] = true;
      } else {
        // this msg still has to wait...
        om.pushObject(vc_msg);
        e_aux.setMessage(om);
      }
    }

    int countRemoved = 0;
    // now, let's check the toRemove list to clean the pendingMsg list
    for (int k = 0; k < toRemove.length; k++) {
      if (toRemove[k]) {
        pendingMsg.remove(k - countRemoved);
        countRemoved++;
      }
    }

  }

}