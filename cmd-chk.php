<?PHP

include "UHPPOTE.php";
/**
 * Timer
 *
 * ['index']  2-255   0x02  - 0xFF
 * ['beg']    : beginning Year Month Day : 20190120
 * ['end']    : end Year Month Day       : 20200120
 *
 * ['w1']     : Monday                   : '00' = not allowed, '01' = allowed
 * ['w2']     : Tuesday                  : '00' = not allowed, '01' = allowed
 * ['w3']     : Wedsday                  : '00' = not allowed, '01' = allowed
 * ['w4']     : Thursday                 : '00' = not allowed, '01' = allowed
 * ['w5']     : Friday                   : '00' = not allowed, '01' = allowed
 * ['w6']     : Saturday                 : '00' = not allowed, '01' = allowed
 * ['w7']     : Sunday                   : '00' = not allowed, '01' = allowed
 *
 * ['time1beg'] : Slot 1 Beginning time  : 00:00 midnight    = '0000' Initial time
 * ['time1end'] : Slot 1 Ending time     : 23:59 end of day  = '2359' Max end time
 * ['time2beg'] : Slot 2 Beginning time
 * ['time2end'] : Slot 2 Ending time
 * ['time3beg'] : Slot 3 Beginning time
 * ['time3end'] : Slot 3 Ending time
 *
 * ['countDay']   : Total Allowed Access count per day
 *                : '00' unlimited, '10' 10 access allowed per day
 * ['countMonth'] : Total Allowed Access count per month
 *                : '00' unlimited, '10' 10 access allowed per month
 * ['countZone1'] : Total Allowed Access for zone 1
 *                : '00' unlimited
 * ['countZone2'] : Total Allowed Access for zone 2
 * ['countZone3'] : Total Allowed Access for zone 3
 * ['weekend']    : Weekend Access control
 *                : '00' allowed over weekend '01' deny over weekend
 * Ex:  This can be used as template
 */

/** @var uhppote $u */
$u = new uhppote();

$u->setSn("11223344");


chkTimerCmd($u);
chkTimerRet($u);



function chkTimerCmd($u)
{
    $timer = [
        "index" => '02',

        "beg" => '20190524',
        "end" => '20190524',

        "w1" => '01',
        "w2" => '01',
        "w3" => '01',
        "w4" => '01',
        "w5" => '01',
        "w6" => '01',
        "w7" => '01',

        "time1beg" => '1600',
        "time1end" => '2359',
        "time2beg" => '0000',
        "time2end" => '0000',
        "time3beg" => '0000',
        "time3end" => '0000',

        "countType"  => '01',
        "countDay"   => '00',
        "countMonth" => '00',
        "countZone1" => '00',
        "countZone2" => '00',
        "countZone3" => '00',
        "weekend" => '00'
    ];



    $cmd = $u->getCmdHex('set_timeAccess', null, $timer);

    $u->printCMD($cmd);

}


function chkTimerRet($u) {
    $cmd = '1788000044332211';

    echo "-- Set Success\n";
    $u->procCmd($cmd . '01');

    echo "-- Set Fail\n";
    $u->procCmd($cmd . '00');


}
