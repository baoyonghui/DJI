package com.dji.importSDKDemo;

import android.app.Application;
import android.content.Context;

import org.greenrobot.eventbus.EventBus;

import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.products.HandHeld;
import dji.sdk.sdkmanager.DJISDKManager;

public class DJIApplication extends Application {
    public static final String TAG = DJIApplication.class.getName();

    private static Application app = null;

    public static void notifyStatusChange(ConnectivityChangeEvent event) {
        EventBus.getDefault().post(event);
    }

    public static synchronized BaseProduct getProductInstance() {
        return DJISDKManager.getInstance().getProduct();
    }

    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof Aircraft;
    }

    public static boolean isHandHeldConnected() {
        return getProductInstance() != null && getProductInstance() instanceof HandHeld;
    }

    public static synchronized Aircraft getAircraftInstance() {
        if (!isAircraftConnected()) {
            return null;
        }
        return (Aircraft) getProductInstance();
    }

    public static synchronized HandHeld getHandHeldInstance() {
        if (!isHandHeldConnected()) {
            return null;
        }
        return (HandHeld) getProductInstance();
    }

    public static Application getInstance() {
        return DJIApplication.app;
    }


    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        com.secneo.sdk.Helper.install(this);
        app = this;
    }

    public enum  ConnectivityChangeEvent {
        ProductConnected,
        ProductDisconnected,
        CameraConnect,
        CameraDisconnect,
    }
}