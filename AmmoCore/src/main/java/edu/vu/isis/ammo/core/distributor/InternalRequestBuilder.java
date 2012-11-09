package edu.vu.isis.ammo.core.distributor;

import android.os.RemoteException;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IAmmoRequest;

/**
 * Builds an AmmoRequest object for use internally inside AMMO Core.  The main difference
 * between this and the public Builder (AmmoRequest.Builder) that this extends is that
 * the public Builder also submits the request when makeRequest is called.  This class
 * doesn't submit the request, so that the AmmoRequest object can be passed into the
 * DistributorThread's distributeRequest() method.
 * @author jwilliams
 *
 */
public class InternalRequestBuilder extends AmmoRequest.Builder {
    public InternalRequestBuilder() {
        super();
    }

    @Override
    protected IAmmoRequest makeRequest(AmmoRequest request) throws RemoteException {
        // TODO Auto-generated method stub
        return request;
    }
}
