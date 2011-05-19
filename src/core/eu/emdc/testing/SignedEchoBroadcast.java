package eu.emdc.testing;
import java.io.File;
import java.io.IOException;

import net.sf.appia.core.Appia;
import net.sf.appia.xml.AppiaXML;

import org.xml.sax.SAXException;

public class SignedEchoBroadcast implements Runnable {

	public static void main(String[] args) {
		final File xmlfile = new File(args[0]);
		try {
			AppiaXML.load(xmlfile);
			Appia.run();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

	@Override
	public void run() {
		// ignore for now
	}
	
	
}
