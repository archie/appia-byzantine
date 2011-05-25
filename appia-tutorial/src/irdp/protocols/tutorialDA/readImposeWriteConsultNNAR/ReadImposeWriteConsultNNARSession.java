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

package irdp.protocols.tutorialDA.readImposeWriteConsultNNAR;

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
 * Read-Impose Write-Consult (N,N) Atomic Registers protocol.
 * 
 * @author alexp
 */
public class ReadImposeWriteConsultNNARSession extends Session {

  /**
   * Number of registers.
   */
  public static final int NUM_REGISTERS = 20;

  public ReadImposeWriteConsultNNARSession(Layer layer) {
    super(layer);
  }

  private ProcessSet correct = null;
  private int i = -1;
  private List<HashSet<SampleProcess>> writeSet = 
	  new ArrayList<HashSet<SampleProcess>>(NUM_REGISTERS);
  private boolean[] reading = new boolean[NUM_REGISTERS];
  private int[] reqid = new int[NUM_REGISTERS];
  private Object[] readval = new Object[NUM_REGISTERS];
  private Object[] v = new Object[NUM_REGISTERS];
  private int[] ts = new int[NUM_REGISTERS];
  private int[] mrank = new int[NUM_REGISTERS];

  private Channel mainchannel = null;
  private Channel pp2pchannel = null;
  private Channel pp2pinit = null;

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

  /**
   * @param event
   */
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
    i = correct.getSelfRank();

    int r;
    for (r = 0; r < NUM_REGISTERS; r++) {
      writeSet.add(new HashSet<SampleProcess>());

      reqid[r] = ts[r] = 0;
      mrank[r] = -1;
      readval[r] = null;
      v[r] = null;
      reading[r] = false;
    }
  }

  private void handleCrash(Crash event) {
    correct.setCorrect(event.getCrashedProcess(), false);

    try {
      event.go();
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }

    allAcked();
  }

  private void handleSharedRead(SharedRead event) {
    reqid[event.reg]++;
    reading[event.reg] = true;
    writeSet.get(event.reg).clear();
    readval[event.reg] = v[event.reg];

    try {
      WriteEvent ev = new WriteEvent(mainchannel, Direction.DOWN, this);
      ev.getMessage().pushObject(v[event.reg]);
      ev.getMessage().pushInt(mrank[event.reg]);
      ev.getMessage().pushInt(ts[event.reg]);
      ev.getMessage().pushInt(reqid[event.reg]);
      ev.getMessage().pushInt(event.reg);
      ev.go();
      debug("Sending WRITE for SharedRead");
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleSharedWrite(SharedWrite event) {
    reqid[event.reg]++;
    writeSet.get(event.reg).clear();

    try {
      WriteEvent ev = new WriteEvent(mainchannel, Direction.DOWN, this);
      ev.getMessage().pushObject(event.value);
      ev.getMessage().pushInt(i);
      ev.getMessage().pushInt(ts[event.reg] + 1);
      ev.getMessage().pushInt(reqid[event.reg]);
      ev.getMessage().pushInt(event.reg);
      ev.go();
      debug("Sent READ");
    } catch (AppiaEventException ex) {
      ex.printStackTrace();
    }
  }

  private void handleWriteEvent(WriteEvent event) {
    int r = event.getMessage().popInt();
    int id = event.getMessage().popInt();
    int t = event.getMessage().popInt();
    int j = event.getMessage().popInt();
    Object val = event.getMessage().popObject();

    if ((t > ts[r]) || ((t == ts[r]) && (j < mrank[r]))) {
      v[r] = val;
      ts[r] = t;
      mrank[r] = j;
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

    if (id == reqid[r]) {
      writeSet.get(r).add(p_j);

      debugAll("handleAck");

      allAcked();
    }
  }

  /**
   * When all correct processes have acked, send the return notification.
   * 
   */
  private void allAcked() {
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
      debug.println("\tts[" + i + "]=" + ts[i]);
      debug.println("\treadval[" + i + "]=" + readval[i]);
      debug.println("\twriteval[" + i + "]=" + readval[i]);
      debug.println("\treqid[" + i + "]=" + reqid[i]);
      debug.println("\treading[" + i + "]=" + reading[i]);

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

    debug.println();
  }
}