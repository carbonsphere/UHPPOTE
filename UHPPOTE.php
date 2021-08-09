<?PHP

class uhppote {
    private $type = 0x17;
    private $sn = null;             //Device Serial Number in hex
    private $packetsize = 128;      // 64 since hex is 2 characters instead of 1;

    private $command = array(
      'dev_status'        => 0x20,
      'open_door'         => 0x40,
      'set_time'          => 0x30,
      'get_time'          => 0x32,
      'search'            => 0x94,  // Get Device Serial Number SN
      'set_ip'            => 0x96,  // Set Device IP
      'get_recordIndex'   => 0xb4,  // Get Swipe Records Index
      'set_recordIndex'   => 0xb2,  // Set Swipe Records Index
      'get_records'       => 0xb0,  // Get Swipe Records from Index + 1
      'set_ripp'          => 0x90,  // Remote Event receiver IP and port
      'get_ripp'          => 0x92,  // Remote Event receiver IP and port
      'get_auth_rec'      => 0x58,  // Get Number of authorized record
      'get_auth'          => 0x5A,  // Get/Check Authorizations
      'add_auth'          => 0x50,  // Add/Edit Authorization return true = success false = failed
      'del_auth'          => 0x52,  // Delete Authorization individual
      'del_auth_all'      => 0x54,  // Delete All Authorization
      'door_delay'        => 0x80,  // Set Door Delay seconds
      'door_delay_get'    => 0x82,  // Get Door Delay seconds
      'userid'            => 0x5C,  // User ID is like memory slot of system
      // Clock Timer for Card Id. Each Timer can have 3 different access timeframe.
      // Each timer can be attached to card id.
      'set_timeAccess'    => 0x88,  // Set Access by weekday/time 2-255  0x02-0xFF
      'get_timeAccess'    => 0x98,  // Get weekday/time access settings
      'set_superPass'     => 0x8C,  // Set Super Password
      'keypad_switch'     => 0xA4,  // Enable and disable keypad 1~4
      'interlock'         => 0xA2,  // Set Door interlocking pattern
      'reset_alarm'       => 0xC0,  // Reset Alarm event
      'get_alarm_state'   => 0xC2,  // Get Alarm State

      'sav_task_list'     => 0xAC,  // Save tasks
      'del_task_list'     => 0xA6,  // Clear tasks
      'add_task_list'     => 0xA8,  // Add Task List
    );

    private $ymd_mask = array(
      'month'   => 0x0f,          //Masking last 4 bits
      'day'     => 0x1f,          //Masking last 5 bits
      'minute'  => 0x3f,          //Masking last 6 bits
      'second'  => 0x1f,
    );

