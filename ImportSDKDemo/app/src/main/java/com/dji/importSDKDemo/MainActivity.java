package com.dji.importSDKDemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };

    private static final int REQUEST_PERMISSION_CODE = 12345;
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private Button loginbtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        setContentView(R.layout.activity_main);
        checkAndRequestPermissions();

        loginbtn = (Button) findViewById(R.id.loginBtn);
        loginbtn.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectChange(DJIApplication.ConnectivityChangeEvent event) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            Toast.makeText(getApplicationContext(), "Missing permissions!!!", Toast.LENGTH_LONG).show();
        }
    }
    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    ToastUtils.setResultToToast(MainActivity.this.getString(R.string.sdk_registration_doing_message));
                    DJISDKManager.getInstance().registerApp(MainActivity.this.getApplicationContext(), sdkManagerCallback);
                }
            });
        }
    }

    private void loginAccount() {

        UserAccountManager.getInstance()
                .logIntoDJIUserAccount(this, new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        ToastUtils.setResultToToast("Login Error:" + error.getDescription());
                    }
                });
    }

    private static DJISDKManager.SDKManagerCallback sdkManagerCallback = new DJISDKManager.SDKManagerCallback() {
        @Override
        public void onRegister(DJIError djiError) {
            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                Log.d("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                DJISDKManager.getInstance().startConnectionToProduct();
                ToastUtils.setResultToToast(DJIApplication.getInstance().getString(R.string.sdk_registration_success_message));
            } else {
                ToastUtils.setResultToToast(DJIApplication.getInstance().getString(R.string.sdk_registration_message));
            }
            Log.v(TAG, djiError.getDescription());
        }
        @Override
        public void onProductDisconnect() {
            Log.d(TAG, "onProductDisconnect");
            DJIApplication.notifyStatusChange(DJIApplication.ConnectivityChangeEvent.ProductConnected);
        }
        @Override
        public void onProductConnect(BaseProduct baseProduct) {
            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
            DJIApplication.notifyStatusChange(DJIApplication.ConnectivityChangeEvent.ProductDisconnected);
        }
        @Override
        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                      BaseComponent newComponent) {
            if (newComponent != null) {
                if (componentKey.equals(BaseProduct.ComponentKey.CAMERA)) {
                    newComponent.setComponentListener(new BaseComponent.ComponentListener() {
                        @Override
                        public void onConnectivityChange(boolean connected) {
                            if (connected) {
                                DJIApplication.notifyStatusChange(DJIApplication.ConnectivityChangeEvent.CameraConnect);
                            } else {
                                DJIApplication.notifyStatusChange(DJIApplication.ConnectivityChangeEvent.CameraDisconnect);
                            }
                        }
                    });
                    if (oldComponent == null && newComponent != null) {
                        DJIApplication.notifyStatusChange(DJIApplication.ConnectivityChangeEvent.CameraConnect);
                    }
                }
            }

            Log.d(TAG,
                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                            componentKey,
                            oldComponent,
                            newComponent));

        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.loginBtn:
                loginAccount();
                break;
            default:
                break;
        }
    }
}
