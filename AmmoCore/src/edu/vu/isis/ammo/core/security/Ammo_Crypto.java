package edu.vu.isis.ammo.core.security;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;


public class Ammo_Crypto {

	public byte[] decrypt_data (String pvt_key, byte[] data, String algo, String padding)
    {
       	try{
       	  
			 System.out.println("Getting the private key");
			 
			 PrivateKey pvt = getPrivateKey(pvt_key);
			 
//			Cipher cipher = Cipher.getInstance("RSA");
			Cipher cipher = Cipher.getInstance(algo + "/None/" + padding);
			cipher.init(Cipher.DECRYPT_MODE, pvt);
			 
			byte[] actualData = cipher.doFinal(data);
			
			return actualData;				 
			
       	} 
       	catch (Exception e) {
			    
				System.err.println("Caught exception " + e.toString());
       	}    
       	
       	return null;
    	
    }
	
	public static PublicKey getPublicKey(String filename)
	throws Exception {
	
	File f = new File(filename);
	
	FileInputStream fis = new FileInputStream(f);
	DataInputStream dis = new DataInputStream(fis);

	byte[] keyBytes = new byte[(int)f.length()];

	dis.readFully(keyBytes);

	dis.close();

	X509EncodedKeySpec spec =
					new X509EncodedKeySpec(keyBytes);
	
	KeyFactory kf = KeyFactory.getInstance("RSA");

	return kf.generatePublic(spec);
}



	public static PrivateKey getPrivateKey(String filename)
	throws Exception {

	File f = new File(filename);
	
	FileInputStream fis = new FileInputStream(f);
	DataInputStream dis = new DataInputStream(fis);
	
	byte[] keyBytes = new byte[(int)f.length()];
	dis.readFully(keyBytes);
	dis.close();

	PKCS8EncodedKeySpec spec =
				new PKCS8EncodedKeySpec(keyBytes);

	KeyFactory kf = KeyFactory.getInstance("RSA");

	return kf.generatePrivate(spec);
}

	public byte[] encrypt_data (String pub_key, byte[] data, String algo, String padding)
{
	try {
        System.out.println("Getting the public key");
        PublicKey pub = getPublicKey(pub_key);
                   
        Cipher cipher = Cipher.getInstance(algo + "/None/" + padding);
        
        cipher.init(Cipher.ENCRYPT_MODE, pub);
        
        byte[] cipherData = cipher.doFinal(data);
        
        return cipherData;
       
     } catch (Exception e) {
        
        	System.err.println("Caught exception " + e.toString());
        
     }
     
     return null;
}

	public byte[] sign (String key_file, byte[] data,int data_len, String algo)
{
	
	try {
		
		System.out.println("Getting the key pair");
		PrivateKey priv = getPrivateKey(key_file);
//		PublicKey pub = getPublicKey("public_key.der");
		/* Create a Signature object and initialize it with the private key */
 	      
//		Signature dsa = Signature.getInstance("SHA1withRSA", "SunJSSE");
		Signature dsa = Signature.getInstance(algo);
	//	Signature dsa = Signature.getInstance("SHA1withDSA", "SUN");
 
        dsa.initSign(priv);
    
        /* Update and sign the data */
        dsa.update(data, 0, data_len);		    
	    
        /* Now that all the data to be signed has been read in, 
	    generate a signature for it */
	    byte[] realSig = dsa.sign();
	    
	    
	    return realSig;
	    
	} 
	catch (Exception e) {
		
		System.err.println("Caught exception " + e.toString());
	}

	return null;
}


	public boolean verify (String pubkey, 
							byte []sign, 
							byte[] data, 
							int data_len, 
							String algo)
	{
		try {
		
			PublicKey pubKey = getPublicKey (pubkey);
				
			/* create a Signature object and initialize it with the public key */
			Signature sig = Signature.getInstance(algo);
			sig.initVerify(pubKey);
			
			/* Update and verify the data */
			sig.update(data, 0, data_len);
			
			boolean verifies = sig.verify(sign);
			
			return verifies;
		}
		catch (Exception e) {
		
			System.err.println("Caught exception " + e.toString());
		}
		
		return false;
	}

}
