package eu.emdc.testing;


import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;

public class ApplicationSession extends Session {


	private Channel channel;
	private int localPort;
	
	public ApplicationSession(Layer layer) {
		super(layer);
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
			new RegisterSocketEvent(channel, Direction.DOWN, this, localPort).go();
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
