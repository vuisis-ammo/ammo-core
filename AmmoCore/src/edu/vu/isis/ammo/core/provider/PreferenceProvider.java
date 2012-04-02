/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.core.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;

import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

public class PreferenceProvider extends ContentProvider {
	
	// =================================
	// Constants
	// =================================
//	private static final String[] columnNames = {
//		INetPrefKeys.CORE_IP_ADDR,
//		INetPrefKeys.CORE_IP_PORT,
//		INetPrefKeys.CORE_SOCKET_TIMEOUT,
//		INetPrefKeys.PREF_TRANSMISSION_TIMEOUT,
//		INetPrefKeys.CORE_IS_JOURNALED,
//		INetPrefKeys.CORE_DEVICE_ID,
//		INetPrefKeys.CORE_OPERATOR_KEY,
//		INetPrefKeys.CORE_OPERATOR_ID,
//		
//
//	};
	
	
	// =================================
	// Fields
	// =================================
	Logger logger = LoggerFactory.getLogger(PreferenceProvider.class);
	
	
	// =================================
	// Content Provider Overrides
	// =================================
	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		logger.warn("Attempted to delete item from PreferenceProvider... returning.");
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		logger.warn("Attempted to insert item into PreferenceProvider... returning.");
		return null;
	}

	// Pre-populate preferences with default values if this is the first time
	// the content provider has been created.
	
	@Override
	public boolean onCreate() {
		Context context = getContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean("prefsCreated", false)) {
			Editor editor = prefs.edit();
			String deviceId = UniqueIdentifiers.device(context);
			editor.putString(INetPrefKeys.CORE_DEVICE_ID, deviceId);
			editor.putString(INetPrefKeys.CORE_OPERATOR_ID, INetPrefKeys.DEFAULT_CORE_OPERATOR_ID);
			editor.putBoolean(INetDerivedKeys.NET_IS_ACTIVE, false);
			editor.putBoolean(INetDerivedKeys.NET_IS_AVAILABLE, false);
			editor.putBoolean(INetDerivedKeys.ARE_PREFS_CREATED, true);
			editor.commit();	
		}
		
		return true;
	}

	/**
	 * Matrix cursor columns correspond to keys requested for query. Keys are 
	 * stored in projection.
	 * The selection is the data type of the preference we want.
	 * The selectionArgs corresponds to the default value for the preference.
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		MatrixCursor mc = new MatrixCursor(projection);
		String key = projection[0];
		String defVal = selectionArgs[0];
		Object[] values = null;
		if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_STRING)) {
			values = new Object[] {getString(key, defVal, prefs)};
		} else if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_BOOLEAN)) {
			values = new Object[] {getBoolean(key, defVal, prefs)};
		} else if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_FLOAT)) {
			values = new Object[] {getFloat(key, defVal, prefs)};
		} else if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_INT)) {
			values = new Object[] {getInt(key, defVal, prefs)};
		} else if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_LONG)) {
			values = new Object[] {getLong(key, defVal, prefs)};
		} else {
			// do nothing.
		}
		
		mc.addRow(values);
		return mc;
	}

	/**
	 * ContentValues has preference key and value to set. 
	 * Selection gives type of data.
	 * SelectionArgs has preference key as well. We duplicate this for indexing purposes.
	 * For now, we only support a single insertion at a time.
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
		String key = selectionArgs[0];
		boolean shouldBroadcast = true;
		int updateCount = 1;
		if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_STRING)) {
			this.putString(key, values.getAsString(key), editor);
		} else if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_BOOLEAN)) {
			this.putBoolean(key, values.getAsBoolean(key), editor);
		} else if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_FLOAT)) {
			this.putFloat(key, values.getAsFloat(key), editor);
		} else if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_INT)) {
			this.putInt(key, values.getAsInteger(key), editor);			
		} else if (selection.equals(PreferenceSchema.AMMO_PREF_TYPE_LONG)) {
			this.putLong(key, values.getAsLong(key), editor);
		} else {
			shouldBroadcast = false;
			updateCount = 0;
		}
		
		if (shouldBroadcast) {
			Intent i = new Intent(PreferenceSchema.AMMO_PREF_CHANGED_ACTION);
			i.putExtra(PreferenceSchema.AMMO_INTENT_KEY_PREF_CHANGED_KEY, key);
			getContext().sendBroadcast(i);	
		}
		
		return updateCount;
	}

	// =================================
	// Preference Setters/Getters
	// =================================
	private String getString(String key, String defVal, SharedPreferences prefs) {
		return prefs.getString(key, defVal);
	}
	
	private String getBoolean(String key, String defVal, SharedPreferences prefs) {
		return String.valueOf(prefs.getBoolean(key, Boolean.valueOf(defVal).booleanValue()));
	}
	
	private long getLong(String key, String defVal, SharedPreferences prefs) {
		return prefs.getLong(key, Long.valueOf(defVal));
	}
	
	private float getFloat(String key, String defVal, SharedPreferences prefs) {
		return prefs.getFloat(key, Float.valueOf(defVal));
	}
	
	private int getInt(String key, String defVal, SharedPreferences prefs) {
		return prefs.getInt(key, Integer.valueOf(defVal));
	}
	
	private void putString(String key, String val, Editor editor) {
		editor.putString(key, val).commit();
	}
	
	private void putBoolean(String key, boolean val, Editor editor) {
		editor.putBoolean(key, val).commit();
	}
	
	private void putInt(String key, int val, Editor editor) {
		editor.putInt(key, val).commit();
	}
	
	private void putLong(String key, long val, Editor editor) {
		editor.putLong(key, val).commit();
	}
	
	private void putFloat(String key, float val, Editor editor) {
		editor.putFloat(key, val).commit();
	}
	
}
