/*
 * Copyright (C) 2021 LibXZR <i@xzr.moe>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package moe.xzr.hardware;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import moe.xzr.hardware.IOptimizedCharge;

public final class ChargeManager {
    private static final String TAG = "ChargeManager";
    private static final String UNSUPPORTED = "Unsupported";
    private static final String REMOTE_ERROR = "Remote error";

    public static final String OPTIMIZED_CHARGE_SERVICE = "optimizedcharge";

    private static ChargeManager sChargeManager;
    private IOptimizedCharge mOptimizedCharge;

    private ChargeManager() {
        IBinder b = ServiceManager.getService(OPTIMIZED_CHARGE_SERVICE);
        if (b != null) {
            mOptimizedCharge = IOptimizedCharge.Stub.asInterface(b);
        }
    }

    private static ChargeManager getInstance() {
        if (sChargeManager != null)
            return sChargeManager;
        sChargeManager = new ChargeManager();
        return sChargeManager;
    }

    /** @hide */
    public static ChargeManager getInstance(Context context) {
        return getInstance();
    }

    /**
     * Check whether charging related feature is supported
     * Return true when chgctrl hal exist
     * @hide
     */
    public boolean isSupported() {
        return mOptimizedCharge != null;
    }

    /**
     * Check whether Optimized Charge is enabled
     * Return true when enabled
     * Throw RuntimeException on failure
     * @hide
     */
    public boolean getOptimizedChargeEnabled() {
        if (mOptimizedCharge == null)
            throw new RuntimeException(UNSUPPORTED);

        try {
            return mOptimizedCharge.isEnabled();
        } catch (RemoteException e) {
            throw new RuntimeException(REMOTE_ERROR);
        }
    }

    /**
     * Set the state of Optimized Charge
     * Return true on failure
     * Throw RuntimeException when it is not supported
     * @hide
     */
    public boolean setOptimizedChargeEnabled(boolean enabled) {
        if (mOptimizedCharge == null)
            throw new RuntimeException(UNSUPPORTED);
        try {
            return mOptimizedCharge.setEnabled(enabled);
        } catch (RemoteException e) {
            return true;
        }
    }

    /**
     * Get the ceiling of Optimized Charge
     * Return int on success
     * Throw RuntimeException on failure
     * @hide
     */
    public int getOptimizedChargeCeiling() {
        if (mOptimizedCharge == null)
            throw new RuntimeException(UNSUPPORTED);

        try {
            return mOptimizedCharge.getCeilingLevel();
        } catch (RemoteException e) {
            throw new RuntimeException(REMOTE_ERROR);
        }
    }

    /**
     * Set the ceiling of Optimized Charge
     * Return true on failure
     * Throw RuntimeException when it is not supported
     *
     * When battery level is above the ceiling, the charging
     * would be suspended if Optimized Charge is enabled
     * @hide
     */
    public boolean setOptimizedChargeCeiling(int level) {
        if (mOptimizedCharge == null)
            throw new RuntimeException(UNSUPPORTED);
        try {
            return mOptimizedCharge.setCeilingLevel(level);
        } catch (RemoteException e) {
            return true;
        }
    }

    /**
     * Get the floor of Optimized Charge
     * Return int on success
     * Throw RuntimeException on failure
     * @hide
     */
    public int getOptimizedChargeFloor() {
        if (mOptimizedCharge == null)
            throw new RuntimeException(UNSUPPORTED);

        try {
            return mOptimizedCharge.getFloorLevel();
        } catch (RemoteException e) {
            throw new RuntimeException(REMOTE_ERROR);
        }
    }

    /**
     * Set the floor of Optimized Charge
     * Return true on failure
     * Throw RuntimeException when it is not supported
     *
     * When battery level is below the floor, the charging
     * would be restarted if Optimized Charge is enabled
     * @hide
     */
    public boolean setOptimizedChargeFloor(int level) {
        if (mOptimizedCharge == null)
            throw new RuntimeException(UNSUPPORTED);
        try {
            return mOptimizedCharge.setFloorLevel(level);
        } catch (RemoteException e) {
            return true;
        }
    }
}
