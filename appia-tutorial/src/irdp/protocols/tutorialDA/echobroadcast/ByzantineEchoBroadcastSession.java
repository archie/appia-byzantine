package irdp.protocols.tutorialDA.echobroadcast;

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
import net.sf.appia.protocols.common.RegisterSocketEvent;
import irdp.protocols.tutorialDA.events.EchoBroadcastEvent;
import irdp.protocols.tutorialDA.events.ProcessInitEvent;
import irdp.protocols.tutorialDA.signing.SignatureSession;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;
import net.sf.appia.xml.interfaces.InitializableSession;
import net.sf.appia.xml.utils.SessionProperties;

/**
 * Echo Broadcast Layer
 * @author EMDC
 */
public class ByzantineEchoBroadcastSession extends Session implements InitializableSession {

	private Channel channel;
	private Channel deliverToChannel = null;
	private ProcessSet processes;
    private KeyStore trustedStore;
    	
	private int N = 0;
	private int F = 1;
	
    /*
     * KeyStore, format  used to store the keys.
     * Ex: "JKS"
     */
    private String storeType = "JKS";
	
	/*
	 * A buffer to know what messages we've already
	 * sent echos to.
	 */
	private List<EchoBroadcastEvent> replyBuffer;
	
	/*
	 * List of echos the sender has collected for each
	 * broadcast (or sequence number).
	 */
	private Map<Integer, List<EchoBroadcastEvent>> replyQueue;
	
	/*
	 * Implemented for safety reasons. One sequence number
	 * per broadcast.
	 */
	private int sequenceNumber;

	/* From the algo: The below holds state information for each broadcast */	
	public boolean sentEcho = false;
	public boolean sentFinal = false;
	public boolean delivered = false;
	
	/* From the algo: Sigmas is an array of signatures, and aliases
	 * is the corresponding alias for the signature.
	 */
	private String [] sigmas;
	private String [] aliases;
	
	/*
	 * Trusted certificates and the password for the signature related
	 * operations.
	 */
	private String trustedCertsFile;
	private char[] trustedCertsPass;
	
	/*
	 * Value representing bottom.
	 */
	final String bottom = "BOTTOM";
	
	/*
	 * Used to activate various testcases. See documentation for details. 
	 */
	private String testCase = null;
	
	public ByzantineEchoBroadcastSession(Layer layer) {
		super(layer);
		replyBuffer = new ArrayList<EchoBroadcastEvent>();
		replyQueue = new HashMap<Integer, List<EchoBroadcastEvent>>();
		sequenceNumber = 0;
	}
	
	public void init(SessionProperties params) {
		
		//this.localPort = Integer.parseInt(params.getProperty("localport"));
		processes = ProcessSet.buildProcessSet(params.getProperty("processes"),
				Integer.parseInt(params.getProperty("myrank")));

		// FIXME: XML based configuration is currently broken.
	}
	
	public void init(ProcessSet set, int rank, String trustedcertsfile, String trustedcertspass) {
		this.testCase = "test1";
		processes = set;
		
    	trustedCertsFile = trustedcertsfile;
    	trustedCertsPass = trustedcertspass.toCharArray();
    	
    	try{
            
            trustedStore = KeyStore.getInstance(storeType);
            trustedStore.load(new FileInputStream(trustedCertsFile), trustedCertsPass);
    	} catch (Exception e) {
			e.printStackTrace();
		}

		aliases = new String [processes.getAllProcesses().length];
		sigmas = new String [processes.getAllProcesses().length];
		N = processes.getAllProcesses().length;		
	}
	
	/* Set tx and rx channels, called by Byzantine Consistent Broadcast */
	public void setChannels (Channel c, Channel dc)
	{
		channel = c;
		deliverToChannel = dc;
	}
	
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
	
