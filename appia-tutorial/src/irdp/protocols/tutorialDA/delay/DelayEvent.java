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

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Event;
import net.sf.appia.core.Session;


/**
 * Requests that the messages from the specified sender should be delayed by a
 * given value, counted in tics, that are usually seconds.
 * 
 * @author alexp
 */
public class DelayEvent extends Event {

  /**
   * Process number whose messages will be delayed.
   */
  public int processDelayed = -1;

  /**
   * Number of tics, usually seconds, the message will be delayed. A value of 0
   * signifies the messages should NOT be delayed. A value lesser than 0
   * signifies the messages should be discarded.
   */
  public int ticsDelayed = 10;

  public DelayEvent(Channel channel, int dir, Session src)
      throws AppiaEventException {
    super(channel, dir, src);
  }
}
