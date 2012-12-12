package edu.vu.isis.ammo.util;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import edu.vu.isis.ammo.pretrie.Pretrie;

/**
 * Test the basic functionality of the pretrie. The pretrie is a tree whose keys
 * are prefixes. When queried the pretrie returns the values having the longest
 * matching prefix.
 * <p>
 * When presented with a key the pretrie will select the longest of its matching
 * prefixes. A matching prefix is one which is a prefix to the supplied key.
 * 
 */
public class PretrieTest {

	@Test
	public void basicPutAndGet() {
		final Pretrie<String> pretrie = new Pretrie<String>();
		pretrie.put(new byte[] { 'a', 'b', 'c', 'd', 'e' }, "abcde");
		pretrie.put(new byte[] { 'a', 'b', 'c', 'd', 'f'}, "abcdf");
		pretrie.put(new byte[] { 'a', 'b', 'c' }, "abc");

		Assert.assertThat("just missed (no safetynet)",
				pretrie.get(new byte[] { 'a', 'b' }), CoreMatchers.nullValue());

		pretrie.put(new byte[] { 'a' }, "a");
		Assert.assertThat("just missed (with safetynet)",
				pretrie.get(new byte[] { 'a', 'b' }), CoreMatchers.is("a"));

		Assert.assertThat("exact hit",
				pretrie.get(new byte[] { 'a', 'b', 'c', 'd' }),
				CoreMatchers.is("abcd"));

		Assert.assertThat("exact hit over",
				pretrie.get(new byte[] { 'a', 'b', 'c', 'd', 'e' }),
				CoreMatchers.is("abcde"));

		Assert.assertThat("over shoot",
				pretrie.get(new byte[] { 'a', 'b', 'c', 'd', 'e', 'g' }),
				CoreMatchers.is("abcde"));
	}

}
