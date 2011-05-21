package eu.emdc.testing;


import java.net.InetSocketAddress;
import java.util.Date;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;
import net.sf.appia.protocols.echobroadcast.EchoBroadcastEvent;
import net.sf.appia.test.xml.ecco.MyShell;
import net.sf.appia.xml.utils.SessionProperties;

public class ApplicationSession extends Session {


	private Channel channel;
	private int localPort;
	private ProcessSet processes;
    private ApplicationShell shell;
	
	public ApplicationSession(Layer layer) {
		super(layer);
	}

	
	public void handle(Event event) {
		if (event instanceof ChannelInit) {
			handleChannelEvent((ChannelInit) event);
		} else if (event instanceof RegisterSocketEvent) {
			handleRegisterSocketEvent((RegisterSocketEvent) event);
		} else if (event instanceof EchoBroadcastEvent) {
			handleEchoBroadcastEvent ((EchoBroadcastEvent) event);
		} else {
			try {
				event.go();
			} catch (AppiaEventException appiaerror) {
				appiaerror.printStackTrace();
			}
		}
	}
	
	public void init(SessionProperties params) {
		processes = ProcessSet.buildProcessSet(params.getProperty("processes"),
				Integer.parseInt(params.getProperty("myrank")));		
	}
	
	public void init(String processfile, int rank) {
		processes = ProcessSet.buildProcessSet(processfile,rank);		
	}
	
	private void handleChannelEvent(ChannelInit event) {
		channel = ((ChannelInit) event).getChannel();
				
		try {
			event.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
				 
        try {
        	InetSocketAddress temp = (InetSocketAddress) processes.getSelfProcess().getSocketAddress();
            new RegisterSocketEvent(channel,Direction.DOWN,
            		this, temp.getPort()).go();
        } catch (AppiaEventException e1) {
            e1.printStackTrace();
        }
	}

	private void handleEchoBroadcastEvent (EchoBroadcastEvent event)
	{
		if (event.getDir() == Direction.UP)
		{
			System.out.print("\n> " + event.getText()+"\n> ");
		}
		else
		{	
			try {
				event.go ();
			} catch (AppiaEventException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void handleRegisterSocketEvent(RegisterSocketEvent event) {
		if (event.error) {
			System.exit(-1);
		} else {
			// start debugging shell
			System.out.println("Started application ... ");

	        shell = new ApplicationShell (channel);
	        final Thread t = event.getChannel().getThreadFactory().newThread(shell);
	        t.setName("Ecco shell");
	        t.start();
		}
	}

}
