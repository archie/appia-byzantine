package eu.emdc.testing;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;
import net.sf.appia.xml.interfaces.InitializableSession;
import net.sf.appia.xml.utils.SessionProperties;

public class ApplicationSession extends Session implements InitializableSession {


	private Channel channel;
	private int localPort;
	
	public ApplicationSession(Layer layer) {
		super(layer);		
	}

	@Override
	public void init(SessionProperties params) {
		
	}
	
	public void handle(Event event) {
		if (event instanceof ChannelInit) {
			handleChannelEvent((ChannelInit) event);
		} else if (event instanceof RegisterSocketEvent) {
			handleRegisterSocketEvent((RegisterSocketEvent) event);
		} else {
			try {
				event.go();
			} catch (AppiaEventException appiaerror) {
				appiaerror.printStackTrace();
			}
		}
	}
	
	private void handleChannelEvent(ChannelInit event) {
		channel = ((ChannelInit) event).getChannel();
		try {
			event.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
		
		try {
			new RegisterSocketEvent(channel, Direction.DOWN, this, localPort);
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	private void handleRegisterSocketEvent(RegisterSocketEvent event) {
		if (event.error) {
			System.err.println("Error registering socket");
			System.exit(-1);
		} else {
			// start debugging shell
			System.out.println("Started application ... ");
		}
	}

}
