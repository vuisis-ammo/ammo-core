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
public class NaivePretrieTest {

	@Test
	public void basicPutAndGet() {
		final PrefixList<String> pretrie = new PrefixList<String>();
		pretrie.insert("abcde", "abcde");
		pretrie.insert("abcdf", "abcdf");
		pretrie.insert("abc", "abc");

		Assert.assertThat("just missed (no safetynet)",
				pretrie.longestPrefix("ab"), CoreMatchers.nullValue());

		pretrie.insert("a", "a");
		Assert.assertThat("just missed (with safetynet)",
				pretrie.longestPrefix("ab"), CoreMatchers.is("a"));

		Assert.assertThat("exact hit",
				pretrie.longestPrefix("abc"),
				CoreMatchers.is("abc"));

		Assert.assertThat("exact hit over",
				pretrie.longestPrefix("abcde"),
				CoreMatchers.is("abcde"));

		Assert.assertThat("over shoot",
				pretrie.longestPrefix("abcdeg"),
				CoreMatchers.is("abcde"));
		
	}

}
