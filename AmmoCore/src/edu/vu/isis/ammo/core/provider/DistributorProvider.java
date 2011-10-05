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
	   static {
		   uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		   for (Tables table : Tables.values()) {
			   uriMatcher.addURI(DistributorSchema.AUTHORITY, table.n, table.ordinal());
		   }
		   
		   // Special Uri's for querying 
		   // uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.DISPOSAL.n+"/group", code)
	   }
	
	// =================================
	// Fields
	// =================================
	Logger logger = LoggerFactory.getLogger(PreferenceProvider.class);

	// =================================
	// Content Provider Overrides
	// =================================
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		return null;
	}

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
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		if (this.dds == null) return null;
		
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
		return 0;
	}
}
