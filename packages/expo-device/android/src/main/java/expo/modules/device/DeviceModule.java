package expo.modules.device;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.app.KeyguardManager;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.content.res.Configuration;
import android.Manifest;

import org.unimodules.core.ExportedModule;
import org.unimodules.core.ModuleRegistry;
import org.unimodules.core.Promise;
import org.unimodules.core.interfaces.ActivityProvider;
import org.unimodules.core.interfaces.ExpoMethod;
import org.unimodules.core.interfaces.RegistryLifecycleListener;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceModule extends ExportedModule implements RegistryLifecycleListener, ActivityCompat.OnRequestPermissionsResultCallback {
  private static final String NAME = "ExpoDevice";
  private static final String TAG = DeviceModule.class.getSimpleName();
  private final int REQUEST_READ_PHONE_STATE = 1;

  private ModuleRegistry mModuleRegistry;
  private Context mContext;
  private WifiInfo wifiInfo;
  private ActivityProvider mActivityProvider;
  private Activity mActivity;
  private DeviceType mDeviceType;
  private String mPhoneNumber;

  public DeviceModule(Context context) {
    super(context);
    mContext = context;
  }

  public enum DeviceType {
    HANDSET("Handset"),
    TABLET("Tablet"),
    TV("Tv"),
    UNKNOWN("Unknown");

    private final String value;

    DeviceType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }


  private WifiInfo getWifiInfo() {
    WifiManager manager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    this.wifiInfo = manager.getConnectionInfo();
    return this.wifiInfo;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void onCreate(ModuleRegistry moduleRegistry) {
    mModuleRegistry = moduleRegistry;
    mActivityProvider = moduleRegistry.getModule(ActivityProvider.class);
    mActivity = mActivityProvider.getCurrentActivity();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions, int[] grantResults) {
    switch (requestCode) {
      case REQUEST_READ_PHONE_STATE: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            try{
              TelephonyManager telMgr = (TelephonyManager) mContext.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
              mPhoneNumber = telMgr.getLine1Number();
            }
            catch(SecurityException se){
              throw se;
            }
        } else {
          // permission denied, boo! Disable the
          // functionality that depends on this permission.
          mPhoneNumber = "No permission to read the phone state.";
        }
        return;
      }
    }
  }

  @Override
  public Map<String, Object> getConstants() {
    HashMap<String, Object> constants = new HashMap<>();

    constants.put("brand", Build.BRAND);
    constants.put("manufacturer", Build.MANUFACTURER);
    constants.put("model", Build.MODEL);
    constants.put("serialNumber", this.getSerial());
    String systeName = "";
    if(android.os.Build.VERSION.SDK_INT < 23){
      systeName = "Android";
    }
    else{
      systeName = Build.VERSION.BASE_OS;
      if(systeName.length() == 0){
        systeName = "Android";
      }
    }
    constants.put("systemName", systeName);
    constants.put("deviceId", Build.BOARD);
    constants.put("uniqueId", Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID));
    constants.put("supportedABIs", Build.SUPPORTED_ABIS);

    ActivityManager actMgr = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
    actMgr.getMemoryInfo(memInfo);
    constants.put("totalMemory", memInfo.totalMem);

    mDeviceType = getDeviceType(mContext);
    constants.put("deviceType", mDeviceType.getValue());
    constants.put("isTablet", mDeviceType.getValue().equals("Tablet"));

    return constants;
  }

  private String getCarrier() {
    try {
      TelephonyManager telMgr = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
      return telMgr.getNetworkOperatorName();
    } catch (NullPointerException e) {
      Log.e(TAG, e.getMessage());
    }
    return null;
  }

  private static DeviceType getDeviceType(Context context) {
    // Detect TVs via ui mode (Android TVs) or system features (Fire TV).
    if (context.getApplicationContext().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
      return DeviceType.TV;
    }

    UiModeManager uiManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    if (uiManager != null && uiManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
      return DeviceType.TV;
    }

    // Find the current window manager, if none is found we can't measure the device physical size.
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    if (windowManager == null) {
      return DeviceType.UNKNOWN;
    }

    // Get display metrics to see if we can differentiate handsets and tablets.
    // NOTE: for API level 16 the metrics will exclude window decor.
    DisplayMetrics metrics = new DisplayMetrics();
    windowManager.getDefaultDisplay().getMetrics(metrics);

    // Calculate physical size.
    double widthInches = metrics.widthPixels / (double) metrics.xdpi;
    double heightInches = metrics.heightPixels / (double) metrics.ydpi;
    double diagonalSizeInches = Math.sqrt(Math.pow(widthInches, 2) + Math.pow(heightInches, 2));

    if (diagonalSizeInches >= 3.0 && diagonalSizeInches <= 6.9) {
      // Devices in a sane range for phones are considered to be Handsets.
      return DeviceType.HANDSET;
    } else if (diagonalSizeInches > 6.9 && diagonalSizeInches <= 18.0) {
      // Devices larger than handset and in a sane range for tablets are tablets.
      return DeviceType.TABLET;
    } else {
      // Otherwise, we don't know what device type we're on/
      return DeviceType.UNKNOWN;
    }
  }

  private String getSerial() {
    if (android.os.Build.VERSION.SDK_INT < 26) {
      return Build.SERIAL;
    } else {
      try {
        int permissionCheck = ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);

        } else {
          return Build.getSerial();
        }
      } catch (SecurityException se) {
        Log.e(TAG, se.getMessage());
      }
      return null;
    }
  }

  @ExpoMethod
  public void getFreeDiskStorageAsync(Promise promise) {
    try {
      StatFs external = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
      long availableBlocks;
      long blockSize;

      availableBlocks = external.getAvailableBlocksLong();
      blockSize = external.getBlockSizeLong();

      BigInteger storage = BigInteger.valueOf(availableBlocks).multiply(BigInteger.valueOf(blockSize));
      String stringValue = storage.toString();
      promise.resolve(stringValue);
    } catch (NullPointerException e) {
      Log.e(TAG, e.getMessage());
      promise.reject(e);
    }
  }

  @ExpoMethod
  public void getIpAddressAsync(Promise promise) {
    try {
      Integer ipAddress = getWifiInfo().getIpAddress();
      // Convert little-endian to big-endianif needed
      if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
        ipAddress = Integer.reverseBytes(ipAddress);
      }
      byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
      String ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
      promise.resolve(ipAddressString);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      promise.reject(e);
    }
  }

  @ExpoMethod
  public void getMACAddressAsync(String interfaceName, Promise promise) {
    String permission = "android.permission.INTERNET";
    int res = mContext.checkCallingOrSelfPermission(permission);

    String macAddress = "";
    if (res == PackageManager.PERMISSION_GRANTED) {
      try {
        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface intf : interfaces) {
          if (interfaceName != null) {
            if (!intf.getName().equalsIgnoreCase(interfaceName)) continue;
          }
          byte[] mac = intf.getHardwareAddress();
          if (mac == null) {
            macAddress = "";
          }
          StringBuilder buf = new StringBuilder();
          for (byte aMac : mac) {
            buf.append(String.format("%02X:", aMac));
          }
          if (buf.length() > 0) {
            buf.deleteCharAt(buf.length() - 1);
          }
          macAddress = buf.toString();
          break;
        }
      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
        promise.reject(e);
      }
    }
    promise.resolve(macAddress);
  }

  @ExpoMethod
  public void isAirplaneModeEnabledAsync(Promise promise) {
    boolean isAirPlaneMode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    promise.resolve(isAirPlaneMode);
  }

  @ExpoMethod
  public void hasSystemFeatureAsync(String feature, Promise promise) {
    try {
      promise.resolve(mContext.getApplicationContext().getPackageManager().hasSystemFeature(feature));
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      promise.reject(e);
    }
  }

  @ExpoMethod
  public void isPinOrFingerprintSetAsync(Promise promise) {
    KeyguardManager keyguardManager = (KeyguardManager) mContext.getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE); //api 16+
    promise.resolve(keyguardManager.isKeyguardSecure());
  }

  @ExpoMethod
  public void getUserAgentAsync(Promise promise) {
    String userAgent = System.getProperty("http.agent");
    promise.resolve(userAgent);
  }

  @ExpoMethod
  public void getCarrierAsync(Promise promise) {
    try {
      promise.resolve(this.getCarrier());
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      promise.reject(e);
    }
  }

  @ExpoMethod
  public void getTotalDiskCapacityAsync(Promise promise) {
    try {
      StatFs root = new StatFs(Environment.getRootDirectory().getAbsolutePath());
      promise.resolve(BigInteger.valueOf(root.getBlockCountLong()).multiply(BigInteger.valueOf(root.getBlockSizeLong())).toString());
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      promise.reject(e);
    }
  }

  @ExpoMethod
  public void getPhoneNumberAsync(Promise promise) {
    try {
      int permissionCheck = ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE);

      if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);

      } else {
        TelephonyManager telMgr = (TelephonyManager) mContext.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneNumber = telMgr.getLine1Number();
      }
      promise.resolve(mPhoneNumber);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      promise.reject(e);
    }
  }

}
