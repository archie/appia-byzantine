package irdp.protocols.tutorialDA.byzantineconsistentchannel;

import irdp.protocols.tutorialDA.echobroadcast.ByzantineEchoBroadcastLayer;
import irdp.protocols.tutorialDA.echobroadcast.ByzantineEchoBroadcastSession;
import irdp.protocols.tutorialDA.events.EchoBroadcastEvent;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.signing.SignatureLayer;
import irdp.protocols.tutorialDA.signing.SignatureSession;
import irdp.protocols.tutorialDA.utils.ProcessSet;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.appia.core.AppiaCursorException;
import net.sf.appia.core.AppiaDuplicatedSessionsException;
import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.AppiaInvalidQoSException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.ChannelCursor;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.QoS;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.AppiaMulticast;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;
import net.sf.appia.protocols.tcpcomplete.TcpCompleteLayer;
import net.sf.appia.protocols.tcpcomplete.TcpCompleteSession;
import net.sf.appia.xml.AppiaXML;
import net.sf.appia.xml.interfaces.InitializableSession;
import net.sf.appia.xml.utils.SessionProperties;

/**
 * Echo Broadcast Layer
 * @author EMDC
 * @param <layerType>
 */
public class BByzantineConsistentChannelSession extends Session implements InitializableSession {

		
	// From the algo: N[]
	private int [] sequenceNumbers;
	
	// Instances of Byzantine Consistent Broadcast (bcb) sessions
	private ByzantineEchoBroadcastSession [] bcbs;
	
	// Instances of Instances of Byzantine Consistent Broadcast (Layers)
	private ByzantineEchoBroadcastLayer [] bcls;
	
	// Signature session that lives below the bcb instances
	private SignatureSession sigsession;
	
	// Signature layer that lives below the bcb instances
	private SignatureLayer siglayer;
	
	// Channels used for each of the bcb instances.
	private Channel [] childChannels;

	// A flag to indicate if the childChannels are ready
	private boolean childChannelsReady = false;
	
	// From the algo: Ready
	boolean ready;
	
	// Set of processes in the system
	private ProcessSet processes;
	
	// The channel that includes this session, which branches
	// out into the childChannels declared above.
	Channel channel;
		
	
	public BByzantineConsistentChannelSession(Layer layer) {
		super(layer);
	}
	
	public void init(SessionProperties params) {
	
		processes = ProcessSet.buildProcessSet(params.getProperty("processes"),
				Integer.parseInt(params.getProperty("myrank")));
		
		/* TODO: XML thing is broken. FIXME*/
		//bccInit (processes, params.getProperty("processes"), Integer.parseInt(params.getProperty("myrank")));
	}
	
	/**
	 * Initialise processes and signature related parameters.
	 */
	public void init(String processfile, int rank, String alias, String usercerts) {
		init(processfile, rank, alias, usercerts);
	}
	
	public void init(ProcessSet set, int rank, String alias, String usercerts, String testCase) {
		processes = set;
		bccInit (rank, alias, usercerts, testCase);
	}

	
	private void bccInit (int rank, String alias, String usercerts, String testCase)
	{
		siglayer = new SignatureLayer();
		sigsession = new SignatureSession(siglayer);
		sigsession.init(alias, alias, "123456", usercerts, "123456");
		ready = true;	
		sequenceNumbers = new int [processes.getAllProcesses().length];
		bcbs = new ByzantineEchoBroadcastSession [processes.getAllProcesses().length];
		bcls = new ByzantineEchoBroadcastLayer [processes.getAllProcesses().length];
		childChannels = new Channel [processes.getAllProcesses().length];
		
		for (int i = 0; i < processes.getAllProcesses().length; i++)
		{
			bcls[i] = new ByzantineEchoBroadcastLayer();
			bcbs[i] = new ByzantineEchoBroadcastSession(bcls[i]);
			bcbs[i].init(processes, rank, usercerts, "123456");
		}
	}
	
	/** 
	 * Parse incoming event and handle it accordingly.
	 */
	public void handle(Event event) {
		if (event instanceof ChannelInit) {
			handleChannelInit((ChannelInit)event);
		} else if (event instanceof ProcessInitEvent) {
			handleProcessInitEvent((ProcessInitEvent) event);
		} else if (event instanceof EchoBroadcastEvent) {
			handleEchoBroadcastEvent((EchoBroadcastEvent) event);
		} else if (event instanceof RegisterSocketEvent) {
	         handleRSE((RegisterSocketEvent) event);
		} else {
			try {
				event.go();
			} catch (AppiaEventException appiaerror) {
				appiaerror.printStackTrace();
			}
		}
	}
	
