package com.dji.training.task.djitraining;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.dji.training.task.djitraining.utils.ToastUtils;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.keysdk.DJIKey;
import dji.keysdk.KeyManager;
import dji.keysdk.ProductKey;
import dji.keysdk.callback.KeyListener;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends AppCompatActivity {
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

    private TextView titleTextView;
    private TextView firmwareVersionTextView;
    private TextView openButton;
    private DJIKey firmwareKey;
    private KeyListener firmwareVersionUpdater;
    private boolean hasStartedFirmVersionListener = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        setContentView(R.layout.activity_main);
        titleTextView = findViewById(R.id.text_connection_status);
        firmwareVersionTextView = findViewById(R.id.text_model_available);
        openButton = findViewById(R.id.btn_open);
        openButton.setEnabled(false);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, MissionActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });
        TextView version = (TextView) findViewById(R.id.text_version);
        version.setText("SDK Version:" + DJISDKManager.getInstance().getSDKVersion());
        checkAndRequestPermissions();

    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        removeFirmwareVersionListener();
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectChange(DJIApplication.ConnectivityChangeEvent event) {
        refreshTitle();
        tryUpdateFirmwareVersionWithListener();
    }

    private void tryUpdateFirmwareVersionWithListener() {
        if (!hasStartedFirmVersionListener) {
            firmwareVersionUpdater = new KeyListener() {
                @Override
                public void onValueChange(final Object o, final Object o1) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateVersion();
                        }
                    });
                }
            };
            firmwareKey = ProductKey.create(ProductKey.FIRMWARE_PACKAGE_VERSION);
            if (KeyManager.getInstance() != null) {
                KeyManager.getInstance().addListener(firmwareKey, firmwareVersionUpdater );
            }
            hasStartedFirmVersionListener = true;
        }
        updateVersion();
    }

    private void updateVersion() {
        String version = null;
        BaseProduct product = DJIApplication.getProductInstance();

        if (product != null) {
            version = product.getFirmwarePackageVersion();
        }

        if (TextUtils.isEmpty(version) || !product.isConnected()) {
            firmwareVersionTextView.setText("Firmware version:N/A");
        } else {
            firmwareVersionTextView.setText("Firmware version:"+version);
        }
    }
    private void removeFirmwareVersionListener() {
        if (hasStartedFirmVersionListener) {
            if (KeyManager.getInstance() != null) {
                KeyManager.getInstance().removeListener(firmwareVersionUpdater);
            }
        }
        hasStartedFirmVersionListener = false;
    }

    private void refreshTitle() {
        BaseProduct product = DJIApplication.getProductInstance();
        boolean connected = false;
        if (product != null) {
            if (product.isConnected()) {
                titleTextView.setText(product.getModel().getDisplayName() +" Connected");
                connected = true;
            } else {
                if (product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) product;
                    if (aircraft.getRemoteController() != null ) {
                        titleTextView.setText("Only RC Connected");
                        connected = true;
                    }
                }
            }
        }
        if (!connected) {
            titleTextView.setText("Status: No Product Connected");
            openButton.setEnabled(false);
        } else {
            openButton.setEnabled(true);
        }
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
}
