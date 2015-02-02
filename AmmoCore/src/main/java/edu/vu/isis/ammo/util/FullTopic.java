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

/**
 * Assist in combining the topic object.
 * This should probably be moved to 
 * AmmoLib/edu/vu/isis/ammo/core/api/type/Topic.java but
 * it can stay here for now.
 *
 */

public class FullTopic {
	private static final String TOPIC_JOIN_CHAR = "+";
	private static final String TOPIC_SPLIT_PATTERN = "\\+";

	final public String topic;
	final public String subtopic;
	final public String aggregate;

	public FullTopic(final String aggregate) {
		this.aggregate = aggregate;

		final String[] list = aggregate.split(TOPIC_SPLIT_PATTERN, 2);

		if (list.length < 1) {
			this.topic = "";
			this.subtopic = "";
			return;
		}

		this.topic = list[0];
		if (list.length < 2) {
			this.subtopic = "";
			return;
		} 

		this.subtopic = list[1];
	}

	public FullTopic(final String topic, final String subtopic) {
		this.topic = topic;
		this.subtopic = subtopic;

		final StringBuilder sb = new StringBuilder().append(topic);
		if (subtopic != null && subtopic.length() > 0) {
			sb.append(TOPIC_JOIN_CHAR).append(subtopic);
		}
		this.aggregate = sb.toString();
	}
	

	@Override
	public String toString() {
		return this.aggregate;
	}

	public static FullTopic fromType(String type) {
		return new FullTopic(type);
	}

	public static FullTopic fromTopic(String topic, String subtopic) {
		return new FullTopic(topic, subtopic);
	}

}
