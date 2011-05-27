package irdp.protocols.tutorialDA.byzantineconsistentchannel;

import irdp.protocols.tutorialDA.echobroadcast.ByzantineEchoBroadcastLayer;
import irdp.protocols.tutorialDA.echobroadcast.ByzantineEchoBroadcastSession;
import irdp.protocols.tutorialDA.signing.SignatureLayer;
import irdp.protocols.tutorialDA.signing.SignatureSession;
import irdp.protocols.tutorialDA.utils.ProcessSet;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Layer;

/**
 * Byzantine Consistent Channel abstraction as defined in algorithm 3.19 
 * This class models different byzantine behaviours depending on how it 
 * is being initialized. 
 * @author EMDC
 */
public class BByzantineConsistentChannelSession extends ByzantineConsistentChannelSession {

	public BByzantineConsistentChannelSession(Layer layer) {
		super(layer);
	}
	
	/**
	 * Initialize with a byzantine behaviour. Valid test cases are: "test1", "test2", "test3"
	 * @param set
	 * @param alias
	 * @param usercerts
	 * @param testCase
	 */
	public void init(ProcessSet set, String alias, String usercerts, String testCase) {
		processes = set;
		bccInit (alias, usercerts, testCase);
	}

	
	private void bccInit (String alias, String usercerts, String testCase)
	{
		siglayer = new SignatureLayer();
		sigsession = new SignatureSession(siglayer);
		sigsession.init(alias, "etc/" + alias + ".jks", "123456", usercerts, "123456", true);
		ready = true;	
		sequenceNumbers = new int [processes.getAllProcesses().length];
		bcbs = new ByzantineEchoBroadcastSession [processes.getAllProcesses().length];
		bcls = new ByzantineEchoBroadcastLayer [processes.getAllProcesses().length];
		childChannels = new Channel [processes.getAllProcesses().length];
		
		for (int i = 0; i < processes.getAllProcesses().length; i++)
		{
			bcls[i] = new ByzantineEchoBroadcastLayer();
			ByzantineEchoBroadcastSession bcb = new ByzantineEchoBroadcastSession(bcls[i]);
			bcb.init(processes, usercerts, "123456", testCase);
			bcbs[i] = bcb;
		}
	}
}
