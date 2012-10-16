
package edu.vu.isis.ammo.util;

import android.net.Uri;

public interface AsyncQueryHelper {

    /**
     * the intent here is to run some method which makes use of the result Uri
     * supplied by the recently completed insert.
     */
    public interface InsertResultHandler {
        void run(Uri val);
    }
}