    /**
     * Processing received commands
     * ($receive is a hex string)
     * @param $receive
     * @return array
     */
    public function procCmd($receive) {

        $ret = array();
        $index = 0;
        $type=substr($receive,$index,2);
        $index+=2;

        $cmd=hexdec(substr($receive,$index,2));
        $index+=2;

        $buff=substr($receive,$index,4);
        $index+=4;

        $sn=substr($receive,$index,8);
        $sn = $this->reverseByte($sn);
        $index+=8;

        switch($cmd) {
            case $this->command['set_ip']:

                break;
            /*
             * Get Remote Monitor IP and port
             */
            case $this->command['get_ripp']:
                $ipex = implode(".",array_map(function($el){
                    return hexdec($el);
                },
                  str_split(substr($receive,$index,8),2)));

                $index+=8;

                $port = hexdec($this->reverseByte(substr($receive,$index,4)));
                $index+=4;

                $sec = hexdec(substr($receive,$index,2));

                $ret = array(
                  'ip'        => $ipex,
                  'port'      => $port,
                  'second'    =>$sec
                );
                return $ret;
                break;
            /**
             * Check Door delay
             */
            case $this->command['door_delay_get']:
            case $this->command['door_delay']:
                $lastBytes=substr($receive,$index,6);
                $index+=6;
                return $lastBytes;
                break;
            /*
             * Check Card ID Permission
             * it will return valid begin YMD - end YMD
             */
            case $this->command['userid']:
            case $this->command['get_auth']:
                $cardid=substr($receive,$index,8);
                $index+=8;
                if($cardid == '00000000') {
                    $this->debug('Card ID not in system');
                    return false;
                } elseif ( strtolower($cardid) == 'ffffffff') {
                    $this->debug('Card Slot is deleted');
                    return false;
                }
                $cardid = hexdec($this->reverseByte($cardid));

                $begYMD = str_split(substr($receive,$index,8),2);
                $begYMD = sprintf("%s%s-%s-%s",$begYMD[0],$begYMD[1],$begYMD[2],$begYMD[3]);
                $index+=8;
                $endYMD = str_split(substr($receive,$index,8),2);
                $endYMD = sprintf("%s%s-%s-%s",$endYMD[0],$endYMD[1],$endYMD[2],$endYMD[3]);
                $index+=8;
                $ret = array(
                  'cardid'        => $cardid,
                  'begymd'        => $begYMD,
                  'endymd'        => $endYMD,
                );

                break;
            /*
             * Get Number of Records from Response
             * @return integer
             */
            case $this->command['get_auth_rec']:
                $nocardid=substr($receive,$index,8);
                $index+=8;
                $nocardid = hexdec($this->reverseByte($nocardid));

                return $nocardid;
                break;
            /*
             * Get Add/Delete/Delete All Response
             * @return success = true
             *          failed = false
             */
            case $this->command['set_ripp']: // Set Remote Monitor IP and port
            case $this->command['del_auth_all']:
            case $this->command['del_auth']:
            case $this->command['add_auth']:
            case $this->command['set_superPass']:
            case $this->command['keypad_switch']:
            case $this->command['interlock']:
            case $this->command['reset_alarm']:
            case $this->command['set_recordIndex']:
            case $this->command['open_door']:
            case $this->command['add_task_list']:
            case $this->command['del_task_list']:
            case $this->command['sav_task_list']:

                $status=substr($receive,$index,2);
                $index+=2;
                if($status == '01') {
                    return true;
                } else {
                    return false;
                }

                break;
            case $this->command['set_time']:
                $year=substr($receive,$index,4);
                $index+=4;
                $month=substr($receive,$index,2);
                $index+=2;
                $day = substr($receive,$index,2);
                $index+=2;

                $hour = substr($receive,$index,2);
                $index+=2;
                $minute = substr($receive,$index,2);
                $index+=2;
                $second = substr($receive,$index,2);
                $index+=2;

                $ret = [
                    'year'      => $year,
                    'month'     => $month,
                    'day'       => $day,
                    'hour'      => $hour,
                    'minute'    => $minute,
                    'second'    => $second
                ];

                break;
            /*
             * Get Device DateTime in string
             * @return "2017-02-20 22:10:33"
             */
            case $this->command['get_time']:
                $datetime = str_split(substr($receive,$index,14),2);
                $index+=14;
                $systemymdhms = sprintf("%s%s-%s-%s %s:%s:%s",
                  $datetime[0],$datetime[1],$datetime[2],$datetime[3],
                  $datetime[4],$datetime[5],$datetime[6]
                );
                $ret = array(
                  'systemymdhms'      => $systemymdhms
                );

                break;
            /*
             * Device current status in array
             * Device Health status = $ret['systemstat']
             * 00 = OK
             */
            case $this->command['dev_status']:
                $lastIndexO=substr($receive,$index,8);
                $index+=8;
                $lastIndex = hexdec($this->reverseByte($lastIndexO));

                $swipeRecord=substr($receive,$index,2);
                $index+=2;

                $noAccess =substr($receive,$index,2);
                $index+=2;

                $doornum =substr($receive,$index,2);
                $index+=2;

                $dooropen =substr($receive,$index,2);
                $index+=2;

                $cardid =substr($receive,$index,8);
                $cardid = hexdec($this->reverseByte($cardid));
                $index+=8;

                $swipeymdhms = str_split(substr($receive,$index,14),2);
                $swipeymdhms = sprintf("%s%s-%s-%s %s:%s:%s",
                  $swipeymdhms[0],  // Year First 2
                  $swipeymdhms[1],  // Year Second 2
                  $swipeymdhms[2],  // Month
                  $swipeymdhms[3],  // Day
                  $swipeymdhms[4],  // Hour
                  $swipeymdhms[5],  // Minute
                  $swipeymdhms[6]  // Second
                );
                $index+=14;

                $swipeReason = substr($receive,$index,2);
                $index+=2;

                $door1stat = substr($receive,$index,2);
                $index+=2;

                $door2stat = substr($receive,$index,2);
                $index+=2;

                $door3stat = substr($receive,$index,2);
                $index+=2;

                $door4stat = substr($receive,$index,2);
                $index+=2;

                $door1button = substr($receive,$index,2);
                $index+=2;

                $door2button = substr($receive,$index,2);
                $index+=2;

                $door3button = substr($receive,$index,2);
                $index+=2;

                $door4button = substr($receive,$index,2);
                $index+=2;

                $systemstat = substr($receive,$index,2);
                $index+=2;

                $systemtime = implode(":",str_split(substr($receive,$index,6),2));
                $index+=6;

                $packetserial = substr($receive,$index,8);
                $index+=8;

                $backup = substr($receive,$index,8);
                $index+=8;

                $specialmsg = substr($receive,$index,2);
                $index+=2;

                $battery = substr($receive,$index,2);
                $index+=2;

                $fire = substr($receive,$index,2);
                $index+=2;

                $systemdate = str_split(substr($receive,$index,6),2);
                $systemdate = sprintf("20%s-%s-%s",
                  $systemdate[0],
                  $systemdate[1],
                  $systemdate[2]
                );
                $index+=6;


                $ret = array(
                  'index'             => $lastIndex,
                  'swipeRecord'       => $swipeRecord,
                  'noaccess'          => $noAccess,
                  'doornum'           => $doornum,
                  'dooropen'          => $dooropen,
                  'cardid'            => $cardid,
                  'swipeymdhms'       => $swipeymdhms,
                  'swipereason'       => $swipeReason,
                  'door1stat'         => $door1stat,
                  'door2stat'         => $door2stat,
                  'door3stat'         => $door3stat,
                  'door4stat'         => $door4stat,
                  'door1button'       => $door1button,
                  'door2button'       => $door2button,
                  'door3button'       => $door3button,
                  'door4button'       => $door4button,
                  'systemstat'        => $systemstat,     // 00 System is OK
                  'systemtime'        => $systemtime,
                  'packetserial'      => $packetserial,
                  'backup'            => $backup,
                  'specialmsg'        => $specialmsg,
                  'battery'           => $battery,
                  'fire'              => $fire,
                  'systemdate'        => $systemdate,
                );

                break;

            /*
             * Search response. Usually only SN is required.
             */
            case $this->command['search']:
                $ip = substr($receive,$index,8);
                $index +=8;
                $ipex = str_split($ip,2);
                $ip = implode('.',array_map(function($el){return hexdec($el);},$ipex));

                $mask = substr($receive,$index,8);
                $index +=8;
                $mask = str_split($mask,2);
                $mask = implode('.',array_map(function($el){return hexdec($el);},$mask));

                $gate = substr($receive,$index,8);
                $index +=8;
                $gate = str_split($gate,2);
                $gate = implode('.',array_map(function($el){return hexdec($el);},$gate));

                $mac = substr($receive,$index,12);
                $macex = str_split($mac,2);
                $mac = implode('-',$macex);
                $index +=12;

                $ver = substr($receive,$index,4);
                $index +=4;

                $date = substr($receive,$index,8);
                $index +=8;

                $ret = array(
                  'cmd'   => 'search',
                  'ip'    => $ip,
                  'mask'  => $mask,
                  'gate'  => $gate,
                  'mac'   => $mac,
                  'ver'   => $ver,
                  'date'  => $date,
                  'sn'    => $sn,
                );

                break;

            /*
             * Get Current Record Index
             */
            case $this->command['get_recordIndex']:
                $recIndex=hexdec($this->reverseByte(substr($receive,$index,8)));
                $index+=8;
                $ret = array(
                  'recordIndex' => $recIndex
                );
                break;

			case $this->command['get_records']:
                $recIndex=hexdec($this->reverseByte(substr($receive,$index,8)));
                $index+=8;
                $type = substr($receive,$index,2);
                $index+=2;
                $access = substr($receive,$index,2);
                $index+=2;
                $door = substr($receive,$index,2);
                $index+=2;
                $doorStat = substr($receive,$index,2);
                $index+=2;
                $cardid = hexdec($this->reverseByte(substr($receive,$index,8)));
                $index+=8;
                $swipeymdhms = str_split(substr($receive,$index,14),2);
                $swipeymdhmsFormated = sprintf("%s%s-%s-%s %s:%s:%s",
                  $swipeymdhms[0],  // Year First 2
                  $swipeymdhms[1],  // Year Second 2
                  $swipeymdhms[2],  // Month
                  $swipeymdhms[3],  // Day
                  $swipeymdhms[4],  // Hour
                  $swipeymdhms[5],  // Minute
                  $swipeymdhms[6]  // Second
                );
                $index+=14;
                $rType = substr($receive,$index,2);
                $index+=2;

                $ret = array(
                  'Index'   => $recIndex,
                  'Type'    => $type,
                  'Access'  => $access,
                  'Door'    => $door,
                  'DoorStat' => $doorStat,
                  'CardId'  => $cardid,
                  'swipeymdhms'    => $swipeymdhmsFormated,
                  'rType'   => $rType,
                );
                break;

            case $this->command['set_timeAccess']:
                $state=substr($receive,$index,2);
                $index+=2;

                if($state == '01') {
                    $this->debug("Timer set success");
                } else {
                    $this->debug("Error: unable to set timer '$state'");
                }

                break;
            case $this->command['get_timeAccess']:
                $gt_index = substr($receive,$index,2);
                $index+=2;
                
                $gt_begDate = substr($receive,$index,8);
                $index+=8;
                $gt_endDate = substr($receive,$index,8);
                $index+=8;

                $gt_week1 = substr($receive,$index,2);
                $index+=2;

                $gt_week2 = substr($receive,$index,2);
                $index+=2;

                $gt_week3 = substr($receive,$index,2);
                $index+=2;

                $gt_week4 = substr($receive,$index,2);
                $index+=2;

                $gt_week5 = substr($receive,$index,2);
                $index+=2;

                $gt_week6 = substr($receive,$index,2);
                $index+=2;

                $gt_week7 = substr($receive,$index,2);
                $index+=2;

                $gt_tzBeg1 = substr($receive,$index,4);
                $index+=4;
                $gt_tzEnd1 = substr($receive,$index,4);
                $index+=4;

                $gt_tzBeg2 = substr($receive,$index,4);
                $index+=4;
                $gt_tzEnd2 = substr($receive,$index,4);
                $index+=4;

                $gt_tzBeg3 = substr($receive,$index,4);
                $index+=4;

                $gt_tzEnd3 = substr($receive,$index,4);
                $index+=4;

                $gt_chain = substr($receive,$index,2);
                $index+=2;

                $gt_countType = substr($receive,$index,2);
                $index+=2;

                $gt_countDate = substr($receive,$index,2);
                $index+=2;

                $gt_countMonth = substr($receive,$index,2);
                $index+=2;

                $gt_filler = substr($receive,$index,8);
                $index+=8;

                $gt_zone1 = substr($receive,$index,2);
                $index+=2;

                $gt_zone2 = substr($receive,$index,2);
                $index+=2;

                $gt_zone3 = substr($receive,$index,2);
                $index+=2;

                $gt_weekend = substr($receive,$index,2);
                $index+=2;

                $ret = [
                    "index" => $gt_index,

                    "beg" => $gt_begDate,
                    "end" => $gt_endDate,

                    "w1" => $gt_week1,
                    "w2" => $gt_week2,
                    "w3" => $gt_week3,
                    "w4" => $gt_week4,
                    "w5" => $gt_week5,
                    "w6" => $gt_week6,
                    "w7" => $gt_week7,

                    "time1beg" => $gt_tzBeg1,
                    "time1end" => $gt_tzEnd1,
                    "time2beg" => $gt_tzBeg2,
                    "time2end" => $gt_tzEnd2,
                    "time3beg" => $gt_tzBeg3,
                    "time3end" => $gt_tzEnd3,

                    "countType"  => $gt_countType,
                    "countDay"   => $gt_countDate,
                    "countMonth" => $gt_countMonth,
                    "countZone1" => $gt_zone1,
                    "countZone2" => $gt_zone2,
                    "countZone3" => $gt_zone3,
                    "weekend" => $gt_weekend
                ];
                
                break;
            default:
                $this->debug("Error: unable to process command. Unknown. ".$cmd );
                var_dump($receive);
                break;
        }

        return $ret;

    }


