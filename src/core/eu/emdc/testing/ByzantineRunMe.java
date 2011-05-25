package eu.emdc.testing;

import net.sf.appia.core.Appia;
import net.sf.appia.core.AppiaCursorException;
import net.sf.appia.core.AppiaDuplicatedSessionsException;
import net.sf.appia.core.AppiaInvalidQoSException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.ChannelCursor;
import net.sf.appia.core.Layer;
import net.sf.appia.core.QoS;
import net.sf.appia.protocols.byzantineconsistentchannel.BByzantineConsistentChannelLayer;
import net.sf.appia.protocols.byzantineconsistentchannel.BByzantineConsistentChannelSession;
import net.sf.appia.protocols.byzantineconsistentchannel.ByzantineConsistentChannelLayer;
import net.sf.appia.protocols.byzantineconsistentchannel.ByzantineConsistentChannelSession;
import net.sf.appia.protocols.tcpcomplete.TcpCompleteLayer;
import net.sf.appia.protocols.tcpcomplete.TcpCompleteSession;

public class ByzantineRunMe {
    
    private ByzantineRunMe() {}
    
	private static Layer[] qos={
		    new net.sf.appia.protocols.tcpcomplete.TcpCompleteLayer(),
		    new net.sf.appia.protocols.echobroadcast.EchoBroadcastLayer(),
		    new eu.emdc.testing.ApplicationLayer()
	};
	
	public static void main(String[] args) {
		
		if (args.length >= 4) {
						
			TcpCompleteLayer tcplayer = new TcpCompleteLayer();
			BByzantineConsistentChannelLayer ebl = new BByzantineConsistentChannelLayer();
			ApplicationLayer al = new ApplicationLayer();
			
			Layer[] qos = {tcplayer, ebl, al};
							
			QoS myQoS = null;
			try {
			myQoS = new QoS("byz stack", qos);
			} catch (AppiaInvalidQoSException ex) {
				System.err. println("Invalid QoS");
				System.err. println(ex.getMessage());
				System.exit(1);	
			}
			
			TcpCompleteSession tcpsession = (TcpCompleteSession) tcplayer.createSession();
			BByzantineConsistentChannelSession ebs = (BByzantineConsistentChannelSession) ebl.createSession();
			ApplicationSession as = (ApplicationSession) al.createSession();
			
			final String processfile = args[0];
			final int rank = Integer.parseInt(args[1]);
			final String alias = args[2];
			final String usercerts = args[3];
						
			as.init(processfile, rank);
			
			if (args.length == 5) {
				final String testCase = args[4];
				ebs.init(processfile, rank, alias, usercerts, testCase);
			} else {
				ebs.init(processfile, rank, alias, usercerts);
			}
			
			Channel channel = myQoS.createUnboundChannel("Print Channel");
			ChannelCursor cc = channel.getCursor();

			try {
				cc.bottom();
				cc.setSession(tcpsession);
				cc.up();
				cc.setSession(ebs);
				cc.up();
				cc.setSession(as);
				
			} catch (AppiaCursorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
						
			
			try {
		    	channel.start();
		    } catch(AppiaDuplicatedSessionsException ex) {
		    	System.err.println("Sessions binding strangely resulted in "+
		    			"one single sessions occurring more than "+
						"once in a channel");
		    	System.exit(1);
		    }
			
			Appia.run();
				
		}
	}
}
