package edu.vu.isis.ammo.util;

import java.io.UnsupportedEncodingException;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final Logger logger = LoggerFactory.getLogger(PretrieTest.class);

	@Test
	public void basicPutAndGet() {
		final Pretrie<String> pretrie = new Pretrie<String>();
		logger.debug("begining 1st put set");
		pretrie.put(new byte[] { 'a', 'b', 'c', 'd', 'e' }, "abcde");
		pretrie.put(new byte[] { 'a', 'b', 'c', 'd', 'f' }, "abcdf");
		pretrie.put(new byte[] { 'a', 'b', 'c' }, "abc");

		logger.debug("begining 1st get");
		
		Assert.assertThat("just missed (no safetynet)",
				pretrie.get(new byte[] { 'a', 'b' }), CoreMatchers.nullValue());

		logger.debug("begining 2nd put set");
		pretrie.put(new byte[] { 'a' }, "a");
		
		logger.debug("begining 2nd get");
		Assert.assertThat("just missed (with safetynet)",
				pretrie.get(new byte[] { 'a', 'b' }), CoreMatchers.is("a"));

		logger.debug("begining 3rd get");
		Assert.assertThat("over shot",
				pretrie.get(new byte[] { 'a', 'b', 'c', 'd' }),
				CoreMatchers.is("abc"));

		logger.debug("begining 4th get");
		Assert.assertThat("exact hit",
				pretrie.get(new byte[] { 'a', 'b', 'c', 'd', 'e' }),
				CoreMatchers.is("abcde"));
		
		logger.debug("begining 5th get");
		Assert.assertThat("over shoot",
				pretrie.get(new byte[] { 'a', 'b', 'c', 'd', 'e', 'g' }),
				CoreMatchers.is("abcde"));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void basicInsertAndLongestPrefix() {
		final Pretrie<String> pretrie = new Pretrie<String>();
		try {
			pretrie.insert("abcde", "abcde");

			pretrie.insert("abcdf", "abcdf");
			pretrie.insert("abc", "abc");

			Assert.assertThat("just missed (no safetynet)",
					pretrie.longestPrefix("ab"), CoreMatchers.nullValue());

			pretrie.insert("a", "a");
			Assert.assertThat("just missed (with safetynet)",
					pretrie.longestPrefix("ab"), CoreMatchers.is("a"));

			Assert.assertThat("exact hit", pretrie.longestPrefix("abc"),
					CoreMatchers.is("abc"));

			Assert.assertThat("exact hit over", pretrie.longestPrefix("abcde"),
					CoreMatchers.is("abcde"));

			Assert.assertThat("over shoot", pretrie.longestPrefix("abcdeg"),
					CoreMatchers.is("abcde"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

	}

}
