package edu.vu.isis.ammo.core.distributor;

import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.net.Uri;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;

public class RequestDeserializerThread extends Thread {
	private static final Logger logger = LoggerFactory.getLogger("dist.deserializer");

	private final LinkedBlockingQueue<Item> queue;

	static public class Item {
		final public Context context;
		final public Uri provider;
		final public Encoding encoding;
		final public byte[] data;

		public Item(final Context context, final Uri provider, final Encoding encoding, final byte[] data) {
			this.context = context;
			this.provider = provider;
			this.encoding = encoding;
			this.data = data;
		}
	}

	public RequestDeserializerThread() {
		this.queue = new LinkedBlockingQueue<Item>(200);
	}

	/**
	 * proxy for the RequestSerializer deserializeToProvider method.
	 * 
	 * @param context
	 * @param provider
	 * @param encoding
	 * @param data
	 * @return
	 */
	public boolean toProvider(Context context, Uri provider, Encoding encoding, byte[] data) {
		this.queue.offer(new Item(context, provider, encoding, data));
		return true;
	}

	@Override
	public void run()
	{
		try {
			while (true) {
				final Item item = this.queue.take();

				RequestSerializer.deserializeToProvider(item.context, item.provider, item.encoding, item.data);
			}

		} catch (Exception ex) {
			logger.info("thread being stopped ex=[{}]", ex);
		}
	}
}
