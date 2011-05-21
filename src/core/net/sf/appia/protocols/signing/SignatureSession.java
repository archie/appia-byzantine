/**
 * Appia: Group communication and protocol composition framework library
 * Copyright 2006 University of Lisbon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 *
 * Initial developer(s): Alexandre Pinto and Hugo Miranda.
 * Contributor(s): See Appia web page for a list of contributors.
 */
 package net.sf.appia.protocols.signing;

//////////////////////////////////////////////////////////////////////
//                                                                  //
// Appia: protocol development and composition framework            //
//                                                                  //
// Version: 1.0/J                                                   //
//                                                                  //
// Copyright, 2000, Universidade de Lisboa                          //
// All rights reserved                                              //
// See license.txt for further information                          //
//                                                                  //
// Class: DropSession: Randomly drop sending messages               //
//                                                                  //
// Author: Hugo Miranda, 05/2000                                    //
//                                                                  //
// Change Log:                                                      //
//  11/Jul/2001: The debugOn variable was changed to the            //
//               DropConfig interface                               //
//                                                                  //
//////////////////////////////////////////////////////////////////////


import java.io.FileInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;

import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.SendableEvent;
import net.sf.appia.core.message.Message;
import net.sf.appia.xml.interfaces.InitializableSession;
import net.sf.appia.xml.utils.SessionProperties;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

public class SignatureSession extends Session implements InitializableSession{
    
    /*
     * KeyStore, format  used to store the keys.
     * Ex: "JKS"
     */
    private String storeType = "JKS";
    
    /*
     * Name of the file were the private key is stored.
     */
    private String keystoreFile=null;
    
    /*
     * Passphrase to access the file where the private key is stored.
     */
    private char[] keystorePass=null;
    
    /*
     * Name of the file were the private key is stored.
     */
    private String trustedCertsFile=null;
    
    /*
     * Passphrase to access the file where the private key is stored.
     */
    private char[] trustedCertsPass=null;
	
    
    private String myAlias;
    
    private KeyStore trustedStore;
    
    private PrivateKey privKey;
    
    private BASE64Encoder enc;
    private BASE64Decoder dec;
    
    /**
     * @see net.sf.appia.core.Session
     */

    public SignatureSession(SignatureLayer l) {
    	super(l);
    	enc = new BASE64Encoder();
    	dec = new BASE64Decoder();
    }
    
    /**
     * Initializes the session using the parameters given in the XML configuration.
     * Possible parameters:
     * <ul>
     * <li><b>localport</b> the local port to bind.
     * <li><b>remotehost</b> the remote host (IP address).
     * <li><b>remoteport</b> the remote port.
     * </ul>
     * 
     * @param params The parameters given in the XML configuration.
     */
    public void init(SessionProperties props) {
    	//Set user ID
    	if(props.containsKey("user_alias"))
    		myAlias = props.getString("user_alias");
    	if(props.containsKey("store_type"))
    		storeType = props.getString("store_type");
    	if(props.containsKey("keystore_file"))
    		keystoreFile = props.getString("keystore_file");
    	if(props.containsKey("passphrase"))
    		keystorePass = props.getCharArray("keystore_pass");
    	if(props.containsKey("keystore_file"))
    		keystoreFile = props.getString("keystore_file");
    	if(props.containsKey("keystore_pass"))
    		keystorePass = props.getCharArray("keystore_pass");
    	if(props.containsKey("trustedcerts_file"))
    		trustedCertsFile = props.getString("trustedcerts_file");
    	if(props.containsKey("trustedcerts_pass"))
    		trustedCertsPass = props.getCharArray("trustedcerts_pass");
    	    	
    	try{
            final KeyStore keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(keystoreFile), keystorePass);
            
            //FIXME for simplicity assuming same password as keystore
            Key key = keyStore.getKey(myAlias,keystorePass);
            if(key instanceof PrivateKey){
            	privKey = (PrivateKey)(key);
            }
            
            trustedStore = KeyStore.getInstance(storeType);
            trustedStore.load(new FileInputStream(trustedCertsFile), trustedCertsPass);
    	} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    public void init (String useralias, String keystorefile, String keystorepass, String trustedcertsfile, String trustedcertspass)
    {
    	myAlias = useralias;
    	keystoreFile = keystorefile;
    	keystorePass = keystorepass.toCharArray();
    	trustedCertsFile = trustedcertsfile;
    	trustedCertsPass = trustedcertspass.toCharArray();
    	
    	try{
            final KeyStore keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(keystoreFile), keystorePass);
            
            //FIXME for simplicity assuming same password as keystore
            Key key = keyStore.getKey(myAlias,keystorePass);
            if(key instanceof PrivateKey){
            	privKey = (PrivateKey)(key);
            }
            
            trustedStore = KeyStore.getInstance(storeType);
            trustedStore.load(new FileInputStream(trustedCertsFile), trustedCertsPass);
    	} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    public void handle(Event e) {
    	
    	if(e instanceof SendableEvent) {
        
    		SendableEvent evt = (SendableEvent) e;
    		Message message = evt.getMessage();
    		
    		if(e.getDir() == Direction.DOWN){
    			message.pushString(myAlias);
    			
    			try {
        			String signature = enc.encode(signData(message.toByteArray(), privKey));
        			message.pushString(signature);
        			e.go();        			
    			} catch(Exception ex){
    				System.err.println("Error on signing outgoing message.");
    				ex.printStackTrace();
    			}
    		} else {
    			String signature = message.popString();
    			String userAlias = message.popString();
    			message.pushString(userAlias); //FIXME: peek doest not work :(
    			
    			try{
    				if(trustedStore.containsAlias(userAlias)){
    					Certificate userCert = trustedStore.getCertificate(userAlias);
						if(verifySig(message.toByteArray(), userCert.getPublicKey(), dec.decodeBuffer(signature))){
    						//System.out.println("Signature of user " + userAlias + " succesfully verified");
    						message.pushString(signature);
    						e.go();
    					} else {
    						System.err.println("Failure on verifying signature of user " + userAlias + ".");
    					}
    				} else {
						System.err.println("Received message from untrusted user: " + userAlias + ".");
    				}
    			} catch(Exception ex){
    				System.err.println("Error on verifying signature of ingoing message.");
    				ex.printStackTrace();
    			}

    		}
    	}
    }
	
	public static byte[] signData(byte[] data, PrivateKey key) throws Exception {
		Signature signer = Signature.getInstance("SHA1withRSA");
		signer.initSign(key);
		signer.update(data);
		return (signer.sign());
	}

	public static boolean verifySig(byte[] data, PublicKey key, byte[] sig) throws Exception {
		Signature signer = Signature.getInstance("SHA1withRSA");
		signer.initVerify(key);
		signer.update(data);
		return (signer.verify(sig));

	}
}
