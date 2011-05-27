package irdp.protocols.tutorialDA.echobroadcast;

import irdp.protocols.tutorialDA.events.EchoBroadcastEvent;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import irdp.protocols.tutorialDA.utils.SampleProcess;

import java.util.ArrayList;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Layer;
import net.sf.appia.core.events.AppiaMulticast;

/**
 * Byzantine Echo Broadcast Layer
 * @author EMDC
 */
public class ByzantineEchoBroadcastSession extends EchoBroadcastSession {
	
	/*
	 * Used to activate various testcases. See documentation for details. 
	 */
	private String testCase = null;
	
	public ByzantineEchoBroadcastSession(Layer layer) {
		super(layer);
	}
	
	
	public void init(ProcessSet set, String trustedcertsfile, String trustedcertspass, String testCase) {
		this.testCase = testCase;
		
		super.init(set, trustedcertsfile, trustedcertspass);	
	}
	
	
	/** 
	 * Initiate a broadcast of a message
	 */
	@Override
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
	
	
	@Override
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

	@Override
	protected void sendFinal(EchoBroadcastEvent echoEvent) {
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
				reply.getMessage().pushString(sigmas[i]);
			} catch (NullPointerException e){
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
}
