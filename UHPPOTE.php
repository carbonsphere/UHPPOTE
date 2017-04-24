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
      'get_record'        => 0,
      'set_ripp'          => 0x90,  //Remote Event receiver IP and port
      'get_ripp'          => 0x92,  //Remote Event receiver IP and port
      'get_auth_rec'      => 0x58,  // Get Number of authorized record
      'get_auth'          => 0x5A,  // Get/Check Authorizations
      'add_auth'          => 0x50,  // Add/Edit Authorization return true = success false = failed
      'del_auth'          => 0x52,  // Delete Authorization individual
      'del_auth_all'      => 0x54,  // Delete All Authorization
      'door_delay'        => 0x80,  // Set Door Delay seconds
      'door_delay_get'    => 0x82,  // Get Door Delay seconds
      'userid'            => 0x5C,  // User ID is like memory slot of system
    );

    private $ymd_mask = array(
      'month'   => 0x0f,        //Masking last 4 bits
      'day'     => 0x1f,          //Masking last 5 bits
      'minute'  => 0x3f,       //Masking last 6 bits
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
                $status=substr($receive,$index,2);
                $index+=2;
                if($status == '01') {
                    return true;
                } else {
                    return false;
                }

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
                $lastIndex = $this->reverseByte($lastIndexO);

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
                  'index'             => intval($lastIndex),
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

            default:
                $this->debug("Error: unable to process command. Unknown. ".$cmd );
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
    public function getCmdHex($cmd,$dt=null,$addCardId=null) {
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
                if(isset($addCardId['userid'])) {
                    $id = $addCardId['userid'];
                    $idstr = $this->reverseByte(sprintf("%08X",$id));
                    $hexStr .= $idstr;
                }
                break;
            case 'get_ip':                  // Get Device's IP Info
                break;
            case 'set_ip':                  // Set Device's IP Info
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($addCardId['ip'])) {
                    $ip = $addCardId['ip'];
                    $ipex = explode('.',$ip);
                    $ip = implode('',array_map(function($el){return sprintf("%02X",intval($el));},$ipex));
                    $hexStr .= $ip;

                    $mask = $addCardId['mask'];
                    $maskex = explode('.',$mask);
                    $mask = implode('',array_map(function($el){return sprintf("%02X",intval($el));},$maskex));
                    $hexStr .= $mask;

                    $gate = $addCardId['gate'];
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

                if(isset($addCardId['ip'])) {
                    $ip = $addCardId['ip'];
                    $ipex = explode('.',$ip);
                    $ipf = '';
                    foreach($ipex as $e ){
                        $ipf .= sprintf("%02X",intval($e));
                    }
                    $port = sprintf("%04X",$addCardId['port']);
                    $hexStr .= $ipf . $this->reverseByte($port);
                    $hexStr .= sprintf("%02X",$addCardId['sec']);
                }


                break;
            case 'door_delay_get':  // Gets Door Delay seconds
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($addCardId['door'])) {
                    $hexStr .= $addCardId['door'];
                }
                break;
            case 'door_delay':
                $hexStr .= $this->sn;   // Add Serial Number

                if(isset($addCardId['seconds'])) {
                    $sec = sprintf("%02X",$addCardId['seconds']);
                    $hexStr .= $addCardId['door'];
                    $hexStr .= '03';
                    $hexStr .= $sec;
                }

                break;
            case 'del_auth_all':
                $hexStr .= $this->sn;   // Add Serial Number
                $hexStr .= '55AAAA55';
                break;
            case 'del_auth':
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($addCardId['cardid'])) {
                    $b = base_convert($addCardId['cardid'],10,16);
                    $c = strlen($b);
                    for($i=8-$c; $i > 0; $i--) {
                        $b = '0' . $b;
                    }
                    $hexStr .= $this->reverseByte($b);
                }

                break;
            case 'add_auth':                        // Add/Edit Card ID
                $hexStr .= $this->sn;   // Add Serial Number
                if(isset($addCardId['cardid'])) {
                    $b = base_convert($addCardId['cardid'],10,16);
                    $c = strlen($b);
                    for($i=8-$c; $i > 0; $i--) {
                        $b = '0' . $b;
                    }
                    $hexStr .= $this->reverseByte($b);
                    $hexStr .= $addCardId['beg'] . $addCardId['end'];
                    $hexStr .= '01010101'; //Currently set all door available.
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
            case 'get_time':            // Get Device Time
            case 'dev_status':          // Get Device Status
            case 'get_auth_rec':        // Get Auth Record
                $hexStr .= $this->sn;   // Add Serial Number
                break;
            case 'search':              // Search for Devices
                break;
            case 'open_door':
                $hexStr .= $this->sn;   // Add Serial Number
                $hexStr .= '01';        // Add Door Number 01 02 03 04
                $this->debug("Open Door");
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


}

?>
