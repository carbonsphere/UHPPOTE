<?php
/**
 * User: carbonsphere
 * Example code for testing get/set record index & get swipe records.
 * Date: 16/08/2017
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

$usrInfo = [
    'cardid' => '',
    'beg' => '20190522',
    'end' => '20200522',
    // Leave it default if you do not have a Time Access Rule Index
    // Rule Index for Door 1
    'ta1' => '01', //Time Access rule index. Index starts from 2 - 254  0x02 ~ 0xFE
    // Rule Index for Door 2
    'ta2' => '01',
    // Rule Index for Door 3
    'ta3' => '01',
    // Rule Index for Door 4
    'ta4' => '01',
];

/*
 * Get Record Index from Command from UHPPOTE Class
 */
$cmd = $a->getCmdHex('add_auth',null,$usrInfo);

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
 * Get Last Record from Command from UHPPOTE Class
 * This device only keeps 200,000 records by setting it to 
 * 0xffffffff
 * Since command get records will add 1 we can pass in 0xfffffffe
 * It will return the very last record
 */
$cmd = $a->getCmdHex('get_records',0xfffffffe);

echo "Send the folling command to network\n$cmd\n";

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
$lastIndex = $procmsg["Index"];


/*
 * Get last 6 records
 */
for( $i = $lastIndex-2; $i >= $lastIndex-6; $i--) 
{
  getRecord($a,$sock,$i,$ip,$port);
} 

/*
 * Enable this function to test set index function
 * Remember: It will reset your current Record Index.
 *
 * This function is used to keep track of your read records. 
 * Instead of deleting record once it is read, you  will need to increase this index.
 *
 */

#testSetIndex(1,$a,$sock,$ip,$port);

function testSetIndex($recordIndex,$uhppote,$socket,$ip,$port) {
  $cmd = $uhppote->getCmdHex('set_recordIndex',$recordIndex);
  $input = hex2bin($cmd);
  echo "$cmd\n";
  echo "Sending set record $recordIndex....\n";
  if( ! socket_sendto($socket, $input , strlen($input) , 0 , $ip , $port))
  {
    $errorcode = socket_last_error();
    $errormsg = socket_strerror($errorcode);
    die("There is error");
  }
  $reply = getReturnPacket($socket);
  echo "Processing return $recordIndex status\n";
  $procmsg = $uhppote->procCmd(bin2hex($reply));

  var_dump($procmsg);

}


function getRecord($uhppote,$socket, $recordIndex,$ip,$port) {
  $cmd = $uhppote->getCmdHex('get_records',$recordIndex);
  $input = hex2bin($cmd);
  echo "Sending get record $recordIndex....\n";
  if( ! socket_sendto($socket, $input , strlen($input) , 0 , $ip , $port))
  {
    $errorcode = socket_last_error();
    $errormsg = socket_strerror($errorcode);
    die("There is error");
  }
  $reply = getReturnPacket($socket);
  echo "Processing return $recordIndex status\n";
  $procmsg = $uhppote->procCmd(bin2hex($reply));

  var_dump($procmsg);

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

?>
