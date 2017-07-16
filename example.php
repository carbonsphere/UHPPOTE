<?php
/**
 * User: carbonsphere
 * Date: 15/08/2017
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
 * Get Remote Monitor IP Command from UHPPOTE Class
 */
$cmd = $a->getCmdHex('get_ripp');

echo "Send the folling command to network\n$cmd\n";

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

var_dump($procmsg);


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

?>
