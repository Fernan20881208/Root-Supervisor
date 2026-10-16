package com.rootsupervisor.child;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Legacy device administrator, activated only through Android's visible consent dialog.
 * Deliberately requests no device lock, wipe, camera, or password policies.
 */
public class SupervisorAdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "Administración familiar activada", Toast.LENGTH_LONG).show();
    }
    @Override public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "Administración familiar desactivada", Toast.LENGTH_LONG).show();
    }
}
