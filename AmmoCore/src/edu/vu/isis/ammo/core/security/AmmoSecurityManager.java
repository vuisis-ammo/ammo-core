package edu.vu.isis.ammo.core.security;

import java.security.SecureRandom;

import com.google.protobuf.ByteString;

import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

public class AmmoSecurityManager {
	
	Ammo_Crypto crp_; 
	
	byte the_client_nonce[];
	
	byte the_server_nonce[];
	
	byte preMasterSecret[];
	
	byte keyExchange[];
	
	byte phoneAuth[];
	
	private final int PRE_MASTER_LENGTH = 48;
	private final int NONCE_LENGTH = 20;
	
	SecureRandom random;
	
	public AmmoSecurityManager (){
		
        // Create the Crypto Object for use 
        crp_ = new Ammo_Crypto ();
        
        random = new SecureRandom ();
	}
	
	private byte[] getNonce (){
		
		the_client_nonce = generateRandom(NONCE_LENGTH);
		
		return the_client_nonce;
	}
	
	public void setServerNonce (byte bytes[])
	{
		the_server_nonce = bytes;
	}
	
	private byte[] generatePreMasterSecret ()
	{
		preMasterSecret = generateRandom(PRE_MASTER_LENGTH);
		
		return preMasterSecret;
	}
	
	public byte[] generateKeyExchange ()
	{
		generatePreMasterSecret();
		
		keyExchange = 
			crp_.encrypt_data("/data/public_key_gateway.der", 
							  preMasterSecret, 
							  "RSA", 
							  "PKCS1Padding");
		
		return keyExchange;
	}
	
	public byte[] generatePhoneAuth ()
	{
		byte allData[] = 
			new byte[keyExchange.length + the_client_nonce.length + the_server_nonce.length];
		
		System.arraycopy(keyExchange, 0, allData, 0, keyExchange.length);
		
		System.arraycopy(the_client_nonce, 0, allData, keyExchange.length, the_client_nonce.length);
		
		System.arraycopy(
						the_server_nonce,
						0, 
						allData, 
						keyExchange.length + the_client_nonce.length, 
						the_server_nonce.length);
		
		
		phoneAuth = crp_.sign("/data/private_key_phone.der", allData, allData.length, "SHA1withRSA");
		
		return phoneAuth;
	}
	
	private byte[] generateRandom (int length)
	{
		byte bytes[] = new byte[length];
		
		random.nextBytes(bytes);
		
		return bytes;
	}

	public AmmoMessages.MessageWrapper.Builder getClientNonce (String deviceId) {

		AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
        //mw.setSessionUuid(sessionId);
      
        AmmoMessages.AuthenticationMessage.Builder authreq = AmmoMessages.AuthenticationMessage.newBuilder();
        authreq.setDeviceId(deviceId);
        authreq.setType(AmmoMessages.AuthenticationMessage.Type.CLIENT_NONCE);
        authreq.setMessage(ByteString.copyFrom(getNonce()));
        	//.setUserId(operatorId)
            //   .setUserKey(operatorKey);
            //   .setUserKey(str);
            //   .setUserKey(signStr);

        mw.setAuthenticationMessage(authreq);
        return mw;
	}
}
