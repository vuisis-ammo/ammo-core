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

package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.provider.DistributorSchema;
import edu.vu.isis.ammo.core.provider.Relations;

public class MessageQueueActivity extends Activity {
	public static final Logger logger = LoggerFactory.getLogger("ui.messagequeue");

	// ===============================================
	// Activity Lifecycle
	// ===============================================
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_queue_activity);
        setupView();
        setOnClickListeners();
        
        @SuppressWarnings("unused")
		String tableName = Relations.DISPOSAL.n;
        @SuppressWarnings("unused")
		Uri uri = DistributorSchema.CONTENT_URI.get(Relations.CHANNEL);
        Cursor c = this.getContentResolver().query(DistributorSchema.CONTENT_URI.get(Relations.DISPOSAL), null, null, null, null);
        logger.trace("message{}", c);
        
        
        while(c.moveToNext()) {
        	String channelName = c.getString(c.getColumnIndex(DistributorDataStore.DisposalTableSchema.CHANNEL.n));
        	logger.debug(channelName);
        }
	}
	
	// ===============================================
	// UIInit interface
	// ===============================================
	public void setupView() {
		
	}
	
	public void setOnClickListeners() {
		
	}
}
