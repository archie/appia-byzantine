package net.sf.appia.protocols.echobroadcast;

import java.io.FileInputStream;
import java.net.SocketAddress;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sun.misc.BASE64Decoder;

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
import net.sf.appia.protocols.signing.SignatureSession;
import net.sf.appia.xml.interfaces.InitializableSession;
import net.sf.appia.xml.utils.SessionProperties;
import eu.emdc.testing.ProcessInitEvent;
import eu.emdc.testing.ProcessSet;

/**
 * Echo Broadcast Layer
 * @author EMDC
 */
public class EchoBroadcastSession extends Session implements InitializableSession {

	private Channel channel;
	private Channel deliverToChannel = null;
	private ProcessSet processes;
    private KeyStore trustedStore;
    	
	private int N = 0;
	private int F = 0;
	
    /*
     * KeyStore, format  used to store the keys.
     * Ex: "JKS"
     */
    private String storeType = "JKS";
	
	// Attach state for each sequence number index.
	// private Map<Integer, StateTuple> stateMap = new HashMap<Integer, StateTuple>(); 
	
	// receiver buffers
	private List<EchoBroadcastEvent> replyBuffer;
	
	// initiator buffers
	private Map<Integer, List<EchoBroadcastEvent>> replyQueue;
	private int sequenceNumber;

	/* The below holds state information for each broadcast */
	
	public boolean sentEcho = false;
	public boolean sentFinal = false;
	public boolean delivered = false;
	
	private String [] sigmas;
	private String [] aliases;
	
	private String trustedCertsFile;
	private char[] trustedCertsPass;
	
	final String bottom = "BOTTOM";
	
	
	public EchoBroadcastSession(Layer layer) {
		super(layer);
		replyBuffer = new ArrayList<EchoBroadcastEvent>();
		replyQueue = new HashMap<Integer, List<EchoBroadcastEvent>>();
		sequenceNumber = 0;
	}
	
	public void init(SessionProperties params) {
		
		//this.localPort = Integer.parseInt(params.getProperty("localport"));
		processes = ProcessSet.buildProcessSet(params.getProperty("processes"),
				Integer.parseInt(params.getProperty("myrank")));

		
	}
	
	public void init(String processfile, int rank, String trustedcertsfile, String trustedcertspass) {
		
		processes = ProcessSet.buildProcessSet(processfile,rank);
		
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
	}
	
	/* Manual channel setting */
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
	public void echoBroadcast(EchoBroadcastEvent echoEvent) {
				
		int nextSequenceNumber = ++sequenceNumber;
				
		N = processes.getAllProcesses().length;
		
		//stateMap.put(nextSequenceNumber, new StateTuple ());
		replyQueue.put(nextSequenceNumber, new ArrayList<EchoBroadcastEvent>());
		System.err.println("Seqno: " + nextSequenceNumber);
		
		// for all processes		
		echoEvent.setChannel(channel);
		echoEvent.setDir(Direction.DOWN);
		echoEvent.setSourceSession(this);
		// echoEvent.dest = ??? where is this coming from - a set of processes
		
		
		echoEvent.setEcho(false);
		echoEvent.setFinal(false);
		echoEvent.setSequenceNumber(nextSequenceNumber);
				
		echoEvent.pushValuesToMessage();
		/*
		 * Algorithm:
		 * 
		 * upon event < bcb, Broadcast | m > do
		 * 		for all processes do
		 * 			trigger < al, Send | q, [Send m]>;
		 */
		
		echoEvent.dest = new AppiaMulticast (null, processes.getAllSockets());
		
		System.err.println("Sending on my channel: " + echoEvent.getChannel());
		
		try {
			echoEvent.init();
			echoEvent.go();			
		} catch (AppiaEventException eventerror) {
			eventerror.printStackTrace();
		}
	}
	
