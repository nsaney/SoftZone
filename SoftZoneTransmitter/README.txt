1. Change locale and keyboard layout to English (US) instead of UK/GB.
   - congrats, now you can type a pipe |!

2. Update wifi. This can be done in /etc/network/interfaces
   - it may be tempting to do this in /etc/wpa_supplicant/wpa_supplicant.conf
   - and it may be possible to point /etc/network/interfaces to that config file
   - but whatever, we will probably be using the ethernet cable anyway,
     so this is just a nice-to-have

3. Don't set up a shared drive. Connect to the pi with Notepad++ or another SFTP (or SCP) tool.

4. Change the Pi's network name. This is done in two places.
   - in /etc/hosts (for the Linux-loopback 127.0.1.1 entry)
   - then in /etc/hostname (the only content of that file)
   - then run /etc/init.d/hostname.sh
   - then reboot

5. Install slash configure SoftZoneTransmitter
   - Add SoftZoneTransmitter directory
   -- at /home/pi/Desktop/SoftZoneTransmitter
   -- includes bin/, logs/, input_normal.txt, and SoftZoneTransmitter.jar
   -- make sure the options chosen in input_normal.txt correspond to
      what you would enter (including return characters) to select the appropriate
      sound input device, target data line, and optional debug sound output device
   - Add start command to boot script
   -- in /etc/rc.local
   -- /home/pi/Desktop/SoftZoneTransmitter/bin/softzone start
   - Add binary directory to PATH
   -- in /home/pi/.bashrc
   -- export PATH=$PATH:/home/pi/Desktop/SoftZoneTransmitter/bin

----
