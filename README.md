# Author
Carbon Sphere

# Email

CarbonSphere@gmail.com

# Description

This is a Network Communication Class for door controller board.
UHPPOTE door controller, Weigand Access Control system. 微耕門禁開源
Supported Firmware Version V6.56+

# Donation Address

##### If this source helps you in anyway, please consider donating and "STAR" this github project.
##### Thanks!

- BTC: 395vsb41m46voFyhrgYMh6TauWKmNqJAtm

# Supported commands
    > search               //Search
    > get_ip/set_ip        //Get/Set device IP
    > get_ripp/set_ripp    //Get/Set Remote host IP
    > door_delay/door_delay_get    //Set/Get Door Delay
    > open_door            //Open Door
    > add_auth/del_auth    //Add/Delete Card ID
    > set_timeAccess/get_timeAccess    //Get/Set Timer, Allows each card id to be limited access at certain time frame.
    > set_superPass        // Set super passwords

# Example Codes
    > example.php -> Gets Remote Monitoring IP/Port of board
    > search.php  -> GET Controller board's current setting
    > getRecordExample.php -> GET/SET Record Index, Get Records
    > monitor.php -> Monitor events from the board
    > add-user-example.php -> Add user
    > del-user-example.php -> Dete user
    > timer-example.php -> add Timer Index
    > set-super-password-example.php

# Run Example
```console
$ php search.php
```

