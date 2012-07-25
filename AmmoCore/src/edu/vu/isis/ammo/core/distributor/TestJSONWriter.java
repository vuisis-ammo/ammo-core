package edu.vu.isis.ammo.core.distributor;
public class TestJSONWriter {

	public static String queueReport(String queueName,
			int queueSize) {
		final long timestamp = System.currentTimeMillis();
		return new StringBuilder("{\"timestamp\": ").append(timestamp)
				.append(", \"queue_name\": \"").append(queueName)
				.append("\", \"queue_size\": ").append(queueSize).append("}")
				.toString();
	}

}
