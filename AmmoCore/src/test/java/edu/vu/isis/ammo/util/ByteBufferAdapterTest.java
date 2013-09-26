package edu.vu.isis.ammo.util;

import java.io.IOException;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class ByteBufferAdapterTest extends TestCase {
	/**
	 * Create the test case
	 *
	 * @param testName name of the test case
	 */
	public ByteBufferAdapterTest( String testName ) {
		super( testName );
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite( ByteBufferAdapterTest.class );
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}
	
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		ByteBufferPool.getInstance().dispose();
	}
	
	public void testBufferPool() {
		ByteBufferPool pool = ByteBufferPool.getInstance();
		Assert.assertEquals(0, pool.getByteSize());
		Assert.assertEquals(0, pool.getHitCount());
		Assert.assertEquals(0, pool.getMissCount());
		Assert.assertEquals(0, pool.getNodeCount());
		
		ByteBufferAdapter adapter = ByteBufferAdapter.obtain(new byte[10]);	
		Assert.assertEquals(0, pool.getByteSize());
		Assert.assertEquals(0, pool.getHitCount());
		Assert.assertEquals(0, pool.getMissCount());
		Assert.assertEquals(0, pool.getNodeCount());
		adapter.release();
		adapter.release();
		Assert.assertEquals(0, pool.getByteSize());
		Assert.assertEquals(0, pool.getHitCount());
		Assert.assertEquals(0, pool.getMissCount());
		Assert.assertEquals(0, pool.getNodeCount());
		
		
		adapter = ByteBufferAdapter.obtain(10);		
		Assert.assertEquals(0, pool.getByteSize());
		Assert.assertEquals(0, pool.getHitCount());
		Assert.assertEquals(1, pool.getMissCount());
		Assert.assertEquals(0, pool.getNodeCount());
		adapter.release();
		adapter.release();
		Assert.assertEquals(pool.getMinBufferSize(), pool.getByteSize());
		Assert.assertEquals(0, pool.getHitCount());
		Assert.assertEquals(1, pool.getMissCount());
		Assert.assertEquals(1, pool.getNodeCount());
		
		
		adapter = ByteBufferAdapter.obtain(10);		
		Assert.assertEquals(0, pool.getByteSize());
		Assert.assertEquals(1, pool.getHitCount());
		Assert.assertEquals(1, pool.getMissCount());
		Assert.assertEquals(0, pool.getNodeCount());
		adapter.release();
		adapter.release();
		Assert.assertEquals(pool.getMinBufferSize(), pool.getByteSize());
		Assert.assertEquals(1, pool.getHitCount());
		Assert.assertEquals(1, pool.getMissCount());
		Assert.assertEquals(1, pool.getNodeCount());
		
		
		adapter = ByteBufferAdapter.obtain(5);		
		Assert.assertEquals(0, pool.getByteSize());
		Assert.assertEquals(2, pool.getHitCount());
		Assert.assertEquals(1, pool.getMissCount());
		Assert.assertEquals(0, pool.getNodeCount());
		adapter.release();
		adapter.release();
		Assert.assertEquals(pool.getMinBufferSize(), pool.getByteSize());
		Assert.assertEquals(2, pool.getHitCount());
		Assert.assertEquals(1, pool.getMissCount());
		Assert.assertEquals(1, pool.getNodeCount());
		
		adapter = ByteBufferAdapter.obtain(pool.getMinBufferSize()+1);
		Assert.assertEquals(pool.getMinBufferSize(), pool.getByteSize());
		Assert.assertEquals(2, pool.getHitCount());
		Assert.assertEquals(2, pool.getMissCount());
		Assert.assertEquals(1, pool.getNodeCount());
		adapter.release();
		adapter.release();
		Assert.assertEquals(pool.getMinBufferSize()*3, pool.getByteSize());
		Assert.assertEquals(2, pool.getHitCount());
		Assert.assertEquals(2, pool.getMissCount());
		Assert.assertEquals(2, pool.getNodeCount());
	}
	
	
	public void testChannelReadWrite() throws Exception {
		StringChannel channel = new StringChannel("This is my channel data");
		ByteBufferAdapter adapter = ByteBufferAdapter.obtain(1024);
		adapter.read(channel);
		
		adapter.flip();
		StringChannel channel2 = new StringChannel();
		adapter.write(channel2);
		
		Assert.assertEquals(channel.toString(), channel2.toString());
	}
	
	public void testExpandoByteBufferAdapter() throws Exception {
		ByteBufferAdapter expando = createExpando();
		ByteBufferAdapter flat = createFlatAdapter();		
		
		System.err.println(expando);
		StringChannel channel1 = new StringChannel();
		expando.write(channel1);
		StringChannel channel2 = new StringChannel();
		flat.write(channel2);
		
		Assert.assertEquals("Something is wrong with " + expando,channel2.toString(), channel1.toString());
	}
	
	public void testNestedExpandoByteBufferAdapter() throws Exception {
		ByteBufferAdapter expando = createNestedExpando();
		ByteBufferAdapter flat = createFlatAdapter();		
		
		System.err.println(expando);
		StringChannel channel1 = new StringChannel();
		expando.write(channel1);
		StringChannel channel2 = new StringChannel();
		flat.write(channel2);
		
		Assert.assertEquals("Something is wrong with " + expando,channel2.toString(), channel1.toString());
	}
	
	public void testExpandoChecksum() throws Exception {
		ByteBufferAdapter expando = createNestedExpando();
		ByteBufferAdapter flat = createFlatAdapter();
		Assert.assertEquals(flat.checksum().toHexString(), expando.checksum().toHexString());
	}
	
	private ByteBufferAdapter createFlatAdapter() throws IOException {
		return ByteBufferAdapter.obtain("adapter11adapter22adapter33adapter44adapter55".getBytes());
	}
	
	private ExpandoByteBufferAdapter createExpando() throws IOException {
		ByteBufferAdapter adapter1 = ByteBufferAdapter.obtain(1024);
		ByteBufferAdapter adapter2 = ByteBufferAdapter.obtain(1024);
		ByteBufferAdapter adapter3 = ByteBufferAdapter.obtain(1024);
		ByteBufferAdapter adapter4 = ByteBufferAdapter.obtain(8);
		ByteBufferAdapter adapter5 = ByteBufferAdapter.obtain("adapter5".getBytes());
		adapter1.put("adapter1".getBytes());
		adapter2.read(new StringChannel("adapter2"));
		adapter3.put("adapter3".getBytes());
		adapter4.read(new StringChannel("adapter4"));
		adapter1.flip();
		adapter2.flip();
		adapter3.flip();
		adapter4.flip();
		
		ExpandoByteBufferAdapter expando = ByteBufferAdapter.obtain();
		expando.add(adapter1);
		expando.put("1".getBytes());
		expando.add(adapter2);
		expando.put("2".getBytes());
		expando.add(adapter3);
		expando.put("3".getBytes());
		expando.add(adapter4);
		expando.put("4".getBytes());
		expando.add(adapter5);
		expando.put("5".getBytes());
		expando.flip();
		return expando;
	}
	
	private ExpandoByteBufferAdapter createNestedExpando() throws IOException {
		ByteBufferAdapter adapter1 = ByteBufferAdapter.obtain(1024);
		ByteBufferAdapter adapter2 = ByteBufferAdapter.obtain(1024);
		ByteBufferAdapter adapter3 = ByteBufferAdapter.obtain(1024);
		ByteBufferAdapter adapter4 = ByteBufferAdapter.obtain(8);
		ByteBufferAdapter adapter5 = ByteBufferAdapter.obtain("adapter5".getBytes());
		adapter1.put("adapter1".getBytes());
		adapter2.read(new StringChannel("adapter2"));
		adapter3.put("adapter3".getBytes());
		adapter4.read(new StringChannel("adapter4"));
		adapter1.flip();
		adapter2.flip();
		adapter3.flip();
		adapter4.flip();
		
		
		ExpandoByteBufferAdapter expando = ByteBufferAdapter.obtain();
		expando.add(adapter1);
		expando.put("1".getBytes());
		expando.flip();
		
		ExpandoByteBufferAdapter expando2 = ByteBufferAdapter.obtain();
		expando2.add(expando);
		expando2.add(adapter2);
		expando2.put("2".getBytes());
		expando2.flip();
		
		ExpandoByteBufferAdapter expando3 = ByteBufferAdapter.obtain();
		expando3.add(expando2);
		expando3.add(adapter3);
		expando3.put("3".getBytes());
		expando3.flip();
		
		ExpandoByteBufferAdapter expando4 = ByteBufferAdapter.obtain();
		expando4.add(expando3);
		expando4.add(adapter4);
		expando4.put("4".getBytes());
		expando4.flip();
		
		ExpandoByteBufferAdapter expando5 = ByteBufferAdapter.obtain();
		expando5.add(expando4);
		expando5.add(adapter5);
		expando5.put("5".getBytes());
		expando5.flip();
		return expando5;
	}
}
