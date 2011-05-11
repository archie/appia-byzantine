package net.sf.appia.protocols.echobroadcast;

import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelClose;
import net.sf.appia.core.events.channel.ChannelInit;

/** 
 * @author EMDC
 */
public class EchoBroadcastLayer extends Layer {

	/**
	 * Default constructor which registers
	 * events that the EchoBroadcast layer is interested in. 
	 */
	public EchoBroadcastLayer() {
		super();
		evRequire = new Class[] { ChannelInit.class };
		evProvide = new Class[] { };
		evAccept = new Class[] { 
				ChannelInit.class
				, ChannelClose.class 
				};
	}
	
	public Session createSession() {
		return new EchoBroadcastSession(this);
	}

}
