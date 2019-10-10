<?php
/**
 * User: carbonsphere
 *
 * Example code for adding tasks
 * Mimic UHPPOTE windows software implementation.
 *
 * Allows to add multiple tasks.
 *
 * - Parameter templates are provided.
 * - Default values are used when variables are not provided.
 * - It is essential to clear tasks before any tasks are added.
 *      Single tasks cannot be append on to the list after list has
 *      been saved.
 * - List must be completed in single save.
 *
 *
 * Procedure on setting task
 *
 * 1. Clear old tasks.
 * 2. Add tasks.
 * 3. Save added tasks.
 *
 *
 *
 * Date: 2019/10/10
 */

include "UHPPOTE.php";



$a = new uhppote();
$sock = createSocket();

/*
 * Remember to set your serial number
 * usually the last 4 bytes of your board's MAC address
 * EX:  00:11:22:33:44:55  then the Serial Number = 22334455
 */

$a->setSn("11223344");

/*
 *  IP address can be obtained by running search.php first!
 *  $ip/$port  of controller board
 *  Normally UHPPOTE controller port is static 60000
 */
$ip = "192.168.1.1";
$port = 60000;


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


/*
 *                  Clear tasks first
 */
echo "Clear all tasks first\n";
$cmd = $a->getCmdHex('del_task_list', null, null);

echo "Send the folling command to network\n";
$a->printCMD($cmd);
$input = hex2bin($cmd);

echo "Sending....\n";
sendPackets($sock,$input,$ip,$port);

/*
 * Once the command is sent to UHPPOTE controller board, now we need to listen for return status
 */
echo "Listening for return status\n";
$reply = getReturnPacket($sock);
/*
 * Process returned message. Returned message is in binary format
 * So we revert it into hex before passing into procMessages
 */
echo "Processing return status\n";
$procmsg = $a->procCmd(bin2hex($reply));
if($procmsg) {
    echo "Controller returned success\n";
} else {
    echo "Error, command not set\n";
}





/*
 *                  Add Tasks
 */





/*
 *              Add Task 1
 */

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

$cmd = $a->getCmdHex('add_task_list',null,$task1);

echo "Send the folling command to network\n";
$a->printCMD($cmd);

/*
 *  Input is binary format of command
 */
$input = hex2bin($cmd);

echo "Sending....\n";
sendPackets($sock,$input,$ip,$port);

/*
 * Once the command is sent to UHPPOTE controller board, now we need to listen for return status
 */
echo "Listening for return status\n";
$reply = getReturnPacket($sock);
/*
 * Process returned message. Returned message is in binary format
 * So we revert it into hex before passing into procMessages
 */
echo "Processing return status\n";
$procmsg = $a->procCmd(bin2hex($reply));
if($procmsg) {
    echo "Controller returned success\n";
} else {
    echo "Error, command not set\n";
}










/*
 *                      Add Task 2
 */

/*
 * Open Door 2 on Mon-Sun from 6:30am
 */

$task2 = [
    'begDate' => '20191010',
    'endDate' => '20291231',
    'sTime'  => '0630',
    'door'   => '02',
    'task'   => '01',
];
echo "Add task 2\n";

$cmd = $a->getCmdHex('add_task_list',null,$task2);

echo "Send the folling command to network\n";
$a->printCMD($cmd);

/*
 *  Input is binary format of command
 */
$input = hex2bin($cmd);

echo "Sending....\n";
sendPackets($sock,$input,$ip,$port);

/*
 * Once the command is sent to UHPPOTE controller board, now we need to listen for return status
 */
echo "Listening for return status\n";
$reply = getReturnPacket($sock);
/*
 * Process returned message. Returned message is in binary format
 * So we revert it into hex before passing into procMessages
 */
echo "Processing return status\n";
$procmsg = $a->procCmd(bin2hex($reply));
if($procmsg) {
    echo "Controller returned success\n";
} else {
    echo "Error, command not set\n";
}







/*
 *                  Add Task 3
 */


