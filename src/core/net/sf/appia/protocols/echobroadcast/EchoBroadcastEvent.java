package net.sf.appia.protocols.echobroadcast;

import net.sf.appia.core.events.SendableEvent;

/**
 * 
 * @author EMDC
 *
 */
public class EchoBroadcastEvent extends SendableEvent {
	private boolean isEcho;
	private boolean isFinal;
	private int sequenceNumber;

	public void setEcho(boolean isEcho) {
		this.isEcho = isEcho;
	}

	public boolean isEcho() {
		return isEcho;
	}

	public void setFinal(boolean isFinal) {
		this.isFinal = isFinal;
	}

	public boolean isFinal() {
		return isFinal;
	}

	public void setSequenceNumber(int i) {
		sequenceNumber = i;
	} 
	
	public int getSequenceNumber() {
		return sequenceNumber;
	}
		
}
