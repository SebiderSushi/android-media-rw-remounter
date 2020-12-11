package com.gitlab.giwiniswut.rwremount;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Scanner;

/**
 * BroadcastReceiver listening for ACTION_MEDIA_MOUNTED intents from the system.
 * When called, the receiver will try to remount the given mount point with mask=7
 * to enable write access for regular apps.
 * <p>
 * The logic will try to ignore the normal internal path to only handle external storage.
 * <p>
 * Due to security considerations, everything not under /storage and everything that does not
 * look like a normal filesystem UID will be ignored to prevent any malicious strings from ending
 * up in the root shell where the remount command is executed. See {@link #ALLOWED_PATH_REGEX}
 */
public class MountReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "RWRemount";
    /**
     * The path of the internal storage which should be ignored
     */
    private static final String PRIMARY_PATH = "/storage/emulated/";
    /**
     * Enforce strict rules for path input
     * This is currently the best way i came up with that prevents any malicious input
     * from escaping the root shell where the path will be passed to the mount command
     * or possibly giving write access to any other location by blindly remounting it.
     * TODO Ensure input can safely be passed into root shell without crippling capabilities
     */
    private static final String ALLOWED_PATH_REGEX = "/storage/[A-Za-z0-9_-]+";

    /**
     * Safely combine the mount command and the path
     * <p>
     * mask=0 was chosen because the original mask removes just write access and allows
     * other to read and execute anyway. In contrast to the mask=7 option that android
     * uses when mounting /data/media on /storage/runtime/write/emulated, mask=0 allows the
     * SD Card to be read by secondary Device users as well.
     * Secondary device users are managed via the "Multiple users" section in the System Settings.
     * <p>
     * The path is altered because Android constructs the environment separately for each app.
     * This is based upon the excellent info from this great answer at stackexchange:
     * https://android.stackexchange.com/questions/217741/how-to-bind-mount-a-folder-inside-sdcard-with-correct-permissions/217936#217936
     *
     * @param unixPath The directory that should be given R/W access
     * @return The complete and escaped command line to be executed
     */
    private static String buildCommandLine(String unixPath) {
        String runtimePath = unixPath.replaceFirst("/storage/", "/mnt/runtime/write/");
        return String.format("mount -o remount,mask=0 \"%s\"", runtimePath);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())) {
            Log.d(LOG_TAG, "ERROR: Called by something other than the system!" +
                    "This should never happen and could be malicious.");
            return;
        }
        String path = intent.getDataString();
        if (path == null) {
            Log.d(LOG_TAG, "ERROR: Path is null.");
            return;
        }
        // Remove leading file:// from path URI, that's 7 characters
        String unixPath = path.substring(7);
        // Don't touch the primary storage since it normally is fine
        if (unixPath.startsWith(PRIMARY_PATH)) {
            Log.d(LOG_TAG, "Ignoring primary storage - " + unixPath);
            return;
        }
        // Ignore a path if it looks unsafe
        if (!unixPath.matches(ALLOWED_PATH_REGEX)) {
            Log.d(LOG_TAG, "Ignoring due to security considerations - " + unixPath);
            return;
        }
        boolean useMountmaster = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getBoolean(MainActivity.KEY_MOUNTMASTER, false);
        String su = useMountmaster ? "su -mm" : "su";
        //TODO improve the execution as to what needs to be whithin the try block, what needs to be closed and what needs to dectroyed and when and where
        try {
            String commandLine = buildCommandLine(unixPath);
            Log.d(LOG_TAG, "Executing as root (" + su + ") - " + commandLine);
            Process mountProcess = Runtime.getRuntime().exec(su);
            DataOutputStream terminal = new DataOutputStream(mountProcess.getOutputStream());
            terminal.writeBytes(commandLine + "\nexit\n");
            terminal.flush();
            terminal.close();
            mountProcess.waitFor();
            int mountExitValue = mountProcess.exitValue();
            if (mountExitValue == 0) {
                Log.d(LOG_TAG, "Remounting R/W succeded - " + unixPath);
            } else {
                // Grab output of stderr and print to log
                Scanner scanner = new Scanner(mountProcess.getErrorStream()).useDelimiter("\\A");
                String mountError = scanner.hasNext() ? "\n" + scanner.next() : "";
                scanner.close();
                Log.d(LOG_TAG, "ERROR: mount exited with code " + mountExitValue + " - " +
                        unixPath + mountError);
            }
            mountProcess.destroy();
        } catch (IOException e) {
            Log.d(LOG_TAG, "ERROR: IOException during execution.\n" + e.getMessage());
        } catch (InterruptedException e) {
            Log.d(LOG_TAG, "ERROR: Process interrupted during execution.\n" + e.getMessage());
        }
    }
}
