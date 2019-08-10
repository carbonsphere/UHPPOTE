<?php
/**
 * User: carbonsphere
 * Example code testing add/reset super password
 * Date: 2019/08/10
 */

include "UHPPOTE.php";


$a = new uhppote(); 

/*
 * Remember to set your serial number
 * usually the last 4 bytes of your board's MAC address
 * EX:  00:11:22:33:44:55  then the Serial Number = 22334455
 */

$a->setSn("");

/*   Total of 4 doors with 4 super passwords for each door. (16 super passwords can be set)
 *
 * addCardId['doorIndex'] 
 *
 * addCardId['spassword1'] 
 * addCardId['spassword2'] 
 * addCardId['spassword3'] 
 * addCardId['spassword4'] 
 */

/*
    To reset, just set all 4 passwords 0 for individual door.
    EX: 
 $addCardId = [
    // 1 byte door index  '01': door 1    '02': door 2  '03': door 3   '04': door 4
    "doorIndex" => '01',

    // 6 digit password
    "spassword1" => 0,
    "spassword2" => 0,
    "spassword3" => 0,
    "spassword4" => 0,
];
 */


$addCardId = [
    // 1 byte door index  '01': door 1    '02': door 2  '03': door 3   '04': door 4
    "doorIndex" => '01',

    // 6 digit password
    "spassword1" => 123456,
    "spassword2" => 345678,
    "spassword3" => 901234,
    "spassword4" => 222222,
];

/*
 * Get Record Index from Command from UHPPOTE Class
 */
$cmd = $a->getCmdHex('set_superPass',null,$addCardId);

echo "Send the folling command to network\n";
$a->printCMD($cmd);

/*
 *  IP address can be obtained by running search.php first!
 *  $ip/$port  of controller board
 *  Normally UHPPOTE controller port is static 60000
 */
$ip = "192.168.1.1";
$port = 60000;

$sock = createSocket();

/*
 *  Input is binary format of command
 */
$input = hex2bin($cmd);

echo "Sending....\n";
if( ! socket_sendto($sock, $input , strlen($input) , 0 , $ip , $port))
{
  $errorcode = socket_last_error();
  $errormsg = socket_strerror($errorcode);
  echo "There is error\n";
  exit;
}

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


?>
