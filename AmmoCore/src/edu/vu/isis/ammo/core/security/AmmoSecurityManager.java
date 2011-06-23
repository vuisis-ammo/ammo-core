package edu.vu.isis.ammo.core.security;

import java.security.SecureRandom;

import com.google.protobuf.ByteString;

import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

public class AmmoSecurityManager {
	
	Ammo_Crypto crp_; 
	AmmoSecurityManager (){
		
        // Create the Crypto Object for use 
        crp_ = new Ammo_Crypto ();
	}
	
	byte[] getNonce (){
		
		SecureRandom random = new SecureRandom ();
		byte bytes[] = new byte[20];
		random.nextBytes(bytes);
		
		return bytes;
	}

	public AmmoMessages.MessageWrapper.Builder getClientNonce (String deviceId) {

		AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
        //mw.setSessionUuid(sessionId);

        
//        byte[] enc_operatorKey = 
//        	crp.encrypt_data("/data/public_key_phone.der", operatorKey.getBytes(), "RSA", "PKCS1Padding");
        
//        byte[] sign = crp.sign("/mnt/sdcard/private_key_phone.der", 
        
      //  ByteString str = ByteString.copyFrom(enc_operatorKey);
        
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
