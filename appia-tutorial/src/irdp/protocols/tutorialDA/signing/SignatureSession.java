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
 * @author Paulo Ricardo, Lalith Suresh and Marcus Ljungblad
 * Contributor(s): See Appia web page for a list of contributors.
 */
 package irdp.protocols.tutorialDA.signing;

//////////////////////////////////////////////////////////////////////
//																	//
//Appia: protocol development and composition framework            	//
//																	//
//Class: SignatureLayer: 									        //
//																	//
//Author: Lalith Suresh, Marcus Ljungblad, Paulo Motta, 05/2011    	//
//																	//
//Change Log:                                                       //
//																	//
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
    
    private static final String SIGN_ALGORITHM = "SHA1withRSA";

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
     * Name of the file were the certificates are stored.
     */
    private String trustedCertsFile=null;
    
    /* Passphrase to access the file where the trusted certificates are stored.
     */
    private char[] trustedCertsPass=null;
	
    private String myAlias;
    
    private KeyStore trustedStore;
    private PrivateKey privKey;
    
    private BASE64Encoder enc;
    
    private boolean pushSignature = false;
    
    /**
     * @see net.sf.appia.core.Session
     */

    public SignatureSession(SignatureLayer l) {
    	super(l);
    	enc = new BASE64Encoder();
    }
    
    /**
     * Initializes the session using the parameters given in the XML configuration.
     * Possible parameters:
     * <ul>
     * <li><b>user_alias</b> The alias of the private key that will be used to sign messages of this layer.
     * <li><b>store_type</b> KeyStore is the format  used to store the keys. Default is "JKS".
     * <li><b>keystore_file</b> Name of the file were the private key is stored.
     * <li><b>keystore_pass</b> Passphrase to access the file where the private key is stored.
     * <li><b>trustedcerts_file</b> Name of the file were the trusted certificates are stored.
     * <li><b>keystore_pass</b> Passphrase to access the file where the trusted certificates are stored. 
     * <li><b>push_signature</b> If signature should be pushed to upper layer.
     * </ul>
     * 
     * @param props The parameters given in the XML configuration.
     * @see net.sf.appia.protocols.tcpcomplete.TcpCompleteSession#init(net.sf.appia.xml.utils.SessionProperties)
     */
    public void init(SessionProperties props) {
    	if(props.containsKey("user_alias"))
    		this.myAlias = props.getString("user_alias");
    	if(props.containsKey("store_type"))
    		this.storeType = props.getString("store_type");
    	if(props.containsKey("keystore_file"))
    		this.keystoreFile = props.getString("keystore_file");
    	if(props.containsKey("passphrase"))
    		this.keystorePass = props.getCharArray("keystore_pass");
    	if(props.containsKey("keystore_file"))
    		this.keystoreFile = props.getString("keystore_file");
    	if(props.containsKey("keystore_pass"))
    		this.keystorePass = props.getCharArray("keystore_pass");
    	if(props.containsKey("trustedcerts_file"))
    		this.trustedCertsFile = props.getString("trustedcerts_file");
    	if(props.containsKey("trustedcerts_pass"))
    		this.trustedCertsPass = props.getCharArray("trustedcerts_pass");
    	if(props.containsKey("push_signature"))
    		this.pushSignature = props.getString("push_signature").equals("true");
    	    	
    	loadKeys();
    }
    
    public void init (String useralias, String keystorefile, String keystorepass, String trustedcertsfile, String trustedcertspass, boolean pushSignature)
    {
    	this.myAlias = useralias;
    	this.keystoreFile = keystorefile;
    	this.keystorePass = keystorepass.toCharArray();
    	this.trustedCertsFile = trustedcertsfile;
    	this.trustedCertsPass = trustedcertspass.toCharArray();
    	this.pushSignature = pushSignature;
    	
    	loadKeys();
    }
    
	private void loadKeys() {
		try{
            final KeyStore keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(keystoreFile), keystorePass);
            
            //TODO for simplicity assuming same password as keystore
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
    			attachSignatureAndGo(e, message);
    		} else { //Direction.UP
    			verifySignatureAndGo(e, message);
    		}
    	}
    }

    /**
     * Pushes the alias, sign the message and afterwards pushes the
     * generated signature to the message, and send down
     * @param e event
     * @param message
     */
	private void attachSignatureAndGo(Event e, Message message) {
		message.pushString(myAlias);
		
		try {
			String signature = enc.encode(signData(message.toByteArray(), privKey));
			message.pushString(signature);
			e.go();        			
		} catch(Exception ex){
			System.err.println("Error on signing outgoing message.");
			ex.printStackTrace();
		}
	}
    
	/**
	 * Pops the signature and the alias for the message, verify if
	 * signature is valid for that alias public key, 
	 * and send up in positive case
	 * @param e event
	 * @param message
	 */
	private void verifySignatureAndGo(Event e, Message message) {
		String signature = message.popString();
		String userAlias = message.popString();
		
		try{
			if(verifySignature(message, userAlias, signature, trustedStore)){
				if(pushSignature){
					message.pushString(signature);
				}
				e.go();
			}
		} catch(Exception ex){
			System.err.println("Error on verifying signature of ingoing message.");
			ex.printStackTrace();
		}
	}
	
	public static byte[] signData(byte[] data, PrivateKey key) throws Exception {
		Signature signer = Signature.getInstance(SIGN_ALGORITHM);
		signer.initSign(key);
		signer.update(data);
		return (signer.sign());
	}

	public static boolean verifySig(byte[] data, PublicKey key, byte[] sig) throws Exception {
		Signature signer = Signature.getInstance(SIGN_ALGORITHM);
		signer.initVerify(key);
		signer.update(data);
		return (signer.verify(sig));
	}
	
	/**
	 * Verify if a signature is valid for a given message object
	 * @param message the msg that was signed
	 * @param userAlias the alias of the user that signed the message
	 * @param signature the signature itself
	 * @param trustedStore the keystore of trusted certificates
	 * @return
	 * @throws Exception
	 */
	public static boolean verifySignature(Message message, String userAlias, String signature, KeyStore trustedStore) throws Exception
	{
		boolean verified = false;
		
		BASE64Decoder dec = new BASE64Decoder();
		if(trustedStore.containsAlias(userAlias)){
			Certificate userCert = trustedStore.getCertificate(userAlias);
			message.pushString(userAlias);
			if(verifySig(message.toByteArray(), userCert.getPublicKey(), dec.decodeBuffer(signature))){
				//System.out.println("Signature of user " + userAlias + " succesfully verified"); //TODO uncomment here to debug
				verified = true;
			} else {
				System.err.println("Failure on verifying signature of user " + userAlias + ".");
			}
			message.popString();
		} else {
			System.err.println("Message from untrusted user: " + userAlias + ".");
		}
		
		return verified;
	}
}
