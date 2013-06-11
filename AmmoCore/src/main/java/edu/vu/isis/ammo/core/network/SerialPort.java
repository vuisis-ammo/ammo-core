/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/

package edu.vu.isis.ammo.core.network;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.util.Log;

public class SerialPort {

    private static final String TAG = "SerialPort";

    /*
     * Do not remove or rename the field mFd: it is used by native method close();
     */
    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;

    public SerialPort(File device, int baudrate) throws SecurityException, IOException
    {
        Log.e(TAG, "SerialPort: about to call JNI open() method.");
        mFd = open(device.getAbsolutePath(), baudrate);
        if (mFd == null) {
            Log.e(TAG, "native open returns null");
            throw new IOException();
        }
        mFileInputStream = new FileInputStream(mFd);
        mFileOutputStream = new FileOutputStream(mFd);
    }


    // Getters and setters
    public FileInputStream getInputStream() { return mFileInputStream; }

    public FileOutputStream getOutputStream() { return mFileOutputStream; }


    // JNI
    private native static FileDescriptor open(String path, int baudrate);
    public native static boolean isCorrectTTY( String path );
    public native void close();

    public native int write( byte[] bytestosend );
}