	/* Called by ProcessSet abstraction */
	 private void handleProcessInitEvent(ProcessInitEvent event) {
		    processes = event.getProcessSet();
		    try {
		      event.go();
		    } catch (AppiaEventException e) {
		      e.printStackTrace();
		    }
	 }

	
	private void handleRSE(RegisterSocketEvent event) {
		// TODO Auto-generated method stub
		try {
			event.go();
		} catch (AppiaEventException e) {
			// TODO Auto-generated catch block
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
	public void echoBroadcast(EchoBroadcastEvent originalEchoEvent) {
				
		int nextSequenceNumber = ++sequenceNumber;
						
		replyQueue.put(nextSequenceNumber, new ArrayList<EchoBroadcastEvent>());
				
		for (SampleProcess p : processes.getAllProcesses()) {			
			
			EchoBroadcastEvent echoEvent = null;
			try {
				echoEvent = (EchoBroadcastEvent) originalEchoEvent.cloneEvent();
			} catch (CloneNotSupportedException e) {				
				e.printStackTrace();
			}

			echoEvent.setChannel(channel);
			echoEvent.setDir(Direction.DOWN);
			echoEvent.setSourceSession(this);
			echoEvent.setEcho(false);
			echoEvent.setFinal(false);
			echoEvent.setSequenceNumber(nextSequenceNumber);
			
			/*
			 * Test Case 3: A byzantine coordinator will send a 
			 * different message to all nodes in the set. Will be detected
			 * in the Final phase by all correct processes.  
			 */
			if (testCase.equalsIgnoreCase("test3")) {
				echoEvent.setText(String.valueOf(p.getProcessNumber()));
			}

			// This pushes all the required values to the message stack.
			echoEvent.pushValuesToMessage();

			/*
			 * Algorithm:
			 * 
			 * upon event < bcb, Broadcast | m > do
			 * 		for all processes do
			 * 			trigger < al, Send | q, [Send m]>;
			 */

			echoEvent.dest = p.getSocketAddress();
			
			try {
				echoEvent.init();
				echoEvent.go();			
			} catch (AppiaEventException eventerror) {
				eventerror.printStackTrace();
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
		/* Pop the signature and alias out of the message because
		 * we always have a SignatureSession below us. Failing to
		 * do so will result in bad things.
		 */
		String signature = echoEvent.getMessage().popString();
		String alias = echoEvent.getMessage().popString();
		
		// Now populate the required fields in the message.
		echoEvent.popValuesFromMessage();

		if (echoEvent.isEcho()) {
			//System.err.println("Collect Echo Reply called");
			collectEchoReply(echoEvent, alias, signature);
		} else if (echoEvent.isFinal() && !echoEvent.isEcho()) {
			//System.err.println("Deliver Final called");
			deliverFinal(echoEvent);
		} else if (!echoEvent.isEcho() && !echoEvent.isFinal()) {
			//System.err.println("Send Echo Reply called dst:" + echoEvent.dest + " src:" + echoEvent.source);
			sendEchoReply(echoEvent);			
		}
	}
	
	private void sendEchoReply(EchoBroadcastEvent echoEvent) {
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
		
		/*
		 * Test Case 1: Byzantine node receives a correct message
		 * but tries to modify its content, basically corrupting the message.
		 * This should be detected when comparing the final messages by all
		 * correct processes. 
		 */
		if (testCase.equalsIgnoreCase("test1")) {
			reply.setText("fake message");
		} else {
			reply.setText(echoEvent.getText());		
		}
		
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

	private boolean alreadyReplied(EchoBroadcastEvent echoEvent) {
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

	private void collectEchoReply(EchoBroadcastEvent echoEvent, String alias, String signature) {
		
				
		SocketAddress sa = (SocketAddress) echoEvent.source;
		
		aliases[processes.getRank(sa)] = alias;
		sigmas[processes.getRank(sa)] = signature;
		
		// Add to reply queue.
		replyQueue.get(echoEvent.getSequenceNumber()).add(echoEvent);
		
		// From algo: When #echos > (N + F)/2, and the echos are verified, then continue
		// Note: The verification is done by the signature layer below. Msgs who's verification has
		// failed won't make it till here.
		if (replyQueue.get(echoEvent.getSequenceNumber()).size() > Math.floor((N + F)/2.0) && sentFinal == false)
		{			
			boolean done = false;
			List<String> alreadyCovered = new ArrayList<String> ();
			
			/* See if we have more than (N + F)/2 occurrences for the same message. */
			for (EchoBroadcastEvent ebe1 : replyQueue.get (echoEvent.getSequenceNumber())) {
				int num = 0;
				
				if (alreadyCovered.contains(ebe1.getText()))
				{
					continue;
				}
				else
				{
					alreadyCovered.add(ebe1.getText());
					// Verify if we have > (N + F)/2 identical msgs.
					for (EchoBroadcastEvent ebe2 : replyQueue.get (echoEvent.getSequenceNumber())) {
						if (ebe1.getText().equals(ebe2.getText()))
						{
							num++;
							if (num > Math.floor((N + F)/2.0))
							{
								done = true;
								break;
							}
						}
					}
				}

				if (done == true)
				{
					sendFinal(echoEvent);
					break;
				}
					
			}
		}
	}
	
	private void sendFinal(EchoBroadcastEvent echoEvent) {
		sentFinal = true;
		EchoBroadcastEvent reply = new EchoBroadcastEvent ();
		reply.setFinal(true);
		reply.setSequenceNumber(echoEvent.getSequenceNumber());
		reply.dest =  new AppiaMulticast (null, processes.getAllSockets());
		reply.setSourceSession(this);
		reply.setChannel(channel);
		reply.setDir(Direction.DOWN);	
		
		/*
		 * Test Case 2: Modifying the final message. Correct nodes
		 * should detect this by comparing the signatures attached. 
		 */
		if (testCase.equalsIgnoreCase("test2")) {
			reply.setText("fake message");
		} else {
			reply.setText(echoEvent.getText());	
		}
		/* Add signatures here */
		for (int i = 0; i < processes.getAllProcesses().length; i++)
		{			
			
			try{
				reply.getMessage().pushString(aliases[i]);
				reply.getMessage().pushString(sigmas[i]);
			} catch (NullPointerException e){
				reply.getMessage().pushString(bottom);
				reply.getMessage().pushString(bottom);
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

		System.err.println("Delivering final");
		String sigma, alias;
		int verified = 0;
		
		// Unpack signatures and aliases.
		for (int i = 0; i < processes.getAllProcesses().length; i++)
		{
			
			sigmas[i] = echoEvent.getMessage().popString();
			aliases[i] = echoEvent.getMessage().popString();
			
		}
		
		// Verify all the signatures and maintain a count of them.
		Message echoMessage = getEchoMessage(echoEvent);
		for (int i = 0; i < processes.getAllProcesses().length; i++)
		{
			sigma = sigmas[i];
			alias = aliases[i];
			if (!sigma.equals(bottom))
			{
				try {
					if(SignatureSession.verifySignature(echoMessage, alias, sigma, trustedStore))
					{
						verified++;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		// If we have at least (N + F)/2 verified messages of content 'm', then deliver m.
		if (delivered == false && verified > Math.floor((N+F)/2.0))
		{
			delivered = true;
			try {
				// Check to see if we have different tx/rx paths.
				if (deliverToChannel != null)
				{
					echoEvent.setChannel(deliverToChannel);
					echoEvent.init();
				}
				echoEvent.go();
			} catch (AppiaEventException e) {
				// TODO Auto-generated catch block
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return clonedMsg;
	}

	/* Reinitialises the session, called by ByzantineConsistentChannel */
	public void reset ()
	{
		sentEcho = false;
		sentFinal = false;
		delivered = false;
		aliases = new String [processes.getAllProcesses().length];
		sigmas = new String [processes.getAllProcesses().length];
	}

}