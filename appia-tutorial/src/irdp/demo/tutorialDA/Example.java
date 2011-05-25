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

package irdp.demo.tutorialDA;

import irdp.protocols.tutorialDA.print.BoundedPrintLayer;
import irdp.protocols.tutorialDA.print.PrintApplicationLayer;
import irdp.protocols.tutorialDA.print.PrintLayer;
import net.sf.appia.core.Appia;
import net.sf.appia.core.AppiaDuplicatedSessionsException;
import net.sf.appia.core.AppiaInvalidQoSException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Layer;
import net.sf.appia.core.QoS;


/**
 * This is the MAIN class to run the Print protocols.
 * 
 * @author alexp
 */
public class Example {

  public static void main(String[] args) {
    /* Create layers and put them on a array */
    Layer[] qos = {new PrintLayer(), new BoundedPrintLayer(),
        new PrintApplicationLayer()};

    /* Create a QoS */
    QoS myQoS = null;
    try {
      myQoS = new QoS("Print_stack", qos);
    } catch (AppiaInvalidQoSException ex) {
      ex.printStackTrace();
      System.exit(1);
    }

    /* Create a channel. Uses default event scheduler. */
    Channel channel = myQoS.createUnboundChannel("Print_Channel");
    try {
      channel.start();
    } catch (AppiaDuplicatedSessionsException ex) {
      System.err.println("Error in start");
      System.exit(1);
    }

    /* All set. Appia main class will handle the rest. */
    System.out.println("Starting Appia...");
    Appia.run();
  }
}
