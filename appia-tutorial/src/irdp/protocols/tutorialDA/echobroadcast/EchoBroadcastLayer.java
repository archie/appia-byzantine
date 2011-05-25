package irdp.protocols.tutorialDA.echobroadcast;

import irdp.protocols.tutorialDA.events.EchoBroadcastEvent;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelClose;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;

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
		evProvide = new Class[] { EchoBroadcastEvent.class,
		          RegisterSocketEvent.class};
		evAccept = new Class[] { 
				ChannelInit.class, 
				EchoBroadcastEvent.class, 
                RegisterSocketEvent.class,
				ChannelClose.class 
		};
	}

	public Session createSession() {
		return new EchoBroadcastSession(this);
	}

}
