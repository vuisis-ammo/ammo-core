/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package edu.vu.isis.ammo.core;

import java.util.HashSet;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import edu.vu.isis.ammo.core.distributor.store.Presence;
import edu.vu.isis.ammo.core.distributor.store.Presence.Builder;
import edu.vu.isis.ammo.core.distributor.store.Presence.Item;
import edu.vu.isis.ammo.core.provider.TemporalState;
/**
 * Unit test for TemporalState
 * 
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.provider.TemporalStateTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */

public class TemporalStateTest extends TestCase
{
    public TemporalStateTest( String testName )
    {
        super( testName );
    }

    public static Test suite()
    {
        return new TestSuite( TemporalStateTest.class );
    }

    /**
     * Called before every test
     */
    protected void setUp() throws Exception
    {
	// ...
    }

    /**
     * Called after every test
     */
    protected void tearDown() throws Exception
    {
	// ...
    }

    /**
     * Test methods
     */
    public void testBasicConstruction()
    {
	Builder b = Presence.newBuilder();
	assertNotNull(b);
	b.operator("bubba");
	b.origin("ffffffff");
	Item item = b.buildItem();
	assertNotNull(item);
	assertEquals(item.count, 1);
	
	item.update();
	assertEquals(item.count, 2);
	
	// The user was just now "seen" so state should be present
	TemporalState ts = item.getDominantState();
	assertEquals(ts, TemporalState.PRESENT);
    }
    
    public void testEncodeState()
    {
	Builder b = Presence.newBuilder();
	assertNotNull(b);
	b.operator("bubba");
	b.origin("ffffffff");
	Item item = b.buildItem();
	assertNotNull(item);
	item.update();	
	TemporalState ts = item.getDominantState();
	
	HashSet<TemporalState> set = new HashSet<TemporalState>();
	set.add(ts);
	int encoded = TemporalState.encodeState(set);
	assertEquals(encoded, 1);
    }
    
    public void testDecodeState()
    {
	Builder b = Presence.newBuilder();
	assertNotNull(b);
	b.operator("bubba");
	b.origin("ffffffff");
	Item item = b.buildItem();
	assertNotNull(item);
	item.update();	
	TemporalState ts = item.getDominantState();
	
	HashSet<TemporalState> set = new HashSet<TemporalState>();
	set.add(ts);
	int encoded = TemporalState.encodeState(set);
	
	TemporalState ts2 = TemporalState.decodeState(encoded);
	assertEquals(ts2, TemporalState.PRESENT);
    }
}

