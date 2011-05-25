package irdp.protocols.tutorialDA.events;

import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.message.Message;

/**
 * 
 * @author EMDC
 *
 */
public class EchoBroadcastEvent extends SendableEvent implements Cloneable{
	
	private boolean isEcho;
	private boolean isFinal;

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
	
	
	private int sequenceNumber;	
	private String text;
	
	public void setSequenceNumber(int i) {
		sequenceNumber = i;
	} 
	
	public int getSequenceNumber() {
		return sequenceNumber;
	}
	
	public void setText (String txt) {
		this.text = txt;
	}
	
	public String getText () {
		return this.text;
	}
	
	public void pushValuesToMessage () {
		Message msg = getMessage();
		msg.pushInt(sequenceNumber);
		msg.pushBoolean(isEcho);
		msg.pushBoolean(isFinal);
		msg.pushString(text);
	}

	public void popValuesFromMessage () {
		Message msg = getMessage();
		text = msg.popString();
		isFinal = msg.popBoolean();
		isEcho = msg.popBoolean();
		sequenceNumber = msg.popInt();
	}
}