echo "Add task 3\n";
/*
 * Set Door 1 to Controlled on Tue-Fri from 00:00am
 */
$task3 = [
    'begDate' => '20191010',
    'w1'     => '00',
    'w6'     => '00',
    'w7'     => '00',
    'door'   => '01',
    'task'   => '00',
];

$cmd = $a->getCmdHex('add_task_list',null,$task3);

echo "Send the folling command to network\n";
$a->printCMD($cmd);

/*
 *  Input is binary format of command
 */
$input = hex2bin($cmd);

echo "Sending....\n";
sendPackets($sock,$input,$ip,$port);

/*
 * Once the command is sent to UHPPOTE controller board, now we need to listen for return status
 */
echo "Listening for return status\n";
$reply = getReturnPacket($sock);

/*
 * Process returned message. Returned message is in binary format
 * So we revert it into hex before passing into procMessages
 */
echo "Processing return status\n";
$procmsg = $a->procCmd(bin2hex($reply));
if($procmsg) {
    echo "Controller returned success\n";
} else {
    echo "Error, command not set\n";
}







/*
 *                          Add Task 4
 */

echo "Add task 4\n";
/*
 * Set Door 2 to Controlled on Tue-Fri from 00:00am
 */
$task4 = [
    'begDate' => '20191010',
    'w1'     => '00',
    'w6'     => '00',
    'w7'     => '00',
    'door'   => '02',
    'task'   => '00',
];


$cmd = $a->getCmdHex('add_task_list',null,$task4);

echo "Send the folling command to network\n";
$a->printCMD($cmd);

/*
 *  Input is binary format of command
 */
$input = hex2bin($cmd);

echo "Sending....\n";
sendPackets($sock,$input,$ip,$port);

/*
 * Once the command is sent to UHPPOTE controller board, now we need to listen for return status
 */
echo "Listening for return status\n";
$reply = getReturnPacket($sock);

/*
 * Process returned message. Returned message is in binary format
 * So we revert it into hex before passing into procMessages
 */
echo "Processing return status\n";
$procmsg = $a->procCmd(bin2hex($reply));

if($procmsg) {
    echo "Controller returned success\n";
} else {
    echo "Error, command not set\n";
}



/*
 *
 *              Save Tasks
 *
 */
$cmd = $a->getCmdHex('sav_task_list',null,null);

echo "Send the folling command to network\n";
$a->printCMD($cmd);

/*
 *  Input is binary format of command
 */
$input = hex2bin($cmd);

echo "Sending....\n";
sendPackets($sock,$input,$ip,$port);

/*
 * Once the command is sent to UHPPOTE controller board, now we need to listen for return status
 */
echo "Listening for return status\n";
$reply = getReturnPacket($sock);

/*
 * Process returned message. Returned message is in binary format
 * So we revert it into hex before passing into procMessages
 */
echo "Processing return status\n";
$procmsg = $a->procCmd(bin2hex($reply));

if($procmsg) {
    echo "Controller returned success\n";
} else {
    echo "Error, command not set\n";
}



function createSocket()
{
    if(!($sock = socket_create(AF_INET, SOCK_DGRAM, 0)))
    {
        $errorcode = socket_last_error();
        $errormsg = socket_strerror($errorcode);

        die("Couldn't create socket: [$errorcode] $errormsg \n");
    }
    return $sock;
}


function getReturnPacket($sock)
{

    if(socket_recv ( $sock , $reply , 2045 , MSG_WAITALL ) === FALSE)
    {
        $errorcode = socket_last_error();
        $errormsg = socket_strerror($errorcode);

        die("Receive socket Error: [$errorcode] $errormsg \n");
    }
    return $reply;
}

function sendPackets($sock,$input,$ip,$port)
{

    if (!socket_sendto($sock, $input, strlen($input), 0, $ip, $port)) {
        $errorcode = socket_last_error();
        $errormsg = socket_strerror($errorcode);
        echo "There is error\n";
        var_dump($errormsg);
        exit;
    }
}

?>
