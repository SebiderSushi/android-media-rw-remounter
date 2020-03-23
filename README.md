# [ROOT] Android External Storage R/W Remounter
Remember how google decided in 2013 that android needs a new and fancy framework for storage access?  
Frustrated of how they took away external storage write access because of this, as if to test drive the change on android devices with an SD Card (i.e. not Google devices) before enforcing it with internal storages as well for apps targeting and running on android 10 and above?  
Just want back write access to your sd card?  

Well now there's an app for that!

### Requirements
- An android device with an SD Card slot and/or USB OTG support
- Root access, since the app remounts some stuff

This has successfully been tested with devices running android 8.1, 9 and 10.
The app might be incompatible with devices running android 5.1 and below, but this has not been tested.

### How to use it
- Install
- Launch once
- Insert or remount some storage and grant root acces
- Profit!

Notes:
The app must be launched at least once or else the system won't ever run it. This is a security mechanism of the Android platform to protect users from apps they never launched and maybe never wanted to use or install.
This restriction can be removed by installing the app as a privileged app, i.e. by writing the apk file to `/system/priv-app`.  
Upon the first mount with this app installed, i.e. when you actively need to grant root access, it is possible that the app will do nothing.
This is because the app will currently be terminated if it has to wait more than a few seconds for root access. Once root access has already been granted and the app is immediately given root access every time it needs it, everything should work as expected.

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
This app works by listening to system messages about user facing storage devices. Whenever something gets mounted, the app will run `mount -o remount,mask=7 /mnt/runtime/write/$SD_CARD_FS_UUID` in a root shell.
On my device, the default behavior of Android is to mount external storages with `mask=18`, which prevents "group" and "others" from writing to that directory. `mask=7`, which removes read, write and execute permissions for "others" was chosen to mimic Androids mount options for `/storage/emulated/0` as observed on my Android device running LineageOS 16.0.  
For reference used an [awesome post](https://android.stackexchange.com/questions/217741/how-to-bind-mount-a-folder-inside-sdcard-with-correct-permissions/217936#217936) from android stackexchange.
Especially for the part that `/mnt/runtime/write` needs to be remounted while the final path will be under `/storage`.

Since this app is called by the system every time it needs to take action, it does not have to run anything in the background. And since it should only run very seldomly and also only for very short times - just a few seconds at most - it should not affect the battery life in any way.

### Security considerations
Since the app handles input and has it end up in a root shell in some way, precautions were taken.  
Firstly, the app listens to a system protected broadcast. This means that on a normal android system, only the system itself should be allowed to send these messages.  
If however, an attacker might have managed to send such a broadcast anyway, rigorous restrictions are applied as to what will be accepted as input, to prevent escaping the shell command and running arbitrary stuff in the root shell and to prevent that someone requests an illegal location to be remounted with `mask=7`.

As a result, only storages mounted under `/storage` will be handled. Their possible UUIDS are also restricted to contain only letters, numbers, hyphens and underscores.  
If you know regex, this is the rule for an acceptable path: `/storage/[A-Za-z0-9_-]+`  
If your Android device mounts external storages under a different path or your filesystem UUID contains any other characters, open an issue about it.
