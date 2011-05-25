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

package irdp.protocols.tutorialDA.readImposeWriteAll1NAR;

import irdp.protocols.tutorialDA.events.*;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;
import net.sf.appia.core.*;
import net.sf.appia.core.events.channel.ChannelInit;

import java.io.PrintStream;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * Session implementing the 
 * Read-Impose Write-All (1,N) Atomic Registers protocol.
 * 
 * @author alexp
 */
public class ReadImposeWriteAll1NARSession extends Session {

  /**
   * Number of registers.
   */
  public static final int NUM_REGISTERS = 20;

  public ReadImposeWriteAll1NARSession(Layer layer) {
    super(layer);
  }

  private Object[] v = new Object[NUM_REGISTERS];
  private int[] sn = new int[NUM_REGISTERS];
  private Object[] readval = new Object[NUM_REGISTERS];
  private int[] rqid = new int[NUM_REGISTERS];
  private boolean[] reading = new boolean[NUM_REGISTERS];
  private List<HashSet<SampleProcess>>writeSet = 
	  new ArrayList<HashSet<SampleProcess>>(NUM_REGISTERS);
  private ProcessSet correct = null;

  private Channel mainchannel = null;
  private Channel pp2pchannel = null;
  private Channel pp2pinit = null;

  /*
   * @see appia.Session#handle(appia.Event)
   */
  public void handle(Event event) {
    if (event instanceof ChannelInit)
      handleChannelInit((ChannelInit) event);
    else if (event instanceof ProcessInitEvent)
      handleProcessInit((ProcessInitEvent) event);
    else if (event instanceof Crash)
      handleCrash((Crash) event);
    else if (event instanceof SharedRead)
      handleSharedRead((SharedRead) event);
    else if (event instanceof SharedWrite)
      handleSharedWrite((SharedWrite) event);
    else if (event instanceof WriteEvent)
      handleWriteEvent((WriteEvent) event);
    else if (event instanceof AckEvent)
      handleAckEvent((AckEvent) event);
    else {
      debug("Unwanted event received (\"" + event + "\"), ignoring.");
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
  public void pp2pchannel(Channel c) {
    pp2pinit = c;
  }

  private void handleChannelInit(ChannelInit init) {
    if (mainchannel == null) {
      mainchannel = init.getChannel();
      debug("mainchannel initiated");
      try {
        pp2pinit.start();
      } catch (AppiaDuplicatedSessionsException ex) {
        ex.printStackTrace();
      }
    } else {
      if (init.getChannel() == pp2pinit) {
        pp2pchannel = init.getChannel();
      }
    }

    try {
      init.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleProcessInit(ProcessInitEvent event) {
    correct = event.getProcessSet();
    init();
    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void init() {
    int r;
    for (r = 0; r < NUM_REGISTERS; r++) {
      v[r] = readval[r] = null;
      sn[r] = rqid[r] = 0;
      reading[r] = false;
      writeSet.add(new HashSet<SampleProcess>());
    }
  }

  private void handleCrash(Crash event) {
    correct.setCorrect(event.getCrashedProcess(), false);

    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    allCorrect();
  }

  private void handleSharedRead(SharedRead event) {
    rqid[event.reg]++;
    reading[event.reg] = true;
    readval[event.reg] = v[event.reg];

    try {
      WriteEvent ev = new WriteEvent(mainchannel, Direction.DOWN, this);
      ev.getMessage().pushObject(v[event.reg]);
      ev.getMessage().pushInt(sn[event.reg]);
      ev.getMessage().pushInt(rqid[event.reg]);
      ev.getMessage().pushInt(event.reg);
      ev.go();
      debug("Sending WRITE for SharedRead");
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleSharedWrite(SharedWrite event) {
    rqid[event.reg]++;

    try {
      WriteEvent ev = new WriteEvent(mainchannel, Direction.DOWN, this);
      ev.getMessage().pushObject(event.value);
      ev.getMessage().pushInt(sn[event.reg] + 1);
      ev.getMessage().pushInt(rqid[event.reg]);
      ev.getMessage().pushInt(event.reg);
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleWriteEvent(WriteEvent event) {
    int r = event.getMessage().popInt();
    int id = event.getMessage().popInt();
    int tstamp = event.getMessage().popInt();
    Object val = event.getMessage().popObject();

    if (tstamp > sn[r]) {
      v[r] = val;
      sn[r] = tstamp;
    }

    try {
      AckEvent ev = new AckEvent(pp2pchannel, Direction.DOWN, this);
      ev.getMessage().pushInt(id);
      ev.getMessage().pushInt(r);
      ev.dest = event.source;
      ev.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleAckEvent(AckEvent event) {
    SampleProcess p_j = correct.getProcess((SocketAddress) event.source);
    int r = event.getMessage().popInt();
    int id = event.getMessage().popInt();

    if (id == rqid[r]) {
      writeSet.get(r).add(p_j);

      debugAll("handleAck");

      allCorrect();
    }
  }

  /**
   * When all correct processes have acked, send the return notification.
   */
  private void allCorrect() {
    int reg;
    for (reg = 0; reg < NUM_REGISTERS; reg++) {

      boolean allAcks = true;
      int i;
      for (i = 0; (i < correct.getSize()) && allAcks; i++) {
        SampleProcess p = correct.getProcess(i);
        if (p.isCorrect() && !writeSet.get(reg).contains(p))
          allAcks = false;
      }
      if (allAcks) {
        writeSet.get(reg).clear();

        if (reading[reg]) {
          reading[reg] = false;

          try {
            SharedReadReturn ev = new SharedReadReturn(mainchannel,
                Direction.UP, this);
            ev.reg = reg;
            ev.value = readval[reg];
            ev.go();

            debug("Sent WriteReturn");
          } catch (AppiaEventException ex) {
            ex.printStackTrace();
          }
        } else {
          try {
            SharedWriteReturn ev = new SharedWriteReturn(mainchannel,
                Direction.UP, this);
            ev.reg = reg;
            ev.go();

            debug("Sent WriteReturn");
          } catch (AppiaEventException ex) {
            ex.printStackTrace();
          }
        }
      }
    }
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

    for (i = 0; i < NUM_REGISTERS; i++) {
      debug.println("\tv[" + i + "]=" + v[i]);
      debug.println("\tsn[" + i + "]=" + sn[i]);
      debug.println("\treadval[" + i + "]=" + readval[i]);
      debug.println("\trqid[" + i + "]=" + rqid[i]);
      debug.println("\treading[" + i + "]=" + v[i]);

      debug.print("\twriteSet[" + i + "]=");
      for(SampleProcess p : writeSet.get(i))
          debug.print(p.getProcessNumber() + ",");      
      debug.println();
    }

    debug.print("\tcorrect=");
    for (i = 0; i < correct.getSize(); i++) {
      SampleProcess p = correct.getProcess(i);
      debug.print("[" + p.getProcessNumber() + ";" + p.getSocketAddress() + ";"
          + p.isCorrect() + "],");
    }
    debug.println();
  }
}