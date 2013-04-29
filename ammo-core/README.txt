
Vanderbilt Core
===================

Description
-----------
This application is the core of AMMO. Essentially it 
is a middleware piece on an Android device. It 
takes care of all communication with the Gateway servers
and helps in sending and receiving data to and from 
other entities.

Installation
------------

Please see the script install-stock-apks.sh located 
in Dropbox/Streak Config Files/Vanderbilt. Run the 
script from that location to install all Vanderbilt 
AMMO apps on a connected Android device.


Usage
-----
	Prerequisites
	------------------------
	This application does not have any pre-requisite 
	and runs on its own. It needs a network connection for it
	to function properly.

	Start Core
	------------------------
	Go to <Home> (press Home button)->All Apps. 
	Select the Ammo icon.

	Configuration
	------------------------
	The configuration details are given below.

	1. Configure the Gateway Ip Address

	This should be set to the Ip Address of the machine 
	which runs the Gateway process.

	From the main Core screen, select the Preferences icon.
	This shows the preference screen. The first item is 
	Plugin IP Address. Select this. 
	An edit box shows up. Enter the IP address of the Gateway
	to this edit box and press <OK>.

	2. Set the Operator Identifier

	This is set to the TIGR user id of the person using 
	the phone.

	Select the item <Operator Identifier> and enter the 
	operator id. Press <OK>. 

	Press the <BACK> button. This should show the main 
	screen of the Ammo. The main screen lists the Gateway
	server that was configured. Check the status of the 
	configured gateway.
