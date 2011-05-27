package irdp.protocols.tutorialDA.byzantineconsistentchannel;

import irdp.protocols.tutorialDA.echobroadcast.EchoBroadcastLayer;
import irdp.protocols.tutorialDA.echobroadcast.EchoBroadcastSession;
import irdp.protocols.tutorialDA.events.EchoBroadcastEvent;
import irdp.protocols.tutorialDA.signing.SignatureLayer;
import irdp.protocols.tutorialDA.signing.SignatureSession;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;

import java.net.SocketAddress;

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
import net.sf.appia.core.events.channel.ChannelInit;

/**
 * Byzantine Consistent Channel abstraction as defined in algorithm 3.19
 * @author EMDC
 */
public class ByzantineConsistentChannelSession extends Session {


	// From the algo: N[]
	protected int [] sequenceNumbers;

	// Instances of Byzantine Consistent Broadcast (bcb) sessions
	protected EchoBroadcastSession [] bcbs;

	// Instances of Byzantine Consistent Broadcast (Layers)
	protected EchoBroadcastLayer [] bcls;

	// Signature session that lives below the bcb instances
	protected SignatureSession sigsession;

	// Signature layer that lives below the bcb instances
	protected SignatureLayer siglayer;

	// Channels used for each of the bcb instances.
	protected Channel [] childChannels;

	// A flag to indicate if the childChannels are ready
	protected boolean childChannelsReady = false;

	// From the algo: Ready
	protected boolean ready;

	// Set of processes in the system
	protected ProcessSet processes;

	// The channel that includes this session, which branches
	// out into the childChannels declared above.
	protected Channel channel;


	public ByzantineConsistentChannelSession(Layer layer) {
		super(layer);
	}

	/**
	 * Initialize processes and signature related parameters.
	 * @param set - The set of processes to which we belong
	 * @param userKeystore - Each process have a unique alias
	 * @param usercerts - A set of certificates for the trusted entities (each in the process set)
	 */
	public void init(ProcessSet set, String userKeystore, String usercerts) {		
		processes = set;
		bccInit (userKeystore, usercerts);		
	}


	private void bccInit (String userKeystore, String usercerts) {
		siglayer = new SignatureLayer();
		sigsession = new SignatureSession(siglayer);
		sigsession.init(EchoBroadcastSession.PROCESS_ALIAS_PREFIX + EchoBroadcastSession.getMyProcessRank(processes), userKeystore, "123456", usercerts, "123456", true);
		ready = true;	
		sequenceNumbers = new int [processes.getAllProcesses().length];
		bcbs = new EchoBroadcastSession [processes.getAllProcesses().length];
		bcls = new EchoBroadcastLayer [processes.getAllProcesses().length];
		childChannels = new Channel [processes.getAllProcesses().length];

		for (int i = 0; i < processes.getAllProcesses().length; i++)
		{
			bcls[i] = new EchoBroadcastLayer();
			bcbs[i] = new EchoBroadcastSession(bcls[i]);
			bcbs[i].init(processes, usercerts, "123456");			
		}
	}

	/**
	 * Appia callback to handle events which this layer have registered. 
	 * @param event
	 */
	public void handle(Event event) {
		if (event instanceof ChannelInit) {
			handleChannelInit((ChannelInit)event);
		} else if (event instanceof EchoBroadcastEvent) {
			handleEchoBroadcastEvent((EchoBroadcastEvent) event);
		} else {
			try {
				event.go();
			} catch (AppiaEventException appiaerror) {
				appiaerror.printStackTrace();
			}
		}
	}


	/**
	 * Including a Byzantine Consistent Channel instance in your
	 * stack will initialise multiple instances of Byzantine Consistent
	 * Broadcast (bcb) sessions, and a signature layer below them.
	 * We use sub-channels to de-multiplex events into the appropriate
	 * bcb session as required.
	 * @param event
	 */
	public void handleChannelInit(ChannelInit event) {

		// Only do this once.
		if (!childChannelsReady)
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
	 * @param echoEvent
	 */
	public void echoBroadcast(EchoBroadcastEvent echoEvent)
	{		
		while(true)
		{

			if (ready == true)
			{				
				ready = false;

				/*
				 * Set appropriate child channel before transmitting
				 */
				echoEvent.setChannel(childChannels[processes.getSelfRank()]);
				bcbs[processes.getSelfRank()].reset ();
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

		echoEvent.setText(echoEvent.getText() + " label:"+ (sequenceNumbers[processes.getRank(sa)] - 1));

		try {			
			echoEvent.go ();
		} catch (AppiaEventException e) {			
			e.printStackTrace();
		}


	}
}