	/*
	 * From the ProcessSet abstraction.
	 */
	private void handleProcessInitEvent(ProcessInitEvent event) {
		try {
			event.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	private void handleRSE(RegisterSocketEvent event) {
		try {
			event.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Including a Byzantine Consistent Channel instance in your
	 * stack will initialise multiple instances of Byzantine Consistent
	 * Broadcast (bcb) sessions, and a signature layer below them.
	 * We use sub-channels to de-multiplex events into the appropriate
	 * bcb session as required.
	 */
	public void handleChannelInit(ChannelInit event) {
		
		// Only do this once.
		if (childChannelsReady == false)
		{
			channel = ((ChannelInit) event).getChannel();
	
			// We need to insert the bcbs and signature layers
			// between "this" layer and the layer below it (usually a
			// TcpCompleteLayer).
			Layer layerBelowMe = null;
			Session sessionBelowMe = null;
			
			try {
			
				ChannelCursor cc = channel.getCursor();
				cc.bottom();
				
				/* Find a pointer to this layer */
				while (!cc.getSession().equals(this))
				{
					cc.up();
				}		
		
				cc.down ();
				layerBelowMe = cc.getLayer(); // Obtain layer
				sessionBelowMe = cc.getSession(); // Obtain session
			
			} catch (AppiaCursorException e1) {				
				e1.printStackTrace();
			}
			
			SignatureLayer siglayer = new SignatureLayer ();
			
			/*
			 * From the algo: Instantiate as many instances of Byzantine
			 * consistent broadcast as there are processes in the system.
			 */
			for (int i = 0; i < processes.getAllProcesses().length; i++)
			{
				
				Layer[] qos = {layerBelowMe, siglayer, bcls[i]};
										
				QoS myQoS = null;
				
				try {
				myQoS = new QoS("byz stack", qos);
				} catch (AppiaInvalidQoSException ex) {
					System.err. println("Invalid QoS");
					System.err. println(ex.getMessage());
					System.exit(1);
				}
				childChannels[i] = myQoS.createUnboundChannel("Child Channel" + i);
				
				// Obtain cursor to child channel
				ChannelCursor cc = childChannels[i].getCursor();
				try {
					// Session below current layer remains bottom-most.
					cc.bottom();
					cc.setSession(sessionBelowMe);
					
					// ... on top of which we have the signature layer
					cc.up();
					cc.setSession(sigsession);
					
					// ... on top of which we have the ith bcb instance
					cc.up();
					cc.setSession(bcbs[i]);
				} catch (AppiaCursorException e) {
					e.printStackTrace();
				}
	
				/*
				 * Set the child channel for the bcb for tx-path and
				 * our normal channel for the rx-path.
				 */
				bcbs[i].setChannels(childChannels[i], channel);
				try {
					childChannels[i].start();
				} catch (AppiaDuplicatedSessionsException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			try {
		            event.go();
		    } catch (AppiaEventException e) {
		            e.printStackTrace();
		    }
		    childChannelsReady = true;
		}
	}
	
	/** 
	 * Initiate a broadcast of a message
	 */
	public void echoBroadcast(EchoBroadcastEvent echoEvent)
	{		
		/* TODO: Make sure two consequent requests are pipelined */
		while(true)
		{
			if (ready == true)
			{				
				ready = false;

				/*
				 * Set appropriate child channel before transmitting
				 */
				echoEvent.setChannel(childChannels[processes.getSelfRank()]);			
				try {
					echoEvent.init ();
					echoEvent.go ();
				} catch (AppiaEventException e) {				
					e.printStackTrace();
				}

				break;
			}

			/*
			 * If the session is already in the middle of a broadcast (ready == false)
			 * then wait a little before re-trying to transmit.
			 */
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void handleEchoBroadcastEvent(EchoBroadcastEvent event) {
		if (event.getDir() == Direction.DOWN) {
			echoBroadcast(event);
		} else if (event.getDir() == Direction.UP) {
			pp2pdeliver(event);	
		}
	}
	
	private void pp2pdeliver(EchoBroadcastEvent echoEvent) {

		/*
		 * Re-initialise another instance for the pth bcb instance,
		 * where p is the process ID of the process that initiated
		 * this broadcast.
		 */
		SocketAddress sa = (SocketAddress) echoEvent.source;
		sequenceNumbers[processes.getRank(sa)]++;
		
		bcbs[processes.getRank(sa)].reset();
		
		/*
		 * If we initiated the broadcast, and its done, we're now ready again.
		 */
		if (processes.getRank(sa) == processes.getSelfProcess().getProcessNumber())
		{
			ready = true;
		}
		
		try {
			echoEvent.go ();
		} catch (AppiaEventException e) {	
			e.printStackTrace();
		}

		
	}
}
