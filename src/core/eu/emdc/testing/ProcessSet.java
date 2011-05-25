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

package eu.emdc.testing;

import eu.emdc.testing.ProcessSet;
import eu.emdc.testing.SampleProcess;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A set of sample processes.
 * 
 * @author nuno
 */
public class ProcessSet implements Serializable {
  private static final long serialVersionUID = -8520712350015155147L;

  SampleProcess[] processes;
  private int self;

  public static ProcessSet buildProcessSet(String filename, int selfProc) {
	    BufferedReader reader = null;
	    try {
	      reader = new BufferedReader(new InputStreamReader(new FileInputStream(
	          filename)));
	    } catch (FileNotFoundException e) {
	      e.printStackTrace();
	      System.exit(0);
	    }
	    String line;
	    StringTokenizer st;
	    boolean hasMoreLines = true;
	    ProcessSet set = new ProcessSet();
	    // reads lines of type: <process number> <IP address> <port>
	    while(hasMoreLines) {
	      try {
	        line = reader.readLine();
	        if (line == null)
	          break;
	        st = new StringTokenizer(line);
	        if (st.countTokens() != 3) {
	          System.err.println("Wrong line in file: "+st.countTokens());
	          continue;
	        }
	        int procNumber = Integer.parseInt(st.nextToken());
	        InetAddress addr = InetAddress.getByName(st.nextToken());
	        int portNumber = Integer.parseInt(st.nextToken());
	        boolean self = (procNumber == selfProc);
	        SampleProcess process = new SampleProcess(new InetSocketAddress(addr,
	            portNumber), procNumber, self);
	        set.addProcess(process, procNumber);
	      } catch (IOException e) {
	        hasMoreLines = false;
	      } catch (NumberFormatException e) {
	        System.err.println(e.getMessage());
	      }
	    } // end of while
	    return set;
	  }
  
  /**
   * Constructor of the class.
   * 
   * @param n
   *          number of processes.
   */
  public ProcessSet(int n) {
    processes = new SampleProcess[n];
  }

  /**
   * 
   */
  public ProcessSet() {
    processes = new SampleProcess[0];
  }

  /**
   * Gets an array with all processes.
   * 
   * @return Array with all processes
   */
  public SampleProcess[] getAllProcesses() {
    return processes;
  }

  /**
   * Gets the number of processes.
   * 
   * @return number of processes
   */
  public int getSize() {
    return processes.length;
  }

  /**
   * Gets the rank of the specified process.
   * 
   * @param addr
   *          the address of the process
   * @return the rank of the process
   */
  public int getRank(SocketAddress addr) {
    for (int i = 0; i < processes.length; i++) {
      if ((processes[i] != null) && processes[i].getSocketAddress().equals(addr))
        return i;
    }
    return -1;
  }

  /**
   * Adds a process into the process set.
   * 
   * @param process
   *          the process to add.
   * @param pr
   *          the rank of the process.
   */
  public void addProcess(SampleProcess process, int pr) {
    if (pr >= processes.length) {
      SampleProcess[] temp = new SampleProcess[processes.length + 1];
      for (int i = 0; i < processes.length; i++)
        temp[i] = processes[i];
      processes = temp;
    }
    processes[pr] = process;
    if (process.isSelf())
      self = pr;
  }

  /**
   * Sets the process specified by the rank "proc" to correct or crashed.
   * 
   * @param proc
   *          the process rank.
   * @param correct
   *          true if the process is correct, false if the process crashed.
   */
  public void setCorrect(int proc, boolean correct) {
    processes[proc].setCorrect(correct);
  }

  /**
   * Gets the process with rank "i"
   * 
   * @param i
   *          the process rank
   * @return the process
   */
  public SampleProcess getProcess(int i) {
    return processes[i];
  }

  /**
   * Gets the process with address "addr".
   * 
   * @param addr
   *          the process address
   * @return the process.
   */
  public SampleProcess getProcess(SocketAddress addr) {
    int i = getRank(addr);
    if (i == -1)
      return null;
    else
      return processes[i];
  }

  /**
   * Gets the self rank.
   * 
   * @return My rank
   */
  public int getSelfRank() {
    return self;
  }

  /**
   * Gets the self process.
   * 
   * @return My process
   */
  public SampleProcess getSelfProcess() {
    return processes[self];
  }
  
  public Object [] getAllSockets () {
	  List<SocketAddress> temp = new ArrayList<SocketAddress>();
	  for (int i = 0; i < processes.length; i++) {
	      if ((processes[i] != null) && processes[i].getSocketAddress() != null)
	        temp.add(processes[i].getSocketAddress());
	    }
	  
	  return temp.toArray();
  }

  /**
   * Clones the process set.
   * 
   * @return a clone of the process set.
   */
  public ProcessSet cloneProcessSet() {
    ProcessSet set = new ProcessSet(getSize());
    SampleProcess[] procs = getAllProcesses();
    for (int i = 0; i < procs.length; i++)
      set.addProcess(procs[i].cloneProcess(), i);
    set.self = self;
    return set;
  }

}
