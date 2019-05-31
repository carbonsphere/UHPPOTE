<?php
/**
 * User: carbonsphere
 * Example code testing timer
 * Date: 2019/05/27
 */

include "UHPPOTE.php";


$a = new uhppote(); 

/*
 * Remember to set your serial number
 * usually the last 4 bytes of your board's MAC address
 * EX:  00:11:22:33:44:55  then the Serial Number = 22334455
 */

$a->setSn("");

            /*
             * addCardId['beg']  Beginning date of auth period
             * addCardId['end']  End date of auth period
             * beg/end format [ year month day ]
             * Ex: 2000 01 01   "20000101" year 2000 jan 1st
             * Max End date "20291231"  2029 Dec 31st
             */

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

    "countDay" => '00',
    "countMonth" => '00',
    "countZone1" => '00',
    "countZone2" => '00',
    "countZone3" => '00',
    "weekend" => '00'
];

/*
 * Get Record Index from Command from UHPPOTE Class
 */
$cmd = $a->getCmdHex('add_auth',null,$timer);

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
