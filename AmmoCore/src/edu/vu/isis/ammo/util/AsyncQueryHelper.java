package edu.vu.isis.ammo.util;

import android.net.Uri;

public class AsyncQueryHelper {

    /**
     * the intent here is to run some method which makes use of
     * the result Uri supplied by the recently completed insert.
     *
     */
    static abstract public class InsertResultHandler {
        protected Uri resultTuple;
        
        public void result(Uri val) {
            this.resultTuple = val;
        }
        public InsertResultHandler() {
            
        }
        abstract public void run();
        
    }
}
