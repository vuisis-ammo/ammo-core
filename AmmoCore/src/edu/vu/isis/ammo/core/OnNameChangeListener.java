package edu.vu.isis.ammo.core;

import android.view.View;

/**
 * Interface used by the Gateway class to notify interested parties certain
 * parts of the shared preferences have changed.
 * @author demetri
 *
 */
public interface OnNameChangeListener {
    public boolean onNameChange(View view, String name);
    public boolean onFormalChange(View view, String formal);
}
