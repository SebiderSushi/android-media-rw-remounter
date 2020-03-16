package me.sebi.rwremount;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
    private static final String PRIMARY_PATH = "/storage/emulated/0";
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
     * mask=7 was chosen to mimic Androids own behavior on /storage/emulated
     * as observed on LineageOS 15.1, 16.0 and 17.1
     * <p>
     * The path is altered because Android constructs the environment separately for each app.
     * This is based upon the excellent info from this great answer at stackexchange:
     * https://android.stackexchange.com/questions/217741/how-to-bind-mount-a-folder-inside-sdcard-with-correct-permissions/217936#217936
     *
     * @param unixPath The directory that should be given R/W access
     * @return The complete and escaped command line to be executed
     */
    private String buildCommandLine(String unixPath) {
        String runtimePath = unixPath.replaceFirst("/storage/", "/mnt/runtime/write/");
        return "mount -o remount,mask=7 \"" + runtimePath + "\"";
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
        if (unixPath.contains(PRIMARY_PATH)) {
            Log.d(LOG_TAG, "Ignoring primary storage - " + unixPath);
            return;
        }
        // Ignore a path if it looks unsafe
        if (!unixPath.matches(ALLOWED_PATH_REGEX)) {
            Log.d(LOG_TAG, "Ignoring due to security considerations - " + unixPath);
            return;
        }
        try {
            String commandLine = buildCommandLine(unixPath);
            Log.d(LOG_TAG, "Executing as root - " + commandLine);
            Process mountProcess = Runtime.getRuntime().exec("su");
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
        } catch (IOException e) {
            Log.d(LOG_TAG, "ERROR: IOException during execution.\n" + e.getMessage());
        } catch (InterruptedException e) {
            Log.d(LOG_TAG, "ERROR: Process interrupted during execution.\n" + e.getMessage());
        }
    }
}