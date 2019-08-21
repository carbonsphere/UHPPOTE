<?php
include "UHPPOTE.php";

// Implemented:
//
// 'dev_status'        => 0x20,
// 'open_door'         => 0x40,
// 'set_time'          => 0x30,
// 'get_time'          => 0x32,
// 'search'            => 0x94,  // Get Device Serial Number SN
// 'get_recordIndex'   => 0xb4,  // Get Swipe Records Index
// 'set_ripp'          => 0x90,  // Remote Event receiver IP and port
// 'get_ripp'          => 0x92,  // Remote Event receiver IP and port
// 'get_auth_rec'      => 0x58,  // Get Number of authorized record
// 'get_auth'          => 0x5A,  // Get/Check Authorizations
// 'add_auth'          => 0x50,  // Add/Edit Authorization return true = success false = failed
// 'del_auth'          => 0x52,  // Delete Authorization individual
// 'del_auth_all'      => 0x54,  // Delete All Authorization
// 'door_delay'        => 0x80,  // Set Door Delay seconds
// 'door_delay_get'    => 0x82,  // Get Door Delay seconds
// 'set_timeAccess'    => 0x88,  // Set Access by weekday/time 2-255  0x02-0xFF
// 'get_timeAccess'    => 0x98,  // Get weekday/time access settings
// 'get_alarm_state'   => 0xC2,  // Get Alarm State
// 'interlock'         => 0xA2,  // Set Door interlocking pattern
// 'set_ip'            => 0x96,  // Set Device IP
// 'get_records'       => 0xb0,  // Get Swipe Records from Index + 1
// 'reset_alarm'       => 0xC0,  // Reset Alarm event
// 'set_recordIndex'   => 0xb2,  // Set Swipe Records Index
// 'userid'            => 0x5C,  // User ID is like memory slot of system
// 'keypad_switch'     => 0xA4,  // Enable and disable keypad 1~4
// 'set_superPass'     => 0x8C,  // Set Super Password
//

// Not yet implemented:
// 
// 

// check for valid command line
$cardip = $argv[1];
$cardsn = $argv[2];
$command = $argv[3];
if ($cardip == "help" || !$argv[1] || !$argv[2] || !$argv[3]) {
  $command = "help";
}

// configure parameters
$data = null;
$dt=null;

switch($command) {
  // no parameters required:
  case "dev_status":
  case "get_time":
  case "get_auth_rec":
  case "get_recordIndex":
  case "set_recordIndex":
  case "get_records":
  case "get_ripp":
  case "del_auth_all":
  case "get_alarm_state":
  case "reset_alarm":
  case "search":
    break;
  case "open_door":
  case "door_delay_get":
    echo "Door: " . $argv[4];
    $data = [ 'door' => $argv[4] ];
    break;
  case "set_time":
    $dt = New DateTime('now', new DateTimeZone('America/Chicago'));
    break;
  case "get_auth":
    $data = [ 'cardid' => $argv[4] ];
    break;
  case "set_ripp":
    $data = [ 'ip' => $argv[4] ];
    break;
  case "door_delay":
    $data = [ 'seconds' => $argv[4] ];
    break;
  case "del_auth":
    $data = [ 'cardid' => $argv[4] ];
    break;
  case "add_auth":
    $data = [ 'cardid' => $argv[4],
              'beg' => $argv[5],
              'end' => $argv[6],
              'ta1' => $argv[7],
              'ta2' => $argv[8],
              'ta3' => $argv[9],
              'ta4' => $argv[10]
            ];
    break;
  case "get_timeAccess":
    $data = [ 'index' => $argv[4] ];
    break;
  case "set_timeAccess":
            $weekday = str_split($argv[7],2);
    $data = [ 'index' => $argv[4],
              'beg' => $argv[5],
              'end' => $argv[6],
              'w1' => $weekday[0],
              'w2' => $weekday[1],
              'w3' => $weekday[2],
              'w4' => $weekday[3],
              'w5' => $weekday[4],
              'w6' => $weekday[5],
              'w7' => $weekday[6],
              'time1beg' => $argv[8],
              'time2end' => $argv[9],
              'time2beg' => $argv[10],
              'time2end' => $argv[11],
              'time3beg' => $argv[12],
              'time3end' => $argv[13],
              'countType' => $argv[14],
              'countDay' => $argv[15],
              'countMonth' => $argv[16],
              'countZone1' => $argv[17],
              'countZone2' => $argv[18],
              'countZone3' => $argv[19],
              'weekend' => $argv[20]
            ];
    break;
  case "interlock":
    $data = [ 'interlock' => $argv[4] ];
    break;
  case "set_ip":
    $data = [ 'ip' => $argv[4], 
              'mask' => $argv[5],
              'gate' => $argv[6]
            ];
    break;
  case "userid":
    $data = [ 'userid' => $argv[4] ];
    break;
  case "keypad_switch":
    $data = [ 'pad1' => $argv[4],
              'pad2' => $argv[5],
              'pad3' => $argv[6],
              'pad4' => $argv[7]
            ];
    break;
  case "set_superPass":
    $data = [ 'doorIndex' => $argv[4],
              'spassword1' => $argv[5],
              'spassword2' => $argv[6],
              'spassword3' => $argv[7],
              'spassword4' => $argv[8]
            ];
    break;
  case "help":
    $cmd = '';
    if ($argv[1] == 'help') {
      $cmd = $argv[2];
    }
    if ($argv[3] == 'help') {
      $cmd = $argv[4];
    }    
    showHelp($cmd);
    exit(0);
    break;
  default:
    echo "\n";
    echo "Command not recognised: " . $argv[3] . "\n";
    showHelp("help");
    exit(1);
    break;
}
  
