# [ROOT] Android SD-Card R/W Remounter
Remember how google decided in 2013 that android needs a new and fancy framework for storage access?  
Frustrated of how they took away external storage write access because of this, as if to test drive the change on android devices with an SD Card (i.e. not Google devices) before enforcing it with internal storages as well for apps targeting and running on android 10 and above?  
Just want back write access to your sd card?  

Well now there's an app for that!

### Requirements
- A device with an SD Card slot
- Root access, since the app remounts some stuff
- Android 8.0 or later (but if you are running android 6.0.1, 7.0 or 7.1, it shouldn't hurt to just give it a shot)

The approach currently used by this app definitely only applies to devices running android 6 or later.
It has successfully been tested with devices running android 8.1, 9 and 10 and unsucsessfully on devices running android 7.1, 6.0.1 and 5.1.
For further information consult the [wiki](https://github.com/SebiderSushi/android-media-rw-remounter/wiki).


### How to use it
- Install
- Launch once
- Grant permanent root access
- Insert or remount some storage
- Profit!

Notes:
The app must be launched at least once or else the system won't ever run it. This is a security mechanism of the Android platform to protect users from apps they've never launched and maybe never wanted to use or install.
This restriction can be removed by installing the app as a privileged app, i.e. by writing the apk file to `/system/priv-app`.  
Upon the first mount with this app installed, i.e. when you actively need to grant root access, it is possible that the app will do nothing.
This is because the app will currently be terminated if it has to wait more than a few seconds for root access. Once root access has already been granted and the app is immediately given root access every time it needs it, everything should work as expected.

### Limitations
At the moment, i did not find any way to enable write access for secondary users as well. However, they still get read access to the SD Card.

### Issue reporting and support
If you run into major issues while using the app or if you are an expert and found any problems with the code, don't hesitate to open an issue about it. Try to provide as much details as possible. Describe what you did in exact steps, what you expected and what happened instead.  
If you open an issue because the app doesn't work on your device even though you feel it should, provide at least your android version, device brand and model and filesystem of the storage you want to mount.  
If you can, provide the output of the `mount` command. It can be run inside virtually any android terminal emulator application or, if you are familier with it, via `adb shell mount` when connected to a desktop pc in debugging mode.  
It is okay to redact the output as much as you feel appropriate, but please make sure that the mount options of your external storages are included and that it is obvious that where they are.

Apart from these remarks, keep in mind that this is currently a very small and spontaneous project released in the hopes of being useful. While i will take my best care to design this application to be stable and never harmful, i cannot guarantee any kind of safety. Use of this application happens on your own risk and discretion, especially since i can only test the application on the few devices that i own. If in doubt, consider testing the functionality of the app on your device with an unimportant sd card first to eliminate any possiblity of data loss.  
That being said, there's currently nothing destructive going on and misbehavior should be limited to failing to enable write access. Anything this app might currently do wrong if running on a wrong device should entirely be revertable by uninstalling it and remounting your storage device.

This is not a big project and i do not plan to put a lot of effort into expanding or supporting it too far of my own use case. Thus, i will mostly try to keep it minimal and be satisfied with plain functionality, but any such investments are very welcome. Feel free to report issues, to give suggestions and to open pull requests while not expecting too much activity from me apart from administrating the repository.

### Benefits of this approach
You may know other attempts for enabling write access on external storages. Some of these use an approach that changes your system configuration so that every new app is added to the group `media_rw`. As a result, that app can get write access to the path of your external sd card unter `/mnt/media_rw`.
However, as android mounts external storages without any permission altering mount options in `/mnt/media_rw`, you will be confronted with file ownership and all other permissions from the actual filesystem of your storage device.
This is no problem if you are using some FAT filesystem on your SD Card, but if you are using any modern filesystem because your ROM developer had the mercy to allow your Android device to handle external storages formatted with f2fs or, god help us, ext4, then you will quickly be confronted with the problem that every newly created file will be owned by the user id of the app that created the file. Since there is neiter write or read permission given for others, only the app that wrote these files will be able to read them in the future until you fix the permissions manually.
- Supports permission aware file systems like ext4 or f2fs
- Makes the canonical and usual path under `/storage` writable instead of adding another location.
- All Apps will be able to list storages, even ones with an integrated file manager.  
Such apps were unable to list the directory `/mnt/media_rw` whenever i've used them. As such, it is impossible to browse anything under `/mnt/media_rw` without entering the full path to the external storage first.
- Does not run in background or reduce the battery life by any meaningful amount

### How it works
This app works by listening to system messages about user facing storage devices. Whenever something gets mounted, the app will run `mount -o remount,mask=0 /mnt/runtime/write/$SD_CARD_FS_UUID` in a root shell.
On my device, the default behavior of Android is to mount external storages with `mask=18`, which prevents "group" and "others" from writing to that directory. `mask=0, which removes no permissions at all was chosen to allow for the greatest access possible. Remember that just the path under `/mnt/runtime/write/` is touched - the function of the android storage permission is preserved.
For reference used an [awesome post](https://android.stackexchange.com/questions/217741/how-to-bind-mount-a-folder-inside-sdcard-with-correct-permissions/217936#217936) from android stackexchange.
Especially for the part that `/mnt/runtime/write` needs to be remounted while the final path will be under `/storage`.

Since this app is called by the system every time it needs to take action, it does not have to run anything in the background. And since it should only run very seldomly and also only for very short times - just a few seconds at most - it should not affect the battery life in any way.

### Security considerations
Since the app handles input and has it end up in a root shell in some way, precautions were taken.  
Firstly, the app listens to a system protected broadcast. This means that on a normal android system, only the system itself should be allowed to send these messages.  
If however, an attacker might have managed to send such a broadcast anyway, rigorous restrictions are applied as to what will be accepted as input, to prevent escaping the shell command and running arbitrary stuff in the root shell and to prevent that someone requests an illegal location to be remounted with `mask=0`.

As a result, only storages mounted under `/storage` will be handled. Their possible UUIDS are also restricted to contain only letters, numbers, hyphens and underscores.  
If you know regex, this is the rule for an acceptable path: `/storage/[A-Za-z0-9_-]+`  
If your Android device mounts external storages under a different path or your filesystem UUID contains any other characters, open an issue about it.
