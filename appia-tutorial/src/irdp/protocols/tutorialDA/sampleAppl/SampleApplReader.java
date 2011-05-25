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

package irdp.protocols.tutorialDA.sampleAppl;

import irdp.protocols.tutorialDA.events.SampleSendableEvent;
import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Direction;
import net.sf.appia.core.message.Message;

import java.util.StringTokenizer;


/**
 * Class that reads from the keyboard and generates events to the appia Channel.
 * 
 * @author nuno
 */
public class SampleApplReader extends Thread {

  private SampleApplSession parentSession;
  private java.io.BufferedReader keyb;
  private String local = null;

  public SampleApplReader(SampleApplSession parentSession) {
    super();
    this.parentSession = parentSession;
    keyb = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
  }

  public void run() {
    while (true) {
      try {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
        }
        System.out.print("> ");
        local = keyb.readLine();
        if (local.equals(""))
          continue;
        StringTokenizer st = new StringTokenizer(local);
        /*
         * creates the event, push the message and sends this to the appia
         * channel.
         */
        SampleSendableEvent asyn = new SampleSendableEvent();
        Message message = asyn.getMessage();
        asyn.setCommand(st.nextToken());
        String msg = "";
        while (st.hasMoreTokens())
          msg += (st.nextToken() + " ");
        message.pushString(msg);
        asyn.asyncGo(parentSession.channel, Direction.DOWN);
      } catch (java.io.IOException e) {
        e.printStackTrace();
      } catch (AppiaEventException e) {
        e.printStackTrace();
      }
    }
  }
}