	private void handleEchoBroadcastEvent(EchoBroadcastEvent event) {
		if (event.getDir() == Direction.DOWN) {
			// something
			echoBroadcast(event);
			// Temporary: For now, just send to all
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
		
		// Need to sign the below.
		reply.setEcho(true);
		reply.setSequenceNumber(echoEvent.getSequenceNumber());
		reply.dest = echoEvent.source;
		reply.setSourceSession(this);
		reply.setChannel(channel);
		reply.setDir(Direction.DOWN);
		reply.setText(echoEvent.getText()); // Not sure why this is needed but lolz - Lalith
		
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
		
		/* TODO: Verify signatures */
		// add to reply queue for previously sent message
				
		SocketAddress sa = (SocketAddress) echoEvent.source;
		
		System.err.println("GotSometingfrom PID: " + processes.getRank(sa));
		aliases[processes.getRank(sa)] = alias;
		sigmas[processes.getRank(sa)] = signature;
				
		replyQueue.get(echoEvent.getSequenceNumber()).add(echoEvent);
		
		if (replyQueue.get(echoEvent.getSequenceNumber()).size() > (N + F)/2 && sentFinal == false)
		{
			
			boolean done = false;
			List<String> alreadyCovered = new ArrayList<String> ();
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
							if (num > (N + F)/2)
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
		// if #echoes > (N+f)/2 is fulfilled
		// 	send final
		// else 
		// 	wait
	}
	
	private void sendFinal(EchoBroadcastEvent echoEvent) {
		// send final to all 
		// -- reuse broadcast ?? 
		//send final
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
		/*
		 * 
if # {p ∈ Π | Σ[p] = ⊥ ∧ verifysig(p, bcb p E CHO m, Σ[p])} >
	and delivered = FALSE do
		delivered := TRUE;
		trigger bcb, Deliver | s, m ;

		 */
		
		String sigma, alias;
		int verified = 0;
		for (int i = 0; i < processes.getAllProcesses().length; i++)
		{
			
			sigmas[i] = echoEvent.getMessage().popString();
			aliases[i] = echoEvent.getMessage().popString();
			
		}
		
		echoEvent.getMessage().pushInt(echoEvent.getSequenceNumber());
		echoEvent.getMessage().pushBoolean(true);
		echoEvent.getMessage().pushBoolean(false);
		echoEvent.getMessage().pushString(echoEvent.getText());
		for (int i = 0; i < processes.getAllProcesses().length; i++)
		{
			sigma = sigmas[i];
			alias = aliases[i];
			if (!sigma.equals(bottom))
			{
				try {
					if(verifySignature(echoEvent.getMessage(), alias, sigma))
					{
						verified++;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		echoEvent.getMessage().popString();
		echoEvent.getMessage().popBoolean();
		echoEvent.getMessage().popBoolean();
		echoEvent.getMessage().popInt();
		
		if (delivered == false && verified > Math.ceil((N+F)/2.0))
		{
			delivered = true;
			try {
				if (deliverToChannel != null) // Ugly hack
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

	public boolean verifySignature (Message message, String userAlias, String signature) throws Exception
	{
		BASE64Decoder dec = new BASE64Decoder();
		if(trustedStore.containsAlias(userAlias)){
			Certificate userCert = trustedStore.getCertificate(userAlias);
			message.pushString(userAlias);
			if(SignatureSession.verifySig(message.toByteArray(), userCert.getPublicKey(), dec.decodeBuffer(signature))){
				System.out.println("Deliver Sign blah : Signature of user " + userAlias + " succesfully verified");
				message.popString();
				return true;
			} else {
				System.err.println("Failure on verifying signature of user " + userAlias + ".");
			}
			message.popString();
		} else {
			System.err.println("Received message from untrusted user: " + userAlias + ".");
		}
		
		return false;
	}
	
	public void reset ()
	{
		sentEcho = false;
		sentFinal = false;
		delivered = false;
		aliases = new String [processes.getAllProcesses().length];
		sigmas = new String [processes.getAllProcesses().length];
	}

}
