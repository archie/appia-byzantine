package eu.emdc.testing;

import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelClose;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;

public class ApplicationLayer extends Layer {

	
	public ApplicationLayer() {
		super();
		evRequire = new Class[] { ChannelInit.class };
		evProvide = new Class[] { RegisterSocketEvent.class };
		evAccept = new Class[] { 
				ChannelInit.class
				,RegisterSocketEvent.class
				, ChannelClose.class 
				};
	}
	
	@Override
	public Session createSession() {
		return new ApplicationSession(this);
	}
	

}
