package irdp.protocols.tutorialDA.echobroadcast;

import irdp.protocols.tutorialDA.events.EchoBroadcastEvent;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.signing.SignatureSession;
import irdp.protocols.tutorialDA.utils.ProcessSet;

import java.io.FileInputStream;
import java.net.SocketAddress;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.AppiaMulticast;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.core.message.Message;

/**
 * Byzantine Echo Broadcast (also called Signed Echo Broadcast) defined in
 * algorithm 3.17
 * @author EMDC
 */
public class EchoBroadcastSession extends Session {

	protected Channel channel;
	protected Channel deliverToChannel = null;
	protected ProcessSet processes;
	protected KeyStore trustedStore;
    	
	protected int N = 0;
	protected int F = 1;
	
    /*
     * KeyStore, format  used to store the keys.
     * Ex: "JKS"
     */
	protected String storeType = "JKS";
	
	/*
	 * A buffer to know what messages we've already
	 * sent echos to.
	 */
	protected List<EchoBroadcastEvent> replyBuffer;
	
	/*
	 * List of echos the sender has collected for each
	 * broadcast (or sequence number).
	 */
	protected Map<Integer, List<EchoBroadcastEvent>> replyQueue;
	
	/*
	 * Implemented for safety reasons. One sequence number
	 * per broadcast.
	 */
	protected int sequenceNumber;

	/* From the algo: The below holds state information for each broadcast */	
	protected boolean sentEcho = false;
	protected boolean sentFinal = false;
	protected boolean delivered = false;
	
	/* From the algo: Sigmas is an array of signatures.
	 */
	protected String [] sigmas;
	
	/*
	 * Trusted certificates and the password for the signature related
	 * operations.
	 */
	protected String trustedCertsFile;
	protected char[] trustedCertsPass;
	
	public EchoBroadcastSession(Layer layer) {
		super(layer);
		replyBuffer = new ArrayList<EchoBroadcastEvent>();
		replyQueue = new HashMap<Integer, List<EchoBroadcastEvent>>();
		sequenceNumber = 0;
	}	
	
	/**
	 * Initialize processes and signature related parameters.
	 * @param set - The set of processes to which we belong
	 * @param alias - Each process have a unique alias
	 * @param usercerts - A set of certificates for the trusted entities (each in the process set)
	 */
	public void init(ProcessSet set, String trustedcertsfile, String trustedcertspass) {		
		processes = set;
		
    	trustedCertsFile = trustedcertsfile;
    	trustedCertsPass = trustedcertspass.toCharArray();
    	
    	try{
            
            trustedStore = KeyStore.getInstance(storeType);
            trustedStore.load(new FileInputStream(trustedCertsFile), trustedCertsPass);
    	} catch (Exception e) {
			e.printStackTrace();
		}

		sigmas = new String [processes.getAllProcesses().length];
		N = processes.getAllProcesses().length;		
	}
	
	/**
	 * Set tx and rx channels, called by Byzantine Consistent Broadcast
	 * @param fromChannel
	 * @param toChannel
	 */
	public void setChannels (Channel fromChannel, Channel toChannel)
	{
		channel = fromChannel;
		deliverToChannel = toChannel;
	}
	
