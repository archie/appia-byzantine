package eu.emdc.testing;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import org.xml.sax.SAXException;

import net.sf.appia.core.Appia;
import net.sf.appia.core.AppiaCursorException;
import net.sf.appia.core.AppiaDuplicatedSessionsException;
import net.sf.appia.core.AppiaInvalidQoSException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.ChannelCursor;
import net.sf.appia.core.Layer;
import net.sf.appia.core.QoS;
import net.sf.appia.protocols.byzantineconsistentchannel.ByzantineConsistentChannelLayer;
import net.sf.appia.protocols.byzantineconsistentchannel.ByzantineConsistentChannelSession;
import net.sf.appia.protocols.echobroadcast.EchoBroadcastLayer;
import net.sf.appia.protocols.echobroadcast.EchoBroadcastSession;
import net.sf.appia.protocols.tcpcomplete.TcpCompleteLayer;
import net.sf.appia.protocols.tcpcomplete.TcpCompleteSession;
import net.sf.appia.test.xml.ecco.EccoLayer;
import net.sf.appia.test.xml.ecco.EccoSession;
import net.sf.appia.xml.AppiaXML;

public class RunMe {

private static final int NUMBER_OF_ARGS = 3;
    
    private RunMe() {}
    
	private static Layer[] qos={
		    new net.sf.appia.protocols.tcpcomplete.TcpCompleteLayer(),
		    new net.sf.appia.protocols.echobroadcast.EchoBroadcastLayer(),
		    new eu.emdc.testing.ApplicationLayer()
	};
	
	public static void main(String[] args) {
		
		if (args.length == 2) {
						
			TcpCompleteLayer tcplayer = new TcpCompleteLayer();
			ByzantineConsistentChannelLayer ebl = new ByzantineConsistentChannelLayer();
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
			ByzantineConsistentChannelSession ebs = (ByzantineConsistentChannelSession) ebl.createSession();
			ApplicationSession as = (ApplicationSession) al.createSession();
			
			final String processfile = args[0];
			final int rank = Integer.parseInt(args[1]);
			
			as.init(processfile, rank);
			ebs.init(processfile, rank);
			
			Channel channel = myQoS.createUnboundChannel("Print Channel");
			ChannelCursor cc = channel.getCursor();

			try {
				cc.bottom();
				cc.setSession(tcpsession);
				cc.up ();
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
//		else if (args.length == NUMBER_OF_ARGS) {
//			final int localport = Integer.parseInt(args[0]);
//			final String remotehost = args[1];
//			final int remoteport = Integer.parseInt(args[2]);
//			
//			/* Create a QoS */
//		    QoS myQoS=null;
//		    try {
//		      myQoS=new QoS("Appl QoS",qos);
//		    } catch(AppiaInvalidQoSException ex) {
//		      System.err.println("Invalid QoS");
//		      System.err.println(ex.getMessage());
//		      System.exit(1);
//		    }
//		    
//		    /* Create a channel. Uses default event scheduler. */
//		    final Channel myChannel = myQoS.createUnboundChannel("Appl Channel");
//		    
//		    /* Application Session requires special arguments: qos and port.
//		       A session is created and binded to the stack. Remaining ones
//		       are created by default
//		     */
//		    
//		    final EccoSession es=(EccoSession)qos[qos.length-1].createSession();
//		    try {
//				es.init(
//						localport,
//						new InetSocketAddress(InetAddress.getByName(remotehost),remoteport));
//			} catch (UnknownHostException e) {
//				e.printStackTrace();
//			}
//		    
//		    final ChannelCursor cc=myChannel.getCursor();
//		    /* Application is the last session of the array. Positioning
//		       in it is simple */
//		    try {
//		      cc.top();
//		      cc.setSession(es);
//		    } catch(AppiaCursorException ex) {
//		      System.err.println("Unexpected exception in main. Type code:"+
//		      ex.type);
//		      System.exit(1);
//		    }
//		    
//		    /* Remaining ones are created by default. Just tell the channel to start */
//		    try {
//		    	myChannel.start();
//		    } catch(AppiaDuplicatedSessionsException ex) {
//		    	System.err.println("Sessions binding strangely resulted in "+
//		    			"one single sessions occurring more than "+
//						"once in a channel");
//		    	System.exit(1);
//		    }
//		    
//		    Appia.run();
//		}
//		else {
//			System.out.println("Invalid number of arguments!");
//			System.out.println(
//					"Usage:\tjava Ecco <localport> <remotehost> <remoteport>");
//			System.out.println("\tjava Ecco <xml_file>");
//		}
	}
}
