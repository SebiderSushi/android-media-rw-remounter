package com.gitlab.giwiniswut.rwremount;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class MainActivity extends Activity {
    private static final String LOG_TAG = "RWRemount";
    private boolean mRootGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onHideAppIconButtonClick(View button) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.button_hide_app_icon);
        b.setMessage(R.string.explanation_hide_app_icon);
        b.setNegativeButton(android.R.string.cancel, null);
        b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                getPackageManager().setComponentEnabledSetting(
                        new ComponentName(getApplicationContext(), MainActivity.class),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                finish();
            }
        });
        b.show();
    }

    public void onRequestRootButtonClick(View button) {
        mRootGranted = requestRoot();

        TextView statusView = findViewById(R.id.textView_rootStatus);
        if (mRootGranted) {
            statusView.setText(R.string.root_status_granted);
            statusView.setBackgroundColor(0xFF00FF00);
        } else {
            statusView.setText(R.string.root_status_denied);
            statusView.setBackgroundColor(0xFFFF0000);
        }
    }

    private boolean requestRoot() {
        boolean success = false;
        try {
            Log.d(LOG_TAG, "Requesting root access...");
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream terminal = new DataOutputStream(p.getOutputStream());
            terminal.writeBytes("exit\n");
            terminal.flush();
            terminal.close();
            p.waitFor();
            int suExitValue = p.exitValue();
            success = (suExitValue == 0);
            if (success) {
                Log.d(LOG_TAG, "Root access was granted!");
            } else {
                // Grab output of stderr and print to log
                Scanner scanner = new Scanner(p.getErrorStream()).useDelimiter("\\A");
                String suError = scanner.hasNext() ? "\n" + scanner.next() : "";
                scanner.close();
                Log.d(LOG_TAG, "ERROR: root request exited with code " + suExitValue + "\n" +
                        suError);
            }
            p.destroy();
        } catch (IOException e) {
            Log.d(LOG_TAG, "ERROR: IOException during execution.\n" + e.getMessage());
        } catch (InterruptedException e) {
            Log.d(LOG_TAG, "ERROR: Process interrupted during execution.\n" + e.getMessage());
        }
        return success;
    }
}
