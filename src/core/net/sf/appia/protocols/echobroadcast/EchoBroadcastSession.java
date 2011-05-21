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
import net.sf.appia.core.events.AppiaMulticast;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;
import net.sf.appia.xml.AppiaXML;
import net.sf.appia.xml.interfaces.InitializableSession;
import net.sf.appia.xml.utils.SessionProperties;
import eu.emdc.testing.ProcessSet;
import eu.emdc.testing.ProcessInitEvent;

/**
 * Echo Broadcast Layer
 * @author EMDC
 */
public class EchoBroadcastSession extends Session implements InitializableSession {

	private Channel channel;
	private Channel deliverToChannel = null;
	private ProcessSet processes;
	
	private int N = 0;
	private int F = 0;
	
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
	
	public void init(String processfile, int rank) {
		
		processes = ProcessSet.buildProcessSet(processfile,rank);
		
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
		echoEvent.popValuesFromMessage();

		if (echoEvent.isEcho()) {
			//System.err.println("Collect Echo Reply called");
			collectEchoReply(echoEvent);
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

	private void collectEchoReply(EchoBroadcastEvent echoEvent) {
		
		/* TODO: Verify signatures */
		// add to reply queue for previously sent message
				
		replyQueue.get(echoEvent.getSequenceNumber()).add(echoEvent);
		
		if (replyQueue.get(echoEvent.getSequenceNumber()).size() > (N + F)/2)
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
		EchoBroadcastEvent reply = new EchoBroadcastEvent ();
		reply.setFinal(true);
		reply.setSequenceNumber(echoEvent.getSequenceNumber());
		reply.dest =  new AppiaMulticast (null, processes.getAllSockets());
		reply.setSourceSession(this);
		reply.setChannel(channel);
		reply.setDir(Direction.DOWN);
		reply.setText(echoEvent.getText());
		
		/* Add signatures here */
		
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
		
		//System.err.println("Deliver final");
		
		/* need to verify if number of correct signatures is > N+F/2) */
		if (delivered == false)
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


}
