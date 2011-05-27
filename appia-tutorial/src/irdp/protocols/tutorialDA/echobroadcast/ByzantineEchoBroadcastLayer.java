package irdp.protocols.tutorialDA.echobroadcast;

import net.sf.appia.core.Session;

/** 
 * @author EMDC
 */
public class ByzantineEchoBroadcastLayer extends EchoBroadcastLayer {

	@Override
	public Session createSession() {
		return new ByzantineEchoBroadcastSession(this);
	}

}
