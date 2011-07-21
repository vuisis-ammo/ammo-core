// TcpSecurityObject.java

package edu.vu.isis.ammo.core.network;


import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.core.network.NetworkService.MsgHeader;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.security.AmmoSecurityManager;


public class TcpSecurityObject implements ISecurityObject
{
    private static final Logger logger = LoggerFactory.getLogger( TcpSecurityObject.class );

    TcpSecurityObject( TcpChannel iChannel )
    {
        logger.info( "Constructor of TcpSecurityObject." );
        mChannel = iChannel;
    }


    private AmmoSecurityManager	secMgr = null;
    
    public void authorize()
    {
        logger.info( "TcpSecurityObject::authorize()." );

        // Start the authorization process.
        if (secMgr == null)
        	secMgr = new AmmoSecurityManager();

        AmmoMessages.MessageWrapper.Builder mw = 
        	//secMgr.getClientNonce(UniqueIdentifiers.device(this.getApplicationContext()));
        	secMgr.getClientNonce("DeviceID");
        byte[] protocByteBuf = mw.build().toByteArray();
        MsgHeader msgHeader = MsgHeader.getInstance( protocByteBuf, true );

        mChannel.putFromSecurityObject( msgHeader.size,
                                        msgHeader.checksum,
                                        protocByteBuf,
                                        null );
        mChannel.finishedPuttingFromSecurityObject();
    }


    public boolean deliverMessage( byte[] message,
                                   long checksum )
    {
        logger.info( "Delivering message to TcpSecurityObject." );

        CRC32 crc32 = new CRC32();
        crc32.update(message);
        if (crc32.getValue() != checksum) {
            String msg = "you have received a bad message, the checksums did not match)"+
            Long.toHexString(crc32.getValue()) +":"+ Long.toHexString(checksum);
            // Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            logger.warn(msg);
            return false;
        }

        AmmoMessages.MessageWrapper mw = null;
        
        try {
        
        	mw = AmmoMessages.MessageWrapper.parseFrom(message);
        	
        } 
        catch (InvalidProtocolBufferException ex) 
        {
            ex.printStackTrace();
        }
        
        if (mw == null)
        {
            logger.error( "mw was null!" );
            return false; // TBD SKN: this was true, why? if we can't parse it then its bad
        }
        
        
        if (!mw.hasAuthenticationMessage()) return false;
        
        
        if (mw.getAuthenticationMessage().getResult() != AmmoMessages.AuthenticationMessage.Status.SUCCESS) 
        {
            return false;
        }
        else 
        {
        	// Device authentication worked, so now verify the Gateway, sign 
        	if (mw.getAuthenticationMessage().getType() == AmmoMessages.AuthenticationMessage.Type.SERVER_NONCE)
        	{     		

        		System.out.println("THE SERVER NONCE " + mw.getAuthenticationMessage().getMessage().toString());
        		
        		secMgr.setServerNonce(mw.getAuthenticationMessage().getMessage().toByteArray());
        		
       

        		// send the keyExchange ...
        		AmmoMessages.MessageWrapper.Builder msgW = AmmoMessages.MessageWrapper.newBuilder();
	            msgW.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
	            //mw.setSessionUuid(sessionId);
	          
	            AmmoMessages.AuthenticationMessage.Builder authreq = AmmoMessages.AuthenticationMessage.newBuilder();
	            authreq.setType(AmmoMessages.AuthenticationMessage.Type.CLIENT_KEYXCHANGE);
	            authreq.setMessage(ByteString.copyFrom(secMgr.generateKeyExchange()));
	
	            msgW.setAuthenticationMessage(authreq);
	            
	            byte[] protocByteBuf = msgW.build().toByteArray();
	            MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);
	
	           // sendRequest(msgHeader.size, msgHeader.checksum, protocByteBuf, this);
	            
	            
	            mChannel.putFromSecurityObject(	msgHeader.size,
	            								msgHeader.checksum,
	            								protocByteBuf,
	            								null);
        
	            
	            
	            // now wait for a second or two and then send the PhoneAuth msg 
/*	            try {
	            	
					Thread.sleep(3000);
					
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/

	        	//send it the phone auth message
				AmmoMessages.MessageWrapper.Builder phnAuth = AmmoMessages.MessageWrapper.newBuilder();
	            phnAuth.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
	            //mw.setSessionUuid(sessionId);
	          
	            AmmoMessages.AuthenticationMessage.Builder phnAuthauth = AmmoMessages.AuthenticationMessage.newBuilder();
	
	            phnAuthauth.setType(AmmoMessages.AuthenticationMessage.Type.CLIENT_PHNAUTH);
	            phnAuthauth.setMessage(ByteString.copyFrom(secMgr.generatePhoneAuth()));

	        	phnAuth.setAuthenticationMessage(phnAuthauth);
	            
	            byte[] phnAuthProtocByteBuf = phnAuth.build().toByteArray();
	            MsgHeader msgHeaderPhnAuth = MsgHeader.getInstance(phnAuthProtocByteBuf , true);
	
//	            sendRequest(msgHeaderPhnAuth.size, msgHeaderPhnAuth.checksum, phnAuthProtocByteBuf , this);

	            mChannel.putFromSecurityObject(	msgHeaderPhnAuth.size,
	            								msgHeaderPhnAuth.checksum,
	            								phnAuthProtocByteBuf,
	            								null);
	            // compute the master secret ....
	            secMgr.computeMasterSecret();
	            
	            // now wait for a second or two and then send the Phone Finsh msg 
	            try {
	            	
					Thread.sleep(3000);
					
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	            // send the phone finish ....
	            
				AmmoMessages.MessageWrapper.Builder phnFinish = AmmoMessages.MessageWrapper.newBuilder();
	            phnFinish.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
	            //mw.setSessionUuid(sessionId);
	          
	            AmmoMessages.AuthenticationMessage.Builder phnFinAuth = AmmoMessages.AuthenticationMessage.newBuilder();
	
	            phnFinAuth.setType(AmmoMessages.AuthenticationMessage.Type.CLIENT_FINISH);
	            phnFinAuth.setMessage(ByteString.copyFrom(secMgr.generatePhoneFinish()));

	        	phnFinish.setAuthenticationMessage(phnFinAuth);
	            
	            byte[] phnFinProtocByteBuf = phnFinish.build().toByteArray();
	            MsgHeader msgHeaderPhnFin = MsgHeader.getInstance(phnFinProtocByteBuf , true);
	
	           // sendRequest(msgHeaderPhnFin.size, msgHeaderPhnFin.checksum, phnFinProtocByteBuf , this);

	            
	            mChannel.putFromSecurityObject(	msgHeaderPhnFin.size,
	            								msgHeaderPhnFin.checksum,
	            								phnFinProtocByteBuf,
	            								null);
	            
	            mChannel.finishedPuttingFromSecurityObject();
	        	return true;
        	}
        	else if (mw.getAuthenticationMessage().getType() == AmmoMessages.AuthenticationMessage.Type.SERVER_FINISH)
        	{
        		// need to verify the gateway finish message ...
        		boolean fin_verify = secMgr.verify_GW_finish (mw.getAuthenticationMessage().getMessage().toByteArray());
        		
        		if (fin_verify)
        			System.out.println("Gateway Finish Verified");
        		else
        			System.out.println("Gateway Finish Cannot Verify");
        			
        	}
        	
        	        	
        }
        
        
        // For now we haven't implemented any security.  Just
        // authorize if we receive a packet back from the server.
        mChannel.authorizationSucceeded();

        // If authorization fails, call mChannel.authorizationFailed();

        return true;
    }


    private TcpChannel mChannel;
}
