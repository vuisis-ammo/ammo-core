package edu.vu.isis.ammo.core.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IPrefKeys;

public class PreferenceProvider extends ContentProvider {
	
	// =================================
	// Constants
	// =================================
//	private static final String[] columnNames = {
//		INetPrefKeys.PREF_IP_ADDR,
//		INetPrefKeys.PREF_IP_PORT,
//		INetPrefKeys.PREF_SOCKET_TIMEOUT,
//		INetPrefKeys.PREF_TRANSMISSION_TIMEOUT,
//		INetPrefKeys.PREF_IS_JOURNAL,
//		INetPrefKeys.PREF_DEVICE_ID,
//		INetPrefKeys.PREF_OPERATOR_KEY,
//		INetPrefKeys.PREF_OPERATOR_ID,
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

	// Pre-populate preferences with default values.
	@Override
	public boolean onCreate() {
		Context context = getContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean("prefsCreated", false)) {
			Editor editor = prefs.edit();
			editor.putString(IPrefKeys.PREF_OPERATOR_ID, "foo");
			editor.putBoolean(INetPrefKeys.NET_IS_ACTIVE, false);
			editor.putBoolean(INetPrefKeys.NET_IS_AVAILABLE, false);
			editor.putString(INetPrefKeys.PREF_OPERATOR_KEY, "bar");
			editor.putBoolean("prefsCreated", true);
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
			// do nothing.
		}
		
		return 1;
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
	
	// =================================
	// Helpers
	// =================================
	/**
	 * Returns an array where default value and method name are interleaved.
	 * Methods are even indices and default values are odd indices.
	 */
	private String[] parseFredProtocolStringArray(String[]combinedArray) {
		String[] results = new String[combinedArray.length*2];
		int index = 0;
		for (String s : combinedArray) {
			String method = s.substring(0, s.indexOf(":"));
			String defVal = s.substring(s.indexOf(":")+1);
			results[index++] = method;
			results[index++] = defVal;
		}
		
		return results;
	}
	
	/**
	 * Get's the data type corresponding to each 
	 * @param combinedArray
	 * @return
	 */
	private String[] typesFromFredProtocolArray(String[] combinedArray) {
		String[] results = new String[combinedArray.length*2];
		int index = 0;
		for (String s : combinedArray) {
			String method = s.substring(0, s.indexOf(":"));
			String defVal = s.substring(s.indexOf(":")+1);
			results[index++] = method;
			results[index++] = defVal;
		}
		
		return results;
	}
	
	private String[] defValsFromFredProtocolArray(String[] combinedArray) {
		return null;
	}

}
