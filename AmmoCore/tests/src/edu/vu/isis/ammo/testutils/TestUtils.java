package edu.vu.isis.ammo.core.testutils;

/**
 * Commonly-needed functions for testing, e.g. random string generation
 * 
 * 
 */


import java.math.BigInteger;
import java.util.Random;
import android.util.Log;

public class TestUtils 
{
    private static final String TAG = "TestUtils";

    // random seed 
    private static final Random random = new Random();

    // Symbol set from which to choose random text
    private static final char[] symbols = new char[36];
    static
    {
        for (int idx = 0; idx < 10; ++idx)
            symbols[idx] = (char) ('0' + idx);
        for (int idx = 10; idx < 36; ++idx)
            symbols[idx] = (char) ('a' + idx - 10);
    }

    
    // =========================================================
    // pseudoRandomString()
    // =========================================================
    private static String pseudoRandomString(int length)
    {
	if (length < 1)	{
	    throw new IllegalArgumentException("length < 1: " + length);
	}
	final char[] nonsecureBuffer = new char[length];
	for (int idx = 0; idx < nonsecureBuffer.length; ++idx) {
	    nonsecureBuffer[idx] = symbols[random.nextInt(symbols.length)];
	}
	return new String(nonsecureBuffer);
    }

    // =========================================================
    // another way to generate a pseudorandom string
    // =========================================================
    private static String anotherRandomString()
    {
        return new BigInteger(130, random).toString(32);
    }

    // =========================================================
    // randomText()
    // =========================================================
    public static String randomText(int size)
    {
        String f = anotherRandomString();
        Log.d(TAG, f);

        String g = pseudoRandomString(size);
        Log.d(TAG, g);

        return f;
    }
    
    // =========================================================
    // randomInt()
    // =========================================================
    public static int randomInt(int boundary)
    {
	int limit = boundary;
	if (boundary <= 1) {
	    limit = 1;
	}

	// random int on interval [0, limit)
        int f = random.nextInt(limit);
        return f;
    }

    // =========================================================
    // randomDouble()
    // =========================================================
    public static double randomDouble()
    {
	// random double on interval [0, 1)
	double f = random.nextDouble();
	return f;
    }
    
    // =========================================================
    // randomFloat()
    // =========================================================
    public static float randomFloat()
    {
	// random float on interval [0, 1)
	float f = random.nextFloat();
	return f;
    }
    
    // =========================================================
    // randomBoolean()
    // =========================================================
    public static boolean randomBoolean()
    {
	// random boolean
	boolean f = random.nextBoolean();
	return f;
    }
}

