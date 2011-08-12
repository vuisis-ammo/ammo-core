package edu.vu.isis.ammo.core.security;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.protobuf.ByteString;

import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

public class AmmoSecurityManager {
	
	Ammo_Crypto crp_; 
	
//	final String AMMO_KEY_ROOT_DIR = "/mnt/sdcard/";
	final String AMMO_KEY_ROOT_DIR = "/data/";
	
	byte the_client_nonce[];
	
	byte the_server_nonce[];
	
	byte preMasterSecret[];
	
	byte keyExchange[];
	
	byte phoneAuth[];
	
	private final int PRE_MASTER_LENGTH = 48;
	private final int NONCE_LENGTH = 20;
	
	SecureRandom random;
	
	byte[] masterSecret;
	
	String mDevice;
	
	byte[] phoneFinish;
	
	public AmmoSecurityManager (String device_id){
		
        // Create the Crypto Object for use 
        crp_ = new Ammo_Crypto ();
        
        random = new SecureRandom ();
        
        mDevice = device_id;
	}
	
	public byte[] getNonce (){
		
		the_client_nonce = generateRandom(NONCE_LENGTH);
//		String str = "Nilabja";
//		the_client_nonce = str.getBytes();
		
		return the_client_nonce;
	}
	
	public void setServerNonce (byte bytes[])
	{
		the_server_nonce = bytes;
	}
	
	private byte[] generatePreMasterSecret ()
	{
		preMasterSecret = generateRandom(PRE_MASTER_LENGTH);
//		String str = "Vanderbilt";
//		preMasterSecret = str.getBytes();
		
		return preMasterSecret;
	}
	
	public byte[] generateKeyExchange ()
	{
		generatePreMasterSecret();
		
//		this.dump_to_file("/data/data/edu.vu.isis.ammo.core/masterSec", preMasterSecret);

		String public_file = AMMO_KEY_ROOT_DIR + "public_key_gateway.der";
		keyExchange = 
//			crp_.encrypt_data("/data/public_key_gateway.der", 
//			crp_.encrypt_data("/mnt/sdcard/public_key_gateway.der", 
			crp_.encrypt_data(public_file, 
							  preMasterSecret, 
							  "RSA", 
							  "PKCS1Padding");
		
		return keyExchange;
	}
	
	public byte[] generatePhoneAuth ()
	{
		
		byte[] data = this.concatBytes(keyExchange, the_client_nonce, the_server_nonce);
		
		String pvt_file = AMMO_KEY_ROOT_DIR + "private_key_phone.der";
		phoneAuth = crp_.sign(pvt_file, data, data.length, "SHA1withRSA");
//		phoneAuth = crp_.sign("/mnt/sdcard/private_key_phone.der", data, data.length, "SHA1withRSA");
//		phoneAuth = crp_.sign("/data/private_key_phone.der", data, data.length, "SHA1withRSA");
		
		return phoneAuth;
	}
	
	private byte[] generateRandom (int length)
	{
		byte bytes[] = new byte[length];
		
		random.nextBytes(bytes);
		
		return bytes;
	}

	
	public void computeMasterSecret ()
	{
		 // Create a Message Digest from a Factory method
		MessageDigest md = null;
		
		try {
		
			md = MessageDigest.getInstance("SHA-256");
			
		} catch (NoSuchAlgorithmException e) {
			
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	      // Create the content 
	      // SHA-256('A'||S||client_nonce||server_nonce)
	      byte[] buf = new byte[1];
	      buf[0] = 'A';
	      
//	      byte[] content = concatBytes(buf, this.preMasterSecret, the_client_nonce, the_server_nonce);
	      byte[] content = concatBytes(this.preMasterSecret, the_client_nonce, the_server_nonce);

	     // this.dump_to_file("/data/data/edu.vu.isis.ammo.core/masterSec", content);
	      // Update the message digest with some more bytes
	      // This can be performed multiple times before creating the hash
	      md.update(content);

	      // Create the digest from the message
	      // this is the first level 
	      byte[] first_level = md.digest();
	      
	      content = concatBytes(this.preMasterSecret, first_level);
	      
	      md.reset();
	      
	      md.update(content);
	      
	      this.masterSecret = md.digest();
	      
	}
	
	// concats a list of byte arrays ..
	private byte [] concatBytes (byte[]... bytelist)
	{
		
		int total_len = 0;
		
		for (byte[] bytes : bytelist)
			total_len += bytes.length;
			
		byte allData[] = new byte[total_len];
		

		int content_len = 0;
		
		for (byte[] bytes : bytelist)
		{
			System.arraycopy(bytes, 0, allData, content_len, bytes.length);
			content_len += bytes.length;
		}

		return allData;
	}
	
	public byte [] generatePhoneFinish ()
	{
		// this message has a nested digest .... 
		
		//first level 
		byte[] handshake = concatBytes(this.phoneAuth, this.keyExchange, this.the_client_nonce, this.the_server_nonce);
		
		byte[] content = concatBytes(handshake, mDevice.getBytes(), masterSecret/*, there needs to be a pad here*/ );

		MessageDigest md = null;
		
		try {
		
			md = MessageDigest.getInstance("SHA-256");
			
		} catch (NoSuchAlgorithmException e) {
			
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		md.update(content);
		
		byte[] first_level = md.digest();
		
		// upper level 
		content = concatBytes(masterSecret, /*need padding here*/first_level);
		
		md.reset();
		
		md.update(content);
		
		phoneFinish = md.digest();
		
		return phoneFinish;
	}
	
	void dump_to_file (String file, byte[] buffer)
	{
		try
		{      
//			getBaseContext() openFileOutput ()
			DataOutputStream out = new DataOutputStream(
						new FileOutputStream(file));

		     out.write(buffer);
		     out.close();
		}
		catch ( IOException iox )
		{
			System.out.println("Problem writing " + file );
		}
	}
	
	public boolean verify_GW_finish (byte[] gw_finish)
	{
		return Arrays.equals(gw_finish, generate_GW_finish());
	}
	
	private byte[] generate_GW_finish ()
	{
		byte[] handshake = concatBytes(this.phoneAuth, this.keyExchange, this.the_client_nonce, this.the_server_nonce);
		
		String gateway_id = "MainGateway";
		
		byte[] content = concatBytes(handshake, gateway_id.getBytes(), masterSecret/*, there needs to be a pad here*/ );

		MessageDigest md = null;
		
		try {
		
			md = MessageDigest.getInstance("SHA-256");
			
		} catch (NoSuchAlgorithmException e) {
			
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		md.update(content);
		
		byte[] first_level = md.digest();
		
		// upper level 
		content = concatBytes(masterSecret, /*need padding here*/first_level);
		
		md.reset();
		
		md.update(content);
		
		phoneFinish = md.digest();
		
		return phoneFinish;
	}
	
}
