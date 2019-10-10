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

chkTaskListCmd($u);

#chkTimerCmd($u);
#chkTimerRet($u);



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
/*
 *
 *                  Template

$template = [
        'begDate' => '20191010',        // Default current system time.
        'endDate' => '20291231',        // Default 2019-12-31
        'w1'     => '01',               // Monday Default Enabled
        'w2'     => '01',               // Tuesday Default Enabled
        'w3'     => '01',               // Wednesday Default Enabled
        'w4'     => '01',               // Thursday Default Enabled
        'w5'     => '01',               // Friday Default Enabled
        'w6'     => '01',               // Saturday Default Enabled
        'w7'     => '01',               // Sunday Default Enabled
        'sTime'  => '0000',             // Default midnight 00:00 - 23:59
        'door'   => '01',               // Door Number  01,02,03,04 Default 01
        'task'   => '00',               // Default 00:Door controlled
        'option'  => '00',              // Unknown
    ];

 */
function chkTaskListCmd($u) {

    /*
     * Clear tasks first
     */
    echo "Clear all tasks first\n";
    $cmd = $u->getCmdHex('del_task_list', null, null);

    $u->printCMD($cmd);



    echo "Add task 1\n";
    /*
     * Open Door 1 on Mon-Sun from 6:30am
     */
    $task1 = [
        'begDate' => '20191010',
        'endDate' => '20291231',
        'sTime'  => '0630',
        'door'   => '01',
        'task'   => '01',
    ];

    $cmd = $u->getCmdHex('add_task_list', null, $task1);

    $u->printCMD($cmd);

    echo "Add task 2\n";
    /*
     * Open Door 1 on Mon-Sun from 6:30am
     */
    $task2 = [
        'begDate' => '20191010',
        'endDate' => '20291231',
        'sTime'  => '0630',
        'door'   => '02',
        'task'   => '01',
    ];

    $cmd = $u->getCmdHex('add_task_list', null, $task2);

    $u->printCMD($cmd);

    echo "Save tasks\n";
    /*
     * Save Tasks
     */
    $cmd = $u->getCmdHex('sav_task_list', null, null);

    $u->printCMD($cmd);

}
