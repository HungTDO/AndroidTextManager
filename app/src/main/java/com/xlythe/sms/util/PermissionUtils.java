package com.xlythe.sms.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

public class PermissionUtils {
    /**
     * Returns true if all given permissions are available
     * */
    public static boolean hasPermissions(Context context, String... permissions) {
        boolean ok = true;
        for (String permission : permissions) {
            ok = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
            if (!ok) break;
        }
        return ok;
    }
}
