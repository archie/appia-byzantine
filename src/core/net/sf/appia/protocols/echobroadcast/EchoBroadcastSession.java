package net.sf.appia.protocols.echobroadcast;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
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
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;
import net.sf.appia.xml.AppiaXML;
import net.sf.appia.xml.interfaces.InitializableSession;
import net.sf.appia.xml.utils.SessionProperties;

/**
 * Echo Broadcast Layer
 * @author EMDC
 */
public class EchoBroadcastSession extends Session implements InitializableSession {

	private Channel channel;
	private int localPort;
	private List<InetSocketAddress> remoteProcesses;
	
	// receiver buffers
	private List<EchoBroadcastEvent> replyBuffer;
	
	// initiator buffers
	private Map<Integer, List<EchoBroadcastEvent>> replyQueue;
	private int sequenceNumber;

	
	public EchoBroadcastSession(Layer layer) {
		super(layer);
		replyBuffer = new ArrayList<EchoBroadcastEvent>();
		replyQueue = new HashMap<Integer, List<EchoBroadcastEvent>>();
		remoteProcesses = new ArrayList<InetSocketAddress> ();
		sequenceNumber = 0;
	}
	
	public void init(SessionProperties params) {
		
		this.localPort = Integer.parseInt(params.getProperty("localport"));
		final String[] remoteHost1 = params.getProperty("remotehost1").split(":");
		final String[] remoteHost2 = params.getProperty("remotehost2").split(":");
		
		try {
			this.remoteProcesses.add(new InetSocketAddress(InetAddress.getByName(remoteHost1[0]),
					Integer.parseInt(remoteHost1[1])));
			this.remoteProcesses.add(new InetSocketAddress(InetAddress.getByName(remoteHost2[0]),
					Integer.parseInt(remoteHost2[1])));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
	}

	
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
	
	public void handleChannelInit(ChannelInit event) {
		channel = ((ChannelInit) event).getChannel();
	}
	
	/** 
	 * Initiate a broadcast of a message
	 */
	public void echoBroadcast(EchoBroadcastEvent echoEvent) {
		int nextSequenceNumber = ++sequenceNumber;
		replyQueue.put(nextSequenceNumber, new ArrayList<EchoBroadcastEvent>());
		
		// for all processes		
		echoEvent.setChannel(channel);
		echoEvent.setDir(Direction.DOWN);
		echoEvent.setSourceSession(this);
		// echoEvent.dest = ??? where is this coming from - a set of processes
		
		echoEvent.setEcho(false);
		echoEvent.setFinal(false);
		echoEvent.setSequenceNumber(nextSequenceNumber);
		
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
		} else if (event.getDir() == Direction.UP) {
			pp2pdeliver(event);	
		}
	}
	
	private void pp2pdeliver(EchoBroadcastEvent echoEvent) {				
		if (echoEvent.isEcho()) {
			collectEchoReply(echoEvent);
		} else if (echoEvent.isFinal() && !echoEvent.isEcho()) {	
			deliverFinal(echoEvent);
		} else if (!echoEvent.isEcho() && !echoEvent.isFinal()) {
			sendEchoReply(echoEvent);			
		}
	}
	
	private void sendEchoReply(EchoBroadcastEvent echoEvent) {		
		if (alreadyReplied(echoEvent)) {
			return;
		}
		
		EchoBroadcastEvent reply = new EchoBroadcastEvent();
		reply.setEcho(true);
		reply.setSequenceNumber(echoEvent.getSequenceNumber());
		reply.dest = echoEvent.source;
		reply.setSourceSession(this);
		reply.setChannel(channel);
		reply.setDir(Direction.DOWN);
		
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

	private void collectEchoReply(EchoBroadcastEvent echoEvent) {
		// add to reply queue for previously sent message
		replyQueue.get(echoEvent.getSequenceNumber()).add(echoEvent);
		
		// if #echoes > (N+f)/2 is fulfilled
		// 	send final
		// else 
		// 	wait
	}
	
	private void sendFinal(EchoBroadcastEvent echoEvent) {
		// send final to all 
		// -- reuse broadcast ?? 
	}
	
	private void deliverFinal(EchoBroadcastEvent echoEvent) {
		/*
		 * 
if # {p ∈ Π | Σ[p] = ⊥ ∧ verifysig(p, bcb p E CHO m, Σ[p])} >
	and delivered = FALSE do
		delivered := TRUE;
		trigger bcb, Deliver | s, m ;

		 */
	}


}