    /**
     * This function will construct network payload for commands
     * Commands:
     * search               //Search
     * get_ip/set_ip        //Get/Set device IP
     * get_ripp/set_ripp    //Get/Set Remote host IP
     * door_delay/door_delay_get    //Set/Get Door Delay
     * open_door            //Open Door
     * add_auth/del_auth    //Add/Delete Card ID
     * set_time/get_time    //Get/Set Time
     *
     * @param $cmd
     * @param \DateTime $dt
     * @return string
     */
    public function getCmdHex($cmd,$dt=null,$param=null) {
        //Search doesn't need serial number
        if($cmd != 'search') {
            $this->chkSerialNumber();
        }
        //Get Type
        $hexStr = dechex($this->type);

        $hexStr .= dechex($this->command[$cmd]);    // Add Command Byte
        $hexStr .= '0000'; // 2 byte padding
        switch($cmd) {
            case 'userid':                  // Get Device's user id memory slot permission
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($param['userid'])) {
                    $id = $param['userid'];
                    $idstr = $this->reverseByte(sprintf("%08X",$id));
                    $hexStr .= $idstr;
                }
                break;
            case 'get_ip':                  // Get Device's IP Info
                break;
            case 'set_ip':                  // Set Device's IP Info
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($param['ip'])) {
                    $ip = $param['ip'];
                    $ipex = explode('.',$ip);
                    $ip = implode('',array_map(function($el){return sprintf("%02X",intval($el));},$ipex));
                    $hexStr .= $ip;

                    $mask = $param['mask'];
                    $maskex = explode('.',$mask);
                    $mask = implode('',array_map(function($el){return sprintf("%02X",intval($el));},$maskex));
                    $hexStr .= $mask;

                    $gate = $param['gate'];
                    $gateex = explode('.',$gate);
                    $gate = implode('',array_map(function($el){return sprintf("%02X",intval($el));},$gateex));
                    $hexStr .= $gate;

                    $hexStr .= '55AAAA55';
                }
                break;
            case 'get_ripp':
                $hexStr .= $this->sn;   // Add Serial Number