	/**
	 * Appia callback to handle events which this layer have registered. 
	 * @param event
	 */
	public void handle(Event event) {
		if (event instanceof ChannelInit) {
			handleChannelInit((ChannelInit)event);
		} else if (event instanceof ProcessInitEvent) {
			handleProcessInitEvent((ProcessInitEvent) event);
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

	/* Called by ProcessSet abstraction */
	protected void handleProcessInitEvent(ProcessInitEvent event) {
		processes = event.getProcessSet();
		try {
			event.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	public void handleChannelInit(ChannelInit event) {
		channel = ((ChannelInit) event).getChannel();			
		try {
			event.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	/** 
	 * Initiate a broadcast of a message
	 */
	protected void echoBroadcast(EchoBroadcastEvent echoEvent) {

		int nextSequenceNumber = ++sequenceNumber;
						
		replyQueue.put(nextSequenceNumber, new ArrayList<EchoBroadcastEvent>());	
				
		echoEvent.setChannel(channel);
		echoEvent.setDir(Direction.DOWN);
		echoEvent.setSourceSession(this);
		echoEvent.setEcho(false);
		echoEvent.setFinal(false);
		echoEvent.setSequenceNumber(nextSequenceNumber);
		
		// This pushes all the required values to the message stack.
		echoEvent.pushValuesToMessage();
		
		/*
		 * Algorithm:
		 * 
		 * upon event < bcb, Broadcast | m > do
		 * 		for all processes do
		 * 			trigger < al, Send | q, [Send m]>;
		 */
		
		echoEvent.dest = new AppiaMulticast (null, processes.getAllSockets());
		
		try {
			echoEvent.init();
			echoEvent.go();			
		} catch (AppiaEventException eventerror) {
			eventerror.printStackTrace();
		}
	}
	
	protected void handleEchoBroadcastEvent(EchoBroadcastEvent event) {
		if (event.getDir() == Direction.DOWN) {
			echoBroadcast(event);
		} else if (event.getDir() == Direction.UP) {
			pp2pdeliver(event);
		}
	}
	
	private void pp2pdeliver(EchoBroadcastEvent echoEvent) {
		/* Pop the signature out of the message because
		 * we always have a SignatureSession below us. Failing to
		 * do so will result in bad things.
		 */
		String signature = echoEvent.getMessage().popString();
		
		// Now populate the required fields in the message.
		echoEvent.popValuesFromMessage();

		if (echoEvent.isEcho()) {
			collectEchoReply(echoEvent, signature);
		} else if (echoEvent.isFinal() && !echoEvent.isEcho()) {
			deliverFinal(echoEvent);
		} else if (!echoEvent.isEcho() && !echoEvent.isFinal()) {
			sendEchoReply(echoEvent);			
		}
	}
	
	protected void sendEchoReply(EchoBroadcastEvent echoEvent) {
		if (alreadyReplied(echoEvent)) {
			return;
		}
		
		sentEcho = true;
		
		EchoBroadcastEvent reply = new EchoBroadcastEvent();
		
		// Need to sign the below. This is done by the signature
		// layer below us.
		reply.setEcho(true);
		reply.setSequenceNumber(echoEvent.getSequenceNumber());
		reply.dest = echoEvent.source;
		reply.setSourceSession(this);
		reply.setChannel(channel);
		reply.setDir(Direction.DOWN);
		reply.setText(echoEvent.getText());		
		
		reply.pushValuesToMessage();
		
		// try sending reply to source
		try {
			reply.init();
			reply.go();
			
			// if successful, mark as sent echo
			replyBuffer.add(echoEvent);
		} catch (AppiaEventException appiaerror) {
			appiaerror.printStackTrace();
		}			
	}

	protected boolean alreadyReplied(EchoBroadcastEvent echoEvent) {
		for (EchoBroadcastEvent e : replyBuffer) {
			if (e.getSequenceNumber() == echoEvent.getSequenceNumber()) {
				return true; // this will not work when multiple
							 // processes can send bcast msgs as 
							 // sequence numbers are not unique 
							 // (store pid too?)
			}
		}
		return false;
	}

	protected void collectEchoReply(EchoBroadcastEvent echoEvent, String signature) {
				
		SocketAddress sa = (SocketAddress) echoEvent.source;
		
		sigmas[processes.getRank(sa)] = signature;
		
		// Add to reply queue.
		replyQueue.get(echoEvent.getSequenceNumber()).add(echoEvent);
						
		// From algo: When #echos > (N + F)/2, and the echos are verified, then continue
		// Note: The verification is done by the signature layer below. Msgs who's verification has
		// failed won't make it till here.
		if (replyQueue.get(echoEvent.getSequenceNumber()).size() > Math.floor((N + F)/2.0) && sentFinal == false) {			
			boolean done = false;
			List<String> alreadyCovered = new ArrayList<String> ();
			
			/* See if we have more than (N + F)/2 occurrences for the same message. */
			for (EchoBroadcastEvent ebe1 : replyQueue.get (echoEvent.getSequenceNumber())) {
				int num = 0;

				if (alreadyCovered.contains(ebe1.getText())) {
					continue;
				}
				else {
					alreadyCovered.add(ebe1.getText());
					// Verify if we have > (N + F)/2 identical msgs.
					for (EchoBroadcastEvent ebe2 : replyQueue.get (echoEvent.getSequenceNumber())) {
						if (ebe1.getText().equals(ebe2.getText())) {
							num++;
							if (num > Math.floor((N + F)/2.0))
							{
								done = true;
								break;
							}
						}
					}
				}

				if (done == true) {
					sendFinal(echoEvent);
					break;
				}
					
			}
		}
	}
	
	protected void sendFinal(EchoBroadcastEvent echoEvent) {
		sentFinal = true;
		
		EchoBroadcastEvent reply = new EchoBroadcastEvent ();
		reply.setFinal(true);
		reply.setSequenceNumber(echoEvent.getSequenceNumber());
		reply.dest =  new AppiaMulticast (null, processes.getAllSockets());
		reply.setSourceSession(this);
		reply.setChannel(channel);
		reply.setDir(Direction.DOWN);		
		reply.setText(echoEvent.getText());	
		
		/* Add signatures here */
		for (int i = 0; i < processes.getAllProcesses().length; i++) {
			try {
				reply.getMessage().pushString(sigmas[i]);
			} catch (NullPointerException e) {
				reply.getMessage().pushString(EBConstants.BOTTOM);
			}
		}
		
		reply.pushValuesToMessage();
		
		// try sending reply to source
		try {
			reply.init();
			reply.go();						
		} catch (AppiaEventException appiaerror) {
			appiaerror.printStackTrace();
		}			
		
	}
	
	private void deliverFinal(EchoBroadcastEvent echoEvent) {
		
		String sigma;
		int verified = 0;
		
		// Unpack signatures.
		for (int i = processes.getAllProcesses().length-1; i >= 0; i--) {
			sigmas[i] = echoEvent.getMessage().popString();
		}
		
		// Verify all the signatures and maintain a count of them.
		Message echoMessage = getEchoMessage(echoEvent);
		for (int i = 0; i < processes.getAllProcesses().length; i++) {
			sigma = sigmas[i];
			if (!sigma.equals(EBConstants.BOTTOM)) {
				try {
					if(SignatureSession.verifySignature(echoMessage, EBConstants.PROCESS_ALIAS_PREFIX + i, sigma, trustedStore)) {
						verified++;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		// If we have at least (N + F)/2 verified messages of content 'm', then deliver m.
		if (delivered == false && verified > Math.floor((N+F)/2.0)) {
			delivered = true;
			try {
				// Check to see if we have different tx/rx paths.
				if (deliverToChannel != null) {
					echoEvent.setChannel(deliverToChannel);
					echoEvent.init();
				}
				echoEvent.go();
			} catch (AppiaEventException e) {
				e.printStackTrace();
			}			
		}
	}
	
	private Message getEchoMessage(EchoBroadcastEvent echoEvent) {
		Message clonedMsg = null;
		try {
			clonedMsg = (Message) echoEvent.getMessage().clone();
			clonedMsg.pushInt(echoEvent.getSequenceNumber());
			clonedMsg.pushBoolean(true);
			clonedMsg.pushBoolean(false);
			clonedMsg.pushString(echoEvent.getText());
		} catch (CloneNotSupportedException e) {		
			e.printStackTrace();
		}
		return clonedMsg;
	}

	/**
	 * Reinitialises the session, called by ByzantineConsistentChannel 
	 */
	public void reset() {
		sentEcho = false;
		sentFinal = false;
		delivered = false;
		sigmas = new String [processes.getAllProcesses().length];
		replyBuffer = new ArrayList<EchoBroadcastEvent> ();
	}

}
