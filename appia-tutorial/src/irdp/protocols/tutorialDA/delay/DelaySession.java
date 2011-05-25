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

package irdp.protocols.tutorialDA.delay;

import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.utils.Debug;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import net.sf.appia.core.*;
import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.events.channel.ChannelInit;

import java.net.SocketAddress;
import java.util.Iterator;
import java.util.LinkedList;


/**
 * 
 * For testing: if i'm rank <i>processDelayer</i>, this layer holds the
 * messages coming from rank <i>processDelayed</i>
 * 
 * @author mjmonteiro and alexp
 * 
 */
public class DelaySession extends Session {

  /**
   * Defines one tic duration. Default is 1 second. Value in milliseconds.
   */
  private final static int TIC_PERIOD = 1000;

  /**
   * Process number delaying messages
   */
  private int processDelayer = 2;

  /**
   * Process number whose message delivery should be delayed.
   */
  private int processDelayed = 0;

  /**
   * Number of tics the message should be delayed.
   */
  private int ticsDelayed = 30;

  /**
   * filled with the address of the process which msg are to be delayed!
   */
  private SocketAddress iwpDelayed = null;

  /**
   * Processes
   */
  private ProcessSet processes = null;

  /**
   * the list of the delayed msgs (unit: SendableEvent)
   */
  LinkedList<Delayed> list = new LinkedList<Delayed>();

  public DelaySession(Layer l) {
    super(l);
  }

  public void handle(Event e) {
    if (e instanceof ChannelInit)
      handleChannelInit((ChannelInit) e);
    else if (e instanceof ProcessInitEvent)
      handleProcessInitEvent((ProcessInitEvent) e);
    else if (e instanceof DelayTimer)
      handleDelayTimer((DelayTimer) e);
    else if (e instanceof DelayEvent)
      handleDelayEvent((DelayEvent) e);
    else if (e instanceof SendableEvent) {
      if (e.getDir() == Direction.UP)
        handleSendableEventUP((SendableEvent) e);
      else {
        try {
          e.go();
        } catch (AppiaEventException ex) {
          System.out.println("[DelaySession:handle]:" + ex.getMessage());
        }
      }
    } else {
      try {
        e.go();
      } catch (AppiaEventException ex) {
        System.out.println("[DelaySession:handle]:" + ex.getMessage());
      }
    }
  }

  private void handleChannelInit(ChannelInit e) {
    Debug.print("Delay: handle: " + e.getClass().getName());
    try {
      e.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    try {
      // creates a timer and sends it!!!
      DelayTimer dt = new DelayTimer(TIC_PERIOD, e.getChannel(), this);
      dt.init();
      dt.go();
    } catch (AppiaEventException ex) {
      System.out.println("[DelaySession:handleCI]:1:" + ex.getMessage());
    } catch (AppiaException ex) {
      System.out.println("[DelaySession:handleCI]:2:" + ex.getMessage());
    }

  }

  private void handleProcessInitEvent(ProcessInitEvent e) {
    Debug.print("Delay: handle: " + e.getClass().getName());

    processes = e.getProcessSet();
    if (processDelayed >= 0) {
      iwpDelayed = processes.getProcess(processDelayed).getSocketAddress();
      Debug.print("Delay: IWP delayed: " + iwpDelayed.toString());
    }

    try {
      e.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleDelayTimer(DelayTimer e) {
    if (list.size() > 0) {
      Iterator<Delayed> iter = list.iterator();
      while (iter.hasNext()) {
        Delayed d = (Delayed) iter.next();
        d.ticCounter--;
        if (d.ticCounter <= 0) {
          try {
            Debug.print("Delay: Sending delayed message!");
            d.event.go();
          } catch (AppiaEventException ex) {
            System.out.println("[DelaySession:handleDT]:1:" + ex.getMessage());
          }
          iter.remove();
        }
      }
    }

    try {
      e.go();
    } catch (AppiaEventException ex) {
      System.out.println("[DelaySession:handleDT]:2:" + ex.getMessage());
    }
  }

  private void handleSendableEventUP(SendableEvent e) {
    Debug.print("Delay: handle: " + e.getClass().getName());

    /* if i'm the delayed node and the msg comes from the blocked node */
    if ((processDelayer == processes.getSelfRank())
        && e.source.equals(iwpDelayed)) {
      if (ticsDelayed > 0) {
        // let's delay this msg
        Debug.print("Delay: Message delayed!");
        list.addLast(new Delayed(e, ticsDelayed));
      } else if (ticsDelayed == 0) {
        try {
          e.go();
        } catch (AppiaEventException ex) {
          ex.printStackTrace();
        }
      }
      // if ticsDelayed is lesser than zero then messages are discarded
    } else {
      try {
        e.go();
      } catch (AppiaEventException ex) {
        ex.printStackTrace();
      }
    }
  }

  private void handleDelayEvent(DelayEvent event) {
    processDelayer = processes.getSelfRank();
    processDelayed = event.processDelayed;
    ticsDelayed = event.ticsDelayed;

    iwpDelayed = processes.getProcess(processDelayed).getSocketAddress();
    Debug.print("Delay: IWP delayed: " + iwpDelayed.toString());
  }
}