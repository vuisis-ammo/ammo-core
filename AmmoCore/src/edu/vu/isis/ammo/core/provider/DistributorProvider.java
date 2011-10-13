package edu.vu.isis.ammo.core.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;



public class DistributorProvider extends ContentProvider {
	// =================================
	// Constants
	// =================================
	
	private static final UriMatcher uriMatcher;
	private static final UriMatcher garbageMatcher;
	   static {
		   uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		   garbageMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		   for (final Tables table : Tables.values()) {
			   uriMatcher.addURI(DistributorSchema.AUTHORITY, table.n, table.ordinal());
			   garbageMatcher.addURI(DistributorSchema.AUTHORITY, table.n+"/garbage", table.ordinal());
		   }
	   }
	
	// =================================
	// Fields
	// =================================
	Logger logger = LoggerFactory.getLogger(PreferenceProvider.class);

	// =================================
	// setup
	// =================================

	private DistributorDataStore dds;
	private static final Intent AMMO_SERVICE;
	static {
		AMMO_SERVICE = new Intent();
		final ComponentName serviceComponent = 
				new ComponentName(AmmoService.class.getPackage().getName(), 
						AmmoService.class.getCanonicalName());
		AMMO_SERVICE.setComponent(serviceComponent);
	}
	
	@Override
	public boolean onCreate() {
		this.dds = null;
		final ServiceConnection conn = new ServiceConnection() {
            final DistributorProvider parent = DistributorProvider.this;
            
			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				
				 final AmmoService.DistributorServiceAidl proxy = (AmmoService.DistributorServiceAidl) binder;
		         final AmmoService service = proxy.getService();

		         parent.dds = service.store();
			}
			@Override
			public void onServiceDisconnected(ComponentName name) {
				return;
			}
		};
		this.getContext().bindService(AMMO_SERVICE, conn, Context.BIND_AUTO_CREATE);
		return true;
	}


	@Override
	public String getType(Uri uri) {
		return null;
	}


	// =================================
	// Content Provider Overrides
	// =================================

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		if (this.dds == null) return -1;
		logger.trace("delete on distributor provider {} {}", uri, selection);
		
		switch(Tables.values()[uriMatcher.match(uri)]) {
		case POSTAL:
			return dds.deletePostal(selection, selectionArgs);
		case PUBLISH:
			return dds.deletePublish(selection, selectionArgs);
		case RETRIEVAL:
			return dds.deleteRetrieval(selection, selectionArgs);
		case SUBSCRIBE:
			return dds.deleteSubscribe(selection, selectionArgs);
		case DISPOSAL:
		case CHANNEL:
			return -1;
		}
		
		switch(Tables.values()[garbageMatcher.match(uri)]) {
		case POSTAL:
			return dds.deletePostalGarbage();
		case PUBLISH:
			return dds.deletePublishGarbage();
		case RETRIEVAL:
			return dds.deleteRetrievalGarbage();
		case SUBSCRIBE:
			return dds.deleteSubscribeGarbage();
		case DISPOSAL:
		case CHANNEL:
			return -1;
		}
		return -1;	
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		logger.warn("no inserts allowed on distributor provider {} {}", uri, values);
		return null;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		if (this.dds == null) return null;
		
		logger.trace("query on distributor provider {} {}", uri, selection);
		final Cursor c;
		switch(Tables.values()[uriMatcher.match(uri)]) {
		case POSTAL:
			c = dds.queryPostal(projection, selection, selectionArgs, sortOrder);
			break;
		case PUBLISH:
			c = dds.queryPublish(projection, selection, selectionArgs, sortOrder);
			break;
		case RETRIEVAL:
			c = dds.queryRetrieval(projection, selection, selectionArgs, sortOrder);
			break;
		case SUBSCRIBE:
			c = dds.querySubscribe(projection, selection, selectionArgs, sortOrder);
			break;
		case DISPOSAL:
			c = dds.queryDisposal(projection, selection, selectionArgs, sortOrder);
			break;
		case CHANNEL:
			c = dds.queryChannel(projection, selection, selectionArgs, sortOrder);
			break;
		default:
			// If we get here, it's a special uri and should be matched differently.
			
			c = null;
		}
		
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		logger.warn("no updates allowed on distributor provider {} {}", uri, values);
		return 0;
	}
	
}
