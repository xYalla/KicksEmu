<?php

class Constants {
    
    public static $auto_stats = array(
        10 => array(1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0),
        11 => array(1, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0),
        12 => array(1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0),
        13 => array(1, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0),
        20 => array(1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0),
        21 => array(1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0),
        22 => array(1, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0),
        23 => array(1, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0),
        24 => array(1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        30 => array(1, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        31 => array(1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        32 => array(1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        33 => array(1, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        );
    
    public static $creation_stats = array(
        10 => array(40, 30, 30, 30, 30, 20, 15, 35, 30, 20, 55, 60, 55, 30, 0, 0, 0),
        20 => array(40, 35, 30, 25, 30, 25, 20, 20, 15, 30, 60, 65, 55, 30, 0, 0, 0),
        30 => array(40, 30, 30, 25, 25, 30, 30, 35, 15, 15, 50, 60, 65, 30, 0, 0, 0)
        );
    
    public static $stats_for_level =  array(
        10 => 2, 20 => 2, 30 => 2, 35 => 2, 40 => 2, 50 => 2, 55 => 2
        );
    
    public static $positions = array(
        "FW" => 10, "ST" => 11, "CF" => 12, "WF" => 13,
        "MF" => 20, "AMF" => 21, "SMF" => 22, "CMF" => 23, "DMF" => 24,
        "DF" => 30, "SB" => 31, "CB" => 32, "SW" => 33
        );

    public static $upgrade_stats = array(
        11 => array(0, 0, 0, 7, 7, 0, -10, 7, 7, -10, 0, 0, 0, 0, 0, 0, 0),
        12 => array(0, 0, 7, 0, 7, 0, -10, 0, 7, -10, 0, 7, 0, 0, 0, 0, 0),
        13 => array(0, 7, 0, 0, 7, 0, -10, 0, 7, -10, 7, 0, 0, 0, 0, 0, 0),
        21 => array(0, 0, 7, 0, 7, 0, -10, 0, 0, 7, 0, 7, -10, 0, 0, 0, 0),
        22 => array(0, 7, 0, 0, 7, 0, -10, -10, 0, 7, 7, 0, 0, 0, 0, 0, 0),
        23 => array(0, 0, 0, 7, 7, 0, -10, -10, 0, 7, 0, 7, 0, 0, 0, 0, 0),
        24 => array(0, 7, 0, 0, 0, 7, 0, 7, -10, -10, 0, 0, 0, 7, 0, 0, 0),
        31 => array(0, 7, 7, 0, 0, 7, 0, -10, -10, 0, 7, 0, 0, 0, 0, 0, 0),
        32 => array(0, 7, 0, 0, 0, 7, 7, 7, -10, -10, 0, 0, 0, 0, 0, 0, 0),
        33 => array(0, 7, 0, 0, 0, 0, 7, 0, -10, -10, 0, 0, 7, 7, 0, 0, 0)
        );
}

?>