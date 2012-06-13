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
