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


package edu.vu.isis.ammo.core.distributor;

import android.content.Context;
import android.os.RemoteException;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IAmmoRequest;

/**
 * Builds an AmmoRequest object for use internally inside AMMO Core.  The main difference
 * between this and the public Builder (AmmoRequest.Builder) that this extends is that
 * the public Builder also submits the request when makeRequest is called.  This class
 * doesn't submit the request, so that the AmmoRequest object can be passed into the
 * DistributorThread's distributeRequest() method.
 * 
 * @author jwilliams
 */
public class InternalRequestBuilder extends AmmoRequest.Builder {
    public InternalRequestBuilder(final Context context) {
        super(context);
    }

    @Override
    protected IAmmoRequest makeRequest(AmmoRequest request) throws RemoteException {
        return request;
    }
}
