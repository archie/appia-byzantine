package irdp.protocols.tutorialDA.byzantineconsistentchannel;

import net.sf.appia.core.Session;

/** 
 * @author EMDC
 */
public class BByzantineConsistentChannelLayer extends ByzantineConsistentChannelLayer {

	@Override
	public Session createSession() {
		return new BByzantineConsistentChannelSession(this);
	}

}
