<?php
/**
 * User: jleach
 * Date: 18/08/2017
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
 * The ip address and port of this machine tht will receive 
 * monitor messages
 */

$localip = "192.168.1.2";
$localport = 60002;

/*
 * The number of seconds to wait between messages. 
 * 0 will disable regular messages but messages will still be sent on events e.g. card read
 */
$interval = 0;
/*
 * Get Remote Monitor IP Command from UHPPOTE Class
 */
$args = array("ip"=>$localip, "port"=>$localport, "sec"=>$interval);
$cmd = $a->getCmdHex('set_ripp', null, $args);

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

/*
 * create a new socket for us to listen to monitor message
 */

$sock = createSocket();

if( !socket_bind($sock, $localip , $localport) )
{
    $errorcode = socket_last_error();
    $errormsg = socket_strerror($errorcode);

    die("Could not bind socket : [$errorcode] $errormsg \n");
}

echo "Socket bind OK \n";

//Do some communication, this loop can handle multiple clients
while(1)
{
    echo "\n Waiting for data ... \n";

    //Receive some data
    $r = socket_recvfrom($sock, $reply, 2045, 0,$remote_ip, $remote_port);
	
    echo "Monitor received\n";
    $procmsg = $a->procCmd(bin2hex($reply));

    var_dump($procmsg);

}

socket_close($sock);


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
