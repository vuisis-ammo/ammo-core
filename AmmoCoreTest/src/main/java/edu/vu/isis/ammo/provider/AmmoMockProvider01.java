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


package edu.vu.isis.ammo.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;


public class AmmoMockProvider01 extends AmmoMockProviderBase {

  protected class AmmoMockDatabaseHelper extends AmmoMockProviderBase.AmmoMockDatabaseHelper {

    protected AmmoMockDatabaseHelper(Context context) {
      super(context, (String) null, (SQLiteDatabase.CursorFactory) null, AmmoMockSchema01.DATABASE_VERSION);
    }

  }

  @Override
  public boolean createDatabaseHelper() {
    this.openHelper = new AmmoMockProviderBase.AmmoMockDatabaseHelper(getContext(), null, null, AmmoMockSchema01.DATABASE_VERSION) {

    };
    return true;
  }
 
  
  protected AmmoMockProvider01( Context context ) {
    super(context);
  }
  
  public static AmmoMockProvider01 getInstance(Context context) {
     AmmoMockProvider01 f = new AmmoMockProvider01(context);
     f.onCreate();
     return f;
  }
  
  public SQLiteDatabase getDatabase() {
    return this.openHelper.getWritableDatabase();
  }
  
  public void release() {
      this.openHelper.close();
  }
}
