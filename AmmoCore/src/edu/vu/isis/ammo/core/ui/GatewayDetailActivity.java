package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;

/**
 * Show details about the gateway.
 * Mostly explanatory information about the status.
 * 
 * @author phreed
 *
 */
public class GatewayDetailActivity extends ActivityEx
{
	public static final Logger logger = LoggerFactory.getLogger(GatewayDetailActivity.class);
	
	// ===========================================================
	// Fields
	// ===========================================================
	
	// ===========================================================
	// Views
	// ===========================================================
	
	
	/**
	 * @Cateogry Lifecycle
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.trace("::onCreate");
		this.setContentView(R.layout.gateway_detail_activity);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		logger.trace("::onStart");
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		logger.trace("::onDestroy");
	}
	
}
