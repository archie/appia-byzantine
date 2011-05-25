package net.sf.appia.protocols.byzantineconsistentchannel;

import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelClose;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;
import net.sf.appia.protocols.echobroadcast.EchoBroadcastEvent;

/** 
 * @author EMDC
 */
public class BByzantineConsistentChannelLayer extends Layer {

	/**
	 * Default constructor which registers
	 * events that the EchoBroadcast layer is interested in. 
	 */
	public BByzantineConsistentChannelLayer() {
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
		return new BByzantineConsistentChannelSession(this);
	}

}
