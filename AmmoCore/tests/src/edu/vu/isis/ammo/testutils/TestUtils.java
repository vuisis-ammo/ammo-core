package edu.vu.isis.ammo.testutils;

/**
 * Commonly-needed functions for testing, e.g. random string generation
 *
 *
 */


import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;

public class TestUtils
{
    @SuppressWarnings("unused")
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
        if (length < 1) {
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
    @SuppressWarnings("unused")
    private static String pseudoRandomString2()
    {
        return new BigInteger(130, random).toString(32);
    }

    // =========================================================
    // randomText()
    // =========================================================
    public static String randomText(int size)
    {
        return pseudoRandomString(size);
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

    // =========================================================
    // createJsonAsString()
    // =========================================================
    public static String createJsonAsString(ContentValues cv)
    {
        Set<Map.Entry<String, Object>> data = cv.valueSet();
        Iterator<Map.Entry<String, Object>> iter = data.iterator();
        final JSONObject json = new JSONObject();

        while (iter.hasNext())
        {
	    Map.Entry<String, Object> entry = (Map.Entry<String, Object>)iter.next();
	    try {
		if (entry.getValue() instanceof String)
		    json.put(entry.getKey(), cv.getAsString(entry.getKey()));
		else if (entry.getValue() instanceof Integer)
		    json.put(entry.getKey(), cv.getAsInteger(entry.getKey()));
	    } catch (JSONException e) {
		e.printStackTrace();
		return null;
	    }
	}
        return json.toString();
    }
    
    // =========================================================
    // createJsonAsBytes()
    // =========================================================
    public static byte[] createJsonAsBytes(ContentValues cv)
    {
        String jsonString = createJsonAsString(cv);
        return jsonString.getBytes();
    }

    // =========================================================
    // createContentValues()
    // =========================================================
    public static ContentValues createContentValues()
    {
	// TODO: make the key-value pairs something more meaningful

	ContentValues cv = new ContentValues();
	cv.put("foo1", "bar1");
	cv.put("foo2", "bar2");
	cv.put("foo3", "bar3");
	cv.put("foo4", "bar4");
	cv.put("foo5", "bar5");
	return cv;
    }
}

