/*
 * 	Copyright (c) 2017. Token Browser, Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tokenbrowser.view;


import android.content.ComponentCallbacks2;
import android.content.IntentFilter;
import android.os.Build;
import android.support.multidex.MultiDexApplication;

import com.tokenbrowser.manager.TokenManager;
import com.tokenbrowser.service.NetworkChangeReceiver;
import com.tokenbrowser.util.LogUtil;

import io.realm.Realm;
import rx.subjects.BehaviorSubject;

public final class BaseApplication extends MultiDexApplication {

    private static BaseApplication instance;
    public static BaseApplication get() { return instance; }
    private final BehaviorSubject<Boolean> isConnectedSubject = BehaviorSubject.create();

    private TokenManager tokenManager;
    private boolean inBackground = false;

    public final TokenManager getTokenManager() {
        return this.tokenManager;
    }

    public final Realm getRealm() {
        if (Thread.currentThread().getId() == 1) {
            LogUtil.e(getClass(), "DB call done on Main Thread. Move this to a background thread.");
        }
        return this.tokenManager.getRealm().toBlocking().value();
    }

    @Override
    public final void onCreate() {
        super.onCreate();
        instance = this;
        init();
    }

    private void init() {
        initTokenManager();
        initConnectivityMonitor();
    }

    private void initConnectivityMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final IntentFilter connectivityIntent = new IntentFilter();
            connectivityIntent.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
            this.registerReceiver(new NetworkChangeReceiver(), connectivityIntent);
        }
        isConnectedSubject().onNext(NetworkChangeReceiver.getCurrentConnectivityStatus(this));
    }

    private void initTokenManager() {
        this.tokenManager = new TokenManager();
    }


    public void applicationResumed() {
        if (this.inBackground) {
            this.inBackground = false;
            this.tokenManager.getSofaMessageManager().resumeMessageReceiving();
        }
    }

    @Override
    public void onTrimMemory(final int level) {
        // This is a method for detecting the application going into the background:
        // http://stackoverflow.com/a/19920353
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            this.inBackground = true;
            this.tokenManager.getSofaMessageManager().disconnect();
        }
        super.onTrimMemory(level);
    }

    public BehaviorSubject<Boolean> isConnectedSubject() {
        return isConnectedSubject;
    }

    public boolean isConnected() {
        return isConnectedSubject.getValue();
    }

    public boolean isInBackground() { return this.inBackground; }
}