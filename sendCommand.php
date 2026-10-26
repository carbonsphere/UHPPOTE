<?php
include "UHPPOTE.php";

$debug = 0; // If enabled optional debug output will be present.
$tz = 'America/Chicago'; // Set your time zone here. Find it at https://www.php.net/manual/en/timezones.php.
$timeout = 3; // Seconds to wait for the board to reply.

// check for valid command line
$cardip = isset($argv[1]) ? $argv[1] : '';
$cardsn = isset($argv[2]) ? $argv[2] : '';
$command = isset($argv[3]) ? $argv[3] : '';
if ($cardip == "help" || !$cardip || !$cardsn || !$command) {
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
  case "get_ripp":
  case "del_auth_all":
  case "get_alarm_state":
  case "reset_alarm":
  case "search":
    break;
  case "open_door":
  case "door_delay_get":
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    if ($debug) echo "Door: " . $argv[4];
    $data = [ 'door' => $argv[4] ];
    break;
  case "set_recordIndex":
    if (!isset($argv[4]) || !is_numeric($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $dt = intval($argv[4]);
    break;
  case "get_records":
    // The library fetches record <index + 1>, 0xfffffffe fetches the newest record.
    $dt = isset($argv[4]) ? intval($argv[4]) - 1 : 0xfffffffe;
    break;
  case "set_time":
    $dt = New DateTime('now', new DateTimeZone($tz));
    break;
  case "get_auth":
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'cardid' => $argv[4] ];
    break;
  case "set_ripp":
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'ip' => $argv[4],
              'port' => isset($argv[5]) ? intval($argv[5]) : 60002,
              'sec' => isset($argv[6]) ? intval($argv[6]) : 0
            ];
    break;
  case "door_delay":
    if (empty($argv[4]) || !isset($argv[5])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'door' => $argv[4],
              'seconds' => intval($argv[5])
            ];
    break;
  case "del_auth":
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'cardid' => $argv[4] ];
    break;
  case "add_auth":
    if (empty($argv[4]) || empty($argv[5]) || empty($argv[6])) {
      showHelp($command);
      exit(1);
    }
    // Door rules are optional and default to '01' (allowed).
    $data = [ 'cardid' => $argv[4],
              'beg' => $argv[5],
              'end' => $argv[6],
              'ta1' => isset($argv[7]) ? $argv[7] : '01',
              'ta2' => isset($argv[8]) ? $argv[8] : '01',
              'ta3' => isset($argv[9]) ? $argv[9] : '01',
              'ta4' => isset($argv[10]) ? $argv[10] : '01'
            ];
    break;
  case "get_timeAccess":
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'index' => $argv[4] ];
    break;
  case "set_timeAccess":
    if (empty($argv[4]) || empty($argv[5]) || empty($argv[6]) || empty($argv[7]) || empty($argv[8]) || empty($argv[9]) || 
        empty($argv[10]) || empty($argv[11]) || empty($argv[12]) || empty($argv[13]) || empty($argv[14]) || empty($argv[15]) || 
        empty($argv[16]) || empty($argv[17]) || empty($argv[18]) || empty($argv[19]) || empty($argv[20])) {
      showHelp($command);
      exit(1);
    }
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
              'time1end' => $argv[9],
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
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'interlock' => $argv[4] ];
    break;
  case "set_ip":
    if (empty($argv[4]) || empty($argv[5]) || empty($argv[6])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'ip' => $argv[4], 
              'mask' => $argv[5],
              'gate' => $argv[6]
            ];
    break;
  case "userid":
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'userid' => $argv[4] ];
    break;
  case "keypad_switch":
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'pad1' => $argv[4],
              'pad2' => isset($argv[5]) ? $argv[5] : '00',
              'pad3' => isset($argv[6]) ? $argv[6] : '00',
              'pad4' => isset($argv[7]) ? $argv[7] : '00'
            ];
    break;
  case "set_superPass":
    if (empty($argv[4])) {
      showHelp($command);
      exit(1);
    }
    $data = [ 'doorIndex' => $argv[4],
              'spassword1' => isset($argv[5]) ? intval($argv[5]) : 0,
              'spassword2' => isset($argv[6]) ? intval($argv[6]) : 0,
              'spassword3' => isset($argv[7]) ? intval($argv[7]) : 0,
              'spassword4' => isset($argv[8]) ? intval($argv[8]) : 0
            ];
    break;
  case "help":
    $cmd = '';
    if (isset($argv[1]) && $argv[1] == 'help') {
      $cmd = isset($argv[2]) ? $argv[2] : '';
    }
    if (isset($argv[3]) && $argv[3] == 'help') {
      $cmd = isset($argv[4]) ? $argv[4] : '';
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

if ($debug) print_r($data);

$cmd = $a->getCmdHex($command,$dt,$data);
if ($cmd === false) {
  echo "Invalid command parameters.\n";
  showHelp($command);
  exit(1);
}

if ($debug) echo "Send the following command to network\n$cmd\n";

$port = 60000;

$sock = createSocket($timeout);

$input = hex2bin($cmd);

if( ! socket_sendto($sock, $input , strlen($input) , 0 , $cardip , $port))
{
  $errorcode = socket_last_error();
  $errormsg = socket_strerror($errorcode);
  echo "There is an error:\n";
  echo $errorcode . " -- " . $errormsg . "\n";
  exit;
}

if ($debug) echo "Listening for return status\n";
$reply = getReturnPacket($sock);

if ($debug) echo "Processing return status\n";
$procmsg = $a->procCmd(bin2hex($reply));

print_r($procmsg);
echo "\n";

function createSocket($timeout) 
{
  if(!($sock = socket_create(AF_INET, SOCK_DGRAM, 0)))
  {
    $errorcode = socket_last_error();
    $errormsg = socket_strerror($errorcode);

    die("Couldn't create socket: [$errorcode] $errormsg \n");
   }
   // Don't wait forever if the board doesn't answer (wrong IP or serial number).
   socket_set_option($sock, SOL_SOCKET, SO_RCVTIMEO, array('sec' => $timeout, 'usec' => 0));
   return $sock;
}


function getReturnPacket($sock) 
{
  if(socket_recv ( $sock , $reply , 2045 , MSG_WAITALL ) === FALSE)
  {
     $errorcode = socket_last_error();
     $errormsg = socket_strerror($errorcode);

    die("No reply from board (check IP address and serial number): [$errorcode] $errormsg \n");
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
      echo "set_recordIndex <index>\n";
      echo "\n";
      echo "Marks records up to <index> as read.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_recordIndex 100\n\n";
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
      echo "set_ripp <local ip address> [<port>] [<interval>]\n";
      echo "\n";
      echo "port defaults to 60002. interval is the number of seconds between status\n";
      echo "messages, 0 (default) sends messages on events only.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_ripp 192.168.0.10 60002 0\n\n";
      break;
    case 'door_delay':
      echo "door_delay <door number> <seconds>\n";
      echo "\n";
      echo "Sets the door to controlled mode with the given unlock time.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 door_delay 02 5\n\n";
      break;
    case 'del_auth':
      echo "del_auth <cardid>\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 del_auth 10012345\n\n";
      break;
    case 'add_auth':
      echo "add_auth <cardid> <begindate as YYYYMMDD> <enddate as YYYYMMDD> [<door1> <door2> <door3> <door4>]\n";
      echo "\n";
      echo "door1 through door4 are optional. If excluded access will be added to all doors.\n";
      echo "\n";
      echo "Example (Facility code 100, Card code 12345, From Jan 01, 2019 to Jan 01, 2020, and valid on doors 1, 2, and 4.):\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 add_auth 10012345 20190101 20200101 01 01 00 01\n\n";
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
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_ip 192.168.1.123 255.255.255.0 192.168.1.1\n\n";
      break;
    case 'get_records':
      echo "get_records [<index>]\n";
      echo "\n";
      echo "Gets the record at <index>, or the newest record if no index is given.\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 get_records 100\n\n";
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
      echo "spassword1 through spassword4 are up to four keypad super passwords (0 clears one)\n";
      echo "\n";
      echo "Example:\n\n";
      echo "php -f sendCommand.php 0.0.0.0 12345678 set_superPass 01 1234 5678 0 0\n\n";
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
      echo "set_ripp, door_delay, del_auth, add_auth,\n";
      echo "get_timeAccess, set_timeAccess, get_alarm_state, interlock, set_ip,\n";
      echo "get_records, reset_alarm, set_recordIndex, userid, keypad_switch,\n";
      echo "set_superPass\n";
      echo "\n";
      echo "Each command accepts options as needed.\n";
      echo "\n";
      echo "help -- Shows this information\n";
      echo "\n";
      echo "help command -- shows command specific help\n\n";
      break;
  }
}

?>