                break;
            case 'set_ripp':
                $hexStr .= $this->sn;   // Add Serial Number

                if(isset($param['ip'])) {
                    $ip = $param['ip'];
                    $ipex = explode('.',$ip);
                    $ipf = '';
                    foreach($ipex as $e ){
                        $ipf .= sprintf("%02X",intval($e));
                    }
                    $port = sprintf("%04X",$param['port']);
                    $hexStr .= $ipf . $this->reverseByte($port);
                    $hexStr .= sprintf("%02X",$param['sec']);
                }


                break;
            case 'door_delay_get':  // Gets Door Delay seconds
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($param['door'])) {
                    $hexStr .= $param['door'];
                }
                break;
            case 'door_delay':
                $hexStr .= $this->sn;   // Add Serial Number

                if(isset($param['seconds'])) {
                    $sec = sprintf("%02X",$param['seconds']);
                    $hexStr .= $param['door'];
                    $hexStr .= '03';
                    $hexStr .= $sec;
                }

                break;
            /*
                 Clears all task list
            */
            case 'del_task_list':
            case 'sav_task_list':
            case 'del_auth_all':
                $hexStr .= $this->sn;   // Add Serial Number
                $hexStr .= '55AAAA55';
                break;
            case 'del_auth':
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($param['cardid'])) {
                    $b = base_convert($param['cardid'],10,16);
                    $c = strlen($b);
                    for($i=8-$c; $i > 0; $i--) {
                        $b = '0' . $b;
                    }
                    $hexStr .= $this->reverseByte($b);
                }

                break;

            /*
             * param['beg']  Beginning date of auth period
             * param['end']  End date of auth period
             * beg/end format [ year month day ]
             * Ex: 2000 01 01   "20000101" year 2000 jan 1st
             * Max End date "20291231"  2029 Dec 31st
             */
            case 'add_auth':                        // Add/Edit Card ID
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($param['cardid'])) {
                    $b = base_convert($param['cardid'],10,16);
                    $c = strlen($b);
                    for($i=8-$c; $i > 0; $i--) {
                        $b = '0' . $b;
                    }
                    $hexStr .= $this->reverseByte($b);
                    $hexStr .= $param['beg'] . $param['end'];
                    // Rule Index for Door 1
                    $hexStr .= isset($param['ta1'])? $param['ta1']: '01'; // default to allow all
                    // Rule Index for Door 2
                    $hexStr .= isset($param['ta2'])? $param['ta2']: '01';
                    // Rule Index for Door 3
                    $hexStr .= isset($param['ta3'])? $param['ta3']: '01';
                    // Rule Index for Door 4
                    $hexStr .= isset($param['ta4'])? $param['ta4']: '01';
//                    $hexStr .= '01010101'; //Currently set all door available.
                }

                break;
            case 'set_time':
                $hexStr .= $this->sn;   // Add Serial Number
                if(!$dt) {
                    $this->debug("Error: date time empty");
                    return;
                }
                $YY = $dt->format('Y');
                $Y1 = substr($YY,0,2);
                $Y2 = substr($YY,2);
                $M = $dt->format('m');
                $D = $dt->format('d');
                $H = $dt->format('H');
                $m = $dt->format('i');
                $s = $dt->format('s');

                $hexStr .= $Y1 .$Y2 . $M . $D . $H . $m . $s;
                break;
            case 'get_recordIndex':
            case 'get_time':            // Get Device Time
            case 'dev_status':          // Get Device Status
            case 'get_auth_rec':        // Get Auth Record
            case 'get_alarm_state':        // Get Alarm State
                $hexStr .= $this->sn;   // Add Serial Number
                break;
            case 'get_auth':
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($param['cardid'])) {
                    if($param['cardid'] == 0 || $param['cardid'] == 4294967295 ||  $param['cardid'] >= 4294967040) {
                        $this->debug("Error: invalid ID " . $param['cardid']);
                        break;
                    }
                    $cardidHex = base_convert($param['cardid'], 10, 16);
                    $c = strlen($cardidHex);
                    for ($i = 8 - $c; $i > 0; $i--) {
                        $cardidHex = '0' . $cardidHex;
                    }
                    $hexStr .= $this->reverseByte($cardidHex);
                }
                break;
            case 'search':              // Search for Devices
                break;
            case 'open_door':
                $hexStr .= $this->sn;   // Add Serial Number
                $hexStr .= isset($param['door']) ? (gettype($param['door']) == 'integer' ? '0' . strval($param['door']) : '01') : '01'; // Add Door Number '01' '02' '03' '04'
                $this->debug("Open Door");
                break;
            case 'set_recordIndex':
                $hexStr .= $this->sn;   // Add Serial Number
                $hexStr .= $this->reverseByte(sprintf("%08X", $dt)); // Add Index hex into command string.
                $hexStr .= "55AAAA55";
                $this->debug("Set Record to $dt");
                break;
            case 'get_records':
                $hexStr .= $this->sn;   // Add Serial Number
                $dt++;  //Using DT variable as Record Index
                $hexStr .= $this->reverseByte(sprintf("%08X", $dt)); // Add Index hex into command string.
                $this->debug("Get Record from Index + 1");
                break;

                /**
                 * param
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
                 timeControl = [
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

                    "time1beg"   => '1600',
                    "time1end"   => '2359',
                    "time2beg"   => '0000',
                    "time2end"   => '0000',
                    "time3beg"   => '0000',
                    "time3end"   => '0000',

                    "countType"  => '01',
                    "countDay"   => '00',
                    "countMonth" => '00',
                    "countZone1" => '00',
                    "countZone2" => '00',
                    "countZone3" => '00',
                    "weekend"    => '00',
                 ];
                 *
                 *
                 *
                 *
                 */
            case 'set_timeAccess':
                $hexStr .= $this->sn;   // Add Serial Number
                $hexStr .= $param['index'];           // Index to be modified
                $hexStr .= $param['beg'] . $param['end'];    // Beg Date & End Date
                $hexStr .= $param['w1'] . $param['w2'] . $param['w3'] . $param['w4']. 
                    $param['w5'] . $param['w6'] . $param['w7'];
                $hexStr .= $param['time1beg'] . $param['time1end'] . 
                    $param['time2beg'] . $param['time2end'] .
                    $param['time3beg'] . $param['time3end'];
                $hexStr .= '00';   // Filler
                $hexStr .= $param['countType'];   // 01 Independent Count 00 Total Count
                $hexStr .= $param['countDay'] . $param['countMonth']; // Number of access allowed per day/month
                $hexStr .= '00000000'; //Filler
                $hexStr .= $param['countZone1'] . $param['countZone2'] .$param['countZone3'];
                $hexStr .= $param['weekend'];

                break;

            case 'get_timeAccess':
                $hexStr .= $this->sn;   // Add Serial Number
                $hexStr .= $param['index'];

                break;


            case 'set_superPass':
                $hexStr .= $this->sn;   // Add Serial Number

                // 1 byte door index  '01': door 1    '02': door 2  '03': door 3   '04': door 4
                $hexStr .= $param['doorIndex'];

                $hexStr .= '000000';    // Add 3 byte spacer

                // 4 byte Super password
                $hexStr .= $this->reverseByte(sprintf("%08X", $param['spassword1']));
                // 4 byte Super password
                $hexStr .= $this->reverseByte(sprintf("%08X", $param['spassword2']));
                // 4 byte Super password
                $hexStr .= $this->reverseByte(sprintf("%08X", $param['spassword3']));
                // 4 byte Super password
                $hexStr .= $this->reverseByte(sprintf("%08X", $param['spassword4']));

                break;

            case 'keypad_switch':

                $hexStr .= $this->sn;   // Add Serial Number

                //  '00' disable keypad    '01' enable keypad
                $hexStr .= $param['pad1'];
                $hexStr .= $param['pad2'];
                $hexStr .= $param['pad3'];
                $hexStr .= $param['pad4'];

                break;

            case 'interlock':
                $hexStr .= $this->sn;   // Add Serial Number

                /* 1 byte interlock pattern
                '00' no interlock
                '01' 1,2 door interlock
                '02' 3,4 door interlock
                '03' pair lock for (1,2) (3,4)
                '04' 1,2,3 door interlock
                '08' 1,2,3,4 door interlock
                 */
                $hexStr .= $param['interlock'];

                break;

            case 'reset_alarm':
                $hexStr .= $this->sn;   // Add Serial Number

                break;

            /*
             * Controller task list
             *
             * Input parameters
             $task = [
                'begDate' => '20191010',        // Default current system time.
                'endDate' => '20291231',        // Default 2019-12-31
                'w1'     => '01',               // Monday Default Enabled
                'w2'     => '01',               // Tuesday Default Enabled
                'w3'     => '01',               // Wednesday Default Enabled
                'w4'     => '01',               // Thursday Default Enabled
                'w5'     => '01',               // Friday Default Enabled
                'w6'     => '01',               // Saturday Default Enabled
                'w7'     => '01',               // Sunday Default Enabled
                'sTime'  => '0000',             // Default midnight
                'door'   => '01',               // Door Number  01,02,03,04 Default 01
                'task'   => '00',               // Default 00:Door controlled
                    // Task List Options
                    // '00': Door Controlled
                    // '01': Door Open
                    // '02': Door Closed
                    // '03': Disable Time Profile
                    // '04': Enable Time Profile
                    // '05': Card : No Password
                    // '06': (In)Card + Password
                    // '07': (Out)Card + Password
                    // '08': MoreCard Disable
                    // '09': MoreCard Enable
                    // '10': Trigger Once(V3.9)
                    // '11': PushButton Disable (V5.52)
                    // '12': PushButton Enable (V5.52)
                'option'  => '00',              // Unknown
                ];
             *
             */
            case 'add_task_list':
                $dt = (new \DateTime())->format('Ymd');

                $hexStr .= isset($param['begDate']) ? $param['begDate'] : $dt;
                $hexStr .= isset($param['endDate']) ? $param['endDate'] : "20191231";

                //Monday to Sunday
                $hexStr .= isset($param['w1'])? $param['w1']: '01';
                $hexStr .= isset($param['w2'])? $param['w2']: '01';
                $hexStr .= isset($param['w3'])? $param['w3']: '01';
                $hexStr .= isset($param['w4'])? $param['w4']: '01';
                $hexStr .= isset($param['w5'])? $param['w5']: '01';
                $hexStr .= isset($param['w6'])? $param['w6']: '01';
                $hexStr .= isset($param['w7'])? $param['w7']: '01';

                //Start time
                $hexStr .= isset($param['sTime'])? $param['sTime']: '0000';

                //Door number
                $hexStr .= isset($param['door'])? $param['door']: '01';

                //Task
                $hexStr .= isset($param['task'])? $param['task']: '00';

                $hexStr .= isset($param['option'])? $param['option']: '00';

                break;

            default:
                $this->debug("Error unable to find command ".$cmd);
                break;
        }

        $hexStr = $this->padCmdPackets($hexStr);

        return $hexStr;

    }

    /**
     * If cmd packet is not 64 bytes, then pad the rest of commands.
     *
     * @param $cmd
     * @return string
     */
    private function padCmdPackets($cmd){
        $clen = strlen($cmd);
        if($clen < $this->packetsize) {
            $padlen = ($this->packetsize - $clen)/2;
            for($i = 0 ; $i < $padlen; $i++ ) {
                $cmd .= '00';
            }
            $this->debug("Padding ".$padlen);
        } else {
            $this->debug("Error, unable to pad Packets");
        }
        return $cmd;
    }

    private function debug($msg) {
        echo $msg . "\n";
    }

    /**
     * hexDateBytes = 8 hex 4 binary datetime representation bytes
     * first 2 are YYYY-MM-DD
     * second 2 are H:M:S
     *
     * Ex: 4e 22 c0 b5 = 2017-02-14 22:46:00
     * @param $hexDateBytes
     * @return \DateTime
     */
    public function ConvertDateByteToDateTime($hexDateBytes) {
        $hexDateBytes = str_replace(':','',$hexDateBytes);
        //First check hexDateBytes
        if(strlen($hexDateBytes) != 8) {
            $this->debug("Error: hexDateBytes are not 8 characters.");
            return null;
        }
        $dateYMD = $this->reverseByte(substr($hexDateBytes,0,4));
        $dateHMS = $this->reverseByte(substr($hexDateBytes,4));

        $YMD = base_convert($dateYMD,16,2);
        $HMS = base_convert($dateHMS,16,2);
        $yy = bindec($YMD);
        $Y = ($yy >> 9) + 2000;
        $M = ($yy >> 5) & $this->ymd_mask['month'];
        $D = $yy & $this->ymd_mask['day'];
        $xx = bindec($HMS);
        $H = $xx >> 11;
        $m = ($xx >> 5) & $this->ymd_mask['minute'];
        $S = ($xx & $this->ymd_mask['second']) * 2;
        $retdate = "$Y-$M-$D $H:$m:$S";
        $this->debug($retdate);
        return new Datetime($retdate);

    }


    /**
     * @param $in
     * @return string
     */
    private function reverseByte($in) {

        $inproc = str_split($in,2);

        $clen = count($inproc);
        $outBytes = '';

        for($i=$clen-1; $i >= 0; $i--){
            $outBytes .= $inproc[$i];
        }

        return $outBytes;

    }

    private function chkSerialNumber() {
        if(!$this->sn) {
            echo "Please set Serial number before start";
            die;
        }
    }

    /**
     * @return string
     */
    public function getSn() {
        return $this->sn;
    }

    /**
     * Serial number, generally its the last 4 byte of mac address of the device
     * Ex: 00:00:11:22:33:44
     * input serial number:  "11223344"
     *
     * @param string $sn
     */
    public function setSn($sn) {
        $this->sn = $this->reverseByte($sn);
    }

    public function printCMD($cmd)
    {
        $cmd = implode(' ',str_split($cmd,2));
        $this->debug($cmd);
    }

}

?>