$a = new uhppote(); 

$a->setSn($cardsn);
$ip = $cardip;

#$a->setSn("19395b5e");
#$a->setSn("19395d30");

print_r($data);

$cmd = $a->getCmdHex($argv[3],$dt,$data);

echo "Send the following command to network\n$cmd\n";

$port = 60000;

$sock = createSocket();

$input = hex2bin($cmd);

echo "Sending....\n";
if( ! socket_sendto($sock, $input , strlen($input) , 0 , $cardip , $port))
{
  $errorcode = socket_last_error();
  $errormsg = socket_strerror($errorcode);
  echo "There is an error:\n";
  echo $errorcode . " -- " . $errormsg . "\n";
  exit;
}

echo "Listening for return status\n";
$reply = getReturnPacket($sock);

echo "Processing return status\n";
$procmsg = $a->procCmd(bin2hex($reply));

print_r($procmsg);
echo "\n";

function getRecord($uhppote,$socket, $recordIndex,$cardip,$port) {
  $cmd = $uhppote->getCmdHex('get_records',$recordIndex);
  $input = hex2bin($cmd);
//  echo "Sending get record $recordIndex....\n";
  if( ! socket_sendto($socket, $input , strlen($input) , 0 , $cardip , $port))
  {
    $errorcode = socket_last_error();
    $errormsg = socket_strerror($errorcode);
    die("There is error");
  }
  $reply = getReturnPacket($socket);
//  echo "Processing return $recordIndex status\n";
  $procmsg = $uhppote->procCmd(bin2hex($reply));

//  echo "Card: " . $procmsg[CardId] . " -- Date/Time: " . $procmsg[swipeymdhms] . " -- Type: " . $procmsg[rType] . " -- Door: " . $procmsg[Door] . " -- Door Stat: " . $procmsg[DoorStat] . "\n";
  print_r($procmsg);

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

function showHelp($cmd)
{
  echo "\n";
  switch($cmd) {
    case 'get_time':
      echo "get_time -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_time\n\n";
      break;
    case 'dev_status':
      echo "dev_status -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 dev_status\n\n";
      break;
    case 'get_auth_rec':
      echo "get_auth_rec -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_auth_rec\n\n";
      break;
    case 'get_recordIndex':
      echo "get_recordIndex -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_recordIndex\n\n";
      break;
    case 'set_recordIndex':
      echo "set_recordIndex -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_recordIndex\n\n";
      break;
    case 'get_ripp':
      echo "get_ripp -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_ripp\n\n";
      break;
    case 'del_auth_all':
      echo "del_auth_all -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 del_auth_all\n\n";
      break;
    case 'search':
      echo "search -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 search\n\n";
      break;
    case 'open_door':
      echo "open_door <door number>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 open_door 03\n\n";
      break;
    case 'door_delay_get':
      echo "door_delay_get <door number>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 door_delay_get 02\n\n";
      break;
    case 'set_time':
      echo "set_time -- No parameters required. Uses system time where this command is run.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_time\n\n";
      break;
    case 'get_auth':
      echo "get_auth <cardid>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_auth 10012345\n\n";
      break;
    case 'set_ripp':
      echo "set_ripp <local ip address>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_ripp 192.168.0.10\n\n";
      break;
    case 'door_delay':
      echo "door_delay <seconds>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 door_delay 5\n\n";
      break;
    case 'del_auth':
      echo "del_auth <cardid>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 del_auth 10012345\n\n";
      break;
    case 'add_auth':
      echo "add_auth <cardid> <begindate as YYYYMMDD> <enddate as YYYYMMDD> <door1> <door2> <door3> <door4>\n";
      echo "\n";
      echo "Example (Facility code 100, Card code 12345, From Jan 01, 2019 to Jan 01, 2020, and valid on doors 1, 2, and 4.):\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 10012345 20190101 20200101 01 01 00 01\n\n";
      break;
    case 'get_timeAccess':
      echo "get_timeAccess <index>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_timeAccess 02\n\n";
      break;
    case 'set_timeAccess':
      echo "set_timeAccess <index> <begin> <end> <weekday> \ \n";
      echo "  <time1begin> <time1end> <time2begin> <time2end> <time3begin> <time3end> \ \n";
      echo "  <countType> <countDay> <countMonth> \ \n";
      echo "  <countZone1> <countZone2> <countZone3> <weekend>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_timeAccess 02 20190101 20200101 01010101010101 1600 2359 0000 0000 0000 0000 00 00 00 00 00 00 00\n\n";
      break;
    case 'get_alarm_state':
      echo "get_alarm_state -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_alarm_state\n\n";
      break;
    case 'interlock':
      echo "interlock <pattern>\n";
      echo "\n";
      echo "pattern is one of the following:\n";
      echo "  '00' no interlock\n";
      echo "  '01' 1,2 door interlock\n";
      echo "  '02' 3,4 door interlock\n";
      echo "  '03' pair lock for (1,2) (3,4)\n";
      echo "  '04' 1,2,3 door interlock\n";
      echo "  '08' 1,2,3,4 door interlock\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 interlock 03\n\n";
      break;
    case 'set_ip':
      echo "set_ip <ip> <netmask> <gateway>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 192.168.1.123 255.255.255.0 192.168.1.1\n\n";
      break;
    case 'get_records':
      echo "get_records -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_records\n\n";
      break;
    case 'reset_alarm':
      echo "reset_alarm -- No parameters required.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 reset_alarm\n\n";
      break;
    case 'userid':
      echo "userid <userid>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 userid 2\n\n";
      break;
    case 'keypad_switch':
      echo "keypad_switch <pad1> <pad2> <pad3> <pad4>\n";
      echo "\n";
      echo "pad 1 through pad4 are either 00 for no keypad or 01 for keypad present.\n";
      echo "\n";
      echo "Example (keypad on doors 1 and 3):\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 keypad_switch 01 00 01 00\n\n";
      break;
    case 'set_superPass':
      echo "set_superPass <door> <spassword1> <spassword2> <spassword3> <spassword4>\n";
      echo "\n";
      echo "door is 01 through 04, and\n";
      echo "spassword1 through spassword4 are the four bytes of the super password\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_superPass 34 27 18 22\n\n";
      break;
    default:
      echo "Usage: \n";
      echo "\n";
      echo "php -f sendCommand.php <ip address> <serial> <command> [options]\n";
      echo "\n";
      echo "<ip address> is the IP of the board you're addressing.\n";
      echo "<serial> is the serial number of the board you're addressing. It is the\n";
      echo "  same as the last 4 octets of the MAC address of the card. It is not the\n";
      echo "  serial number printed on the top of the card!\n";
      echo "\n";
      echo "Commands:\n";
      echo "\n";
      echo "get_time, dev_status, get_auth_rec, get_recordIndex, get_ripp,\n";
      echo "del_auth_all, search, open_door, door_delay_get, set_time, get_auth,\n";
      echo "set_ripp, door_delay, del_auth, add_auth, set_timeAccess\n";
      echo "get_timeAccess, set_timeAccess, get_alarm_state, interlock, set_ip,\n";
      echo "get_records, reset_alarm, set_recordIndex, userid, keypad_switch,\n";
      echo "set_superPass\n";
      echo "\n";
      echo "Each command accepts options as needed.\n";
      echo "\n";
      echo "help -- Shows this information\n";
      echo "\n";
      echo "help command -- shows command soecific help\n\n";
      break;
  }
}

?>
