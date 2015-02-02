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


package edu.vu.isis.ammo.util;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import edu.vu.isis.ammo.pretrie.PrefixList;

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
