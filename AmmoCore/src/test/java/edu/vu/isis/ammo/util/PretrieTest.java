package edu.vu.isis.ammo.util;


import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import edu.vu.isis.ammo.pretrie.Pretrie;

public class PretrieTest {
	
	@Test
	public void basicPutAndGet() {
		final Pretrie<String> pretrie = new Pretrie<String>();
		pretrie.put(new byte[]{'a','b','c','d'}, "abcd");
		pretrie.put(new byte[]{'a'}, "a");
		final String expectedWithAbc = pretrie.get(new byte[]{'a','b','c'});
		Assert.assertThat("shorter", expectedWithAbc, CoreMatchers.is("a"));
	}

}
