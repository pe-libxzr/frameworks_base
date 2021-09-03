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

package moe.xzr.server;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import static android.os.Process.THREAD_PRIORITY_DEFAULT;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import com.android.server.ServiceThread;
import com.android.server.SystemService;

import java.lang.InterruptedException;
import java.lang.String;
import java.lang.Thread;
import java.util.NoSuchElementException;

import static moe.xzr.hardware.ChargeManager.OPTIMIZED_CHARGE_SERVICE;
import moe.xzr.hardware.IOptimizedCharge;
import vendor.libxzr.chgctrl.V1_0.IChargeControl;

public final class OptimizedChargeService extends SystemService {

    private static final String TAG = "OptimizedChargeService";

    private final Context mContext;
    private final ContentResolver mResolver;
    private ServiceThread mWorker;
    private Handler mHandler;
    private SettingsObserver mSettingsObserver;
    private IChargeControl mChargeControl;

    private float mLevel;
    private boolean mPlugged;
    private boolean mLastChargeEnabled;

    private int mCeiling;
    private int mFloor;
    private boolean mEnabled;

    private void updateAction() {
        Log.d(TAG, "pct: " + mLevel);
        Log.d(TAG, "plugged: " + mPlugged);
        Log.d(TAG, "enabled: " + mEnabled);
        Log.d(TAG, "ceiling: " + mCeiling);
        Log.d(TAG, "floor: " + mFloor);

        boolean chargeEnabled = getChargeEnabled();
        boolean failed = false;

        if (!mEnabled) {
            if (!chargeEnabled)
                failed = setChargeEnabled(true);
        } else {
            if (mLevel >= mCeiling) {
                mLastChargeEnabled = false;
                if (chargeEnabled)
                    failed = setChargeEnabled(false);
            } else if (mLevel < mFloor) {
                mLastChargeEnabled = true;
                if (!chargeEnabled)
                    failed = setChargeEnabled(true);
            } else if (mLevel >= mFloor && mLevel <= mCeiling &&
                    chargeEnabled != mLastChargeEnabled)
                failed = setChargeEnabled(mLastChargeEnabled);
        }

        if (failed)
            Log.e(TAG, "Failed when updating action");
    }

    private boolean getChargeEnabled() {
        try {
            return mChargeControl.getChargeEnabled();
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to get charge status");
        }
        return true;
    }

    private boolean setChargeEnabled(boolean enabled) {
        try {
            return mChargeControl.setChargeEnabled(enabled);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to set charge status");
        }
        return true;
    }

    private void onBatteryChanged(Intent batteryStatus) {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

        float newLevel = level * 100 / (float)scale;
        boolean newPlugged = status != 0;

        boolean shouldUpdate = false;
        if (newLevel != mLevel) {
            mLevel = newLevel;
            shouldUpdate = true;
        }
        if (newPlugged != mPlugged) {
            if (newPlugged) {
                mLastChargeEnabled = false;
                if (status == BatteryManager.BATTERY_PLUGGED_USB) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {}
                }
            }
            mPlugged = newPlugged;
            shouldUpdate = true;
        }

        if (shouldUpdate && mPlugged)
            updateAction();
    }

    private void updateParam(String name) {
        if (name == null) {
            _updateParam(Settings.System.OPTIMIZED_CHARGE_CEILING);
            _updateParam(Settings.System.OPTIMIZED_CHARGE_FLOOR);
            _updateParam(Settings.System.OPTIMIZED_CHARGE_ENABLED);
            updateAction();
            return;
        }
        _updateParam(name);
        updateAction();
    }

    private void _updateParam(String name) {
        if (name.equals(Settings.System.OPTIMIZED_CHARGE_CEILING))
            mCeiling = Settings.System.getInt(mResolver, name, 80);
        else if (name.equals(Settings.System.OPTIMIZED_CHARGE_FLOOR))
            mFloor = Settings.System.getInt(mResolver, name, 75);
        else if (name.equals(Settings.System.OPTIMIZED_CHARGE_ENABLED))
            mEnabled = Settings.System.getInt(mResolver, name, 0) == 1;
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            String name = uri.getLastPathSegment();
            updateParam(name);
        }
    }

    @Override
    public void onStart() {
        try {
            mChargeControl = IChargeControl.getService(true);
        } catch (NoSuchElementException | RemoteException e) {
            Log.e(TAG, "Unable to get ChargeManager service");
        }

        if (mChargeControl == null)
            return;

        mWorker = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
        mWorker.start();
        mHandler = new Handler(mWorker.getLooper());
        mSettingsObserver = new SettingsObserver(mHandler);

        publishBinderService(OPTIMIZED_CHARGE_SERVICE, new IOptimizedCharge.Stub() {

            @Override
            public boolean setCeilingLevel(int level) throws RemoteException {
                if (level < 0 || level > 100)
                    return true;
                return !Settings.System.putInt(mResolver, Settings.System.OPTIMIZED_CHARGE_CEILING, level);
            }

            @Override
            public boolean setFloorLevel(int level) throws RemoteException {
                if (level < 0 || level > 100)
                    return true;
                return !Settings.System.putInt(mResolver, Settings.System.OPTIMIZED_CHARGE_FLOOR, level);
            }

            @Override
            public boolean setEnabled(boolean enabled) throws RemoteException {
                return !Settings.System.putInt(mResolver, Settings.System.OPTIMIZED_CHARGE_ENABLED, enabled ? 1 : 0);
            }

            @Override
            public int getCeilingLevel() throws RemoteException {
                return mCeiling;
            }

            @Override
            public int getFloorLevel() throws RemoteException {
                return mFloor;
            }

            @Override
            public boolean isEnabled() throws RemoteException {
                return mEnabled;
            }

        });
    }

    @Override
    public void onBootPhase(int phase) {
        if (mChargeControl == null)
            return;
        if (phase != PHASE_BOOT_COMPLETED)
            return;

        updateParam(null);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBatteryChanged(intent);
            }
        }, filter, null, mHandler);

        mResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.OPTIMIZED_CHARGE_ENABLED),
                false, mSettingsObserver);

        mResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.OPTIMIZED_CHARGE_CEILING),
                false, mSettingsObserver);

        mResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.OPTIMIZED_CHARGE_FLOOR),
                false, mSettingsObserver);
    }

    public OptimizedChargeService(Context context) {
        super(context);
        mContext = context;
        mResolver = context.getContentResolver();
    }
}
