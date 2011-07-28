// TcpSecurityObject.java

package edu.vu.isis.ammo.core.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.security.AmmoSecurityManager;



public class TcpSecurityObject implements ISecurityObject,
                                          INetworkService.OnSendMessageHandler
{
    private static final Logger logger = LoggerFactory.getLogger( TcpSecurityObject.class );
    
    private Context mContext;
    
    private String mOperatorId;
    private String mOperatorKey;
    private String mDeviceId;

    TcpSecurityObject( TcpChannel iChannel, Context context )
    {
        logger.info( "Constructor of TcpSecurityObject." );
        mChannel = iChannel;
        
        mContext = context;
    }


    private AmmoSecurityManager	secMgr = null;

    public void authorize()
    {
        logger.info( "TcpSecurityObject::authorize()." );

        // Start the authorization process.

        getPreferences();
        
        if (secMgr == null)
//        	secMgr = new AmmoSecurityManager("DeviceID");
        	secMgr = new AmmoSecurityManager(mOperatorId);


        AmmoMessages.MessageWrapper.Builder builder = getClientNonce ();        
        
        AmmoGatewayMessage agm = AmmoGatewayMessage.newInstance(builder, this );

        mChannel.putFromSecurityObject( agm );
        mChannel.finishedPuttingFromSecurityObject();
    }



    public boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.info( "Delivering message to TcpSecurityObject." );
/*
        CRC32 crc32 = new CRC32();
        crc32.update(message);
        if (crc32.getValue() != checksum) {
            String msg = "you have received a bad message, the checksums did not match)"+
            Long.toHexString(crc32.getValue()) +":"+ Long.toHexString(checksum);
            // Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            logger.warn(msg);
            return false;
        }
*/
        AmmoMessages.MessageWrapper mw = null;
        
        try {
        
        	mw = AmmoMessages.MessageWrapper.parseFrom(agm.payload);
        	
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
        
        
        if (!mw.hasAuthenticationMessage())
        {
          mChannel.authorizationFailed();
          return false;
        }

        
        
        if (mw.getAuthenticationMessage().getResult() != AmmoMessages.AuthenticationMessage.Status.SUCCESS) 
        {
            mChannel.authorizationFailed();
            return false;
        }
        else 
        {
        	// Device authentication worked, so now verify the Gateway, sign 
        	if (mw.getAuthenticationMessage().getType() == AmmoMessages.AuthenticationMessage.Type.SERVER_NONCE)
        	{     		

        		System.out.println("THE SERVER NONCE " + mw.getAuthenticationMessage().getMessage().toString());
        		
        		secMgr.setServerNonce(mw.getAuthenticationMessage().getMessage().toByteArray());
        		

        		//send the key Exchange
        		AmmoMessages.MessageWrapper.Builder builder = getKeyExchange();
        		
	            AmmoGatewayMessage agmout = AmmoGatewayMessage.newInstance(builder, this );

	            mChannel.putFromSecurityObject( agmout );
	            
	            // now wait for a second or two and then send the PhoneAuth msg 
/*	            try {
	            	
					Thread.sleep(3000);
					
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/

	        	//send it the phone auth message
        		builder = getPhoneAuth();
        		
	            agmout = AmmoGatewayMessage.newInstance(builder, this );

	            mChannel.putFromSecurityObject( agmout );

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
	            
        		builder = getPhoneFinish();
        		
	            agmout = AmmoGatewayMessage.newInstance(builder, this );

	            mChannel.putFromSecurityObject( agmout );
	            
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
        		{
        			System.out.println("Gateway Finish Cannot Verify");
                                mChannel.authorizationFailed();
        			return false;
        		}
        			
        	}
        }
        
        
        // For now we haven't implemented any security.  Just
        // authorize if we receive a packet back from the server.
        mChannel.authorizationSucceeded( agm );

        // If authorization fails, call mChannel.authorizationFailed();

        return true;
    }

	private AmmoMessages.MessageWrapper.Builder getClientNonce () 
	{

		AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);

		mw.setSessionUuid("");
  
	    AmmoMessages.AuthenticationMessage.Builder authreq = AmmoMessages.AuthenticationMessage.newBuilder();
//	    authreq.setDeviceId(mDeviceId);
	    // HACKKKKKK
	    authreq.setDeviceId(mOperatorId);
	    authreq.setType(AmmoMessages.AuthenticationMessage.Type.CLIENT_NONCE);
	    authreq.setMessage(ByteString.copyFrom(secMgr.getNonce()));
	
	    mw.setAuthenticationMessage(authreq);
	    return mw;
	}

	private AmmoMessages.MessageWrapper.Builder getKeyExchange () 
	{		
		AmmoMessages.MessageWrapper.Builder msgW = AmmoMessages.MessageWrapper.newBuilder();
        msgW.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
        //mw.setSessionUuid(sessionId);
      
        AmmoMessages.AuthenticationMessage.Builder authreq = AmmoMessages.AuthenticationMessage.newBuilder();
        authreq.setType(AmmoMessages.AuthenticationMessage.Type.CLIENT_KEYXCHANGE);
        authreq.setMessage(ByteString.copyFrom(secMgr.generateKeyExchange()));

        msgW.setAuthenticationMessage(authreq);
        
        return msgW;
	}
	
	private AmmoMessages.MessageWrapper.Builder getPhoneFinish() 
	{
		AmmoMessages.MessageWrapper.Builder phnFinish = AmmoMessages.MessageWrapper.newBuilder();
        phnFinish.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
        //mw.setSessionUuid(sessionId);
      
        AmmoMessages.AuthenticationMessage.Builder phnFinAuth = AmmoMessages.AuthenticationMessage.newBuilder();

        phnFinAuth.setType(AmmoMessages.AuthenticationMessage.Type.CLIENT_FINISH);
        phnFinAuth.setMessage(ByteString.copyFrom(secMgr.generatePhoneFinish()));

    	phnFinish.setAuthenticationMessage(phnFinAuth);		
    	
    	return phnFinish;
	}
	
	private AmmoMessages.MessageWrapper.Builder getPhoneAuth()
	{
		AmmoMessages.MessageWrapper.Builder phnAuth = AmmoMessages.MessageWrapper.newBuilder();
        phnAuth.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
        //mw.setSessionUuid(sessionId);
      
        AmmoMessages.AuthenticationMessage.Builder phnAuthauth = AmmoMessages.AuthenticationMessage.newBuilder();

        phnAuthauth.setType(AmmoMessages.AuthenticationMessage.Type.CLIENT_PHNAUTH);
        phnAuthauth.setMessage(ByteString.copyFrom(secMgr.generatePhoneAuth()));

    	phnAuth.setAuthenticationMessage(phnAuthauth);
    	
    	return phnAuth;
	}

	private void getPreferences ()
	{
		logger.info("::getPreferences");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        mDeviceId = prefs.getString(INetPrefKeys.CORE_DEVICE_ID, this.mDeviceId);
        mOperatorId = prefs.getString(INetPrefKeys.CORE_OPERATOR_ID, this.mOperatorId);
        mOperatorKey = prefs.getString(INetPrefKeys.CORE_OPERATOR_KEY, this.mOperatorKey);

	}
    

    public boolean ack( boolean status )
    {
        return true;
    }


    private TcpChannel mChannel;
}
